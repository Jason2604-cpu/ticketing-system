package com.konohahelpdesk.dto.audit;

import java.util.List;

/**
 * A page of audit rows plus what the viewer needs to render its controls: the total count, and
 * the distinct action names actually present so the filter dropdown only offers events that
 * exist rather than the full enum of everything the app could ever emit.
 */
public class AuditPageResponse {

    private List<AuditEventResponse> events;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
    private List<String> availableActions;

    public AuditPageResponse(List<AuditEventResponse> events, long totalElements, int totalPages,
                             int page, int size, List<String> availableActions) {
        this.events = events;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.page = page;
        this.size = size;
        this.availableActions = availableActions;
    }

    public List<AuditEventResponse> getEvents() { return events; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public List<String> getAvailableActions() { return availableActions; }
}
