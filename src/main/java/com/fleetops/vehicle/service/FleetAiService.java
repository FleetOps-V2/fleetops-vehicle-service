package com.fleetops.vehicle.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(Map.of(
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(Map.of("text", prompt))
                    )),
                    "inferenceConfig", Map.of("max_new_tokens", 1024)
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Bedrock request", e);
        }

        log.debug("Calling Bedrock model={} for fleet analysis requested by={}", modelId, requestedBy);

        InvokeModelResponse response = bedrockClient.invokeModel(
                InvokeModelRequest.builder()
                        .modelId(modelId)
                        .contentType("application/json")
                        .accept("application/json")
                        .body(SdkBytes.fromUtf8String(bodyJson))
                        .build()
        );

        FleetAnalysisResponse result;
        try {
            JsonNode root = objectMapper.readTree(response.body().asUtf8String());
            String text = root.at("/output/message/content/0/text").asText();
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
        sb.append("You are a fleet maintenance advisor for a vehicle management platform.\n");
        sb.append("Analyse the fleet and return ONLY a JSON object — no markdown, no explanation.\n\n");
        sb.append("JSON format:\n");
        sb.append("{\n");
        sb.append("  \"fleetHealthScore\": <0-100 integer>,\n");
        sb.append("  \"summary\": \"<2-3 sentence fleet health overview>\",\n");
        sb.append("  \"recommendations\": [\n");
        sb.append("    {\n");
        sb.append("      \"vehicleId\": <Long>,\n");
        sb.append("      \"vehicleNumber\": \"<string>\",\n");
        sb.append("      \"priority\": \"HIGH|MEDIUM|LOW\",\n");
        sb.append("      \"taskType\": \"ROUTINE_SERVICE|OIL_CHANGE|TIRE_CHANGE|BATTERY|BREAKDOWN\",\n");
        sb.append("      \"action\": \"<max 80 chars>\",\n");
        sb.append("      \"reasoning\": \"<max 150 chars>\",\n");
        sb.append("      \"confidence\": <0-100 integer>\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append(String.format("Fleet data (%d total vehicles, %d service alerts, %d insurance alerts):\n",
                total, serviceAlerts, insuranceAlerts));

        for (Vehicle v : alertVehicles) {
            sb.append(String.format(
                    "ID=%d | %s | %s %s | Status=%s | Mileage=%d | LastService=%s | NextServiceDate=%s | NextServiceMileage=%s | InsuranceExpiry=%s%n",
                    v.getId(),
                    v.getVehicleNumber(),
                    v.getBrand(),
                    v.getModel(),
                    v.getStatus(),
                    v.getCurrentMileage() != null ? v.getCurrentMileage() : 0,
                    v.getLastServiceDate() != null ? v.getLastServiceDate() : "N/A",
                    v.getNextServiceDate() != null ? v.getNextServiceDate() : "N/A",
                    v.getNextServiceMileage() != null ? v.getNextServiceMileage() : "N/A",
                    v.getInsuranceExpiry() != null ? v.getInsuranceExpiry() : "N/A"
            ));
        }

        if (alertVehicles.isEmpty()) {
            sb.append("No vehicles with immediate alerts — all fleet vehicles appear to be in good condition.\n");
        }

        sb.append("\nOnly include vehicles that need attention. Return empty recommendations array if fleet is healthy.");
        return sb.toString();
    }
}
