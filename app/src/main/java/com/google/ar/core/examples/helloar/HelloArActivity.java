package com.google.ar.core.examples.helloar;

import android.os.Bundle;
import android.view.MotionEvent;
import android.opengl.GLSurfaceView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.helloar.R;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.examples.helloar.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.helloar.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.helloar.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.helloar.common.helpers.TapHelper;
import com.google.ar.core.examples.helloar.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.helloar.common.samplerender.Mesh;
import com.google.ar.core.examples.helloar.common.samplerender.SampleRender;
import com.google.ar.core.examples.helloar.common.samplerender.Shader;
import com.google.ar.core.examples.helloar.common.samplerender.Texture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HelloArActivity extends AppCompatActivity implements SampleRender.Renderer {

  private GLSurfaceView surfaceView;
  private Session session;
  private SampleRender render;
  private DisplayRotationHelper displayRotationHelper;
  private TapHelper tapHelper;
  private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
  private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);

  private Mesh virtualObjectMesh;
  private Shader virtualObjectShader;
  private Texture virtualObjectAlbedoTexture;
  private final List<WrappedAnchor> wrappedAnchors = new ArrayList<>();

  private static final float MIN_DISTANCE_METERS = 0.5f;
  private static final float MAX_DISTANCE_METERS = 5.0f;
  private static final float Z_NEAR = 0.1f;
  private static final float Z_FAR = 100f;

  private final float[] modelMatrix = new float[16];
  private final float[] viewMatrix = new float[16];
  private final float[] projectionMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16];
  private final float[] modelViewProjectionMatrix = new float[16];

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    surfaceView = findViewById(R.id.surfaceview);
    displayRotationHelper = new DisplayRotationHelper(this);

    tapHelper = new TapHelper(this);
    surfaceView.setOnTouchListener(tapHelper);

    render = new SampleRender(surfaceView, this, getAssets());
  }

  @Override
  protected void onResume() {
    super.onResume();

    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      CameraPermissionHelper.requestCameraPermission(this);
      return;
    }

    resumeSession();
  }

  private void resumeSession() {
    if (session == null) {
      try {
        session = new Session(this);
        configureSession();
      } catch (UnavailableArcoreNotInstalledException e) {
        Toast.makeText(this, "ARCore not installed", Toast.LENGTH_LONG).show();
        finish();
        return;
      } catch (UnavailableDeviceNotCompatibleException e) {
        Toast.makeText(this, "Device not compatible with ARCore", Toast.LENGTH_LONG).show();
        finish();
        return;
      } catch (Exception e) {
        Toast.makeText(this, "Failed to create AR session", Toast.LENGTH_LONG).show();
        finish();
        return;
      }
    }

    try {
      session.resume();
      surfaceView.onResume();
      displayRotationHelper.onResume();
    } catch (CameraNotAvailableException e) {
      Toast.makeText(this, "Camera not available. Restart app.", Toast.LENGTH_LONG).show();
      session = null;
    }
  }

  private void configureSession() {
    Config config = new Config(session);
    config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
    session.configure(config);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(requestCode, permissions, results);
    if (!CameraPermissionHelper.hasCameraPermission(this)) {
      Toast.makeText(this, "Camera permission is needed to run this app", Toast.LENGTH_LONG).show();
      finish();
    } else {
      resumeSession();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (session != null) {
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void onSurfaceCreated(SampleRender render) {
    try {
      virtualObjectAlbedoTexture = Texture.createFromAsset(
              render,
              "models/pawn_albedo.png",
              Texture.WrapMode.CLAMP_TO_EDGE,
              Texture.ColorFormat.SRGB
      );
      virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
      virtualObjectShader = Shader.createFromAssets(
              render,
              "shaders/environmental_hdr.vert",
              "shaders/environmental_hdr.frag",
              new HashMap<String, String>());
    } catch (Exception e) {
      messageSnackbarHelper.showError(this, "Error loading assets.");
    }
  }

  @Override
  public void onSurfaceChanged(SampleRender render, int width, int height) {
    displayRotationHelper.onSurfaceChanged(width, height);
  }

  @Override
  public void onDrawFrame(SampleRender render) {
    if (session == null) return;

    displayRotationHelper.updateSessionIfNeeded(session);

    Frame frame;
    try {
      frame = session.update();
    } catch (CameraNotAvailableException e) {
      return;
    }

    Camera camera = frame.getCamera();
    handleTap(frame, camera);

    for (WrappedAnchor wrappedAnchor : wrappedAnchors) {
      Anchor anchor = wrappedAnchor.getAnchor();
      if (anchor.getTrackingState() != TrackingState.TRACKING) continue;

      anchor.getPose().toMatrix(modelMatrix, 0);
      camera.getViewMatrix(viewMatrix, 0);
      camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

      multiplyMM(modelViewMatrix, viewMatrix, modelMatrix);
      multiplyMM(modelViewProjectionMatrix, projectionMatrix, modelViewMatrix);

      virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
      virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
      virtualObjectShader.setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture);

      render.draw(virtualObjectMesh, virtualObjectShader);
    }
  }

  private void multiplyMM(float[] result, float[] a, float[] b) {
    android.opengl.Matrix.multiplyMM(result, 0, a, 0, b, 0);
  }

  private void handleTap(Frame frame, Camera camera) {
    MotionEvent tap = tapHelper.poll();
    if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
      for (HitResult hit : frame.hitTest(tap)) {
        Trackable trackable = hit.getTrackable();
        if ((trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hit.getHitPose()))
                || trackable instanceof Point) {

          float[] camPos = camera.getPose().getTranslation();
          float[] hitPos = hit.getHitPose().getTranslation();
          float distance = (float) Math.sqrt(
                  Math.pow(camPos[0] - hitPos[0], 2) +
                          Math.pow(camPos[1] - hitPos[1], 2) +
                          Math.pow(camPos[2] - hitPos[2], 2)
          );

          if (distance < MIN_DISTANCE_METERS) {
            Toast.makeText(this, "Object is too close!", Toast.LENGTH_SHORT).show();
            continue;
          } else if (distance > MAX_DISTANCE_METERS) {
            Toast.makeText(this, "Object is too far!", Toast.LENGTH_SHORT).show();
            continue;
          }

          if (wrappedAnchors.size() >= 20) {
            wrappedAnchors.get(0).getAnchor().detach();
            wrappedAnchors.remove(0);
          }

          wrappedAnchors.add(new WrappedAnchor(hit.createAnchor(), trackable));
          Toast.makeText(this, "Anchor placed at distance: " + distance, Toast.LENGTH_SHORT).show();
          break;
        }
      }
    }
  }

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
