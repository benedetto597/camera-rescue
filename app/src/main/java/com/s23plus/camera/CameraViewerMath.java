package com.s23plus.camera;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Funciones puras de cálculo y normalización para el visor Camera2 universal
 * (zoom, compensación EV, apertura variable, flash/linterna, cuadrícula,
 * temporizador, orientación de captura y selección de tamaño de video).
 */
public final class CameraViewerMath {

    private static final float ZOOM_MIN_FLOOR = 0.1f;
    private static final float ZOOM_PRESET_EPSILON = 0.01f;
    private static final float APERTURE_DEDUP_EPSILON = 0.01f;
    private static final float APERTURE_MATCH_EPSILON = 0.05f;
    private static final double TARGET_ASPECT_16_9 = 16.0 / 9.0;

    public enum FlashMode {
        OFF("Flash Off"),
        AUTO("Flash Auto"),
        ON("Flash On"),
        TORCH("Linterna");

        public final String displayLabel;

        FlashMode(String displayLabel) {
            this.displayLabel = displayLabel;
        }
    }

    public enum GridMode {
        OFF("Sin guía"),
        RULE_OF_THIRDS("3x3"),
        CROSSHAIR("Cruz");

        public final String displayLabel;

        GridMode(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public GridMode next() {
            switch (this) {
                case OFF:
                    return RULE_OF_THIRDS;
                case RULE_OF_THIRDS:
                    return CROSSHAIR;
                case CROSSHAIR:
                default:
                    return OFF;
            }
        }
    }

    public enum ZoomStrategy {
        ZOOM_RATIO,
        SCALER_CROP_REGION,
        NONE
    }

    public static final class ZoomCapabilities {
        public final float minZoom;
        public final float maxZoom;
        public final boolean supportsZoomRatioApi;
        public final int activeArrayWidth;
        public final int activeArrayHeight;

        public ZoomCapabilities(
                float minZoom,
                float maxZoom,
                boolean supportsZoomRatioApi,
                int activeArrayWidth,
                int activeArrayHeight
        ) {
            float normalizedMin = (!Float.isFinite(minZoom) || minZoom <= 0.0f)
                    ? 1.0f
                    : Math.max(ZOOM_MIN_FLOOR, minZoom);
            float normalizedMax = (!Float.isFinite(maxZoom) || maxZoom < normalizedMin)
                    ? normalizedMin
                    : maxZoom;

            this.minZoom = normalizedMin;
            this.maxZoom = normalizedMax;
            this.supportsZoomRatioApi = supportsZoomRatioApi;
            this.activeArrayWidth = Math.max(0, activeArrayWidth);
            this.activeArrayHeight = Math.max(0, activeArrayHeight);
        }
    }

    public static final class ZoomApplication {
        public final ZoomStrategy strategy;
        public final float clampedZoom;
        public final int cropLeft;
        public final int cropTop;
        public final int cropRight;
        public final int cropBottom;

        public ZoomApplication(
                ZoomStrategy strategy,
                float clampedZoom,
                int cropLeft,
                int cropTop,
                int cropRight,
                int cropBottom
        ) {
            this.strategy = strategy != null ? strategy : ZoomStrategy.NONE;
            this.clampedZoom = clampedZoom;
            this.cropLeft = cropLeft;
            this.cropTop = cropTop;
            this.cropRight = cropRight;
            this.cropBottom = cropBottom;
        }
    }

    public static final class EvCapabilities {
        public final int minIndex;
        public final int maxIndex;
        public final int stepNumerator;
        public final int stepDenominator;
        public final boolean supported;

        public EvCapabilities(int minIndex, int maxIndex, int stepNumerator, int stepDenominator) {
            this.minIndex = minIndex;
            this.maxIndex = maxIndex;
            this.stepNumerator = stepNumerator;
            this.stepDenominator = stepDenominator;
            this.supported = (minIndex < maxIndex && stepNumerator > 0 && stepDenominator > 0);
        }
    }

    public static final class ApertureCapabilities {
        public static final float APERTURE_AUTO = 0.0f;

        private final float[] supportedApertures;
        public final boolean variableApertureSupported;

