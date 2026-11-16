package com.konohahelpdesk.controller.media;

import com.konohahelpdesk.model.media.MediaFile;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.media.MediaStorageService;
import com.konohahelpdesk.service.ticket.TicketService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Serves stored files to authenticated users. Access depends on what the file is:
 *  - USER_PHOTO:        any signed-in user (avatars are visible across the app)
 *  - COMPANY_LOGO:      admins/engineers, or members of that company
 *  - TICKET_ATTACHMENT: anyone who can see the ticket
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaStorageService storage;
    private final TicketService tickets;
    private final CurrentUser currentUser;

    public MediaController(MediaStorageService storage, TicketService tickets, CurrentUser currentUser) {
        this.storage = storage;
        this.tickets = tickets;
        this.currentUser = currentUser;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> get(@PathVariable String id) {
        MediaFile media = storage.get(id);
        switch (media.getKind()) {
            case USER_PHOTO -> { /* any authenticated user */ }
            case COMPANY_LOGO -> currentUser.requireCompanyAccess(media.getOwnerId());
            case TICKET_ATTACHMENT -> { if (!tickets.canAccess(currentUser.get(), media.getOwnerId())) throw new AccessDeniedException("Forbidden"); }
            // Contract documents are provider-internal: the whole Contracts surface is admin /
            // MANAGE_CONTRACTS, so a customer must not be able to pull one by guessing its id.
            case CONTRACT_ATTACHMENT -> { if (!currentUser.canManageContracts()) throw new AccessDeniedException("Forbidden"); }
        }

        Resource body = storage.open(media);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(media.getContentType()))
            .contentLength(media.getSizeBytes())
            // Served inline, but with nosniff + an immutable UUID the browser can cache it safely.
            .header(HttpHeaders.CONTENT_DISPOSITION, (media.getContentType().startsWith("image/") ? "inline" : "attachment")
                + "; filename=\"" + media.getOriginalName().replaceAll("[^A-Za-z0-9._-]", "_") + "\"")
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
            .body(body);
    }
}
