package com.s23plus.camera;

/**
 * Pruebas unitarias TDD para CameraViewerMath (zoom, EV, apertura variable, flash, temporizador,
 * cuadricula, orientacion y seleccion de resolucion de video).
 */
public final class CameraViewerMathTest {

    public static void main(String[] args) {
        testZoomCapabilitiesNormalizationAndClamp();
        testResolveZoomApplicationStrategies();
        testStepZoomPresetStopsAndFormat();
        testEvCapabilitiesClampAndFormat();
        testApertureCapabilitiesAndTransitions();
        testFlashModeResolveAndCycle();
        testGridModeAndTimer();
        testCaptureOrientation();
        testChooseBestVideoSize();
        System.out.println("PASS: Todas las pruebas unitarias de CameraViewerMathTest finalizaron con exito (9/9).");
    }

    private static void testZoomCapabilitiesNormalizationAndClamp() {
        CameraViewerMath.ZoomCapabilities invalid = new CameraViewerMath.ZoomCapabilities(
                Float.NaN, Float.POSITIVE_INFINITY, true, -100, -50);
        assertFloatEquals(1.0f, invalid.minZoom, 0.001f, "minZoom NaN debe normalizarse a 1.0");
        assertFloatEquals(1.0f, invalid.maxZoom, 0.001f, "maxZoom Infinity debe normalizarse a minZoom");
        assertEquals(0, invalid.activeArrayWidth, "activeArrayWidth negativo debe clampearse a 0");
        assertEquals(0, invalid.activeArrayHeight, "activeArrayHeight negativo debe clampearse a 0");

        CameraViewerMath.ZoomCapabilities tinyMin = new CameraViewerMath.ZoomCapabilities(
                0.02f, 0.01f, false, 4000, 3000);
        assertFloatEquals(0.1f, tinyMin.minZoom, 0.001f, "minZoom positivo menor a 0.1 debe normalizarse a 0.1");
        assertFloatEquals(0.1f, tinyMin.maxZoom, 0.001f, "maxZoom menor a minZoom debe igualarse a minZoom");

        CameraViewerMath.ZoomCapabilities zeroMin = new CameraViewerMath.ZoomCapabilities(
                0.0f, 5.0f, true, 4000, 3000);
        assertFloatEquals(1.0f, zeroMin.minZoom, 0.001f, "minZoom <= 0 debe fallback a 1.0");
        assertFloatEquals(5.0f, zeroMin.maxZoom, 0.001f, "maxZoom valido debe mantenerse");

        assertFloatEquals(1.0f, CameraViewerMath.clampZoom(2.5f, null), 0.001f, "caps null debe retornar 1.0");
        CameraViewerMath.ZoomCapabilities ultrawide = new CameraViewerMath.ZoomCapabilities(
                0.6f, 8.0f, true, 4000, 3000);
        assertFloatEquals(0.6f, CameraViewerMath.clampZoom(0.2f, ultrawide), 0.001f, "Debe clampear a minZoom");
        assertFloatEquals(8.0f, CameraViewerMath.clampZoom(12.0f, ultrawide), 0.001f, "Debe clampear a maxZoom");
        assertFloatEquals(1.0f, CameraViewerMath.clampZoom(Float.NaN, ultrawide), 0.001f, "NaN debe clampear 1.0");
        assertFloatEquals(1.0f, CameraViewerMath.clampZoom(Float.POSITIVE_INFINITY, ultrawide), 0.001f,
                "Infinity debe clampear 1.0");
    }

