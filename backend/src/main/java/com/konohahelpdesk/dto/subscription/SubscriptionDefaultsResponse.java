package com.konohahelpdesk.dto.subscription;

import com.konohahelpdesk.model.plan.Plan;

import java.util.List;

/** Pre-fill values for the "Custom Plan" form + caps for client-side validation. */
public class SubscriptionDefaultsResponse {
    private LimitsDto customDefaults;
    private List<SlaDto> customDefaultSla;
    private LimitsDto caps;
    private Integer slaResponseMinutesCap;
    private Integer slaResolutionMinutesCap;

    public LimitsDto getCustomDefaults() { return customDefaults; }
    public void setCustomDefaults(LimitsDto customDefaults) { this.customDefaults = customDefaults; }
    public List<SlaDto> getCustomDefaultSla() { return customDefaultSla; }
    public void setCustomDefaultSla(List<SlaDto> customDefaultSla) { this.customDefaultSla = customDefaultSla; }
    public LimitsDto getCaps() { return caps; }
    public void setCaps(LimitsDto caps) { this.caps = caps; }
    public Integer getSlaResponseMinutesCap() { return slaResponseMinutesCap; }
    public void setSlaResponseMinutesCap(Integer slaResponseMinutesCap) { this.slaResponseMinutesCap = slaResponseMinutesCap; }
    public Integer getSlaResolutionMinutesCap() { return slaResolutionMinutesCap; }
    public void setSlaResolutionMinutesCap(Integer slaResolutionMinutesCap) { this.slaResolutionMinutesCap = slaResolutionMinutesCap; }
}
