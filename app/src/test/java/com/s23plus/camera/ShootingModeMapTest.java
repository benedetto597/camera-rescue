package com.s23plus.camera;

import java.util.List;

/**
 * Pruebas unitarias TDD para ShootingModeMap (generaciones One UI y variantes heuristicas).
 */
public final class ShootingModeMapTest {

    public static void main(String[] args) {
        testOneUiGenerationLabelsAndApiLevels();
        testDetectOneUiGenerationFromSemPlatformAndSdkFallback();
        testResolveProPhotoVariants();
        testResolveProVideoSupportedAndUnsupported();
        testResolveQrScannerVariants();
        testResolveNullSafetyClampingAndImmutability();
        testResolveAllOrderAndImmutability();
        System.out.println("PASS: Todas las pruebas unitarias de ShootingModeMapTest finalizaron con exito (7/7).");
    }

    private static void testOneUiGenerationLabelsAndApiLevels() {
        assertEquals("One UI 2.x", ShootingModeMap.OneUiGeneration.ONE_UI_2.displayName, "displayName One UI 2");
        assertEquals(29, ShootingModeMap.OneUiGeneration.ONE_UI_2.minApiLevel, "minApiLevel One UI 2");
        assertEquals("Ajustes que mantener", ShootingModeMap.OneUiGeneration.ONE_UI_2.settingsToKeepLabel, "settingsToKeep One UI 2");
        assertEquals("Tecla lateral / Inicio rápido", ShootingModeMap.OneUiGeneration.ONE_UI_2.sideKeyLabel, "sideKeyLabel One UI 2");

        assertEquals("One UI 3.x", ShootingModeMap.OneUiGeneration.ONE_UI_3.displayName, "displayName One UI 3");
        assertEquals(30, ShootingModeMap.OneUiGeneration.ONE_UI_3.minApiLevel, "minApiLevel One UI 3");
        assertEquals("Ajustes para conservar", ShootingModeMap.OneUiGeneration.ONE_UI_3.settingsToKeepLabel, "settingsToKeep One UI 3");
        assertEquals("Tecla lateral", ShootingModeMap.OneUiGeneration.ONE_UI_3.sideKeyLabel, "sideKeyLabel One UI 3");

        assertEquals("One UI 6.x", ShootingModeMap.OneUiGeneration.ONE_UI_6.displayName, "displayName One UI 6");
        assertEquals(34, ShootingModeMap.OneUiGeneration.ONE_UI_6.minApiLevel, "minApiLevel One UI 6");
        assertEquals("Botón lateral", ShootingModeMap.OneUiGeneration.ONE_UI_6.sideKeyLabel, "sideKeyLabel One UI 6");

        assertEquals("One UI 8+", ShootingModeMap.OneUiGeneration.ONE_UI_8_PLUS.displayName, "displayName One UI 8+");
        assertEquals(36, ShootingModeMap.OneUiGeneration.ONE_UI_8_PLUS.minApiLevel, "minApiLevel One UI 8+");
    }