    private static void testResolveZoomApplicationStrategies() {
        CameraViewerMath.ZoomApplication nullCaps = CameraViewerMath.resolveZoomApplication(2.0f, 34, null);
        assertEquals(CameraViewerMath.ZoomStrategy.NONE, nullCaps.strategy, "caps null -> NONE");
        assertFloatEquals(1.0f, nullCaps.clampedZoom, 0.001f, "caps null clampedZoom -> 1.0");
        assertEquals(0, nullCaps.cropLeft, "caps null cropLeft -> 0");
        assertEquals(0, nullCaps.cropRight, "caps null cropRight -> 0");

        CameraViewerMath.ZoomCapabilities caps = new CameraViewerMath.ZoomCapabilities(
                0.6f, 10.0f, true, 4000, 3000);

        // API 30+ with supportsZoomRatioApi -> ZOOM_RATIO, centered crop uses max(1.0, clamped)
        CameraViewerMath.ZoomApplication api33Wide = CameraViewerMath.resolveZoomApplication(0.6f, 33, caps);
        assertEquals(CameraViewerMath.ZoomStrategy.ZOOM_RATIO, api33Wide.strategy, "API 33 debe usar ZOOM_RATIO");
        assertFloatEquals(0.6f, api33Wide.clampedZoom, 0.001f, "ZOOM_RATIO permite 0.6x");
        assertEquals(0, api33Wide.cropLeft, "Zoom < 1.0 no recorta por debajo de full array");
        assertEquals(0, api33Wide.cropTop, "Zoom < 1.0 cropTop debe ser 0");
        assertEquals(4000, api33Wide.cropRight, "Zoom < 1.0 cropRight debe ser 4000");
        assertEquals(3000, api33Wide.cropBottom, "Zoom < 1.0 cropBottom debe ser 3000");

        // API 29 -> falls back to SCALER_CROP_REGION and clamps < 1.0 up to 1.0
        CameraViewerMath.ZoomApplication api29Wide = CameraViewerMath.resolveZoomApplication(0.6f, 29, caps);
        assertEquals(CameraViewerMath.ZoomStrategy.SCALER_CROP_REGION, api29Wide.strategy,
                "API 29 debe usar SCALER_CROP_REGION");
        assertFloatEquals(1.0f, api29Wide.clampedZoom, 0.001f,
                "SCALER_CROP_REGION no puede hacer zoom out < 1.0");

        // 2.0x centered crop on 4000x3000 -> 2000x1500 centered at (1000, 750, 3000, 2250)
        CameraViewerMath.ZoomApplication crop2x = CameraViewerMath.resolveZoomApplication(2.0f, 28, caps);
        assertEquals(CameraViewerMath.ZoomStrategy.SCALER_CROP_REGION, crop2x.strategy,
                "API 28 debe usar SCALER_CROP_REGION");
        assertFloatEquals(2.0f, crop2x.clampedZoom, 0.001f, "clampedZoom debe ser 2.0");
        assertEquals(1000, crop2x.cropLeft, "cropLeft 2x");
        assertEquals(750, crop2x.cropTop, "cropTop 2x");
        assertEquals(3000, crop2x.cropRight, "cropRight 2x");
        assertEquals(2250, crop2x.cropBottom, "cropBottom 2x");

        // No zoom capability -> NONE
        CameraViewerMath.ZoomCapabilities fixedLens = new CameraViewerMath.ZoomCapabilities(
                1.0f, 1.0f, true, 4000, 3000);
        CameraViewerMath.ZoomApplication noneApp = CameraViewerMath.resolveZoomApplication(2.0f, 34, fixedLens);
        assertEquals(CameraViewerMath.ZoomStrategy.NONE, noneApp.strategy, "Sin rango de zoom -> NONE");
    }

