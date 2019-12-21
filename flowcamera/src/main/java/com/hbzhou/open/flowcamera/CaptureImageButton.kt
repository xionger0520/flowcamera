package com.hbzhou.open.flowcamera

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View


/**
 * author hbzhou
 * date 2019/12/13 11:10
 */
class CaptureImageButton : View {

    companion object {
        val BUTTON_STATE_ONLY_CAPTURE = 0x101 //只能拍照

        val BUTTON_STATE_ONLY_RECORDER = 0x102 //只能录像

        val BUTTON_STATE_BOTH = 0x103
    }

    private var state //当前按钮状态
            = 0
    private var button_state //按钮可执行的功能状态（拍照,录制,两者）
            = 0

    val STATE_IDLE = 0x001 //空闲状态

    val STATE_PRESS = 0x002 //按下状态

    private val STATE_LONG_PRESS = 0x003 //长按状态

    val STATE_RECORDERING = 0x004 //录制状态

    val STATE_BAN = 0x005 //禁止状态


    private val progress_color = -0x11e951ea //进度条颜色

    private val outside_color = -0x11232324 //外圆背景色

    private val inside_color = -0x1 //内圆背景色


    private var event_Y //Touch_Event_Down时候记录的Y值
            = 0f


    private var mPaint: Paint? = null

    private var strokeWidth //进度条宽度
            = 0f
    private var outside_add_size //长按外圆半径变大的Size
            = 0
    private var inside_reduce_size //长安内圆缩小的Size
            = 0

    //中心坐标
    private var center_X = 0f
    private var center_Y = 0f

    private var button_radius //按钮半径
            = 0f
    private var button_outside_radius //外圆半径
            = 0f
    private var button_inside_radius //内圆半径
            = 0f
    private var button_size //按钮大小
            = 0

    private var progress //录制视频的进度
            = 0f
    private var duration //录制视频最大时间长度
            = 0
    private var min_duration //最短录制时间限制
            = 0
    private var recorded_time //记录当前录制的时间
            = 0

    private var rectF: RectF? = null

