package com.palmbaby.lib_common.base

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.palmbaby.lib_common.support.StatusBar
import com.palmbaby.lib_common.widget.LoadingDialog

abstract class BaseActivity : AppCompatActivity {

    constructor() : super()

    private lateinit var mLoadingDialog: LoadingDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        StatusBar().fitSystemBar(this)
        mLoadingDialog = LoadingDialog(this, false)
        initData(savedInstanceState)

    }

    override fun onDestroy() {
        super.onDestroy()
    }

    abstract fun initData(savedInstanceState: Bundle?)

    /**
     * show 加载中
     */
    fun showLoading() {
        mLoadingDialog.showDialog(this, false)
    }

    /**
     * dismiss loading dialog
     */
    fun dismissLoading() {
        mLoadingDialog.dismissDialog()
    }


    /**
     * 设置toolbar名称
     */
    protected fun setToolbarTitle(view: TextView, title: String) {
        view.text = title
    }

    /**
     * 设置toolbar返回按键图片
     */
    protected fun setToolbarBackIcon(view: ImageView, id: Int) {
        view.setBackgroundResource(id)
    }
}