package com.google.ar.core.examples.helloar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.Pose;
import java.util.ArrayList;
import java.util.List;

public class GridNavigationActivity extends AppCompatActivity {
    private static final String TAG = GridNavigationActivity.class.getSimpleName();
    private Pose[] boundaryPoses = new Pose[4];
    private GridView gridView;
    private Button btnBackToAR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grid_navigation);

        Log.d(TAG, "GridNavigationActivity onCreate: Starting...");

        // Initialize UI elements
        FrameLayout gridContainer = findViewById(R.id.gridContainer);
        btnBackToAR = findViewById(R.id.btnBackToAR);

        // Set up Back button
        btnBackToAR.setOnClickListener(v -> returnToARView());

        // Handle system back button/gesture using OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Return to AR view when back button/gesture is pressed
                Log.d(TAG, "Back pressed - returning to AR view");
                returnToARView();
            }
        });

        // Retrieve the corner data from the Intent
        Intent intent = getIntent();
        Log.d(TAG, "Intent received: " + (intent != null ? "not null" : "null"));

        float[] corner1 = intent.getFloatArrayExtra("corner1");
        float[] corner2 = intent.getFloatArrayExtra("corner2");
        float[] corner3 = intent.getFloatArrayExtra("corner3");
        float[] corner4 = intent.getFloatArrayExtra("corner4");

        Log.d("c1", "corner1: " + (corner1 != null ? "length=" + corner1.length : "null"));
        Log.d(TAG, "corner2: " + (corner2 != null ? "length=" + corner2.length : "null"));
        Log.d(TAG, "corner3: " + (corner3 != null ? "length=" + corner3.length : "null"));
        Log.d(TAG, "corner4: " + (corner4 != null ? "length=" + corner4.length : "null"));

        if (corner1 == null || corner2 == null || corner3 == null || corner4 == null ||
                corner1.length != 3 || corner2.length != 3 || corner3.length != 3 || corner4.length != 3) {

            Log.e(TAG, "Error: Missing or invalid anchor data");
            Toast.makeText(this, "Error: Missing or invalid anchor data. Using default visualization.", Toast.LENGTH_LONG).show();

            // Create dummy poses
            for (int i = 0; i < 4; i++) {
                boundaryPoses[i] = new Pose(new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 0f, 1f});
            }
        } else {
            // Convert the corner coordinates to Pose objects
            float[] identityRotation = new float[]{0f, 0f, 0f, 1f};

            boundaryPoses[0] = new Pose(corner1, identityRotation); // Top-Left
            boundaryPoses[1] = new Pose(corner2, identityRotation); // Top-Right
            boundaryPoses[2] = new Pose(corner3, identityRotation); // Bottom-Left
            boundaryPoses[3] = new Pose(corner4, identityRotation); // Bottom-Right

            Log.i(TAG, "Received 4 boundary corners successfully:");
            Log.i(TAG, "  TL: [" + corner1[0] + ", " + corner1[1] + ", " + corner1[2] + "]");
            Log.i(TAG, "  TR: [" + corner2[0] + ", " + corner2[1] + ", " + corner2[2] + "]");
            Log.i(TAG, "  BL: [" + corner3[0] + ", " + corner3[1] + ", " + corner3[2] + "]");
            Log.i(TAG, "  BR: [" + corner4[0] + ", " + corner4[1] + ", " + corner4[2] + "]");
        }

        // Initialize the Grid View and add it to the container
        gridView = new GridView(this, boundaryPoses);
        gridContainer.addView(gridView);

        Log.d(TAG, "GridNavigationActivity onCreate: Complete");
    }

    private void returnToARView() {
        // Get the visited cell data from the grid view
        ArrayList<GridCellData> cellDataList = gridView.getCellDataList();

        Intent resultIntent = new Intent();
        resultIntent.putParcelableArrayListExtra("visitedCells", cellDataList);
        setResult(RESULT_OK, resultIntent);

        int visitedCount = 0;
        for (GridCellData cell : cellDataList) {
            Log.d("celldata", "Cell " + cell.cellNumber + " visited: " + cell.visited);
            if (cell.visited) visitedCount++;
        }

        Log.d("return data", "Returning to AR view with " + visitedCount + " visited cells out of " + cellDataList.size() + " total cells");
        finish();
    }

    /**
     * GridCell class to represent each cell in the grid
     */
    private static class GridCell {
        public final int cellNumber;
        public final int row;
        public final int col;
        public boolean visited;
        public Paint fillPaint;
        public RectF rect;

        public GridCell(int cellNumber, int row, int col, RectF rect) {
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

        public void markVisited() {
            if (!visited) {
                this.visited = true;
                this.fillPaint.setColor(Color.parseColor("#4CAF50")); // Green
            } else {
                this.visited = false;
                this.fillPaint.setColor(Color.WHITE);
            }
        }
    }

    /**
     * Custom View to draw and manage the 2D grid
     */
    public static class GridView extends View {
        private final Pose[] boundaryPoses;
        private final List<GridCell> cells = new ArrayList<>();
        private final int GRID_ROWS = 7;
        private final int GRID_COLS = 7;
        private Paint borderPaint;
        private Paint textPaint;
        private float cellViewSize;

        public GridView(Context context, Pose[] poses) {
            super(context);
            this.boundaryPoses = poses;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            init();
        }

        private void init() {
            borderPaint = new Paint();
            borderPaint.setColor(Color.DKGRAY);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);

            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
        }

        // NEW: Method to get cell data for passing back to AR view
        public ArrayList<GridCellData> getCellDataList() {
            ArrayList<GridCellData> cellDataList = new ArrayList<>();
            for (GridCell cell : cells) {
                cellDataList.add(new GridCellData(cell.cellNumber, cell.row, cell.col, cell.visited));
            }
            return cellDataList;
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

            generate2DGrid(startX, startY, cellViewSize);
        }

        private void generate2DGrid(float startX, float startY, float cellSize) {
            cells.clear();
            int cellCount = 1;

            for (int row = 0; row < GRID_ROWS; row++) {
                for (int col = 0; col < GRID_COLS; col++) {
                    float left = startX + col * cellSize;
                    float top = startY + row * cellSize;
                    float right = left + cellSize;
                    float bottom = top + cellSize;

                    RectF rect = new RectF(left, top, right, bottom);
                    GridCell cell = new GridCell(cellCount, row, col, rect);
                    cells.add(cell);
                    cellCount++;
                }
            }
            Log.i(TAG, "2D Grid of " + GRID_ROWS * GRID_COLS + " cells generated.");
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (cells.isEmpty()) {
                canvas.drawText("Calculating Map Data...", getWidth() / 2f, getHeight() / 2f, textPaint);
                return;
            }

            for (GridCell cell : cells) {
                canvas.drawRoundRect(cell.rect, 10f, 10f, cell.fillPaint);
                canvas.drawRoundRect(cell.rect, 10f, 10f, borderPaint);

                String num = String.valueOf(cell.cellNumber);
                float x = cell.rect.centerX();
                float textHeight = textPaint.descent() - textPaint.ascent();
                float y = cell.rect.centerY() + (textHeight / 2) - textPaint.descent();

                canvas.drawText(num, x, y, textPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float touchX = event.getX();
                float touchY = event.getY();

                for (GridCell cell : cells) {
                    if (cell.rect.contains(touchX, touchY)) {
                        cell.markVisited();
                        invalidate();
                        Toast.makeText(getContext(), "Cell " + cell.cellNumber +
                                (cell.visited ? " Visited!" : " Unvisited!"), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
            }
            return super.onTouchEvent(event);
        }
    }
}