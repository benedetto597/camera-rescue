package com.s23plus.camera;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pruebas unitarias para SensorSelectionState y RescueActionSpec.
 */
public final class SensorSelectionStateTest {

    public static void main(String[] args) {
        testIsSelectable();
        testDefaultSelectedIds();
        testSanitizeSelection();
        testSerializeAndDeserializeRoundTrip();
        testRescueActionSpecConstructionAndValidation();
        System.out.println("PASS: Todas las pruebas unitarias de SensorSelectionStateTest finalizaron con exito (5/5).");
    }

    private static List<DiscoveredSensor> sampleSensors() {
        return Arrays.asList(
                new DiscoveredSensor("56", "Trasera Principal 1x", 0, 4080, 3060, 5.4f, 1.8f, SensorHealth.HAL_PRIVATE, "HAL privado"),
                new DiscoveredSensor("58", "Ultra-Wide 0.6x", 0, 4000, 3000, 2.2f, 2.2f, SensorHealth.DAMAGED, "EEPROM error"),
                new DiscoveredSensor("1", "Frontal Wide", 1, 4000, 3000, 3.3f, 2.2f, SensorHealth.HEALTHY, "OK"),
                new DiscoveredSensor("99", "Sensor Indeterminado", 0, 0, 0, 0f, 0f, SensorHealth.UNKNOWN, "Sin respuesta")
        );
    }

    private static void testIsSelectable() {
        DiscoveredSensor healthy = new DiscoveredSensor("1", "Front", 1, 4000, 3000, 3.3f, 2.2f, SensorHealth.HEALTHY, "OK");
        DiscoveredSensor halPrivate = new DiscoveredSensor("56", "Rear", 0, 4080, 3060, 5.4f, 1.8f, SensorHealth.HAL_PRIVATE, "Pro");
        DiscoveredSensor damaged = new DiscoveredSensor("58", "UW", 0, 4000, 3000, 2.2f, 2.2f, SensorHealth.DAMAGED, "Fail");
        DiscoveredSensor unknown = new DiscoveredSensor("99", "Unk", 0, 0, 0, 0f, 0f, SensorHealth.UNKNOWN, "?");

        assertTrue(SensorSelectionState.isSelectable(healthy), "HEALTHY debe ser seleccionable");
        assertTrue(SensorSelectionState.isSelectable(halPrivate), "HAL_PRIVATE debe ser seleccionable");
        assertFalse(SensorSelectionState.isSelectable(damaged), "DAMAGED no debe ser seleccionable");
        assertFalse(SensorSelectionState.isSelectable(unknown), "UNKNOWN no debe ser seleccionable");
        assertFalse(SensorSelectionState.isSelectable(null), "null no debe ser seleccionable");
    }

    private static void testDefaultSelectedIds() {
        Set<String> defaults = SensorSelectionState.defaultSelectedIds(sampleSensors());
        assertEquals(new LinkedHashSet<>(Arrays.asList("56", "1")), defaults,
                "defaultSelectedIds debe incluir solo HEALTHY y HAL_PRIVATE en orden");

        Set<String> emptyFromNull = SensorSelectionState.defaultSelectedIds(null);
        assertTrue(emptyFromNull.isEmpty(), "defaultSelectedIds(null) debe retornar conjunto vacio");

        List<DiscoveredSensor> withBlankId = Arrays.asList(
                new DiscoveredSensor(" ", "Blank", 1, 100, 100, 1f, 2f, SensorHealth.HEALTHY, "OK"),
                new DiscoveredSensor(null, "NullId", 1, 100, 100, 1f, 2f, SensorHealth.HEALTHY, "OK")
        );
        assertTrue(SensorSelectionState.defaultSelectedIds(withBlankId).isEmpty(),
                "defaultSelectedIds debe ignorar sensores con id nulo o en blanco");
    }

    private static void testSanitizeSelection() {
        List<DiscoveredSensor> discovered = sampleSensors();

        // Incluye dañado "58", inexistente "777", y orden invertido ("1", "56") -> debe filtrar y preservar orden descubierto ("56", "1")
        Set<String> sanitized = SensorSelectionState.sanitizeSelection(
                Arrays.asList("1", "58", "777", "99", "56"),
                discovered
        );
        assertEquals(Arrays.asList("56", "1"), Arrays.asList(sanitized.toArray(new String[0])),
                "sanitizeSelection debe descartar dañados/desconocidos y preservar orden descubierto");

        // Seleccion vacia explicita debe preservarse vacia
        Set<String> explicitEmpty = SensorSelectionState.sanitizeSelection(Collections.emptySet(), discovered);
        assertTrue(explicitEmpty.isEmpty(), "sanitizeSelection debe preservar seleccion vacia explicita");

        // rawSelectedIds == null -> fallback a defaultSelectedIds
        Set<String> defaulted = SensorSelectionState.sanitizeSelection(null, discovered);
        assertEquals(new LinkedHashSet<>(Arrays.asList("56", "1")), defaulted,
                "sanitizeSelection(null, discovered) debe retornar defaultSelectedIds");

        // discovered vacio o null -> vacio
        assertTrue(SensorSelectionState.sanitizeSelection(Arrays.asList("56"), Collections.emptyList()).isEmpty(),
                "sanitizeSelection con discovered vacio debe retornar vacio");
        assertTrue(SensorSelectionState.sanitizeSelection(null, null).isEmpty(),
                "sanitizeSelection con discovered null debe retornar vacio");
    }

