package com.konohahelpdesk.dto.ticket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.model.ticket.HoldReason;
import com.konohahelpdesk.model.ticket.SlaState;
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.ticket.TicketCategory;
import com.konohahelpdesk.model.ticket.TicketComment;
import com.konohahelpdesk.model.ticket.TicketPriority;
import com.konohahelpdesk.model.ticket.TicketStatus;
import com.konohahelpdesk.model.user.User;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketResponse {
    private Long id;
    private String title;
    private String description;
    private String stepsToReproduce;
    private TicketCategory category;
    private TicketPriority priority;
    private TicketStatus status;
    private Long companyId;
    private String companyName;
    private Person createdBy;
    /** Who the ticket is for. Absent on a company-level ticket. */
    private Person requester;
    private Person assignee;
    private LocalDateTime responseDueAt;
    private LocalDateTime resolutionDueAt;
    private LocalDateTime firstResponseAt;
    private LocalDateTime responseBreachedAt;
    private LocalDateTime resolutionBreachedAt;
    /** Derived by SlaCalculator — see {@link SlaState}. Null only when the caller didn't ask for it. */
    private SlaState responseSla;
    private SlaState resolutionSla;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    // ON_HOLD only (absent otherwise). The staff note behind a hold is an internal comment.
    private HoldReason holdReason;
    private String holdReasonLabel;
    private LocalDateTime holdUntil;
    private LocalDateTime slaPausedAt;
    /** Working minutes on hold during the current SLA clock. */
    private long slaPausedMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long commentCount;
    private List<Comment> comments;
    private List<Attachment> attachments;

    public record Person(Long id, String firstName, String lastName, String photoUrl) {
        public static Person of(User u) {
            return u == null ? null : new Person(u.getId(), u.getFirstName(), u.getLastName(), u.getPhotoId() == null ? null : "/api/media/" + u.getPhotoId());
        }
    }
    public record Comment(Long id, Person author, String body, boolean internal, boolean system, LocalDateTime createdAt) {
        public static Comment of(TicketComment c) {
            return new Comment(c.getId(), Person.of(c.getAuthor()), c.getBody(), c.isInternal(), c.isSystem(), c.getCreatedAt());
        }
    }
    public record Attachment(String id, String name, String contentType, long sizeBytes, String url, LocalDateTime createdAt) {
        public static Attachment of(MediaFile m) {
            return new Attachment(m.getId(), m.getOriginalName(), m.getContentType(), m.getSizeBytes(), "/api/media/" + m.getId(), m.getCreatedAt());
        }
    }

    /** List-view shape: no description/comments/attachments. */
    public static TicketResponse summary(Ticket t, long commentCount) {
        TicketResponse r = new TicketResponse();
        r.id = t.getId();
        r.title = t.getTitle();
        r.category = t.getCategory();
        r.priority = t.getPriority();
        r.status = t.getStatus();
        r.companyId = t.getCompany().getId();
        r.companyName = t.getCompany().getName();
        r.createdBy = Person.of(t.getCreatedBy());
        r.requester = Person.of(t.getRequester());
        r.assignee = Person.of(t.getAssignee());
        r.responseDueAt = t.getResponseDueAt();
        r.resolutionDueAt = t.getResolutionDueAt();
        r.firstResponseAt = t.getFirstResponseAt();
        r.responseBreachedAt = t.getResponseBreachedAt();
        r.resolutionBreachedAt = t.getResolutionBreachedAt();
        r.resolvedAt = t.getResolvedAt();
        r.closedAt = t.getClosedAt();
        r.holdReason = t.getHoldReason();
        r.holdReasonLabel = t.getHoldReason() == null ? null : t.getHoldReason().label();
        r.holdUntil = t.getHoldUntil();
        r.slaPausedAt = t.getSlaPausedAt();
        r.slaPausedMinutes = t.getSlaPausedMinutes();
        r.createdAt = t.getCreatedAt();
        r.updatedAt = t.getUpdatedAt();
        r.commentCount = commentCount;
        return r;
    }

    public static TicketResponse full(Ticket t, List<TicketComment> comments, List<MediaFile> attachments) {
        TicketResponse r = summary(t, comments.stream().filter(c -> !c.isSystem()).count());
        r.description = t.getDescription();
        r.stepsToReproduce = t.getStepsToReproduce();
        r.comments = comments.stream().map(Comment::of).toList();
        r.attachments = attachments.stream().map(Attachment::of).toList();
        return r;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getStepsToReproduce() { return stepsToReproduce; }
    public TicketCategory getCategory() { return category; }
    public TicketPriority getPriority() { return priority; }
    public TicketStatus getStatus() { return status; }
    public Long getCompanyId() { return companyId; }
    public String getCompanyName() { return companyName; }
    public Person getCreatedBy() { return createdBy; }
    public Person getRequester() { return requester; }
    public Person getAssignee() { return assignee; }
    public LocalDateTime getResponseDueAt() { return responseDueAt; }
    public LocalDateTime getResolutionDueAt() { return resolutionDueAt; }
    /** Filled in by TicketService, which owns the SlaCalculator. */
    public void applySla(SlaState response, SlaState resolution) {
        this.responseSla = response;
        this.resolutionSla = resolution;
    }

    public SlaState getResponseSla() { return responseSla; }
    public SlaState getResolutionSla() { return resolutionSla; }
    public LocalDateTime getResponseBreachedAt() { return responseBreachedAt; }
    public LocalDateTime getResolutionBreachedAt() { return resolutionBreachedAt; }
    public LocalDateTime getFirstResponseAt() { return firstResponseAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public HoldReason getHoldReason() { return holdReason; }
    public String getHoldReasonLabel() { return holdReasonLabel; }
    public LocalDateTime getHoldUntil() { return holdUntil; }
    public LocalDateTime getSlaPausedAt() { return slaPausedAt; }
    public long getSlaPausedMinutes() { return slaPausedMinutes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public long getCommentCount() { return commentCount; }
    public List<Comment> getComments() { return comments; }
    public List<Attachment> getAttachments() { return attachments; }
}
