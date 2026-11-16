-- Sessions signed out before their token expired (see TokenRevocationService).
-- Rows are removed once the token would have expired anyway.
CREATE TABLE revoked_tokens (
    token_hash character varying(64) NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT revoked_tokens_pkey PRIMARY KEY (token_hash)
);

CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens USING btree (expires_at);
