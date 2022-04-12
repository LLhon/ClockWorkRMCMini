package com.palmbaby.lib_stfacedetect;

import android.annotation.SuppressLint;
import android.app.smdt.SmdtManager;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import com.blankj.utilcode.util.SPUtils;
import com.orhanobut.logger.Logger;
import com.palmbaby.lib_common.config.AppConfig;
import com.palmbaby.lib_stfacedetect.constants.Constants;
import com.palmbaby.lib_stfacedetect.event.UpdateCameraInfoEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.greenrobot.eventbus.EventBus;

/**
 * Camera帮助类，可以用于人脸识别
 */
public class CameraHelp implements Camera.ErrorCallback {

    private static final String TAG = "CameraHelp";
    Camera.Size pictureSize = null;
    int w;
    int h;
    public Camera camera;
    private Camera.Parameters parameters = null;
    private SurfaceView surfaceView;
    private CameraCreatedListener listener;
    private SurfaceCallback surfaceCallback;
    Camera.PictureCallback pictureCallback = null;
    Camera.Size previewSize = null;
    private final ReadWriteLock lock;
    private boolean isAdjustmentSurfaceView = true;  //主页不显示预览。所以这里用于区分主页和摄像头检测页面

    private byte[] mBuffer;  //用于预览缓存
    private Looper previewLooper;
    private PreviewHandler previewHandler;
    private static final int PREVIEW_HANDLER_MESSAGE = 0;
    private CameraPreviewCallback cameraPreviewCallback;
    private int displayRotation = 0;  //Display  的 rotation
    private int degree = 0;  //预览画面旋转角度，默认0（一般是横屏时）
    private int cameraId = -1;
    private int cameraFacing = -1;  //使用摄像头的类型：前置或后置，默认后置
    private Boolean isMonocular = false; //用来记录是否是单目摄像头

    public CameraHelp(int cameraId) {
        this(cameraId, -1, null, null);
    }

    public CameraHelp(boolean isCameraFacingFront) {
        this(-1, isCameraFacingFront ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK, null, null);
    }

    public CameraHelp(SurfaceView sv, CameraCreatedListener listener) {
        this(-1, Camera.CameraInfo.CAMERA_FACING_BACK, sv, listener);
    }

    /**
     * 指定cameraId优先于指定cameraFacing
     */
    public CameraHelp(int cameraId, int cameraFacing, SurfaceView sv, CameraCreatedListener listener) {
        if (cameraId < 0 && cameraFacing != Camera.CameraInfo.CAMERA_FACING_FRONT && cameraFacing != Camera.CameraInfo.CAMERA_FACING_BACK) {
            cameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
        } else if (cameraId >= 0) {
            cameraFacing = -1;  //cameraId 优先
        }
        this.cameraId = cameraId;
        this.cameraFacing = cameraFacing;
        surfaceView = sv;
        this.listener = listener;
        pictureCallback = null;
        lock = new ReentrantReadWriteLock(); //可重入的读写锁
    }

    public void setDisplayRotation(int rotation) {
        this.displayRotation = rotation;
    }


    public void setAdjustmentSurfaceView(boolean adjustmentSurfaceView) {
        isAdjustmentSurfaceView = adjustmentSurfaceView;
    }

    /**
     * 调用初始化后会自动打开相机并设置参数开始预览，surfaceView为null时，不能调用只能手动打开相机设置参数
     */
    public void initSurfaceView() {
        if (surfaceView == null) {
            return;
        }
        surfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        surfaceView.getHolder().setKeepScreenOn(true);// 屏幕常亮
        if (surfaceCallback != null) {
            surfaceView.getHolder().removeCallback(surfaceCallback);
        }
        surfaceCallback = new SurfaceCallback();
        surfaceView.getHolder().addCallback(surfaceCallback);// 为SurfaceView的句柄添加一个回调函数
        pictureCallback = null;
    }

