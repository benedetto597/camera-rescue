package com.s23plus.camera;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Puente de sistema Android / One UI para detectar la generación de One UI,
 * construir y lanzar variantes heurísticas de Intent con fallback, y abrir
 * pantallas de ajustes relevantes (Tecla lateral, Ajustes de cámara, App Info).
 */
public final class OneUiSystemBridge {

    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String SETTINGS_USEFUL_FEATURE_ACTIVITY =
            "com.android.settings.Settings$UsefulFeatureMainActivity";

    private OneUiSystemBridge() {
        // Clase de utilidad no instanciable
    }

    /**
     * Detecta la generación de One UI del dispositivo combinando SDK_INT y SEM_PLATFORM_INT.
     */
    public static ShootingModeMap.OneUiGeneration detectDeviceOneUiGeneration() {
        int semPlatformInt = 0;
        try {
            semPlatformInt = Build.VERSION.class.getField("SEM_PLATFORM_INT").getInt(null);
        } catch (Throwable ignored) {
            semPlatformInt = 0;
        }
        return ShootingModeMap.detectOneUiGeneration(Build.VERSION.SDK_INT, semPlatformInt);
    }

    /**
     * Construye un {@link Intent} explícito o implícito a partir de una {@link ShootingModeMap.LaunchVariant}.
     */
    public static Intent buildLaunchIntent(ShootingModeMap.LaunchVariant variant) {
        if (variant == null) {
            return null;
        }
        String action = hasText(variant.intentAction) ? variant.intentAction.trim() : Intent.ACTION_MAIN;
        Intent intent = new Intent(action);

        if (hasText(variant.componentPackage) && hasText(variant.componentClass)) {
            intent.setComponent(new ComponentName(
                    variant.componentPackage.trim(),
                    variant.componentClass.trim()
            ));
        } else if (hasText(variant.componentPackage)) {
            intent.setPackage(variant.componentPackage.trim());
        }

        if (hasText(variant.previousMode)) {
            intent.putExtra(CameraConfig.EXTRA_PREVIOUS_MODE, variant.previousMode.trim());
        }
        if (hasText(variant.activityName)) {
            intent.putExtra(CameraConfig.EXTRA_ACTIVITY_NAME, variant.activityName.trim());
        }
        if (hasText(variant.cameraFacing)) {
            intent.putExtra(CameraConfig.EXTRA_CAMERA_FACING, variant.cameraFacing.trim());
        }

        int taskFlags = variant.clearTask
                ? Intent.FLAG_ACTIVITY_CLEAR_TASK
                : Intent.FLAG_ACTIVITY_CLEAR_TOP;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | taskFlags);
        return intent;
    }

    /**
     * Intenta lanzar una variante concreta de Intent de forma segura.
     */
    public static boolean launchVariant(Context context, ShootingModeMap.LaunchVariant variant) {
        if (context == null || variant == null) {
            return false;
        }
        Intent intent = buildLaunchIntent(variant);
        return tryStartActivity(context, intent);
    }

    /**
     * Recorre las variantes de un descriptor en orden y devuelve la primera que se lance con éxito.
     */
    public static ShootingModeMap.LaunchVariant launchModeWithFallback(
            Context context,
            ShootingModeMap.ModeDescriptor descriptor
    ) {
        if (context == null || descriptor == null || descriptor.variants == null) {
            return null;
        }
        for (ShootingModeMap.LaunchVariant variant : descriptor.variants) {
            if (launchVariant(context, variant)) {
                return variant;
            }
        }
        return null;
    }

    /**
     * Abre los ajustes de Tecla/Botón lateral de Samsung con cadena de fallback segura.
     */
    public static String openSideKeySettings(Context context) {
        if (context == null) {
            return "Contexto no disponible";
        }

        Intent sideKeyIntent = new Intent(CameraConfig.ACTION_SAMSUNG_SIDE_KEY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStartActivity(context, sideKeyIntent)) {
            return "Ajustes de Tecla/Botón lateral abiertos";
        }

        Intent usefulFeaturesIntent = new Intent()
                .setComponent(new ComponentName(SETTINGS_PACKAGE, SETTINGS_USEFUL_FEATURE_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStartActivity(context, usefulFeaturesIntent)) {
            return "Funciones avanzadas abiertas (entra a Tecla/Botón lateral)";
        }

        Intent systemSettingsIntent = new Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStartActivity(context, systemSettingsIntent)) {
            return "Ajustes abiertos: ve a Funciones avanzadas → Tecla/Botón lateral";
        }

        return "No se pudieron abrir los Ajustes del sistema (abre Ajustes → Funciones avanzadas → Tecla/Botón lateral)";
    }

    /**
     * Abre directamente Ajustes de Samsung Camera o recurre al lanzamiento de Modo Pro como alternativa.
     */
    public static String openCameraSettingsOrPro(
            Context context,
            ShootingModeMap.ModeDescriptor proDescriptor
    ) {
        if (context == null) {
            return "Contexto no disponible";
        }

        Intent cameraSettingsIntent = new Intent()
                .setComponent(new ComponentName(
                        CameraConfig.SAMSUNG_CAMERA_PACKAGE,
                        CameraConfig.SAMSUNG_CAMERA_SETTINGS_ACTIVITY
                ))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStartActivity(context, cameraSettingsIntent)) {
            return "Ajustes de Samsung Camera abiertos: entra a Ajustes para conservar → Modo de cámara";
        }

        if (launchModeWithFallback(context, proDescriptor) != null) {
            return "Modo Pro abierto: toca el ícono ⚙️ arriba a la izquierda → Ajustes para conservar";
        }

        return "No se pudo abrir Ajustes de Samsung Camera directamente";
    }

    /**
     * Abre la pantalla de información de aplicación (App Info) de Samsung Camera.
     */
    public static boolean openSamsungCameraAppInfo(Context context) {
        if (context == null) {
            return false;
        }
        Intent appInfoIntent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", CameraConfig.SAMSUNG_CAMERA_PACKAGE, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return tryStartActivity(context, appInfoIntent);
    }

    private static boolean tryStartActivity(Context context, Intent intent) {
        if (context == null || intent == null) {
            return false;
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
