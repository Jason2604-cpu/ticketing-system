package com.konohahelpdesk.dto.template;

import com.konohahelpdesk.model.template.TemplateKind;
import com.konohahelpdesk.model.template.TicketTemplate;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** A template as the API returns it. */
public class TemplateResponse {

    public record CompanyRef(Long id, String name) {}


    private Long id;
    private TemplateKind kind;
    private String name;
    private String description;
    private String titleTemplate;
    private String body;
    private String stepsTemplate;
    private TicketCategory category;
    private TicketPriority priority;
    private List<CompanyRef> companies;
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
        r.companies = t.getCompanies().stream()
            .map(c -> new CompanyRef(c.getId(), c.getName()))
            .sorted(Comparator.comparing(CompanyRef::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
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
    /** Empty means the template is available to every company. */
    public List<CompanyRef> getCompanies() { return companies; }
    public boolean isVisibleToCustomers() { return visibleToCustomers; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public long getUseCount() { return useCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
