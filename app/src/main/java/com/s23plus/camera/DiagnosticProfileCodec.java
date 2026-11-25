package com.s23plus.camera;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Codec offline estricto en Java puro (sin dependencias de android.* ni org.json.*)
 * para exportar e importar perfiles de diagnóstico y configuración (.json) mediante SAF.
 */
public final class DiagnosticProfileCodec {

    public static final int CURRENT_SCHEMA_VERSION = CameraConfig.PROFILE_SCHEMA_VERSION;
    public static final int MAX_JSON_BYTES = 262_144;
    public static final int MAX_SENSORS = 64;
    public static final int MAX_STRING_LENGTH = 512;

    private static final int MAX_JSON_DEPTH = 32;
    private static final int MAX_NUMBER_TOKEN_LENGTH = 64;
    private static final int MAX_FILENAME_MODEL_LENGTH = 64;
    private static final Object JSON_NULL = new Object();
    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US);

    private static final Set<String> ALLOWED_QUICK_TILE_ACTIONS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    CameraConfig.ACTION_LAUNCH_REAR_PRO,
                    CameraConfig.ACTION_LAUNCH_REAR_QR,
                    CameraConfig.ACTION_LAUNCH_REAR_VIDEO,
                    CameraConfig.ACTION_LAUNCH_FRONT,
                    CameraConfig.ACTION_QUICK_RECOVERY,
                    CameraConfig.ACTION_OPEN_INTEGRATED_VIEWER
            ))
    );

    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class ViewerPreferencesSnapshot {
        public final String preferredCameraId;
        public final CameraViewerMath.GridMode gridMode;
        public final int timerSeconds;
        public final CameraViewerMath.FlashMode flashMode;
        public final float preferredAperture;

        public ViewerPreferencesSnapshot(
                String preferredCameraId,
                CameraViewerMath.GridMode gridMode,
                int timerSeconds,
                CameraViewerMath.FlashMode flashMode,
                float preferredAperture
        ) {
            this.preferredCameraId = (preferredCameraId == null || preferredCameraId.trim().isEmpty())
                    ? CameraConfig.FRONT_CAMERA_WIDE_ID
                    : preferredCameraId.trim();
            this.gridMode = gridMode != null ? gridMode : CameraViewerMath.GridMode.OFF;
            this.timerSeconds = CameraViewerMath.sanitizeTimerSeconds(timerSeconds);
            this.flashMode = flashMode != null ? flashMode : CameraViewerMath.FlashMode.OFF;
            this.preferredAperture = preferredAperture;
        }

        public static ViewerPreferencesSnapshot defaults() {
            return new ViewerPreferencesSnapshot(
                    CameraConfig.FRONT_CAMERA_WIDE_ID,
                    CameraViewerMath.GridMode.OFF,
                    0,
                    CameraViewerMath.FlashMode.OFF,
                    0.0f
            );
        }
    }

    public static final class DiagnosticProfile {
        public final int schemaVersion;
        public final String appVersion;
        public final long exportedAtEpochMs;
        public final String deviceModel;
        public final String marketingName;
        public final ShootingModeMap.OneUiGeneration oneUiGeneration;
        public final Set<String> selectedSensorIds;
        public final String quickTileAction;
        public final String quickTileLabel;
        public final Set<String> completedAssistantStepIds;
        public final ViewerPreferencesSnapshot viewerPreferences;
        public final List<DiscoveredSensor> discoveredSensors;

        public DiagnosticProfile(
                int schemaVersion,
                String appVersion,
                long exportedAtEpochMs,
                String deviceModel,
                String marketingName,
                ShootingModeMap.OneUiGeneration oneUiGeneration,
                Set<String> selectedSensorIds,
                String quickTileAction,
                String quickTileLabel,
                Set<String> completedAssistantStepIds,
                ViewerPreferencesSnapshot viewerPreferences,
                List<DiscoveredSensor> discoveredSensors
        ) {
            this.schemaVersion = schemaVersion;
            this.appVersion = appVersion;
            this.exportedAtEpochMs = exportedAtEpochMs;
            this.deviceModel = deviceModel;
            this.marketingName = marketingName;
            this.oneUiGeneration = oneUiGeneration;
            this.selectedSensorIds = selectedSensorIds == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(selectedSensorIds));
            this.quickTileAction = quickTileAction;
            this.quickTileLabel = quickTileLabel;
            this.completedAssistantStepIds = completedAssistantStepIds == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(completedAssistantStepIds));
            this.viewerPreferences = viewerPreferences;
            this.discoveredSensors = discoveredSensors == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(discoveredSensors));
        }
    }

    private DiagnosticProfileCodec() {
        // Clase de utilidad no instanciable
    }

    public static String encode(DiagnosticProfile profile) {
        validateProfileForEncode(profile);

        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\n");
        appendField(sb, 1, "schemaVersion", Integer.toString(profile.schemaVersion), true);
        appendStringField(sb, 1, "appVersion", profile.appVersion, true);
        appendField(sb, 1, "exportedAtEpochMs", Long.toString(profile.exportedAtEpochMs), true);
        appendStringField(sb, 1, "deviceModel", profile.deviceModel, true);
        appendStringField(sb, 1, "marketingName", profile.marketingName, true);
        appendStringField(sb, 1, "oneUiGeneration", profile.oneUiGeneration.name(), true);
        appendStringSetField(sb, 1, "selectedSensorIds", profile.selectedSensorIds, true);
        appendStringField(sb, 1, "quickTileAction", profile.quickTileAction, true);
        appendStringField(sb, 1, "quickTileLabel", profile.quickTileLabel, true);
        appendStringSetField(sb, 1, "completedAssistantStepIds", profile.completedAssistantStepIds, true);
        appendViewerPreferencesField(sb, 1, profile.viewerPreferences, true);
        appendSensorsField(sb, 1, profile.discoveredSensors, false);
        sb.append("}");

        String encoded = sb.toString();
        if (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new ValidationException("Encoded JSON exceeds MAX_JSON_BYTES (" + MAX_JSON_BYTES + ")");
        }
        return encoded;
    }

    public static DiagnosticProfile decode(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new ValidationException("JSON input cannot be null or blank");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new ValidationException("JSON input exceeds MAX_JSON_BYTES (" + MAX_JSON_BYTES + ")");
        }

        Map<String, Object> root = new JsonParser(json).parseRootObject();

        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new ValidationException("Unsupported schemaVersion: " + schemaVersion);
        }

        String appVersion = requireString(root, "appVersion", false);
        long exportedAtEpochMs = requireLong(root, "exportedAtEpochMs");
        if (exportedAtEpochMs < 0L) {
            throw new ValidationException("exportedAtEpochMs must be >= 0");
        }

        String deviceModel = requireString(root, "deviceModel", true);
        String marketingName = requireString(root, "marketingName", false);
        ShootingModeMap.OneUiGeneration oneUiGeneration =
                requireEnum(root, "oneUiGeneration", ShootingModeMap.OneUiGeneration.class);

        Set<String> selectedSensorIds = requireStringSet(root, "selectedSensorIds");

        String quickTileAction = requireString(root, "quickTileAction", true);
        if (!ALLOWED_QUICK_TILE_ACTIONS.contains(quickTileAction)) {
            throw new ValidationException("Unauthorized quickTileAction: " + quickTileAction);
        }
        String quickTileLabel = requireString(root, "quickTileLabel", false);

        Set<String> completedStepIds = requireStringSet(root, "completedAssistantStepIds");
        ViewerPreferencesSnapshot viewerPrefs = parseViewerPreferences(requireObject(root, "viewerPreferences"));
        List<DiscoveredSensor> sensors = parseSensors(requireArray(root, "discoveredSensors"));

        return new DiagnosticProfile(
                schemaVersion,
                appVersion,
                exportedAtEpochMs,
                deviceModel,
                marketingName,
                oneUiGeneration,
                selectedSensorIds,
                quickTileAction,
                quickTileLabel,
                completedStepIds,
                viewerPrefs,
                sensors
        );
    }

    public static String buildSuggestedFileName(String rawModel, long epochMs) {
        String safeModel = sanitizeFileModel(rawModel);
        String datePart = Instant.ofEpochMilli(Math.max(0L, epochMs))
                .atZone(ZoneOffset.UTC)
                .format(FILE_DATE_FORMAT);
        return "camera-rescue-" + safeModel + "-" + datePart + ".json";
    }

    public static boolean isCompatibleModel(String profileModel, String currentDeviceModel) {
        if (profileModel == null || currentDeviceModel == null) {
            return false;
        }
        String a = profileModel.trim();
        String b = currentDeviceModel.trim();
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    private static void validateProfileForEncode(DiagnosticProfile profile) {
        if (profile == null) {
            throw new ValidationException("DiagnosticProfile cannot be null");
        }
        if (profile.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new ValidationException("Unsupported schemaVersion: " + profile.schemaVersion);
        }
        validateString(profile.appVersion, "appVersion", false);
        if (profile.exportedAtEpochMs < 0L) {
            throw new ValidationException("exportedAtEpochMs must be >= 0");
        }
        validateString(profile.deviceModel, "deviceModel", true);
        validateString(profile.marketingName, "marketingName", false);
        if (profile.oneUiGeneration == null) {
            throw new ValidationException("oneUiGeneration cannot be null");
        }
        validateStringCollection(profile.selectedSensorIds, "selectedSensorIds");
        validateString(profile.quickTileAction, "quickTileAction", true);
        if (!ALLOWED_QUICK_TILE_ACTIONS.contains(profile.quickTileAction)) {
            throw new ValidationException("Unauthorized quickTileAction: " + profile.quickTileAction);
        }
        validateString(profile.quickTileLabel, "quickTileLabel", false);
        validateStringCollection(profile.completedAssistantStepIds, "completedAssistantStepIds");

        if (profile.viewerPreferences == null) {
            throw new ValidationException("viewerPreferences cannot be null");
        }
        validateViewerPreferences(profile.viewerPreferences);

        if (profile.discoveredSensors == null) {
            throw new ValidationException("discoveredSensors cannot be null");
        }
        if (profile.discoveredSensors.size() > MAX_SENSORS) {
            throw new ValidationException("discoveredSensors exceeds MAX_SENSORS (" + MAX_SENSORS + ")");
        }
        for (int i = 0; i < profile.discoveredSensors.size(); i++) {
            validateSensor(profile.discoveredSensors.get(i), i);
        }
    }

    private static void validateViewerPreferences(ViewerPreferencesSnapshot prefs) {
        validateString(prefs.preferredCameraId, "viewerPreferences.preferredCameraId", true);
        if (prefs.gridMode == null) {
            throw new ValidationException("viewerPreferences.gridMode cannot be null");
        }
        if (!isValidTimerSeconds(prefs.timerSeconds)) {
            throw new ValidationException("Invalid viewerPreferences.timerSeconds: " + prefs.timerSeconds);
        }
        if (prefs.flashMode == null) {
            throw new ValidationException("viewerPreferences.flashMode cannot be null");
        }
        if (!Float.isFinite(prefs.preferredAperture) || prefs.preferredAperture < 0.0f) {
            throw new ValidationException("Invalid viewerPreferences.preferredAperture: " + prefs.preferredAperture);
        }
    }

    private static void validateSensor(DiscoveredSensor sensor, int index) {
        String prefix = "discoveredSensors[" + index + "]";
        if (sensor == null) {
            throw new ValidationException(prefix + " cannot be null");
        }
        validateString(sensor.id, prefix + ".id", true);
        validateString(sensor.name, prefix + ".name", false);
        if (sensor.facing != 0 && sensor.facing != 1) {
            throw new ValidationException("Invalid " + prefix + ".facing: " + sensor.facing);
        }
        if (sensor.width < 0) {
            throw new ValidationException("Invalid " + prefix + ".width: " + sensor.width);
        }
        if (sensor.height < 0) {
            throw new ValidationException("Invalid " + prefix + ".height: " + sensor.height);
        }
        if (!Float.isFinite(sensor.focalLengthMm) || sensor.focalLengthMm < 0.0f) {
            throw new ValidationException("Invalid " + prefix + ".focalLengthMm: " + sensor.focalLengthMm);
        }
        if (!Float.isFinite(sensor.apertureF) || sensor.apertureF < 0.0f) {
            throw new ValidationException("Invalid " + prefix + ".apertureF: " + sensor.apertureF);
        }
        if (sensor.health == null) {
            throw new ValidationException(prefix + ".health cannot be null");
        }
        validateString(sensor.statusDetail, prefix + ".statusDetail", false);
    }

    private static void validateStringCollection(Set<String> values, String fieldName) {
        if (values == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }
        for (String item : values) {
            validateString(item, fieldName + "[]", true);
        }
    }

    private static void validateString(String value, String fieldName, boolean requireNonBlank) {
        if (value == null) {
            throw new ValidationException(fieldName + " cannot be null");
        }
        if (value.length() > MAX_STRING_LENGTH) {
            throw new ValidationException(fieldName + " exceeds MAX_STRING_LENGTH (" + MAX_STRING_LENGTH + ")");
        }
        if (requireNonBlank && value.trim().isEmpty()) {
            throw new ValidationException(fieldName + " cannot be blank");
        }
    }

    private static boolean isValidTimerSeconds(int seconds) {
        return seconds == 0 || seconds == 3 || seconds == 5 || seconds == 10;
    }

    private static ViewerPreferencesSnapshot parseViewerPreferences(Map<String, Object> obj) {
        String preferredCameraId = requireString(obj, "preferredCameraId", false);
        CameraViewerMath.GridMode gridMode =
                requireEnum(obj, "gridMode", CameraViewerMath.GridMode.class);
        int timerSeconds = requireInt(obj, "timerSeconds");
        if (!isValidTimerSeconds(timerSeconds)) {
            throw new ValidationException("Invalid viewerPreferences.timerSeconds: " + timerSeconds);
        }
        CameraViewerMath.FlashMode flashMode =
                requireEnum(obj, "flashMode", CameraViewerMath.FlashMode.class);
        float preferredAperture = requireNonNegativeFloat(obj, "preferredAperture");

        return new ViewerPreferencesSnapshot(
                preferredCameraId,
                gridMode,
                timerSeconds,
                flashMode,
                preferredAperture
        );
    }

    private static List<DiscoveredSensor> parseSensors(List<Object> array) {
        if (array.size() > MAX_SENSORS) {
            throw new ValidationException("discoveredSensors exceeds MAX_SENSORS (" + MAX_SENSORS + ")");
        }
        List<DiscoveredSensor> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object rawItem = array.get(i);
            if (!(rawItem instanceof Map<?, ?>)) {
                throw new ValidationException("discoveredSensors[" + i + "] must be a JSON object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) rawItem;

            String id = requireString(obj, "id", true);
            String name = requireString(obj, "name", false);
            int facing = requireInt(obj, "facing");
            if (facing != 0 && facing != 1) {
                throw new ValidationException("Invalid sensor facing: " + facing);
            }
            int width = requireInt(obj, "width");
            if (width < 0) {
                throw new ValidationException("Negative sensor width: " + width);
            }
            int height = requireInt(obj, "height");
            if (height < 0) {
                throw new ValidationException("Negative sensor height: " + height);
            }
            float focalLengthMm = requireNonNegativeFloat(obj, "focalLengthMm");
            float apertureF = requireNonNegativeFloat(obj, "apertureF");
            SensorHealth health = requireEnum(obj, "health", SensorHealth.class);
            String statusDetail = requireString(obj, "statusDetail", false);

            result.add(new DiscoveredSensor(
                    id, name, facing, width, height, focalLengthMm, apertureF, health, statusDetail
            ));
        }
        return result;
    }

    private static Map<String, Object> requireObject(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key)) {
            throw new ValidationException("Missing required object field: " + key);
        }
        Object raw = obj.get(key);
        if (!(raw instanceof Map<?, ?>)) {
            throw new ValidationException("Field '" + key + "' must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        return map;
    }

    private static List<Object> requireArray(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key)) {
            throw new ValidationException("Missing required array field: " + key);
        }
        Object raw = obj.get(key);
        if (!(raw instanceof List<?>)) {
            throw new ValidationException("Field '" + key + "' must be a JSON array");
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) raw;
        return list;
    }

    private static Set<String> requireStringSet(Map<String, Object> obj, String key) {
        List<Object> array = requireArray(obj, key);
        Set<String> set = new LinkedHashSet<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);
            if (!(item instanceof String)) {
                throw new ValidationException("Element " + i + " of '" + key + "' must be a string");
            }
            String str = (String) item;
            validateString(str, key + "[" + i + "]", true);
            set.add(str);
        }
        return set;
    }

    private static String requireString(Map<String, Object> obj, String key, boolean requireNonBlank) {
        if (!obj.containsKey(key)) {
            throw new ValidationException("Missing required string field: " + key);
        }
        Object raw = obj.get(key);
        if (!(raw instanceof String)) {
            throw new ValidationException("Field '" + key + "' must be a JSON string");
        }
        String str = (String) raw;
        validateString(str, key, requireNonBlank);
        return str;
    }

    private static <E extends Enum<E>> E requireEnum(Map<String, Object> obj, String key, Class<E> enumClass) {
        String raw = requireString(obj, key, true);
        try {
            return Enum.valueOf(enumClass, raw);
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("Unknown enum value '" + raw + "' for field '" + key + "'", ex);
        }
    }

    private static int requireInt(Map<String, Object> obj, String key) {
        BigDecimal bd = requireExactIntegerDecimal(obj, key, 10);
        try {
            return bd.intValueExact();
        } catch (ArithmeticException ex) {
            throw new ValidationException("Field '" + key + "' is out of 32-bit int range", ex);
        }
    }

    private static long requireLong(Map<String, Object> obj, String key) {
        BigDecimal bd = requireExactIntegerDecimal(obj, key, 19);
        try {
            return bd.longValueExact();
        } catch (ArithmeticException ex) {
            throw new ValidationException("Field '" + key + "' is out of 64-bit long range", ex);
        }
    }

    private static BigDecimal requireExactIntegerDecimal(Map<String, Object> obj, String key, int maxDigits) {
        JsonNumber num = requireJsonNumber(obj, key);
        try {
            BigDecimal bd = new BigDecimal(num.raw);
            if (bd.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal normalized = bd.stripTrailingZeros();
            if (normalized.scale() > 0) {
                throw new ValidationException("Field '" + key + "' must be an integer, got: " + num.raw);
            }
            long integerDigits = (long) normalized.precision() - normalized.scale();
            if (integerDigits > maxDigits) {
                throw new ValidationException("Field '" + key + "' exceeds integer bounds: " + num.raw);
            }
            return normalized;
        } catch (NumberFormatException ex) {
            throw new ValidationException("Invalid number for field '" + key + "': " + num.raw, ex);
        }
    }

    private static float requireNonNegativeFloat(Map<String, Object> obj, String key) {
        JsonNumber num = requireJsonNumber(obj, key);
        try {
            float val = Float.parseFloat(num.raw);
            if (!Float.isFinite(val) || val < 0.0f) {
                throw new ValidationException("Field '" + key + "' must be finite and >= 0: " + num.raw);
            }
            return val == 0.0f ? 0.0f : val;
        } catch (NumberFormatException ex) {
            throw new ValidationException("Invalid float for field '" + key + "': " + num.raw, ex);
        }
    }

    private static JsonNumber requireJsonNumber(Map<String, Object> obj, String key) {
        if (!obj.containsKey(key)) {
            throw new ValidationException("Missing required number field: " + key);
        }
        Object raw = obj.get(key);
        if (!(raw instanceof JsonNumber)) {
            throw new ValidationException("Field '" + key + "' must be a JSON number");
        }
        return (JsonNumber) raw;
    }

    private static String sanitizeFileModel(String rawModel) {
        if (rawModel == null || rawModel.trim().isEmpty()) {
            return "galaxy";
        }
        StringBuilder sb = new StringBuilder(rawModel.length());
        boolean lastWasDash = false;
        for (int i = 0; i < rawModel.length(); i++) {
            char c = rawModel.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
                lastWasDash = false;
            } else if (c == '-') {
                if (sb.length() > 0 && !lastWasDash) {
                    sb.append('-');
                    lastWasDash = true;
                }
            } else {
                if (sb.length() > 0 && !lastWasDash) {
                    sb.append('-');
                    lastWasDash = true;
                }
            }
            if (sb.length() >= MAX_FILENAME_MODEL_LENGTH) {
                break;
            }
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '-') {
            end--;
        }
        String cleaned = sb.substring(0, end);
        return cleaned.isEmpty() ? "galaxy" : cleaned;
    }

    private static void appendIndent(StringBuilder sb, int indentLevel) {
        for (int i = 0; i < indentLevel; i++) {
            sb.append("  ");
        }
    }

    private static void appendField(StringBuilder sb, int indent, String key, String rawJsonValue, boolean comma) {
        appendIndent(sb, indent);
        appendQuotedString(sb, key);
        sb.append(": ").append(rawJsonValue);
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendStringField(StringBuilder sb, int indent, String key, String value, boolean comma) {
        appendIndent(sb, indent);
        appendQuotedString(sb, key);
        sb.append(": ");
        appendQuotedString(sb, value);
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendStringSetField(
            StringBuilder sb,
            int indent,
            String key,
            Set<String> values,
            boolean comma
    ) {
        appendIndent(sb, indent);
        appendQuotedString(sb, key);
        sb.append(": [");
        if (!values.isEmpty()) {
            sb.append('\n');
            int idx = 0;
            int size = values.size();
            for (String item : values) {
                appendIndent(sb, indent + 1);
                appendQuotedString(sb, item);
                if (idx + 1 < size) {
                    sb.append(',');
                }
                sb.append('\n');
                idx++;
            }
            appendIndent(sb, indent);
        }
        sb.append(']');
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendViewerPreferencesField(
            StringBuilder sb,
            int indent,
            ViewerPreferencesSnapshot prefs,
            boolean comma
    ) {
        appendIndent(sb, indent);
        appendQuotedString(sb, "viewerPreferences");
        sb.append(": {\n");
        appendStringField(sb, indent + 1, "preferredCameraId", prefs.preferredCameraId, true);
        appendStringField(sb, indent + 1, "gridMode", prefs.gridMode.name(), true);
        appendField(sb, indent + 1, "timerSeconds", Integer.toString(prefs.timerSeconds), true);
        appendStringField(sb, indent + 1, "flashMode", prefs.flashMode.name(), true);
        appendField(sb, indent + 1, "preferredAperture", formatFloat(prefs.preferredAperture), false);
        appendIndent(sb, indent);
        sb.append('}');
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendSensorsField(
            StringBuilder sb,
            int indent,
            List<DiscoveredSensor> sensors,
            boolean comma
    ) {
        appendIndent(sb, indent);
        appendQuotedString(sb, "discoveredSensors");
        sb.append(": [");
        if (!sensors.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < sensors.size(); i++) {
                DiscoveredSensor s = sensors.get(i);
                appendIndent(sb, indent + 1);
                sb.append("{\n");
                appendStringField(sb, indent + 2, "id", s.id, true);
                appendStringField(sb, indent + 2, "name", s.name, true);
                appendField(sb, indent + 2, "facing", Integer.toString(s.facing), true);
                appendField(sb, indent + 2, "width", Integer.toString(s.width), true);
                appendField(sb, indent + 2, "height", Integer.toString(s.height), true);
                appendField(sb, indent + 2, "focalLengthMm", formatFloat(s.focalLengthMm), true);
                appendField(sb, indent + 2, "apertureF", formatFloat(s.apertureF), true);
                appendStringField(sb, indent + 2, "health", s.health.name(), true);
                appendStringField(sb, indent + 2, "statusDetail", s.statusDetail, false);
                appendIndent(sb, indent + 1);
                sb.append('}');
                if (i + 1 < sensors.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            appendIndent(sb, indent);
        }
        sb.append(']');
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static String formatFloat(float value) {
        float normalized = (value == 0.0f) ? 0.0f : value;
        return Float.toString(normalized);
    }

    private static void appendQuotedString(StringBuilder sb, String value) {
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    private static final class JsonNumber {
        final String raw;

        JsonNumber(String raw) {
            this.raw = raw;
        }
    }

    private static final class JsonParser {
        private final String input;
        private final int len;
        private int pos;

        JsonParser(String input) {
            this.input = input;
            this.len = input.length();
            this.pos = 0;
        }

        Map<String, Object> parseRootObject() {
            skipWhitespace();
            if (pos >= len || input.charAt(pos) != '{') {
                throw new ValidationException("Root JSON value must be an object");
            }
            Map<String, Object> root = parseObject(0);
            skipWhitespace();
            if (pos != len) {
                throw new ValidationException("Unexpected trailing characters at index " + pos);
            }
            return root;
        }

        private Object parseValue(int depth) {
            if (depth > MAX_JSON_DEPTH) {
                throw new ValidationException("JSON nesting depth exceeds limit (" + MAX_JSON_DEPTH + ")");
            }
            skipWhitespace();
            if (pos >= len) {
                throw new ValidationException("Unexpected end of JSON input");
            }
            char c = input.charAt(pos);
            switch (c) {
                case '{':
                    return parseObject(depth + 1);
                case '[':
                    return parseArray(depth + 1);
                case '"':
                    return parseString();
                case 't':
                case 'f':
                    return parseBoolean();
                case 'n':
                    return parseNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw new ValidationException("Unexpected character '" + c + "' at index " + pos);
            }
        }

        private Map<String, Object> parseObject(int depth) {
            pos++; // consume '{'
            skipWhitespace();
            Map<String, Object> map = new LinkedHashMap<>();
            if (pos < len && input.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (pos >= len || input.charAt(pos) != '"') {
                    throw new ValidationException("Expected string object key at index " + pos);
                }
                String key = parseString();
                if (map.containsKey(key)) {
                    throw new ValidationException("Duplicate JSON key: " + key);
                }
                skipWhitespace();
                if (pos >= len || input.charAt(pos) != ':') {
                    throw new ValidationException("Expected ':' after key '" + key + "' at index " + pos);
                }
                pos++; // consume ':'
                Object value = parseValue(depth);
                map.put(key, value);
                skipWhitespace();
                if (pos >= len) {
                    throw new ValidationException("Unclosed JSON object");
                }
                char sep = input.charAt(pos);
                if (sep == ',') {
                    pos++;
                } else if (sep == '}') {
                    pos++;
                    return map;
                } else {
                    throw new ValidationException("Expected ',' or '}' at index " + pos);
                }
            }
        }

        private List<Object> parseArray(int depth) {
            pos++; // consume '['
            skipWhitespace();
            List<Object> list = new ArrayList<>();
            if (pos < len && input.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = parseValue(depth);
                list.add(value);
                skipWhitespace();
                if (pos >= len) {
                    throw new ValidationException("Unclosed JSON array");
                }
                char sep = input.charAt(pos);
                if (sep == ',') {
                    pos++;
                } else if (sep == ']') {
                    pos++;
                    return list;
                } else {
                    throw new ValidationException("Expected ',' or ']' at index " + pos);
                }
            }
        }

        private String parseString() {
            pos++; // consume opening '"'
            StringBuilder sb = new StringBuilder();
            while (pos < len) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw new ValidationException("Unescaped control character in JSON string");
                }
                if (c == '\\') {
                    if (pos >= len) {
                        throw new ValidationException("Unterminated escape sequence in JSON string");
                    }
                    char esc = input.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            sb.append(parseUnicodeEscape());
                            break;
                        default:
                            throw new ValidationException("Invalid JSON escape sequence: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new ValidationException("Unterminated JSON string");
        }

        private char parseUnicodeEscape() {
            if (pos + 4 > len) {
                throw new ValidationException("Incomplete \\uXXXX escape sequence");
            }
            int code = 0;
            for (int i = 0; i < 4; i++) {
                char hex = input.charAt(pos++);
                int digit = Character.digit(hex, 16);
                if (digit < 0) {
                    throw new ValidationException("Invalid hex digit in \\uXXXX escape: " + hex);
                }
                code = (code << 4) | digit;
            }
            return (char) code;
        }

        private JsonNumber parseNumber() {
            int start = pos;
            if (input.charAt(pos) == '-') {
                pos++;
            }
            if (pos >= len) {
                throw new ValidationException("Invalid JSON number at index " + start);
            }
            char first = input.charAt(pos);
            if (first == '0') {
                pos++;
                if (pos < len && isAsciiDigit(input.charAt(pos))) {
                    throw new ValidationException("Leading zero not allowed in JSON number at index " + start);
                }
            } else if (first >= '1' && first <= '9') {
                while (pos < len && isAsciiDigit(input.charAt(pos))) {
                    pos++;
                }
            } else {
                throw new ValidationException("Invalid JSON number at index " + start);
            }

            if (pos < len && input.charAt(pos) == '.') {
                pos++;
                if (pos >= len || !isAsciiDigit(input.charAt(pos))) {
                    throw new ValidationException("Expected digit after decimal point at index " + pos);
                }
                while (pos < len && isAsciiDigit(input.charAt(pos))) {
                    pos++;
                }
            }

            if (pos < len && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                pos++;
                if (pos < len && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                    pos++;
                }
                if (pos >= len || !isAsciiDigit(input.charAt(pos))) {
                    throw new ValidationException("Expected digit in exponent at index " + pos);
                }
                while (pos < len && isAsciiDigit(input.charAt(pos))) {
                    pos++;
                }
            }

            String raw = input.substring(start, pos);
            if (raw.length() > MAX_NUMBER_TOKEN_LENGTH) {
                throw new ValidationException("JSON number token exceeds maximum length");
            }
            return new JsonNumber(raw);
        }

        private Boolean parseBoolean() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new ValidationException("Invalid JSON literal at index " + pos);
        }

        private Object parseNull() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return JSON_NULL;
            }
            throw new ValidationException("Invalid JSON literal at index " + pos);
        }

        private void skipWhitespace() {
            while (pos < len) {
                char c = input.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private static boolean isAsciiDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
