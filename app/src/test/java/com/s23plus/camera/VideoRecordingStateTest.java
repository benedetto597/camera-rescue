package com.s23plus.camera;

/**
 * Pruebas unitarias TDD para la maquina de estados de grabacion MP4 VideoRecordingState.
 */
public final class VideoRecordingStateTest {

    public static void main(String[] args) {
        testStartDecisionMatrix();
        testAudioModeAfterPermissionResult();
        testFormatElapsedDuration();
        testBuildVideoFilePrefix();
        testStateMachineHappyPathAndMinimumDuration();
        testInvalidTransitionsAndReset();
        System.out.println("PASS: Todas las pruebas unitarias de VideoRecordingStateTest finalizaron con exito (6/6).");
    }

    private static void testStartDecisionMatrix() {
        assertEquals(VideoRecordingState.StartDecision.START_VIDEO_ONLY,
                VideoRecordingState.decideStart(true, false, true),
                "Si el usuario silencio el audio, siempre START_VIDEO_ONLY");
        assertEquals(VideoRecordingState.StartDecision.START_VIDEO_ONLY,
                VideoRecordingState.decideStart(false, false, true),
                "Usuario silenciado sin permiso -> START_VIDEO_ONLY");
        assertEquals(VideoRecordingState.StartDecision.START_WITH_AUDIO,
                VideoRecordingState.decideStart(true, false, false),
                "Con permiso y sin mute -> START_WITH_AUDIO");
        assertEquals(VideoRecordingState.StartDecision.START_VIDEO_ONLY,
                VideoRecordingState.decideStart(false, true, false),
                "Permiso denegado antes -> START_VIDEO_ONLY");
        assertEquals(VideoRecordingState.StartDecision.REQUEST_AUDIO_PERMISSION,
                VideoRecordingState.decideStart(false, false, false),
                "Sin permiso y nunca denegado -> REQUEST_AUDIO_PERMISSION");
    }

    private static void testAudioModeAfterPermissionResult() {
        assertEquals(VideoRecordingState.AudioMode.VIDEO_ONLY_USER_MUTED,
                VideoRecordingState.resolveAudioModeAfterPermissionResult(true, true),
                "User muted tiene prioridad sobre granted");
        assertEquals(VideoRecordingState.AudioMode.WITH_AUDIO,
                VideoRecordingState.resolveAudioModeAfterPermissionResult(true, false),
                "Permiso otorgado -> WITH_AUDIO");
        assertEquals(VideoRecordingState.AudioMode.VIDEO_ONLY_NO_MIC_PERMISSION,
                VideoRecordingState.resolveAudioModeAfterPermissionResult(false, false),
                "Permiso denegado -> VIDEO_ONLY_NO_MIC_PERMISSION");
    }

    private static void testFormatElapsedDuration() {
        assertEquals("00:00", VideoRecordingState.formatElapsedDuration(-500L), "Negativo debe clampear a 00:00");
        assertEquals("00:00", VideoRecordingState.formatElapsedDuration(0L), "0ms -> 00:00");
        assertEquals("00:09", VideoRecordingState.formatElapsedDuration(9_500L), "9.5s -> 00:09");
        assertEquals("01:05", VideoRecordingState.formatElapsedDuration(65_000L), "65s -> 01:05");
        assertEquals("1:00:00", VideoRecordingState.formatElapsedDuration(3_600_000L), "3600s -> 1:00:00");
        assertEquals("2:05:09", VideoRecordingState.formatElapsedDuration(7_509_000L), "7509s -> 2:05:09");
    }

    private static void testBuildVideoFilePrefix() {
        assertEquals("CR_FRONT_ID1", VideoRecordingState.buildVideoFilePrefix("1", true), "Front ID 1");
        assertEquals("CR_REAR_ID0", VideoRecordingState.buildVideoFilePrefix("0", false), "Rear ID 0");
        assertEquals("CR_REAR_ID56", VideoRecordingState.buildVideoFilePrefix(" 56 ", false), "Trim ID");
        assertEquals("CR_FRONT_ID0", VideoRecordingState.buildVideoFilePrefix(null, true), "Null ID -> 0");
        assertEquals("CR_REAR_ID0", VideoRecordingState.buildVideoFilePrefix("   ", false), "Blank ID -> 0");
    }

