package com.konohahelpdesk.repository.kb;

import com.konohahelpdesk.model.kb.KbTicketLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbTicketLinkRepository extends JpaRepository<KbTicketLink, KbTicketLink.Key> {

    @Query("select l from KbTicketLink l where l.id.ticketId = :ticketId order by l.linkedAt")
    List<KbTicketLink> findByTicket(@Param("ticketId") Long ticketId);

    @Query("select l from KbTicketLink l where l.id.articleId = :articleId order by l.linkedAt desc")
    List<KbTicketLink> findByArticle(@Param("articleId") Long articleId);
}
