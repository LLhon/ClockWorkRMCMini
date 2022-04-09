package com.palmbaby.lib_stfacedetect;

import android.content.Context;
import com.blankj.utilcode.util.SPUtils;
import com.orhanobut.logger.Logger;
import com.palmbaby.lib_stfacedetect.constants.Constants;
import com.smdt.deviceauth.Auth;
import com.smdt.facesdk.mipsFaceInfoTrack;
import com.smdt.facesdk.mipsFaceVipDB;
import com.smdt.facesdk.mipsVideoFaceTrack;
import java.io.File;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ┏┓　　┏┓+ +
 * 　　　　　　　┏┛┻━━━┛┻┓ + +
 * 　　　　　　　┃　　　　　　　┃
 * 　　　　　　　┃　　　━　　　┃ ++ + + +
 * 　　　　　　 ████━████ ┃+
 * 　　　　　　　┃　　　　　　　┃ +
 * 　　　　　　　┃　　　┻　　　┃
 * HugBio.      ┃　　　　　　　┃ + +
 * 　　　　　　　┗━┓　　　┏━┛
 * 　　　　　　　　　┃　　　┃
 * 　　　　　　　　　┃　　　┃ + + + +
 * 　　　　　　　　　┃　　　┃　　　　Code is far away from bug with the animal protecting
 * 　　　　　　　　　┃　　　┃ + 　　　　神兽保佑,代码无bug
 * 　　　　　　　　　┃　　　┃
 * 　　　　　　　　　┃　　　┃　　+
 * 　　　　　　　　　┃　 　　┗━━━┓ + +
 * 　　　　　　　　　┃ 　　　　　　　┣┓
 * 　　　　　　　　　┃ 　　　　　　　┏┛
 * 　　　　　　　　　┗┓┓┏━┳┓┏┛ + + + +
 * 　　　　　　　　　　┃┫┫　┃┫┫
 * 　　　　　　　　　　┗┻┛　┗┻┛+ + + +
 * <p>
 * 作者： huangbiao
 * 时间： 2019-05-10
 */
public class MipsIDFaceManage {

    private static MipsIDFaceManage instance;
    private mipsVideoFaceTrack mfaceTrackLiveness = null;
    private String VIP_DB_PATH = null;//"/sdcard/mipsfacevip/";
    private final Lock lockFaceDb = new ReentrantLock();
    private final Lock lockFaceInfo = new ReentrantLock();
    private int trackDirection = 0;
    public static int VIP_DETECT_FACE_WIDTH = 180;
    public static int DETECT_FACE_WIDTH = 135;

    public static synchronized MipsIDFaceManage getInstance() {
        if (instance == null) {
            instance = new MipsIDFaceManage();
        }
        return instance;
    }

    private MipsIDFaceManage() {

    }


