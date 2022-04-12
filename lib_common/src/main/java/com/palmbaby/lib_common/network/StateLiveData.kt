package com.palmbaby.lib_common.network

import androidx.lifecycle.MutableLiveData


/**
 * @date：2021/6/11
 * @author fuusy
 * @instruction：MutableLiveData,用于将请求状态分发给UI
 */
class StateLiveData<T> : MutableLiveData<BaseResp<T>>() {
}