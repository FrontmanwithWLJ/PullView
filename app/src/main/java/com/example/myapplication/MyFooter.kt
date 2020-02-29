package com.example.myapplication

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.logging.Handler

class MyFooter(private val context:Context, offset:Int) : PullView.FooterAdapter(offset) {
    private lateinit var view:View
    private lateinit var content :TextView
    private lateinit var img :ImageView
    private lateinit var callBack: PullView.PullCallBack
    override fun getFooterView(viewGroup: ViewGroup): View {
        view = LayoutInflater.from(context).inflate(R.layout.header,viewGroup,false)
        content = view.findViewById(R.id.text)
        img = view.findViewById(R.id.icon)
        return view
    }

    override fun scrollProgress(progress: Int) {}
    override fun setPullCallBack(callBack: PullView.PullCallBack) {
        this.callBack = callBack
    }

    override fun pullToLoad() {
        content.text = "继续上拉加载更多"
    }

    override fun releaseToLoad() {
        content.text = "释放加载"
    }

    override fun loading() {
        content.text = "正在加载"
        GlobalScope.launch {
            delay(2500)
            Thread {
                content.text = "加载完成"
            }.run()
            callBack.over(true)
        }
    }
}