    /**
     * @param distanceType    人脸追踪距离：
     *                        0: <1m ,
     *                        1: 1~2m ,
     *                        2: 2~4m ,
     *                        3: 4~6m 注意：该参数只对加密芯片类型为“3”的 sdk 有效
     *                        类型可通过 mipsCheckSdkType 获取）
     * @param degree          摄像头画面输入角度，可取值 0,90,180 及 270
     * @param pathMipsLicFile 授权文件存放路径
     * @param isLiving        是否开启活体检测
     *                        2：开启双目活体识别功能
     *                        1：开启单目活体识别功能
     *                        0：关闭活体识别功能
     */
    public int init(final Context context, String pathMipsLicFile, int distanceType, int degree, boolean isLiving) {
        if (mfaceTrackLiveness != null) {
            unInit();
        }

        mfaceTrackLiveness = new mipsVideoFaceTrack();
        int ret = mfaceTrackLiveness.mipsInit(context, distanceType, DeviceInfo.degree, pathMipsLicFile, isLiving ? 2 : 0); // 最后一个参数 0：不开启活体检测，1：开启，开活体检测需要相应的硬件支持和授权
        /*if(isLiving){
            if(mfaceTrackLiveness!=null){
                mfaceTrackLiveness.mipsSetLivenessMode(0);
            }
        }*/
        Logger.e("isLiving:" + isLiving);
        if (ret < 0) {
            mfaceTrackLiveness = null;
            return ret;
        }
        VIP_DB_PATH = context.getFilesDir().getAbsolutePath() + File.separator;
        setTrackDirection();
        mfaceTrackLiveness.mipsSetMaxFaceTrackCnt(1);  //人脸检测最大个数
        float faceThreshold = SPUtils.getInstance().getFloat(Constants.FACE_COMPARED_THRESHOLD, 0.75f);

        mfaceTrackLiveness.mipsSetSimilarityThrehold(faceThreshold);  //设置/获取人脸对比相似度阈值（大于此阈值上报人脸库中与画面中为同一人）
        //人脸宽度
        DETECT_FACE_WIDTH = isLiving ? 90 / 2 : 200 / 2;
        VIP_DETECT_FACE_WIDTH = isLiving ? 90 / 2 : 180 / 2;

        int faceDistance = SPUtils.getInstance().getInt(Constants.FACERECOGNITIONDISTANCE, 0);

        if (faceDistance != 0) {
            mfaceTrackLiveness.mipsSetFaceWidthThrehold(faceDistance);  //设置人脸属性提取、特征提取人脸框宽度的最小像素【默认为：90】。
        } else {
            mfaceTrackLiveness.mipsSetFaceWidthThrehold(DETECT_FACE_WIDTH);  //设置人脸属性提取、特征提取人脸框宽度的最小像素【默认为：90】。
        }
        mfaceTrackLiveness.mipsSetVIPDetectFaceWidthThrehold(VIP_DETECT_FACE_WIDTH);  //人脸实时识别的人脸最小宽
        mfaceTrackLiveness.mipsSetLivenessFaceWidthThrehold(VIP_DETECT_FACE_WIDTH);  //人脸活体检测的人脸最小宽
        mfaceTrackLiveness.mipsSetPicFaceWidthThrehold(80);  //人脸像素阈值设置(推荐 30~200）
        // mfaceTrackLiveness.mipsSetIDProCropFaceBorderwidth(200); //默认200，人脸框边沿到截图边沿的宽度(像素)
        // mfaceTrackLiveness.mipsEnableIDFeatureCropFace(); //特征提取截图
        //人脸检测角度
        mfaceTrackLiveness.mipsSetRollAngle(35); //倾斜角，默认30，建议5-45
        mfaceTrackLiveness.mipsSetYawAngle(35);   //水平转角，默认30，建议5-45
        mfaceTrackLiveness.mipsSetPitchAngle(30);  //俯仰角，默认25，建议5-45
        //通过图片检测的角度，会影响添加人脸库、图片校验、图片特征提取等
        mfaceTrackLiveness.mipsSetPicRollAngle(15);//设置/获取图片校验的倾斜角阈 值（可设置范围 1~89，推荐 5~15，默认：10）
        mfaceTrackLiveness.mipsSetPicYawAngle(25);//设置/获取图片校验的水平转角 阈值（可设置范围 1~89，推荐 5~45，，默认：10）
        mfaceTrackLiveness.mipsSetPicPitchAngle(25);//设置/获取图片校验的俯仰角阈 值（可设置范围 1~89，推荐 5~45，，默认：10）
        //VIP人脸库搜索的角度
        mfaceTrackLiveness.mipsSetVIPRollAngle(25); //推荐5-45，默认20
        mfaceTrackLiveness.mipsSetVIPYawAngle(30); //推荐5-45，默认25
        mfaceTrackLiveness.mipsSetVIPPitchAngle(30); //推荐5-45，默认20

        mfaceTrackLiveness.mipsEnableRefreshFaceRect();  //使能刷新人脸矩形框。调用本函数后，已检测的人脸也会实时刷新人脸矩形框
//        mfaceTrackLiveness.mipsEnableRefreshFaceLiveness();  //即使第一次有结果，会增大系统开销实时刷新活体检测功能，
//        mfaceTrackLiveness.mipsEnableRefreshFaceVIP();  //实时刷新 VIP 校验，即使第一次没匹配到，会增大系统开销
        mfaceTrackLiveness.mipsEnableFaceMoveDetect(); //人脸运动检测，开启时，人脸静止后才会进行特征提取和人脸库匹配，可以提高首次识别率，但会增大识别时间


        mfaceTrackLiveness.mipsSetFaceMoveRitio_THRESHOLD(10);

        int initFaceDB = mfaceTrackLiveness.initFaceDB(context, VIP_DB_PATH + "mipsVipFaceDB", VIP_DB_PATH + "image", 1);
        if (initFaceDB == 0) {
            //0: 未找到人脸库，重新创建;
            Logger.e("未找到人脸库，重新创建");
        } else if (initFaceDB == 1) {
            //1:找到已有人脸库;
            Logger.e("找到已有人脸库");
        } else {
            //其他未知
            Logger.e("初始化人脸库异常，ret：" + initFaceDB);
        }
        mfaceTrackLiveness.mipsEnableVipFaceVerify();  //实时识别开启 VIP人脸库检测（默认关闭）
        return ret;
    }

