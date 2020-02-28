package com.example.myapplication

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

//

class MyScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr){
    private var isDecrement = true
    private var damping = 0.5f
    private var dampingTemp = 0.5f
    private var moveOffset = 0f
    private var animTime = 300L
    private var isFling = false

    override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean {
        Log.e("sl","scroll =$scrollY max = $maxOverScrollY range=$maxOverScrollY")
        if ((moveOffset < 0f && deltaY >0) || (moveOffset > 0f && deltaY < 0)){
            var offset = deltaY*damping
            if (offset<0){
                if (-offset > moveOffset)
                    offset = -moveOffset
            }else{
                if (-offset < moveOffset)
                    offset = -moveOffset
            }
            y -= offset
            moveOffset += offset
            //返回true就不会对内容进行滑动
            return true
        }
        //canScrollVertically(1)滑动到底部返回false，canScrollVertically(-1)滑动到顶部返回false
        if ((!canScrollVertically(-1) && deltaY < 0) || (!canScrollVertically(1) && deltaY > 0 )){//下拉和上滑
            val offset = deltaY*dampingTemp
            y -= offset
            moveOffset+=offset
            if (isDecrement){
                calcDamping()
            }
        }
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        when(checkNotNull(ev).action){
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL->{
                if (moveOffset != 0f)
                    springBack()
                if (isFling){
                    GlobalScope.launch {
                        var count = 0
                        var oy = y
                        delay(200)
                        while (y != oy){
                            count++
                            oy = y
                            if (count == 15)//超过3秒就取消
                                break
                            delay(200)
                        }
                        if (y==oy && moveOffset!=0f){
                            isFling = false
                            springBack()
                        }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun setDamping(damping:Float,isDecrement:Boolean = true){
        this.damping = damping
        this.dampingTemp = damping
        this.isDecrement = isDecrement
    }
    fun setAnimTime(time:Long){
        animTime = time
    }
    private fun calcDamping(){
        //val offset = abs(moveOffset).toDouble()
        //双曲正切函数(e^x-e^(-x))/(e^x+e^(-x)),随着x递增，y从零开始增加无限趋近于0
        //dampingTemp = damping * (1-((exp(offset) - exp(-offset))/(exp(offset) + exp(-offset)))).toFloat()
        var count = (abs(moveOffset)/40).toInt()
        if (count==0){
            count=1
        }
        dampingTemp = damping/count
        Log.e("SL","damping = $dampingTemp")
    }

    //@Synchronized
    private fun springBack(){
        val animation = TranslateAnimation(0f,0f,-moveOffset,0f)
        animation.duration = animTime
        setAnimation(animation)
        startAnimation(animation)
        y += moveOffset
        moveOffset = 0f
        dampingTemp = damping
    }

    /**
     * 将自身放在父布局的最底层
     * 把父布局所有的view全部置顶
     */
    fun sendToBack(){
        val root = parent
        if (root is ViewGroup){
            for (i in 0 until root.childCount){
                root.getChildAt(i).bringToFront()
            }
        }
    }
}