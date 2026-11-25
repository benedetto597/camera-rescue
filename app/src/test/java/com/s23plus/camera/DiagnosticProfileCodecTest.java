package com.s23plus.camera;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pruebas unitarias TDD para DiagnosticProfileCodec (JSON RFC-8259 estricto en Java puro,
 * validacion de esquema y limites, nombres de archivo seguros y compatibilidad de modelo).
 */
public final class DiagnosticProfileCodecTest {

    public static void main(String[] args) {
        testRoundTripWithSpecialCharactersUnicodeAndMultipleSensors();
        testDecodeRejectsNullBlankMalformedTrailingAndDuplicateKeys();
        testDecodeRejectsUnsupportedSchemaVersionAndNegativeTimestamp();
        testDecodeRejectsOversizedPayloadAndTooManySensors();
        testDecodeAndEncodeRejectUnauthorizedQuickTileAction();
        testDecodeRejectsInvalidEnumsAndViewerPreferences();
        testDecodeRejectsInvalidSensorFields();
        testDecodeRejectsMissingFieldsAndWrongValueTypes();
        testEncodeValidatesStrictConstraints();
        testBuildSuggestedFileNameAndIsCompatibleModel();
        testDecodeAcceptsValidJsonEscapeSequences();
        System.out.println("PASS: Todas las pruebas unitarias de DiagnosticProfileCodecTest finalizaron con exito (11/11).");
    }

    private static DiagnosticProfileCodec.DiagnosticProfile createValidSampleProfile() {
        Set<String> selectedIds = new LinkedHashSet<>(Arrays.asList("1", "56", "52"));
        Set<String> completedSteps = new LinkedHashSet<>(Arrays.asList("step_keep_mode", "step_side_key"));
        DiagnosticProfileCodec.ViewerPreferencesSnapshot viewerPrefs =
                new DiagnosticProfileCodec.ViewerPreferencesSnapshot(
                        "3",
                        CameraViewerMath.GridMode.RULE_OF_THIRDS,
                        5,
                        CameraViewerMath.FlashMode.TORCH,
                        1.5f
                );
        List<DiscoveredSensor> sensors = Arrays.asList(
                new DiscoveredSensor(
                        "1",
                        "Frontal \"S5K3LU\"\\12MP",
                        1,
                        4000,
                        3000,
                        3.3f,
                        2.2f,
                        SensorHealth.HEALTHY,
                        "Óptico frontal\ncon tildes áéíóúñ\tOK"
                ),
                new DiscoveredSensor(
                        "56",
                        "Trasera Principal 1x",
                        0,
                        4080,
                        3060,
                        5.4f,
                        1.8f,
                        SensorHealth.HAL_PRIVATE,
                        "Modo Pro requerido (sin Ultra-Wide)"
                ),
                new DiscoveredSensor(
                        "2",
                        "Ultra-Wide 0.6x",
                        0,
                        4000,
                        3000,
                        1.9f,
                        2.2f,
                        SensorHealth.DAMAGED,
                        "Error I2C/EEPROM"
                )
        );
        return new DiagnosticProfileCodec.DiagnosticProfile(
                DiagnosticProfileCodec.CURRENT_SCHEMA_VERSION,
                "2.0.0",
                1700000000000L,
                "SM-S916U1",
                "Galaxy S23+ \"Edición\"\nEspecial áéíóúñ",
                ShootingModeMap.OneUiGeneration.ONE_UI_6,
                selectedIds,
                CameraConfig.ACTION_LAUNCH_REAR_PRO,
                "Rescate\\Pro\t\"1x\"",
                completedSteps,
                viewerPrefs,
                sensors
        );
    }

