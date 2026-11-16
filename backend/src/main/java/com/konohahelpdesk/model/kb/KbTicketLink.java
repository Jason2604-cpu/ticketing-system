package com.konohahelpdesk.model.kb;

import com.konohahelpdesk.model.user.User;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * "This ticket is about that article" — set by staff from a ticket's reply box or Related
 * articles card, or when an article is written from a resolved ticket. Customers see the link only
 * when they could read the article anyway.
 */
@Entity
@Table(name = "kb_ticket_articles")
public class KbTicketLink {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "ticket_id", nullable = false)
        private Long ticketId;

        @Column(name = "article_id", nullable = false)
        private Long articleId;

        protected Key() {}

        public Key(Long ticketId, Long articleId) {
            this.ticketId = ticketId;
            this.articleId = articleId;
        }

        public Long getTicketId() { return ticketId; }
        public Long getArticleId() { return articleId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(ticketId, k.ticketId) && Objects.equals(articleId, k.articleId);
        }

        @Override
        public int hashCode() { return Objects.hash(ticketId, articleId); }
    }

    @EmbeddedId
    private Key id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_by")
    private User linkedBy;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    protected KbTicketLink() {}

    public KbTicketLink(Long ticketId, Long articleId, User linkedBy) {
        this.id = new Key(ticketId, articleId);
        this.linkedBy = linkedBy;
        this.linkedAt = LocalDateTime.now();
    }

    public Key getId() { return id; }
    public Long getTicketId() { return id.getTicketId(); }
    public Long getArticleId() { return id.getArticleId(); }
    public User getLinkedBy() { return linkedBy; }
    public LocalDateTime getLinkedAt() { return linkedAt; }
}
