package com.konohahelpdesk.service.subscription;

import com.konohahelpdesk.config.AppProperties;
import com.konohahelpdesk.dto.subscription.LimitsDto;
import com.konohahelpdesk.dto.subscription.SlaDto;
import com.konohahelpdesk.exception.BusinessRuleException;
import com.konohahelpdesk.model.plan.SlaPriority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Business-rule validation for limits and SLA input, using the caps and defaults
 * from app-config.properties. Bean Validation handles shape; this handles bounds.
 */
@Component
public class SubscriptionValidator {

    private final AppProperties.Caps caps;
    private final AppProperties.Subscription subCfg;

    public SubscriptionValidator(AppProperties props) {
        this.caps = props.getSubscription().getCaps();
        this.subCfg = props.getSubscription();
    }

    public void validateLimits(LimitsDto limits) {
        if (limits == null) {
            throw new BusinessRuleException("limits are required");
        }
        List<String> problems = new ArrayList<>();
        if (limits.getMaxUsers() > caps.getMaxUsers())
            problems.add("maxUsers exceeds the maximum of " + caps.getMaxUsers());
        if (limits.getMonthlyTicketLimit() > caps.getMonthlyTicketLimit())
            problems.add("monthlyTicketLimit exceeds the maximum of " + caps.getMonthlyTicketLimit());
        if (limits.getStorageLimitMb() > caps.getStorageLimitMb())
            problems.add("storageLimitMb exceeds the maximum of " + caps.getStorageLimitMb());
        if (!problems.isEmpty()) {
            throw new BusinessRuleException(String.join("; ", problems));
        }
    }

    /**
     * Validates the supplied SLA rows and fills any missing priority from the configured
     * custom defaults, so every company/plan always has a complete CRITICAL/HIGH/NORMAL set.
     */
    public Map<SlaPriority, SlaDto> validateAndComplete(List<SlaDto> sla) {
        Map<SlaPriority, SlaDto> result = new EnumMap<>(SlaPriority.class);
        if (sla != null) {
            for (SlaDto row : sla) {
                if (row.getPriority() == null) {
                    throw new BusinessRuleException("SLA priority is required");
                }
                if (result.containsKey(row.getPriority())) {
                    throw new BusinessRuleException("Duplicate SLA entry for priority " + row.getPriority());
                }
                if (row.getResponseTimeMinutes() > caps.getSlaResponseMinutes())
                    throw new BusinessRuleException(row.getPriority() + " response time exceeds the maximum of "
                        + caps.getSlaResponseMinutes() + " minutes");
                if (row.getResolutionTimeMinutes() > caps.getSlaResolutionMinutes())
                    throw new BusinessRuleException(row.getPriority() + " resolution time exceeds the maximum of "
                        + caps.getSlaResolutionMinutes() + " minutes");
                if (row.getResolutionTimeMinutes() < row.getResponseTimeMinutes())
                    throw new BusinessRuleException(row.getPriority() + " resolution time cannot be shorter than its response time");
                result.put(row.getPriority(), row);
            }
        }
        for (SlaPriority p : SlaPriority.values()) {
            if (!result.containsKey(p)) {
                AppProperties.Sla d = subCfg.getCustomDefaultSla().get(p);
                int response = d != null ? d.getResponseMinutes() : 60;
                int resolution = d != null ? d.getResolutionMinutes() : 480;
                result.put(p, new SlaDto(p, response, resolution));
            }
        }
        return result;
    }

    public LimitsDto customDefaultLimits() {
        AppProperties.Limits d = subCfg.getCustomDefaults();
        return new LimitsDto(d.getMaxUsers(), d.getMonthlyTicketLimit(), d.getStorageLimitMb());
    }

    public List<SlaDto> customDefaultSla() {
        return new ArrayList<>(validateAndComplete(null).values());
    }

    public AppProperties.Caps caps() {
        return caps;
    }
}
