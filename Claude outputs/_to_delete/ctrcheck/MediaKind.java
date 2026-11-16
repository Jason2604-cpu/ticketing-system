package com.konohahelpdesk.model.media;

/** What a stored file belongs to. Also the sub-folder under app.media.base-path. */
public enum MediaKind {
    USER_PHOTO,
    COMPANY_LOGO,
    TICKET_ATTACHMENT,
    /** A signed contract, quotation or scope document attached to a ServiceContract. */
    CONTRACT_ATTACHMENT
}
