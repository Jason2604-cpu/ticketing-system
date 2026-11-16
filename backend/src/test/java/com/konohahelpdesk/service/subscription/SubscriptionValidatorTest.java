package com.konohahelpdesk.service.subscription;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.subscription.LimitsDto;
import com.konohahelpdesk.dto.subscription.SlaDto;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.model.plan.SlaPriority;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionValidatorTest {

    private final SubscriptionValidator validator = new SubscriptionValidator(new AppProperties());

    @Test
    void limitsAboveCapsAreRejected() {
        LimitsDto tooMany = new LimitsDto(1_000_000, 10, 10L);
        assertThrows(BusinessRuleException.class, () -> validator.validateLimits(tooMany));
        assertDoesNotThrow(() -> validator.validateLimits(new LimitsDto(50, 1000, 5120L)));
    }

    @Test
    void missingPrioritiesAreFilledFromDefaults() {
        Map<SlaPriority, SlaDto> sla = validator.validateAndComplete(List.of(new SlaDto(SlaPriority.CRITICAL, 30, 360)));
        assertEquals(SlaPriority.values().length, sla.size());
        assertEquals(30, sla.get(SlaPriority.CRITICAL).getResponseTimeMinutes());
    }

    @Test
    void duplicateAndInvertedSlaRowsAreRejected() {
        assertThrows(BusinessRuleException.class, () -> validator.validateAndComplete(List.of(
            new SlaDto(SlaPriority.HIGH, 60, 120), new SlaDto(SlaPriority.HIGH, 60, 120))));
        assertThrows(BusinessRuleException.class, () -> validator.validateAndComplete(List.of(
            new SlaDto(SlaPriority.NORMAL, 600, 60))));
    }
}
