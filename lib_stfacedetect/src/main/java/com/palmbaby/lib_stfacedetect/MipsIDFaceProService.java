package com.palmbaby.lib_stfacedetect;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.SparseLongArray;
import androidx.annotation.Nullable;
import com.ancda.registration.MyApplication;
import com.ancda.registration.MyEventCode;
import com.ancda.registration.event.ActivityOnPauseEvent;
import com.ancda.registration.model.FaceData;
import com.ancda.registration.model.FaceWidthTooSmallEvent;
import com.ancda.registration.model.UpdateFaceInitStatusEvent;
import com.ancda.registration.utils.DBUtils;
import com.ancda.registration.utils.FileUtils;
import com.hugbio.log.AncdaLog;
import com.palmbaby.lib_stfacedetect.model.ScanFaceInfo;
import com.smdt.facesdk.mipsFaceInfoTrack;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * 人脸识别服务
 */
public class MipsIDFaceProService extends Service {

    private static final String TAG = MipsIDFaceProService.class.getSimpleName();
    private String libPath;
    int PREVIEW_WIDTH = 0;
    int PREVIEW_HEIGHT = 0;

    private byte nv21[];
    private byte tmp[];
    private boolean isNV21ready = false;

    private boolean killed = false;
    private Thread mTrackThread = null;
//    private Thread mInitTestThread;
//    private Thread thread1;

    private long timeBak = 0;
    private int framePerSec = 0;

    private String license;
    mipsFaceInfoTrack[] mFaceInfoDetected;

    private volatile boolean mIsTracking = false; // 是否正在进行track操作

    private int mtrackLivenessID = -1;
    private MipsIDFaceManage mipsIDFaceManage = null;

    private WeakReference<MipsIDFaceCallback> callbackWeakReference = null;

    //双目摄像头
    private CameraHelp cameraHelpIR;   //双目摄像头
    private byte nv21IR[];
    private byte tmpIR[];
    private boolean isNV21readyIR = false;
    int PREVIEW_WIDTH_IR = 0;
    int PREVIEW_HEIGHT_IR = 0;

    private long lastUnmatchedTime = 0;
    private long timeInterval = 1500;
    private int lastCheckStatus = -3;
    private int lastSwipeFaceId = -1;  //上一次匹配到的人脸
    private final SparseLongArray faceRecordMap = new SparseLongArray();//人脸匹配对应时间记录

    private boolean isContinueCheckFace = false;  //是否继续监测人脸数据


    public void setFaceCallback(MipsIDFaceCallback callback) {
        callbackWeakReference = new WeakReference<>(callback);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new Binder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        closeCameraIR();
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        startCameraIR();
    }

    public class Binder extends android.os.Binder {
        public MipsIDFaceProService getService() {
            return MipsIDFaceProService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        libPath = FileUtils.getExternalDir(this) + "mipsLic/mipsAi.lic";
        //首次启动服务时，尝试开启副摄像头，再决定初始化sdk方式
        openCameraIR();

        new FaceSdkInitThread().start();
    }


