package com.example.wristbandapp;

/**
 * Unified model for the dashboard list – can represent either a saved location
 * or a saved alarm.
 */
public class DashboardItem {
    public static final int TYPE_LOCATION = 0;
    public static final int TYPE_ALARM    = 1;

    public final int    type;
    public final int    id;
    public final String name;       // location name  OR  alarm label

    // -- Location fields (TYPE_LOCATION only) --
    public double latitude;
    public double longitude;
    public float  radiusMeters;

    // -- Alarm fields (TYPE_ALARM only) --
    public int    hour;
    public int    minute;
    public String repeatDays; // e.g. "Mon, Wed, Fri"
    public String vibration;  // "Low" | "Medium" | "High"

    // -- Live Weather Status (for locations) --
    public String weatherStatus;

    /** Construct a location item */
    public DashboardItem(int id, String name, double latitude, double longitude, float radiusMeters) {
        this.type         = TYPE_LOCATION;
        this.id           = id;
        this.name         = name;
        this.latitude     = latitude;
        this.longitude    = longitude;
        this.radiusMeters = radiusMeters;
    }

    /** Construct an alarm item */
    public DashboardItem(int id, String name, int hour, int minute, String repeatDays, String vibration) {
        this.type        = TYPE_ALARM;
        this.id          = id;
        this.name        = name;
        this.hour        = hour;
        this.minute      = minute;
        this.repeatDays  = repeatDays;
        this.vibration   = vibration;
    }

    /** Convenience: formatted 12-hour time string for display */
    public String getFormattedTime() {
        String ampm   = hour < 12 ? "AM" : "PM";
        int displayH  = hour % 12;
        if (displayH == 0) displayH = 12;
        return String.format("%d:%02d %s", displayH, minute, ampm);
    }
}
