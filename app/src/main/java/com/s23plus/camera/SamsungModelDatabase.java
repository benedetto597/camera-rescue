package com.s23plus.camera;

import java.util.Arrays;
import java.util.List;

/**
 * Catálogo heurístico de modelos Samsung Galaxy para identificar sensores de hardware,
 * IDs de bypass independientes y compatibilidad con modos de rescate.
 */
public final class SamsungModelDatabase {

    /**
     * Ficha técnica de hardware asociada a una familia/prefijo de modelo Samsung Galaxy.
     */
    public static final class ModelProfile {
        public final String modelPrefix;
        public final String marketingName;
        public final String series;
        public final String rearMainStandaloneId;
        public final String rearTeleStandaloneId;
        public final String frontWideId;
        public final String frontNormalId;
        public final boolean hasUltraWideFaultProneHistory;
        public final boolean isVerifiedProfile;
        public final boolean supportsNativeProVideo;
        public final boolean hasVariableAperture;
        public final boolean hasFoldableHingeRisk;
        public final String hardwareNotes;

        public ModelProfile(
                String modelPrefix,
                String marketingName,
                String series,
                String rearMainStandaloneId,
                String rearTeleStandaloneId,
                String frontWideId,
                String frontNormalId,
                boolean hasUltraWideFaultProneHistory,
                boolean isVerifiedProfile
        ) {
            this(
                    modelPrefix,
                    marketingName,
                    series,
                    rearMainStandaloneId,
                    rearTeleStandaloneId,
                    frontWideId,
                    frontNormalId,
                    hasUltraWideFaultProneHistory,
                    isVerifiedProfile,
                    true,
                    false,
                    false,
                    ""
            );
        }

        public ModelProfile(
                String modelPrefix,
                String marketingName,
                String series,
                String rearMainStandaloneId,
                String rearTeleStandaloneId,
                String frontWideId,
                String frontNormalId,
                boolean hasUltraWideFaultProneHistory,
                boolean isVerifiedProfile,
                boolean supportsNativeProVideo,
                boolean hasVariableAperture,
                boolean hasFoldableHingeRisk,
                String hardwareNotes
        ) {
            this.modelPrefix = modelPrefix;
            this.marketingName = marketingName;
            this.series = series;
            this.rearMainStandaloneId = rearMainStandaloneId;
            this.rearTeleStandaloneId = rearTeleStandaloneId;
            this.frontWideId = frontWideId;
            this.frontNormalId = frontNormalId;
            this.hasUltraWideFaultProneHistory = hasUltraWideFaultProneHistory;
            this.isVerifiedProfile = isVerifiedProfile;
            this.supportsNativeProVideo = supportsNativeProVideo;
            this.hasVariableAperture = hasVariableAperture;
            this.hasFoldableHingeRisk = hasFoldableHingeRisk;
            this.hardwareNotes = hardwareNotes == null ? "" : hardwareNotes;
        }
    }

    private static final class ModelRule {
        final String[] prefixes;
        final ModelProfile profile;

        ModelRule(ModelProfile profile, String... prefixes) {
            this.profile = profile;
            this.prefixes = prefixes;
        }

