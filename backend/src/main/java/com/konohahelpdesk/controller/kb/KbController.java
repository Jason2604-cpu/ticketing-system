package com.konohahelpdesk.controller.kb;

import com.konohahelpdesk.dto.kb.KbArticleResponse;
import com.konohahelpdesk.dto.kb.KbArticleSummary;
import com.konohahelpdesk.dto.kb.KbFeedbackRequest;
import com.konohahelpdesk.dto.kb.KbCategoryResponse;
import com.konohahelpdesk.dto.kb.KbPage;
import com.konohahelpdesk.security.CurrentUser;
import com.konohahelpdesk.service.kb.KbReadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reading the Knowledge Base: published articles only. Staff read on /kb, customers on /help.
 *
 * The annotation only says who may knock. What each caller actually sees — Internal vs Public,
 * which companies, whether a customer's plan includes KNOWLEDGE_BASE — is decided in
 * {@code KbReadService} from the caller's identity, never from a parameter.
 */
@RestController
@RequestMapping("/api/kb")
@PreAuthorize("hasAnyRole('ADMIN','ENGINEER','CUSTOMER_ADMIN','CUSTOMER') or hasAuthority('PRIV_MANAGE_KNOWLEDGE_BASE')")
public class KbController {

    private final KbReadService reader;
    private final CurrentUser currentUser;

    public KbController(KbReadService reader, CurrentUser currentUser) {
        this.reader = reader;
        this.currentUser = currentUser;
    }

    /**
     * Recently updated first, or with {@code sort=popular} most viewed first. With {@code q}, a
     * full-text search instead — best match first, with excerpts — and {@code sort} is ignored.
     */
    @GetMapping("/articles")
    public ResponseEntity<KbPage<KbArticleSummary>> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        boolean popular = "popular".equalsIgnoreCase(sort);
        return ResponseEntity.ok(reader.list(currentUser.get(), q, categoryId, popular, page, size));
    }

    /**
     * Type-ahead for the new-ticket forms: up to five articles. {@code companyId} is honoured for
     * staff only. A customer whose plan lacks the Knowledge Base gets an empty list, not an error.
     */
    @GetMapping("/suggest")
    public ResponseEntity<List<KbArticleSummary>> suggest(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long companyId
    ) {
        return ResponseEntity.ok(reader.suggest(currentUser.get(), q, companyId));
    }

    @GetMapping("/articles/{slug}")
    public ResponseEntity<KbArticleResponse> get(@PathVariable String slug) {
        return ResponseEntity.ok(reader.get(currentUser.get(), slug));
    }

    /** Suggestion opened / "this solved it" from the ticket form. Audit only. */
    @PostMapping("/articles/{slug}/feedback")
    public ResponseEntity<Void> feedback(@PathVariable String slug, @Valid @RequestBody KbFeedbackRequest request) {
        reader.feedback(currentUser.get(), slug, request.event());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/categories")
    public ResponseEntity<List<KbCategoryResponse>> categories() {
        return ResponseEntity.ok(reader.categories(currentUser.get()));
    }
}
