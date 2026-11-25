# Política de Seguridad y Privacidad (`SECURITY.md`)

> **Aviso de Independencia:** *Rescue Cam: Sensor Recovery (`io.github.benedetto597.rescuecam`) es una herramienta de utilidad independiente sin afiliación ni respaldo de Samsung Electronics Co., Ltd.*

## 1. Principios de Diseño Seguro (`v2.1.0`)

`Rescue Cam: Sensor Recovery` está diseñada bajo los principios de **Mínimo Privilegio**, **Arquitectura Cero-Accesibilidad** y **Privacidad por Diseño**:

1. **100% Offline (Sin Permiso de Red):**
   - `AndroidManifest.xml` **NO solicita `android.permission.INTERNET`**, `ACCESS_NETWORK_STATE`, ni permisos de ubicación.
   - Es técnicamente imposible para la aplicación transmitir imágenes, videos, diagnósticos o telemetría fuera del dispositivo (`No data collected`, `No data shared`).
2. **Arquitectura Cero-Accesibilidad (Sin `BIND_ACCESSIBILITY_SERVICE` ni Overlays):**
   - A partir de `v2.1.0`, la aplicación opera exclusivamente mediante APIs estándar de cámara (`Camera2`, `MediaRecorder`) e Intents públicos/nativos de cámara, sin servicios de accesibilidad, sin `TYPE_ACCESSIBILITY_OVERLAY` y sin `SYSTEM_ALERT_WINDOW`.
3. **Cero Dependencias de Terceros:**
   - El código fuente utiliza únicamente APIs estándar del Android SDK (`android.hardware.camera2`, `android.media.MediaRecorder`, `android.provider.MediaStore`, `android.service.quicksettings.TileService`). No incluye SDKs analíticos, publicitarios ni rastreadores.
4. **Almacenamiento Seguro (`Scoped Storage` + SAF) y Backup Deshabilitado:**
   - Fotos JPEG en `Pictures/RescueCam` y videos MP4 en `Movies/RescueCam` mediante `MediaStore` con escritura transaccional (`IS_PENDING = 1 -> 0`), sin permisos amplios (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES` ni `READ_MEDIA_VIDEO`).
   - Exportación/importación `.json` vía Storage Access Framework (SAF) con parser RFC-8259 estricto y lista blanca de acciones en `DiagnosticProfileCodec`.
   - `android:allowBackup="false"` en `AndroidManifest.xml` para evitar respaldo automático en la nube de preferencias locales.
5. **Seguridad de Firma y Repositorio Público:**
   - Ningún keystore privado (`.jks`, `.keystore`, `.p12`, `.pk8`, `.pem`), archivo `.aab` ni `keystore.properties` se rastrea en Git (`.gitignore` + verificación automática en `scripts/build-aab-local.ps1`).
   - La firma de lanzamiento para producción se inyecta únicamente desde variables de entorno locales o GitHub Secrets efímeros en `$RUNNER_TEMP` con limpieza `if: always()`.
6. **Política de Privacidad Estática:**
   - Disponible públicamente para revisión y Google Play Console en [`docs/privacy-policy.html`](docs/privacy-policy.html) y en el diálogo offline dentro de la propia aplicación.
