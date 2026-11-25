package com.s23plus.camera;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Persistencia local offline (SharedPreferences) para la selección de sensores,
 * la acción configurada en el mosaico de Ajustes Rápidos (Quick Settings Tile)
 * y el progreso del asistente de configuración One UI.
 */
public final class RescuePreferences {

    private static final String PREFS_NAME = "camera_rescue_prefs";
    private static final String KEY_SELECTED_SENSOR_IDS = "selected_sensor_ids";
    private static final String KEY_HAS_SAVED_SELECTION = "has_saved_selection";
    private static final String KEY_QUICK_TILE_ACTION = "quick_tile_action";
    private static final String KEY_QUICK_TILE_LABEL = "quick_tile_label";
    private static final String KEY_ASSISTANT_COMPLETED_STEPS = "assistant_completed_steps";
    private static final String KEY_VIEWER_PREFERRED_CAMERA_ID = "viewer_preferred_camera_id";
    private static final String KEY_VIEWER_GRID_MODE = "viewer_grid_mode";
    private static final String KEY_VIEWER_TIMER_SECONDS = "viewer_timer_seconds";
    private static final String KEY_VIEWER_FLASH_MODE = "viewer_flash_mode";
    private static final String KEY_VIEWER_PREFERRED_APERTURE = "viewer_preferred_aperture";
    private static final String DEFAULT_QUICK_TILE_LABEL = "Trasera Pro";

    private RescuePreferences() {
        // Clase de utilidad no instanciable
    }

    /**
     * Guarda el conjunto de IDs de sensores seleccionados por el usuario y marca
     * que existe una preferencia explícita almacenada.
     */
    public static void saveSelectedSensorIds(Context context, Set<String> ids) {
        if (context == null) {
            return;
        }
        String serialized = SensorSelectionState.serializeSelectedIds(ids);
        prefs(context)
                .edit()
                .putString(KEY_SELECTED_SENSOR_IDS, serialized)
                .putBoolean(KEY_HAS_SAVED_SELECTION, true)
                .apply();
    }

    /**
     * Carga los IDs de sensores guardados.
     *
     * @return {@code null} si el usuario nunca ha guardado una selección;
     *         o el conjunto deserializado (posiblemente vacío) si ya fue configurado.
     */
    public static Set<String> loadSelectedSensorIds(Context context) {
        if (context == null) {
            return null;
        }
        SharedPreferences sharedPreferences = prefs(context);
        if (!sharedPreferences.getBoolean(KEY_HAS_SAVED_SELECTION, false)) {
            return null;
        }
        String serialized = sharedPreferences.getString(KEY_SELECTED_SENSOR_IDS, "");
        return SensorSelectionState.deserializeSelectedIds(serialized);
    }

    /**
     * Guarda la acción y etiqueta de subtítulo asociadas al Quick Settings Tile.
     */
    public static void saveQuickTileAction(Context context, String intentAction, String label) {
        if (context == null) {
            return;
        }
        String safeAction = (intentAction == null || intentAction.trim().isEmpty())
                ? CameraConfig.ACTION_LAUNCH_REAR_PRO
                : intentAction.trim();
        String safeLabel = (label == null || label.trim().isEmpty())
                ? DEFAULT_QUICK_TILE_LABEL
                : label.trim();

        prefs(context)
                .edit()
                .putString(KEY_QUICK_TILE_ACTION, safeAction)
                .putString(KEY_QUICK_TILE_LABEL, safeLabel)
                .apply();
    }

    /**
     * Devuelve la acción configurada para el Quick Settings Tile, con respaldo seguro.
     */
    public static String loadQuickTileAction(Context context) {
        if (context == null) {
            return CameraConfig.ACTION_LAUNCH_REAR_PRO;
        }
        String action = prefs(context).getString(KEY_QUICK_TILE_ACTION, CameraConfig.ACTION_LAUNCH_REAR_PRO);
        if (action == null || action.trim().isEmpty()) {
            return CameraConfig.ACTION_LAUNCH_REAR_PRO;
        }
        return action;
    }

    /**
     * Devuelve la etiqueta de subtítulo configurada para el Quick Settings Tile.
     */
    public static String loadQuickTileLabel(Context context) {
        if (context == null) {
            return DEFAULT_QUICK_TILE_LABEL;
        }
        String label = prefs(context).getString(KEY_QUICK_TILE_LABEL, DEFAULT_QUICK_TILE_LABEL);
        if (label == null || label.trim().isEmpty()) {
            return DEFAULT_QUICK_TILE_LABEL;
        }
        return label;
    }

