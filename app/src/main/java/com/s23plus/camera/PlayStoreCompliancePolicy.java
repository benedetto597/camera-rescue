package com.s23plus.camera;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Política pura Java de auditoría técnica para cumplimiento con Google Play Store (v2.1.0):
 * - Título libre de marcas registradas y <= 30 caracteres.
 * - applicationId reverse-DNS en minúsculas sin marcas registradas.
 * - API objetivo >= 36, minSdk >= 30, allowBackup = false, cero AccessibilityService.
 * - Permisos estrictamente acotados a CAMERA, RECORD_AUDIO y BIND_QUICK_SETTINGS_TILE (cero INTERNET).
 * - Política de privacidad HTTPS, aviso de independencia y rutas MediaStore neutrales.
 *
 * Clase 100% Java pura (sin dependencias de android.*).
 */
public final class PlayStoreCompliancePolicy {

    public static final int MAX_PLAY_TITLE_LENGTH = 30;
    public static final int MIN_REQUIRED_PLAY_TARGET_SDK = 36;
    public static final int MIN_SUPPORTED_SDK = 30;

    public static final Set<String> FORBIDDEN_PERMISSIONS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "android.permission.INTERNET",
                    "android.permission.ACCESS_NETWORK_STATE",
                    "android.permission.BIND_ACCESSIBILITY_SERVICE",
                    "android.permission.SYSTEM_ALERT_WINDOW",
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.MANAGE_EXTERNAL_STORAGE",
                    "android.permission.READ_MEDIA_IMAGES",
                    "android.permission.READ_MEDIA_VIDEO",
                    "android.permission.QUERY_ALL_PACKAGES",
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION"
            ))
    );

    public static final Set<String> ALLOWED_PERMISSIONS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "android.permission.CAMERA",
                    "android.permission.RECORD_AUDIO",
                    "android.permission.BIND_QUICK_SETTINGS_TILE"
            ))
    );

    private static final List<String> TRADEMARK_TITLE_PREFIXES = Arrays.asList(
            "samsung",
            "galaxy",
            "android",
            "google",
            "official"
    );

    private static final Set<String> TRADEMARK_PACKAGE_SEGMENTS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "s23plus",
                    "samsung",
                    "galaxy",
                    "android",
                    "google"
            ))
    );

    private static final Pattern APPLICATION_ID_PATTERN =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){2,}$");

    /**
     * Instantánea inmutable de metadatos del manifiesto y configuración de publicación.
     */
    public static final class ManifestSnapshot {
        public final String applicationId;
        public final String appTitle;
        public final int minSdk;
        public final int targetSdk;
        public final int versionCode;
        public final String versionName;
        public final boolean allowBackup;
        public final boolean hasAccessibilityService;
        public final Set<String> declaredPermissions;
        public final String privacyPolicyUrl;
        public final String trademarkDisclaimer;
        public final String mediaRelativePath;
        public final String videoRelativePath;

        public ManifestSnapshot(
                String applicationId,
                String appTitle,
                int minSdk,
                int targetSdk,
                int versionCode,
                String versionName,
                boolean allowBackup,
                boolean hasAccessibilityService,
                Collection<String> declaredPermissions,
                String privacyPolicyUrl,
                String trademarkDisclaimer,
                String mediaRelativePath,
                String videoRelativePath
        ) {
            this.applicationId = applicationId;
            this.appTitle = appTitle;
            this.minSdk = minSdk;
            this.targetSdk = targetSdk;
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.allowBackup = allowBackup;
            this.hasAccessibilityService = hasAccessibilityService;
            LinkedHashSet<String> normalizedPerms = new LinkedHashSet<>();
            if (declaredPermissions != null) {
                for (String perm : declaredPermissions) {
                    if (perm != null && !perm.trim().isEmpty()) {
                        normalizedPerms.add(perm.trim());
                    }
                }
            }
            this.declaredPermissions = Collections.unmodifiableSet(normalizedPerms);
            this.privacyPolicyUrl = privacyPolicyUrl;
            this.trademarkDisclaimer = trademarkDisclaimer;
            this.mediaRelativePath = mediaRelativePath;
            this.videoRelativePath = videoRelativePath;
        }
    }

    /**
     * Resultado inmutable de auditoría de cumplimiento.
     */
    public static final class ComplianceReport {
        public final boolean compliant;
        public final List<String> violations;

        public ComplianceReport(boolean compliant, List<String> violations) {
            this.compliant = compliant;
            if (violations == null || violations.isEmpty()) {
                this.violations = Collections.emptyList();
            } else {
                this.violations = Collections.unmodifiableList(new ArrayList<>(violations));
            }
        }
    }

    /**
     * Resumen offline inmutable de Data Safety para UI y documentación técnica.
     */
    public static final class DataSafetySummary {
        public final boolean collectsUserData;
        public final boolean sharesDataWithThirdParties;
        public final boolean usesInternetPermission;
        public final boolean usesAccessibilityApi;
        public final String localMediaPaths;
        public final String permissionsSummary;
        public final String privacyPolicyUrl;
        public final String trademarkDisclaimer;

        public DataSafetySummary(
                boolean collectsUserData,
                boolean sharesDataWithThirdParties,
                boolean usesInternetPermission,
                boolean usesAccessibilityApi,
                String localMediaPaths,
                String permissionsSummary,
                String privacyPolicyUrl,
                String trademarkDisclaimer
        ) {
            this.collectsUserData = collectsUserData;
            this.sharesDataWithThirdParties = sharesDataWithThirdParties;
            this.usesInternetPermission = usesInternetPermission;
            this.usesAccessibilityApi = usesAccessibilityApi;
            this.localMediaPaths = localMediaPaths != null ? localMediaPaths : "";
            this.permissionsSummary = permissionsSummary != null ? permissionsSummary : "";
            this.privacyPolicyUrl = privacyPolicyUrl != null ? privacyPolicyUrl : "";
            this.trademarkDisclaimer = trademarkDisclaimer != null ? trademarkDisclaimer : "";
        }
    }

    private PlayStoreCompliancePolicy() {
    }

    /**
     * Valida el título público de Play Store (no nulo/blanco, <= 30 chars, sin marcas registradas).
     */
    public static List<String> validateAppTitle(String title) {
        List<String> violations = new ArrayList<>();
        if (title == null || title.trim().isEmpty()) {
            violations.add("El título de la aplicación no puede ser nulo ni estar en blanco.");
            return violations;
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_PLAY_TITLE_LENGTH) {
            violations.add("El título excede el límite de Play Store (" + MAX_PLAY_TITLE_LENGTH + " caracteres): " + trimmed.length());
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String[] tokens = lower.split("[^a-z0-9]+");
        for (String prefix : TRADEMARK_TITLE_PREFIXES) {
            if (lower.startsWith(prefix) || containsToken(tokens, prefix)) {
                violations.add("El título contiene término o prefijo de marca protegida: " + prefix);
            }
        }
        return violations;
    }

    /**
     * Valida el applicationId público (reverse-DNS en minúsculas, >= 3 segmentos, sin marcas registradas).
     */
    public static List<String> validateApplicationId(String applicationId) {
        List<String> violations = new ArrayList<>();
        if (applicationId == null || applicationId.trim().isEmpty()) {
            violations.add("applicationId no puede ser nulo ni estar en blanco.");
            return violations;
        }
        String trimmed = applicationId.trim();
        if (!trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
            violations.add("applicationId debe estar completamente en minúsculas: " + trimmed);
        }
        if (!APPLICATION_ID_PATTERN.matcher(trimmed).matches()) {
            violations.add("applicationId debe tener formato reverse-DNS válido con al menos 3 segmentos: " + trimmed);
        }
        String[] segments = trimmed.toLowerCase(Locale.ROOT).split("\\.");
        for (String segment : segments) {
            if (TRADEMARK_PACKAGE_SEGMENTS.contains(segment)) {
                violations.add("applicationId contiene segmento de marca o legado no permitido: " + segment);
            }
        }
        return violations;
    }

    /**
     * Valida que la URL de Política de Privacidad use HTTPS y un host válido.
     */
    public static List<String> validatePrivacyPolicyUrl(String url) {
        List<String> violations = new ArrayList<>();
        if (url == null || url.trim().isEmpty()) {
            violations.add("La URL de Política de Privacidad no puede ser nula ni estar en blanco.");
            return violations;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("https://")) {
            violations.add("La URL de Política de Privacidad debe usar HTTPS: " + trimmed);
            return violations;
        }
        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty() || !host.contains(".")) {
                violations.add("La URL de Política de Privacidad no contiene un host válido: " + trimmed);
            }
        } catch (URISyntaxException e) {
            violations.add("La URL de Política de Privacidad tiene sintaxis inválida: " + trimmed);
        }
        return violations;
    }

    /**
     * Valida el aviso legal de independencia respecto a Samsung Electronics y compatibilidad descriptiva.
     */
    public static List<String> validateDisclaimer(String disclaimer) {
        List<String> violations = new ArrayList<>();
        if (disclaimer == null || disclaimer.trim().isEmpty()) {
            violations.add("El aviso legal de independencia no puede ser nulo ni estar en blanco.");
            return violations;
        }
        String lower = disclaimer.trim().toLowerCase(Locale.ROOT);
        if (!lower.contains("independiente") && !lower.contains("independent")) {
            violations.add("El aviso legal debe indicar que la herramienta es independiente.");
        }
        if (!lower.contains("afiliada") && !lower.contains("affiliated")) {
            violations.add("El aviso legal debe declarar ausencia de afiliación.");
        }
        if (!lower.contains("samsung")) {
            violations.add("El aviso legal debe identificar explícitamente a Samsung Electronics para el uso descriptivo.");
        }
        if (!lower.contains("descriptiv") && !lower.contains("compatibil")) {
            violations.add("El aviso legal debe aclarar el uso únicamente descriptivo de compatibilidad de hardware.");
        }
        return violations;
    }

    /**
     * Ejecuta la auditoría integral de cumplimiento Play Store sobre una instantánea del manifiesto.
     */
    public static ComplianceReport audit(ManifestSnapshot snapshot) {
        if (snapshot == null) {
            return new ComplianceReport(false, Collections.singletonList("ManifestSnapshot no puede ser nulo."));
        }

        List<String> violations = new ArrayList<>();
        violations.addAll(validateAppTitle(snapshot.appTitle));
        violations.addAll(validateApplicationId(snapshot.applicationId));

        if (snapshot.minSdk < MIN_SUPPORTED_SDK) {
            violations.add("minSdk debe ser >= " + MIN_SUPPORTED_SDK + " (actual: " + snapshot.minSdk + ").");
        }
        if (snapshot.targetSdk < MIN_REQUIRED_PLAY_TARGET_SDK) {
            violations.add("targetSdk debe ser >= " + MIN_REQUIRED_PLAY_TARGET_SDK + " (actual: " + snapshot.targetSdk + ").");
        }
        if (snapshot.minSdk > snapshot.targetSdk) {
            violations.add("minSdk (" + snapshot.minSdk + ") no puede superar a targetSdk (" + snapshot.targetSdk + ").");
        }
        if (snapshot.versionCode <= 0) {
            violations.add("versionCode debe ser positivo (actual: " + snapshot.versionCode + ").");
        }
        if (snapshot.versionName == null || snapshot.versionName.trim().isEmpty()) {
            violations.add("versionName no puede estar en blanco.");
        }
        if (snapshot.allowBackup) {
            violations.add("android:allowBackup debe ser false para evitar copias automáticas no cifradas.");
        }
        if (snapshot.hasAccessibilityService) {
            violations.add("La arquitectura cero-accesibilidad prohíbe declarar AccessibilityService.");
        }

        for (String perm : snapshot.declaredPermissions) {
            if (FORBIDDEN_PERMISSIONS.contains(perm)) {
                violations.add("Permiso prohibido declarado: " + perm);
            } else if (!ALLOWED_PERMISSIONS.contains(perm)) {
                violations.add("Permiso no autorizado fuera de la lista permitida: " + perm);
            }
        }

        violations.addAll(validatePrivacyPolicyUrl(snapshot.privacyPolicyUrl));
        violations.addAll(validateDisclaimer(snapshot.trademarkDisclaimer));

        auditMediaPath(snapshot.mediaRelativePath, "Pictures/", "mediaRelativePath", violations);
        auditMediaPath(snapshot.videoRelativePath, "Movies/", "videoRelativePath", violations);

        return new ComplianceReport(violations.isEmpty(), violations);
    }

    /**
     * Construye el resumen determinista 100% offline para la sección Data Safety y la tarjeta legal de la app.
     */
    public static DataSafetySummary buildOfflineDataSafetySummary() {
        return new DataSafetySummary(
                false,
                false,
                false,
                false,
                CameraConfig.MEDIA_RELATIVE_PATH + " · " + CameraConfig.VIDEO_RELATIVE_PATH,
                "CAMERA, RECORD_AUDIO (opcional en grabación de video), BIND_QUICK_SETTINGS_TILE (sin INTERNET ni Accesibilidad)",
                CameraConfig.PRIVACY_POLICY_URL,
                CameraConfig.TRADEMARK_DISCLAIMER
        );
    }

    private static void auditMediaPath(String path, String requiredPrefix, String fieldName, List<String> violations) {
        if (path == null || path.trim().isEmpty()) {
            violations.add(fieldName + " no puede estar en blanco.");
            return;
        }
        String trimmed = path.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("s23camera") || lower.contains("samsung") || lower.contains("galaxy")) {
            violations.add(fieldName + " contiene ruta legada o de marca no permitida: " + trimmed);
        }
        if (!trimmed.startsWith(requiredPrefix)) {
            violations.add(fieldName + " debe iniciar con " + requiredPrefix + " (actual: " + trimmed + ").");
        }
    }

    private static boolean containsToken(String[] tokens, String target) {
        for (String token : tokens) {
            if (target.equals(token)) {
                return true;
            }
        }
        return false;
    }
}
