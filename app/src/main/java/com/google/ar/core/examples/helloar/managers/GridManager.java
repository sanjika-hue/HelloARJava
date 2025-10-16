package com.google.ar.core.examples.helloar.managers;

import android.opengl.GLES30;
import android.util.Log;

import com.google.ar.core.examples.helloar.common.samplerender.Mesh;
import com.google.ar.core.examples.helloar.common.samplerender.SampleRender;
import com.google.ar.core.examples.helloar.common.samplerender.VertexBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GridManager - Creates and manages a grid overlay on the AR floor
 * Divides the floor space into cells with visible borders
 */
public class GridManager {
    private static final String TAG = "GridManager";

    // Grid configuration
    private int gridRows = 4;
    private int gridCols = 4;
    private float gapSize = 0.010f; // 5mm gap between cells
    private float cellHeight = 0.01f; // Lift cells 1.5cm above floor

    // Grid bounds (from corners)
    private float[] topLeft;
    private float[] topRight;
    private float[] bottomLeft;
    private float[] bottomRight;

    // Grid cells
    private List<GridCell> gridCells = new ArrayList<>();
    private List<Mesh> cellMeshes = new ArrayList<>();
    private List<Mesh> borderMeshes = new ArrayList<>();

    /**
     * Represents a single grid cell
     */
    public static class GridCell {
        public int row;
        public int col;
        public float[] topLeft;
        public float[] topRight;
        public float[] bottomLeft;
        public float[] bottomRight;
        public float[] center;

