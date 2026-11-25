package com.s23plus.camera;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pruebas unitarias TDD para OneUiAssistantGuide.
 */
public final class OneUiAssistantGuideTest {

    public static void main(String[] args) {
        testStepConstantsAndEnumValues();
        testBuildStepsOrderAndPathsAcrossAllOneUiGenerations();
        testBuildStepsHardwareNotesAndNullSafety();
        testSanitizeCompletedStepIds();
        testComputeProgress();
        testSerializeAndDeserializeCompletedStepIds();
        System.out.println("PASS: Todas las pruebas unitarias de OneUiAssistantGuideTest finalizaron con exito (6/6).");
    }

    private static void testStepConstantsAndEnumValues() {
        assertEquals("step_open_pro", OneUiAssistantGuide.STEP_OPEN_PRO, "Constante STEP_OPEN_PRO");
        assertEquals("step_settings_to_keep", OneUiAssistantGuide.STEP_SETTINGS_TO_KEEP, "Constante STEP_SETTINGS_TO_KEEP");
        assertEquals("step_pro_auto_mode", OneUiAssistantGuide.STEP_PRO_AUTO_MODE, "Constante STEP_PRO_AUTO_MODE");
        assertEquals("step_side_key_double_press", OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS, "Constante STEP_SIDE_KEY_DOUBLE_PRESS");

        assertEquals(4, OneUiAssistantGuide.StepAction.values().length, "StepAction debe tener 4 valores");
        assertTrue(OneUiAssistantGuide.StepAction.LAUNCH_PRO_MODE != null, "LAUNCH_PRO_MODE requerido");
        assertTrue(OneUiAssistantGuide.StepAction.OPEN_CAMERA_SETTINGS != null, "OPEN_CAMERA_SETTINGS requerido");
        assertTrue(OneUiAssistantGuide.StepAction.OPEN_SIDE_KEY_SETTINGS != null, "OPEN_SIDE_KEY_SETTINGS requerido");
        assertTrue(OneUiAssistantGuide.StepAction.VERIFY_RECOVERY != null, "VERIFY_RECOVERY requerido");
    }

    private static void testBuildStepsOrderAndPathsAcrossAllOneUiGenerations() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");

        for (ShootingModeMap.OneUiGeneration gen : ShootingModeMap.OneUiGeneration.values()) {
            List<OneUiAssistantGuide.AssistantStep> steps = OneUiAssistantGuide.buildSteps(s23Plus, gen);
            assertEquals(4, steps.size(), "Debe haber 4 pasos para " + gen.name());

            assertEquals(OneUiAssistantGuide.STEP_OPEN_PRO, steps.get(0).id, "Paso 1 id");
            assertEquals(1, steps.get(0).stepNumber, "Paso 1 stepNumber");
            assertEquals(OneUiAssistantGuide.StepAction.LAUNCH_PRO_MODE, steps.get(0).action, "Paso 1 action");

            assertEquals(OneUiAssistantGuide.STEP_SETTINGS_TO_KEEP, steps.get(1).id, "Paso 2 id");
            assertEquals(2, steps.get(1).stepNumber, "Paso 2 stepNumber");
            assertEquals(OneUiAssistantGuide.StepAction.OPEN_CAMERA_SETTINGS, steps.get(1).action, "Paso 2 action");
            assertEquals(
                    "Cámara → ⚙️ Ajustes → " + gen.settingsToKeepLabel + " → Modo de cámara (ON)",
                    steps.get(1).menuPathEs,
                    "Paso 2 menuPathEs para " + gen.name()
            );
            assertEquals(
                    "Camera → ⚙️ Settings → Settings to keep → Camera mode (ON)",
                    steps.get(1).menuPathEn,
                    "Paso 2 menuPathEn para " + gen.name()
            );

            assertEquals(OneUiAssistantGuide.STEP_PRO_AUTO_MODE, steps.get(2).id, "Paso 3 id");
            assertEquals(3, steps.get(2).stepNumber, "Paso 3 stepNumber");
            assertEquals(OneUiAssistantGuide.StepAction.LAUNCH_PRO_MODE, steps.get(2).action, "Paso 3 action");

            assertEquals(OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS, steps.get(3).id, "Paso 4 id");
            assertEquals(4, steps.get(3).stepNumber, "Paso 4 stepNumber");
            assertEquals(OneUiAssistantGuide.StepAction.OPEN_SIDE_KEY_SETTINGS, steps.get(3).action, "Paso 4 action");
            assertEquals(
                    "Settings → Advanced features → Side button → Double press → Open app → Samsung Camera Rescue",
                    steps.get(3).menuPathEn,
                    "Paso 4 menuPathEn para " + gen.name()
            );

            for (OneUiAssistantGuide.AssistantStep step : steps) {
                assertNonEmpty(step.title, "title no debe ser vacio en " + step.id);
                assertNonEmpty(step.instruction, "instruction no debe ser vacio en " + step.id);
                assertNonEmpty(step.buttonLabel, "buttonLabel no debe ser vacio en " + step.id);
            }

            boolean threw = false;
            try {
                steps.clear();
            } catch (UnsupportedOperationException expected) {
                threw = true;
            }
            assertTrue(threw, "buildSteps debe retornar lista inmodificable");
        }

