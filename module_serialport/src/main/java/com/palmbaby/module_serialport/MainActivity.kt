package com.palmbaby.module_serialport

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.android.arouter.facade.annotation.Route
import com.google.android.material.button.MaterialButton
import com.palmbaby.lib_common.support.Constants
import com.palmbaby.lib_serialport.OnSerialPortListener
import com.palmbaby.lib_serialport.SdkHelper
import com.palmbaby.module_serialport.adapter.MsgAdapter

@Route(path = Constants.PATH_SERIALPORT)
class MainActivity : AppCompatActivity(), OnSerialPortListener {

    private val TAG = "MainActivity"
    private lateinit var mBtnOpen: MaterialButton
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mAdapter: MsgAdapter
    private val mMsgList = mutableListOf<String>()

    companion object {
        init {
            try{
                System.loadLibrary("SerialPort")
            }catch (e: Exception){
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_serialport)

        mBtnOpen = findViewById(R.id.btnOpen)
        mRecyclerView = findViewById(R.id.rv)

        SdkHelper.init()

        mBtnOpen.setOnClickListener {
            SdkHelper.openSerialPort(this)
//            SdkHelper.getDeviceController().heartBeat()
        }

        mAdapter = MsgAdapter(R.layout.item_msg, mMsgList)
        mRecyclerView.adapter = mAdapter
    }

    override fun onOpenSuccess() {
        Log.d(TAG, "串口打开成功！")
        runOnUiThread {
            mAdapter.addData("串口打开成功！")
        }
    }

    override fun onOpenFail(errorMsg: String) {
        Log.e(TAG, "串口打开失败：$errorMsg")
        runOnUiThread {
            mAdapter.addData("串口打开失败：$errorMsg")
        }
    }

    override fun onElectricity(percent: Int) {
        runOnUiThread {
            Log.e(TAG, "电池电量：$percent")
            runOnUiThread {
                mAdapter.addData("电池电量：$percent")
                mRecyclerView.smoothScrollToPosition(mAdapter.data.size - 1)
            }
        }
    }

    override fun onChargeType(chargeType: Int) {
        runOnUiThread {
            val chargeTypeStr = if (chargeType == 1) {
                "快充"
            } else if (chargeType == 2) {
                "慢充"
            } else if (chargeType == 3) {
                "涓流充"
            } else {
                ""
            }
            Log.e(TAG, "充电方式：$chargeTypeStr")
            runOnUiThread {
                mAdapter.addData("充电方式：$chargeTypeStr")
                mRecyclerView.smoothScrollToPosition(mAdapter.data.size - 1)
            }
        }
    }

    override fun onDisinfectStatus(status: Boolean) {
        runOnUiThread {
            val disinfectStatus = if (status) {
                "已消毒"
            } else {
                "未消毒"
            }
            Log.e(TAG, "右手消毒液喷洒状态：$disinfectStatus")
            runOnUiThread {
                mAdapter.addData("右手消毒液喷洒状态：$disinfectStatus")
                mRecyclerView.smoothScrollToPosition(mAdapter.data.size - 1)
            }
        }
    }

    override fun onLiquidMargin(margin: Int) {
        runOnUiThread {
            Log.e(TAG, "消毒液余量状态：$margin")
            runOnUiThread {
                mAdapter.addData("消毒液余量状态：$margin")
                mRecyclerView.smoothScrollToPosition(mAdapter.data.size - 1)
            }
        }
    }

    override fun onTemperature(temperature: Float) {
        runOnUiThread {
            Log.e(TAG, "左手测温温度：$temperature")
            runOnUiThread {
                mAdapter.addData("左手测温温度：$temperature")
                mRecyclerView.smoothScrollToPosition(mAdapter.data.size - 1)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SdkHelper.closeSerialPort()
    }
}