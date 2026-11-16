package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Enforces app.security.password.* from app-config.properties. */
@Component
public class PasswordPolicy {

    private final AppProperties.Password cfg;

    public PasswordPolicy(AppProperties props) {
        this.cfg = props.getSecurity().getPassword();
    }

    /** @throws BusinessRuleException listing every unmet rule */
    public void validate(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null) password = "";

        if (password.length() < cfg.getMinLength()) problems.add("at least " + cfg.getMinLength() + " characters");
        if (password.length() > cfg.getMaxLength()) problems.add("at most " + cfg.getMaxLength() + " characters");
        if (cfg.isRequireUppercase() && password.chars().noneMatch(Character::isUpperCase)) problems.add("an uppercase letter");
        if (cfg.isRequireLowercase() && password.chars().noneMatch(Character::isLowerCase)) problems.add("a lowercase letter");
        if (cfg.isRequireDigit() && password.chars().noneMatch(Character::isDigit)) problems.add("a digit");
        if (cfg.isRequireSpecial() && password.chars().allMatch(Character::isLetterOrDigit)) problems.add("a special character");

        if (!problems.isEmpty()) {
            throw new BusinessRuleException("Password must contain " + String.join(", ", problems));
        }
    }
}
