package me.wmh.clockprogress;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;

import java.text.DecimalFormat;

/**
 * Created by Jeremy on 2015/2/4.
 */
public class ClockProgress extends View {

    /**
     * 时钟背景图片
     */
    private Bitmap bitmapClockBackground;
    /**
     * 时钟分针背景图片
     */
    private Bitmap bitmapClockMinute;
    /**
     * 时钟秒针背景图片
     */
    private Bitmap bitmapClockSecond;

    /**
     * 用于旋转分针的Matrix
     */
    private Matrix matrixMinute;

    /**
     * 用于旋转秒针的Matrix
     */
    private Matrix matrixSecond;

    /**
     * 用于手动设置旋转秒针的Matrix
     */
    private Matrix matrixSecondManual;

    /**
     * 是否开始时钟效果
     */
    private boolean startRotate = false;

    /**
     * 秒针旋转的角度
     */
    private float secondDegree = 0;
    /**
     * 分针旋转的角度
     */
    private float minuteDegree = 0;

    /**
     * 旋转动画的handle信息
     */
    private final int CLOCK_ROTATE = 1;

    private Message msg;

    /**
     * 秒针旋转的中心点x坐标
     */
    private float rotateSecondX = 0;
    /**
     * 秒针旋转的中心点y坐标
     */
    private float rotateSecondY = 0;
    /**
     * 分针旋转的中心点x坐标
     */
    private float rotateMinuteX = 0;
    /**
     * 分针旋转的中心点y坐标
     */
    private float rotateMinuteY = 0;

    /**
     * 默认宽度
     */
    private int DEFAULT_VIEW_WIDTH = 100;
    /**
     * 默认高度
     */
    private int DEFAULT_VIEW_HEIGHT = 100;

    /**
     * 下拉距离与旋转角度的比率
     */
    private double ratio = 0.00;

    public ClockProgress(Context context) {
        super(context);
        init();
    }

