package com.s23plus.camera;

/**
 * Pruebas unitarias TDD para CameraRecoveryEngine.
 */
public final class CameraRecoveryEngineTest {

    public static void main(String[] args) {
        testIsSuspectedCrashReturnBoundaries();
        testIsCamera2LockupError();
        testIsCameraErrorMessageLockup();
        testNextAttemptIndexEscalationAndReset();
        testBuildRecoveryPlanStagesAndNullSafety();
        System.out.println("PASS: Todas las pruebas unitarias de CameraRecoveryEngineTest finalizaron con exito (5/5).");
    }

    private static void testIsSuspectedCrashReturnBoundaries() {
        assertFalse(CameraRecoveryEngine.isSuspectedCrashReturn(0L, 1000L),
                "launchTimestampMs == 0 debe retornar false");
        assertFalse(CameraRecoveryEngine.isSuspectedCrashReturn(-500L, 100L),
                "launchTimestampMs negativo debe retornar false");
        assertFalse(CameraRecoveryEngine.isSuspectedCrashReturn(2000L, 1999L),
                "resumeTimestampMs menor que launchTimestampMs debe retornar false");

        assertTrue(CameraRecoveryEngine.isSuspectedCrashReturn(1000L, 1000L),
                "Delta 0ms debe considerarse retorno rapido sospechoso");
        assertTrue(CameraRecoveryEngine.isSuspectedCrashReturn(1000L, 1000L + CameraConfig.RAPID_RETURN_LOCKUP_THRESHOLD_MS),
                "Delta exacto de 2500ms debe considerarse retorno rapido sospechoso");
        assertFalse(CameraRecoveryEngine.isSuspectedCrashReturn(1000L, 1000L + CameraConfig.RAPID_RETURN_LOCKUP_THRESHOLD_MS + 1L),
                "Delta de 2501ms supera el umbral y debe retornar false");
    }

    private static void testIsCamera2LockupError() {
        assertFalse(CameraRecoveryEngine.isCamera2LockupError(-1), "Codigo -1 no es bloqueo Camera2");
        assertFalse(CameraRecoveryEngine.isCamera2LockupError(0), "Codigo 0 no es bloqueo Camera2");
        for (int code = 1; code <= 5; code++) {
            assertTrue(CameraRecoveryEngine.isCamera2LockupError(code), "Codigo " + code + " debe ser bloqueo Camera2");
        }
        assertFalse(CameraRecoveryEngine.isCamera2LockupError(6), "Codigo 6 esta fuera de rango Camera2 1..5");
        assertFalse(CameraRecoveryEngine.isCamera2LockupError(99), "Codigo 99 no es bloqueo Camera2");
    }

    private static void testIsCameraErrorMessageLockup() {
        assertFalse(CameraRecoveryEngine.isCameraErrorMessageLockup(null), "null debe retornar false");
        assertFalse(CameraRecoveryEngine.isCameraErrorMessageLockup(""), "vacio debe retornar false");
        assertFalse(CameraRecoveryEngine.isCameraErrorMessageLockup("   "), "blank debe retornar false");
        assertFalse(CameraRecoveryEngine.isCameraErrorMessageLockup("Permiso de camara denegado por el usuario"),
                "Mensaje sin palabras clave de lockup debe retornar false");

        String[] positiveMessages = {
                "Error de HAL inesperado",
                "Camera TIMEOUT al abrir sesion",
                "Sensor IN_USE",
                "CAMERA_IN_USE by another process",
                "Error codigo 1",
                "Fallo codigo 2",
                "Error codigo 3",
                "Error codigo 4",
                "Error codigo 5",
                "Error código 1",
                "Fallo código 2",
                "Error código 3",
                "Error código 4",
                "Error código 5",
                "Camera device DISCONNECTED",
                "Dispositivo desconectado",
                "Camera service is BUSY",
                "Recurso ocupado temporalmente"
        };
        for (String msg : positiveMessages) {
            assertTrue(CameraRecoveryEngine.isCameraErrorMessageLockup(msg),
                    "Mensaje debe detectarse como bloqueo HAL: " + msg);
        }
    }

    private static void testNextAttemptIndexEscalationAndReset() {
        assertEquals(1, CameraRecoveryEngine.nextAttemptIndex(-3, false), "Negativo sin clear debe pasar a 1");
        assertEquals(1, CameraRecoveryEngine.nextAttemptIndex(0, false), "0 -> 1");
        assertEquals(2, CameraRecoveryEngine.nextAttemptIndex(1, false), "1 -> 2");
        assertEquals(3, CameraRecoveryEngine.nextAttemptIndex(2, false), "2 -> 3");
        assertEquals(3, CameraRecoveryEngine.nextAttemptIndex(3, false), "3 se mantiene en 3");
        assertEquals(3, CameraRecoveryEngine.nextAttemptIndex(9, false), "Mayor que 3 se limita a 3");

        assertEquals(0, CameraRecoveryEngine.nextAttemptIndex(0, true), "clear=true reinicia 0 -> 0");
        assertEquals(0, CameraRecoveryEngine.nextAttemptIndex(2, true), "clear=true reinicia 2 -> 0");
        assertEquals(0, CameraRecoveryEngine.nextAttemptIndex(3, true), "clear=true reinicia 3 -> 0");
    }

