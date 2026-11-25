package com.s23plus.camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Mapa heurístico de modos de disparo y variantes de Intent para Samsung Camera
 * según generación de One UI y perfil de hardware del dispositivo.
 *
 * Clase 100% Java pura (sin dependencias de android.*).
 */
public final class ShootingModeMap {

    /**
     * Generación de One UI con etiquetas localizadas para los menús de configuración relevantes.
     */
    public enum OneUiGeneration {
        ONE_UI_2("One UI 2.x", 29, "Ajustes que mantener", "Tecla lateral / Inicio rápido"),
        ONE_UI_3("One UI 3.x", 30, "Ajustes para conservar", "Tecla lateral"),
        ONE_UI_4("One UI 4.x", 31, "Ajustes para conservar", "Tecla lateral"),
        ONE_UI_5("One UI 5.x", 33, "Ajustes para conservar", "Tecla lateral"),
        ONE_UI_6("One UI 6.x", 34, "Ajustes para conservar", "Botón lateral"),
        ONE_UI_7("One UI 7.x", 35, "Ajustes para conservar", "Botón lateral"),
        ONE_UI_8_PLUS("One UI 8+", 36, "Ajustes para conservar", "Botón lateral");

        public final String displayName;
        public final int minApiLevel;
        public final String settingsToKeepLabel;
        public final String sideKeyLabel;

        OneUiGeneration(
                String displayName,
                int minApiLevel,
                String settingsToKeepLabel,
                String sideKeyLabel
        ) {
            this.displayName = displayName;
            this.minApiLevel = minApiLevel;
            this.settingsToKeepLabel = settingsToKeepLabel;
            this.sideKeyLabel = sideKeyLabel;
        }
    }

    /**
     * Categorías de modos de rescate traseros soportados por el mapeador heurístico.
     */
    public enum ModeKind {
        PRO_PHOTO,
        PRO_VIDEO,
        QR_SCANNER
    }

    /**
     * Variante concreta de Intent para lanzar o recuperar un modo de Samsung Camera.
     */
    public static final class LaunchVariant {
        public final String variantId;
        public final String intentAction;
        public final String componentPackage;
        public final String componentClass;
        public final String previousMode;
        public final String activityName;
        public final String cameraFacing;
        public final boolean clearTask;
        public final String description;

        public LaunchVariant(
                String variantId,
                String intentAction,
                String componentPackage,
                String componentClass,
                String previousMode,
                String activityName,
                String cameraFacing,
                boolean clearTask,
                String description
        ) {
            this.variantId = normalize(variantId);
            this.intentAction = normalize(intentAction);
            this.componentPackage = normalize(componentPackage);
            this.componentClass = normalize(componentClass);
            this.previousMode = normalize(previousMode);
            this.activityName = normalize(activityName);
            this.cameraFacing = normalize(cameraFacing);
            this.clearTask = clearTask;
            this.description = normalize(description);
        }
    }

    /**
     * Descriptor resuelto para un modo concreto, incluyendo compatibilidad y cadena de variantes.
     */
    public static final class ModeDescriptor {
        public final ModeKind kind;
        public final int shootingModeId;
        public final OneUiGeneration oneUi;
        public final boolean nativeSupportedOnHardware;
        public final String primarySensorSummary;
        public final String compatibilityNote;
        public final List<LaunchVariant> variants;

        public ModeDescriptor(
                ModeKind kind,
                int shootingModeId,
                OneUiGeneration oneUi,
                boolean nativeSupportedOnHardware,
                String primarySensorSummary,
                String compatibilityNote,
                List<LaunchVariant> variants
        ) {
            if (variants == null || variants.isEmpty()) {
                throw new IllegalArgumentException("ModeDescriptor requiere al menos una LaunchVariant.");
            }
            this.kind = kind == null ? ModeKind.PRO_PHOTO : kind;
            this.shootingModeId = shootingModeId;
            this.oneUi = oneUi == null ? OneUiGeneration.ONE_UI_6 : oneUi;
            this.nativeSupportedOnHardware = nativeSupportedOnHardware;
            this.primarySensorSummary = normalize(primarySensorSummary);
            this.compatibilityNote = normalize(compatibilityNote);
            this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
        }

        public LaunchVariant getPrimaryVariant() {
            return variants.get(0);
        }

        public LaunchVariant getVariantAt(int index) {
            int clamped = Math.max(0, Math.min(index, variants.size() - 1));
            return variants.get(clamped);
        }
    }

    private static final String ACTION_MAIN = "android.intent.action.MAIN";

    private ShootingModeMap() {
    }

