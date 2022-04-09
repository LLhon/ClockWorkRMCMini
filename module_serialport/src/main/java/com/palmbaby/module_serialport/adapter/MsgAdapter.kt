package com.palmbaby.module_serialport.adapter

import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.palmbaby.module_serialport.R

/**
 *
 * Created by LLhon on 2022/4/7 10:37.
 */
class MsgAdapter(layoutResId: Int, data: MutableList<String>?) :
    BaseQuickAdapter<String, BaseViewHolder>(layoutResId, data) {

    override fun convert(holder: BaseViewHolder, item: String) {
        holder.setText(R.id.tv, item)
    }
}