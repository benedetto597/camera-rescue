package com.s23plus.camera;

import android.Manifest;
import android.content.Intent;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Actividad principal de Samsung Camera Rescue (v2.0.0).
 *
 * Incluye:
 * 1. Motor de diagnóstico heurístico de modelos Samsung (SamsungModelDatabase) y detección de One UI.
 * 2. Escáner de sensores on-device (SensorScanner) con selector interactivo de lentes funcionales.
 * 3. Visor Camera2 universal integrado (frontal + trasera pública) con zoom, EV, flash/linterna,
 *    apertura variable LENS_APERTURE, cuadrícula, temporizador y grabación MP4 transaccional.
 * 4. Tarjetas dinámicas de rescate vía ShootingModeMap, recuperación rápida de bloqueos,
 *    asistente colapsable One UI, accesos directos, Quick Settings Tile y perfiles .json vía SAF.
 */
public final class MainActivity extends Activity implements UniversalCameraController.Listener {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_AUDIO_PERMISSION = 1002;
    private static final int REQUEST_EXPORT_PROFILE = 2001;
    private static final int REQUEST_IMPORT_PROFILE = 2002;
    private static final long COUNTDOWN_TICK_MS = 1000L;

    private ScrollView rootScroll;
    private LinearLayout viewerCard;
    private TextureView viewerPreviewView;
    private ViewfinderGridOverlay gridOverlayView;
    private TextView statusBanner;
    private TextView lensOverlayBadge;
    private TextView recordingTimerBadge;
    private TextView countdownOverlayText;

    private Button zoomOutButton;
    private Button zoomPresetButton;
    private Button zoomInButton;
    private Button evMinusButton;
    private TextView evValueLabel;
    private Button evPlusButton;
    private Button flashButton;
    private Button timerButton;
    private Button gridButton;
    private Button apertureButton;
    private Button capturePhotoButton;
    private Button recordVideoButton;
    private Button switchLensButton;
    private Button testPublicRearButton;

    private UniversalCameraController universalCameraController;

    // Preferencias y estado del visor Camera2 universal (v2.0.0)
    private String viewerPreferredCameraId = CameraConfig.FRONT_CAMERA_WIDE_ID;
    private CameraViewerMath.GridMode viewerGridMode = CameraViewerMath.GridMode.OFF;
    private int viewerTimerSeconds = 0;
    private CameraViewerMath.FlashMode viewerFlashMode = CameraViewerMath.FlashMode.OFF;
    private float viewerPreferredAperture = CameraViewerMath.ApertureCapabilities.APERTURE_AUTO;
    private boolean pendingApplySavedFlash = true;
    private boolean pendingApplySavedAperture = true;
    private boolean currentCameraOpenedSuccessfully = false;

    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private int countdownRemainingSeconds = 0;
    private boolean audioPermissionDeniedThisSession = false;
    private boolean pendingVideoRecordAfterAudioPrompt = false;
    private VideoRecordingState.AudioMode pendingVideoRecordAudioMode = null;

    // Componentes del motor de diagnóstico y selector dinámico (v1.2.0 - v1.3.0)
    private SamsungModelDatabase.ModelProfile modelProfile;
    private SensorScanner sensorScanner;
    private TextView scanStatusText;
    private LinearLayout scanResultsContainer;
    private Button runScanButton;

    private List<DiscoveredSensor> lastScannedSensors = new ArrayList<>();
    private Set<String> selectedSensorIds = new LinkedHashSet<>();
    private List<RescueActionSpec> currentRearSpecs = new ArrayList<>();
    private List<DiscoveredSensor> activeViewerSensors = new ArrayList<>();
    private List<DiscoveredSensor> activePublicRearSensors = new ArrayList<>();
    private LinearLayout rearCardsContainer;
    private TextView shortcutsStatusView;
    private Button quickTileTargetButton;
    private int lastPublishedShortcutsCount = 0;

    // Motor heurístico de modos, asistente One UI y recuperación rápida (v1.4.0)
    private ShootingModeMap.OneUiGeneration oneUiGeneration;
    private List<OneUiAssistantGuide.AssistantStep> assistantSteps = new ArrayList<>();
    private Set<String> completedAssistantStepIds = new LinkedHashSet<>();
    private final Map<String, CheckBox> assistantStepCheckBoxes = new LinkedHashMap<>();
    private boolean suppressAssistantCheckCallbacks = false;
    private final Handler recoveryHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingRecoveryRunnable;
    private long lastRearLaunchTimestampMs = 0L;
    private ShootingModeMap.ModeKind lastLaunchedRearMode = ShootingModeMap.ModeKind.PRO_PHOTO;
    private int recoveryAttemptIndex = 0;
    private boolean recoveryAttemptAdvancedOnLaunch = false;

    private LinearLayout recoveryCard;
    private TextView recoveryStatusText;
    private Button recoveryActionButton;
    private TextView assistantProgressBadge;
    private LinearLayout assistantStepsContainer;
    private Button assistantToggleButton;

    // Tarjeta de exportación / importación offline .json vía SAF (v2.0.0)
    private TextView profileStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        modelProfile = SamsungModelDatabase.lookup(Build.MODEL);
        oneUiGeneration = OneUiSystemBridge.detectDeviceOneUiGeneration();
        assistantSteps = OneUiAssistantGuide.buildSteps(modelProfile, oneUiGeneration);
        completedAssistantStepIds = OneUiAssistantGuide.sanitizeCompletedStepIds(
                RescuePreferences.loadAssistantCompletedSteps(this),
                assistantSteps
        );
        sensorScanner = new SensorScanner(this);

        DiagnosticProfileCodec.ViewerPreferencesSnapshot loadedViewerPrefs =
                RescuePreferences.loadViewerPreferences(this);
        applyViewerPreferencesSnapshot(loadedViewerPrefs, false);

        universalCameraController = new UniversalCameraController(this, this);
        universalCameraController.setCurrentCameraId(viewerPreferredCameraId);

        Set<String> saved = RescuePreferences.loadSelectedSensorIds(this);
        if (saved != null) {
            selectedSensorIds.addAll(saved);
        }