    /**
     * Detecta la generación de One UI usando primero SEM_PLATFORM_INT y, si no está disponible,
     * el nivel de API de Android (sdkInt).
     */
    public static OneUiGeneration detectOneUiGeneration(int sdkInt, int semPlatformInt) {
        if (semPlatformInt >= 100000) {
            int major = (semPlatformInt - 90000) / 10000;
            return generationFromMajor(major);
        }
        if (sdkInt <= 29) {
            return OneUiGeneration.ONE_UI_2;
        }
        if (sdkInt == 30) {
            return OneUiGeneration.ONE_UI_3;
        }
        if (sdkInt <= 32) {
            return OneUiGeneration.ONE_UI_4;
        }
        if (sdkInt == 33) {
            return OneUiGeneration.ONE_UI_5;
        }
        if (sdkInt == 34) {
            return OneUiGeneration.ONE_UI_6;
        }
        if (sdkInt == 35) {
            return OneUiGeneration.ONE_UI_7;
        }
        return OneUiGeneration.ONE_UI_8_PLUS;
    }

    private static OneUiGeneration generationFromMajor(int major) {
        if (major <= 2) {
            return OneUiGeneration.ONE_UI_2;
        }
        if (major == 3) {
            return OneUiGeneration.ONE_UI_3;
        }
        if (major == 4) {
            return OneUiGeneration.ONE_UI_4;
        }
        if (major == 5) {
            return OneUiGeneration.ONE_UI_5;
        }
        if (major == 6) {
            return OneUiGeneration.ONE_UI_6;
        }
        if (major == 7) {
            return OneUiGeneration.ONE_UI_7;
        }
        return OneUiGeneration.ONE_UI_8_PLUS;
    }

    /**
     * Resuelve el descriptor y la cadena de variantes heurísticas para un modo dado.
     */
    public static ModeDescriptor resolve(
            SamsungModelDatabase.ModelProfile profile,
            OneUiGeneration oneUi,
            ModeKind kind
    ) {
        SamsungModelDatabase.ModelProfile safeProfile =
                profile != null ? profile : SamsungModelDatabase.lookup(null);
        OneUiGeneration safeOneUi = oneUi != null ? oneUi : OneUiGeneration.ONE_UI_6;
        ModeKind safeKind = kind != null ? kind : ModeKind.PRO_PHOTO;

        String sensorSummary = buildSensorSummary(safeProfile);

        switch (safeKind) {
            case QR_SCANNER:
                return resolveQrScanner(safeOneUi, sensorSummary);
            case PRO_VIDEO:
                return resolveProVideo(safeProfile, safeOneUi, sensorSummary);
            case PRO_PHOTO:
            default:
                return resolveProPhoto(safeProfile, safeOneUi, sensorSummary);
        }
    }

    /**
     * Resuelve los tres modos principales en orden fijo: [PRO_PHOTO, QR_SCANNER, PRO_VIDEO].
     */
    public static List<ModeDescriptor> resolveAll(
            SamsungModelDatabase.ModelProfile profile,
            OneUiGeneration oneUi
    ) {
        return Collections.unmodifiableList(Arrays.asList(
                resolve(profile, oneUi, ModeKind.PRO_PHOTO),
                resolve(profile, oneUi, ModeKind.QR_SCANNER),
                resolve(profile, oneUi, ModeKind.PRO_VIDEO)
        ));
    }

    private static ModeDescriptor resolveProPhoto(
            SamsungModelDatabase.ModelProfile profile,
            OneUiGeneration oneUi,
            String sensorSummary
    ) {
        List<LaunchVariant> variants = Arrays.asList(
                mainCameraVariant(
                        "pro_standard",
                        CameraConfig.MODE_PRO_PHOTO,
                        CameraConfig.ACTIVITY_NAME_PRO_PHOTO,
                        "Modo Pro estándar (ShootingMode 3)"
                ),
                mainCameraVariant(
                        "pro_legacy_class",
                        CameraConfig.MODE_PRO_PHOTO,
                        CameraConfig.ACTIVITY_NAME_PRO_LEGACY,
                        "Modo Pro con clase ProMode heredada (One UI antiguo)"
                ),
                mainCameraVariant(
                        "pro_minimal_extras",
                        CameraConfig.MODE_PRO_PHOTO,
                        "",
                        "Modo Pro con extras mínimos (sin activity_name)"
                ),
                qrVariant(
                        "pro_qr_extreme_fallback",
                        "Rescate extremo vía Escáner QR (aísla sensor 1x)"
                )
        );
        String note = profile.hasVariableAperture
                ? "Modo Pro soporta control manual sobre sensor con apertura variable."
                : "Modo Pro evita el grupo lógico Ultra-Wide y abre sensores independientes.";
        return new ModeDescriptor(
                ModeKind.PRO_PHOTO,
                3,
                oneUi,
                true,
                sensorSummary,
                note,
                variants
        );
    }

