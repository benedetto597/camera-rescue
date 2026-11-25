package com.s23plus.camera;

/**
 * Suite de pruebas unitarias para verificar la configuración de hardware y cálculos geométricos
 * de S23+ Camera Rescue sin requerir emulador.
 */
public final class CameraConfigTest {

    public static void main(String[] args) {
        testCameraIdsAndConstants();
        testAppBrandingAndShortLabels();
        testV130ActionConstants();
        testV140HeuristicAndRecoveryConstants();
        testToggleFrontCameraId();
        testCleanCropBoundsOnS23PlusResolution();
        testFullFrameCropBounds();
        testInvalidDimensionsThrowException();
        System.out.println("PASS: Todas las pruebas unitarias de CameraConfigTest finalizaron con exito (8/8).");
    }

    private static void testAppBrandingAndShortLabels() {
        assertEquals("Samsung Camera Rescue", CameraConfig.APP_TITLE, "APP_TITLE debe incluir Rescue");
        assertEquals("1.4.0", CameraConfig.APP_VERSION, "APP_VERSION debe ser 1.4.0");
        assertTrue(CameraConfig.BTN_REAR_PRO_SHORT.length() <= 24, "Nombre corto de boton Pro debe ser conciso");
        assertTrue(CameraConfig.BTN_REAR_QR_SHORT.length() <= 26, "Nombre corto de boton QR debe ser conciso");
        assertTrue(CameraConfig.BTN_REAR_VIDEO_SHORT.length() <= 24, "Nombre corto de boton Video debe ser conciso");
        assertTrue(CameraConfig.TECH_DESC_REAR_PRO.contains("ID 56") && CameraConfig.TECH_DESC_REAR_PRO.contains("ID 52"),
                "Descripcion tecnica Pro debe documentar sensores ID 56 e ID 52");
        assertTrue(CameraConfig.TECH_DESC_REAR_QR.contains("SCAN_QR_CODE"),
                "Descripcion tecnica QR debe documentar SCAN_QR_CODE");
        assertTrue(CameraConfig.TECH_DESC_FRONT.contains("S5K3LU"),
                "Descripcion tecnica Frontal debe documentar sensor S5K3LU");
    }

    private static void testV130ActionConstants() {
        assertEquals("com.s23plus.camera.action.LAUNCH_REAR_PRO", CameraConfig.ACTION_LAUNCH_REAR_PRO,
                "ACTION_LAUNCH_REAR_PRO incorrecta");
        assertEquals("com.s23plus.camera.action.LAUNCH_REAR_QR", CameraConfig.ACTION_LAUNCH_REAR_QR,
                "ACTION_LAUNCH_REAR_QR incorrecta");
        assertEquals("com.s23plus.camera.action.LAUNCH_REAR_VIDEO", CameraConfig.ACTION_LAUNCH_REAR_VIDEO,
                "ACTION_LAUNCH_REAR_VIDEO incorrecta");
        assertEquals("com.s23plus.camera.action.LAUNCH_FRONT", CameraConfig.ACTION_LAUNCH_FRONT,
                "ACTION_LAUNCH_FRONT incorrecta");
        assertEquals("extra_shortcut_sensor_id", CameraConfig.EXTRA_SHORTCUT_SENSOR_ID,
                "EXTRA_SHORTCUT_SENSOR_ID incorrecto");
        assertEquals(4, CameraConfig.MAX_DYNAMIC_SHORTCUTS,
                "MAX_DYNAMIC_SHORTCUTS debe ser 4");
    }

