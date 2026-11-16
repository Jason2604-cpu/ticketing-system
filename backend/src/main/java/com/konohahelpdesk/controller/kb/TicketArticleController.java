package com.konohahelpdesk.controller.kb;

import com.konohahelpdesk.dto.kb.KbTicketArticleResponse;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.kb.KbTicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Knowledge Base articles linked to a ticket. Anyone who can see the ticket can list them (a
 * customer only gets the ones they could open); only staff link and unlink.
 */
@RestController
@RequestMapping("/api/tickets/{ticketId}/articles")
@PreAuthorize("hasAnyRole('ADMIN','ENGINEER','CUSTOMER_ADMIN','CUSTOMER')")
public class TicketArticleController {

    private final KbTicketService service;
    private final CurrentUser currentUser;

    public TicketArticleController(KbTicketService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<List<KbTicketArticleResponse>> list(@PathVariable Long ticketId) {
        return ResponseEntity.ok(service.forTicket(currentUser.get(), ticketId));
    }

    @PostMapping("/{articleId}")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ResponseEntity<List<KbTicketArticleResponse>> link(@PathVariable Long ticketId, @PathVariable Long articleId) {
        return ResponseEntity.ok(service.link(currentUser.get(), ticketId, articleId));
    }

    @DeleteMapping("/{articleId}")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ResponseEntity<List<KbTicketArticleResponse>> unlink(@PathVariable Long ticketId, @PathVariable Long articleId) {
        return ResponseEntity.ok(service.unlink(currentUser.get(), ticketId, articleId));
    }
}
