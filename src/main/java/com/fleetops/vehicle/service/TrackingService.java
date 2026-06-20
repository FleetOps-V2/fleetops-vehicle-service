package com.fleetops.vehicle.service;

import com.fleetops.vehicle.entity.VehicleTelemetry;
import com.fleetops.vehicle.repository.VehicleTelemetryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * TrackingService — handles GPS telemetry ingestion and live position queries.
 *
 * Current implementation: PostgreSQL backend.
 *
 * Phase 5 migration path:
 *   - pingVehicle() → will call SQS.sendMessage() instead of repo.save()
 *   - getLivePositions() → will query DynamoDB instead of PostgreSQL
 *   - Controller and API contract stay exactly the same.
 */
@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    private final VehicleTelemetryRepository telemetryRepository;

    public TrackingService(VehicleTelemetryRepository telemetryRepository) {
        this.telemetryRepository = telemetryRepository;
    }

    /**
     * Record a GPS ping for a vehicle.
     * Called by the frontend GPS simulator (or real IoT device in production).
     *
     * @param telemetry  the ping payload
     * @return the saved telemetry record
     */
    @Transactional
    public VehicleTelemetry recordPing(VehicleTelemetry telemetry) {
        // Auto-derive status from speed if caller didn't set it
        if (telemetry.getStatus() == null) {
            int speed = telemetry.getSpeed() != null ? telemetry.getSpeed() : 0;
            if (speed == 0) {
                telemetry.setStatus(VehicleTelemetry.TelemetryStatus.IDLE);
            } else if (speed > 85) {
                telemetry.setStatus(VehicleTelemetry.TelemetryStatus.SPEEDING);
            } else {
                telemetry.setStatus(VehicleTelemetry.TelemetryStatus.EN_ROUTE);
            }
        }

        VehicleTelemetry saved = telemetryRepository.save(telemetry);
        log.debug("Telemetry ping recorded: vehicle={} lat={} lng={} speed={} status={}",
            saved.getVehicleId(), saved.getLatitude(), saved.getLongitude(),
            saved.getSpeed(), saved.getStatus());
        return saved;
    }

    /**
     * Returns the latest GPS position for every active vehicle.
     * Used by GET /api/tracking/live — polled by the frontend every 3 seconds.
     */
    @Transactional(readOnly = true)
    public List<VehicleTelemetry> getLivePositions() {
        return telemetryRepository.findLatestPerVehicle();
    }

    /**
     * Returns the latest GPS position for a single vehicle.
     */
    @Transactional(readOnly = true)
    public Optional<VehicleTelemetry> getVehiclePosition(Long vehicleId) {
        return telemetryRepository.findFirstByVehicleIdOrderByRecordedAtDesc(vehicleId);
    }

    /**
     * Returns the telemetry history for a vehicle (last 50 pings).
     */
    @Transactional(readOnly = true)
    public List<VehicleTelemetry> getVehicleHistory(Long vehicleId) {
        return telemetryRepository.findTop50ByVehicleIdOrderByRecordedAtDesc(vehicleId);
    }
}
