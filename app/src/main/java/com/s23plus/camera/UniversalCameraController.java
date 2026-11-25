package com.s23plus.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Controlador Camera2 universal unificado para sensores frontales y traseros publicos funcionales.
 * Soporta vista previa, zoom (CONTROL_ZOOM_RATIO / SCALER_CROP_REGION), compensacion EV,
 * flash/linterna, apertura variable LENS_APERTURE, captura JPEG y grabacion MP4 transaccional.
 */
public final class UniversalCameraController {

    private static final long VIDEO_TICKER_INTERVAL_MS = 500L;
    private static final float ZOOM_PRESET_EPSILON = 0.01f;

    public interface Listener {
        void onStatusChanged(String message);
        void onCapabilitiesChanged(ViewerCapabilities capabilities);
        void onPhotoSaved(MediaStoreSaver.SaveResult result);
        void onVideoRecordingStarted(VideoRecordingState.AudioMode audioMode);
        void onVideoRecordingTick(long elapsedMs, VideoRecordingState.AudioMode audioMode);
        void onVideoSaved(MediaStoreSaver.SaveResult result, long durationMs, VideoRecordingState.AudioMode audioMode);
        void onVideoRecordingAborted(String reason);
        void onError(String error);
    }

    public static final class ViewerCapabilities {
        public final String cameraId;
        public final boolean isFrontFacing;
        public final int jpegWidth;
        public final int jpegHeight;
        public final int videoWidth;
        public final int videoHeight;
        public final CameraViewerMath.ZoomCapabilities zoomCaps;
        public final CameraViewerMath.EvCapabilities evCaps;
        public final boolean flashAvailable;
        public final CameraViewerMath.ApertureCapabilities apertureCaps;
        public final float activeZoom;
        public final int activeEvIndex;
        public final CameraViewerMath.FlashMode activeFlashMode;
        public final float activeAperture;

        public ViewerCapabilities(
                String cameraId,
                boolean isFrontFacing,
                int jpegWidth,
                int jpegHeight,
                int videoWidth,
                int videoHeight,
                CameraViewerMath.ZoomCapabilities zoomCaps,
                CameraViewerMath.EvCapabilities evCaps,
                boolean flashAvailable,
                CameraViewerMath.ApertureCapabilities apertureCaps,
                float activeZoom,
                int activeEvIndex,
                CameraViewerMath.FlashMode activeFlashMode,
                float activeAperture
        ) {
            this.cameraId = cameraId;
            this.isFrontFacing = isFrontFacing;
            this.jpegWidth = jpegWidth;
            this.jpegHeight = jpegHeight;
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            this.zoomCaps = zoomCaps;
            this.evCaps = evCaps;
            this.flashAvailable = flashAvailable;
            this.apertureCaps = apertureCaps;
            this.activeZoom = activeZoom;
            this.activeEvIndex = activeEvIndex;
            this.activeFlashMode = activeFlashMode;
            this.activeAperture = activeAperture;
        }
    }

    private final Context context;
    private final CameraManager cameraManager;
    private final Handler mainHandler;
    private final Listener listener;
    private final VideoRecordingState videoState = new VideoRecordingState();

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Surface previewSurface;
    private TextureView boundTextureView;
    private int lastDisplayRotation = Surface.ROTATION_0;

    private MediaRecorder mediaRecorder;
    private Surface mediaRecorderSurface;
    private MediaStoreSaver.PendingVideoSession pendingVideoSession;

    private String currentCameraId = CameraConfig.FRONT_CAMERA_WIDE_ID;
    private boolean isFrontFacing = true;
    private int sensorOrientation = 270;
    private Size jpegSize = new Size(4000, 3000);
    private Size videoSize = new Size(1920, 1080);

    private CameraViewerMath.ZoomCapabilities zoomCaps =
            new CameraViewerMath.ZoomCapabilities(1.0f, 1.0f, false, 0, 0);
    private CameraViewerMath.EvCapabilities evCaps =
            new CameraViewerMath.EvCapabilities(0, 0, 1, 1);
    private boolean flashAvailable = false;
    private CameraViewerMath.ApertureCapabilities apertureCaps =
            new CameraViewerMath.ApertureCapabilities(null);

