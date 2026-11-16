package com.konohahelpdesk.dto.template;

import com.konohahelpdesk.model.template.TemplateKind;
import com.konohahelpdesk.model.template.TicketTemplate;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;

import java.time.LocalDateTime;

/** A template as the API returns it. */
public class TemplateResponse {

    private Long id;
    private TemplateKind kind;
    private String name;
    private String description;
    private String titleTemplate;
    private String body;
    private String stepsTemplate;
    private TicketCategory category;
    private TicketPriority priority;
    private Long companyId;
    private String companyName;
    private boolean visibleToCustomers;
    private boolean active;
    private int sortOrder;
    private long useCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TemplateResponse from(TicketTemplate t) {
        TemplateResponse r = new TemplateResponse();
        r.id = t.getId();
        r.kind = t.getKind();
        r.name = t.getName();
        r.description = t.getDescription();
        r.titleTemplate = t.getTitleTemplate();
        r.body = t.getBody();
        r.stepsTemplate = t.getStepsTemplate();
        r.category = t.getCategory();
        r.priority = t.getPriority();
        r.companyId = t.getCompany() == null ? null : t.getCompany().getId();
        r.companyName = t.getCompany() == null ? null : t.getCompany().getName();
        r.visibleToCustomers = t.isVisibleToCustomers();
        r.active = t.isActive();
        r.sortOrder = t.getSortOrder();
        r.useCount = t.getUseCount();
        r.createdAt = t.getCreatedAt();
        r.updatedAt = t.getUpdatedAt();
        return r;
    }

    public Long getId() { return id; }
    public TemplateKind getKind() { return kind; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getTitleTemplate() { return titleTemplate; }
    public String getBody() { return body; }
    public String getStepsTemplate() { return stepsTemplate; }
    public TicketCategory getCategory() { return category; }
    public TicketPriority getPriority() { return priority; }
    public Long getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public boolean isVisibleToCustomers() { return visibleToCustomers; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public long getUseCount() { return useCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