    public void unInit() {
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsUninit();
        }
        mfaceTrackLiveness = null;
    }

    public boolean isInit() {
        return mfaceTrackLiveness != null;
    }

    public void setCameraDirection(int cameraDegree, boolean isCameraFacingFront) {
        switch (cameraDegree) {
            case 0:
                trackDirection = 0;
                break;
            case 90:
                if (isCameraFacingFront) { //由于前置摄像头预览旋转前做了水平翻转，所以这里需要调换回去，建议使用后置摄像头预览。
                    trackDirection = 3;
                } else {
                    trackDirection = 1;
                }
                break;
            case 180:
                trackDirection = 2;
                break;
            case 270:
                if (isCameraFacingFront) {
                    trackDirection = 1;
                } else {
                    trackDirection = 3;
                }
                break;
        }
        setTrackDirection();
    }

    private void setTrackDirection() {

        if (mfaceTrackLiveness == null) {
            return;
        }
        switch (trackDirection) {
            case 0:
                mipsSetTrackLandscape();
                break;
            case 1:
                mipsSetTrackPortrait();
                break;
            case 2:
                mipsSetTrackReverseLandscape();
                break;
            case 3:
                mipsSetTrackReversePortrait();
                break;
        }
    }

    //================================人脸比对相关方法================================================================

    public int mipsDetectOneFrame(byte[] frameImage, int frame_width, int frame_height, int trackIDLiveness) {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        lockFaceDb.lock();
        int ret = mfaceTrackLiveness.mipsDetectOneFrame(frameImage, frame_width, frame_height, trackIDLiveness);
        lockFaceDb.unlock();
        return ret;
    }

    /**
     * 识别单目摄像头一张图像中人脸的信息
     * 0: 检测到人脸信息较上一张无变化；
     * 1：检测到人脸信息较上一张有变化；
     * -1：检测失败
     */
    public int mipsDetectOneFrame(byte[] frameImage, int frame_width, int frame_height, byte[] frameImageIR, int frame_widthIR, int frame_heightintIR, int trackIDLiveness) {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        lockFaceDb.lock();
        int ret = mfaceTrackLiveness.mipsDetectOneFrame(frameImage, frame_width, frame_height, frameImageIR, frame_widthIR, frame_heightintIR, trackIDLiveness);
        lockFaceDb.unlock();
        return ret;
    }

    public int mipsGetFaceCnt() {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        return mfaceTrackLiveness.mipsGetFaceCnt();
    }

    public mipsFaceInfoTrack[] mipsGetFaceInfoDetected() {
        if (mfaceTrackLiveness == null) {
            return null;
        }
        return mfaceTrackLiveness.mipsGetFaceInfoDetected();
    }

    //=========================================================================================================


    //=========================================参数设置与获取================================================
    //注意：mipsDetectOneFrame 函数应与所有参数设置函数互斥，即保证不同时执行，否则可能导致底层库出错;

    /**
     * 获取设备信息（授权使用）
     * 可以将多台设备获取到的设备信息保存到同一个文件，然后发给我们做授权，拿到授权文件后，放到 mipsInit中 pathMipsLicFile的路径中即可
     */
    public static String mipsGetDeviceInfo(Context context) {
        String deviceInfo = null;

        try {
            deviceInfo = Auth.mipsGetDeviceInfo() + "\n";
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return deviceInfo;
    }

    /**
     * 设置摄像头横屏
     */
    public void mipsSetTrackLandscape() {
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetTrackLandscape();
        }
    }

    /**
     * 设置摄像头竖屏
     */
    public void mipsSetTrackPortrait() {
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetTrackPortrait();
        }
    }

    /**
     * 设置摄像头反向横屏
     */
    public void mipsSetTrackReverseLandscape() {
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetTrackReverseLandscape();
        }
    }

    /**
     * 设置摄像头反向竖屏
     */
    public void mipsSetTrackReversePortrait() {
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetTrackReversePortrait();
        }
    }

    //设置人脸检测倾斜角，推荐5-45
    public void mipsSetRollAngle(float threhold) {
        lockFaceDb.lock();
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetRollAngle(threhold);
        }
        lockFaceDb.unlock();
    }

    //获取人脸检测倾斜角
    public float mipsGetRollAngle() {
        if (mfaceTrackLiveness == null) {
            return -1.0f;
        }
        return mfaceTrackLiveness.mipsGetRollAngle();
    }

    //设置人脸检测的水平转角，推荐5-45
    public void mipsSetYawAngle(float threhold) {
        lockFaceDb.lock();
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetYawAngle(threhold);
        }
        lockFaceDb.unlock();
    }

    //获取人脸检测的水平转角
    public float mipsGetYawAngle() {
        if (mfaceTrackLiveness == null) {
            return -1.0f;
        }
        return mfaceTrackLiveness.mipsGetYawAngle();
    }

    //设置人脸检测的俯仰角，推荐5-45
    public void mipsSetPitchAngle(float threhold) {
        lockFaceDb.lock();
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetPitchAngle(threhold);
        }
        lockFaceDb.unlock();
    }

    //获取人脸检测的俯仰角
    public float mipsGetPitchAngle() {
        if (mfaceTrackLiveness == null) {
            return -1.0f;
        }
        return mfaceTrackLiveness.mipsGetPitchAngle();
    }

    //设置人脸跟踪人脸置信度阈值(可设置范围 0.1~1.0，推荐 0.9~0.99)
    public void mipsSetFaceScoreThrehold(float threhold) {
        lockFaceDb.lock();
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetFaceScoreThrehold(threhold);
        }
        lockFaceDb.unlock();
    }

    //获取人脸跟踪人脸置信度阈值
    public float mipsGetFaceScoreThrehold() {
        if (mfaceTrackLiveness == null) {
            return -1.0f;
        }
        return mfaceTrackLiveness.mipsGetFaceScoreThrehold();
    }

    //设置人脸对比相似度阈值，可设置范围 0.1~1.0， 推荐 0.5~0.8
    public void mipsSetSimilarityThrehold(float threhold) {
        lockFaceDb.lock();
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetSimilarityThrehold(threhold);
        }
        lockFaceDb.unlock();
    }

    //获取人脸对比相似度阈值
    public float mipsGetSimilarityThrehold() {
        if (mfaceTrackLiveness == null) {
            return -1.0f;
        }
        return mfaceTrackLiveness.mipsGetSimilarityThrehold();
    }

    //设置照片人脸检测置信度阈值，可设置范围 0.1~1.0，推 荐 0.9~0.99
    public void mipsSetVerifyScoreThrehold(float threhold) {
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetVerifyScoreThrehold(threhold);
        }
    }

    //获取照片人脸检测置信度阈值
    public float mipsGetVerifyScoreThrehold() {
        if (mfaceTrackLiveness == null) {
            return -1.0f;
        }
        return mfaceTrackLiveness.mipsGetVerifyScoreThrehold();
    }

    //设置活体检测人脸最小宽度，推荐30-200
    public void mipsSetLivenessFaceWidthThrehold(int width) {
        if (mfaceTrackLiveness == null) {
            return;
        }
        mfaceTrackLiveness.mipsSetLivenessFaceWidthThrehold(width);
    }

    //获取活体检测人脸最小宽度
    public int mipsGetLivenessFaceWidthThrehold() {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        return mfaceTrackLiveness.mipsGetLivenessFaceWidthThrehold();
    }

    //设置人脸检测最小人脸框大小，推荐30-200
    public void mipsSetFaceWidthThrehold(int width) {
        if (mfaceTrackLiveness == null) {
            return;
        }
        mfaceTrackLiveness.mipsSetFaceWidthThrehold(width);
    }

    //获取人脸检测最小人脸框大小
    public int mipsGetFaceWidthThrehold() {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        return mfaceTrackLiveness.mipsGetFaceWidthThrehold();
    }

    //设置照片人脸检测人脸最小宽度，推荐30-200
    public void mipsSetPicFaceWidthThrehold(int width) {
        if (mfaceTrackLiveness == null) {
            return;
        }
        mfaceTrackLiveness.mipsSetPicFaceWidthThrehold(width);
    }

    //获取照片人脸检测人脸最小宽度
    public int mipsGetPicFaceWidthThrehold() {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        return mfaceTrackLiveness.mipsGetPicFaceWidthThrehold();
    }

    //mipsGetVIPDetectFaceWidthThrehold  设置VIP实时检测人脸最小宽度，推荐30-200


    //设置单张图片识别的最大人脸数，可设置范围 1~32，推荐 4
    public void mipsSetMaxFaceTrackCnt(int cnt) {
        lockFaceDb.lock();
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsSetMaxFaceTrackCnt(cnt);
        }
        lockFaceDb.unlock();
    }

    //获取单张图片识别的最大人脸数
    public int mipsGetMaxFaceTrackCnt() {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        return mfaceTrackLiveness.mipsGetMaxFaceTrackCnt();
    }

    //====================================================================================================


    //=====================================实时识别设置===============================================
    //使能VIP人脸验证（默认关闭）
    public void mipsEnableVipFaceVerify() {
        lockFaceDb.lock();
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsEnableVipFaceVerify();
        }
        lockFaceDb.unlock();
    }

    //禁止VIP人脸验证（默认关闭）
    public void mipsDisableVipFaceVerify() {
        lockFaceDb.lock();
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsDisableVipFaceVerify();
        }
        lockFaceDb.unlock();
    }

    //获取VIP人脸验证状态
    public int mipsGetVipFaceVerifyState() {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        return mfaceTrackLiveness.mipsGetVipFaceVerifyState();
    }

    /**
     * 实时识别开启活体检测，默认开启
     */
    public void mipsEnableFaceLiveness() {
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsEnableFaceLiveness();
        }
    }

    /**
     * 实时识别关闭活体检测，默认开启
     */
    public void mipsDisableFaceLiveness() {
        if (mfaceTrackLiveness != null) {
            mfaceTrackLiveness.mipsDisableFaceLiveness();
        }
    }

    /**
     * 获取实时识别是否开启活体检测状态
     */
    public int mipsGetFaceAttrState() {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        return mfaceTrackLiveness.mipsGetFaceLivenessState();
    }

    /**
     * 开启实时刷新活体检测功能（默认关闭），会增大开销，不建议开启
     */