        public ApertureCapabilities(float[] rawApertures) {
            this.supportedApertures = normalizeApertures(rawApertures);
            this.variableApertureSupported = this.supportedApertures.length >= 2;
        }

        public float[] getSupportedApertures() {
            return supportedApertures.clone();
        }
    }

    private CameraViewerMath() {
        // Clase de utilidad no instanciable
    }

    public static float clampZoom(float requestedZoom, ZoomCapabilities caps) {
        if (caps == null) {
            return 1.0f;
        }
        if (!Float.isFinite(requestedZoom)) {
            return clampZoom(1.0f, caps);
        }
        return Math.max(caps.minZoom, Math.min(caps.maxZoom, requestedZoom));
    }

    public static ZoomApplication resolveZoomApplication(float requestedZoom, int sdkInt, ZoomCapabilities caps) {
        if (caps == null) {
            return new ZoomApplication(ZoomStrategy.NONE, 1.0f, 0, 0, 0, 0);
        }

        float clamped = clampZoom(requestedZoom, caps);
        int left = 0;
        int top = 0;
        int right = 0;
        int bottom = 0;

        if (caps.activeArrayWidth > 0 && caps.activeArrayHeight > 0) {
            float effectiveCropZoom = Math.max(1.0f, clamped);
            int w = caps.activeArrayWidth;
            int h = caps.activeArrayHeight;
            int cropW = Math.max(1, Math.min(w, Math.round(w / effectiveCropZoom)));
            int cropH = Math.max(1, Math.min(h, Math.round(h / effectiveCropZoom)));
            left = (w - cropW) / 2;
            top = (h - cropH) / 2;
            right = left + cropW;
            bottom = top + cropH;
        }

        if (sdkInt >= 30 && caps.supportsZoomRatioApi && caps.maxZoom > caps.minZoom) {
            return new ZoomApplication(ZoomStrategy.ZOOM_RATIO, clamped, left, top, right, bottom);
        }
        if (caps.activeArrayWidth > 0 && caps.activeArrayHeight > 0 && caps.maxZoom > 1.0f) {
            return new ZoomApplication(ZoomStrategy.SCALER_CROP_REGION, Math.max(1.0f, clamped), left, top, right, bottom);
        }
        return new ZoomApplication(ZoomStrategy.NONE, clamped, left, top, right, bottom);
    }

    public static float stepZoom(float currentZoom, float delta, ZoomCapabilities caps) {
        if (caps == null) {
            return 1.0f;
        }
        float base = Float.isFinite(currentZoom) ? currentZoom : 1.0f;
        float safeDelta = Float.isFinite(delta) ? delta : 0.0f;
        float rounded = Math.round((base + safeDelta) * 10.0f) / 10.0f;
        return clampZoom(rounded, caps);
    }

    public static float[] presetZoomStops(ZoomCapabilities caps) {
        if (caps == null) {
            return new float[]{1.0f};
        }

        List<Float> stops = new ArrayList<>();
        if (caps.minZoom < 0.95f) {
            addUniqueStop(stops, caps.minZoom);
        }

        float[] standardStops = new float[]{1.0f, 2.0f, 4.0f};
        for (float stop : standardStops) {
            if (stop >= caps.minZoom - ZOOM_PRESET_EPSILON && stop <= caps.maxZoom + ZOOM_PRESET_EPSILON) {
                addUniqueStop(stops, clampZoom(stop, caps));
            }
        }

        if (caps.maxZoom > 1.0f) {
            addUniqueStop(stops, caps.maxZoom);
        }

        if (stops.isEmpty()) {
            stops.add(clampZoom(1.0f, caps));
        }

        float[] result = new float[stops.size()];
        for (int i = 0; i < stops.size(); i++) {
            result[i] = stops.get(i);
        }
        return result;
    }

    public static String formatZoomLabel(float zoom) {
        if (!Float.isFinite(zoom)) {
            return "1.0x";
        }
        return String.format(Locale.US, "%.1fx", zoom);
    }

