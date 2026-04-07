package com.hashteelabs.dodomap;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.media.MediaScannerConnection;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.os.Vibrator;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import com.google.firebase.auth.FirebaseAuth;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import android.net.Uri;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import android.location.LocationManager;
import android.location.Location;
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

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.Size;
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
import androidx.core.app.ActivityCompat;

import com.google.ar.core.*;

import com.google.ar.core.exceptions.*;

import com.hashteelabs.dodomap.common.helpers.CameraPermissionHelper;
import com.hashteelabs.dodomap.common.helpers.DepthSettings;
import com.hashteelabs.dodomap.common.helpers.DisplayRotationHelper;
import com.hashteelabs.dodomap.common.helpers.FullScreenHelper;
import com.hashteelabs.dodomap.common.helpers.InstantPlacementSettings;
import com.hashteelabs.dodomap.common.helpers.TapHelper;
import com.hashteelabs.dodomap.common.helpers.TrackingStateHelper;
import com.hashteelabs.dodomap.common.samplerender.Framebuffer;

import com.hashteelabs.dodomap.common.samplerender.SampleRender;
import com.hashteelabs.dodomap.common.samplerender.Shader;
import com.hashteelabs.dodomap.common.samplerender.Texture;
import com.hashteelabs.dodomap.common.samplerender.VertexBuffer;
import com.hashteelabs.dodomap.common.samplerender.arcore.BackgroundRenderer;
import com.hashteelabs.dodomap.common.samplerender.arcore.PlaneRenderer;
import com.hashteelabs.dodomap.common.samplerender.arcore.SpecularCubemapFilter;
import com.hashteelabs.dodomap.managers.CornerManager;

import com.hashteelabs.dodomap.managers.GridManager;
import com.hashteelabs.dodomap.managers.MeshManager;
import com.hashteelabs.dodomap.managers.VisitedCellManager;
import com.hashteelabs.dodomap.common.samplerender.Mesh.PrimitiveMode;
import com.hashteelabs.dodomap.common.samplerender.Mesh;
import com.hashteelabs.dodomap.DatabaseHelper;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MediaType;
import java.util.concurrent.TimeUnit;
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
    private boolean cloudAnchorsLoaded = false;
    // Add this with your other fields
    private YuvToRgbConverter yuvConverter;
    // UI Elements
    private Button btnDone;
    private Button btnRecordVideo;
    private Button btnUploadVideo;
    private Button btn3DRender;
    private boolean isRecording = false;
    private File currentVideoFile = null;
    private static final int REQUEST_PICK_VIDEO = 1002;
    private static final String VIDEO_UPLOAD_ENDPOINT = "https://vtdjepkjlodxix-8000.proxy.runpod.net/process";
    private static final String VIDEO_STATUS_ENDPOINT = "https://vtdjepkjlodxix-8000.proxy.runpod.net/status/";
    private static final String VIDEO_DOWNLOAD_ENDPOINT = "https://vtdjepkjlodxix-8000.proxy.runpod.net/download/";
    private TextView tvInstructions;
    private TextView tvDistance;
    private final List<Map<String, Object>> hostedAnchors = new ArrayList<>();
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
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
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
    private Button btnResolveAnchors;
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


    private float totalGridAreaMetersSq = 0.0f; // NEW: Store the calculated total area
    private boolean captureMode = false;
    private int targetCellForCapture = -1;
    private static final float CAPTURE_ANGLE_THRESHOLD = 30f; // Degrees from vertical
    private static final float CAPTURE_DISTANCE_THRESHOLD = 1.0f; // meters from cell
    private HashMap<Integer, String> cellImagePaths = new HashMap<>();
    private boolean[] capturedCells = new boolean[GRID_ROWS * GRID_COLS];
    private static final int DEFAULT_JPEG_QUALITY = 100; // Max JPEG quality for sharper crops
    private long lastCaptureCheckTime = 0;
    private static final long CAPTURE_CHECK_INTERVAL = 250;
    private Map<Integer, AreaCalculator.AreaResult> cellAreaResults = new HashMap<>();
    // Store the chosen floor number
    private int floorNumber = -1;

    private long lastCaptureTime = 0;
    private enum InspectionMode {
        FLOOR,
        WALL,
        VIRTUAL_WALL,
        NONE
    }
    private InspectionMode currentMode = InspectionMode.NONE;
    private Mesh floatingAnchorMesh;
    private Shader floatingAnchorShader;
    private float[] currentIntersectionPoint = null;
    private int currentTargetedCell = -1;
    private long animationStartTime = 0;
    private float anchorPulseScale = 1.0f;

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

    private long lastAutoCaptureTime = 0;
    private static final long AUTO_CAPTURE_COOLDOWN_MS = 3000; // 3 seconds

    // Add this line with your other member variables
    private float[] sideLengthsMeters = new float[4]; // Store the length of each side (A, B, C, D)
    private Map<Integer, String> cellQualityStatus = new HashMap<>();
    private DatabaseHelper dbHelper;
    private String currentWorkOrderId;
    private String currentStepId;
    private String currentStepName;
    private TextView tvWorkOrderInfo;
    // Collect Cloud Anchor IDs before saving
    private final List<String> hostedAnchorIds = new ArrayList<>();

    // Sync visited state with captured images so 2D view reflects captures.
    private void syncVisitedWithCaptured() {
        for (int i = 0; i < visitedCells.length; i++) {
            if (cellImagePaths.containsKey(i)) {
                visitedCells[i] = true;
            }
        }
    }

    // Update your onCreate() method - ADD THIS SECTION:
    @Override
    protected void onCreate(Bundle savedInstanceState) {
            // Video record/upload buttons removed
        super.onCreate(savedInstanceState);
        setContentView(com.hashteelabs.dodomap.R.layout.activity_main);
        tvWorkOrderInfo = findViewById(com.hashteelabs.dodomap.R.id.tvWorkOrderInfo);
        // Initialize database helper
        dbHelper = new DatabaseHelper(this);
        //  Log.d(TAG, "Intent extras: " + intent.getExtras());

        // ✅ ADD FIREBASE AUTH HERE
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously()
                    .addOnSuccessListener(a -> Log.d(TAG, "✅ Anonymous auth success"))
                    .addOnFailureListener(e -> Log.e(TAG, "❌ Auth failed", e));
        }

        // 1️⃣ Get data from Intent
        Intent intent = getIntent();
        currentWorkOrderId ="TEST_FLAT_101";
        //intent.getStringExtra("WORK_ORDER_ID");
        currentStepId = intent.getStringExtra("STEP_ID");
        currentStepName = intent.getStringExtra("STEP_NAME");

