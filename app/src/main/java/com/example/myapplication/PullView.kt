package com.example.myapplication

/**
 * PullView 布局内仅有一个View。headerView和footerView用户设置适配器
 */
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.LinearLayout
import androidx.core.view.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class PullView : LinearLayout {
    private val TAG = "PullView"
    //down事件的记录
    private var oldY = 0f
    //目标子view
    private val middleView: View by lazy {
        if (childCount >= 2)
            getChildAt(1)
        else
            getChildAt(0)
    }
    //header
    private var headerView: View? = null
    private var headerAdapter: HeaderAdapter? = null
    //footer
    private var footerView: View? = null
    private var footerAdapter: FooterAdapter? = null
    //记录自身的位移偏移量
    private var moveOffset = 0f
    //是否需要拦截
    private var isNeedToIntercept = false
    //弹回的动画时间
    private var animTime = 300L
    //阻尼系数
    private var damping = 0.5f
    private var dampingTemp = 0.5f
    private var isDecrement = true     //阻尼系数是否需要逐减

    private var isRefreshOrLoad = false
    private val refreshCallBack = object : PullCallBack {
        override suspend fun over(success: Boolean) {
            headerAdapter!!.refreshed()
            delay(300)
            springBack((-headerAdapter!!.offset).toFloat())
            isRefreshOrLoad = false
        }
    }
    private val loadCallBack = object : PullCallBack {
        override suspend fun over(success: Boolean) {
            footerAdapter!!.loaded()
            delay(300)
            springBack(footerAdapter!!.offset.toFloat())
            isRefreshOrLoad = false
        }
    }

    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
    ) : super(context, attrs, defStyleAttr) {
        //默认垂直布局
        (this as LinearLayout).orientation = LinearLayout.VERTICAL
        //不裁剪布局
        this.clipChildren = false
        this.clipToPadding = false
    }

    //拦截所有消息
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        //Log.e(TAG,"get message")
        when (checkNotNull(event).action) {
            MotionEvent.ACTION_DOWN -> {
                oldY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                //如果满足滑动条件则不共享move事件
                val offset = oldY - event.y
                oldY = event.y
                if (isNeedToIntercept) {
                    scroll(offset)
                    return true
                    //canScrollVertically(1)滑动到底部返回false，canScrollVertically(-1)滑动到顶部返回false
                } else if ((!(middleView.canScrollVertically(-1)) && offset < -2f)
                    || (!(middleView.canScrollVertically(1)) && offset > 2f)
                ) {
                    scroll(offset)
                    isNeedToIntercept = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isNeedToIntercept) {//需要拦截，不共享
                    if (headerAdapter != null && moveOffset <= -headerAdapter!!.offset) {
                        headerAdapter!!.refreshing()
                        isRefreshOrLoad = true
                        springBack(moveOffset + headerAdapter!!.offset)
                        //headerAdapter!!.refreshed()
                        isNeedToIntercept = false
                        oldY = 0f
                        return true
                    } else if (footerAdapter != null && moveOffset >= footerAdapter!!.offset) {
                        footerAdapter!!.loading()
                        isRefreshOrLoad = true
                        springBack(moveOffset - footerAdapter!!.offset)
                        //footerAdapter!!.loaded()
                        isNeedToIntercept = false
                        oldY = 0f
                        return true
                    }
                    springBack(moveOffset)
                    isNeedToIntercept = false
                    oldY = 0f
                    return true
                }
            }
        }
        //最后共享满足条件所有消息
        middleView.dispatchTouchEvent(event)
        return true
    }

    //移动调整header和footer
    private fun myScrollBy(deltaY: Int) {
        if (!canScrollVertically(-1)){
//                middleView.y-=deltaY
            if (headerView!=null) {
                val layoutParams = headerView!!.layoutParams as LinearLayout.LayoutParams
                layoutParams.topMargin -= deltaY
                GlobalScope.launch(Dispatchers.Main) {
                    headerView!!.layoutParams = layoutParams
                }
            }
        }else if(!canScrollVertically(1)){
            //middleView.y-=deltaY
            if (footerView != null){
                val layoutParams = footerView!!.layoutParams as LinearLayout.LayoutParams
                layoutParams.topMargin -= deltaY
                GlobalScope.launch(Dispatchers.Main) {
                    footerView!!.layoutParams = layoutParams
                }

            }
        }
    }
    private fun scroll(deltaY: Float) {
        if (isRefreshOrLoad) {
            return
        }
        //Log.e(TAG,"move offset = $deltaY")
        //Log.e("sl","scroll =$scrollY max = $maxOverScrollY range=$maxOverScrollY")
        if ((moveOffset < 0f && deltaY > 0) || (moveOffset > 0f && deltaY < 0)) {
            var offset = deltaY * damping
            if (offset < 0 && (-offset > moveOffset)
                || offset > 0 && -offset < moveOffset
            ) {
                offset = -moveOffset
            }
            //y -= offset
            //layout(left, (top-offset).toInt(),right, (bottom-offset).toInt())
            myScrollBy(offset.toInt())
            moveOffset += offset.toInt()
        }
        //canScrollVertically(1)滑动到底部返回false，canScrollVertically(-1)滑动到顶部返回false
        else if ((!middleView.canScrollVertically(-1) && deltaY < 0)
            || (!middleView.canScrollVertically(1) && deltaY > 0)
        ) {//下拉和上滑
            val offset = deltaY * dampingTemp
            //y -= offset
            //layout(left, (top-offset).toInt(),right, (bottom-offset).toInt())
            myScrollBy(offset.toInt())
            moveOffset += offset.toInt()
            if (isDecrement) {
                calcDamping()
            }
        }
        if (moveOffset < 0) {
            if (headerAdapter == null)
                return
            headerAdapter!!.scrollProgress(moveOffset.toInt())
            if (moveOffset <= -headerAdapter!!.offset)
                headerAdapter!!.releaseToRefresh()
            else
                headerAdapter!!.pullToRefresh()
        } else {
            if (footerAdapter == null)
                return
            footerAdapter!!.scrollProgress(moveOffset.toInt())
            if (moveOffset >= footerAdapter!!.offset)
                footerAdapter!!.releaseToLoad()
            else
                footerAdapter!!.pullToLoad()
        }
    }

    fun setDamping(damping: Float, isDecrement: Boolean = true) {
        this.damping = damping
        this.dampingTemp = damping
        this.isDecrement = isDecrement
    }

    fun setAnimTime(time: Long) {
        animTime = time
    }

    private fun calcDamping() {
        //val offset = abs(moveOffset).toDouble()
        //双曲正切函数(e^x-e^(-x))/(e^x+e^(-x)),随着x递增，y从零开始增加无限趋近于0
        //dampingTemp = damping * (1-((exp(offset) - exp(-offset))/(exp(offset) + exp(-offset)))).toFloat()
        var count = (abs(moveOffset) / 150).toInt()
        if (count == 0) {
            count = 1
        }
        dampingTemp = damping / count
    }

    //弹回动画
    private fun springBack(offset: Float) {
        val animation = TranslateAnimation(0f, 0f, -offset,0f)
        animation.duration = animTime
        animation.setAnimationListener(object :Animation.AnimationListener{
            override fun onAnimationRepeat(animation: Animation?) {

            }

            override fun onAnimationEnd(animation: Animation?) {
            }

            override fun onAnimationStart(animation: Animation?) {
              //  scrollBy(0, (-offset).toInt())
//                myScrollBy(-offset.toInt())
            }
        })
        startAnimation(animation)
        myScrollBy(-offset.toInt())//自定义滚动，和scrollBy一样的效果
        //这里使用scrollBy会导致图层被切割,屏幕上只有移动过后要显示内容，过渡内容被裁剪
        //scrollY -= offset.toInt()
        //scrollBy(0, 0)//设置在动画结束后执行，动画就重复了会闪烁,最后还是接受了闪烁
        //使用setY（）会导致，整体布局移动，因为之前移动是使用的scrollBy
//        y += offset
        //最终采用layout方法调整布局,结果还是有问题，会移动布局
        //layout(left, (top + offset).toInt(), right, (bottom+offset).toInt())
        moveOffset -= offset
        dampingTemp = damping
    }

    fun setHeaderAdapter(adapter: HeaderAdapter) {
        headerAdapter = adapter
        headerAdapter!!.setPullCallBack(refreshCallBack)
        addHeaderView(headerAdapter!!.getHeaderView(this))
    }

    private fun addHeaderView(view: View) {
        if (childCount == 3) {
            Log.e(TAG, "only support three views")
            return
        }
        headerView = view
        addView(headerView, 0)//最底层
        headerView!!.post {
            val height = headerView!!.measuredHeight
            val layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height)
            layoutParams.topMargin = -height
            headerView!!.layoutParams = layoutParams
        }
    }

    fun setFooterAdapter(adapter: FooterAdapter) {
        footerAdapter = adapter
        footerAdapter!!.setPullCallBack(loadCallBack)
        addFooterView(footerAdapter!!.getFooterView(this))
    }

    private fun addFooterView(view: View) {
        if (childCount == 3) {
            Log.e(TAG, "only support three views")
            return
        }
        footerView = view
        addView(footerView, 2)
        footerView!!.post {
            val height = footerView!!.measuredHeight
            val layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            layoutParams.bottomMargin = -height
            footerView!!.layoutParams = layoutParams
        }
    }

    //将自身置于底层
    fun sendToBack() {
        val group = parent as ViewGroup
        val count = group.childCount
        for (i in 0 until count) {
            if (group[i] != this) {
                group[i].bringToFront()//置顶除自身外的所有view
            }
        }
    }

    //将MiddleView置于底层,内部调用
    private fun sendMiddleToBack() {
        for (i in 0 until childCount) {
            if (getChildAt(i) != this)
                getChildAt(i).bringToFront()
        }
    }

    abstract class HeaderAdapter(var offset: Int) {
        abstract fun getHeaderView(viewGroup: ViewGroup): View
        abstract fun scrollProgress(progress: Int)
        abstract fun setPullCallBack(callBack: PullCallBack)
        //下拉刷新
        abstract fun pullToRefresh()

        //释放刷新
        abstract fun releaseToRefresh()

        //刷新中
        abstract fun refreshing()

        //刷新完成
        abstract fun refreshed()
    }

    abstract class FooterAdapter(var offset: Int) {
        abstract fun getFooterView(viewGroup: ViewGroup): View
        abstract fun scrollProgress(progress: Int)
        abstract fun setPullCallBack(callBack: PullCallBack)
        //上拉加载
        abstract fun pullToLoad()

        //释放加载
        abstract fun releaseToLoad()

        //加载中
        abstract fun loading()

        //加载完成
        abstract fun loaded()
    }

    interface PullCallBack {
        suspend fun over(success: Boolean)
    }
}