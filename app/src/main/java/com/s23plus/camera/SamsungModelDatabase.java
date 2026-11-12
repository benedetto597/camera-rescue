package com.s23plus.camera;

/**
 * Catálogo heurístico de modelos Samsung Galaxy para identificar sensores de hardware,
 * IDs de bypass independientes y compatibilidad con modos de rescate.
 */
public final class SamsungModelDatabase {

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
            this.modelPrefix = modelPrefix;
            this.marketingName = marketingName;
            this.series = series;
            this.rearMainStandaloneId = rearMainStandaloneId;
            this.rearTeleStandaloneId = rearTeleStandaloneId;
            this.frontWideId = frontWideId;
            this.frontNormalId = frontNormalId;
            this.hasUltraWideFaultProneHistory = hasUltraWideFaultProneHistory;
            this.isVerifiedProfile = isVerifiedProfile;
        }
    }

    private SamsungModelDatabase() {
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

        // 1. Serie Galaxy S23 (SM-S91x)
        if (model.startsWith("SM-S916")) {
            return new ModelProfile(
                    "SM-S916", "Samsung Galaxy S23+", "S23",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-S911")) {
            return new ModelProfile(
                    "SM-S911", "Samsung Galaxy S23", "S23",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-S918")) {
            return new ModelProfile(
                    "SM-S918", "Samsung Galaxy S23 Ultra", "S23",
                    "56", "52", "1", "3", true, true
            );
        }

        // 2. Serie Galaxy S24 (SM-S92x)
        if (model.startsWith("SM-S928")) {
            return new ModelProfile(
                    "SM-S928", "Samsung Galaxy S24 Ultra", "S24",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-S926")) {
            return new ModelProfile(
                    "SM-S926", "Samsung Galaxy S24+", "S24",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-S921")) {
            return new ModelProfile(
                    "SM-S921", "Samsung Galaxy S24", "S24",
                    "56", "52", "1", "3", true, true
            );
        }

        // 3. Serie Galaxy S22 (SM-S90x)
        if (model.startsWith("SM-S908")) {
            return new ModelProfile(
                    "SM-S908", "Samsung Galaxy S22 Ultra", "S22",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-S906")) {
            return new ModelProfile(
                    "SM-S906", "Samsung Galaxy S22+", "S22",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-S901")) {
            return new ModelProfile(
                    "SM-S901", "Samsung Galaxy S22", "S22",
                    "56", "52", "1", "3", true, true
            );
        }

        // 4. Serie Galaxy S21 (SM-G99x)
        if (model.startsWith("SM-G998")) {
            return new ModelProfile(
                    "SM-G998", "Samsung Galaxy S21 Ultra", "S21",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-G996")) {
            return new ModelProfile(
                    "SM-G996", "Samsung Galaxy S21+ 5G", "S21",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-G991")) {
            return new ModelProfile(
                    "SM-G991", "Samsung Galaxy S21 5G", "S21",
                    "56", "52", "1", "3", true, true
            );
        }

        // 5. Serie Galaxy S20 (SM-G98x)
        if (model.startsWith("SM-G988")) {
            return new ModelProfile(
                    "SM-G988", "Samsung Galaxy S20 Ultra", "S20",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-G985") || model.startsWith("SM-G986")) {
            return new ModelProfile(
                    "SM-G986", "Samsung Galaxy S20+ 5G", "S20",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-G980") || model.startsWith("SM-G981")) {
            return new ModelProfile(
                    "SM-G981", "Samsung Galaxy S20 5G", "S20",
                    "56", "52", "1", "3", true, true
            );
        }

        // 6. Serie Galaxy Note (SM-N98x, SM-N97x, SM-N96x)
        if (model.startsWith("SM-N986")) {
            return new ModelProfile(
                    "SM-N986", "Samsung Galaxy Note 20 Ultra", "Note 20",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-N980") || model.startsWith("SM-N981") || model.startsWith("SM-N985")) {
            return new ModelProfile(
                    "SM-N980", "Samsung Galaxy Note 20", "Note 20",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-N975") || model.startsWith("SM-N976")) {
            return new ModelProfile(
                    "SM-N975", "Samsung Galaxy Note 10+", "Note 10",
                    "56", "52", "1", "3", false, true
            );
        } else if (model.startsWith("SM-N970") || model.startsWith("SM-N971")) {
            return new ModelProfile(
                    "SM-N970", "Samsung Galaxy Note 10", "Note 10",
                    "56", "52", "1", "3", false, true
            );
        } else if (model.startsWith("SM-N960")) {
            return new ModelProfile(
                    "SM-N960", "Samsung Galaxy Note 9", "Note 9",
                    "0", "1", "1", null, false, true
            );
        }

        // 7. Serie Galaxy Z (Plegables SM-F9x / SM-F7x)
        if (model.startsWith("SM-F946")) {
            return new ModelProfile(
                    "SM-F946", "Samsung Galaxy Z Fold 5", "Galaxy Z",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-F956")) {
            return new ModelProfile(
                    "SM-F956", "Samsung Galaxy Z Fold 6", "Galaxy Z",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-F9")) {
            return new ModelProfile(
                    "SM-F9", "Samsung Galaxy Z Fold", "Galaxy Z",
                    "56", "52", "1", "3", true, true
            );
        } else if (model.startsWith("SM-F7")) {
            return new ModelProfile(
                    "SM-F7", "Samsung Galaxy Z Flip", "Galaxy Z",
                    "56", null, "1", null, true, true
            );
        }

        // 8. Serie Galaxy A (SM-A5x, SM-A7x)
        if (model.startsWith("SM-A546")) {
            return new ModelProfile(
                    "SM-A546", "Samsung Galaxy A54 5G", "Galaxy A",
                    "56", null, "1", null, true, true
            );
        } else if (model.startsWith("SM-A556")) {
            return new ModelProfile(
                    "SM-A556", "Samsung Galaxy A55 5G", "Galaxy A",
                    "56", null, "1", null, true, true
            );
        } else if (model.startsWith("SM-A")) {
            return new ModelProfile(
                    "SM-A", "Samsung Galaxy A Series", "Galaxy A",
                    "56", null, "1", null, false, true
            );
        }

        // 9. Serie Galaxy S10 / S9 (SM-G97x, SM-G96x)
        if (model.startsWith("SM-G975")) {
            return new ModelProfile(
                    "SM-G975", "Samsung Galaxy S10+", "S10",
                    "56", "52", "1", "3", false, true
            );
        } else if (model.startsWith("SM-G973")) {
            return new ModelProfile(
                    "SM-G973", "Samsung Galaxy S10", "S10",
                    "56", "52", "1", "3", false, true
            );
        } else if (model.startsWith("SM-G960")) {
            return new ModelProfile(
                    "SM-G960", "Samsung Galaxy S9", "S9",
                    "0", null, "1", null, false, true
            );
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
                false
        );
    }
}
