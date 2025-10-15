package com.google.ar.core.examples.helloar;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.Pose;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen 2: Non-AR Activity that visualizes the 2D floor plan grid based on the 4 boundary anchors
 * (or simulates a boundary for 2D viewing).
 * This activity handles grid creation, cell numbering, and visited color changes.
 */
public class GridNavigationActivity extends AppCompatActivity {
    private static final String TAG = GridNavigationActivity.class.getSimpleName();
    private Pose[] boundaryPoses = new Pose[4];
    private GridView gridView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "GridNavigationActivity onCreate: Starting...");

        // Use a simple LinearLayout for layout management
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));
        setContentView(mainLayout);

        // 1. Add a Title/Header TextView
        TextView title = new TextView(this);
        title.setText("2D Floor Plan Navigation");
        title.setTextColor(Color.DKGRAY);
        title.setTextSize(24);
        title.setPadding(30, 30, 30, 30);
        mainLayout.addView(title);

        // 2. Retrieve the corner data from the Intent
        Intent intent = getIntent();
        Log.d(TAG, "Intent received: " + (intent != null ? "not null" : "null"));

        float[] corner1 = intent.getFloatArrayExtra("corner1");
        float[] corner2 = intent.getFloatArrayExtra("corner2");
        float[] corner3 = intent.getFloatArrayExtra("corner3");
        float[] corner4 = intent.getFloatArrayExtra("corner4");

        Log.d(TAG, "corner1: " + (corner1 != null ? "length=" + corner1.length : "null"));
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

        // 3. Initialize the Grid View and add it to the layout
        gridView = new GridView(this, boundaryPoses);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        layoutParams.weight = 1;
        gridView.setLayoutParams(layoutParams);
        mainLayout.addView(gridView);

        Log.d(TAG, "GridNavigationActivity onCreate: Complete");
    }

    private static class GridCell {
        public final int cellNumber;
        public final int row;
        public final int col;
        public boolean visited;
        public Paint fillPaint; // Paint for the cell background
        public RectF rect; // 2D drawing boundaries for the canvas

        public GridCell(int cellNumber, int row, int col, RectF rect) {
            this.cellNumber = cellNumber;
            this.row = row;
            this.col = col;
            this.rect = rect;
            this.visited = false;
            this.fillPaint = new Paint();
            this.fillPaint.setColor(Color.WHITE); // Initial color: White (unvisited)
            this.fillPaint.setStyle(Paint.Style.FILL);
            this.fillPaint.setShadowLayer(5f, 0f, 0f, Color.GRAY); // Adding a subtle shadow
        }

        public void markVisited() {
            if (!visited) {
                this.visited = true;
                this.fillPaint.setColor(Color.parseColor("#4CAF50")); // Visited color: Green
            } else {
                // Allow unvisiting for demonstration
                this.visited = false;
                this.fillPaint.setColor(Color.WHITE);
            }
        }
    }

    /**
     * Custom View to draw and manage the 2D grid.
     */
    private static class GridView extends View {
        private final Pose[] boundaryPoses;
        private final List<GridCell> cells = new ArrayList<>();
        private final int GRID_ROWS = 7; // Increased grid size for better visualization
        private final int GRID_COLS = 7;
        private Paint borderPaint;
        private Paint textPaint;
        private float cellViewSize; // Size of a cell in screen pixels

        public GridView(Context context, Pose[] poses) {
            super(context);
            this.boundaryPoses = poses;
            // Enable hardware acceleration for drawing shadow layers
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            init();
        }

        private void init() {
            // Setup border paint (for drawing cell boundaries)
            borderPaint = new Paint();
            borderPaint.setColor(Color.DKGRAY);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);

            // Setup text paint (for drawing cell numbers)
            textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w == 0 || h == 0) return;

            // Determine the maximum size for the grid square within the view
            int padding = 40;
            // The grid is squared, based on the minimum dimension
            int gridSize = Math.min(w, h) - 2 * padding;

            // Calculate starting position to center the grid
            float startX = (w - gridSize) / 2f;
            float startY = (h - gridSize) / 2f;

            // Calculate the size of each cell in view pixels
            cellViewSize = (float) gridSize / GRID_COLS;

            // Adjust text size based on cell size
            textPaint.setTextSize(cellViewSize / 4);

            // Re-generate the 2D grid based on the new view size
            generate2DGrid(startX, startY, cellViewSize);
        }

        private void generate2DGrid(float startX, float startY, float cellSize) {
            cells.clear();
            int cellCount = 1;

            // Note: For a true AR mapping, this section would involve complex 3D-to-2D projection
            // using the boundaryPoses. Here, we create a simple, visually appealing grid
            // that is constrained by the view size.

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

            // Draw each cell
            for (GridCell cell : cells) {
                // 1. Draw the fill color (White or Green)
                canvas.drawRoundRect(cell.rect, 10f, 10f, cell.fillPaint); // Rounded corners

                // 2. Draw the border
                canvas.drawRoundRect(cell.rect, 10f, 10f, borderPaint);

                // 3. Draw the cell number (centered)
                String num = String.valueOf(cell.cellNumber);
                float x = cell.rect.centerX();

                // Calculate vertical text center
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

                // Check which cell was tapped
                for (GridCell cell : cells) {
                    if (cell.rect.contains(touchX, touchY)) {
                        cell.markVisited();
                        // Request redraw to update the cell color
                        invalidate();
                        Toast.makeText(getContext(), "Cell " + cell.cellNumber + (cell.visited ? " Visited!" : " Unvisited!"), Toast.LENGTH_SHORT).show();
                        return true; // Event consumed
                    }
                }
            }
            return super.onTouchEvent(event);
        }
    }
}