    private static void testStateMachineHappyPathAndMinimumDuration() {
        VideoRecordingState machine = new VideoRecordingState();
        assertEquals(VideoRecordingState.State.IDLE, machine.getState(), "Estado inicial IDLE");
        assertTrue(!machine.isRecordingOrBusy(), "IDLE no esta ocupado");
        assertEquals(0L, machine.getElapsedMs(5000L), "Elapsed en IDLE es 0");

        assertTrue(machine.beginPrepare(VideoRecordingState.AudioMode.WITH_AUDIO, 1000L),
                "IDLE -> PREPARING debe tener exito");
        assertEquals(VideoRecordingState.State.PREPARING, machine.getState(), "Debe estar en PREPARING");
        assertTrue(machine.isRecordingOrBusy(), "PREPARING esta ocupado");
        assertEquals(VideoRecordingState.AudioMode.WITH_AUDIO, machine.getActiveAudioMode(), "AudioMode WITH_AUDIO");
        assertEquals(0L, machine.getElapsedMs(1200L), "Elapsed en PREPARING antes de start es 0");

        assertTrue(machine.markRecordingStarted(2000L), "PREPARING -> RECORDING debe tener exito");
        assertEquals(VideoRecordingState.State.RECORDING, machine.getState(), "Debe estar en RECORDING");
        assertEquals(1500L, machine.getElapsedMs(3500L), "Elapsed en RECORDING debe calcular diferencia");
        assertEquals(0L, machine.getElapsedMs(1500L), "Elapsed no debe ser negativo");

        assertTrue(machine.beginStop(), "RECORDING -> STOPPING debe tener exito");
        assertEquals(VideoRecordingState.State.STOPPING, machine.getState(), "Debe estar en STOPPING");
        assertTrue(machine.isRecordingOrBusy(), "STOPPING sigue ocupado");

        VideoRecordingState.StopOutcome validStop = machine.finishStop(2600L);
        assertEquals(VideoRecordingState.State.IDLE, machine.getState(), "finishStop retorna a IDLE");
        assertTrue(!machine.isRecordingOrBusy(), "IDLE tras finishStop");
        assertEquals(600L, validStop.durationMs, "Duracion exacta de 600ms");
        assertTrue(validStop.meetsMinimumDuration, "600ms cumple MIN_VALID_VIDEO_DURATION_MS");
        assertEquals(VideoRecordingState.AudioMode.WITH_AUDIO, validStop.audioMode, "Outcome conserva audioMode");

        // Grabacion demasiado corta (< 600ms)
        assertTrue(machine.beginPrepare(VideoRecordingState.AudioMode.VIDEO_ONLY_NO_MIC_PERMISSION, 5000L),
                "Segundo ciclo beginPrepare");
        assertTrue(machine.markRecordingStarted(5100L), "Segundo ciclo start");
        assertTrue(machine.beginStop(), "Segundo ciclo beginStop");
        VideoRecordingState.StopOutcome tooShort = machine.finishStop(5699L);
        assertEquals(599L, tooShort.durationMs, "Duracion 599ms");
        assertTrue(!tooShort.meetsMinimumDuration, "599ms NO cumple minimo de 600ms");
        assertEquals(VideoRecordingState.AudioMode.VIDEO_ONLY_NO_MIC_PERMISSION, tooShort.audioMode,
                "Outcome conserva VIDEO_ONLY_NO_MIC_PERMISSION");
    }

    private static void testInvalidTransitionsAndReset() {
        VideoRecordingState machine = new VideoRecordingState();

        assertTrue(!machine.markRecordingStarted(1000L), "No puede iniciar desde IDLE");
        assertTrue(!machine.beginStop(), "No puede detener desde IDLE");

        assertTrue(machine.beginPrepare(VideoRecordingState.AudioMode.VIDEO_ONLY_USER_MUTED, 1000L),
                "IDLE -> PREPARING");
        assertTrue(!machine.beginPrepare(VideoRecordingState.AudioMode.WITH_AUDIO, 1100L),
                "No puede preparar dos veces");
        assertTrue(!machine.beginStop(), "No puede detener desde PREPARING sin haber iniciado");

        // finishStop desde PREPARING retorna 0ms y reinicia a IDLE
        VideoRecordingState.StopOutcome aborted = machine.finishStop(1500L);
        assertEquals(0L, aborted.durationMs, "Aborted before start debe tener durationMs 0");
        assertTrue(!aborted.meetsMinimumDuration, "0ms no cumple minimo");
        assertEquals(VideoRecordingState.State.IDLE, machine.getState(), "Debe volver a IDLE");

        // resetToIdle desde RECORDING
        assertTrue(machine.beginPrepare(VideoRecordingState.AudioMode.WITH_AUDIO, 2000L), "Prepare");
        assertTrue(machine.markRecordingStarted(2100L), "Start");
        assertTrue(!machine.markRecordingStarted(2200L), "No puede hacer start estando ya en RECORDING");
        machine.resetToIdle();
        assertEquals(VideoRecordingState.State.IDLE, machine.getState(), "resetToIdle debe volver a IDLE");
        assertEquals(0L, machine.getElapsedMs(3000L), "Elapsed tras reset debe ser 0");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " | Esperado: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
