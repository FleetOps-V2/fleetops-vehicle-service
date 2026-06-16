package com.fleetops.vehicle.controller;

import com.fleetops.vehicle.entity.VehicleTelemetry;
import com.fleetops.vehicle.service.TrackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * TrackingController — REST API for real-time GPS telemetry.
 *
 * Endpoints:
 *   POST /api/tracking/ping          ← GPS simulator or IoT device sends here
 *   GET  /api/tracking/live          ← Frontend polls this every 3s (latest position per vehicle)
 *   GET  /api/tracking/vehicle/{id}  ← Single vehicle latest position
 *
 * Authorization:
 *   POST /ping  — DRIVER, MANAGER, ADMIN  (any authenticated user can send a ping)
 *   GET  /live  — MANAGER, ADMIN          (live view for operations team)
 *   GET  /vehicle/{id} — authenticated    (any role)
 *
 * Phase 5 Note:
 *   When migrating to SQS/DynamoDB in Phase 5, only TrackingService internals change.
 *   These endpoints, their request/response shapes, and authorization rules stay identical.
 */
@RestController
@RequestMapping("/api/tracking")
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    /**
     * POST /api/tracking/ping
     * Accept a GPS telemetry ping from a vehicle (simulator or real device).
     *
     * Request body example:
     * {
     *   "vehicleId": 1,
     *   "vehicleNumber": "KL07AB1234",
     *   "driverName": "Rajesh Kumar",
     *   "latitude": 12.9716,
     *   "longitude": 77.5946,
     *   "speed": 65,
     *   "engineTemp": 88,
     *   "routeDescription": "Chennai → Bangalore"
     * }
     */
    @PostMapping("/ping")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VehicleTelemetry> recordPing(@RequestBody VehicleTelemetry telemetry) {
        VehicleTelemetry saved = trackingService.recordPing(telemetry);
        return ResponseEntity.ok(saved);
    }

    /**
     * GET /api/tracking/live
     * Returns the latest GPS ping for every vehicle that has ever sent a ping.
     * The frontend polls this every 3 seconds to update the map markers.
     */
    @GetMapping("/live")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<VehicleTelemetry>> getLivePositions() {
        return ResponseEntity.ok(trackingService.getLivePositions());
    }

    /**
     * GET /api/tracking/vehicle/{id}
     * Latest position for a specific vehicle.
     */
    @GetMapping("/vehicle/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getVehiclePosition(@PathVariable Long id) {
        return trackingService.getVehiclePosition(id)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.ok(Map.of("message", "No telemetry data for vehicle " + id)));
    }

    /**
     * GET /api/tracking/vehicle/{id}/history
     * Last 50 telemetry pings for a specific vehicle.
     * Useful for route replay and analytics.
     */
    @GetMapping("/vehicle/{id}/history")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<List<VehicleTelemetry>> getVehicleHistory(@PathVariable Long id) {
        return ResponseEntity.ok(trackingService.getVehicleHistory(id));
    }
}
