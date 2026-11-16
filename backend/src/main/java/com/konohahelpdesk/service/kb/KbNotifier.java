package com.konohahelpdesk.service.kb;

import com.konohahelpdesk.mail.MailService;
import com.konohahelpdesk.model.kb.KbArticle;
import com.konohahelpdesk.model.kb.KbVisibility;
import com.konohahelpdesk.model.notification.NotificationType;
import com.konohahelpdesk.model.rbac.Privilege;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.service.notification.NotificationService;
import org.springframework.stereotype.Component;

/**
 * Tells people about publish requests (plan §8.3):
 *  - Request publish   → every Admin and Knowledge Base manager (KB_PUBLISH_REQUESTED), opening the draft;
 *  - published         → the engineer who asked (KB_PUBLISH_DECIDED), opening the live article;
 *  - declined          → the engineer who asked (KB_PUBLISH_DECIDED), opening their draft — and the
 *                        same when a manager archives or deletes it while the request is waiting.
 *
 * A bell notification always; an email too for people who keep email notifications on. Mail is
 * sent after the transaction commits (MailService), so a rolled-back publish never emails anyone.
 * Nobody is told about their own action.
 */
@Component
public class KbNotifier {

    private final NotificationService notifications;
    private final MailService mail;
    private final UserRepository users;

    public KbNotifier(NotificationService notifications, MailService mail, UserRepository users) {
        this.notifications = notifications;
        this.mail = mail;
        this.users = users;
    }

    public void publishRequested(KbArticle a, User requester) {
        String link = editLink(a);
        String kind = a.getVisibility() == KbVisibility.PUBLIC ? "Public" : "Internal";
        String what = a.everPublished()
            ? "changes to the " + kind + " article \"" + a.getTitle() + "\""
            : "the new " + kind + " article \"" + a.getTitle() + "\"";
        String body = name(requester) + " asked for " + what + " to be published.";

        for (User manager : users.findActiveHolders(Privilege.MANAGE_KNOWLEDGE_BASE)) {
            if (same(manager, requester)) continue;
            notifications.notifyLink(manager, NotificationType.KB_PUBLISH_REQUESTED,
                "Publish requested: " + a.getTitle(), body, link);
            email(manager, "Publish requested — " + a.getTitle(),
                "Hi " + first(manager) + ",\n\n" + body + "\n\n"
                + "Review it, then publish or decline:\n" + mail.appLink(link) + "\n");
        }
    }

    public void published(KbArticle a, User requester, User manager, int version) {
        if (!stillHere(requester) || same(requester, manager)) return;
        String link = "/kb/" + a.getSlug();
        String body = name(manager) + " published \"" + a.getTitle() + "\" — readers now see version " + version + ".";
        notifications.notifyLink(requester, NotificationType.KB_PUBLISH_DECIDED, "Published: " + a.getTitle(), body, link);
        email(requester, "Published — " + a.getTitle(),
            "Hi " + first(requester) + ",\n\n" + body + "\n\nRead it here:\n" + mail.appLink(link) + "\n");
    }

    /** How a waiting request ended without a publish. */
    public enum Outcome { DECLINED, ARCHIVED, DELETED }

    /** Declined — or archived / deleted while the request was waiting: either way it wasn't published. */
    public void notPublished(KbArticle a, User requester, User manager, String note, Outcome how) {
        if (!stillHere(requester) || same(requester, manager)) return;
        // A deleted draft has nowhere left to open.
        String link = how == Outcome.DELETED ? null : editLink(a);
        String body = switch (how) {
            case ARCHIVED -> name(manager) + " archived \"" + a.getTitle() + "\" instead of publishing it.";
            case DELETED -> name(manager) + " deleted the draft \"" + a.getTitle() + "\" instead of publishing it.";
            case DECLINED -> name(manager) + " didn't publish \"" + a.getTitle() + "\""
                + (note == null ? ". Your draft is unchanged." : ": " + note);
        };
        notifications.notifyLink(requester, NotificationType.KB_PUBLISH_DECIDED, "Not published: " + a.getTitle(), body, link);
        email(requester, "Not published — " + a.getTitle(),
            "Hi " + first(requester) + ",\n\n" + body + "\n"
            + (link == null ? "" : "\nYour draft:\n" + mail.appLink(link) + "\n"));
    }

    private void email(User to, String subject, String text) {
        if (!to.isNotifyEmail() || to.getEmail() == null || to.getEmail().isBlank()) return;
        mail.send(to.getEmail(), "[Knowledge Base] " + subject + " — " + mail.appName(), text);
    }

    private static String editLink(KbArticle a) {
        return "/admin/kb/" + a.getId() + "/edit";
    }

    private static boolean stillHere(User u) {
        return u != null && u.getStatus() == UserStatus.ACTIVE;
    }

    private static boolean same(User a, User b) {
        return a != null && b != null && a.getId().equals(b.getId());
    }

    private static String name(User u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                  + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isEmpty() ? "Someone" : n;
    }

    private static String first(User u) {
        return u.getFirstName() == null || u.getFirstName().isBlank() ? "there" : u.getFirstName();
    }
}