    private float activeZoom = 1.0f;
    private int activeEvIndex = 0;
    private CameraViewerMath.FlashMode activeFlashMode = CameraViewerMath.FlashMode.OFF;
    private float activeAperture = CameraViewerMath.ApertureCapabilities.APERTURE_AUTO;

    private boolean isOpening = false;
    private int openGeneration = 0;

    private final Runnable recordingTickerRunnable = new Runnable() {
        @Override
        public void run() {
            VideoRecordingState.State state;
            long elapsedMs;
            VideoRecordingState.AudioMode mode;
            synchronized (UniversalCameraController.this) {
                state = videoState.getState();
                elapsedMs = videoState.getElapsedMs(SystemClock.elapsedRealtime());
                mode = videoState.getActiveAudioMode();
            }
            if (state == VideoRecordingState.State.RECORDING) {
                if (listener != null) {
                    listener.onVideoRecordingTick(elapsedMs, mode);
                }
                mainHandler.postDelayed(this, VIDEO_TICKER_INTERVAL_MS);
            }
        }
    };

    public UniversalCameraController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.cameraManager = (CameraManager) this.context.getSystemService(Context.CAMERA_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.listener = listener;
    }

    public synchronized String getCurrentCameraId() {
        return currentCameraId;
    }

    public synchronized void setCurrentCameraId(String cameraId) {
        if (cameraId != null && !cameraId.trim().isEmpty()) {
            this.currentCameraId = cameraId.trim();
        }
    }

    public synchronized boolean isCurrentFrontFacing() {
        return isFrontFacing;
    }

    public synchronized ViewerCapabilities getCapabilities() {
        return buildCapabilitiesSnapshot();
    }

    public boolean isRecordingVideo() {
        return videoState.isRecordingOrBusy();
    }

