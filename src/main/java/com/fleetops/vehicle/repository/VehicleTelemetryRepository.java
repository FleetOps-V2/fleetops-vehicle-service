package com.fleetops.vehicle.repository;

import com.fleetops.vehicle.entity.VehicleTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * VehicleTelemetryRepository — data access for GPS telemetry pings.
 *
 * Key query: findLatestPerVehicle — the "live view" query.
 * Returns the single most recent ping per vehicle_id.
 * This is what the Tracking page polls every 3 seconds.
 */
@Repository
public interface VehicleTelemetryRepository extends JpaRepository<VehicleTelemetry, Long> {

    /**
     * Latest ping for each unique vehicle — used by GET /api/tracking/live.
     * Uses a subquery to find the max recorded_at per vehicle_id.
     */
    @Query("""
        SELECT t FROM VehicleTelemetry t
        WHERE t.recordedAt = (
            SELECT MAX(t2.recordedAt)
            FROM VehicleTelemetry t2
            WHERE t2.vehicleId = t.vehicleId
        )
        ORDER BY t.vehicleId ASC
        """)
    List<VehicleTelemetry> findLatestPerVehicle();

    /**
     * Latest ping for a specific vehicle — used by GET /api/tracking/vehicle/{id}.
     */
    @Query("""
        SELECT t FROM VehicleTelemetry t
        WHERE t.vehicleId = :vehicleId
        ORDER BY t.recordedAt DESC
        LIMIT 1
        """)
    Optional<VehicleTelemetry> findLatestByVehicleId(Long vehicleId);

    /**
     * Recent telemetry history for a vehicle — available for future analytics/audit.
     */
    List<VehicleTelemetry> findTop50ByVehicleIdOrderByRecordedAtDesc(Long vehicleId);
}
