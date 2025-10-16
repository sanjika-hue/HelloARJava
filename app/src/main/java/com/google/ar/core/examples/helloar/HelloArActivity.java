package com.google.ar.core.examples.helloar;

import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.Image;
import java.util.Arrays;
import java.io.*;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.*;
import com.google.ar.core.examples.helloar.common.helpers.*;
import com.google.ar.core.examples.helloar.common.samplerender.*;
import com.google.ar.core.examples.helloar.common.samplerender.Mesh;
import com.google.ar.core.examples.helloar.common.samplerender.Mesh.PrimitiveMode;
import com.google.ar.core.examples.helloar.common.samplerender.arcore.*;
import com.google.ar.core.exceptions.*;
import com.google.ar.core.examples.helloar.managers.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Refactored HelloArActivity with proper separation of concerns
 */
public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {

    private static final String TAG = HelloArActivity.class.getSimpleName();
    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";
    private static final int GRID_NAVIGATION_REQUEST = 1001;
    private MeshManager floorOverlayMeshManager;
    // AR Core constants
    private static final float[] SPHERICAL_HARMONIC_FACTORS = {
            0.282095f, -0.325735f, 0.325735f, -0.325735f, 0.273137f,
            -0.273137f, 0.078848f, -0.273137f, 0.136569f,
    };
    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100f;
    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;

    // AR Session and rendering
    private GLSurfaceView surfaceView;
    private Session session;
    private SampleRender render;
    private boolean installRequested;
    private boolean hasSetTextureNames = false;

    // Helpers
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;

    // UI Elements
    private Button btnDone;
    private TextView tvInstructions;
    private TextView tvDistance;

    // Rendering components
    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    private Shader lineShader;
    private Mesh virtualObjectMesh;
    private Shader virtualObjectShader;
    private Texture virtualObjectAlbedoTexture;
    private Texture virtualObjectAlbedoInstantPlacementTexture;
    private Texture dfgTexture;
    private SpecularCubemapFilter cubemapFilter;
    private long lastPointCloudTimestamp = 0;

    // Settings
    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];
    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];

    // Managers - NEW!
    private CornerManager cornerManager;
    private MeshManager cornerLineMeshManager;
    private MeshManager visitedCellMeshManager;
    private VisitedCellManager visitedCellManager;

    // Add these fields with your other managers
    private GridManager gridManager;
    private boolean gridViewVisible = false;
    private FrameLayout gridViewContainer;
    private Custom2DGridView gridView2D;

    // Grid configuration - easy to modify
    private static final int GRID_ROWS = 10;
    private static final int GRID_COLS = 10;
    private static final float GRID_GAP_SIZE = 0.005f; // 5mm gap between cells

    // Track visited cells
    private boolean[] visitedCells = new boolean[GRID_ROWS * GRID_COLS];

    // Matrix storage for rendering
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
    private final float[] viewInverseMatrix = new float[16];
    private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] viewLightDirection = new float[4];
    private Shader cellOverlayShader;  // NEW - dedicated shader for cell overlays

    // UI Elements - ADD THESE NEW ONES
    private androidx.cardview.widget.CardView cardGridInfo;
    private TextView tvGridSize;
    private TextView tvVisitedCount;
    private androidx.cardview.widget.CardView cardDistance;
    private LinearLayout cornerHintsContainer;
    private View[] cornerIndicators = new View[4];


    // Update your onCreate() method - ADD THIS SECTION:
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI
        btnDone = findViewById(R.id.btnDone);
        tvInstructions = findViewById(R.id.tvInstructions);
        tvDistance = findViewById(R.id.tvDistance);
        surfaceView = findViewById(R.id.surfaceview);

        // ⭐ NEW: Initialize professional UI elements
        cardGridInfo = findViewById(R.id.cardGridInfo);
        tvGridSize = findViewById(R.id.tvGridSize);
        tvVisitedCount = findViewById(R.id.tvVisitedCount);
        cardDistance = findViewById(R.id.cardDistance);
        cornerHintsContainer = findViewById(R.id.cornerHintsContainer);

        // Initialize corner indicators
        cornerIndicators[0] = findViewById(R.id.cornerIndicator1);
        cornerIndicators[1] = findViewById(R.id.cornerIndicator2);
        cornerIndicators[2] = findViewById(R.id.cornerIndicator3);
        cornerIndicators[3] = findViewById(R.id.cornerIndicator4);

        // ⭐ CREATE 2D grid view container (initially hidden)
        RelativeLayout rootLayout = findViewById(R.id.root_layout);
        gridViewContainer = new FrameLayout(this);
        gridViewContainer.setVisibility(View.GONE);
        gridViewContainer.setBackgroundColor(Color.parseColor("#F5F5F5"));
        RelativeLayout.LayoutParams containerParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        rootLayout.addView(gridViewContainer, containerParams);


        // Initialize managers
        cornerManager = new CornerManager();
        cornerLineMeshManager = new MeshManager(surfaceView);
        visitedCellMeshManager = new MeshManager(surfaceView);
        floorOverlayMeshManager = new MeshManager(surfaceView);
        visitedCellManager = new VisitedCellManager(surfaceView, cornerManager, visitedCellMeshManager);
        gridManager = new GridManager();

        // Set up UI listeners
        btnDone.setOnClickListener(v -> onDoneClicked());

        // ⭐ ADD back button handler for 2D grid view
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (gridViewVisible) {
                    toggle2DGridView();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        // Initialize AR helpers
        displayRotationHelper = new DisplayRotationHelper(this);
        tapHelper = new TapHelper(this);
        surfaceView.setOnTouchListener(tapHelper);

        // Initialize renderer
        render = new SampleRender(surfaceView, this, getAssets());
        installRequested = false;

        // Initialize settings
        depthSettings.onCreate(this);
        instantPlacementSettings.onCreate(this);

        // Set up settings button
        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(HelloArActivity.this, v);
            popup.setOnMenuItemClickListener(HelloArActivity.this::settingsMenuClick);
            popup.inflate(R.menu.settings_menu);
            popup.show();
        });

        updateInstructions();
    }
    private void createFloorOverlay() {
        if (!cornerManager.hasAllCorners()) return;

        surfaceView.queueEvent(() -> {
            try {
                float[] orderedCoordinates = cornerManager.getOrderedCorners();
                float[] p1 = Arrays.copyOfRange(orderedCoordinates, 0, 3);
                float[] p2 = Arrays.copyOfRange(orderedCoordinates, 3, 6);
                float[] p3 = Arrays.copyOfRange(orderedCoordinates, 6, 9);
                float[] p4 = Arrays.copyOfRange(orderedCoordinates, 9, 12);

                // ⭐ Lift each point by 2cm (more visible)
                float offsetY = 0.02f;
                p1[1] += offsetY;
                p2[1] += offsetY;
                p3[1] += offsetY;
                p4[1] += offsetY;

                // ⭐ Create quad with BOTH triangles using correct winding
                // Make sure triangles are counter-clockwise when viewed from above
                float[] quadVertices = {
                        // Triangle 1: p1 -> p2 -> p3
                        p1[0], p1[1], p1[2],
                        p2[0], p2[1], p2[2],
                        p3[0], p3[1], p3[2],

                        // Triangle 2: p2 -> p4 -> p3
                        p2[0], p2[1], p2[2],
                        p4[0], p4[1], p4[2],
                        p3[0], p3[1], p3[2]
                };

                FloatBuffer vertexBuffer = ByteBuffer
                        .allocateDirect(quadVertices.length * Float.BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
                vertexBuffer.put(quadVertices);
                vertexBuffer.position(0);

                VertexBuffer vb = new VertexBuffer(render, 3, vertexBuffer);
                Mesh floorMesh = new Mesh(render, PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{vb});

                List<Mesh> meshList = new ArrayList<>();
                meshList.add(floorMesh);
                floorOverlayMeshManager.replaceMeshes(meshList);

                Log.d(TAG, "Floor overlay mesh created - 2 triangles covering quad");
                Log.d(TAG, "Vertices: " + Arrays.toString(quadVertices));
            } catch (Exception e) {
                Log.e(TAG, "Failed to create floor overlay: " + e.getMessage(), e);
            }
        });
    }

    // ⭐ NEW: Update the updateInstructions() method:
    private void updateInstructions() {
        runOnUiThread(() -> {
            int cornerCount = cornerManager.getCornerCount();

            // Update corner indicators
            for (int i = 0; i < cornerIndicators.length; i++) {
                if (i < cornerCount) {
                    cornerIndicators[i].setBackgroundResource(R.drawable.corner_indicator);
                } else {
                    cornerIndicators[i].setBackgroundResource(R.drawable.corner_indicator_empty);
                }
            }

            if (cornerCount < 4) {
                tvInstructions.setText("Tap to place corner anchor " + (cornerCount + 1) + " of 4");
                btnDone.setEnabled(false);
                btnDone.setAlpha(0.5f);
                cornerHintsContainer.setVisibility(View.VISIBLE);
            } else {
                tvInstructions.setText("All 4 corners placed. Press 'Done' to create grid.");
                btnDone.setEnabled(true);
                btnDone.setAlpha(1.0f);
                cornerHintsContainer.setVisibility(View.GONE);
            }
            tvDistance.setVisibility(View.GONE);
            cardDistance.setVisibility(View.GONE);
        });
    }

    // ⭐ UPDATE: Enhanced onDoneClicked() with professional UI updates:
    private void onDoneClicked() {
        // Check if grid is already created
        if (gridManager != null && gridManager.hasAllCorners()) {
            // Grid exists - toggle 2D view
            toggle2DGridView();
            return;
        }

        // First time - create grid
        if (!cornerManager.hasAllCorners()) {
            Log.w(TAG, "Cannot proceed: only " + cornerManager.getCornerCount() + " corners placed");
            Toast.makeText(this, "Please place exactly 4 corners before pressing Done.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "onDoneClicked: Starting grid visualization...");

        float[] orderedCoordinates = cornerManager.getOrderedCorners();

        if (orderedCoordinates == null || orderedCoordinates.length != 12) {
            Log.e(TAG, "Error: orderedCoordinates is null or wrong length");
            Toast.makeText(this, "Error in corner ordering. Try placing corners again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Store coordinates for later use
        final float[] finalCoordinates = orderedCoordinates.clone();

        // Initialize and create grid visualization
        surfaceView.queueEvent(() -> {
            try {
                // Initialize grid using configured values
                gridManager.setGridSize(GRID_ROWS, GRID_COLS);
                gridManager.setGapSize(GRID_GAP_SIZE);
                gridManager.initialize(finalCoordinates);
                gridManager.createMeshes(render);

                Log.d(TAG, "Grid visualization created: " +
                        GRID_ROWS + "x" + GRID_COLS + " cells with " +
                        GRID_GAP_SIZE + "m gap");

                runOnUiThread(() -> {
                    // Update UI for professional look
                    Toast.makeText(this, "Grid visualization created!", Toast.LENGTH_SHORT).show();
                    tvInstructions.setText("Grid overlay active - Tap '2D View' to edit");

                    // Show grid info card
                    cardGridInfo.setVisibility(View.VISIBLE);
                    tvGridSize.setText(GRID_ROWS + "×" + GRID_COLS + " Grid Active");
                    updateVisitedCountDisplay();

                    // Update button
                    btnDone.setText("2D VIEW");

                    // Initialize 2D grid view
                    initialize2DGridView(finalCoordinates);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to create grid: " + e.getMessage(), e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Error creating grid", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    // ⭐ NEW: Helper method to update visited count display
    private void updateVisitedCountDisplay() {
        int visitedCount = 0;
        for (boolean visited : visitedCells) {
            if (visited) visitedCount++;
        }

        int totalCells = GRID_ROWS * GRID_COLS;
        tvVisitedCount.setText(visitedCount + " of " + totalCells + " cells visited");
    }

    // ⭐ UPDATE: Enhanced toggle2DGridView() with UI updates:
    private void toggle2DGridView() {
        // Safety check
        if (gridViewContainer == null) {
            Log.e(TAG, "Grid view container not initialized");
            Toast.makeText(this, "Grid view not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        gridViewVisible = !gridViewVisible;

        if (gridViewVisible) {
            // Show 2D grid view
            gridViewContainer.setVisibility(View.VISIBLE);
            surfaceView.setVisibility(View.GONE);
            btnDone.setVisibility(View.GONE);
            tvInstructions.setVisibility(View.GONE);
            cardGridInfo.setVisibility(View.GONE);

            // Update 2D view with current visited state
            if (gridView2D != null) {
                gridView2D.updateVisitedCells(visitedCells);
            }

            Log.d(TAG, "Switched to 2D grid view");
        } else {
            // Show AR view
            gridViewContainer.setVisibility(View.GONE);
            surfaceView.setVisibility(View.VISIBLE);
            btnDone.setVisibility(View.VISIBLE);
            tvInstructions.setVisibility(View.VISIBLE);
            cardGridInfo.setVisibility(View.VISIBLE);

            // Get updated visited cells from 2D view
            if (gridView2D != null) {
                visitedCells = gridView2D.getVisitedState();

                // Update visited count display
                updateVisitedCountDisplay();

                // Update AR meshes with visited cells
                surfaceView.queueEvent(() -> updateGridMeshColors());
            }

            Log.d(TAG, "Switched to AR view");
        }
    }

    // ⭐ UPDATE: Enhanced updateGridMeshColors() with UI feedback:
    private void updateGridMeshColors() {
        try {
            // Recreate meshes - this will be called on GL thread
            if (gridManager != null && render != null) {
                gridManager.clearMeshes();
                gridManager.createMeshes(render);

                int visitedCount = 0;
                for (boolean visited : visitedCells) {
                    if (visited) visitedCount++;
                }

                Log.d(TAG, "Updated grid meshes: " + visitedCount + " cells marked as visited");

                final int count = visitedCount;
                runOnUiThread(() -> {
                    updateVisitedCountDisplay();
                    Toast.makeText(HelloArActivity.this,
                            count + " cells marked as visited", Toast.LENGTH_SHORT).show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating grid mesh colors: " + e.getMessage(), e);
            Log.e(TAG, "Error updating grid mesh colors: " + e.getMessage(), e);
        }
    }
    private void initialize2DGridView(float[] orderedCoordinates) {
        // Convert coordinates to Pose objects for GridView
        float[] identityRotation = new float[]{0f, 0f, 0f, 1f};
        Pose[] boundaryPoses = new Pose[4];
        boundaryPoses[0] = new Pose(Arrays.copyOfRange(orderedCoordinates, 0, 3), identityRotation);
        boundaryPoses[1] = new Pose(Arrays.copyOfRange(orderedCoordinates, 3, 6), identityRotation);
        boundaryPoses[2] = new Pose(Arrays.copyOfRange(orderedCoordinates, 6, 9), identityRotation);
        boundaryPoses[3] = new Pose(Arrays.copyOfRange(orderedCoordinates, 9, 12), identityRotation);

        // Clear container
        gridViewContainer.removeAllViews();

        // Create custom GridView that matches our grid configuration
        gridView2D = new Custom2DGridView(this, boundaryPoses);
        gridViewContainer.addView(gridView2D);

        // ⭐ PROFESSIONAL: Create top bar for 2D view
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#2196F3"));
        topBar.setPadding(20, 40, 20, 20);

        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        topBarParams.gravity = android.view.Gravity.TOP;

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("2D Floor Plan");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        tvTitle.setLayoutParams(titleParams);
        topBar.addView(tvTitle);

        // Info text
        TextView tvInfo = new TextView(this);
        tvInfo.setText("Tap cells to mark as visited");
        tvInfo.setTextColor(Color.parseColor("#BBDEFB"));
        tvInfo.setTextSize(14);
        topBar.addView(tvInfo);

        gridViewContainer.addView(topBar, topBarParams);

        // ⭐ PROFESSIONAL: Create bottom action bar for 2D view
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(Color.parseColor("#FAFAFA"));
        bottomBar.setPadding(20, 20, 20, 40);

        FrameLayout.LayoutParams bottomBarParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bottomBarParams.gravity = android.view.Gravity.BOTTOM;

        // Stats card
        androidx.cardview.widget.CardView statsCard = new androidx.cardview.widget.CardView(this);
        statsCard.setCardBackgroundColor(Color.WHITE);
        statsCard.setRadius(12 * getResources().getDisplayMetrics().density);
        statsCard.setCardElevation(4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statsParams.bottomMargin = (int)(12 * getResources().getDisplayMetrics().density);

        LinearLayout statsContent = new LinearLayout(this);
        statsContent.setOrientation(LinearLayout.HORIZONTAL);
        statsContent.setPadding(16, 16, 16, 16);

        // Grid size info
        LinearLayout gridSizeLayout = new LinearLayout(this);
        gridSizeLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridSizeParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        gridSizeLayout.setLayoutParams(gridSizeParams);

        TextView tvGridSizeLabel = new TextView(this);
        tvGridSizeLabel.setText("GRID SIZE");
        tvGridSizeLabel.setTextColor(Color.parseColor("#757575"));
        tvGridSizeLabel.setTextSize(10);
        gridSizeLayout.addView(tvGridSizeLabel);

        TextView tvGridSizeValue = new TextView(this);
        tvGridSizeValue.setText(GRID_ROWS + " × " + GRID_COLS);
        tvGridSizeValue.setTextColor(Color.parseColor("#212121"));
        tvGridSizeValue.setTextSize(18);
        tvGridSizeValue.setTypeface(null, android.graphics.Typeface.BOLD);
        gridSizeLayout.addView(tvGridSizeValue);

        statsContent.addView(gridSizeLayout);

        // Visited count info
        LinearLayout visitedLayout = new LinearLayout(this);
        visitedLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams visitedParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        visitedLayout.setLayoutParams(visitedParams);

        TextView tvVisitedLabel = new TextView(this);
        tvVisitedLabel.setText("VISITED");
        tvVisitedLabel.setTextColor(Color.parseColor("#757575"));
        tvVisitedLabel.setTextSize(10);
        visitedLayout.addView(tvVisitedLabel);

        TextView tvVisitedValue = new TextView(this);
        int visitedCount = 0;
        for (boolean visited : visitedCells) {
            if (visited) visitedCount++;
        }
        tvVisitedValue.setText(visitedCount + " / " + (GRID_ROWS * GRID_COLS));
        tvVisitedValue.setTextColor(Color.parseColor("#4CAF50"));
        tvVisitedValue.setTextSize(18);
        tvVisitedValue.setTypeface(null, android.graphics.Typeface.BOLD);
        visitedLayout.addView(tvVisitedValue);

        statsContent.addView(visitedLayout);
        statsCard.addView(statsContent);
        bottomBar.addView(statsCard, statsParams);

        // With this:
        Button btnBackToAR = new Button(this);
        btnBackToAR.setText("BACK TO AR VIEW");
        btnBackToAR.setTextSize(16);
        btnBackToAR.setTypeface(null, android.graphics.Typeface.BOLD);
        btnBackToAR.setBackgroundColor(Color.parseColor("#2196F3"));
        btnBackToAR.setTextColor(Color.WHITE);
        btnBackToAR.setElevation(8 * getResources().getDisplayMetrics().density);


        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int)(56 * getResources().getDisplayMetrics().density)
        );
        btnBackToAR.setLayoutParams(buttonParams);
        btnBackToAR.setOnClickListener(v -> toggle2DGridView());

        bottomBar.addView(btnBackToAR);
        gridViewContainer.addView(bottomBar, bottomBarParams);
    }
    // Draw a single grid cell with specified color
    private void drawSingleCell(GridManager.GridCell cell,
                                com.google.ar.core.examples.helloar.common.samplerender.Shader shader,
                                float[] color) {
        try {
            // Create mesh for single cell
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
            Mesh cellMesh = new Mesh(render, PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{vb});

            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);

            shader.setVec4("u_Color", color);
            render.draw(cellMesh, shader);

            cellMesh.close();

            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        } catch (Exception e) {
            Log.w(TAG, "Error drawing single cell: " + e.getMessage());
        }
    }

//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//
//        if (requestCode == GRID_NAVIGATION_REQUEST && resultCode == RESULT_OK && data != null) {
//            try {
//                ArrayList<GridCellData> visitedCells =
//                        data.getParcelableArrayListExtra("visitedCells");
//
//                if (visitedCells != null && !visitedCells.isEmpty()) {
//                    Log.d(TAG, "Received " + visitedCells.size() + " cell data entries");
//
//                    // Process visited cells using manager
//                    visitedCellManager.processVisitedCells(visitedCells, render);
//
//                    // Count visited cells
//                    int visitedCount = 0;
//                    for (GridCellData cell : visitedCells) {
//                        if (cell.visited) visitedCount++;
//                    }
//
//                    showToastOnUiThread("Showing " + visitedCount + " visited cells in AR");
//                } else {
//                    Log.w(TAG, "No visited cell data received");
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Error processing visited cells: " + e.getMessage(), e);
//                showToastOnUiThread("Error loading visited cells");
//            }
//        }
//    }

    private void handleTapForCornerPlacement(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap == null || camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        if (cornerManager.hasAllCorners()) {
            return;
        }

        float x = tap.getX();
        float y = tap.getY();

        for (HitResult hit : frame.hitTest(x, y)) {
            Trackable trackable = hit.getTrackable();
            if ((trackable instanceof Plane &&
                    ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) ||
                    (trackable instanceof PointCloud)) {

                Anchor anchor = hit.createAnchor();
                boolean added = cornerManager.addCorner(anchor, trackable);

                if (added) {
                    showToastOnUiThread("Corner " + cornerManager.getCornerCount() + " placed");
                    updateInstructions();
                    surfaceView.queueEvent(() -> {
                        createCornerConnectionLines();
                        if (cornerManager.hasAllCorners()) {
                            createFloorOverlay();
                        }
                    });
                }
                break;
            }
        }
    }

    private void createCornerConnectionLines() {
        int cornerCount = cornerManager.getCornerCount();
        if (cornerCount < 2) return;

        List<Mesh> newLineMeshes = new ArrayList<>();
        List<float[]> corners = cornerManager.getCornerPositions();

        try {
            if (cornerManager.hasAllCorners()) {
                // Create rectangle outline
                float[] orderedCoordinates = cornerManager.getOrderedCorners();
                float[] p1 = Arrays.copyOfRange(orderedCoordinates, 0, 3);
                float[] p2 = Arrays.copyOfRange(orderedCoordinates, 3, 6);
                float[] p3 = Arrays.copyOfRange(orderedCoordinates, 6, 9);
                float[] p4 = Arrays.copyOfRange(orderedCoordinates, 9, 12);

                newLineMeshes.add(createLineMesh(p1, p2));
                newLineMeshes.add(createLineMesh(p2, p4));
                newLineMeshes.add(createLineMesh(p4, p3));
                newLineMeshes.add(createLineMesh(p3, p1));
            } else {
                // Create sequential lines
                for (int i = 0; i < cornerCount - 1; i++) {
                    float[] p1 = corners.get(i);
                    float[] p2 = corners.get(i + 1);
                    newLineMeshes.add(createLineMesh(p1, p2));
                }
            }

            // Replace old meshes with new ones (thread-safe)
            cornerLineMeshManager.replaceMeshes(newLineMeshes);

        } catch (Exception e) {
            Log.e(TAG, "Failed to create corner lines: " + e.getMessage(), e);
        }
    }

    private Mesh createLineMesh(float[] start, float[] end) {
        float[] lineVertices = {
                start[0], start[1], start[2],
                end[0], end[1], end[2]
        };

        FloatBuffer vertexBuffer = ByteBuffer
                .allocateDirect(lineVertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(lineVertices);
        vertexBuffer.position(0);

        VertexBuffer vb = new VertexBuffer(render, 3, vertexBuffer);
        return new Mesh(render, PrimitiveMode.LINES, null, new VertexBuffer[]{vb});
    }

    private void drawAnchor(Anchor anchor) {
        if (anchor.getTrackingState() != TrackingState.TRACKING) return;

        anchor.getPose().toMatrix(modelMatrix, 0);

        // Scale the anchor visualization
        float scale = 0.2f;
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        Matrix.scaleM(scaleMatrix, 0, scale, scale, scale);

        float[] scaledModelMatrix = new float[16];
        Matrix.multiplyMM(scaledModelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);

        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, scaledModelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

        virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
        virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
        virtualObjectShader.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture);

        if (virtualObjectMesh != null) {
            render.draw(virtualObjectMesh, virtualObjectShader);
        }
    }

    protected boolean settingsMenuClick(MenuItem item) {
        if (item.getItemId() == R.id.depth_settings) {
            launchDepthSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == R.id.instant_placement_settings) {
            launchInstantPlacementSettingsMenuDialog();
            return true;
        }
        return false;
    }

    // UPDATE onDestroy() to cleanup grid manager
    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }

        // Clean up all meshes on GL thread
        if (surfaceView != null) {
            surfaceView.queueEvent(() -> {
                cornerLineMeshManager.cleanup();
                visitedCellMeshManager.cleanup();
                floorOverlayMeshManager.cleanup();
                gridManager.cleanup(); // ADD THIS LINE

                if (pointCloudMesh != null) {
                    try {
                        pointCloudMesh.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Error closing point cloud mesh: " + e.getMessage());
                    }
                }
                if (virtualObjectMesh != null) {
                    try {
                        virtualObjectMesh.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Error closing virtual object mesh: " + e.getMessage());
                    }
                }
            });
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;

            try {
                ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);

                if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                    switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                        case INSTALL_REQUESTED:
                            installRequested = true;
                            return;
                        case INSTALLED:
                            break;
                    }
                }

                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                session = new Session(this);
            } catch (UnavailableArcoreNotInstalledException
                     | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        try {
            configureSession();
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application",
                    Toast.LENGTH_LONG).show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        this.render = render;

        try {
            planeRenderer = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, 1, 1);

            cubemapFilter = new SpecularCubemapFilter(
                    render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);

            dfgTexture = new Texture(
                    render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false);

            final int dfgResolution = 64;
            final int dfgChannels = 2;
            final int halfFloatSize = 2;

            ByteBuffer buffer = ByteBuffer.allocateDirect(
                    dfgResolution * dfgResolution * dfgChannels * halfFloatSize);
            try (InputStream is = getAssets().open("models/dfg.raw")) {
                is.read(buffer.array());
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG16F,
                    dfgResolution, dfgResolution, 0, GLES30.GL_RG, GLES30.GL_HALF_FLOAT, buffer);

            pointCloudShader = Shader.createFromAssets(
                            render, "shaders/point_cloud.vert", "shaders/point_cloud.frag", null)
                    .setVec4("u_Color", new float[]{31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
                    .setFloat("u_PointSize", 5.0f);

            pointCloudVertexBuffer = new VertexBuffer(render, 4, null);
            final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
            pointCloudMesh = new Mesh(render, Mesh.PrimitiveMode.POINTS, null, pointCloudVertexBuffers);

            virtualObjectAlbedoTexture = Texture.createFromAsset(
                    render, "models/pawn_albedo.png", Texture.WrapMode.CLAMP_TO_EDGE,
                    Texture.ColorFormat.SRGB);
            virtualObjectAlbedoInstantPlacementTexture = Texture.createFromAsset(
                    render, "models/pawn_albedo_instant_placement.png",
                    Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.SRGB);
            Texture virtualObjectPbrTexture = Texture.createFromAsset(
                    render, "models/pawn_roughness_metallic_ao.png",
                    Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.LINEAR);

            virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
            virtualObjectShader = Shader.createFromAssets(
                            render, "shaders/environmental_hdr.vert", "shaders/environmental_hdr.frag",
                            new HashMap<String, String>() {{
                                put("NUMBER_OF_MIPMAP_LEVELS",
                                        Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                            }})
                    .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
                    .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
                    .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                    .setTexture("u_DfgTexture", dfgTexture);

            // Line shader for corner connections
            lineShader = Shader.createFromAssets(
                    render, "shaders/line.vert", "shaders/line.frag", null);

            // ⭐ NEW: Inline shader for cell overlays with visible green color
            cellOverlayShader = createCellOverlayShader(render);

        } catch (IOException e) {
            Log.e(TAG, "Failed to load shader", e);
            messageSnackbarHelper.showError(this, "Failed to load shader: " + e);
        }
    }

    // ⭐ NEW METHOD: Create inline shader for cell overlays
    private Shader createCellOverlayShader(SampleRender render) {
        // Vertex shader - transforms vertices to screen space
        String vertexShaderCode =
                "#version 300 es\n" +
                        "uniform mat4 u_ModelViewProjection;\n" +
                        "layout(location = 0) in vec4 a_Position;\n" +
                        "void main() {\n" +
                        "    gl_Position = u_ModelViewProjection * a_Position;\n" +
                        "}\n";

        // Fragment shader - draws semi-transparent green
        String fragmentShaderCode =
                "#version 300 es\n" +
                        "precision mediump float;\n" +
                        "uniform vec4 u_Color;\n" +
                        "out vec4 o_FragColor;\n" +
                        "void main() {\n" +
                        "    o_FragColor = u_Color;\n" +
                        "}\n";

        Shader shader = Shader.createFromSource(
                render,
                vertexShaderCode,
                fragmentShaderCode,
                null
        );

        // Set a bright green color with 60% opacity
        shader.setVec4("u_Color", new float[]{0.2f, 0.9f, 0.3f, 0.6f});

        return shader;

    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    // ⭐ MODIFIED: Update onDrawFrame to use the new shader
    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null) return;

        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        displayRotationHelper.updateSessionIfNeeded(session);

        Frame frame;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available", e);
            messageSnackbarHelper.showError(this, "Camera not available. Restart app.");
            return;
        }

        Camera camera = frame.getCamera();

        // Handle user tap for corner placement
        handleTapForCornerPlacement(frame, camera);

        // Configure background renderer
        try {
            backgroundRenderer.setUseDepthVisualization(
                    render, depthSettings.depthColorVisualizationEnabled());
            backgroundRenderer.setUseOcclusion(
                    render, depthSettings.useDepthForOcclusion());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read assets", e);
            messageSnackbarHelper.showError(this, "Failed to read assets: " + e);
            return;
        }
        backgroundRenderer.updateDisplayGeometry(frame);

        // Update depth texture if needed
        if (camera.getTrackingState() == TrackingState.TRACKING
                && (depthSettings.useDepthForOcclusion()
                || depthSettings.depthColorVisualizationEnabled())) {
            try (Image depthImage = frame.acquireDepthImage16Bits()) {
                backgroundRenderer.updateCameraDepthTexture(depthImage);
            } catch (NotYetAvailableException e) {
                // Depth not available yet
            }
        }

        trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

        // Update status message
        String message;
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            message = camera.getTrackingFailureReason() == TrackingFailureReason.NONE
                    ? SEARCHING_PLANE_MESSAGE
                    : TrackingStateHelper.getTrackingFailureReasonString(camera);
        } else if (hasTrackingPlane()) {
            message = cornerManager.getCornerCount() == 0 ? "Tap to place corners (1 of 4)..." : null;
        } else {
            message = SEARCHING_PLANE_MESSAGE;
        }

        if (message == null) {
            messageSnackbarHelper.hide(this);
        } else {
            messageSnackbarHelper.showMessage(this, message);
        }

        // Draw background
        if (frame.getTimestamp() != 0) {
            backgroundRenderer.drawBackground(render);
        }

        if (camera.getTrackingState() == TrackingState.PAUSED) return;

        // Get camera matrices
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(viewMatrix, 0);

        // Draw corner anchors
        for (CornerManager.WrappedAnchor wrappedAnchor : cornerManager.getCorners()) {
            drawAnchor(wrappedAnchor.getAnchor());
        }

        // Draw corner connection lines (white lines)
        if (!cornerLineMeshManager.isEmpty() && lineShader != null) {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            cornerLineMeshManager.drawAll(render, lineShader);
        }

        // ⭐ NEW: Draw grid overlay
        if (gridManager != null && cellOverlayShader != null && lineShader != null) {
            try {
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

                // Set matrix for both shaders
                cellOverlayShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

                // Draw cells individually with different colors based on visited state
                List<GridManager.GridCell> allCells = gridManager.getAllCells();
                for (int i = 0; i < allCells.size(); i++) {
                    GridManager.GridCell cell = allCells.get(i);

                    // Determine color based on visited state
                    float[] cellColor;
                    if (i < visitedCells.length && visitedCells[i]) {
                        cellColor = new float[]{0.3f, 0.8f, 0.4f, 0.7f}; // Green for visited
                    } else {
                        cellColor = new float[]{0.7f, 0.7f, 0.7f, 0.3f}; // Light gray for unvisited
                    }

                    // Draw individual cell
                    drawSingleCell(cell, cellOverlayShader, cellColor);
                }

                // Draw borders with white
                float[] borderColor = {1.0f, 1.0f, 1.0f, 1.0f}; // White, solid
                gridManager.drawBorders(render, lineShader, borderColor);

            } catch (Exception e) {
                Log.e(TAG, "Grid drawing failed: " + e.getMessage());
            }
        }

// ⭐ Draw floor using ACTUAL corner positions (not bounding box)
//        if (cornerManager.hasAllCorners() && cellOverlayShader != null) {
//            try {
//                float[] orderedCoordinates = cornerManager.getOrderedCorners();
//                if (orderedCoordinates != null && orderedCoordinates.length == 12) {
//                    // Get actual corners: topLeft, topRight, bottomLeft, bottomRight
//                    float[] p1 = Arrays.copyOfRange(orderedCoordinates, 0, 3);   // topLeft
//                    float[] p2 = Arrays.copyOfRange(orderedCoordinates, 3, 6);   // topRight
//                    float[] p3 = Arrays.copyOfRange(orderedCoordinates, 6, 9);   // bottomLeft
//                    float[] p4 = Arrays.copyOfRange(orderedCoordinates, 9, 12);  // bottomRight
//
//                    // Lift slightly above surface
//                    float offsetY = 0.01f;
//
//                    GLES30.glEnable(GLES30.GL_BLEND);
//                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
//                    GLES30.glDisable(GLES30.GL_DEPTH_TEST);
//                    GLES30.glDisable(GLES30.GL_CULL_FACE);
//
//                    Matrix.setIdentityM(modelMatrix, 0);
//                    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
//                    cellOverlayShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
//
//                    // Create quad using actual corners with FLIPPED winding
//                    // Corners: p1=topLeft, p2=topRight, p3=bottomLeft, p4=bottomRight
//                    float[] floorVertices = {
//                            // Triangle 1: topLeft -> bottomRight -> topRight (flipped)
//                            p1[0], p1[1] + offsetY, p1[2],
//                            p4[0], p4[1] + offsetY, p4[2],
//                            p2[0], p2[1] + offsetY, p2[2],
//
//                            // Triangle 2: topLeft -> bottomLeft -> bottomRight (flipped)
//                            p1[0], p1[1] + offsetY, p1[2],
//                            p3[0], p3[1] + offsetY, p3[2],
//                            p4[0], p4[1] + offsetY, p4[2]
//                    };
//
//                    FloatBuffer floorBuffer = ByteBuffer.allocateDirect(floorVertices.length * Float.BYTES)
//                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
//                    floorBuffer.put(floorVertices).position(0);
//
//                    VertexBuffer floorVb = new VertexBuffer(render, 3, floorBuffer);
//                    Mesh floorMesh = new Mesh(render, PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{floorVb});
//
//                    cellOverlayShader.setVec4("u_Color", new float[]{0.2f, 0.4f, 0.9f, 0.3f}); // Blue
//                    render.draw(floorMesh, cellOverlayShader);
//                    floorMesh.close();
//
//                    Log.d(TAG, "Drew floor using actual corner positions");
//
//                    GLES30.glEnable(GLES30.GL_DEPTH_TEST);
//                    GLES30.glEnable(GLES30.GL_CULL_FACE);
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Floor overlay failed: " + e.getMessage(), e);
//            }
//        }
        // ────────────────────────────────────────────────────────────────────────
////  Draw visited-cell overlays (mirrors the floor-quad drawing style)
//// ────────────────────────────────────────────────────────────────────────
// ⭐ DEBUG: Draw a BRIGHT YELLOW marker at each visited cell center
//        if (!visitedCellMeshManager.isEmpty() && cellOverlayShader != null) {
//            try {
//                // Get visited cell positions from manager
//                float[] orderedCoordinates = cornerManager.getOrderedCorners();
//                if (orderedCoordinates != null) {
//                    float[] topLeft = Arrays.copyOfRange(orderedCoordinates, 0, 3);
//                    float[] topRight = Arrays.copyOfRange(orderedCoordinates, 3, 6);
//                    float[] bottomLeft = Arrays.copyOfRange(orderedCoordinates, 6, 9);
//                    float[] bottomRight = Arrays.copyOfRange(orderedCoordinates, 9, 12);
//
//                    // Draw small markers at cell positions from logs
//                    float[][] testCells = {
//                            {0.162f, -0.454f, -0.271f},  // Cell 18
//                            {0.197f, -0.454f, -0.271f},  // Cell 19
//                            {0.158f, -0.454f, -0.235f}   // Cell 25
//                    };
//
//                    GLES30.glDisable(GLES30.GL_DEPTH_TEST);
//                    GLES30.glDisable(GLES30.GL_CULL_FACE);
//                    GLES30.glEnable(GLES30.GL_BLEND);
//                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
//
//                    Matrix.setIdentityM(modelMatrix, 0);
//                    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
//                    cellOverlayShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
//
//                    for (float[] cellPos : testCells) {
//                        float size = 0.05f;
//                        float[] markerVerts = {
//                                cellPos[0], cellPos[1], cellPos[2],
//                                cellPos[0] + size, cellPos[1], cellPos[2],
//                                cellPos[0] + size/2, cellPos[1] + size, cellPos[2]
//                        };
//
//                        FloatBuffer buf = ByteBuffer.allocateDirect(markerVerts.length * Float.BYTES)
//                                .order(ByteOrder.nativeOrder()).asFloatBuffer();
//                        buf.put(markerVerts).position(0);
//
//                        VertexBuffer vb = new VertexBuffer(render, 3, buf);
//                        Mesh mesh = new Mesh(render, PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{vb});
//
//                        cellOverlayShader.setVec4("u_Color", new float[]{1.0f, 1.0f, 0.0f, 1.0f}); // BRIGHT YELLOW
//                        render.draw(mesh, cellOverlayShader);
//                        mesh.close();
//                    }
//
//                    Log.d(TAG, "Drew yellow cell markers");
//                }
//            } catch (Exception e) {
//                Log.e(TAG, "Cell marker failed: " + e.getMessage());
//            }
//        }

// ⭐ RESTORE ALL OpenGL state
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
    }

    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING
                    && plane.isPoseInPolygon(plane.getCenterPose())) {
                return true;
            }
        }
        return false;
    }

    private void showToastOnUiThread(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void launchInstantPlacementSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_instant_placement)
                .setMultiChoiceItems(
                        resources.getStringArray(R.array.instant_placement_options_array),
                        instantPlacementSettingsMenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(R.string.done,
                        (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                .setNegativeButton(android.R.string.cancel,
                        (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                .show();
    }

    private void launchDepthSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_with_depth)
                    .setMultiChoiceItems(
                            resources.getStringArray(R.array.depth_options_array),
                            depthSettingsMenuDialogCheckboxes,
                            (DialogInterface dialog, int which, boolean isChecked) ->
                                    depthSettingsMenuDialogCheckboxes[which] = isChecked)
                    .setPositiveButton(R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .setNegativeButton(android.R.string.cancel,
                            (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.options_title_without_depth)
                    .setPositiveButton(R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .show();
        }
    }

    private void resetSettingsMenuDialogCheckboxes() {
        depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
        depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
        instantPlacementSettingsMenuDialogCheckboxes[0] =
                instantPlacementSettings.isInstantPlacementEnabled();
    }

    private void applySettingsMenuDialogCheckboxes() {
        depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
        depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
        instantPlacementSettings.setInstantPlacementEnabled(
                instantPlacementSettingsMenuDialogCheckboxes[0]);
        configureSession();
    }

    private void configureSession() {
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        if (instantPlacementSettings.isInstantPlacementEnabled()) {
            config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);
        } else {
            config.setInstantPlacementMode(Config.InstantPlacementMode.DISABLED);
        }
        session.configure(config);
    }

    // ============================================================================
// Custom 2D Grid View - Inner class inside HelloArActivity
// Place this at the end of HelloArActivity class, before the final closing brace
// ============================================================================

    /**
     * Custom2DGridView - Interactive 2D grid overlay for marking visited cells
     * This is an inner class of HelloArActivity
     */
    private class Custom2DGridView extends View {
        private final Pose[] boundaryPoses;
        private final List<GridCell2D> cells = new ArrayList<>();
        private Paint borderPaint;
        private Paint textPaint;
        private float cellViewSize;

        /**
         * Represents a single cell in the 2D grid view
         */
        private static class GridCell2D {
            public final int cellNumber;
            public final int row;
            public final int col;
            public boolean visited;
            public Paint fillPaint;
            public RectF rect;

            public GridCell2D(int cellNumber, int row, int col, RectF rect) {
                this.cellNumber = cellNumber;
                this.row = row;
                this.col = col;
                this.rect = rect;
                this.visited = false;
                this.fillPaint = new Paint();
                this.fillPaint.setColor(Color.WHITE);
                this.fillPaint.setStyle(Paint.Style.FILL);
                this.fillPaint.setShadowLayer(5f, 0f, 0f, Color.GRAY);
            }

            /**
             * Toggle the visited state of this cell
             */
            public void toggleVisited() {
                this.visited = !this.visited;
                this.fillPaint.setColor(visited ? Color.parseColor("#4CAF50") : Color.WHITE);
            }
        }

        /**
         * Constructor
         * @param context Android context
         * @param poses Array of 4 boundary poses (corners)
         */
        public Custom2DGridView(Context context, Pose[] poses) {
            super(context);
            this.boundaryPoses = poses;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null); // Enable shadow rendering
            init();
        }

        /**
         * Initialize paints for drawing
         */
        private void init() {
            // Border paint for cell outlines
            borderPaint = new Paint();
            borderPaint.setColor(Color.DKGRAY);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);

            // Text paint for cell numbers
            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
        }

        /**
         * Update visited state from external array
         * @param visitedState Boolean array indicating which cells are visited
         */
        public void updateVisitedCells(boolean[] visitedState) {
            for (int i = 0; i < cells.size() && i < visitedState.length; i++) {
                GridCell2D cell = cells.get(i);
                if (cell.visited != visitedState[i]) {
                    cell.visited = visitedState[i];
                    cell.fillPaint.setColor(cell.visited ? Color.parseColor("#4CAF50") : Color.WHITE);
                }
            }
            invalidate(); // Request redraw
        }

        /**
         * Get current visited state of all cells
         * @return Boolean array indicating which cells are visited
         */
        public boolean[] getVisitedState() {
            boolean[] state = new boolean[GRID_ROWS * GRID_COLS];
            for (int i = 0; i < cells.size() && i < state.length; i++) {
                state[i] = cells.get(i).visited;
            }
            return state;
        }

        /**
         * Called when view size changes
         * Generates the grid layout based on available space
         */
        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w == 0 || h == 0) return;

            // Calculate grid dimensions to fit in the view
            int padding = 40;
            int gridSize = Math.min(w, h) - 2 * padding;

            float startX = (w - gridSize) / 2f;
            float startY = (h - gridSize) / 2f;

            cellViewSize = (float) gridSize / GRID_COLS;
            textPaint.setTextSize(cellViewSize / 4);

            generate2DGrid(startX, startY, cellViewSize);
        }

        /**
         * Generate the 2D grid layout
         * @param startX Starting X coordinate
         * @param startY Starting Y coordinate
         * @param cellSize Size of each cell
         */
        private void generate2DGrid(float startX, float startY, float cellSize) {
            cells.clear();
            int cellCount = 0;

            for (int row = 0; row < GRID_ROWS; row++) {
                for (int col = 0; col < GRID_COLS; col++) {
                    // Calculate cell boundaries
                    float left = startX + col * cellSize;
                    float top = startY + row * cellSize;
                    float right = left + cellSize;
                    float bottom = top + cellSize;

                    RectF rect = new RectF(left, top, right, bottom);
                    GridCell2D cell = new GridCell2D(cellCount + 1, row, col, rect);

                    // Apply current visited state if available
                    if (cellCount < visitedCells.length && visitedCells[cellCount]) {
                        cell.visited = true;
                        cell.fillPaint.setColor(Color.parseColor("#4CAF50"));
                    }

                    cells.add(cell);
                    cellCount++;
                }
            }
            Log.i(TAG, "2D Grid of " + GRID_ROWS * GRID_COLS + " cells generated.");
        }

        /**
         * Draw the grid
         */
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (cells.isEmpty()) {
                canvas.drawText("Generating Grid...", getWidth() / 2f, getHeight() / 2f, textPaint);
                return;
            }

            // Draw each cell
            for (GridCell2D cell : cells) {
                // Draw filled rectangle
                canvas.drawRoundRect(cell.rect, 10f, 10f, cell.fillPaint);

                // Draw border
                canvas.drawRoundRect(cell.rect, 10f, 10f, borderPaint);

                // Draw cell number
                String num = String.valueOf(cell.cellNumber);
                float x = cell.rect.centerX();
                float textHeight = textPaint.descent() - textPaint.ascent();
                float y = cell.rect.centerY() + (textHeight / 2) - textPaint.descent();

                canvas.drawText(num, x, y, textPaint);
            }
        }

        /**
         * Handle touch events to toggle cell visited state
         */
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float touchX = event.getX();
                float touchY = event.getY();

                // Check if touch is within any cell
                for (GridCell2D cell : cells) {
                    if (cell.rect.contains(touchX, touchY)) {
                        // Toggle visited state
                        cell.toggleVisited();

                        // Update the global visited state array
                        int cellIndex = cell.row * GRID_COLS + cell.col;
                        if (cellIndex < visitedCells.length) {
                            visitedCells[cellIndex] = cell.visited;
                        }

                        // Request redraw
                        invalidate();

                        // Show feedback
                        Toast.makeText(getContext(), "Cell " + cell.cellNumber +
                                (cell.visited ? " Visited!" : " Unvisited!"), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
            }
            return super.onTouchEvent(event);
        }
    }

// End of Custom2DGridView class
// This goes inside HelloArActivity, before the final closing brace
}