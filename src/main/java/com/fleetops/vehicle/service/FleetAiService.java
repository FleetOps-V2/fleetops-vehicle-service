package com.fleetops.vehicle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetops.vehicle.dto.FleetAnalysisResponse;
import com.fleetops.vehicle.entity.AiAnalysisAudit;
import com.fleetops.vehicle.entity.Vehicle;
import com.fleetops.vehicle.repository.AiAnalysisAuditRepository;
import com.fleetops.vehicle.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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

    @Cacheable(value = "fleetAnalysis", key = "'current'")
    public FleetAnalysisResponse analyseFleet(String requestedBy) {
        long start = System.currentTimeMillis();

        List<Vehicle> allVehicles = vehicleRepository.findAll();
        LocalDate today = LocalDate.now();
        LocalDate insuranceThreshold = today.plusDays(30);

        List<Vehicle> alertVehicles = allVehicles.stream()
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

        log.debug("Calling Bedrock model={} via Converse API for fleet analysis by={}", modelId, requestedBy);

        ConverseResponse response = bedrockClient.converse(
                ConverseRequest.builder()
                        .modelId(modelId)
                        .messages(Message.builder()
                                .role(ConversationRole.USER)
                                .content(ContentBlock.fromText(prompt))
                                .build())
                        .inferenceConfig(InferenceConfiguration.builder()
                                .maxTokens(512)
                                .build())
                        .build()
        );

        FleetAnalysisResponse result;
        try {
            String text = response.output().message().content().get(0).text();
            text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            result = objectMapper.readValue(text, FleetAnalysisResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Bedrock response", e);
        }

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

    private String buildPrompt(int total, long serviceAlerts, long insuranceAlerts, List<Vehicle> alertVehicles) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fleet advisor. Return ONLY JSON, no markdown:\n");
        sb.append("{\"fleetHealthScore\":0-100,\"summary\":\"1 sentence\",\"recommendations\":[");
        sb.append("{\"vehicleId\":0,\"vehicleNumber\":\"\",\"priority\":\"HIGH|MEDIUM|LOW\",");
        sb.append("\"taskType\":\"ROUTINE_SERVICE|OIL_CHANGE|TIRE_CHANGE|BATTERY|BREAKDOWN\",");
        sb.append("\"action\":\"<60 chars\",\"reasoning\":\"<80 chars\",\"confidence\":0-100}]}\n\n");
        sb.append(String.format("Fleet: %d vehicles, %d service alerts, %d insurance alerts.\n",
                total, serviceAlerts, insuranceAlerts));

        List<Vehicle> topVehicles = alertVehicles.stream().limit(8).collect(Collectors.toList());
        for (Vehicle v : topVehicles) {
            sb.append(String.format("ID=%d %s km=%d svcDate=%s insExp=%s\n",
                    v.getId(), v.getVehicleNumber(),
                    v.getCurrentMileage() != null ? v.getCurrentMileage() : 0,
                    v.getNextServiceDate() != null ? v.getNextServiceDate() : "ok",
                    v.getInsuranceExpiry() != null ? v.getInsuranceExpiry() : "ok"
            ));
        }

        if (alertVehicles.isEmpty()) {
            sb.append("All vehicles healthy. Return empty recommendations.\n");
        }
        return sb.toString();
    }
}