    private static void testSerializeAndDeserializeRoundTrip() {
        Set<String> input = new LinkedHashSet<>(Arrays.asList(" 56 ", "1", "", "52", "1"));
        String serialized = SensorSelectionState.serializeSelectedIds(input);
        assertEquals("1,52,56", serialized, "serializeSelectedIds debe ordenar de forma determinista, recortar y unir por coma");

        Set<String> deserialized = SensorSelectionState.deserializeSelectedIds(serialized);
        assertEquals(new LinkedHashSet<>(Arrays.asList("1", "52", "56")), deserialized,
                "deserializeSelectedIds debe reconstruir el conjunto");

        assertEquals("", SensorSelectionState.serializeSelectedIds(null), "serialize null debe retornar vacio");
        assertEquals("", SensorSelectionState.serializeSelectedIds(Collections.emptySet()), "serialize vacio debe retornar vacio");

        assertTrue(SensorSelectionState.deserializeSelectedIds(null).isEmpty(), "deserialize null debe retornar vacio");
        assertTrue(SensorSelectionState.deserializeSelectedIds("   ").isEmpty(), "deserialize blank debe retornar vacio");
        assertEquals(new LinkedHashSet<>(Arrays.asList("56", "3")),
                SensorSelectionState.deserializeSelectedIds(" 56 , , 3 , "),
                "deserializeSelectedIds debe ignorar tokens vacios y hacer trim");
    }

    private static void testRescueActionSpecConstructionAndValidation() {
        RescueActionSpec rearPro = new RescueActionSpec(
                "rear_pro_56",
                RescueActionSpec.ActionType.REAR_PRO_PHOTO,
                "56",
                "Camara Trasera Pro",
                "Sensor Principal 1x",
                "#1565C0",
                "#42A5F5",
                null,
                null,
                null,
                CameraConfig.ACTION_LAUNCH_REAR_PRO
        );

        assertEquals("rear_pro_56", rearPro.id, "id incorrecto");
        assertEquals(RescueActionSpec.ActionType.REAR_PRO_PHOTO, rearPro.actionType, "actionType incorrecto");
        assertEquals("56", rearPro.sensorId, "sensorId incorrecto");
        assertEquals("", rearPro.technicalDescription, "technicalDescription null debe normalizarse a vacio");
        assertEquals("", rearPro.shortcutShortLabel, "shortcutShortLabel null debe normalizarse a vacio");
        assertEquals("", rearPro.shortcutLongLabel, "shortcutLongLabel null debe normalizarse a vacio");
        assertTrue(rearPro.isRearAction(), "REAR_PRO_PHOTO debe ser rear action");

        RescueActionSpec front = new RescueActionSpec(
                "front_1",
                RescueActionSpec.ActionType.FRONT_CAMERA,
                null,
                "Tomar Selfie",
                null,
                null,
                null,
                "Desc",
                "Selfie",
                "Tomar Selfie",
                CameraConfig.ACTION_LAUNCH_FRONT
        );
        assertEquals("", front.sensorId, "sensorId null debe normalizarse a vacio");
        assertEquals("", front.subtitle, "subtitle null debe normalizarse a vacio");
        assertEquals("", front.primaryColorHex, "primaryColorHex null debe normalizarse a vacio");
        assertEquals("", front.accentColorHex, "accentColorHex null debe normalizarse a vacio");
        assertFalse(front.isRearAction(), "FRONT_CAMERA no debe ser rear action");

        assertThrowsIllegalArgument(() -> new RescueActionSpec(
                " ", RescueActionSpec.ActionType.FRONT_CAMERA, "1", "Title", "", "", "", "", "", "", "act"
        ), "id blank debe lanzar IllegalArgumentException");
        assertThrowsIllegalArgument(() -> new RescueActionSpec(
                "id1", null, "1", "Title", "", "", "", "", "", "", "act"
        ), "actionType null debe lanzar IllegalArgumentException");
        assertThrowsIllegalArgument(() -> new RescueActionSpec(
                "id1", RescueActionSpec.ActionType.FRONT_CAMERA, "1", null, "", "", "", "", "", "", "act"
        ), "title null debe lanzar IllegalArgumentException");
        assertThrowsIllegalArgument(() -> new RescueActionSpec(
                "id1", RescueActionSpec.ActionType.FRONT_CAMERA, "1", "Title", "", "", "", "", "", "", "  "
        ), "intentAction blank debe lanzar IllegalArgumentException");
    }

    private static void assertThrowsIllegalArgument(Runnable action, String message) {
        boolean threw = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " | Esperado: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