    private static void testStepZoomPresetStopsAndFormat() {
        CameraViewerMath.ZoomCapabilities caps = new CameraViewerMath.ZoomCapabilities(
                0.6f, 6.0f, true, 4000, 3000);

        assertFloatEquals(1.3f, CameraViewerMath.stepZoom(1.0f, 0.34f, caps), 0.001f,
                "stepZoom debe redondear a 1 decimal");
        assertFloatEquals(6.0f, CameraViewerMath.stepZoom(5.9f, 0.5f, caps), 0.001f,
                "stepZoom debe clampear al maximo");
        assertFloatEquals(1.0f, CameraViewerMath.stepZoom(1.0f, 0.5f, null), 0.001f,
                "stepZoom con caps null debe retornar 1.0");

        float[] stops = CameraViewerMath.presetZoomStops(caps);
        assertEquals(5, stops.length, "Stops esperados: 0.6, 1.0, 2.0, 4.0, 6.0");
        assertFloatEquals(0.6f, stops[0], 0.001f, "stop 0");
        assertFloatEquals(1.0f, stops[1], 0.001f, "stop 1");
        assertFloatEquals(2.0f, stops[2], 0.001f, "stop 2");
        assertFloatEquals(4.0f, stops[3], 0.001f, "stop 3");
        assertFloatEquals(6.0f, stops[4], 0.001f, "stop 4");

        CameraViewerMath.ZoomCapabilities exact4x = new CameraViewerMath.ZoomCapabilities(
                1.0f, 4.0f, false, 4000, 3000);
        float[] stops4x = CameraViewerMath.presetZoomStops(exact4x);
        assertEquals(3, stops4x.length, "No debe duplicar 4.0f en stops");
        assertFloatEquals(1.0f, stops4x[0], 0.001f, "stop4x 0");
        assertFloatEquals(2.0f, stops4x[1], 0.001f, "stop4x 1");
        assertFloatEquals(4.0f, stops4x[2], 0.001f, "stop4x 2");

        float[] nullStops = CameraViewerMath.presetZoomStops(null);
        assertEquals(1, nullStops.length, "null caps preset stops debe tener longitud 1");
        assertFloatEquals(1.0f, nullStops[0], 0.001f, "null caps preset stop debe ser 1.0");

        assertEquals("1.0x", CameraViewerMath.formatZoomLabel(1.0f), "formatZoomLabel 1.0");
        assertEquals("2.5x", CameraViewerMath.formatZoomLabel(2.5f), "formatZoomLabel 2.5");
        assertEquals("1.0x", CameraViewerMath.formatZoomLabel(Float.NaN), "formatZoomLabel NaN");
        assertEquals("1.0x", CameraViewerMath.formatZoomLabel(Float.NEGATIVE_INFINITY), "formatZoomLabel -Inf");
    }

    private static void testEvCapabilitiesClampAndFormat() {
        CameraViewerMath.EvCapabilities unsupported = new CameraViewerMath.EvCapabilities(0, 0, 1, 2);
        assertTrue(!unsupported.supported, "min == max no es soportado");
        assertEquals(0, CameraViewerMath.clampEvIndex(3, unsupported), "EV no soportado retorna 0");
        assertEquals("+0.0 EV", CameraViewerMath.formatEvValue(3, unsupported), "EV no soportado formatea +0.0 EV");
        assertEquals("+0.0 EV", CameraViewerMath.formatEvValue(1, null), "EV null formatea +0.0 EV");

        CameraViewerMath.EvCapabilities badStep = new CameraViewerMath.EvCapabilities(-4, 4, 0, 2);
        assertTrue(!badStep.supported, "stepNumerator <= 0 no es soportado");

        CameraViewerMath.EvCapabilities ev = new CameraViewerMath.EvCapabilities(-6, 6, 1, 2);
        assertTrue(ev.supported, "EV valido debe marcarse soportado");
        assertEquals(-6, CameraViewerMath.clampEvIndex(-10, ev), "clampEvIndex inferior");
        assertEquals(6, CameraViewerMath.clampEvIndex(10, ev), "clampEvIndex superior");
        assertEquals(2, CameraViewerMath.clampEvIndex(2, ev), "clampEvIndex dentro de rango");

        assertEquals("+0.0 EV", CameraViewerMath.formatEvValue(0, ev), "EV 0 debe ser +0.0 EV");
        assertEquals("+1.0 EV", CameraViewerMath.formatEvValue(2, ev), "EV +2 * 0.5 debe ser +1.0 EV");
        assertEquals("-0.5 EV", CameraViewerMath.formatEvValue(-1, ev), "EV -1 * 0.5 debe ser -0.5 EV");
        assertEquals("+3.0 EV", CameraViewerMath.formatEvValue(99, ev), "EV clampeado al max");
    }

