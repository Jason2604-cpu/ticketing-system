package com.konohahelpdesk.controller.ticket;

import com.konohahelpdesk.dto.ticket.CommentRequest;
import com.konohahelpdesk.dto.ticket.CreateTicketRequest;
import com.konohahelpdesk.dto.ticket.TicketResponse;
import com.konohahelpdesk.dto.user.UserResponse;
import com.konohahelpdesk.model.ticket.TicketPriority;
import com.konohahelpdesk.model.ticket.TicketStatus;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.ticket.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService tickets;
    private final CurrentUser currentUser;

    public TicketController(TicketService tickets, CurrentUser currentUser) {
        this.tickets = tickets;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<List<TicketResponse>> list(@RequestParam(required = false) Long companyId,
                                                     @RequestParam(required = false) TicketStatus status,
                                                     @RequestParam(required = false) TicketPriority priority,
                                                     @RequestParam(required = false) Long assigneeId,
                                                     @RequestParam(required = false) String q) {
        return ResponseEntity.ok(tickets.list(currentUser.get(), companyId, status, priority, assigneeId, q));
    }

    /**
     * Open-ticket counts per company for the sidebar badges. Any signed-in user; the scoping is
     * the same as the ticket list's, decided from the caller's own identity.
     */
    @GetMapping("/counts")
    public ResponseEntity<Map<String, Long>> counts() {
        Map<Long, Long> byCompany = tickets.openCountsByCompany(currentUser.get());
        Map<String, Long> body = new java.util.LinkedHashMap<>();
        byCompany.forEach((id, n) -> body.put(String.valueOf(id), n));
        // Jackson would happily serialise Long keys, but a string-keyed object is what the client
        // gets back from JSON anyway — being explicit avoids a type that only looks numeric.
        body.put("total", byCompany.values().stream().mapToLong(Long::longValue).sum());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/assignees")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ResponseEntity<List<UserResponse>> assignees() {
        return ResponseEntity.ok(tickets.assignees());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(tickets.get(currentUser.get(), id));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tickets.create(currentUser.get(), request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TicketResponse> setStatus(@PathVariable Long id, @RequestParam TicketStatus status) {
        return ResponseEntity.ok(tickets.setStatus(currentUser.get(), id, status));
    }

    /** Staff-only. Re-stamps the SLA due dates from the new priority. */
    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ResponseEntity<TicketResponse> setPriority(@PathVariable Long id, @RequestParam TicketPriority priority) {
        return ResponseEntity.ok(tickets.setPriority(currentUser.get(), id, priority));
    }

    /** Body: {"assigneeId": 12} or {"assigneeId": null} to unassign. */
    @PatchMapping("/{id}/assignee")
    @PreAuthorize("hasAnyRole('ADMIN','ENGINEER')")
    public ResponseEntity<TicketResponse> assign(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(tickets.assign(currentUser.get(), id, body.get("assigneeId")));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<TicketResponse> comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.ok(tickets.comment(currentUser.get(), id, request));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketResponse> attach(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(tickets.attach(currentUser.get(), id, file));
    }

    @DeleteMapping("/{id}/attachments/{mediaId}")
    public ResponseEntity<Void> removeAttachment(@PathVariable Long id, @PathVariable String mediaId) {
        tickets.removeAttachment(currentUser.get(), id, mediaId);
        return ResponseEntity.noContent().build();
    }
}
