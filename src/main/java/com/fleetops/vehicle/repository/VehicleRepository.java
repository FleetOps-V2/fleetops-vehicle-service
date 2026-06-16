package com.fleetops.vehicle.repository;

import com.fleetops.vehicle.entity.Vehicle;
import com.fleetops.vehicle.entity.Vehicle.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByType(String type);

    List<Vehicle> findByStatus(VehicleStatus status);

    List<Vehicle> findByAssignedDriverId(String driverId);

    // FIX #11: find unassigned vehicles (useful for driver assignment workflows)
    List<Vehicle> findByAssignedDriverIdIsNull();

    // FIX #11: case-insensitive brand search (useful for admin search)
    List<Vehicle> findByBrandContainingIgnoreCase(String brand);

    // FIX #5: Aggregate status count — avoids loading 3 full lists just for dashboard numbers
    @Query("SELECT v.status AS status, COUNT(v) AS total FROM Vehicle v GROUP BY v.status")
    List<Map<String, Object>> countGroupedByStatus();

    // FIX #11: count by status without loading the full list
    long countByStatus(VehicleStatus status);

    // Vehicles whose insurance expires within N days (sorted soonest first)
    @Query("SELECT v FROM Vehicle v WHERE v.insuranceExpiry IS NOT NULL " +
           "AND v.insuranceExpiry <= :cutoffDate AND v.status != 'RETIRED' " +
           "ORDER BY v.insuranceExpiry ASC")
    List<Vehicle> findVehiclesWithExpiringInsurance(@Param("cutoffDate") LocalDate cutoffDate);

    // Vehicles that are service-due by date or mileage
    @Query("SELECT v FROM Vehicle v WHERE v.status = 'ACTIVE' AND (" +
           "(v.nextServiceDate IS NOT NULL AND v.nextServiceDate <= :today) OR " +
           "(v.nextServiceMileage IS NOT NULL AND v.currentMileage >= v.nextServiceMileage))")
    List<Vehicle> findVehiclesDueForService(@Param("today") LocalDate today);

    // Update status in-place (used by Request Service callback)
    @Modifying
    @Transactional
    @Query("UPDATE Vehicle v SET v.status = :status, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") VehicleStatus status);

    // Update mileage in-place
    @Modifying
    @Transactional
    @Query("UPDATE Vehicle v SET v.currentMileage = :mileage, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    int updateMileage(@Param("id") Long id, @Param("mileage") Integer mileage);
}
