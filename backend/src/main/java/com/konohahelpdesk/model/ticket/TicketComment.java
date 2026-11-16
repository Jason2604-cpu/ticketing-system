package com.konohahelpdesk.model.ticket;

import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_comments", indexes = @Index(name = "ix_ticket_comments_ticket_created", columnList = "ticket_id, created_at"))
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: comments are always loaded for a ticket that is already in hand.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Null for system-generated entries (created, assigned, status changes). */
    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Internal notes are visible to staff only, never to the customer. */
    @Column(nullable = false)
    private boolean internal = false;

    /** System events (status/assignment) vs. human comments. */
    @Column(nullable = false)
    private boolean system = false;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isInternal() { return internal; }
    public void setInternal(boolean internal) { this.internal = internal; }
    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
