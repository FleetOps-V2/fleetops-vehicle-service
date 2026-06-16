package com.fleetops.vehicle.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * VehicleTelemetry — stores GPS ping data from fleet vehicles.
 *
 * Intermediate Stage:
 *   GPS simulator (frontend) → POST /api/tracking/ping → this table → GET /api/tracking/live
 *
 * Phase 5 (AWS):
 *   GPS Simulator → SQS FIFO → Lambda → DynamoDB
 *   The API contract (/api/tracking/ping and /api/tracking/live) remains unchanged.
 *   Only the backend implementation changes — the frontend never needs updating.
 *
 * Only the latest ping per vehicle is shown in the live view.
 * History is retained for audit and analytics.
 */
@Entity
@Table(name = "vehicle_telemetry", indexes = {
    @Index(name = "idx_telemetry_vehicle_id", columnList = "vehicle_id"),
    @Index(name = "idx_telemetry_recorded_at", columnList = "recorded_at")
})
public class VehicleTelemetry {

    public enum TelemetryStatus {
        EN_ROUTE, IDLE, SPEEDING, BREAKDOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Integer speed = 0;

    @Column(name = "engine_temp")
    private Integer engineTemp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TelemetryStatus status = TelemetryStatus.IDLE;

    @Column(name = "route_description", length = 200)
    private String routeDescription;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    protected void onCreate() {
        recordedAt = LocalDateTime.now();
        // Auto-classify status from speed if not explicitly set
        if (speed != null && status == TelemetryStatus.IDLE && speed > 0) {
            status = speed > 85 ? TelemetryStatus.SPEEDING : TelemetryStatus.EN_ROUTE;
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Integer getSpeed() { return speed; }
    public void setSpeed(Integer speed) { this.speed = speed; }

    public Integer getEngineTemp() { return engineTemp; }
    public void setEngineTemp(Integer engineTemp) { this.engineTemp = engineTemp; }

    public TelemetryStatus getStatus() { return status; }
    public void setStatus(TelemetryStatus status) { this.status = status; }

    public String getRouteDescription() { return routeDescription; }
    public void setRouteDescription(String routeDescription) { this.routeDescription = routeDescription; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