    private static void testDetectOneUiGenerationFromSemPlatformAndSdkFallback() {
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_2,
                ShootingModeMap.detectOneUiGeneration(34, 110500), "semPlatform 110500 -> ONE_UI_2");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_3,
                ShootingModeMap.detectOneUiGeneration(30, 120100), "semPlatform 120100 -> ONE_UI_3");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_4,
                ShootingModeMap.detectOneUiGeneration(31, 130100), "semPlatform 130100 -> ONE_UI_4");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_5,
                ShootingModeMap.detectOneUiGeneration(33, 140100), "semPlatform 140100 -> ONE_UI_5");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_6,
                ShootingModeMap.detectOneUiGeneration(34, 150100), "semPlatform 150100 -> ONE_UI_6");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_7,
                ShootingModeMap.detectOneUiGeneration(35, 160000), "semPlatform 160000 -> ONE_UI_7");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_8_PLUS,
                ShootingModeMap.detectOneUiGeneration(36, 170000), "semPlatform 170000 -> ONE_UI_8_PLUS");

        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_2,
                ShootingModeMap.detectOneUiGeneration(29, 0), "sdk 29 -> ONE_UI_2");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_3,
                ShootingModeMap.detectOneUiGeneration(30, 0), "sdk 30 -> ONE_UI_3");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_4,
                ShootingModeMap.detectOneUiGeneration(31, 0), "sdk 31 -> ONE_UI_4");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_4,
                ShootingModeMap.detectOneUiGeneration(32, -1), "sdk 32 -> ONE_UI_4");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_5,
                ShootingModeMap.detectOneUiGeneration(33, 0), "sdk 33 -> ONE_UI_5");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_6,
                ShootingModeMap.detectOneUiGeneration(34, 0), "sdk 34 -> ONE_UI_6");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_7,
                ShootingModeMap.detectOneUiGeneration(35, 0), "sdk 35 -> ONE_UI_7");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_8_PLUS,
                ShootingModeMap.detectOneUiGeneration(36, 0), "sdk 36 -> ONE_UI_8_PLUS");
    }

    private static void testResolveProPhotoVariants() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        ShootingModeMap.ModeDescriptor desc = ShootingModeMap.resolve(
                s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_6, ShootingModeMap.ModeKind.PRO_PHOTO
        );

        assertEquals(ShootingModeMap.ModeKind.PRO_PHOTO, desc.kind, "kind debe ser PRO_PHOTO");
        assertEquals(3, desc.shootingModeId, "PRO_PHOTO shootingModeId debe ser 3");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_6, desc.oneUi, "oneUi debe ser ONE_UI_6");
        assertTrue(desc.nativeSupportedOnHardware, "PRO_PHOTO siempre debe ser soportado");
        assertEquals(4, desc.variants.size(), "PRO_PHOTO debe tener 4 variantes");

        ShootingModeMap.LaunchVariant v0 = desc.getPrimaryVariant();
        assertEquals("pro_standard", v0.variantId, "Variante 0 incorrecta");
        assertEquals("android.intent.action.MAIN", v0.intentAction, "v0 intentAction incorrecto");
        assertEquals(CameraConfig.SAMSUNG_CAMERA_PACKAGE, v0.componentPackage, "v0 package incorrecto");
        assertEquals(CameraConfig.SAMSUNG_CAMERA_ACTIVITY, v0.componentClass, "v0 class incorrecto");
        assertEquals(CameraConfig.MODE_PRO_PHOTO, v0.previousMode, "v0 previousMode incorrecto");
        assertEquals(CameraConfig.ACTIVITY_NAME_PRO_PHOTO, v0.activityName, "v0 activityName incorrecto");
        assertEquals(CameraConfig.FACING_BACK, v0.cameraFacing, "v0 facing incorrecto");
        assertTrue(v0.clearTask, "v0 clearTask debe ser true");

        ShootingModeMap.LaunchVariant v1 = desc.getVariantAt(1);
        assertEquals("pro_legacy_class", v1.variantId, "Variante 1 incorrecta");
        assertEquals(CameraConfig.ACTIVITY_NAME_PRO_LEGACY, v1.activityName, "v1 debe usar ACTIVITY_NAME_PRO_LEGACY");

        ShootingModeMap.LaunchVariant v2 = desc.getVariantAt(2);
        assertEquals("pro_minimal_extras", v2.variantId, "Variante 2 incorrecta");
        assertEquals("", v2.activityName, "v2 activityName debe ser vacio");
        assertEquals(CameraConfig.MODE_PRO_PHOTO, v2.previousMode, "v2 previousMode debe ser Pro");

        ShootingModeMap.LaunchVariant v3 = desc.getVariantAt(3);
        assertEquals("pro_qr_extreme_fallback", v3.variantId, "Variante 3 incorrecta");
        assertEquals(CameraConfig.ACTION_SCAN_QR_CODE, v3.intentAction, "v3 intentAction debe ser ACTION_SCAN_QR_CODE");
        assertEquals("", v3.componentClass, "v3 componentClass debe ser vacio");
        assertFalse(v3.clearTask, "v3 clearTask debe ser false");
    }

    private static void testResolveProVideoSupportedAndUnsupported() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        ShootingModeMap.ModeDescriptor supported = ShootingModeMap.resolve(
                s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_6, ShootingModeMap.ModeKind.PRO_VIDEO
        );
        assertEquals(36, supported.shootingModeId, "PRO_VIDEO shootingModeId debe ser 36");
        assertTrue(supported.nativeSupportedOnHardware, "S23+ en One UI 6 debe soportar Pro Video nativo");
        assertEquals(3, supported.variants.size(), "PRO_VIDEO soportado debe tener 3 variantes");
        assertEquals("pro_video_standard", supported.getVariantAt(0).variantId, "v0 pro_video_standard");
        assertEquals(CameraConfig.MODE_PRO_VIDEO, supported.getVariantAt(0).previousMode, "v0 mode Pro video");
        assertEquals("pro_video_camel", supported.getVariantAt(1).variantId, "v1 pro_video_camel");
        assertEquals(CameraConfig.MODE_PRO_VIDEO_CAMEL, supported.getVariantAt(1).previousMode, "v1 mode ProVideo");
        assertEquals("pro_video_to_pro_fallback", supported.getVariantAt(2).variantId, "v2 pro_video_to_pro_fallback");
        assertEquals(CameraConfig.MODE_PRO_PHOTO, supported.getVariantAt(2).previousMode, "v2 fallback a Pro");

        // Caso 1: hardware sin Pro Video nativo (Galaxy A54)
        SamsungModelDatabase.ModelProfile a54 = SamsungModelDatabase.lookup("SM-A546B");
        ShootingModeMap.ModeDescriptor unsupportedHw = ShootingModeMap.resolve(
                a54, ShootingModeMap.OneUiGeneration.ONE_UI_6, ShootingModeMap.ModeKind.PRO_VIDEO
        );
        assertFalse(unsupportedHw.nativeSupportedOnHardware, "A54 no debe reportar Pro Video nativo");
        assertEquals(2, unsupportedHw.variants.size(), "PRO_VIDEO no soportado debe tener 2 variantes");
        assertEquals("pro_video_try_standard", unsupportedHw.getVariantAt(0).variantId, "v0 try standard");
        assertEquals("pro_video_fallback_pro", unsupportedHw.getVariantAt(1).variantId, "v1 fallback pro");
        assertTrue(unsupportedHw.compatibilityNote != null && !unsupportedHw.compatibilityNote.trim().isEmpty(),
                "compatibilityNote debe explicar ausencia de Video Pro nativo");

        // Caso 2: hardware capaz pero One UI 2.x
        ShootingModeMap.ModeDescriptor unsupportedUi = ShootingModeMap.resolve(
                s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_2, ShootingModeMap.ModeKind.PRO_VIDEO
        );
        assertFalse(unsupportedUi.nativeSupportedOnHardware, "One UI 2.x no debe reportar Pro Video nativo");
        assertEquals(2, unsupportedUi.variants.size(), "One UI 2.x PRO_VIDEO debe tener 2 variantes");
        assertTrue(unsupportedUi.compatibilityNote != null && !unsupportedUi.compatibilityNote.trim().isEmpty(),
                "compatibilityNote debe explicar limitacion en One UI 2.x");
    }

    private static void testResolveQrScannerVariants() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        ShootingModeMap.ModeDescriptor qr = ShootingModeMap.resolve(
                s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_7, ShootingModeMap.ModeKind.QR_SCANNER
        );
        assertEquals(ShootingModeMap.ModeKind.QR_SCANNER, qr.kind, "kind QR_SCANNER");
        assertEquals(40, qr.shootingModeId, "QR_SCANNER shootingModeId debe ser 40");
        assertTrue(qr.nativeSupportedOnHardware, "QR_SCANNER debe ser soportado");
        assertEquals(2, qr.variants.size(), "QR_SCANNER debe tener 2 variantes");

        assertEquals("qr_scan_exported", qr.getVariantAt(0).variantId, "v0 qr_scan_exported");
        assertEquals(CameraConfig.ACTION_SCAN_QR_CODE, qr.getVariantAt(0).intentAction, "v0 SCAN_QR_CODE");
        assertFalse(qr.getVariantAt(0).clearTask, "v0 clearTask false");

        assertEquals("qr_fallback_pro", qr.getVariantAt(1).variantId, "v1 qr_fallback_pro");
        assertEquals(CameraConfig.MODE_PRO_PHOTO, qr.getVariantAt(1).previousMode, "v1 fallback Pro");
        assertTrue(qr.getVariantAt(1).clearTask, "v1 clearTask true");
    }

    private static void testResolveNullSafetyClampingAndImmutability() {
        ShootingModeMap.ModeDescriptor defaulted = ShootingModeMap.resolve(null, null, null);
        assertEquals(ShootingModeMap.ModeKind.PRO_PHOTO, defaulted.kind, "kind null -> PRO_PHOTO");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_6, defaulted.oneUi, "oneUi null -> ONE_UI_6");
        assertTrue(defaulted.primarySensorSummary != null && !defaulted.primarySensorSummary.isEmpty(),
                "primarySensorSummary no debe ser vacio");

        assertEquals(defaulted.variants.get(0).variantId, defaulted.getVariantAt(-5).variantId,
                "getVariantAt(-5) debe clamped a 0");
        assertEquals(defaulted.variants.get(defaulted.variants.size() - 1).variantId,
                defaulted.getVariantAt(99).variantId,
                "getVariantAt(99) debe clamped al ultimo elemento");

        boolean threw = false;
        try {
            defaulted.variants.clear();
        } catch (UnsupportedOperationException expected) {
            threw = true;
        }
        assertTrue(threw, "variants debe ser inmodificable");

        ShootingModeMap.LaunchVariant nullNorm = new ShootingModeMap.LaunchVariant(
                null, null, null, null, null, null, null, false, null
        );
        assertEquals("", nullNorm.variantId, "variantId null -> vacio");
        assertEquals("", nullNorm.intentAction, "intentAction null -> vacio");
        assertEquals("", nullNorm.description, "description null -> vacio");
    }

    private static void testResolveAllOrderAndImmutability() {
        SamsungModelDatabase.ModelProfile s23Plus = SamsungModelDatabase.lookup("SM-S916U1");
        List<ShootingModeMap.ModeDescriptor> all = ShootingModeMap.resolveAll(
                s23Plus, ShootingModeMap.OneUiGeneration.ONE_UI_6
        );
        assertEquals(3, all.size(), "resolveAll debe retornar 3 modos");
        assertEquals(ShootingModeMap.ModeKind.PRO_PHOTO, all.get(0).kind, "Indice 0 debe ser PRO_PHOTO");
        assertEquals(ShootingModeMap.ModeKind.QR_SCANNER, all.get(1).kind, "Indice 1 debe ser QR_SCANNER");
        assertEquals(ShootingModeMap.ModeKind.PRO_VIDEO, all.get(2).kind, "Indice 2 debe ser PRO_VIDEO");

        boolean threw = false;
        try {
            all.clear();
        } catch (UnsupportedOperationException expected) {
            threw = true;
        }
        assertTrue(threw, "resolveAll debe retornar lista inmodificable");
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
