package com.konohahelpdesk.dto.notification;

import com.konohahelpdesk.model.notification.Notification;
import com.konohahelpdesk.model.notification.NotificationType;

import java.time.LocalDateTime;

public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String body;
    private Long ticketId;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.id = n.getId();
        r.type = n.getType();
        r.title = n.getTitle();
        r.body = n.getBody();
        r.ticketId = n.getTicketId();
        r.read = n.isRead();
        r.createdAt = n.getCreatedAt();
        return r;
    }

    public Long getId() { return id; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Long getTicketId() { return ticketId; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
