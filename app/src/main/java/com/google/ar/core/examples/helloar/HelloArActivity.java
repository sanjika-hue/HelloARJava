package com.google.ar.core.examples.helloar;

import android.content.Intent;
import android.content.DialogInterface;
import android.content.res.Resources;
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
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.TextView;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI
        btnDone = findViewById(R.id.btnDone);
        tvInstructions = findViewById(R.id.tvInstructions);
        tvDistance = findViewById(R.id.tvDistance);
        surfaceView = findViewById(R.id.surfaceview);

        // Initialize managers
        cornerManager = new CornerManager();
        cornerLineMeshManager = new MeshManager(surfaceView);
        visitedCellMeshManager = new MeshManager(surfaceView);
        floorOverlayMeshManager = new MeshManager(surfaceView);
        visitedCellManager = new VisitedCellManager(surfaceView, cornerManager, visitedCellMeshManager);

        // Set up UI listeners
        btnDone.setOnClickListener(v -> onDoneClicked());

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
    private void updateInstructions() {
        runOnUiThread(() -> {
            int cornerCount = cornerManager.getCornerCount();
            if (cornerCount < 4) {
                tvInstructions.setText("Tap to place corner anchor " + (cornerCount + 1) + " of 4");
                btnDone.setEnabled(false);
            } else {
                tvInstructions.setText("All 4 corners placed. Press 'Done' to launch navigation.");
                btnDone.setEnabled(true);
            }
            tvDistance.setVisibility(View.GONE);
        });
    }

    private void onDoneClicked() {
        Log.d(TAG, "onDoneClicked: Starting...");

        if (!cornerManager.hasAllCorners()) {
            Log.w(TAG, "Cannot proceed: only " + cornerManager.getCornerCount() + " corners placed");
            Toast.makeText(this, "Please place exactly 4 corners before pressing Done.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        float[] orderedCoordinates = cornerManager.getOrderedCorners();

        if (orderedCoordinates == null || orderedCoordinates.length != 12) {
            Log.e(TAG, "Error: orderedCoordinates is null or wrong length");
            Toast.makeText(this, "Error in corner ordering. Try placing corners again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Extract corner coordinates
        float[] corner1 = Arrays.copyOfRange(orderedCoordinates, 0, 3);
        float[] corner2 = Arrays.copyOfRange(orderedCoordinates, 3, 6);
        float[] corner3 = Arrays.copyOfRange(orderedCoordinates, 6, 9);
        float[] corner4 = Arrays.copyOfRange(orderedCoordinates, 9, 12);

        // Launch grid navigation
        Intent intent = new Intent(this, GridNavigationActivity.class);
        intent.putExtra("corner1", corner1);
        intent.putExtra("corner2", corner2);
        intent.putExtra("corner3", corner3);
        intent.putExtra("corner4", corner4);

        Log.d(TAG, "Starting GridNavigationActivity with corner data...");
        startActivityForResult(intent, GRID_NAVIGATION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GRID_NAVIGATION_REQUEST && resultCode == RESULT_OK && data != null) {
            try {
                ArrayList<GridCellData> visitedCells =
                        data.getParcelableArrayListExtra("visitedCells");

                if (visitedCells != null && !visitedCells.isEmpty()) {
                    Log.d(TAG, "Received " + visitedCells.size() + " cell data entries");

                    // Process visited cells using manager
                    visitedCellManager.processVisitedCells(visitedCells, render);

                    // Count visited cells
                    int visitedCount = 0;
                    for (GridCellData cell : visitedCells) {
                        if (cell.visited) visitedCount++;
                    }

                    showToastOnUiThread("Showing " + visitedCount + " visited cells in AR");
                } else {
                    Log.w(TAG, "No visited cell data received");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing visited cells: " + e.getMessage(), e);
                showToastOnUiThread("Error loading visited cells");
            }
        }
    }

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
// ⭐ Draw floor using ACTUAL corner positions (not bounding box)
        if (cornerManager.hasAllCorners() && cellOverlayShader != null) {
            try {
                float[] orderedCoordinates = cornerManager.getOrderedCorners();
                if (orderedCoordinates != null && orderedCoordinates.length == 12) {
                    // Get actual corners: topLeft, topRight, bottomLeft, bottomRight
                    float[] p1 = Arrays.copyOfRange(orderedCoordinates, 0, 3);   // topLeft
                    float[] p2 = Arrays.copyOfRange(orderedCoordinates, 3, 6);   // topRight
                    float[] p3 = Arrays.copyOfRange(orderedCoordinates, 6, 9);   // bottomLeft
                    float[] p4 = Arrays.copyOfRange(orderedCoordinates, 9, 12);  // bottomRight

                    // Lift slightly above surface
                    float offsetY = 0.02f;

                    GLES30.glEnable(GLES30.GL_BLEND);
                    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
                    GLES30.glDisable(GLES30.GL_DEPTH_TEST);
                    GLES30.glDisable(GLES30.GL_CULL_FACE);

                    Matrix.setIdentityM(modelMatrix, 0);
                    Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
                    cellOverlayShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

                    // Create quad using actual corners with FLIPPED winding
                    // Corners: p1=topLeft, p2=topRight, p3=bottomLeft, p4=bottomRight
                    float[] floorVertices = {
                            // Triangle 1: topLeft -> bottomRight -> topRight (flipped)
                            p1[0], p1[1] + offsetY, p1[2],
                            p4[0], p4[1] + offsetY, p4[2],
                            p2[0], p2[1] + offsetY, p2[2],

                            // Triangle 2: topLeft -> bottomLeft -> bottomRight (flipped)
                            p1[0], p1[1] + offsetY, p1[2],
                            p3[0], p3[1] + offsetY, p3[2],
                            p4[0], p4[1] + offsetY, p4[2]
                    };

                    FloatBuffer floorBuffer = ByteBuffer.allocateDirect(floorVertices.length * Float.BYTES)
                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                    floorBuffer.put(floorVertices).position(0);

                    VertexBuffer floorVb = new VertexBuffer(render, 3, floorBuffer);
                    Mesh floorMesh = new Mesh(render, PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{floorVb});

                    cellOverlayShader.setVec4("u_Color", new float[]{0.2f, 0.4f, 0.9f, 0.6f}); // Blue
                    render.draw(floorMesh, cellOverlayShader);
                    floorMesh.close();

                    Log.d(TAG, "Drew floor using actual corner positions");

                    GLES30.glEnable(GLES30.GL_DEPTH_TEST);
                    GLES30.glEnable(GLES30.GL_CULL_FACE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Floor overlay failed: " + e.getMessage(), e);
            }
        }
// ────────────────────────────────────────────────────────────────────────
//  Draw visited-cell overlays (mirrors the floor-quad drawing style)
// ────────────────────────────────────────────────────────────────────────
        if (!visitedCellMeshManager.isEmpty() && cellOverlayShader != null) {
            // 1. Build the MVP exactly like the floor quad
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0,
                    projectionMatrix, 0, viewMatrix, 0);

            // 2. Same GL state as the floor overlay
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);   // depth test off → always draw on top
            GLES30.glDepthFunc(GLES30.GL_ALWAYS);    // (kept for completeness)
            GLES30.glDepthMask(false);
            GLES30.glDisable(GLES30.GL_CULL_FACE);

            // 3. Shader uniforms
            cellOverlayShader.setVec4("u_Color",
                    new float[]{0.2f, 0.9f, 0.3f, 0.8f});               // green overlay
            cellOverlayShader.setMat4("u_ModelViewProjection",
                    modelViewProjectionMatrix);

            // 4. Draw all active visited-cell meshes
            visitedCellMeshManager.drawAll(render, cellOverlayShader);

            Log.d(TAG, "Drew " + visitedCellMeshManager.getActiveCount()
                    + " visited cells");

            // 5. **Leave the GL state clean** – the final restore block at the
            //    bottom of onDrawFrame will put everything back to the global
            //    defaults, but we also reset what we touched locally.
            GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            GLES30.glDepthFunc(GLES30.GL_LEQUAL);
            GLES30.glDepthMask(true);
            GLES30.glEnable(GLES30.GL_CULL_FACE);
        }
//

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
}