package com.konohahelpdesk.dto.user;

import com.konohahelpdesk.model.user.User;

/** Request + response shape for a user's own notification settings. */
public class NotificationPreferences {
    private boolean email = true;
    private boolean browser = false;
    private boolean onAssign = true;
    private boolean onReply = true;
    private boolean onResolve = true;

    public static NotificationPreferences from(User u) {
        NotificationPreferences p = new NotificationPreferences();
        p.email = u.isNotifyEmail();
        p.browser = u.isNotifyBrowser();
        p.onAssign = u.isNotifyOnAssign();
        p.onReply = u.isNotifyOnReply();
        p.onResolve = u.isNotifyOnResolve();
        return p;
    }

    public void applyTo(User u) {
        u.setNotifyEmail(email);
        u.setNotifyBrowser(browser);
        u.setNotifyOnAssign(onAssign);
        u.setNotifyOnReply(onReply);
        u.setNotifyOnResolve(onResolve);
    }

    public boolean isEmail() { return email; }
    public void setEmail(boolean email) { this.email = email; }
    public boolean isBrowser() { return browser; }
    public void setBrowser(boolean browser) { this.browser = browser; }
    public boolean isOnAssign() { return onAssign; }
    public void setOnAssign(boolean onAssign) { this.onAssign = onAssign; }
    public boolean isOnReply() { return onReply; }
    public void setOnReply(boolean onReply) { this.onReply = onReply; }
    public boolean isOnResolve() { return onResolve; }
    public void setOnResolve(boolean onResolve) { this.onResolve = onResolve; }
}
