package com.google.ar.core.examples.helloar;
import android.os.Environment;
import android.media.MediaScannerConnection;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Vibrator;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.*;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.EditText;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import android.graphics.BitmapFactory;

/**
 * Refactored HelloArActivity with proper separation of concerns
 */
public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {

    private static final String TAG = HelloArActivity.class.getSimpleName();
    // private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor();

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
    private int stableCellIndex = -1;
    private int currentStableCell = -1;
    private long stableStartTime = 0;
    private static final long STABLE_DURATION_MS = 2000;
    private boolean isCaptureInProgress = false;
    // AR Session and rendering
    private GLSurfaceView surfaceView;
    private Session session;
    private SampleRender render;
    private boolean installRequested;
    private boolean hasSetTextureNames = false;
    private float[] lastCameraPosition = new float[3];
    private TextView tvVisitedValueIn2DView;
    private TextView tvCameraAngle;  // Shows current camera angle
    private View angleIndicator;     // Visual angle indicator
    private TextView tvAngleStatus;
    private float currentCameraAngle = 0f;

    // Helpers
    //private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private final SafeMessageHelper messageSnackbarHelper = new SafeMessageHelper();
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
    //private Button btnCapture;
    // private ImageButton btnCapture;
    private Button btnCapture;

    // Settings
    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];
    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];

    // Managers - NEW!
    private CornerManager cornerManager;
    // final Image finalImage = image;

    private MeshManager cornerLineMeshManager;
    private MeshManager visitedCellMeshManager;
    private VisitedCellManager visitedCellManager;

    // Add these fields with your other managers
    private GridManager gridManager;
    private boolean gridViewVisible = false;
    private FrameLayout gridViewContainer;
    private Custom2DGridView gridView2D;

    // Grid configuration - easy to modify
    private static final int GRID_ROWS = 4;
    private static final int GRID_COLS = 4;
    private static final float GRID_GAP_SIZE = 0.01f ; //0.005f; // 5mm gap between cells

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


    private boolean captureMode = false;
    private int targetCellForCapture = -1;
    private static final float CAPTURE_ANGLE_THRESHOLD = 30f; // Degrees from vertical
    private static final float CAPTURE_DISTANCE_THRESHOLD = 1.0f; // meters from cell
    private HashMap<Integer, String> cellImagePaths = new HashMap<>();
    private long lastCaptureCheckTime = 0;
    private static final long CAPTURE_CHECK_INTERVAL = 250;


    private long lastCaptureTime = 0;
    private enum InspectionMode {
        FLOOR,
        WALL,
        VIRTUAL_WALL,
        NONE
    }
    private InspectionMode currentMode = InspectionMode.NONE;


    private Button btnInspectFloor;
    private Button btnInspectWall;
    private LinearLayout modeSelectionContainer;

    // ===== VIRTUAL WALL FIELDS =====
    private Button btnInspectVirtualWall;
    private androidx.cardview.widget.CardView cardHeightInput;
    private EditText etRoomHeight;
    private Button btnConfirmHeight;
    private float userInputRoomHeight = 2.5f; // Default, but user MUST confirm
    private float[] storedFloorCorners = null;


    // Update your onCreate() method - ADD THIS SECTION:
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1️⃣ Initialize basic mode selection views
        modeSelectionContainer = findViewById(R.id.modeSelectionContainer);
        btnInspectFloor = findViewById(R.id.btnInspectFloor);
        btnInspectWall = findViewById(R.id.btnInspectWall);

        // Bind Virtual Wall UI
        btnInspectVirtualWall = findViewById(R.id.btnInspectVirtualWall);
        cardHeightInput = findViewById(R.id.cardHeightInput);
        etRoomHeight = findViewById(R.id.etRoomHeight);
        btnConfirmHeight = findViewById(R.id.btnConfirmHeight);
        btnInspectVirtualWall.setOnClickListener(v -> selectInspectionMode(InspectionMode.VIRTUAL_WALL));
        btnConfirmHeight.setOnClickListener(v -> onHeightConfirmed());

        // 2️⃣ Initialize capture-related UI elements
        btnCapture = findViewById(R.id.btnCapture);
        btnDone = findViewById(R.id.btnDone);
        tvInstructions = findViewById(R.id.tvInstructions);
        tvDistance = findViewById(R.id.tvDistance);
        surfaceView = findViewById(R.id.surfaceview);

        // 3️⃣ Initialize additional controls + professional UI elements
        cardGridInfo = findViewById(R.id.cardGridInfo);
        tvGridSize = findViewById(R.id.tvGridSize);
        tvVisitedCount = findViewById(R.id.tvVisitedCount);
        cardDistance = findViewById(R.id.cardDistance);
        cornerHintsContainer = findViewById(R.id.cornerHintsContainer);

        // Corner indicators
        cornerIndicators[0] = findViewById(R.id.cornerIndicator1);
        cornerIndicators[1] = findViewById(R.id.cornerIndicator2);
        cornerIndicators[2] = findViewById(R.id.cornerIndicator3);
        cornerIndicators[3] = findViewById(R.id.cornerIndicator4);

        // View captured images button
        Button btnViewCaptured = findViewById(R.id.btnViewCaptured);

        // 4️⃣ Hide non-mode views initially
        modeSelectionContainer.setVisibility(View.VISIBLE);
        btnDone.setVisibility(View.GONE);
        btnCapture.setVisibility(View.GONE);
        cardGridInfo.setVisibility(View.GONE);
        tvInstructions.setVisibility(View.GONE);

        btnCapture.setText("START");
        btnCapture.setBackgroundColor(Color.parseColor("#2196F3"));

        btnViewCaptured.setVisibility(View.GONE);

        // 5️⃣ Set listeners
        btnInspectFloor.setOnClickListener(v -> selectInspectionMode(InspectionMode.FLOOR));
        btnInspectWall.setOnClickListener(v -> selectInspectionMode(InspectionMode.WALL));
        btnCapture.setOnClickListener(v -> toggleCaptureMode());
        btnDone.setOnClickListener(v -> onDoneClicked());
        btnViewCaptured.setOnClickListener(v -> showCapturedImages());

        // 6️⃣ Create 2D grid view container (initially hidden)
        RelativeLayout rootLayout = findViewById(R.id.root_layout);
        gridViewContainer = new FrameLayout(this);
        gridViewContainer.setVisibility(View.GONE);
        gridViewContainer.setBackgroundColor(Color.parseColor("#F5F5F5"));
        RelativeLayout.LayoutParams containerParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        rootLayout.addView(gridViewContainer, containerParams);

        // 7️⃣ Initialize managers
        cornerManager = new CornerManager();
        cornerLineMeshManager = new MeshManager(surfaceView);
        visitedCellMeshManager = new MeshManager(surfaceView);
        floorOverlayMeshManager = new MeshManager(surfaceView);
        visitedCellManager = new VisitedCellManager(surfaceView, cornerManager, visitedCellMeshManager);
        gridManager = new GridManager();

        createAngleIndicator();

        // Back button behavior
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

        // AR Touch helper
        displayRotationHelper = new DisplayRotationHelper(this);
        tapHelper = new TapHelper(this);
        surfaceView.setOnTouchListener(tapHelper);

        // Renderer setup
        render = new SampleRender(surfaceView, this, getAssets());
        installRequested = false;

        // Settings setup
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

    /*   private void showCapturedImages() {
           File imgDir = new File(getExternalFilesDir(null), "cell_images");
           if (!imgDir.exists() || imgDir.list() == null || imgDir.list().length == 0) {
               Toast.makeText(this, "No images captured yet", Toast.LENGTH_SHORT).show();
               return;
           }

           File[] imageFiles = imgDir.listFiles((dir, name) -> name.endsWith(".jpg"));
           if (imageFiles == null || imageFiles.length == 0) {
               Toast.makeText(this, "No images found", Toast.LENGTH_SHORT).show();
               return;
           }

           // Sort by name (chronological)
           Arrays.sort(imageFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));

           // Build list of image paths
           List<String> imagePaths = new ArrayList<>();
           for (File f : imageFiles) {
               imagePaths.add(f.getAbsolutePath());
           }

           // Show image picker dialog
           AlertDialog.Builder builder =
                   new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);

           builder.setTitle("Select an image to view");
           builder.setItems(
                   imagePaths.stream()
                           .map(path -> new File(path).getName())
                           .toArray(String[]::new),
                   (dialog, which) -> {
                       String selectedPath = imagePaths.get(which);
                       showFullScreenImage(selectedPath);
                   }
           );
           builder.setNegativeButton("Cancel", null);
           builder.show();
       }*/
    private void showCapturedImages() {
        // ✅ Scan the SAME public folder where images are saved
        File imgDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "AR_Floor_Inspection"
        );

        if (!imgDir.exists() || imgDir.list() == null || imgDir.list().length == 0) {
            Toast.makeText(this, "No images captured yet", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] imageFiles = imgDir.listFiles((dir, name) -> name.endsWith(".jpg"));
        if (imageFiles == null || imageFiles.length == 0) {
            Toast.makeText(this, "No images found", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ SORT BY LAST MODIFIED TIME (most recent first)
        Arrays.sort(imageFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        List<String> imagePaths = new ArrayList<>();
        for (File f : imageFiles) {
            imagePaths.add(f.getAbsolutePath());
        }

        // ✅ FIX: Use android.app.AlertDialog (NOT androidx)
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select an image to view (most recent first)");
        builder.setItems(
                imagePaths.stream()
                        .map(path -> {
                            File f = new File(path);
                            // Show: "cell_005 • Oct 24, 14:30"
                            String name = f.getName();
                            String[] parts = name.split("_");
                            if (parts.length >= 4) {
                                String cellNum = parts[1];
                                String date = parts[2]; // yyyymmdd
                                String time = parts[3].replace(".jpg", ""); // hhmmss
                                return String.format("Cell %s • %s-%s %s:%s",
                                        Integer.parseInt(cellNum),
                                        date.substring(4,6), date.substring(6,8), // mm-dd
                                        time.substring(0,2), time.substring(2,4)  // hh:mm
                                );
                            }
                            return name;
                        })
                        .toArray(String[]::new),
                (dialog, which) -> showFullScreenImage(imagePaths.get(which))
        );
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showFullScreenImage(String imagePath) {
        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setBackgroundColor(Color.BLACK);

        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ FIX: Use android.app.AlertDialog (NOT androidx)
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setView(imageView);
        builder.setPositiveButton("Close", null);

        // ✅ Make dialog fill screen
        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Optional: Make image fill more of the screen
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
        }
    }
    private void createAngleIndicator() {
        RelativeLayout rootLayout = findViewById(R.id.root_layout);

        LinearLayout angleContainer = new LinearLayout(this);
        angleContainer.setOrientation(LinearLayout.VERTICAL);
        angleContainer.setGravity(android.view.Gravity.CENTER);
        angleContainer.setBackgroundColor(Color.parseColor("#CC000000"));
        angleContainer.setPadding(20, 15, 20, 15);
        angleContainer.setVisibility(View.GONE);

        RelativeLayout.LayoutParams containerParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        containerParams.addRule(RelativeLayout.CENTER_HORIZONTAL);
        containerParams.topMargin = 150;

        // Angle value
        tvCameraAngle = new TextView(this);
        tvCameraAngle.setTextColor(Color.WHITE);
        tvCameraAngle.setTextSize(24);
        tvCameraAngle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCameraAngle.setText("--°");
        angleContainer.addView(tvCameraAngle);

        // ✅ Status text (shows what's perfect)
        tvAngleStatus = new TextView(this);
        tvAngleStatus.setTextColor(Color.parseColor("#FFEB3B"));
        tvAngleStatus.setTextSize(12);
        tvAngleStatus.setText("Camera Angle");
        angleContainer.addView(tvAngleStatus);

        rootLayout.addView(angleContainer, containerParams);
        angleIndicator = angleContainer;
    }


    private void selectInspectionMode(InspectionMode mode) {
        currentMode = mode;
        modeSelectionContainer.setVisibility(View.GONE);
        tvInstructions.setVisibility(View.VISIBLE);
        btnDone.setVisibility(View.VISIBLE);

        if (mode == InspectionMode.FLOOR) {
            btnInspectFloor.setBackgroundColor(Color.parseColor("#4CAF50"));
            btnInspectWall.setBackgroundColor(Color.parseColor("#757575"));
            btnInspectVirtualWall.setBackgroundColor(Color.parseColor("#757575"));
            tvInstructions.setText("🏢 FLOOR MODE: Tap to place corner 1 of 4");
            cardHeightInput.setVisibility(View.GONE);
        } else if (mode == InspectionMode.WALL) {
            btnInspectWall.setBackgroundColor(Color.parseColor("#4CAF50"));
            btnInspectFloor.setBackgroundColor(Color.parseColor("#757575"));
            btnInspectVirtualWall.setBackgroundColor(Color.parseColor("#757575"));
            tvInstructions.setText("🧱 WALL MODE: Tap to place corner 1 of 4");
            cardHeightInput.setVisibility(View.GONE);
        } else if (mode == InspectionMode.VIRTUAL_WALL) {
            btnInspectVirtualWall.setBackgroundColor(Color.parseColor("#4CAF50"));
            btnInspectFloor.setBackgroundColor(Color.parseColor("#757575"));
            btnInspectWall.setBackgroundColor(Color.parseColor("#757575"));
            tvInstructions.setText("📐 VIRTUAL WALL: Enter room height below");
            cardHeightInput.setVisibility(View.VISIBLE);
        }

        // Reset state
        cornerManager = new CornerManager();
        storedFloorCorners = null;
        updateInstructions();

        Toast.makeText(this,
                mode == InspectionMode.FLOOR ? "Floor Inspection Mode" :
                        mode == InspectionMode.WALL ? "Wall Inspection Mode" :
                                "Virtual Wall Mode - Enter Height",
                Toast.LENGTH_SHORT).show();
    }
    private void onHeightConfirmed() {
        String heightStr = etRoomHeight.getText().toString().trim();
        if (heightStr.isEmpty()) {
            Toast.makeText(this, "Please enter room height", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            userInputRoomHeight = Float.parseFloat(heightStr);
            if (userInputRoomHeight < 2.0f || userInputRoomHeight > 4.0f) {
                Toast.makeText(this, "Height must be between 2.0m and 4.0m", Toast.LENGTH_LONG).show();
                return;
            }

            // ✅ Hide input card immediately
            cardHeightInput.setVisibility(View.GONE);

            // ✅ Update instructions immediately
            tvInstructions.setText(String.format("✓ Height: %.2fm • Now tap FLOOR corner 1 of 4", userInputRoomHeight));

            // ✅ Show corner hints
            cornerHintsContainer.setVisibility(View.VISIBLE);

            Toast.makeText(this,
                    String.format("Height set to %.2fm. Tap to place 4 corners on the FLOOR.", userInputRoomHeight),
                    Toast.LENGTH_LONG).show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid height value", Toast.LENGTH_SHORT).show();
        }
    }


    private void toggleCaptureMode() {
        captureMode = !captureMode;
        if (captureMode) {
            // Use text and background color for Button
            btnCapture.setText("CAPTURE");
            btnCapture.setBackgroundColor(Color.parseColor("#4CAF50")); // Green background
            tvInstructions.setText("📸 CAPTURE MODE: Position above cell, then press CAPTURE");
            angleIndicator.setVisibility(View.VISIBLE);
            btnCapture.setOnClickListener(v -> captureCurrentCell());
            Toast.makeText(this, "Position camera above cells and press CAPTURE button",
                    Toast.LENGTH_LONG).show();
        } else {
            btnCapture.setText("START");
            btnCapture.setBackgroundColor(Color.parseColor("#2196F3")); // Blue background
            angleIndicator.setVisibility(View.GONE);
            updateInstructions();
            currentStableCell = -1;
            btnCapture.setOnClickListener(v -> toggleCaptureMode());
            Toast.makeText(this, "Capture Mode OFF", Toast.LENGTH_SHORT).show();
        }
    }



    private int detectCellBelowCamera(float[] cameraPos, float[] cameraForward, float[] outAngle) {
        if (!gridManager.hasAllCorners()) {
            return -1;
        }

        List<GridManager.GridCell> allCells = gridManager.getAllCells();
        if (allCells == null || allCells.isEmpty()) {
            return -1;
        }

        float[] gridNormal = getGridPlaneNormal();

        // ✅ FIXED: Different angle logic for floors vs walls
        float angleFromPerpendicular;
        float displayAngle;
        float threshold;

        if (currentMode == InspectionMode.FLOOR) {
            // FLOOR: Measure angle from vertical (camera should point DOWN)
            float[] downVector = {0f, -1f, 0f};
            float dot = Math.abs(
                    cameraForward[0] * downVector[0] +
                            cameraForward[1] * downVector[1] +
                            cameraForward[2] * downVector[2]
            );
            angleFromPerpendicular = (float) Math.toDegrees(Math.acos(Math.min(1.0f, dot)));
            displayAngle = 90f - angleFromPerpendicular; // 90° = straight down
            threshold = 30f;

        } else {
            // WALL/VIRTUAL_WALL: Measure angle from HORIZONTAL plane
            // For walls, camera should look horizontally (perpendicular to gravity)

            // Project camera forward onto horizontal plane (remove Y component)
            float[] horizontalForward = {
                    cameraForward[0],
                    0f,  // Remove vertical component
                    cameraForward[2]
            };

            // Normalize horizontal projection
            float horizontalLength = (float) Math.sqrt(
                    horizontalForward[0] * horizontalForward[0] +
                            horizontalForward[2] * horizontalForward[2]
            );

            if (horizontalLength > 0.001f) {
                horizontalForward[0] /= horizontalLength;
                horizontalForward[2] /= horizontalLength;
            }

            // Calculate angle from horizontal (using Y component of original vector)
            float verticalComponent = Math.abs(cameraForward[1]);
            displayAngle = (float) Math.toDegrees(Math.asin(Math.min(1.0f, verticalComponent)));
            angleFromPerpendicular = displayAngle; // 0° = horizontal (perfect for walls)
            threshold = 30f;
        }

        outAngle[0] = displayAngle;
        currentCameraAngle = displayAngle;

        // ✅ Check alignment
        if (angleFromPerpendicular > threshold) {
            return -1;
        }

        // Project camera position onto grid plane
        float[] ordered = cornerManager.getOrderedCorners();
        float[] gridCenter = {
                (ordered[0] + ordered[3] + ordered[6] + ordered[9]) / 4,
                (ordered[1] + ordered[4] + ordered[7] + ordered[10]) / 4,
                (ordered[2] + ordered[5] + ordered[8] + ordered[11]) / 4
        };

        float[] camToGrid = {
                cameraPos[0] - gridCenter[0],
                cameraPos[1] - gridCenter[1],
                cameraPos[2] - gridCenter[2]
        };

        float distAlongNormal =
                camToGrid[0] * gridNormal[0] +
                        camToGrid[1] * gridNormal[1] +
                        camToGrid[2] * gridNormal[2];

        // ✅ Distance thresholds
        float distThreshold = (currentMode == InspectionMode.WALL ||
                currentMode == InspectionMode.VIRTUAL_WALL)
                ? 3.0f  // 3 meters for walls
                : 1.5f; // 1.5 meters for floors

        float absHeight = Math.abs(distAlongNormal);
        if (absHeight > distThreshold) {
            return -1;
        }

        // Project camera onto wall plane
        float[] projectedPos = {
                cameraPos[0] - distAlongNormal * gridNormal[0],
                cameraPos[1] - distAlongNormal * gridNormal[1],
                cameraPos[2] - distAlongNormal * gridNormal[2]
        };

        // Find closest cell
        int closest = -1;
        float minDist = Float.MAX_VALUE;
        float cellTolerance = (currentMode == InspectionMode.WALL ||
                currentMode == InspectionMode.VIRTUAL_WALL)
                ? 0.8f  // 80cm tolerance for walls
                : 0.4f; // 40cm tolerance for floors

        for (int i = 0; i < allCells.size(); i++) {
            GridManager.GridCell cell = allCells.get(i);
            float dx = projectedPos[0] - cell.center[0];
            float dy = projectedPos[1] - cell.center[1];
            float dz = projectedPos[2] - cell.center[2];
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist < cellTolerance && dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }

        return closest;
    }


    private void updateAngleIndicator(float angle) {
        if (angleIndicator == null || tvCameraAngle == null) return;

        runOnUiThread(() -> {
            tvCameraAngle.setText(String.format("%.1f°", angle));

            int color;
            String guidance;

            if (currentMode == InspectionMode.FLOOR) {
                guidance = "90° = Perfect";
                float deviation = Math.abs(angle - 90f);

                if (deviation < 5f) {
                    color = Color.parseColor("#4CAF50"); // Green
                } else if (deviation < 15f) {
                    color = Color.parseColor("#FFEB3B"); // Yellow
                } else if (deviation < 30f) {
                    color = Color.parseColor("#FF9800"); // Orange
                } else {
                    color = Color.parseColor("#F44336"); // Red
                }

            } else {
                // Wall: 0° is perfect (perpendicular to wall)
                guidance = "0° = Perfect";

                if (angle < 5f) {
                    color = Color.parseColor("#4CAF50"); // Green
                } else if (angle < 15f) {
                    color = Color.parseColor("#FFEB3B"); // Yellow
                } else if (angle < 30f) {
                    color = Color.parseColor("#FF9800"); // Orange
                } else {
                    color = Color.parseColor("#F44336"); // Red
                }
            }

            tvCameraAngle.setTextColor(color);

            // ✅ Update status text
            if (tvAngleStatus != null) {
                tvAngleStatus.setText(guidance);
            }
        });
    }






//  private long lastCaptureTime = 0;



    // Single-threaded executor to save images one by one
    private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor();

    //private long lastCaptureTime = 0;

    // Single-threaded executor to save images one by one
    //private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor();



    private void captureCellImage(int cellIndex, Frame frame) {
        try {
            if (cellIndex < 0 || cellIndex >= GRID_ROWS * GRID_COLS) {
                Log.e("capture1", "Invalid cell index: " + cellIndex);
                return;
            }

            if (cellImagePaths.containsKey(cellIndex)) {
                Log.d("capture2", "Cell already captured");
                return;
            }

            // ✅ Memory check
            if (!isMemorySafe()) {
                runOnUiThread(() ->
                        Toast.makeText(this, "❌ LOW MEMORY - Restart app", Toast.LENGTH_SHORT).show()
                );
                return;
            }

            // ✅ Rate limit
            long now = System.currentTimeMillis();
            if (now - lastCaptureTime < 3000) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Wait 3 seconds", Toast.LENGTH_SHORT).show()
                );
                return;
            }
            lastCaptureTime = now;

            Image image = null;
            try {
                image = frame.acquireCameraImage();

                if (image.getFormat() != ImageFormat.YUV_420_888) {
                    Log.e("capture3", "Wrong format: " + image.getFormat());
                    if (image != null) image.close(); // FIX: Close on error
                    return;
                }

                Log.d("capture4", "📸 Capturing cell " + (cellIndex + 1));

                // ✅ Create output file
                // File imgDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                File imgDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AR_Floor_Inspection");

                if (!imgDir.exists()) {
                    imgDir.mkdirs();
                }

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                String timestamp = dateFormat.format(new Date());
                String filename = String.format(Locale.US, "cell_%03d_%s.jpg",
                        cellIndex + 1, timestamp);
                File outputFile = new File(imgDir, filename);

                // ✅ CRITICAL: Direct conversion (NO intermediate buffers!)
                final Image finalImage = image;
                final int finalIndex = cellIndex;
                final File finalFile = outputFile;

                captureExecutor.execute(() -> {
                    boolean success = false;
                    try {
                        // ✅ Use 25% quality (was 40%)
                        success = YuvConverter.saveImageDirectly(finalImage, finalFile, 25);
                    } catch (Exception e) {
                        Log.e("capture5", "Save failed", e);
                    } finally {
                        // ✅ CRITICAL: Close in finally block
                        try {
                            finalImage.close();
                            Log.d("capture6", "Image closed");
                        } catch (Exception e) {
                            Log.e("capture7", "Failed to close image", e);
                        }
                    }

                    if (success && finalFile.exists()) {
                        Log.d("capture8", "✓ SAVED: " + finalFile.getName() +
                                " (" + (finalFile.length() / 1024) + " KB)");

                        // Add to gallery
                        MediaScannerConnection.scanFile(
                                HelloArActivity.this,
                                new String[]{finalFile.getAbsolutePath()},
                                new String[]{"image/jpeg"},
                                null
                        );

                        // Update UI
                        runOnUiThread(() -> {
                            cellImagePaths.put(finalIndex, finalFile.getAbsolutePath());

                            // Vibrate
                            try {
                                Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                                if (vib != null) vib.vibrate(150);
                            } catch (Exception ignored) {
                            }

                            int total = cellImagePaths.size();
                            int remaining = (GRID_ROWS * GRID_COLS) - total;
                            Toast.makeText(HelloArActivity.this,
                                    String.format("✓ Cell %d saved • %d more to go",
                                            finalIndex + 1, remaining),
                                    Toast.LENGTH_SHORT).show();

                            updateVisitedCountDisplay();
                            updateViewButtonVisibility();

                            if (gridViewVisible && gridView2D != null) {
                                gridView2D.invalidate();
                            }
                        });
                    } else {
                        Log.e(TAG, "❌ Save failed");
                        runOnUiThread(() ->
                                Toast.makeText(HelloArActivity.this,
                                        "❌ Failed to save cell " + (finalIndex + 1),
                                        Toast.LENGTH_SHORT).show()
                        );
                    }

                    // ✅ Explicit cleanup
                    System.gc();
                });

                // Don't close image here - executor will do it in finally block
                image = null;

            } catch (NotYetAvailableException e) {
                if (image != null) {
                    try {
                        image.close();
                    } catch (Exception ignored) {
                    }
                }
                runOnUiThread(() ->
                        Toast.makeText(this, "Camera busy", Toast.LENGTH_SHORT).show()
                );
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "❌ OUT OF MEMORY!", e);
                if (image != null) {
                    try {
                        image.close();
                    } catch (Exception ignored) {
                    }
                }
                runOnUiThread(() ->
                        new AlertDialog.Builder(this)
                                .setTitle("Memory Full")
                                .setMessage("Cannot capture. Restart app.")
                                .setPositiveButton("OK", null)
                                .show()
                );
            } catch (Exception e) {
                Log.e(TAG, "Capture failed", e);
                if (image != null) {
                    try {
                        image.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        catch (Exception e) {
            Log.e("capture9", "Capture failed", e);}
    }
    // Add this helper method
    private void updateViewButtonVisibility() {
        runOnUiThread(() -> {
            Button btnViewCaptured = findViewById(R.id.btnViewCaptured);
            if (btnViewCaptured != null) {
                if (cellImagePaths.isEmpty()) {
                    btnViewCaptured.setVisibility(View.GONE);
                } else {
                    btnViewCaptured.setVisibility(View.VISIBLE);
                    // Update button text with count
                    int count = cellImagePaths.size();
                    btnViewCaptured.setText("VIEW (" + count + ")");
                }
            }
        });
    }



    private boolean isMemorySafe() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long availableMemory = maxMemory - usedMemory;

        float percentUsed = (float) usedMemory / maxMemory * 100;

        Log.d(TAG, String.format("Memory: %.1f%% used (%.1f MB / %.1f MB free)",
                percentUsed,
                usedMemory / 1024f / 1024f,
                availableMemory / 1024f / 1024f));

        // If using more than 75%, refuse to capture
        if (percentUsed > 75) {
            Log.e(TAG, "❌ MEMORY CRITICAL - cannot capture");
            return false;
        }

        return true;
    }







    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        // ✅ Android is telling us memory is low
        if (level >= TRIM_MEMORY_MODERATE) {
            Log.w(TAG, "Memory pressure detected - forcing cleanup");

            // Stop capture mode
            if (captureMode) {
                runOnUiThread(() -> toggleCaptureMode());
            }

            // Aggressive GC
            System.gc();
            System.runFinalization();
            System.gc();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.e(TAG, "CRITICAL: Low memory warning! Forcing cleanup.");
        // DO NOT call finish() — just warn and clean up
        runOnUiThread(() -> {
            Toast.makeText(this, "MemoryWarning: Freeing resources...", Toast.LENGTH_LONG).show();
            // Optional: disable capture to prevent further memory pressure
            if (captureMode) {
                toggleCaptureMode(); // turns off capture mode
            }
        });

        // Aggressively release memory
        System.gc();
        System.runFinalization();
        System.gc();
    }


    private void highlightTargetCell(int cellIndex) {
        if (cellIndex < 0 || cellIndex >= gridManager.getAllCells().size()) return;

        try {
            GridManager.GridCell cell = gridManager.getAllCells().get(cellIndex);

            // Subtle yellow highlight (no animation)
            float[] highlightColor = {1.0f, 1.0f, 0.0f, 0.5f}; // Yellow, semi-transparent

            drawSingleCell(cell, cellOverlayShader, highlightColor);

        } catch (Exception e) {
            Log.w(TAG, "Failed to highlight cell: " + e.getMessage());
        }
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

                // â­ Lift each point by 2cm (more visible)
                float offsetY = 0.02f;
                p1[1] += offsetY;
                p2[1] += offsetY;
                p3[1] += offsetY;
                p4[1] += offsetY;

                // â­ Create quad with BOTH triangles using correct winding
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
    /**
     * Calculate the normal vector of the plane defined by the 4 corner anchors.
     * This allows us to detect if the camera is pointing perpendicular to the grid,
     * regardless of how the grid is tilted in 3D space.
     */
    private float[] getGridPlaneNormal() {
        if (!cornerManager.hasAllCorners()) {
            return new float[]{0f, 1f, 0f}; // Default up vector
        }
        float[] orderedCoordinates = cornerManager.getOrderedCorners();
        if (orderedCoordinates == null || orderedCoordinates.length != 12) {
            Log.e(TAG, "Invalid corner coordinates");
            return new float[]{0f, 1f, 0f};
        }
        float[] p1 = Arrays.copyOfRange(orderedCoordinates, 0, 3);
        float[] p2 = Arrays.copyOfRange(orderedCoordinates, 3, 6);
        float[] p3 = Arrays.copyOfRange(orderedCoordinates, 6, 9);
        float[] v1 = {p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2]};
        float[] v2 = {p3[0] - p1[0], p3[1] - p1[1], p3[2] - p1[2]};
        float[] normal = new float[3];
        normal[0] = v1[1] * v2[2] - v1[2] * v2[1];
        normal[1] = v1[2] * v2[0] - v1[0] * v2[2];
        normal[2] = v1[0] * v2[1] - v1[1] * v2[0];
        float len = (float) Math.sqrt(normal[0]*normal[0] + normal[1]*normal[1] + normal[2]*normal[2]);
        if (len > 0) {
            normal[0] /= len; normal[1] /= len; normal[2] /= len;
        }
        return normal;
    }
    // â­ NEW: Update the updateInstructions() method:

    private void updateInstructions() {
        runOnUiThread(() -> {
            int cornerCount = cornerManager.getCornerCount();
            for (int i = 0; i < cornerIndicators.length; i++) {
                cornerIndicators[i].setBackgroundResource(
                        i < cornerCount ? R.drawable.corner_indicator : R.drawable.corner_indicator_empty
                );
            }

            String surfaceType = currentMode == InspectionMode.FLOOR ? "floor" :
                    currentMode == InspectionMode.WALL ? "wall" :
                            "floor (for virtual wall)";

            if (cornerCount < 4) {
                if (currentMode == InspectionMode.VIRTUAL_WALL && cardHeightInput.getVisibility() == View.VISIBLE) {
                    tvInstructions.setText("📐 Enter room height below, then tap Confirm");
                    btnDone.setEnabled(false);
                    btnDone.setAlpha(0.5f);
                } else {
                    tvInstructions.setText(String.format("Tap to place %s corner %d of 4", surfaceType, cornerCount + 1));
                    btnDone.setEnabled(false);
                    btnDone.setAlpha(0.5f);
                }
                cornerHintsContainer.setVisibility(View.VISIBLE);
            } else {
                if (currentMode == InspectionMode.VIRTUAL_WALL) {
                    tvInstructions.setText("All 4 floor corners placed. Press 'Done' to create virtual walls.");
                } else {
                    tvInstructions.setText(String.format("All 4 %s corners placed. Press 'Done' to create grid.", surfaceType));
                }
                btnDone.setEnabled(true);
                btnDone.setAlpha(1.0f);
                cornerHintsContainer.setVisibility(View.GONE);
            }

            tvDistance.setVisibility(View.GONE);
            cardDistance.setVisibility(View.GONE);
        });
    }

    // â­ UPDATE: Enhanced onDoneClicked() with professional UI updates:
    private void onDoneClicked() {
        // Toggle 2D view if grid already exists
        if (gridManager != null && gridManager.hasAllCorners()) {
            toggle2DGridView();
            return;
        }

        // Handle Virtual Wall mode
        if (currentMode == InspectionMode.VIRTUAL_WALL) {
            if (cardHeightInput.getVisibility() == View.VISIBLE) {
                Toast.makeText(this, "Please confirm room height first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!cornerManager.hasAllCorners()) {
                Toast.makeText(this, "Place all 4 floor corners first", Toast.LENGTH_SHORT).show();
                return;
            }
            createVirtualWallAnchors();
            return;
        }

        // Original FLOOR/WALL logic
        if (!cornerManager.hasAllCorners()) {
            Toast.makeText(this, "Please place exactly 4 corners before pressing Done.", Toast.LENGTH_LONG).show();
            return;
        }
        float[] orderedCoordinates = cornerManager.getOrderedCorners();
        if (orderedCoordinates == null || orderedCoordinates.length != 12) {
            Toast.makeText(this, "Error in corner ordering. Try placing corners again.", Toast.LENGTH_LONG).show();
            return;
        }
        final float[] finalCoordinates = orderedCoordinates.clone();
        surfaceView.queueEvent(() -> {
            try {
                gridManager.setGridSize(GRID_ROWS, GRID_COLS);
                gridManager.setGapSize(GRID_GAP_SIZE);
                gridManager.initialize(finalCoordinates);
                gridManager.createMeshes(render);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Grid visualization created!", Toast.LENGTH_SHORT).show();
                    tvInstructions.setText("Grid overlay active - Tap '2D View' to edit");
                    tvGridSize.setText(GRID_ROWS + "×" + GRID_COLS + " Grid Active");
                    updateVisitedCountDisplay();

                    // ✅ FIX: Change button text to "2D VIEW"
                    btnDone.setText("2D VIEW");

                    btnCapture.setVisibility(View.VISIBLE);
                    btnCapture.setBackgroundColor(Color.parseColor("#2196F3")); // Blue for "START"
                    initialize2DGridView(finalCoordinates);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to create grid", e);
                runOnUiThread(() -> Toast.makeText(this, "Error creating grid", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // â­ NEW: Helper method to update visited count display
    private void updateVisitedCountDisplay() {
        int visitedCount = 0;
        for (boolean visited : visitedCells) {
            if (visited) visitedCount++;
        }

        int totalCells = GRID_ROWS * GRID_COLS;
        tvVisitedCount.setText(visitedCount + " of " + totalCells + " cells visited");
    }

    // â­ UPDATE: Enhanced toggle2DGridView() with UI updates:
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
            if (tvVisitedValueIn2DView != null) {
                int count = 0;
                for (boolean v : visitedCells) if (v) count++;
                tvVisitedValueIn2DView.setText(count + " / " + (GRID_ROWS * GRID_COLS));
            }


            Log.d(TAG, "Switched to 2D grid view");
        } else {
            // Show AR view
            gridViewContainer.setVisibility(View.GONE);
            surfaceView.setVisibility(View.VISIBLE);
            btnDone.setVisibility(View.VISIBLE);
            tvInstructions.setVisibility(View.VISIBLE);
            //cardGridInfo.setVisibility(View.VISIBLE);
            cardGridInfo.setVisibility(View.GONE);

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


    // â­ UPDATE: Enhanced updateGridMeshColors() with UI feedback:
    private void updateGridMeshColors() {
        try {
            // DON'T recreate meshes - they're already drawn in onDrawFrame
            // Just update the visited count display

            int visitedCount = 0;
            for (boolean visited : visitedCells) {
                if (visited) visitedCount++;
            }

            Log.d(TAG, "Updated visited cells: " + visitedCount);

            final int count = visitedCount;
            runOnUiThread(() -> {
                updateVisitedCountDisplay();
                // No toast spam - user already got feedback from saveCellImage
            });

        } catch (Exception e) {
            Log.e(TAG, "Error updating visited cells: " + e.getMessage());
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
        if (lastCameraPosition != null) {
            gridView2D.updateCameraPosition(lastCameraPosition.clone());
        }

        // â­ PROFESSIONAL: Create top bar for 2D view
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

        // â­ PROFESSIONAL: Create bottom action bar for 2D view
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
        statsParams.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);

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
        tvGridSizeValue.setText(GRID_ROWS + " Ã— " + GRID_COLS);
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
        this.tvVisitedValueIn2DView = tvVisitedValue; // ← Add this line

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
                (int) (56 * getResources().getDisplayMetrics().density)
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



    private void handleTapForCornerPlacement(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap == null || camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }
        if (currentMode == InspectionMode.VIRTUAL_WALL && cardHeightInput.getVisibility() == View.VISIBLE) {
            runOnUiThread(() ->
                    Toast.makeText(this, "Please confirm room height first", Toast.LENGTH_SHORT).show()
            );
            return;
        }
        // Require mode selection
        if (currentMode == InspectionMode.NONE) {
            runOnUiThread(() ->
                    Toast.makeText(this, "Select Floor or Wall mode first", Toast.LENGTH_SHORT).show()
            );
            return;
        }

        if (cornerManager.hasAllCorners()) {
            return;
        }

        float x = tap.getX();
        float y = tap.getY();

        for (HitResult hit : frame.hitTest(x, y)) {
            Trackable trackable = hit.getTrackable();

            if (trackable instanceof Plane) {
                Plane plane = (Plane) trackable;

                boolean isValidPlane = false;
                if (currentMode == InspectionMode.FLOOR || currentMode == InspectionMode.VIRTUAL_WALL) {
                    isValidPlane = (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING);
                } else if (currentMode == InspectionMode.WALL) {
                    isValidPlane = (plane.getType() == Plane.Type.VERTICAL);
                }

                if (!isValidPlane || !plane.isPoseInPolygon(hit.getHitPose())) {
                    continue;
                }

                Anchor anchor = hit.createAnchor();
                boolean added = cornerManager.addCorner(anchor, trackable);
                if (added) {
                    String modeText = currentMode == InspectionMode.FLOOR ? "Floor" : "Wall";
                    runOnUiThread(() ->
                            Toast.makeText(this, modeText + " Corner " + cornerManager.getCornerCount() + " placed", Toast.LENGTH_SHORT).show()
                    );
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
  /*  @Override
    protected void onDestroy() {
        if (captureExecutor != null) {
            captureExecutor.shutdown();
        }
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
    }*/
    @Override
    protected void onDestroy() {
        if (captureExecutor != null) {
            captureExecutor.shutdownNow(); // Force shutdown
        }
        if (session != null) {
            session.close();
            session = null;
        }
        // Clean up ALL GPU resources on GL thread
        if (surfaceView != null) {
            surfaceView.queueEvent(() -> {
                // Close all meshes, textures, framebuffers
                if (pointCloudMesh != null) pointCloudMesh.close();
                if (virtualObjectMesh != null) virtualObjectMesh.close();
                if (virtualSceneFramebuffer != null) virtualSceneFramebuffer.close();
                if (cubemapFilter != null) cubemapFilter.close();
                if (dfgTexture != null) dfgTexture.close();
                // ... and your custom managers
                cornerLineMeshManager.cleanup();
                visitedCellMeshManager.cleanup();
                floorOverlayMeshManager.cleanup();
                gridManager.cleanup();
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

        // ✅ Clear capture mode
        if (captureMode) {
            captureMode = false;
            if (angleIndicator != null) {
                angleIndicator.setVisibility(View.GONE);
            }
        }

        // ✅ Force memory cleanup
        System.gc();

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

            // â­ NEW: Inline shader for cell overlays with visible green color
            cellOverlayShader = createCellOverlayShader(render);

        } catch (IOException e) {
            Log.e(TAG, "Failed to load shader", e);
            messageSnackbarHelper.showError(this, "Failed to load shader: " + e);
        }
    }

    // â­ NEW METHOD: Create inline shader for cell overlays
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

    // â­ MODIFIED: Update onDrawFrame to use the new shader
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
        camera.getPose().getTranslation(lastCameraPosition, 0);

        // === MODIFIED: Only show angle guidance in capture mode ===
        if (captureMode && gridManager.hasAllCorners()) {
            // Get camera direction
            float[] quaternion = new float[4];
            camera.getPose().getRotationQuaternion(quaternion, 0);
            float x = quaternion[0], y = quaternion[1], z = quaternion[2], w = quaternion[3];

            float[] cameraForward = {
                    -(2.0f * (x * z - w * y)),
                    -(2.0f * (y * z + w * x)),
                    -(1.0f - 2.0f * (x * x + y * y))
            };

            // Detect cell ONLY FOR GUIDANCE
            float[] angleOut = new float[1];
            int cellBelow = detectCellBelowCamera(lastCameraPosition, cameraForward, angleOut);

            // ✅ Update UI every frame
            updateAngleIndicator(angleOut[0]);

            // ✅ Show which cell user is targeting
            if (cellBelow >= 0) {
                boolean shouldCapture = visitedCells[cellBelow];
                boolean alreadyCaptured = cellImagePaths.containsKey(cellBelow);

                currentStableCell = cellBelow;

                runOnUiThread(() -> {
                    int captured = cellImagePaths.size();

                    // ✅ Different angle guidance for floor vs wall
                    String angleGuidance;
                    if (currentMode == InspectionMode.FLOOR) {
                        angleGuidance = String.format("%.1f° (90° = Perfect)", angleOut[0]);
                    } else {
                        angleGuidance = String.format("%.1f° (0° = Perfect)", angleOut[0]);
                    }

                    if (alreadyCaptured) {
                        tvInstructions.setText(
                                String.format("✓ Cell %d captured • %s (%d/%d)",
                                        cellBelow + 1, angleGuidance, captured, GRID_ROWS * GRID_COLS)
                        );
                        btnCapture.setEnabled(false);
                        btnCapture.setAlpha(0.5f);
                    } else if (shouldCapture) {
                        tvInstructions.setText(
                                String.format("📸 Cell %d ready • %s • PRESS CAPTURE (%d/%d)",
                                        cellBelow + 1, angleGuidance, captured, GRID_ROWS * GRID_COLS)
                        );
                        btnCapture.setEnabled(true);
                        btnCapture.setAlpha(1.0f);
                    } else {
                        tvInstructions.setText(
                                String.format("⚠️ Cell %d not marked • Mark in 2D view first",
                                        cellBelow + 1)
                        );
                        btnCapture.setEnabled(false);
                        btnCapture.setAlpha(0.5f);
                    }
                });

                highlightTargetCell(cellBelow);

            } else {
                currentStableCell = -1;
                runOnUiThread(() -> {
                    int captured = cellImagePaths.size();
                    String angleGuidance;
                    if (currentMode == InspectionMode.FLOOR) {
                        angleGuidance = String.format("%.1f° (need 60-90°)", angleOut[0]);
                    } else {
                        angleGuidance = String.format("%.1f° (need 0-30°)", angleOut[0]);
                    }

                    tvInstructions.setText(
                            String.format("📐 Position over cell • %s (%d/%d)",
                                    angleGuidance, captured, GRID_ROWS * GRID_COLS)
                    );
                    btnCapture.setEnabled(false);
                    btnCapture.setAlpha(0.5f);
                });
            }
        }

        // ... rest of drawing code ...


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

        // Draw corner connection lines
        if (!cornerLineMeshManager.isEmpty() && lineShader != null) {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            cornerLineMeshManager.drawAll(render, lineShader);
        }

        // Draw grid overlay
        if (gridManager != null && cellOverlayShader != null && lineShader != null) {
            try {
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

                cellOverlayShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

                List<GridManager.GridCell> allCells = gridManager.getAllCells();
                for (int i = 0; i < allCells.size(); i++) {
                    GridManager.GridCell cell = allCells.get(i);

                    float[] cellColor;
                    if (i < visitedCells.length && visitedCells[i]) {
                        cellColor = new float[]{0.3f, 0.8f, 0.4f, 0.7f}; // Green for visited
                    } else {
                        cellColor = new float[]{0.7f, 0.7f, 0.7f, 0.3f}; // Gray for unvisited
                    }

                    drawSingleCell(cell, cellOverlayShader, cellColor);
                }

                float[] borderColor = {1.0f, 1.0f, 1.0f, 1.0f};
                gridManager.drawBorders(render, lineShader, borderColor);

            } catch (Exception e) {
                Log.e(TAG, "Grid drawing failed: " + e.getMessage());
            }
        }

        // Restore OpenGL state
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
    }

    // === NEW: Manual capture method triggered by button ===
    private void captureCurrentCell() {
        // ✅ Check if already capturing
        if (isCaptureInProgress) {
            Toast.makeText(this, "Capture in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Check memory first
        if (!isMemorySafe()) {
            Toast.makeText(this, "❌ Memory too low - restart app", Toast.LENGTH_LONG).show();
            return;
        }
        if (!captureMode || !gridManager.hasAllCorners()) {
            Toast.makeText(this, "Enable capture mode first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentStableCell < 0) {
            Toast.makeText(this, "Position above a cell first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!visitedCells[currentStableCell]) {
            Toast.makeText(this, "Mark cell in 2D view first!", Toast.LENGTH_LONG).show();
            return;
        }
        if (cellImagePaths.containsKey(currentStableCell)) {
            Toast.makeText(this, "Already captured", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Mark as in progress
        isCaptureInProgress = true;

        // ✅ Disable button
        runOnUiThread(() -> {
            btnCapture.setEnabled(false);
            btnCapture.setText("CAPTURING...");
        });

        final int cellToCapture = currentStableCell;
        surfaceView.queueEvent(() -> {
            try {
                Frame frame = session.update();
                captureCellImage(cellToCapture, frame);
                // ✅ 3 second cooldown
                surfaceView.postDelayed(() -> {
                    runOnUiThread(() -> {
                        btnCapture.setEnabled(true);
                        btnCapture.setText("CAPTURE");
                        // ✅ Clear flag after delay
                        isCaptureInProgress = false;
                    });
                }, 3000);
            } catch (Exception e) {
                Log.e(TAG, "Capture failed", e);
                runOnUiThread(() -> {
                    btnCapture.setEnabled(true);
                    btnCapture.setText("CAPTURE");
                    isCaptureInProgress = false; // ✅ Always clear flag on error
                });
            }
        });
    }

    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() != TrackingState.TRACKING) {
                continue;
            }
            if (!plane.isPoseInPolygon(plane.getCenterPose())) {
                continue;
            }

            // ✅ NEW: Only count planes that match selected mode
            if (currentMode == InspectionMode.FLOOR) {
                if (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING ||
                        plane.getType() == Plane.Type.HORIZONTAL_DOWNWARD_FACING) {
                    return true;
                }
            } else if (currentMode == InspectionMode.WALL) {
                if (plane.getType() == Plane.Type.VERTICAL) {
                    return true;
                }
            }
        }
        return false;
    }
    private void resetForNewMode() {
        // Clear existing corners
        cornerManager = new CornerManager();

        // Clear grid
        if (gridManager != null) {
            gridManager.cleanup();
            gridManager = new GridManager();
        }

        // Reset visited cells
        visitedCells = new boolean[GRID_ROWS * GRID_COLS];
        cellImagePaths.clear();

        // Reset UI
        updateInstructions();
        btnCapture.setVisibility(View.GONE);
        cardGridInfo.setVisibility(View.GONE);

        Toast.makeText(this, "Mode changed. Place new corners.", Toast.LENGTH_LONG).show();
    }

    private void showModeSelectionDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Change Inspection Mode")
                .setMessage("Current mode: " + currentMode)
                .setPositiveButton("Floor", (dialog, which) -> {
                    selectInspectionMode(InspectionMode.FLOOR);
                    resetForNewMode();
                })
                .setNegativeButton("Wall", (dialog, which) -> {
                    selectInspectionMode(InspectionMode.WALL);
                    resetForNewMode();
                })
                .setNeutralButton("Cancel", null)
                .show();
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

        // ✅ NEW: Enable both horizontal and vertical plane detection
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);

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
    // ===== VIRTUAL WALL HELPER METHODS =====


    private void createVirtualWallAnchors() {
        float[] floorCorners = cornerManager.getOrderedCorners();
        if (floorCorners == null || floorCorners.length != 12) {
            Toast.makeText(this, "Error getting floor corners", Toast.LENGTH_SHORT).show();
            return;
        }

        // Store floor corners
        storedFloorCorners = floorCorners.clone();

        // Calculate ceiling corners
        float[] floorTL = Arrays.copyOfRange(floorCorners, 0, 3);
        float[] floorTR = Arrays.copyOfRange(floorCorners, 3, 6);
        float[] floorBL = Arrays.copyOfRange(floorCorners, 6, 9);
        float[] floorBR = Arrays.copyOfRange(floorCorners, 9, 12);

        float[] ceilTL = floorTL.clone(); ceilTL[1] += userInputRoomHeight;
        float[] ceilTR = floorTR.clone(); ceilTR[1] += userInputRoomHeight;
        float[] ceilBL = floorBL.clone(); ceilBL[1] += userInputRoomHeight;
        float[] ceilBR = floorBR.clone(); ceilBR[1] += userInputRoomHeight;

        // ✅ Show visualization FIRST (immediate feedback)
        visualizeRoomBox(floorTL, floorTR, floorBL, floorBR, ceilTL, ceilTR, ceilBL, ceilBR);

        // ✅ Then show dialog (don't block on GL thread)
        runOnUiThread(() -> {
            showWallSelectionDialog(floorTL, floorTR, floorBL, floorBR, ceilTL, ceilTR, ceilBL, ceilBR);
        });
    }


    private void visualizeRoomBox(float[] floorTL, float[] floorTR, float[] floorBL, float[] floorBR,
                                  float[] ceilTL, float[] ceilTR, float[] ceilBL, float[] ceilBR) {

        // ✅ Show immediate UI feedback
        runOnUiThread(() -> {
            tvInstructions.setText("Creating virtual room box...");
        });

        surfaceView.queueEvent(() -> {
            try {
                List<Mesh> boxLines = new ArrayList<>();

                // Floor edges (white)
                boxLines.add(createLineMesh(floorTL, floorTR));
                boxLines.add(createLineMesh(floorTR, floorBR));
                boxLines.add(createLineMesh(floorBR, floorBL));
                boxLines.add(createLineMesh(floorBL, floorTL));

                // Ceiling edges (cyan)
                boxLines.add(createLineMesh(ceilTL, ceilTR));
                boxLines.add(createLineMesh(ceilTR, ceilBR));
                boxLines.add(createLineMesh(ceilBR, ceilBL));
                boxLines.add(createLineMesh(ceilBL, ceilTL));

                // Vertical edges (yellow)
                boxLines.add(createLineMesh(floorTL, ceilTL));
                boxLines.add(createLineMesh(floorTR, ceilTR));
                boxLines.add(createLineMesh(floorBL, ceilBL));
                boxLines.add(createLineMesh(floorBR, ceilBR));

                cornerLineMeshManager.replaceMeshes(boxLines);

                // ✅ Update UI after visualization is ready
                runOnUiThread(() -> {
                    tvInstructions.setText("Virtual room created! Select wall to inspect:");
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to visualize room box", e);
                runOnUiThread(() -> {
                    Toast.makeText(HelloArActivity.this,
                            "Failed to create visualization", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showWallSelectionDialog(float[] floorTL, float[] floorTR, float[] floorBL, float[] floorBR,
                                         float[] ceilTL, float[] ceilTR, float[] ceilBL, float[] ceilBR) {
        String[] options = {"Front Wall", "Right Wall", "Back Wall", "Left Wall"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Select Wall to Inspect")
                .setItems(options, (d, i) -> createGridForSelectedWall(i, floorTL, floorTR, floorBL, floorBR, ceilTL, ceilTR, ceilBL, ceilBR))
                .show();
    }

    private void createGridForSelectedWall(int wallIndex,
                                           float[] floorTL, float[] floorTR, float[] floorBL, float[] floorBR,
                                           float[] ceilTL, float[] ceilTR, float[] ceilBL, float[] ceilBR) {

        final float[] wallCorners = new float[12];
        final String[] wallName = {""};

        // ✅ Correct corner ordering (counter-clockwise from top-left)
        switch (wallIndex) {
            case 0: // Front Wall (looking from inside room)
                System.arraycopy(ceilTL, 0, wallCorners, 0, 3);    // Top Left
                System.arraycopy(ceilTR, 0, wallCorners, 3, 3);    // Top Right
                System.arraycopy(floorTL, 0, wallCorners, 6, 3);   // Bottom Left
                System.arraycopy(floorTR, 0, wallCorners, 9, 3);   // Bottom Right
                wallName[0] = "Front Wall";
                break;

            case 1: // Right Wall
                System.arraycopy(ceilTR, 0, wallCorners, 0, 3);    // Top Left
                System.arraycopy(ceilBR, 0, wallCorners, 3, 3);    // Top Right
                System.arraycopy(floorTR, 0, wallCorners, 6, 3);   // Bottom Left
                System.arraycopy(floorBR, 0, wallCorners, 9, 3);   // Bottom Right
                wallName[0] = "Right Wall";
                break;

            case 2: // Back Wall
                System.arraycopy(ceilBR, 0, wallCorners, 0, 3);    // Top Left
                System.arraycopy(ceilBL, 0, wallCorners, 3, 3);    // Top Right
                System.arraycopy(floorBR, 0, wallCorners, 6, 3);   // Bottom Left
                System.arraycopy(floorBL, 0, wallCorners, 9, 3);   // Bottom Right
                wallName[0] = "Back Wall";
                break;

            case 3: // Left Wall
                System.arraycopy(ceilBL, 0, wallCorners, 0, 3);    // Top Left
                System.arraycopy(ceilTL, 0, wallCorners, 3, 3);    // Top Right
                System.arraycopy(floorBL, 0, wallCorners, 6, 3);   // Bottom Left
                System.arraycopy(floorTL, 0, wallCorners, 9, 3);   // Bottom Right
                wallName[0] = "Left Wall";
                break;
        }

        // ✅ Show immediate feedback
        runOnUiThread(() -> {
            tvInstructions.setText("Creating " + wallName[0] + " grid...");
        });

        // ✅ Create grid on GL thread
        surfaceView.queueEvent(() -> {
            try {
                gridManager.setGridSize(GRID_ROWS, GRID_COLS);
                gridManager.setGapSize(GRID_GAP_SIZE);
                gridManager.initialize(wallCorners);
                gridManager.createMeshes(render);

                // ✅ Update UI immediately after grid creation
                runOnUiThread(() -> {
                    Toast.makeText(HelloArActivity.this,
                            wallName[0] + " grid created!", Toast.LENGTH_SHORT).show();

                    tvInstructions.setText(
                            "Grid on " + wallName[0] + " • Mark cells in 2D view, then capture");

                    // Show all controls
                    btnCapture.setVisibility(View.VISIBLE);
                    btnCapture.setText("START");
                    btnCapture.setBackgroundColor(Color.parseColor("#2196F3"));

                    btnDone.setVisibility(View.VISIBLE);
                    btnDone.setText("2D VIEW");
                    btnDone.setEnabled(true);
                    btnDone.setAlpha(1.0f);

                    cardGridInfo.setVisibility(View.VISIBLE);
                    tvGridSize.setText(GRID_ROWS + "×" + GRID_COLS + " Grid (" + wallName[0] + ")");
                    updateVisitedCountDisplay();

                    // Initialize 2D view
                    initialize2DGridView(wallCorners);
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to create wall grid", e);
                runOnUiThread(() -> {
                    Toast.makeText(HelloArActivity.this,
                            "Error creating grid", Toast.LENGTH_SHORT).show();
                });
            }
        });
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
        private Paint distancePaint;
        private float cellViewSize;
        private float[] cameraPosition;

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
            public float[] worldPosition;
            public float distance;

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
                this.worldPosition = null;
                this.distance = 0f;
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
         */
        public Custom2DGridView(Context context, Pose[] poses) {
            super(context);
            this.boundaryPoses = poses;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            init();
        }

        /**
         * Initialize paints for drawing
         */
        private void init() {
            borderPaint = new Paint();
            borderPaint.setColor(Color.DKGRAY);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);

            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);

            distancePaint = new Paint();
            distancePaint.setColor(Color.parseColor("#1976D2"));
            distancePaint.setTextAlign(Paint.Align.CENTER);
            distancePaint.setTextSize(12f);
        }

        /**
         * Update camera position and recalculate distances
         */
        public void updateCameraPosition(float[] camPos) {
            this.cameraPosition = camPos;
            calculateDistances();
            invalidate();
        }

        /**
         * Calculate distances from camera to each cell
         */
        private void calculateDistances() {
            if (cameraPosition == null || cells.isEmpty()) return;

            float[] orderedCoordinates = cornerManager.getOrderedCorners();
            if (orderedCoordinates == null) return;

            float[] topLeft = Arrays.copyOfRange(orderedCoordinates, 0, 3);
            float[] topRight = Arrays.copyOfRange(orderedCoordinates, 3, 6);
            float[] bottomLeft = Arrays.copyOfRange(orderedCoordinates, 6, 9);
            float[] bottomRight = Arrays.copyOfRange(orderedCoordinates, 9, 12);

            for (GridCell2D cell : cells) {
                float colRatio = (cell.col + 0.5f) / GRID_COLS;
                float rowRatio = (cell.row + 0.5f) / GRID_ROWS;

                float[] topPoint = interpolate(topLeft, topRight, colRatio);
                float[] bottomPoint = interpolate(bottomLeft, bottomRight, colRatio);
                float[] cellCenter = interpolate(topPoint, bottomPoint, rowRatio);

                cell.worldPosition = cellCenter;

                float dx = cellCenter[0] - cameraPosition[0];
                float dy = cellCenter[1] - cameraPosition[1];
                float dz = cellCenter[2] - cameraPosition[2];
                cell.distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
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
         * Update visited state from external array
         */
        public void updateVisitedCells(boolean[] visitedState) {
            for (int i = 0; i < cells.size() && i < visitedState.length; i++) {
                GridCell2D cell = cells.get(i);
                if (cell.visited != visitedState[i]) {
                    cell.visited = visitedState[i];
                    cell.fillPaint.setColor(cell.visited ? Color.parseColor("#4CAF50") : Color.WHITE);
                }
            }
            invalidate();
        }

        /**
         * Get current visited state of all cells
         */
        public boolean[] getVisitedState() {
            boolean[] state = new boolean[GRID_ROWS * GRID_COLS];
            for (int i = 0; i < cells.size() && i < state.length; i++) {
                state[i] = cells.get(i).visited;
            }
            return state;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w == 0 || h == 0) return;

            int padding = 40;
            int gridSize = Math.min(w, h) - 2 * padding;

            float startX = (w - gridSize) / 2f;
            float startY = (h - gridSize) / 2f;

            cellViewSize = (float) gridSize / GRID_COLS;
            textPaint.setTextSize(cellViewSize / 4);
            distancePaint.setTextSize(cellViewSize / 6);

            generate2DGrid(startX, startY, cellViewSize);
        }

        /**
         * Generate the 2D grid layout
         */
        private void generate2DGrid(float startX, float startY, float cellSize) {
            cells.clear();
            int cellCount = 0;

            for (int row = 0; row < GRID_ROWS; row++) {
                for (int col = 0; col < GRID_COLS; col++) {
                    float left = startX + col * cellSize;
                    float top = startY + row * cellSize;
                    float right = left + cellSize;
                    float bottom = top + cellSize;

                    RectF rect = new RectF(left, top, right, bottom);
                    GridCell2D cell = new GridCell2D(cellCount + 1, row, col, rect);

                    if (cellCount < visitedCells.length && visitedCells[cellCount]) {
                        cell.visited = true;
                        cell.fillPaint.setColor(Color.parseColor("#4CAF50"));
                    }

                    cells.add(cell);
                    cellCount++;
                }
            }

            if (cameraPosition != null) {
                calculateDistances();
            }

            Log.i(TAG, "2D Grid of " + GRID_ROWS * GRID_COLS + " cells generated.");
        }


        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (cells.isEmpty()) {
                canvas.drawText("Generating Grid...", getWidth() / 2f, getHeight() / 2f, textPaint);
                return;
            }
            for (GridCell2D cell : cells) {
                // Draw the cell background
                canvas.drawRoundRect(cell.rect, 10f, 10f, cell.fillPaint);
                canvas.drawRoundRect(cell.rect, 10f, 10f, borderPaint);
                // Draw cell number
                String num = String.valueOf(cell.cellNumber);
                float x = cell.rect.centerX();
                float textHeight = textPaint.descent() - textPaint.ascent();
                float yNumber = cell.rect.centerY() - textHeight / 4;
                canvas.drawText(num, x, yNumber, textPaint);

                // Draw distance
                if (cameraPosition != null && cell.distance > 0) {
                    String distText = String.format("%.2fm", cell.distance);
                    float yDist = cell.rect.centerY() + textHeight / 2;
                    canvas.drawText(distText, x, yDist, distancePaint);
                }

                // ✅ Draw small camera icon if image exists for this cell (ONLY ONCE!)
                int cellIdx = cell.row * GRID_COLS + cell.col;
                if (cellImagePaths.containsKey(cellIdx)) {
                    Paint capturePaint = new Paint();
                    capturePaint.setColor(Color.parseColor("#FF5722")); // Orange
                    capturePaint.setStyle(Paint.Style.FILL);
                    float iconSize = cell.rect.width() / 5f;
                    float iconX = cell.rect.right - iconSize * 1.5f;
                    float iconY = cell.rect.top + iconSize * 1.5f;
                    // Camera body
                    RectF cameraBody = new RectF(
                            iconX - iconSize/2,
                            iconY - iconSize/3,
                            iconX + iconSize/2,
                            iconY + iconSize/3
                    );
                    canvas.drawRoundRect(cameraBody, 2f, 2f, capturePaint);
                    // Lens
                    canvas.drawCircle(iconX, iconY, iconSize/4, capturePaint);
                }
            }
        }


        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float touchX = event.getX();
                float touchY = event.getY();

                for (GridCell2D cell : cells) {
                    if (cell.rect.contains(touchX, touchY)) {
                        cell.toggleVisited();
                        int cellIndex = cell.row * GRID_COLS + cell.col;
                        if (cellIndex < visitedCells.length) {
                            visitedCells[cellIndex] = cell.visited;
                        }
                        invalidate();

                        String distInfo = cell.distance > 0 ?
                                String.format(" (%.2fm)", cell.distance) : "";
                        Toast.makeText(getContext(), "Cell " + cell.cellNumber +
                                        (cell.visited ? " Visited!" : " Unvisited!") + distInfo,
                                Toast.LENGTH_SHORT).show();

                        return true;
                    }
                }
            }
            return super.onTouchEvent(event);
        }
    }
}
// ============================================================================
// ALSO UPDATE YOUR initialize2DGridView() METHOD
// Find this method in HelloArActivity and update it
// ============================================================================

