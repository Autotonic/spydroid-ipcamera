package study.lastwarmth.me.videocapturedemo;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private SurfaceView surfaceview;

    private SurfaceHolder surfaceHolder;

    private Camera camera;

    private Camera.Parameters parameters;

    int width = 1280;

    int height = 720;

    int framerate = 30;

    int biterate = 8500 * 1000;

    private static int yuvqueuesize = 10;

    public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(yuvqueuesize);

    private AvcEncoder avcCodec;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceview = (SurfaceView) findViewById(R.id.surfaceview);
        surfaceHolder = surfaceview.getHolder();
        surfaceHolder.addCallback(this);
        SupportAvcCodec();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = getBackCamera();
        startcamera(camera);
        avcCodec = new AvcEncoder(width, height, framerate, biterate);
        Camera.Parameters parameters = camera.getParameters();
        String supportedSizesStr = "Supported resolutions: ";
        List<Camera.Size> supportedSizes = parameters.getSupportedPreviewSizes();
        for (Iterator<Camera.Size> it = supportedSizes.iterator(); it.hasNext(); ) {
            Camera.Size size = it.next();
            supportedSizesStr += size.width + "x" + size.height + (it.hasNext() ? ", " : "");
        }
        Log.v("TAG", supportedSizesStr);
        avcCodec.StartEncoderThread();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != camera) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
            avcCodec.StopThread();
        }
    }


    @Override
    public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
        // TODO Auto-generated method stub
//        int length = data.length;
//        byte[] rotate = new byte[length];
//        rotateYUV240SP(data, rotate, width, height);
//        rotate(width, height, data, rotate);

        putYUVData(data, data.length);
    }

    public void putYUVData(byte[] buffer, int length) {
        if (YUVQueue.size() >= 10) {
            YUVQueue.poll();
        }
        YUVQueue.add(buffer);
    }

    @SuppressLint("NewApi")
    private boolean SupportAvcCodec() {
        if (Build.VERSION.SDK_INT >= 18) {
            for (int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);

                String[] types = codecInfo.getSupportedTypes();
                for (int i = 0; i < types.length; i++) {
                    if (types[i].equalsIgnoreCase("video/avc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private void startcamera(Camera mCamera) {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(this);
                mCamera.setDisplayOrientation(90);
                if (parameters == null) {
                    parameters = mCamera.getParameters();
                }
                parameters = mCamera.getParameters();
                parameters.setPreviewFormat(ImageFormat.NV21);
                parameters.setPreviewSize(width, height);
                mCamera.setParameters(parameters);
                mCamera.setPreviewDisplay(surfaceHolder);
                mCamera.startPreview();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @TargetApi(9)
    private Camera getBackCamera() {
        Camera c = null;
        try {
            c = Camera.open(0); // attempt to get a Camera instance
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c; // returns null if camera is unavailable
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {//屏幕触摸事件
        if (event.getAction() == MotionEvent.ACTION_DOWN) {//按下时自动对焦
            camera.autoFocus(null);
        }
        return true;
    }

//    public static void rotateYUV240SP(byte[] src, byte[] des, int width, int height) {
//
//        int wh = width * height;
//        //旋转Y
//        int k = 0;
//        for (int i = 0; i < width; i++) {
//            for (int j = 0; j < height; j++) {
//                des[k] = src[width * j + i];
//                k++;
//            }
//        }
//
//        for (int i = 0; i < width; i += 2) {
//            for (int j = 0; j < height / 2; j++) {
//                des[k] = src[wh + width * j + i];
//                des[k + 1] = src[wh + width * j + i + 1];
//                k += 2;
//            }
//        }
//
//
//    }

    // http://www.ay27.com/2014/10/09/2014-10-09-a-simple-image-rotate-function/
//    private static void rotate(int width, int height, byte[] src, byte[] dst) {
//        int delta = 0;
//        for (int x = 0; x < width; x++) {
//            for (int y = height - 1; y >= 0; y--) {
//                dst[delta] = src[y * width + x];
//                delta++;
//            }
//        }
//
//        int wh = width * height;
//        int ww = width / 2, hh = height / 2;
//
//        for (int i = 0; i < ww; i++) {
//            for (int j = 0; j < hh; j++) {
//                dst[delta] = src[wh + width * (hh - j - 1) + i];
//                dst[delta + 1] = src[wh + width * (hh - j - 1) + i + 1];
//                delta += 2;
//            }
//        }
//    }
}