    public ClockProgress(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ClockProgress(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初始化相关
     */
    private void init() {
        bitmapClockBackground = ((BitmapDrawable) getResources().getDrawable(R.drawable.bg_clock)).getBitmap();
        bitmapClockMinute = ((BitmapDrawable) getResources().getDrawable(R.drawable.bg_clock_minute)).getBitmap();
        bitmapClockSecond = ((BitmapDrawable) getResources().getDrawable(R.drawable.bg_clock_second)).getBitmap();

        DEFAULT_VIEW_WIDTH = bitmapClockBackground.getWidth();
        DEFAULT_VIEW_HEIGHT = bitmapClockBackground.getHeight();

        matrixMinute = new Matrix();
        matrixSecond = new Matrix();
        matrixSecondManual = new Matrix();
        msg = new Message();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = measureDimension(DEFAULT_VIEW_WIDTH, widthMeasureSpec);
        int height = measureDimension(DEFAULT_VIEW_HEIGHT, heightMeasureSpec);

        // 计算缩放比例
        float scaleWidth = ((float)width)/bitmapClockBackground.getWidth();
        float scaleHeight = ((float)height)/bitmapClockBackground.getHeight();
        // 取得想要缩放的matrix参数
        Matrix matrix = new Matrix();
        matrix.setScale(scaleWidth, scaleHeight);
        // 得到新的图片
        bitmapClockBackground = Bitmap.createBitmap(bitmapClockBackground, 0, 0, bitmapClockBackground.getWidth(), bitmapClockBackground.getHeight(), matrix,true);

        scaleWidth = ((float)width)/bitmapClockMinute.getWidth();
        scaleHeight = ((float)height)/bitmapClockMinute.getHeight();
        // 取得想要缩放的matrix参数
        matrix.setScale(scaleWidth, scaleHeight);
        // 得到新的图片
        bitmapClockMinute = Bitmap.createBitmap(bitmapClockMinute, 0, 0, bitmapClockMinute.getWidth(), bitmapClockMinute.getHeight(), matrix,true);

        scaleWidth = ((float)width)/bitmapClockSecond.getWidth();
        scaleHeight = ((float)height)/bitmapClockSecond.getHeight();
        // 取得想要缩放的matrix参数
        matrix.setScale(scaleWidth, scaleHeight);
        // 得到新的图片
        bitmapClockSecond = Bitmap.createBitmap(bitmapClockSecond, 0, 0, bitmapClockSecond.getWidth(), bitmapClockSecond.getHeight(), matrix,true);

        rotateMinuteX = rotateSecondX = width / 2;
        rotateMinuteY = rotateSecondY = height / 2;
        matrixSecondManual.setRotate(0,rotateSecondX,rotateSecondY);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (startRotate) {
            canvas.drawBitmap(bitmapClockBackground, 0, 0, null);
            canvas.drawBitmap(bitmapClockMinute, matrixMinute, null);
            canvas.drawBitmap(bitmapClockSecond, matrixSecond, null);

            msg.what = CLOCK_ROTATE;
            handler.handleMessage(msg);
        } else {
            canvas.drawBitmap(bitmapClockBackground, 0, 0, null);
            canvas.drawBitmap(bitmapClockMinute, 0, 0, null);
            canvas.drawBitmap(bitmapClockSecond, matrixSecondManual, null);
        }
    }

    /**
     * 下拉listview时根据headerview的距离顶部的高度来设置钟表中秒针的旋转角度
     * @param defaultPadding 默认距离
     * @param currentPadding 实际距离
     */
    public void setClockByPaddingTop(int defaultPadding,int currentPadding){
        ratio = -360.0/defaultPadding;
        if(currentPadding == defaultPadding){
            setClockToZero();
        }else if(currentPadding < 0 && currentPadding > defaultPadding){
            //根据高度换算出秒针旋转的角度，旋转到当前的角度正好等于默认的高度时表针正好旋转一周，之后不再旋转
            secondDegree = (float)(-ratio*(defaultPadding-currentPadding));
            matrixSecondManual.setRotate(secondDegree, rotateSecondX, rotateSecondY);
            invalidate();
        }
    }

    /**
     * 设置开始自动转动表针
     */
    public void setStartAutoRotate(){
        this.startRotate = true;
        minuteDegree = 0;
        secondDegree = 0;
        setMinuteMatrix(minuteDegree, rotateMinuteX, rotateMinuteY);
        setSecondMatrix(secondDegree, rotateSecondX, rotateSecondY);
        invalidate();
    }

    /**
     * 设置是否开始自动转动表针
     */
    public void setClockToZero(){
        this.startRotate = false;
        secondDegree = 0;
        matrixSecondManual.setRotate(secondDegree,rotateSecondX,rotateSecondY);
        invalidate();
    }

    /**
     * 测量view大小
     * @param defaultSize 默认大小
     * @param measureSpec 实际测量
     * @return 实际
     */
    protected int measureDimension(int defaultSize, int measureSpec) {
        int result = defaultSize;

        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            //1. layout给出了确定的值，比如：100dp
            //2. layout使用的是match_parent，但父控件的size已经可以确定了，比如设置的是具体的值或者match_parent
            result = specSize; //建议：result直接使用确定值
        } else if (specMode == MeasureSpec.AT_MOST) {
            //1. layout使用的是wrap_content
            //2. layout使用的是match_parent,但父控件使用的是确定的值或者wrap_content
            result = Math.min(defaultSize, specSize); //建议：result不能大于specSize
        } else {
            //UNSPECIFIED,没有任何限制.多半出现在自定义的父控件的情况下，期望由自控件自行决定大小
            result = defaultSize;
        }
        return result;
    }

    /**
     * 设置分针旋转的角度
     *
     * @param degrees 角度
     * @param px      旋转中心x
     * @param py      旋转中心y
     */
    private void setMinuteMatrix(float degrees, float px, float py) {
        matrixMinute.setRotate(degrees, px, py);
    }

    /**
     * 设置秒针旋转的角度
     *
     * @param degrees 角度
     * @param px      旋转中心x
     * @param py      旋转中心y
     */
    private void setSecondMatrix(float degrees, float px, float py) {
        matrixSecond.setRotate(degrees, px, py);
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case CLOCK_ROTATE:
                    if (secondDegree == 360) {
                        secondDegree = 0;

                        if (minuteDegree == 360) {
                            minuteDegree = 0;
                        }
                    }
                    //秒针转3度分针转0.25度
                    minuteDegree += 0.25;
                    secondDegree += 3;
                    setMinuteMatrix(minuteDegree, rotateMinuteX, rotateMinuteY);
                    setSecondMatrix(secondDegree, rotateSecondX, rotateSecondY);
                    invalidate();
                    break;
            }
        }
    };
}
