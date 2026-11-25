package com.s23plus.camera;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Guía interactiva paso a paso adaptada a cada generación de One UI y perfil de hardware
 * para fijar Modo Pro como entrada segura de cámara en Samsung Galaxy.
 *
 * Clase 100% Java pura (sin dependencias de android.*).
 */
public final class OneUiAssistantGuide {

    public static final String STEP_OPEN_PRO = "step_open_pro";
    public static final String STEP_SETTINGS_TO_KEEP = "step_settings_to_keep";
    public static final String STEP_PRO_AUTO_MODE = "step_pro_auto_mode";
    public static final String STEP_SIDE_KEY_DOUBLE_PRESS = "step_side_key_double_press";

    /**
     * Acción de UI sugerida para cada paso del asistente.
     */
    public enum StepAction {
        LAUNCH_PRO_MODE,
        OPEN_CAMERA_SETTINGS,
        OPEN_SIDE_KEY_SETTINGS,
        VERIFY_RECOVERY
    }

    /**
     * Paso inmutable del asistente de configuración One UI.
     */
    public static final class AssistantStep {
        public final String id;
        public final int stepNumber;
        public final String title;
        public final String menuPathEs;
        public final String menuPathEn;
        public final String instruction;
        public final StepAction action;
        public final String buttonLabel;

        public AssistantStep(
                String id,
                int stepNumber,
                String title,
                String menuPathEs,
                String menuPathEn,
                String instruction,
                StepAction action,
                String buttonLabel
        ) {
            this.id = normalize(id);
            this.stepNumber = stepNumber;
            this.title = normalize(title);
            this.menuPathEs = normalize(menuPathEs);
            this.menuPathEn = normalize(menuPathEn);
            this.instruction = normalize(instruction);
            this.action = action == null ? StepAction.LAUNCH_PRO_MODE : action;
            this.buttonLabel = normalize(buttonLabel);
        }
    }

    /**
     * Resumen inmutable del progreso de configuración del asistente.
     */
    public static final class ProgressSummary {
        public final int completedCount;
        public final int totalCount;
        public final int percent;
        public final boolean allCompleted;
        public final String nextPendingStepId;

        public ProgressSummary(
                int completedCount,
                int totalCount,
                int percent,
                boolean allCompleted,
                String nextPendingStepId
        ) {
            this.completedCount = completedCount;
            this.totalCount = totalCount;
            this.percent = percent;
            this.allCompleted = allCompleted;
            this.nextPendingStepId = normalize(nextPendingStepId);
        }
    }

    private OneUiAssistantGuide() {
    }

    /**
     * Construye los 4 pasos ordenados del asistente adaptados al perfil de hardware y generación de One UI.
     */
    public static List<AssistantStep> buildSteps(
            SamsungModelDatabase.ModelProfile profile,
            ShootingModeMap.OneUiGeneration oneUi
    ) {
        SamsungModelDatabase.ModelProfile safeProfile =
                profile != null ? profile : SamsungModelDatabase.lookup(null);
        ShootingModeMap.OneUiGeneration safeOneUi =
                oneUi != null ? oneUi : ShootingModeMap.OneUiGeneration.ONE_UI_6;

        AssistantStep step1 = buildStepOpenPro(safeProfile);
        AssistantStep step2 = buildStepSettingsToKeep(safeOneUi);
        AssistantStep step3 = buildStepProAutoMode(safeProfile);
        AssistantStep step4 = buildStepSideKeyDoublePress(safeOneUi);

        return Collections.unmodifiableList(Arrays.asList(step1, step2, step3, step4));
    }

    private static AssistantStep buildStepOpenPro(SamsungModelDatabase.ModelProfile profile) {
        String mainId = hasText(profile.rearMainStandaloneId)
                ? profile.rearMainStandaloneId
                : CameraConfig.REAR_MAIN_STANDALONE_ID;
        boolean hasTele = hasText(profile.rearTeleStandaloneId);

        StringBuilder instruction = new StringBuilder();
        instruction.append("Inicia Samsung Camera directamente en Modo Pro seguro para evitar el sensor secundario dañado y usar ");
        if (hasTele) {
            instruction.append("los sensores independientes 1x (ID ")
                    .append(mainId)
                    .append(") y Telefoto (ID ")
                    .append(profile.rearTeleStandaloneId)
                    .append(").");
        } else {
            instruction.append("el sensor principal independiente 1x (ID ")
                    .append(mainId)
                    .append(").");
        }

        return new AssistantStep(
                STEP_OPEN_PRO,
                1,
                "Abrir Samsung Camera en Modo Pro seguro",
                CameraConfig.APP_SHORT_NAME + " → Cámara Trasera Pro",
                CameraConfig.APP_SHORT_NAME + " → Rear Pro Camera",
                instruction.toString(),
                StepAction.LAUNCH_PRO_MODE,
                "Abrir Modo Pro"
        );
    }

    private static AssistantStep buildStepSettingsToKeep(ShootingModeMap.OneUiGeneration oneUi) {
        String menuPathEs = "Cámara → ⚙️ Ajustes → " + oneUi.settingsToKeepLabel + " → Modo de cámara (ON)";
        String menuPathEn = "Camera → ⚙️ Settings → Settings to keep → Camera mode (ON)";
        String instruction = "Dentro de Ajustes de Cámara en " + oneUi.displayName
                + ", entra en \"" + oneUi.settingsToKeepLabel
                + "\" y activa \"Modo de cámara\" (Settings to keep → Camera mode) para que Samsung Camera recuerde Modo Pro al abrir.";

        return new AssistantStep(
                STEP_SETTINGS_TO_KEEP,
                2,
                "Fijar Modo de cámara en " + oneUi.settingsToKeepLabel,
                menuPathEs,
                menuPathEn,
                instruction,
                StepAction.OPEN_CAMERA_SETTINGS,
                "Abrir Ajustes de Cámara"
        );
    }

