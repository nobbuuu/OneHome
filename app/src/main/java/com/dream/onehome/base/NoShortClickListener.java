package com.dream.onehome.base;

import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;

public abstract class NoShortClickListener implements View.OnTouchListener {

    private boolean isDown,isAnim;
    private int longTime = 1;

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

        }
    };

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()){
            case MotionEvent.ACTION_DOWN:
                isDown = true;
                longTime = 1;
                mHandler.postDelayed(runnable,500);
                break;
            case MotionEvent.ACTION_UP:
                if (isDown&&isAnim){
                    onACTION_UP();
                }
                isDown = false;
                isAnim = false;
                break;
        }
        return true;
    }

    public abstract void onACTION_DOWN();
    public abstract void onShortClick();
    public abstract void onACTION_UP();

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {

            longTime--;
            if (longTime==0&&isDown){
                onACTION_DOWN();
                isAnim = true;
            }else {
                onShortClick();
            }

        }
    };

}