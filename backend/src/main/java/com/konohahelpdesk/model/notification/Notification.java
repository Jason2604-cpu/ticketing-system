package com.konohahelpdesk.model.notification;

import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** An in-app / browser-push notification for one recipient. */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "ix_notifications_recipient", columnList = "recipient_id, created_at"),
        @Index(name = "ix_notifications_recipient_unread", columnList = "recipient_id, is_read")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: the bell lists a user's own notifications and never reads the recipient back, so
    // loading the user (and its company graph) per row was wasted work on a polled endpoint.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    /** The ticket this notification is about, if any — used to link/deep-link from the UI. */
    @Column(name = "ticket_id")
    private Long ticketId;

    /**
     * Where opening the notification goes, as an in-app path ("/admin/kb/12/edit"). Null for the
     * ticket notifications, which open their ticket through {@link #ticketId}.
     */
    @Column(length = 300)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
