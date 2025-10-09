package com.google.ar.core.examples.helloar;

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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Pose;
import com.google.ar.core.ArCoreApk.Availability;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.InstantPlacementMode;
import com.google.ar.core.Frame;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.helloar.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.helloar.common.helpers.DepthSettings;
import com.google.ar.core.examples.helloar.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.helloar.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.helloar.common.helpers.InstantPlacementSettings;
import com.google.ar.core.examples.helloar.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.helloar.common.helpers.TapHelper;
import com.google.ar.core.examples.helloar.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.helloar.common.samplerender.Framebuffer;
import com.google.ar.core.examples.helloar.common.samplerender.GLError;
import com.google.ar.core.examples.helloar.common.samplerender.Mesh;
import com.google.ar.core.examples.helloar.common.samplerender.SampleRender;
import com.google.ar.core.examples.helloar.common.samplerender.Shader;
import com.google.ar.core.examples.helloar.common.samplerender.Texture;
import com.google.ar.core.examples.helloar.common.samplerender.VertexBuffer;
import com.google.ar.core.examples.helloar.common.samplerender.arcore.BackgroundRenderer;
import com.google.ar.core.examples.helloar.common.samplerender.arcore.PlaneRenderer;
import com.google.ar.core.examples.helloar.common.samplerender.arcore.SpecularCubemapFilter;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.nio.FloatBuffer;
import android.widget.TextView;
import com.google.ar.core.HitResult;
import android.view.MotionEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {

    private static final String TAG = HelloArActivity.class.getSimpleName();
    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";

    private static final float[] sphericalHarmonicFactors = {
            0.282095f, -0.325735f, 0.325735f, -0.325735f, 0.273137f,
            -0.273137f, 0.078848f, -0.273137f, 0.136569f,
    };

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100f;
    private static final int CUBEMAP_RESOLUTION = 16;
    private static final int CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32;

    private GLSurfaceView surfaceView;
    private boolean installRequested;
    private Session session;
    private Mesh gridQuadMesh;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;
    private SampleRender render;
    private Button btnDone;

    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;
    private final List<Mesh> cornerLineMeshes = new ArrayList<>();

    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];

    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];

    // private boolean anchorsPlaced = false;
    // private boolean hasShownPlacementToast = false;

    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    private long lastPointCloudTimestamp = 0;
    private Shader lineShader;
    private final List<Mesh> gridLineMeshes = new ArrayList<>();
    private Mesh virtualObjectMesh;
    private Shader virtualObjectShader;
    private Texture virtualObjectAlbedoTexture;
    private Texture virtualObjectAlbedoInstantPlacementTexture;

    // private final List<WrappedAnchor> wrappedAnchors = new ArrayList<>();

    private Texture dfgTexture;
    private Shader gridShader;

    private SpecularCubemapFilter cubemapFilter;
    private TextView tvInstructions;
    private TextView tvDistance;
    // Separate corner anchors from grid anchors
    private final List<WrappedAnchor> cornerAnchors = new ArrayList<>();
    private final List<GridCell> gridCells = new ArrayList<>();

    // Grid state tracking
    private static final int GRID_ROWS = 8;
    private static final int GRID_COLS = 8;
    private int currentTargetCell = 1;
    private boolean gridCreated = false;

    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    private final float[] sphericalHarmonicsCoefficients = new float[9 * 3];
    private final float[] viewInverseMatrix = new float[16];
    private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] viewLightDirection = new float[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnDone = findViewById(R.id.btnDone);
        tvInstructions = findViewById(R.id.tvInstructions);
        tvDistance = findViewById(R.id.tvDistance);
        //  btnDone.setOnClickListener(v -> logFloorPolygon());
        btnDone.setOnClickListener(v -> onDoneClicked());

        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(this);

        tapHelper = new TapHelper(this);
        surfaceView.setOnTouchListener(tapHelper);

        render = new SampleRender(surfaceView, this, getAssets());
        installRequested = false;

        depthSettings.onCreate(this);
        instantPlacementSettings.onCreate(this);

        ImageButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(HelloArActivity.this, v);
            popup.setOnMenuItemClickListener(HelloArActivity.this::settingsMenuClick);
            popup.inflate(R.menu.settings_menu);
            popup.show();

        });
        updateInstructions();
    }

    private void updateInstructions() {
        runOnUiThread(() -> {
            if (cornerAnchors.size() < 4) {
                tvInstructions.setText("Tap to place corner anchor " + (cornerAnchors.size() + 1) + " of 4");
                btnDone.setEnabled(false);
            } else if (!gridCreated) {
                tvInstructions.setText("Press 'Done' to create 8x8 grid");
                btnDone.setEnabled(true);
            } else {
                tvInstructions.setText("Navigate to Grid Cell " + currentTargetCell);
                btnDone.setEnabled(false);
            }
        });
    }

    private void onDoneClicked() {
        if (cornerAnchors.size() == 4 && !gridCreated) {
            createGrid();
            gridCreated = true;
            updateInstructions();
        }
    }

    private void createGrid() {
        if (cornerAnchors.size() != 4) {
            showToastOnUiThread("Need exactly 4 corner anchors");
            return;
        }

        float[] p1 = cornerAnchors.get(0).getAnchor().getPose().getTranslation();
        float[] p2 = cornerAnchors.get(1).getAnchor().getPose().getTranslation();
        float[] p3 = cornerAnchors.get(2).getAnchor().getPose().getTranslation();
        float[] p4 = cornerAnchors.get(3).getAnchor().getPose().getTranslation();

        int cellNumber = 1;

        for (int row = 0; row < GRID_ROWS; row++) {
            float rowFraction = (row + 0.5f) / GRID_ROWS; // center of cell

            // Interpolate left and right edges
            float[] left = new float[3];
            float[] right = new float[3];
            for (int i = 0; i < 3; i++) {
                left[i] = p1[i] + (p3[i] - p1[i]) * rowFraction; // TL -> BL
                right[i] = p2[i] + (p4[i] - p2[i]) * rowFraction; // TR -> BR
            }

            for (int col = 0; col < GRID_COLS; col++) {
                float colFraction = (col + 0.5f) / GRID_COLS;

                // Compute position
                float[] pos = new float[3];
                for (int i = 0; i < 3; i++) {
                    pos[i] = left[i] + (right[i] - left[i]) * colFraction;
                }

                // Create anchor
                try {
                    Pose pose = Pose.makeTranslation(pos[0], pos[1], pos[2]);
                    Anchor anchor = session.createAnchor(pose);
                    gridCells.add(new GridCell(cellNumber, row, col, anchor));
                    cellNumber++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create grid cell: " + e.getMessage());
                }
            }
        }


        //createGridLineMeshes(render);
        createCornerConnectionLines(render);

        showToastOnUiThread("Grid created with " + gridCells.size() + " cells");

    }

    private void handleTapForCornerPlacement(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();  // get the tap
        if (tap == null || camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        float x = tap.getX();
        float y = tap.getY();

        for (HitResult hit : frame.hitTest(x, y)) {  // use x, y instead of MotionEvent
            Trackable trackable = hit.getTrackable();
            if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) ||
                    (trackable instanceof PointCloud)) {

                Anchor anchor = hit.createAnchor();
                cornerAnchors.add(new WrappedAnchor(anchor, trackable));
                Log.d("CornerLine1", "cornerAnchors.size() = " + cornerAnchors.size());
                showToastOnUiThread("Corner " + cornerAnchors.size() + " placed");
                updateInstructions();
                break;
            }
        }
    }


    private void calculateDistanceToTargetCell(Camera camera) {
        if (!gridCreated || gridCells.isEmpty()) return;

        GridCell targetCell = null;
        for (GridCell cell : gridCells) {
            if (cell.cellNumber == currentTargetCell) {
                targetCell = cell;
                break;
            }
        }

        if (targetCell == null) return;

        float[] cameraPos = camera.getPose().getTranslation();
        float[] cellPos = targetCell.anchor.getPose().getTranslation();

        float dx = cellPos[0] - cameraPos[0];
        float dy = cellPos[1] - cameraPos[1];
        float dz = cellPos[2] - cameraPos[2];

        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        runOnUiThread(() -> {
            tvDistance.setText(String.format("Distance to Cell %d: %.2f meters", currentTargetCell, distance));
            tvDistance.setVisibility(View.VISIBLE);
        });

        if (distance < 0.5f && !targetCell.visited) { // only if not already visited
            // MARK CELL AS VISITED
            targetCell.visited = true;
            targetCell.color = new float[]{1f, 0f, 0f, 1f}; // red color, fully opaque
            Log.d("GridCheck12", "Cell visited at index: " + targetCell.cellNumber);
            // MOVE TO NEXT CELL
            if (currentTargetCell < GRID_ROWS * GRID_COLS) {
                currentTargetCell++;
                updateInstructions();
                showToastOnUiThread("Cell reached! Moving to cell " + currentTargetCell);
            }
        }

    }



    private void createGridQuadMesh(SampleRender render) {
        float[] vertices = {
                -0.5f, 0, -0.5f,
                0.5f, 0, -0.5f,
                -0.5f, 0,  0.5f,
                0.5f, 0,  0.5f
        };

        // Allocate a direct ByteBuffer and convert to FloatBuffer
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.rewind(); // Important: reset position to 0

        VertexBuffer vb = new VertexBuffer(render, 3, vertexBuffer);
        gridQuadMesh = new Mesh(render, Mesh.PrimitiveMode.TRIANGLE_STRIP, null, new VertexBuffer[]{vb});
    }
    private void createCornerConnectionLines(SampleRender render) {
        if (cornerAnchors.size() != 4) return;

        // Clear old corner line meshes
        cornerLineMeshes.clear();

        // Get corner positions
        float[] p1 = cornerAnchors.get(0).getAnchor().getPose().getTranslation(); // Corner 1
        float[] p2 = cornerAnchors.get(1).getAnchor().getPose().getTranslation(); // Corner 2
        float[] p3 = cornerAnchors.get(2).getAnchor().getPose().getTranslation(); // Corner 3
        float[] p4 = cornerAnchors.get(3).getAnchor().getPose().getTranslation(); // Corner 4

        try {
            // Create lines connecting corners in order: 1->2->3->4->1
            createCornerLineMesh(p1, p2, render); // Corner 1 to 2
            createCornerLineMesh(p2, p4, render); // Corner 2 to 4
            createCornerLineMesh(p4, p3, render); // Corner 4 to 3
            createCornerLineMesh(p3, p1, render); // Corner 3 to 1
            Log.d("CornerLine12", "Created " + cornerLineMeshes.size() + " corner connection lines");

            Log.d("CornerLine", "Number of corner lines: " + cornerLineMeshes.size());

        } catch (Exception e) {
            Log.e("CornerLine", "Failed to create corner lines: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /*private void createCornerLineMesh(float[] start, float[] end, SampleRender render) {
        float[] lineVertices = {start[0], start[1], start[2], end[0], end[1], end[2]};
        FloatBuffer vertexBuffer = FloatBuffer.wrap(lineVertices);
        VertexBuffer vb = new VertexBuffer(render, 3, vertexBuffer);
        Mesh lineMesh = new Mesh(render, Mesh.PrimitiveMode.LINES, null, new VertexBuffer[]{vb});
        cornerLineMeshes.add(lineMesh);
    }*/
    private void createCornerLineMesh(float[] start, float[] end, SampleRender render) {
        float[] lineVertices = {start[0], start[1], start[2], end[0], end[1], end[2]};

        // Create a direct float buffer
        FloatBuffer vertexBuffer = ByteBuffer
                .allocateDirect(lineVertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(lineVertices);
        vertexBuffer.position(0);

        VertexBuffer vb = new VertexBuffer(render, 3, vertexBuffer);
        Mesh lineMesh = new Mesh(render, Mesh.PrimitiveMode.LINES, null, new VertexBuffer[]{vb});
        cornerLineMeshes.add(lineMesh);

        Log.d("CornerLineMesh", "Mesh added. Total corner meshes: " + cornerLineMeshes.size());
    }



    private void drawGridCellPlane(GridCell cell, SampleRender render) {
        if (cell.anchor.getTrackingState() != TrackingState.TRACKING) return;

        Log.d("GridDebug", "Cell " + cell.cellNumber + " color: " + Arrays.toString(cell.color));

        // Get anchor model matrix
        float[] modelMatrix = new float[16];
        cell.anchor.getPose().toMatrix(modelMatrix, 0);

        // Scale the plane to the size of the grid cell
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        float cellSize = 0.25f; // adjust for your grid
        Matrix.scaleM(scaleMatrix, 0, cellSize, 0.01f, cellSize);

        float[] modelScaleMatrix = new float[16];
        Matrix.multiplyMM(modelScaleMatrix, 0, modelMatrix, 0, scaleMatrix, 0);

        // Compute ModelViewProjection
        float[] modelViewMatrix = new float[16];
        float[] modelViewProjectionMatrix = new float[16];
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelScaleMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

        // **Use the shader before setting uniforms**

        gridShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
        gridShader.setVec4("u_Color", cell.color);

        // Draw the grid quad
        render.draw(gridQuadMesh, gridShader, virtualSceneFramebuffer);

        Log.d("GridDebug", "Drawing cell at: " + Arrays.toString(modelMatrix));
    }


    private void drawAnchor(Anchor anchor, SampleRender render) {
        if (anchor.getTrackingState() != TrackingState.TRACKING) return;

        anchor.getPose().toMatrix(modelMatrix, 0);

        // Apply scaling
        float scale = 0.2f; // adjust this value as needed
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        Matrix.scaleM(scaleMatrix, 0, scale, scale, scale);

        float[] scaledModelMatrix = new float[16];
        Matrix.multiplyMM(scaledModelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);

        // Multiply with view and projection matrices
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, scaledModelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

        virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
        virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
        virtualObjectShader.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture);

        render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
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
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            try {
                session = new Session(this);
            } catch (UnavailableArcoreNotInstalledException e) {
                messageSnackbarHelper.showError(this, "Install Google Play Services for AR");
                return;
            } catch (UnavailableApkTooOldException e) {
                messageSnackbarHelper.showError(this, "Update Google Play Services for AR");
                return;
            } catch (UnavailableDeviceNotCompatibleException e) {
                messageSnackbarHelper.showError(this, "AR not supported on this device");
                return;
            } catch (Exception e) {
                messageSnackbarHelper.showError(this, "Failed to create AR session");
                return;
            }
        }

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                Availability availability = ArCoreApk.getInstance().checkAvailability(this);

                if (availability != Availability.SUPPORTED_INSTALLED) {
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
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
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

    public void onSurfaceCreated(SampleRender render) {
        try {
            // Existing renderers and framebuffers
            planeRenderer = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, 1, 1);
            createGridQuadMesh(render);

            cubemapFilter = new SpecularCubemapFilter(render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES);

            dfgTexture = new Texture(render, Texture.Target.TEXTURE_2D, Texture.WrapMode.CLAMP_TO_EDGE, false);

            final int dfgResolution = 64;
            final int dfgChannels = 2;
            final int halfFloatSize = 2;

            ByteBuffer buffer = ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize);
            try (InputStream is = getAssets().open("models/dfg.raw")) {
                is.read(buffer.array());
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId());
            GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture");
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG16F, dfgResolution, dfgResolution, 0, GLES30.GL_RG, GLES30.GL_HALF_FLOAT, buffer);
            GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D");

            // Point cloud shader
            pointCloudShader = Shader.createFromAssets(render, "shaders/point_cloud.vert", "shaders/point_cloud.frag", null)
                    .setVec4("u_Color", new float[]{31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
                    .setFloat("u_PointSize", 5.0f);

            pointCloudVertexBuffer = new VertexBuffer(render, 4, null);
            final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
            pointCloudMesh = new Mesh(render, Mesh.PrimitiveMode.POINTS, null, pointCloudVertexBuffers);

            // Virtual object textures
            virtualObjectAlbedoTexture = Texture.createFromAsset(render, "models/pawn_albedo.png", Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.SRGB);
            virtualObjectAlbedoInstantPlacementTexture = Texture.createFromAsset(render, "models/pawn_albedo_instant_placement.png", Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.SRGB);
            Texture virtualObjectPbrTexture = Texture.createFromAsset(render, "models/pawn_roughness_metallic_ao.png", Texture.WrapMode.CLAMP_TO_EDGE, Texture.ColorFormat.LINEAR);

            // Virtual object mesh and shader
            virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
            virtualObjectShader = Shader.createFromAssets(render, "shaders/environmental_hdr.vert", "shaders/environmental_hdr.frag",
                            new HashMap<String, String>() {{
                                put("NUMBER_OF_MIPMAP_LEVELS", Integer.toString(cubemapFilter.getNumberOfMipmapLevels()));
                            }})
                    .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
                    .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
                    .setTexture("u_Cubemap", cubemapFilter.getFilteredCubemapTexture())
                    .setTexture("u_DfgTexture", dfgTexture);

            // ====== ADD GRID SHADER INITIALIZATION HERE ======
            gridShader = Shader.createFromAssets(
                    render,
                    "shaders/grid_color.vert",
                    "shaders/grid_color.frag",
                    null
            );
            lineShader = Shader.createFromAssets(
                    render,
                    "shaders/line.vert",   // Your vertex shader for lines
                    "shaders/line.frag",   // Your fragment shader for lines
                    null
            );


        } catch (IOException e) {
            Log.e(TAG, "Failed to load shader", e);
            messageSnackbarHelper.showError(this, "Failed to load shader: " + e);
        }
    }





    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null) return;

        if (!hasSetTextureNames) {
            session.setCameraTextureNames(new int[]{backgroundRenderer.getCameraColorTexture().getTextureId()});
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
        // Handle taps to place corner anchors
        if (cornerAnchors.size() < 4) {
            handleTapForCornerPlacement(frame, camera);
        }

// Calculate distance to target cell if grid is created
        if (gridCreated) {
            calculateDistanceToTargetCell(camera);
        }

        // Detect floor and place grid anchors automatically (ONE TIME)
        // float floorY = Float.MAX_VALUE; // Default value since we don't auto-detect floor anymore

        try {
            backgroundRenderer.setUseDepthVisualization(render, depthSettings.depthColorVisualizationEnabled());
            backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read assets", e);
            messageSnackbarHelper.showError(this, "Failed to read assets: " + e);
            return;
        }
        backgroundRenderer.updateDisplayGeometry(frame);

        if (camera.getTrackingState() == TrackingState.TRACKING
                && (depthSettings.useDepthForOcclusion() || depthSettings.depthColorVisualizationEnabled())) {
            try (Image depthImage = frame.acquireDepthImage16Bits()) {
                backgroundRenderer.updateCameraDepthTexture(depthImage);
            } catch (NotYetAvailableException e) {
                // Depth not available
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
            message = (cornerAnchors.isEmpty() && gridCells.isEmpty()) ? "Tap to place corners..." : null;
        } else {
            message = SEARCHING_PLANE_MESSAGE;
        }

        if (message == null) {
            messageSnackbarHelper.hide(this);
        } else {
            messageSnackbarHelper.showMessage(this, message);
        }

        if (frame.getTimestamp() != 0) {
            backgroundRenderer.drawBackground(render);
        }
        if (camera.getTrackingState() == TrackingState.PAUSED) return;

        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(viewMatrix, 0);

        try (PointCloud pointCloud = frame.acquirePointCloud()) {
            if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
                pointCloudVertexBuffer.set(pointCloud.getPoints());
                lastPointCloudTimestamp = pointCloud.getTimestamp();
            }
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            render.draw(pointCloudMesh, pointCloudShader);
        }
        Plane floorPlane = findFloorPlane();
        float floorY = (floorPlane != null) ? floorPlane.getCenterPose().ty() : Float.MAX_VALUE;


        List<Plane> filteredPlanes = new ArrayList<>();
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING
                    && plane.getTrackingState() == TrackingState.TRACKING) {
                float heightAboveFloor = plane.getCenterPose().ty() - floorY;
                if (plane == floorPlane || heightAboveFloor < 0.8f) {
                    filteredPlanes.add(plane);
                }
            }
        }
        // planeRenderer.drawPlanes(render, filteredPlanes, camera.getDisplayOrientedPose(), projectionMatrix);

        updateLightEstimation(frame.getLightEstimate(), viewMatrix);

        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);

        // Draw pawn objects at each anchor position
        for (WrappedAnchor wrappedAnchor : cornerAnchors) {
            drawAnchor(wrappedAnchor.getAnchor(), render);
        }

