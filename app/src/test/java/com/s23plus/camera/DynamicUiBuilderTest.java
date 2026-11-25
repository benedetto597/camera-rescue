package com.s23plus.camera;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pruebas unitarias TDD para DynamicUiBuilder.
 */
public final class DynamicUiBuilderTest {

    public static void main(String[] args) {
        testPreScanS23PlusProfile();
        testPreScanGalaxyA54NoTelephoto();
        testPostScanS23PlusDefaultSelection();
        testPostScanS23PlusOnlyTeleSelected();
        testPostScanS23PlusOnlyMainSelected();
        testPostScanAllRearDamagedOrUnchecked();
        testPostScanAllFrontUnchecked();
        testPostScanPublicRearFallbackAndNullProfile();
        testSelectablePublicViewerAndRearSensors();
        System.out.println("PASS: Todas las pruebas unitarias de DynamicUiBuilderTest finalizaron con exito (9/9).");
    }

    private static List<DiscoveredSensor> s23PlusScanResults() {
        return Arrays.asList(
                new DiscoveredSensor("0", "Trasera Multi-Lente", 0, 4000, 3000, 5.4f, 1.8f, SensorHealth.DAMAGED, "Timeout HAL"),
                new DiscoveredSensor("1", "Frontal Gran Angular", 1, 4000, 3000, 3.3f, 2.2f, SensorHealth.HEALTHY, "OK"),
                new DiscoveredSensor("2", "Ultra-Wide 0.6x", 0, 4000, 3000, 2.2f, 2.2f, SensorHealth.DAMAGED, "EEPROM error"),
                new DiscoveredSensor("3", "Frontal Normal", 1, 3392, 2544, 3.3f, 2.2f, SensorHealth.HEALTHY, "OK"),
                new DiscoveredSensor("56", "Trasera Principal 1x", 0, 4080, 3060, 5.4f, 1.8f, SensorHealth.HAL_PRIVATE, "Pro / QR"),
                new DiscoveredSensor("52", "Trasera Telefoto 3x", 0, 4080, 3060, 14.0f, 2.4f, SensorHealth.HAL_PRIVATE, "Pro")
        );
    }

    private static void testPreScanS23PlusProfile() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        List<RescueActionSpec> rearSpecs = DynamicUiBuilder.buildRearActionSpecs(s23Plus, null, null);

        assertEquals(3, rearSpecs.size(), "Pre-scan S23+ debe generar 3 tarjetas traseras");
        assertEquals(RescueActionSpec.ActionType.REAR_PRO_PHOTO, rearSpecs.get(0).actionType, "Primer spec debe ser REAR_PRO_PHOTO");
        assertEquals(RescueActionSpec.ActionType.REAR_QR_DIRECT, rearSpecs.get(1).actionType, "Segundo spec debe ser REAR_QR_DIRECT");
        assertEquals(RescueActionSpec.ActionType.REAR_PRO_VIDEO, rearSpecs.get(2).actionType, "Tercer spec debe ser REAR_PRO_VIDEO");

        assertEquals("56", rearSpecs.get(0).sensorId, "REAR_PRO_PHOTO sensorId debe ser 56");
        assertContains(rearSpecs.get(0).subtitle, "56", "Subtitle Pro debe mencionar ID 56");
        assertContains(rearSpecs.get(0).subtitle, "52", "Subtitle Pro debe mencionar ID 52");
        assertContains(rearSpecs.get(0).technicalDescription, "56", "Desc tecnica Pro debe mencionar ID 56");
        assertContains(rearSpecs.get(0).technicalDescription, "52", "Desc tecnica Pro debe mencionar ID 52");