        public GridCell(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    /**
     * Set the number of rows and columns in the grid
     */
    public void setGridSize(int rows, int cols) {
        this.gridRows = rows;
        this.gridCols = cols;
    }

    /**
     * Set the gap size between cells (in meters)
     */
    public void setGapSize(float gap) {
        this.gapSize = gap;
    }

    /**
     * Initialize the grid with corner positions
     * @param orderedCorners Array of 12 floats: [topLeft(3), topRight(3), bottomLeft(3), bottomRight(3)]
     */
    public void initialize(float[] orderedCorners) {
        if (orderedCorners == null || orderedCorners.length != 12) {
            Log.e(TAG, "Invalid corner data");
            return;
        }

        topLeft = Arrays.copyOfRange(orderedCorners, 0, 3);
        topRight = Arrays.copyOfRange(orderedCorners, 3, 6);
        bottomLeft = Arrays.copyOfRange(orderedCorners, 6, 9);
        bottomRight = Arrays.copyOfRange(orderedCorners, 9, 12);

        generateGridCells();

        Log.d(TAG, "Grid initialized: " + gridRows + "x" + gridCols + " cells");
    }

    /**
     * Generate all grid cells based on corner positions
     */
    private void generateGridCells() {
        gridCells.clear();

        for (int row = 0; row < gridRows; row++) {
            for (int col = 0; col < gridCols; col++) {
                GridCell cell = new GridCell(row, col);

                // Calculate normalized positions within the grid
                float rowStart = (float) row / gridRows;
                float rowEnd = (float) (row + 1) / gridRows;
                float colStart = (float) col / gridCols;
                float colEnd = (float) (col + 1) / gridCols;

                // Apply gap by shrinking each cell
                float gapRatio = gapSize / Math.max(
                        distance(topLeft, topRight),
                        distance(topLeft, bottomLeft)
                );

                float gapRow = gapRatio * gridRows;
                float gapCol = gapRatio * gridCols;

                rowStart += gapRow / 2;
                rowEnd -= gapRow / 2;
                colStart += gapCol / 2;
                colEnd -= gapCol / 2;

                // Interpolate corner positions
                cell.topLeft = interpolateQuad(rowStart, colStart);
                cell.topRight = interpolateQuad(rowStart, colEnd);
                cell.bottomLeft = interpolateQuad(rowEnd, colStart);
                cell.bottomRight = interpolateQuad(rowEnd, colEnd);

                // Calculate center
                cell.center = new float[] {
                        (cell.topLeft[0] + cell.topRight[0] + cell.bottomLeft[0] + cell.bottomRight[0]) / 4,
                        (cell.topLeft[1] + cell.topRight[1] + cell.bottomLeft[1] + cell.bottomRight[1]) / 4,
                        (cell.topLeft[2] + cell.topRight[2] + cell.bottomLeft[2] + cell.bottomRight[2]) / 4
                };

                // Lift all corners by cellHeight
                cell.topLeft[1] += cellHeight;
                cell.topRight[1] += cellHeight;
                cell.bottomLeft[1] += cellHeight;
                cell.bottomRight[1] += cellHeight;
                cell.center[1] += cellHeight;

                gridCells.add(cell);
            }
        }
    }

    /**
     * Interpolate a position within the quadrilateral defined by the corners
     */
    private float[] interpolateQuad(float rowRatio, float colRatio) {
        // Bilinear interpolation within the quad
        float[] top = lerp(topLeft, topRight, colRatio);
        float[] bottom = lerp(bottomLeft, bottomRight, colRatio);
        return lerp(top, bottom, rowRatio);
    }

    /**
     * Linear interpolation between two 3D points
     */
    private float[] lerp(float[] a, float[] b, float t) {
        return new float[] {
                a[0] + (b[0] - a[0]) * t,
                a[1] + (b[1] - a[1]) * t,
                a[2] + (b[2] - a[2]) * t
        };
    }

    /**
     * Calculate distance between two 3D points
     */
    private float distance(float[] a, float[] b) {
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Create all meshes for the grid (cells + borders)
     */
    public void createMeshes(SampleRender render) {
        clearMeshes();

        for (GridCell cell : gridCells) {
            // Create cell mesh (filled quad)
            Mesh cellMesh = createCellMesh(render, cell);
            if (cellMesh != null) {
                cellMeshes.add(cellMesh);
            }

            // Create border mesh (white outline)
            Mesh borderMesh = createBorderMesh(render, cell);
            if (borderMesh != null) {
                borderMeshes.add(borderMesh);
            }
        }

        Log.d(TAG, "Created " + cellMeshes.size() + " cell meshes and " +
                borderMeshes.size() + " border meshes");
    }

    /**
     * Create a filled mesh for a single cell
     */
    private Mesh createCellMesh(SampleRender render, GridCell cell) {
        // Create two triangles to form a quad
        float[] vertices = {
                // Triangle 1: topLeft -> bottomRight -> topRight
                cell.topLeft[0], cell.topLeft[1], cell.topLeft[2],
                cell.bottomRight[0], cell.bottomRight[1], cell.bottomRight[2],
                cell.topRight[0], cell.topRight[1], cell.topRight[2],

                // Triangle 2: topLeft -> bottomLeft -> bottomRight
                cell.topLeft[0], cell.topLeft[1], cell.topLeft[2],
                cell.bottomLeft[0], cell.bottomLeft[1], cell.bottomLeft[2],
                cell.bottomRight[0], cell.bottomRight[1], cell.bottomRight[2]
        };

        FloatBuffer buffer = ByteBuffer
                .allocateDirect(vertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(vertices).position(0);

        VertexBuffer vb = new VertexBuffer(render, 3, buffer);
        return new Mesh(render, Mesh.PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{vb});
    }

    /**
     * Create a border mesh (outline) for a single cell
     */
    private Mesh createBorderMesh(SampleRender render, GridCell cell) {
        // Create 4 lines forming the cell border
        float[] vertices = {
                // Top edge
                cell.topLeft[0], cell.topLeft[1], cell.topLeft[2],
                cell.topRight[0], cell.topRight[1], cell.topRight[2],

                // Right edge
                cell.topRight[0], cell.topRight[1], cell.topRight[2],
                cell.bottomRight[0], cell.bottomRight[1], cell.bottomRight[2],

                // Bottom edge
                cell.bottomRight[0], cell.bottomRight[1], cell.bottomRight[2],
                cell.bottomLeft[0], cell.bottomLeft[1], cell.bottomLeft[2],

                // Left edge
                cell.bottomLeft[0], cell.bottomLeft[1], cell.bottomLeft[2],
                cell.topLeft[0], cell.topLeft[1], cell.topLeft[2]
        };

        FloatBuffer buffer = ByteBuffer
                .allocateDirect(vertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(vertices).position(0);

        VertexBuffer vb = new VertexBuffer(render, 3, buffer);
        return new Mesh(render, Mesh.PrimitiveMode.LINES, null, new VertexBuffer[]{vb});
    }

    /**
     * Draw all cell meshes with the given shader
     */
    public void drawCells(SampleRender render, com.google.ar.core.examples.helloar.common.samplerender.Shader shader, float[] color) {
        if (cellMeshes.isEmpty()) return;

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        shader.setVec4("u_Color", color);

        for (Mesh mesh : cellMeshes) {
            render.draw(mesh, shader);
        }

        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    /**
     * Draw all border meshes with the given shader
     */
    public void drawBorders(SampleRender render, com.google.ar.core.examples.helloar.common.samplerender.Shader shader, float[] color) {
        if (borderMeshes.isEmpty()) return;

        shader.setVec4("u_Color", color);

        for (Mesh mesh : borderMeshes) {
            render.draw(mesh, shader);
        }
    }

    /**
     * Get a specific cell by row and column
     */
    public GridCell getCell(int row, int col) {
        if (row < 0 || row >= gridRows || col < 0 || col >= gridCols) {
            return null;
        }
        int index = row * gridCols + col;
        return index < gridCells.size() ? gridCells.get(index) : null;
    }

    /**
     * Get all grid cells
     */
    public List<GridCell> getAllCells() {
        return new ArrayList<>(gridCells);
    }

    /**
     * Clear all meshes
     */
    public void clearMeshes() {
        for (Mesh mesh : cellMeshes) {
            try {
                mesh.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing cell mesh: " + e.getMessage());
            }
        }
        cellMeshes.clear();

        for (Mesh mesh : borderMeshes) {
            try {
                mesh.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing border mesh: " + e.getMessage());
            }
        }
        borderMeshes.clear();
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        clearMeshes();
        gridCells.clear();
    }

    public int getRows() {
        return gridRows;
    }

    public int getCols() {
        return gridCols;
    }
}