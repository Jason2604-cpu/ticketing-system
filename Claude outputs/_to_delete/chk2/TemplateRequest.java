package com.konohahelpdesk.dto.template;

import com.konohahelpdesk.model.template.TemplateKind;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create/update payload for a ticket template or canned reply. */
public class TemplateRequest {

    @NotNull
    private TemplateKind kind = TemplateKind.TICKET;

    @NotBlank(message = "Give the template a name")
    @Size(max = 120)
    private String name;

    @Size(max = 250)
    private String description;

    @Size(max = 200)
    private String titleTemplate;

    @NotBlank(message = "A template needs a body")
    private String body;

    private String stepsTemplate;

    private TicketCategory category;

    private TicketPriority priority;

    /** Null = available to every company. */
    private Long companyId;

    private boolean visibleToCustomers = false;

    private boolean active = true;

    private int sortOrder = 0;

    public TemplateKind getKind() { return kind; }
    public void setKind(TemplateKind kind) { this.kind = kind; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTitleTemplate() { return titleTemplate; }
    public void setTitleTemplate(String titleTemplate) { this.titleTemplate = titleTemplate; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getStepsTemplate() { return stepsTemplate; }
    public void setStepsTemplate(String stepsTemplate) { this.stepsTemplate = stepsTemplate; }
    public TicketCategory getCategory() { return category; }
    public void setCategory(TicketCategory category) { this.category = category; }
    public TicketPriority getPriority() { return priority; }
    public void setPriority(TicketPriority priority) { this.priority = priority; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public boolean isVisibleToCustomers() { return visibleToCustomers; }
    public void setVisibleToCustomers(boolean visibleToCustomers) { this.visibleToCustomers = visibleToCustomers; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