    class FaceSdkInitThread extends Thread {
        @Override
        public void run() {
            //初始化sdk，并且启动人脸识别线程
            int ret = startDetect(MipsIDFaceProService.this, "", cameraHelpIR != null);  //使用批量授权，不需要传单独传授权文件（libPath)
            if (ret < 0) {
                MyApplication.faceSDKInitStatus = 1;
                DBUtils.saveLog("初始化人脸识别SDK失败：" + ret);
            } else {
                MyApplication.faceSDKInitStatus = 0;
            }
            EventBus.getDefault().post(new UpdateFaceInitStatusEvent());

        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    public void startCameraIR() {
        if (cameraHelpIR != null) {  //服务被绑定时，如果之前成功打开过副摄像头则重新打开副摄像头
            new Thread(new Runnable() {
                @Override
                public void run() {
                    openCameraIR();
                }
            }).start();
        }
    }

    /**
     * 首次启动服务或者打开
     */
    private synchronized void openCameraIR() {
        int cameraCount = Camera.getNumberOfCameras(); // get cameras number
        if (cameraCount == 2) {//双目
            if (cameraHelpIR == null) {
                cameraHelpIR = new CameraHelp(true);  //后置摄像头用于预览。这里打开前置摄像头辅助活体检测
            }
            if (!cameraHelpIR.isCameraAvailable()) {
                if (cameraHelpIR.initCameraParameters() && cameraHelpIR.startPreview(null)) {
                    //找到双目摄像头
                    cameraHelpIR.setCameraPreviewCallback(previewCallbackIR);
                } else {//没有双目摄像头，不支持双目活体检测
                    closeCameraIR();
                    cameraHelpIR = null;
                }
            }
        } else {//单目
            closeCameraIR();
            cameraHelpIR = null;
        }

    }

    /**
     * 当app退出主页面关闭主摄像头时，调用此方法关闭副摄像头
     */
    private void closeCameraIR() {
        if (cameraHelpIR != null) {
            cameraHelpIR.stopPreviewAndRelease();
            cameraHelpIR.setCameraPreviewCallback(null);
        }
    }

    private int startDetect(Context context, String licPath, boolean isLiving) {
        int ret = 0;
        if (mipsIDFaceManage == null) {
            ret = initDetect(context, licPath, isLiving);
        }
        if (ret < 0) {
            mipsIDFaceManage = null;
        }
        return ret;
    }

    private CameraHelp.CameraPreviewCallback previewCallback = new CameraHelp.CameraPreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, int previewWidth, int previewHeight) {
            if (data == null) {
                return; // 切分辨率的过程中可能这个地方的data为空
            }
            if (mTrackThread == null || !MyApplication.isFaceService) { //人脸识别库没有初始化,或者人脸识别服务器关闭
                if (!mIsTracking && lastCheckStatus != -3) {
                    noticeFaceCallback(null);
                    resetCheckResult();
                    resetCheckStatus();
                }
                return;
            }
            if (!mIsTracking) {
                if (PREVIEW_WIDTH != previewWidth || PREVIEW_HEIGHT != previewHeight) {
                    PREVIEW_WIDTH = previewWidth;
                    PREVIEW_HEIGHT = previewHeight;
                    initBuff(previewWidth, previewHeight);
                }
                if (nv21 == null) {
                    return;
                }
                synchronized (nv21) {
                    System.arraycopy(data, 0, nv21, 0, data.length);
                    isNV21ready = true;
                }
                synchronized (mTrackThread) {
                    mTrackThread.notify();
                }
            }
        }

        private void initBuff(int width, int height) {
            nv21 = new byte[width * height * 2];
            tmp = new byte[width * height * 2];
        }
    };

    private CameraHelp.CameraPreviewCallback previewCallbackIR = new CameraHelp.CameraPreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, int previewWidth, int previewHeight) {
            if (data == null) {
                return; // 切分辨率的过程中可能这个地方的data为空
            }
            if (mTrackThread == null || !MyApplication.isFaceService) { //人脸识别库没有初始化
                return;
            }


            if (!mIsTracking) {
                if (PREVIEW_WIDTH_IR != previewWidth || PREVIEW_HEIGHT_IR != previewHeight) {
                    PREVIEW_WIDTH_IR = previewWidth;
                    PREVIEW_HEIGHT_IR = previewHeight;
                    initBuff(previewWidth, previewHeight);
                }
                if (nv21IR == null) {
                    return;
                }
                synchronized (nv21IR) {
                    System.arraycopy(data, 0, nv21IR, 0, data.length);
                    isNV21readyIR = true;
                }
                synchronized (mTrackThread) {
                    mTrackThread.notify();
                }
            }
        }

