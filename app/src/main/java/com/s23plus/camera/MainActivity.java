package com.s23plus.camera;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Actividad principal de Samsung Camera Rescue (v1.3.0).
 *
 * Incluye:
 * 1. Motor de diagnóstico heurístico de modelos Samsung (SamsungModelDatabase).
 * 2. Escáner de sensores on-device (SensorScanner) con selector interactivo de lentes funcionales.
 * 3. Visor Camera2 en vivo para sensores frontales funcionales adaptativo según selección.
 * 4. Tarjetas dinámicas de rescate, accesos directos dinámicos/fijables y Quick Settings Tile.
 */
public final class MainActivity extends Activity implements FrontCameraController.StatusListener {

    private static final int REQUEST_CAMERA_PERMISSION = 1001;

    private TextureView frontPreviewView;
    private TextView statusBanner;
    private TextView lensOverlayBadge;
    private Button switchLensButton;
    private FrontCameraController frontCameraController;

    // Componentes del motor de diagnóstico y selector dinámico (v1.2.0 - v1.3.0)
    private SamsungModelDatabase.ModelProfile modelProfile;
    private SensorScanner sensorScanner;
    private TextView scanStatusText;
    private LinearLayout scanResultsContainer;
    private Button runScanButton;

    private List<DiscoveredSensor> lastScannedSensors = new ArrayList<>();
    private Set<String> selectedSensorIds = new LinkedHashSet<>();
    private List<RescueActionSpec> currentRearSpecs = new ArrayList<>();
    private List<DiscoveredSensor> activeFrontSensors = new ArrayList<>();
    private LinearLayout rearCardsContainer;
    private TextView shortcutsStatusView;
    private Button quickTileTargetButton;
    private int lastPublishedShortcutsCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);

        modelProfile = SamsungModelDatabase.lookup(Build.MODEL);
        sensorScanner = new SensorScanner(this);
        frontCameraController = new FrontCameraController(this, this);

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
            launchSamsungRearMode(CameraConfig.MODE_PRO_PHOTO, CameraConfig.ACTIVITY_NAME_PRO_PHOTO);
        } else if (CameraConfig.ACTION_LAUNCH_REAR_QR.equals(action)) {
            setIntent(new Intent(Intent.ACTION_MAIN));
            launchRearQrWithFloatingHud();
        } else if (CameraConfig.ACTION_LAUNCH_REAR_VIDEO.equals(action)) {
            setIntent(new Intent(Intent.ACTION_MAIN));
            launchSamsungRearMode(CameraConfig.MODE_PRO_VIDEO, CameraConfig.ACTIVITY_NAME_PRO_VIDEO);
        } else if (CameraConfig.ACTION_LAUNCH_FRONT.equals(action)) {
            String sensorId = intent.getStringExtra(CameraConfig.EXTRA_SHORTCUT_SENSOR_ID);
            setIntent(new Intent(Intent.ACTION_MAIN));
            if (sensorId != null && !sensorId.trim().isEmpty()) {
                String trimmedId = sensorId.trim();
                frontCameraController.switchToCameraId(trimmedId, frontPreviewView, getDisplayRotationCompat());
                updateFrontLensLabels(trimmedId);
                onStatusChanged("Atajo frontal activado (ID " + trimmedId + ")");
            } else {
                onStatusChanged("Atajo frontal activado");
            }
        }
    }

    private void buildModernUserInterface() {
        ScrollView rootScroll = new ScrollView(this);
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
        subtitleView.setText("Rescate de Sensores · " + modelProfile.marketingName);
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

        // 3. Tarjeta del Visor Frontal Camera2
        LinearLayout frontCard = new LinearLayout(this);
        frontCard.setOrientation(LinearLayout.VERTICAL);
        int cardPad = dpToPx(10);
        frontCard.setPadding(cardPad, cardPad, cardPad, cardPad);
        frontCard.setBackground(createRoundedDrawable("#111827", "#1F2937", 20, 1));

        FrameLayout previewFrame = new FrameLayout(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(280));
        previewFrame.setLayoutParams(previewParams);
        previewFrame.setBackground(createRoundedDrawable("#090D16", "#334155", 16, 1));

        frontPreviewView = new TextureView(this);
        FrameLayout.LayoutParams textureLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int innerMargin = dpToPx(3);
        textureLp.setMargins(innerMargin, innerMargin, innerMargin, innerMargin);
        frontPreviewView.setLayoutParams(textureLp);
        previewFrame.addView(frontPreviewView);

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

        frontCard.addView(previewFrame);

        // Controles cortos del visor frontal + botón desplegable (▾)
        LinearLayout frontControlsRow = new LinearLayout(this);
        frontControlsRow.setOrientation(LinearLayout.HORIZONTAL);
        frontControlsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsLp.topMargin = dpToPx(10);
        frontControlsRow.setLayoutParams(controlsLp);

        Button takeSelfieBtn = createShortButton(
                CameraConfig.BTN_FRONT_SHUTTER_SHORT,
                "#059669",
                "#FFFFFF",
                true
        );
        LinearLayout.LayoutParams selfieLp = new LinearLayout.LayoutParams(
                0, dpToPx(46), 1.15f);
        selfieLp.rightMargin = dpToPx(6);
        takeSelfieBtn.setLayoutParams(selfieLp);
        takeSelfieBtn.setOnClickListener(v -> frontCameraController.capturePhoto(getDisplayRotationCompat()));
        frontControlsRow.addView(takeSelfieBtn);

        switchLensButton = createShortButton(
                "Lente 8.6 MP",
                "#1E293B",
                "#7DD3FC",
                false
        );
        LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(
                0, dpToPx(46), 0.85f);
        switchLp.rightMargin = dpToPx(6);
        switchLensButton.setLayoutParams(switchLp);
        switchLensButton.setOnClickListener(v -> onSwitchFrontLensClicked());
        frontControlsRow.addView(switchLensButton);

        TextView frontTechView = createTechDescriptionView(CameraConfig.TECH_DESC_FRONT);
        Button frontArrowBtn = createArrowToggleButton(frontTechView);
        frontControlsRow.addView(frontArrowBtn);

        frontCard.addView(frontControlsRow);
        frontCard.addView(frontTechView);
        root.addView(frontCard);

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

        // 5. Tarjeta de Atajos Dinámicos y Quick Settings Tile (v1.3.0)
        root.addView(buildShortcutsAndTileCard());

        rootScroll.addView(root);
        setContentView(rootScroll);
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
        header.setText("ATAJOS Y ACCESO RÁPIDO (v1.3.0)");
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

        updateFrontControlsAfterSelection();

        List<RescueActionSpec> shortcutSpecs =
                DynamicUiBuilder.buildShortcutSpecs(modelProfile, lastScannedSensors, selectedSensorIds);
        lastPublishedShortcutsCount = RescueShortcutManager.updateDynamicShortcuts(this, shortcutSpecs);

        if (!shortcutSpecs.isEmpty()) {
            String savedTileAction = RescuePreferences.loadQuickTileAction(this);
            boolean actionStillActive = false;
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
        explanation.setText("Marca al menos un sensor trasero funcional en el diagnóstico superior para habilitar las tarjetas traseras, o usa el visor frontal Camera2.");
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
                launchSamsungRearMode(CameraConfig.MODE_PRO_PHOTO, CameraConfig.ACTIVITY_NAME_PRO_PHOTO);
                break;
            case REAR_QR_HUD:
                launchRearQrWithFloatingHud();
                break;
            case REAR_PRO_VIDEO:
                launchSamsungRearMode(CameraConfig.MODE_PRO_VIDEO, CameraConfig.ACTIVITY_NAME_PRO_VIDEO);
                break;
            case FRONT_CAMERA:
                if (spec.sensorId != null && !spec.sensorId.trim().isEmpty()) {
                    frontCameraController.switchToCameraId(spec.sensorId.trim(), frontPreviewView, getDisplayRotationCompat());
                    updateFrontLensLabels(spec.sensorId.trim());
                }
                break;
        }
    }

    private void updateFrontControlsAfterSelection() {
        if (lastScannedSensors.isEmpty() || switchLensButton == null || lensOverlayBadge == null) {
            return;
        }
        activeFrontSensors = DynamicUiBuilder.getSelectableFrontSensors(lastScannedSensors, selectedSensorIds);
        if (activeFrontSensors.isEmpty()) {
            switchLensButton.setEnabled(false);
            switchLensButton.setAlpha(0.55f);
            switchLensButton.setText("Sin frontal activa");
            lensOverlayBadge.setText("Frontal desactivada en checklist");
            return;
        }

        String currentId = frontCameraController.getCurrentCameraId();
        if (findSensorIndexById(activeFrontSensors, currentId) < 0) {
            String targetId = activeFrontSensors.get(0).id;
            if (frontPreviewView != null && frontPreviewView.isAvailable()) {
                frontCameraController.switchToCameraId(targetId, frontPreviewView, getDisplayRotationCompat());
            } else {
                frontCameraController.setCurrentCameraId(targetId);
            }
            currentId = targetId;
        }
        updateFrontLensLabels(currentId);
    }

    private void onSwitchFrontLensClicked() {
        if (!activeFrontSensors.isEmpty()) {
            if (activeFrontSensors.size() < 2) {
                return;
            }
            int idx = findSensorIndexById(activeFrontSensors, frontCameraController.getCurrentCameraId());
            DiscoveredSensor next = activeFrontSensors.get((idx + 1) % activeFrontSensors.size());
            frontCameraController.switchToCameraId(next.id, frontPreviewView, getDisplayRotationCompat());
            updateFrontLensLabels(next.id);
            return;
        }
        frontCameraController.switchFrontCamera(frontPreviewView, getDisplayRotationCompat());
        updateFrontLensLabels(frontCameraController.getCurrentCameraId());
    }

    private void updateFrontLensLabels(String activeId) {
        if (switchLensButton == null || lensOverlayBadge == null) {
            return;
        }
        if (!activeFrontSensors.isEmpty()) {
            if (activeFrontSensors.size() == 1) {
                DiscoveredSensor only = activeFrontSensors.get(0);
                switchLensButton.setEnabled(false);
                switchLensButton.setAlpha(0.65f);
                switchLensButton.setText("1 lente frontal");
                lensOverlayBadge.setText("Frontal ID " + only.id + " · " + only.getResolutionMegaPixels());
                return;
            }
            int idx = findSensorIndexById(activeFrontSensors, activeId);
            if (idx < 0) {
                idx = 0;
            }
            DiscoveredSensor current = activeFrontSensors.get(idx);
            DiscoveredSensor next = activeFrontSensors.get((idx + 1) % activeFrontSensors.size());
            switchLensButton.setEnabled(true);
            switchLensButton.setAlpha(1.0f);
            String nextMp = next.getResolutionMegaPixels();
            switchLensButton.setText("N/D".equals(nextMp) ? "Lente ID " + next.id : "Lente " + nextMp);
            lensOverlayBadge.setText("Frontal ID " + current.id + " · " + current.getResolutionMegaPixels());
            return;
        }

        switchLensButton.setEnabled(true);
        switchLensButton.setAlpha(1.0f);
        if (CameraConfig.FRONT_CAMERA_WIDE_ID.equals(activeId)) {
            switchLensButton.setText("Lente 8.6 MP");
            lensOverlayBadge.setText("Frontal ID 1 · 12 MP");
        } else if (CameraConfig.FRONT_CAMERA_NORMAL_ID.equals(activeId)) {
            switchLensButton.setText("Lente 12 MP");
            lensOverlayBadge.setText("Frontal ID 3 · 8.6 MP");
        } else {
            switchLensButton.setText("Cambiar Lente");
            lensOverlayBadge.setText("Frontal ID " + activeId);
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

    private void cycleQuickTileTarget() {
        List<RescueActionSpec> available =
                DynamicUiBuilder.buildShortcutSpecs(modelProfile, lastScannedSensors, selectedSensorIds);
        if (available.isEmpty()) {
            Toast.makeText(this, "Activa al menos un sensor para configurar el Tile Rápido", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentAction = RescuePreferences.loadQuickTileAction(this);
        int currentIndex = -1;
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i).intentAction.equals(currentAction)) {
                currentIndex = i;
                break;
            }
        }
        RescueActionSpec next = available.get((currentIndex + 1) % available.size());
        String nextLabel = resolveShortcutLabel(next);
        RescuePreferences.saveQuickTileAction(this, next.intentAction, nextLabel);
        refreshShortcutsStatusViews();
        Toast.makeText(this, "Tile Rápido actualizado a: " + nextLabel, Toast.LENGTH_SHORT).show();
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
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.parseColor(fgHex));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", bold ? Typeface.BOLD : Typeface.NORMAL));
        button.setBackground(createRoundedDrawable(bgHex, bgHex, 13, 0));
        button.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
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

    private void launchSamsungRearMode(String previousMode, String activityName) {
        frontCameraController.closeCameraOnly();
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName(
                    CameraConfig.SAMSUNG_CAMERA_PACKAGE,
                    CameraConfig.SAMSUNG_CAMERA_ACTIVITY
            ));
            intent.putExtra(CameraConfig.EXTRA_PREVIOUS_MODE, previousMode);
            intent.putExtra(CameraConfig.EXTRA_ACTIVITY_NAME, activityName);
            intent.putExtra(CameraConfig.EXTRA_CAMERA_FACING, CameraConfig.FACING_BACK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } catch (RuntimeException e) {
            onError("No se pudo abrir Samsung Camera en modo " + previousMode + ": " + e.getMessage());
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

        frontCameraController.closeCameraOnly();
        service.showHud();

        try {
            Intent qrIntent = new Intent(CameraConfig.ACTION_SCAN_QR_CODE);
            qrIntent.setPackage(CameraConfig.SAMSUNG_CAMERA_PACKAGE);
            qrIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(qrIntent);
        } catch (RuntimeException e) {
            onError("No se pudo iniciar SCAN_QR_CODE: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        RearCaptureService service = RearCaptureService.getInstance();
        if (service != null) {
            service.hideHud();
        }
        ensureCameraPermissionAndStartFrontPreview();
    }

    @Override
    protected void onPause() {
        frontCameraController.stop();
        super.onPause();
    }

    private void ensureCameraPermissionAndStartFrontPreview() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            frontCameraController.start(frontPreviewView, getDisplayRotationCompat());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                frontCameraController.start(frontPreviewView, getDisplayRotationCompat());
            } else {
                onError("Permiso de cámara requerido para probar sensores y visor frontal.");
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
    public void onError(String error) {
        if (statusBanner != null) {
            statusBanner.setTextColor(Color.parseColor("#FCA5A5"));
            statusBanner.setText("▲ " + error);
        }
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }
}
