package com.konohahelpdesk.dto.subscription;

import com.konohahelpdesk.model.plan.SlaPriority;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SlaDto {

    @NotNull private SlaPriority priority;
    @NotNull @Min(1) private Integer responseTimeMinutes;
    @NotNull @Min(1) private Integer resolutionTimeMinutes;

    public SlaDto() {}

    public SlaDto(SlaPriority priority, Integer responseTimeMinutes, Integer resolutionTimeMinutes) {
        this.priority = priority;
        this.responseTimeMinutes = responseTimeMinutes;
        this.resolutionTimeMinutes = resolutionTimeMinutes;
    }

    public SlaPriority getPriority() { return priority; }
    public void setPriority(SlaPriority priority) { this.priority = priority; }
    public Integer getResponseTimeMinutes() { return responseTimeMinutes; }
    public void setResponseTimeMinutes(Integer responseTimeMinutes) { this.responseTimeMinutes = responseTimeMinutes; }
    public Integer getResolutionTimeMinutes() { return resolutionTimeMinutes; }
    public void setResolutionTimeMinutes(Integer resolutionTimeMinutes) { this.resolutionTimeMinutes = resolutionTimeMinutes; }
}
