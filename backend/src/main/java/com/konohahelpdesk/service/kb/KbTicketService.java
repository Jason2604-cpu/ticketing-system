package com.konohahelpdesk.service.kb;

import com.konohahelpdesk.dto.kb.KbLinkedTicketResponse;
import com.konohahelpdesk.dto.kb.KbRefs;
import com.konohahelpdesk.dto.kb.KbTicketArticleResponse;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.exception.ResourceNotFoundException;
import com.konohahelpdesk.model.kb.KbArticle;
import com.konohahelpdesk.model.kb.KbArticleVersion;
import com.konohahelpdesk.model.kb.KbStatus;
import com.konohahelpdesk.model.kb.KbTicketLink;
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.kb.KbArticleRepository;
import com.konohahelpdesk.repository.kb.KbTicketLinkRepository;
import com.konohahelpdesk.repository.ticket.TicketRepository;
import com.konohahelpdesk.security.AuditLogger;
import com.konohahelpdesk.service.ticket.TicketService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Articles ↔ tickets. Staff link an article to a ticket (from the reply box, the Related articles
 * card, or by writing an article from a resolved ticket); everyone who can see the ticket sees the
 * links they could open anyway.
 *
 * The rule a customer must never see broken: a linked article is only listed for them when they
 * could read it — published, Public, applies to their company, and their plan includes it. Staff
 * get {@code visibleToCustomer} so the UI can warn before a link goes into a public reply.
 */
@Service
public class KbTicketService {

    private final KbTicketLinkRepository links;
    private final KbArticleRepository articles;
    private final TicketRepository tickets;
    private final TicketService ticketService;
    private final KbAccess access;
    private final AuditLogger audit;

    public KbTicketService(KbTicketLinkRepository links, KbArticleRepository articles, TicketRepository tickets,
                           TicketService ticketService, KbAccess access, AuditLogger audit) {
        this.links = links;
        this.articles = articles;
        this.tickets = tickets;
        this.ticketService = ticketService;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<KbTicketArticleResponse> forTicket(User actor, Long ticketId) {
        Ticket ticket = loadTicket(actor, ticketId);
        List<KbTicketLink> list = links.findByTicket(ticketId);
        if (list.isEmpty()) return List.of();

        Map<Long, KbArticle> byId = articles.findAllById(list.stream().map(KbTicketLink::getArticleId).toList())
            .stream().collect(Collectors.toMap(KbArticle::getId, Function.identity()));
        boolean staff = actor.getRole().worksTickets();
        KbViewer me = access.viewer(actor);
        KbViewer customer = staff ? access.customerViewerFor(ticket.getCompany().getId()) : me;

        return list.stream().map(l -> {
            KbArticle a = byId.get(l.getArticleId());
            if (a == null) return null;
            KbArticleVersion pub = a.getPublishedVersion();
            boolean live = a.getStatus() == KbStatus.PUBLISHED && pub != null;
            boolean customerCan = live && KbRules.canReadPublished(customer, pub.getVisibility(), pub.companyIdList());
            if (!staff) {
                return customerCan ? new KbTicketArticleResponse(a.getId(), a.getSlug(), pub.getTitle(), pub.getSummary(),
                    null, null, null, null, null) : null;
            }
            // Staff see a link only to what they could open themselves (an engineer's scope still applies).
            if (!canSee(me, a)) return null;
            return new KbTicketArticleResponse(a.getId(), a.getSlug(),
                live ? pub.getTitle() : a.getTitle(), live ? pub.getSummary() : a.getSummary(),
                a.getStatus(), live ? pub.getVisibility() : a.getVisibility(), customerCan,
                KbRefs.UserRef.of(l.getLinkedBy()), l.getLinkedAt());
        }).filter(Objects::nonNull).toList();
    }

    @Transactional
    public List<KbTicketArticleResponse> link(User actor, Long ticketId, Long articleId) {
        requireStaff(actor);
        loadTicket(actor, ticketId);
        KbArticle a = articles.findById(articleId).orElseThrow(() -> KbAccess.notFound(articleId));
        if (!canSee(access.viewer(actor), a)) throw KbAccess.notFound(articleId);
        if (a.getStatus() == KbStatus.ARCHIVED) throw new BusinessRuleException("Archived articles can't be linked");

        KbTicketLink.Key key = new KbTicketLink.Key(ticketId, articleId);
        if (!links.existsById(key)) {
            links.save(new KbTicketLink(ticketId, articleId, actor));
            audit.event("KB_ARTICLE_LINKED", "ticketId=" + ticketId + " articleId=" + articleId + " title=" + a.getTitle());
        }
        return forTicket(actor, ticketId);
    }

    @Transactional
    public List<KbTicketArticleResponse> unlink(User actor, Long ticketId, Long articleId) {
        requireStaff(actor);
        loadTicket(actor, ticketId);
        KbTicketLink.Key key = new KbTicketLink.Key(ticketId, articleId);
        if (links.existsById(key)) {
            links.deleteById(key);
            audit.event("KB_ARTICLE_UNLINKED", "ticketId=" + ticketId + " articleId=" + articleId);
        }
        return forTicket(actor, ticketId);
    }

    /** The tickets an article is linked to that this writer can open — the "Linked tickets" list. */
    @Transactional(readOnly = true)
    public List<KbLinkedTicketResponse> ticketsFor(User actor, Long articleId) {
        KbViewer v = access.viewer(actor);
        KbArticle a = articles.findById(articleId).orElseThrow(() -> KbAccess.notFound(articleId));
        access.requireEditable(v, a);
        List<KbTicketLink> list = links.findByArticle(articleId);
        if (list.isEmpty() || !actor.getRole().worksTickets()) return List.of();
        Map<Long, Ticket> byId = tickets.findAllById(list.stream().map(KbTicketLink::getTicketId).toList())
            .stream().collect(Collectors.toMap(Ticket::getId, Function.identity()));
        return list.stream()
            .filter(l -> byId.containsKey(l.getTicketId()) && ticketService.canAccess(actor, l.getTicketId()))
            .map(l -> {
                Ticket t = byId.get(l.getTicketId());
                return new KbLinkedTicketResponse(t.getId(), t.getTitle(), t.getStatus(), t.getCompany().getName(), l.getLinkedAt());
            })
            .toList();
    }

    /** Published and readable, or a working copy this writer can edit. */
    private static boolean canSee(KbViewer v, KbArticle a) {
        KbArticleVersion pub = a.getPublishedVersion();
        boolean readable = a.getStatus() == KbStatus.PUBLISHED && pub != null
            && KbRules.canReadPublished(v, pub.getVisibility(), pub.companyIdList());
        return readable || KbRules.canEdit(v, a.companyIds());
    }

    private Ticket loadTicket(User actor, Long ticketId) {
        return tickets.findById(ticketId)
            .filter(t -> ticketService.canAccess(actor, ticketId))
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private static void requireStaff(User actor) {
        if (!actor.getRole().worksTickets()) throw new AccessDeniedException("Forbidden");
    }
}
