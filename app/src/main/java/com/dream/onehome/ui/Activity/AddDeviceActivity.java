package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;

import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class AddDeviceActivity extends BaseActivity {

    @BindView(R.id.flash_iv)
    ImageView flashIv;

    private int index;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (index%2 == 0){
                flashIv.setImageResource(R.drawable.valicon);
            }else {
                flashIv.setImageResource(R.drawable.valiconr);
            }
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            index++;
            mHandler.sendEmptyMessage(10);
            mHandler.postDelayed(this,600);
        }
    };
    @Override
    public int getLayoutId() {
        return R.layout.activity_addstep_one;
    }

    @Override
    public void initView() {

        mHandler.post(mRunnable);
    }

    @Override
    public void OnResume() {

    }

    @Override
    public void loadDatas() {

    }

    @Override
    public void eventListener() {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mRunnable);
        mHandler = null;
    }

    @OnClick({R.id.back_iv, R.id.sure_tv})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.sure_tv:
                Intent intent = new Intent(this, WifiSetActivity.class);
                startActivity(intent);
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO: add setContentView(...) invocation
        ButterKnife.bind(this);
    }
}