    public static int clampEvIndex(int requestedIndex, EvCapabilities caps) {
        if (caps == null || !caps.supported) {
            return 0;
        }
        return Math.max(caps.minIndex, Math.min(caps.maxIndex, requestedIndex));
    }

    public static String formatEvValue(int index, EvCapabilities caps) {
        if (caps == null || !caps.supported) {
            return "+0.0 EV";
        }
        int clamped = clampEvIndex(index, caps);
        double ev = ((double) clamped * caps.stepNumerator) / caps.stepDenominator;
        if (Math.abs(ev) < 0.0001) {
            ev = 0.0;
        }
        String formatted = String.format(Locale.US, "%+.1f EV", ev);
        return "-0.0 EV".equals(formatted) ? "+0.0 EV" : formatted;
    }

    public static ApertureCapabilities buildApertureCapabilities(float[] rawApertures) {
        return new ApertureCapabilities(rawApertures);
    }

    public static float resolveNextAperture(float currentAperture, ApertureCapabilities caps) {
        if (caps == null || !caps.variableApertureSupported) {
            return ApertureCapabilities.APERTURE_AUTO;
        }
        float[] apertures = caps.getSupportedApertures();
        if (!Float.isFinite(currentAperture) || currentAperture <= 0.0f) {
            return apertures[0];
        }
        int matchedIndex = findClosestApertureIndex(currentAperture, apertures);
        if (matchedIndex < 0) {
            return ApertureCapabilities.APERTURE_AUTO;
        }
        return (matchedIndex + 1 < apertures.length)
                ? apertures[matchedIndex + 1]
                : ApertureCapabilities.APERTURE_AUTO;
    }

    public static float sanitizeApertureSelection(float requestedAperture, ApertureCapabilities caps) {
        if (caps == null || !caps.variableApertureSupported
                || !Float.isFinite(requestedAperture) || requestedAperture <= 0.0f) {
            return ApertureCapabilities.APERTURE_AUTO;
        }
        float[] apertures = caps.getSupportedApertures();
        int matchedIndex = findClosestApertureIndex(requestedAperture, apertures);
        return matchedIndex >= 0 ? apertures[matchedIndex] : ApertureCapabilities.APERTURE_AUTO;
    }

    public static String formatApertureLabel(float selectedAperture, ApertureCapabilities caps) {
        float sanitized = sanitizeApertureSelection(selectedAperture, caps);
        if (sanitized <= 0.0f) {
            return "f/Auto";
        }
        return String.format(Locale.US, "f/%.1f", sanitized);
    }

    public static FlashMode resolveFlashMode(FlashMode requested, boolean flashAvailable, boolean isVideoMode) {
        if (!flashAvailable || requested == null) {
            return FlashMode.OFF;
        }
        if (isVideoMode && requested != FlashMode.TORCH) {
            return FlashMode.OFF;
        }
        return requested;
    }

    public static FlashMode nextFlashMode(FlashMode current, boolean flashAvailable, boolean isVideoMode) {
        if (!flashAvailable) {
            return FlashMode.OFF;
        }
        if (isVideoMode) {
            return (current == FlashMode.TORCH) ? FlashMode.OFF : FlashMode.TORCH;
        }
        if (current == null) {
            return FlashMode.AUTO;
        }
        switch (current) {
            case OFF:
                return FlashMode.AUTO;
            case AUTO:
                return FlashMode.ON;
            case ON:
                return FlashMode.TORCH;
            case TORCH:
            default:
                return FlashMode.OFF;
        }
    }

    public static int sanitizeTimerSeconds(int seconds) {
        if (seconds == 3 || seconds == 5 || seconds == 10) {
            return seconds;
        }
        return 0;
    }

    public static int nextTimerSeconds(int currentSeconds) {
        switch (currentSeconds) {
            case 0:
                return 3;
            case 3:
                return 5;
            case 5:
                return 10;
            case 10:
            default:
                return 0;
        }
    }

    public static int computeCaptureOrientation(
            int sensorOrientation,
            int displayRotationDegrees,
            boolean isFrontFacing
    ) {
        int raw = isFrontFacing
                ? (sensorOrientation + displayRotationDegrees)
                : (sensorOrientation - displayRotationDegrees);
        return ((raw % 360) + 360) % 360;
    }

