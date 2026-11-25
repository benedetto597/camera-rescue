package com.s23plus.camera;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Guardado transaccional de fotografías JPEG y videos MP4 en el almacenamiento compartido de Android (MediaStore).
 * Las imágenes se almacenan en {@link CameraConfig#MEDIA_RELATIVE_PATH} ("Pictures/RescueCam")
 * y los videos en {@link CameraConfig#VIDEO_RELATIVE_PATH} ("Movies/RescueCam")
 * utilizando el flag atómico IS_PENDING para evitar archivos corruptos o incompletos en la galería.
 */
public final class MediaStoreSaver {

    private static final int JPEG_QUALITY = 95;

    private MediaStoreSaver() {
    }

    public static final class SaveResult {
        public final Uri uri;
        public final String fileName;
        public final int byteCount;

        public SaveResult(Uri uri, String fileName, int byteCount) {
            this.uri = uri;
            this.fileName = fileName;
            this.byteCount = byteCount;
        }
    }

    /**
     * Sesion transaccional de video MP4 pendiente en MediaStore (IS_PENDING = 1).
     */
    public static final class PendingVideoSession {
        public final Uri uri;
        public final String fileName;
        public final ParcelFileDescriptor pfd;
        private boolean closed;

        public PendingVideoSession(Uri uri, String fileName, ParcelFileDescriptor pfd) {
            this.uri = uri;
            this.fileName = fileName;
            this.pfd = pfd;
            this.closed = false;
        }

        public synchronized boolean isClosed() {
            return closed;
        }

        public synchronized void closeQuietly() {
            if (!closed && pfd != null) {
                closed = true;
                try {
                    pfd.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Guarda un arreglo de bytes JPEG (proveniente del ImageReader de Camera2) en MediaStore.
     */
    public static SaveResult saveJpegBytes(Context context, byte[] jpegBytes, String prefix) throws IOException {
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new IOException("Datos JPEG vacios.");
        }
        String fileName = buildFileName(prefix);
        ContentResolver resolver = context.getContentResolver();
        Uri itemUri = insertPendingImage(resolver, fileName);

        try (OutputStream out = resolver.openOutputStream(itemUri)) {
            if (out == null) {
                throw new IOException("No se pudo abrir OutputStream para MediaStore.");
            }
            out.write(jpegBytes);
            out.flush();
        } catch (IOException | RuntimeException e) {
            resolver.delete(itemUri, null, null);
            throw new IOException("Error al escribir bytes JPEG en MediaStore: " + e.getMessage(), e);
        }

        publishPendingImage(resolver, itemUri);
        return new SaveResult(itemUri, fileName, jpegBytes.length);
    }

    /**
     * Comprime y guarda un Bitmap en formato JPEG dentro de MediaStore.
     */
    public static SaveResult saveBitmap(Context context, Bitmap bitmap, String prefix) throws IOException {
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IOException("Bitmap invalido o reciclado.");
        }
        String fileName = buildFileName(prefix);
        ContentResolver resolver = context.getContentResolver();
        Uri itemUri = insertPendingImage(resolver, fileName);

        try (OutputStream out = resolver.openOutputStream(itemUri)) {
            if (out == null) {
                throw new IOException("No se pudo abrir OutputStream para MediaStore.");
            }
            boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
            if (!compressed) {
                throw new IOException("Fallo en Bitmap.compress().");
            }
            out.flush();
        } catch (IOException | RuntimeException e) {
            resolver.delete(itemUri, null, null);
            throw new IOException("Error al guardar Bitmap en MediaStore: " + e.getMessage(), e);
        }

        publishPendingImage(resolver, itemUri);
        return new SaveResult(itemUri, fileName, bitmap.getAllocationByteCount());
    }

    /**
     * Crea una entrada MP4 pendiente (IS_PENDING = 1) en MediaStore y abre su descriptor rw
     * para grabar directamente con MediaRecorder.
     */
    public static PendingVideoSession createPendingVideoSession(Context context, String prefix) throws IOException {
        if (context == null) {
            throw new IOException("Context nulo al crear sesion de video.");
        }
        String fileName = buildVideoFileName(prefix);
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, CameraConfig.VIDEO_RELATIVE_PATH);
        values.put(MediaStore.Video.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore.insert retorno null para " + fileName);
        }

        ParcelFileDescriptor pfd;
        try {
            pfd = resolver.openFileDescriptor(uri, "rw");
        } catch (IOException | RuntimeException e) {
            deleteQuietly(resolver, uri);
            throw new IOException("No se pudo abrir ParcelFileDescriptor para video: " + e.getMessage(), e);
        }

        if (pfd == null) {
            deleteQuietly(resolver, uri);
            throw new IOException("ParcelFileDescriptor nulo para " + fileName);
        }

        return new PendingVideoSession(uri, fileName, pfd);
    }

    /**
     * Cierra el descriptor de archivo de forma idempotente, publica el MP4 en MediaStore (IS_PENDING = 0)
     * y devuelve el resultado con el tamano final del archivo.
     */
    public static SaveResult publishPendingVideo(Context context, PendingVideoSession session) throws IOException {
        if (context == null || session == null || session.uri == null) {
            throw new IOException("Sesion de video invalida al publicar.");
        }

        long sizeBytes = -1L;
        if (session.pfd != null && !session.isClosed()) {
            sizeBytes = session.pfd.getStatSize();
        }
        session.closeQuietly();

        ContentResolver resolver = context.getContentResolver();
        try {
            ContentValues update = new ContentValues();
            update.put(MediaStore.Video.Media.IS_PENDING, 0);
            resolver.update(session.uri, update, null, null);
        } catch (RuntimeException e) {
            throw new IOException("Error al publicar video en MediaStore: " + e.getMessage(), e);
        }

        if (sizeBytes < 0L) {
            sizeBytes = queryMediaStoreSize(resolver, session.uri);
        }
        return new SaveResult(session.uri, session.fileName, clampByteCount(sizeBytes));
    }

    /**
     * Aborta una sesion de video pendiente cerrando el descriptor de forma idempotente
     * y eliminando la entrada en MediaStore. Seguro ante valores nulos.
     */
    public static void abortPendingVideo(Context context, PendingVideoSession session) {
        if (session == null) {
            return;
        }
        session.closeQuietly();
        if (context != null && session.uri != null) {
            deleteQuietly(context.getContentResolver(), session.uri);
        }
    }

    private static String buildFileName(String prefix) {
        String safePrefix = (prefix == null || prefix.isEmpty()) ? "RC" : prefix;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        return safePrefix + "_" + timestamp + ".jpg";
    }

    private static String buildVideoFileName(String prefix) {
        String safePrefix = (prefix == null || prefix.trim().isEmpty()) ? "CR_VIDEO" : prefix.trim();
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        return safePrefix + "_" + timestamp + ".mp4";
    }

    private static long queryMediaStoreSize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(
                uri,
                new String[]{MediaStore.MediaColumns.SIZE},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                return cursor.getLong(0);
            }
        } catch (RuntimeException ignored) {
        }
        return 0L;
    }

    private static int clampByteCount(long sizeBytes) {
        if (sizeBytes <= 0L) {
            return 0;
        }
        if (sizeBytes >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) sizeBytes;
    }

    private static void deleteQuietly(ContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
        } catch (RuntimeException ignored) {
        }
    }

    private static Uri insertPendingImage(ContentResolver resolver, String fileName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, CameraConfig.MEDIA_RELATIVE_PATH);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("MediaStore.insert retorno null para " + fileName);
        }
        return uri;
    }

    private static void publishPendingImage(ContentResolver resolver, Uri itemUri) {
        ContentValues update = new ContentValues();
        update.put(MediaStore.Images.Media.IS_PENDING, 0);
        resolver.update(itemUri, update, null, null);
    }
}
