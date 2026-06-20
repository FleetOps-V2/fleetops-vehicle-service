package com.fleetops.vehicle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleetops.vehicle.dto.FleetAnalysisResponse;
import com.fleetops.vehicle.entity.AiAnalysisAudit;
import com.fleetops.vehicle.entity.Vehicle;
import com.fleetops.vehicle.repository.AiAnalysisAuditRepository;
import com.fleetops.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FleetAiServiceTest {

    @Mock VehicleRepository vehicleRepository;
    @Mock BedrockRuntimeClient bedrockClient;
    @Mock AiAnalysisAuditRepository auditRepository;

    FleetAiService fleetAiService;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        fleetAiService = new FleetAiService(vehicleRepository, bedrockClient, objectMapper, auditRepository);
        ReflectionTestUtils.setField(fleetAiService, "modelId", "mistral.mistral-7b-instruct-v0:2");
        // Spring doesn't inject @Lazy self-proxy in unit tests; set it directly so analyseFleet() can call invokeBedrockCached()
        ReflectionTestUtils.setField(fleetAiService, "self", fleetAiService);
    }

    @Test
    void analyseFleet_alertVehicles_returnsAnalysisAndSavesAudit() throws Exception {
        Vehicle v = buildVehicle(1L, "FL-001", LocalDate.now().minusDays(1), 50000, 45000,
                LocalDate.now().plusDays(60));
        when(vehicleRepository.findAll()).thenReturn(List.of(v));

        String innerJson = objectMapper.writeValueAsString(Map.of(
                "fleetHealthScore", 70,
                "summary", "Fleet needs attention.",
                "recommendations", List.of(Map.of(
                        "vehicleId", 1, "vehicleNumber", "FL-001",
                        "priority", "HIGH", "taskType", "ROUTINE_SERVICE",
                        "action", "Schedule overdue service",
                        "reasoning", "Next service date passed.",
                        "confidence", 90
                ))
        ));
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(converseResponse(innerJson));
        when(auditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FleetAnalysisResponse result = fleetAiService.analyseFleet("admin");

        assertThat(result.getFleetHealthScore()).isEqualTo(70);
        assertThat(result.getSummary()).isEqualTo("Fleet needs attention.");
        assertThat(result.getRecommendations()).hasSize(1);
        assertThat(result.getRecommendations().get(0).getVehicleNumber()).isEqualTo("FL-001");
        assertThat(result.getRecommendations().get(0).getPriority()).isEqualTo("HIGH");
        assertThat(result.getRecommendations().get(0).getConfidence()).isEqualTo(90);
        verify(auditRepository).save(any(AiAnalysisAudit.class));
    }

    @Test
    void analyseFleet_healthyFleet_returnsEmptyRecommendations() throws Exception {
        Vehicle v = buildVehicle(2L, "FL-002", LocalDate.now().plusMonths(3), 10000, 20000,
                LocalDate.now().plusMonths(6));
        when(vehicleRepository.findAll()).thenReturn(List.of(v));

        String innerJson = objectMapper.writeValueAsString(Map.of(
                "fleetHealthScore", 95,
                "summary", "All vehicles are in good condition.",
                "recommendations", List.of()
        ));
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(converseResponse(innerJson));
        when(auditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FleetAnalysisResponse result = fleetAiService.analyseFleet("manager1");

        assertThat(result.getFleetHealthScore()).isEqualTo(95);
        assertThat(result.getRecommendations()).isEmpty();
    }

    @Test
    void analyseFleet_bedrockThrows_propagatesRuntimeException() {
        when(vehicleRepository.findAll()).thenReturn(List.of());
        when(bedrockClient.converse(any(ConverseRequest.class)))
                .thenThrow(new RuntimeException("Bedrock unreachable"));

        assertThatThrownBy(() -> fleetAiService.analyseFleet("admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bedrock unreachable");
    }

    @Test
    void analyseFleet_markdownWrappedJson_stripsAndParses() throws Exception {
        when(vehicleRepository.findAll()).thenReturn(List.of());
        String innerJson = "```json\n{\"fleetHealthScore\":80,\"summary\":\"OK\",\"recommendations\":[]}\n```";
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(converseResponse(innerJson));
        when(auditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FleetAnalysisResponse result = fleetAiService.analyseFleet("admin");

        assertThat(result.getFleetHealthScore()).isEqualTo(80);
    }

    @Test
    void analyseFleet_vehicleWithExpiringInsurance_includedInPrompt() throws Exception {
        Vehicle v = buildVehicle(3L, "FL-003", LocalDate.now().plusMonths(3), 5000, 20000,
                LocalDate.now().plusDays(20));
        when(vehicleRepository.findAll()).thenReturn(List.of(v));

        String innerJson = objectMapper.writeValueAsString(Map.of(
                "fleetHealthScore", 60, "summary", "Insurance expiry alert.",
                "recommendations", List.of(Map.of(
                        "vehicleId", 3, "vehicleNumber", "FL-003",
                        "priority", "MEDIUM", "taskType", "ROUTINE_SERVICE",
                        "action", "Renew insurance", "reasoning", "Expires within 30 days.",
                        "confidence", 85
                ))
        ));
        when(bedrockClient.converse(any(ConverseRequest.class))).thenReturn(converseResponse(innerJson));
        when(auditRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        FleetAnalysisResponse result = fleetAiService.analyseFleet("manager1");

        assertThat(result.getFleetHealthScore()).isEqualTo(60);
        assertThat(result.getRecommendations().get(0).getVehicleNumber()).isEqualTo("FL-003");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Vehicle buildVehicle(Long id, String number, LocalDate nextServiceDate,
                                  int currentMileage, int nextServiceMileage,
                                  LocalDate insuranceExpiry) {
        Vehicle v = new Vehicle();
        v.setId(id);
        v.setVehicleNumber(number);
        v.setBrand("Toyota");
        v.setModel("Hiace");
        v.setType("VAN");
        v.setStatus(Vehicle.VehicleStatus.ACTIVE);
        v.setCurrentMileage(currentMileage);
        v.setNextServiceDate(nextServiceDate);
        v.setNextServiceMileage(nextServiceMileage);
        v.setInsuranceExpiry(insuranceExpiry);
        return v;
    }

    private ConverseResponse converseResponse(String textContent) {
        return ConverseResponse.builder()
                .output(ConverseOutput.builder()
                        .message(Message.builder()
                                .role(ConversationRole.ASSISTANT)
                                .content(ContentBlock.fromText(textContent))
                                .build())
                        .build())
                .stopReason(StopReason.END_TURN)
                .usage(TokenUsage.builder().inputTokens(50).outputTokens(100).totalTokens(150).build())
                .build();
    }
}
