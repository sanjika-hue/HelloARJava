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
import com.google.ar.core.examples.helloar.common.samplerender.Mesh.PrimitiveMode;
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
    private SampleRender render;

    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;

    private Button btnDone;
    private TextView tvInstructions;
    private TextView tvDistance;

    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;
    private final List<Mesh> cornerLineMeshes = new ArrayList<>();

    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];

    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];

    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    private long lastPointCloudTimestamp = 0;

    private Shader lineShader;

    private Mesh virtualObjectMesh;
    private Shader virtualObjectShader;
    private Texture virtualObjectAlbedoTexture;
    private Texture virtualObjectAlbedoInstantPlacementTexture;

    private Texture dfgTexture;

    private SpecularCubemapFilter cubemapFilter;

    private final List<WrappedAnchor> cornerAnchors = new ArrayList<>();

    private static final int GRID_ROWS = 8;
    private static final int GRID_COLS = 8;

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

        // Modified: onDoneClicked now handles validation and launches the next activity directly.
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
                // Instruction for corner placement (First Screen)
                tvInstructions.setText("Tap to place corner anchor " + (cornerAnchors.size() + 1) + " of 4");
                btnDone.setEnabled(false);
            } else {
                // Instruction after 4 corners are placed (End of First Screen)
                tvInstructions.setText("All 4 corners placed. Press 'Done' to launch navigation.");
                btnDone.setEnabled(true);
            }
            // Hide distance tracker as it's for the second stage
            tvDistance.setVisibility(View.GONE);
        });
    }

    /**
     * Finalizes the corner selection and sends the ordered corner coordinates to the
     * GridNavigationActivity.
     * The order is assumed to be: 1:Top-Left, 2:Top-Right, 3:Bottom-Left, 4:Bottom-Right.
     */
    private void onDoneClicked() {
        Log.d(TAG, "onDoneClicked: Starting...");
        Log.d(TAG, "cornerAnchors.size() = " + cornerAnchors.size());

        if (cornerAnchors.size() == 4) {
            // 1. Extract raw corner positions (translations)
            List<float[]> corners = new ArrayList<>();
            for (int i = 0; i < cornerAnchors.size(); i++) {
                float[] translation = cornerAnchors.get(i).getAnchor().getPose().getTranslation();
                corners.add(translation);
                Log.d(TAG, "Corner " + i + " raw: [" + translation[0] + ", " + translation[1] + ", " + translation[2] + "]");
            }

            // 2. Order the corners into a standard format (TL, TR, BL, BR)
            float[] orderedCoordinates = orderCornersAsRectangle(corners);

            if (orderedCoordinates == null || orderedCoordinates.length != 12) {
                Log.e(TAG, "Error: orderedCoordinates is null or wrong length");
                Toast.makeText(this, "Error in corner ordering. Try placing corners again.", Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(TAG, "Ordered coordinates length: " + orderedCoordinates.length);

            // 3. Split the ordered coordinates into four separate float[] arrays
            float[] corner1 = Arrays.copyOfRange(orderedCoordinates, 0, 3); // Top-Left
            float[] corner2 = Arrays.copyOfRange(orderedCoordinates, 3, 6); // Top-Right
            float[] corner3 = Arrays.copyOfRange(orderedCoordinates, 6, 9); // Bottom-Left
            float[] corner4 = Arrays.copyOfRange(orderedCoordinates, 9, 12); // Bottom-Right

            Log.d(TAG, "TL (corner1): [" + corner1[0] + ", " + corner1[1] + ", " + corner1[2] + "]");
            Log.d(TAG, "TR (corner2): [" + corner2[0] + ", " + corner2[1] + ", " + corner2[2] + "]");
            Log.d(TAG, "BL (corner3): [" + corner3[0] + ", " + corner3[1] + ", " + corner3[2] + "]");
            Log.d(TAG, "BR (corner4): [" + corner4[0] + ", " + corner4[1] + ", " + corner4[2] + "]");

            Intent intent = new Intent(this, GridNavigationActivity.class);

            // Pass the three-element float arrays using standard Intent extras
            intent.putExtra("corner1", corner1);
            intent.putExtra("corner2", corner2);
            intent.putExtra("corner3", corner3);
            intent.putExtra("corner4", corner4);

            Log.d(TAG, "Starting GridNavigationActivity with corner data...");
            startActivity(intent);
        } else {
            Log.w(TAG, "Cannot proceed: only " + cornerAnchors.size() + " corners placed");
            Toast.makeText(this, "Please place exactly 4 corners before pressing Done.", Toast.LENGTH_LONG).show();
        }
    }

    // Keep, as it's a utility for ordering the corners
    private float[] orderCornersAsRectangle(List<float[]> corners) {
        if (corners.size() != 4) {
            Log.e(TAG, "Must have exactly 4 corners");
            return null;
        }

        // Find centroid
        float centerX = 0, centerY = 0, centerZ = 0;
        for (float[] corner : corners) {
            centerX += corner[0];
            centerY += corner[1];
            centerZ += corner[2];
        }
        centerX /= 4;
        centerY /= 4;
        centerZ /= 4;

        final float finalCenterX = centerX;
        final float finalCenterZ = centerZ;

        // Find the pair with maximum distance (diagonal)
        float maxDist = 0;
        int idx1 = 0, idx2 = 0;

        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                float[] c1 = corners.get(i);
                float[] c2 = corners.get(j);
                float dist = (float) Math.sqrt(
                        Math.pow(c1[0] - c2[0], 2) +
                                Math.pow(c1[2] - c2[2], 2)
                );
                if (dist > maxDist) {
                    maxDist = dist;
                    idx1 = i;
                    idx2 = j;
                }
            }
        }

        float[] corner1 = corners.get(idx1);
        float[] corner2 = corners.get(idx2);
        List<float[]> otherCorners = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i != idx1 && i != idx2) {
                otherCorners.add(corners.get(i));
            }
        }

        float[] topLeft, topRight, bottomLeft, bottomRight;

        // Ordering based on X and Z relative to the centroid
        boolean c1IsLeft = corner1[0] < finalCenterX;
        boolean c1IsTop = corner1[2] < finalCenterZ;

        if (c1IsLeft && c1IsTop) {
            topLeft = corner1;
            bottomRight = corner2;

            boolean oc1IsLeft = otherCorners.get(0)[0] < finalCenterX;
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

            boolean oc1IsLeft = otherCorners.get(0)[0] < finalCenterX;
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

            boolean oc1IsLeft = otherCorners.get(0)[0] < finalCenterX;
            if (oc1IsLeft) {
                topLeft = otherCorners.get(0);
                bottomRight = otherCorners.get(1);
            } else {
                bottomRight = otherCorners.get(0);
                topLeft = otherCorners.get(1);
            }
        } else { // c1IsRight && c1IsBottom
            bottomRight = corner1;
            topLeft = corner2;

            boolean oc1IsLeft = otherCorners.get(0)[0] < finalCenterX;
            if (oc1IsLeft) {
                bottomLeft = otherCorners.get(0);
                topRight = otherCorners.get(1);
            } else {
                topRight = otherCorners.get(0);
                bottomLeft = otherCorners.get(1);
            }
        }

        // Return a single float array: [TL_x, TL_y, TL_z, TR_x, TR_y, TR_z, BL_x, BL_y, BL_z, BR_x, BR_y, BR_z]
        float[] ordered = new float[12];
        System.arraycopy(topLeft, 0, ordered, 0, 3);
        System.arraycopy(topRight, 0, ordered, 3, 3);
        System.arraycopy(bottomLeft, 0, ordered, 6, 3);
        System.arraycopy(bottomRight, 0, ordered, 9, 3);

        return ordered;
    }

    private void handleTapForCornerPlacement(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap == null || camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }

        if (cornerAnchors.size() >= 4) {
            return;
        }

        float x = tap.getX();
        float y = tap.getY();

        for (HitResult hit : frame.hitTest(x, y)) {
            Trackable trackable = hit.getTrackable();
            // Allow placing on planes or point clouds (Instant Placement compatible)
            if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) ||
                    (trackable instanceof PointCloud)) {

                Anchor anchor = hit.createAnchor();
                cornerAnchors.add(new WrappedAnchor(anchor, trackable));
                showToastOnUiThread("Corner " + cornerAnchors.size() + " placed");
                updateInstructions();

                // Re-create the corner connection lines to update the drawing
                surfaceView.queueEvent(() -> createCornerConnectionLines(render));
                break;
            }
        }
    }

    private void createCornerConnectionLines(SampleRender render) {
        if (cornerAnchors.size() < 2) return; // Need at least two to draw a line

        // Clear old corner line meshes
        for (Mesh mesh : cornerLineMeshes) {
            try {
                mesh.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing corner line mesh: " + e.getMessage());
            }
        }
        cornerLineMeshes.clear();

        // Get and order corner positions
        List<float[]> corners = new ArrayList<>();
        for (WrappedAnchor anchor : cornerAnchors) {
            corners.add(anchor.getAnchor().getPose().getTranslation());
        }

        // Connect lines between the currently placed anchors in the order they were placed
        if (cornerAnchors.size() == 4) {
            float[] orderedCoordinates = orderCornersAsRectangle(corners);

            float[] p1 = Arrays.copyOfRange(orderedCoordinates, 0, 3); // TL
            float[] p2 = Arrays.copyOfRange(orderedCoordinates, 3, 6); // TR
            float[] p3 = Arrays.copyOfRange(orderedCoordinates, 6, 9); // BL
            float[] p4 = Arrays.copyOfRange(orderedCoordinates, 9, 12); // BR

            try {
                // Create lines connecting ordered corners: TL->TR->BR->BL->TL
                createCornerLineMesh(p1, p2, render);
                createCornerLineMesh(p2, p4, render);
                createCornerLineMesh(p4, p3, render);
                createCornerLineMesh(p3, p1, render); // Close the loop
            } catch (Exception e) {
                Log.e("CornerLine", "Failed to create ordered corner lines: " + e.getMessage());
                e.printStackTrace();
            }

        } else {
            // Connect sequentially if less than 4 corners are placed
            for (int i = 0; i < cornerAnchors.size() - 1; i++) {
                float[] p1 = corners.get(i);
                float[] p2 = corners.get(i + 1);
                try {
                    createCornerLineMesh(p1, p2, render);
                } catch (Exception e) {
                    Log.e("CornerLine", "Failed to create sequential corner lines: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }


    private void createCornerLineMesh(float[] start, float[] end, SampleRender render) {
        float[] lineVertices = {start[0], start[1], start[2], end[0], end[1], end[2]};

        FloatBuffer vertexBuffer = ByteBuffer
                .allocateDirect(lineVertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(lineVertices);
        vertexBuffer.position(0);

        VertexBuffer vb = new VertexBuffer(render, 3, vertexBuffer);
        Mesh lineMesh = new Mesh(render, PrimitiveMode.LINES, null, new VertexBuffer[]{vb});
        cornerLineMeshes.add(lineMesh);
    }

    private void drawAnchor(Anchor anchor, SampleRender render) {
        if (anchor.getTrackingState() != TrackingState.TRACKING) return;

        anchor.getPose().toMatrix(modelMatrix, 0);

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

        render.draw(virtualObjectMesh, virtualObjectShader);
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
            // ... (session initialization and permission checks) ...
            Exception exception = null; // Declare exception outside try/catch
            String message = null;

            try {
                // ... (existing session creation and permission checks) ...
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
        this.render = render;

        try {
            planeRenderer = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, 1, 1);

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
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RG16F, dfgResolution, dfgResolution, 0, GLES30.GL_RG, GLES30.GL_HALF_FLOAT, buffer);

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

            // Line shader is needed to draw the corner connections
            lineShader = Shader.createFromAssets(
                    render,
                    "shaders/line.vert",
                    "shaders/line.frag",
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
        handleTapForCornerPlacement(frame, camera);

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
            message = cornerAnchors.isEmpty() ? "Tap to place corners (1 of 4)..." : null;
        } else {
            message = SEARCHING_PLANE_MESSAGE;
        }

        if (message == null) {
            messageSnackbarHelper.hide(this);
        } else {
            messageSnackbarHelper.showMessage(this, message);
        }

        // ========== CRITICAL: ALWAYS DRAW BACKGROUND FIRST ==========
        if (frame.getTimestamp() != 0) {
            backgroundRenderer.drawBackground(render);
        }

        if (camera.getTrackingState() == TrackingState.PAUSED) return;

        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(viewMatrix, 0);

        // ========== DRAW 3D OBJECTS (Anchors) ==========

        // Draw pawn objects at each corner anchor position
        for (WrappedAnchor wrappedAnchor : cornerAnchors) {
            drawAnchor(wrappedAnchor.getAnchor(), render);
        }

        // ========== DRAW OVERLAYS (Lines) ==========

        // Draw corner connection lines
        if (!cornerLineMeshes.isEmpty()) {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            for (Mesh lineMesh : cornerLineMeshes) {
                render.draw(lineMesh, lineShader);
            }
        }
    }

    // Helper method needed for tracking state
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING && plane.isPoseInPolygon(plane.getCenterPose())) {
                return true;
            }
        }
        return false;
    }


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

    // Completed Method
    private void resetSettingsMenuDialogCheckboxes() {
        depthSettingsMenuDialogCheckboxes[0] = depthSettings.useDepthForOcclusion();
        depthSettingsMenuDialogCheckboxes[1] = depthSettings.depthColorVisualizationEnabled();
        instantPlacementSettingsMenuDialogCheckboxes[0] = instantPlacementSettings.isInstantPlacementEnabled();
    }

    // Completed Method
    private void applySettingsMenuDialogCheckboxes() {
        depthSettings.setUseDepthForOcclusion(depthSettingsMenuDialogCheckboxes[0]);
        depthSettings.setDepthColorVisualizationEnabled(depthSettingsMenuDialogCheckboxes[1]);

        // Apply instant placement setting
        instantPlacementSettings.setInstantPlacementEnabled(instantPlacementSettingsMenuDialogCheckboxes[0]);

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
}
