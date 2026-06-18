package com.fleetops.vehicle.dto;

import java.io.Serializable;
import java.util.List;

public class FleetAnalysisResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private int fleetHealthScore;
    private String summary;
    private List<MaintenanceRecommendation> recommendations;

    public int getFleetHealthScore() { return fleetHealthScore; }
    public void setFleetHealthScore(int fleetHealthScore) { this.fleetHealthScore = fleetHealthScore; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<MaintenanceRecommendation> getRecommendations() { return recommendations; }
    public void setRecommendations(List<MaintenanceRecommendation> recommendations) { this.recommendations = recommendations; }
}
