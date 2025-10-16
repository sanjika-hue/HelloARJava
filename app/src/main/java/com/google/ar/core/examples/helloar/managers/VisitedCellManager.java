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
    private static final float CELL_Y_OFFSET = 0.001f; // Avoid z-fighting

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

            for (GridCellData cellData : cellDataList) {
                if (cellData.visited) {
                    float rowRatio = (cellData.row + 0.5f) / GRID_ROWS;
                    float colRatio = (cellData.col + 0.5f) / GRID_COLS;

                    float[] topPoint = interpolate(topLeft, topRight, colRatio);
                    float[] bottomPoint = interpolate(bottomLeft, bottomRight, colRatio);
                    float[] cellCenter = interpolate(topPoint, bottomPoint, rowRatio);

                    visitedCellPositions.add(cellCenter);

                    Log.d(TAG, String.format("Cell %d [%d,%d] -> [%.3f, %.3f, %.3f]",
                            cellData.cellNumber, cellData.row, cellData.col,
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

            // Create mesh for each visited cell
            for (float[] cellCenter : visitedCellPositions) {
                Mesh cellMesh = createCellQuadMesh(cellCenter, cellWidthVec, cellHeightVec, render);
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

    /**
     * Create a quad mesh for a single cell
     */
    private Mesh createCellQuadMesh(float[] center, float[] cellWidth,
                                    float[] cellHeight, SampleRender render) {
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

        // Create quad vertices (2 triangles)
        float[] vertices = new float[]{
                // Triangle 1
                center[0] - halfWidth[0] - halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] - halfWidth[2] - halfHeight[2],

                center[0] + halfWidth[0] - halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] + halfWidth[2] - halfHeight[2],

                center[0] + halfWidth[0] + halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] + halfWidth[2] + halfHeight[2],

                // Triangle 2
                center[0] - halfWidth[0] - halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] - halfWidth[2] - halfHeight[2],

                center[0] + halfWidth[0] + halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] + halfWidth[2] + halfHeight[2],

                center[0] - halfWidth[0] + halfHeight[0],
                center[1] + CELL_Y_OFFSET,
                center[2] - halfWidth[2] + halfHeight[2]
        };

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