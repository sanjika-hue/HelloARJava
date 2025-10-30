package com.google.ar.core.examples.helloar.managers;

import android.opengl.GLSurfaceView;
import android.util.Log;
import com.google.ar.core.examples.helloar.common.samplerender.Mesh;
import com.google.ar.core.examples.helloar.common.samplerender.SampleRender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe mesh manager that handles creation, rendering, and disposal of meshes
 */
public class MeshManager {
    private static final String TAG = "MeshManager";

    // Use CopyOnWriteArrayList for thread-safe iteration without explicit synchronization
    private final CopyOnWriteArrayList<MeshHolder> activeMeshes = new CopyOnWriteArrayList<>();
    private final List<MeshHolder> meshesToDispose = new ArrayList<>();
    private final GLSurfaceView surfaceView;

    public MeshManager(GLSurfaceView surfaceView) {
        this.surfaceView = surfaceView;
    }

    /**
     * Wrapper class that tracks mesh state
     */
    private static class MeshHolder {
        final Mesh mesh;
        final AtomicBoolean isValid = new AtomicBoolean(true);

        MeshHolder(Mesh mesh) {
            this.mesh = mesh;
        }

        void invalidate() {
            isValid.set(false);
        }

        boolean isValid() {
            return isValid.get();
        }
    }

    /**
     * Add a mesh to be managed
     */
    public void addMesh(Mesh mesh) {
        if (mesh != null) {
            activeMeshes.add(new MeshHolder(mesh));
        }
    }

    /**
     * Replace all current meshes with new ones (for visited cells)
     */
    public void replaceMeshes(List<Mesh> newMeshes) {
        // Mark old meshes as invalid
        for (MeshHolder holder : activeMeshes) {
            holder.invalidate();
            synchronized (meshesToDispose) {
                meshesToDispose.add(holder);
            }
        }

        // Clear the active list
        activeMeshes.clear();

        // Add new meshes
        for (Mesh mesh : newMeshes) {
            if (mesh != null) {
                activeMeshes.add(new MeshHolder(mesh));
            }
        }

        // Schedule disposal of old meshes on GL thread
        surfaceView.queueEvent(this::disposeInvalidMeshes);

        Log.d(TAG, "Replaced meshes. Active: " + activeMeshes.size() + ", Pending disposal: " + meshesToDispose.size());
    }

    /**
     * Clear all meshes
     */
    public void clearAll() {
        // Mark all as invalid
        for (MeshHolder holder : activeMeshes) {
            holder.invalidate();
            synchronized (meshesToDispose) {
                meshesToDispose.add(holder);
            }
        }
        activeMeshes.clear();

        // Schedule disposal
        surfaceView.queueEvent(this::disposeInvalidMeshes);
    }


    /**
     * Draw all valid meshes (safe to call from render thread)
     */
    public void drawAll(SampleRender render,
                        com.google.ar.core.examples.helloar.common.samplerender.Shader shader) {
        for (MeshHolder holder : activeMeshes) {
            if (holder.isValid()) {
                try {
                    render.draw(holder.mesh, shader);
                } catch (IllegalStateException e) {
                    // Mesh was freed between validity check and draw
                    Log.w(TAG, "Mesh became invalid during draw, skipping");
                    holder.invalidate();
                }
            }
        }
    }

    /**
     * Must be called on GL thread to dispose of meshes
     */
    private void disposeInvalidMeshes() {
        List<MeshHolder> toDispose;
        synchronized (meshesToDispose) {
            toDispose = new ArrayList<>(meshesToDispose);
            meshesToDispose.clear();
        }

        for (MeshHolder holder : toDispose) {
            try {
                if (holder.mesh != null) {
                    holder.mesh.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error disposing mesh: " + e.getMessage());
            }
        }

        Log.d(TAG, "Disposed " + toDispose.size() + " meshes");
    }

    /**
     * Get count of active meshes
     */
    public int getActiveCount() {
        return activeMeshes.size();
    }

    /**
     * Check if empty
     */
    public boolean isEmpty() {
        return activeMeshes.isEmpty();
    }

    /**
     * Cleanup - must be called on GL thread
     */
    public void cleanup() {
        for (MeshHolder holder : activeMeshes) {
            try {
                if (holder.mesh != null) {
                    holder.mesh.close();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error during cleanup: " + e.getMessage());
            }
        }
        activeMeshes.clear();

        synchronized (meshesToDispose) {
            meshesToDispose.clear();
        }
    }
}