    private static ModeDescriptor resolveProVideo(
            SamsungModelDatabase.ModelProfile profile,
            OneUiGeneration oneUi,
            String sensorSummary
    ) {
        boolean supported = profile.supportsNativeProVideo && oneUi != OneUiGeneration.ONE_UI_2;
        if (supported) {
            List<LaunchVariant> variants = Arrays.asList(
                    mainCameraVariant(
                            "pro_video_standard",
                            CameraConfig.MODE_PRO_VIDEO,
                            CameraConfig.ACTIVITY_NAME_PRO_VIDEO,
                            "Video Pro estándar (ShootingMode 36)"
                    ),
                    mainCameraVariant(
                            "pro_video_camel",
                            CameraConfig.MODE_PRO_VIDEO_CAMEL,
                            CameraConfig.ACTIVITY_NAME_PRO_VIDEO,
                            "Video Pro con identificador ProVideo (variante de firmware)"
                    ),
                    mainCameraVariant(
                            "pro_video_to_pro_fallback",
                            CameraConfig.MODE_PRO_PHOTO,
                            CameraConfig.ACTIVITY_NAME_PRO_PHOTO,
                            "Fallback seguro hacia Modo Pro Foto"
                    )
            );
            return new ModeDescriptor(
                    ModeKind.PRO_VIDEO,
                    36,
                    oneUi,
                    true,
                    sensorSummary,
                    "Video Pro nativo soportado en " + profile.marketingName + " (" + oneUi.displayName + ").",
                    variants
            );
        }

        List<LaunchVariant> fallbackVariants = Arrays.asList(
                mainCameraVariant(
                        "pro_video_try_standard",
                        CameraConfig.MODE_PRO_VIDEO,
                        CameraConfig.ACTIVITY_NAME_PRO_VIDEO,
                        "Intento directo de Video Pro (puede no estar disponible en este firmware)"
                ),
                mainCameraVariant(
                        "pro_video_fallback_pro",
                        CameraConfig.MODE_PRO_PHOTO,
                        CameraConfig.ACTIVITY_NAME_PRO_PHOTO,
                        "Fallback automático a Modo Pro Foto por ausencia de Video Pro nativo"
                )
        );
        String reason = !profile.supportsNativeProVideo
                ? profile.marketingName + " no incluye Video Pro nativo; usar Modo Pro Foto como fallback."
                : oneUi.displayName + " carece de Video Pro nativo estable; usar Modo Pro Foto como fallback.";
        return new ModeDescriptor(
                ModeKind.PRO_VIDEO,
                36,
                oneUi,
                false,
                sensorSummary,
                reason,
                fallbackVariants
        );
    }

    private static ModeDescriptor resolveQrScanner(
            OneUiGeneration oneUi,
            String sensorSummary
    ) {
        List<LaunchVariant> variants = Arrays.asList(
                qrVariant(
                        "qr_scan_exported",
                        "Intent exportado SCAN_QR_CODE (ShootingMode 40 -> sensor 1x exclusivo)"
                ),
                mainCameraVariant(
                        "qr_fallback_pro",
                        CameraConfig.MODE_PRO_PHOTO,
                        CameraConfig.ACTIVITY_NAME_PRO_PHOTO,
                        "Fallback a Modo Pro Foto si el escáner QR está bloqueado"
                )
        );
        return new ModeDescriptor(
                ModeKind.QR_SCANNER,
                40,
                oneUi,
                true,
                sensorSummary,
                "Escáner QR abre únicamente el sensor trasero principal sin inicializar Ultra-Wide.",
                variants
        );
    }

    private static LaunchVariant mainCameraVariant(
            String variantId,
            String previousMode,
            String activityName,
            String description
    ) {
        return new LaunchVariant(
                variantId,
                ACTION_MAIN,
                CameraConfig.SAMSUNG_CAMERA_PACKAGE,
                CameraConfig.SAMSUNG_CAMERA_ACTIVITY,
                previousMode,
                activityName,
                CameraConfig.FACING_BACK,
                true,
                description
        );
    }

    private static LaunchVariant qrVariant(String variantId, String description) {
        return new LaunchVariant(
                variantId,
                CameraConfig.ACTION_SCAN_QR_CODE,
                CameraConfig.SAMSUNG_CAMERA_PACKAGE,
                "",
                "",
                "",
                CameraConfig.FACING_BACK,
                false,
                description
        );
    }

    private static String buildSensorSummary(SamsungModelDatabase.ModelProfile profile) {
        String mainId = profile.rearMainStandaloneId != null ? profile.rearMainStandaloneId : "56";
        if (profile.rearTeleStandaloneId != null && !profile.rearTeleStandaloneId.isEmpty()) {
            return "Principal 1x (ID " + mainId + ") + Telefoto (ID " + profile.rearTeleStandaloneId + ")";
        }
        return "Principal 1x (ID " + mainId + ")";
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