    private static void testV140HeuristicAndRecoveryConstants() {
        assertEquals("com.s23plus.camera.action.QUICK_RECOVERY", CameraConfig.ACTION_QUICK_RECOVERY,
                "ACTION_QUICK_RECOVERY incorrecta");
        assertEquals("com.sec.android.app.camera.setting.CameraSettingActivity",
                CameraConfig.SAMSUNG_CAMERA_SETTINGS_ACTIVITY,
                "SAMSUNG_CAMERA_SETTINGS_ACTIVITY incorrecta");
        assertEquals("com.samsung.settings.FUNCTION_KEY_SETTINGS",
                CameraConfig.ACTION_SAMSUNG_SIDE_KEY_SETTINGS,
                "ACTION_SAMSUNG_SIDE_KEY_SETTINGS incorrecta");
        assertEquals("ProVideo", CameraConfig.MODE_PRO_VIDEO_CAMEL,
                "MODE_PRO_VIDEO_CAMEL incorrecto");
        assertEquals("com.sec.android.app.camera.shootingmode.pro.ProMode",
                CameraConfig.ACTIVITY_NAME_PRO_LEGACY,
                "ACTIVITY_NAME_PRO_LEGACY incorrecto");
        assertEquals(2500L, CameraConfig.RAPID_RETURN_LOCKUP_THRESHOLD_MS,
                "RAPID_RETURN_LOCKUP_THRESHOLD_MS debe ser 2500L");
        assertEquals(350L, CameraConfig.RECOVERY_HAL_COOLDOWN_MS,
                "RECOVERY_HAL_COOLDOWN_MS debe ser 350L");
    }

    private static void testCameraIdsAndConstants() {
        assertEquals("1", CameraConfig.FRONT_CAMERA_WIDE_ID, "ID frontal Gran Angular debe ser 1");
        assertEquals("3", CameraConfig.FRONT_CAMERA_NORMAL_ID, "ID frontal Normal debe ser 3");
        assertEquals("56", CameraConfig.REAR_MAIN_STANDALONE_ID, "ID trasero principal independiente debe ser 56");
        assertEquals("52", CameraConfig.REAR_TELE_STANDALONE_ID, "ID trasero telefoto independiente debe ser 52");
        assertEquals("com.sec.android.app.camera.action.SCAN_QR_CODE", CameraConfig.ACTION_SCAN_QR_CODE, "Accion QR incorrecta");
        assertEquals("Pro", CameraConfig.MODE_PRO_PHOTO, "Modo Pro incorrecto");
    }

    private static void testToggleFrontCameraId() {
        assertEquals("3", CameraConfig.toggleFrontCameraId("1"), "Toggle de 1 debe retornar 3");
        assertEquals("1", CameraConfig.toggleFrontCameraId("3"), "Toggle de 3 debe retornar 1");
        assertEquals("1", CameraConfig.toggleFrontCameraId(null), "Toggle de null debe retornar 1 por defecto");
    }

    private static void testCleanCropBoundsOnS23PlusResolution() {
        int[] bounds = CameraConfig.computeCropBounds(1080, 2340, true);
        assertEquals(24, bounds[0], "Left margin en 1080p debe ser 24px");
        assertEquals(280, bounds[1], "Top bound en 2340p debe ser 280px");
        assertEquals(1032, bounds[2], "Width limpio en 1080p debe ser 1032px");
        assertEquals(1480, bounds[3], "Height limpio en 2340p debe ser 1480px");
    }

    private static void testFullFrameCropBounds() {
        int[] bounds = CameraConfig.computeCropBounds(1080, 2340, false);
        assertEquals(0, bounds[0], "Left en modo completo debe ser 0");
        assertTrue(bounds[1] > 0 && bounds[1] < 200, "Top en modo completo debe excluir barra de estado");
        assertEquals(1080, bounds[2], "Width en modo completo debe ser 1080");
        assertTrue(bounds[3] > 2000 && (bounds[1] + bounds[3]) <= 2340, "Height en modo completo debe estar dentro de la pantalla");
    }

    private static void testInvalidDimensionsThrowException() {
        boolean threw = false;
        try {
            CameraConfig.computeCropBounds(0, 2340, true);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw, "Dimension 0 debe lanzar IllegalArgumentException");
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
