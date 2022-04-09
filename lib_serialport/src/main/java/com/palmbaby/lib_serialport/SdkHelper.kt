package com.palmbaby.lib_serialport

/**
 *
 * Created by LLhon on 2022/4/6 14:21.
 */
object SdkHelper {

    private lateinit var mDeviceController: DeviceController

    fun init() {
        mDeviceController = DeviceController()
    }

    fun getDeviceController() : DeviceController = mDeviceController

    fun openSerialPort(listener: OnSerialPortListener) {
        mDeviceController.openSerialPort(listener)
    }

    fun closeSerialPort() {
        mDeviceController.closeSerialPort()
    }
}