        assertEquals("rear_qr_direct", rearSpecs.get(1).id, "ID de QR directo debe ser rear_qr_direct");
        assertEquals("Aislamiento Directo 1x · Sin Accesibilidad (ID 56)", rearSpecs.get(1).subtitle,
                "Subtitle QR directo debe coincidir");
        assertEquals("QR 1x", rearSpecs.get(1).shortcutShortLabel, "Shortcut short label QR debe ser QR 1x");
        assertEquals("Abrir Escáner QR 1x", rearSpecs.get(1).shortcutLongLabel,
                "Shortcut long label QR debe ser Abrir Escáner QR 1x");
        assertContains(rearSpecs.get(1).technicalDescription, "SCAN_QR_CODE",
                "Desc tecnica QR debe mencionar SCAN_QR_CODE");
        assertContains(rearSpecs.get(1).technicalDescription, "Pictures/RescueCam",
                "Desc tecnica QR debe mencionar Pictures/RescueCam");
        assertNotContains(rearSpecs.get(1).technicalDescription, "RearCaptureService",
                "Desc tecnica QR no debe mencionar RearCaptureService");
        assertContains(rearSpecs.get(2).subtitle, "Tele", "Subtitle Video Pro debe mencionar Tele");
        assertContains(rearSpecs.get(2).technicalDescription, "56", "Desc tecnica Video Pro debe mencionar ID 56");
        assertContains(rearSpecs.get(2).technicalDescription, "52", "Desc tecnica Video Pro debe mencionar ID 52");

        assertTrue(DynamicUiBuilder.getSelectableFrontSensors(null, null).isEmpty(),
                "Pre-scan getSelectableFrontSensors debe retornar lista vacia");