    private static void testRoundTripWithSpecialCharactersUnicodeAndMultipleSensors() {
        DiagnosticProfileCodec.DiagnosticProfile original = createValidSampleProfile();

        String json = DiagnosticProfileCodec.encode(original);
        assertTrue(json != null && !json.trim().isEmpty(), "JSON codificado no debe estar vacio");
        assertTrue(json.contains("\\\""), "JSON debe escapar comillas dobles");
        assertTrue(json.contains("\\\\"), "JSON debe escapar backslashes");
        assertTrue(json.contains("\\n"), "JSON debe escapar saltos de linea");
        assertTrue(json.contains("\\t"), "JSON debe escapar tabuladores");

        DiagnosticProfileCodec.DiagnosticProfile decoded = DiagnosticProfileCodec.decode(json);

        assertEquals(1, decoded.schemaVersion, "schemaVersion");
        assertEquals("2.0.0", decoded.appVersion, "appVersion");
        assertEquals(1700000000000L, decoded.exportedAtEpochMs, "exportedAtEpochMs");
        assertEquals("SM-S916U1", decoded.deviceModel, "deviceModel");
        assertEquals(original.marketingName, decoded.marketingName, "marketingName");
        assertEquals(ShootingModeMap.OneUiGeneration.ONE_UI_6, decoded.oneUiGeneration, "oneUiGeneration");
        assertEquals(new ArrayList<>(original.selectedSensorIds), new ArrayList<>(decoded.selectedSensorIds),
                "selectedSensorIds");
        assertEquals(CameraConfig.ACTION_LAUNCH_REAR_PRO, decoded.quickTileAction, "quickTileAction");
        assertEquals(original.quickTileLabel, decoded.quickTileLabel, "quickTileLabel");
        assertEquals(new ArrayList<>(original.completedAssistantStepIds),
                new ArrayList<>(decoded.completedAssistantStepIds), "completedAssistantStepIds");

        assertEquals("3", decoded.viewerPreferences.preferredCameraId, "preferredCameraId");
        assertEquals(CameraViewerMath.GridMode.RULE_OF_THIRDS, decoded.viewerPreferences.gridMode, "gridMode");
        assertEquals(5, decoded.viewerPreferences.timerSeconds, "timerSeconds");
        assertEquals(CameraViewerMath.FlashMode.TORCH, decoded.viewerPreferences.flashMode, "flashMode");
        assertFloatEquals(1.5f, decoded.viewerPreferences.preferredAperture, 0.001f, "preferredAperture");

        assertEquals(3, decoded.discoveredSensors.size(), "discoveredSensors size");
        DiscoveredSensor s0 = decoded.discoveredSensors.get(0);
        assertEquals("1", s0.id, "s0.id");
        assertEquals("Frontal \"S5K3LU\"\\12MP", s0.name, "s0.name");
        assertEquals(1, s0.facing, "s0.facing");
        assertEquals(4000, s0.width, "s0.width");
        assertEquals(3000, s0.height, "s0.height");
        assertFloatEquals(3.3f, s0.focalLengthMm, 0.001f, "s0.focalLengthMm");
        assertFloatEquals(2.2f, s0.apertureF, 0.001f, "s0.apertureF");
        assertEquals(SensorHealth.HEALTHY, s0.health, "s0.health");
        assertEquals("Óptico frontal\ncon tildes áéíóúñ\tOK", s0.statusDetail, "s0.statusDetail");

        DiscoveredSensor s2 = decoded.discoveredSensors.get(2);
        assertEquals(SensorHealth.DAMAGED, s2.health, "s2.health");

        // Inmutabilidad de colecciones
        assertThrowsUnsupported(() -> decoded.selectedSensorIds.add("99"), "selectedSensorIds debe ser inmutable");
        assertThrowsUnsupported(() -> decoded.completedAssistantStepIds.add("x"),
                "completedAssistantStepIds debe ser inmutable");
        assertThrowsUnsupported(() -> decoded.discoveredSensors.add(s0), "discoveredSensors debe ser inmutable");

        // Defaults de ViewerPreferencesSnapshot
        DiagnosticProfileCodec.ViewerPreferencesSnapshot defaults =
                DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults();
        assertEquals(CameraConfig.FRONT_CAMERA_WIDE_ID, defaults.preferredCameraId, "default cameraId");
        assertEquals(CameraViewerMath.GridMode.OFF, defaults.gridMode, "default gridMode");
        assertEquals(0, defaults.timerSeconds, "default timerSeconds");
        assertEquals(CameraViewerMath.FlashMode.OFF, defaults.flashMode, "default flashMode");
        assertFloatEquals(0.0f, defaults.preferredAperture, 0.001f, "default preferredAperture");
    }

