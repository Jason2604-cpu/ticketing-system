package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.ticket.TicketStatus;

import java.time.LocalDateTime;

/** A ticket an article is linked to, for the article's "Linked tickets" list (staff). */
public record KbLinkedTicketResponse(Long id, String title, TicketStatus status, String companyName, LocalDateTime linkedAt) {}
