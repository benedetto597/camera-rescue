# Diagnóstico de Hardware y Subsistema de Cámara — Samsung Galaxy S23+ (`SM-S916U1`)

**Fecha de diagnóstico:** 8 de noviembre de 2026  
**Dispositivo:** Samsung Galaxy S23+ (`SM-S916U1`)  
**Versión de SO / Firmware:** Android 16 (API 36) / One UI 8 (`BP4A.251205.006.S916U1UES8FZI1`)  
**Herramientas utilizadas:** `adb dumpsys media.camera`, `adb logcat` (`CameraService`, `CHI`, `CamX`), y suite de sondeo en dispositivo (`com.s23plus.probe` v0.5–v0.8).

---

## 1. Mapa Completo de Sensores y Dispositivos HAL (`ICameraProvider/internal/0-1`)

El proveedor HAL de cámara de Samsung (`vendor.samsung.hardware.camera.provider-service_64`) registra **12 dispositivos de cámara** a nivel interno, de los cuales Android `CameraManager` expone públicamente `0, 1, 2, 3` (y metadatos físicos para `5, 6`):

| ID Cámara | Tipo | Dirección | Sensor Físico | Focal / Resolución Activa | Estado de Hardware | Acceso API Estándar (`Camera2`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`0`** | Lógica Multi-Cámara (`[2, 5, 6]`) | Trasera (`BACK`) | Agrupa `IMX564` + `S5KGN3` + `S5K3K1` | `5.4 mm` / `4080x3060` | **Bloqueada por fallo en sub-módulo `2`** | `openCamera("0")` abre, pero `ConfigureStreams()` falla con `-38 (ENOSYS)` |
| **`1`** | Física / Pública | Frontal (`FRONT`) | `S5K3LU` (`sensorId 3`, modo completo) | `3.3 mm` / `4000x3000` (12 MP) | **100% Funcional** | **Acceso público total** (Preview + JPEG 30 fps verificados) |
| **`2`** | Física / Pública | Trasera (`BACK`) | `IMX564` (`sensorId 2`, Ultra-Wide 0.6x) | `2.2 mm` / `4000x3000` (12 MP) | **DAÑADO (Fallo I2C / EEPROM)** | Falla al inicializar sensor (`CamxResultEFailed`) |
| **`3`** | Física / Pública | Frontal (`FRONT`) | `S5K3LU` (`sensorId 3`, recorte 1x) | `3.3 mm` / `3392x2544` (8.6 MP) | **100% Funcional** | **Acceso público total** (Preview + JPEG 30 fps verificados) |
| **`4`** | Segura (`SECURE_IMAGE_DATA`) | Frontal (`FRONT`) | `S5K3LU` | `3.3 mm` / `4000x3000` | Funcional | Restringida a sistema (reconocimiento facial) |
| **`5`** | Sub-cámara física de `0` | Trasera (`BACK`) | `S5KGN3` (`sensorId 0`, Principal 1x 50MP) | `5.4 mm` / `4080x3060` | **100% Funcional** | No permite `openCamera("5")` directo; dentro de `0` es bloqueada por el chequeo global de `sensorId 2` |
| **`6`** | Sub-cámara física de `0` | Trasera (`BACK`) | `S5K3K1` (`sensorId 1`, Telefoto 3x 10MP) | `7.0 mm` / `3648x2736` | Funcional | No permite `openCamera("6")` directo |
| **`20`** | Lógica Interna (`[58, 56, 52]`) | Trasera (`BACK`) | `IMX564` + `S5KGN3` + `S5K3K1` | `5.4 mm` / `4080x3060` | Bloqueada por `58` (`sensorId 2`) | Oculta (`cameraId >= 20`, solo sistema) |
| **`21`** | Lógica Interna (`[52, 56]`) | Trasera (`BACK`) | `S5K3K1` + `S5KGN3` (sin Ultra-Wide) | `7.0 mm` / `3648x2736` | Funcional | Oculta (`cameraId >= 20`, solo sistema) |
| **`23`** | Lógica Interna (`[56, 58]`) | Trasera (`BACK`) | `S5KGN3` + `IMX564` | `5.4 mm` / `4080x3060` | Bloqueada por `58` (`sensorId 2`) | Oculta (`cameraId >= 20`, solo sistema) |
| **`52`** | Física Interna Independiente | Trasera (`BACK`) | `S5K3K1` (`sensorId 1`, Telefoto 3x) | `7.0 mm` / `3648x2736` | Funcional | Oculta (`cameraId >= 20`, solo sistema) |
| **`56`** | **Física Interna Independiente** | Trasera (`BACK`) | **`S5KGN3` (`sensorId 0`, Principal 1x)** | `5.4 mm` / `4080x3060` | **100% Funcional** | **Usada por el Escáner QR de Samsung (`com.sec.android.app.camera`)** |
| **`58`** | Física Interna Independiente | Trasera (`BACK`) | `IMX564` (`sensorId 2`, Ultra-Wide 0.6x) | `2.2 mm` / `4000x3000` | **DAÑADO** | Oculta (`cameraId >= 20`, solo sistema) |
| **`90`** | Segura (`SECURE_IMAGE_DATA`) | Frontal (`FRONT`) | `S5K3LU` | `3.3 mm` / `4000x3000` | Funcional | Oculta (`cameraId >= 20`, solo sistema) |

---

## 2. Causa Raíz Exacta a Nivel de Driver (`CHI` / `CamX`)

### 2.1. Evidencia del fallo físico en el sensor Ultra-Wide (`sensorId 2` / `IMX564`)
Al intentar abrir `ID 2` o configurar streams en `ID 0`, el módulo de extensión de Qualcomm/Samsung (`chxextensionmodule.cpp` y `camxsensornode.cpp`) registra:

```text
E CHI  : [SS_ERR ]: [CHI]: chxextensionmodule.cpp: isAvailableModule: 16079: Unavailable Cal Data, check i2c or module eeprom
E CHI  : [SS_ERR ]: [CHI]: chxextensionmodule.cpp: isAvailableModule: 16201: Invalid sizes of moduleinfo, make camera fail (13 >= 8)
E CHI  : [SS_ERR ]: [CHI]: chxextensionmodule.cpp: isAvailableModule: 16254: invalid module : 0x05
E CHI  : [SS_ERR ]: [CHI]: chxextensionmodule.cpp: isAvailableModule: 16256: numPhysicalCameras: 3, chiCameraId: 16, sensorId 2
E CHI  : [SS_ERR ]: [CHI]: chxextensionmodule.cpp: InitializeOverrideSession: 6032: Unavailable camera module (4)
E CamX : [ERROR][HAL] camxhaldevice.cpp:2354 ConfigureStreams() CHI Module failed to configure streams
```

### 2.2. ¿Por qué fallan todas las apps de terceros al abrir la cámara trasera?
1. Las aplicaciones de terceros (OpenCamera, WhatsApp, Instagram, GCam, etc.) solo ven `ID 0` como cámara trasera principal.
2. `ID 0` mapea a `chiCameraId: 16`, que tiene `numPhysicalCameras: 3` (`sensorId 0`, `sensorId 1`, `sensorId 2`).
3. Aunque una app use la API de cámaras físicas de Android 9+ (`OutputConfiguration.setPhysicalCameraId("5")`) o `CameraExtensionSession`, la función `InitializeOverrideSession` de Samsung verifica `isAvailableModule()` para **los 3 sensores** del grupo lógico antes de inicializar el pipeline. Como `sensorId 2` tiene su EEPROM/I2C inaccesible, `InitializeOverrideSession` aborta con `Unavailable camera module (4)`.

---

## 3. Rescate Comprobado de Hardware

1. **Modo Pro (`ShootingMode 3`) y Video Pro (`ShootingMode 36`):**
   - Mapea el lente `W` (1x Principal) directamente a `CameraId.BACK` (`device 56`) y el lente `T` (3x Telefoto) a `CameraId.BACK_TELE` (`device 52`), ejecutando cada sensor como una cámara física individual sin verificar el sensor Ultra-Wide.
2. **Modo Escáner de QR (`ShootingMode 40`, `SCAN_QR_CODE`):**
   - Abre de manera determinista `device 56` (`S5KGN3` 1x Principal) con autoenfoque y linterna.
3. **Cámaras Frontales (`ID 1` e `ID 3`):**
   - Sensores frontales independientes con acceso total vía API estándar `android.hardware.camera2`.