        buildModernUserInterface();
        rebuildDynamicUiAndShortcuts();
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (CameraConfig.ACTION_LAUNCH_REAR_PRO.equals(action)) {
            setIntent(new Intent(Intent.ACTION_MAIN));
            launchRearModeViaMap(ShootingModeMap.ModeKind.PRO_PHOTO);
        } else if (CameraConfig.ACTION_LAUNCH_REAR_QR.equals(action)) {
            setIntent(new Intent(Intent.ACTION_MAIN));
            launchRearQrWithFloatingHud();
        } else if (CameraConfig.ACTION_LAUNCH_REAR_VIDEO.equals(action)) {
            setIntent(new Intent(Intent.ACTION_MAIN));
            launchRearModeViaMap(ShootingModeMap.ModeKind.PRO_VIDEO);
        } else if (CameraConfig.ACTION_QUICK_RECOVERY.equals(action)) {
            setIntent(new Intent(Intent.ACTION_MAIN));
            executeQuickRecovery();
        } else if (CameraConfig.ACTION_LAUNCH_FRONT.equals(action)
                || CameraConfig.ACTION_OPEN_INTEGRATED_VIEWER.equals(action)) {
            String sensorId = intent.getStringExtra(CameraConfig.EXTRA_SHORTCUT_SENSOR_ID);
            boolean isIntegratedViewer = CameraConfig.ACTION_OPEN_INTEGRATED_VIEWER.equals(action);
            setIntent(new Intent(Intent.ACTION_MAIN));
            if (sensorId != null && !sensorId.trim().isEmpty()) {
                String trimmedId = sensorId.trim();
                switchViewerToCameraId(trimmedId, true);
                onStatusChanged((isIntegratedViewer ? "Visor integrado activado (ID " : "Atajo frontal activado (ID ")
                        + trimmedId + ")");
            } else {
                ensureCameraPermissionAndStartViewerPreview();
                onStatusChanged(isIntegratedViewer ? "Visor Camera2 universal activado" : "Atajo frontal activado");
            }
            focusViewerCard();
        }
    }

    private void focusViewerCard() {
        if (rootScroll != null && viewerCard != null) {
            rootScroll.post(() -> rootScroll.smoothScrollTo(0, viewerCard.getTop()));
        }
    }

    private void buildModernUserInterface() {
        rootScroll = new ScrollView(this);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(Color.parseColor("#070B12"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padH = dpToPx(16);
        root.setPadding(padH, dpToPx(14), padH, dpToPx(20));

        // 1. Encabezado Moderno con Insignia de Versión
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleColumn.setLayoutParams(titleColLp);

        TextView titleView = new TextView(this);
        titleView.setText(CameraConfig.APP_TITLE);
        titleView.setTextColor(Color.parseColor("#F8FAFC"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleColumn.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText("Rescate de Sensores · " + modelProfile.marketingName + " · " + oneUiGeneration.displayName);
        subtitleView.setTextColor(Color.parseColor("#64748B"));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleColumn.addView(subtitleView);
        headerRow.addView(titleColumn);

        TextView versionBadge = new TextView(this);
        versionBadge.setText("v" + CameraConfig.APP_VERSION);
        versionBadge.setTextColor(Color.parseColor("#38BDF8"));
        versionBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        versionBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        versionBadge.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        versionBadge.setBackground(createRoundedDrawable("#0F172A", "#1E3A5F", 12, 1));
        headerRow.addView(versionBadge);
        root.addView(headerRow);

        // Banner de estado en vivo
        statusBanner = new TextView(this);
        statusBanner.setText("● Inicializando visor...");
        statusBanner.setTextColor(Color.parseColor("#34D399"));
        statusBanner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusBanner.setPadding(dpToPx(12), dpToPx(7), dpToPx(12), dpToPx(7));
        statusBanner.setBackground(createRoundedDrawable("#0D1522", "#1E293B", 12, 1));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dpToPx(10);
        statusLp.bottomMargin = dpToPx(10);
        statusBanner.setLayoutParams(statusLp);
        root.addView(statusBanner);

        // 2. Tarjeta del Motor de Diagnóstico y Escáner (v1.2.0)
        root.addView(buildDiagnosticCard());

        // 3. Tarjeta del Visor Camera2 Universal (v2.0.0)
        viewerCard = buildUniversalViewerCard();
        root.addView(viewerCard);

        // 4. Encabezado de Sección Trasera
        TextView rearSectionHeader = new TextView(this);
        rearSectionHeader.setText("MODOS DE RESCATE — CÁMARA TRASERA");
        rearSectionHeader.setTextColor(Color.parseColor("#94A3B8"));
        rearSectionHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        rearSectionHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sectionLp.topMargin = dpToPx(16);
        sectionLp.bottomMargin = dpToPx(8);
        rearSectionHeader.setLayoutParams(sectionLp);
        root.addView(rearSectionHeader);

        // Contenedor dinámico de tarjetas traseras (v1.3.0)
        rearCardsContainer = new LinearLayout(this);
        rearCardsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(rearCardsContainer);

        // 5. Tarjeta de Recuperación Rápida de Bloqueos (v1.4.0)
        root.addView(buildRecoveryCard());

        // 6. Asistente Colapsable One UI (v1.4.0)
        root.addView(buildOneUiAssistantCard());

        // 7. Tarjeta de Atajos Dinámicos y Quick Settings Tile
        root.addView(buildShortcutsAndTileCard());

        // 8. Tarjeta de Perfil Offline (.json · SAF) (v2.0.0)
        root.addView(buildOfflineProfileCard());

        rootScroll.addView(root);
        setContentView(rootScroll);
    }

    /**
     * Construye la tarjeta unificada del Visor Camera2 Universal (sensores frontales y traseros públicos).
     */
    private LinearLayout buildUniversalViewerCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int cardPad = dpToPx(10);
        card.setPadding(cardPad, cardPad, cardPad, cardPad);
        card.setBackground(createRoundedDrawable("#111827", "#1F2937", 20, 1));

        TextView viewerHeader = new TextView(this);
        viewerHeader.setText("VISOR CAMERA2 UNIVERSAL · FRONTAL Y TRASERA PÚBLICA");
        viewerHeader.setTextColor(Color.parseColor("#38BDF8"));
        viewerHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        viewerHeader.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dpToPx(8);
        viewerHeader.setLayoutParams(headerLp);
        card.addView(viewerHeader);

        // Marco de vista previa + cuadrícula + insignias + temporizador
        FrameLayout previewFrame = new FrameLayout(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(290));
        previewFrame.setLayoutParams(previewParams);
        previewFrame.setBackground(createRoundedDrawable("#090D16", "#334155", 16, 1));

        viewerPreviewView = new TextureView(this);
        FrameLayout.LayoutParams textureLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int innerMargin = dpToPx(3);
        textureLp.setMargins(innerMargin, innerMargin, innerMargin, innerMargin);
        viewerPreviewView.setLayoutParams(textureLp);
        previewFrame.addView(viewerPreviewView);

        gridOverlayView = new ViewfinderGridOverlay(this);
        FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        gridLp.setMargins(innerMargin, innerMargin, innerMargin, innerMargin);
        gridOverlayView.setLayoutParams(gridLp);
        gridOverlayView.setGridMode(viewerGridMode);
        previewFrame.addView(gridOverlayView);

        lensOverlayBadge = new TextView(this);
        lensOverlayBadge.setText("Frontal ID 1 · 12 MP");
        lensOverlayBadge.setTextColor(Color.parseColor("#E2E8F0"));
        lensOverlayBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        lensOverlayBadge.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        lensOverlayBadge.setBackground(createRoundedDrawable("#BF0F172A", "#38BDF8", 10, 1));
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.gravity = Gravity.TOP | Gravity.START;
        badgeLp.setMargins(dpToPx(12), dpToPx(12), 0, 0);
        lensOverlayBadge.setLayoutParams(badgeLp);
        previewFrame.addView(lensOverlayBadge);

        recordingTimerBadge = new TextView(this);
        recordingTimerBadge.setText("● REC 00:00");
        recordingTimerBadge.setTextColor(Color.parseColor("#FFFFFF"));
        recordingTimerBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        recordingTimerBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        recordingTimerBadge.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        recordingTimerBadge.setBackground(createRoundedDrawable("#CCDC2626", "#FCA5A5", 10, 1));
        FrameLayout.LayoutParams recBadgeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recBadgeLp.gravity = Gravity.TOP | Gravity.END;
        recBadgeLp.setMargins(0, dpToPx(12), dpToPx(12), 0);
        recordingTimerBadge.setLayoutParams(recBadgeLp);
        recordingTimerBadge.setVisibility(View.GONE);
        previewFrame.addView(recordingTimerBadge);

        countdownOverlayText = new TextView(this);
        countdownOverlayText.setText("3");
        countdownOverlayText.setTextColor(Color.parseColor("#FFFFFF"));
        countdownOverlayText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        countdownOverlayText.setTypeface(Typeface.DEFAULT_BOLD);
        countdownOverlayText.setGravity(Gravity.CENTER);
        countdownOverlayText.setPadding(dpToPx(24), dpToPx(10), dpToPx(24), dpToPx(10));
        countdownOverlayText.setBackground(createRoundedDrawable("#A6090D16", "#38BDF8", 22, 1));
        FrameLayout.LayoutParams countLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countLp.gravity = Gravity.CENTER;
        countdownOverlayText.setLayoutParams(countLp);
        countdownOverlayText.setVisibility(View.GONE);
        previewFrame.addView(countdownOverlayText);

        card.addView(previewFrame);

        // Fila de Zoom (− / 1.0x / +)
        LinearLayout zoomRow = new LinearLayout(this);
        zoomRow.setOrientation(LinearLayout.HORIZONTAL);
        zoomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams zoomRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zoomRowLp.topMargin = dpToPx(10);
        zoomRow.setLayoutParams(zoomRowLp);

        zoomOutButton = createCompactButton("−", "#0F172A", "#334155", "#E2E8F0", 15, true);
        LinearLayout.LayoutParams zoomBtnLp = new LinearLayout.LayoutParams(0, dpToPx(38), 1.0f);
        zoomBtnLp.rightMargin = dpToPx(6);
        zoomOutButton.setLayoutParams(zoomBtnLp);
        zoomOutButton.setOnClickListener(v -> universalCameraController.stepZoom(-0.5f));
        zoomRow.addView(zoomOutButton);

        zoomPresetButton = createCompactButton("1.0x", "#1E293B", "#38BDF8", "#7DD3FC", 13, true);
        LinearLayout.LayoutParams zoomPresetLp = new LinearLayout.LayoutParams(0, dpToPx(38), 1.2f);
        zoomPresetLp.rightMargin = dpToPx(6);
        zoomPresetButton.setLayoutParams(zoomPresetLp);
        zoomPresetButton.setOnClickListener(v -> universalCameraController.cycleZoomPreset());
        zoomRow.addView(zoomPresetButton);

        zoomInButton = createCompactButton("+", "#0F172A", "#334155", "#E2E8F0", 15, true);
        zoomInButton.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(38), 1.0f));
        zoomInButton.setOnClickListener(v -> universalCameraController.stepZoom(0.5f));
        zoomRow.addView(zoomInButton);
        card.addView(zoomRow);

        // Controles de Captura: Fila A (EV − / +0.0 EV / EV + / Flash)
        LinearLayout evFlashRow = new LinearLayout(this);
        evFlashRow.setOrientation(LinearLayout.HORIZONTAL);
        evFlashRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams evFlashLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        evFlashLp.topMargin = dpToPx(8);
        evFlashRow.setLayoutParams(evFlashLp);

        evMinusButton = createCompactButton("EV −", "#0F172A", "#334155", "#CBD5E1", 12, true);
        LinearLayout.LayoutParams evBtnLp = new LinearLayout.LayoutParams(0, dpToPx(38), 0.9f);
        evBtnLp.rightMargin = dpToPx(6);
        evMinusButton.setLayoutParams(evBtnLp);
        evMinusButton.setOnClickListener(v -> universalCameraController.stepEv(-1));
        evFlashRow.addView(evMinusButton);

        evValueLabel = new TextView(this);
        evValueLabel.setText("+0.0 EV");
        evValueLabel.setTextColor(Color.parseColor("#E2E8F0"));
        evValueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        evValueLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        evValueLabel.setGravity(Gravity.CENTER);
        evValueLabel.setBackground(createRoundedDrawable("#090D16", "#1E293B", 11, 1));
        LinearLayout.LayoutParams evValLp = new LinearLayout.LayoutParams(0, dpToPx(38), 1.05f);
        evValLp.rightMargin = dpToPx(6);
        evValueLabel.setLayoutParams(evValLp);
        evFlashRow.addView(evValueLabel);

        evPlusButton = createCompactButton("EV +", "#0F172A", "#334155", "#CBD5E1", 12, true);
        LinearLayout.LayoutParams evPlusLp = new LinearLayout.LayoutParams(0, dpToPx(38), 0.9f);
        evPlusLp.rightMargin = dpToPx(6);
        evPlusButton.setLayoutParams(evPlusLp);
        evPlusButton.setOnClickListener(v -> universalCameraController.stepEv(1));
        evFlashRow.addView(evPlusButton);

        flashButton = createCompactButton("⚡ Off", "#1E293B", "#334155", "#FDE68A", 12, true);
        flashButton.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(38), 1.15f));
        flashButton.setOnClickListener(v -> onFlashButtonClicked());
        evFlashRow.addView(flashButton);
        card.addView(evFlashRow);

        // Controles de Captura: Fila B (Temporizador / Cuadrícula / Apertura variable)
        LinearLayout captureExtrasRow = new LinearLayout(this);
        captureExtrasRow.setOrientation(LinearLayout.HORIZONTAL);
        captureExtrasRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams extrasLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        extrasLp.topMargin = dpToPx(8);
        captureExtrasRow.setLayoutParams(extrasLp);

        timerButton = createCompactButton("⏱ Off", "#0F172A", "#334155", "#CBD5E1", 12, true);
        LinearLayout.LayoutParams timerLp = new LinearLayout.LayoutParams(0, dpToPx(38), 0.95f);
        timerLp.rightMargin = dpToPx(6);
        timerButton.setLayoutParams(timerLp);
        timerButton.setOnClickListener(v -> onTimerButtonClicked());
        captureExtrasRow.addView(timerButton);

        gridButton = createCompactButton("⊞ Sin guía", "#0F172A", "#334155", "#CBD5E1", 12, true);
        LinearLayout.LayoutParams gridBtnLp = new LinearLayout.LayoutParams(0, dpToPx(38), 1.05f);
        gridBtnLp.rightMargin = dpToPx(6);
        gridButton.setLayoutParams(gridBtnLp);
        gridButton.setOnClickListener(v -> onGridButtonClicked());
        captureExtrasRow.addView(gridButton);

        apertureButton = createCompactButton("◉ f/Auto", "#1E293B", "#38BDF8", "#7DD3FC", 11, true);
        apertureButton.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(38), 1.25f));
        apertureButton.setOnClickListener(v -> onApertureButtonClicked());
        captureExtrasRow.addView(apertureButton);
        card.addView(captureExtrasRow);

        // Fila principal de captura: Foto / Grabar MP4 / Desplegable técnico (▾)
        LinearLayout primaryCaptureRow = new LinearLayout(this);
        primaryCaptureRow.setOrientation(LinearLayout.HORIZONTAL);
        primaryCaptureRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        primaryLp.topMargin = dpToPx(10);
        primaryCaptureRow.setLayoutParams(primaryLp);

        capturePhotoButton = createShortButton(
                CameraConfig.BTN_VIEWER_PHOTO_SHORT,
                "#059669",
                "#FFFFFF",
                true
        );
        LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(0, dpToPx(46), 1.0f);
        photoLp.rightMargin = dpToPx(6);
        capturePhotoButton.setLayoutParams(photoLp);
        capturePhotoButton.setOnClickListener(v -> onCapturePhotoClicked());
        primaryCaptureRow.addView(capturePhotoButton);

        recordVideoButton = createShortButton(
                CameraConfig.BTN_VIEWER_VIDEO_SHORT,
                "#1E293B",
                "#FCA5A5",
                true
        );
        LinearLayout.LayoutParams videoLp = new LinearLayout.LayoutParams(0, dpToPx(46), 1.15f);
        videoLp.rightMargin = dpToPx(6);
        recordVideoButton.setLayoutParams(videoLp);
        recordVideoButton.setOnClickListener(v -> onRecordVideoClicked());
        primaryCaptureRow.addView(recordVideoButton);

        TextView viewerTechView = createTechDescriptionView(CameraConfig.TECH_DESC_UNIVERSAL_VIEWER);
        Button viewerArrowBtn = createArrowToggleButton(viewerTechView);
        primaryCaptureRow.addView(viewerArrowBtn);
        card.addView(primaryCaptureRow);

        // Fila secundaria de lentes: Cambiar Lente + Probar Trasera Pública en Visor
        LinearLayout lensRow = new LinearLayout(this);
        lensRow.setOrientation(LinearLayout.HORIZONTAL);
        lensRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lensRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lensRowLp.topMargin = dpToPx(8);
        lensRow.setLayoutParams(lensRowLp);

        switchLensButton = createCompactButton("Lente 8.6 MP", "#1E293B", "#334155", "#7DD3FC", 12, true);
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(0, dpToPx(42), 0.9f);
        switchLp.rightMargin = dpToPx(6);
        switchLensButton.setLayoutParams(switchLp);
        switchLensButton.setOnClickListener(v -> onSwitchViewerLensClicked());
        lensRow.addView(switchLensButton);

        testPublicRearButton = createCompactButton(
                "📷 Probar Trasera Pública en Visor",
                "#0F172A",
                "#1E3A5F",
                "#38BDF8",
                12,
                true
        );
        testPublicRearButton.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(42), 1.25f));
        testPublicRearButton.setOnClickListener(v -> onTestPublicRearClicked());
        lensRow.addView(testPublicRearButton);
        card.addView(lensRow);

        card.addView(viewerTechView);

        // Inicializar etiquetas acorde a las preferencias guardadas
        updateTimerButtonLabel();
        updateGridButtonLabel();
        updateFlashButtonUi(false, viewerFlashMode);
        updateApertureButtonUi(null);
        updateViewerLensLabels(viewerPreferredCameraId);
        return card;
    }

    /**
     * Construye la tarjeta de recuperación rápida ante bloqueos HAL o cierres inmediatos de Samsung Camera.
     */
    private LinearLayout buildRecoveryCard() {
        recoveryCard = new LinearLayout(this);
        recoveryCard.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(12);
        recoveryCard.setPadding(pad, pad, pad, pad);
        recoveryCard.setBackground(createRoundedDrawable("#111827", "#334155", 18, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dpToPx(4);
        cardLp.bottomMargin = dpToPx(10);
        recoveryCard.setLayoutParams(cardLp);

        TextView header = new TextView(this);
        header.setText("RECUPERACIÓN RÁPIDA DE BLOQUEOS");
        header.setTextColor(Color.parseColor("#38BDF8"));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        recoveryCard.addView(header);

        recoveryStatusText = new TextView(this);
        recoveryStatusText.setTextColor(Color.parseColor("#94A3B8"));
        recoveryStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dpToPx(4);
        statusLp.bottomMargin = dpToPx(10);
        recoveryStatusText.setLayoutParams(statusLp);
        recoveryCard.addView(recoveryStatusText);

        recoveryActionButton = createShortButton(
                "⚡ Destrabar Cámara → Modo Pro",
                "#0284C7",
                "#FFFFFF",
                true
        );
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(46));
        recoveryActionButton.setLayoutParams(primaryLp);
        recoveryActionButton.setOnClickListener(v -> executeQuickRecovery());
        recoveryCard.addView(recoveryActionButton);

        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setOrientation(LinearLayout.HORIZONTAL);
        secondaryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams secRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secRowLp.topMargin = dpToPx(8);
        secondaryRow.setLayoutParams(secRowLp);

        Button resetPreviewBtn = createShortButton("↻ Reiniciar Visor", "#1E293B", "#7DD3FC", false);
        LinearLayout.LayoutParams resetLp = new LinearLayout.LayoutParams(0, dpToPx(42), 1.0f);
        resetLp.rightMargin = dpToPx(8);
        resetPreviewBtn.setLayoutParams(resetLp);
        resetPreviewBtn.setOnClickListener(v -> {
            cancelCountdown(false);
            pendingVideoRecordAudioMode = null;
            currentCameraOpenedSuccessfully = false;
            universalCameraController.stop();
            updateRecordingUi(false, 0L, null);
            ensureCameraPermissionAndStartViewerPreview();
            recoveryAttemptIndex = 0;
            recoveryAttemptAdvancedOnLaunch = false;
            updateRecoveryCardState(false, "Visor Camera2 local reiniciado.");
        });
        secondaryRow.addView(resetPreviewBtn);

        Button appInfoBtn = createShortButton("⚙ App Cámara", "#1E293B", "#CBD5E1", false);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, dpToPx(42), 1.0f);
        appInfoBtn.setLayoutParams(infoLp);
        appInfoBtn.setOnClickListener(v -> {
            if (!OneUiSystemBridge.openSamsungCameraAppInfo(this)) {
                onError("No se pudo abrir Información de Samsung Camera.");
            }
        });
        secondaryRow.addView(appInfoBtn);

        recoveryCard.addView(secondaryRow);
        updateRecoveryCardState(false, null);
        return recoveryCard;
    }

    /**
     * Construye el asistente colapsable paso a paso para fijar Modo Pro y Tecla Lateral en One UI.
     */
    private LinearLayout buildOneUiAssistantCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(12);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(createRoundedDrawable("#111827", "#1F2937", 18, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardLp);

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleCol.setLayoutParams(titleColLp);

        TextView header = new TextView(this);
        header.setText("ASISTENTE ONE UI · AJUSTES Y TECLA LATERAL");
        header.setTextColor(Color.parseColor("#38BDF8"));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleCol.addView(header);

        TextView sub = new TextView(this);
        sub.setText(oneUiGeneration.displayName + " · Fija Modo Pro y pulsación doble");
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        titleCol.addView(sub);
        headerRow.addView(titleCol);

        assistantProgressBadge = new TextView(this);
        assistantProgressBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        assistantProgressBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        assistantProgressBadge.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.rightMargin = dpToPx(8);
        assistantProgressBadge.setLayoutParams(badgeLp);
        headerRow.addView(assistantProgressBadge);

        assistantToggleButton = new Button(this);
        assistantToggleButton.setAllCaps(false);
        assistantToggleButton.setText("▾");
        assistantToggleButton.setTextColor(Color.parseColor("#38BDF8"));
        assistantToggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        assistantToggleButton.setTypeface(Typeface.DEFAULT_BOLD);
        assistantToggleButton.setBackground(createRoundedDrawable("#0F172A", "#334155", 13, 1));
        assistantToggleButton.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(44), dpToPx(44)));
        headerRow.addView(assistantToggleButton);

        card.addView(headerRow);

        assistantStepsContainer = new LinearLayout(this);
        assistantStepsContainer.setOrientation(LinearLayout.VERTICAL);
        assistantStepsContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams stepsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepsLp.topMargin = dpToPx(10);
        assistantStepsContainer.setLayoutParams(stepsLp);

        assistantStepCheckBoxes.clear();
        for (OneUiAssistantGuide.AssistantStep step : assistantSteps) {
            assistantStepsContainer.addView(buildAssistantStepView(step));
        }
        card.addView(assistantStepsContainer);

        assistantToggleButton.setOnClickListener(v -> {
            boolean willShow = assistantStepsContainer.getVisibility() != View.VISIBLE;
            assistantStepsContainer.setVisibility(willShow ? View.VISIBLE : View.GONE);
            assistantToggleButton.setText(willShow ? "▴" : "▾");
            assistantToggleButton.setBackground(createRoundedDrawable(
                    willShow ? "#1E293B" : "#0F172A",
                    willShow ? "#38BDF8" : "#334155",
                    13,
                    1
            ));
        });

        refreshAssistantProgressBadge();
        return card;
    }

    private LinearLayout buildAssistantStepView(final OneUiAssistantGuide.AssistantStep step) {
        LinearLayout stepBox = new LinearLayout(this);
        stepBox.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(10);
        stepBox.setPadding(pad, pad, pad, pad);
        stepBox.setBackground(createRoundedDrawable("#090D16", "#1E293B", 12, 1));
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.bottomMargin = dpToPx(8);
        stepBox.setLayoutParams(boxLp);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        CheckBox checkBox = new CheckBox(this);
        checkBox.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#38BDF8")));
        checkBox.setChecked(completedAssistantStepIds.contains(step.id));
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cbLp.rightMargin = dpToPx(6);
        checkBox.setLayoutParams(cbLp);
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressAssistantCheckCallbacks) {
                return;
            }
            if (isChecked) {
                completedAssistantStepIds.add(step.id);
            } else {
                completedAssistantStepIds.remove(step.id);
            }
            RescuePreferences.saveAssistantCompletedSteps(MainActivity.this, completedAssistantStepIds);
            refreshAssistantProgressBadge();
        });
        assistantStepCheckBoxes.put(step.id, checkBox);
        topRow.addView(checkBox);

        TextView titleView = new TextView(this);
        titleView.setText(step.stepNumber + ". " + step.title);
        titleView.setTextColor(Color.parseColor("#F1F5F9"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        topRow.addView(titleView);
        stepBox.addView(topRow);

        TextView pathEsView = new TextView(this);
        pathEsView.setText("ES: " + step.menuPathEs);
        pathEsView.setTextColor(Color.parseColor("#7DD3FC"));
        pathEsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        pathEsView.setPadding(0, dpToPx(4), 0, 0);
        stepBox.addView(pathEsView);

        TextView pathEnView = new TextView(this);
        pathEnView.setText("EN: " + step.menuPathEn);
        pathEnView.setTextColor(Color.parseColor("#64748B"));
        pathEnView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        pathEnView.setPadding(0, dpToPx(2), 0, 0);
        stepBox.addView(pathEnView);

        TextView instructionView = new TextView(this);
        instructionView.setText(step.instruction);
        instructionView.setTextColor(Color.parseColor("#CBD5E1"));
        instructionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        instructionView.setPadding(0, dpToPx(4), 0, 0);
        stepBox.addView(instructionView);

        Button actionBtn = createShortButton(step.buttonLabel, "#1E293B", "#38BDF8", true);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(40));
        btnLp.topMargin = dpToPx(8);
        actionBtn.setLayoutParams(btnLp);
        actionBtn.setOnClickListener(v -> executeAssistantStepAction(step));
        stepBox.addView(actionBtn);

        return stepBox;
    }

    private void refreshAssistantStepsUi() {
        suppressAssistantCheckCallbacks = true;
        try {
            for (Map.Entry<String, CheckBox> entry : assistantStepCheckBoxes.entrySet()) {
                entry.getValue().setChecked(completedAssistantStepIds.contains(entry.getKey()));
            }
        } finally {
            suppressAssistantCheckCallbacks = false;
        }
        refreshAssistantProgressBadge();
    }

    private void executeAssistantStepAction(OneUiAssistantGuide.AssistantStep step) {
        if (step == null) {
            return;
        }
        switch (step.action) {
            case LAUNCH_PRO_MODE:
                launchRearModeViaMap(ShootingModeMap.ModeKind.PRO_PHOTO);
                break;
            case OPEN_CAMERA_SETTINGS:
                ShootingModeMap.ModeDescriptor proDesc = ShootingModeMap.resolve(
                        modelProfile, oneUiGeneration, ShootingModeMap.ModeKind.PRO_PHOTO);
                String camMsg = OneUiSystemBridge.openCameraSettingsOrPro(this, proDesc);
                onStatusChanged(camMsg);
                Toast.makeText(this, camMsg, Toast.LENGTH_LONG).show();
                break;
            case OPEN_SIDE_KEY_SETTINGS:
                String sideMsg = OneUiSystemBridge.openSideKeySettings(this);
                onStatusChanged(sideMsg);
                Toast.makeText(this, sideMsg, Toast.LENGTH_LONG).show();
                break;
            case VERIFY_RECOVERY:
                executeQuickRecovery();
                break;
        }
    }

    private void refreshAssistantProgressBadge() {
        if (assistantProgressBadge == null) {
            return;
        }
        OneUiAssistantGuide.ProgressSummary summary =
                OneUiAssistantGuide.computeProgress(assistantSteps, completedAssistantStepIds);
        assistantProgressBadge.setText(summary.completedCount + "/" + summary.totalCount);
        if (summary.allCompleted) {
            assistantProgressBadge.setTextColor(Color.parseColor("#34D399"));
            assistantProgressBadge.setBackground(createRoundedDrawable("#064E3B", "#059669", 10, 1));
        } else {
            assistantProgressBadge.setTextColor(Color.parseColor("#38BDF8"));
            assistantProgressBadge.setBackground(createRoundedDrawable("#0F172A", "#1E3A5F", 10, 1));
        }
    }

    /**
     * Construye la tarjeta para fijar accesos directos y elegir el objetivo del Quick Settings Tile.
     */
    private LinearLayout buildShortcutsAndTileCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(12);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(createRoundedDrawable("#111827", "#1F2937", 18, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dpToPx(6);
        card.setLayoutParams(cardLp);

        TextView header = new TextView(this);
        header.setText("ATAJOS Y ACCESO RÁPIDO");
        header.setTextColor(Color.parseColor("#38BDF8"));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        card.addView(header);

        shortcutsStatusView = new TextView(this);
        shortcutsStatusView.setTextColor(Color.parseColor("#94A3B8"));
        shortcutsStatusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dpToPx(4);
        statusLp.bottomMargin = dpToPx(10);
        shortcutsStatusView.setLayoutParams(statusLp);
        card.addView(shortcutsStatusView);

        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        Button pinShortcutButton = createShortButton("＋ Fijar Atajo", "#0284C7", "#FFFFFF", true);
        LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(0, dpToPx(46), 1.0f);
        pinLp.rightMargin = dpToPx(8);
        pinShortcutButton.setLayoutParams(pinLp);
        pinShortcutButton.setOnClickListener(v -> onPinShortcutClicked());
        actionsRow.addView(pinShortcutButton);

        quickTileTargetButton = createShortButton(
                "Tile: " + RescuePreferences.loadQuickTileLabel(this),
                "#1E293B",
                "#7DD3FC",
                false
        );
        LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0, dpToPx(46), 1.0f);
        quickTileTargetButton.setLayoutParams(tileLp);
        quickTileTargetButton.setOnClickListener(v -> cycleQuickTileTarget());
        actionsRow.addView(quickTileTargetButton);

        card.addView(actionsRow);
        return card;
    }

    /**
     * Construye la tarjeta de exportación e importación offline de perfiles .json vía SAF.
     */
    private LinearLayout buildOfflineProfileCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(12);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(createRoundedDrawable("#111827", "#1F2937", 18, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dpToPx(10);
        card.setLayoutParams(cardLp);

        TextView header = new TextView(this);
        header.setText("PERFIL OFFLINE (.JSON · SAF)");
        header.setTextColor(Color.parseColor("#38BDF8"));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        card.addView(header);

        profileStatusText = new TextView(this);
        profileStatusText.setText("Respalda o restaura sensores diagnosticados, visor Camera2 y asistente sin conexión.");
        profileStatusText.setTextColor(Color.parseColor("#94A3B8"));
        profileStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dpToPx(4);
        statusLp.bottomMargin = dpToPx(10);
        profileStatusText.setLayoutParams(statusLp);
        card.addView(profileStatusText);

        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        Button exportBtn = createShortButton("⬇ Exportar .json", "#0F172A", "#38BDF8", true);
        exportBtn.setBackground(createRoundedDrawable("#0F172A", "#1E3A5F", 13, 1));
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        exportLp.rightMargin = dpToPx(8);
        exportBtn.setLayoutParams(exportLp);
        exportBtn.setOnClickListener(v -> launchSafExportProfile());
        actionsRow.addView(exportBtn);

        Button importBtn = createShortButton("⬆ Importar .json", "#1E293B", "#E2E8F0", true);
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        importBtn.setLayoutParams(importLp);
        importBtn.setOnClickListener(v -> launchSafImportProfile());
        actionsRow.addView(importBtn);

        card.addView(actionsRow);
        return card;
    }

    /**
     * Construye la tarjeta interactiva de diagnóstico de sensores (v1.2.0).
     */
    private LinearLayout buildDiagnosticCard() {
        LinearLayout diagCard = new LinearLayout(this);
        diagCard.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(12);
        diagCard.setPadding(pad, pad, pad, pad);
        diagCard.setBackground(createRoundedDrawable("#0F172A", "#0284C7", 18, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dpToPx(14);
        diagCard.setLayoutParams(cardLp);

        // Cabecera del diagnóstico
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        infoCol.setLayoutParams(infoColLp);

        TextView diagTitle = new TextView(this);
        diagTitle.setText("DIAGNÓSTICO ON-DEVICE");
        diagTitle.setTextColor(Color.parseColor("#38BDF8"));
        diagTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        diagTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        infoCol.addView(diagTitle);

        TextView modelText = new TextView(this);
        modelText.setText(modelProfile.marketingName + " (" + Build.MODEL + ")");
        modelText.setTextColor(Color.parseColor("#F8FAFC"));
        modelText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        modelText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        infoCol.addView(modelText);
        topRow.addView(infoCol);

        TextView seriesBadge = new TextView(this);
        seriesBadge.setText(modelProfile.series);
        seriesBadge.setTextColor(Color.parseColor("#10B981"));
        seriesBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        seriesBadge.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
        seriesBadge.setBackground(createRoundedDrawable("#064E3B", "#059669", 10, 1));
        topRow.addView(seriesBadge);
        diagCard.addView(topRow);

        // Botón de ejecución de escaneo
        runScanButton = createShortButton(
                "🔍 Escanear Sensores de este Galaxy",
                "#0284C7",
                "#FFFFFF",
                true
        );
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(46));
        btnLp.topMargin = dpToPx(10);
        btnLp.bottomMargin = dpToPx(6);
        runScanButton.setLayoutParams(btnLp);
        runScanButton.setOnClickListener(v -> triggerSensorScan());
        diagCard.addView(runScanButton);

        // Estado del escaneo
        scanStatusText = new TextView(this);
        scanStatusText.setText("Prueba secuencial no bloqueante (Canary Streams) para aislar fallos de hardware.");
        scanStatusText.setTextColor(Color.parseColor("#94A3B8"));
        scanStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        diagCard.addView(scanStatusText);

        // Contenedor dinámico de resultados
        scanResultsContainer = new LinearLayout(this);
        scanResultsContainer.setOrientation(LinearLayout.VERTICAL);
        scanResultsContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams resLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resLp.topMargin = dpToPx(8);
        scanResultsContainer.setLayoutParams(resLp);
        diagCard.addView(scanResultsContainer);

        return diagCard;
    }

    private void triggerSensorScan() {
        if (sensorScanner.isScanning()) {
            return;
        }
        runScanButton.setEnabled(false);
        runScanButton.setText("⏳ Escaneando lentes de hardware...");
        scanResultsContainer.removeAllViews();
        scanResultsContainer.setVisibility(View.VISIBLE);

        sensorScanner.startScan(modelProfile, new SensorScanner.ScanCallback() {
            @Override
            public void onScanStarted(int totalSensors) {
                scanStatusText.setText("Iniciando prueba de " + totalSensors + " sensores detectados...");
                scanStatusText.setTextColor(Color.parseColor("#38BDF8"));
            }

            @Override
            public void onSensorTested(int index, int total, DiscoveredSensor sensor) {
                scanStatusText.setText("Probando sensor " + index + " de " + total + " (" + sensor.name + ")...");
                scanResultsContainer.addView(buildSensorRow(sensor, false));
            }

            @Override
            public void onScanCompleted(List<DiscoveredSensor> results, int healthyCount, int damagedCount) {
                runScanButton.setEnabled(true);
                runScanButton.setText("✓ Escanear Nuevamente");
                String summary = "Escaneo completado: " + healthyCount + " lentes funcionales, " + damagedCount + " con avería.";
                scanStatusText.setText(summary);
                scanStatusText.setTextColor(damagedCount > 0 ? Color.parseColor("#FBBF24") : Color.parseColor("#34D399"));

                lastScannedSensors = results != null ? new ArrayList<>(results) : new ArrayList<>();
                Set<String> saved = RescuePreferences.loadSelectedSensorIds(MainActivity.this);
                selectedSensorIds = SensorSelectionState.sanitizeSelection(saved, lastScannedSensors);
                RescuePreferences.saveSelectedSensorIds(MainActivity.this, selectedSensorIds);

                renderScanChecklist();
                rebuildDynamicUiAndShortcuts();
                Toast.makeText(MainActivity.this, summary, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onScanError(String errorMessage) {
                runScanButton.setEnabled(true);
                runScanButton.setText("🔍 Reintentar Escaneo");
                scanStatusText.setText("Error en escaneo: " + errorMessage);
                scanStatusText.setTextColor(Color.parseColor("#FCA5A5"));
            }
        });
    }

    private void renderScanChecklist() {
        scanResultsContainer.removeAllViews();
        scanResultsContainer.setVisibility(lastScannedSensors.isEmpty() ? View.GONE : View.VISIBLE);
        for (DiscoveredSensor sensor : lastScannedSensors) {
            scanResultsContainer.addView(buildSensorRow(sensor, true));
        }
    }

    private LinearLayout buildSensorRow(DiscoveredSensor sensor, boolean interactiveChecklist) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(8);
        row.setPadding(pad, pad, pad, pad);
        row.setBackground(createRoundedDrawable("#090D16", "#1E293B", 10, 1));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dpToPx(6);
        row.setLayoutParams(rowLp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        if (interactiveChecklist) {
            boolean selectable = SensorSelectionState.isSelectable(sensor);
            CheckBox checkBox = new CheckBox(this);
            checkBox.setButtonTintList(ColorStateList.valueOf(
                    Color.parseColor(selectable ? "#38BDF8" : "#475569")));
            checkBox.setEnabled(selectable);
            checkBox.setChecked(selectable && selectedSensorIds.contains(sensor.id));
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cbLp.rightMargin = dpToPx(6);
            checkBox.setLayoutParams(cbLp);

            if (!selectable) {
                row.setAlpha(0.55f);
            } else {
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedSensorIds.add(sensor.id);
                    } else {
                        selectedSensorIds.remove(sensor.id);
                    }
                    RescuePreferences.saveSelectedSensorIds(MainActivity.this, selectedSensorIds);
                    rebuildDynamicUiAndShortcuts();
                });
            }
            header.addView(checkBox);
        }

        TextView nameView = new TextView(this);
        nameView.setText(sensor.name + " (ID " + sensor.id + ")");
        nameView.setTextColor(Color.parseColor("#F1F5F9"));
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        nameView.setLayoutParams(nameLp);
        header.addView(nameView);

        TextView badge = new TextView(this);
        badge.setText(sensor.health.displayName);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));

        String bgHex = "#1E293B";
        String strokeHex = "#475569";
        if (sensor.health == SensorHealth.HEALTHY) {
            bgHex = "#064E3B";
            strokeHex = "#059669";
            badge.setTextColor(Color.parseColor("#34D399"));
        } else if (sensor.health == SensorHealth.DAMAGED) {
            bgHex = "#450A0A";
            strokeHex = "#DC2626";
            badge.setTextColor(Color.parseColor("#FCA5A5"));
        } else if (sensor.health == SensorHealth.HAL_PRIVATE) {
            bgHex = "#1E1B4B";
            strokeHex = "#6366F1";
            badge.setTextColor(Color.parseColor("#A5B4FC"));
        }
        badge.setBackground(createRoundedDrawable(bgHex, strokeHex, 8, 1));
        header.addView(badge);
        row.addView(header);

        if (sensor.statusDetail != null && !sensor.statusDetail.isEmpty()) {
            TextView detail = new TextView(this);
            detail.setText(sensor.statusDetail);
            detail.setTextColor(Color.parseColor("#94A3B8"));
            detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            detail.setPadding(0, dpToPx(3), 0, 0);
            row.addView(detail);
        }

        return row;
    }

    private void rebuildDynamicUiAndShortcuts() {
        currentRearSpecs = DynamicUiBuilder.buildRearActionSpecs(modelProfile, lastScannedSensors, selectedSensorIds);
        if (rearCardsContainer != null) {
            rearCardsContainer.removeAllViews();
            if (currentRearSpecs.isEmpty()) {
                rearCardsContainer.addView(buildNoRearSensorsCard());
            } else {
                for (RescueActionSpec spec : currentRearSpecs) {
                    rearCardsContainer.addView(createExpandableRescueCard(
                            spec.title,
                            spec.subtitle,
                            spec.primaryColorHex,
                            spec.accentColorHex,
                            spec.technicalDescription,
                            () -> executeRescueSpec(spec)
                    ));
                }
            }
        }

        updateViewerControlsAfterSelection();

        List<RescueActionSpec> shortcutSpecs =
                DynamicUiBuilder.buildShortcutSpecs(modelProfile, lastScannedSensors, selectedSensorIds);
        lastPublishedShortcutsCount = RescueShortcutManager.updateDynamicShortcuts(this, shortcutSpecs);

        if (!shortcutSpecs.isEmpty()) {
            String savedTileAction = RescuePreferences.loadQuickTileAction(this);
            boolean actionStillActive = CameraConfig.ACTION_QUICK_RECOVERY.equals(savedTileAction)
                    || CameraConfig.ACTION_OPEN_INTEGRATED_VIEWER.equals(savedTileAction);
            for (RescueActionSpec spec : shortcutSpecs) {
                if (spec.intentAction.equals(savedTileAction)) {
                    actionStillActive = true;
                    break;
                }
            }
            if (!actionStillActive) {
                RescueActionSpec fallback = shortcutSpecs.get(0);
                RescuePreferences.saveQuickTileAction(this, fallback.intentAction, resolveShortcutLabel(fallback));
            }
        }
        refreshShortcutsStatusViews();
    }

    private LinearLayout buildNoRearSensorsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(12);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(createRoundedDrawable("#111827", "#F59E0B", 18, 1));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardLp);

        TextView title = new TextView(this);
        title.setText("Sin lentes traseros seleccionados");
        title.setTextColor(Color.parseColor("#FBBF24"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        card.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("Marca al menos un sensor trasero funcional en el diagnóstico superior para habilitar las tarjetas traseras, o usa el visor Camera2 universal.");
        explanation.setTextColor(Color.parseColor("#CBD5E1"));
        explanation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        explanation.setPadding(0, dpToPx(6), 0, 0);
        card.addView(explanation);

        return card;
    }

    private void executeRescueSpec(RescueActionSpec spec) {
        if (spec == null) {
            return;
        }
        switch (spec.actionType) {
            case REAR_PRO_PHOTO:
                launchRearModeViaMap(ShootingModeMap.ModeKind.PRO_PHOTO);
                break;
            case REAR_QR_HUD:
                launchRearQrWithFloatingHud();
                break;
            case REAR_PRO_VIDEO:
                launchRearModeViaMap(ShootingModeMap.ModeKind.PRO_VIDEO);
                break;
            case FRONT_CAMERA:
                if (spec.sensorId != null && !spec.sensorId.trim().isEmpty()) {
                    switchViewerToCameraId(spec.sensorId.trim(), true);
                    focusViewerCard();
                }
                break;
        }
    }

    private void updateViewerControlsAfterSelection() {
        if (switchLensButton == null || lensOverlayBadge == null) {
            return;
        }
        if (lastScannedSensors.isEmpty()) {
            activeViewerSensors = new ArrayList<>();
            activePublicRearSensors = new ArrayList<>();
            updateViewerLensLabels(universalCameraController.getCurrentCameraId());
            return;
        }

        activeViewerSensors = DynamicUiBuilder.getSelectablePublicViewerSensors(lastScannedSensors, selectedSensorIds);
        activePublicRearSensors = DynamicUiBuilder.getSelectablePublicRearSensors(lastScannedSensors, selectedSensorIds);

        if (activeViewerSensors.isEmpty()) {
            switchLensButton.setEnabled(false);
            switchLensButton.setAlpha(0.55f);
            switchLensButton.setText("Sin sensores en visor");
            if (testPublicRearButton != null) {
                testPublicRearButton.setEnabled(false);
                testPublicRearButton.setAlpha(0.5f);
                testPublicRearButton.setText("📷 Sin Trasera Pública");
            }
            lensOverlayBadge.setText("Sensores desactivados en checklist");
            return;
        }

        String currentId = universalCameraController.getCurrentCameraId();
        if (findSensorIndexById(activeViewerSensors, currentId) < 0) {
            String targetId = resolvePreferredViewerFallbackId(activeViewerSensors);
            switchViewerToCameraId(targetId, true);
            currentId = targetId;
        }
        updateViewerLensLabels(currentId);
    }

    private static String resolvePreferredViewerFallbackId(List<DiscoveredSensor> viewerSensors) {
        for (DiscoveredSensor sensor : viewerSensors) {
            if (sensor.facing == 1) {
                return sensor.id;
            }
        }
        return viewerSensors.get(0).id;
    }

    private void onSwitchViewerLensClicked() {
        cancelCountdown(false);
        if (!activeViewerSensors.isEmpty()) {
            if (activeViewerSensors.size() < 2) {
                return;
            }
            int idx = findSensorIndexById(activeViewerSensors, universalCameraController.getCurrentCameraId());
            DiscoveredSensor next = activeViewerSensors.get((idx + 1) % activeViewerSensors.size());
            switchViewerToCameraId(next.id, true);
            return;
        }

        String currentId = universalCameraController.getCurrentCameraId();
        String nextId = CameraConfig.FRONT_CAMERA_WIDE_ID.equals(currentId)
                ? CameraConfig.FRONT_CAMERA_NORMAL_ID
                : CameraConfig.FRONT_CAMERA_WIDE_ID;
        switchViewerToCameraId(nextId, true);
    }

    private void onTestPublicRearClicked() {
        cancelCountdown(false);
        if (!lastScannedSensors.isEmpty()) {
            if (activePublicRearSensors.isEmpty()) {
                Toast.makeText(this, "No hay sensores traseros públicos saludables seleccionados.", Toast.LENGTH_SHORT).show();
                return;
            }
            String currentId = universalCameraController.getCurrentCameraId();
            int currentIdx = findSensorIndexById(activePublicRearSensors, currentId);
            DiscoveredSensor target = (currentIdx >= 0 && activePublicRearSensors.size() > 1)
                    ? activePublicRearSensors.get((currentIdx + 1) % activePublicRearSensors.size())
                    : activePublicRearSensors.get(0);
            switchViewerToCameraId(target.id, true);
            return;
        }
        switchViewerToCameraId("0", true);
    }

    private void switchViewerToCameraId(String cameraId, boolean persistPreference) {
        if (cameraId == null || cameraId.trim().isEmpty()) {
            return;
        }
        String trimmedId = cameraId.trim();
        cancelCountdown(false);
        pendingVideoRecordAudioMode = null;
        viewerPreferredCameraId = trimmedId;
        currentCameraOpenedSuccessfully = false;
        pendingApplySavedFlash = true;
        pendingApplySavedAperture = true;

        boolean hasCameraPermission =
                checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (hasCameraPermission && viewerPreviewView != null && viewerPreviewView.isAvailable()) {
            universalCameraController.switchToCameraId(trimmedId, viewerPreviewView, getDisplayRotationCompat());
        } else {
            universalCameraController.setCurrentCameraId(trimmedId);
            if (!hasCameraPermission) {
                ensureCameraPermissionAndStartViewerPreview();
            }
        }
        updateViewerLensLabels(trimmedId);
        if (persistPreference) {
            persistViewerPreferences();
        }
    }

    private void updateViewerLensLabels(String activeId) {
        if (switchLensButton == null || lensOverlayBadge == null) {
            return;
        }
        boolean recording = universalCameraController != null && universalCameraController.isRecordingVideo();

        if (!activeViewerSensors.isEmpty()) {
            if (activeViewerSensors.size() == 1) {
                DiscoveredSensor only = activeViewerSensors.get(0);
                switchLensButton.setEnabled(false);
                switchLensButton.setAlpha(0.65f);
                switchLensButton.setText("1 lente en visor");
                lensOverlayBadge.setText(formatSensorBadge(only));
            } else {
                int idx = findSensorIndexById(activeViewerSensors, activeId);
                if (idx < 0) {
                    idx = 0;
                }
                DiscoveredSensor current = activeViewerSensors.get(idx);
                DiscoveredSensor next = activeViewerSensors.get((idx + 1) % activeViewerSensors.size());
                switchLensButton.setEnabled(!recording);
                switchLensButton.setAlpha(recording ? 0.5f : 1.0f);
                String nextDir = (next.facing == 1) ? "Frontal" : "Trasera";
                String nextMp = next.getResolutionMegaPixels();
                switchLensButton.setText("N/D".equals(nextMp) ? (nextDir + " ID " + next.id) : (nextDir + " " + nextMp));
                lensOverlayBadge.setText(formatSensorBadge(current));
            }
            if (testPublicRearButton != null) {
                boolean hasRear = !activePublicRearSensors.isEmpty();
                testPublicRearButton.setEnabled(hasRear && !recording);
                testPublicRearButton.setAlpha((hasRear && !recording) ? 1.0f : 0.5f);
                testPublicRearButton.setText(hasRear ? "📷 Probar Trasera Pública en Visor" : "📷 Sin Trasera Pública");
            }
            return;
        }

        switchLensButton.setEnabled(!recording);
        switchLensButton.setAlpha(recording ? 0.5f : 1.0f);
        if (testPublicRearButton != null) {
            testPublicRearButton.setEnabled(!recording);
            testPublicRearButton.setAlpha(recording ? 0.5f : 1.0f);
            testPublicRearButton.setText("📷 Probar Trasera Pública en Visor");
        }

        if (CameraConfig.FRONT_CAMERA_WIDE_ID.equals(activeId)) {
            switchLensButton.setText("Lente 8.6 MP");
            lensOverlayBadge.setText("Frontal ID 1 · 12 MP");
        } else if (CameraConfig.FRONT_CAMERA_NORMAL_ID.equals(activeId)) {
            switchLensButton.setText("Lente 12 MP");
            lensOverlayBadge.setText("Frontal ID 3 · 8.6 MP");
        } else if (universalCameraController != null && !universalCameraController.isCurrentFrontFacing()) {
            switchLensButton.setText("Volver a Frontal");
            lensOverlayBadge.setText("Trasera Pública ID " + activeId);
        } else {
            switchLensButton.setText("Cambiar Lente");
            lensOverlayBadge.setText("Frontal ID " + activeId);
        }
    }

    private static String formatSensorBadge(DiscoveredSensor sensor) {
        String prefix = (sensor.facing == 1)
                ? ("Frontal ID " + sensor.id)
                : ("Trasera Pública ID " + sensor.id);
        String mp = sensor.getResolutionMegaPixels();
        return "N/D".equals(mp) ? prefix : (prefix + " · " + mp);
    }

    private void onFlashButtonClicked() {
        universalCameraController.cycleFlashMode();
        viewerFlashMode = universalCameraController.getCapabilities().activeFlashMode;
        pendingApplySavedFlash = false;
        persistViewerPreferences();
    }

    private void onTimerButtonClicked() {
        if (countdownRemainingSeconds > 0) {
            cancelCountdown(true);
        }
        viewerTimerSeconds = CameraViewerMath.nextTimerSeconds(viewerTimerSeconds);
        updateTimerButtonLabel();
        persistViewerPreferences();
    }

    private void onGridButtonClicked() {
        viewerGridMode = viewerGridMode.next();
        if (gridOverlayView != null) {
            gridOverlayView.setGridMode(viewerGridMode);
        }
        updateGridButtonLabel();
        persistViewerPreferences();
    }

    private void onApertureButtonClicked() {
        universalCameraController.cycleAperture();
        viewerPreferredAperture = universalCameraController.getCapabilities().activeAperture;
        pendingApplySavedAperture = false;
        persistViewerPreferences();
    }

    private void onCapturePhotoClicked() {
        if (universalCameraController.isRecordingVideo()) {
            return;
        }
        if (countdownRemainingSeconds > 0) {
            cancelCountdown(true);
            return;
        }
        if (viewerTimerSeconds <= 0) {
            universalCameraController.capturePhoto(getDisplayRotationCompat());
            return;
        }
        startPhotoCountdown(viewerTimerSeconds);
    }

    private void startPhotoCountdown(int seconds) {
        cancelCountdown(false);
        countdownRemainingSeconds = seconds;
        updateCountdownUi(countdownRemainingSeconds);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                countdownRemainingSeconds--;
                if (countdownRemainingSeconds > 0) {
                    updateCountdownUi(countdownRemainingSeconds);
                    countdownHandler.postDelayed(this, COUNTDOWN_TICK_MS);
                } else {
                    countdownRunnable = null;
                    countdownRemainingSeconds = 0;
                    updateCountdownUi(0);
                    universalCameraController.capturePhoto(getDisplayRotationCompat());
                }
            }
        };
        countdownHandler.postDelayed(countdownRunnable, COUNTDOWN_TICK_MS);
    }

    private void cancelCountdown(boolean notifyUser) {
        boolean wasActive = countdownRemainingSeconds > 0 || countdownRunnable != null;
        if (countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }
        countdownRemainingSeconds = 0;
        updateCountdownUi(0);
        if (wasActive && notifyUser) {
            onStatusChanged("Temporizador cancelado.");
        }
    }

    private void updateCountdownUi(int remainingSeconds) {
        if (countdownOverlayText != null) {
            if (remainingSeconds > 0) {
                countdownOverlayText.setText(String.valueOf(remainingSeconds));
                countdownOverlayText.setVisibility(View.VISIBLE);
            } else {
                countdownOverlayText.setVisibility(View.GONE);
            }
        }
        if (capturePhotoButton != null && !universalCameraController.isRecordingVideo()) {
            if (remainingSeconds > 0) {
                capturePhotoButton.setText("✕ Cancelar " + remainingSeconds + "s");
                capturePhotoButton.setBackground(createRoundedDrawable("#B45309", "#B45309", 13, 0));
            } else {
                capturePhotoButton.setText(CameraConfig.BTN_VIEWER_PHOTO_SHORT);
                capturePhotoButton.setBackground(createRoundedDrawable("#059669", "#059669", 13, 0));
            }
        }
    }

    private void onRecordVideoClicked() {
        if (universalCameraController.isRecordingVideo()) {
            universalCameraController.stopVideoRecording();
            return;
        }
        cancelCountdown(false);

        boolean hasAudioPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        VideoRecordingState.StartDecision decision = VideoRecordingState.decideStart(
                hasAudioPerm,
                audioPermissionDeniedThisSession,
                false
        );

        switch (decision) {
            case REQUEST_AUDIO_PERMISSION:
                pendingVideoRecordAfterAudioPrompt = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION);
                break;
            case START_WITH_AUDIO:
                universalCameraController.startVideoRecording(
                        getDisplayRotationCompat(),
                        VideoRecordingState.AudioMode.WITH_AUDIO
                );
                break;
            case START_VIDEO_ONLY:
                String msg = "Grabando MP4 sin audio (permiso de micrófono no concedido)";
                onStatusChanged(msg);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                universalCameraController.startVideoRecording(
                        getDisplayRotationCompat(),
                        VideoRecordingState.AudioMode.VIDEO_ONLY_NO_MIC_PERMISSION
                );
                break;
        }
    }

    private void updateRecordingUi(boolean recording, long elapsedMs, VideoRecordingState.AudioMode audioMode) {
        if (recordingTimerBadge != null) {
            if (recording) {
                String formatted = VideoRecordingState.formatElapsedDuration(elapsedMs);
                String prefix = (audioMode == VideoRecordingState.AudioMode.WITH_AUDIO)
                        ? "● REC "
                        : "● REC (Sin audio) ";
                recordingTimerBadge.setText(prefix + formatted);
                recordingTimerBadge.setVisibility(View.VISIBLE);
            } else {
                recordingTimerBadge.setVisibility(View.GONE);
            }
        }
        if (recordVideoButton != null) {
            if (recording) {
                recordVideoButton.setText(CameraConfig.BTN_VIEWER_VIDEO_STOP_SHORT);
                recordVideoButton.setTextColor(Color.parseColor("#FFFFFF"));
                recordVideoButton.setBackground(createRoundedDrawable("#DC2626", "#DC2626", 13, 0));
            } else {
                recordVideoButton.setText(CameraConfig.BTN_VIEWER_VIDEO_SHORT);
                recordVideoButton.setTextColor(Color.parseColor("#FCA5A5"));
                recordVideoButton.setBackground(createRoundedDrawable("#1E293B", "#1E293B", 13, 0));
            }
        }
        if (capturePhotoButton != null) {
            capturePhotoButton.setEnabled(!recording);
            capturePhotoButton.setAlpha(recording ? 0.45f : 1.0f);
            if (!recording && countdownRemainingSeconds == 0) {
                capturePhotoButton.setText(CameraConfig.BTN_VIEWER_PHOTO_SHORT);
                capturePhotoButton.setBackground(createRoundedDrawable("#059669", "#059669", 13, 0));
            }
        }
        updateViewerLensLabels(universalCameraController != null
                ? universalCameraController.getCurrentCameraId()
                : viewerPreferredCameraId);
    }

    private void updateViewerCapabilityControls(UniversalCameraController.ViewerCapabilities caps) {
        if (caps == null) {
            return;
        }
        if (zoomPresetButton != null && zoomOutButton != null && zoomInButton != null) {
            boolean zoomSupported = caps.zoomCaps != null
                    && (caps.zoomCaps.maxZoom > caps.zoomCaps.minZoom + 0.001f);
            zoomPresetButton.setText(CameraViewerMath.formatZoomLabel(caps.activeZoom));
            zoomPresetButton.setEnabled(zoomSupported);
            zoomPresetButton.setAlpha(zoomSupported ? 1.0f : 0.65f);
            zoomOutButton.setEnabled(zoomSupported && caps.activeZoom > caps.zoomCaps.minZoom + 0.001f);
            zoomInButton.setEnabled(zoomSupported && caps.activeZoom < caps.zoomCaps.maxZoom - 0.001f);
            zoomOutButton.setAlpha(zoomOutButton.isEnabled() ? 1.0f : 0.45f);
            zoomInButton.setAlpha(zoomInButton.isEnabled() ? 1.0f : 0.45f);
        }

        if (evValueLabel != null && evMinusButton != null && evPlusButton != null) {
            boolean evSupported = caps.evCaps != null && caps.evCaps.supported;
            evValueLabel.setText(CameraViewerMath.formatEvValue(caps.activeEvIndex, caps.evCaps));
            evMinusButton.setEnabled(evSupported && caps.activeEvIndex > caps.evCaps.minIndex);
            evPlusButton.setEnabled(evSupported && caps.activeEvIndex < caps.evCaps.maxIndex);
            evMinusButton.setAlpha(evMinusButton.isEnabled() ? 1.0f : 0.45f);
            evPlusButton.setAlpha(evPlusButton.isEnabled() ? 1.0f : 0.45f);
        }

        updateFlashButtonUi(caps.flashAvailable, caps.activeFlashMode);
        updateApertureButtonUi(caps);

        if (lastScannedSensors.isEmpty() && lensOverlayBadge != null) {
            String dirLabel = caps.isFrontFacing ? "Frontal" : "Trasera Pública";
            String mpLabel = (caps.jpegWidth > 0 && caps.jpegHeight > 0)
                    ? String.format(java.util.Locale.US, " · %.1f MP", ((double) caps.jpegWidth * caps.jpegHeight) / 1_000_000.0)
                    : "";
            lensOverlayBadge.setText(dirLabel + " ID " + caps.cameraId + mpLabel);
        }
    }

    private void updateFlashButtonUi(boolean flashAvailable, CameraViewerMath.FlashMode mode) {
        if (flashButton == null) {
            return;
        }
        CameraViewerMath.FlashMode safeMode = (mode != null) ? mode : CameraViewerMath.FlashMode.OFF;
        flashButton.setText(formatFlashButtonLabel(safeMode));
        flashButton.setEnabled(flashAvailable);
        flashButton.setAlpha(flashAvailable ? 1.0f : 0.45f);
    }

    private static String formatFlashButtonLabel(CameraViewerMath.FlashMode mode) {
        if (mode == null) {
            return "⚡ Off";
        }
        switch (mode) {
            case AUTO:
                return "⚡ Auto";
            case ON:
                return "⚡ On";
            case TORCH:
                return "⚡ Linterna";
            case OFF:
            default:
                return "⚡ Off";
        }
    }

    private void updateTimerButtonLabel() {
        if (timerButton != null) {
            timerButton.setText(viewerTimerSeconds == 0 ? "⏱ Off" : ("⏱ " + viewerTimerSeconds + "s"));
        }
    }

    private void updateGridButtonLabel() {
        if (gridButton != null) {
            CameraViewerMath.GridMode mode = (viewerGridMode != null) ? viewerGridMode : CameraViewerMath.GridMode.OFF;
            gridButton.setText("⊞ " + mode.displayLabel);
        }
    }

    private void updateApertureButtonUi(UniversalCameraController.ViewerCapabilities caps) {
        if (apertureButton == null) {
            return;
        }
        boolean supportedBySensor = caps != null
                && caps.apertureCaps != null
                && caps.apertureCaps.variableApertureSupported;
        if (supportedBySensor) {
            apertureButton.setVisibility(View.VISIBLE);
            apertureButton.setEnabled(true);
            apertureButton.setAlpha(1.0f);
            apertureButton.setText("◉ " + CameraViewerMath.formatApertureLabel(caps.activeAperture, caps.apertureCaps));
        } else if (modelProfile != null && modelProfile.hasVariableAperture) {
            apertureButton.setVisibility(View.VISIBLE);
            apertureButton.setEnabled(false);
            apertureButton.setAlpha(0.5f);
            apertureButton.setText("f/1.5↔2.4 (Trasera)");
        } else {
            apertureButton.setVisibility(View.GONE);
        }
    }

    private void applyViewerPreferencesSnapshot(
            DiagnosticProfileCodec.ViewerPreferencesSnapshot snapshot,
            boolean resetPendingFlags
    ) {
        DiagnosticProfileCodec.ViewerPreferencesSnapshot safe =
                (snapshot != null) ? snapshot : DiagnosticProfileCodec.ViewerPreferencesSnapshot.defaults();
        viewerPreferredCameraId = safe.preferredCameraId;
        viewerGridMode = safe.gridMode;
        viewerTimerSeconds = safe.timerSeconds;
        viewerFlashMode = safe.flashMode;
        viewerPreferredAperture = safe.preferredAperture;
        if (resetPendingFlags) {
            pendingApplySavedFlash = true;
            pendingApplySavedAperture = true;
        }
    }

    private DiagnosticProfileCodec.ViewerPreferencesSnapshot buildCurrentViewerPreferencesSnapshot() {
        String activeId = (viewerPreferredCameraId != null && !viewerPreferredCameraId.trim().isEmpty())
                ? viewerPreferredCameraId
                : CameraConfig.FRONT_CAMERA_WIDE_ID;
        return new DiagnosticProfileCodec.ViewerPreferencesSnapshot(
                activeId,
                viewerGridMode,
                viewerTimerSeconds,
                viewerFlashMode,
                viewerPreferredAperture
        );
    }

    private void persistViewerPreferences() {
        RescuePreferences.saveViewerPreferences(this, buildCurrentViewerPreferencesSnapshot());
    }

    private void launchSafExportProfile() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(
                    Intent.EXTRA_TITLE,
                    DiagnosticProfileCodec.buildSuggestedFileName(Build.MODEL, System.currentTimeMillis())
            );
            startActivityForResult(intent, REQUEST_EXPORT_PROFILE);
        } catch (RuntimeException e) {
            onError("No se pudo abrir el selector SAF para exportar: " + e.getMessage());
        }
    }

    private void launchSafImportProfile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain"});
            startActivityForResult(intent, REQUEST_IMPORT_PROFILE);
        } catch (RuntimeException e) {
            onError("No se pudo abrir el selector SAF para importar: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_PROFILE) {
            handleExportProfileUri(uri);
        } else if (requestCode == REQUEST_IMPORT_PROFILE) {
            handleImportProfileUri(uri);
        }
    }

    private void handleExportProfileUri(Uri uri) {
        try {
            DiagnosticProfileCodec.DiagnosticProfile profile = new DiagnosticProfileCodec.DiagnosticProfile(
                    CameraConfig.PROFILE_SCHEMA_VERSION,
                    CameraConfig.APP_VERSION,
                    System.currentTimeMillis(),
                    Build.MODEL,
                    modelProfile.marketingName,
                    oneUiGeneration,
                    selectedSensorIds,
                    RescuePreferences.loadQuickTileAction(this),
                    RescuePreferences.loadQuickTileLabel(this),
                    completedAssistantStepIds,
                    buildCurrentViewerPreferencesSnapshot(),
                    lastScannedSensors
            );
            String json = DiagnosticProfileCodec.encode(profile);
            try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                if (os == null) {
                    throw new IOException("No se pudo abrir el flujo de escritura SAF.");
                }
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            if (profileStatusText != null) {
                profileStatusText.setText("✓ Perfil .json exportado (" + lastScannedSensors.size() + " sensores).");
                profileStatusText.setTextColor(Color.parseColor("#34D399"));
            }
            onStatusChanged("Perfil .json exportado");
            Toast.makeText(this, "Perfil .json exportado", Toast.LENGTH_SHORT).show();
        } catch (IOException | RuntimeException e) {
            onError("Error exportando perfil .json: " + e.getMessage());
        }
    }

    private void handleImportProfileUri(Uri uri) {
        try {
            String json = readUtf8BoundedFromUri(uri, DiagnosticProfileCodec.MAX_JSON_BYTES);
            DiagnosticProfileCodec.DiagnosticProfile profile = DiagnosticProfileCodec.decode(json);

            lastScannedSensors = new ArrayList<>(profile.discoveredSensors);
            if (!lastScannedSensors.isEmpty()) {
                selectedSensorIds = SensorSelectionState.sanitizeSelection(profile.selectedSensorIds, lastScannedSensors);
            } else {
                selectedSensorIds = new LinkedHashSet<>(profile.selectedSensorIds);
            }
            RescuePreferences.saveSelectedSensorIds(this, selectedSensorIds);

            RescuePreferences.saveQuickTileAction(this, profile.quickTileAction, profile.quickTileLabel);

            completedAssistantStepIds = OneUiAssistantGuide.sanitizeCompletedStepIds(
                    profile.completedAssistantStepIds,
                    assistantSteps
            );
            RescuePreferences.saveAssistantCompletedSteps(this, completedAssistantStepIds);
            refreshAssistantStepsUi();

            applyViewerPreferencesSnapshot(profile.viewerPreferences, true);
            RescuePreferences.saveViewerPreferences(this, profile.viewerPreferences);
            if (gridOverlayView != null) {
                gridOverlayView.setGridMode(viewerGridMode);
            }
            updateTimerButtonLabel();
            updateGridButtonLabel();
            updateFlashButtonUi(false, viewerFlashMode);
            updateApertureButtonUi(null);
            switchViewerToCameraId(viewerPreferredCameraId, false);

            if (scanStatusText != null && !lastScannedSensors.isEmpty()) {
                scanStatusText.setText("Perfil cargado: " + lastScannedSensors.size() + " sensores importados.");
                scanStatusText.setTextColor(Color.parseColor("#38BDF8"));
            }
            renderScanChecklist();
            rebuildDynamicUiAndShortcuts();

            if (!DiagnosticProfileCodec.isCompatibleModel(profile.deviceModel, Build.MODEL)) {
                String warn = "Perfil importado de otro modelo (" + profile.deviceModel + "): sensores adaptados.";
                if (profileStatusText != null) {
                    profileStatusText.setText("▲ " + warn);
                    profileStatusText.setTextColor(Color.parseColor("#FBBF24"));
                }
                if (statusBanner != null) {
                    statusBanner.setTextColor(Color.parseColor("#FBBF24"));
                    statusBanner.setText("▲ " + warn);
                }
                Toast.makeText(this, warn, Toast.LENGTH_LONG).show();
            } else {
                String ok = "Perfil .json importado (" + lastScannedSensors.size() + " sensores).";
                if (profileStatusText != null) {
                    profileStatusText.setText("✓ " + ok);
                    profileStatusText.setTextColor(Color.parseColor("#34D399"));
                }
                onStatusChanged(ok);
                Toast.makeText(this, ok, Toast.LENGTH_SHORT).show();
            }
        } catch (DiagnosticProfileCodec.ValidationException e) {
            onError("Perfil .json inválido: " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            onError("Error importando perfil .json: " + e.getMessage());
        }
    }

    private String readUtf8BoundedFromUri(Uri uri, int maxBytes) throws IOException {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IOException("No se pudo abrir el archivo .json seleccionado.");
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int totalBytes = 0;
            int read;
            while ((read = is.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > maxBytes) {
                    throw new IOException("Archivo demasiado grande (máx 256 KB).");
                }
                baos.write(buffer, 0, read);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void onPinShortcutClicked() {
        RescueActionSpec targetSpec = null;
        if (!currentRearSpecs.isEmpty()) {
            targetSpec = currentRearSpecs.get(0);
        } else {
            List<RescueActionSpec> shortcutSpecs =
                    DynamicUiBuilder.buildShortcutSpecs(modelProfile, lastScannedSensors, selectedSensorIds);
            if (!shortcutSpecs.isEmpty()) {
                targetSpec = shortcutSpecs.get(0);
            }
        }
        if (targetSpec == null) {
            Toast.makeText(this, "Activa al menos un sensor para fijar un atajo", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean sent = RescueShortcutManager.requestPinShortcut(this, targetSpec);
        Toast.makeText(
                this,
                sent ? "Solicitud enviada al launcher" : "El launcher no soporta atajos fijados",
                Toast.LENGTH_SHORT
        ).show();
    }

    private static final class QuickTileOption {
        final String action;
        final String label;

        QuickTileOption(String action, String label) {
            this.action = action;
            this.label = label;
        }
    }

    private List<QuickTileOption> buildQuickTileOptions() {
        List<RescueActionSpec> specs =
                DynamicUiBuilder.buildShortcutSpecs(modelProfile, lastScannedSensors, selectedSensorIds);
        List<QuickTileOption> options = new ArrayList<>();
        for (RescueActionSpec spec : specs) {
            options.add(new QuickTileOption(spec.intentAction, resolveShortcutLabel(spec)));
        }
        options.add(new QuickTileOption(CameraConfig.ACTION_OPEN_INTEGRATED_VIEWER, "Visor Universal"));
        options.add(new QuickTileOption(CameraConfig.ACTION_QUICK_RECOVERY, "Recuperación"));
        return options;
    }

    private void cycleQuickTileTarget() {
        List<QuickTileOption> available = buildQuickTileOptions();
        if (available.isEmpty()) {
            Toast.makeText(this, "Activa al menos un sensor para configurar el Tile Rápido", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentAction = RescuePreferences.loadQuickTileAction(this);
        int currentIndex = -1;
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i).action.equals(currentAction)) {
                currentIndex = i;
                break;
            }
        }
        QuickTileOption next = available.get((currentIndex + 1) % available.size());
        RescuePreferences.saveQuickTileAction(this, next.action, next.label);
        refreshShortcutsStatusViews();
        Toast.makeText(this, "Tile Rápido actualizado a: " + next.label, Toast.LENGTH_SHORT).show();
    }

    private void refreshShortcutsStatusViews() {
        String tileLabel = RescuePreferences.loadQuickTileLabel(this);
        if (quickTileTargetButton != null) {
            quickTileTargetButton.setText("Tile: " + tileLabel);
        }
        if (shortcutsStatusView != null) {
            shortcutsStatusView.setText(lastPublishedShortcutsCount + " atajos en Launcher · Tile Rápido: " + tileLabel);
        }
    }

    private static int findSensorIndexById(List<DiscoveredSensor> sensors, String id) {
        if (sensors == null || id == null) {
            return -1;
        }
        for (int i = 0; i < sensors.size(); i++) {
            if (id.equals(sensors.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isKnownFrontCameraId(String cameraId) {
        if (cameraId == null) {
            return false;
        }
        if (CameraConfig.FRONT_CAMERA_WIDE_ID.equals(cameraId)
                || CameraConfig.FRONT_CAMERA_NORMAL_ID.equals(cameraId)) {
            return true;
        }
        if (modelProfile != null
                && (cameraId.equals(modelProfile.frontWideId) || cameraId.equals(modelProfile.frontNormalId))) {
            return true;
        }
        for (DiscoveredSensor sensor : lastScannedSensors) {
            if (cameraId.equals(sensor.id) && sensor.facing == 1) {
                return true;
            }
        }
        return false;
    }

    private static String resolveShortcutLabel(RescueActionSpec spec) {
        if (spec.shortcutShortLabel != null && !spec.shortcutShortLabel.trim().isEmpty()) {
            return spec.shortcutShortLabel.trim();
        }
        return spec.title;
    }

    private LinearLayout createExpandableRescueCard(
            String shortButtonTitle,
            String subtitleText,
            String primaryButtonHex,
            String accentBorderHex,
            String technicalText,
            Runnable onLaunchAction
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(10);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(createRoundedDrawable("#111827", "#1F2937", 18, 1));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardLp);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        Button launchBtn = createShortButton(shortButtonTitle, primaryButtonHex, "#FFFFFF", true);
        LinearLayout.LayoutParams launchLp = new LinearLayout.LayoutParams(
                0, dpToPx(46), 1.0f);
        launchLp.rightMargin = dpToPx(8);
        launchBtn.setLayoutParams(launchLp);
        launchBtn.setOnClickListener(v -> onLaunchAction.run());
        topRow.addView(launchBtn);

        TextView techDetailView = createTechDescriptionView(technicalText);
        Button arrowBtn = createArrowToggleButton(techDetailView);
        topRow.addView(arrowBtn);

        card.addView(topRow);

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitleText);
        subtitleView.setTextColor(Color.parseColor(accentBorderHex));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        subtitleView.setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(2));
        card.addView(subtitleView);

        card.addView(techDetailView);
        return card;
    }

    private Button createShortButton(String text, String bgHex, String fgHex, boolean bold) {
        return createCompactButton(text, bgHex, bgHex, fgHex, 14, bold);
    }

    private Button createCompactButton(
            String text,
            String bgHex,
            String strokeHex,
            String fgHex,
            int textSp,
            boolean bold
    ) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.parseColor(fgHex));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
        button.setTypeface(Typeface.create("sans-serif-medium", bold ? Typeface.BOLD : Typeface.NORMAL));
        int strokeDp = bgHex.equals(strokeHex) ? 0 : 1;
        button.setBackground(createRoundedDrawable(bgHex, strokeHex, 12, strokeDp));
        button.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));
        return button;
    }

    private Button createArrowToggleButton(final TextView targetTechView) {
        final Button arrowBtn = new Button(this);
        arrowBtn.setAllCaps(false);
        arrowBtn.setText("▾");
        arrowBtn.setTextColor(Color.parseColor("#38BDF8"));
        arrowBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        arrowBtn.setTypeface(Typeface.DEFAULT_BOLD);
        arrowBtn.setBackground(createRoundedDrawable("#0F172A", "#334155", 13, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(46), dpToPx(46));
        arrowBtn.setLayoutParams(lp);
        arrowBtn.setOnClickListener(v -> {
            boolean willShow = targetTechView.getVisibility() != View.VISIBLE;
            targetTechView.setVisibility(willShow ? View.VISIBLE : View.GONE);
            arrowBtn.setText(willShow ? "▴" : "▾");
            arrowBtn.setBackground(createRoundedDrawable(
                    willShow ? "#1E293B" : "#0F172A",
                    willShow ? "#38BDF8" : "#334155",
                    13,
                    1
            ));
        });
        return arrowBtn;
    }

    private TextView createTechDescriptionView(String description) {
        TextView tv = new TextView(this);
        tv.setText(description);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setLineSpacing(dpToPx(2), 1.0f);
        int p = dpToPx(10);
        tv.setPadding(p, p, p, p);
        tv.setBackground(createRoundedDrawable("#090D16", "#1E3A5F", 12, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dpToPx(8);
        tv.setLayoutParams(lp);
        tv.setVisibility(View.GONE);
        return tv;
    }

    private GradientDrawable createRoundedDrawable(String fillHex, String strokeHex, int cornerDp, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(fillHex));
        drawable.setCornerRadius(dpToPx(cornerDp));
        if (strokeDp > 0) {
            drawable.setStroke(dpToPx(strokeDp), Color.parseColor(strokeHex));
        }
        return drawable;
    }

    private void launchRearModeViaMap(ShootingModeMap.ModeKind kind) {
        cancelCountdown(false);
        pendingVideoRecordAudioMode = null;
        currentCameraOpenedSuccessfully = false;
        universalCameraController.closeCameraOnly();
        updateRecordingUi(false, 0L, null);
        ShootingModeMap.ModeDescriptor descriptor = ShootingModeMap.resolve(modelProfile, oneUiGeneration, kind);
        lastLaunchedRearMode = kind;
        lastRearLaunchTimestampMs = System.currentTimeMillis();
        recoveryAttemptAdvancedOnLaunch = false;
        ShootingModeMap.LaunchVariant launched = OneUiSystemBridge.launchModeWithFallback(this, descriptor);
        if (launched == null) {
            lastRearLaunchTimestampMs = 0L;
            onError("No se pudo abrir Samsung Camera en modo " + kind.name());
        }
    }

    private void launchRearQrWithFloatingHud() {
        RearCaptureService service = RearCaptureService.getInstance();
        if (service == null) {
            Toast.makeText(
                    this,
                    "Activa 'Samsung Camera Rescue — Obturador Trasero' en Accesibilidad para usar el botón flotante.",
                    Toast.LENGTH_LONG
            ).show();
            try {
                Intent settingsIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(settingsIntent);
            } catch (RuntimeException ignored) {
            }
            return;
        }

        cancelCountdown(false);
        pendingVideoRecordAudioMode = null;
        currentCameraOpenedSuccessfully = false;
        universalCameraController.closeCameraOnly();
        updateRecordingUi(false, 0L, null);
        service.showHud();

        ShootingModeMap.ModeDescriptor descriptor = ShootingModeMap.resolve(
                modelProfile, oneUiGeneration, ShootingModeMap.ModeKind.QR_SCANNER);
        lastLaunchedRearMode = ShootingModeMap.ModeKind.QR_SCANNER;
        lastRearLaunchTimestampMs = System.currentTimeMillis();
        recoveryAttemptAdvancedOnLaunch = false;
        ShootingModeMap.LaunchVariant launched = OneUiSystemBridge.launchModeWithFallback(this, descriptor);
        if (launched == null) {
            lastRearLaunchTimestampMs = 0L;
            service.hideHud();
            onError("No se pudo iniciar SCAN_QR_CODE de Samsung Camera.");
        }
    }

    private void executeQuickRecovery() {
        if (pendingRecoveryRunnable != null) {
            recoveryHandler.removeCallbacks(pendingRecoveryRunnable);
            pendingRecoveryRunnable = null;
        }

        CameraRecoveryEngine.RecoveryPlan plan = CameraRecoveryEngine.buildRecoveryPlan(
                modelProfile, oneUiGeneration, lastLaunchedRearMode, recoveryAttemptIndex);

        if (plan.shouldResetLocalSession) {
            cancelCountdown(false);
            pendingVideoRecordAudioMode = null;
            currentCameraOpenedSuccessfully = false;
            universalCameraController.stop();
            updateRecordingUi(false, 0L, null);
            RearCaptureService service = RearCaptureService.getInstance();
            if (service != null) {
                service.hideHud();
            }
        }

        if (plan.action == CameraRecoveryEngine.RecoveryAction.OPEN_CAMERA_APP_INFO) {
            boolean opened = OneUiSystemBridge.openSamsungCameraAppInfo(this);
            if (!opened) {
                onError("No se pudo abrir la información de Samsung Camera.");
                return;
            }
            if (statusBanner != null) {
                statusBanner.setTextColor(Color.parseColor("#F59E0B"));
                statusBanner.setText("▲ " + plan.userMessage);
            }
            Toast.makeText(this, plan.userMessage, Toast.LENGTH_LONG).show();
            recoveryAttemptIndex = 0;
            recoveryAttemptAdvancedOnLaunch = false;
            updateRecoveryCardState(false, plan.userMessage);
            return;
        }

        if (statusBanner != null) {
            statusBanner.setTextColor(Color.parseColor("#F59E0B"));
            statusBanner.setText("⚡ " + plan.title);
        }

        pendingRecoveryRunnable = () -> {
            pendingRecoveryRunnable = null;
            if (isFinishing() || isDestroyed()) {
                return;
            }
            lastRearLaunchTimestampMs = System.currentTimeMillis();
            boolean launched = OneUiSystemBridge.launchVariant(this, plan.launchVariant);
            if (!launched) {
                lastRearLaunchTimestampMs = 0L;
                recoveryAttemptAdvancedOnLaunch = false;
                onError("No se pudo ejecutar " + plan.title);
                return;
            }
            recoveryAttemptIndex = CameraRecoveryEngine.nextAttemptIndex(recoveryAttemptIndex, false);
            recoveryAttemptAdvancedOnLaunch = true;
            updateRecoveryCardState(false, null);
        };
        recoveryHandler.postDelayed(pendingRecoveryRunnable, plan.cooldownMs);
    }

    private void updateRecoveryCardState(boolean isLockupAlert, String customMessage) {
        if (recoveryCard == null || recoveryStatusText == null || recoveryActionButton == null) {
            return;
        }
        String borderHex = isLockupAlert ? "#F59E0B" : "#334155";
        recoveryCard.setBackground(createRoundedDrawable("#111827", borderHex, 18, 1));

        CameraRecoveryEngine.RecoveryPlan plan = CameraRecoveryEngine.buildRecoveryPlan(
                modelProfile, oneUiGeneration, lastLaunchedRearMode, recoveryAttemptIndex);

        recoveryActionButton.setText(resolveRecoveryButtonLabel(recoveryAttemptIndex));
        String buttonHex = (isLockupAlert || recoveryAttemptIndex > 0) ? "#D97706" : "#0284C7";
        recoveryActionButton.setBackground(createRoundedDrawable(buttonHex, buttonHex, 13, 0));

        if (customMessage != null && !customMessage.trim().isEmpty()) {
            recoveryStatusText.setText(customMessage);
        } else if (recoveryAttemptIndex > 0) {
            recoveryStatusText.setText(plan.title + " · " + plan.userMessage);
        } else {
            recoveryStatusText.setText("Si Samsung Camera se cierra o queda bloqueado por el sensor averiado, ejecuta la recuperación escalonada.");
        }
        recoveryStatusText.setTextColor(Color.parseColor(isLockupAlert ? "#FBBF24" : "#94A3B8"));
    }

    private static String resolveRecoveryButtonLabel(int attemptIndex) {
        switch (attemptIndex) {
            case 1:
                return "⚡ Destrabar → Modo Pro Alt.";
            case 2:
                return "⚡ Aislar Sensor 1x (QR)";
            case 3:
                return "⚙ Abrir App Info (Forzar Detención)";
            case 0:
            default:
                return "⚡ Destrabar Cámara → Modo Pro";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        RearCaptureService service = RearCaptureService.getInstance();
        if (service != null) {
            service.hideHud();
        }
        boolean lockupDetected = false;
        if (lastRearLaunchTimestampMs > 0L) {
            long launchTs = lastRearLaunchTimestampMs;
            lastRearLaunchTimestampMs = 0L;
            if (CameraRecoveryEngine.isSuspectedCrashReturn(launchTs, System.currentTimeMillis())) {
                lockupDetected = true;
                if (!recoveryAttemptAdvancedOnLaunch) {
                    recoveryAttemptIndex = CameraRecoveryEngine.nextAttemptIndex(recoveryAttemptIndex, false);
                }
                recoveryAttemptAdvancedOnLaunch = false;
                CameraRecoveryEngine.RecoveryPlan nextPlan = CameraRecoveryEngine.buildRecoveryPlan(
                        modelProfile, oneUiGeneration, lastLaunchedRearMode, recoveryAttemptIndex);
                updateRecoveryCardState(true, "Cierre rápido detectado. Siguiente paso: " + nextPlan.title);
                if (statusBanner != null) {
                    statusBanner.setTextColor(Color.parseColor("#F59E0B"));
                    statusBanner.setText("▲ Retorno rápido detectado · Usa Recuperación Rápida (" + nextPlan.title + ")");
                }
            } else {
                recoveryAttemptIndex = CameraRecoveryEngine.nextAttemptIndex(recoveryAttemptIndex, true);
                recoveryAttemptAdvancedOnLaunch = false;
                updateRecoveryCardState(false, null);
            }
        }
        if (pendingRecoveryRunnable == null) {
            ensureCameraPermissionAndStartViewerPreview();
        }
        if (lockupDetected && statusBanner != null) {
            statusBanner.setTextColor(Color.parseColor("#F59E0B"));
        }
    }

    @Override
    protected void onPause() {
        cancelCountdown(false);
        pendingVideoRecordAudioMode = null;
        currentCameraOpenedSuccessfully = false;
        universalCameraController.stop();
        updateRecordingUi(false, 0L, null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        recoveryHandler.removeCallbacksAndMessages(null);
        countdownHandler.removeCallbacksAndMessages(null);
        pendingRecoveryRunnable = null;
        countdownRunnable = null;
        super.onDestroy();
    }

    private void ensureCameraPermissionAndStartViewerPreview() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            universalCameraController.start(viewerPreviewView, getDisplayRotationCompat());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                universalCameraController.start(viewerPreviewView, getDisplayRotationCompat());
            } else {
                pendingVideoRecordAudioMode = null;
                onError("Permiso de cámara requerido para probar sensores y visor Camera2.");
            }
        } else if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (!pendingVideoRecordAfterAudioPrompt) {
                return;
            }
            pendingVideoRecordAfterAudioPrompt = false;
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            VideoRecordingState.AudioMode mode =
                    VideoRecordingState.resolveAudioModeAfterPermissionResult(granted, false);
            if (!granted) {
                audioPermissionDeniedThisSession = true;
                String msg = "Sin micrófono: grabando video MP4 sin audio";
                onStatusChanged(msg);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
            if (currentCameraOpenedSuccessfully) {
                universalCameraController.startVideoRecording(getDisplayRotationCompat(), mode);
            } else {
                pendingVideoRecordAudioMode = mode;
            }
        }
    }

    private int getDisplayRotationCompat() {
        if (getDisplay() != null) {
            return getDisplay().getRotation();
        }
        return Surface.ROTATION_0;
    }

    @Override
    public void onStatusChanged(String message) {
        if (statusBanner != null) {
            statusBanner.setTextColor(Color.parseColor("#34D399"));
            statusBanner.setText("● " + message);
        }
        if (message != null && message.startsWith("Activa:")) {
            currentCameraOpenedSuccessfully = true;
            if (pendingVideoRecordAudioMode != null) {
                VideoRecordingState.AudioMode mode = pendingVideoRecordAudioMode;
                pendingVideoRecordAudioMode = null;
                universalCameraController.startVideoRecording(getDisplayRotationCompat(), mode);
            }
        }
    }

    @Override
    public void onCapabilitiesChanged(UniversalCameraController.ViewerCapabilities capabilities) {
        if (capabilities == null) {
            return;
        }

        if (pendingApplySavedFlash) {
            pendingApplySavedFlash = false;
            if (capabilities.flashAvailable && viewerFlashMode != capabilities.activeFlashMode) {
                universalCameraController.setFlashMode(viewerFlashMode);
                return;
            }
        }
        if (pendingApplySavedAperture) {
            pendingApplySavedAperture = false;
            if (capabilities.apertureCaps != null
                    && capabilities.apertureCaps.variableApertureSupported
                    && viewerPreferredAperture > 0.0f
                    && Math.abs(capabilities.activeAperture - viewerPreferredAperture) > 0.05f) {
                universalCameraController.setAperture(viewerPreferredAperture);
                return;
            }
        }

        updateViewerCapabilityControls(capabilities);
    }

    @Override
    public void onPhotoSaved(MediaStoreSaver.SaveResult result) {
        String msg = "✓ Guardada en Galería: " + result.fileName;
        if (statusBanner != null) {
            statusBanner.setTextColor(Color.parseColor("#38BDF8"));
            statusBanner.setText(msg);
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onVideoRecordingStarted(VideoRecordingState.AudioMode audioMode) {
        updateRecordingUi(true, 0L, audioMode);
        updateViewerCapabilityControls(universalCameraController.getCapabilities());
    }

    @Override
    public void onVideoRecordingTick(long elapsedMs, VideoRecordingState.AudioMode audioMode) {
        updateRecordingUi(true, elapsedMs, audioMode);
    }

    @Override
    public void onVideoSaved(
            MediaStoreSaver.SaveResult result,
            long durationMs,
            VideoRecordingState.AudioMode audioMode
    ) {
        updateRecordingUi(false, 0L, audioMode);
        String duration = VideoRecordingState.formatElapsedDuration(durationMs);
        String suffix = (audioMode == VideoRecordingState.AudioMode.WITH_AUDIO) ? "" : " (sin audio)";
        String msg = "✓ Video MP4 guardado (" + duration + suffix + "): " + result.fileName;
        if (statusBanner != null) {
            statusBanner.setTextColor(Color.parseColor("#38BDF8"));
            statusBanner.setText(msg);
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onVideoRecordingAborted(String reason) {
        updateRecordingUi(false, 0L, null);
        if (statusBanner != null) {
            statusBanner.setTextColor(Color.parseColor("#FBBF24"));
            statusBanner.setText("▲ " + reason);
        }
        Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(String error) {
        pendingVideoRecordAudioMode = null;
        String activeId = (universalCameraController != null)
                ? universalCameraController.getCurrentCameraId()
                : CameraConfig.FRONT_CAMERA_WIDE_ID;
        if (!currentCameraOpenedSuccessfully
                && !isKnownFrontCameraId(activeId)
                && !CameraConfig.FRONT_CAMERA_WIDE_ID.equals(activeId)) {
            String fallbackMsg = "Sensor ID " + activeId + " no disponible en visor; volviendo a Frontal ID "
                    + CameraConfig.FRONT_CAMERA_WIDE_ID + ".";
            switchViewerToCameraId(CameraConfig.FRONT_CAMERA_WIDE_ID, true);
            if (statusBanner != null) {
                statusBanner.setTextColor(Color.parseColor("#FBBF24"));
                statusBanner.setText("▲ " + fallbackMsg);
            }
            Toast.makeText(this, fallbackMsg, Toast.LENGTH_LONG).show();
            return;
        }

        if (statusBanner != null) {
            statusBanner.setTextColor(Color.parseColor("#FCA5A5"));
            statusBanner.setText("▲ " + error);
        }
        if (CameraRecoveryEngine.isCameraErrorMessageLockup(error)) {
            updateRecoveryCardState(true, "Posible bloqueo HAL detectado: " + error + ". Usa Recuperación Rápida.");
        }
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}
