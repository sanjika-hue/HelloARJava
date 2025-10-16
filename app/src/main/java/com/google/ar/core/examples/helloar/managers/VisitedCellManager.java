package com.google.ar.core.examples.helloar.managers;

import android.opengl.GLSurfaceView;
import android.util.Log;
import com.google.ar.core.examples.helloar.GridCellData;
import com.google.ar.core.examples.helloar.common.samplerender.Mesh;
import com.google.ar.core.examples.helloar.common.samplerender.Mesh.PrimitiveMode;
import com.google.ar.core.examples.helloar.common.samplerender.SampleRender;
import com.google.ar.core.examples.helloar.common.samplerender.VertexBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages visited cell visualization in AR
 */
public class VisitedCellManager {
    private static final String TAG = "VisitedCellManager";
    private static final int GRID_ROWS = 7;
    private static final int GRID_COLS = 7;
    private static final float CELL_Y_OFFSET = 0.03f; // Just 3cm above floor

    private final List<float[]> visitedCellPositions = new ArrayList<>();
    private final MeshManager meshManager;
    private final CornerManager cornerManager;
    private final GLSurfaceView surfaceView;

    public VisitedCellManager(GLSurfaceView surfaceView,
                              CornerManager cornerManager,
                              MeshManager meshManager) {
        this.surfaceView = surfaceView;
        this.cornerManager = cornerManager;
        this.meshManager = meshManager;
    }

    /**
     * Process visited cell data and create meshes
     */
    public void processVisitedCells(ArrayList<GridCellData> cellDataList, SampleRender render) {
        if (cellDataList == null || cellDataList.isEmpty()) {
            Log.w(TAG, "No cell data to process");
            return;
        }

        // Calculate positions
        calculateVisitedCellPositions(cellDataList);

        // Create meshes on GL thread
        if (render != null && !visitedCellPositions.isEmpty()) {
            surfaceView.queueEvent(() -> {
                try {
                    createVisitedCellMeshes(render);
                } catch (Exception e) {
                    Log.e(TAG, "Error creating meshes: " + e.getMessage(), e);
                }
            });
        }

        // Log statistics
        int visitedCount = 0;
        for (GridCellData cell : cellDataList) {
            if (cell.visited) visitedCount++;
        }
        Log.d(TAG, "Processed " + visitedCount + " visited cells");
    }

    /**
     * Calculate 3D positions for visited cells
     */
    private void calculateVisitedCellPositions(ArrayList<GridCellData> cellDataList) {
        visitedCellPositions.clear();

        if (!cornerManager.hasAllCorners()) {
            Log.e(TAG, "Cannot calculate positions without 4 corners");
            return;
        }

        try {
            float[] orderedCoordinates = cornerManager.getOrderedCorners();
            if (orderedCoordinates == null) {
                Log.e(TAG, "Failed to get ordered corners");
                return;
            }

            float[] topLeft = Arrays.copyOfRange(orderedCoordinates, 0, 3);
            float[] topRight = Arrays.copyOfRange(orderedCoordinates, 3, 6);
            float[] bottomLeft = Arrays.copyOfRange(orderedCoordinates, 6, 9);
            float[] bottomRight = Arrays.copyOfRange(orderedCoordinates, 9, 12);

            // ⭐ LOG the corners for debugging
            Log.d(TAG, String.format("Grid corners: TL=[%.3f,%.3f,%.3f] TR=[%.3f,%.3f,%.3f] BL=[%.3f,%.3f,%.3f] BR=[%.3f,%.3f,%.3f]",
                    topLeft[0], topLeft[1], topLeft[2],
                    topRight[0], topRight[1], topRight[2],
                    bottomLeft[0], bottomLeft[1], bottomLeft[2],
                    bottomRight[0], bottomRight[1], bottomRight[2]));

            for (GridCellData cellData : cellDataList) {
                if (cellData.visited) {
                    // ⭐ IMPORTANT: row/col ratios based on cell center
                    // Columns go from 0 (left) to GRID_COLS-1 (right)
                    // Rows go from 0 (top) to GRID_ROWS-1 (bottom)
                    float colRatio = (cellData.col + 0.5f) / GRID_COLS;
                    float rowRatio = (cellData.row + 0.5f) / GRID_ROWS;

                    // Interpolate along top edge (TL to TR)
                    float[] topPoint = interpolate(topLeft, topRight, colRatio);

                    // Interpolate along bottom edge (BL to BR)
                    float[] bottomPoint = interpolate(bottomLeft, bottomRight, colRatio);

                    // Interpolate between top and bottom
                    float[] cellCenter = interpolate(topPoint, bottomPoint, rowRatio);

                    visitedCellPositions.add(cellCenter);

                    Log.d(TAG, String.format("Cell %d [row=%d,col=%d] colRatio=%.3f rowRatio=%.3f -> [%.3f, %.3f, %.3f]",
                            cellData.cellNumber, cellData.row, cellData.col, colRatio, rowRatio,
                            cellCenter[0], cellCenter[1], cellCenter[2]));
                }
            }

            Log.d(TAG, "Calculated " + visitedCellPositions.size() + " cell positions");
        } catch (Exception e) {
            Log.e(TAG, "Error calculating positions: " + e.getMessage(), e);
        }
    }
    /**
     * Linear interpolation between two 3D points
     */
    private float[] interpolate(float[] p1, float[] p2, float ratio) {
        return new float[]{
                p1[0] + (p2[0] - p1[0]) * ratio,
                p1[1] + (p2[1] - p1[1]) * ratio,
                p1[2] + (p2[2] - p1[2]) * ratio
        };
    }

