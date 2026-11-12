package com.s23plus.camera;

/**
 * Clasificación de salud física y accesibilidad para cada sensor de cámara.
 */
public enum SensorHealth {

    HEALTHY("🟢 Operativo", "Sensor responde y produce cuadros correctamente."),
    DAMAGED("🔴 Dañado / Error I2C", "Avería física en sensor o EEPROM inaccesible."),
    HAL_PRIVATE("🔒 Modo Pro Requerido", "Sensor trasero desacoplado del grupo lógico; accesible vía Pro/QR."),
    UNKNOWN("⚪ Desconocido", "Sensor sin probar o con respuesta indeterminada.");

    public final String displayName;
    public final String description;

    SensorHealth(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