        private void initBuff(int width, int height) {
            nv21IR = new byte[width * height * 2];
            tmpIR = new byte[width * height * 2];
        }
    };

    public CameraHelp.CameraPreviewCallback getPreviewCallback() {
        return previewCallback;
    }


    public int lastFaceTRrackID = -1;

    /**
     * 处理检测出来的人脸信息
     *
     * @param flgFaceChange 0：人脸信息较上一张无变化  1：人脸信息较上一张有变化
     * @return 返回需要跟踪的活体检测人脸id（如果有的话)
     */
    DetectedResult handleFaceInfo(mipsFaceInfoTrack[] info, int faceCount, int flgFaceChange) {
        DetectedResult result = new DetectedResult();
        if (info == null) {
            return result;
        }
        int swipeFaceId = -1;
        int faceWidthMax = 0;  //还未有活体检测结果的最大人脸
        int idx = -1;   //还未有活体检测结果的最大人脸对应的idx
        int faceLivenessMax = 0;  //活体检测不通过的最大人脸
        int idx_liveness = -1;  //活体检测不通过的最大人脸对应的idx
        if (flgFaceChange == 1) { //人脸信息发送变化，重新开始检查人脸数据
            isContinueCheckFace = true;
        }
        for (int i = 0; i < mipsFaceInfoTrack.MAX_FACE_CNT_ONEfRAME && i < faceCount; i++) {
            if (info[i] == null) {
                continue;
            }
            //检测人脸特征库
            if (isContinueCheckFace && swipeFaceId <= 0) {
                //保存人脸图片
                int faceIdxDB = checkFaceIdxDB(info[i]);  //-1：匹配检测中  -2 匹配不通过  >0 匹配到人脸
                if (faceIdxDB > 0 && faceIdxDB != lastSwipeFaceId) {
                    swipeFaceId = faceIdxDB;
                }
                if (faceIdxDB > result.checkStatus) {
                    result.checkStatus = faceIdxDB;
                }
            }
            //已有有效的活体检测的结果(活体检测结束)，则跳过活体检测跟踪
            if (info[i].flgSetLiveness == 1) {
                if (info[i].flgLiveness != 1 && info[i].faceRect.width() > faceLivenessMax) {
                    faceLivenessMax = info[i].faceRect.width();
                    idx_liveness = i;
                }
                continue;
            }
            if (info[i].livenessDetecting == 1) {//1:活体检测中，需要继续输入图像;0:活体检测空闲
                result.trackLivenessID = info[i].FaceTRrackID;
                break;
            }
            if (info[i].faceRect.width() > faceWidthMax) {
                faceWidthMax = info[i].faceRect.width();
                idx = i;
            }
        }
        if (result.checkStatus != -1) { //所有人脸都已经匹配完成时停止检查人脸数据
            isContinueCheckFace = false;
        }
        if (result.trackLivenessID == -1 && idx >= 0) { //没有正在进行的活体检测，则取还未有活体检测结果的最大人脸进行检测
            result.trackLivenessID = info[idx].FaceTRrackID;
        } else if (result.trackLivenessID == -1 && idx_liveness >= 0) { //没有正在进行的活体检测并且都已经有活体检测结果，则取最大的不通过活体检测的人脸
            result.trackLivenessID = info[idx_liveness].FaceTRrackID;
        }
        long lastTime = faceRecordMap.get(swipeFaceId);
        long currTime = System.currentTimeMillis();
        // todo 同一个faceId 间隔时间10秒，防止频繁重刷
        if (swipeFaceId > 0 && currTime - lastTime >= 10 * 1000) { //匹配到与上次不同的人脸时刷卡
            faceRecordMap.put(swipeFaceId, currTime);
            if (swipeByFaceId(swipeFaceId)) {
                lastSwipeFaceId = swipeFaceId;
            } else {
                lastSwipeFaceId = -1;
            }
        } else if (result.checkStatus == -2) { //未匹配到任何人脸
            lastSwipeFaceId = -1;
        }
        return result;
    }

    /**
     * 检测人脸信息是否在人脸特征库中
     *
     * @return 是否在人脸特征库中  //-1：匹配检测中  -2 匹配不通过  >0 匹配到人脸
     */
    private int checkFaceIdxDB(mipsFaceInfoTrack info) {
//        AncdaLog.dToFile("MipsIDFaceProService", "mipsFaceInfoTrack: mfaceSimilarity="
//            + info.mfaceSimilarity + ", mfaceScore=" + info.mfaceScore + ", similaritySet=" + info.similaritySet);
        //活体检测
        if (cameraHelpIR != null && cameraHelpIR.isCameraAvailable()) {
            if (info.livenessDetecting == 1 || info.flgSetLiveness != 1) {//活体检测有效标志(只有为 1，flgLiveness 才是一个有效值)
                return -1;
            }
            if (info.flgLiveness != 1) { //活体检测结果:1:真人，活体检测通过;0:活体检测不通过;
                if (info.flgFaceMotionless != 1) { //人脸非静止，一般flgFaceMotionless==0时
                    return -1;
                } else { //人脸静止，则未匹配到人脸
                    return -2;
                }
            }
        }
        //检测人脸特征库
        if (info.flgSetVIP != 1) { //VIP识别校验完成标志(只有当 flgSetVIP 为 1，FaceIdxDB 才是一个有效值)
            return -1;
        }
        if (info.FaceIdxDB < 0) {
            //人脸运动检测
            if (info.flgFaceMotionless != 1) { //人脸非静止，一般flgFaceMotionless==0时
                return -1;
            } else { //人脸静止，则未匹配到人脸
                return -2;
            }
        }
        return info.FaceIdxDB;
    }

    private boolean swipeByFaceId(int faceId) {
        //检测到特征库中的人脸，特征库人脸id为FaceIdxDB，这里要开始处理刷卡
        try {
            List<FaceData> faceDataList = DBUtils.findAllDataByWhere(FaceData.class, "faceId=" + faceId);
            AncdaLog.dToFile("匹配到的人脸ID:" + faceId);
            if (faceDataList != null && faceDataList.size() > 0) {
                FaceData faceData = faceDataList.get(0);
                String studentId = faceData.getStudentId();
                String teacherId = faceData.getTeacherId();
                if (!TextUtils.isEmpty(studentId) || !TextUtils.isEmpty(teacherId)) {
                    return sendSwipe(faceData.getFaceId(), studentId, teacherId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean sendSwipe(int faceId, String studentId, String teacherId) {
        String[] studentIds = null;
        String[] teacherIds = null;
        if (!TextUtils.isEmpty(studentId)) {
            studentIds = studentId.split(",");
        }
        if (!TextUtils.isEmpty(teacherId)) {
            teacherIds = teacherId.split(",");
        }
        if ((studentIds == null || studentIds.length == 0) && (teacherIds == null || teacherIds.length == 0)) {
            return false;
        }
        List<ScanFaceInfo> scanFaceInfos = new ArrayList<>();
        if (studentIds != null && studentIds.length > 0) {
            for (String id : studentIds) {
                ScanFaceInfo scanFaceInfo = new ScanFaceInfo();
                scanFaceInfo.setId(id);
                scanFaceInfo.setFaceId(faceId);
                scanFaceInfo.setTeacherFace(false);
                scanFaceInfos.add(scanFaceInfo);
            }
        }
        if (teacherIds != null && teacherIds.length > 0) {
            for (String id : teacherIds) {
                ScanFaceInfo scanFaceInfo = new ScanFaceInfo();
                scanFaceInfo.setId(id);
                scanFaceInfo.setFaceId(faceId);
                scanFaceInfo.setTeacherFace(true);
                scanFaceInfos.add(scanFaceInfo);
            }
        }
        int size = scanFaceInfos.size();
        if (size > 0) {
            for (int index = 0; index < size; index++) {
                ScanFaceInfo scanFaceInfo = scanFaceInfos.get(index);
                Intent intent = new Intent("com.ancda.registration.face");
                intent.putExtra("scanFaceInfo", scanFaceInfo);
                sendBroadcast(intent);
                if (size > 1 && index < size - 1) {
                    try {
                        noticeFaceCallback(null); //暂时清空人脸框
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            return true;
        }
        return false;
    }

    public boolean isClearFaceFrame = true;

    private int initDetect(final Context context, String licPath, boolean isLiving) {
        mipsIDFaceManage = MipsIDFaceManage.getInstance();
        if (!mipsIDFaceManage.isInit()) {
            int init = mipsIDFaceManage.init(context, licPath, 1, DeviceInfo.degree, isLiving);

            if (init < 0) {
                return init;
            }
        }
        killed = false;
        mTrackThread = new Thread() {
            @Override
            public void run() {
                while (!killed) {
                    try {
                        if (isNV21ready) {
                            if (cameraHelpIR != null && cameraHelpIR.isCameraAvailable() && !isNV21readyIR) {
                                continue; //双目摄像头可用但是预览准备还未完成
                            }

                            //  FillLightControlV2Util.INSTANCE.TurnOffFillLight();
                            mIsTracking = true;
                            try {
                                synchronized (nv21) {
                                    System.arraycopy(nv21, 0, tmp, 0, nv21.length);
                                }
                                int flgFaceChange = -1;
                                if (cameraHelpIR != null && cameraHelpIR.isCameraAvailable() && isNV21readyIR) {  //双目检测
                                    synchronized (nv21IR) {
                                        System.arraycopy(nv21IR, 0, tmpIR, 0, nv21IR.length);
                                    }
                                    flgFaceChange = mipsIDFaceManage.mipsDetectOneFrame(tmp, PREVIEW_WIDTH, PREVIEW_HEIGHT, tmpIR, PREVIEW_WIDTH_IR, PREVIEW_HEIGHT_IR, mtrackLivenessID);
                                } else { //单目检测
                                    flgFaceChange = mipsIDFaceManage.mipsDetectOneFrame(tmp, PREVIEW_WIDTH, PREVIEW_HEIGHT, mtrackLivenessID);
                                }

                                if (timeBak == 0) {
                                    timeBak = System.currentTimeMillis();
                                }

                                if (System.currentTimeMillis() - timeBak > 500 && isClearFaceFrame) {
                                    noticeFaceCallback(null);
                                    isClearFaceFrame = false;
                                }

                                DetectedResult result = null;
                                if (flgFaceChange != -1) {  //-1：检测失败   0：人脸信息较上一张无变化  1：人脸信息较上一张有变化
                                    int mcntCurFace = mipsIDFaceManage.mipsGetFaceCnt(); //检测到人脸的个数
                                    mFaceInfoDetected = mipsIDFaceManage.mipsGetFaceInfoDetected(); //获取人脸识别返回的人脸信息，可能多个人脸
                                    if (flgFaceChange == 1) {
                                        noticeFaceCallback(mFaceInfoDetected);
                                    }
                                    if (mcntCurFace > 0) {
                                        timeBak = 0;
                                        isClearFaceFrame = true;
                                        result = handleFaceInfo(mFaceInfoDetected, mcntCurFace, flgFaceChange);
                                        mtrackLivenessID = result.trackLivenessID;
                                        if (result.checkStatus > -3) { //结果较上一次有变化，更新状态
                                            lastCheckStatus = result.checkStatus;
                                        }
                                        if (lastCheckStatus == -2) { //-1:匹配中  -2:人脸未匹配
                                            if (lastUnmatchedTime > 0 && System.currentTimeMillis() - lastUnmatchedTime >= timeInterval) {
                                                if (!MyApplication.isUpdateFaceDataStatus()) {
                                                    Intent intent = new Intent("com.ancda.registration.notice");
                                                    intent.putExtra("type", MyEventCode.FACE_UNMATCHED);
                                                    sendBroadcast(intent);

                                                    //复位人脸检测
                                                    resetCheckResult();
                                                    resetCheckStatus();
                                                }
                                                lastUnmatchedTime = System.currentTimeMillis();
                                                timeInterval = 5500;
                                            } else if (lastUnmatchedTime == 0) {
                                                lastUnmatchedTime = System.currentTimeMillis();
                                                timeInterval = 1500;
                                            }
                                        } else {
                                            lastUnmatchedTime = 0;
                                            if (mFaceInfoDetected[0] != null) {
                                                //判断待匹配的人脸宽是否太小
                                                int width = mFaceInfoDetected[0].faceRect.width();
                                                if (lastCheckStatus == -1 && width < MipsIDFaceManage.VIP_DETECT_FACE_WIDTH) {
                                                    EventBus.getDefault().post(new FaceWidthTooSmallEvent(width));
                                                } else if (width < MipsIDFaceManage.DETECT_FACE_WIDTH) { //一般出现在检测到后远离
                                                    //复位人脸检测
                                                    resetCheckResult();
                                                    resetCheckStatus();
                                                }
                                            }
                                        }
                                    } else {
                                        resetCheckStatus();
                                    }

                                } else {
                                    //DBUtils.saveLog("识别检测失败");
                                    resetCheckStatus();
                                }
                            } finally {
                                //恢复状态，准备接收下一帧
                                isNV21ready = false;
                                isNV21readyIR = false;
                            }
                        } else {
                            synchronized (this) {
                                mTrackThread.wait(100); // 数据没有准备好就等待
                            }
                        }
                    } catch (InterruptedException e) {
                        killed = true;  //停止线程工作
                        Thread.currentThread().interrupt();  //恢复中断标志
//                    stopThread();
                    } catch (Exception e) { //出现其他异常
                        e.printStackTrace();
                    } finally {  //恢复状态，准备下一次检测
                        mIsTracking = false;
                    }
                }
                DBUtils.saveLog("Face TrackThread exit");
            }
        };
        mTrackThread.start();
        return 0;
    }


    public final int FINISH_DELAY_TIME = 0x0002;


    private void resetCheckStatus() {
        mFaceInfoDetected = null;
        lastCheckStatus = -3;
        mtrackLivenessID = -1;
        lastUnmatchedTime = 0;
        lastSwipeFaceId = -1;
    }

    private void resetCheckResult() {
        if (mipsIDFaceManage == null || tmp == null) {
            return;
        }
        Arrays.fill(tmp, (byte) 0);
        mipsIDFaceManage.mipsDetectOneFrame(tmp, PREVIEW_WIDTH, PREVIEW_HEIGHT, -1);
    }

    private void noticeFaceCallback(mipsFaceInfoTrack[] faceInfos) {
        if (callbackWeakReference == null) {
            return;
        }
        MipsIDFaceCallback faceCallback = callbackWeakReference.get();
        if (faceCallback != null) {
            faceCallback.onDetectedResult(faceInfos);
        }
    }

    private void stopThread() {
        killed = true;
        if (mTrackThread != null) {
            try {
                mTrackThread.interrupt();
                mTrackThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mTrackThread = null;
        }
        if (mipsIDFaceManage != null) {
            mipsIDFaceManage.unInit();
            mipsIDFaceManage = null;
        }
    }

    public void stopService() {
        stopThread();
        closeCameraIR();
        cameraHelpIR = null;
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void clearTimeCallback(ActivityOnPauseEvent event) {
        timeBak = 0;
        AncdaLog.dToFile("Activty不可见,时间清0");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService();
        //FillLightControlV2Util.INSTANCE.TurnOffFillLight();
        callbackWeakReference.clear();
        callbackWeakReference = null;
    }

    public static class DetectedResult {
        int checkStatus = -3;  //>0:匹配到人脸  -1：匹配中  -2：匹配不通过 3：结果较上一次未改变
        int trackLivenessID = -1;  //后续需要跟踪的活体检测人脸id
//        mipsFaceInfoTrackLiveness faceInfo;  //当前检测到的人脸信息
    }


}