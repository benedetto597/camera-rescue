# Samsung Camera Rescue (`com.s23plus.camera`)

<!-- en-summary -->
> **In English.** Offline Android camera app that keeps Samsung Galaxy phones (S9 to S25) usable when one rear camera sensor fails. The full documentation below is in Spanish.

- **Problem:** when a secondary sensor (for example the ultra-wide) fails, Samsung's camera HAL aborts the whole logical multi-camera session, so every camera app crashes even though the main, telephoto and front sensors still work.
- **Approach:** found which Samsung shooting modes open a single physical sensor (Pro, Pro Video, QR scanner) and launches them directly; the front cameras run in a native Camera2 viewfinder. An on-device scanner tests each sensor on a background `HandlerThread` and reports its health.
- **Stack:** Java, Android Camera2 API, Gradle (Kotlin DSL), GitHub Actions (signed APK released on `v*` tags).
- **Build:** `scripts/build-local.ps1` produces `dist/camera-rescue-v1.2.0.apk`; install with `adb install -r`.

Aplicación de cámara minimalista, limpia, segura y **100% offline** diseñada específicamente para rescatar la funcionalidad fotográfica en dispositivos **Samsung Galaxy** cuando un módulo físico secundario (como el Ultra-Wide `sensorId 2` / `IMX564`) presenta fallo de hardware (EEPROM/I2C), dejando intactos el **lente trasero Principal 1x (`S5KGN3`, `ID 56`)**, el **Telefoto 3x (`S5K3K1`, `ID 52`)** y las **dos cámaras frontales (`S5K3LU`, `ID 1` e `ID 3`)**.

---

## 🔬 ¿Por qué fallan todas las apps de cámara convencionales y cómo lo resuelve esta app?

En dispositivos Samsung Galaxy con arreglos de cámaras múltiples, el proveedor HAL (`vendor.samsung.hardware.camera.provider`) expone la cámara trasera pública como el dispositivo lógico multi-cámara **`ID 0`** (`[2, 5, 6]`) y el modo Foto nativo usa **`device 20`** (`BACK_SEAMLESS_ZOOM` = `[58, 56, 52]`). Al abrir cualquiera de ellos, la función `isAvailableModule()` del driver HAL verifica la EEPROM de los **sensores agrupados simultáneamente**, abortando toda la sesión por la falla eléctrica del sensor secundario averiado.

Sin embargo, el análisis de ingeniería inversa sobre `ShootingModeMap` y el subsistema de cámara de Samsung demostró que:
1. **Modo Pro (`ShootingMode 3`) y Video Pro (`ShootingMode 36`)** mapean el lente `W` (1x Principal) directamente a **`CameraId.BACK` (`device 56`)** y el lente `T` (3x Telefoto) directamente a **`CameraId.BACK_TELE` (`device 52`)**, inicializando cada sensor de forma individual (`numPhysicalCameras: 1`) sin tocar el sensor Ultra-Wide dañado.
2. **Modo Escáner de QR (`ShootingMode 40`, `SCAN_QR_CODE`)** abre directamente **`device 56`** (`S5KGN3` 1x Principal) con autoenfoque continuo y flash.
3. **Cámaras Frontales (`ID 1` 12 MP Gran Angular e `ID 3` 8.6 MP Normal)** son dispositivos públicos independientes que funcionan al 100% mediante la API estándar `android.hardware.camera2`.

Documentación técnica completa:
- [Diagnóstico de Hardware y Subsistema HAL (`docs/hardware-diagnosis.md`)](docs/hardware-diagnosis.md)
- [Arquitectura de Software y Seguridad (`docs/architecture.md`)](docs/architecture.md)
- [Hoja de Ruta y Futuras Versiones (`docs/ROADMAP.md`)](docs/ROADMAP.md)

---

## ✨ Características Principales (`v1.3.0`)

