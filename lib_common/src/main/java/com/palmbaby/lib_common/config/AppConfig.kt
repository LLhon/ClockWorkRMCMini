package com.palmbaby.lib_common.config

import android.app.Application

/**
 * 全局配置类
 * 生命周期和 Application 一样
 * Created by LLhon on 2022/4/11 10:11.
 */
object AppConfig {

    lateinit var application: Application
    //人脸sdk初始化状态，-1：还未初始化   0：初始化成功   1：初始化失败
    var faceSDKInitStatus = -1
    var isFaceService = true
    //是否正在更新人脸数据
    var isUpdateFaceData = false

    fun initConfig(app: Application) {
        application = app
    }
}