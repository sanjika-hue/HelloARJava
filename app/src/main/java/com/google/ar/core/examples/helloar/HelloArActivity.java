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
    private SampleRender render;
    private Mesh gridQuadMesh;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private TapHelper tapHelper;
    //    private SampleRender render;
    private Button btnDone;

    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;
    private final List<Mesh> cornerLineMeshes = new ArrayList<>();

    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];
    private boolean shouldCreateGrid = false;

    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];

    // private boolean anchorsPlaced = false;
    // private boolean hasShownPlacementToast = false;
    private boolean hasPlayedSoundForCurrentCell = false;
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
    private Shader textOverlayShader;
    private Mesh textQuadMesh;

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
            shouldCreateGrid = true; // Set flag instead of creating immediately
            btnDone.setEnabled(false);
        }
    }

    private void createGridOnGLThread(SampleRender render) {
        if (cornerAnchors.size() != 4) {
            showToastOnUiThread("Need exactly 4 corner anchors");
            return;
        }

        // Sort corners to form a proper rectangle
        List<float[]> corners = new ArrayList<>();
        for (WrappedAnchor anchor : cornerAnchors) {
            corners.add(anchor.getAnchor().getPose().getTranslation());
        }

        // Order corners properly
        float[] orderedCorners = orderCornersAsRectangle(corners);

        float[] p1 = new float[]{orderedCorners[0], orderedCorners[1], orderedCorners[2]};   // Top-Left
        float[] p2 = new float[]{orderedCorners[3], orderedCorners[4], orderedCorners[5]};   // Top-Right
        float[] p3 = new float[]{orderedCorners[6], orderedCorners[7], orderedCorners[8]};   // Bottom-Left
        float[] p4 = new float[]{orderedCorners[9], orderedCorners[10], orderedCorners[11]}; // Bottom-Right

        int cellNumber = 1;

        for (int row = 0; row < GRID_ROWS; row++) {
            float rowFraction = (row + 0.5f) / GRID_ROWS;

            float[] left = new float[3];
            float[] right = new float[3];
            for (int i = 0; i < 3; i++) {
                left[i] = p1[i] + (p3[i] - p1[i]) * rowFraction;
                right[i] = p2[i] + (p4[i] - p2[i]) * rowFraction;
            }

            for (int col = 0; col < GRID_COLS; col++) {
                float colFraction = (col + 0.5f) / GRID_COLS;

                float[] pos = new float[3];
                for (int i = 0; i < 3; i++) {
                    pos[i] = left[i] + (right[i] - left[i]) * colFraction;
                }

                try {
                    Pose pose = Pose.makeTranslation(pos[0], pos[1], pos[2]);
                    Anchor anchor = session.createAnchor(pose);
                    GridCell cell = new GridCell(cellNumber, row, col, anchor);

                    // Create number texture for this cell
                    cell.numberTexture = createNumberTexture(cellNumber, render);

                    gridCells.add(cell);
                    cellNumber++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create grid cell: " + e.getMessage());
                }
            }
        }

        // Create meshes on GL thread
        createGridLineMeshes(render);
        createCornerConnectionLines(render);

        showToastOnUiThread("Grid created with " + gridCells.size() + " cells");
        Log.d(TAG, "Grid created successfully on GL thread");
    }
    private void drawCellNumber(GridCell cell, SampleRender render) {
        if (cell.anchor.getTrackingState() != TrackingState.TRACKING || cell.numberTexture == null) {
            return;
        }

        try {
            float[] modelMatrix = new float[16];
            cell.anchor.getPose().toMatrix(modelMatrix, 0);

            // Elevate text above the cell plane
            float[] translationMatrix = new float[16];
            Matrix.setIdentityM(translationMatrix, 0);
            Matrix.translateM(translationMatrix, 0, 0, 0.015f, 0); // Lift 1.5cm above

            float[] elevatedModelMatrix = new float[16];
            Matrix.multiplyMM(elevatedModelMatrix, 0, modelMatrix, 0, translationMatrix, 0);

            // Scale the text quad appropriately
            float[] scaleMatrix = new float[16];
            Matrix.setIdentityM(scaleMatrix, 0);
            Matrix.scaleM(scaleMatrix, 0, 0.15f, 1.0f, 0.15f); // Adjust size as needed

            float[] modelScaleMatrix = new float[16];
            Matrix.multiplyMM(modelScaleMatrix, 0, elevatedModelMatrix, 0, scaleMatrix, 0);

            float[] modelViewMatrix = new float[16];
            float[] modelViewProjectionMatrix = new float[16];
            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelScaleMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

            // Enable blending for transparency
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

            textOverlayShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            textOverlayShader.setTexture("u_Texture", cell.numberTexture);

            // Set color based on visited status
            if (cell.visited) {
                textOverlayShader.setVec4("u_Color", new float[]{1f, 1f, 1f, 1f}); // White on green
            } else if (cell.cellNumber == currentTargetCell) {
                textOverlayShader.setVec4("u_Color", new float[]{0f, 0f, 0f, 1f}); // Black on yellow
            } else {
                textOverlayShader.setVec4("u_Color", new float[]{0f, 0f, 0f, 0.8f}); // Semi-transparent black
            }

            render.draw(textQuadMesh, textOverlayShader);

            GLES30.glDisable(GLES30.GL_BLEND);

        } catch (Exception e) {
            Log.e(TAG, "Error drawing cell number " + cell.cellNumber + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    private Texture createNumberTexture(int number, SampleRender render) {
        try {
            // Create a bitmap with the cell number
            int size = 256;
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    size, size, android.graphics.Bitmap.Config.ARGB_8888
            );
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);

            // Set background transparent
            canvas.drawColor(android.graphics.Color.TRANSPARENT);

            // Configure paint for text
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setTextSize(160); // Increased size
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setAntiAlias(true);
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
            ));

            // Draw white outline for better visibility
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(12);
            paint.setColor(android.graphics.Color.WHITE);
            canvas.drawText(String.valueOf(number), size / 2f, size / 2f + 60, paint);

            // Draw black text
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(android.graphics.Color.BLACK);
            canvas.drawText(String.valueOf(number), size / 2f, size / 2f + 60, paint);

            // Convert bitmap to ByteBuffer
            ByteBuffer buffer = ByteBuffer.allocateDirect(size * size * 4);
            buffer.order(ByteOrder.nativeOrder());
            bitmap.copyPixelsToBuffer(buffer);
            buffer.rewind();

            // Create texture using SampleRender's Texture class
            Texture texture = new Texture(
                    render,
                    Texture.Target.TEXTURE_2D,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    false // useMipmaps
            );

            // Upload texture data
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.getTextureId());
            GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D,
                    0,
                    GLES30.GL_RGBA,
                    size,
                    size,
                    0,
                    GLES30.GL_RGBA,
                    GLES30.GL_UNSIGNED_BYTE,
                    buffer
            );
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

            // Recycle bitmap
            bitmap.recycle();

            Log.d(TAG, "Created number texture for: " + number);
            return texture;

        } catch (Exception e) {
            Log.e(TAG, "Failed to create number texture for " + number + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Add this new method to order corners properly
    private float[] orderCornersAsRectangle(List<float[]> corners) {
        // Find centroid
        float centerX = 0, centerZ = 0;
        for (float[] corner : corners) {
            centerX += corner[0];
            centerZ += corner[2];
        }
        centerX /= 4;
        centerZ /= 4;

        // Classify corners based on their position relative to centroid
        float[] topLeft = null, topRight = null, bottomLeft = null, bottomRight = null;

        for (float[] corner : corners) {
            boolean isLeft = corner[0] < centerX;
            boolean isTop = corner[2] < centerZ;  // In AR, negative Z is typically "forward"

            if (isLeft && isTop) {
                topLeft = corner;
            } else if (!isLeft && isTop) {
                topRight = corner;
            } else if (isLeft && !isTop) {
                bottomLeft = corner;
            } else {
                bottomRight = corner;
            }
        }

        // Fallback: if any corner is null, use the original order
        if (topLeft == null || topRight == null || bottomLeft == null || bottomRight == null) {
            Log.w(TAG, "Could not properly classify corners, using original order");
            return new float[]{
                    corners.get(0)[0], corners.get(0)[1], corners.get(0)[2],
                    corners.get(1)[0], corners.get(1)[1], corners.get(1)[2],
                    corners.get(2)[0], corners.get(2)[1], corners.get(2)[2],
                    corners.get(3)[0], corners.get(3)[1], corners.get(3)[2]
            };
        }

        Log.d(TAG, "Corners ordered: TL, TR, BL, BR");

        // Return ordered corners: TL, TR, BL, BR
        return new float[]{
                topLeft[0], topLeft[1], topLeft[2],
                topRight[0], topRight[1], topRight[2],
                bottomLeft[0], bottomLeft[1], bottomLeft[2],
                bottomRight[0], bottomRight[1], bottomRight[2]
        };
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
        if (!gridCreated || gridCells.isEmpty()) {
            return;
        }

        GridCell targetCell = null;
        for (GridCell cell : gridCells) {
            if (cell.cellNumber == currentTargetCell) {
                targetCell = cell;
                break;
            }
        }

        if (targetCell == null) {
            Log.e(TAG, "ERROR: Target cell " + currentTargetCell + " not found!");
            return;
        }

        float[] cameraPos = camera.getPose().getTranslation();
        float[] cellPos = targetCell.anchor.getPose().getTranslation();

        float dx = cellPos[0] - cameraPos[0];
        float dy = cellPos[1] - cameraPos[1];
        float dz = cellPos[2] - cameraPos[2];

        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        runOnUiThread(() -> {
            tvDistance.setText(String.format("Distance to Cell %d: %.2fm", currentTargetCell, distance));
            tvDistance.setVisibility(View.VISIBLE);
        });

        // Check if cell is reached (within 50cm threshold)
        if (distance < 0.5f && !targetCell.visited && !hasPlayedSoundForCurrentCell) {
            Log.i(TAG, "✓ CELL " + targetCell.cellNumber + " REACHED!");

            // Mark as visited
            targetCell.visited = true;
            hasPlayedSoundForCurrentCell = true; // Prevent sound from playing again

            // Update colors for all cells
            updateCellColors();

            // Play a success sound ONCE
            runOnUiThread(() -> {
                try {
                    android.media.ToneGenerator tg = new android.media.ToneGenerator(
                            android.media.AudioManager.STREAM_NOTIFICATION, 100);
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200);

                    // Release after a delay
                    new android.os.Handler().postDelayed(() -> {
                        tg.release();
                    }, 300);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to play sound: " + e.getMessage());
                }
            });

            // Move to next cell
            if (currentTargetCell < GRID_ROWS * GRID_COLS) {
                currentTargetCell++;
                hasPlayedSoundForCurrentCell = false; // Reset for next cell
                updateInstructions();
                showToastOnUiThread("Cell " + (currentTargetCell - 1) + " completed! → Cell " + currentTargetCell);
            } else {
                showToastOnUiThread("🎉 All cells completed!");
                runOnUiThread(() -> tvInstructions.setText("All cells visited!"));
            }
        }
    }
    private void createGridQuadMesh(SampleRender render) {
        float[] vertices = {
                -0.5f, 0, -0.5f,
                0.5f, 0, -0.5f,
                -0.5f, 0, 0.5f,
                0.5f, 0, 0.5f
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

        // Get and order corner positions
        List<float[]> corners = new ArrayList<>();
        for (WrappedAnchor anchor : cornerAnchors) {
            corners.add(anchor.getAnchor().getPose().getTranslation());
        }

        float[] orderedCorners = orderCornersAsRectangle(corners);

        float[] p1 = new float[]{orderedCorners[0], orderedCorners[1], orderedCorners[2]};   // Top-Left
        float[] p2 = new float[]{orderedCorners[3], orderedCorners[4], orderedCorners[5]};   // Top-Right
        float[] p3 = new float[]{orderedCorners[6], orderedCorners[7], orderedCorners[8]};   // Bottom-Left
        float[] p4 = new float[]{orderedCorners[9], orderedCorners[10], orderedCorners[11]}; // Bottom-Right

        try {
            // Create lines connecting corners: TL->TR->BR->BL->TL
            createCornerLineMesh(p1, p2, render); // Top-Left to Top-Right
            createCornerLineMesh(p2, p4, render); // Top-Right to Bottom-Right
            createCornerLineMesh(p4, p3, render); // Bottom-Right to Bottom-Left
            createCornerLineMesh(p3, p1, render); // Bottom-Left to Top-Left

            Log.d("CornerLine12", "Created " + cornerLineMeshes.size() + " corner connection lines");

        } catch (Exception e) {
            Log.e("CornerLine", "Failed to create corner lines: " + e.getMessage());
            e.printStackTrace();
        }
    }


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
        if (cell.anchor.getTrackingState() != TrackingState.TRACKING) {
            Log.d("GridDebug", "Cell " + cell.cellNumber + " not tracking");
            return;
        }

        // Log to verify this method is being called
        if (cell.visited || cell.cellNumber == currentTargetCell) {
            Log.d("GridDebug", "Drawing cell " + cell.cellNumber +
                    " visited=" + cell.visited +
                    " color=" + Arrays.toString(cell.color));
        }

        // Get anchor model matrix
        float[] modelMatrix = new float[16];
        cell.anchor.getPose().toMatrix(modelMatrix, 0);

        // Slightly elevate the cell above the floor to avoid z-fighting
        float[] translationMatrix = new float[16];
        Matrix.setIdentityM(translationMatrix, 0);
        Matrix.translateM(translationMatrix, 0, 0, 0.005f, 0); // Lift 5mm above floor

        float[] elevatedModelMatrix = new float[16];
        Matrix.multiplyMM(elevatedModelMatrix, 0, modelMatrix, 0, translationMatrix, 0);

        // Scale the plane to the size of the grid cell
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);

        // Make cells much more visible
        float cellSize = cell.visited ? 0.35f :
                (cell.cellNumber == currentTargetCell ? 0.32f : 0.28f);
        float cellHeight = cell.visited ? 0.03f :
                (cell.cellNumber == currentTargetCell ? 0.025f : 0.015f);

        Matrix.scaleM(scaleMatrix, 0, cellSize, cellHeight, cellSize);

        float[] modelScaleMatrix = new float[16];
        Matrix.multiplyMM(modelScaleMatrix, 0, elevatedModelMatrix, 0, scaleMatrix, 0);

        // Compute ModelViewProjection
        float[] modelViewMatrix = new float[16];
        float[] modelViewProjectionMatrix = new float[16];
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelScaleMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);

        try {
            gridShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            gridShader.setVec4("u_Color", cell.color);

            // Draw the grid quad
            render.draw(gridQuadMesh, gridShader);
        } catch (Exception e) {
            Log.e("GridDebug", "Error drawing cell " + cell.cellNumber + ": " + e.getMessage());
        }
    }
    private void createTextQuadMesh(SampleRender render) {
        // Separate position and texture coordinate data
        float[] positions = {
                -0.15f, 0.001f, -0.15f,  // Bottom-left
                0.15f, 0.001f, -0.15f,  // Bottom-right
                -0.15f, 0.001f,  0.15f,  // Top-left
                0.15f, 0.001f,  0.15f   // Top-right
        };

        float[] texCoords = {
                0, 1,  // Bottom-left
                1, 1,  // Bottom-right
                0, 0,  // Top-left
                1, 0   // Top-right
        };

        // Create position buffer
        FloatBuffer positionBuffer = ByteBuffer
                .allocateDirect(positions.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        positionBuffer.put(positions);
        positionBuffer.rewind();

        // Create texture coordinate buffer
        FloatBuffer texCoordBuffer = ByteBuffer
                .allocateDirect(texCoords.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        texCoordBuffer.put(texCoords);
        texCoordBuffer.rewind();

        VertexBuffer positionVB = new VertexBuffer(render, 3, positionBuffer);
        VertexBuffer texCoordVB = new VertexBuffer(render, 2, texCoordBuffer);

        textQuadMesh = new Mesh(
                render,
                Mesh.PrimitiveMode.TRIANGLE_STRIP,
                null,
                new VertexBuffer[]{positionVB, texCoordVB}
        );
    }

    private void updateCellColors() {
        for (GridCell cell : gridCells) {
            if (cell.visited) {
                // Visited cells: BRIGHT GREEN and OPAQUE
                cell.color = new float[]{0f, 0.8f, 0f, 0.9f}; // Bright green
            } else if (cell.cellNumber == currentTargetCell) {
                // Current target cell: BRIGHT YELLOW and OPAQUE
                cell.color = new float[]{1f, 0.9f, 0f, 0.9f}; // Bright yellow
            } else {
                // Unvisited cells: LIGHT GRAY and SEMI-TRANSPARENT
                cell.color = new float[]{0.9f, 0.9f, 0.9f, 0.3f}; // More transparent white
            }
        }
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

    private void createGridLineMeshes(SampleRender render) {
        // Clear old meshes first
        for (Mesh mesh : gridLineMeshes) {
            try {
                mesh.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing mesh: " + e.getMessage());
            }
        }
        gridLineMeshes.clear();

        if (cornerAnchors.size() != 4) return;

        // Get and order corner positions
        List<float[]> corners = new ArrayList<>();
        for (WrappedAnchor anchor : cornerAnchors) {
            corners.add(anchor.getAnchor().getPose().getTranslation());
        }

        float[] orderedCorners = orderCornersAsRectangle(corners);

        float[] p1 = new float[]{orderedCorners[0], orderedCorners[1], orderedCorners[2]};   // Top-Left
        float[] p2 = new float[]{orderedCorners[3], orderedCorners[4], orderedCorners[5]};   // Top-Right
        float[] p3 = new float[]{orderedCorners[6], orderedCorners[7], orderedCorners[8]};   // Bottom-Left
        float[] p4 = new float[]{orderedCorners[9], orderedCorners[10], orderedCorners[11]}; // Bottom-Right

        // Create horizontal lines (rows)
        for (int row = 0; row <= GRID_ROWS; row++) {
            float rowFraction = (float) row / GRID_ROWS;

            float[] left = new float[3];
            float[] right = new float[3];
            for (int i = 0; i < 3; i++) {
                left[i] = p1[i] + (p3[i] - p1[i]) * rowFraction;
                right[i] = p2[i] + (p4[i] - p2[i]) * rowFraction;
            }

            createLineMesh(left, right, render);
        }

        // Create vertical lines (columns)
        for (int col = 0; col <= GRID_COLS; col++) {
            float colFraction = (float) col / GRID_COLS;

            float[] top = new float[3];
            float[] bottom = new float[3];
            for (int i = 0; i < 3; i++) {
                top[i] = p1[i] + (p2[i] - p1[i]) * colFraction;
                bottom[i] = p3[i] + (p4[i] - p3[i]) * colFraction;
            }

            createLineMesh(top, bottom, render);
        }

        Log.d(TAG, "Created " + gridLineMeshes.size() + " grid line meshes");
    }

    private void createLineMesh(float[] start, float[] end, SampleRender render) {
        float[] lineVertices = {start[0], start[1], start[2], end[0], end[1], end[2]};

        FloatBuffer vertexBuffer = ByteBuffer
                .allocateDirect(lineVertices.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(lineVertices);
        vertexBuffer.position(0);

        VertexBuffer vb = new VertexBuffer(render, 3, vertexBuffer);
        Mesh lineMesh = new Mesh(render, Mesh.PrimitiveMode.LINES, null, new VertexBuffer[]{vb});
        gridLineMeshes.add(lineMesh);
    }

    public void onSurfaceCreated(SampleRender render) {
        this.render = render; // Store the render instance

        try {
            // Existing renderers and framebuffers
            planeRenderer = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, 1, 1);
            createGridQuadMesh(render);
            createTextQuadMesh(render);

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
            // Add text overlay shader for cell numbers
            textOverlayShader = Shader.createFromAssets(
                    render,
                    "shaders/text_overlay.vert",
                    "shaders/text_overlay.frag",
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

        // Create grid on GL thread when flag is set
        if (shouldCreateGrid && !gridCreated) {
            createGridOnGLThread(render);
            shouldCreateGrid = false;
            gridCreated = true;
            updateInstructions();
        }

        // Handle taps to place corner anchors
        if (cornerAnchors.size() < 4) {
            handleTapForCornerPlacement(frame, camera);
        }

        // Calculate distance to target cell if grid is created
        if (gridCreated) {
            updateCellColors();
            calculateDistanceToTargetCell(camera);
        }

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

        // ========== CRITICAL: ALWAYS DRAW BACKGROUND FIRST ==========
        if (frame.getTimestamp() != 0) {
            backgroundRenderer.drawBackground(render);
        }

        if (camera.getTrackingState() == TrackingState.PAUSED) return;

        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(viewMatrix, 0);

        // ...

        // Clear virtual scene framebuffer
        //   render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);

        // ========== DRAW 3D OBJECTS TO FRAMEBUFFER ==========

        // Draw pawn objects at each corner anchor position
        for (WrappedAnchor wrappedAnchor : cornerAnchors) {
            drawAnchor(wrappedAnchor.getAnchor(), render);
        }

        // Draw grid cell planes (colored quads)
        for (GridCell cell : gridCells) {
            drawGridCellPlane(cell, render);
        }

        // Draw grid cell anchors (pawns at center of each cell)
        for (GridCell cell : gridCells) {
            drawAnchor(cell.anchor, render);
        }

        // ========== COMPOSITE VIRTUAL SCENE WITH BACKGROUND ==========
        //   backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);

        // ========== DRAW OVERLAYS (AFTER COMPOSITE) ==========

        // Draw cell numbers on top
        for (GridCell cell : gridCells) {
            drawCellNumber(cell, render);
        }

        // Draw grid lines
        if (gridCreated && !gridLineMeshes.isEmpty()) {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

            for (Mesh lineMesh : gridLineMeshes) {
                render.draw(lineMesh, lineShader);
            }
        }

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
        public boolean visited;
        public float[] color;
        public Texture numberTexture; // Add this field

        public GridCell(int cellNumber, int row, int col, Anchor anchor) {
            this.cellNumber = cellNumber;
            this.row = row;
            this.col = col;
            this.anchor = anchor;
            this.visited = false;
            this.color = new float[]{1f, 1f, 1f, 0.9f};
            this.numberTexture = null; // Initialize later
        }
    }
}