package com.s23plus.camera;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Funciones puras para determinar elegibilidad, saneamiento y persistencia de sensores seleccionados.
 */
public final class SensorSelectionState {

    private SensorSelectionState() {
        // Clase de utilidad no instanciable
    }

    /**
     * Indica si un sensor descubierto es apto para ser seleccionado por el usuario.
     */
    public static boolean isSelectable(DiscoveredSensor sensor) {
        return sensor != null
                && (sensor.health == SensorHealth.HEALTHY || sensor.health == SensorHealth.HAL_PRIVATE);
    }

    /**
     * Retorna los IDs seleccionados por defecto (todos los sensores elegibles con ID válido, en orden descubierto).
     */
    public static Set<String> defaultSelectedIds(List<DiscoveredSensor> discovered) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (discovered == null) {
            return result;
        }
        for (DiscoveredSensor sensor : discovered) {
            if (isSelectableWithValidId(sensor)) {
                result.add(sensor.id);
            }
        }
        return result;
    }

    /**
     * Depura una selección cruda contra los sensores descubiertos actualmente.
     */
    public static Set<String> sanitizeSelection(Collection<String> rawSelectedIds, List<DiscoveredSensor> discovered) {
        if (discovered == null || discovered.isEmpty()) {
            return new LinkedHashSet<>();
        }
        if (rawSelectedIds == null) {
            return defaultSelectedIds(discovered);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (DiscoveredSensor sensor : discovered) {
            if (isSelectableWithValidId(sensor) && rawSelectedIds.contains(sensor.id)) {
                result.add(sensor.id);
            }
        }
        return result;
    }

    /**
     * Serializa un conjunto de IDs en una cadena ordenada, recortada y separada por comas.
     */
    public static String serializeSelectedIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String id : ids) {
            if (id != null) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    sorted.add(trimmed);
                }
            }
        }
        return String.join(",", sorted);
    }

    /**
     * Deserializa una cadena separada por comas en un conjunto ordenado de inserción sin tokens vacíos.
     */
    public static Set<String> deserializeSelectedIds(String serialized) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (serialized == null || serialized.trim().isEmpty()) {
            return result;
        }
        String[] tokens = serialized.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static boolean isSelectableWithValidId(DiscoveredSensor sensor) {
        return isSelectable(sensor) && sensor.id != null && !sensor.id.trim().isEmpty();
    }
}
