package com.konohahelpdesk.mail;

import com.konohahelpdesk.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Actually sends mail over SMTP (spring.mail.* from .env) when app.mail.enabled=true;
 * otherwise prints the message to the log so the flow works locally without SMTP.
 *
 * Runs @Async so a slow mail server never blocks an API response. Failures are
 * logged, not surfaced — the token stays valid and an admin can resend.
 */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);

    private final AppProperties.Mail cfg;
    private final ObjectProvider<JavaMailSender> senderProvider;

    public MailDispatcher(AppProperties props, ObjectProvider<JavaMailSender> senderProvider) {
        this.cfg = props.getMail();
        this.senderProvider = senderProvider;
        if (cfg.isEnabled() && senderProvider.getIfAvailable() == null) {
            throw new IllegalStateException("app.mail.enabled=true but no SMTP host is configured (MAIL_HOST in .env)");
        }
    }

    @Async
    public void deliver(String to, String subject, String text) {
        if (!cfg.isEnabled()) {
            log.info("\n===== EMAIL (not sent — app.mail.enabled=false) =====\nTo: {}\nSubject: {}\n\n{}\n=====================================================",
                to, subject, text);
            return;
        }
        try {
            JavaMailSender sender = senderProvider.getObject();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(cfg.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            sender.send(message);
            log.info("Email sent to {} subject='{}'", to, subject);
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send email to {} subject='{}': {}", to, subject, e.getMessage());
        }
    }

    /**
     * Same as {@link #deliver} but as a multipart message: mail clients that render HTML show
     * {@code html}, the rest fall back to {@code text}. Used for reports with tables.
     */
    @Async
    public void deliverHtml(String to, String subject, String html, String text) {
        if (!cfg.isEnabled()) {
            log.info("\n===== EMAIL (not sent — app.mail.enabled=false) =====\nTo: {}\nSubject: {}\n(HTML message; plain-text part below)\n\n{}\n=====================================================",
                to, subject, text);
            return;
        }
        try {
            JavaMailSender sender = senderProvider.getObject();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(cfg.getFrom());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
            log.info("HTML email sent to {} subject='{}'", to, subject);
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send HTML email to {} subject='{}': {}", to, subject, e.getMessage());
        }
    }
}
