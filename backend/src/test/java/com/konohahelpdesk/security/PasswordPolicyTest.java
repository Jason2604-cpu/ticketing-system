package com.konohahelpdesk.security;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(new AppProperties()); // defaults: 10+, U/l/d/special

    @Test
    void acceptsCompliantPassword() {
        assertDoesNotThrow(() -> policy.validate("Str0ng!Passw0rd"));
    }

    @Test
    void rejectsShortPassword() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> policy.validate("Sh0rt!"));
        assertTrue(ex.getMessage().contains("at least 10"));
    }

    @Test
    void rejectsMissingClasses() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> policy.validate("alllowercase1234"));
        assertTrue(ex.getMessage().contains("uppercase"));
        assertTrue(ex.getMessage().contains("special"));
    }

    @Test
    void generatedTemporaryPasswordsAlwaysSatisfyPolicy() {
        for (int i = 0; i < 200; i++) {
            String p = com.konohahelpdesk.util.generator.PasswordGenerator.generate(14);
            assertDoesNotThrow(() -> policy.validate(p), "generated password failed policy: " + p);
        }
    }
}
