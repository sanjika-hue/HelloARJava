package com.hashteelabs.dodomap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.util.Log;

/**
 * Lightweight YUV_420_888 → RGB converter for ARCore frames.
 * Manual NV21 → ARGB conversion to avoid JPEG loss.
 */
public class YuvToRgbConverter {
    private static final String TAG = "YuvToRgbConverter";

    @SuppressWarnings("unused")
    public YuvToRgbConverter(Context context) {
        // Context kept for future GPU-based implementations; unused here.
    }

    /**
     * Convert an ARCore Image (YUV_420_888) into the provided ARGB_8888 Bitmap.
     */
    public void convert(Image image, Bitmap output) {
        if (image == null || output == null) {
            Log.w(TAG, "convert called with null image or output");
            return;
        }

        try {
            byte[] nv21 = YuvConverter.imageToNV21(image);
            if (nv21 == null) {
                Log.w(TAG, "NV21 conversion failed");
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int frameSize = width * height;

            int[] argb = new int[frameSize];

            for (int j = 0, yp = 0; j < height; j++) {
                int uvp = frameSize + (j >> 1) * width;
                int u = 0, v = 0;
                for (int i = 0; i < width; i++, yp++) {
                    int y = (0xff & nv21[yp]) - 16;
                    if (y < 0) y = 0;
                    if ((i & 1) == 0) {
                        v = (0xff & nv21[uvp++]) - 128;
                        u = (0xff & nv21[uvp++]) - 128;
                    }
                    int y1192 = 1192 * y;
                    int r = y1192 + 1634 * v;
                    int g = y1192 - 833 * v - 400 * u;
                    int b = y1192 + 2066 * u;

                    r = r < 0 ? 0 : Math.min(r, 262143);
                    g = g < 0 ? 0 : Math.min(g, 262143);
                    b = b < 0 ? 0 : Math.min(b, 262143);

                    argb[yp] = 0xff000000 |
                            ((r << 6) & 0xff0000) |
                            ((g >> 2) & 0xff00) |
                            ((b >> 10) & 0xff);
                }
            }

            output.setPixels(argb, 0, width, 0, 0, width, height);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "OOM during YUV→RGB conversion", oom);
        } catch (Exception e) {
            Log.e(TAG, "convert failed", e);
        }
    }
}

