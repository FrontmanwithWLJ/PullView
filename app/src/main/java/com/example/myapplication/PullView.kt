package com.example.myapplication

/**
 * PullView 布局内仅有一个View。headerView和footerView用户设置适配器
 */
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import androidx.core.view.get
import kotlinx.coroutines.delay
import kotlin.math.abs

class PullView//不裁剪布局
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LinearLayout(context, attrs, defStyleAttr) {
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
    //阻尼系数是否需要逐减
    private var isDecrement = true
    //第一根手指
    private var firstFingerId = 0
    //当前活跃的触控点
    private var currentActionFingerId = 0
    //是否切换了手指
    private var isCheckFinger = false

    private val refreshCallBack = object : PullCallBack {
        override suspend fun over(success: Boolean) {
            delay(300)
            springBack((-headerAdapter!!.offset).toFloat())
            headerAdapter!!.isRefreshing = false
        }
    }
    private val loadCallBack = object : PullCallBack {
        override suspend fun over(success: Boolean) {
            delay(300)
            springBack(footerAdapter!!.offset.toFloat())
            footerAdapter!!.isLoading = false
        }
    }

    init {
        this.clipChildren = false
        this.clipToPadding = false
    }

    private fun onFling(velocityY:Int){

    }
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return super.dispatchTouchEvent(ev)
    }

    //拦截所有消息
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        //Log.e(TAG,"get message")
        if (event == null)
            return true
        when (event.action and event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentActionFingerId = event.getPointerId(event.actionIndex)
                firstFingerId = currentActionFingerId
                oldY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN->{
                currentActionFingerId = event.getPointerId(event.actionIndex)
                isCheckFinger = true
                return true
            }
            MotionEvent.ACTION_POINTER_UP->{
                if (firstFingerId == event.getPointerId(event.actionIndex)) {
                    firstFingerId = currentActionFingerId
                    return true
                }
                currentActionFingerId = firstFingerId
                oldY = event.getY(event.findPointerIndex(currentActionFingerId))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                //如果满足滑动条件则不共享move事件
                var index = event.findPointerIndex(currentActionFingerId)
                while (index == -1){
                    //没能获取到信息
                    currentActionFingerId = event.getPointerId(event.actionIndex)
                    firstFingerId = currentActionFingerId
                    index = event.findPointerIndex(currentActionFingerId)
                }
                val newY = event.getY(index)
                if (isCheckFinger){
                    oldY = newY
                    isCheckFinger = false
                    return true
                }
                val offset = oldY - newY
                Log.e(TAG,"move =$offset")
                oldY = newY
                if (isNeedToIntercept) {
                    onScrolled(offset)
                    return true
                    //canScrollVertically(1)滑动到底部返回false，canScrollVertically(-1)滑动到顶部返回false
                } else if ((!(middleView.canScrollVertically(-1)) && offset < -2f)
                    || (!(middleView.canScrollVertically(1)) && offset > 2f)
                ) {
                    onScrolled(offset)
                    isNeedToIntercept = true
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isNeedToIntercept) {//需要拦截，不共享
                    isNeedToIntercept = false
                    oldY = 0f
                    if (headerAdapter != null && moveOffset <= -headerAdapter!!.offset) {
                        headerAdapter!!.refreshing()
                        headerAdapter!!.isRefreshing = true
                        springBack(moveOffset + headerAdapter!!.offset)

                        return true
                    } else if (footerAdapter != null && moveOffset >= footerAdapter!!.offset) {
                        footerAdapter!!.loading()
                        footerAdapter!!.isLoading = true
                        springBack(moveOffset - footerAdapter!!.offset)

                        return true
                    }
                    springBack(moveOffset)
                    return true
                }
            }
        }
        //最后共享满足条件所有消息
        middleView.dispatchTouchEvent(event)
        return true
    }

    //对滚动消息初步处理，调整滚动值，更新状态
    private fun onScrolled(deltaY: Float) {
        if ((headerAdapter != null && headerAdapter!!.isRefreshing) || (footerAdapter != null && footerAdapter!!.isLoading) ) {
            return
        }
        if ((moveOffset < 0f && deltaY > 0) || (moveOffset > 0f && deltaY < 0)) {
            var offset = deltaY * damping
            if (offset < 0 && (-offset > moveOffset)
                || offset > 0 && -offset < moveOffset
            ) {
                offset = -moveOffset
            }
            scroll(offset.toInt())
        }
        //canScrollVertically(1)滑动到底部返回false，canScrollVertically(-1)滑动到顶部返回false
        else if ((!middleView.canScrollVertically(-1) && deltaY < 0)
            || (!middleView.canScrollVertically(1) && deltaY > 0)
        ) {//下拉和上滑
            val offset = deltaY * dampingTemp
            scroll(offset.toInt())
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

    //滚动视图
    private fun scroll(deltaY: Int) {
        if (deltaY == 0) return
        scrollBy(0,deltaY)
        moveOffset += deltaY
        return
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
        val animator = ValueAnimator.ofInt(0, (-offset).toInt())
        animator.duration = animTime
        val oy = scrollY    //当前scrollY
        animator.addUpdateListener { animation ->
            scrollTo(scrollX, oy+animation.animatedValue as Int)
        }
        post { animator.start() }
        moveOffset-=offset
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
            val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, height)
            //使其位于布局范围之外
            layoutParams.topMargin = -height
            headerView!!.layoutParams = layoutParams
        }
    }

    fun setFooterAdapter(adapter: FooterAdapter) {
        footerAdapter = adapter
        footerAdapter!!.setPullCallBack(loadCallBack)
        addFooterView(footerAdapter!!.getFooterView(this))
    }

    //相比于HeaderView，footerView处于middleView的最后，无须设置marginTop
    private fun addFooterView(view: View) {
        if (childCount == 3) {
            Log.e(TAG, "only support three views")
            return
        }
        footerView = view
        addView(footerView, 2)
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
        var isRefreshing = false
        abstract fun getHeaderView(viewGroup: ViewGroup): View
        /**
         * @param progress in 0 .. offset
         */
        abstract fun scrollProgress(progress: Int)
        abstract fun setPullCallBack(callBack: PullCallBack)
        //下拉刷新
        abstract fun pullToRefresh()

        //释放刷新
        abstract fun releaseToRefresh()

        //刷新中
        abstract fun refreshing()
    }

    abstract class FooterAdapter(var offset: Int) {
        var isLoading = false
        abstract fun getFooterView(viewGroup: ViewGroup): View
        /**
        * @param progress in 0 .. offset
        */
        abstract fun scrollProgress(progress: Int)
        abstract fun setPullCallBack(callBack: PullCallBack)
        //上拉加载
        abstract fun pullToLoad()

        //释放加载
        abstract fun releaseToLoad()

        //加载中
        abstract fun loading()
    }

    interface PullCallBack {
        suspend fun over(success: Boolean)
    }
}