    /**
     * Create meshes for all visited cells (must be called on GL thread)
     */
    private void createVisitedCellMeshes(SampleRender render) {
        if (visitedCellPositions.isEmpty()) {
            Log.d(TAG, "No positions to create meshes for");
            return;
        }

        List<Mesh> newMeshes = new ArrayList<>();

        try {
            // Get cell dimensions
            float[] orderedCoordinates = cornerManager.getOrderedCorners();
            if (orderedCoordinates == null) {
                Log.e(TAG, "Cannot create meshes without ordered corners");
                return;
            }

            float[] topLeft = Arrays.copyOfRange(orderedCoordinates, 0, 3);
            float[] topRight = Arrays.copyOfRange(orderedCoordinates, 3, 6);
            float[] bottomLeft = Arrays.copyOfRange(orderedCoordinates, 6, 9);

            // Calculate cell size vectors
            float[] cellWidthVec = new float[3];
            float[] cellHeightVec = new float[3];
            for (int i = 0; i < 3; i++) {
                cellWidthVec[i] = (topRight[i] - topLeft[i]) / GRID_COLS;
                cellHeightVec[i] = (bottomLeft[i] - topLeft[i]) / GRID_ROWS;
            }

            // Log global grid basis vectors (for orientation reference)
            Log.d(TAG, String.format("Grid basis: widthVec=[%.3f, %.3f, %.3f], heightVec=[%.3f, %.3f, %.3f]",
                    cellWidthVec[0], cellWidthVec[1], cellWidthVec[2],
                    cellHeightVec[0], cellHeightVec[1], cellHeightVec[2]));

            // Compute world up vector (assuming Y+ in ARCore world space)
            float[] up = {0.0f, 1.0f, 0.0f};

            // Compute approximate right vector for the plane (cross product of heightVec and up, normalized)
            float[] planeNormal = normalize(cross(cellHeightVec, up));
            float[] planeRight = normalize(cross(up, planeNormal));  // Note: this approximates the col direction if plane is tilted

            Log.d(TAG, String.format("Plane orientation: up=[0,1,0], normal=[%.3f, %.3f, %.3f], right=[%.3f, %.3f, %.3f]",
                    planeNormal[0], planeNormal[1], planeNormal[2],
                    planeRight[0], planeRight[1], planeRight[2]));

            // Create mesh for each visited cell
            for (float[] cellCenter : visitedCellPositions) {
                Mesh cellMesh = createCellQuadMesh(cellCenter, cellWidthVec, cellHeightVec, render,
                        planeRight, up);  // Pass orientation vectors for per-cell logging
                if (cellMesh != null) {
                    newMeshes.add(cellMesh);
                }
            }

            // Replace old meshes with new ones (thread-safe)
            meshManager.replaceMeshes(newMeshes);

            Log.d(TAG, "Created " + newMeshes.size() + " cell meshes");

        } catch (Exception e) {
            Log.e(TAG, "Error creating meshes: " + e.getMessage(), e);

            // Clean up any created meshes on error
            for (Mesh mesh : newMeshes) {
                try {
                    if (mesh != null) mesh.close();
                } catch (Exception ex) {
                    Log.w(TAG, "Error cleaning up mesh: " + ex.getMessage());
                }
            }
        }
    }

