package com.google.ar.core.examples.helloar;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import de.javagl.obj.Obj;
import de.javagl.obj.ObjFace;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;

public class SimpleRenderer implements GLSurfaceView.Renderer {

    private final Context context;
    private final Session session;

    // Anchors
    private final List<Anchor> gridAnchors = new ArrayList<>();

    // OBJ model and buffers
    private Obj model;
    private FloatBuffer vertexBuffer;
    private ShortBuffer indexBuffer;
    private int indexCount;

    // Shader program
    private int program;
    private int aPositionLoc;
    private int uMVPMatrixLoc;

    public SimpleRenderer(Context context, Session session,List<Pose> gridPoses) {
        this.context = context;
        this.session = session;
    }

    public void addAnchor(Anchor anchor) {
        gridAnchors.add(anchor);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        // 1. Load OBJ
        try {
            InputStream objStream = context.getAssets().open("models/pawn.obj");
            model = ObjReader.read(objStream);
            ObjUtils.convertToRenderable(model);
            prepareObjBuffers();
            Log.d("SimpleRenderer", "OBJ loaded and buffers prepared");
        } catch (IOException e) {
            Log.e("SimpleRenderer", "Failed to load OBJ", e);
        }

        // 2. Load shaders from assets
        String vertexShaderCode = loadShaderFromAsset("vertex_shader.glsl");
        String fragmentShaderCode = loadShaderFromAsset("fragment_shader.glsl");

        int vertexShader = ShaderUtils.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = ShaderUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        aPositionLoc = GLES20.glGetAttribLocation(program, "a_Position");
        uMVPMatrixLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix");

        Log.d("SimpleRenderer", "Shaders compiled and program linked");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (session == null) return;

        try {
            Frame frame = session.update();
            Camera camera = frame.getCamera();

            float[] viewMatrix = new float[16];
            float[] projectionMatrix = new float[16];
            camera.getViewMatrix(viewMatrix, 0);
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);

            synchronized (this) {
                for (Anchor anchor : gridAnchors) {
                    if (anchor.getTrackingState() != TrackingState.TRACKING) continue;

                    float[] modelMatrix = new float[16];
                    anchor.getPose().toMatrix(modelMatrix, 0);

                    float[] tempMatrix = new float[16];
                    float[] mvpMatrix = new float[16];
                    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
                    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);

                    drawObjModel(mvpMatrix);
                }
            }

        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
            Log.e("SimpleRenderer", "Camera not available");
        }
    }

    private void drawObjModel(float[] mvpMatrix) {
        GLES20.glUseProgram(program);

        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0);

        GLES20.glEnableVertexAttribArray(aPositionLoc);
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        indexBuffer.position(0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        GLES20.glDisableVertexAttribArray(aPositionLoc);
    }

    private void prepareObjBuffers() {
        // Flatten vertices
        float[] vertices = new float[model.getNumVertices() * 3];
        for (int i = 0; i < model.getNumVertices(); i++) {
            de.javagl.obj.FloatTuple pos = model.getVertex(i);
            vertices[i * 3] = pos.getX();
            vertices[i * 3 + 1] = pos.getY();
            vertices[i * 3 + 2] = pos.getZ();
        }

        // Flatten indices
        int numFaces = model.getNumFaces();
        short[] indices = new short[numFaces * 3];
        for (int i = 0; i < numFaces; i++) {
            ObjFace face = model.getFace(i);
            indices[i * 3] = (short) face.getVertexIndex(0);
            indices[i * 3 + 1] = (short) face.getVertexIndex(1);
            indices[i * 3 + 2] = (short) face.getVertexIndex(2);
        }

        // Convert to buffers
        ByteBuffer vb = ByteBuffer.allocateDirect(vertices.length * 4);
        vb.order(ByteOrder.nativeOrder());
        vertexBuffer = vb.asFloatBuffer();
        vertexBuffer.put(vertices);
        vertexBuffer.position(0);

        ByteBuffer ib = ByteBuffer.allocateDirect(indices.length * 2);
        ib.order(ByteOrder.nativeOrder());
        indexBuffer = ib.asShortBuffer();
        indexBuffer.put(indices);
        indexBuffer.position(0);

        indexCount = indices.length;
    }

    private String loadShaderFromAsset(String filename) {
        try {
            InputStream is = context.getAssets().open("models/" + filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");
        } catch (IOException e) {
            Log.e("SimpleRenderer", "Failed to load shader: " + filename, e);
            return "";
        }
    }
}
