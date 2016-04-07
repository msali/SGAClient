/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.libraries;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.view.ViewGroup.LayoutParams;

import com.android.libraries.gles.Drawable2d;
import com.android.libraries.gles.EglCore;
import com.android.libraries.gles.GlUtil;
import com.android.libraries.gles.Sprite2d;
import com.android.libraries.gles.Texture2dProgram;
import com.android.libraries.gles.WindowSurface;
import com.threed.jpct.RGBColor;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;


//sensors management
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/**
 * Direct the Camera preview to a GLES texture and manipulate it.
 * <p/>
 * We manage the Camera and GLES rendering from a dedicated thread.  We don't animate anything,
 * so we don't need a Choreographer heartbeat -- just redraw when we get a new frame from the
 * camera or the user has caused a change in size or position.
 * <p/>
 * The Camera needs to follow the activity pause/resume cycle so we don't keep it locked
 * while we're in the background.  Also, for power reasons, we don't want to keep getting
 * frames when the screen is off.  As noted in
 * http://source.android.com/devices/graphics/architecture.html#activity
 * the Surface lifecycle isn't quite the same as the activity's.  We follow approach #1.
 * <p/>
 * The tricky part about the lifecycle is that our SurfaceView's Surface can outlive the
 * Activity, and we can get surface callbacks while paused, so we need to keep track of it
 * in a static variable and be prepared for calls at odd times.
 * <p/>
 * The zoom, size, and rotate values are determined by the values stored in the "seek bars"
 * (sliders).  When the device is rotated, the Activity is paused and resumed, but the
 * controls retain their value, which is kind of nice.  The position, set by touch, is lost
 * on rotation.
 * <p/>
 * The UI updates go through a multi-stage process:
 * <ol>
 * <li> The user updates a slider.
 * <li> The new value is passed as a percent to the render thread.
 * <li> The render thread converts the percent to something concrete (e.g. size in pixels).
 * The rect geometry is updated.
 * <li> (For most things) The values computed by the render thread are sent back to the main
 * UI thread.
 * <li> (For most things) The UI thread updates some text views.
 * </ol>
 */
