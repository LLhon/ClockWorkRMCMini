package com.palmbaby.lib_serialport

/**
 *
 * Created by LLhon on 2022/4/6 11:17.
 */
interface OnSerialPortListener {

    fun onOpenSuccess()

    fun onOpenFail(errorMsg: String)

    /**
     * 电池电量
     * 百分比：99
     */
    fun onElectricity(percent: Int)

    /**
     * 充电方式：1 快充  2 慢充  3 涓流充
     */
    fun onChargeType(chargeType: Int)

    /**
     * 消毒液余量
     * 百分比：50
     */
    fun onLiquidMargin(margin: Int)

    /**
     * 右手消毒液喷洒状态: true 已消毒  false 未消毒
     */
    fun onDisinfectStatus(status: Boolean)

    /**
     * 左手测温温度: 37.5
     */
    fun onTemperature(temperature: Float)
}