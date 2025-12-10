package com.hashteelabs.dodomap;

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
     * Convenience helper to convert an ARCore Image into an NV21 byte array.
     */
    public static byte[] imageToNV21(Image image) {
        if (image == null) {
            Log.e(TAG, "imageToNV21 called with null image");
            return null;
        }
        try {
            return manualConversionNV21(image.getPlanes(), image.getWidth(), image.getHeight());
        } catch (Exception e) {
            Log.e(TAG, "imageToNV21 failed", e);
            return null;
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
     * Save a cropped JPEG from raw YUV byte array
     * This method creates a JPEG from the full YUV frame but crops it first
     *
     * @param yuvData Full frame YUV data in NV21 format
     * @param fullWidth Full frame width
     * @param fullHeight Full frame height
     * @param cropX Top-left X coordinate of crop region
     * @param cropY Top-left Y coordinate of crop region
     * @param cropWidth Width of crop region
     * @param cropHeight Height of crop region
     * @param outputFile Target JPEG file
     * @param quality JPEG quality (1-100)
     * @return true if successful
     */
    public static boolean saveImageFromByteArray(byte[] yuvData, int fullWidth, int fullHeight,
                                                 int cropX, int cropY, int cropWidth, int cropHeight,
                                                 File outputFile, int quality) {
        if (yuvData == null || outputFile == null) {
            Log.e(TAG, "Null yuvData or outputFile");
            return false;
        }

        if (cropWidth <= 0 || cropHeight <= 0) {
            Log.e(TAG, "Invalid crop dimensions: " + cropWidth + "x" + cropHeight);
            return false;
        }

        FileOutputStream fos = null;
        YuvImage yuvImage = null;
        byte[] croppedNv21 = null;

        try {
            Log.d(TAG, String.format("Saving cropped image from byte array: full=%dx%d, crop=[%d,%d,%dx%d] quality=%d%%",
                    fullWidth, fullHeight, cropX, cropY, cropWidth, cropHeight, quality));

            // Step 1: Extract the cropped Y, U, V planes from the NV21 data
            croppedNv21 = cropNV21(yuvData, fullWidth, fullHeight, cropX, cropY, cropWidth, cropHeight);

            if (croppedNv21 == null) {
                Log.e(TAG, "Failed to crop NV21 data");
                return false;
            }

            // Step 2: Create YuvImage from cropped data
            yuvImage = new YuvImage(croppedNv21, ImageFormat.NV21, cropWidth, cropHeight, null);
            fos = new FileOutputStream(outputFile);

            // Step 3: Compress to JPEG (now cropped size)
            boolean success = yuvImage.compressToJpeg(
                    new Rect(0, 0, cropWidth, cropHeight),
                    quality,
                    fos
            );

            if (success) {
                Log.d(TAG, "✓ Cropped image saved successfully");
            } else {
                Log.e(TAG, "✗ JPEG compression of cropped image failed");
            }

            return success;

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "❌ OUT OF MEMORY during cropped save", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Cropped save failed", e);
            return false;
        } finally {
            croppedNv21 = null;
            yuvImage = null;

            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    Log.w(TAG, "Failed to close file stream", e);
                }
            }

            System.gc();
        }
    }

    /**
     * Crop an NV21 image buffer
     * NV21 format: Y plane (full resolution) + interleaved VU planes (half resolution)
     *
     * @return Cropped NV21 data, or null if failed
     */
    private static byte[] cropNV21(byte[] originalNv21, int originalWidth, int originalHeight,
                                   int cropX, int cropY, int cropWidth, int cropHeight) {
        try {
            // Validate crop region
            if (cropX < 0 || cropY < 0 ||
                    cropX + cropWidth > originalWidth ||
                    cropY + cropHeight > originalHeight) {
                Log.w(TAG, "Crop region out of bounds, clamping");
                cropX = Math.max(0, cropX);
                cropY = Math.max(0, cropY);
                cropWidth = Math.min(cropWidth, originalWidth - cropX);
                cropHeight = Math.min(cropHeight, originalHeight - cropY);
            }

            // Ensure crop dimensions are even (required for YUV 4:2:0)
            if (cropWidth % 2 != 0) cropWidth--;
            if (cropHeight % 2 != 0) cropHeight--;

            // Allocate output buffer for cropped NV21
            int croppedSize = cropWidth * cropHeight * 3 / 2;
            byte[] croppedNv21 = new byte[croppedSize];

            int originalYSize = originalWidth * originalHeight;
            int croppedYSize = cropWidth * cropHeight;

            // ========================================
            // Step 1: Crop Y plane
            // ========================================
            int croppedYOffset = 0;
            for (int row = 0; row < cropHeight; row++) {
                int originalRow = cropY + row;
                int originalOffset = originalRow * originalWidth + cropX;
                System.arraycopy(originalNv21, originalOffset, croppedNv21, croppedYOffset, cropWidth);
                croppedYOffset += cropWidth;
            }

            // ========================================
            // Step 2: Crop UV plane (interleaved VU)
            // ========================================
            int uvCropWidth = cropWidth / 2;
            int uvCropHeight = cropHeight / 2;
            int originalUvX = cropX / 2;
            int originalUvY = cropY / 2;
            int originalUvWidth = originalWidth / 2;

            int croppedUvOffset = croppedYSize;
            for (int row = 0; row < uvCropHeight; row++) {
                int originalRow = originalUvY + row;
                // Each UV pixel is 2 bytes (V, U interleaved)
                int originalOffset = originalYSize + originalRow * originalUvWidth * 2 + originalUvX * 2;
                System.arraycopy(originalNv21, originalOffset, croppedNv21, croppedUvOffset, uvCropWidth * 2);
                croppedUvOffset += uvCropWidth * 2;
            }

            Log.d(TAG, String.format("Cropped NV21: %dx%d -> %dx%d (size: %d bytes)",
                    originalWidth, originalHeight, cropWidth, cropHeight, croppedSize));

            return croppedNv21;

        } catch (Exception e) {
            Log.e(TAG, "NV21 crop error", e);
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