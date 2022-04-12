package com.palmbaby.lib_serialport

import android.os.Handler
import android.os.Message
import android.text.TextUtils
import android.util.Log
import com.kongqw.serialportlibrary.SerialPortManager
import com.kongqw.serialportlibrary.listener.OnOpenSerialPortListener
import com.kongqw.serialportlibrary.listener.OnSerialPortDataListener
import com.palmbaby.lib_common.utils.ByteUtil
import java.io.File
import java.lang.Exception

/**
 * 串口通信协议封装
 * Created by LLhon on 2022/4/6 11:13.
 */
class DeviceController : IController, OnOpenSerialPortListener, OnSerialPortDataListener {

    private val TAG = "DeviceController"
    private var mSerialPortManager: SerialPortManager? = null
    private var mOnSerialPortListener: OnSerialPortListener? = null
    private var mHandler: HeartBeatHandler? = null
    private val MSG_WHAT_HEARTBEAT = 0x01
    private val CMD_HEART_BEAT = "AF02FB0157"
    private val CMD_RESPOND = "AF03FA010750"

    override fun openSerialPort(listener: OnSerialPortListener) {
        mOnSerialPortListener = listener
        if (mSerialPortManager == null) {
            mSerialPortManager = SerialPortManager()
            mSerialPortManager?.setOnOpenSerialPortListener(this)
            mSerialPortManager?.setOnSerialPortDataListener(this)
            val file = File("/dev/ttyS1")
            if (file.exists()) {
                mSerialPortManager?.openSerialPort(file, 9600)
            } else {
                mOnSerialPortListener?.onOpenFail("/dev/ttyS1 串口不存在")
            }
            mHandler = HeartBeatHandler()
        }
    }

    override fun closeSerialPort() {
        mHandler?.removeCallbacksAndMessages(null)
        mSerialPortManager?.closeSerialPort()
    }

    override fun onSuccess(p0: File?) {
        mOnSerialPortListener?.onOpenSuccess()
    }

    override fun onFail(p0: File?, p1: OnOpenSerialPortListener.Status?) {
        mOnSerialPortListener?.onOpenFail("读卡器串口打开失败: ${p1?.toString()}")
    }

    override fun onDataReceived(bytes: ByteArray) {
        var result = ByteUtil.bytes2HexStr2(bytes)
        //AF0976184055AF0976184055AF0976184055AF0976184055
        //AF0976184055AF0976184055AF0976184055AF0976184055
        Log.i(TAG, "收到回复：$result")

        // TODO: test
        val list = mutableListOf<String>()
        dealDataReceived(list, result)
        for ((index, value) in list.withIndex()) {
            result = list[index]
//            var dealBytes = ByteUtil.hexStr2bytes(result)
            Log.i(TAG, "收到回复解析>>>>> $index:$result")
            if (result.startsWith("AF")) {
                try {
                    //AF 03 02 1F 0C BD
                    //AF 03 06 20 03 89
                    val subString = result.substring(0, 6)
                    val len = ByteUtil.hexStr2decimal(result.substring(2, 4))
                    val data = ByteArray(len.toInt())
                    System.arraycopy(bytes, 4, data, 0, data.size)
                    when (subString) {
                        "AF0201" -> {
                            //系统启动信息
                            Log.d(TAG, "系统启动信息：$subString")

                        }
                        "AF0302" -> {
                            //电池电量信息 AF03021F0CBD
                            Log.d(TAG, "电池电量信息：$subString")
                            val electricity = ByteUtil.hexStr2Algorism(ByteUtil.byte2Hex(data[1]))
                            mOnSerialPortListener?.onElectricity(electricity)
                        }
                        "AF0303" -> {
                            //外部电源供电状态
                            Log.d(TAG, "外部电源供电状态：$subString")
                        }
                        "AF0304" -> {
                            //当前充电状态 AF0304010CA5
                            val chargeType = ByteUtil.byte2Hex(data[1])
                            if (chargeType == "01") {
                                //快充
                                mOnSerialPortListener?.onChargeType(1)
                            } else if (chargeType == "02") {
                                //慢充
                                mOnSerialPortListener?.onChargeType(2)
                            } else if (chargeType == "03") {
                                //涓流充
                                mOnSerialPortListener?.onChargeType(3)
                            }
                        }
                        "AF0205" -> {
                            //消毒液余量状态
                            val liquidMargin = ByteUtil.hexStr2Algorism(ByteUtil.byte2Hex(data[1]))
                            mOnSerialPortListener?.onLiquidMargin(liquidMargin)
                        }
                        "AF0306" -> {
                            //左手测温温度
                            val temperatureInt = ByteUtil.hexStr2Algorism(ByteUtil.byte2Hex(data[1]))
                            val temperatureDecimal = ByteUtil.hexStr2Algorism(ByteUtil.byte2Hex(data[2]))
                            val temperature = "$temperatureInt.$temperatureDecimal"
                            mOnSerialPortListener?.onTemperature(temperature.toFloat())
                        }
                        "AF0207" -> {
                            //右手消毒液喷洒状态
                            val disinfectStatus = ByteUtil.byte2Hex(data[1])
                            if (disinfectStatus == "01") {
                                //已消毒
                                mOnSerialPortListener?.onDisinfectStatus(true)
                            } else {
                                //未消毒
                                mOnSerialPortListener?.onDisinfectStatus(false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                Log.e(TAG, "ddcc收到错误数据")
            }
        }
    }

    private fun dealDataReceived(list: MutableList<String>, result: String) : MutableList<String> {
        var nextResult = ""
        var end = 0
        try {
            //AF01FA50
            //AF 03 FA 01 07 50
            //数据格式：帧头+数据区长度+命令码+数据+校验码
            //校验逻辑：先取出数据区长度，根据数据区长度算出总长度，再截取出完整的数据
            nextResult = result.substring(2, 4)
            val len = Integer.parseInt(nextResult, 16)
            end = 6 + len * 2
            val order = result.substring(0, end)
            list.add(order)
        } catch (e: Exception) {
            e.printStackTrace()
            return list
        }
        return if (result.length > end) {
            nextResult = result.substring(end)
            dealDataReceived(list, nextResult)
        } else {
            list
        }
    }

    override fun onDataSent(bytes: ByteArray) {
        Log.i(TAG, "send bytes: ${bytes.size}")
    }

    private fun sendCmd(cmd: String) {
        Log.i(TAG, "发送指令：$cmd")
        val bytes = ByteUtil.hexStr2bytes(cmd)
        mSerialPortManager?.sendBytes(bytes)
    }

    fun heartBeat() {
        mHandler?.sendEmptyMessageDelayed(MSG_WHAT_HEARTBEAT, 60 * 1000)
    }

    inner class HeartBeatHandler : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MSG_WHAT_HEARTBEAT ->  {
                    Log.i(TAG, "ccdd发送心跳指令")
                    sendCmd(CMD_HEART_BEAT)
                    mHandler?.sendEmptyMessageDelayed(MSG_WHAT_HEARTBEAT, 60 * 1000)
                }
            }
        }
    }

    fun sendRespond() {
        Log.i(TAG, "ccdd发送应答回应指令")
        sendCmd(CMD_RESPOND)
    }
}