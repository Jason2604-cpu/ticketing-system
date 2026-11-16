package com.konohahelpdesk.controller.kb;

import com.konohahelpdesk.dto.kb.*;
import com.konohahelpdesk.model.kb.KbStatus;
import com.konohahelpdesk.model.kb.KbVisibility;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.kb.KbService;
import com.konohahelpdesk.service.kb.KbTicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Writing the Knowledge Base: working copies, publishing, versions, categories.
 *
 * Class level: Admin, Engineer, or MANAGE_KNOWLEDGE_BASE — customers never get in.
 * The manager-only actions (publish, decline, archive, category writes) carry their own annotation
 * AND are re-checked in {@code KbService}, where the engineer's company scope is also applied.
 */
@RestController
@RequestMapping("/api/kb/manage")
@PreAuthorize("hasAnyRole('ADMIN','ENGINEER') or hasAuthority('PRIV_MANAGE_KNOWLEDGE_BASE')")
public class KbManageController {

    private static final String MANAGER = "hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_KNOWLEDGE_BASE')";

    private final KbService kb;
    private final KbTicketService ticketLinks;
    private final CurrentUser currentUser;

    public KbManageController(KbService kb, KbTicketService ticketLinks, CurrentUser currentUser) {
        this.kb = kb;
        this.ticketLinks = ticketLinks;
        this.currentUser = currentUser;
    }

    // ── Articles ─────────────────────────────────────────────────────────────

    @GetMapping("/articles")
    public ResponseEntity<KbPage<KbManageSummary>> list(
        @RequestParam(required = false) KbStatus status,
        @RequestParam(required = false) KbVisibility visibility,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(defaultValue = "false") boolean requested,
        @RequestParam(required = false) String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size
    ) {
        return ResponseEntity.ok(kb.list(currentUser.get(), status, visibility, categoryId, requested, q, page, size));
    }

    /** {"count": n} — the "Needs publishing" badge. */
    @GetMapping("/requested-count")
    public ResponseEntity<Map<String, Long>> requestedCount() {
        return ResponseEntity.ok(Map.of("count", kb.requestedCount(currentUser.get())));
    }

    @GetMapping("/articles/{id}")
    public ResponseEntity<KbManageResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(kb.get(currentUser.get(), id));
    }

    @PostMapping("/articles")
    public ResponseEntity<KbManageResponse> create(@Valid @RequestBody KbArticleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kb.create(currentUser.get(), request));
    }

    @PutMapping("/articles/{id}")
    public ResponseEntity<KbManageResponse> update(@PathVariable Long id, @Valid @RequestBody KbArticleRequest request) {
        return ResponseEntity.ok(kb.update(currentUser.get(), id, request));
    }

    @PostMapping("/articles/{id}/publish")
    @PreAuthorize(MANAGER)
    public ResponseEntity<KbManageResponse> publish(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) KbPublishRequest request) {
        return ResponseEntity.ok(kb.publish(currentUser.get(), id, request == null ? null : request.changeNote()));
    }

    @PostMapping("/articles/{id}/request-publish")
    public ResponseEntity<KbManageResponse> requestPublish(@PathVariable Long id) {
        return ResponseEntity.ok(kb.requestPublish(currentUser.get(), id));
    }

    @PostMapping("/articles/{id}/decline-publish")
    @PreAuthorize(MANAGER)
    public ResponseEntity<KbManageResponse> declinePublish(@PathVariable Long id,
                                                           @Valid @RequestBody(required = false) KbDeclineRequest request) {
        return ResponseEntity.ok(kb.declinePublish(currentUser.get(), id, request == null ? null : request.note()));
    }

    @PostMapping("/articles/{id}/archive")
    @PreAuthorize(MANAGER)
    public ResponseEntity<KbManageResponse> archive(@PathVariable Long id) {
        return ResponseEntity.ok(kb.archive(currentUser.get(), id));
    }

    @PostMapping("/articles/{id}/unarchive")
    @PreAuthorize(MANAGER)
    public ResponseEntity<KbManageResponse> unarchive(@PathVariable Long id) {
        return ResponseEntity.ok(kb.unarchive(currentUser.get(), id));
    }

    @DeleteMapping("/articles/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        kb.delete(currentUser.get(), id);
        return ResponseEntity.noContent().build();
    }

    // ── Versions ─────────────────────────────────────────────────────────────

    @GetMapping("/articles/{id}/versions")
    public ResponseEntity<List<KbVersionResponse>> versions(@PathVariable Long id) {
        return ResponseEntity.ok(kb.versions(currentUser.get(), id));
    }

    @GetMapping("/articles/{id}/versions/{number}")
    public ResponseEntity<KbVersionResponse> version(@PathVariable Long id, @PathVariable int number) {
        return ResponseEntity.ok(kb.version(currentUser.get(), id, number));
    }

    @PostMapping("/articles/{id}/versions/{number}/restore")
    public ResponseEntity<KbManageResponse> restore(@PathVariable Long id, @PathVariable int number) {
        return ResponseEntity.ok(kb.restoreVersion(currentUser.get(), id, number));
    }

    /** Tickets this article is linked to, limited to the ones the caller can open. */
    @GetMapping("/articles/{id}/tickets")
    public ResponseEntity<List<KbLinkedTicketResponse>> tickets(@PathVariable Long id) {
        return ResponseEntity.ok(ticketLinks.ticketsFor(currentUser.get(), id));
    }

    // ── Images and files ─────────────────────────────────────────────────────

    @GetMapping("/articles/{id}/attachments")
    public ResponseEntity<List<KbAttachmentResponse>> attachments(@PathVariable Long id) {
        return ResponseEntity.ok(kb.attachments(currentUser.get(), id));
    }

    @PostMapping(value = "/articles/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KbAttachmentResponse> upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kb.upload(currentUser.get(), id, file));
    }

    @DeleteMapping("/articles/{id}/attachments/{mediaId}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id, @PathVariable String mediaId) {
        kb.deleteAttachment(currentUser.get(), id, mediaId);
        return ResponseEntity.noContent().build();
    }

    // ── Categories ───────────────────────────────────────────────────────────

    @GetMapping("/categories")
    public ResponseEntity<List<KbCategoryResponse>> categories() {
        return ResponseEntity.ok(kb.categories(currentUser.get()));
    }

    @PostMapping("/categories")
    @PreAuthorize(MANAGER)
    public ResponseEntity<KbCategoryResponse> createCategory(@Valid @RequestBody KbCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kb.createCategory(currentUser.get(), request));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(MANAGER)
    public ResponseEntity<KbCategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody KbCategoryRequest request) {
        return ResponseEntity.ok(kb.updateCategory(currentUser.get(), id, request));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize(MANAGER)
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        kb.deleteCategory(currentUser.get(), id);
        return ResponseEntity.noContent().build();
    }
}