    private static AssistantStep buildStepProAutoMode(SamsungModelDatabase.ModelProfile profile) {
        boolean hasTele = hasText(profile.rearTeleStandaloneId);

        StringBuilder instruction = new StringBuilder();
        instruction.append("En Modo Pro, deja ISO, Velocidad (Shutter Speed) y Balance de Blancos (WB) en AUTO y selecciona un lente sano (");
        if (hasTele) {
            instruction.append("1x W o Tele");
        } else {
            instruction.append("1x W");
        }
        instruction.append(").");

        if (profile.hasVariableAperture) {
            instruction.append(" Nota de hardware: este modelo tiene apertura dual variable (f/1.5–f/2.4); mantén exposición en AUTO si el diafragma mecánico falla.");
        }
        if (profile.hasFoldableHingeRisk) {
            instruction.append(" Nota de hardware: revisa el estado del flex de bisagra (plegable) si el visor parpadea al desplegar.");
        }

        return new AssistantStep(
                STEP_PRO_AUTO_MODE,
                3,
                "Dejar ISO / Shutter / WB en AUTO y elegir lente sano",
                "Modo Pro → ISO (AUTO) · SPEED (AUTO) · WB (AUTO) · Lente sano",
                "Pro Mode → ISO (AUTO) · SPEED (AUTO) · WB (AUTO) · Healthy lens",
                instruction.toString(),
                StepAction.LAUNCH_PRO_MODE,
                "Verificar Modo Pro"
        );
    }

    private static AssistantStep buildStepSideKeyDoublePress(ShootingModeMap.OneUiGeneration oneUi) {
        String menuPathEs = buildSideKeyPathEs(oneUi);
        String menuPathEn = "Settings → Advanced features → Side button → Double press → Open app → "
                + CameraConfig.APP_SHORT_NAME;
        String instruction = "Configura la pulsación doble en \"" + oneUi.sideKeyLabel
                + "\" (Double press → Open app → " + CameraConfig.APP_SHORT_NAME
                + ") para abrir la cámara de rescate al instante sin pasar por el modo Foto roto.";

        return new AssistantStep(
                STEP_SIDE_KEY_DOUBLE_PRESS,
                4,
                "Asignar pulsación doble en " + oneUi.sideKeyLabel,
                menuPathEs,
                menuPathEn,
                instruction,
                StepAction.OPEN_SIDE_KEY_SETTINGS,
                "Configurar " + oneUi.sideKeyLabel
        );
    }

    private static String buildSideKeyPathEs(ShootingModeMap.OneUiGeneration oneUi) {
        switch (oneUi) {
            case ONE_UI_7:
            case ONE_UI_8_PLUS:
                return "Ajustes → Funciones avanzadas → Botón lateral → Pulsación doble → Abrir aplicación → "
                        + CameraConfig.APP_SHORT_NAME;
            case ONE_UI_6:
                return "Ajustes → Funciones avanzadas → Botón lateral → Pulsar dos veces → Abrir aplicación → "
                        + CameraConfig.APP_SHORT_NAME;
            case ONE_UI_3:
            case ONE_UI_4:
            case ONE_UI_5:
                return "Ajustes → Funciones avanzadas → Tecla lateral → Pulsar dos veces → Abrir aplicación → "
                        + CameraConfig.APP_SHORT_NAME;
            case ONE_UI_2:
            default:
                return "Ajustes → Funciones avanzadas → Tecla lateral (o Inicio rápido) → Pulsar dos veces → Abrir aplicación → "
                        + CameraConfig.APP_SHORT_NAME;
        }
    }

    /**
     * Filtra IDs desconocidos y preserva el orden definido por la lista de pasos.
     */
    public static Set<String> sanitizeCompletedStepIds(Collection<String> rawIds, List<AssistantStep> steps) {
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        if (rawIds == null || steps == null || steps.isEmpty()) {
            return sanitized;
        }
        for (AssistantStep step : steps) {
            if (step != null && hasText(step.id) && rawIds.contains(step.id)) {
                sanitized.add(step.id);
            }
        }
        return sanitized;
    }

    /**
     * Calcula el resumen de progreso considerando únicamente IDs válidos presentes en steps.
     */
    public static ProgressSummary computeProgress(List<AssistantStep> steps, Set<String> completedStepIds) {
        if (steps == null || steps.isEmpty()) {
            return new ProgressSummary(0, 0, 0, false, "");
        }
        Set<String> validCompleted = sanitizeCompletedStepIds(completedStepIds, steps);
        int totalCount = steps.size();
        int completedCount = validCompleted.size();
        int percent = (completedCount * 100) / totalCount;
        boolean allCompleted = completedCount == totalCount;
        String nextPendingStepId = "";
        for (AssistantStep step : steps) {
            if (step != null && hasText(step.id) && !validCompleted.contains(step.id)) {
                nextPendingStepId = step.id;
                break;
            }
        }
        return new ProgressSummary(completedCount, totalCount, percent, allCompleted, nextPendingStepId);
    }

    /**
     * Serializa un conjunto de IDs de pasos completados en una cadena determinista separada por comas.
     */
    public static String serializeCompletedStepIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String id : ids) {
            if (id != null) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    sorted.add(trimmed);
                }
            }
        }
        return String.join(",", sorted);
    }

    /**
     * Deserializa una cadena separada por comas en un conjunto ordenado de IDs sin tokens vacíos.
     */
    public static Set<String> deserializeCompletedStepIds(String serialized) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (serialized == null || serialized.trim().isEmpty()) {
            return result;
        }
        String[] tokens = serialized.split(",");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