    public synchronized void start(TextureView textureView, int displayRotation) {
        this.boundTextureView = textureView;
        this.lastDisplayRotation = displayRotation;
        startBackgroundThread();
        if (textureView == null) {
            notifyError("TextureView nulo al iniciar el visor Camera2.");
            return;
        }
        if (textureView.isAvailable()) {
            openCameraInternal(textureView.getSurfaceTexture(), displayRotation);
        } else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    synchronized (UniversalCameraController.this) {
                        startBackgroundThread();
                        openCameraInternal(surface, lastDisplayRotation);
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    closeCameraOnly();
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                }
            });
        }
    }

    public synchronized void switchToCameraId(String cameraId, TextureView textureView, int displayRotation) {
        if (cameraId == null || cameraId.trim().isEmpty()) {
            return;
        }
        if (videoState.isRecordingOrBusy()) {
            stopVideoRecordingInternal(false);
        }
        this.currentCameraId = cameraId.trim();
        this.activeZoom = 1.0f;
        this.activeEvIndex = 0;
        this.activeAperture = CameraViewerMath.sanitizeApertureSelection(
                CameraViewerMath.ApertureCapabilities.APERTURE_AUTO,
                apertureCaps
        );
        this.boundTextureView = textureView;
        this.lastDisplayRotation = displayRotation;
        closeCameraOnly();
        if (textureView != null && textureView.isAvailable()) {
            startBackgroundThread();
            openCameraInternal(textureView.getSurfaceTexture(), displayRotation);
        }
    }

    public synchronized void switchFrontCamera(TextureView textureView, int displayRotation) {
        switchToCameraId(CameraConfig.toggleFrontCameraId(currentCameraId), textureView, displayRotation);
    }

    public synchronized void setZoomRatio(float zoom) {
        activeZoom = CameraViewerMath.clampZoom(zoom, zoomCaps);
        updatePreviewRepeatingRequest();
        notifyCapabilitiesChanged();
    }

    public synchronized void stepZoom(float delta) {
        activeZoom = CameraViewerMath.stepZoom(activeZoom, delta, zoomCaps);
        updatePreviewRepeatingRequest();
        notifyCapabilitiesChanged();
    }

    public synchronized void cycleZoomPreset() {
        float[] stops = CameraViewerMath.presetZoomStops(zoomCaps);
        if (stops.length == 0) {
            return;
        }
        float nextStop = stops[0];
        for (float stop : stops) {
            if (stop > activeZoom + ZOOM_PRESET_EPSILON) {
                nextStop = stop;
                break;
            }
        }
        activeZoom = CameraViewerMath.clampZoom(nextStop, zoomCaps);
        updatePreviewRepeatingRequest();
        notifyCapabilitiesChanged();
    }

    public synchronized void setEvIndex(int index) {
        activeEvIndex = CameraViewerMath.clampEvIndex(index, evCaps);
        updatePreviewRepeatingRequest();
        notifyCapabilitiesChanged();
    }

    public synchronized void stepEv(int delta) {
        setEvIndex(activeEvIndex + delta);
    }

    public synchronized void setFlashMode(CameraViewerMath.FlashMode mode) {
        boolean isVideoSession = videoState.isRecordingOrBusy();
        activeFlashMode = CameraViewerMath.resolveFlashMode(mode, flashAvailable, isVideoSession);
        updatePreviewRepeatingRequest();
        notifyCapabilitiesChanged();
    }

    public synchronized void cycleFlashMode() {
        boolean isVideoSession = videoState.isRecordingOrBusy();
        activeFlashMode = CameraViewerMath.nextFlashMode(activeFlashMode, flashAvailable, isVideoSession);
        updatePreviewRepeatingRequest();
        notifyCapabilitiesChanged();
    }

    public synchronized void setAperture(float aperture) {
        activeAperture = CameraViewerMath.sanitizeApertureSelection(aperture, apertureCaps);
        updatePreviewRepeatingRequest();
        notifyCapabilitiesChanged();
    }

    public synchronized void cycleAperture() {
        activeAperture = CameraViewerMath.resolveNextAperture(activeAperture, apertureCaps);
        updatePreviewRepeatingRequest();
        notifyCapabilitiesChanged();
    }

    public synchronized void capturePhoto(int displayRotation) {
        if (cameraDevice == null || captureSession == null || imageReader == null) {
            notifyError("El visor Camera2 aun no esta listo.");
            return;
        }
        if (videoState.isRecordingOrBusy()) {
            notifyError("No se puede capturar foto mientras hay una grabacion MP4 activa.");
            return;
        }

        final String captureCameraId = currentCameraId;
        final boolean captureFrontFacing = isFrontFacing;
        final int captureSensorOrientation = sensorOrientation;

        try {
            imageReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    if (image == null) {
                        return;
                    }
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);

                    String prefix = captureFrontFacing
                            ? ("CR_FRONT_ID" + captureCameraId)
                            : ("CR_REAR_ID" + captureCameraId);
                    MediaStoreSaver.SaveResult result = MediaStoreSaver.saveJpegBytes(context, bytes, prefix);
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onPhotoSaved(result);
                        }
                    });
                } catch (Exception e) {
                    notifyError("Error guardando fotografia JPEG: " + e.getMessage());
                }
            }, backgroundHandler);

            CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            applyCaptureControls(captureBuilder, true, false);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            int rotationDegrees = rotationToDegrees(displayRotation);
            captureBuilder.set(
                    CaptureRequest.JPEG_ORIENTATION,
                    CameraViewerMath.computeCaptureOrientation(
                            captureSensorOrientation,
                            rotationDegrees,
                            captureFrontFacing
                    )
            );

            captureSession.capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(
                        CameraCaptureSession session,
                        CaptureRequest request,
                        TotalCaptureResult result
                ) {
                    notifyStatus("Foto capturada en ID " + captureCameraId + ", guardando...");
                }
            }, backgroundHandler);
        } catch (CameraAccessException | RuntimeException e) {
            notifyError("Error al disparar foto Camera2: " + e.getMessage());
        }
    }

    public synchronized void startVideoRecording(int displayRotation, VideoRecordingState.AudioMode audioMode) {
        if (cameraDevice == null || previewSurface == null || backgroundHandler == null) {
            notifyError("El visor Camera2 no esta listo para grabar MP4.");
            return;
        }
        VideoRecordingState.AudioMode effectiveAudioMode =
                (audioMode != null) ? audioMode : VideoRecordingState.AudioMode.WITH_AUDIO;

        if (!videoState.beginPrepare(effectiveAudioMode, SystemClock.elapsedRealtime())) {
            return;
        }

        final int generationAtPrepare = openGeneration;
        try {
            String prefix = VideoRecordingState.buildVideoFilePrefix(currentCameraId, isFrontFacing);
            pendingVideoSession = MediaStoreSaver.createPendingVideoSession(context, prefix);

            mediaRecorder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    ? new MediaRecorder(context)
                    : createLegacyMediaRecorder();

            // Orden estricto de configuracion de MediaRecorder
            if (effectiveAudioMode == VideoRecordingState.AudioMode.WITH_AUDIO) {
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            }
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(pendingVideoSession.pfd.getFileDescriptor());
            mediaRecorder.setVideoEncodingBitRate(8_000_000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (effectiveAudioMode == VideoRecordingState.AudioMode.WITH_AUDIO) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mediaRecorder.setAudioEncodingBitRate(128_000);
                mediaRecorder.setAudioSamplingRate(44_100);
            }
            int rotationDegrees = rotationToDegrees(displayRotation);
            mediaRecorder.setOrientationHint(
                    CameraViewerMath.computeCaptureOrientation(sensorOrientation, rotationDegrees, isFrontFacing)
            );
            mediaRecorder.prepare();

            closeCaptureSessionOnly();
            mediaRecorderSurface = mediaRecorder.getSurface();

            final CaptureRequest.Builder recordBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            recordBuilder.addTarget(previewSurface);
            recordBuilder.addTarget(mediaRecorderSurface);
            applyCaptureControls(recordBuilder, false, true);

            createCaptureSessionCompat(
                    Arrays.asList(previewSurface, mediaRecorderSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            synchronized (UniversalCameraController.this) {
                                if (cameraDevice == null
                                        || generationAtPrepare != openGeneration
                                        || videoState.getState() != VideoRecordingState.State.PREPARING
                                        || mediaRecorder == null) {
                                    try {
                                        session.close();
                                    } catch (Exception ignored) {
                                    }
                                    return;
                                }
                                captureSession = session;
                                try {
                                    session.setRepeatingRequest(recordBuilder.build(), null, backgroundHandler);
                                    mediaRecorder.start();
                                    videoState.markRecordingStarted(SystemClock.elapsedRealtime());
                                    startRecordingTicker();
                                    notifyVideoRecordingStarted(effectiveAudioMode);
                                    notifyCapabilitiesChanged();
                                    notifyStatus("Grabando MP4 en ID " + currentCameraId + "...");
                                } catch (CameraAccessException | RuntimeException e) {
                                    abortActivePrepare("Error iniciando grabacion MP4: " + e.getMessage());
                                }
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            synchronized (UniversalCameraController.this) {
                                abortActivePrepare("Fallo al configurar sesion de video Camera2.");
                            }
                        }
                    }
            );
        } catch (IOException | CameraAccessException | RuntimeException e) {
            abortActivePrepare("No se pudo preparar grabacion MP4: " + e.getMessage());
        }
    }

    public synchronized void stopVideoRecording() {
        stopVideoRecordingInternal(true);
    }

    public synchronized void closeCameraOnly() {
        if (videoState.isRecordingOrBusy()) {
            stopVideoRecordingInternal(false);
        }
        openGeneration++;
        isOpening = false;
        closeCaptureSessionOnly();
        if (cameraDevice != null) {
            try {
                cameraDevice.close();
            } catch (Exception ignored) {
            }
            cameraDevice = null;
        }
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Exception ignored) {
            }
            imageReader = null;
        }
        if (previewSurface != null) {
            try {
                previewSurface.release();
            } catch (Exception ignored) {
            }
            previewSurface = null;
        }
    }

    public synchronized void stop() {
        closeCameraOnly();
        stopBackgroundThread();
    }

    @SuppressLint("MissingPermission")
    private synchronized void openCameraInternal(SurfaceTexture surfaceTexture, int displayRotation) {
        if (isOpening || cameraDevice != null || surfaceTexture == null || cameraManager == null) {
            return;
        }
        isOpening = true;
        final int generation = ++openGeneration;
        this.lastDisplayRotation = displayRotation;

        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(currentCameraId);
            readCapabilitiesFromCharacteristics(characteristics);

            StreamConfigurationMap map =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size previewSize = choosePreviewSize(map, jpegSize);
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(surfaceTexture);

            imageReader = ImageReader.newInstance(
                    jpegSize.getWidth(),
                    jpegSize.getHeight(),
                    ImageFormat.JPEG,
                    2
            );

            cameraManager.openCamera(currentCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    synchronized (UniversalCameraController.this) {
                        if (generation != openGeneration) {
                            camera.close();
                            return;
                        }
                        isOpening = false;
                        cameraDevice = camera;
                        notifyCapabilitiesChanged();
                        createPreviewSession();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    synchronized (UniversalCameraController.this) {
                        isOpening = false;
                        camera.close();
                        if (cameraDevice == camera) {
                            cameraDevice = null;
                        }
                    }
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    synchronized (UniversalCameraController.this) {
                        isOpening = false;
                        camera.close();
                        if (cameraDevice == camera) {
                            cameraDevice = null;
                        }
                    }
                    notifyError("Error al abrir camara ID " + currentCameraId + " (codigo " + error + ")");
                }
            }, backgroundHandler);
        } catch (CameraAccessException | RuntimeException e) {
            isOpening = false;
            notifyError("No se pudo inicializar camara ID " + currentCameraId + ": " + e.getMessage());
        }
    }

    private void readCapabilitiesFromCharacteristics(CameraCharacteristics characteristics) {
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        isFrontFacing = (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT);

        Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        sensorOrientation = (orientation != null) ? orientation : (isFrontFacing ? 270 : 90);

        Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        int activeW = activeArray != null ? activeArray.width() : 0;
        int activeH = activeArray != null ? activeArray.height() : 0;

        float minZoom = 1.0f;
        float maxZoom = 1.0f;
        boolean supportsZoomRatioApi = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            if (zoomRange != null) {
                minZoom = zoomRange.getLower();
                maxZoom = zoomRange.getUpper();
                supportsZoomRatioApi = (maxZoom > minZoom);
            }
        }
        if (!supportsZoomRatioApi) {
            Float maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            if (maxDigitalZoom != null && maxDigitalZoom > 1.0f) {
                maxZoom = maxDigitalZoom;
            }
        }
        zoomCaps = new CameraViewerMath.ZoomCapabilities(
                minZoom,
                maxZoom,
                supportsZoomRatioApi,
                activeW,
                activeH
        );
        activeZoom = CameraViewerMath.clampZoom(activeZoom, zoomCaps);

        Range<Integer> evRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        Rational evStep = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
        if (evRange != null && evStep != null) {
            evCaps = new CameraViewerMath.EvCapabilities(
                    evRange.getLower(),
                    evRange.getUpper(),
                    evStep.getNumerator(),
                    evStep.getDenominator()
            );
        } else {
            evCaps = new CameraViewerMath.EvCapabilities(0, 0, 1, 1);
        }
        activeEvIndex = CameraViewerMath.clampEvIndex(activeEvIndex, evCaps);

        Boolean flashInfo = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        flashAvailable = Boolean.TRUE.equals(flashInfo);
        activeFlashMode = CameraViewerMath.resolveFlashMode(activeFlashMode, flashAvailable, false);

        float[] rawApertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES);
        apertureCaps = CameraViewerMath.buildApertureCapabilities(rawApertures);
        activeAperture = CameraViewerMath.sanitizeApertureSelection(activeAperture, apertureCaps);

        StreamConfigurationMap map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        jpegSize = isFrontFacing
                ? (CameraConfig.FRONT_CAMERA_NORMAL_ID.equals(currentCameraId)
                        ? new Size(3392, 2544)
                        : new Size(4000, 3000))
                : new Size(4000, 3000);
        videoSize = new Size(1920, 1080);

        if (map != null) {
            Size[] jpegSizes = map.getOutputSizes(ImageFormat.JPEG);
            if (jpegSizes != null && jpegSizes.length > 0) {
                jpegSize = chooseLargestSize(jpegSizes);
            }
            Size[] videoSizes = map.getOutputSizes(MediaRecorder.class);
            if (videoSizes != null && videoSizes.length > 0) {
                int[][] candidateSizes = new int[videoSizes.length][2];
                for (int i = 0; i < videoSizes.length; i++) {
                    candidateSizes[i][0] = videoSizes[i].getWidth();
                    candidateSizes[i][1] = videoSizes[i].getHeight();
                }
                int[] bestVideo = CameraViewerMath.chooseBestVideoSize(candidateSizes, 1920, 1080);
                videoSize = new Size(bestVideo[0], bestVideo[1]);
            }
        }
    }

    private void createPreviewSession() {
        if (cameraDevice == null || previewSurface == null || imageReader == null) {
            return;
        }
        final int generation = openGeneration;
        try {
            final CaptureRequest.Builder previewBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            applyCaptureControls(previewBuilder, false, false);

            createCaptureSessionCompat(
                    Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            synchronized (UniversalCameraController.this) {
                                if (cameraDevice == null || generation != openGeneration) {
                                    try {
                                        session.close();
                                    } catch (Exception ignored) {
                                    }
                                    return;
                                }
                                captureSession = session;
                                try {
                                    session.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
                                    String facingLabel = isFrontFacing ? "Frontal" : "Trasera";
                                    notifyStatus("Activa: " + facingLabel + " ID " + currentCameraId
                                            + " [" + jpegSize.getWidth() + "x" + jpegSize.getHeight() + "]");
                                } catch (CameraAccessException e) {
                                    notifyError("Error al iniciar vista previa: " + e.getMessage());
                                }
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            notifyError("Fallo al configurar sesion Camera2 en ID " + currentCameraId);
                        }
                    }
            );
        } catch (CameraAccessException | RuntimeException e) {
            notifyError("Error creando sesion de vista previa: " + e.getMessage());
        }
    }

    private void updatePreviewRepeatingRequest() {
        if (cameraDevice == null || captureSession == null || previewSurface == null || backgroundHandler == null) {
            return;
        }
        try {
            VideoRecordingState.State state = videoState.getState();
            if (state == VideoRecordingState.State.RECORDING && mediaRecorderSurface != null) {
                CaptureRequest.Builder recordBuilder =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                recordBuilder.addTarget(previewSurface);
                recordBuilder.addTarget(mediaRecorderSurface);
                applyCaptureControls(recordBuilder, false, true);
                captureSession.setRepeatingRequest(recordBuilder.build(), null, backgroundHandler);
            } else if (!videoState.isRecordingOrBusy()) {
                CaptureRequest.Builder previewBuilder =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewBuilder.addTarget(previewSurface);
                applyCaptureControls(previewBuilder, false, false);
                captureSession.setRepeatingRequest(previewBuilder.build(), null, backgroundHandler);
            }
        } catch (CameraAccessException | RuntimeException e) {
            notifyError("No se pudo actualizar controles Camera2: " + e.getMessage());
        }
    }

    private void applyCaptureControls(
            CaptureRequest.Builder builder,
            boolean isStillCapture,
            boolean isVideoSession
    ) {
        builder.set(
                CaptureRequest.CONTROL_AF_MODE,
                isVideoSession
                        ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        );

        CameraViewerMath.ZoomApplication zoomApp = CameraViewerMath.resolveZoomApplication(
                activeZoom,
                Build.VERSION.SDK_INT,
                zoomCaps
        );
        if (zoomApp.strategy == CameraViewerMath.ZoomStrategy.ZOOM_RATIO
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoomApp.clampedZoom);
        } else if (zoomApp.strategy == CameraViewerMath.ZoomStrategy.SCALER_CROP_REGION) {
            builder.set(
                    CaptureRequest.SCALER_CROP_REGION,
                    new Rect(zoomApp.cropLeft, zoomApp.cropTop, zoomApp.cropRight, zoomApp.cropBottom)
            );
        }

        if (evCaps.supported) {
            builder.set(
                    CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    CameraViewerMath.clampEvIndex(activeEvIndex, evCaps)
            );
        }

        CameraViewerMath.FlashMode resolvedFlash = CameraViewerMath.resolveFlashMode(
                activeFlashMode,
                flashAvailable,
                isVideoSession
        );
        switch (resolvedFlash) {
            case TORCH:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                break;
            case ON:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                builder.set(
                        CaptureRequest.FLASH_MODE,
                        isStillCapture ? CaptureRequest.FLASH_MODE_SINGLE : CaptureRequest.FLASH_MODE_OFF
                );
                break;
            case AUTO:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case OFF:
            default:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
        }

        if (apertureCaps.variableApertureSupported && activeAperture > 0.0f) {
            builder.set(CaptureRequest.LENS_APERTURE, activeAperture);
        }
    }

    private void stopVideoRecordingInternal(boolean restorePreview) {
        VideoRecordingState.State currentState = videoState.getState();
        if (currentState == VideoRecordingState.State.IDLE) {
            return;
        }
        stopRecordingTicker();

        if (currentState == VideoRecordingState.State.PREPARING) {
            abortActivePrepare("Grabacion cancelada antes de iniciar.");
            if (restorePreview && cameraDevice != null && previewSurface != null && imageReader != null) {
                createPreviewSession();
            }
            return;
        }

        if (!videoState.beginStop()) {
            return;
        }

        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
            } catch (Exception ignored) {
            }
            try {
                captureSession.abortCaptures();
            } catch (Exception ignored) {
            }
            try {
                captureSession.close();
            } catch (Exception ignored) {
            }
            captureSession = null;
        }

        boolean stopFailed = false;
        String stopError = null;
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException e) {
                stopFailed = true;
                stopError = e.getMessage();
            }
        }
        releaseMediaRecorderQuietly();

        VideoRecordingState.StopOutcome outcome = videoState.finishStop(SystemClock.elapsedRealtime());
        MediaStoreSaver.PendingVideoSession sessionToFinalize = pendingVideoSession;
        pendingVideoSession = null;

        if (stopFailed) {
            MediaStoreSaver.abortPendingVideo(context, sessionToFinalize);
            notifyVideoRecordingAborted(
                    "Grabacion abortada al detener MediaRecorder"
                            + (stopError != null ? ": " + stopError : "")
            );
        } else if (!outcome.meetsMinimumDuration) {
            MediaStoreSaver.abortPendingVideo(context, sessionToFinalize);
            notifyVideoRecordingAborted(
                    "Video demasiado corto (< " + CameraConfig.MIN_VALID_VIDEO_DURATION_MS + " ms)."
            );
        } else {
            try {
                MediaStoreSaver.SaveResult saved = MediaStoreSaver.publishPendingVideo(context, sessionToFinalize);
                notifyVideoSaved(saved, outcome.durationMs, outcome.audioMode);
            } catch (IOException | RuntimeException e) {
                MediaStoreSaver.abortPendingVideo(context, sessionToFinalize);
                notifyVideoRecordingAborted("Error al publicar video MP4: " + e.getMessage());
            }
        }

        if (restorePreview && cameraDevice != null && previewSurface != null && imageReader != null) {
            createPreviewSession();
        }
    }

    private void abortActivePrepare(String reason) {
        stopRecordingTicker();
        closeCaptureSessionOnly();
        releaseMediaRecorderQuietly();
        MediaStoreSaver.abortPendingVideo(context, pendingVideoSession);
        pendingVideoSession = null;
        videoState.resetToIdle();
        notifyVideoRecordingAborted(reason);
        if (cameraDevice != null && previewSurface != null && imageReader != null) {
            createPreviewSession();
        }
    }

    private void closeCaptureSessionOnly() {
        if (captureSession != null) {
            try {
                captureSession.close();
            } catch (Exception ignored) {
            }
            captureSession = null;
        }
    }

    private void releaseMediaRecorderQuietly() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
            } catch (Exception ignored) {
            }
            try {
                mediaRecorder.release();
            } catch (Exception ignored) {
            }
            mediaRecorder = null;
        }
        if (mediaRecorderSurface != null) {
            try {
                mediaRecorderSurface.release();
            } catch (Exception ignored) {
            }
            mediaRecorderSurface = null;
        }
    }

    @SuppressWarnings("deprecation")
    private MediaRecorder createLegacyMediaRecorder() {
        return new MediaRecorder();
    }

    @SuppressWarnings("deprecation")
    private void createCaptureSessionCompat(
            List<Surface> outputs,
            CameraCaptureSession.StateCallback callback
    ) throws CameraAccessException {
        cameraDevice.createCaptureSession(outputs, callback, backgroundHandler);
    }

    private void startRecordingTicker() {
        mainHandler.removeCallbacks(recordingTickerRunnable);
        mainHandler.postDelayed(recordingTickerRunnable, VIDEO_TICKER_INTERVAL_MS);
    }

    private void stopRecordingTicker() {
        mainHandler.removeCallbacks(recordingTickerRunnable);
    }

    private ViewerCapabilities buildCapabilitiesSnapshot() {
        int jW = jpegSize != null ? jpegSize.getWidth() : 0;
        int jH = jpegSize != null ? jpegSize.getHeight() : 0;
        int vW = videoSize != null ? videoSize.getWidth() : 0;
        int vH = videoSize != null ? videoSize.getHeight() : 0;
        return new ViewerCapabilities(
                currentCameraId,
                isFrontFacing,
                jW,
                jH,
                vW,
                vH,
                zoomCaps,
                evCaps,
                flashAvailable,
                apertureCaps,
                activeZoom,
                activeEvIndex,
                activeFlashMode,
                activeAperture
        );
    }

    private static Size chooseLargestSize(Size[] sizes) {
        return Collections.max(
                Arrays.asList(sizes),
                (a, b) -> Long.compare((long) a.getWidth() * a.getHeight(), (long) b.getWidth() * b.getHeight())
        );
    }

    private static Size choosePreviewSize(StreamConfigurationMap map, Size targetAspectSize) {
        Size fallback = new Size(1440, 1080);
        if (map == null) {
            return fallback;
        }
        Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
        if (previewSizes == null || previewSizes.length == 0) {
            return fallback;
        }
        double targetAspect = (targetAspectSize != null && targetAspectSize.getHeight() > 0)
                ? (double) targetAspectSize.getWidth() / targetAspectSize.getHeight()
                : (4.0 / 3.0);

        Size best = null;
        double bestAspectDiff = Double.MAX_VALUE;
        long bestArea = -1L;

        for (Size size : previewSizes) {
            if (size.getWidth() > 1920 || size.getHeight() > 1440 || size.getHeight() <= 0) {
                continue;
            }
            double aspect = (double) size.getWidth() / size.getHeight();
            double diff = Math.abs(aspect - targetAspect);
            long area = (long) size.getWidth() * size.getHeight();
            if (best == null
                    || diff < bestAspectDiff - 0.02
                    || (Math.abs(diff - bestAspectDiff) <= 0.02 && area > bestArea)) {
                best = size;
                bestAspectDiff = diff;
                bestArea = area;
            }
        }
        return best != null ? best : fallback;
    }

    private static int rotationToDegrees(int displayRotation) {
        switch (displayRotation) {
            case Surface.ROTATION_90:
                return 90;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_0:
            default:
                return 0;
        }
    }

    private void startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("S23UniversalCameraThread");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            backgroundThread = null;
            backgroundHandler = null;
        }
    }

    private void notifyStatus(String message) {
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onStatusChanged(message));
    }

    private void notifyCapabilitiesChanged() {
        if (listener == null) {
            return;
        }
        final ViewerCapabilities snapshot = buildCapabilitiesSnapshot();
        mainHandler.post(() -> listener.onCapabilitiesChanged(snapshot));
    }

    private void notifyVideoRecordingStarted(VideoRecordingState.AudioMode audioMode) {
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onVideoRecordingStarted(audioMode));
    }

    private void notifyVideoSaved(
            MediaStoreSaver.SaveResult result,
            long durationMs,
            VideoRecordingState.AudioMode audioMode
    ) {
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onVideoSaved(result, durationMs, audioMode));
    }

    private void notifyVideoRecordingAborted(String reason) {
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onVideoRecordingAborted(reason));
    }

    private void notifyError(String error) {
        if (listener == null) {
            return;
        }
        mainHandler.post(() -> listener.onError(error));
    }
}