        boolean matches(String normalizedModel) {
            for (String prefix : prefixes) {
                if (normalizedModel.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final String NOTE_FLAGSHIP_BYPASS =
            "Grupo lógico 20 vulnerable a fallo I2C/EEPROM en Ultra-Wide; Pro/QR aíslan 1x (56) y Tele (52).";
    private static final String NOTE_S25_EDGE =
            "Chasis delgado sin telefoto dedicado; usa sensor principal 1x (56) y frontales 1/3.";
    private static final String NOTE_FE_BYPASS =
            "Edición Fan con telefoto independiente (52) y principal (56); evitar grupo multi-lente.";
    private static final String NOTE_VAR_APERTURE_PRO =
            "Apertura mecánica variable f/1.5-f/2.4 en sensor 1x; si el diafragma se atasca, Modo Pro estabiliza exposición.";
    private static final String NOTE_LEGACY_NO_PRO_VIDEO =
            "Generación previa sin Video Pro nativo completo; el rescate de video redirige a Modo Pro.";
    private static final String NOTE_FOLDABLE_FOLD =
            "Plegable con riesgo de micro-fractura en flex de bisagra sobre módulo Ultra-Wide; usar IDs 56 y 52.";
    private static final String NOTE_FOLDABLE_FLIP =
            "Plegable tipo Flip sin telefoto; riesgo de flex en bisagra afectando Ultra-Wide, aislar principal 56.";
    private static final String NOTE_GALAXY_A_SINGLE_REAR =
            "Gama A sin Video Pro nativo ni telefoto independiente; usar Modo Pro o Escáner QR sobre sensor 56.";
    private static final String NOTE_GALAXY_A72 =
            "Galaxy A72 incluye telefoto 3x (52) pero carece de Video Pro nativo en el HAL.";

    private static final List<ModelRule> RULES = Arrays.asList(
            // 1. Serie Galaxy S25 (SM-S93x)
            rule("SM-S938", "Samsung Galaxy S25 Ultra", "S25", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S936", "Samsung Galaxy S25+", "S25", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S931", "Samsung Galaxy S25", "S25", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S937", "Samsung Galaxy S25 Edge", "S25", "56", null, "1", "3", true, true, true, false, false, NOTE_S25_EDGE),

            // 2. Serie Galaxy S24 (SM-S92x, SM-S721)
            rule("SM-S928", "Samsung Galaxy S24 Ultra", "S24", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S926", "Samsung Galaxy S24+", "S24", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S921", "Samsung Galaxy S24", "S24", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S721", "Samsung Galaxy S24 FE", "S24", "56", "52", "1", "3", true, true, true, false, false, NOTE_FE_BYPASS),

            // 3. Serie Galaxy S23 (SM-S91x, SM-S711)
            rule("SM-S916", "Samsung Galaxy S23+", "S23", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S911", "Samsung Galaxy S23", "S23", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S918", "Samsung Galaxy S23 Ultra", "S23", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S711", "Samsung Galaxy S23 FE", "S23", "56", "52", "1", "3", true, true, true, false, false, NOTE_FE_BYPASS),

            // 4. Serie Galaxy S22 (SM-S90x)
            rule("SM-S908", "Samsung Galaxy S22 Ultra", "S22", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S906", "Samsung Galaxy S22+", "S22", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-S901", "Samsung Galaxy S22", "S22", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),

            // 5. Serie Galaxy S21 (SM-G99x)
            rule("SM-G998", "Samsung Galaxy S21 Ultra", "S21", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-G996", "Samsung Galaxy S21+ 5G", "S21", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-G991", "Samsung Galaxy S21 5G", "S21", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule("SM-G990", "Samsung Galaxy S21 FE 5G", "S21", "56", "52", "1", "3", true, true, true, false, false, NOTE_FE_BYPASS),

            // 6. Serie Galaxy S20 (SM-G98x, SM-G78x)
            rule("SM-G988", "Samsung Galaxy S20 Ultra", "S20", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule(new String[]{"SM-G985", "SM-G986"}, "SM-G986", "Samsung Galaxy S20+ 5G", "S20", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule(new String[]{"SM-G980", "SM-G981"}, "SM-G981", "Samsung Galaxy S20 5G", "S20", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule(new String[]{"SM-G780", "SM-G781"}, "SM-G781", "Samsung Galaxy S20 FE", "S20", "56", "52", "1", null, true, true, true, false, false, NOTE_FE_BYPASS),

            // 7. Serie Galaxy Note (SM-N98x, SM-N97x, SM-N96x)
            rule("SM-N986", "Samsung Galaxy Note 20 Ultra", "Note 20", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule(new String[]{"SM-N980", "SM-N981", "SM-N985"}, "SM-N980", "Samsung Galaxy Note 20", "Note 20", "56", "52", "1", "3", true, true, true, false, false, NOTE_FLAGSHIP_BYPASS),
            rule(new String[]{"SM-N975", "SM-N976"}, "SM-N975", "Samsung Galaxy Note 10+", "Note 10", "56", "52", "1", "3", false, true, true, true, false, NOTE_VAR_APERTURE_PRO),
            rule(new String[]{"SM-N970", "SM-N971"}, "SM-N970", "Samsung Galaxy Note 10", "Note 10", "56", "52", "1", "3", false, true, true, true, false, NOTE_VAR_APERTURE_PRO),
            rule("SM-N960", "Samsung Galaxy Note 9", "Note 9", "0", "52", "1", null, false, true, false, true, false, NOTE_VAR_APERTURE_PRO + " " + NOTE_LEGACY_NO_PRO_VIDEO),

            // 8. Serie Galaxy S10 / S9 (SM-G97x, SM-G96x)
            rule("SM-G977", "Samsung Galaxy S10 5G", "S10", "56", "52", "1", "3", false, true, true, true, false, NOTE_VAR_APERTURE_PRO),
            rule("SM-G975", "Samsung Galaxy S10+", "S10", "56", "52", "1", "3", false, true, true, true, false, NOTE_VAR_APERTURE_PRO),
            rule("SM-G973", "Samsung Galaxy S10", "S10", "56", "52", "1", "3", false, true, true, true, false, NOTE_VAR_APERTURE_PRO),
            rule("SM-G970", "Samsung Galaxy S10e", "S10", "56", null, "1", null, false, true, true, true, false, NOTE_VAR_APERTURE_PRO),
            rule("SM-G965", "Samsung Galaxy S9+", "S9", "0", "52", "1", null, false, true, false, true, false, NOTE_VAR_APERTURE_PRO + " " + NOTE_LEGACY_NO_PRO_VIDEO),
            rule("SM-G960", "Samsung Galaxy S9", "S9", "0", null, "1", null, false, true, false, true, false, NOTE_VAR_APERTURE_PRO + " " + NOTE_LEGACY_NO_PRO_VIDEO),

            // 9. Serie Galaxy Z (Plegables SM-F9xx / SM-F7xx)
            rule("SM-F956", "Samsung Galaxy Z Fold 6", "Galaxy Z", "56", "52", "1", "3", true, true, true, false, true, NOTE_FOLDABLE_FOLD),
            rule("SM-F946", "Samsung Galaxy Z Fold 5", "Galaxy Z", "56", "52", "1", "3", true, true, true, false, true, NOTE_FOLDABLE_FOLD),
            rule("SM-F936", "Samsung Galaxy Z Fold 4", "Galaxy Z", "56", "52", "1", "3", true, true, true, false, true, NOTE_FOLDABLE_FOLD),
            rule("SM-F926", "Samsung Galaxy Z Fold 3", "Galaxy Z", "56", "52", "1", "3", true, true, true, false, true, NOTE_FOLDABLE_FOLD),
            rule("SM-F916", "Samsung Galaxy Z Fold 2", "Galaxy Z", "56", "52", "1", "3", true, true, true, false, true, NOTE_FOLDABLE_FOLD),
            rule(new String[]{"SM-F900", "SM-F907"}, "SM-F900", "Samsung Galaxy Fold", "Galaxy Z", "56", "52", "1", "3", true, true, true, false, true, NOTE_FOLDABLE_FOLD),
            rule("SM-F9", "Samsung Galaxy Z Fold", "Galaxy Z", "56", "52", "1", "3", true, true, true, false, true, NOTE_FOLDABLE_FOLD),

            rule("SM-F741", "Samsung Galaxy Z Flip 6", "Galaxy Z", "56", null, "1", null, true, true, true, false, true, NOTE_FOLDABLE_FLIP),
            rule("SM-F731", "Samsung Galaxy Z Flip 5", "Galaxy Z", "56", null, "1", null, true, true, true, false, true, NOTE_FOLDABLE_FLIP),
            rule("SM-F721", "Samsung Galaxy Z Flip 4", "Galaxy Z", "56", null, "1", null, true, true, true, false, true, NOTE_FOLDABLE_FLIP),
            rule("SM-F711", "Samsung Galaxy Z Flip 3", "Galaxy Z", "56", null, "1", null, true, true, true, false, true, NOTE_FOLDABLE_FLIP),
            rule(new String[]{"SM-F700", "SM-F707"}, "SM-F700", "Samsung Galaxy Z Flip", "Galaxy Z", "56", null, "1", null, true, true, false, false, true, NOTE_FOLDABLE_FLIP + " " + NOTE_LEGACY_NO_PRO_VIDEO),
            rule("SM-F7", "Samsung Galaxy Z Flip", "Galaxy Z", "56", null, "1", null, true, true, true, false, true, NOTE_FOLDABLE_FLIP),

            // 10. Serie Galaxy A (SM-A3xx, SM-A5xx, SM-A7xx y fallback SM-A)
            rule("SM-A556", "Samsung Galaxy A55 5G", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule("SM-A546", "Samsung Galaxy A54 5G", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule("SM-A536", "Samsung Galaxy A53 5G", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule(new String[]{"SM-A525", "SM-A526", "SM-A528"}, "SM-A525", "Samsung Galaxy A52 / A52s 5G", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule(new String[]{"SM-A515", "SM-A516"}, "SM-A515", "Samsung Galaxy A51", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule("SM-A736", "Samsung Galaxy A73 5G", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule("SM-A725", "Samsung Galaxy A72", "Galaxy A", "56", "52", "1", null, true, true, false, false, false, NOTE_GALAXY_A72),
            rule(new String[]{"SM-A715", "SM-A716"}, "SM-A715", "Samsung Galaxy A71", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule("SM-A356", "Samsung Galaxy A35 5G", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule("SM-A346", "Samsung Galaxy A34 5G", "Galaxy A", "56", null, "1", null, true, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR),
            rule("SM-A", "Samsung Galaxy A Series", "Galaxy A", "56", null, "1", null, false, true, false, false, false, NOTE_GALAXY_A_SINGLE_REAR)
    );

    private SamsungModelDatabase() {
    }

    private static ModelRule rule(
            String prefix,
            String marketingName,
            String series,
            String rearMainId,
            String rearTeleId,
            String frontWideId,
            String frontNormalId,
            boolean uwFault,
            boolean verified,
            boolean supportsProVideo,
            boolean variableAperture,
            boolean foldableHingeRisk,
            String notes
    ) {
        return rule(
                new String[]{prefix},
                prefix,
                marketingName,
                series,
                rearMainId,
                rearTeleId,
                frontWideId,
                frontNormalId,
                uwFault,
                verified,
                supportsProVideo,
                variableAperture,
                foldableHingeRisk,
                notes
        );
    }

    private static ModelRule rule(
            String[] prefixes,
            String modelPrefix,
            String marketingName,
            String series,
            String rearMainId,
            String rearTeleId,
            String frontWideId,
            String frontNormalId,
            boolean uwFault,
            boolean verified,
            boolean supportsProVideo,
            boolean variableAperture,
            boolean foldableHingeRisk,
            String notes
    ) {
        return new ModelRule(
                new ModelProfile(
                        modelPrefix,
                        marketingName,
                        series,
                        rearMainId,
                        rearTeleId,
                        frontWideId,
                        frontNormalId,
                        uwFault,
                        verified,
                        supportsProVideo,
                        variableAperture,
                        foldableHingeRisk,
                        notes
                ),
                prefixes
        );
    }

    /**
     * Busca el perfil de hardware correspondiente a un modelo de dispositivo (ej. Build.MODEL).
     *
     * @param rawModel identificador del modelo (ej. "SM-S916U1", "SM-S928B", etc.)
     * @return ModelProfile con la ficha de hardware y los IDs de sensores correspondientes
     */
    public static ModelProfile lookup(String rawModel) {
        if (rawModel == null || rawModel.trim().isEmpty()) {
            return buildGenericFallback("Desconocido");
        }
        String model = rawModel.trim().toUpperCase();
        for (ModelRule rule : RULES) {
            if (rule.matches(model)) {
                return rule.profile;
            }
        }
        return buildGenericFallback(model);
    }

    private static ModelProfile buildGenericFallback(String model) {
        return new ModelProfile(
                model,
                "Samsung Galaxy (Generico)",
                "Generico",
                "56",
                "52",
                "1",
                "3",
                false,
                false,
                true,
                false,
                false,
                "Perfil genérico heurístico: se asume sensor principal 1x (56) y telefoto (52) si están presentes en HAL."
        );
    }
}
