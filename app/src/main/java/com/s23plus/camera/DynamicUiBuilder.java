package com.s23plus.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Motor puro Java para generar dinámicamente tarjetas de acción y accesos directos
 * a partir del perfil del dispositivo, sensores descubiertos y selección del usuario.
 */
public final class DynamicUiBuilder {

    private DynamicUiBuilder() {
        // Clase de utilidad no instanciable
    }

    /**
     * Genera las especificaciones de tarjetas traseras en modo pre-escaneo (fallback de perfil)
     * o post-escaneo (basado en salud y selección del usuario).
     */
    public static List<RescueActionSpec> buildRearActionSpecs(
            SamsungModelDatabase.ModelProfile profile,
            List<DiscoveredSensor> discoveredSensors,
            Set<String> selectedSensorIds
    ) {
        SamsungModelDatabase.ModelProfile effectiveProfile =
                (profile != null) ? profile : SamsungModelDatabase.lookup(null);

        if (discoveredSensors == null || discoveredSensors.isEmpty()) {
            return buildPreScanRearSpecs(effectiveProfile);
        }

        Set<String> effectiveSelected =
                SensorSelectionState.sanitizeSelection(selectedSensorIds, discoveredSensors);

        List<DiscoveredSensor> selectedRear = new ArrayList<>();
        for (DiscoveredSensor sensor : discoveredSensors) {
            if (sensor.facing == 0
                    && SensorSelectionState.isSelectable(sensor)
                    && effectiveSelected.contains(sensor.id)) {
                selectedRear.add(sensor);
            }
        }

        if (selectedRear.isEmpty()) {
            return Collections.emptyList();
        }

        String mainId = effectiveProfile.rearMainStandaloneId;
        String teleId = effectiveProfile.rearTeleStandaloneId;
        boolean hasTeleId = isNonBlank(teleId);
        boolean teleSelected = hasTeleId && containsSensorId(selectedRear, teleId);
        boolean mainExplicitlySelected = isNonBlank(mainId) && containsSensorId(selectedRear, mainId);
        String firstNonTeleId = resolveFirstNonTeleId(selectedRear, teleId);
        boolean mainSelected = mainExplicitlySelected
                || (firstNonTeleId != null && !(selectedRear.size() == 1 && teleSelected));

        String activeMainOrFirstId = mainExplicitlySelected
                ? mainId
                : (firstNonTeleId != null ? firstNonTeleId : selectedRear.get(0).id);

        String lensSummary = buildLensSummary(
                mainSelected, teleSelected, activeMainOrFirstId, teleId, selectedRear);
        String primarySensorId = mainSelected ? activeMainOrFirstId : selectedRear.get(0).id;

        List<RescueActionSpec> specs = new ArrayList<>();
        specs.add(buildProPhotoSpec(
                primarySensorId,
                "Activo · " + lensSummary + " · ISP Nativo",
                buildProPhotoTechDesc(mainSelected, activeMainOrFirstId, teleSelected, teleId, selectedRear)
        ));
        if (mainSelected) {
            specs.add(buildQrHudSpec(activeMainOrFirstId));
        }
        specs.add(buildProVideoSpec(
                primarySensorId,
                "Video Pro · " + lensSummary,
                buildProVideoTechDesc(mainSelected, activeMainOrFirstId, teleSelected, teleId, selectedRear)
        ));
        return Collections.unmodifiableList(specs);
    }

