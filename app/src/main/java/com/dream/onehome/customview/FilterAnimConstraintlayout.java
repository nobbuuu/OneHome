package com.dream.onehome.customview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.dream.onehome.R;

/**
 * Time:2019/01/10
 * Author:TiaoZi
 */
public class FilterAnimConstraintlayout extends ConstraintLayout {

    private int centerX,centerY;
    private int mWidth,mHeight;
    private Paint mPaint;
    public FilterAnimConstraintlayout(Context context) {
        super(context);
        init();
    }

    public FilterAnimConstraintlayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    private void init(){
        mWidth = getMeasuredWidth();
        mHeight = getMeasuredHeight();
        mPaint = new Paint();
        mPaint.setColor(getResources().getColor(R.color.color_anim));
    }

    public void startAnim(int centerWidth, int centerHeight){
        centerX = centerWidth;
        centerY = centerHeight;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawCircle(mWidth/2,mHeight,10f,mPaint);

    }
}
