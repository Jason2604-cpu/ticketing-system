package com.konohahelpdesk.mail;

import com.konohahelpdesk.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Entry point for transactional email. Builds links and defers delivery to
 * {@link MailDispatcher} (async) — and, when called inside a database
 * transaction, only after that transaction commits, so a rolled-back user
 * creation never emails a link to a token that doesn't exist.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final AppProperties.Mail cfg;
    private final MailDispatcher dispatcher;

    public MailService(AppProperties props, MailDispatcher dispatcher) {
        this.cfg = props.getMail();
        this.dispatcher = dispatcher;
        if (!cfg.isEnabled()) {
            log.warn("Email sending is DISABLED (app.mail.enabled=false) — emails will be printed to the log.");
        }
    }

    public void send(String to, String subject, String text) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.deliver(to, subject, text);
                }
            });
        } else {
            dispatcher.deliver(to, subject, text);
        }
    }

    public String activationLink(String rawToken) {
        return cfg.getFrontendBaseUrl() + cfg.getActivatePath() + "?token=" + rawToken;
    }

    public String resetLink(String rawToken) {
        return cfg.getFrontendBaseUrl() + cfg.getResetPasswordPath() + "?token=" + rawToken;
    }

    public String ticketLink(Long ticketId) {
        return cfg.getFrontendBaseUrl() + "/tickets/" + ticketId;
    }

    public String appName() {
        return cfg.getAppName();
    }
}
