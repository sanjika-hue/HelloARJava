package com.google.ar.core.examples.helloar;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * YuvConverter - Memory-safe image conversion utility
 * Converts ARCore YUV_420_888 images to JPEG with aggressive memory management
 */
public class YuvConverter {
    private static final String TAG = "YuvConverter";

    /**
     * Convert and save Image directly to JPEG file
     *
     * @param image ARCore camera image (YUV_420_888)
     * @param outputFile Target file
     * @param quality JPEG quality (1-100, recommend 20-30 for memory safety)
     * @return true if successful
     */
    public static boolean saveImageDirectly(Image image, File outputFile, int quality) {
        if (image == null || outputFile == null) {
            Log.e(TAG, "Null image or file");
            return false;
        }

        FileOutputStream fos = null;
        YuvImage yuvImage = null;
        byte[] nv21 = null;

        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes.length < 3) {
                Log.e(TAG, "Invalid plane count: " + planes.length);
                return false;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            Log.d(TAG, String.format("Converting %dx%d image at %d%% quality",
                    width, height, quality));

            // ✅ Convert to NV21 format
            nv21 = manualConversionNV21(planes, width, height);

            if (nv21 == null) {
                Log.e(TAG, "NV21 conversion failed");
                return false;
            }

            // ✅ Create YuvImage and compress to JPEG
            yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            fos = new FileOutputStream(outputFile);

            boolean success = yuvImage.compressToJpeg(
                    new Rect(0, 0, width, height),
                    quality,
                    fos
            );

            if (success) {
                Log.d(TAG, "✓ Conversion successful");
            } else {
                Log.e(TAG, "✗ JPEG compression failed");
            }

            return success;

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "❌ OUT OF MEMORY during conversion", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Conversion failed", e);
            return false;
        } finally {
            // ✅ CRITICAL: Clean up everything
            nv21 = null;
            yuvImage = null;

            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close file stream", e);
                }
            }

            // ✅ Force garbage collection
            System.gc();
        }
    }

    /**
     * Manual conversion from YUV_420_888 to NV21
     * NV21 format: Y plane + interleaved VU planes
     */
    private static byte[] manualConversionNV21(Image.Plane[] planes, int width, int height) {
        try {
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int yRowStride = planes[0].getRowStride();
            int uvRowStride = planes[1].getRowStride();
            int uvPixelStride = planes[1].getPixelStride();

            // Allocate output buffer
            byte[] nv21 = new byte[width * height * 3 / 2];

            // ========================================
            // Step 1: Copy Y plane
            // ========================================
            if (yRowStride == width) {
                // Contiguous Y data - fast path
                yBuffer.get(nv21, 0, width * height);
            } else {
                // Non-contiguous Y data - copy row by row
                int offset = 0;
                for (int row = 0; row < height; row++) {
                    yBuffer.position(row * yRowStride);
                    yBuffer.get(nv21, offset, width);
                    offset += width;
                }
            }

            // ========================================
            // Step 2: Interleave V and U planes
            // NV21 format requires VUVUVU... ordering
            // ========================================
            int uvWidth = width / 2;
            int uvHeight = height / 2;
            int uvOffset = width * height;

            for (int row = 0; row < uvHeight; row++) {
                for (int col = 0; col < uvWidth; col++) {
                    int uvIndex = row * uvRowStride + col * uvPixelStride;

                    // Write V (Cr)
                    vBuffer.position(uvIndex);
                    nv21[uvOffset++] = vBuffer.get();

                    // Write U (Cb)
                    uBuffer.position(uvIndex);
                    nv21[uvOffset++] = uBuffer.get();
                }
            }

            return nv21;

        } catch (Exception e) {
            Log.e(TAG, "NV21 conversion error", e);
            return null;
        }
    }

    /**
     * Get memory usage info for debugging
     */
    public static String getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        float percentUsed = (float) usedMemory / maxMemory * 100;

        return String.format("Memory: %.1f%% (%.1f / %.1f MB)",
                percentUsed,
                usedMemory / 1024f / 1024f,
                maxMemory / 1024f / 1024f);
    }
}