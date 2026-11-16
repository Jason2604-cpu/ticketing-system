package com.konohahelpdesk.model.template;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketPriority;
import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A reusable ticket or reply body.
 *
 * TICKET templates exist because the difference between "it's broken" and a usable ticket is
 * whether the person raising it was asked the right questions. REPLY templates are the
 * engineer-side equivalent for the answers they type twenty times a week.
 *
 * Scoping: a null {@code company} means the template is available everywhere; setting one pins it
 * to that customer. {@code visibleToCustomers} is what decides whether it appears on the client
 * portal's new-ticket form — and it is only ever honoured for {@link TemplateKind#TICKET},
 * because a canned reply is provider-side text by definition.
 */
@Entity
@Table(name = "ticket_templates", indexes = {
    @Index(name = "idx_template_kind", columnList = "kind"),
    @Index(name = "idx_template_company", columnList = "company_id")
})
public class TicketTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateKind kind = TemplateKind.TICKET;

    /** What the template is called in the picker, e.g. "Server unreachable". */
    @Column(nullable = false, length = 120)
    private String name;

    /** One line under the name in the picker, so people choose the right one without opening it. */
    @Column(length = 250)
    private String description;

    /** TICKET only: prefills the ticket's title. Null leaves the field for the user to write. */
    @Column(length = 200)
    private String titleTemplate;

    /** The body. For TICKET it fills the description; for REPLY it is the comment text. */
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    /** TICKET only: prefills the "steps to reproduce" field. */
    @Column(name = "steps_template", columnDefinition = "text")
    private String stepsTemplate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TicketPriority priority;

    /** Null = available to every company. Set = pinned to that one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /** TICKET only: show it on the client portal's new-ticket form. Ignored for REPLY. */
    @Column(name = "visible_to_customers", nullable = false)
    private boolean visibleToCustomers = false;

    /** Unticked templates stay in the admin list but disappear from every picker. */
    @Column(nullable = false)
    private boolean active = true;

    /** Ordering hint for the picker — lower first, then by name. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /** How many times it has been used. Cheap signal for which templates are worth keeping. */
    @Column(name = "use_count", nullable = false)
    private long useCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
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
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public boolean isVisibleToCustomers() { return visibleToCustomers; }
    public void setVisibleToCustomers(boolean visibleToCustomers) { this.visibleToCustomers = visibleToCustomers; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public long getUseCount() { return useCount; }
    public void setUseCount(long useCount) { this.useCount = useCount; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