    /**
     * Guarda el conjunto de IDs de pasos completados del asistente One UI.
     */
    public static void saveAssistantCompletedSteps(Context context, Set<String> stepIds) {
        if (context == null) {
            return;
        }
        String serialized = OneUiAssistantGuide.serializeCompletedStepIds(stepIds);
        prefs(context)
                .edit()
                .putString(KEY_ASSISTANT_COMPLETED_STEPS, serialized)
                .apply();
    }

    /**
     * Carga el conjunto de IDs de pasos completados del asistente One UI.
     */
    public static Set<String> loadAssistantCompletedSteps(Context context) {
        if (context == null) {
            return new LinkedHashSet<>();
        }
        String serialized = prefs(context).getString(KEY_ASSISTANT_COMPLETED_STEPS, "");
        return OneUiAssistantGuide.deserializeCompletedStepIds(serialized);
    }

    /**
     * Guarda las preferencias del visor Camera2 universal.
     */
    public static void saveViewerPreferences(
            Context context,
            DiagnosticProfileCodec.ViewerPreferencesSnapshot snapshot
    ) {
        if (context == null || snapshot == null) {
            return;
        }
        String safeCameraId = (snapshot.preferredCameraId == null || snapshot.preferredCameraId.trim().isEmpty())
                ? CameraConfig.FRONT_CAMERA_WIDE_ID
                : snapshot.preferredCameraId.trim();
        CameraViewerMath.GridMode safeGrid = (snapshot.gridMode != null)
                ? snapshot.gridMode
                : CameraViewerMath.GridMode.OFF;
        int safeTimer = CameraViewerMath.sanitizeTimerSeconds(snapshot.timerSeconds);
        CameraViewerMath.FlashMode safeFlash = (snapshot.flashMode != null)
                ? snapshot.flashMode
                : CameraViewerMath.FlashMode.OFF;
        float safeAperture = sanitizeStoredAperture(snapshot.preferredAperture);

        prefs(context)
                .edit()
                .putString(KEY_VIEWER_PREFERRED_CAMERA_ID, safeCameraId)
                .putString(KEY_VIEWER_GRID_MODE, safeGrid.name())
                .putInt(KEY_VIEWER_TIMER_SECONDS, safeTimer)
                .putString(KEY_VIEWER_FLASH_MODE, safeFlash.name())
                .putFloat(KEY_VIEWER_PREFERRED_APERTURE, safeAperture)
                .apply();
    }

    /**
     * Carga las preferencias del visor Camera2 universal con respaldo seguro ante valores corruptos.
     */
    public static DiagnosticProfileCodec.ViewerPreferencesSnapshot loadViewerPreferences(Context context) {
        if (context == null) {
            return DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults();
        }
        SharedPreferences sharedPreferences = prefs(context);
        String cameraId = sharedPreferences.getString(
                KEY_VIEWER_PREFERRED_CAMERA_ID,
                CameraConfig.FRONT_CAMERA_WIDE_ID
        );
        CameraViewerMath.GridMode gridMode = parseEnumOrDefault(
                sharedPreferences.getString(KEY_VIEWER_GRID_MODE, CameraViewerMath.GridMode.OFF.name()),
                CameraViewerMath.GridMode.class,
                CameraViewerMath.GridMode.OFF
        );
        int timerSeconds = CameraViewerMath.sanitizeTimerSeconds(
                sharedPreferences.getInt(KEY_VIEWER_TIMER_SECONDS, 0)
        );
        CameraViewerMath.FlashMode flashMode = parseEnumOrDefault(
                sharedPreferences.getString(KEY_VIEWER_FLASH_MODE, CameraViewerMath.FlashMode.OFF.name()),
                CameraViewerMath.FlashMode.class,
                CameraViewerMath.FlashMode.OFF
        );
        float aperture = sanitizeStoredAperture(
                sharedPreferences.getFloat(
                        KEY_VIEWER_PREFERRED_APERTURE,
                        CameraViewerMath.ApertureCapabilities.APERTURE_AUTO
                )
        );
        return new DiagnosticProfileCodec.ViewerPreferencesSnapshot(
                cameraId,
                gridMode,
                timerSeconds,
                flashMode,
                aperture
        );
    }

    private static <E extends Enum<E>> E parseEnumOrDefault(String raw, Class<E> enumClass, E fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, raw.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static float sanitizeStoredAperture(float aperture) {
        if (!Float.isFinite(aperture) || aperture <= 0.0f) {
            return CameraViewerMath.ApertureCapabilities.APERTURE_AUTO;
        }
        return aperture;
    }

    private static SharedPreferences prefs(Context context) {
        Context appContext = context.getApplicationContext();
        Context target = appContext != null ? appContext : context;
        return target.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
