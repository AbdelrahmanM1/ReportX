package me.abdoabk.reportx.model;

public enum ReportStatus {
    OPEN,
    CLAIMED,
    RESOLVED,
    REJECTED,
    ESCALATED;

    public String getDisplayName() {
        return switch (this) {
            case OPEN -> "OPEN";
            case CLAIMED -> "CLAIMED";
            case RESOLVED -> "RESOLVED";
            case REJECTED -> "REJECTED";
            case ESCALATED -> "ESCALATED";
        };
    }

    public static ReportStatus fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
