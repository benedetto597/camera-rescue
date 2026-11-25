# Samsung Camera Rescue (`com.s23plus.camera`)

<!-- en-summary -->
> **In English.** Offline Android camera app that keeps Samsung Galaxy phones (S9 to S25) usable when one rear camera sensor fails. The full documentation below is in Spanish.

- **Problem:** when a secondary sensor (for example the ultra-wide) fails, Samsung's camera HAL aborts the whole logical multi-camera session, so every camera app crashes even though the main, telephoto and front sensors still work.
- **Approach:** launches Samsung single-sensor shooting modes (Pro, Pro Video, QR scanner) via a One UI-aware `ShootingModeMap` (Galaxy S9–S25, Note 9–20, Z Fold/Flip 1–6, A series), and provides a built-in universal Camera2 viewfinder (`UniversalCameraController`) for operational front and public rear sensors with safe digital zoom, EV/flash/timer/grid controls, mechanical variable aperture ($f/1.5 \leftrightarrow f/2.4$), transactional MP4 video recording (gracefully degrading to video-only if `RECORD_AUDIO` is denied), and offline `.json` profile export/import via Storage Access Framework.
- **Stack:** Java 17, Android Camera2 API + MediaRecorder, Gradle (Kotlin DSL), GitHub Actions (signed APK released on `v*` tags).
- **Build:** `scripts/build-local.ps1` produces `dist/camera-rescue-v2.0.0.apk`; install with `adb install -r`.

Aplicación de cámara minimalista, limpia, segura y **100% offline** diseñada específicamente para rescatar la funcionalidad fotográfica y de video en dispositivos **Samsung Galaxy** cuando un módulo físico secundario (como el Ultra-Wide `sensorId 2` / `IMX564`) presenta fallo de hardware (EEPROM/I2C), manteniendo operativos el **lente trasero Principal 1x (`S5KGN3`, `ID 56`)**, el **Telefoto 3x (`S5K3K1`, `ID 52`)**, las **cámaras frontales (`S5K3LU`, `ID 1` e `ID 3`)** y cualquier sensor público operativo mediante visor `Camera2` nativo.

---

## 🔬 ¿Por qué fallan todas las apps de cámara convencionales y cómo lo resuelve esta app?

En dispositivos Samsung Galaxy con arreglos de cámaras múltiples, el proveedor HAL (`vendor.samsung.hardware.camera.provider`) expone la cámara trasera pública como el dispositivo lógico multi-cámara **`ID 0`** (`[2, 5, 6]`) y el modo Foto nativo usa **`device 20`** (`BACK_SEAMLESS_ZOOM` = `[58, 56, 52]`). Al abrir cualquiera de ellos, la función `isAvailableModule()` del driver HAL verifica la EEPROM de los **sensores agrupados simultáneamente**, abortando toda la sesión por la falla eléctrica del sensor secundario averiado.

Sin embargo, el análisis de ingeniería inversa sobre `ShootingModeMap` y el subsistema de cámara de Samsung demostró que:
1. **Modo Pro (`ShootingMode 3`) y Video Pro (`ShootingMode 36`)** mapean el lente `W` (1x Principal) directamente a **`CameraId.BACK` (`device 56`)** y el lente `T` (3x Telefoto) directamente a **`CameraId.BACK_TELE` (`device 52`)**, inicializando cada sensor de forma individual (`numPhysicalCameras: 1`) sin tocar el sensor Ultra-Wide dañado.
2. **Modo Escáner de QR (`ShootingMode 40`, `SCAN_QR_CODE`)** abre directamente **`device 56`** (`S5KGN3` 1x Principal) con autoenfoque continuo y flash.
3. **Visor Nativo `Camera2` Universal (`UniversalCameraController`):** opera directamente sobre sensores frontales (`ID 1` / `ID 3`) y sensores traseros públicos operativos (cuando no están bloqueados por el HAL) con foto JPEG, video MP4 transaccional y controles manuales/asistidos completos.

Documentación técnica completa:
- [Diagnóstico de Hardware y Subsistema HAL (`docs/hardware-diagnosis.md`)](docs/hardware-diagnosis.md)
- [Arquitectura de Software y Seguridad (`docs/architecture.md`)](docs/architecture.md)
- [Hoja de Ruta y Futuras Versiones (`docs/ROADMAP.md`)](docs/ROADMAP.md)

---

## ✨ Características Principales (`v2.0.0`)

