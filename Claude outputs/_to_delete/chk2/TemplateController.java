package com.konohahelpdesk.controller.template;

import com.konohahelpdesk.dto.template.TemplateRequest;
import com.konohahelpdesk.dto.template.TemplateResponse;
import com.konohahelpdesk.model.template.TemplateKind;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.template.TemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ticket templates and canned replies.
 *
 * Split access on purpose, and NOT with a class-level annotation: the two read endpoints at the
 * bottom are open to any signed-in user because customers pick templates too, while everything
 * that writes is admin / MANAGE_TEMPLATES. The scoping of what a customer may see is decided
 * inside {@code TemplateService.usableFor} from their own identity, never from a query param.
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templates;
    private final CurrentUser currentUser;

    public TemplateController(TemplateService templates, CurrentUser currentUser) {
        this.templates = templates;
        this.currentUser = currentUser;
    }

    // ── Management: admin / MANAGE_TEMPLATES ─────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_TEMPLATES')")
    public ResponseEntity<List<TemplateResponse>> list() {
        return ResponseEntity.ok(templates.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_TEMPLATES')")
    public ResponseEntity<TemplateResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(templates.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_TEMPLATES')")
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templates.create(request, currentUser.get()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_TEMPLATES')")
    public ResponseEntity<TemplateResponse> update(@PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(templates.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('PRIV_MANAGE_TEMPLATES')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        templates.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ── Picker: any signed-in user, scoped to who they are ───────────────────

    /**
     * The templates the caller may pick from. {@code companyId} is a hint for staff choosing a
     * company on the new-ticket form; a customer's own company is used regardless of what they send.
     */
    @GetMapping("/usable")
    public ResponseEntity<List<TemplateResponse>> usable(
        @RequestParam(defaultValue = "TICKET") TemplateKind kind,
        @RequestParam(required = false) Long companyId
    ) {
        return ResponseEntity.ok(templates.usableFor(currentUser.get(), kind, companyId));
    }

    /** Records that a template was picked. Fire-and-forget from the client. */
    @PostMapping("/{id}/used")
    public ResponseEntity<Void> markUsed(@PathVariable Long id) {
        templates.markUsed(id, currentUser.get());
        return ResponseEntity.accepted().build();
    }
}