1. **Selector Múltiple Dinámico & UI Builder (`v1.3.0`):**
   - **Checklist Interactivo de Sensores:** Tras el escaneo on-device, permite habilitar o deshabilitar manualmente mediante casillas (`CheckBox`) qué lentes funcionales (`🟢 Operativo` / `🔒 Modo Pro Requerido`) desea usar el usuario, persistiendo la selección en `RescuePreferences`.
   - **Generación Adaptativa de Tarjetas (`DynamicUiBuilder`):** Reconstruye dinámicamente las tarjetas de rescate trasero (Pro, QR y Video Pro) y los botones frontales según los sensores seleccionados y las capacidades reales del modelo (por ejemplo, omitiendo menciones de Telefoto o el modo QR cuando solo se selecciona un lente auxiliar).
   - **App Shortcuts Dinámicos (`RescueShortcutManager`):** Publica hasta 4 accesos directos dinámicos en el Launcher (`ShortcutManager`) y permite fijar atajos en la pantalla de inicio para abrir directamente el modo deseado.
   - **Quick Settings Tile (`CameraRescueTileService`):** Mosaico nativo en la barra de Ajustes Rápidos de Android configurable desde la app, compatible con Android 11–16 (incluyendo el flujo seguro con `PendingIntent` en Android 14+ / API 34+).
2. **Motor de Diagnóstico y Escáner On-Device (*Canary Testing*):**
   - **`SamsungModelDatabase`:** Catálogo heurístico offline que detecta automáticamente el modelo exacto del dispositivo (`Build.MODEL`, series S9 a S25 Ultra, Note, Fold/Flip y Serie A) y asigna sus sensores de hardware e IDs de bypass.
   - **`SensorScanner`:** Pruebas secuenciales no bloqueantes en un hilo de fondo dedicado (`HandlerThread`) sobre `CameraManager` para probar cada lente y aislar averías de hardware en el bus I2C o EEPROM.
   - **Clasificador Visual de Salud:** Muestra en pantalla el estado exacto de cada lente (`🟢 Operativo`, `🔴 Dañado / Error I2C`, `🔒 Modo Pro Requerido`).
3. **Visor Frontal Nativo `Camera2` (`ID 1` y `ID 3`) Integrado:**
   - Vista previa fluida en tiempo real (`TextureView`).
   - Conmutación dinámica entre las lentes frontales activas en el checklist (por defecto **`ID 1` 12 MP Gran Angular** e **`ID 3` 8.6 MP Normal**).
   - Disparo directo y guardado transaccional JPEG en `Pictures/S23Camera`.
4. **Lanzador Directo Trasero — Modo Pro ISP (`device 56` y `device 52`):**
   - Abre la cámara nativa en Modo Pro (`ShootingMode 3`) y Video Pro (`ShootingMode 36`) sobre los sensores desacoplados `device 56` (1x Principal) y `device 52` (3x Telefoto), omitiendo el chequeo del sensor dañado.
5. **Lanzador Trasero 1x vía Escáner QR + HUD Flotante (`device 56`):**
   - Abre el visor `SCAN_QR_CODE` (`device 56`) y superpone la barra flotante minimalista (`RearCaptureService`) con botón **Capturar** y selector de recorte (**Visor** vs **Completo**).

---

## 💡 Tip Adicional: Dejar la Cámara Nativa de Samsung Fija en el Lente Funcional

Además de usar `Camera Rescue`, puedes dejar la aplicación nativa de Samsung configurada para que al abrir su icono normal (o con doble toque al botón de encendido) **no vuelva a cerrarse**:
1. Abre **Modo Pro** desde el botón de `Camera Rescue`.
2. Toca el icono de **Ajustes (⚙️)** arriba a la izquierda en la cámara.
3. Entra en **Ajustes para conservar (*Settings to keep*)** y activa **Modo de cámara (*Camera mode*)**.
4. Mientras dejes la cámara en **Pro** (con ISO/Speed en `AUTO` y lente `W` 1x o `T` 3x), la cámara nativa recordará iniciar siempre en `device 56` en vez del modo Foto (`device 20`), evitando por completo el sensor Ultra-Wide dañado. Si alguna vez cambias por error a Foto o `UW`, pulsa el botón de rescate en `Camera Rescue` para destrabarla al instante.

---

## 🛠️ Compilación y Pruebas en Local (Costo $0)

### Ejecutar pruebas unitarias
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-local.ps1
```

### Compilar y firmar el APK en local
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-local.ps1
```
El APK firmado se genera en `dist/camera-rescue-v1.3.0.apk`.

### Instalar en el dispositivo vía ADB
```powershell
adb install -r .\dist\camera-rescue-v1.3.0.apk
```

---

## 🚀 Publicación de Versiones con GitHub Actions ($0 Costo)

El workflow [`.github/workflows/release-apk.yml`](.github/workflows/release-apk.yml) está configurado para ejecutarse **únicamente cuando se envía un tag de versión (`v*`)**:
```bash
git tag v1.3.0
git push origin v1.3.0
```
Esto mantiene el consumo de minutos de CI en `0` durante el desarrollo normal.