    /**
     * Retorna los sensores frontales elegibles y seleccionados en el orden descubierto.
     */
    public static List<DiscoveredSensor> getSelectableFrontSensors(
            List<DiscoveredSensor> discoveredSensors,
            Set<String> selectedSensorIds
    ) {
        if (discoveredSensors == null || discoveredSensors.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> effectiveSelected =
                SensorSelectionState.sanitizeSelection(selectedSensorIds, discoveredSensors);

        List<DiscoveredSensor> frontSensors = new ArrayList<>();
        for (DiscoveredSensor sensor : discoveredSensors) {
            if (sensor.facing == 1
                    && SensorSelectionState.isSelectable(sensor)
                    && effectiveSelected.contains(sensor.id)) {
                frontSensors.add(sensor);
            }
        }
        return Collections.unmodifiableList(frontSensors);
    }

    /**
     * Retorna los sensores saludables y seleccionados (frontales y traseros públicos) aptos para el visor Camera2.
     */
    public static List<DiscoveredSensor> getSelectablePublicViewerSensors(
            List<DiscoveredSensor> discoveredSensors,
            Set<String> selectedSensorIds
    ) {
        if (discoveredSensors == null || discoveredSensors.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> effectiveSelected =
                SensorSelectionState.sanitizeSelection(selectedSensorIds, discoveredSensors);

        List<DiscoveredSensor> viewerSensors = new ArrayList<>();
        for (DiscoveredSensor sensor : discoveredSensors) {
            if (sensor != null
                    && sensor.health == SensorHealth.HEALTHY
                    && (sensor.facing == 0 || sensor.facing == 1)
                    && effectiveSelected.contains(sensor.id)) {
                viewerSensors.add(sensor);
            }
        }
        return Collections.unmodifiableList(viewerSensors);
    }

    /**
     * Retorna únicamente los sensores traseros públicos saludables y seleccionados para el visor Camera2.
     */
    public static List<DiscoveredSensor> getSelectablePublicRearSensors(
            List<DiscoveredSensor> discoveredSensors,
            Set<String> selectedSensorIds
    ) {
        List<DiscoveredSensor> viewerSensors =
                getSelectablePublicViewerSensors(discoveredSensors, selectedSensorIds);
        if (viewerSensors.isEmpty()) {
            return Collections.emptyList();
        }

        List<DiscoveredSensor> rearSensors = new ArrayList<>();
        for (DiscoveredSensor sensor : viewerSensors) {
            if (sensor.facing == 0) {
                rearSensors.add(sensor);
            }
        }
        return Collections.unmodifiableList(rearSensors);
    }

    /**
     * Genera las especificaciones para los accesos directos dinámicos del lanzador (máximo 4).
     */
    public static List<RescueActionSpec> buildShortcutSpecs(
            SamsungModelDatabase.ModelProfile profile,
            List<DiscoveredSensor> discoveredSensors,
            Set<String> selectedSensorIds
    ) {
        SamsungModelDatabase.ModelProfile effectiveProfile =
                (profile != null) ? profile : SamsungModelDatabase.lookup(null);

        List<RescueActionSpec> specs = new ArrayList<>(
                buildRearActionSpecs(effectiveProfile, discoveredSensors, selectedSensorIds));

        String frontId = null;
        if (discoveredSensors == null || discoveredSensors.isEmpty()) {
            frontId = isNonBlank(effectiveProfile.frontWideId)
                    ? effectiveProfile.frontWideId
                    : CameraConfig.FRONT_CAMERA_WIDE_ID;
        } else {
            List<DiscoveredSensor> frontSensors =
                    getSelectableFrontSensors(discoveredSensors, selectedSensorIds);
            if (!frontSensors.isEmpty()) {
                frontId = frontSensors.get(0).id;
            }
        }

        if (isNonBlank(frontId)) {
            specs.add(new RescueActionSpec(
                    "front_selfie",
                    RescueActionSpec.ActionType.FRONT_CAMERA,
                    frontId,
                    CameraConfig.BTN_FRONT_SHUTTER_SHORT,
                    "Visor Camera2 Directo (ID " + frontId + ")",
                    "#059669",
                    "#34D399",
                    CameraConfig.TECH_DESC_FRONT,
                    "Tomar Selfie",
                    "Abrir Visor Frontal Camera2",
                    CameraConfig.ACTION_LAUNCH_FRONT
            ));
        }

        if (specs.size() > CameraConfig.MAX_DYNAMIC_SHORTCUTS) {
            specs = new ArrayList<>(specs.subList(0, CameraConfig.MAX_DYNAMIC_SHORTCUTS));
        }
        return Collections.unmodifiableList(specs);
    }

    private static List<RescueActionSpec> buildPreScanRearSpecs(SamsungModelDatabase.ModelProfile profile) {
        String mainId = isNonBlank(profile.rearMainStandaloneId)
                ? profile.rearMainStandaloneId
                : CameraConfig.REAR_MAIN_STANDALONE_ID;
        String teleId = profile.rearTeleStandaloneId;
        boolean hasTele = isNonBlank(teleId);

        String proSubtitle = hasTele
                ? "Recomendado · Lentes 1x (ID " + mainId + ") y Tele (ID " + teleId + ") · ISP Nativo"
                : "Recomendado · Lente 1x Principal (ID " + mainId + ") · ISP Nativo";

        String videoSubtitle = hasTele
                ? "Grabación 1x y Tele con Estabilización y Audio"
                : "Grabación 1x Principal con Estabilización y Audio";

        List<RescueActionSpec> specs = new ArrayList<>(3);
        specs.add(buildProPhotoSpec(
                mainId,
                proSubtitle,
                buildProPhotoTechDesc(true, mainId, hasTele, teleId, null)
        ));
        specs.add(buildQrHudSpec(mainId));
        specs.add(buildProVideoSpec(
                mainId,
                videoSubtitle,
                buildProVideoTechDesc(true, mainId, hasTele, teleId, null)
        ));
        return Collections.unmodifiableList(specs);
    }

    private static RescueActionSpec buildProPhotoSpec(String sensorId, String subtitle, String techDesc) {
        return new RescueActionSpec(
                "rear_pro_photo",
                RescueActionSpec.ActionType.REAR_PRO_PHOTO,
                sensorId,
                CameraConfig.BTN_REAR_PRO_SHORT,
                subtitle,
                "#0284C7",
                "#38BDF8",
                techDesc,
                "Trasera Pro",
                "Abrir Cámara Trasera Pro",
                CameraConfig.ACTION_LAUNCH_REAR_PRO
        );
    }

    private static RescueActionSpec buildQrHudSpec(String mainSensorId) {
        String techDesc = "Invoca el Intent exportado com.sec.android.app.camera.action.SCAN_QR_CODE (ShootingMode 40).\n"
                + "• Fuerza la apertura exclusiva del pipeline independiente ID " + mainSensorId + " (1x, numPhysicalCameras: 1).\n"
                + "• Superpone el HUD flotante de RearCaptureService para capturar el visor óptico limpio y guardarlo en Pictures/S23Camera.";
        return new RescueActionSpec(
                "rear_qr_hud",
                RescueActionSpec.ActionType.REAR_QR_HUD,
                mainSensorId,
                CameraConfig.BTN_REAR_QR_SHORT,
                "Obturador Flotante Rescue · Lente 1x (ID " + mainSensorId + ")",
                "#4F46E5",
                "#818CF8",
                techDesc,
                "Trasera QR",
                "Abrir Trasera Rápida (QR)",
                CameraConfig.ACTION_LAUNCH_REAR_QR
        );
    }

    private static RescueActionSpec buildProVideoSpec(String sensorId, String subtitle, String techDesc) {
        return new RescueActionSpec(
                "rear_pro_video",
                RescueActionSpec.ActionType.REAR_PRO_VIDEO,
                sensorId,
                CameraConfig.BTN_REAR_VIDEO_SHORT,
                subtitle,
                "#1E293B",
                "#94A3B8",
                techDesc,
                "Video Pro",
                "Abrir Video Trasero Pro",
                CameraConfig.ACTION_LAUNCH_REAR_VIDEO
        );
    }

    private static String buildLensSummary(
            boolean mainSelected,
            boolean teleSelected,
            String activeMainOrFirstId,
            String teleId,
            List<DiscoveredSensor> selectedRear
    ) {
        if (mainSelected && teleSelected) {
            return "Lentes 1x (ID " + activeMainOrFirstId + ") y Tele (ID " + teleId + ")";
        } else if (mainSelected) {
            return "Lente 1x (ID " + activeMainOrFirstId + ")";
        } else if (teleSelected) {
            return "Solo Tele (ID " + teleId + ")";
        }
        return "Sensores (ID " + joinSensorIds(selectedRear) + ")";
    }

    private static String buildProPhotoTechDesc(
            boolean mainActive,
            String mainId,
            boolean teleActive,
            String teleId,
            List<DiscoveredSensor> selectedRear
    ) {
        String targetLine;
        if (mainActive && teleActive) {
            targetLine = "• Abre directamente el sensor principal 1x (ID " + mainId + ") y Tele (ID " + teleId + ").\n";
        } else if (mainActive) {
            targetLine = "• Abre directamente el sensor principal 1x (ID " + mainId + ").\n";
        } else if (teleActive) {
            targetLine = "• Abre directamente el sensor Tele (ID " + teleId + ").\n";
        } else {
            targetLine = "• Abre directamente los sensores seleccionados (ID " + joinSensorIds(selectedRear) + ").\n";
        }
        return "Inicia Samsung Camera en ShootingMode 3 (Pro) con FLAG_ACTIVITY_CLEAR_TASK.\n"
                + targetLine
                + "• Evita el grupo lógico multi-cámara, saltándose sensores inactivos o dañados.\n"
                + "• Conserva autoenfoque, flash, controles ISO/Speed en AUTO y disparo ISP en DCIM/Camera.";
    }

    private static String buildProVideoTechDesc(
            boolean mainActive,
            String mainId,
            boolean teleActive,
            String teleId,
            List<DiscoveredSensor> selectedRear
    ) {
        String targetLine;
        if (mainActive && teleActive) {
            targetLine = "• Habilita grabación de video con estabilización y audio sobre los sensores independientes ID "
                    + mainId + " (1x) e ID " + teleId + " (Tele).\n";
        } else if (mainActive) {
            targetLine = "• Habilita grabación de video con estabilización y audio sobre el sensor independiente ID "
                    + mainId + " (1x Principal).\n";
        } else if (teleActive) {
            targetLine = "• Habilita grabación de video con estabilización y audio sobre el sensor independiente ID "
                    + teleId + " (Tele).\n";
        } else {
            targetLine = "• Habilita grabación de video con estabilización y audio sobre los sensores seleccionados (ID "
                    + joinSensorIds(selectedRear) + ").\n";
        }
        return "Inicia Samsung Camera en ShootingMode 36 (Pro video) con FLAG_ACTIVITY_CLEAR_TASK.\n"
                + targetLine
                + "• Evita por completo el grupo lógico multi-cámara y los sensores excluidos o averiados.";
    }

    private static boolean containsSensorId(List<DiscoveredSensor> sensors, String id) {
        if (id == null) {
            return false;
        }
        for (DiscoveredSensor sensor : sensors) {
            if (id.equals(sensor.id)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveFirstNonTeleId(List<DiscoveredSensor> selectedRear, String teleId) {
        boolean hasTele = isNonBlank(teleId);
        for (DiscoveredSensor sensor : selectedRear) {
            if (sensor.id != null && (!hasTele || !teleId.equals(sensor.id))) {
                return sensor.id;
            }
        }
        return null;
    }

    private static String joinSensorIds(List<DiscoveredSensor> sensors) {
        if (sensors == null || sensors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sensors.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(sensors.get(i).id);
        }
        return sb.toString();
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
