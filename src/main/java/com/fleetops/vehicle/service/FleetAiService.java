package com.fleetops.vehicle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetops.vehicle.dto.FleetAnalysisResponse;
import com.fleetops.vehicle.entity.AiAnalysisAudit;
import com.fleetops.vehicle.entity.Vehicle;
import com.fleetops.vehicle.repository.AiAnalysisAuditRepository;
import com.fleetops.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FleetAiService {

    private static final Logger log = LoggerFactory.getLogger(FleetAiService.class);

    private final VehicleRepository vehicleRepository;
    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;
    private final AiAnalysisAuditRepository auditRepository;

    // Self-injection via proxy so @Cacheable on invokeBedrockCached() is respected.
    // Direct this.invokeBedrockCached() bypasses the AOP proxy and skips the cache.
    @Autowired @Lazy
    private FleetAiService self;

    @Value("${bedrock.model-id}")
    private String modelId;

    public FleetAiService(VehicleRepository vehicleRepository,
                          BedrockRuntimeClient bedrockClient,
                          ObjectMapper objectMapper,
                          AiAnalysisAuditRepository auditRepository) {
        this.vehicleRepository = vehicleRepository;
        this.bedrockClient = bedrockClient;
        this.objectMapper = objectMapper;
        this.auditRepository = auditRepository;
    }

    // Outer method — never cached, always records audit regardless of cache hits.
    public FleetAnalysisResponse analyseFleet(String requestedBy) {
        long start = System.currentTimeMillis();
        FleetAnalysisResponse result = self.invokeBedrockCached();

        AiAnalysisAudit audit = new AiAnalysisAudit();
        audit.setRequestedBy(requestedBy);
        audit.setRequestedAt(Instant.now());
        audit.setModel(modelId);
        audit.setFleetHealthScore(result.getFleetHealthScore());
        audit.setRecommendationCount(result.getRecommendations() == null ? 0 : result.getRecommendations().size());
        audit.setExecutionTimeMs(System.currentTimeMillis() - start);
        auditRepository.save(audit);

        log.info("Fleet analysis complete: score={}, recommendations={}, ms={}",
                result.getFleetHealthScore(), audit.getRecommendationCount(), audit.getExecutionTimeMs());

        return result;
    }

    // Inner cached method — calls Bedrock; result served from cache for 15 min after first call.
    @Cacheable(value = "fleetAnalysis", key = "'current'")
    public FleetAnalysisResponse invokeBedrockCached() {
        List<Vehicle> allVehicles = vehicleRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate insuranceThreshold = today.plusDays(30);

        List<Vehicle> alertVehicles = allVehicles.stream()
                .filter(v -> v.getStatus() == Vehicle.VehicleStatus.ACTIVE)
                .filter(v -> {
                    boolean serviceDue = (v.getNextServiceDate() != null && !today.isBefore(v.getNextServiceDate()))
                            || (v.getNextServiceMileage() != null && v.getCurrentMileage() != null
                                && v.getCurrentMileage() >= v.getNextServiceMileage());
                    boolean insuranceExpiring = v.getInsuranceExpiry() != null
                            && !insuranceThreshold.isBefore(v.getInsuranceExpiry());
                    return serviceDue || insuranceExpiring;
                })
                .collect(Collectors.toList());

        long serviceAlertCount = alertVehicles.stream().filter(Vehicle::isServiceDueSoon).count();
        long insuranceAlertCount = alertVehicles.stream().filter(Vehicle::isInsuranceExpiringSoon).count();

        String prompt = buildPrompt(allVehicles.size(), serviceAlertCount, insuranceAlertCount, alertVehicles);

        log.debug("Calling Bedrock model={} via Converse API for fleet analysis", modelId);

        ConverseResponse response = bedrockClient.converse(
                ConverseRequest.builder()
                        .modelId(modelId)
                        .messages(Message.builder()
                                .role(ConversationRole.USER)
                                .content(ContentBlock.fromText(prompt))
                                .build())
                        .inferenceConfig(InferenceConfiguration.builder()
                                .maxTokens(1200)
                                .build())
                        .build()
        );

        try {
            String text = response.output().message().content().get(0).text();
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            return objectMapper.readValue(text, FleetAnalysisResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Bedrock response", e);
        }
    }

    private String buildPrompt(int total, long serviceAlerts, long insuranceAlerts, List<Vehicle> alertVehicles) {
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder();
        sb.append("You are a fleet maintenance advisor writing for a non-technical fleet manager.\n");
        sb.append("Return ONLY valid JSON, no markdown, no explanation outside the JSON:\n");
        sb.append("{\"fleetHealthScore\":0-100,\"summary\":\"2 sentences explaining overall fleet status and the most urgent risk\",");
        sb.append("\"recommendations\":[{\"vehicleId\":0,\"vehicleNumber\":\"\",\"priority\":\"HIGH|MEDIUM|LOW\",");
        sb.append("\"taskType\":\"ROUTINE_SERVICE|OIL_CHANGE|TIRE_CHANGE|BATTERY|BREAKDOWN\",");
        sb.append("\"action\":\"Short imperative title (max 70 chars)\",");
        sb.append("\"reasoning\":\"2-3 sentences: state the specific problem with numbers, explain the operational or financial risk if ignored, and recommend the urgency (e.g. within X days/weeks)\",");
        sb.append("\"confidence\":0-100}]}\n\n");
        sb.append(String.format("Fleet overview: %d total vehicles, %d needing service, %d with insurance expiring within 30 days. Today: %s.\n",
                total, serviceAlerts, insuranceAlerts, today));
        sb.append("Only ACTIVE vehicles are listed — vehicles already IN_SERVICE or BREAKDOWN are being handled.\n");
        sb.append("Vehicles needing attention:\n");

        alertVehicles.stream().limit(8).forEach(v -> {
            long currentKm = v.getCurrentMileage() != null ? v.getCurrentMileage() : 0;
            String kmGap = "-";
            if (v.getNextServiceMileage() != null) {
                long gap = v.getNextServiceMileage() - currentKm;
                kmGap = gap <= 0 ? "OVERDUE by " + Math.abs(gap) + " km" : gap + " km until service";
            }
            String svcDate = "-";
            if (v.getNextServiceDate() != null) {
                long daysUntil = today.until(v.getNextServiceDate(), java.time.temporal.ChronoUnit.DAYS);
                svcDate = daysUntil < 0
                        ? "service date OVERDUE by " + Math.abs(daysUntil) + " days"
                        : "service due in " + daysUntil + " days (" + v.getNextServiceDate() + ")";
            }
            String insStatus = "-";
            if (v.getInsuranceExpiry() != null) {
                long daysUntilIns = today.until(v.getInsuranceExpiry(), java.time.temporal.ChronoUnit.DAYS);
                insStatus = daysUntilIns < 0
                        ? "insurance EXPIRED " + Math.abs(daysUntilIns) + " days ago"
                        : "insurance expires in " + daysUntilIns + " days (" + v.getInsuranceExpiry() + ")";
            }
            sb.append(String.format("- %s (id=%d): current=%d km, mileage=%s, %s, %s\n",
                    v.getVehicleNumber(), v.getId(), currentKm, kmGap, svcDate, insStatus));
        });

        if (alertVehicles.isEmpty()) {
            sb.append("All vehicles are healthy — no action required.\n");
        }
        return sb.toString();
    }
}
