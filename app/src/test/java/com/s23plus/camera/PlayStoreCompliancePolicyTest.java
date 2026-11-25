package com.s23plus.camera;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pruebas unitarias TDD para PlayStoreCompliancePolicy (cumplimiento Play Store v2.1.0).
 */
public final class PlayStoreCompliancePolicyTest {

    public static void main(String[] args) {
        testPolicyConstantsAndImmutableSets();
        testValidBaselineSnapshotPasses();
        testNullSnapshotFailsSafely();
        testAppTitleValidation();
        testApplicationIdValidation();
        testSdkAndBackupAndAccessibilityValidation();
        testForbiddenAndUnknownPermissionsValidation();
        testPrivacyUrlAndDisclaimerValidation();
        testLegacyMediaPathsRejected();
        testOfflineDataSafetySummaryInvariants();
        testWorkspaceArtifactsMeetPlayStorePolicy();
        System.out.println("PASS: Todas las pruebas unitarias de PlayStoreCompliancePolicyTest finalizaron con exito (11/11).");
    }

    private static PlayStoreCompliancePolicy.ManifestSnapshot validBaselineSnapshot() {
        return new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID,
                CameraConfig.APP_TITLE,
                PlayStoreCompliancePolicy.MIN_SUPPORTED_SDK,
                CameraConfig.TARGET_PLAY_SDK,
                CameraConfig.APP_VERSION_CODE,
                CameraConfig.APP_VERSION,
                false,
                false,
                PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS,
                CameraConfig.PRIVACY_POLICY_URL,
                CameraConfig.TRADEMARK_DISCLAIMER,
                CameraConfig.MEDIA_RELATIVE_PATH,
                CameraConfig.VIDEO_RELATIVE_PATH
        );
    }

    private static void testPolicyConstantsAndImmutableSets() {
        assertEquals(30, PlayStoreCompliancePolicy.MAX_PLAY_TITLE_LENGTH, "MAX_PLAY_TITLE_LENGTH debe ser 30");
        assertEquals(36, PlayStoreCompliancePolicy.MIN_REQUIRED_PLAY_TARGET_SDK, "MIN_REQUIRED_PLAY_TARGET_SDK debe ser 36");
        assertEquals(30, PlayStoreCompliancePolicy.MIN_SUPPORTED_SDK, "MIN_SUPPORTED_SDK debe ser 30");

        assertTrue(PlayStoreCompliancePolicy.FORBIDDEN_PERMISSIONS.contains("android.permission.INTERNET"),
                "FORBIDDEN_PERMISSIONS debe incluir INTERNET");
        assertTrue(PlayStoreCompliancePolicy.FORBIDDEN_PERMISSIONS.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"),
                "FORBIDDEN_PERMISSIONS debe incluir BIND_ACCESSIBILITY_SERVICE");
        assertTrue(PlayStoreCompliancePolicy.FORBIDDEN_PERMISSIONS.contains("android.permission.MANAGE_EXTERNAL_STORAGE"),
                "FORBIDDEN_PERMISSIONS debe incluir MANAGE_EXTERNAL_STORAGE");
        assertTrue(PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS.contains("android.permission.CAMERA"),
                "ALLOWED_PERMISSIONS debe incluir CAMERA");
        assertTrue(PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS.contains("android.permission.RECORD_AUDIO"),
                "ALLOWED_PERMISSIONS debe incluir RECORD_AUDIO");
        assertTrue(PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS.contains("android.permission.BIND_QUICK_SETTINGS_TILE"),
                "ALLOWED_PERMISSIONS debe incluir BIND_QUICK_SETTINGS_TILE");

        assertUnmodifiableSet(PlayStoreCompliancePolicy.FORBIDDEN_PERMISSIONS, "FORBIDDEN_PERMISSIONS debe ser inmutable");
        assertUnmodifiableSet(PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS, "ALLOWED_PERMISSIONS debe ser inmutable");
    }

    private static void testValidBaselineSnapshotPasses() {
        PlayStoreCompliancePolicy.ComplianceReport report =
                PlayStoreCompliancePolicy.audit(validBaselineSnapshot());
        assertTrue(report.compliant, "Baseline v2.1.0 debe cumplir sin violaciones: " + report.violations);
        assertEquals(0, report.violations.size(), "Baseline no debe tener violaciones");
        assertUnmodifiableList(report.violations, "violations debe ser inmodificable");
    }

    private static void testNullSnapshotFailsSafely() {
        PlayStoreCompliancePolicy.ComplianceReport report = PlayStoreCompliancePolicy.audit(null);
        assertFalse(report.compliant, "Snapshot null no debe ser compliant");
        assertTrue(!report.violations.isEmpty(), "Snapshot null debe reportar al menos una violacion");
    }

    private static void testAppTitleValidation() {
        assertTrue(PlayStoreCompliancePolicy.validateAppTitle(CameraConfig.APP_TITLE).isEmpty(),
                "APP_TITLE oficial debe ser valido");
        assertFalse(PlayStoreCompliancePolicy.validateAppTitle(null).isEmpty(),
                "Titulo null debe rechazarse");
        assertFalse(PlayStoreCompliancePolicy.validateAppTitle("   ").isEmpty(),
                "Titulo en blanco debe rechazarse");
        assertFalse(PlayStoreCompliancePolicy.validateAppTitle("1234567890123456789012345678901").isEmpty(),
                "Titulo > 30 caracteres debe rechazarse");

        List<String> trademarkTitles = Arrays.asList(
                "Samsung Camera",
                "Galaxy Fix",
                "Android Cam",
                "Google Cam",
                "Official Rescue"
        );
        for (String badTitle : trademarkTitles) {
            assertFalse(PlayStoreCompliancePolicy.validateAppTitle(badTitle).isEmpty(),
                    "Titulo con marca registrada debe rechazarse: " + badTitle);
        }
    }

    private static void testApplicationIdValidation() {
        assertTrue(PlayStoreCompliancePolicy.validateApplicationId(CameraConfig.PLAY_APPLICATION_ID).isEmpty(),
                "PLAY_APPLICATION_ID oficial debe ser valido");

        List<String> invalidIds = Arrays.asList(
                null,
                "",
                "io.github.Benedetto597.rescuecam",
                "io.rescuecam",
                "com.s23plus.camera",
                "com.samsung.cam",
                "com.galaxy.rescue",
                "com.android.rescuecam",
                "com.google.rescuecam"
        );
        for (String badId : invalidIds) {
            assertFalse(PlayStoreCompliancePolicy.validateApplicationId(badId).isEmpty(),
                    "applicationId invalido debe rechazarse: " + badId);
        }
    }

    private static void testSdkAndBackupAndAccessibilityValidation() {
        PlayStoreCompliancePolicy.ManifestSnapshot lowTargetSdk = new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                30, 35, 7, "2.1.0", false, false,
                PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS,
                CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                CameraConfig.MEDIA_RELATIVE_PATH, CameraConfig.VIDEO_RELATIVE_PATH
        );
        assertFalse(PlayStoreCompliancePolicy.audit(lowTargetSdk).compliant, "targetSdk < 36 debe fallar");

        PlayStoreCompliancePolicy.ManifestSnapshot lowMinSdk = new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                29, 36, 7, "2.1.0", false, false,
                PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS,
                CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                CameraConfig.MEDIA_RELATIVE_PATH, CameraConfig.VIDEO_RELATIVE_PATH
        );
        assertFalse(PlayStoreCompliancePolicy.audit(lowMinSdk).compliant, "minSdk < 30 debe fallar");

        PlayStoreCompliancePolicy.ManifestSnapshot invertedSdk = new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                37, 36, 7, "2.1.0", false, false,
                PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS,
                CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                CameraConfig.MEDIA_RELATIVE_PATH, CameraConfig.VIDEO_RELATIVE_PATH
        );
        assertFalse(PlayStoreCompliancePolicy.audit(invertedSdk).compliant, "minSdk > targetSdk debe fallar");

        PlayStoreCompliancePolicy.ManifestSnapshot backupEnabled = new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                30, 36, 7, "2.1.0", true, false,
                PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS,
                CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                CameraConfig.MEDIA_RELATIVE_PATH, CameraConfig.VIDEO_RELATIVE_PATH
        );
        assertFalse(PlayStoreCompliancePolicy.audit(backupEnabled).compliant, "allowBackup = true debe fallar");

        PlayStoreCompliancePolicy.ManifestSnapshot accessibilityEnabled = new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                30, 36, 7, "2.1.0", false, true,
                PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS,
                CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                CameraConfig.MEDIA_RELATIVE_PATH, CameraConfig.VIDEO_RELATIVE_PATH
        );
        assertFalse(PlayStoreCompliancePolicy.audit(accessibilityEnabled).compliant,
                "hasAccessibilityService = true debe fallar");
    }

    private static void testForbiddenAndUnknownPermissionsValidation() {
        for (String forbidden : PlayStoreCompliancePolicy.FORBIDDEN_PERMISSIONS) {
            Set<String> perms = new LinkedHashSet<>(PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS);
            perms.add(forbidden);
            PlayStoreCompliancePolicy.ManifestSnapshot snapshot = new PlayStoreCompliancePolicy.ManifestSnapshot(
                    CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                    30, 36, 7, "2.1.0", false, false,
                    perms,
                    CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                    CameraConfig.MEDIA_RELATIVE_PATH, CameraConfig.VIDEO_RELATIVE_PATH
            );
            PlayStoreCompliancePolicy.ComplianceReport report = PlayStoreCompliancePolicy.audit(snapshot);
            assertFalse(report.compliant, "Permiso prohibido debe fallar auditoria: " + forbidden);
        }

        Set<String> unknownPerms = new LinkedHashSet<>(PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS);
        unknownPerms.add("android.permission.READ_CONTACTS");
        PlayStoreCompliancePolicy.ManifestSnapshot unknownSnapshot = new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                30, 36, 7, "2.1.0", false, false,
                unknownPerms,
                CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                CameraConfig.MEDIA_RELATIVE_PATH, CameraConfig.VIDEO_RELATIVE_PATH
        );
        assertFalse(PlayStoreCompliancePolicy.audit(unknownSnapshot).compliant,
                "Permiso desconocido fuera de ALLOWED_PERMISSIONS debe fallar");
    }

    private static void testPrivacyUrlAndDisclaimerValidation() {
        assertTrue(PlayStoreCompliancePolicy.validatePrivacyPolicyUrl(CameraConfig.PRIVACY_POLICY_URL).isEmpty(),
                "PRIVACY_POLICY_URL oficial debe ser valida");
        assertFalse(PlayStoreCompliancePolicy.validatePrivacyPolicyUrl(null).isEmpty(),
                "URL null debe fallar");
        assertFalse(PlayStoreCompliancePolicy.validatePrivacyPolicyUrl("http://benedetto597.github.io/camera-rescue/privacy-policy.html").isEmpty(),
                "URL HTTP no segura debe fallar");
        assertFalse(PlayStoreCompliancePolicy.validatePrivacyPolicyUrl("https://").isEmpty(),
                "URL sin host debe fallar");

        assertTrue(PlayStoreCompliancePolicy.validateDisclaimer(CameraConfig.TRADEMARK_DISCLAIMER).isEmpty(),
                "TRADEMARK_DISCLAIMER oficial debe ser valido");
        assertFalse(PlayStoreCompliancePolicy.validateDisclaimer(null).isEmpty(),
                "Disclaimer null debe fallar");
        assertFalse(PlayStoreCompliancePolicy.validateDisclaimer("").isEmpty(),
                "Disclaimer vacio debe fallar");
        assertFalse(PlayStoreCompliancePolicy.validateDisclaimer("App de cámara independiente.").isEmpty(),
                "Disclaimer incompleto sin Samsung ni compatibilidad debe fallar");
    }

    private static void testLegacyMediaPathsRejected() {
        PlayStoreCompliancePolicy.ManifestSnapshot legacyImagePath = new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                30, 36, 7, "2.1.0", false, false,
                PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS,
                CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                "Pictures/S23Camera", CameraConfig.VIDEO_RELATIVE_PATH
        );
        assertFalse(PlayStoreCompliancePolicy.audit(legacyImagePath).compliant,
                "Ruta legada Pictures/S23Camera debe rechazarse");

        PlayStoreCompliancePolicy.ManifestSnapshot legacyVideoPath = new PlayStoreCompliancePolicy.ManifestSnapshot(
                CameraConfig.PLAY_APPLICATION_ID, CameraConfig.APP_TITLE,
                30, 36, 7, "2.1.0", false, false,
                PlayStoreCompliancePolicy.ALLOWED_PERMISSIONS,
                CameraConfig.PRIVACY_POLICY_URL, CameraConfig.TRADEMARK_DISCLAIMER,
                CameraConfig.MEDIA_RELATIVE_PATH, "Movies/S23Camera"
        );
        assertFalse(PlayStoreCompliancePolicy.audit(legacyVideoPath).compliant,
                "Ruta legada Movies/S23Camera debe rechazarse");
    }

    private static void testOfflineDataSafetySummaryInvariants() {
        PlayStoreCompliancePolicy.DataSafetySummary summary =
                PlayStoreCompliancePolicy.buildOfflineDataSafetySummary();
        assertFalse(summary.collectsUserData, "collectsUserData debe ser false");
        assertFalse(summary.sharesDataWithThirdParties, "sharesDataWithThirdParties debe ser false");
        assertFalse(summary.usesInternetPermission, "usesInternetPermission debe ser false");
        assertFalse(summary.usesAccessibilityApi, "usesAccessibilityApi debe ser false");
        assertTrue(summary.localMediaPaths.contains("Pictures/RescueCam")
                        && summary.localMediaPaths.contains("Movies/RescueCam"),
                "localMediaPaths debe incluir Pictures/RescueCam y Movies/RescueCam");
        assertTrue(summary.permissionsSummary.contains("CAMERA")
                        && summary.permissionsSummary.contains("RECORD_AUDIO")
                        && summary.permissionsSummary.contains("BIND_QUICK_SETTINGS_TILE"),
                "permissionsSummary debe listar los 3 permisos permitidos");
        assertEquals(CameraConfig.PRIVACY_POLICY_URL, summary.privacyPolicyUrl,
                "privacyPolicyUrl debe coincidir con CameraConfig");
        assertEquals(CameraConfig.TRADEMARK_DISCLAIMER, summary.trademarkDisclaimer,
                "trademarkDisclaimer debe coincidir con CameraConfig");
    }

    private static void testWorkspaceArtifactsMeetPlayStorePolicy() {
        Path repoRoot = resolveRepoRoot();
        assertTrue(repoRoot != null, "No se pudo localizar la raiz del repositorio con app/src/main/AndroidManifest.xml");

        Path manifestPath = repoRoot.resolve("app/src/main/AndroidManifest.xml");
        Path gradlePath = repoRoot.resolve("app/build.gradle.kts");
        Path privacyPolicyPath = repoRoot.resolve("docs/privacy-policy.html");

        assertTrue(Files.exists(manifestPath), "Debe existir app/src/main/AndroidManifest.xml");
        assertTrue(Files.exists(gradlePath), "Debe existir app/build.gradle.kts");
        assertTrue(Files.exists(privacyPolicyPath), "Debe existir docs/privacy-policy.html");

        String manifest = readUtf8(manifestPath);
        assertFalse(manifest.contains("BIND_ACCESSIBILITY_SERVICE"),
                "AndroidManifest.xml no debe declarar BIND_ACCESSIBILITY_SERVICE");
        assertFalse(manifest.contains("RearCaptureService"),
                "AndroidManifest.xml no debe referenciar RearCaptureService");
        assertFalse(manifest.contains("android.permission.INTERNET"),
                "AndroidManifest.xml no debe declarar android.permission.INTERNET");
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"),
                "AndroidManifest.xml no debe declarar READ_EXTERNAL_STORAGE");
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"),
                "AndroidManifest.xml no debe declarar WRITE_EXTERNAL_STORAGE");
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"),
                "AndroidManifest.xml no debe declarar MANAGE_EXTERNAL_STORAGE");
        assertFalse(manifest.contains("screenOrientation=\"portrait\""),
                "AndroidManifest.xml no debe bloquear screenOrientation=\"portrait\"");
        assertFalse(manifest.contains("Samsung Camera Rescue"),
                "AndroidManifest.xml no debe contener la marca heredada Samsung Camera Rescue");

        assertTrue(manifest.contains("android:allowBackup=\"false\""),
                "AndroidManifest.xml debe declarar android:allowBackup=\"false\"");
        assertTrue(manifest.contains("Rescue Cam: Sensor Recovery"),
                "AndroidManifest.xml debe declarar la etiqueta Rescue Cam: Sensor Recovery");
        assertTrue(manifest.contains("android.permission.CAMERA"),
                "AndroidManifest.xml debe declarar android.permission.CAMERA");
        assertTrue(manifest.contains("com.s23plus.camera.MainActivity"),
                "AndroidManifest.xml debe declarar com.s23plus.camera.MainActivity");

        String gradle = readUtf8(gradlePath);
        assertTrue(gradle.contains("applicationId = \"io.github.benedetto597.rescuecam\""),
                "app/build.gradle.kts debe configurar applicationId = \"io.github.benedetto597.rescuecam\"");
        assertTrue(gradle.contains("compileSdk = 36"),
                "app/build.gradle.kts debe configurar compileSdk = 36");
        assertTrue(gradle.contains("targetSdk = 36"),
                "app/build.gradle.kts debe configurar targetSdk = 36");
        assertTrue(gradle.contains("versionCode = 7"),
                "app/build.gradle.kts debe configurar versionCode = 7");
        assertTrue(gradle.contains("versionName = \"2.1.0\""),
                "app/build.gradle.kts debe configurar versionName = \"2.1.0\"");
        assertTrue(gradle.contains("isMinifyEnabled = true"),
                "app/build.gradle.kts debe habilitar isMinifyEnabled = true");
        assertTrue(gradle.contains("isShrinkResources = true"),
                "app/build.gradle.kts debe habilitar isShrinkResources = true");
        assertFalse(gradle.contains("signingConfigs.getByName(\"debug\")"),
                "app/build.gradle.kts no debe usar signingConfigs.getByName(\"debug\") en release");

        String privacyPolicy = readUtf8(privacyPolicyPath);
        assertTrue(!privacyPolicy.trim().isEmpty(), "docs/privacy-policy.html no debe estar vacio");
        assertTrue(privacyPolicy.contains("io.github.benedetto597.rescuecam"),
                "docs/privacy-policy.html debe incluir io.github.benedetto597.rescuecam");
        assertTrue(privacyPolicy.contains("Rescue Cam: Sensor Recovery"),
                "docs/privacy-policy.html debe incluir Rescue Cam: Sensor Recovery");
        assertTrue(privacyPolicy.contains("Samsung Electronics Co., Ltd."),
                "docs/privacy-policy.html debe incluir Samsung Electronics Co., Ltd.");
        assertTrue(privacyPolicy.contains("100% offline"),
                "docs/privacy-policy.html debe declarar 100% offline");
        assertTrue(privacyPolicy.contains("No data collected"),
                "docs/privacy-policy.html debe declarar No data collected");
    }

    private static Path resolveRepoRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 5 && current != null; i++) {
            if (Files.exists(current.resolve("app/src/main/AndroidManifest.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("No se pudo leer archivo requerido: " + path, e);
        }
    }

    private static void assertUnmodifiableSet(Set<String> set, String message) {
        boolean threw = false;
        try {
            set.clear();
        } catch (UnsupportedOperationException expected) {
            threw = true;
        }
        assertTrue(threw, message);
    }

    private static void assertUnmodifiableList(List<String> list, String message) {
        boolean threw = false;
        try {
            list.add("x");
        } catch (UnsupportedOperationException expected) {
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
