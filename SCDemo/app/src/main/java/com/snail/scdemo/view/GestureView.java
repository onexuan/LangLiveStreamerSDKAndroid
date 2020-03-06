package com.snail.scdemo.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Created by MRKING on 2017/6/1.
 */

public class GestureView extends View implements GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener {

    private final static String TAG = "GestureView";
    private  float minScale = 1.0f;
    private  float maxScale = 3.0f;

    private ScaleGestureDetector mScaleDetector;
    private GestureDetector mGesture;

    private float minDistance = 100;
    private int mDistanceStep = 1;
    private GestureListener mGestureListener = null;
    private boolean isMultiTouch = false;
    private float mScaleFactor = 1.f;

    public GestureView(Context context) {
        this(context, null);
    }

    public GestureView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mScaleDetector = new ScaleGestureDetector(context, this);
        mGesture = new GestureDetector(context, this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (event.getPointerCount() > 1 && !isMultiTouch)
            isMultiTouch = true;
        if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
            mDistanceStep = 1;
            isMultiTouch = false;
            Log.d(TAG, "mDistanceStep = 1  " + event.getAction());
            if (mGestureListener != null)
                mGestureListener.onPointerUp();

        }

        return mScaleDetector.onTouchEvent(event) | mGesture.onTouchEvent(event);
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        if (mGestureListener != null)
            mGestureListener.onClick(motionEvent.getX(), motionEvent.getY());
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float distanceX,
                            float distanceY) {
        if (isMultiTouch)
            return false;

        if (mGestureListener != null) {
            //判断是否水平滑动 防止手势识别错误
            float absX = Math.abs(motionEvent.getRawX() - motionEvent1.getRawX());
            float absY = Math.abs(motionEvent.getRawY() - motionEvent1.getRawY());
           /* Log.d(TAG, "Math.abs(absY-->" + absY);
            Log.d(TAG, "absX-->" + absX);
            Log.d(TAG, "distanceX-->" + distanceX);
            Log.d(TAG, "distanceY-->" + distanceY);*/

            if (absX > (minDistance + 100) && absY > (minDistance + 100)) {
                return false;
            }
            if ((absX > minDistance) && (Math.abs(distanceY) < 5) && (Math.abs(distanceY) < Math.abs(distanceX))) {
                if (distanceX > 0) {

                    if ((motionEvent.getRawX() - motionEvent1.getRawX()) >= mDistanceStep * minDistance) {
                        mDistanceStep++;
                        mGestureListener.onHorizontalLeft();
                    }
                } else {
                    if ((motionEvent.getRawX() - motionEvent1.getRawX()) <= mDistanceStep * minDistance) {
                        mDistanceStep--;
                        mGestureListener.onHorizontalRight();
                    }
                }
            }
            //判断是否垂直滑动 防止手势识别错误
            else if ((absY > minDistance) && (Math.abs(distanceX) < 5) && (Math.abs(distanceX) < Math.abs(distanceY))) {
                if (distanceY > 0) {

                    if ((motionEvent.getRawY() - motionEvent1.getRawY()) >= mDistanceStep * minDistance) {
                        mDistanceStep++;
                        mGestureListener.onVerticalUp();
                    }
                } else {
                    if ((motionEvent.getRawY() - motionEvent1.getRawY()) <= mDistanceStep * minDistance) {
                        mDistanceStep--;
                        mGestureListener.onVerticalDown();
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {
        if (mGestureListener != null)
            mGestureListener.onLongPress();
    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float velocityX,
                           float velocityY) {
        return false;
    }

    @Override
    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        Log.e(TAG, "scaleGestureDetector.getScaleFactor()-->" + scaleGestureDetector.getScaleFactor());
        if (mGestureListener != null) {
            mScaleFactor *= scaleGestureDetector.getScaleFactor();

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(minScale, Math.min(mScaleFactor, maxScale));
            Log.e(TAG,"mScaleFactor--》" + mScaleFactor);
            mGestureListener.onScale(mScaleFactor);
        }
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {

    }

    public interface GestureListener {
        /**
         * 点击
         */
        void onClick(float x, float y);

        /**
         * 长按
         */
        void onLongPress();

        /**
         * 垂直向上滑动
         */
        void onVerticalUp();

        /**
         * 垂直向下滑动
         */
        void onVerticalDown();

        /**
         * 水平向左滑动
         */
        void onHorizontalLeft();

        /**
         * 水平向右滑动
         */
        void onHorizontalRight();

        /**
         * 放大
         * @param scale 放大倍数
         */
        void onScale(float scale);


        /**
         * 手指抬起
         */
        void onPointerUp();
    }

    public void setMinDistance(float minDistance) {
        if (minDistance <= 0)
            throw new RuntimeException("minDistance can not <= 0");
        this.minDistance = minDistance;
    }

    public void setGestureListener(GestureListener gestureListener) {
        mGestureListener = gestureListener;
    }

    public void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }
}
