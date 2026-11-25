package com.s23plus.camera;

/**
 * Especificación inmutable de una acción de rescate generada dinámicamente a partir de los sensores seleccionados.
 */
public final class RescueActionSpec {

    public enum ActionType {
        FRONT_CAMERA,
        REAR_PRO_PHOTO,
        REAR_QR_HUD,
        REAR_PRO_VIDEO
    }

    public final String id;
    public final ActionType actionType;
    public final String sensorId;
    public final String title;
    public final String subtitle;
    public final String primaryColorHex;
    public final String accentColorHex;
    public final String technicalDescription;
    public final String shortcutShortLabel;
    public final String shortcutLongLabel;
    public final String intentAction;

    public RescueActionSpec(
            String id,
            ActionType actionType,
            String sensorId,
            String title,
            String subtitle,
            String primaryColorHex,
            String accentColorHex,
            String technicalDescription,
            String shortcutShortLabel,
            String shortcutLongLabel,
            String intentAction
    ) {
        this.id = requireNonBlank(id, "id");
        if (actionType == null) {
            throw new IllegalArgumentException("actionType no puede ser nulo.");
        }
        this.actionType = actionType;
        this.sensorId = normalizeOptional(sensorId);
        this.title = requireNonBlank(title, "title");
        this.subtitle = normalizeOptional(subtitle);
        this.primaryColorHex = normalizeOptional(primaryColorHex);
        this.accentColorHex = normalizeOptional(accentColorHex);
        this.technicalDescription = normalizeOptional(technicalDescription);
        this.shortcutShortLabel = normalizeOptional(shortcutShortLabel);
        this.shortcutLongLabel = normalizeOptional(shortcutLongLabel);
        this.intentAction = requireNonBlank(intentAction, "intentAction");
    }

    public boolean isRearAction() {
        return actionType != ActionType.FRONT_CAMERA;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " no puede ser nulo ni estar en blanco.");
        }
        return value;
    }

    private static String normalizeOptional(String value) {
        return value != null ? value : "";
    }
}