    private static void testApertureCapabilitiesAndTransitions() {
        CameraViewerMath.ApertureCapabilities s9Dual =
                CameraViewerMath.buildApertureCapabilities(new float[]{2.4f, 0.0f, -1.0f, Float.NaN, 1.5f, 1.505f});
        assertTrue(s9Dual.variableApertureSupported, "S9 dual aperture debe soportar apertura variable");
        float[] copy = s9Dual.getSupportedApertures();
        assertEquals(2, copy.length, "Debe filtrar invalidos y deduplicar dentro de 0.01f");
        assertFloatEquals(1.5f, copy[0], 0.001f, "Primera apertura ordenada debe ser 1.5");
        assertFloatEquals(2.4f, copy[1], 0.001f, "Segunda apertura ordenada debe ser 2.4");

        // Verificar copia defensiva
        copy[0] = 99.0f;
        assertFloatEquals(1.5f, s9Dual.getSupportedApertures()[0], 0.001f,
                "Mutar el arreglo devuelto no debe alterar el estado interno");

        CameraViewerMath.ApertureCapabilities fixed =
                CameraViewerMath.buildApertureCapabilities(new float[]{1.8f});
        assertTrue(!fixed.variableApertureSupported, "Una sola apertura no es apertura variable");
        assertFloatEquals(0.0f, CameraViewerMath.resolveNextAperture(0.0f, fixed), 0.001f,
                "Sin apertura variable, next debe ser APERTURE_AUTO (0.0)");

        // Ciclo completo: 0.0 -> 1.5 -> 2.4 -> 0.0
        assertFloatEquals(1.5f, CameraViewerMath.resolveNextAperture(0.0f, s9Dual), 0.001f, "Auto -> f/1.5");
        assertFloatEquals(2.4f, CameraViewerMath.resolveNextAperture(1.5f, s9Dual), 0.001f, "f/1.5 -> f/2.4");
        assertFloatEquals(0.0f, CameraViewerMath.resolveNextAperture(2.4f, s9Dual), 0.001f, "f/2.4 -> Auto");

        // Sanitize & Format
        assertFloatEquals(1.5f, CameraViewerMath.sanitizeApertureSelection(1.52f, s9Dual), 0.001f,
                "Dentro de 0.05f debe ajustar al valor soportado");
        assertFloatEquals(0.0f, CameraViewerMath.sanitizeApertureSelection(2.0f, s9Dual), 0.001f,
                "Apertura no soportada debe caer a 0.0");
        assertFloatEquals(0.0f, CameraViewerMath.sanitizeApertureSelection(Float.NaN, s9Dual), 0.001f,
                "NaN debe caer a 0.0");

        assertEquals("f/Auto", CameraViewerMath.formatApertureLabel(0.0f, s9Dual), "0.0 -> f/Auto");
        assertEquals("f/1.5", CameraViewerMath.formatApertureLabel(1.5f, s9Dual), "1.5 -> f/1.5");
        assertEquals("f/2.4", CameraViewerMath.formatApertureLabel(2.4f, s9Dual), "2.4 -> f/2.4");
        assertEquals("f/Auto", CameraViewerMath.formatApertureLabel(1.5f, fixed), "No soportado -> f/Auto");
        assertEquals("f/Auto", CameraViewerMath.formatApertureLabel(1.5f, null), "Null caps -> f/Auto");
    }