        List<RescueActionSpec> shortcuts = DynamicUiBuilder.buildShortcutSpecs(s23Plus, Collections.emptyList(), null);
        assertEquals(4, shortcuts.size(), "Pre-scan S23+ debe generar 4 accesos directos");
        assertEquals(RescueActionSpec.ActionType.FRONT_CAMERA, shortcuts.get(3).actionType, "Cuarto shortcut debe ser FRONT_CAMERA");
        assertEquals("1", shortcuts.get(3).sensorId, "Shortcut frontal pre-scan debe usar ID 1");
        assertEquals(CameraConfig.ACTION_LAUNCH_FRONT, shortcuts.get(3).intentAction, "Intent action frontal debe coincidir");
        assertUnmodifiable(shortcuts);
    }

    private static void testPreScanGalaxyA54NoTelephoto() {
        SamsungModelDatabase.ModelProfile a54 = SamsungModelDatabase.lookup("SM-A546B");
        assertTrue(a54.rearTeleStandaloneId == null, "A54 no debe tener telefoto");

        List<RescueActionSpec> rearSpecs = DynamicUiBuilder.buildRearActionSpecs(a54, null, null);
        assertEquals(3, rearSpecs.size(), "Pre-scan A54 debe generar 3 tarjetas traseras");

        for (RescueActionSpec spec : rearSpecs) {
            assertNotContainsIgnoreCase(spec.subtitle, "tele", "A54 subtitle no debe mencionar Tele");
            assertNotContains(spec.subtitle, "52", "A54 subtitle no debe mencionar 52");
            assertNotContainsIgnoreCase(spec.subtitle, "3x", "A54 subtitle no debe mencionar 3x");
            assertNotContainsIgnoreCase(spec.technicalDescription, "tele", "A54 desc tecnica no debe mencionar Tele");
            assertNotContains(spec.technicalDescription, "52", "A54 desc tecnica no debe mencionar 52");
            assertNotContainsIgnoreCase(spec.technicalDescription, "3x", "A54 desc tecnica no debe mencionar 3x");
        }
        assertContains(rearSpecs.get(0).subtitle, "56", "A54 subtitle Pro debe mencionar ID 56");
    }

    private static void testPostScanS23PlusDefaultSelection() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        List<DiscoveredSensor> discovered = s23PlusScanResults();

        List<RescueActionSpec> rearSpecs = DynamicUiBuilder.buildRearActionSpecs(s23Plus, discovered, null);
        assertEquals(3, rearSpecs.size(), "Post-scan S23+ completo debe generar 3 tarjetas traseras");
        assertEquals("56", rearSpecs.get(0).sensorId, "Sensor principal debe ser 56");
        assertContains(rearSpecs.get(0).subtitle, "56", "Subtitle Pro post-scan debe mencionar 56");
        assertContains(rearSpecs.get(0).subtitle, "52", "Subtitle Pro post-scan debe mencionar 52");
        assertContains(rearSpecs.get(1).subtitle, "56", "Subtitle QR post-scan debe mencionar 56");
        assertContains(rearSpecs.get(2).subtitle, "56", "Subtitle Video post-scan debe mencionar 56");
        assertContains(rearSpecs.get(2).subtitle, "52", "Subtitle Video post-scan debe mencionar 52");

        List<DiscoveredSensor> frontSensors = DynamicUiBuilder.getSelectableFrontSensors(discovered, null);
        assertEquals(2, frontSensors.size(), "S23+ debe reportar 2 sensores frontales seleccionables");
        assertEquals("1", frontSensors.get(0).id, "Primer frontal debe ser ID 1");
        assertEquals("3", frontSensors.get(1).id, "Segundo frontal debe ser ID 3");

        List<RescueActionSpec> shortcuts = DynamicUiBuilder.buildShortcutSpecs(s23Plus, discovered, null);
        assertEquals(4, shortcuts.size(), "S23+ post-scan debe generar 4 accesos directos");
        assertEquals(RescueActionSpec.ActionType.FRONT_CAMERA, shortcuts.get(3).actionType, "Debe incluir acceso directo frontal");
        assertEquals("1", shortcuts.get(3).sensorId, "Shortcut frontal debe usar ID 1");
    }

    private static void testPostScanS23PlusOnlyTeleSelected() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        List<DiscoveredSensor> discovered = s23PlusScanResults();
        Set<String> selected = new HashSet<>(Arrays.asList("52", "1", "3"));

        List<RescueActionSpec> rearSpecs = DynamicUiBuilder.buildRearActionSpecs(s23Plus, discovered, selected);
        assertEquals(2, rearSpecs.size(), "Con solo Tele seleccionado debe omitir REAR_QR_DIRECT y retornar 2 tarjetas");
        assertEquals(RescueActionSpec.ActionType.REAR_PRO_PHOTO, rearSpecs.get(0).actionType, "Primera debe ser REAR_PRO_PHOTO");
        assertEquals(RescueActionSpec.ActionType.REAR_PRO_VIDEO, rearSpecs.get(1).actionType, "Segunda debe ser REAR_PRO_VIDEO");

        for (RescueActionSpec spec : rearSpecs) {
            assertEquals("52", spec.sensorId, "sensorId debe ser 52 cuando solo Tele esta seleccionado");
            assertContains(spec.subtitle, "52", "Subtitle debe mencionar ID 52");
            assertNotContains(spec.subtitle, "56", "Subtitle no debe mencionar ID 56 cuando 56 fue desmarcado");
            assertContains(spec.technicalDescription, "52", "Desc tecnica debe mencionar ID 52");
            assertNotContains(spec.technicalDescription, "56", "Desc tecnica no debe mencionar ID 56 cuando 56 fue desmarcado");
        }

        List<RescueActionSpec> shortcuts = DynamicUiBuilder.buildShortcutSpecs(s23Plus, discovered, selected);
        assertEquals(3, shortcuts.size(), "Con solo Tele + frontales debe generar 3 shortcuts");
        assertEquals(RescueActionSpec.ActionType.FRONT_CAMERA, shortcuts.get(2).actionType, "Tercer shortcut debe ser FRONT_CAMERA");
    }

    private static void testPostScanS23PlusOnlyMainSelected() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        List<DiscoveredSensor> discovered = s23PlusScanResults();
        Set<String> selected = new HashSet<>(Arrays.asList("56", "1"));

        List<RescueActionSpec> rearSpecs = DynamicUiBuilder.buildRearActionSpecs(s23Plus, discovered, selected);
        assertEquals(3, rearSpecs.size(), "Con sensor principal 56 seleccionado debe generar 3 tarjetas incluyendo QR");
        for (RescueActionSpec spec : rearSpecs) {
            assertNotContainsIgnoreCase(spec.subtitle, "tele", "Subtitle no debe mencionar Tele cuando 52 fue desmarcado");
            assertNotContains(spec.subtitle, "52", "Subtitle no debe mencionar 52 cuando 52 fue desmarcado");
            assertNotContainsIgnoreCase(spec.technicalDescription, "tele", "Desc tecnica no debe mencionar Tele cuando 52 fue desmarcado");
            assertNotContains(spec.technicalDescription, "52", "Desc tecnica no debe mencionar 52 cuando 52 fue desmarcado");
        }
        assertContains(rearSpecs.get(0).subtitle, "56", "Subtitle Pro debe mencionar 56");
    }

    private static void testPostScanAllRearDamagedOrUnchecked() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");

        // Caso 1: todos los sensores traseros dañados, frontal sano
        List<DiscoveredSensor> allRearDamaged = Arrays.asList(
                new DiscoveredSensor("0", "Trasera", 0, 0, 0, 0f, 0f, SensorHealth.DAMAGED, "Fallo"),
                new DiscoveredSensor("2", "UW", 0, 0, 0, 0f, 0f, SensorHealth.DAMAGED, "Fallo"),
                new DiscoveredSensor("1", "Frontal", 1, 4000, 3000, 3.3f, 2.2f, SensorHealth.HEALTHY, "OK")
        );
        assertTrue(DynamicUiBuilder.buildRearActionSpecs(s23Plus, allRearDamaged, null).isEmpty(),
                "Todos los traseros dañados debe retornar lista vacia");

        List<RescueActionSpec> shortcutsOnlyFront = DynamicUiBuilder.buildShortcutSpecs(s23Plus, allRearDamaged, null);
        assertEquals(1, shortcutsOnlyFront.size(), "Con traseros dañados y frontal sano debe retornar 1 shortcut frontal");
        assertEquals(RescueActionSpec.ActionType.FRONT_CAMERA, shortcutsOnlyFront.get(0).actionType, "Shortcut debe ser FRONT_CAMERA");

        // Caso 2: traseros sanos pero usuario los desmarco todos
        List<DiscoveredSensor> s23Scan = s23PlusScanResults();
        Set<String> onlyFrontSelected = new HashSet<>(Collections.singletonList("3"));
        assertTrue(DynamicUiBuilder.buildRearActionSpecs(s23Plus, s23Scan, onlyFrontSelected).isEmpty(),
                "Traseros desmarcados debe retornar lista vacia");

        List<RescueActionSpec> shortcutsFront3 = DynamicUiBuilder.buildShortcutSpecs(s23Plus, s23Scan, onlyFrontSelected);
        assertEquals(1, shortcutsFront3.size(), "Solo frontal 3 seleccionado debe retornar 1 shortcut");
        assertEquals("3", shortcutsFront3.get(0).sensorId, "Shortcut frontal debe usar el sensor seleccionado (ID 3)");
    }

    private static void testPostScanAllFrontUnchecked() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        List<DiscoveredSensor> discovered = s23PlusScanResults();
        Set<String> onlyRearSelected = new HashSet<>(Arrays.asList("56", "52"));

        List<DiscoveredSensor> frontSensors = DynamicUiBuilder.getSelectableFrontSensors(discovered, onlyRearSelected);
        assertTrue(frontSensors.isEmpty(), "Con frontales desmarcados getSelectableFrontSensors debe retornar vacio");

        List<RescueActionSpec> shortcuts = DynamicUiBuilder.buildShortcutSpecs(s23Plus, discovered, onlyRearSelected);
        assertEquals(3, shortcuts.size(), "Con frontales desmarcados buildShortcutSpecs debe omitir FRONT_CAMERA");
        for (RescueActionSpec spec : shortcuts) {
            assertTrue(spec.isRearAction(), "Ningun shortcut debe ser FRONT_CAMERA cuando no hay frontales seleccionados");
        }
    }

    private static void testPostScanPublicRearFallbackAndNullProfile() {
        // Simula dispositivo donde rearMainStandaloneId ("56") no está pero la cámara pública trasera "0" está sana
        List<DiscoveredSensor> discovered = Arrays.asList(
                new DiscoveredSensor("0", "Trasera Principal", 0, 4000, 3000, 4.3f, 1.5f, SensorHealth.HEALTHY, "OK"),
                new DiscoveredSensor("1", "Frontal", 1, 3264, 2448, 3.3f, 1.7f, SensorHealth.HEALTHY, "OK")
        );

        List<RescueActionSpec> rearSpecs = DynamicUiBuilder.buildRearActionSpecs(null, discovered, null);
        assertEquals(3, rearSpecs.size(), "Con trasero publico '0' sano debe generar 3 tarjetas");
        assertEquals("0", rearSpecs.get(0).sensorId, "sensorId debe ser '0'");
        assertEquals("0", rearSpecs.get(1).sensorId, "QR sensorId debe ser '0'");
        assertContains(rearSpecs.get(0).subtitle, "0", "Subtitle debe mencionar ID 0");
        assertUnmodifiable(rearSpecs);
    }

    private static void testSelectablePublicViewerAndRearSensors() {
        List<DiscoveredSensor> mixed = Arrays.asList(
                new DiscoveredSensor("0", "Trasera Principal", 0, 4032, 3024, 4.3f, 1.5f, SensorHealth.HEALTHY, "OK"),
                new DiscoveredSensor("2", "Ultra-Wide", 0, 4000, 3000, 2.2f, 2.2f, SensorHealth.DAMAGED, "I2C fail"),
                new DiscoveredSensor("56", "Pro 1x", 0, 4080, 3060, 5.4f, 1.8f, SensorHealth.HAL_PRIVATE, "Privado"),
                new DiscoveredSensor("1", "Frontal Wide", 1, 4000, 3000, 3.3f, 2.2f, SensorHealth.HEALTHY, "OK"),
                new DiscoveredSensor("99", "Desconocido", 0, 0, 0, 0f, 0f, SensorHealth.UNKNOWN, "?")
        );

        assertTrue(DynamicUiBuilder.getSelectablePublicViewerSensors(null, null).isEmpty(),
                "null discovered debe retornar lista vacia");
        assertTrue(DynamicUiBuilder.getSelectablePublicRearSensors(Collections.emptyList(), null).isEmpty(),
                "empty discovered debe retornar lista vacia");

        List<DiscoveredSensor> defaultViewer = DynamicUiBuilder.getSelectablePublicViewerSensors(mixed, null);
        assertEquals(2, defaultViewer.size(), "Solo HEALTHY (0 y 1) deben ser elegibles para visor publico");
        assertEquals("0", defaultViewer.get(0).id, "Primer sensor publico debe ser 0");
        assertEquals("1", defaultViewer.get(1).id, "Segundo sensor publico debe ser 1");
        assertUnmodifiableSensors(defaultViewer);

        List<DiscoveredSensor> defaultRear = DynamicUiBuilder.getSelectablePublicRearSensors(mixed, null);
        assertEquals(1, defaultRear.size(), "Solo el trasero HEALTHY 0 debe aparecer en public rear");
        assertEquals("0", defaultRear.get(0).id, "Public rear debe ser ID 0");
        assertUnmodifiableSensors(defaultRear);

        Set<String> onlyFrontAndHal = new HashSet<>(Arrays.asList("1", "56"));
        List<DiscoveredSensor> filteredViewer = DynamicUiBuilder.getSelectablePublicViewerSensors(mixed, onlyFrontAndHal);
        assertEquals(1, filteredViewer.size(), "HAL_PRIVATE 56 no debe incluirse en visor publico");
        assertEquals("1", filteredViewer.get(0).id, "Solo frontal 1 debe quedar seleccionado");
        assertTrue(DynamicUiBuilder.getSelectablePublicRearSensors(mixed, onlyFrontAndHal).isEmpty(),
                "Public rear debe estar vacio si 0 no fue seleccionado");
    }

    private static void assertUnmodifiableSensors(List<DiscoveredSensor> list) {
        boolean threw = false;
        try {
            list.clear();
        } catch (UnsupportedOperationException expected) {
            threw = true;
        }
        assertTrue(threw, "La lista de sensores devuelta debe ser inmodificable");
    }

    private static void assertUnmodifiable(List<RescueActionSpec> list) {
        boolean threw = false;
        try {
            list.clear();
        } catch (UnsupportedOperationException expected) {
            threw = true;
        }
        assertTrue(threw, "La lista devuelta debe ser inmodificable");
    }

    private static void assertContains(String text, String needle, String message) {
        if (text == null || !text.contains(needle)) {
            throw new AssertionError(message + " | Texto: [" + text + "] no contiene [" + needle + "]");
        }
    }

    private static void assertNotContains(String text, String needle, String message) {
        if (text != null && text.contains(needle)) {
            throw new AssertionError(message + " | Texto: [" + text + "] no debe contener [" + needle + "]");
        }
    }

    private static void assertNotContainsIgnoreCase(String text, String needle, String message) {
        if (text != null && text.toLowerCase().contains(needle.toLowerCase())) {
            throw new AssertionError(message + " | Texto: [" + text + "] no debe contener [" + needle + "]");
        }
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
}
