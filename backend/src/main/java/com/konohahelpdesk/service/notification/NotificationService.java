package com.konohahelpdesk.service.notification;

import com.konohahelpdesk.dto.notification.NotificationResponse;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.notification.Notification;
import com.konohahelpdesk.model.notification.NotificationType;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.notification.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * In-app / browser-push notifications. Kept deliberately simple: rows in a table, polled by the
 * frontend (same pattern as the rest of the app's "live" data) — no websocket/SSE infrastructure.
 * The frontend fires the actual browser Notification popup itself when it sees a new unread row.
 */
@Service
public class NotificationService {

    private static final int LIST_CAP = 50;

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Fire-and-forget: create one notification. Never throws into the caller's transaction over a bad recipient. */
    @Transactional
    public void notify(User recipient, NotificationType type, String title, String body, Long ticketId) {
        save(recipient, type, title, body, ticketId, null);
    }

    /**
     * A notification about something other than a ticket: {@code link} is the in-app path the bell
     * opens ("/admin/kb/12/edit"). Title and body are cut to fit their columns rather than failing.
     */
    @Transactional
    public void notifyLink(User recipient, NotificationType type, String title, String body, String link) {
        save(recipient, type, title, body, null, link);
    }

    private void save(User recipient, NotificationType type, String title, String body, Long ticketId, String link) {
        if (recipient == null) return;
        Notification n = new Notification();
        n.setRecipient(recipient);
        n.setType(type);
        n.setTitle(fit(title, 200));
        n.setBody(fit(body, 500));
        n.setTicketId(ticketId);
        n.setLink(link);
        repository.save(n);
    }

    private static String fit(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Same as {@link #notify} but skips when the recipient is the actor themselves (no self-notifications). */
    @Transactional
    public void notifyUnlessSelf(User recipient, User actor, NotificationType type, String title, String body, Long ticketId) {
        if (recipient == null || actor == null || recipient.getId().equals(actor.getId())) return;
        notify(recipient, type, title, body, ticketId);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(User actor) {
        return repository.findByRecipientIdOrderByCreatedAtDesc(actor.getId(), PageRequest.of(0, LIST_CAP)).stream()
            .map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(User actor) {
        return Map.of("count", repository.countByRecipientIdAndReadFalse(actor.getId()));
    }

    @Transactional
    public void markRead(User actor, Long id) {
        Notification n = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
        if (!n.getRecipient().getId().equals(actor.getId())) throw new AccessDeniedException("Forbidden");
        if (!n.isRead()) {
            n.setRead(true);
            repository.save(n);
        }
    }

    @Transactional
    public void markAllRead(User actor) {
        repository.markAllRead(actor.getId());
    }
}