// 2️⃣ Save work order to DB if received
        if (currentWorkOrderId != null) {
            dbHelper.saveWorkOrder(currentWorkOrderId, currentStepId, currentStepName);
            Log.d(HelloArActivity.TAG, "Received and saved work order: " + currentWorkOrderId);
        } else {
            Log.d(HelloArActivity.TAG, "No work order received from intent");
        }


        updateWorkOrderDisplay();


        Button btnAreaReport = findViewById(com.hashteelabs.dodomap.R.id.btnAreaReport);
        btnAreaReport.setOnClickListener(v -> showAreaReport());
        btnAreaReport.setVisibility(View.GONE); // Hidden initially GONE
        // 1️⃣ Initialize basic mode selection views
        modeSelectionContainer = findViewById(com.hashteelabs.dodomap.R.id.modeSelectionContainer);
        btnInspectFloor = findViewById(com.hashteelabs.dodomap.R.id.btnInspectFloor);
        btnInspectWall = findViewById(com.hashteelabs.dodomap.R.id.btnInspectWall);

        // Bind Virtual Wall UI
        btnInspectVirtualWall = findViewById(com.hashteelabs.dodomap.R.id.btnInspectVirtualWall);
        cardHeightInput = findViewById(com.hashteelabs.dodomap.R.id.cardHeightInput);
        etRoomHeight = findViewById(com.hashteelabs.dodomap.R.id.etRoomHeight);
        btnConfirmHeight = findViewById(com.hashteelabs.dodomap.R.id.btnConfirmHeight);
        btnInspectVirtualWall.setOnClickListener(v -> selectInspectionMode(InspectionMode.VIRTUAL_WALL));
        btnConfirmHeight.setOnClickListener(v -> onHeightConfirmed());

        // 2️⃣ Initialize capture-related UI elements
        btnCapture = findViewById(com.hashteelabs.dodomap.R.id.btnCapture);
        btnDone = findViewById(com.hashteelabs.dodomap.R.id.btnDone);
        tvInstructions = findViewById(com.hashteelabs.dodomap.R.id.tvInstructions);
        tvDistance = findViewById(com.hashteelabs.dodomap.R.id.tvDistance);
        surfaceView = findViewById(com.hashteelabs.dodomap.R.id.surfaceview);

        btnResolveAnchors = findViewById(R.id.btnResolveAnchors);
        btnResolveAnchors.setVisibility(View.VISIBLE); // for testing, later you can keep it gone until needed
        btnResolveAnchors.setOnClickListener(v -> showFloorDropdownForResolve());

        // 3️⃣ Initialize additional controls + professional UI elements
        cardGridInfo = findViewById(com.hashteelabs.dodomap.R.id.cardGridInfo);
        tvGridSize = findViewById(com.hashteelabs.dodomap.R.id.tvGridSize);
        tvVisitedCount = findViewById(com.hashteelabs.dodomap.R.id.tvVisitedCount);
        cardDistance = findViewById(com.hashteelabs.dodomap.R.id.cardDistance);
        cornerHintsContainer = findViewById(com.hashteelabs.dodomap.R.id.cornerHintsContainer);

        // Corner indicators
        cornerIndicators[0] = findViewById(com.hashteelabs.dodomap.R.id.cornerIndicator1);
        cornerIndicators[1] = findViewById(com.hashteelabs.dodomap.R.id.cornerIndicator2);
        cornerIndicators[2] = findViewById(com.hashteelabs.dodomap.R.id.cornerIndicator3);
        cornerIndicators[3] = findViewById(com.hashteelabs.dodomap.R.id.cornerIndicator4);

        // View captured images button
        Button btnViewCaptured = findViewById(com.hashteelabs.dodomap.R.id.btnViewCaptured);

        // 4️⃣ Hide non-mode views initially
        modeSelectionContainer.setVisibility(View.VISIBLE);
        btnDone.setVisibility(View.GONE);
        btnCapture.setVisibility(View.GONE);
        cardGridInfo.setVisibility(View.GONE);
        tvInstructions.setVisibility(View.GONE);

        btnCapture.setText("CAPTURE ALL");
        btnCapture.setBackgroundColor(Color.parseColor("#4CAF50"));


        btnViewCaptured.setVisibility(View.GONE);

        btnRecordVideo = findViewById(R.id.btnRecordVideo);
        btnUploadVideo = findViewById(R.id.btnUploadVideo);
        btn3DRender = findViewById(R.id.btn3DRender);
        btn3DRender.setVisibility(View.VISIBLE);
        btnRecordVideo.setOnClickListener(v -> onRecordVideoClicked());
        btnUploadVideo.setOnClickListener(v -> onUploadVideoClicked());
        btn3DRender.setOnClickListener(v -> show3DRenderDialog());

        // 5️⃣ Set listeners
        btnInspectFloor.setOnClickListener(v -> selectInspectionMode(InspectionMode.FLOOR));
        btnInspectWall.setOnClickListener(v -> selectInspectionMode(InspectionMode.WALL));
        btnCapture.setOnClickListener(v -> captureCurrentCell());
        btnDone.setOnClickListener(v -> onDoneClicked());
        btnViewCaptured.setOnClickListener(v -> showCapturedImagesDialog());

        // 6️⃣ Create 2D grid view container (initially hidden)
        RelativeLayout rootLayout = findViewById(com.hashteelabs.dodomap.R.id.root_layout);
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

        ImageButton settingsButton = findViewById(com.hashteelabs.dodomap.R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(HelloArActivity.this, v);
            popup.setOnMenuItemClickListener(HelloArActivity.this::settingsMenuClick);
            popup.inflate(com.hashteelabs.dodomap.R.menu.settings_menu);
            popup.show();
        });

        updateInstructions();
    }


    // === Show cropped images in an AlertDialog with cell number and timestamp ===
    private void showCapturedImagesDialog() {
        if (cellImagePaths == null || cellImagePaths.isEmpty()) {
            Toast.makeText(this, "No images captured yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build sorted list by cell index
        List<Integer> indices = new ArrayList<>(cellImagePaths.keySet());
        Collections.sort(indices);

        List<String> labels = new ArrayList<>();
        for (int idx : indices) {
            String path = cellImagePaths.get(idx);
            String ts = extractTimestampLabel(path);
            labels.add(String.format(Locale.US, "Cell %d • %s", idx + 1, ts));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Captured Cells");
        builder.setItems(labels.toArray(new String[0]), (dialog, which) -> {
            int cellIdx = indices.get(which);
            String path = cellImagePaths.get(cellIdx);
            showImagePreviewDialog(cellIdx, path);
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private String extractTimestampLabel(String path) {
        try {
            File f = new File(path);
            String parentName = f.getParentFile() != null ? f.getParentFile().getName() : "";
            // Expected: capture_<millis>
            if (parentName.startsWith("capture_")) {
                String millisStr = parentName.substring("capture_".length());
                long ms = Long.parseLong(millisStr);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                return sdf.format(new Date(ms));
            }
        } catch (Exception ignored) {}
        return "unknown time";
    }

    private void showImagePreviewDialog(int cellIdx, String path) {
        if (path == null) return;
        try {
            ImageView iv = new ImageView(this);
            iv.setAdjustViewBounds(true);
            iv.setPadding(16, 16, 16, 16);

            // Decode with downscaling to avoid OOM
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            int req = 1024;
            int inSample = 1;
            while ((opts.outWidth / inSample) > req || (opts.outHeight / inSample) > req) {
                inSample <<= 1;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = inSample;
            Bitmap bmp = BitmapFactory.decodeFile(path, opts);
            iv.setImageBitmap(bmp);

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(String.format(Locale.US, "Cell %d", cellIdx + 1))
                    .setView(iv)
                    .setPositiveButton("Close", (d, w) -> d.dismiss())
                    .setOnDismissListener(d -> {
                        if (bmp != null && !bmp.isRecycled()) bmp.recycle();
                    })
                    .create();
            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to show image", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "showImagePreviewDialog error", e);
        }
    }
    private void createAngleIndicator() {
        RelativeLayout rootLayout = findViewById(com.hashteelabs.dodomap.R.id.root_layout);

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
            btnInspectFloor.setBackgroundColor(Color.parseColor("#FF9800"));
            btnInspectWall.setBackgroundColor(Color.parseColor("#757575"));
            btnInspectVirtualWall.setBackgroundColor(Color.parseColor("#757575"));
            tvInstructions.setText("🏢 FLOOR MODE: Tap to place corner 1 of 4");
            cardHeightInput.setVisibility(View.GONE);
            askFloorNumber();
        } else if (mode == InspectionMode.WALL) {
            btnInspectWall.setBackgroundColor(Color.parseColor("#FF9800"));
            btnInspectFloor.setBackgroundColor(Color.parseColor("#757575"));
            btnInspectVirtualWall.setBackgroundColor(Color.parseColor("#757575"));
            tvInstructions.setText("🧱 WALL MODE: Tap to place corner 1 of 4");
            cardHeightInput.setVisibility(View.GONE);
            askFloorNumber();
        } else if (mode == InspectionMode.VIRTUAL_WALL) {
            btnInspectVirtualWall.setBackgroundColor(Color.parseColor("#FF9800"));
            btnInspectFloor.setBackgroundColor(Color.parseColor("#757575"));
            btnInspectWall.setBackgroundColor(Color.parseColor("#757575"));
            tvInstructions.setText("📐 VIRTUAL WALL: Enter room height below");
            cardHeightInput.setVisibility(View.VISIBLE);
            askFloorNumber();
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
        // Simplified: single behavior = capture all cells.
        captureCurrentCell();
    }


    private void createFloatingAnchor() {
        surfaceView.queueEvent(() -> {
            try {
                int segments = 20;
                float radius = 0.18f; // 8cm
                List<Float> vertices = new ArrayList<>();
                vertices.add(0f); vertices.add(0f); vertices.add(0f); // center
                for (int i = 0; i <= segments; i++) {
                    float angle = (float) (2.0f * Math.PI * i / segments);
                    vertices.add(radius * (float) Math.cos(angle));
                    vertices.add(radius * (float) Math.sin(angle));
                    vertices.add(0f);
                }
                float[] vertexArray = new float[vertices.size()];
                for (int i = 0; i < vertices.size(); i++) vertexArray[i] = vertices.get(i);
                FloatBuffer buffer = ByteBuffer.allocateDirect(vertexArray.length * Float.BYTES)
                        .order(ByteOrder.nativeOrder()).asFloatBuffer();
                buffer.put(vertexArray).position(0);
                VertexBuffer vb = new VertexBuffer(render, 3, buffer);
                floatingAnchorMesh = new Mesh(render, PrimitiveMode.TRIANGLE_FAN, null, new VertexBuffer[]{vb});

                String vShader = "#version 300 es\nuniform mat4 u_MVP;\nlayout(location=0) in vec4 a_Pos;\nvoid main(){gl_Position=u_MVP*a_Pos;}";
                String fShader = "#version 300 es\nprecision mediump float;\nuniform vec4 u_Color;\nout vec4 o_FragColor;\nvoid main(){o_FragColor=u_Color;}";
                floatingAnchorShader = Shader.createFromSource(render, vShader, fShader, null);
                if (floatingAnchorShader == null) {
                    Log.e("fanchor", " Shader is null! Device may not support OpenGL ES 3.0.");
                    floatingAnchorMesh = null; // avoid partial state
                    return;
                }
                Log.d("fanchor", " Floating anchor created");
            } catch (Exception e) {
                Log.e("fanchor", " Failed to create floating anchor", e);
            }
        });
    }

    private int detectCellAndIntersection(float[] cameraPos, float[] cameraForward,
                                          float[] outAngle, float[] outIntersection) {
        if (!gridManager.hasAllCorners()) return -1;
        List<GridManager.GridCell> cells = gridManager.getAllCells();
        if (cells == null || cells.isEmpty()) return -1;

        // === 1. Compute plane from grid cells ===
        float[] p1 = cells.get(0).topLeft;
        float[] p2 = cells.get(0).topRight;
        float[] p3 = cells.get(0).bottomLeft;

        float[] planePoint = p1;
        float[] v1 = {p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2]};
        float[] v2 = {p3[0] - p1[0], p3[1] - p1[1], p3[2] - p1[2]};

        float[] normal = new float[3];
        normal[0] = v1[1] * v2[2] - v1[2] * v2[1];
        normal[1] = v1[2] * v2[0] - v1[0] * v2[2];
        normal[2] = v1[0] * v2[1] - v1[1] * v2[0];

        float len = (float) Math.sqrt(normal[0]*normal[0] + normal[1]*normal[1] + normal[2]*normal[2]);
        if (len > 1e-6f) {
            normal[0] /= len; normal[1] /= len; normal[2] /= len;
        }

        // === 2. Compute angle relative to this plane ===
        float dotWithNormal = Math.abs(
                cameraForward[0] * normal[0] +
                        cameraForward[1] * normal[1] +
                        cameraForward[2] * normal[2]
        );
        dotWithNormal = Math.min(1.0f, Math.max(0.0f, dotWithNormal));
        float angleFromPerpendicular = (float) Math.toDegrees(Math.acos(dotWithNormal));

        float displayAngle;
        if (currentMode == InspectionMode.FLOOR || currentMode == InspectionMode.VIRTUAL_WALL) {
            displayAngle = 90f - angleFromPerpendicular;
        } else {
            displayAngle = angleFromPerpendicular;
        }
        outAngle[0] = displayAngle;
        currentCameraAngle = displayAngle;

        // === 3. Validate angle ===
        boolean angleValid;
        if (currentMode == InspectionMode.FLOOR || currentMode == InspectionMode.VIRTUAL_WALL) {
            angleValid = (displayAngle >= 60f && displayAngle <= 90f);
        } else {
            angleValid = (displayAngle >= 0f && displayAngle <= 30f);
        }
        if (!angleValid) return -1;

        // === 4. Ray-plane intersection (using correct plane) ===
        float denom = normal[0]*cameraForward[0] + normal[1]*cameraForward[1] + normal[2]*cameraForward[2];
        if (Math.abs(denom) < 0.0001f) return -1;

        float[] camToPlane = {
                planePoint[0] - cameraPos[0],
                planePoint[1] - cameraPos[1],
                planePoint[2] - cameraPos[2]
        };
        float numer = normal[0]*camToPlane[0] + normal[1]*camToPlane[1] + normal[2]*camToPlane[2];
        float t = numer / denom;
        if (t < 0.1f || t > 10f) return -1;

        float[] intersection = {
                cameraPos[0] + cameraForward[0] * t,
                cameraPos[1] + cameraForward[1] * t,
                cameraPos[2] + cameraForward[2] * t
        };
        if (outIntersection != null) System.arraycopy(intersection, 0, outIntersection, 0, 3);

        // === 5. Find closest cell ===
        int targetCell = -1;
        float minDist = Float.MAX_VALUE;
        float tolerance = (currentMode == InspectionMode.FLOOR) ? 0.4f : 0.8f;
        for (int i = 0; i < cells.size(); i++) {
            GridManager.GridCell cell = cells.get(i);
            float dx = intersection[0] - cell.center[0];
            float dy = intersection[1] - cell.center[1];
            float dz = intersection[2] - cell.center[2];
            float dist = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < tolerance && dist < minDist) {
                minDist = dist;
                targetCell = i;
            }
        }
        return targetCell;
    }

    private void drawFloatingAnchor(float[] point, int cellIndex) {
        Log.d("drawAnchor", "Drawing at: " + Arrays.toString(point));
        if (floatingAnchorMesh == null || floatingAnchorShader == null || point == null) return;

        try {
            // === ✅ Dynamic Scaling Based on Camera Distance ===
            float[] camPos = lastCameraPosition;
            float distance = (float) Math.sqrt(
                    Math.pow(point[0] - camPos[0], 2) +
                            Math.pow(point[1] - camPos[1], 2) +
                            Math.pow(point[2] - camPos[2], 2)
            );

            // Scale: stays visually same size regardless of distance
            float baseScale = Math.max(0.02f, distance * 0.05f);

            // === Build model matrix ===
            float[] temp = new float[16];
            Matrix.setIdentityM(temp, 0);
            Matrix.translateM(temp, 0, point[0], point[1], point[2]);

            // === ✅ Pulsing animation remains same ===
            long now = System.currentTimeMillis();
            if (animationStartTime == 0) animationStartTime = now;
            float t = (now - animationStartTime) / 1000f;
            anchorPulseScale = 1.0f + 0.2f * (float) Math.sin(t * 3.0f);

            // Apply both distance scaling + pulse
            float totalScale = baseScale * anchorPulseScale;
            Matrix.scaleM(temp, 0, totalScale, totalScale, totalScale);

            // === MVP ===
            float[] mvp = new float[16];
            Matrix.multiplyMM(mvp, 0, viewMatrix, 0, temp, 0);
            Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, mvp, 0);

            // === ✅ Color logic ===
            float[] color;
            if (cellIndex < 0) color = new float[]{1.0f, 0.0f, 0.0f, 1.0f};          // red → no valid cell
                // Floating anchor color: red before capture, orange after capture
            else if (cellImagePaths.containsKey(cellIndex)) color = new float[]{1f, 0.6f, 0f, 0.8f}; // orange → captured
            else color = new float[]{1f, 0f, 0f, 0.8f};                                            // red → not captured

            // === Render ===
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            floatingAnchorShader.setMat4("u_MVP", mvp);
            floatingAnchorShader.setVec4("u_Color", color);
            render.draw(floatingAnchorMesh, floatingAnchorShader);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            GLES30.glDisable(GLES30.GL_BLEND);

        } catch (Exception e) {
            Log.e("draw anchor", "Error drawing floating anchor", e);
        }
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
    private float[] getScreenCenterRay(Camera camera, float[] viewMatrix, float[] projMatrix) {
        float[] viewProj = new float[16];
        Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0);
        float[] invViewProj = new float[16];
        if (!Matrix.invertM(invViewProj, 0, viewProj, 0)) {
            // Fallback to camera forward
            float[] quat = new float[4];
            camera.getPose().getRotationQuaternion(quat, 0);
            return new float[]{
                    -(2.0f * (quat[0] * quat[2] - quat[3] * quat[1])),
                    -(2.0f * (quat[1] * quat[2] + quat[3] * quat[0])),
                    -(1.0f - 2.0f * (quat[0] * quat[0] + quat[1] * quat[1]))
            };
        }

        float[] near4 = new float[4];
        float[] far4 = new float[4];
        // ✅ FIXED: Added srcVecOffset = 0 (6th argument)
        Matrix.multiplyMV(near4, 0, invViewProj, 0, new float[]{0, 0, -1, 1}, 0);
        Matrix.multiplyMV(far4, 0, invViewProj, 0, new float[]{0, 0, 1, 1}, 0);

        float[] near = {
                near4[0] / near4[3],
                near4[1] / near4[3],
                near4[2] / near4[3]
        };
        float[] far = {
                far4[0] / far4[3],
                far4[1] / far4[3],
                far4[2] / far4[3]
        };

        float[] dir = {
                far[0] - near[0],
                far[1] - near[1],
                far[2] - near[2]
        };
        float len = (float) Math.sqrt(dir[0]*dir[0] + dir[1]*dir[1] + dir[2]*dir[2]);
        if (len > 0) {
            dir[0] /= len; dir[1] /= len; dir[2] /= len;
        }
        return dir;
    }




//  private long lastCaptureTime = 0;



    // Single-threaded executor to save images one by one
    private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor();

    //private long lastCaptureTime = 0;

    // Single-threaded executor to save images one by one
    //private final ExecutorService captureExecutor = Executors.newSingleThreadExecutor();

    /**
     * Simple holder for a rectangular crop in pixel coordinates.
     */
    private static class CropRegion {
        final int x;
        final int y;
        final int width;
        final int height;

        CropRegion(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Bounding box (in image pixels) that encloses the projected grid.
     */
    private static class GridBounds {
        final int left;
        final int top;
        final int right;
        final int bottom;

        GridBounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int width() { return right - left; }
        int height() { return bottom - top; }
    }

    /**
     * Compute the projected bounding box of the grid (all cell corners) in image pixels.
     */
    private GridBounds computeGridBounds(Camera camera, List<GridManager.GridCell> cells, int imageWidth, int imageHeight) {
        if (camera == null || cells == null || cells.isEmpty()) return null;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        int valid = 0;

        for (GridManager.GridCell cell : cells) {
            float[][] corners = new float[][]{
                    cell.topLeft, cell.topRight, cell.bottomRight, cell.bottomLeft
            };
            for (float[] c : corners) {
                if (c == null) continue;
                float[] px = new float[2];
                if (projectWorldToImage(c, camera, imageWidth, imageHeight, px)) {
                    minX = Math.min(minX, px[0]);
                    maxX = Math.max(maxX, px[0]);
                    minY = Math.min(minY, px[1]);
                    maxY = Math.max(maxY, px[1]);
                    valid++;
                }
            }
        }

        if (valid < 4) return null;

        int left = Math.max(0, (int) Math.floor(minX));
        int top = Math.max(0, (int) Math.floor(minY));
        int right = Math.min(imageWidth, (int) Math.ceil(maxX));
        int bottom = Math.min(imageHeight, (int) Math.ceil(maxY));

        if (right <= left || bottom <= top) return null;

        return new GridBounds(left, top, right, bottom);
    }

    /**
     * Project a 3D world-space point into image pixel coordinates using camera pose + intrinsics.
     * Returns false if behind camera or off-frame.
     */
    private boolean projectWorldToImage(float[] worldPos,
                                        Camera camera,
                                        int imageWidth,
                                        int imageHeight,
                                        float[] outPixel2) {
        if (worldPos == null || camera == null ||
                outPixel2 == null || imageWidth <= 0 || imageHeight <= 0) {
            return false;
        }
        // World -> camera space
        float[] camPos = new float[3];
        camera.getPose().inverse().transformPoint(worldPos, 0, camPos, 0);

        float z = camPos[2];
        if (z >= -1e-6f) return false; // behind or on camera plane

        float[] focal = new float[2];
        float[] principal = new float[2];
        camera.getImageIntrinsics().getFocalLength(focal, 0);
        camera.getImageIntrinsics().getPrincipalPoint(principal, 0);

        float px = (camPos[0] / z) * focal[0] + principal[0];
        float py = (camPos[1] / z) * focal[1] + principal[1];

        outPixel2[0] = px;
        outPixel2[1] = py;
        return true;
    }

    private CropRegion calculateCropRegionForCell(
            int cellIndex,
            GridManager.GridCell cell,
            Camera camera,
            int imageWidth,
            int imageHeight,
            GridBounds gridBounds
    ) {
        if (cell == null || camera == null || imageWidth <= 0 || imageHeight <= 0 || gridBounds == null) {
            return null;
        }

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        int valid = 0;

        float[][] corners = new float[][]{
                cell.topLeft,
                cell.topRight,
                cell.bottomRight,
                cell.bottomLeft
        };

        // 🔹 LOG 1: Print 3D world coordinates of this cell
        Log.d("ProjectionLog", String.format(
                "Cell %d - 3D corners: TL(%.2f,%.2f,%.2f) TR(%.2f,%.2f,%.2f) BR(%.2f,%.2f,%.2f) BL(%.2f,%.2f,%.2f)",
                cellIndex,
                cell.topLeft[0], cell.topLeft[1], cell.topLeft[2],
                cell.topRight[0], cell.topRight[1], cell.topRight[2],
                cell.bottomRight[0], cell.bottomRight[1], cell.bottomRight[2],
                cell.bottomLeft[0], cell.bottomLeft[1], cell.bottomLeft[2]
        ));

        for (int i = 0; i < corners.length; i++) {
            float[] corner3D = corners[i];
            if (corner3D == null) continue;

            float[] px = new float[2];
            if (!projectWorldToImage(corner3D, camera, imageWidth, imageHeight, px)) {
                Log.d("ProjectionLog", "Cell " + cellIndex + " corner " + i + " not visible in image");
                continue;
            }

            // Count only corners that land inside the frame
            if (px[0] < 0 || px[0] > imageWidth || px[1] < 0 || px[1] > imageHeight) {
                Log.d("ProjectionLog", "Cell " + cellIndex + " corner " + i + " is outside frame bounds");
                continue;
            }

            // 🔹 LOG 2: Print projected 2D pixel for each corner
            Log.d("ProjectionLog", String.format(
                    "Cell %d corner %d → pixel (%.1f, %.1f)",
                    cellIndex, i, px[0], px[1]
            ));

            minX = Math.min(minX, px[0]);
            maxX = Math.max(maxX, px[0]);
            minY = Math.min(minY, px[1]);
            maxY = Math.max(maxY, px[1]);
            valid++;
        }

        // Require at least 2 visible corners; otherwise skip this cell
        if (valid < 2) {
            Log.w("ProjectionLog", "Cell " + cellIndex + " has <2 visible corners → skipping");
            return null;
        }

        // Base tile bounds (non-overlapping) inside the projected grid bounds.
        int actualCols = (gridManager != null) ? gridManager.getCols() : GRID_COLS;
        int actualRows = (gridManager != null) ? gridManager.getRows() : GRID_ROWS;
        int tileW = gridBounds.width() / actualCols;
        int tileH = gridBounds.height() / actualRows;
        int row = cellIndex / actualCols;
        int col = cellIndex % actualCols;
        int tileLeft = gridBounds.left + col * tileW;
        int tileTop = gridBounds.top + row * tileH;
        int tileRight = (col == actualCols - 1) ? gridBounds.right : gridBounds.left + (col + 1) * tileW;
        int tileBottom = (row == actualRows - 1) ? gridBounds.bottom : gridBounds.top + (row + 1) * tileH;

        // Use bounding box of visible corners as our base crop (don't force tile size)
        float bboxLeft = Math.max(0, minX);
        float bboxTop = Math.max(0, minY);
        float bboxRight = Math.min(imageWidth, maxX);
        float bboxBottom = Math.min(imageHeight, maxY);
        float bboxW = Math.max(0f, bboxRight - bboxLeft);
        float bboxH = Math.max(0f, bboxBottom - bboxTop);

        // If no visible area within image, skip
        if (bboxW <= 0 || bboxH <= 0) {
            Log.w("ProjectionLog", "Cell " + cellIndex + " projected box has zero visible area, skipping");
            return null;
        }

        // Pad the bounding box a bit so the visible content isn't cropped too tightly.
        float paddingFactor = 0.12f; // 12% padding around visible bbox
        int padX = Math.max(2, Math.round(bboxW * paddingFactor));
        int padY = Math.max(2, Math.round(bboxH * paddingFactor));

        int left = Math.max(tileLeft, (int) Math.floor(bboxLeft) - padX);
        int top = Math.max(tileTop, (int) Math.floor(bboxTop) - padY);
        int right = Math.min(tileRight, (int) Math.ceil(bboxRight) + padX);
        int bottom = Math.min(tileBottom, (int) Math.ceil(bboxBottom) + padY);

        // Clamp to tile bounds to avoid overlap.
        if (left < tileLeft) { right += (tileLeft - left); left = tileLeft; }
        if (right > tileRight) { left -= (right - tileRight); right = tileRight; }
        if (top < tileTop) { bottom += (tileTop - top); top = tileTop; }
        if (bottom > tileBottom) { top -= (bottom - tileBottom); bottom = tileBottom; }

        // Final clamp to image bounds.
        left = Math.max(0, left);
        top = Math.max(0, top);
        right = Math.min(imageWidth, right);
        bottom = Math.min(imageHeight, bottom);

        int width = right - left;
        int height = bottom - top;

        // Enforce even dimensions for YUV by shrinking inside the tile if needed.
        if ((width & 1) == 1) { width--; right = left + width; }
        if ((height & 1) == 1) { height--; bottom = top + height; }
        width = Math.max(2, width);
        height = Math.max(2, height);

        Log.d("ProjectionLog", String.format(
                "✅ Cell %d → CROP: x=%d, y=%d, w=%d, h=%d (valid %d, tile [%d,%d,%d,%d])",
                cellIndex, left, top, width, height, valid, tileLeft, tileTop, tileRight, tileBottom
        ));

        return new CropRegion(left, top, width, height);
    }



    private void captureAllCellsFromSingleFrame(Frame frame) {
        if (!isMemorySafe()) {
            runOnUiThread(() -> Toast.makeText(this, "❌ LOW MEMORY", Toast.LENGTH_SHORT).show());
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCaptureTime < 3000) {
            runOnUiThread(() -> Toast.makeText(this, "Wait 3 seconds", Toast.LENGTH_SHORT).show());
            return;
        }
        lastCaptureTime = now;

        Image image = null;
        Bitmap fullBitmap = null;
        int width = 0, height = 0;

        try {
            image = frame.acquireCameraImage();
            if (image.getFormat() != ImageFormat.YUV_420_888) return;
            width = image.getWidth();
            height = image.getHeight();

            // ✅ Convert YUV → high-quality RGB Bitmap
            fullBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            yuvConverter.convert(image, fullBitmap); // GPU-accelerated, full color

        } catch (Exception e) {
            Log.e(TAG, "Bitmap conversion failed", e);
            return;
        } finally {
            if (image != null) image.close();
        }

        Camera captureCamera = frame.getCamera();
        List<GridManager.GridCell> allCells = gridManager != null ? gridManager.getAllCells() : null;

        GridBounds gridBounds = computeGridBounds(captureCamera, allCells, width, height);
        if (gridBounds == null) {
            gridBounds = new GridBounds(0, 0, width, height);
        }

        CropRegion[] precomputedCrops = new CropRegion[GRID_ROWS * GRID_COLS];

        // Precompute crops
        for (int i = 0; i < GRID_ROWS * GRID_COLS; i++) {
            GridManager.GridCell cell = (allCells != null && i < allCells.size()) ? allCells.get(i) : null;
            CropRegion crop = calculateCropRegionForCell(i, cell, captureCamera, width, height, gridBounds);
            if (crop == null) {
                // Fallback to uniform tiling within grid bounds
                int row = i / GRID_COLS;
                int col = i % GRID_COLS;
                int cellW = gridBounds.width() / GRID_COLS;
                int cellH = gridBounds.height() / GRID_ROWS;
                int left = gridBounds.left + col * cellW;
                int top = gridBounds.top + row * cellH;
                int right = (col == GRID_COLS - 1) ? gridBounds.right : gridBounds.left + (col + 1) * cellW;
                int bottom = (row == GRID_ROWS - 1) ? gridBounds.bottom : gridBounds.top + (row + 1) * cellH;
                crop = new CropRegion(left, top, right - left, bottom - top);
            }
            precomputedCrops[i] = crop;
        }

        final Bitmap finalBitmap = fullBitmap;
        final CropRegion[] finalCrops = precomputedCrops;
        final long captureId = System.currentTimeMillis(); // unique folder per capture

        captureExecutor.execute(() -> {
            for (int i = 0; i < GRID_ROWS * GRID_COLS; i++) {
                CropRegion crop = finalCrops[i];
                if (crop == null || crop.width <= 0 || crop.height <= 0) continue;

                // ✅ High-quality crop from RGB bitmap, save lossless PNG
                try {
                    Bitmap cropped = Bitmap.createBitmap(
                            finalBitmap,
                            crop.x,
                            crop.y,
                            Math.min(crop.width, finalBitmap.getWidth() - crop.x),
                            Math.min(crop.height, finalBitmap.getHeight() - crop.y)
                    );

                    File parentDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AR_Floor_Inspection");
                    File imgDir = new File(parentDir, String.format(Locale.US, "capture_%d", captureId));
                    if (!imgDir.exists()) imgDir.mkdirs();
                    File file = new File(imgDir, String.format(Locale.US, "cell_%03d.png", i + 1));

                    // ✅ Save lossless
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                    try {
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        fos.flush();
                    } finally {
                        try { fos.close(); } catch (Exception ignore) {}
                    }
                    MediaScannerConnection.scanFile(HelloArActivity.this,
                            new String[]{file.getAbsolutePath()},
                            new String[]{"image/png"}, null);

                    Log.d("capture_all", "Saved high-quality cell " + i + ": " + file.getAbsolutePath());
                    cropped.recycle();

                    // Mark as captured/visited for UI (green cells) and store path
                    cellImagePaths.put(i, file.getAbsolutePath());
                    if (i < visitedCells.length) {
                        visitedCells[i] = true;
                    }
                    runOnUiThread(() -> {
                        if (gridView2D != null) {
                            gridView2D.updateVisitedCells(visitedCells);
                        }
                        updateVisitedCountDisplay();
                        updateViewButtonVisibility();
                    });

                } catch (Exception e) {
                    Log.e("capture_all", "Failed to save cell " + i, e);
                }
            }
            finalBitmap.recycle();
            // Final UI refresh to ensure 2D grid reflects all captured cells
            runOnUiThread(() -> {
                syncVisitedWithCaptured();
                if (gridView2D != null) {
                    gridView2D.updateVisitedCells(visitedCells);
                }
                updateVisitedCountDisplay();
                updateViewButtonVisibility();
            });
            System.gc();
        });
    }

    // Add this method inside HelloArActivity class
    /**
     * Rotate saved JPEG to match device/display rotation if necessary.
     * This uses a memory-conscious decode with inSampleSize to avoid OOM on large images.
     */
    private void maybeRotateImage(File file) {
        if (file == null || !file.exists()) return;
        try {
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case android.view.Surface.ROTATION_90:
                    degrees = 90; break;
                case android.view.Surface.ROTATION_180:
                    degrees = 180; break;
                case android.view.Surface.ROTATION_270:
                    degrees = 270; break;
                default:
                    degrees = 0; break;
            }

            if (degrees == 0) return; // no rotation needed

            // Decode bounds first
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int w = bounds.outWidth;
            int h = bounds.outHeight;

            // Compute sample size to limit memory (downscale if too large)
            int maxDim = 2048; // conservative
            int inSampleSize = 1;
            while (w / inSampleSize > maxDim || h / inSampleSize > maxDim) {
                inSampleSize <<= 1;
            }

            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = inSampleSize;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bitmap == null) return;

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.postRotate(degrees);
            android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            java.io.FileOutputStream fos = null;
            try {
                fos = new java.io.FileOutputStream(file);
                rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, DEFAULT_JPEG_QUALITY, fos);
            } finally {
                if (fos != null) {
                    try { fos.flush(); fos.close(); } catch (Exception ignored) {}
                }
            }

            bitmap.recycle();
            rotated.recycle();
        } catch (OutOfMemoryError oom) {
            Log.w(TAG, "Rotation decode OOM, skipping rotation", oom);
        } catch (Exception e) {
            Log.w(TAG, "maybeRotateImage failed", e);
        }
    }
    private void updateWorkOrderDisplay() {
        // ✅ Add null check
        if (tvWorkOrderInfo == null) {
            Log.w(TAG, "tvWorkOrderInfo is null - view not initialized yet");
            return;
        }

        if (currentWorkOrderId != null) {
            int capturedCount = dbHelper.getCaptureCount(currentWorkOrderId);
            int totalCells = GRID_ROWS * GRID_COLS;

            String displayText = String.format(
                    "📋 %s\n%s\n📸 %d/%d",
                    currentWorkOrderId,
                    currentStepName != null ? currentStepName : "",
                    capturedCount,
                    totalCells
            );

            tvWorkOrderInfo.setText(displayText);
            Log.d(TAG, "Updated work order display: " + displayText); // ✅ Add this log
        } else {
            tvWorkOrderInfo.setText("No Work Order");
            Log.d(TAG, "No work order to display");
        }
    }
    private void uploadImageToServer(File imageFile, int cellIndex) {
        new Thread(() -> {
            try {
                // Calculate cell dimensions in meters
                List<GridManager.GridCell> cells = gridManager.getAllCells();
                if (cells == null || cells.isEmpty()) {
                    Log.e(TAG, "No cells available");
                    return;
                }

                GridManager.GridCell cell = cells.get(cellIndex);

                // Calculate cell width (distance between topLeft and topRight)
                float[] tl = cell.topLeft;
                float[] tr = cell.topRight;
                float cellWidth = (float) Math.sqrt(
                        Math.pow(tr[0] - tl[0], 2) +
                                Math.pow(tr[1] - tl[1], 2) +
                                Math.pow(tr[2] - tl[2], 2)
                );

                // Calculate cell height (distance between topLeft and bottomLeft)
                float[] bl = cell.bottomLeft;
                float cellHeight = (float) Math.sqrt(
                        Math.pow(bl[0] - tl[0], 2) +
                                Math.pow(bl[1] - tl[1], 2) +
                                Math.pow(bl[2] - tl[2], 2)
                );

                Log.d(TAG, String.format("Cell %d dimensions: %.3fm x %.3fm",
                        cellIndex, cellWidth, cellHeight));

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                // ✅ CRITICAL: Send cell dimensions to server
                RequestBody body = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", imageFile.getName(),
                                RequestBody.create(imageFile, MediaType.parse("image/jpeg")))
                        .addFormDataPart("cell_width_m", String.valueOf(cellWidth))
                        .addFormDataPart("cell_height_m", String.valueOf(cellHeight))
                        .build();

                Request request = new Request.Builder()
                        .url("https://vtdjepkjlodxix-8000.proxy.runpod.net/upload/")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "{}";
                    Log.d("Upload", "Server response: " + responseBody);

                    JSONObject json = new JSONObject(responseBody);
                    String imageId = json.optString("image_id", "");

                    if (!imageId.isEmpty()) {
                        pollQualityStatus(imageId, cellIndex);
                    } else {
                        Log.e("Upload", "No image_id in response");
                    }
                }
            } catch (Exception e) {
                Log.e("Upload", "Failed", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "📤 Upload failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
    public class AreaCalculator {
        private static final String TAG = "AreaCalculator";

        /**
         * Calculate paintable area by combining AR grid dimensions with server detection
         *
         * @param cellAreaM2 Area of one grid cell in square meters (from AR)
         * @param totalCells Total number of cells in grid
         * @param serverResponse JSON response from server containing detection data
         * @return AreaResult object with all area calculations
         */
        public static AreaResult calculatePaintableArea(
                float cellAreaM2,
                int totalCells,
                String serverResponse) {

            try {
                JSONObject json = new JSONObject(serverResponse);

                // Get area data from server
                JSONObject areaAnalysis = json.optJSONObject("area_analysis");
                if (areaAnalysis == null) {
                    Log.e(TAG, "No area_analysis in server response");
                    return null;
                }

                // Total wall area from AR grid
                float totalWallAreaM2 = cellAreaM2 * totalCells;

                // Get pixel measurements from server
                double totalAreaPx = areaAnalysis.getDouble("total_area_px");
                double excludedAreaPx = areaAnalysis.getDouble("excluded_area_px");
                double paintableAreaPx = areaAnalysis.getDouble("paintable_area_px");

                // Calculate scale factor: meters per pixel
                float pixelsToMeters = (float) (totalWallAreaM2 / totalAreaPx);

                // Convert excluded area from pixels to square meters
                float excludedAreaM2 = (float) (excludedAreaPx * pixelsToMeters);

                // Calculate paintable area
                float paintableAreaM2 = totalWallAreaM2 - excludedAreaM2;
                float paintablePercentage = (paintableAreaM2 / totalWallAreaM2) * 100f;

                // Get detection details
                int numDetections = json.optInt("windows_doors_detected", 0);
                JSONArray detections = json.optJSONArray("detection_details");

                // Build result
                AreaResult result = new AreaResult();
                result.totalAreaM2 = totalWallAreaM2;
                result.excludedAreaM2 = excludedAreaM2;
                result.paintableAreaM2 = paintableAreaM2;
                result.paintablePercentage = paintablePercentage;
                result.numExclusions = numDetections;
                result.detections = detections;

                Log.d(TAG, String.format(
                        "Area Calculation: Total=%.2fm², Excluded=%.2fm², Paintable=%.2fm² (%.1f%%)",
                        totalWallAreaM2, excludedAreaM2, paintableAreaM2, paintablePercentage
                ));

                return result;

            } catch (Exception e) {
                Log.e(TAG, "Error calculating area", e);
                return null;
            }
        }

        /**
         * Result class containing all area calculations
         */
        public static class AreaResult {
            public float totalAreaM2;        // Total wall area in m²
            public float excludedAreaM2;     // Area covered by windows/doors in m²
            public float paintableAreaM2;    // Remaining paintable area in m²
            public float paintablePercentage; // Percentage that's paintable
            public int numExclusions;        // Number of windows/doors detected
            public JSONArray detections;     // Details of each detection

            @Override
            public String toString() {
                return String.format(
                        "Total: %.2fm² | Excluded: %.2fm² | Paintable: %.2fm² (%.1f%%)",
                        totalAreaM2, excludedAreaM2, paintableAreaM2, paintablePercentage
                );
            }

            /**
             * Get formatted summary for display
             */
            public String getSummary() {
                if (numExclusions == 0) {
                    return String.format("100%% Paintable\n%.2f m² total", totalAreaM2);
                } else {
                    return String.format(
                            "%.1f%% Paintable\n%.2f m² of %.2f m²\n(%d exclusions)",
                            paintablePercentage, paintableAreaM2, totalAreaM2, numExclusions
                    );
                }
            }

            /**
             * Get detailed breakdown
             */
            public String getDetailedBreakdown() {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(" Total Area: %.2f m²\n", totalAreaM2));

                if (numExclusions > 0) {
                    sb.append(String.format(" Excluded: %.2f m² (%d windows/doors)\n",
                            excludedAreaM2, numExclusions));
                }

                sb.append(String.format(" Paintable: %.2f m² (%.1f%%)",
                        paintableAreaM2, paintablePercentage));

                return sb.toString();
            }
        }
    }

    private void pollQualityStatus(String imageId, int cellIndex) {
        new Thread(() -> {
            int maxAttempts = 30;  // 30 attempts * 2 seconds = 1 minute max
            int attempt = 0;

            while (attempt < maxAttempts) {
                try {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder()
                            .url("https://vtdjepkjlodxix-8000.proxy.runpod.net/status/" + imageId)
                            .build();

                    Response response = client.newCall(request).execute();
                    String json = response.body().string();

                    Log.d("Poll", "Attempt " + attempt + ": " + json);

                    JSONObject statusJson = new JSONObject(json);
                    String status = statusJson.optString("status", "");
                    String qualityStatus = statusJson.optString("quality_status", "");

                    if ("completed".equals(status)) {
                        // ✅ Processing complete - save quality status to database
                        if (currentWorkOrderId != null) {
                            dbHelper.updateCaptureQuality(
                                    currentWorkOrderId,
                                    cellIndex,
                                    qualityStatus
                            );
                            Log.d("Poll", "Updated database: Cell " + cellIndex +
                                    " quality = " + qualityStatus);
                        }

                        // ✅ Update UI to show new database count
                        runOnUiThread(() -> {
                            updateWorkOrderDisplay();
                        });

                        // Calculate and display area
                        calculateAndDisplayArea(cellIndex, json);
                        break;

                    } else if ("error".equals(status)) {
                        Log.e("Poll", "Server error: " + json);

                        // ✅ Save error status to database
                        if (currentWorkOrderId != null) {
                            dbHelper.updateCaptureQuality(
                                    currentWorkOrderId,
                                    cellIndex,
                                    "error"
                            );
                        }

                        runOnUiThread(() -> {
                            Toast.makeText(HelloArActivity.this,
                                    "Server error processing image",
                                    Toast.LENGTH_SHORT).show();
                            updateWorkOrderDisplay();
                        });
                        break;
                    }

                    Thread.sleep(2000); // Poll every 2 seconds
                    attempt++;

                } catch (Exception e) {
                    Log.e("Poll", "Polling error", e);

                    // ✅ Save error status to database on exception
                    if (currentWorkOrderId != null) {
                        dbHelper.updateCaptureQuality(
                                currentWorkOrderId,
                                cellIndex,
                                "error"
                        );
                    }

                    runOnUiThread(() -> {
                        updateWorkOrderDisplay();
                    });
                    break;
                }
            }

            if (attempt >= maxAttempts) {
                Log.e("Poll", "Polling timeout for cell " + cellIndex);

                // ✅ Save timeout status to database
                if (currentWorkOrderId != null) {
                    dbHelper.updateCaptureQuality(
                            currentWorkOrderId,
                            cellIndex,
                            "timeout"
                    );
                }

                runOnUiThread(() -> {
                    Toast.makeText(HelloArActivity.this,
                            "Processing timeout for cell " + (cellIndex + 1),
                            Toast.LENGTH_SHORT).show();
                    updateWorkOrderDisplay();
                });
            }
        }).start();
    }

    private void calculateAndDisplayArea(int cellIndex, String serverResponse) {
        try {
            // Get cell dimensions from AR
            List<GridManager.GridCell> cells = gridManager.getAllCells();
            if (cells == null || cells.isEmpty()) {
                Log.e(TAG, "No cells available for area calculation");
                return;
            }

            GridManager.GridCell cell = cells.get(cellIndex);

            // Calculate cell dimensions
            float[] tl = cell.topLeft;
            float[] tr = cell.topRight;
            float[] bl = cell.bottomLeft;

            float cellWidth = (float) Math.sqrt(
                    Math.pow(tr[0] - tl[0], 2) +
                            Math.pow(tr[1] - tl[1], 2) +
                            Math.pow(tr[2] - tl[2], 2)
            );

            float cellHeight = (float) Math.sqrt(
                    Math.pow(bl[0] - tl[0], 2) +
                            Math.pow(bl[1] - tl[1], 2) +
                            Math.pow(bl[2] - tl[2], 2)
            );

            float cellAreaM2 = cellWidth * cellHeight;
            int totalCells = GRID_ROWS * GRID_COLS;

            Log.d(TAG, String.format("Cell %d: %.2fm x %.2fm = %.4f m²",
                    cellIndex, cellWidth, cellHeight, cellAreaM2));

            // ✅ Your existing AreaCalculator already handles this correctly!
            AreaCalculator.AreaResult areaResult = AreaCalculator.calculatePaintableArea(
                    cellAreaM2,
                    totalCells,
                    serverResponse
            );

            if (areaResult != null) {
                // Store result
                cellAreaResults.put(cellIndex, areaResult);

                // Update UI
                runOnUiThread(() -> {
                    String quality = areaResult.paintablePercentage > 90 ? "passed" : "failed";
                    update2DGrid(cellIndex, quality);

                    String message = String.format(
                            "Cell %d:\n%s\n%d windows/doors found",
                            cellIndex + 1,
                            areaResult.getSummary(),
                            areaResult.numExclusions
                    );
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();

                    updateAreaDisplay();
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error calculating area", e);
        }
    }

    private void updateAreaDisplay() {
        // Calculate total across all cells
        float totalWallArea = 0f;
        float totalPaintableArea = 0f;
        int cellsWithDetections = 0;

        for (AreaCalculator.AreaResult result : cellAreaResults.values()) {
            totalWallArea += result.totalAreaM2;
            totalPaintableArea += result.paintableAreaM2;
            if (result.numExclusions > 0) {
                cellsWithDetections++;
            }
        }

        if (cellAreaResults.isEmpty()) return;

        float overallPercentage = (totalPaintableArea / totalWallArea) * 100f;

        // Update UI with total area info
        String areaText = String.format(
                "Total: %.2f m²\nPaintable: %.2f m² (%.1f%%)\nCells with windows: %d",
                totalWallArea, totalPaintableArea, overallPercentage, cellsWithDetections
        );

        Log.d(TAG, "Area Summary: " + areaText);

        // ✅ Show Area Report button when data is available
        Button btnAreaReport = findViewById(com.hashteelabs.dodomap.R.id.btnAreaReport);
        if (btnAreaReport != null) {
            btnAreaReport.setVisibility(View.VISIBLE);
        }

        // Optional: Update grid info card
        if (tvGridSize != null) {
            String gridInfo = String.format(
                    "%d×%d Grid\n%.2f m² total\n%.1f%% paintable",
                    GRID_ROWS, GRID_COLS, totalWallArea, overallPercentage
            );
            tvGridSize.setText(gridInfo);
        }
    }

    // ===== ADD THIS METHOD TO SHOW DETAILED REPORT =====
    private void showAreaReport() {
        if (cellAreaResults.isEmpty()) {
            Toast.makeText(this, "No area data available yet", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder report = new StringBuilder();
        report.append("╔═══ PAINTING AREA REPORT ═══╗\n\n");

        float totalWallArea = 0f;
        float totalExcludedArea = 0f;
        float totalPaintableArea = 0f;
        int totalExclusions = 0;

        // Per-cell breakdown
        for (Map.Entry<Integer, AreaCalculator.AreaResult> entry : cellAreaResults.entrySet()) {
            int cellNum = entry.getKey() + 1;
            AreaCalculator.AreaResult result = entry.getValue();

            report.append(String.format("📐 Cell %d:\n", cellNum));
            report.append(String.format("   Total: %.2f m²\n", result.totalAreaM2));

            if (result.numExclusions > 0) {
                report.append(String.format("   🚪 Windows/Doors: %d\n", result.numExclusions));
                report.append(String.format("   ❌ Excluded: %.2f m²\n", result.excludedAreaM2));
                report.append(String.format("   ✅ Paintable: %.2f m² (%.1f%%)\n",
                        result.paintableAreaM2, result.paintablePercentage));
            } else {
                report.append("   ✅ 100% Paintable (no obstructions)\n");
            }
            report.append("\n");

            totalWallArea += result.totalAreaM2;
            totalExcludedArea += result.excludedAreaM2;
            totalPaintableArea += result.paintableAreaM2;
            totalExclusions += result.numExclusions;
        }

        // Summary
        report.append("╔═══════ SUMMARY ═══════╗\n");
        report.append(String.format("Total Wall Area: %.2f m²\n", totalWallArea));
        report.append(String.format(" Total Exclusions: %d\n", totalExclusions));
        report.append(String.format(" Excluded Area: %.2f m² (%.1f%%)\n",
                totalExcludedArea, (totalExcludedArea / totalWallArea) * 100));
        report.append(String.format(" Paintable Area: %.2f m² (%.1f%%)\n",
                totalPaintableArea, (totalPaintableArea / totalWallArea) * 100));

        // Show dialog
        new android.app.AlertDialog.Builder(this)
                .setTitle("📊 Area Analysis Report")
                .setMessage(report.toString())
                .setPositiveButton("OK", null)
                .setNeutralButton("Share", (dialog, which) -> shareAreaReport(report.toString()))
                .show();
    }

    // ===== ADD THIS METHOD TO SHARE REPORT =====
    private void shareAreaReport(String report) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Painting Inspection Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(shareIntent, "Share Report"));
    }


    private void update2DGrid(int cellIndex, String status) {
        runOnUiThread(() -> {
            if (gridView2D != null) {
                gridView2D.updateCellQuality(cellIndex, status);
            }
        });
    }
    // Add this helper method
    private void updateViewButtonVisibility() {
        runOnUiThread(() -> {
            Button btnViewCaptured = findViewById(com.hashteelabs.dodomap.R.id.btnViewCaptured);
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

            captureMode = false;

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
            captureMode = false; // disable capture flag
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
                Mesh floorMesh = new Mesh(render, com.hashteelabs.dodomap.common.samplerender.Mesh.PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{vb});

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

    // â­ NEW: Update the updateInstructions() method:

    private void updateInstructions() {
        runOnUiThread(() -> {
            int cornerCount = cornerManager.getCornerCount();
            for (int i = 0; i < cornerIndicators.length; i++) {
                cornerIndicators[i].setBackgroundResource(
                        i < cornerCount ? com.hashteelabs.dodomap.R.drawable.corner_indicator : com.hashteelabs.dodomap.R.drawable.corner_indicator_empty
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
    // UPDATE: Enhanced onDoneClicked() with area calculation and professional UI updates:
    // UPDATE: Enhanced onDoneClicked() with 4-side length calculation and professional UI updates:
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

                // >>>>> ADD 4-SIDE LENGTH CALCULATION HERE <<<<<
                float[] p1 = Arrays.copyOfRange(finalCoordinates, 0, 3); // Corner 1 (Top-Left)
                float[] p2 = Arrays.copyOfRange(finalCoordinates, 3, 6); // Corner 2 (Top-Right)
                float[] p3 = Arrays.copyOfRange(finalCoordinates, 6, 9); // Corner 3 (Bottom-Left)
                float[] p4 = Arrays.copyOfRange(finalCoordinates, 9, 12); // Corner 4 (Bottom-Right)

                // Calculate side lengths using 3D distance formula
                // Side A (e.g., Top: p1 -> p2)
                float dx_A = p2[0] - p1[0];
                float dy_A = p2[1] - p1[1];
                float dz_A = p2[2] - p1[2];
                float sideLengthA = (float) Math.sqrt(dx_A * dx_A + dy_A * dy_A + dz_A * dz_A);

                // Side B (e.g., Right: p2 -> p4)
                float dx_B = p4[0] - p2[0];
                float dy_B = p4[1] - p2[1];
                float dz_B = p4[2] - p2[2];
                float sideLengthB = (float) Math.sqrt(dx_B * dx_B + dy_B * dy_B + dz_B * dz_B);

                // Side C (e.g., Bottom: p4 -> p3)
                float dx_C = p3[0] - p4[0];
                float dy_C = p3[1] - p4[1];
                float dz_C = p3[2] - p4[2];
                float sideLengthC = (float) Math.sqrt(dx_C * dx_C + dy_C * dy_C + dz_C * dz_C);

                // Side D (e.g., Left: p3 -> p1)
                float dx_D = p1[0] - p3[0];
                float dy_D = p1[1] - p3[1];
                float dz_D = p1[2] - p3[2];
                float sideLengthD = (float) Math.sqrt(dx_D * dx_D + dy_D * dy_D + dz_D * dz_D);

                // Calculate total area (as before, using cross product or avg length * avg height)
                // Using average length and width for area (more robust for non-perfect rectangles)
                float avgLength = (sideLengthA + sideLengthC) / 2.0f;
                float avgWidth = (sideLengthB + sideLengthD) / 2.0f;
                float totalGridAreaMetersSq = avgLength * avgWidth;
                // >>>>> END 4-SIDE LENGTH CALCULATION <<<<<


                runOnUiThread(() -> {
                    // >>>>> UPDATE UI WITH CALCULATED DIMENSIONS <<<<<
                    String sideLengthsText = String.format(
                            "A: %.2fm, B: %.2fm, C: %.2fm, D: %.2fm",
                            sideLengthA, sideLengthB, sideLengthC, sideLengthD
                    );
                    String areaText = String.format("%.2f m²", totalGridAreaMetersSq);

                    Toast.makeText(this, "Grid created!\n" + sideLengthsText + "\nArea: " + areaText, Toast.LENGTH_LONG).show();
                    tvInstructions.setText("Grid overlay active - Tap '2D View' to edit");

                    // Update the main grid size display card - Shows all 4 sides and area
                    tvGridSize.setText(GRID_ROWS + "×" + GRID_COLS + " Grid\n" + sideLengthsText + "\n(" + areaText + ")");

                    // Show the card containing grid info (including area and side lengths)
                    cardGridInfo.setVisibility(View.VISIBLE);

                    // >>>>> END UPDATE UI <<<<<

                    updateVisitedCountDisplay();
                    // ✅ FIX: Change button text to "2D VIEW"
                    btnDone.setText("2D VIEW");
                    btnCapture.setVisibility(View.VISIBLE);
                    btnCapture.setBackgroundColor(Color.parseColor("#FF9800")); // Orange for "START"

                    initialize2DGridView(finalCoordinates);
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to create grid or calculate dimensions", e);
                runOnUiThread(() -> Toast.makeText(this, "Error creating grid or calculating dimensions", Toast.LENGTH_SHORT).show());
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
                syncVisitedWithCaptured();
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
        // Sync initial state with captured/visited cells
        syncVisitedWithCaptured();
        gridView2D.updateVisitedCells(visitedCells);

        // â­ PROFESSIONAL: Create top bar for 2D view
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#FF9800"));
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
        syncVisitedWithCaptured();
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
        btnBackToAR.setBackgroundColor(Color.parseColor("#FF9800"));
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
                                com.hashteelabs.dodomap.common.samplerender.Shader shader,
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
            Mesh cellMesh = new Mesh(render, com.hashteelabs.dodomap.common.samplerender.Mesh.PrimitiveMode.TRIANGLES, null, new VertexBuffer[]{vb});

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
        final HelloArActivity self = this;

        MotionEvent tap = tapHelper.poll();
        if (tap == null || camera.getTrackingState() != TrackingState.TRACKING) {
            return;
        }
        if (currentMode == InspectionMode.VIRTUAL_WALL && cardHeightInput.getVisibility() == View.VISIBLE) {
            self.runOnUiThread(() ->
                    Toast.makeText(self, "Please confirm room height first", Toast.LENGTH_SHORT).show()
            );
            return;
        }
        if (currentMode == InspectionMode.NONE) {
            self.runOnUiThread(() ->
                    Toast.makeText(self, "Select Floor or Wall mode first", Toast.LENGTH_SHORT).show()
            );
            return;
        }
        if (self.cornerManager.hasAllCorners()) {
            return;
        }

        float x = tap.getX();
        float y = tap.getY();

        for (HitResult hit : frame.hitTest(x, y)) {
            Trackable trackable = hit.getTrackable();
            if (!(trackable instanceof Plane)) continue;

            Plane plane = (Plane) trackable;
            boolean isValidPlane = false;
            if (self.currentMode == InspectionMode.FLOOR || self.currentMode == InspectionMode.VIRTUAL_WALL) {
                isValidPlane = (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING);
            } else if (self.currentMode == InspectionMode.WALL) {
                isValidPlane = (plane.getType() == Plane.Type.VERTICAL);
            }
            if (!isValidPlane || !plane.isPoseInPolygon(hit.getHitPose())) {
                continue;
            }

            // ✅ Create and add LOCAL anchor (so user sees it immediately)
            Anchor localAnchor = hit.createAnchor();
            Log.d("LOCAL ANCHOR", "✅ Local anchor created");

            boolean added = self.cornerManager.addCorner(localAnchor, trackable);
            if (added) {
                String modeText = self.currentMode == InspectionMode.FLOOR ? "Floor" : "Wall";
                self.runOnUiThread(() -> {
                    Toast.makeText(self,
                            modeText + " Corner " + self.cornerManager.getCornerCount() + " placed",
                            Toast.LENGTH_SHORT).show();
                    self.updateInstructions();
                });
                self.surfaceView.queueEvent(() -> {
                    self.createCornerConnectionLines();
                    if (self.cornerManager.hasAllCorners()) {
                        self.createFloorOverlay();
                    }
                });
            }

            // ✅ Host as Cloud Anchor (for persistence)
            final Anchor finalLocalAnchor = localAnchor;

            new Thread(() -> {
                Anchor cloudAnchor;
                try {
                    // Optional: use TTL if you want anchors valid >24h
                    // cloudAnchor = self.session.hostCloudAnchorWithTtl(finalLocalAnchor, 30); // 30 days
                    cloudAnchor = self.session.hostCloudAnchor(finalLocalAnchor);
                } catch (Exception e) {
                    Log.e("CANCHOR", "❌ Cloud Anchor hosting failed", e);
                    return;
                }

                if (cloudAnchor == null) {
                    Log.e("CANCHOR", "❌ Cloud anchor is null");
                    return;
                }

                int retries = 0;
                while (retries < 80) {
                    Anchor.CloudAnchorState state = cloudAnchor.getCloudAnchorState();
                    if (state == Anchor.CloudAnchorState.SUCCESS) {
                        String anchorId = cloudAnchor.getCloudAnchorId();
                        Log.d("CANCHOR", "✅ Cloud Anchor hosted successfully: " + anchorId);

                        hostedAnchorIds.add(anchorId);
                        self.runOnUiThread(() -> {
                            Toast.makeText(self, "Cloud Anchor hosted: " + anchorId, Toast.LENGTH_SHORT).show();
                            saveAnchorsToFirebase();
                        });
                        return;
                    } else if (state.isError()) {
                        Log.e("CANCHOR", "❌ Hosting failed: " + state);
                        self.runOnUiThread(() -> Toast.makeText(self,
                                "⚠️ Hosting failed: " + state,
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                    try {
                        Thread.sleep(100);
                        retries++;
                    } catch (InterruptedException ignored) {}
                }

                Log.e("CANCHOR", "❌ Hosting timed out");
            }).start();

            break;
        }
    }

    private double[] getCurrentGps() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permission not granted; skipping GPS lookup");
            return null;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm != null) {
            for (String provider : lm.getProviders(true)) {
                try {
                    Location loc = lm.getLastKnownLocation(provider);
                    if (loc != null) {
                        return new double[]{loc.getLatitude(), loc.getLongitude()};
                    }
                } catch (SecurityException se) {
                    Log.w(TAG, "Location access denied for provider " + provider, se);
                }
            }
        }
        return null;
    }

    private void askFloorNumber() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Floor Number");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g: 1");
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String floorStr = input.getText().toString().trim();
            if (floorStr.isEmpty()) {
                Toast.makeText(this, "Floor number required", Toast.LENGTH_SHORT).show();
                return;
            }
            floorNumber = Integer.parseInt(floorStr);
            Toast.makeText(this, "Floor set to " + floorNumber, Toast.LENGTH_SHORT).show();
            Log.d("CANCHOR", "✅ Floor number chosen: " + floorNumber);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    private void saveAnchorsToFirebase() {
        Log.d("CANCHOR", " Entered saveAnchorsToFirebase()");

        if (floorNumber < 0 || hostedAnchorIds.isEmpty()) {
            Log.w("CANCHOR", "⚠️ Floor number not set or no anchors to save");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("floor_number", floorNumber);
        data.put("anchor_ids", hostedAnchorIds);

        db.collection("cloudAnchors")
                .document("floor_" + floorNumber)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    Log.d("CANCHOR", "✅ Anchors saved to Firestore for floor " + floorNumber);
                    Toast.makeText(this, "Saved anchors for floor " + floorNumber, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("CANCHOR", "❌ Failed to save anchors to Firestore", e);
                    Toast.makeText(this, "Failed to save anchors", Toast.LENGTH_SHORT).show();
                });
    }

    private void showFloorDropdownForResolve() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("cloudAnchors")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> floorNumbers = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        // Assuming your Firestore docs are named like "floor_3"
                        floorNumbers.add(doc.getId().replace("floor_", ""));
                    }
                    showFloorSelectionDialog(floorNumbers);
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to fetch floors", e));
    }

    private void showFloorSelectionDialog(List<String> floorNumbers) {
        if (floorNumbers.isEmpty()) {
            Toast.makeText(this, "No saved anchors found", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Floor to Resolve");
        builder.setItems(floorNumbers.toArray(new String[0]), (dialog, which) -> {
            int chosenFloor = Integer.parseInt(floorNumbers.get(which));
            Log.d(TAG, "✅ User chose floor: " + chosenFloor);
            resolveAnchorsForFloor(chosenFloor); // <-- call your existing resolve method
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void resolveAnchorsForFloor(int floorNumber) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("cloudAnchors")
                .document("floor_" + floorNumber)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        List<String> ids = (List<String>) doc.get("anchor_ids"); // ⚠️ ensure field name matches Firestore
                        if (ids != null) {
                            Log.d("RESOLVE", "📦 Fetched " + ids.size() + " anchor IDs for floor " + floorNumber);
                            for (String id : ids) {
                                Log.d("RESOLVE", "➡️ Attempting to resolve anchor ID: " + id);
                                Anchor resolved = session.resolveCloudAnchor(id);
                                monitorResolvedAnchor(resolved, id);
                            }
                        } else {
                            Log.w("RESOLVE", "⚠️ No anchor_ids field found in Firestore doc for floor " + floorNumber);
                        }
                    } else {
                        Log.w("RESOLVE", "⚠️ No Firestore document found for floor_" + floorNumber);
                        Toast.makeText(this, "No anchors saved for floor " + floorNumber, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Log.e("RESOLVE", "❌ Failed to fetch anchors for floor " + floorNumber, e));
    }

    /**
     * Polls the Cloud Anchor state until it is resolved or fails.
     */
    private void monitorResolvedAnchor(Anchor anchor, String id) {
        Anchor.CloudAnchorState state = anchor.getCloudAnchorState();

        if (state == Anchor.CloudAnchorState.SUCCESS) {
            Log.d(TAG, "✅ Resolved anchor: " + id);
            // Add the resolved anchor to your scene or manager
            cornerManager.addCorner(anchor, null);
        } else if (state == Anchor.CloudAnchorState.TASK_IN_PROGRESS) {
            Log.d(TAG, "⏳ Still resolving: " + id);
            // Re-check after 1 second
            surfaceView.postDelayed(() -> monitorResolvedAnchor(anchor, id), 1000);
        } else {
            Log.e(TAG, "❌ Resolve failed for " + id + " state=" + state);
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
        return new Mesh(render, com.hashteelabs.dodomap.common.samplerender.Mesh.PrimitiveMode.LINES, null, new VertexBuffer[]{vb});
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
        if (item.getItemId() == com.hashteelabs.dodomap.R.id.depth_settings) {
            launchDepthSettingsMenuDialog();
            return true;
        } else if (item.getItemId() == com.hashteelabs.dodomap.R.id.instant_placement_settings) {
            launchInstantPlacementSettingsMenuDialog();
            return true;
        }
        return false;
    }


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
        if (currentWorkOrderId != null && !cloudAnchorsLoaded) {

            cloudAnchorsLoaded = true;
        }
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
            yuvConverter = new YuvToRgbConverter(this); // GPU-friendly YUV → RGB helper
        } catch (Exception e) {
            Log.e(TAG, "Failed to create YuvToRgbConverter", e);
        }
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
            // 🔹 Enhanced inline line shader (supports color)
            String lineVertShader =
                    "#version 300 es\n" +
                            "uniform mat4 u_ModelViewProjection;\n" +
                            "layout(location = 0) in vec4 a_Position;\n" +
                            "void main() {\n" +
                            "    gl_Position = u_ModelViewProjection * a_Position;\n" +
                            "}\n";

            String lineFragShader =
                    "#version 300 es\n" +
                            "precision mediump float;\n" +
                            "uniform vec4 u_Color;\n" +
                            "out vec4 o_FragColor;\n" +
                            "void main() {\n" +
                            "    o_FragColor = u_Color;\n" +
                            "}\n";

            lineShader = Shader.createFromSource(render, lineVertShader, lineFragShader, null);

// Default line color = white
            if (lineShader != null) {
                lineShader.setVec4("u_Color", new float[]{1.0f, 1.0f, 1.0f, 1.0f});
            }

            // â­ NEW: Inline shader for cell overlays with visible green color
            cellOverlayShader = createCellOverlayShader(render);
            createFloatingAnchor();

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
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(viewMatrix, 0);
        camera.getPose().getTranslation(lastCameraPosition, 0);

        // === CAPTURE MODE LOGIC (Enhanced) ===
        if (captureMode && gridManager.hasAllCorners()) {
            float[] camForward = getScreenCenterRay(camera, viewMatrix, projectionMatrix);
            float[] angleOut = new float[1];

            List<GridManager.GridCell> cells = gridManager.getAllCells();
            if (cells != null && !cells.isEmpty()) {
                float[] p1 = cells.get(0).topLeft;
                float[] p2 = cells.get(0).topRight;
                float[] p3 = cells.get(0).bottomLeft;

                // Compute plane normal
                float[] v1 = {p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2]};
                float[] v2 = {p3[0] - p1[0], p3[1] - p1[1], p3[2] - p1[2]};
                float[] normal = {
                        v1[1] * v2[2] - v1[2] * v2[1],
                        v1[2] * v2[0] - v1[0] * v2[2],
                        v1[0] * v2[1] - v1[1] * v2[0]
                };
                float len = (float) Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
                if (len > 1e-6f) {
                    normal[0] /= len;
                    normal[1] /= len;
                    normal[2] /= len;
                }

                // Compute angle relative to plane
                float dot = Math.abs(camForward[0] * normal[0] + camForward[1] * normal[1] + camForward[2] * normal[2]);
                dot = Math.min(1f, Math.max(0f, dot));
                float angleFromPerp = (float) Math.toDegrees(Math.acos(dot));
                float displayAngle = (currentMode == InspectionMode.FLOOR || currentMode == InspectionMode.VIRTUAL_WALL)
                        ? 90f - angleFromPerp : angleFromPerp;
                angleOut[0] = displayAngle;

                // Compute ray-plane intersection
                float[] planePoint = p1;
                float denom = normal[0] * camForward[0] + normal[1] * camForward[1] + normal[2] * camForward[2];
                if (Math.abs(denom) > 0.001f) {
                    float[] camToPlane = {
                            planePoint[0] - lastCameraPosition[0],
                            planePoint[1] - lastCameraPosition[1],
                            planePoint[2] - lastCameraPosition[2]
                    };
                    float numer = normal[0] * camToPlane[0] + normal[1] * camToPlane[1] + normal[2] * camToPlane[2];
                    float t = numer / denom;
                    if (t >= 0.05f && t <= 20f) {
                        currentIntersectionPoint = new float[]{
                                lastCameraPosition[0] + camForward[0] * t,
                                lastCameraPosition[1] + camForward[1] * t,
                                lastCameraPosition[2] + camForward[2] * t
                        };
                    } else {
                        currentIntersectionPoint = null;
                    }
                } else {
                    currentIntersectionPoint = null;
                }
            }

            // Detect which cell is targeted
            int cellBelow = detectCellAndIntersection(lastCameraPosition, camForward, angleOut, null);
            currentTargetedCell = cellBelow;
            updateAngleIndicator(angleOut[0]);

            // In onDrawFrame(), inside captureMode block:
            if (cellBelow >= 0 && cellBelow < visitedCells.length) {
                boolean captured = cellImagePaths.containsKey(cellBelow);
                runOnUiThread(() -> {
                    String guidance = (currentMode == InspectionMode.FLOOR)
                            ? String.format("%.1f° (90° = Perfect)", angleOut[0])
                            : String.format("%.1f° (0° = Perfect)", angleOut[0]);

                    if (captured) {
                        tvInstructions.setText(String.format("✓ Cell %d captured • %s",
                                cellBelow + 1, guidance));
                    } else {
                        // ✅ SIMPLE: just show which cell you're pointing at
                        tvInstructions.setText(String.format("🎯 Aiming at Cell %d • %s",
                                cellBelow + 1, guidance));
                    }
                    // ✅ Always enable "CAPTURE ALL"
                    btnCapture.setEnabled(true);
                    btnCapture.setAlpha(1.0f);
                });
                highlightTargetCell(cellBelow);
            } else {
                runOnUiThread(() -> {
                    String guidance = (currentMode == InspectionMode.FLOOR)
                            ? String.format("%.1f° (need 60–90°)", angleOut[0])
                            : String.format("%.1f° (need 0–30°)", angleOut[0]);
                    tvInstructions.setText("📸 Position camera over entire grid • " + guidance);
                    btnCapture.setEnabled(true);
                    btnCapture.setAlpha(1.0f);
                });
            }
        }
        // Handle tap for corner placement (outside capture mode logic)
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
                // Depth not ready yet
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

        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);
        camera.getViewMatrix(viewMatrix, 0);

        // Draw corner anchors
        for (CornerManager.WrappedAnchor wrappedAnchor : cornerManager.getCorners()) {
            drawAnchor(wrappedAnchor.getAnchor());
        }

        // Draw corner lines
        if (!cornerLineMeshManager.isEmpty() && lineShader != null) {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            cornerLineMeshManager.drawAll(render, lineShader);
        }

        // === DRAW GRID WITH ENHANCED VISUALS ===
        if (gridManager != null && cellOverlayShader != null && lineShader != null) {
            try {
                Matrix.setIdentityM(modelMatrix, 0);
                Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
                cellOverlayShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
                lineShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);

                List<GridManager.GridCell> allCells = gridManager.getAllCells();

                // Fill cells based on state
                for (int i = 0; i < allCells.size(); i++) {
                    GridManager.GridCell cell = allCells.get(i);
                    float[] color;
                    if (cellImagePaths.containsKey(i)) {
                        // ✅ CAPTURED = Green
                        color = new float[]{0.2f, 0.9f, 0.3f, 0.7f};
                    } else {
                        // ✅ UNCAPTURED = Blue
                        color = new float[]{0.2f, 0.6f, 1.0f, 0.6f};
                    }
                    drawSingleCell(cell, cellOverlayShader, color);
                }

                // Grid borders (white)
                gridManager.drawBorders(render, lineShader, new float[]{1.0f, 1.0f, 1.0f, 1.0f});

                // ✅ Red outline for captured cells
             /*   for (int i = 0; i < allCells.size(); i++) {
                    if (cellImagePaths.containsKey(i)) {
                        drawCellCapturedIndicator(allCells.get(i), lineShader);
                    }
                }*/

            } catch (Exception e) {
                Log.e(TAG, "Grid drawing failed: " + e.getMessage());
            }
        }

        // Draw floating anchor (only in capture mode with valid intersection)
        if (captureMode && currentIntersectionPoint != null) {
            drawFloatingAnchor(currentIntersectionPoint, currentTargetedCell);
        }

        // Restore OpenGL state
        GLES30.glDepthFunc(GLES30.GL_LEQUAL);
        GLES30.glDepthMask(true);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glEnable(GLES30.GL_CULL_FACE);
    }

    // === NEW: Manual capture method triggered by button ===

    private void captureCurrentCell() {
        if (isCaptureInProgress) {
            Toast.makeText(this, "Capture already in progress...", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ ONLY check if grid is ready
        if (!gridManager.hasAllCorners()) {
            Toast.makeText(this, "Grid not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ OPTIONAL: Check if entire grid is visible (implement canSeeEntireGrid properly)
        // if (!canSeeEntireGrid()) {
        //     Toast.makeText(this, "⚠️ Position camera to see entire grid", Toast.LENGTH_LONG).show();
        //     return;
        // }

        isCaptureInProgress = true;
        runOnUiThread(() -> {
            btnCapture.setEnabled(false);
            btnCapture.setText("CAPTURING ALL CELLS...");
        });

        surfaceView.queueEvent(() -> {
            try {
                Frame frame = session.update();
                captureAllCellsFromSingleFrame(frame); // ✅ ONE FRAME → 16 CROPS
            } catch (Exception e) {
                Log.e(TAG, "Capture failed", e);
            } finally {
                runOnUiThread(() -> {
                    btnCapture.setEnabled(true);
                    btnCapture.setText("CAPTURE");
                    isCaptureInProgress = false;
                });
            }
        });
    }

    // Helper method to check if entire grid is visible
    private boolean canSeeEntireGrid() {
        List<GridManager.GridCell> cells = gridManager.getAllCells();
        if (cells == null || cells.isEmpty()) return false;

        // Check if all 4 corners are visible in current view
        // (Implementation depends on your projection logic)
        return true; // Simplified
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
                .setTitle(com.hashteelabs.dodomap.R.string.options_title_instant_placement)
                .setMultiChoiceItems(
                        resources.getStringArray(com.hashteelabs.dodomap.R.array.instant_placement_options_array),
                        instantPlacementSettingsMenuDialogCheckboxes,
                        (DialogInterface dialog, int which, boolean isChecked) ->
                                instantPlacementSettingsMenuDialogCheckboxes[which] = isChecked)
                .setPositiveButton(com.hashteelabs.dodomap.R.string.done,
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
                    .setTitle(com.hashteelabs.dodomap.R.string.options_title_with_depth)
                    .setMultiChoiceItems(
                            resources.getStringArray(com.hashteelabs.dodomap.R.array.depth_options_array),
                            depthSettingsMenuDialogCheckboxes,
                            (DialogInterface dialog, int which, boolean isChecked) ->
                                    depthSettingsMenuDialogCheckboxes[which] = isChecked)
                    .setPositiveButton(com.hashteelabs.dodomap.R.string.done,
                            (DialogInterface dialogInterface, int which) -> applySettingsMenuDialogCheckboxes())
                    .setNegativeButton(android.R.string.cancel,
                            (DialogInterface dialog, int which) -> resetSettingsMenuDialogCheckboxes())
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(com.hashteelabs.dodomap.R.string.options_title_without_depth)
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
        if (session == null) return;

        // Prefer the highest-resolution back camera config to improve crop quality.
        try {
            CameraConfigFilter filter = new CameraConfigFilter(session)
                    .setFacingDirection(CameraConfig.FacingDirection.BACK);
            List<CameraConfig> configs = session.getSupportedCameraConfigs(filter);
            CameraConfig best = null;
            int bestPixels = 0;
            for (CameraConfig cfg : configs) {
                Size sz = cfg.getImageSize();
                int pixels = sz.getWidth() * sz.getHeight();
                if (pixels > bestPixels) {
                    bestPixels = pixels;
                    best = cfg;
                }
            }
            if (best != null) {
                session.setCameraConfig(best);
                Log.d(TAG, "Using camera config " + best.getImageSize().getWidth() + "x" + best.getImageSize().getHeight());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to set highest-res camera config, using default", e);
        }

        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);

        // ✅ Enable both horizontal and vertical plane detection
        config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);

        // ✅ CRITICAL: Enable Cloud Anchor support
        config.setCloudAnchorMode(Config.CloudAnchorMode.ENABLED);

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
                    btnCapture.setBackgroundColor(Color.parseColor("#FF9800"));

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
        private Paint redBorderPaint; // ← Add this field
        // Add this inside Custom2DGridView
        private Map<Integer, String> cellQualityStatusMap = new HashMap<>();
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
                this.fillPaint.setColor(Color.parseColor("#2A7FFF")); // blue initial
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
                this.fillPaint.setColor(visited ? Color.parseColor("#4CAF50") : Color.parseColor("#2A7FFF"));
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
        public void updateCellQuality(int cellIndex, String qualityStatus) {
            cellQualityStatusMap.put(cellIndex, qualityStatus);
            invalidate(); // Triggers onDraw() to update visuals
        }
        /**
         * Initialize paints for drawing
         */
        private void init() {
            borderPaint = new Paint();
            borderPaint.setColor(Color.DKGRAY);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);

            // ✅ Add this:
            redBorderPaint = new Paint();
            redBorderPaint.setColor(Color.RED);
            redBorderPaint.setStyle(Paint.Style.STROKE);
            redBorderPaint.setStrokeWidth(6f); // or 4f, as desired



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
                boolean visited = visitedState[i] || cellImagePaths.containsKey(i);
                if (cell.visited != visited) {
                    cell.visited = visited;
                    cell.fillPaint.setColor(cell.visited ? Color.parseColor("#4CAF50") : Color.parseColor("#2A7FFF"));
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
                int cellIdx = cell.row * GRID_COLS + cell.col;

                // ✅ Colors: Blue before capture, Green after capture
                if (cellImagePaths.containsKey(cellIdx)) {
                    cell.fillPaint.setColor(Color.parseColor("#4CAF50")); // Green = captured
                } else if (cell.visited) {
                    cell.fillPaint.setColor(Color.parseColor("#4CAF50")); // Treat visited as captured → green
                } else {
                    cell.fillPaint.setColor(Color.parseColor("#2A7FFF")); // Blue = not captured
                }

                // Draw filled background
                canvas.drawRoundRect(cell.rect, 10f, 10f, cell.fillPaint);

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

                // ✅ Draw camera icon for captured cells
                if (cellImagePaths.containsKey(cellIdx)) {
                    Paint capturePaint = new Paint();
                    capturePaint.setColor(Color.parseColor("#4CAF50")); // green icon
                    capturePaint.setStyle(Paint.Style.FILL);
                    float iconSize = cell.rect.width() / 5f;
                    float iconX = cell.rect.right - iconSize * 1.5f;
                    float iconY = cell.rect.top + iconSize * 1.5f;

                    RectF cameraBody = new RectF(
                            iconX - iconSize / 2,
                            iconY - iconSize / 3,
                            iconX + iconSize / 2,
                            iconY + iconSize / 3
                    );
                    canvas.drawRoundRect(cameraBody, 2f, 2f, capturePaint);
                    canvas.drawCircle(iconX, iconY, iconSize / 4, capturePaint);
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

    // ===== VIDEO RECORDING & UPLOAD =====

    private void show3DRenderDialog() {
        String[] options = {"Record Video", "Upload Video"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("3D Render")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        onRecordVideoClicked();
                    } else {
                        onUploadVideoClicked();
                    }
                })
                .show();
    }

    private void onRecordVideoClicked() {
        if (session == null) {
            Toast.makeText(this, "AR session not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isRecording) {
            stopArVideoRecording();
        } else {
            startArVideoRecording();
        }
    }

    private void startArVideoRecording() {
        try {
            File videoDir = new File(getExternalFilesDir(null), "AR_Videos");
            if (!videoDir.exists()) videoDir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            currentVideoFile = new File(videoDir, "ar_video_" + timestamp + ".mp4");

            com.google.ar.core.RecordingConfig recordingConfig =
                    new com.google.ar.core.RecordingConfig(session)
                            .setMp4DatasetUri(Uri.fromFile(currentVideoFile))
                            .setAutoStopOnPause(false);

            session.startRecording(recordingConfig);
            isRecording = true;

            runOnUiThread(() -> {
                btnRecordVideo.setVisibility(View.VISIBLE);
                btnRecordVideo.setText("STOP RECORDING");
                btnRecordVideo.setBackgroundColor(Color.parseColor("#F44336"));
                btn3DRender.setVisibility(View.GONE);
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to start AR recording", e);
            runOnUiThread(() -> Toast.makeText(this,
                    "Could not start recording: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void stopArVideoRecording() {
        try {
            session.stopRecording();
            isRecording = false;

            runOnUiThread(() -> {
                btnRecordVideo.setVisibility(View.GONE);
                btn3DRender.setVisibility(View.VISIBLE);

                if (currentVideoFile != null && currentVideoFile.exists()) {
                    new AlertDialog.Builder(this)
                            .setTitle("Recording Saved")
                            .setMessage("Video saved!\n\nGenerate 3D model from this recording now?")
                            .setPositiveButton("Generate 3D", (d, w) ->
                                    uploadVideoToServer(Uri.fromFile(currentVideoFile)))
                            .setNegativeButton("Later", null)
                            .show();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop AR recording", e);
            runOnUiThread(() -> Toast.makeText(this,
                    "Stop recording failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    private void onUploadVideoClicked() {
        // Check if a downloaded PLY already exists to offer "View last 3D"
        File plyDir = new File(getExternalFilesDir(null), "PLY_Models");
        File[] plyFiles = plyDir.exists() ? plyDir.listFiles((d, n) -> n.endsWith(".ply")) : null;
        boolean hasExisting3D = plyFiles != null && plyFiles.length > 0;

        List<String> options = new ArrayList<>();
        options.add("Generate 3D from recorded video");
        options.add("Generate 3D from gallery video");
        if (hasExisting3D) options.add("View last 3D model");

        new AlertDialog.Builder(this)
                .setTitle("3D Model Generation")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        // Upload recorded video
                        if (currentVideoFile != null && currentVideoFile.exists()) {
                            uploadVideoToServer(Uri.fromFile(currentVideoFile));
                        } else {
                            Toast.makeText(this,
                                    "No recorded video. Tap RECORD VIDEO first.",
                                    Toast.LENGTH_LONG).show();
                        }
                    } else if (which == 1) {
                        // Pick from gallery
                        pickVideoFromGallery();
                    } else {
                        // View last downloaded 3D model
                        File latest = plyFiles[0];
                        for (File f : plyFiles) {
                            if (f.lastModified() > latest.lastModified()) latest = f;
                        }
                        Intent intent = new Intent(this, PlyViewerActivity.class);
                        intent.putExtra(PlyViewerActivity.EXTRA_PLY_PATH, latest.getAbsolutePath());
                        startActivity(intent);
                    }
                })
                .show();
    }

    private void pickVideoFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        startActivityForResult(intent, REQUEST_PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            Uri videoUri = data.getData();
            if (videoUri != null) {
                uploadVideoToServer(videoUri);
            }
        }
    }

    // ===== PLY POLLING, DOWNLOAD & PARSING =====

    private void pollAndDownloadResult(String jobId) {
        new Thread(() -> {
            int maxAttempts = 180; // 15 min max (180 × 5s)
            runOnUiThread(() -> Toast.makeText(this,
                    "3D processing started. You'll be notified when ready.",
                    Toast.LENGTH_LONG).show());

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                try {
                    Thread.sleep(5000);
                    OkHttpClient client = new OkHttpClient();
                    Request req = new Request.Builder()
                            .url(VIDEO_STATUS_ENDPOINT + jobId)
                            .build();
                    try (Response resp = client.newCall(req).execute()) {
                        String body = resp.body() != null ? resp.body().string() : "{}";
                        String status = new JSONObject(body).optString("status", "");
                        Log.d(TAG, "3D job " + jobId + " status: " + status);

                        if (status.equals("done")) {
                            downloadAndRenderPly(jobId);
                            return;
                        } else if (status.startsWith("error")) {
                            runOnUiThread(() -> Toast.makeText(this,
                                    "3D generation failed: " + status, Toast.LENGTH_LONG).show());
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Poll error: " + e.getMessage());
                }
            }
            runOnUiThread(() -> Toast.makeText(this,
                    "3D processing timed out.", Toast.LENGTH_LONG).show());
        }).start();
    }

    private void downloadAndRenderPly(String jobId) {
        try {
            runOnUiThread(() -> Toast.makeText(this,
                    "3D model ready! Downloading...", Toast.LENGTH_SHORT).show());

            OkHttpClient client = new OkHttpClient.Builder()
                    .readTimeout(600, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build();
            Request req = new Request.Builder()
                    .url(VIDEO_DOWNLOAD_ENDPOINT + jobId)
                    .build();

            try (Response resp = client.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Download failed: " + resp.code(), Toast.LENGTH_SHORT).show());
                    return;
                }

                File plyDir = new File(getExternalFilesDir(null), "PLY_Models");
                plyDir.mkdirs();
                File plyFile = new File(plyDir, jobId + ".ply");

                runOnUiThread(() -> Toast.makeText(this,
                        "Downloading 3D model (may take a minute)...", Toast.LENGTH_LONG).show());

                try (InputStream is = resp.body().byteStream();
                     OutputStream os = new FileOutputStream(plyFile)) {
                    byte[] buf = new byte[65536];
                    int len;
                    while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                }
                Log.d(TAG, "PLY saved: " + plyFile.getAbsolutePath());

                // Open the 3D viewer in a separate screen
                runOnUiThread(() -> {
                    Toast.makeText(this, "Opening 3D viewer...", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(this, PlyViewerActivity.class);
                    intent.putExtra(PlyViewerActivity.EXTRA_PLY_PATH, plyFile.getAbsolutePath());
                    startActivity(intent);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "PLY download error", e);
            runOnUiThread(() -> Toast.makeText(this,
                    "Failed to download 3D: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void uploadVideoToServer(Uri videoUri) {
        new Thread(() -> {
            File tempFile = null;
            try {
                // Step 1: Check server is reachable first
                runOnUiThread(() -> Toast.makeText(this,
                        "Checking server connection...", Toast.LENGTH_SHORT).show());
                try {
                    OkHttpClient pingClient = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .build();
                    Request pingReq = new Request.Builder()
                            .url("https://vtdjepkjlodxix-8000.proxy.runpod.net/")
                            .build();
                    try (Response pingResp = pingClient.newCall(pingReq).execute()) {
                        if (!pingResp.isSuccessful()) throw new Exception("Server returned " + pingResp.code());
                    }
                } catch (Exception pingEx) {
                    runOnUiThread(() -> new AlertDialog.Builder(this)
                            .setTitle("Server Unreachable")
                            .setMessage(
                                "Cannot connect to the 3D server.\n\n" +
                                "Please check:\n" +
                                "• RunPod server is running\n" +
                                "• Run: uvicorn server:app --host 0.0.0.0 --port 8000\n\n" +
                                "Error: " + pingEx.getMessage())
                            .setPositiveButton("OK", null)
                            .show());
                    return;
                }

                // Step 2: Copy video to temp file
                runOnUiThread(() -> Toast.makeText(this,
                        "Preparing video...", Toast.LENGTH_SHORT).show());

                tempFile = File.createTempFile("upload_video_", ".mp4", getCacheDir());
                try (InputStream is = getContentResolver().openInputStream(videoUri);
                     OutputStream os = new FileOutputStream(tempFile)) {
                    if (is == null) throw new Exception("Cannot read video file. Try picking again.");
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                }

                long fileSizeMB = tempFile.length() / (1024 * 1024);
                Log.d(TAG, "Video temp file: " + fileSizeMB + " MB");

                // Step 3: Upload + wait for PLY (synchronous server — no polling needed)
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(600, TimeUnit.SECONDS)
                        .readTimeout(700, TimeUnit.SECONDS) // server timeout is 600s
                        .build();

                RequestBody body = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "video.mp4",
                                RequestBody.create(tempFile, MediaType.parse("video/mp4")))
                        .build();

                Request request = new Request.Builder()
                        .url(VIDEO_UPLOAD_ENDPOINT)
                        .post(body)
                        .build();

                runOnUiThread(() -> Toast.makeText(this,
                        "Uploading & processing 3D... (may take 5-10 min, keep app open)",
                        Toast.LENGTH_LONG).show());

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String err = response.body() != null ? response.body().string() : "";
                        runOnUiThread(() -> Toast.makeText(this,
                                "Upload failed: " + response.code() + " " + err,
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                    String respBody = response.body() != null ? response.body().string() : "{}";
                    String jobId = new JSONObject(respBody).optString("job_id", "");
                    if (jobId.isEmpty()) {
                        runOnUiThread(() -> Toast.makeText(this, "No job ID returned", Toast.LENGTH_LONG).show());
                        return;
                    }
                    runOnUiThread(() -> Toast.makeText(this,
                            "Video uploaded! Processing 3D... keep app open",
                            Toast.LENGTH_LONG).show());
                    // Poll status
                    OkHttpClient pollClient = new OkHttpClient.Builder()
                            .readTimeout(30, TimeUnit.SECONDS).build();
                    for (int i = 0; i < 180; i++) {
                        Thread.sleep(5000);
                        Request statusReq = new Request.Builder()
                                .url(VIDEO_STATUS_ENDPOINT + jobId).build();
                        try (Response sr = pollClient.newCall(statusReq).execute()) {
                            String s = new JSONObject(sr.body().string()).optString("status", "");
                            Log.d(TAG, "Status: " + s);
                            if (s.equals("done")) {
                                // Download PLY
                                runOnUiThread(() -> Toast.makeText(this, "3D ready! Downloading...", Toast.LENGTH_SHORT).show());
                                OkHttpClient dlClient = new OkHttpClient.Builder()
                                        .readTimeout(300, TimeUnit.SECONDS).build();
                                Request dlReq = new Request.Builder()
                                        .url(VIDEO_DOWNLOAD_ENDPOINT + jobId).build();
                                try (Response dr = dlClient.newCall(dlReq).execute()) {
                                    File plyDir = new File(getExternalFilesDir(null), "PLY_Models");
                                    plyDir.mkdirs();
                                    File plyFile = new File(plyDir, jobId + ".ply");
                                    try (InputStream is = dr.body().byteStream();
                                         OutputStream os = new FileOutputStream(plyFile)) {
                                        byte[] buf = new byte[65536];
                                        int len;
                                        while ((len = is.read(buf)) > 0) os.write(buf, 0, len);
                                    }
                                    if (plyFile.length() > 1000) {
                                        runOnUiThread(() -> {
                                            Toast.makeText(this, "Opening 3D viewer...", Toast.LENGTH_SHORT).show();
                                            Intent intent = new Intent(this, PlyViewerActivity.class);
                                            intent.putExtra(PlyViewerActivity.EXTRA_PLY_PATH, plyFile.getAbsolutePath());
                                            startActivity(intent);
                                        });
                                    }
                                }
                                return;
                            } else if (s.startsWith("error") || s.equals("failed")) {
                                runOnUiThread(() -> Toast.makeText(this, "3D failed: " + s, Toast.LENGTH_LONG).show());
                                return;
                            }
                        }
                    }
                    runOnUiThread(() -> Toast.makeText(this, "Timed out waiting for 3D", Toast.LENGTH_LONG).show());
                }

            } catch (Exception e) {
                Log.e(TAG, "Video upload failed", e);
                final String msg;
                if (e.getMessage() != null && e.getMessage().contains("Unable to resolve host")) {
                    msg = "Cannot reach server. Check your internet and make sure RunPod is running.";
                } else if (e.getMessage() != null && e.getMessage().contains("Cannot read video")) {
                    msg = e.getMessage();
                } else {
                    msg = "Upload failed: " + e.getMessage();
                }
                runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
            } finally {
                if (tempFile != null) tempFile.delete();
            }
        }).start();
    }
}