1. **Visor Nativo `Camera2` Universal (`UniversalCameraController` + `CameraViewerMath` + `ViewfinderGridOverlay`):**
   - Reutilizable para **sensores frontales y traseros públicos operativos** (`DynamicUiBuilder.getSelectablePublicViewerSensors`), con respaldo automático a cámara frontal si un sensor trasero público dispara error HAL.
   - **Zoom digital seguro:** usa `CONTROL_ZOOM_RATIO` en API 30+ cuando el hardware lo soporta, con respaldo automático a `SCALER_CROP_REGION`.
   - **Controles de captura en vivo:** compensación de exposición (**EV**), **flash / linterna** (`Off`, `Auto`, `On`, `Linterna`), **temporizador** (`Off`, `3s`, `5s`, `10s`) con cuenta regresiva en pantalla y **cuadrícula de encuadre** (`Sin guía`, `3x3`, `Cruz`).
   - **Apertura variable mecánica (`LENS_APERTURE`):** soporte para conmutar $f/1.5 \leftrightarrow f/2.4 \leftrightarrow \text{Auto}$ en modelos compatibles (Galaxy S9/S10/Note 9–10).
2. **Grabación de Video MP4 Transaccional (`MediaRecorder` + `MediaStoreSaver` + `VideoRecordingState`):**
   - Guarda archivos `.mp4` en `Movies/S23Camera` usando `MediaStore.Video.Media` con bandera atómica `IS_PENDING` (sin permisos de almacenamiento amplio ni `INTERNET`).
   - Solicita `RECORD_AUDIO` solo al iniciar grabación de video; si el usuario deniega el permiso de micrófono, **degrada limpiamente a grabación MP4 sin audio** sin interrumpir el rescate.
   - Descarta grabaciones abortadas o menores a `600 ms` eliminando la fila pendiente de `MediaStore`.
3. **Exportación e Importación Offline de Perfiles `.json` vía SAF (`DiagnosticProfileCodec`):**
   - Exporta e importa perfiles de diagnóstico y configuración del visor mediante Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) sin requerir permisos de almacenamiento.
   - Parser RFC-8259 y validador estricto en **Java puro** (sin dependencias externas): verifica esquema `schemaVersion`, límites de tamaño (`256 KB`) y sensores (`64`), claves duplicadas, rangos numéricos y lista blanca de acciones de Tile/Shortcut.
4. **Mapeador Heurístico `ShootingModeMap` & Catálogo Ampliado (`S9–S25 / FE / Note / Z / A`):**
   - Detecta la generación de One UI (`2.x` hasta `8+` vía `SEM_PLATFORM_INT` / nivel de API) y resuelve variantes ordenadas de lanzamiento con respaldo automático (`Pro`, `ProMode`, `ProVideo`, `SCAN_QR_CODE`).
5. **Asistente Interactivo One UI (`OneUiAssistantGuide`) & Recuperación Rápida (`CameraRecoveryEngine`):**
   - Guía paso a paso persistente (`4` pasos con `CheckBox`) para activar **"Ajustes para conservar → Modo de cámara"** y configurar la **Tecla / Botón lateral (doble toque)**.
   - Detección de retorno rápido tras cierres (`≤ 2.5 s`) y recuperación escalonada en 4 etapas (Modo Pro primario → Variante alterna → Aislamiento 1x vía QR → App Info / Forzar detención).
6. **Diagnóstico On-Device, Checklist Dinámico, App Shortcuts y Quick Settings Tile:**
   - Pruebas canary secuenciales no bloqueantes en `HandlerThread`, selector de lentes sanas con persistencia local (`RescuePreferences`), hasta 4 accesos directos dinámicos y mosaico configurable en Ajustes Rápidos.

---

## 💡 Tip Adicional: Dejar la Cámara Nativa de Samsung Fija en el Lente Funcional

Además de usar `Camera Rescue`, puedes dejar la aplicación nativa de Samsung configurada para que al abrir su icono normal (o con doble toque al botón de encendido) **no vuelva a cerrarse**:
1. Abre **Modo Pro** desde el botón de `Camera Rescue`.
2. Toca el icono de **Ajustes (⚙️)** arriba a la izquierda en la cámara.
3. Entra en **Ajustes para conservar (*Settings to keep*)** y activa **Modo de cámara (*Camera mode*)**.
4. Mientras dejes la cámara en **Pro** (con ISO/Speed en `AUTO` y lente `W` 1x o `T` 3x), la cámara nativa recordará iniciar siempre en `device 56` en vez del modo Foto (`device 20`), evitando por completo el sensor Ultra-Wide dañado. Si alguna vez cambias por error a Foto o `UW`, pulsa el botón de rescate en `Camera Rescue` para destrabarla al instante.

---

## 🛠️ Compilación y Pruebas en Local (Costo $0)

### Ejecutar pruebas unitarias (10 suites en Java puro)
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-local.ps1
```

### Compilar y firmar el APK en local
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-local.ps1
```
El APK firmado se genera en `dist/camera-rescue-v2.0.0.apk`.

### Instalar en el dispositivo vía ADB
```powershell
adb install -r .\dist\camera-rescue-v2.0.0.apk
```

---

## 🚀 Publicación de Versiones con GitHub Actions ($0 Costo)

El workflow [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml) está configurado para ejecutarse **únicamente cuando se envía un tag de versión (`v*`)**:
```bash
git tag v2.0.0
git push origin v2.0.0
```
Esto mantiene el consumo de minutos de CI en `0` durante el desarrollo normal.
