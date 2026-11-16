package com.konohahelpdesk.model.media;

/** What a stored file belongs to. Also the sub-folder under app.media.base-path. */
public enum MediaKind {
    USER_PHOTO,
    COMPANY_LOGO,
    TICKET_ATTACHMENT,
    /** A signed contract, quotation or scope document attached to a ServiceContract. */
    CONTRACT_ATTACHMENT,
    /**
     * An image or file in a Knowledge Base article (owner = article id). Belongs to the provider,
     * not a customer, so it is not counted against any company's storage quota.
     */
    KB_ATTACHMENT
}
