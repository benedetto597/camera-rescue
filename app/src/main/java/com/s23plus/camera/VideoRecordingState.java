package com.s23plus.camera;

import java.util.Locale;

/**
 * Máquina de estados pura Java y sincronizada para la grabación MP4 transaccional
 * del visor Camera2 universal.
 */
public final class VideoRecordingState {

    public enum State {
        IDLE,
        PREPARING,
        RECORDING,
        STOPPING
    }

    public enum AudioMode {
        WITH_AUDIO,
        VIDEO_ONLY_NO_MIC_PERMISSION,
        VIDEO_ONLY_USER_MUTED
    }

    public enum StartDecision {
        REQUEST_AUDIO_PERMISSION,
        START_WITH_AUDIO,
        START_VIDEO_ONLY
    }

    public static final class StopOutcome {
        public final boolean meetsMinimumDuration;
        public final long durationMs;
        public final AudioMode audioMode;

        public StopOutcome(boolean meetsMinimumDuration, long durationMs, AudioMode audioMode) {
            this.meetsMinimumDuration = meetsMinimumDuration;
            this.durationMs = durationMs;
            this.audioMode = audioMode;
        }
    }

    private State state = State.IDLE;
    private AudioMode activeAudioMode = AudioMode.WITH_AUDIO;
    private long startedAtMs = 0L;

    public static StartDecision decideStart(
            boolean hasAudioPermission,
            boolean audioPermissionDeniedBefore,
            boolean userMutedAudio
    ) {
        if (userMutedAudio) {
            return StartDecision.START_VIDEO_ONLY;
        }
        if (hasAudioPermission) {
            return StartDecision.START_WITH_AUDIO;
        }
        if (audioPermissionDeniedBefore) {
            return StartDecision.START_VIDEO_ONLY;
        }
        return StartDecision.REQUEST_AUDIO_PERMISSION;
    }

    public static AudioMode resolveAudioModeAfterPermissionResult(boolean granted, boolean userMutedAudio) {
        if (userMutedAudio) {
            return AudioMode.VIDEO_ONLY_USER_MUTED;
        }
        return granted ? AudioMode.WITH_AUDIO : AudioMode.VIDEO_ONLY_NO_MIC_PERMISSION;
    }

    public static String formatElapsedDuration(long elapsedMs) {
        long clampedMs = Math.max(0L, elapsedMs);
        long totalSeconds = clampedMs / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    public static String buildVideoFilePrefix(String cameraId, boolean isFrontFacing) {
        String safeId = (cameraId == null || cameraId.trim().isEmpty()) ? "0" : cameraId.trim();
        return (isFrontFacing ? "CR_FRONT_ID" : "CR_REAR_ID") + safeId;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized AudioMode getActiveAudioMode() {
        return activeAudioMode;
    }

    public synchronized boolean isRecordingOrBusy() {
        return state != State.IDLE;
    }

    public synchronized long getElapsedMs(long nowMs) {
        if ((state != State.RECORDING && state != State.STOPPING) || startedAtMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, nowMs - startedAtMs);
    }

    public synchronized boolean beginPrepare(AudioMode mode, long nowMs) {
        if (state != State.IDLE) {
            return false;
        }
        state = State.PREPARING;
        activeAudioMode = (mode != null) ? mode : AudioMode.WITH_AUDIO;
        startedAtMs = 0L;
        return true;
    }

    public synchronized boolean markRecordingStarted(long nowMs) {
        if (state != State.PREPARING) {
            return false;
        }
        state = State.RECORDING;
        startedAtMs = nowMs;
        return true;
    }

    public synchronized boolean beginStop() {
        if (state != State.RECORDING) {
            return false;
        }
        state = State.STOPPING;
        return true;
    }

    public synchronized StopOutcome finishStop(long nowMs) {
        long durationMs = startedAtMs > 0L ? Math.max(0L, nowMs - startedAtMs) : 0L;
        boolean meetsMinimumDuration = durationMs >= CameraConfig.MIN_VALID_VIDEO_DURATION_MS;
        AudioMode outcomeMode = activeAudioMode;
        state = State.IDLE;
        startedAtMs = 0L;
        return new StopOutcome(meetsMinimumDuration, durationMs, outcomeMode);
    }

    public synchronized void resetToIdle() {
        state = State.IDLE;
        startedAtMs = 0L;
    }
}
