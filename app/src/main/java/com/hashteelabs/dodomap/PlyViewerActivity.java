package com.hashteelabs.dodomap;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PlyViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PLY_PATH = "ply_path";
    private static final String TAG = "PlyViewer";

    // ── Measurement state ────────────────────────────────────────
    private enum MeasureState { IDLE, WAITING_A, WAITING_B, WAITING_C }
    private MeasureState measureState = MeasureState.IDLE;
    private boolean angleModeActive = false;

    private float[] pointA = null;
    private float[] pointB = null;
    private float[] pointC = null;

    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
    private float[] rawVerts = null;

    // ── Views ────────────────────────────────────────────────────
    private GLSurfaceView glSurfaceView;
    private PlyRenderer   renderer;
    private TextView      tvPointCount;
    private TextView      tvDistance;
    private TextView      tvMeasureHint;
    private Button        btnMeasure;
    private Button        btnAngle;
    private ProgressBar   progressBar;

    private float lastTouchX, lastTouchY;
    private boolean wasDragging = false;
    private ScaleGestureDetector scaleDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ply_viewer);

        String plyPath = getIntent().getStringExtra(EXTRA_PLY_PATH);

        tvPointCount  = findViewById(R.id.tvPointCount);
        tvDistance    = findViewById(R.id.tvDistance);
        tvMeasureHint = findViewById(R.id.tvMeasureHint);
        btnMeasure    = findViewById(R.id.btnMeasure);
        btnAngle      = findViewById(R.id.btnAngle);
        progressBar   = findViewById(R.id.progressBar);

        glSurfaceView = findViewById(R.id.glSurfaceView);
        glSurfaceView.setEGLContextClientVersion(2);
        renderer = new PlyRenderer();
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // ── Pinch-to-zoom ────────────────────────────────────────
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        renderer.zoom /= d.getScaleFactor();
                        renderer.zoom  = Math.max(0.3f, Math.min(renderer.zoom, 40f));
                        return true;
                    }
                });

        // ── Touch handler ────────────────────────────────────────
        glSurfaceView.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            if (event.getPointerCount() == 1) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastTouchX  = event.getX();
                        lastTouchY  = event.getY();
                        wasDragging = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getX() - lastTouchX;
                        float dy = event.getY() - lastTouchY;
                        if (Math.abs(dx) > 8 || Math.abs(dy) > 8) wasDragging = true;
                        renderer.rotY += dx * 0.5f;
                        renderer.rotX += dy * 0.5f;
                        lastTouchX = event.getX();
                        lastTouchY = event.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!wasDragging && measureState != MeasureState.IDLE) {
                            handleTap(event.getX(), event.getY(),
                                      glSurfaceView.getWidth(),
                                      glSurfaceView.getHeight());
                        }
                        break;
                }
            }
            return true;
        });

        // ── Buttons ──────────────────────────────────────────────
        btnMeasure.setOnClickListener(v -> {
            if (measureState == MeasureState.IDLE) startDistanceMeasure();
            else cancelMeasurement();
        });

        btnAngle.setOnClickListener(v -> {
            if (measureState == MeasureState.IDLE) startAngleMeasure();
            else cancelMeasurement();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // ── Load PLY ─────────────────────────────────────────────
        if (plyPath != null) {
            new Thread(() -> {
                float[] verts = parsePly(plyPath);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (verts != null) {
                        rawVerts = verts;
                        renderer.setVertices(verts);
                        tvPointCount.setText(String.format("%,d pts", verts.length / 6));
                    } else {
                        tvPointCount.setText("Error");
                        Toast.makeText(this, "Could not load 3D model", Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // MEASUREMENT CONTROL
    // ─────────────────────────────────────────────────────────────

    private void startDistanceMeasure() {
        angleModeActive = false;
        measureState    = MeasureState.WAITING_A;
        pointA = null; pointB = null; pointC = null;
        renderer.setPickedPoints(null, null, null);
        btnMeasure.setText("Cancel");
        btnAngle.setEnabled(false);
        tvDistance.setVisibility(View.GONE);
        tvMeasureHint.setVisibility(View.VISIBLE);
        tvMeasureHint.setText("Tap Point A on the model");
    }

    private void startAngleMeasure() {
        angleModeActive = true;
        measureState    = MeasureState.WAITING_A;
        pointA = null; pointB = null; pointC = null;
        renderer.setPickedPoints(null, null, null);
        btnAngle.setText("Cancel");
        btnMeasure.setEnabled(false);
        tvDistance.setVisibility(View.GONE);
        tvMeasureHint.setVisibility(View.VISIBLE);
        tvMeasureHint.setText("Tap Point A — the corner (vertex)");
    }

    private void cancelMeasurement() {
        measureState    = MeasureState.IDLE;
        angleModeActive = false;
        pointA = null; pointB = null; pointC = null;
        renderer.setPickedPoints(null, null, null);
        btnMeasure.setText("Measure");
        btnMeasure.setEnabled(true);
        btnAngle.setText("Angle");
        btnAngle.setEnabled(true);
        tvDistance.setVisibility(View.GONE);
        tvMeasureHint.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────
    // TAP HANDLER
    // ─────────────────────────────────────────────────────────────

    private void handleTap(float sx, float sy, int vpW, int vpH) {
        if (rawVerts == null) return;
        float[] mvp = renderer.getMvpSnapshot();
        if (mvp == null) return;

        float[] near = unproject(sx, sy, 0f, mvp, vpW, vpH);
        float[] far  = unproject(sx, sy, 1f, mvp, vpW, vpH);
        if (near == null || far == null) return;

        float[] rayOrig = { near[0], near[1], near[2] };
        float[] rayDir  = normalize(far[0]-near[0], far[1]-near[1], far[2]-near[2]);

        final float[]      vertsSnap = rawVerts;
        final MeasureState phase     = measureState;
        final boolean      isAngle   = angleModeActive;

        searchExecutor.submit(() -> {
            float[] hit = findNearestPoint(vertsSnap, rayOrig, rayDir);
            if (hit == null) return;

            runOnUiThread(() -> {
                switch (phase) {
                    case WAITING_A:
                        pointA = hit;
                        measureState = MeasureState.WAITING_B;
                        renderer.setPickedPoints(pointA, null, null);
                        tvMeasureHint.setText(isAngle
                                ? "Tap Point B — first wall"
                                : "Tap Point B on the model");
                        break;

                    case WAITING_B:
                        pointB = hit;
                        if (isAngle) {
                            measureState = MeasureState.WAITING_C;
                            renderer.setPickedPoints(pointA, pointB, null);
                            tvMeasureHint.setText("Tap Point C — second wall");
                        } else {
                            measureState = MeasureState.IDLE;
                            renderer.setPickedPoints(pointA, pointB, null);
                            btnMeasure.setText("Measure");
                            btnAngle.setEnabled(true);
                            tvMeasureHint.setVisibility(View.GONE);
                            showDistance();
                        }
                        break;

                    case WAITING_C:
                        pointC = hit;
                        measureState = MeasureState.IDLE;
                        renderer.setPickedPoints(pointA, pointB, pointC);
                        btnAngle.setText("Angle");
                        btnMeasure.setEnabled(true);
                        tvMeasureHint.setVisibility(View.GONE);
                        showAngle();
                        break;
                }
            });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // RESULTS
    // ─────────────────────────────────────────────────────────────

    private void showDistance() {
        if (pointA == null || pointB == null) return;
        double dx = pointB[0]-pointA[0], dy = pointB[1]-pointA[1], dz = pointB[2]-pointA[2];
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        tvDistance.setText(String.format("Distance: %.3f m  (%.1f cm)", dist, dist*100));
        tvDistance.setVisibility(View.VISIBLE);
    }

    private void showAngle() {
        if (pointA==null||pointB==null||pointC==null) return;
        float abx=pointB[0]-pointA[0], aby=pointB[1]-pointA[1], abz=pointB[2]-pointA[2];
        float acx=pointC[0]-pointA[0], acy=pointC[1]-pointA[1], acz=pointC[2]-pointA[2];
        double dot=abx*acx+aby*acy+abz*acz;
        double magAB=Math.sqrt(abx*abx+aby*aby+abz*abz);
        double magAC=Math.sqrt(acx*acx+acy*acy+acz*acz);
        if (magAB<1e-9||magAC<1e-9) {
            tvDistance.setText("Points too close — try again");
            tvDistance.setVisibility(View.VISIBLE);
            return;
        }
        double cosTheta=Math.max(-1.0,Math.min(1.0,dot/(magAB*magAC)));
        double angleDeg=Math.toDegrees(Math.acos(cosTheta));
        tvDistance.setText(String.format("Angle at A: %.2f°", angleDeg));
        tvDistance.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────
    // RAY CASTING
    // ─────────────────────────────────────────────────────────────

    private float[] unproject(float sx,float sy,float depth,float[] mvp,int vpW,int vpH) {
        float[] inv=new float[16];
        if (!Matrix.invertM(inv,0,mvp,0)) return null;
        float ndcX=(sx/vpW)*2f-1f;
        float ndcY=-((sy/vpH)*2f-1f);
        float ndcZ=depth*2f-1f;
        float[] clip={ndcX,ndcY,ndcZ,1f}, world=new float[4];
        Matrix.multiplyMV(world,0,inv,0,clip,0);
        if (world[3]==0f) return null;
        return new float[]{world[0]/world[3],world[1]/world[3],world[2]/world[3]};
    }

    private float[] normalize(float x,float y,float z) {
        float len=(float)Math.sqrt(x*x+y*y+z*z);
        if (len<1e-9f) return new float[]{0,0,1};
        return new float[]{x/len,y/len,z/len};
    }

    // ─────────────────────────────────────────────────────────────
    // NEAREST-POINT SEARCH  (background thread)
    // ─────────────────────────────────────────────────────────────

    private float[] findNearestPoint(float[] verts,float[] ro,float[] rd) {
        float bestDist=Float.MAX_VALUE;
        int bestIdx=-1, n=verts.length/6;
        for (int i=0;i<n;i++) {
            float px=verts[i*6],py=verts[i*6+1],pz=verts[i*6+2];
            float wx=px-ro[0],wy=py-ro[1],wz=pz-ro[2];
            float t=wx*rd[0]+wy*rd[1]+wz*rd[2];
            if (t<0) continue;
            float cx=wy*rd[2]-wz*rd[1], cy=wz*rd[0]-wx*rd[2], cz=wx*rd[1]-wy*rd[0];
            float d2=cx*cx+cy*cy+cz*cz;
            if (d2<bestDist){bestDist=d2;bestIdx=i;}
        }
        if (bestIdx<0) return null;
        return new float[]{verts[bestIdx*6],verts[bestIdx*6+1],verts[bestIdx*6+2]};
    }

    @Override protected void onResume()  { super.onResume();  glSurfaceView.onResume();  }
    @Override protected void onPause()   { super.onPause();   glSurfaceView.onPause();   }
    @Override protected void onDestroy() { super.onDestroy(); searchExecutor.shutdownNow(); }

    // ─────────────────────────────────────────────────────────────
    // PLY PARSER
    // ─────────────────────────────────────────────────────────────

    private float[] parsePly(String path) {
        try {
            RandomAccessFile raf=new RandomAccessFile(path,"r");
            int vertexCount=0; boolean isBinaryLE=false;
            List<String[]> propDefs=new ArrayList<>(); long headerEnd=0;
            while (true) {
                String line=raf.readLine(); if (line==null) break; line=line.trim();
                if (line.startsWith("format binary_little_endian")) isBinaryLE=true;
                if (line.startsWith("element vertex")) vertexCount=Integer.parseInt(line.split("\\s+")[2]);
                if (line.startsWith("property")) propDefs.add(line.split("\\s+"));
                if (line.equals("end_header")){headerEnd=raf.getFilePointer();break;}
            }
            raf.close(); if (vertexCount==0) return null;

            int bpv=0; int[] offs=new int[propDefs.size()]; String[] nm=new String[propDefs.size()];
            for (int i=0;i<propDefs.size();i++){offs[i]=bpv;nm[i]=propDefs.get(i)[2];bpv+=propSize(propDefs.get(i)[1]);}
            int xOff=-1,yOff=-1,zOff=-1,rOff=-1,gOff=-1,bOff=-1;
            boolean rIsFloat=false,gIsFloat=false,bIsFloat=false;
            for (int i=0;i<nm.length;i++) {
                switch(nm[i]){
                    case "x":xOff=offs[i];break; case "y":yOff=offs[i];break; case "z":zOff=offs[i];break;
                    case "red":  rOff=offs[i];rIsFloat=propDefs.get(i)[1].equals("float");break;
                    case "green":gOff=offs[i];gIsFloat=propDefs.get(i)[1].equals("float");break;
                    case "blue": bOff=offs[i];bIsFloat=propDefs.get(i)[1].equals("float");break;
                }
            }
            if (xOff<0||yOff<0||zOff<0) return null;
            boolean hasColor=(rOff>=0&&gOff>=0&&bOff>=0);
            int step=Math.max(1,vertexCount/1_000_000);
            int sampledCount=(vertexCount+step-1)/step;
            float[] result=new float[sampledCount*6];

            if (isBinaryLE) {
                byte[] raw=new byte[vertexCount*bpv];
                try (InputStream fis=new FileInputStream(path)){
                    fis.skip(headerEnd); int done=0;
                    while(done<raw.length){int r=fis.read(raw,done,raw.length-done);if(r<0)break;done+=r;}
                }
                ByteBuffer bb=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                double sx=0,sy=0,sz=0;
                for(int i=0;i<vertexCount;i++){sx+=bb.getFloat(i*bpv+xOff);sy+=bb.getFloat(i*bpv+yOff);sz+=bb.getFloat(i*bpv+zOff);}
                float cx=(float)(sx/vertexCount),cy=(float)(sy/vertexCount),cz=(float)(sz/vertexCount);
                int out=0;
                for(int i=0;i<vertexCount;i+=step){
                    int base=i*bpv;
                    result[out*6]=bb.getFloat(base+xOff)-cx; result[out*6+1]=bb.getFloat(base+yOff)-cy; result[out*6+2]=bb.getFloat(base+zOff)-cz;
                    if(hasColor){
                        if(rIsFloat){result[out*6+3]=bb.getFloat(base+rOff);result[out*6+4]=bb.getFloat(base+gOff);result[out*6+5]=bb.getFloat(base+bOff);}
                        else{result[out*6+3]=(raw[base+rOff]&0xFF)/255f;result[out*6+4]=(raw[base+gOff]&0xFF)/255f;result[out*6+5]=(raw[base+bOff]&0xFF)/255f;}
                    } else {result[out*6+3]=1f;result[out*6+4]=1f;result[out*6+5]=1f;}
                    out++;
                }
                result=java.util.Arrays.copyOf(result,out*6);
            } else {
                BufferedReader reader=new BufferedReader(new FileReader(path));
                String line; boolean past=false; List<String[]> rows=new ArrayList<>();
                while((line=reader.readLine())!=null){
                    if(past){rows.add(line.trim().split("\\s+"));if(rows.size()>=vertexCount)break;}
                    if(line.trim().equals("end_header"))past=true;
                }
                reader.close();
                double sx=0,sy=0,sz=0;
                for(String[] r:rows){sx+=Float.parseFloat(r[0]);sy+=Float.parseFloat(r[1]);sz+=Float.parseFloat(r[2]);}
                float cx=(float)(sx/rows.size()),cy=(float)(sy/rows.size()),cz=(float)(sz/rows.size());
                for(int i=0;i<rows.size();i++){
                    String[] r=rows.get(i);
                    result[i*6]=Float.parseFloat(r[0])-cx; result[i*6+1]=Float.parseFloat(r[1])-cy; result[i*6+2]=Float.parseFloat(r[2])-cz;
                    if(hasColor&&r.length>5){result[i*6+3]=Float.parseFloat(r[3])/255f;result[i*6+4]=Float.parseFloat(r[4])/255f;result[i*6+5]=Float.parseFloat(r[5])/255f;}
                    else{result[i*6+3]=1f;result[i*6+4]=1f;result[i*6+5]=1f;}
                }
            }
            return result;
        } catch(Exception e){Log.e(TAG,"PLY parse error",e);return null;}
    }

    private int propSize(String type) {
        switch(type){
            case "double":return 8; case "float":case "int":case "uint":return 4;
            case "short":case "ushort":return 2; default:return 1;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // OPENGL RENDERER
    // ─────────────────────────────────────────────────────────────

    private static class PlyRenderer implements GLSurfaceView.Renderer {

        private static final int STRIDE=24;

        private static final String VERT=
            "uniform mat4 u_MVP;\nattribute vec3 a_Position;\nattribute vec3 a_Color;\nvarying vec3 v_Color;\n"+
            "void main(){gl_Position=u_MVP*vec4(a_Position,1.0);gl_PointSize=3.0;v_Color=a_Color;}\n";
        private static final String FRAG=
            "precision mediump float;\nvarying vec3 v_Color;\nvoid main(){gl_FragColor=vec4(v_Color,1.0);}\n";
        private static final String MARKER_VERT=
            "uniform mat4 u_MVP;\nattribute vec3 a_Position;\n"+
            "void main(){gl_Position=u_MVP*vec4(a_Position,1.0);gl_PointSize=20.0;}\n";
        private static final String MARKER_FRAG=
            "precision mediump float;\nuniform vec4 u_Color;\nvoid main(){gl_FragColor=u_Color;}\n";

        private int program=0,markerProgram=0;
        private int posHandle,colHandle,mvpHandle,mPosHandle,mMvpHandle,mColHandle;
        private FloatBuffer vertexBuf;
        private int vertexCount=0;
        private volatile float[] pendingVerts;

        private volatile float[] pickedA=null,pickedB=null,pickedC=null;
        private FloatBuffer markerBufA,markerBufB,markerBufC,lineBufAB,lineBufAC;

        volatile float rotX=20f,rotY=0f,zoom=4f;
        private final float[] proj=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];
        private final float[] mvpSnapshot=new float[16];
        private final Object  mvpLock=new Object();

        void setVertices(float[] v){pendingVerts=v;}

        void setPickedPoints(float[] a,float[] b,float[] c){
            pickedA=a;pickedB=b;pickedC=c;
            if(a!=null)markerBufA=ptBuf(a); if(b!=null)markerBufB=ptBuf(b); if(c!=null)markerBufC=ptBuf(c);
            lineBufAB=(a!=null&&b!=null)?lnBuf(a,b):null;
            lineBufAC=(a!=null&&c!=null)?lnBuf(a,c):null;
        }

        float[] getMvpSnapshot(){synchronized(mvpLock){return java.util.Arrays.copyOf(mvpSnapshot,16);}}

        private FloatBuffer ptBuf(float[] p){ByteBuffer bb=ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder());FloatBuffer fb=bb.asFloatBuffer();fb.put(p,0,3).position(0);return fb;}
        private FloatBuffer lnBuf(float[] a,float[] b){ByteBuffer bb=ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder());FloatBuffer fb=bb.asFloatBuffer();fb.put(a,0,3).put(b,0,3).position(0);return fb;}

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig cfg){
            GLES20.glClearColor(0.08f,0.08f,0.13f,1f); GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            program=link(compile(GLES20.GL_VERTEX_SHADER,VERT),compile(GLES20.GL_FRAGMENT_SHADER,FRAG));
            posHandle=GLES20.glGetAttribLocation(program,"a_Position");
            colHandle=GLES20.glGetAttribLocation(program,"a_Color");
            mvpHandle=GLES20.glGetUniformLocation(program,"u_MVP");
            markerProgram=link(compile(GLES20.GL_VERTEX_SHADER,MARKER_VERT),compile(GLES20.GL_FRAGMENT_SHADER,MARKER_FRAG));
            mPosHandle=GLES20.glGetAttribLocation(markerProgram,"a_Position");
            mMvpHandle=GLES20.glGetUniformLocation(markerProgram,"u_MVP");
            mColHandle=GLES20.glGetUniformLocation(markerProgram,"u_Color");
        }

        @Override public void onSurfaceChanged(GL10 gl,int w,int h){
            GLES20.glViewport(0,0,w,h);
            Matrix.perspectiveM(proj,0,60f,(float)w/h,0.01f,500f);
        }

        @Override public void onDrawFrame(GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            float[] pv=pendingVerts;
            if(pv!=null){pendingVerts=null;ByteBuffer bb=ByteBuffer.allocateDirect(pv.length*4).order(ByteOrder.nativeOrder());vertexBuf=bb.asFloatBuffer();vertexBuf.put(pv).position(0);vertexCount=pv.length/6;}
            Matrix.setLookAtM(view,0,0,0,zoom,0,0,0,0,1,0);
            Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,rotX,1,0,0);Matrix.rotateM(model,0,rotY,0,1,0);
            Matrix.multiplyMM(mv,0,view,0,model,0);Matrix.multiplyMM(mvp,0,proj,0,mv,0);
            synchronized(mvpLock){System.arraycopy(mvp,0,mvpSnapshot,0,16);}

            // Point cloud
            if(vertexCount>0&&program!=0){
                GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
                vertexBuf.position(0);GLES20.glEnableVertexAttribArray(posHandle);GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,STRIDE,vertexBuf);
                vertexBuf.position(3);GLES20.glEnableVertexAttribArray(colHandle);GLES20.glVertexAttribPointer(colHandle,3,GLES20.GL_FLOAT,false,STRIDE,vertexBuf);
                GLES20.glDrawArrays(GLES20.GL_POINTS,0,vertexCount);
                GLES20.glDisableVertexAttribArray(posHandle);GLES20.glDisableVertexAttribArray(colHandle);
            }

            // Overlays
            if(markerProgram!=0){
                GLES20.glUseProgram(markerProgram);GLES20.glUniformMatrix4fv(mMvpHandle,1,false,mvp,0);
                if(pickedA!=null&&markerBufA!=null) drawMarker(markerBufA,1f,0.88f,0.1f,1f);   // yellow
                if(pickedB!=null&&markerBufB!=null) drawMarker(markerBufB,0.1f,0.9f,1f,1f);    // cyan
                if(pickedC!=null&&markerBufC!=null) drawMarker(markerBufC,0.2f,1f,0.4f,1f);    // green
                if(lineBufAB!=null) drawLine(lineBufAB,1f,1f,1f,1f);                           // white A-B
                if(lineBufAC!=null) drawLine(lineBufAC,0.2f,1f,0.4f,1f);                       // green A-C
            }
        }

        private void drawMarker(FloatBuffer buf,float r,float g,float b,float a){
            GLES20.glUniform4f(mColHandle,r,g,b,a);buf.position(0);
            GLES20.glEnableVertexAttribArray(mPosHandle);GLES20.glVertexAttribPointer(mPosHandle,3,GLES20.GL_FLOAT,false,12,buf);
            GLES20.glDrawArrays(GLES20.GL_POINTS,0,1);GLES20.glDisableVertexAttribArray(mPosHandle);
        }
        private void drawLine(FloatBuffer buf,float r,float g,float b,float a){
            GLES20.glUniform4f(mColHandle,r,g,b,a);buf.position(0);
            GLES20.glEnableVertexAttribArray(mPosHandle);GLES20.glVertexAttribPointer(mPosHandle,3,GLES20.GL_FLOAT,false,12,buf);
            GLES20.glDrawArrays(GLES20.GL_LINES,0,2);GLES20.glDisableVertexAttribArray(mPosHandle);
        }
        private int compile(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
        private int link(int v,int f){int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);return p;}
    }
}