    /** Helper: cross product */
    private float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    /** Helper: normalize vector (returns new array) */
    private float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        if (len == 0) return new float[]{0,0,0};
        return new float[]{v[0]/len, v[1]/len, v[2]/len};
    }

    /**
     * Create a quad mesh for a single cell
     */
    /**
     * Create a quad mesh for a single cell
     */
    private Mesh createCellQuadMesh(float[] center, float[] cellWidth,
                                    float[] cellHeight, SampleRender render,
                                    float[] planeRight, float[] up) {
        // Scale down slightly to show gaps between cells
        float[] halfWidth = new float[]{
                cellWidth[0] * 0.45f,
                cellWidth[1] * 0.45f,
                cellWidth[2] * 0.45f
        };
        float[] halfHeight = new float[]{
                cellHeight[0] * 0.45f,
                cellHeight[1] * 0.45f,
                cellHeight[2] * 0.45f
        };

        // Calculate the 4 corners of the cell quad using orthogonal offsets
        // Assumption: cellWidth is along columns (right in grid), cellHeight along rows (down in grid)
        // For a horizontal floor (XZ plane), +widthVec = right (X or Z), +heightVec = forward (Z or X)
        float[] topLeft = new float[]{
                center[0] - halfWidth[0] - halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] - halfWidth[2] - halfHeight[2]
        };

        float[] topRight = new float[]{
                center[0] + halfWidth[0] - halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] + halfWidth[2] - halfHeight[2]
        };

        float[] bottomLeft = new float[]{
                center[0] - halfWidth[0] + halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] - halfWidth[2] + halfHeight[2]
        };

        float[] bottomRight = new float[]{
                center[0] + halfWidth[0] + halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] + halfWidth[2] + halfHeight[2]
        };

        // Full logs for pose and orientation
        Log.d(TAG, String.format("Cell mesh pose at center=[%.3f, %.3f, %.3f] | localWidthVec=[%.3f, %.3f, %.3f] | localHeightVec=[%.3f, %.3f, %.3f] | planeRight=[%.3f, %.3f, %.3f] | up=[%.3f, %.3f, %.3f]",
                center[0], center[1], center[2],
                cellWidth[0], cellWidth[1], cellWidth[2],
                cellHeight[0], cellHeight[1], cellHeight[2],
                planeRight[0], planeRight[1], planeRight[2],
                up[0], up[1], up[2]));

        Log.d(TAG, String.format("Quad corners: TL=[%.3f,%.3f,%.3f] TR=[%.3f,%.3f,%.3f] BL=[%.3f,%.3f,%.3f] BR=[%.3f,%.3f,%.3f] (winding: TL->TR->BR / TL->BL->TR - FLIPPED TO CCW)",
                topLeft[0], topLeft[1], topLeft[2],
                topRight[0], topRight[1], topRight[2],
                bottomLeft[0], bottomLeft[1], bottomLeft[2],
                bottomRight[0], bottomRight[1], bottomRight[2]));

        // Compute and log actual normal from first triangle (TL->TR->BR)
        float[] edge1 = {topRight[0] - topLeft[0], topRight[1] - topLeft[1], topRight[2] - topLeft[2]};
        float[] edge2 = {bottomRight[0] - topLeft[0], bottomRight[1] - topLeft[1], bottomRight[2] - topLeft[2]};
        float[] normal = normalize(cross(edge1, edge2));
        Log.d(TAG, String.format("Quad facing normal (from TL->TR->BR): [%.3f, %.3f, %.3f] (should be approx [0,1,0] or [0,-1,0] depending on view; flip winding if wrong)",
                normal[0], normal[1], normal[2]));

        // ⭐ CCW WINDING for +Y facing (counter-clockwise when viewed from above/+Y)
        // Triangle 1: TL -> TR -> BR
        // Triangle 2: TL -> BR -> BL (note: BR before BL to complete CCW)
        // ⭐ CORRECT WINDING for upward-facing normal [0, 1, 0]
        float[] vertices = new float[]{
                // Triangle 1: TL -> TR -> BR (counter-clockwise from above)
                topLeft[0], topLeft[1], topLeft[2],
                topRight[0], topRight[1], topRight[2],
                bottomRight[0], bottomRight[1], bottomRight[2],

                // Triangle 2: TL -> BR -> BL (counter-clockwise from above)
                topLeft[0], topLeft[1], topLeft[2],
                bottomRight[0], bottomRight[1], bottomRight[2],
                bottomLeft[0], bottomLeft[1], bottomLeft[2]
        };

        // Optional: If normal comes out [0,-1,0] (downward), and you need upward, swap to CW:
        // - Swap order: TL -> BR -> TR and TL -> BL -> BR (your original flipped)
        // But for floor overlays viewed from above, CCW = front face up.

        // Create vertex buffer
        FloatBuffer vertexBuffer = ByteBuffer
                .allocateDirect(vertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        VertexBuffer vb = new VertexBuffer(render, 3, vertexBuffer);
        return new Mesh(render, PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{vb});
    }

    /**
     * Get count of visited cells
     */
    public int getVisitedCount() {
        return visitedCellPositions.size();
    }

    /**
     * Clear all visited cell data
     */
    public void clear() {
        visitedCellPositions.clear();
        meshManager.clearAll();
    }
}