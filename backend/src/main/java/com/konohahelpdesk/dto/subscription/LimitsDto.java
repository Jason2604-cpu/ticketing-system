package com.konohahelpdesk.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Quotas. Upper bounds are enforced against app.subscription.caps.* in the service. */
public class LimitsDto {

    @NotNull @Min(1) private Integer maxUsers;
    @NotNull @Min(1) private Integer monthlyTicketLimit;
    @NotNull @Min(1) private Long storageLimitMb;

    public LimitsDto() {}

    public LimitsDto(Integer maxUsers, Integer monthlyTicketLimit, Long storageLimitMb) {
        this.maxUsers = maxUsers;
        this.monthlyTicketLimit = monthlyTicketLimit;
        this.storageLimitMb = storageLimitMb;
    }

    public Integer getMaxUsers() { return maxUsers; }
    public void setMaxUsers(Integer maxUsers) { this.maxUsers = maxUsers; }
    public Integer getMonthlyTicketLimit() { return monthlyTicketLimit; }
    public void setMonthlyTicketLimit(Integer monthlyTicketLimit) { this.monthlyTicketLimit = monthlyTicketLimit; }
    public Long getStorageLimitMb() { return storageLimitMb; }
    public void setStorageLimitMb(Long storageLimitMb) { this.storageLimitMb = storageLimitMb; }
}