//    public void mipsEnableRefreshFaceAttr()
//    {
//        if(mfaceTrackLiveness != null) {
//            mfaceTrackLiveness.mipsEnableRefreshFaceLiveness();
//        }
//    }
    /**
     * 关闭实时刷新活体检测功能（默认关闭），会增大开销，不建议开启
     */
//    public void mipssDisableRefreshFaceAttr()
//    {
//        if(mfaceTrackLiveness != null) {
//            mfaceTrackLiveness.mipsDisableRefreshFaceLiveness();
//        }
//    }

    /**
     * 获取实时刷新活体检测功能状态，会增大开销，不建议开启
     */
//    public int mipsGetRefreshFaceAttrState()
//    {
//        if(mfaceTrackLiveness == null) {
//            return -1;
//        }
//        return mfaceTrackLiveness.mipsGetRefreshFaceLivenessState();
//    }


    /**
     * 开启实时刷新 VIP校验功能（默认关闭），会增大开销，不建议开启
     */
//    public void mipsEnableRefreshFaceVIP()
//    {
//        if(mfaceTrackLiveness != null) {
//            mfaceTrackLiveness.mipsEnableRefreshFaceVIP();
//        }
//    }

    /**
     * 关闭实时刷新 VIP校验功能（默认关闭），会增大开销，不建议开启
     */