// Draw grid cell anchors
     /*   for (GridCell cell : gridCells) {
            drawAnchor(cell.anchor, render);
        }*/
        for (GridCell cell : gridCells) {
            drawGridCellPlane(cell, render);
        }



        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
        // Draw grid cell anchors
        for (GridCell cell : gridCells) {
            drawAnchor(cell.anchor, render);
        }

// DRAW GRID LINES
        if (gridCreated && !gridLineMeshes.isEmpty()) {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            for (Mesh lineMesh : gridLineMeshes) {
                render.draw(lineMesh, lineShader);
            }
        }
        // DRAW GRID LINES
        if (gridCreated && !gridLineMeshes.isEmpty()) {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            for (Mesh lineMesh : gridLineMeshes) {
                render.draw(lineMesh, lineShader);
            }
        }

// ADD THIS SECTION:
// DRAW CORNER CONNECTION LINES
        if (!cornerLineMeshes.isEmpty()) {
            Log.d("CornerLine", "Drawing corner lines...");
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            for (Mesh lineMesh : cornerLineMeshes) {
                render.draw(lineMesh, lineShader);
            }
        }

        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);
    }

    // Find the lowest upward-facing horizontal plane = floor candidate
    private Plane findFloorPlane() {
        Plane floorPlane = null;
        float minY = Float.MAX_VALUE;

        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING
                    && plane.getTrackingState() == TrackingState.TRACKING) {
                float planeY = plane.getCenterPose().ty();
                if (planeY < minY) {
                    minY = planeY;
                    floorPlane = plane;
                }
            }
        }
        return floorPlane;
    }

    private void showToastOnUiThread(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void launchInstantPlacementSettingsMenuDialog() {
        resetSettingsMenuDialogCheckboxes();
        Resources resources = getResources();
        new AlertDialog.Builder(this)
                .setTitle(R.string.options_title_instant_placement)
                .setMultiChoiceItems(resources.getStringArray(R.array.instant_placement_options_array),
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
                    .setMultiChoiceItems(resources.getStringArray(R.array.depth_options_array),
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

    private void applySettingsMenuDialogCheckboxes() {
        depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
        depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);
        instantPlacementSettings.setInstantPlacementEnabled(instantPlacementSettingsMenuDialogCheckboxes[0]);
        configureSession();
    }

    private void resetSettingsMenuDialogCheckboxes() {
        depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
        depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
        instantPlacementSettingsMenuDialogCheckboxes[0] = instantPlacementSettings.isInstantPlacementEnabled();
    }

    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                return true;
            }
        }
        return false;
    }

    private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
        if (lightEstimate.getState() != LightEstimate.State.VALID) {
            virtualObjectShader.setBool("u_LightEstimateIsValid", false);
            return;
        }
        virtualObjectShader.setBool("u_LightEstimateIsValid", true);

        Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
        virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);

        updateMainLight(lightEstimate.getEnvironmentalHdrMainLightDirection(),
                lightEstimate.getEnvironmentalHdrMainLightIntensity(), viewMatrix);
        updateSphericalHarmonicsCoefficients(lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
        cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap());
    }

    private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
        worldLightDirection[0] = direction[0];
        worldLightDirection[1] = direction[1];
        worldLightDirection[2] = direction[2];
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
        virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader.setVec3("u_LightIntensity", intensity);
    }

    private void updateSphericalHarmonicsCoefficients(float[] coefficients) {
        if (coefficients.length != 9 * 3) {
            throw new IllegalArgumentException("The given coefficients array must be of length 27");
        }

        for (int i = 0; i < 9 * 3; ++i) {
            sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3];
        }
        virtualObjectShader.setVec3Array("u_SphericalHarmonicsCoefficients", sphericalHarmonicsCoefficients);
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
            config.setInstantPlacementMode(InstantPlacementMode.LOCAL_Y_UP);
        } else {
            config.setInstantPlacementMode(InstantPlacementMode.DISABLED);
        }
        session.configure(config);
    }


    /**
     * Wrapper class for Anchor and associated Trackable
     */
    class WrappedAnchor {
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

    class GridCell {
        public final int cellNumber;
        public final int row;
        public final int col;
        public final Anchor anchor;
        public boolean visited;   // Track if this cell has been visited
        public float[] color;     // RGBA color for rendering

        public GridCell(int cellNumber, int row, int col, Anchor anchor) {
            this.cellNumber = cellNumber;
            this.row = row;
            this.col = col;
            this.anchor = anchor;
            this.visited = false;              // Default: not visited
            this.color = new float[]{1f, 1f, 1f, 0.3f}; // Default: white + semi-transparent
        }
    }
}