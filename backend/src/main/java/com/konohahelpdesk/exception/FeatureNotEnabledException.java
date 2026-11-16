package com.konohahelpdesk.exception;

/** The company's subscription does not include the requested feature (HTTP 403). */
public class FeatureNotEnabledException extends RuntimeException {
    private final String featureCode;

    public FeatureNotEnabledException(String featureCode) {
        super("This feature is not included in your subscription: " + featureCode);
        this.featureCode = featureCode;
    }

    public String getFeatureCode() {
        return featureCode;
    }
}
