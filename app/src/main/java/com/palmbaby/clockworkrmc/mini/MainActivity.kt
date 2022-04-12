package com.palmbaby.clockworkrmc.mini

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.alibaba.android.arouter.launcher.ARouter
import com.google.android.material.button.MaterialButton
import com.palmbaby.lib_common.support.Constants

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<MaterialButton>(R.id.btnSerialPort).setOnClickListener {
            //串口通信模块
            ARouter.getInstance().build(Constants.PATH_SERIALPORT)
                .navigation()
        }
        findViewById<MaterialButton>(R.id.btnFaceDetect).setOnClickListener {
            //人脸识别模块
            ARouter.getInstance().build(Constants.PATH_FACEDETECT)
                .navigation()
        }
    }
}