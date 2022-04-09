package com.palmbaby.lib_serialport

/**
 *
 * Created by LLhon on 2022/4/6 11:14.
 */
interface IController {

    fun openSerialPort(listener: OnSerialPortListener)

    fun closeSerialPort()
}