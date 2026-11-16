package com.konohahelpdesk.dto.subscription;

import com.konohahelpdesk.model.plan.Feature;

public class FeatureResponse {
    private Long id;
    private String code;
    private String name;
    private Boolean enabled; // null when listing the catalog; set in plan/company views

    public static FeatureResponse of(Feature f, Boolean enabled) {
        FeatureResponse r = new FeatureResponse();
        r.id = f.getId();
        r.code = f.getFeatureCode();
        r.name = f.getFeatureName();
        r.enabled = enabled;
        return r;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Boolean getEnabled() { return enabled; }
}
