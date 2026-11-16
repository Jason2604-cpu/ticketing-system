package com.konohahelpdesk.dto.ticket;

import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateTicketRequest {

    @NotBlank @Size(min = 4, max = 200)
    private String title;

    @NotBlank @Size(min = 10, max = 10_000)
    private String description;

    @Size(max = 5_000)
    private String stepsToReproduce;

    @NotNull private TicketCategory category = TicketCategory.TECHNICAL;
    @NotNull private TicketPriority priority = TicketPriority.MEDIUM;

    /** Required for staff; ignored for customers (always their own company). */
    private Long companyId;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStepsToReproduce() { return stepsToReproduce; }
    public void setStepsToReproduce(String stepsToReproduce) { this.stepsToReproduce = stepsToReproduce; }
    public TicketCategory getCategory() { return category; }
    public void setCategory(TicketCategory category) { this.category = category; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
}