public class TextureFromCameraActivity extends Activity
        implements SurfaceHolder.Callback/*,SeekBar.OnSeekBarChangeListener*/
        {
    public static final String TAG = "TextureCameraActivity";/////MainActivity.TAG;

    private static final int DEFAULT_ZOOM_PERCENT = 0;      // 0-100
    private static final int DEFAULT_SIZE_PERCENT = 100;     // 0-100
    private static final int DEFAULT_ROTATE_PERCENT = 0;    // 0-100

    // Requested values; actual may differ.
    private static final int REQ_CAMERA_WIDTH = 1280;
    private static final int REQ_CAMERA_HEIGHT = 720;
    private static final int REQ_CAMERA_FPS = 30;


    /**
     * Id to identify a camera permission request.
     */
    public static final int REQUEST_FINE_LOC = 0;

    /**
     * Id to identify a contacts permission request.
     */
    public static final int REQUEST_COARSE_LOC = 1;

    // The holder for our SurfaceView.  The Surface can outlive the Activity (e.g. when
    // the screen is turned off and back on with the power button).
    //
    // This becomes non-null after the surfaceCreated() callback is called, and gets set
    // to null when surfaceDestroyed() is called.
    private static SurfaceHolder sSurfaceHolder;

    private SurfaceView cameraView = null;
    //private SurfaceView svOnTop = null;
    private TextView tmptv = null;
    private GLSurfaceView mGLView;
    private boolean gl2 = true;
    private JPCTWorldManager jpctWorldManager = null;
    private SimParameters simulation = null;




    // Thread that handles rendering and controls the camera.  Started in onResume(),
    // stopped in onPause().
    private RenderThread mRenderThread;


    // Receives messages from renderer thread.
    private MainHandler mHandler;

    /*
    // User controls.
    private SeekBar mZoomBar;
    private SeekBar mSizeBar;
    private SeekBar mRotateBar;
    */

    // These values are passed to us by the camera/render thread, and displayed in the UI.
    // We could also just peek at the values in the RenderThread object, but we'd need to
    // synchronize access carefully.
    private int mCameraPreviewWidth, mCameraPreviewHeight;
    private float mCameraPreviewFps;
    private int mRectWidth, mRectHeight;
    private int mZoomWidth, mZoomHeight;
    private int mRotateDeg;


    private Double xG, yG, zG;
    private Double vCamRoll,vCamPitch,vCamHead;
    private GPSLocator myLocator;
    private SensorFusion mySensorFusion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        this.simulation = new SimParameters();

        /*
            GPS Sensor
         */
        myLocator = new GPSLocator(this,simulation);


        //sensors
        mySensorFusion = new SensorFusion(this);

        jpctWorldManager = new JPCTWorldManager(this,simulation,myLocator);




        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        //setContentView(R.layout.activity_texture_from_camera);

        mHandler = new MainHandler(this);

        cameraView = new SurfaceView(this);
        SurfaceHolder sh = cameraView.getHolder();
        sh.addCallback(this);
        sh.setFormat(PixelFormat.TRANSLUCENT);

        /*
        svOnTop = new SurfaceView(this);
        SurfaceHolder sfOnTopHolder = svOnTop.getHolder();
        sfOnTopHolder.setFormat(PixelFormat.TRANSLUCENT);
        */

        tmptv = new TextView(this);
        tmptv.setBackgroundColor(PixelFormat.OPAQUE);

        mGLView = new GLSurfaceView(this);
        SurfaceHolder GLSsfOnTopHolder = mGLView.getHolder();
        GLSsfOnTopHolder.setFormat(PixelFormat.TRANSLUCENT);

        if (gl2) {
            mGLView.setEGLContextClientVersion(2);
        } else {
             /*
                The creation of a dedicated EGLConfigChooser:
                This serves one single purpose, which is to make 3D acceleration on old phones.
                Almost no device requires this nowadays but it doesn't hurt either.
            */
            mGLView.setEGLConfigChooser(new GLSurfaceView.EGLConfigChooser() {
                public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
                    // Ensure that we get a 16bit framebuffer. Otherwise, we'll
                    // fall back to Pixelflinger on some device (read: Samsung
                    // I7500). Current devices usually don't need this, but it
                    // doesn't hurt either.
                    int[] attributes = new int[]{EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE};
                    EGLConfig[] configs = new EGLConfig[1];
                    int[] result = new int[1];
                    egl.eglChooseConfig(display, attributes, configs, 1, result);
                    return configs[0];
                }
            });


        }

        // Translucent window 8888 pixel format and depth buffer
        mGLView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        mGLView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mGLView.setRenderer(jpctWorldManager);

        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        mGLView.setZOrderOnTop(true);

        setContentView(R.layout.activity_texture_from_camera);
        // get your outer relative layout
        RelativeLayout rl = (RelativeLayout) this.findViewById(R.id.relLay);
        // inflate content layout and add it to the relative layout as second child
        // add as second child, therefore pass index 1 (0,1,...)
        rl.addView(cameraView);
        rl.addView(mGLView);
        rl.addView(tmptv);

        /*
        mario
        mZoomBar = (SeekBar) findViewById(R.id.tfcZoom_seekbar);
        mSizeBar = (SeekBar) findViewById(R.id.tfcSize_seekbar);
        mRotateBar = (SeekBar) findViewById(R.id.tfcRotate_seekbar);
        mZoomBar.setProgress(DEFAULT_ZOOM_PERCENT);
        mSizeBar.setProgress(DEFAULT_SIZE_PERCENT);
        mRotateBar.setProgress(DEFAULT_ROTATE_PERCENT);
        mZoomBar.setOnSeekBarChangeListener(this);
        mSizeBar.setOnSeekBarChangeListener(this);
        mRotateBar.setOnSeekBarChangeListener(this);
        updateControls();
        */



    }


    @Override
    public void onStart() {
        super.onStart();

    }

    @Override
    public void onStop() {
        super.onStop();

        mySensorFusion.unregisterListeners();

    }


    @Override
    protected void onResume() {

        super.onResume();
        mGLView.onResume();
        myLocator.requestLocationUpdate();
        mySensorFusion.initListeners();
        //try catch mario
        try {
            mRenderThread = new RenderThread(mHandler);
            mRenderThread.setName("TexFromCam Render");
            mRenderThread.start();
            mRenderThread.waitUntilReady();

            RenderHandler rh = mRenderThread.getHandler();


            /*
            topRenderThread = new TopTextureViewRenderThread(mHandler, svOnTop, this);
            topRenderThread.setName("TopSurfView Render");
            topRenderThread.start();
            topRenderThread.waitUntilReady();
            */

            /*
            mario
            rh.sendZoomValue(mZoomBar.getProgress());
            rh.sendSizeValue(mSizeBar.getProgress());
            rh.sendRotateValue(mRotateBar.getProgress());
            */

            if (sSurfaceHolder != null) {
                Log.d(TAG, "Sending previous surface");
                rh.sendSurfaceAvailable(sSurfaceHolder, false);
            } else {
                Log.d(TAG, "No previous surface");
            }

        } catch (Exception e) {
            // Show toast to the user
            Toast.makeText(getApplicationContext(), "Data lost due to excess use of other apps", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause BEGIN");
        mGLView.onPause();
        myLocator.stopUsingGPS();
        super.onPause();

        mySensorFusion.unregisterListeners();

        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendShutdown();
            try {
                mRenderThread.join();
            } catch (InterruptedException ie) {
                // not expected
                throw new RuntimeException("join was interrupted", ie);
            }
            mRenderThread = null;
        }

        /*
        if (topRenderThread != null) {
            TopTextureViewRenderHandler topTVHandler = topRenderThread.getHandler();
            topTVHandler.sendShutdown();
            try {
                topRenderThread.join();
            } catch (InterruptedException ie) {
                // not expected
                throw new RuntimeException("join was interrupted", ie);
            }
            topRenderThread = null;
        }
        */
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_FINE_LOC: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                //return;
            }
            break;
            case REQUEST_COARSE_LOC: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                //return;
            }


            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated holder=" + holder + " (static=" + sSurfaceHolder + ")");
        if (sSurfaceHolder != null) {
            throw new RuntimeException("sSurfaceHolder is already set");
        }

        sSurfaceHolder = holder;

        if (mRenderThread != null) {
            // Normal case -- render thread is running, tell it about the new surface.
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendSurfaceAvailable(holder, true);
        } else {
            // Sometimes see this on 4.4.x N5: power off, power on, unlock, with device in
            // landscape and a lock screen that requires portrait.  The surface-created
            // message is showing up after onPause().
            //
            // Chances are good that the surface will be destroyed before the activity is
            // unpaused, but we track it anyway.  If the activity is un-paused and we start
            // the RenderThread, the SurfaceHolder will be passed in right after the thread
            // is created.
            Log.d(TAG, "render thread not running");
        }
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);

        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendSurfaceChanged(format, width, height);
        } else {
            Log.d(TAG, "Ignoring surfaceChanged");
            return;
        }
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        // In theory we should tell the RenderThread that the surface has been destroyed.
        if (mRenderThread != null) {
            RenderHandler rh = mRenderThread.getHandler();
            rh.sendSurfaceDestroyed();
        }
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
        sSurfaceHolder = null;
    }

    /*
    mario
    @Override   // SeekBar.OnSeekBarChangeListener
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (mRenderThread == null) {
            // Could happen if we programmatically update the values after setting a listener
            // but before starting the thread.  Also, easy to cause this by scrubbing the seek
            // bar with one finger then tapping "recents" with another.
            Log.w(TAG, "Ignoring onProgressChanged received w/o RT running");
            return;
        }
        RenderHandler rh = mRenderThread.getHandler();

        // "progress" ranges from 0 to 100
        if (seekBar == mZoomBar) {
            //Log.v(TAG, "zoom: " + progress);
            rh.sendZoomValue(progress);
        } else if (seekBar == mSizeBar) {
            //Log.v(TAG, "size: " + progress);
            rh.sendSizeValue(progress);
        } else if (seekBar == mRotateBar) {
            //Log.v(TAG, "rotate: " + progress);
            rh.sendRotateValue(progress);
        } else {
            throw new RuntimeException("unknown seek bar");
        }

        // If we're getting preview frames quickly enough we don't really need this, but
        // we don't want to have chunky-looking resize movement if the camera is slow.
        // OTOH, if we get the updates too quickly (60fps camera?), this could jam us
        // up and cause us to run behind.  So use with caution.
        rh.sendRedraw();
    }



    @Override   // SeekBar.OnSeekBarChangeListener
    public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override   // SeekBar.OnSeekBarChangeListener
    public void onStopTrackingTouch(SeekBar seekBar) {}
    */

    @Override
    /**
     * Handles any touch events that aren't grabbed by one of the controls.
     */
    public boolean onTouchEvent(MotionEvent e) {

        /*
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            xpos = e.getX();
            ypos = e.getY();
            return true;
        }

        if (e.getAction() == MotionEvent.ACTION_UP) {
            xpos = -1;
            ypos = -1;
            jpctWorldManager.touchTurn = 0;
            jpctWorldManager.touchTurnUp = 0;
            return true;
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            float xd = e.getX() - xpos;
            float yd = e.getY() - ypos;

            xpos = e.getX();
            ypos = e.getY();

            jpctWorldManager.touchTurn = xd / -100f;
            jpctWorldManager.touchTurnUp = yd / -100f;
            return true;
        }
        */
        return super.onTouchEvent(e);


        /*
        mario
        float x = e.getX();
        float y = e.getY();

        switch (e.getAction()) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_DOWN:
                //Log.v(TAG, "onTouchEvent act=" + e.getAction() + " x=" + x + " y=" + y);
                if (mRenderThread != null) {
                    RenderHandler rh = mRenderThread.getHandler();
                    rh.sendPosition((int) x, (int) y);

                    // Forcing a redraw can cause sluggish-looking behavior if the touch
                    // events arrive quickly.
                    //rh.sendRedraw();
                }
                break;
            default:
                break;
        }
        return true;
        */


    }


    //called by SensorFusion
    public void onNewOrientationAnglesComputed(float roll,float pitch,float head, final boolean facedown){

        final float rollA=roll,pitchA=pitch,headA=head;


        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                jpctWorldManager.setRPHCam(rollA, pitchA, headA, facedown);
            }
        });

    }

    public void onNewOrientationMatrixComputed(final float[] mResult) {

        mGLView.queueEvent(new Runnable() {
            @Override
            public void run() {
                jpctWorldManager.remapCoors(mResult);
            }
        });

    }


    public void showOrientationFusedAngle(double f0, double f1, double f2) {

        this.mHandler.sendTempMessageParams(f0, f1, f2);

    }

    public void showOrientationVirtualCameraAngle(float roll,float pitch,float head) {

                this.mHandler.sendVCamOrientation(roll,pitch,head);

    }



    /**
     * Updates the current state of the controls.
     */

    //mario
    private void updateControls() {
        //String str = getString(R.string.tfcCameraParams, mCameraPreviewWidth,mCameraPreviewHeight, mCameraPreviewFps);

        //TextView tv = (TextView) findViewById(R.id.tfcCameraParams_text);
        //tv.setText(str);
        if(xG!=null) {
            String coordinates = "H:" + xG.floatValue() + "\n P:" + yG.floatValue() + "\n R:" + zG.floatValue();
                    //+ "VR:" + vCamRoll.floatValue() + " VP:" + vCamPitch.floatValue() + " VH:" + vCamHead.floatValue();
            tmptv.setText(coordinates);
        }
       /*
        str = getString(R.string.tfcRectSize, mRectWidth, mRectHeight);
        tv = (TextView) findViewById(R.id.tfcRectSize_text);
        tv.setText(str);

        str = getString(R.string.tfcZoomArea, mZoomWidth, mZoomHeight);
        tv = (TextView) findViewById(R.id.tfcZoomArea_text);
        tv.setText(str);
        */
    }


    /**
     * Custom message handler for main UI thread.
     * <p/>
     * Receives messages from the renderer thread with UI-related updates, like the camera
     * parameters (which we show in a text message on screen).
     */
    private static class MainHandler extends Handler {
        private static final int MSG_SEND_CAMERA_PARAMS0 = 0;
        private static final int MSG_SEND_CAMERA_PARAMS1 = 1;
        private static final int MSG_SEND_RECT_SIZE = 2;
        private static final int MSG_SEND_ZOOM_AREA = 3;
        private static final int MSG_SEND_ROTATE_DEG = 4;
        private static final int MSG_SET_TEMP_TV = 5;
        private static final int MSG_SET_VCAM = 6;


        private WeakReference<TextureFromCameraActivity> mWeakActivity;

        public MainHandler(TextureFromCameraActivity activity) {
            mWeakActivity = new WeakReference<TextureFromCameraActivity>(activity);
        }

        public void sendTempMessageParams(double f0, double f1, double f2) {
            sendMessage(obtainMessage(MSG_SET_TEMP_TV, 0, 0, new Coordinates(f0, f1, f2)));
        }

        public void sendVCamOrientation(float roll,float pitch,float head){
            sendMessage(obtainMessage(MSG_SET_VCAM, 0, 0, new Coordinates(roll,pitch,head)));
        }

        /**
         * Sends the updated camera parameters to the main thread.
         * <p/>
         * Call from render thread.
         */
        public void sendCameraParams(int width, int height, float fps) {
            // The right way to do this is to bundle them up into an object.  The lazy
            // way is to send two messages.
            sendMessage(obtainMessage(MSG_SEND_CAMERA_PARAMS0, width, height));
            sendMessage(obtainMessage(MSG_SEND_CAMERA_PARAMS1, (int) (fps * 1000), 0));
        }

        /**
         * Sends the updated rect size to the main thread.
         * <p/>
         * Call from render thread.
         */
        public void sendRectSize(int width, int height) {
            sendMessage(obtainMessage(MSG_SEND_RECT_SIZE, width, height));
        }

        /**
         * Sends the updated zoom area to the main thread.
         * <p/>
         * Call from render thread.
         */
        public void sendZoomArea(int width, int height) {
            sendMessage(obtainMessage(MSG_SEND_ZOOM_AREA, width, height));
        }

        /**
         * Sends the updated zoom area to the main thread.
         * <p/>
         * Call from render thread.
         */
        public void sendRotateDeg(int rot) {
            sendMessage(obtainMessage(MSG_SEND_ROTATE_DEG, rot, 0));
        }

        @Override
        public void handleMessage(Message msg) {
            TextureFromCameraActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }

            switch (msg.what) {
                case MSG_SEND_CAMERA_PARAMS0: {
                    activity.mCameraPreviewWidth = msg.arg1;
                    activity.mCameraPreviewHeight = msg.arg2;
                    break;
                }
                case MSG_SEND_CAMERA_PARAMS1: {
                    activity.mCameraPreviewFps = msg.arg1 / 1000.0f;
                    //mario
                    //activity.updateControls();
                    break;
                }
                case MSG_SEND_RECT_SIZE: {
                    activity.mRectWidth = msg.arg1;
                    activity.mRectHeight = msg.arg2;
                    //mario
                    //activity.updateControls();
                    break;
                }
                case MSG_SEND_ZOOM_AREA: {
                    activity.mZoomWidth = msg.arg1;
                    activity.mZoomHeight = msg.arg2;
                    //mario
                    //activity.updateControls();
                    break;
                }
                case MSG_SEND_ROTATE_DEG: {
                    activity.mRotateDeg = msg.arg1;
                    //mario
                    //activity.updateControls();
                    break;
                }
                case MSG_SET_TEMP_TV: {
                    Coordinates coors = (Coordinates) msg.obj;
                    activity.xG = coors.getX();
                    activity.yG = coors.getY();
                    activity.zG = coors.getZ();
                    activity.updateControls();
                    break;
                }
                case MSG_SET_VCAM:{
                    Coordinates coors = (Coordinates) msg.obj;
                    activity.vCamRoll = coors.getX();
                    activity.vCamPitch = coors.getY();
                    activity.vCamHead = coors.getZ();
                    activity.updateControls();
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }


    /**
     * Thread that handles all rendering and camera operations.
     */
    private static class RenderThread extends Thread implements
            SurfaceTexture.OnFrameAvailableListener {
        // Object must be created on render thread to get correct Looper, but is used from
        // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully
        // constructed object.
        private volatile RenderHandler mHandler;

        // Used to wait for the thread to start.
        private Object mStartLock = new Object();
        private boolean mReady = false;

        private MainHandler mMainHandler;

        private Camera mCamera;
        private int mCameraPreviewWidth, mCameraPreviewHeight;

        private EglCore mEglCore;
        private WindowSurface mWindowSurface;
        private int mWindowSurfaceWidth;
        private int mWindowSurfaceHeight;

        // Receives the output from the camera preview.
        private SurfaceTexture mCameraTexture;

        // Orthographic projection matrix.
        private float[] mDisplayProjectionMatrix = new float[16];

        private Texture2dProgram mTexProgram;
        private final ScaledDrawable2d mRectDrawable =
                new ScaledDrawable2d(Drawable2d.Prefab.RECTANGLE);
        private final Sprite2d mRect = new Sprite2d(mRectDrawable);

        private int mZoomPercent = DEFAULT_ZOOM_PERCENT;
        private int mSizePercent = DEFAULT_SIZE_PERCENT;
        private int mRotatePercent = DEFAULT_ROTATE_PERCENT;
        private float mPosX, mPosY;


        /**
         * Constructor.  Pass in the MainHandler, which allows us to send stuff back to the
         * Activity.
         */
        public RenderThread(MainHandler handler) {
            mMainHandler = handler;
        }

        /**
         * Thread entry point.
         */
        @Override
        public void run() {
            Looper.prepare();

            // We need to create the Handler before reporting ready.
            mHandler = new RenderHandler(this);
            synchronized (mStartLock) {
                mReady = true;
                mStartLock.notify();    // signal waitUntilReady()
            }

            // Prepare EGL and open the camera before we start handling messages.
            mEglCore = new EglCore(null, 0);
            openCamera(REQ_CAMERA_WIDTH, REQ_CAMERA_HEIGHT, REQ_CAMERA_FPS);

            Looper.loop();

            Log.d(TAG, "looper quit");
            releaseCamera();
            releaseGl();
            mEglCore.release();

            synchronized (mStartLock) {
                mReady = false;
            }
        }

        /**
         * Waits until the render thread is ready to receive messages.
         * <p/>
         * Call from the UI thread.
         */
        public void waitUntilReady() {
            synchronized (mStartLock) {
                while (!mReady) {
                    try {
                        mStartLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        /**
         * Shuts everything down.
         */
        private void shutdown() {
            Log.d(TAG, "shutdown");
            Looper.myLooper().quit();
        }

        /**
         * Returns the render thread's Handler.  This may be called from any thread.
         */
        public RenderHandler getHandler() {
            return mHandler;
        }

        /**
         * Handles the surface-created callback from SurfaceView.  Prepares GLES and the Surface.
         */
        private void surfaceAvailable(SurfaceHolder holder, boolean newSurface) {
            Surface surface = holder.getSurface();
            mWindowSurface = new WindowSurface(mEglCore, surface, false);
            mWindowSurface.makeCurrent();

            // Create and configure the SurfaceTexture, which will receive frames from the
            // camera.  We set the textured rect's program to render from it.
            mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);
            int textureId = mTexProgram.createTextureObject();
            mCameraTexture = new SurfaceTexture(textureId);
            mRect.setTexture(textureId);

            if (!newSurface) {
                // This Surface was established on a previous run, so no surfaceChanged()
                // message is forthcoming.  Finish the surface setup now.
                //
                // We could also just call this unconditionally, and perhaps do an unnecessary
                // bit of reallocating if a surface-changed message arrives.
                mWindowSurfaceWidth = mWindowSurface.getWidth();
                mWindowSurfaceHeight = mWindowSurface.getHeight();
                finishSurfaceSetup();
            }

            mCameraTexture.setOnFrameAvailableListener(this);
        }

        /**
         * Releases most of the GL resources we currently hold (anything allocated by
         * surfaceAvailable()).
         * <p/>
         * Does not release EglCore.
         */
        private void releaseGl() {
            GlUtil.checkGlError("releaseGl start");

            if (mWindowSurface != null) {
                mWindowSurface.release();
                mWindowSurface = null;
            }
            if (mTexProgram != null) {
                mTexProgram.release();
                mTexProgram = null;
            }
            GlUtil.checkGlError("releaseGl done");

            mEglCore.makeNothingCurrent();
        }

        /**
         * Handles the surfaceChanged message.
         * <p/>
         * We always receive surfaceChanged() after surfaceCreated(), but surfaceAvailable()
         * could also be called with a Surface created on a previous run.  So this may not
         * be called.
         */
        private void surfaceChanged(int width, int height) {
            Log.d(TAG, "RenderThread surfaceChanged " + width + "x" + height);

            mWindowSurfaceWidth = width;
            mWindowSurfaceHeight = height;
            finishSurfaceSetup();
        }

        /**
         * Handles the surfaceDestroyed message.
         */
        private void surfaceDestroyed() {
            // In practice this never appears to be called -- the activity is always paused
            // before the surface is destroyed.  In theory it could be called though.
            Log.d(TAG, "RenderThread surfaceDestroyed");
            releaseGl();
        }

        /**
         * Sets up anything that depends on the window size.
         * <p/>
         * Open the camera (to set mCameraAspectRatio) before calling here.
         */
        private void finishSurfaceSetup() {
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;
            Log.d(TAG, "finishSurfaceSetup size=" + width + "x" + height +
                    " camera=" + mCameraPreviewWidth + "x" + mCameraPreviewHeight);

            // Use full window.
            GLES20.glViewport(0, 0, width, height);

            // Simple orthographic projection, with (0,0) in lower-left corner.
            Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, width, 0, height, -1, 1);

            // Default position is center of screen.
            mPosX = width / 2.0f;
            mPosY = height / 2.0f;

            updateGeometry();

            // Ready to go, start the camera.
            Log.d(TAG, "starting camera preview");
            try {
                mCamera.setPreviewTexture(mCameraTexture);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
            mCamera.startPreview();
        }

        /**
         * Updates the geometry of mRect, based on the size of the window and the current
         * values set by the UI.
         */
        private void updateGeometry() {
            int width = mWindowSurfaceWidth;
            int height = mWindowSurfaceHeight;

            int smallDim = Math.min(width, height);
            // Max scale is a bit larger than the screen, so we can show over-size.
            float scaled = smallDim * (mSizePercent / 100.0f) * 1.25f;
            float cameraAspect = (float) mCameraPreviewWidth / mCameraPreviewHeight;
            int newWidth = Math.round(scaled * cameraAspect);
            int newHeight = Math.round(scaled);

            float zoomFactor = 1.0f - (mZoomPercent / 100.0f);
            //mario rotation changed
            int rotAngle =-90;//Math.round(360 * (mRotatePercent / 100.0f));

            mRect.setScale(newWidth, newHeight);
            mRect.setPosition(mPosX, mPosY);
            mRect.setRotation(rotAngle);
            mRectDrawable.setScale(zoomFactor);

            mMainHandler.sendRectSize(newWidth, newHeight);
            mMainHandler.sendZoomArea(Math.round(mCameraPreviewWidth * zoomFactor),
                    Math.round(mCameraPreviewHeight * zoomFactor));
            mMainHandler.sendRotateDeg(rotAngle);
        }

        @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            mHandler.sendFrameAvailable();
        }

        /**
         * Handles incoming frame of data from the camera.
         */
        private void frameAvailable() {
            mCameraTexture.updateTexImage();
            draw();
        }

        /**
         * Draws the scene and submits the buffer.
         */
        private void draw() {
            GlUtil.checkGlError("draw start");

            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);
            mWindowSurface.swapBuffers();

            GlUtil.checkGlError("draw done");
        }

        private void setZoom(int percent) {
            mZoomPercent = percent;
            updateGeometry();
        }

        private void setSize(int percent) {
            mSizePercent = percent;
            updateGeometry();
        }

        private void setRotate(int percent) {
            mRotatePercent = percent;
            updateGeometry();
        }

        private void setPosition(int x, int y) {
            mPosX = x;
            mPosY = mWindowSurfaceHeight - y;   // GLES is upside-down
            updateGeometry();
        }


        /**
         * Opens a camera, and attempts to establish preview mode at the specified width
         * and height with a fixed frame rate.
         * <p/>
         * Sets mCameraPreviewWidth / mCameraPreviewHeight.
         */
        private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
            if (mCamera != null) {
                throw new RuntimeException("camera already initialized");
            }

            Camera.CameraInfo info = new Camera.CameraInfo();

            // Try to find a front-facing camera (e.g. for videoconferencing).
            int numCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    mCamera = Camera.open(i);
                    break;
                }
            }
            if (mCamera == null) {
                Log.d(TAG, "No front-facing camera found; opening default");
                mCamera = Camera.open();    // opens first back-facing camera
            }
            if (mCamera == null) {
                throw new RuntimeException("Unable to open camera");
            }

            Camera.Parameters parms = mCamera.getParameters();

            CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

            // Try to set the frame rate to a constant value.
            int thousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

            // Give the camera a hint that we're recording video.  This can have a big
            // impact on frame rate.
            parms.setRecordingHint(true);

            mCamera.setParameters(parms);

            int[] fpsRange = new int[2];
            Camera.Size mCameraPreviewSize = parms.getPreviewSize();
            parms.getPreviewFpsRange(fpsRange);
            String previewFacts = mCameraPreviewSize.width + "x" + mCameraPreviewSize.height;
            if (fpsRange[0] == fpsRange[1]) {
                previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
            } else {
                previewFacts += " @[" + (fpsRange[0] / 1000.0) +
                        " - " + (fpsRange[1] / 1000.0) + "] fps";
            }
            Log.i(TAG, "Camera config: " + previewFacts);

            mCameraPreviewWidth = mCameraPreviewSize.width;
            mCameraPreviewHeight = mCameraPreviewSize.height;
            mMainHandler.sendCameraParams(mCameraPreviewWidth, mCameraPreviewHeight,
                    thousandFps / 1000.0f);
        }

        /**
         * Stops camera preview, and releases the camera to the system.
         */
        private void releaseCamera() {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
                Log.d(TAG, "releaseCamera -- done");
            }
        }
    }


    /**
     * Handler for RenderThread.  Used for messages sent from the UI thread to the render thread.
     * <p/>
     * The object is created on the render thread, and the various "send" methods are called
     * from the UI thread.
     */
    private static class RenderHandler extends Handler {
        private static final int MSG_SURFACE_AVAILABLE = 0;
        private static final int MSG_SURFACE_CHANGED = 1;
        private static final int MSG_SURFACE_DESTROYED = 2;
        private static final int MSG_SHUTDOWN = 3;
        private static final int MSG_FRAME_AVAILABLE = 4;
        private static final int MSG_ZOOM_VALUE = 5;
        private static final int MSG_SIZE_VALUE = 6;
        private static final int MSG_ROTATE_VALUE = 7;
        private static final int MSG_POSITION = 8;
        private static final int MSG_REDRAW = 9;

        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private WeakReference<RenderThread> mWeakRenderThread;

        /**
         * Call from render thread.
         */
        public RenderHandler(RenderThread rt) {
            mWeakRenderThread = new WeakReference<RenderThread>(rt);
        }

        /**
         * Sends the "surface available" message.  If the surface was newly created (i.e.
         * this is called from surfaceCreated()), set newSurface to true.  If this is
         * being called during Activity startup for a previously-existing surface, set
         * newSurface to false.
         * <p/>
         * The flag tells the caller whether or not it can expect a surfaceChanged() to
         * arrive very soon.
         * <p/>
         * Call from UI thread.
         */
        public void sendSurfaceAvailable(SurfaceHolder holder, boolean newSurface) {
            sendMessage(obtainMessage(MSG_SURFACE_AVAILABLE,
                    newSurface ? 1 : 0, 0, holder));
        }

        /**
         * Sends the "surface changed" message, forwarding what we got from the SurfaceHolder.
         * <p/>
         * Call from UI thread.
         */
        public void sendSurfaceChanged(@SuppressWarnings("unused") int format, int width,
                                       int height) {
            // ignore format
            sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p/>
         * Call from UI thread.
         */
        public void sendSurfaceDestroyed() {
            sendMessage(obtainMessage(MSG_SURFACE_DESTROYED));
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * <p/>
         * Call from UI thread.
         */
        public void sendShutdown() {
            sendMessage(obtainMessage(MSG_SHUTDOWN));
        }

        /**
         * Sends the "frame available" message.
         * <p/>
         * Call from UI thread.
         */
        public void sendFrameAvailable() {
            sendMessage(obtainMessage(MSG_FRAME_AVAILABLE));
        }

        /**
         * Sends the "zoom value" message.  "progress" should be 0-100.
         * <p/>
         * Call from UI thread.
         */
        public void sendZoomValue(int progress) {
            sendMessage(obtainMessage(MSG_ZOOM_VALUE, progress, 0));
        }

        /**
         * Sends the "size value" message.  "progress" should be 0-100.
         * <p/>
         * Call from UI thread.
         */
        public void sendSizeValue(int progress) {
            sendMessage(obtainMessage(MSG_SIZE_VALUE, progress, 0));
        }

        /**
         * Sends the "rotate value" message.  "progress" should be 0-100.
         * <p/>
         * Call from UI thread.
         */
        public void sendRotateValue(int progress) {
            sendMessage(obtainMessage(MSG_ROTATE_VALUE, progress, 0));
        }

        /**
         * Sends the "position" message.  Sets the position of the rect.
         * <p/>
         * Call from UI thread.
         */
        public void sendPosition(int x, int y) {
            sendMessage(obtainMessage(MSG_POSITION, x, y));
        }


        /**
         * Sends the "redraw" message.  Forces an immediate redraw.
         * <p/>
         * Call from UI thread.
         */
        public void sendRedraw() {
            sendMessage(obtainMessage(MSG_REDRAW));
        }

        @Override  // runs on RenderThread
        public void handleMessage(Message msg) {
            int what = msg.what;
            //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);

            RenderThread renderThread = mWeakRenderThread.get();
            if (renderThread == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_SURFACE_AVAILABLE:
                    renderThread.surfaceAvailable((SurfaceHolder) msg.obj, msg.arg1 != 0);
                    break;
                case MSG_SURFACE_CHANGED:
                    renderThread.surfaceChanged(msg.arg1, msg.arg2);
                    break;
                case MSG_SURFACE_DESTROYED:
                    renderThread.surfaceDestroyed();
                    break;
                case MSG_SHUTDOWN:
                    renderThread.shutdown();
                    break;
                case MSG_FRAME_AVAILABLE:
                    renderThread.frameAvailable();
                    break;
                case MSG_ZOOM_VALUE:
                    renderThread.setZoom(msg.arg1);
                    break;
                case MSG_SIZE_VALUE:
                    renderThread.setSize(msg.arg1);
                    break;
                case MSG_ROTATE_VALUE:
                    renderThread.setRotate(msg.arg1);
                    break;
                case MSG_POSITION:
                    renderThread.setPosition(msg.arg1, msg.arg2);
                    break;
                case MSG_REDRAW:
                    renderThread.draw();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }


    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

}