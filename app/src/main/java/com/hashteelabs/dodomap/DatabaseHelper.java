
package com.hashteelabs.dodomap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DatabaseHelper";
    private static final String DATABASE_NAME = "inspection.db";
    private static final int DATABASE_VERSION = 2;

    // Tables
    private static final String TABLE_WORK_ORDERS = "work_orders";
    private static final String TABLE_CAPTURES = "captures";

    // Work Orders Table Columns
    private static final String COL_WO_ID = "work_order_id";
    private static final String COL_STEP_ID = "step_id";
    private static final String COL_STEP_NAME = "step_name";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_STATUS = "status";

    // Captures Table Columns
    private static final String COL_CAPTURE_ID = "id";
    private static final String COL_WO_FK = "work_order_id";
    private static final String COL_CELL_INDEX = "cell_index";
    private static final String COL_CELL_ROW = "cell_row";
    private static final String COL_CELL_COL = "cell_col";
    private static final String COL_IMAGE_PATH = "image_path";
    private static final String COL_QUALITY_STATUS = "quality_status";
    private static final String COL_CAPTURED_AT = "captured_at";
    private static final String COL_CROP_X = "crop_x";
    private static final String COL_CROP_Y = "crop_y";
    private static final String COL_CROP_WIDTH = "crop_width";
    private static final String COL_CROP_HEIGHT = "crop_height";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Work Orders Table
        String createWorkOrdersTable = "CREATE TABLE " + TABLE_WORK_ORDERS + " (" +
                COL_WO_ID + " TEXT PRIMARY KEY," +
                COL_STEP_ID + " TEXT," +
                COL_STEP_NAME + " TEXT," +
                COL_STATUS + " TEXT DEFAULT 'IN_PROGRESS'," +
                COL_CREATED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP)";

        // Create Captures Table
        String createCapturesTable = "CREATE TABLE " + TABLE_CAPTURES + " (" +
                COL_CAPTURE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_WO_FK + " TEXT," +
                COL_CELL_INDEX + " INTEGER," +
                COL_CELL_ROW + " INTEGER," +
                COL_CELL_COL + " INTEGER," +
                COL_IMAGE_PATH + " TEXT," +
                COL_QUALITY_STATUS + " TEXT," +
                COL_CROP_X + " INTEGER," +
                COL_CROP_Y + " INTEGER," +
                COL_CROP_WIDTH + " INTEGER," +
                COL_CROP_HEIGHT + " INTEGER," +
                COL_CAPTURED_AT + " DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY(" + COL_WO_FK + ") REFERENCES " + TABLE_WORK_ORDERS + "(" + COL_WO_ID + "))";

        db.execSQL(createWorkOrdersTable);
        db.execSQL(createCapturesTable);

        Log.d(TAG, "Database tables created");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CAPTURES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WORK_ORDERS);
        onCreate(db);
    }

    // Insert or update work order
    public void saveWorkOrder(String workOrderId, String stepId, String stepName) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_WO_ID, workOrderId);
        values.put(COL_STEP_ID, stepId);
        values.put(COL_STEP_NAME, stepName);

        long result = db.insertWithOnConflict(TABLE_WORK_ORDERS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE);

        if (result != -1) {
            Log.d(TAG, "Work order saved: " + workOrderId);
        }
    }

    // Save captured image data, including grid layout / crop coordinates
    public void saveCapturedImage(String workOrderId,
                                  int cellIndex,
                                  int cellRow,
                                  int cellCol,
                                  int cropX,
                                  int cropY,
                                  int cropWidth,
                                  int cropHeight,
                                  String imagePath,
                                  String qualityStatus) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_WO_FK, workOrderId);
        values.put(COL_CELL_INDEX, cellIndex);
        values.put(COL_CELL_ROW, cellRow);
        values.put(COL_CELL_COL, cellCol);
        values.put(COL_CROP_X, cropX);
        values.put(COL_CROP_Y, cropY);
        values.put(COL_CROP_WIDTH, cropWidth);
        values.put(COL_CROP_HEIGHT, cropHeight);
        values.put(COL_IMAGE_PATH, imagePath);
        values.put(COL_QUALITY_STATUS, qualityStatus);

        long result = db.insert(TABLE_CAPTURES, null, values);

        if (result != -1) {
            Log.d(TAG, "Capture saved for cell " + cellIndex + " in work order: " + workOrderId);
        }
    }

    // Get all captures for a work order
    public List<CaptureRecord> getCapturesForWorkOrder(String workOrderId) {
        List<CaptureRecord> captures = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_CAPTURES, null,
                COL_WO_FK + "=?",
                new String[]{workOrderId},
                null, null, null);

        if (cursor.moveToFirst()) {
            do {
                CaptureRecord record = new CaptureRecord();
                record.id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAPTURE_ID));
                record.cellIndex = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CELL_INDEX));
                record.cellRow = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CELL_ROW));
                record.cellCol = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CELL_COL));
                record.cropX = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CROP_X));
                record.cropY = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CROP_Y));
                record.cropWidth = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CROP_WIDTH));
                record.cropHeight = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CROP_HEIGHT));
                record.imagePath = cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_PATH));
                record.qualityStatus = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUALITY_STATUS));
                record.capturedAt = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAPTURED_AT));
                captures.add(record);
            } while (cursor.moveToNext());
        }

        cursor.close();
        return captures;
    }

    // Get capture count for work order
    public int getCaptureCount(String workOrderId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_CAPTURES + " WHERE " + COL_WO_FK + "=?",
                new String[]{workOrderId}
        );

        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    // Update work order status
    public void updateWorkOrderStatus(String workOrderId, String status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_STATUS, status);

        db.update(TABLE_WORK_ORDERS, values, COL_WO_ID + "=?", new String[]{workOrderId});
        Log.d(TAG, "Updated work order status for " + workOrderId + " to " + status);
    }

    // Update capture quality status
    public void updateCaptureQuality(String workOrderId, int cellIndex, String qualityStatus) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_QUALITY_STATUS, qualityStatus);

        int rowsAffected = db.update(TABLE_CAPTURES, values,
                COL_WO_FK + "=? AND " + COL_CELL_INDEX + "=?",
                new String[]{workOrderId, String.valueOf(cellIndex)});

        if (rowsAffected > 0) {
            Log.d(TAG, "Updated quality status for cell " + cellIndex + " in work order " + workOrderId + " to " + qualityStatus);
        } else {
            Log.w(TAG, "No rows updated for quality status. WO: " + workOrderId + ", Cell: " + cellIndex);
        }
    }

    // Data class for capture records
    public static class CaptureRecord {
        public int id;
        public int cellIndex;
        public int cellRow;
        public int cellCol;
        public int cropX;
        public int cropY;
        public int cropWidth;
        public int cropHeight;
        public String imagePath;
        public String qualityStatus;
        public String capturedAt;
    }
}