    private static void testFlashModeResolveAndCycle() {
        assertEquals("Flash Off", CameraViewerMath.FlashMode.OFF.displayLabel, "Label OFF");
        assertEquals("Flash Auto", CameraViewerMath.FlashMode.AUTO.displayLabel, "Label AUTO");
        assertEquals("Flash On", CameraViewerMath.FlashMode.ON.displayLabel, "Label ON");
        assertEquals("Linterna", CameraViewerMath.FlashMode.TORCH.displayLabel, "Label TORCH");

        // Sin flash hardware -> siempre OFF
        assertEquals(CameraViewerMath.FlashMode.OFF,
                CameraViewerMath.resolveFlashMode(CameraViewerMath.FlashMode.ON, false, false),
                "Sin flash -> OFF");
        assertEquals(CameraViewerMath.FlashMode.OFF,
                CameraViewerMath.nextFlashMode(CameraViewerMath.FlashMode.OFF, false, false),
                "Sin flash next -> OFF");

        // Modo video: solo OFF y TORCH
        assertEquals(CameraViewerMath.FlashMode.OFF,
                CameraViewerMath.resolveFlashMode(CameraViewerMath.FlashMode.AUTO, true, true),
                "Video no permite AUTO");
        assertEquals(CameraViewerMath.FlashMode.OFF,
                CameraViewerMath.resolveFlashMode(CameraViewerMath.FlashMode.ON, true, true),
                "Video no permite ON");
        assertEquals(CameraViewerMath.FlashMode.TORCH,
                CameraViewerMath.resolveFlashMode(CameraViewerMath.FlashMode.TORCH, true, true),
                "Video permite TORCH");
        assertEquals(CameraViewerMath.FlashMode.TORCH,
                CameraViewerMath.nextFlashMode(CameraViewerMath.FlashMode.OFF, true, true),
                "Video OFF -> TORCH");
        assertEquals(CameraViewerMath.FlashMode.OFF,
                CameraViewerMath.nextFlashMode(CameraViewerMath.FlashMode.TORCH, true, true),
                "Video TORCH -> OFF");

        // Modo foto: OFF -> AUTO -> ON -> TORCH -> OFF
        assertEquals(CameraViewerMath.FlashMode.AUTO,
                CameraViewerMath.nextFlashMode(CameraViewerMath.FlashMode.OFF, true, false), "Foto OFF -> AUTO");
        assertEquals(CameraViewerMath.FlashMode.ON,
                CameraViewerMath.nextFlashMode(CameraViewerMath.FlashMode.AUTO, true, false), "Foto AUTO -> ON");
        assertEquals(CameraViewerMath.FlashMode.TORCH,
                CameraViewerMath.nextFlashMode(CameraViewerMath.FlashMode.ON, true, false), "Foto ON -> TORCH");
        assertEquals(CameraViewerMath.FlashMode.OFF,
                CameraViewerMath.nextFlashMode(CameraViewerMath.FlashMode.TORCH, true, false), "Foto TORCH -> OFF");
    }

    private static void testGridModeAndTimer() {
        assertEquals("Sin guía", CameraViewerMath.GridMode.OFF.displayLabel, "Grid OFF label");
        assertEquals("3x3", CameraViewerMath.GridMode.RULE_OF_THIRDS.displayLabel, "Grid 3x3 label");
        assertEquals("Cruz", CameraViewerMath.GridMode.CROSSHAIR.displayLabel, "Grid CROSSHAIR label");

        assertEquals(CameraViewerMath.GridMode.RULE_OF_THIRDS, CameraViewerMath.GridMode.OFF.next(), "Grid next 1");
        assertEquals(CameraViewerMath.GridMode.CROSSHAIR, CameraViewerMath.GridMode.RULE_OF_THIRDS.next(), "Grid next 2");
        assertEquals(CameraViewerMath.GridMode.OFF, CameraViewerMath.GridMode.CROSSHAIR.next(), "Grid next 3");

        assertEquals(0, CameraViewerMath.sanitizeTimerSeconds(0), "Timer 0");
        assertEquals(3, CameraViewerMath.sanitizeTimerSeconds(3), "Timer 3");
        assertEquals(5, CameraViewerMath.sanitizeTimerSeconds(5), "Timer 5");
        assertEquals(10, CameraViewerMath.sanitizeTimerSeconds(10), "Timer 10");
        assertEquals(0, CameraViewerMath.sanitizeTimerSeconds(7), "Timer invalido -> 0");
        assertEquals(0, CameraViewerMath.sanitizeTimerSeconds(-3), "Timer negativo -> 0");

        assertEquals(3, CameraViewerMath.nextTimerSeconds(0), "0 -> 3");
        assertEquals(5, CameraViewerMath.nextTimerSeconds(3), "3 -> 5");
        assertEquals(10, CameraViewerMath.nextTimerSeconds(5), "5 -> 10");
        assertEquals(0, CameraViewerMath.nextTimerSeconds(10), "10 -> 0");
    }