//    public void mipsDisableRefreshFaceVIP()
//    {
//        if(mfaceTrackLiveness != null) {
//            mfaceTrackLiveness.mipsDisableRefreshFaceVIP();
//        }
//    }

    /**
     * 获取实时刷新 VIP校验功能状态（默认关闭），会增大开销，不建议开启
     */
//    public int mipsGetRefreshFaceVIPState()
//    {
//        if(mfaceTrackLiveness == null) {
//            return -1;
//        }
//        return mfaceTrackLiveness.mipsGetRefreshFaceVIPState();
//    }
    //====================================================================================================

    //=========================================人脸库操作==================================================
    // 在初次批量创建人脸库的过程中，最好将人脸库检测功能禁止(调用 mipsDisableVipFaceVerify)，
    // 初始化完成后再开启 VIP 人脸库识别(调用 mipsEnableVipFaceVerify);
    // 在之后的单张添加中无 需有此步骤。

    /**
     * 校验图片质量是否可以作为人脸库输入图片
     *
     * @param imagePath 待校验的一组图片路径
     * @param imageCnt  待校验的图片个数
     * @return >=0 图片质量最佳的索引   -1 校验失败，没有可用的图片
     */
    public int mipsVerifyVipImage(String[] imagePath, int imageCnt) {
        if (imagePath == null) {
            return -1;
        }
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        lockFaceInfo.lock();
        int ret = mfaceTrackLiveness.verifyVipImage(imagePath, imageCnt);
        lockFaceInfo.unlock();

        return ret;
    }

    //获取人脸库的总人脸数
    public int mipsGetDbFaceCnt() {
        if (mfaceTrackLiveness == null) {
            return 0;
        }
        return mfaceTrackLiveness.mipsGetDbFaceCnt();
    }

    //添加一个人脸到人脸库,目前特征库可添加的人脸最大大概是2万个
    public int mipsAddVipFace(Context context, int idx, String imagePath) {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        if (idx < 0) {
            return -1;
        }
        lockFaceInfo.lock();
        int ret = 0;
        try {
            mipsFaceVipDB faceVipDB = new mipsFaceVipDB(imagePath, idx);
            ret = mfaceTrackLiveness.addOneFaceToDB(context, faceVipDB);
        } catch (Exception e) {
            e.printStackTrace();
            ret = -1;
        } finally {
            lockFaceInfo.unlock();
        }
        return ret;
    }

    //从人脸库删除一个人脸
    public int mipsDeleteVipFace(Context context, int idxInFaceDB) {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        if (idxInFaceDB < 0) {
            return -1;
        }
        lockFaceInfo.lock();
        int ret = 0;
        try {
            ret = mfaceTrackLiveness.deleteOneFaceFrDB(context, idxInFaceDB);
        } catch (Exception e) {
            e.printStackTrace();
            ret = -1;
        } finally {
            lockFaceInfo.unlock();
        }
        return ret;
    }

    //从人脸库删除所有人脸
    public int mipsDeleteAllFaceFrDB(Context context) {
        if (mfaceTrackLiveness == null) {
            return -1;
        }
        lockFaceInfo.lock();
        int ret = 0;
        try {
            ret = mfaceTrackLiveness.deleteAllFaceFrDB(context);
        } catch (Exception e) {
            e.printStackTrace();
            ret = -1;
        } finally {
            lockFaceInfo.unlock();
        }
        return ret;
    }


}