    private static void testBuildRecoveryPlanStagesAndNullSafety() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        ShootingModeMap.OneUiGeneration oneUi = ShootingModeMap.OneUiGeneration.ONE_UI_6;
        ShootingModeMap.ModeDescriptor proDesc = ShootingModeMap.resolve(
                s23Plus, oneUi, ShootingModeMap.ModeKind.PRO_PHOTO
        );
        ShootingModeMap.ModeDescriptor qrDesc = ShootingModeMap.resolve(
                s23Plus, oneUi, ShootingModeMap.ModeKind.QR_SCANNER
        );

        CameraRecoveryEngine.RecoveryPlan stage0 = CameraRecoveryEngine.buildRecoveryPlan(
                s23Plus, oneUi, ShootingModeMap.ModeKind.PRO_VIDEO, -2
        );
        assertEquals(0, stage0.stageIndex, "Indice negativo debe clamped a 0");
        assertEquals(CameraRecoveryEngine.RecoveryAction.FORCE_PRO_PRIMARY, stage0.action, "Stage 0 action");
        assertEquals(proDesc.getPrimaryVariant().variantId, stage0.launchVariant.variantId, "Stage 0 variant");
        assertTrue(stage0.shouldResetLocalSession, "Stage 0 shouldResetLocalSession");
        assertEquals(CameraConfig.RECOVERY_HAL_COOLDOWN_MS, stage0.cooldownMs, "Stage 0 cooldownMs");
        assertFalse(stage0.isFinalEscalation, "Stage 0 isFinalEscalation");
        assertNonEmpty(stage0.title, "Stage 0 title");
        assertNonEmpty(stage0.userMessage, "Stage 0 userMessage");

        CameraRecoveryEngine.RecoveryPlan stage1 = CameraRecoveryEngine.buildRecoveryPlan(
                s23Plus, oneUi, ShootingModeMap.ModeKind.PRO_PHOTO, 1
        );
        assertEquals(1, stage1.stageIndex, "Stage 1 index");
        assertEquals(CameraRecoveryEngine.RecoveryAction.FORCE_PRO_ALTERNATE, stage1.action, "Stage 1 action");
        assertEquals(proDesc.getVariantAt(1).variantId, stage1.launchVariant.variantId, "Stage 1 variant");
        assertTrue(stage1.shouldResetLocalSession, "Stage 1 shouldResetLocalSession");
        assertEquals(CameraConfig.RECOVERY_HAL_COOLDOWN_MS, stage1.cooldownMs, "Stage 1 cooldownMs");
        assertFalse(stage1.isFinalEscalation, "Stage 1 isFinalEscalation");
        assertNonEmpty(stage1.title, "Stage 1 title");
        assertNonEmpty(stage1.userMessage, "Stage 1 userMessage");

        CameraRecoveryEngine.RecoveryPlan stage2 = CameraRecoveryEngine.buildRecoveryPlan(
                s23Plus, oneUi, ShootingModeMap.ModeKind.PRO_PHOTO, 2
        );
        assertEquals(2, stage2.stageIndex, "Stage 2 index");
        assertEquals(CameraRecoveryEngine.RecoveryAction.FORCE_QR_SINGLE_SENSOR, stage2.action, "Stage 2 action");
        assertEquals(qrDesc.getPrimaryVariant().variantId, stage2.launchVariant.variantId, "Stage 2 variant");
        assertTrue(stage2.shouldResetLocalSession, "Stage 2 shouldResetLocalSession");
        assertEquals(CameraConfig.RECOVERY_HAL_COOLDOWN_MS, stage2.cooldownMs, "Stage 2 cooldownMs");
        assertFalse(stage2.isFinalEscalation, "Stage 2 isFinalEscalation");
        assertNonEmpty(stage2.title, "Stage 2 title");
        assertNonEmpty(stage2.userMessage, "Stage 2 userMessage");

        CameraRecoveryEngine.RecoveryPlan stage3 = CameraRecoveryEngine.buildRecoveryPlan(
                s23Plus, oneUi, ShootingModeMap.ModeKind.QR_SCANNER, 5
        );
        assertEquals(3, stage3.stageIndex, "Indice > 3 debe clamped a 3");
        assertEquals(CameraRecoveryEngine.RecoveryAction.OPEN_CAMERA_APP_INFO, stage3.action, "Stage 3 action");
        assertEquals(null, stage3.launchVariant, "Stage 3 launchVariant debe ser null");
        assertTrue(stage3.shouldResetLocalSession, "Stage 3 shouldResetLocalSession");
        assertEquals(0L, stage3.cooldownMs, "Stage 3 cooldownMs debe ser 0");
        assertTrue(stage3.isFinalEscalation, "Stage 3 isFinalEscalation debe ser true");
        assertNonEmpty(stage3.title, "Stage 3 title");
        assertTrue(stage3.userMessage != null && stage3.userMessage.contains("Forzar detención"),
                "Stage 3 debe guiar al usuario a pulsar Forzar detención");

        CameraRecoveryEngine.RecoveryPlan nullInputs = CameraRecoveryEngine.buildRecoveryPlan(
                null, null, null, 0
        );
        assertEquals(0, nullInputs.stageIndex, "Null inputs stage 0");
        assertEquals(CameraRecoveryEngine.RecoveryAction.FORCE_PRO_PRIMARY, nullInputs.action, "Null inputs action");
        assertTrue(nullInputs.launchVariant != null, "Null inputs debe resolver variante valida");
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