    private static void testCaptureOrientation() {
        // Front: (sensor + display) % 360
        assertEquals(270, CameraViewerMath.computeCaptureOrientation(270, 0, true), "Front 270 + 0");
        assertEquals(0, CameraViewerMath.computeCaptureOrientation(270, 90, true), "Front 270 + 90");
        assertEquals(90, CameraViewerMath.computeCaptureOrientation(270, 180, true), "Front 270 + 180");

        // Back: (sensor - display + 360) % 360
        assertEquals(90, CameraViewerMath.computeCaptureOrientation(90, 0, false), "Back 90 - 0");
        assertEquals(0, CameraViewerMath.computeCaptureOrientation(90, 90, false), "Back 90 - 90");
        assertEquals(180, CameraViewerMath.computeCaptureOrientation(90, 270, false), "Back 90 - 270");
    }

    private static void testChooseBestVideoSize() {
        int[] fallback = CameraViewerMath.chooseBestVideoSize(null, 1920, 1080);
        assertEquals(1280, fallback[0], "Fallback width 1280");
        assertEquals(720, fallback[1], "Fallback height 720");

        int[][] invalidRows = new int[][]{null, {0, 720}, {1280, -1}, {1920}};
        int[] fallbackInvalid = CameraViewerMath.chooseBestVideoSize(invalidRows, 1920, 1080);
        assertEquals(1280, fallbackInvalid[0], "Invalid candidates fallback width");
        assertEquals(720, fallbackInvalid[1], "Invalid candidates fallback height");

        // Prefiere 1920x1080 sobre 1920x1440 cuando ambos caben
        int[][] candidatesWith1080 = new int[][]{
                {3840, 2160},
                {1920, 1440},
                {1920, 1080},
                {1280, 720}
        };
        int[] prefer1080 = CameraViewerMath.chooseBestVideoSize(candidatesWith1080, 1920, 1440);
        assertEquals(1920, prefer1080[0], "Debe preferir 1920x1080 cuando esta disponible");
        assertEquals(1080, prefer1080[1], "Debe preferir 1080p");

        // Sin 1080p: elige mayor area dentro de limites, desempate mas cercano a 16:9
        int[][] no1080 = new int[][]{
                {1600, 900},  // area 1,440,000, ratio 16:9
                {1200, 1200}, // area 1,440,000, ratio 1:1
                {1280, 720}
        };
        int[] bestFit = CameraViewerMath.chooseBestVideoSize(no1080, 1920, 1200);
        assertEquals(1600, bestFit[0], "Desempate de area debe preferir 16:9");
        assertEquals(900, bestFit[1], "Height 900");

        // Todos exceden el maximo: elige menor area positiva
        int[][] allExceed = new int[][]{
                {3840, 2160},
                {2560, 1440},
                {1920, 1080}
        };
        int[] smallest = CameraViewerMath.chooseBestVideoSize(allExceed, 1280, 720);
        assertEquals(1920, smallest[0], "Si todos exceden, elige el de menor area");
        assertEquals(1080, smallest[1], "Height 1080");
    }

    private static void assertFloatEquals(float expected, float actual, float eps, String message) {
        if (Float.isNaN(actual) || Math.abs(expected - actual) > eps) {
            throw new AssertionError(message + " | Esperado: " + expected + ", Actual: " + actual);
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
