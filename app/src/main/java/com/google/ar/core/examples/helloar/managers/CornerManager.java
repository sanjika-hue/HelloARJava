package com.google.ar.core.examples.helloar.managers;

import android.util.Log;
import com.google.ar.core.Anchor;
import com.google.ar.core.Trackable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages corner anchors and their ordering
 */
public class CornerManager {
    private static final String TAG = "CornerManager";
    private static final int MAX_CORNERS = 4;

    private final List<WrappedAnchor> corners = new ArrayList<>();

    public static class WrappedAnchor {
        private final Anchor anchor;
        private final Trackable trackable;

        public WrappedAnchor(Anchor anchor, Trackable trackable) {
            this.anchor = anchor;
            this.trackable = trackable;
        }

        public Anchor getAnchor() {
            return anchor;
        }

        public Trackable getTrackable() {
            return trackable;
        }
    }

    /**
     * Add a corner anchor
     * @return true if added successfully, false if max corners reached
     */
    public boolean addCorner(Anchor anchor, Trackable trackable) {
        if (corners.size() >= MAX_CORNERS) {
            return false;
        }
        corners.add(new WrappedAnchor(anchor, trackable));
        Log.d(TAG, "Corner added. Total: " + corners.size());
        return true;
    }

    /**
     * Get current corner count
     */
    public int getCornerCount() {
        return corners.size();
    }

    /**
     * Check if all corners are placed
     */
    public boolean hasAllCorners() {
        return corners.size() == MAX_CORNERS;
    }

    /**
     * Get all corner anchors
     */
    public List<WrappedAnchor> getCorners() {
        return new ArrayList<>(corners);
    }

    /**
     * Get corner positions as float arrays
     */
    public List<float[]> getCornerPositions() {
        List<float[]> positions = new ArrayList<>();
        for (WrappedAnchor corner : corners) {
            positions.add(corner.getAnchor().getPose().getTranslation());
        }
        return positions;
    }

    /**
     * Order corners as a rectangle (top-left, top-right, bottom-left, bottom-right)
     * @return ordered coordinates as single float array [x1,y1,z1, x2,y2,z2, ...]
     */
    public float[] getOrderedCorners() {
        if (corners.size() != MAX_CORNERS) {
            Log.e(TAG, "Must have exactly 4 corners to order");
            return null;
        }

        List<float[]> positions = getCornerPositions();
        return orderCornersAsRectangle(positions);
    }

    /**
     * Order 4 corners as a rectangle based on their spatial positions
     */
    private float[] orderCornersAsRectangle(List<float[]> cornerPositions) {
        if (cornerPositions.size() != 4) {
            return null;
        }

        // Calculate centroid
        float centerX = 0, centerY = 0, centerZ = 0;
        for (float[] corner : cornerPositions) {
            centerX += corner[0];
            centerY += corner[1];
            centerZ += corner[2];
        }
        centerX /= 4;
        centerY /= 4;
        centerZ /= 4;

        // Find the diagonal pair (longest distance)
        float maxDist = 0;
        int idx1 = 0, idx2 = 0;

        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                float[] c1 = cornerPositions.get(i);
                float[] c2 = cornerPositions.get(j);
                float dist = (float) Math.sqrt(
                        Math.pow(c1[0] - c2[0], 2) + Math.pow(c1[2] - c2[2], 2)
                );
                if (dist > maxDist) {
                    maxDist = dist;
                    idx1 = i;
                    idx2 = j;
                }
            }
        }

        float[] corner1 = cornerPositions.get(idx1);
        float[] corner2 = cornerPositions.get(idx2);

        List<float[]> otherCorners = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i != idx1 && i != idx2) {
                otherCorners.add(cornerPositions.get(i));
            }
        }

        // Determine positions relative to center
        float[] topLeft, topRight, bottomLeft, bottomRight;

        boolean c1IsLeft = corner1[0] < centerX;
        boolean c1IsTop = corner1[2] < centerZ;

        if (c1IsLeft && c1IsTop) {
            topLeft = corner1;
            bottomRight = corner2;
            boolean oc1IsLeft = otherCorners.get(0)[0] < centerX;
            if (oc1IsLeft) {
                bottomLeft = otherCorners.get(0);
                topRight = otherCorners.get(1);
            } else {
                topRight = otherCorners.get(0);
                bottomLeft = otherCorners.get(1);
            }
        } else if (!c1IsLeft && c1IsTop) {
            topRight = corner1;
            bottomLeft = corner2;
            boolean oc1IsLeft = otherCorners.get(0)[0] < centerX;
            if (oc1IsLeft) {
                topLeft = otherCorners.get(0);
                bottomRight = otherCorners.get(1);
            } else {
                bottomRight = otherCorners.get(0);
                topLeft = otherCorners.get(1);
            }
        } else if (c1IsLeft && !c1IsTop) {
            bottomLeft = corner1;
            topRight = corner2;
            boolean oc1IsLeft = otherCorners.get(0)[0] < centerX;
            if (oc1IsLeft) {
                topLeft = otherCorners.get(0);
                bottomRight = otherCorners.get(1);
            } else {
                bottomRight = otherCorners.get(0);
                topLeft = otherCorners.get(1);
            }
        } else {
            bottomRight = corner1;
            topLeft = corner2;
            boolean oc1IsLeft = otherCorners.get(0)[0] < centerX;
            if (oc1IsLeft) {
                bottomLeft = otherCorners.get(0);
                topRight = otherCorners.get(1);
            } else {
                topRight = otherCorners.get(0);
                bottomLeft = otherCorners.get(1);
            }
        }

        // Create ordered array
        float[] ordered = new float[12];
        System.arraycopy(topLeft, 0, ordered, 0, 3);
        System.arraycopy(topRight, 0, ordered, 3, 3);
        System.arraycopy(bottomLeft, 0, ordered, 6, 3);
        System.arraycopy(bottomRight, 0, ordered, 9, 3);

        return ordered;
    }

    /**
     * Clear all corners
     */
    public void clear() {
        corners.clear();
    }
}