    private static void testDecodeRejectsNullBlankMalformedTrailingAndDuplicateKeys() {
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(null), "null JSON");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(""), "empty JSON");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("   \n\t "), "blank JSON");

        // Root no es objeto
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("[]"), "root array");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("123"), "root number");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("\"hola\""), "root string");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("true"), "root boolean");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("null"), "root null");

        // Sintaxis invalida
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": 1"), "unclosed brace");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": 1 \"appVersion\": \"2.0.0\"}"),
                "missing comma");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": 1,}"), "trailing comma");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": \"unclosed}"), "unclosed string");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": \"bad\\x20\"}"),
                "invalid escape sequence");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": \"bad\\u12\"}"),
                "short unicode escape");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": \"line\nbreak\"}"),
                "unescaped control char in string");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": 01}"), "leading zero number");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": +1}"), "leading plus number");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": 1.}"), "trailing dot number");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode("{\"schemaVersion\": .5}"), "leading dot number");

        String validJson = DiagnosticProfileCodec.encode(createValidSampleProfile());

        // Trailing garbage tokens
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(validJson + " extra"), "trailing garbage");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(validJson + " {}"), "trailing object");

        // Claves duplicadas en raiz
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                "{\"schemaVersion\": 1, \"schemaVersion\": 1}"), "duplicate root key");

        // Claves duplicadas en objeto anidado viewerPreferences
        String dupViewer = validJson.replace(
                "\"timerSeconds\": 5,",
                "\"timerSeconds\": 5,\n    \"timerSeconds\": 10,"
        );
        assertTrue(!dupViewer.equals(validJson), "test setup debe mutar viewerPreferences");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(dupViewer), "duplicate nested key");

        // Claves duplicadas en sensor
        String dupSensor = validJson.replace(
                "\"facing\": 1,",
                "\"facing\": 1,\n      \"facing\": 0,"
        );
        assertTrue(!dupSensor.equals(validJson), "test setup debe mutar sensor");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(dupSensor), "duplicate sensor key");
    }

    private static void testDecodeRejectsUnsupportedSchemaVersionAndNegativeTimestamp() {
        String validJson = DiagnosticProfileCodec.encode(createValidSampleProfile());

        String v0 = validJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 0");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(v0), "schemaVersion 0");

        String v2 = validJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(v2), "schemaVersion 2");

        String vFloat = validJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1.5");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(vFloat), "schemaVersion 1.5");

        String negTime = validJson.replace("\"exportedAtEpochMs\": 1700000000000", "\"exportedAtEpochMs\": -1");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(negTime), "negative exportedAtEpochMs");
    }

    private static void testDecodeRejectsOversizedPayloadAndTooManySensors() {
        // Excede MAX_JSON_BYTES por bytes UTF-8 aunque tenga menos caracteres
        StringBuilder hugeMultiByte = new StringBuilder("{\"deviceModel\":\"");
        for (int i = 0; i < 135_000; i++) {
            hugeMultiByte.append('á'); // 2 bytes UTF-8 por caracter => > 262,144 bytes
        }
        hugeMultiByte.append("\"}");
        assertTrue(hugeMultiByte.toString().getBytes(StandardCharsets.UTF_8).length > DiagnosticProfileCodec.MAX_JSON_BYTES,
                "Payload de prueba debe superar MAX_JSON_BYTES");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(hugeMultiByte.toString()), "oversized UTF-8 JSON");

        // Mas de MAX_SENSORS (65)
        DiagnosticProfileCodec.DiagnosticProfile base = createValidSampleProfile();
        List<DiscoveredSensor> tooMany = new ArrayList<>();
        for (int i = 0; i < DiagnosticProfileCodec.MAX_SENSORS + 1; i++) {
            tooMany.add(new DiscoveredSensor(
                    String.valueOf(i), "S" + i, 0, 4000, 3000, 5.0f, 1.8f, SensorHealth.HEALTHY, "OK"));
        }
        // Construimos JSON manual con 65 sensores para probar decode
        String baseJson = DiagnosticProfileCodec.encode(base);
        String singleSensorJson = extractFirstSensorObject(baseJson);
        StringBuilder sensorsArray = new StringBuilder("[");
        for (int i = 0; i < DiagnosticProfileCodec.MAX_SENSORS + 1; i++) {
            if (i > 0) {
                sensorsArray.append(",");
            }
            sensorsArray.append(singleSensorJson.replace("\"id\": \"1\"", "\"id\": \"" + i + "\""));
        }
        sensorsArray.append("]");
        int idxStart = baseJson.indexOf("\"discoveredSensors\": [");
        int idxEnd = baseJson.lastIndexOf("]");
        String jsonWith65Sensors = baseJson.substring(0, idxStart)
                + "\"discoveredSensors\": " + sensorsArray
                + baseJson.substring(idxEnd + 1);
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(jsonWith65Sensors), "> MAX_SENSORS en decode");

        // deviceModel blank o > MAX_STRING_LENGTH
        String blankModel = baseJson.replace("\"deviceModel\": \"SM-S916U1\"", "\"deviceModel\": \"   \"");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(blankModel), "blank deviceModel");

        String longStr = repeat('A', DiagnosticProfileCodec.MAX_STRING_LENGTH + 1);
        String tooLongModel = baseJson.replace("\"deviceModel\": \"SM-S916U1\"", "\"deviceModel\": \"" + longStr + "\"");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(tooLongModel), "deviceModel > MAX_STRING_LENGTH");

        String tooLongMarketing = baseJson.replace("\"marketingName\": " + quoteExpected(base.marketingName),
                "\"marketingName\": \"" + longStr + "\"");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(tooLongMarketing),
                "marketingName > MAX_STRING_LENGTH");
    }

    private static void testDecodeAndEncodeRejectUnauthorizedQuickTileAction() {
        String validJson = DiagnosticProfileCodec.encode(createValidSampleProfile());

        String injected = validJson.replace(
                CameraConfig.ACTION_LAUNCH_REAR_PRO,
                "android.intent.action.DELETE"
        );
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(injected), "unauthorized quickTileAction decode");

        DiagnosticProfileCodec.DiagnosticProfile badProfile = new DiagnosticProfileCodec.DiagnosticProfile(
                1, "2.0.0", 100L, "SM-S916U1", "S23+", ShootingModeMap.OneUiGeneration.ONE_UI_6,
                Collections.singleton("1"), "com.evil.HIJACK", "Evil",
                Collections.emptySet(), DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults(),
                Collections.emptyList()
        );
        assertThrowsValidation(() -> DiagnosticProfileCodec.encode(badProfile), "unauthorized quickTileAction encode");

        // Las 6 acciones de allowlist deben aceptarse
        String[] allowed = new String[]{
                CameraConfig.ACTION_LAUNCH_REAR_PRO,
                CameraConfig.ACTION_LAUNCH_REAR_QR,
                CameraConfig.ACTION_LAUNCH_REAR_VIDEO,
                CameraConfig.ACTION_LAUNCH_FRONT,
                CameraConfig.ACTION_QUICK_RECOVERY,
                CameraConfig.ACTION_OPEN_INTEGRATED_VIEWER
        };
        for (String action : allowed) {
            DiagnosticProfileCodec.DiagnosticProfile p = new DiagnosticProfileCodec.DiagnosticProfile(
                    1, "2.0.0", 100L, "SM-S916U1", "S23+", ShootingModeMap.OneUiGeneration.ONE_UI_6,
                    Collections.singleton("1"), action, "Tile",
                    Collections.emptySet(), DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults(),
                    Collections.emptyList()
            );
            DiagnosticProfileCodec.DiagnosticProfile roundTrip =
                    DiagnosticProfileCodec.decode(DiagnosticProfileCodec.encode(p));
            assertEquals(action, roundTrip.quickTileAction, "Allowed action " + action);
        }
    }

    private static void testDecodeRejectsInvalidEnumsAndViewerPreferences() {
        String validJson = DiagnosticProfileCodec.encode(createValidSampleProfile());

        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"ONE_UI_6\"", "\"ONE_UI_99\"")), "unknown oneUiGeneration");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"RULE_OF_THIRDS\"", "\"GOLDEN_SPIRAL\"")), "unknown gridMode");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"TORCH\"", "\"LASER\"")), "unknown flashMode");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"timerSeconds\": 5", "\"timerSeconds\": 7")), "invalid timerSeconds 7");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"timerSeconds\": 5", "\"timerSeconds\": -3")), "invalid timerSeconds -3");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"preferredAperture\": 1.5", "\"preferredAperture\": -0.5")),
                "negative preferredAperture");

        // Constructor sanitizacion de ViewerPreferencesSnapshot
        DiagnosticProfileCodec.ViewerPreferencesSnapshot sanitized =
                new DiagnosticProfileCodec.ViewerPreferencesSnapshot("  ", null, 42, null, 0.0f);
        assertEquals(CameraConfig.FRONT_CAMERA_WIDE_ID, sanitized.preferredCameraId, "blank cameraId fallback");
        assertEquals(CameraViewerMath.GridMode.OFF, sanitized.gridMode, "null gridMode fallback");
        assertEquals(0, sanitized.timerSeconds, "invalid timerSeconds sanitized to 0");
        assertEquals(CameraViewerMath.FlashMode.OFF, sanitized.flashMode, "null flashMode fallback");
    }

    private static void testDecodeRejectsInvalidSensorFields() {
        String validJson = DiagnosticProfileCodec.encode(createValidSampleProfile());

        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"id\": \"1\"", "\"id\": \"   \"")), "blank sensor id");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"facing\": 1", "\"facing\": 2")), "invalid facing 2");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"facing\": 1", "\"facing\": -1")), "invalid facing -1");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"width\": 4000", "\"width\": -10")), "negative sensor width");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"height\": 3000", "\"height\": -1")), "negative sensor height");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"focalLengthMm\": 3.3", "\"focalLengthMm\": -1.0")), "negative focalLengthMm");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"apertureF\": 2.2", "\"apertureF\": -2.2")), "negative apertureF");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"HEALTHY\"", "\"BROKEN\"")), "unknown SensorHealth");
    }

    private static void testDecodeRejectsMissingFieldsAndWrongValueTypes() {
        String validJson = DiagnosticProfileCodec.encode(createValidSampleProfile());

        // Campo faltante
        String missingDeviceModel = validJson.replace("  \"deviceModel\": \"SM-S916U1\",\n", "");
        assertTrue(!missingDeviceModel.equals(validJson), "test setup debe quitar deviceModel");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(missingDeviceModel), "missing deviceModel");

        // Tipos erroneos
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\"")), "string schemaVersion");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"exportedAtEpochMs\": 1700000000000", "\"exportedAtEpochMs\": \"1700000000000\"")),
                "string exportedAtEpochMs");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"deviceModel\": \"SM-S916U1\"", "\"deviceModel\": 916")), "number deviceModel");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"timerSeconds\": 5", "\"timerSeconds\": true")), "boolean timerSeconds");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"width\": 4000", "\"width\": \"4000\"")), "string width");
        assertThrowsValidation(() -> DiagnosticProfileCodec.decode(
                validJson.replace("\"selectedSensorIds\": [", "\"selectedSensorIds\": {")),
                "object instead of array");
    }

    private static void testEncodeValidatesStrictConstraints() {
        assertThrowsValidation(() -> DiagnosticProfileCodec.encode(null), "encode null profile");

        DiagnosticProfileCodec.DiagnosticProfile badSchema = new DiagnosticProfileCodec.DiagnosticProfile(
                2, "2.0.0", 100L, "SM-S916U1", "S23+", ShootingModeMap.OneUiGeneration.ONE_UI_6,
                Collections.emptySet(), CameraConfig.ACTION_LAUNCH_REAR_PRO, "Pro",
                Collections.emptySet(), DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults(),
                Collections.emptyList()
        );
        assertThrowsValidation(() -> DiagnosticProfileCodec.encode(badSchema), "encode bad schemaVersion");

        DiagnosticProfileCodec.DiagnosticProfile badAperture = new DiagnosticProfileCodec.DiagnosticProfile(
                1, "2.0.0", 100L, "SM-S916U1", "S23+", ShootingModeMap.OneUiGeneration.ONE_UI_6,
                Collections.emptySet(), CameraConfig.ACTION_LAUNCH_REAR_PRO, "Pro",
                Collections.emptySet(),
                new DiagnosticProfileCodec.ViewerPreferencesSnapshot(
                        "1", CameraViewerMath.GridMode.OFF, 0, CameraViewerMath.FlashMode.OFF, Float.NaN),
                Collections.emptyList()
        );
        assertThrowsValidation(() -> DiagnosticProfileCodec.encode(badAperture), "encode NaN aperture");

        DiagnosticProfileCodec.DiagnosticProfile badSensor = new DiagnosticProfileCodec.DiagnosticProfile(
                1, "2.0.0", 100L, "SM-S916U1", "S23+", ShootingModeMap.OneUiGeneration.ONE_UI_6,
                Collections.emptySet(), CameraConfig.ACTION_LAUNCH_REAR_PRO, "Pro",
                Collections.emptySet(), DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults(),
                Collections.singletonList(new DiscoveredSensor(
                        "1", "Main", 0, -100, 3000, 5.0f, 1.8f, SensorHealth.HEALTHY, "bad"))
        );
        assertThrowsValidation(() -> DiagnosticProfileCodec.encode(badSensor), "encode negative sensor width");

        DiagnosticProfileCodec.DiagnosticProfile badTimestamp = new DiagnosticProfileCodec.DiagnosticProfile(
                1, "2.0.0", -1L, "SM-S916U1", "S23+", ShootingModeMap.OneUiGeneration.ONE_UI_6,
                Collections.emptySet(), CameraConfig.ACTION_LAUNCH_REAR_PRO, "Pro",
                Collections.emptySet(), DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults(),
                Collections.emptyList()
        );
        assertThrowsValidation(() -> DiagnosticProfileCodec.encode(badTimestamp), "encode negative timestamp");

        List<DiscoveredSensor> tooMany = new ArrayList<>();
        for (int i = 0; i < DiagnosticProfileCodec.MAX_SENSORS + 1; i++) {
            tooMany.add(new DiscoveredSensor(
                    String.valueOf(i), "S" + i, 0, 4000, 3000, 5.0f, 1.8f, SensorHealth.HEALTHY, "OK"));
        }
        DiagnosticProfileCodec.DiagnosticProfile tooManyProfile = new DiagnosticProfileCodec.DiagnosticProfile(
                1, "2.0.0", 100L, "SM-S916U1", "S23+", ShootingModeMap.OneUiGeneration.ONE_UI_6,
                Collections.emptySet(), CameraConfig.ACTION_LAUNCH_REAR_PRO, "Pro",
                Collections.emptySet(), DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults(),
                tooMany
        );
        assertThrowsValidation(() -> DiagnosticProfileCodec.encode(tooManyProfile), "encode > MAX_SENSORS");
    }

    private static void testBuildSuggestedFileNameAndIsCompatibleModel() {
        // 1700000000000L = 2023-11-14T22:13:20Z -> 20231114
        assertEquals("camera-rescue-SM-S916U1-20231114.json",
                DiagnosticProfileCodec.buildSuggestedFileName("SM-S916U1", 1700000000000L),
                "nombre de archivo estandar");
        assertEquals("camera-rescue-Galaxy-S23-20231114.json",
                DiagnosticProfileCodec.buildSuggestedFileName("Galaxy S23+", 1700000000000L),
                "espacios y signos sanitizados");

        String traversal = DiagnosticProfileCodec.buildSuggestedFileName("../../etc/passwd", 0L);
        assertTrue(!traversal.contains("..") && !traversal.contains("/"),
                "no debe contener path traversal: " + traversal);
        assertTrue(traversal.startsWith("camera-rescue-") && traversal.endsWith("-19700101.json"),
                "formato esperado para epoch 0: " + traversal);

        assertEquals("camera-rescue-galaxy-19700101.json",
                DiagnosticProfileCodec.buildSuggestedFileName("   ///..  ", -500L),
                "fallback galaxy y clamp epoch negativo");
        assertEquals("camera-rescue-galaxy-20231114.json",
                DiagnosticProfileCodec.buildSuggestedFileName(null, 1700000000000L),
                "fallback galaxy en null");

        assertTrue(DiagnosticProfileCodec.isCompatibleModel("SM-S916U1", "sm-s916u1"),
                "compatibilidad case-insensitive");
        assertTrue(DiagnosticProfileCodec.isCompatibleModel("  SM-S916U1  ", "SM-s916u1"),
                "compatibilidad trimmed");
        assertTrue(!DiagnosticProfileCodec.isCompatibleModel("SM-S916U1", "SM-S918B"),
                "modelos distintos deben ser incompatibles");
        assertTrue(!DiagnosticProfileCodec.isCompatibleModel(null, "SM-S916U1"), "null profileModel");
        assertTrue(!DiagnosticProfileCodec.isCompatibleModel("SM-S916U1", null), "null deviceModel");
        assertTrue(!DiagnosticProfileCodec.isCompatibleModel("   ", "SM-S916U1"), "blank profileModel");
    }

    private static void testDecodeAcceptsValidJsonEscapeSequences() {
        DiagnosticProfileCodec.DiagnosticProfile base = createValidSampleProfile();
        String json = DiagnosticProfileCodec.encode(base);
        String withEscapes = json.replace(
                "\"appVersion\": \"2.0.0\"",
                "\"appVersion\": \"2.0\\u002e0\\/\\b\\f\\n\\r\\t\""
        );
        DiagnosticProfileCodec.DiagnosticProfile decoded = DiagnosticProfileCodec.decode(withEscapes);
        assertEquals("2.0.0/\b\f\n\r\t", decoded.appVersion, "secuencias de escape RFC-8259");

        // Caracter de control < 0x20 en encode se escapa como \u0007 y vuelve igual
        DiagnosticProfileCodec.DiagnosticProfile withCtrl = new DiagnosticProfileCodec.DiagnosticProfile(
                1, "2.0.0", 100L, "SM-S916U1", "S23\u0007Plus",
                ShootingModeMap.OneUiGeneration.ONE_UI_6,
                Collections.emptySet(), CameraConfig.ACTION_LAUNCH_REAR_PRO, "Pro",
                Collections.emptySet(), DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults(),
                Collections.emptyList()
        );
        String ctrlJson = DiagnosticProfileCodec.encode(withCtrl);
        assertTrue(ctrlJson.contains("\\u0007"), "control char debe escaparse como \\u0007");
        DiagnosticProfileCodec.DiagnosticProfile decodedCtrl = DiagnosticProfileCodec.decode(ctrlJson);
        assertEquals("S23\u0007Plus", decodedCtrl.marketingName, "control char round-trip");
    }

    private static String extractFirstSensorObject(String json) {
        int start = json.indexOf("\"discoveredSensors\": [");
        int objStart = json.indexOf("{", start);
        int objEnd = json.indexOf("}", objStart);
        return json.substring(objStart, objEnd + 1);
    }

    private static String quoteExpected(String raw) {
        return "\"" + raw
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static void assertThrowsValidation(Runnable action, String label) {
        try {
            action.run();
        } catch (DiagnosticProfileCodec.ValidationException expected) {
            return;
        }
        throw new AssertionError("Se esperaba ValidationException para: " + label);
    }

    private static void assertThrowsUnsupported(Runnable action, String label) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Se esperaba UnsupportedOperationException para: " + label);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " -> esperado: [" + expected + "], actual: [" + actual + "]");
        }
    }

    private static void assertFloatEquals(float expected, float actual, float epsilon, String message) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > epsilon) {
            throw new AssertionError(message + " -> esperado: " + expected + ", actual: " + actual);
        }
    }
}