    private var longPressRunnable //长按后处理的逻辑Runnable
            : LongPressRunnable? = null
    private var captureListener //按钮回调接口
            : CaptureListener? = null
    private var timer //计时器
            : RecordCountDownTimer? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, size: Int) : super(
        context,
        attrs,
        size
    ) {

        button_size = size
        button_radius = size / 2.0f
        button_outside_radius = button_radius
        button_inside_radius = button_radius * 0.75f
        strokeWidth = size / 15.toFloat()
        outside_add_size = size / 5
        inside_reduce_size = size / 8
        mPaint = Paint()
        mPaint!!.isAntiAlias = true
        progress = 0f
        longPressRunnable = LongPressRunnable()
        state = STATE_IDLE //初始化为空闲状态
        button_state = BUTTON_STATE_BOTH //初始化按钮为可录制可拍照
        //LogUtil.i("CaptureButtom start")
        duration = 10 * 1000 //默认最长录制时间为10s
        //LogUtil.i("CaptureButtom end")
        min_duration = 1500 //默认最短录制时间为1.5s
        center_X = (button_size + outside_add_size * 2) / 2.toFloat()
        center_Y = (button_size + outside_add_size * 2) / 2.toFloat()
        rectF = RectF(
            center_X - (button_radius + outside_add_size - strokeWidth / 2),
            center_Y - (button_radius + outside_add_size - strokeWidth / 2),
            center_X + (button_radius + outside_add_size - strokeWidth / 2),
            center_Y + (button_radius + outside_add_size - strokeWidth / 2)
        )
        timer = RecordCountDownTimer(duration.toLong(), (duration / 360).toLong()) //录制定时器
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(button_size + outside_add_size * 2, button_size + outside_add_size * 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mPaint?.setStyle(Paint.Style.FILL)
        mPaint?.setColor(outside_color) //外圆（半透明灰色）
        canvas.drawCircle(center_X, center_Y, button_outside_radius, mPaint!!)
        mPaint?.setColor(inside_color) //内圆（白色）
        canvas.drawCircle(center_X, center_Y, button_inside_radius, mPaint!!)
        //如果状态为录制状态，则绘制录制进度条
        if (state == STATE_RECORDERING) {
            mPaint?.setColor(progress_color)
            mPaint?.setStyle(Paint.Style.STROKE)
            mPaint?.setStrokeWidth(strokeWidth)
            canvas.drawArc(rectF!!, -90f, progress, false, mPaint!!)
        }
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                //LogUtil.i("state = $state")
                if (event.pointerCount > 1 || state != STATE_IDLE)
                    return false
                event_Y = event.y //记录Y值
                state = STATE_PRESS //修改当前状态为点击按下
                //判断按钮状态是否为可录制状态
                if (button_state == BUTTON_STATE_ONLY_RECORDER || button_state == BUTTON_STATE_BOTH) postDelayed(
                    longPressRunnable,
                    500
                ) //同时延长500启动长按后处理的逻辑Runnable
            }
            MotionEvent.ACTION_MOVE -> if (captureListener != null && state == STATE_RECORDERING && (button_state == BUTTON_STATE_ONLY_RECORDER || button_state == BUTTON_STATE_BOTH)
            ) { //记录当前Y值与按下时候Y值的差值，调用缩放回调接口
                captureListener!!.recordZoom(event_Y - event.y)
            }
            MotionEvent.ACTION_UP ->  //根据当前按钮的状态进行相应的处理
                handlerUnpressByState()
        }
        return true
    }

    //当手指松开按钮时候处理的逻辑
    private fun handlerUnpressByState() {
        removeCallbacks(longPressRunnable) //移除长按逻辑的Runnable
        when (state) {
            STATE_PRESS -> if (captureListener != null && (button_state == BUTTON_STATE_ONLY_CAPTURE || button_state ==
                        BUTTON_STATE_BOTH)
            ) {
                startCaptureAnimation(button_inside_radius)
            } else {
                state = STATE_IDLE
            }
            STATE_RECORDERING -> {
                timer!!.cancel() //停止计时器
                recordEnd() //录制结束
            }
        }
    }

    //录制结束
    private fun recordEnd() {
        if (captureListener != null) {
            if (recorded_time < min_duration) captureListener?.recordShort(recorded_time.toLong()) //回调录制时间过短
            else captureListener?.recordEnd(recorded_time.toLong()) //回调录制结束
        }
        resetRecordAnim() //重制按钮状态
    }

    //重制状态
    private fun resetRecordAnim() {
        state = STATE_BAN
        progress = 0f //重制进度
        invalidate()
        //还原按钮初始状态动画
        startRecordAnimation(
            button_outside_radius,
            button_radius,
            button_inside_radius,
            button_radius * 0.75f
        )
    }

    //内圆动画
    private fun startCaptureAnimation(inside_start: Float) {
        val inside_anim =
            ValueAnimator.ofFloat(inside_start, inside_start * 0.75f, inside_start)
        inside_anim.addUpdateListener { animation ->
            button_inside_radius = animation.animatedValue as Float
            invalidate()
        }
        inside_anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                //回调拍照接口
                captureListener?.takePictures()
                state = STATE_BAN
            }
        })
        inside_anim.duration = 100
        inside_anim.start()
    }

    //内外圆动画
    private fun startRecordAnimation(
        outside_start: Float,
        outside_end: Float,
        inside_start: Float,
        inside_end: Float
    ) {
        val outside_anim = ValueAnimator.ofFloat(outside_start, outside_end)
        val inside_anim = ValueAnimator.ofFloat(inside_start, inside_end)
        //外圆动画监听
        outside_anim.addUpdateListener { animation ->
            button_outside_radius = animation.animatedValue as Float
            invalidate()
        }
        //内圆动画监听
        inside_anim.addUpdateListener { animation ->
            button_inside_radius = animation.animatedValue as Float
            invalidate()
        }
        val set = AnimatorSet()
        //当动画结束后启动录像Runnable并且回调录像开始接口
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                //设置为录制状态
                if (state == STATE_LONG_PRESS) {
                    if (captureListener != null) captureListener?.recordStart()
                    state = STATE_RECORDERING
                    timer!!.start()
                }
            }
        })
        set.playTogether(outside_anim, inside_anim)
        set.duration = 100
        set.start()
    }


    //更新进度条
    private fun updateProgress(millisUntilFinished: Long) {
        recorded_time = (duration - millisUntilFinished).toInt()
        progress = 360f - millisUntilFinished / duration.toFloat() * 360f
        invalidate()
    }

    //录制视频计时器
    inner class RecordCountDownTimer internal constructor(
        millisInFuture: Long,
        countDownInterval: Long
    ) :
        CountDownTimer(millisInFuture, countDownInterval) {
        override fun onTick(millisUntilFinished: Long) {
            updateProgress(millisUntilFinished)
        }

        override fun onFinish() {
            updateProgress(0)
            recordEnd()
        }
    }

    //长按线程
    inner class LongPressRunnable : Runnable {
        override fun run() {
            state = STATE_LONG_PRESS //如果按下后经过500毫秒则会修改当前状态为长按状态
            //没有录制权限
            //if (CheckPermission.getRecordState() !== CheckPermission.STATE_SUCCESS) {
                state = STATE_IDLE
                if (captureListener != null) {
                    captureListener?.recordError()
                    return
                }
            //}
            //启动按钮动画，外圆变大，内圆缩小
            startRecordAnimation(
                button_outside_radius,
                button_outside_radius + outside_add_size,
                button_inside_radius,
                button_inside_radius - inside_reduce_size
            )
        }
    }

    /**************************************************
     * 对外提供的API                     *
     */
    //设置最长录制时间
    fun setDuration(duration: Int) {
        this.duration = duration
        timer = RecordCountDownTimer(duration.toLong(), (duration / 360).toLong()) //录制定时器
    }

    //设置最短录制时间
    fun setMinDuration(duration: Int) {
        min_duration = duration
    }

    //设置回调接口
    fun setCaptureLisenter(captureListener: CaptureListener?) {
        this.captureListener = captureListener
    }

    //设置按钮功能（拍照和录像）
    fun setButtonFeatures(state: Int) {
        button_state = state
    }

    //是否空闲状态
    fun isIdle(): Boolean {
        return state == STATE_IDLE
    }

    //设置状态
    fun resetState() {
        state = STATE_IDLE
    }
}