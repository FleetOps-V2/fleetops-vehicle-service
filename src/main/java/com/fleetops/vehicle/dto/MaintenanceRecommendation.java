package com.fleetops.vehicle.dto;

import java.io.Serializable;

public class MaintenanceRecommendation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long vehicleId;
    private String vehicleNumber;
    private String priority;
    private String taskType;
    private String action;
    private String reasoning;
    private Integer confidence;

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
}
