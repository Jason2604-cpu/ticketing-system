package com.konohahelpdesk.dto.kb;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.kb.KbCategory;
import com.konohahelpdesk.model.user.User;

/** Small reference shapes shared by the KB responses. */
public final class KbRefs {

    private KbRefs() {}

    public record CategoryRef(Long id, String name, String icon) {
        public static CategoryRef of(KbCategory c) {
            return c == null ? null : new CategoryRef(c.getId(), c.getName(), c.getIcon());
        }
    }

    public record CompanyRef(Long id, String name) {
        public static CompanyRef of(Company c) {
            return new CompanyRef(c.getId(), c.getName());
        }
    }

    public record UserRef(Long id, String name) {
        public static UserRef of(User u) {
            if (u == null) return null;
            String name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                         + (u.getLastName() == null ? "" : u.getLastName())).trim();
            return new UserRef(u.getId(), name.isEmpty() ? u.getEmail() : name);
        }
    }
}
