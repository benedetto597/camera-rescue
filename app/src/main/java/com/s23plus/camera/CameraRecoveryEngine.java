package com.s23plus.camera;

import java.util.Locale;

/**
 * Motor heurístico puro para detectar bloqueos del HAL Camera2 / cierres rápidos de Samsung Camera
 * y construir planes escalonados de recuperación (etapas 0..3).
 *
 * Clase 100% Java pura (sin dependencias de android.*).
 */
public final class CameraRecoveryEngine {

    private static final String[] LOCKUP_KEYWORDS = {
            "hal",
            "timeout",
            "in_use",
            "camera_in_use",
            "codigo 1",
            "codigo 2",
            "codigo 3",
            "codigo 4",
            "codigo 5",
            "código 1",
            "código 2",
            "código 3",
            "código 4",
            "código 5",
            "disconnected",
            "desconectado",
            "busy",
            "ocupad"
    };

    /**
     * Acción de recuperación recomendada por el plan escalonado.
     */
    public enum RecoveryAction {
        FORCE_PRO_PRIMARY,
        FORCE_PRO_ALTERNATE,
        FORCE_QR_SINGLE_SENSOR,
        OPEN_CAMERA_APP_INFO
    }

    /**
     * Plan inmutable de recuperación para una etapa concreta (0..3).
     */
    public static final class RecoveryPlan {
        public final int stageIndex;
        public final RecoveryAction action;
        public final ShootingModeMap.LaunchVariant launchVariant;
        public final boolean shouldResetLocalSession;
        public final long cooldownMs;
        public final String title;
        public final String userMessage;
        public final boolean isFinalEscalation;

        public RecoveryPlan(
                int stageIndex,
                RecoveryAction action,
                ShootingModeMap.LaunchVariant launchVariant,
                boolean shouldResetLocalSession,
                long cooldownMs,
                String title,
                String userMessage,
                boolean isFinalEscalation
        ) {
            this.stageIndex = stageIndex;
            this.action = action == null ? RecoveryAction.FORCE_PRO_PRIMARY : action;
            this.launchVariant = launchVariant;
            this.shouldResetLocalSession = shouldResetLocalSession;
            this.cooldownMs = cooldownMs;
            this.title = normalize(title);
            this.userMessage = normalize(userMessage);
            this.isFinalEscalation = isFinalEscalation;
        }
    }

    private CameraRecoveryEngine() {
    }

    /**
     * Determina si el retorno a la app fue demasiado rápido tras lanzar Samsung Camera,
     * señalando un probable cierre inmediato por bloqueo HAL / EEPROM.
     */
    public static boolean isSuspectedCrashReturn(long launchTimestampMs, long resumeTimestampMs) {
        if (launchTimestampMs <= 0L || resumeTimestampMs < launchTimestampMs) {
            return false;
        }
        return (resumeTimestampMs - launchTimestampMs) <= CameraConfig.RAPID_RETURN_LOCKUP_THRESHOLD_MS;
    }

    /**
     * Indica si un código numérico corresponde a los errores CameraDevice (1..5).
     */
    public static boolean isCamera2LockupError(int errorCode) {
        return errorCode >= 1 && errorCode <= 5;
    }

    /**
     * Analiza un mensaje de error para detectar señales de bloqueo del HAL de cámara.
     */
    public static boolean isCameraErrorMessageLockup(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return false;
        }
        String lower = errorMessage.toLowerCase(Locale.ROOT);
        for (String keyword : LOCKUP_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calcula el siguiente índice de intento consecutivo (0..3), reiniciando a 0 si el bloqueo se resolvió.
     */
    public static int nextAttemptIndex(int currentAttemptIndex, boolean lockupCleared) {
        if (lockupCleared) {
            return 0;
        }
        return Math.min(Math.max(0, currentAttemptIndex) + 1, 3);
    }

    /**
     * Construye el plan escalonado de recuperación (etapas 0..3) según hardware, One UI e intentos previos.
     */
    public static RecoveryPlan buildRecoveryPlan(
            SamsungModelDatabase.ModelProfile profile,
            ShootingModeMap.OneUiGeneration oneUi,
            ShootingModeMap.ModeKind lastAttemptedMode,
            int consecutiveAttemptIndex
    ) {
        int stage = Math.max(0, Math.min(consecutiveAttemptIndex, 3));
        ShootingModeMap.ModeDescriptor proDesc = ShootingModeMap.resolve(
                profile, oneUi, ShootingModeMap.ModeKind.PRO_PHOTO
        );
        ShootingModeMap.ModeDescriptor qrDesc = ShootingModeMap.resolve(
                profile, oneUi, ShootingModeMap.ModeKind.QR_SCANNER
        );

        switch (stage) {
            case 0:
                return new RecoveryPlan(
                        0,
                        RecoveryAction.FORCE_PRO_PRIMARY,
                        proDesc.getPrimaryVariant(),
                        true,
                        CameraConfig.RECOVERY_HAL_COOLDOWN_MS,
                        "Reintento 1: Modo Pro seguro",
                        "Se liberará la sesión local y se relanzará Modo Pro con la variante principal para evitar el sensor averiado.",
                        false
                );
            case 1:
                return new RecoveryPlan(
                        1,
                        RecoveryAction.FORCE_PRO_ALTERNATE,
                        proDesc.getVariantAt(1),
                        true,
                        CameraConfig.RECOVERY_HAL_COOLDOWN_MS,
                        "Reintento 2: Variante alternativa Modo Pro",
                        "La variante primaria no estabilizó el HAL; se intentará el lanzamiento alternativo de Modo Pro tras enfriar el bus.",
                        false
                );
            case 2:
                return new RecoveryPlan(
                        2,
                        RecoveryAction.FORCE_QR_SINGLE_SENSOR,
                        qrDesc.getPrimaryVariant(),
                        true,
                        CameraConfig.RECOVERY_HAL_COOLDOWN_MS,
                        "Reintento 3: Aislamiento QR (sensor único)",
                        "Se usará el escáner QR nativo para forzar apertura exclusiva del sensor 1x sin inicializar la matriz multi-cámara.",
                        false
                );
            case 3:
            default:
                return new RecoveryPlan(
                        3,
                        RecoveryAction.OPEN_CAMERA_APP_INFO,
                        null,
                        true,
                        0L,
                        "Escalamiento final: Reiniciar proceso de Cámara",
                        "El HAL continúa bloqueado. Abre Información de la aplicación de Samsung Camera, pulsa \"Forzar detención\" y vuelve a lanzar Modo Pro.",
                        true
                );
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
