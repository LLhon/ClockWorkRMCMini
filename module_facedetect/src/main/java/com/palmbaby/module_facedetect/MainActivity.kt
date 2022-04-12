package com.palmbaby.module_facedetect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.Camera
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.alibaba.android.arouter.facade.annotation.Route
import com.orhanobut.logger.Logger
import com.palmbaby.lib_common.support.Constants
import com.palmbaby.lib_stfacedetect.CameraHelp
import com.palmbaby.lib_stfacedetect.MipsIDFaceCallback
import com.palmbaby.lib_stfacedetect.MipsIDFaceManage
import com.palmbaby.lib_stfacedetect.MipsIDFaceProService
import com.palmbaby.lib_stfacedetect.widget.FaceCanvasView

@Route(path = Constants.PATH_FACEDETECT)
class MainActivity : AppCompatActivity(), MipsIDFaceCallback {

    private lateinit var mSurfaceView: SurfaceView
    private lateinit var mFaceFrameView: FaceCanvasView
    private lateinit var mCameraHelp: CameraHelp
    private var isCanTakePicture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facedetect)

        mSurfaceView = findViewById(R.id.surfaceView)
        mFaceFrameView = findViewById(R.id.faceFrameView)

        initCamera()

        val service = Intent(this, MipsIDFaceProService::class.java)
        startService(service)
        bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initCamera() {
        Log.d("MainActivity", "initCamera")
        mCameraHelp = CameraHelp(mSurfaceView, object : CameraHelp.CameraCreatedListener {
            override fun onCreatedSucceed(
                camera: Camera?,
                holder: SurfaceHolder?,
                cameraId: Int,
                isCameraFacingFront: Boolean,
                isMonocular: Boolean
            ) {
                camera?.let {
                    isCanTakePicture = true
                    val previewSize = camera.parameters.previewSize
                    mFaceFrameView.setCameraPreviewSize(previewSize.width, previewSize.height, 0, isMonocular)
                    mFaceFrameView.setCameraFacingFront(isCameraFacingFront)
                    //设置人脸识别摄像头方向
                    MipsIDFaceManage.getInstance().setCameraDirection(0, isCameraFacingFront)
                }
            }

            override fun onCreatedErr(cameraId: Int) {
                //摄像头异常
                Logger.e("摄像头异常：cameraId=$cameraId")
                isCanTakePicture = false

            }

            override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
                isCanTakePicture = false
            }
        })
        mCameraHelp.setDisplayRotation(windowManager.defaultDisplay.rotation)
        mCameraHelp.initSurfaceView()
    }

    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MipsIDFaceProService.Binder
            mCameraHelp.setCameraPreviewCallback(binder.service.previewCallback)
            binder.service.setFaceCallback(this@MainActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mCameraHelp.setCameraPreviewCallback(null)
        }

    }

    override fun onDetectedResult(faceInfos: Array<out com.smdt.facesdk.mipsFaceInfoTrack>?) {
        mFaceFrameView.addFacesFrames(faceInfos)

    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mServiceConnection)
        mCameraHelp.setCameraPreviewCallback(null)
        mCameraHelp.stopPreviewAndRelease()
    }
}