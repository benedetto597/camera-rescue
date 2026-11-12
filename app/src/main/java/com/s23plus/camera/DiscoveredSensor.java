package com.s23plus.camera;

import java.util.Locale;

/**
 * Ficha descriptiva inmutable de un sensor descubierto y diagnosticado.
 */
public final class DiscoveredSensor {

    public final String id;
    public final String name;
    public final int facing; // 0 = BACK, 1 = FRONT
    public final int width;
    public final int height;
    public final float focalLengthMm;
    public final float apertureF;
    public final SensorHealth health;
    public final String statusDetail;

    public DiscoveredSensor(
            String id,
            String name,
            int facing,
            int width,
            int height,
            float focalLengthMm,
            float apertureF,
            SensorHealth health,
            String statusDetail
    ) {
        this.id = id;
        this.name = name;
        this.facing = facing;
        this.width = width;
        this.height = height;
        this.focalLengthMm = focalLengthMm;
        this.apertureF = apertureF;
        this.health = health != null ? health : SensorHealth.UNKNOWN;
        this.statusDetail = statusDetail != null ? statusDetail : "";
    }

    public String getResolutionMegaPixels() {
        if (width <= 0 || height <= 0) {
            return "N/D";
        }
        double mp = ((double) width * height) / 1_000_000.0;
        return String.format(Locale.US, "%.1f MP", mp);
    }

    public String getFormattedSummary() {
        String direction = (facing == 1) ? "Frontal" : "Trasera";
        return String.format(Locale.US, "ID %s · %s [%s] · %s",
                id, name != null ? name : direction, getResolutionMegaPixels(), health.displayName);
    }
}
