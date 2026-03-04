package com.jobcrawler.adapter.model;

public enum RemoteType {
    REMOTE,
    HYBRID,
    ON_SITE;

    public static RemoteType fromRaw(String raw) {
        if (raw == null) return null;
        String normalized = raw.toLowerCase().trim();
        if (normalized.contains("full remote") || normalized.contains("fully remote")
                || normalized.equals("remote") || normalized.contains("100% remote")) {
            return REMOTE;
        }
        if (normalized.contains("hybrid") || normalized.contains("part remote")) {
            return HYBRID;
        }
        if (normalized.contains("on-site") || normalized.contains("onsite")
                || normalized.contains("in-office") || normalized.contains("office")) {
            return ON_SITE;
        }
        return null;
    }
}