    public void ForciblyCreated() {
        if (surfaceView != null) {
            surfaceCallback.surfaceCreated(surfaceView.getHolder());
            pictureCallback = null;
        }
    }

    public void stopPreviewAndRelease() {
//        surfaceView.getHolder().removeCallback(surfaceCallback);
        try {
            lock.writeLock().lock();
            previewSize = null;
            if (camera != null) {
                camera.stopPreview();
                camera.release(); // 释放照相机
                Log.e("camera", "camera release");
            }
            camera = null;
            pictureCallback = null;
            if (previewLooper != null) {
                previewLooper.quit();
                previewLooper = null;
            }
            if (previewHandler != null) {
                previewHandler.removeMessages(PREVIEW_HANDLER_MESSAGE);
                previewHandler = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isCameraAvailable() {
        return camera != null;
    }

    public boolean isTaking() {
        return pictureCallback != null;
    }

    /**
     * 拍照
     *
     * @param jpeg 结果回调
     * @return 0：拍照调用成功   -1：拍照调用失败     1：当前拍照中，不能重复调用
     */
    public int takePicture(Camera.ShutterCallback shutter, Camera.PictureCallback raw, Camera.PictureCallback jpeg) {
        if (camera != null && surfaceCallback != null) {
            if (isTaking()) {
                return 1;  //不能重复调用拍照
            }
            if (lock.readLock().tryLock()) {  //尝试获取相机锁
                try {
                    if (camera != null) {
                        pictureCallback = jpeg;
                        return 0;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
//                    DBUtils.saveLog("拍照发送异常" + e.toString());
                    Logger.e("拍照发送异常" + e.toString());
                } finally {
                    lock.readLock().unlock();
                }
            }
        }
        return -1;
    }

    private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (camera != null && data != null) {
                if (previewHandler != null && !previewHandler.hasMessages(PREVIEW_HANDLER_MESSAGE) && previewSize != null) {
                    byte[] nv21 = new byte[data.length];  //用于预览处理
                    System.arraycopy(data, 0, nv21, 0, data.length);
                    previewHandler.obtainMessage(PREVIEW_HANDLER_MESSAGE, nv21).sendToTarget();
                }
                addCallbackBuffer(data);  //这里必须调用这个方法
            }
        }
    };

    private class PreviewHandler extends Handler {

        public PreviewHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            byte[] data = (byte[]) msg.obj;
            Camera.PictureCallback callback = pictureCallback;
            if (callback != null) {
                byte[] bytes = decodeToByte(data);
                if (bytes != null) {
                    callback.onPictureTaken(bytes, camera);
                }
                pictureCallback = null;
                return;
            }
            restartCameraCount = 0;//将相机重连次数重置成0
            //人脸识别Callback
            if (cameraPreviewCallback != null && previewSize != null) {
                cameraPreviewCallback.onPreviewFrame(data, previewSize.width, previewSize.height);
            }
        }

        private byte[] decodeToByte(byte[] data) {
            if (previewSize == null) {
                return null;
            }
            byte[] bytes = null;
            try {
                YuvImage image = new YuvImage(data, ImageFormat.NV21, previewSize.width,
                        previewSize.height, null);
                if (image != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    image.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height),
                            80, stream);
                    bytes = stream.toByteArray();
                    stream.close();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return bytes;
        }
    }

    public void setCameraPreviewCallback(CameraPreviewCallback cameraPreviewCallback) {
        this.cameraPreviewCallback = cameraPreviewCallback;
    }

    private final class SurfaceCallback implements SurfaceHolder.Callback {

        // 拍照状态变化时调用该方法
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if ((w == 0 || h == 0) && pictureSize != null) {
                double pw = pictureSize.width;
                double ph = pictureSize.height;
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                Camera.getCameraInfo(cameraId, cameraInfo);
                int orientation = cameraInfo.orientation; // 摄像头的默认方向。影响需要设置的摄像头旋转角度
                if (orientation == 0) { //
                    if (width > height) {
                        w = width;
                        h = (int) (width * (ph / pw));
                    } else {
                        w = (int) (height * (pw / ph));
                        h = height;
                    }
                } else {
                    if (width > height) {
                        w = (int) (height * (ph / pw));
                        h = height;
                    } else {
                        w = width;
                        h = (int) (width * (pw / ph));
                    }
                }
                ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
                layoutParams.width = w;
                layoutParams.height = h;
                Log.d("testttt","w:"+w +"---h:"+ h);
                if (true) {
                    surfaceView.setLayoutParams(layoutParams);
                }
            }
        }

        // 开始拍照时调用该方法
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            EventBus.getDefault().post(new UpdateCameraInfoEvent(false));
            if (initCameraParameters() && startPreview(holder)) {
                if (listener != null) {
                    listener.onCreatedSucceed(camera, holder, cameraId, cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT, isMonocular);
                }
            } else {
//                MobclickAgent.onEvent(MyApplication.getContext(), "CameraError");
                if (listener != null) {
                    listener.onCreatedErr(cameraId);
                }
            }
        }