    public static int[] chooseBestVideoSize(int[][] candidateSizes, int maxWidth, int maxHeight) {
        if (candidateSizes == null || candidateSizes.length == 0) {
            return new int[]{1280, 720};
        }

        List<int[]> valid = new ArrayList<>();
        for (int[] size : candidateSizes) {
            if (size != null && size.length >= 2 && size[0] > 0 && size[1] > 0) {
                valid.add(new int[]{size[0], size[1]});
            }
        }
        if (valid.isEmpty()) {
            return new int[]{1280, 720};
        }

        if (1920 <= maxWidth && 1080 <= maxHeight) {
            for (int[] size : valid) {
                if (size[0] == 1920 && size[1] == 1080) {
                    return new int[]{1920, 1080};
                }
            }
        }

        int[] bestWithinBounds = null;
        for (int[] size : valid) {
            if (size[0] <= maxWidth && size[1] <= maxHeight) {
                if (bestWithinBounds == null || isBetterMaxArea(size, bestWithinBounds)) {
                    bestWithinBounds = size;
                }
            }
        }
        if (bestWithinBounds != null) {
            return new int[]{bestWithinBounds[0], bestWithinBounds[1]};
        }

        int[] smallestPositive = valid.get(0);
        for (int i = 1; i < valid.size(); i++) {
            int[] candidate = valid.get(i);
            if (isBetterMinArea(candidate, smallestPositive)) {
                smallestPositive = candidate;
            }
        }
        return new int[]{smallestPositive[0], smallestPositive[1]};
    }

    private static void addUniqueStop(List<Float> stops, float candidate) {
        for (float existing : stops) {
            if (Math.abs(existing - candidate) <= ZOOM_PRESET_EPSILON) {
                return;
            }
        }
        stops.add(candidate);
    }

    private static float[] normalizeApertures(float[] rawApertures) {
        if (rawApertures == null || rawApertures.length == 0) {
            return new float[0];
        }
        float[] validBuffer = new float[rawApertures.length];
        int count = 0;
        for (float value : rawApertures) {
            if (Float.isFinite(value) && value > 0.0f) {
                validBuffer[count++] = value;
            }
        }
        if (count == 0) {
            return new float[0];
        }
        Arrays.sort(validBuffer, 0, count);

        float[] deduped = new float[count];
        int uniqueCount = 0;
        for (int i = 0; i < count; i++) {
            if (uniqueCount == 0 || Math.abs(validBuffer[i] - deduped[uniqueCount - 1]) > APERTURE_DEDUP_EPSILON) {
                deduped[uniqueCount++] = validBuffer[i];
            }
        }
        return Arrays.copyOf(deduped, uniqueCount);
    }

    private static int findClosestApertureIndex(float target, float[] apertures) {
        int bestIdx = -1;
        float bestDiff = Float.MAX_VALUE;
        for (int i = 0; i < apertures.length; i++) {
            float diff = Math.abs(apertures[i] - target);
            if (diff <= APERTURE_MATCH_EPSILON && diff < bestDiff) {
                bestDiff = diff;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private static boolean isBetterMaxArea(int[] candidate, int[] currentBest) {
        long candidateArea = (long) candidate[0] * candidate[1];
        long currentArea = (long) currentBest[0] * currentBest[1];
        if (candidateArea != currentArea) {
            return candidateArea > currentArea;
        }
        return aspectDistanceFrom16By9(candidate) < aspectDistanceFrom16By9(currentBest);
    }

    private static boolean isBetterMinArea(int[] candidate, int[] currentBest) {
        long candidateArea = (long) candidate[0] * candidate[1];
        long currentArea = (long) currentBest[0] * currentBest[1];
        if (candidateArea != currentArea) {
            return candidateArea < currentArea;
        }
        return aspectDistanceFrom16By9(candidate) < aspectDistanceFrom16By9(currentBest);
    }

    private static double aspectDistanceFrom16By9(int[] size) {
        double ratio = (double) size[0] / size[1];
        return Math.abs(ratio - TARGET_ASPECT_16_9);
    }
}