        assertEquals(
                "Ajustes → Funciones avanzadas → Tecla lateral (o Inicio rápido) → Pulsar dos veces → Abrir aplicación → Samsung Camera Rescue",
                OneUiAssistantGuide.buildSteps(s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_2).get(3).menuPathEs,
                "Ruta One UI 2 paso 4"
        );
        assertEquals(
                "Ajustes → Funciones avanzadas → Tecla lateral → Pulsar dos veces → Abrir aplicación → Samsung Camera Rescue",
                OneUiAssistantGuide.buildSteps(s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_3).get(3).menuPathEs,
                "Ruta One UI 3 paso 4"
        );
        assertEquals(
                "Ajustes → Funciones avanzadas → Tecla lateral → Pulsar dos veces → Abrir aplicación → Samsung Camera Rescue",
                OneUiAssistantGuide.buildSteps(s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_4).get(3).menuPathEs,
                "Ruta One UI 4 paso 4"
        );
        assertEquals(
                "Ajustes → Funciones avanzadas → Tecla lateral → Pulsar dos veces → Abrir aplicación → Samsung Camera Rescue",
                OneUiAssistantGuide.buildSteps(s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_5).get(3).menuPathEs,
                "Ruta One UI 5 paso 4"
        );
        assertEquals(
                "Ajustes → Funciones avanzadas → Botón lateral → Pulsar dos veces → Abrir aplicación → Samsung Camera Rescue",
                OneUiAssistantGuide.buildSteps(s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_6).get(3).menuPathEs,
                "Ruta One UI 6 paso 4"
        );
        assertEquals(
                "Ajustes → Funciones avanzadas → Botón lateral → Pulsación doble → Abrir aplicación → Samsung Camera Rescue",
                OneUiAssistantGuide.buildSteps(s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_7).get(3).menuPathEs,
                "Ruta One UI 7 paso 4"
        );
        assertEquals(
                "Ajustes → Funciones avanzadas → Botón lateral → Pulsación doble → Abrir aplicación → Samsung Camera Rescue",
                OneUiAssistantGuide.buildSteps(s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_8_PLUS).get(3).menuPathEs,
                "Ruta One UI 8+ paso 4"
        );
    }

    private static void testBuildStepsHardwareNotesAndNullSafety() {
        List<OneUiAssistantGuide.AssistantStep> fallbackSteps = OneUiAssistantGuide.buildSteps(null, null);
        assertEquals(4, fallbackSteps.size(), "buildSteps(null, null) debe retornar 4 pasos");
        assertTrue(fallbackSteps.get(1).menuPathEs.contains("Ajustes para conservar"),
                "oneUi null debe usar ONE_UI_6");
        assertTrue(fallbackSteps.get(0).instruction.contains("56"),
                "profile null debe mencionar sensor principal 56");

        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        List<OneUiAssistantGuide.AssistantStep> s23Steps = OneUiAssistantGuide.buildSteps(
                s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_6
        );
        assertTrue(s23Steps.get(0).instruction.contains("56") && s23Steps.get(0).instruction.contains("52"),
                "S23+ paso 1 debe mencionar IDs 56 y 52");
        assertTrue(s23Steps.get(2).instruction.contains("1x W") && s23Steps.get(2).instruction.contains("Tele"),
                "S23+ paso 3 debe mencionar 1x W y Tele");
        assertFalse(s23Steps.get(2).instruction.contains("f/1.5"),
                "S23+ no debe incluir nota de apertura variable");
        assertFalse(s23Steps.get(2).instruction.toLowerCase().contains("bisagra"),
                "S23+ no debe incluir nota de bisagra plegable");

        SamsungModelDatabase.ModelProfile s25Edge = SamsungModelDatabase.lookup("SM-S937B");
        List<OneUiAssistantGuide.AssistantStep> s25EdgeSteps = OneUiAssistantGuide.buildSteps(
                s25Edge, ShootingModeMap.OneUiGeneration.ONE_UI_7
        );
        assertTrue(s25EdgeSteps.get(0).instruction.contains("56"),
                "S25 Edge paso 1 debe mencionar sensor 56");
        assertTrue(s25EdgeSteps.get(2).instruction.contains("1x W"),
                "S25 Edge paso 3 debe mencionar 1x W");
        assertFalse(s25EdgeSteps.get(2).instruction.contains("Tele"),
                "S25 Edge sin telefoto no debe sugerir lente Tele");

        SamsungModelDatabase.ModelProfile s9 = SamsungModelDatabase.lookup("SM-G960F");
        List<OneUiAssistantGuide.AssistantStep> s9Steps = OneUiAssistantGuide.buildSteps(
                s9, ShootingModeMap.OneUiGeneration.ONE_UI_2
        );
        assertTrue(s9Steps.get(0).instruction.contains("0"),
                "S9 paso 1 debe mencionar sensor principal 0");
        assertTrue(s9Steps.get(2).instruction.contains("f/1.5") && s9Steps.get(2).instruction.contains("f/2.4"),
                "S9 paso 3 debe mencionar apertura dual f/1.5-f/2.4");

        SamsungModelDatabase.ModelProfile zFold6 = SamsungModelDatabase.lookup("SM-F956B");
        List<OneUiAssistantGuide.AssistantStep> foldSteps = OneUiAssistantGuide.buildSteps(
                zFold6, ShootingModeMap.OneUiGeneration.ONE_UI_6
        );
        assertTrue(foldSteps.get(2).instruction.toLowerCase().contains("bisagra")
                        || foldSteps.get(2).instruction.toLowerCase().contains("flex"),
                "Z Fold 6 paso 3 debe incluir nota de bisagra/flex");
    }

    private static void testSanitizeCompletedStepIds() {
        List<OneUiAssistantGuide.AssistantStep> steps = OneUiAssistantGuide.buildSteps(null, null);

        assertTrue(OneUiAssistantGuide.sanitizeCompletedStepIds(null, steps).isEmpty(),
                "rawIds null debe retornar conjunto vacio");
        assertTrue(OneUiAssistantGuide.sanitizeCompletedStepIds(
                Arrays.asList(OneUiAssistantGuide.STEP_OPEN_PRO), null).isEmpty(),
                "steps null debe retornar conjunto vacio");
        assertTrue(OneUiAssistantGuide.sanitizeCompletedStepIds(
                Arrays.asList(OneUiAssistantGuide.STEP_OPEN_PRO), Collections.emptyList()).isEmpty(),
                "steps vacio debe retornar conjunto vacio");

        Set<String> sanitized = OneUiAssistantGuide.sanitizeCompletedStepIds(
                Arrays.asList(
                        OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS,
                        "step_desconocido",
                        null,
                        "  ",
                        OneUiAssistantGuide.STEP_OPEN_PRO,
                        OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS
                ),
                steps
        );
        assertEquals(
                Arrays.asList(OneUiAssistantGuide.STEP_OPEN_PRO, OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS),
                Arrays.asList(sanitized.toArray(new String[0])),
                "sanitizeCompletedStepIds debe filtrar desconocidos y preservar orden de steps"
        );
    }

    private static void testComputeProgress() {
        List<OneUiAssistantGuide.AssistantStep> steps = OneUiAssistantGuide.buildSteps(null, null);

        OneUiAssistantGuide.ProgressSummary zero = OneUiAssistantGuide.computeProgress(steps, null);
        assertEquals(0, zero.completedCount, "0 completados");
        assertEquals(4, zero.totalCount, "4 totales");
        assertEquals(0, zero.percent, "0%");
        assertFalse(zero.allCompleted, "allCompleted false");
        assertEquals(OneUiAssistantGuide.STEP_OPEN_PRO, zero.nextPendingStepId, "Primer pendiente STEP_OPEN_PRO");

        Set<String> partialIds = new LinkedHashSet<>(Arrays.asList(
                OneUiAssistantGuide.STEP_OPEN_PRO,
                OneUiAssistantGuide.STEP_PRO_AUTO_MODE,
                "invalid_step_id"
        ));
        OneUiAssistantGuide.ProgressSummary partial = OneUiAssistantGuide.computeProgress(steps, partialIds);
        assertEquals(2, partial.completedCount, "Solo 2 validos completados");
        assertEquals(4, partial.totalCount, "4 totales");
        assertEquals(50, partial.percent, "50%");
        assertFalse(partial.allCompleted, "allCompleted false en 2/4");
        assertEquals(OneUiAssistantGuide.STEP_SETTINGS_TO_KEEP, partial.nextPendingStepId,
                "Siguiente pendiente debe ser STEP_SETTINGS_TO_KEEP");

        Set<String> allIds = new LinkedHashSet<>(Arrays.asList(
                OneUiAssistantGuide.STEP_OPEN_PRO,
                OneUiAssistantGuide.STEP_SETTINGS_TO_KEEP,
                OneUiAssistantGuide.STEP_PRO_AUTO_MODE,
                OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS
        ));
        OneUiAssistantGuide.ProgressSummary done = OneUiAssistantGuide.computeProgress(steps, allIds);
        assertEquals(4, done.completedCount, "4 completados");
        assertEquals(4, done.totalCount, "4 totales");
        assertEquals(100, done.percent, "100%");
        assertTrue(done.allCompleted, "allCompleted true");
        assertEquals("", done.nextPendingStepId, "nextPendingStepId vacio al completar todos");

        OneUiAssistantGuide.ProgressSummary emptySteps = OneUiAssistantGuide.computeProgress(
                Collections.emptyList(), allIds
        );
        assertEquals(0, emptySteps.completedCount, "empty steps completedCount 0");
        assertEquals(0, emptySteps.totalCount, "empty steps totalCount 0");
        assertEquals(0, emptySteps.percent, "empty steps percent 0");
        assertFalse(emptySteps.allCompleted, "empty steps allCompleted false");
        assertEquals("", emptySteps.nextPendingStepId, "empty steps nextPendingStepId vacio");

        OneUiAssistantGuide.ProgressSummary nullSteps = OneUiAssistantGuide.computeProgress(null, allIds);
        assertEquals(0, nullSteps.totalCount, "null steps totalCount 0");
        assertFalse(nullSteps.allCompleted, "null steps allCompleted false");
    }

    private static void testSerializeAndDeserializeCompletedStepIds() {
        Set<String> input = new LinkedHashSet<>(Arrays.asList(
                " " + OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS + " ",
                OneUiAssistantGuide.STEP_OPEN_PRO,
                "",
                null,
                OneUiAssistantGuide.STEP_OPEN_PRO
        ));
        String serialized = OneUiAssistantGuide.serializeCompletedStepIds(input);
        assertEquals(
                OneUiAssistantGuide.STEP_OPEN_PRO + "," + OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS,
                serialized,
                "serializeCompletedStepIds debe ordenar deterministicamente y limpiar espacios"
        );

        Set<String> deserialized = OneUiAssistantGuide.deserializeCompletedStepIds(serialized);
        assertEquals(
                new LinkedHashSet<>(Arrays.asList(
                        OneUiAssistantGuide.STEP_OPEN_PRO,
                        OneUiAssistantGuide.STEP_SIDE_KEY_DOUBLE_PRESS
                )),
                deserialized,
                "deserializeCompletedStepIds round-trip"
        );

        assertEquals("", OneUiAssistantGuide.serializeCompletedStepIds(null), "serialize null -> vacio");
        assertEquals("", OneUiAssistantGuide.serializeCompletedStepIds(Collections.emptySet()), "serialize empty -> vacio");
        assertTrue(OneUiAssistantGuide.deserializeCompletedStepIds(null).isEmpty(), "deserialize null -> vacio");
        assertTrue(OneUiAssistantGuide.deserializeCompletedStepIds("   ").isEmpty(), "deserialize blank -> vacio");
        assertEquals(
                new LinkedHashSet<>(Arrays.asList(OneUiAssistantGuide.STEP_OPEN_PRO, OneUiAssistantGuide.STEP_PRO_AUTO_MODE)),
                OneUiAssistantGuide.deserializeCompletedStepIds(
                        " " + OneUiAssistantGuide.STEP_OPEN_PRO + " , , " + OneUiAssistantGuide.STEP_PRO_AUTO_MODE + " , "
                ),
                "deserialize debe omitir tokens vacios y recortar"
        );
    }

    private static void assertNonEmpty(String value, String message) {
        assertTrue(value != null && !value.trim().isEmpty(), message);
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