        // 停止拍照时调用该方法
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            stopPreviewAndRelease();
            if (listener != null) {
                listener.onSurfaceDestroyed(holder);
            }
        }
    }


    private final int RESTART_CAMERA_CODE = 0x100; //重启相机，重新与surfaceView绑定
    private final int RESTART_SYSTEM_CODE = 0x101; //重启相机，重新与surfaceView绑定

    private int restartCameraCount = 0;
    @SuppressLint("HandlerLeak")
    private Handler myHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {

                case RESTART_CAMERA_CODE:
                    if (surfaceView != null) {
                        SurfaceHolder holder = surfaceView.getHolder();
                        if (holder != null) {
                            if (initCameraParameters() && startPreview(holder)) {
                                Logger.e("摄像头初始化成功了!");
                                SPUtils.getInstance().put(Constants.CAMERA_RESTART_COUNT, 0);
                                myHandler.removeCallbacksAndMessages(null);
                                EventBus.getDefault().post(new UpdateCameraInfoEvent(false));
                                restartCameraCount = 0;
                                if (listener != null) {
                                    listener.onCreatedSucceed(camera, holder, cameraId, cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT, isMonocular);
                                }
                            } else {
                                if (listener != null) {
                                    listener.onCreatedErr(cameraId);
                                    if (restartCameraCount < 3) {//进行相机重连接，第一次20s  第二次 40s  第三次 60s
                                        restartCameraCount++;
//                                        DBUtils.saveLog("RESTART_CAMERA_CODE摄像头异常进行第" + restartCameraCount + "次重连！");
                                        Logger.e("RESTART_CAMERA_CODE摄像头异常进行第" + restartCameraCount + "次重连！");
                                        myHandler.sendEmptyMessageDelayed(RESTART_CAMERA_CODE, restartCameraCount * 5 * 1000);
                                    } else {
                                        myHandler.sendEmptyMessage(RESTART_SYSTEM_CODE);
                                    }

                                }
                            }
                        }

                    }

                    break;

                case RESTART_SYSTEM_CODE:
                    Logger.e("接受到重启请求啦!");
                    //记录次数
                    int cameraRestartCount = SPUtils.getInstance().getInt(Constants.CAMERA_RESTART_COUNT, 0);

                    if (cameraRestartCount > 2) {
                        Logger.e("UI提示,不重启!");
                        //UI提示,不重启
                        EventBus.getDefault().post(new UpdateCameraInfoEvent(true));
                        //发送事件提醒用户
                    } else {
                        Logger.e("次数没到,重启设备!");
                        cameraRestartCount += 1;
                        SPUtils.getInstance().put(Constants.CAMERA_RESTART_COUNT, cameraRestartCount);
                        EventBus.getDefault().post(new UpdateCameraInfoEvent(false));
                        //重启设备
                        SmdtManager smdt = SmdtManager.create(AppConfig.INSTANCE.getApplication());
                        smdt.smdtReboot("reboot");
                    }
                    break;
                default:
                    break;
            }

        }
    };


    @Override
    public void onError(int error, Camera camera) {
        try {
            stopPreviewAndRelease();
            Logger.e("摄像头异常(cameraId:" + cameraId + "):errorCode=" + error);
            if (restartCameraCount < 3) {//进行相机重连接，第一次20s  第二次 40s  第三次 60s
                restartCameraCount++;
                Logger.e("摄像头异常进行第" + restartCameraCount + "次重连！");
                myHandler.sendEmptyMessageDelayed(RESTART_CAMERA_CODE, restartCameraCount * 5 * 1000);
            } else {
                Logger.e("重启设备!RESTART_SYSTEM_CODE");
                myHandler.sendEmptyMessage(RESTART_SYSTEM_CODE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int findCameraId() {
        if (cameraId >= 0) {
            return cameraId;
        }
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        int cameraCount = Camera.getNumberOfCameras(); // get cameras number
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            if (cameraCount == 1) {
                Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo
                isMonocular = true;
                cameraId = 0;
                break;
            } else {
                Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo
                if (cameraInfo.facing == cameraFacing) {
                    cameraId = camIdx;
                    isMonocular = false;
                    break;
                }

            }

        }
        return cameraId;
    }

    public boolean initCameraParameters() {
        try {
            if (camera == null) {
                int cameraId = findCameraId();
                if (cameraId >= 0) {
                    camera = Camera.open(this.cameraId);// 取得摄像头
                } else {
                    return false;
                }
            }
            //设置参数
            parameters = camera.getParameters(); // 获取各项参数
            parameters.setPictureFormat(PixelFormat.JPEG); // 设置图片格式
            if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE); // 设置对焦模式
            }
            // 这里设置预览大小。照片大小，和surfaceView大小。只要保证三者宽高比例相同就好。
            // 注意设置的摄像头旋转角度。如果为90、270，则照片宽高对应预览高宽。如果为0、180，则照片宽高对应预览宽高
            if (pictureSize == null) {
                List<Camera.Size> pictureSizes = parameters.getSupportedPictureSizes();


                for (int i = 0; i < pictureSizes.size(); i++) {
                    Camera.Size size = pictureSizes.get(i);
                    Log.d("TestCamera", "pictureSize.width:" + size.width + "---pictureSize.height:" + size.height);
                }

                pictureSize = CameraHelp.getCurrentScreenSize(pictureSizes, 640, 480);
//                pictureSize = CameraHelp.getCurrentScreenSize(pictureSizes, DeviceInfo.ScreenWidth, DeviceInfo.ScreenHeight);
            }
            if (pictureSize != null) {
                parameters.setPictureSize(pictureSize.width, pictureSize.height);
            }
            List<Camera.Size> previewSizes = parameters.getSupportedPreviewSizes();
            previewSize = CameraHelp.getCurrentScreenSize(previewSizes, pictureSize.width, pictureSize.height);


            Log.d("ADDRR", "pictureSize.width:" + pictureSize.width);
            Log.d("ADDRR", "pictureSize.height:" + pictureSize.height);


            Log.d("ADDRR", "previewSize.width:" + previewSize.width);
            Log.d("ADDRR", "previewSize.height:" + previewSize.height);

//            previewSize.width = 1280;
//            previewSize.height = 720;
            parameters.setPreviewSize(previewSize.width, previewSize.height);
//                List<int[]> supportedPreviewFpsRange = parameters.getSupportedPreviewFpsRange();  //5000-60000
            parameters.setPreviewFpsRange(5, 10);
//                parameters.set("jpen-quality", 85);
            parameters.setPictureFormat(ImageFormat.JPEG);
            parameters.setJpegQuality(80); // 设置照片质量
            parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            try {
                camera.setParameters(parameters);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Logger.e("摄像头异常(cameraId:" + cameraId + "):" + e.toString());
        }
        return camera != null;
    }

    public boolean startPreview(SurfaceHolder holder) {
        if (camera == null) {
            return false;
        }
        if (holder != null) {
            try {
                camera.setPreviewDisplay(holder); // 设置用于显示拍照影像的SurfaceHolder对象
            } catch (IOException e) {
                e.printStackTrace();
            }
            //这里为一些指定的考勤机手动设置旋转角度
            if (DeviceInfo.degree >= 0) {
                degree = DeviceInfo.degree;
            } else {
                degree = getPreviewDegree();
            }
//            degree = 90;
            DeviceInfo.degree = degree;
            //设置旋转角并不影响拍照和onPreviewFrame的方向，只影响屏幕显示的预览画面方向
            camera.setDisplayOrientation(degree);
        }
        initPreviewBuffer();  //初始化预览监听
        camera.startPreview(); // 开始预览
        camera.setErrorCallback(this);
        return true;
    }


    private void initPreviewBuffer() {
        if (camera == null || previewSize == null) {
            return;
        }
        HandlerThread ht = new HandlerThread("PreviewHandler");
        ht.start();
        previewLooper = ht.getLooper();
        previewHandler = new PreviewHandler(previewLooper);
        mBuffer = new byte[previewSize.width * previewSize.height * 3 / 2]; // 初始化预览缓冲数据的大小
        camera.addCallbackBuffer(mBuffer); // 将此预览缓冲数据添加到相机预览缓冲数据队列里
        camera.setPreviewCallbackWithBuffer(previewCallback); // 设置预览的回调
    }

    /**
     * 每次预览的回调中，需要调用这个方法才可以起到重用mBuffer
     */
    private void addCallbackBuffer(byte[] data) {
        if (camera != null) {
            camera.addCallbackBuffer(mBuffer);
        }
    }

    // 提供一个静态方法，用于根据手机方向获得相机预览画面旋转的角度
    private int getPreviewDegree() {
        int result = 0;
        int degree = 0;
        // 获得activity的旋转方向，跟随系统的默认方向。比如系统默认竖屏则activity竖屏为0
        switch (displayRotation) {
            case Surface.ROTATION_0:
                degree = 0;
                break;
            case Surface.ROTATION_90:
                degree = 90;
                break;
            case Surface.ROTATION_180:
                degree = 180;
                break;
            case Surface.ROTATION_270:
                degree = 270;
                break;
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        //获取摄像头在系统默认方向时应该旋转的方向
        Camera.getCameraInfo(cameraId, info);
        //如果activity不是默认的系统方向，则需要根据当前activity的旋转方向计算正确的旋转方向
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            //前置摄像头的预览显示在旋转之前需要水平翻转，即图像沿着摄像头传感器的中心垂直轴水平翻转，所以旋转90或270时，实际上是选择270或0
            result = (info.orientation + degree) % 360;
            result = (360 - result) % 360;
            cameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            result = (info.orientation - degree + 360) % 360;
            cameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        return result;
    }

    /**
     * 获取合适的预览图和照片的大小
     */
    private static Camera.Size getCurrentScreenSize(List<Camera.Size> sizeList, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizeList == null)
            return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;

        // 尝试找到一个匹配宽高比的大小
        for (Camera.Size size : sizeList) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // 如果找不到匹配的宽高比，忽略此要求
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizeList) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    public interface CameraCreatedListener {
        void onCreatedSucceed(Camera camera, SurfaceHolder holder, int cameraId, boolean isCameraFacingFront, boolean isMonocular);

        void onCreatedErr(int cameraId);

        void onSurfaceDestroyed(SurfaceHolder holder);
    }

    public interface CameraPreviewCallback {
        void onPreviewFrame(byte[] data, int previewWidth, int previewHeight);
    }
}
