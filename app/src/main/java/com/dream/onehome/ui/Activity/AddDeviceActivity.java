package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;
import com.dream.onehome.common.Const;
import com.dream.onehome.utils.DensityUtil;
import com.dream.onehome.utils.PopWindowUtil;
import com.dream.onehome.utils.SP;
import com.dream.onehome.utils.SpUtils;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class AddDeviceActivity extends BaseActivity {

    @BindView(R.id.flash_iv)
    ImageView flashIv;
    @BindView(R.id.netmode_change)
    LinearLayout netmodeChange;
    @BindView(R.id.divider)
    View divider;
    @BindView(R.id.sure_rad)
    RadioButton sureRad;
    @BindView(R.id.ra_tv)
    TextView ra_tv;
    @BindView(R.id.sure_tv)
    Button sureTv;
    @BindView(R.id.summary_tv)
    TextView summaryTv;

    private int index;
    private int modePerTime = 2;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (flashIv != null) {
                if (index % 2 == 0) {
                    flashIv.setImageResource(R.drawable.valicon);
                } else {
                    flashIv.setImageResource(R.mipmap.valiconr);
                }
            }
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            index++;
            mHandler.sendEmptyMessage(10);
            mHandler.postDelayed(this, modePerTime*100);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        if (mHandler != null) {
            mHandler.removeCallbacks(mRunnable);
        }
    }

    @Override
    public int getLayoutId() {
        return R.layout.activity_addstep_one;
    }

    @Override
    public void initView() {
        ImmersionBar.with(this).statusBarDarkFont(true).hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR).fitsSystemWindows(true).barColor(R.color.color_valicode).keyboardEnable(true).init();

    }

    @Override
    public void OnResume() {
        mHandler.removeCallbacks(mRunnable);
        mHandler.post(mRunnable);
    }

    @Override
    public void loadDatas() {

    }

    @Override
    public void eventListener() {
        sureRad.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    sureTv.setBackgroundResource(R.drawable.select_surebtn);
                    sureTv.setEnabled(true);
                }else {
                    sureTv.setBackgroundResource(R.drawable.shape_voice_unenable_btn);
                    sureTv.setEnabled(false);
                }
            }
        });
    }

    @OnClick({R.id.back_iv, R.id.sure_tv, R.id.netmode_change})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.sure_tv:
                SP.put(Const.netMode,modePerTime);
                Intent intent = new Intent(this, WifiSetActivity.class);
                startActivity(intent);
                break;
            case R.id.netmode_change:
                getNetmodeChangePop();
                break;
        }
    }

    private PopupWindow mPopupWindow;
    private void getNetmodeChangePop() {
        if (mPopupWindow == null) {
            mPopupWindow = new PopupWindow(this);
            View inflate = LayoutInflater.from(this).inflate(R.layout.pop_netchange, null);
            LinearLayout wifiLay = inflate.findViewById(R.id.wifilay);
            LinearLayout hotnetLay = inflate.findViewById(R.id.hotnetlay);
            ImageView selectIv1 = inflate.findViewById(R.id.select_iv1);
            ImageView selectIv2 = inflate.findViewById(R.id.select_iv2);
            wifiLay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectIv1.setVisibility(View.VISIBLE);
                    selectIv2.setVisibility(View.INVISIBLE);
                    modePerTime = 2;
                    summaryTv.setText("长按复位键5秒进入快闪模式");
                    ra_tv.setText("确认指示灯正在快闪");
                    mPopupWindow.dismiss();
                }
            });
            hotnetLay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectIv1.setVisibility(View.INVISIBLE);
                    selectIv2.setVisibility(View.VISIBLE);
                    modePerTime = 6;
                    summaryTv.setText("长按复位键5秒进入快闪模式，再按复位键5秒进入慢闪模式");
                    ra_tv.setText("确认指示灯在慢闪");
                    mPopupWindow.dismiss();
                }
            });
            mPopupWindow.setContentView(inflate);
            mPopupWindow.setFocusable(true);
            mPopupWindow.setBackgroundDrawable(new BitmapDrawable());
            mPopupWindow.setTouchable(true);
            mPopupWindow.setAnimationStyle(R.style.top2botAnimation);
            mPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    sureRad.setChecked(false);
                    PopWindowUtil.setBackgroundAlpaha(AddDeviceActivity.this, 1.0f);
                    mHandler.removeCallbacks(mRunnable);
                    mHandler.post(mRunnable);
                }
            });
        }
        PopWindowUtil.setBackgroundAlpaha(this, 0.5f);
        mPopupWindow.showAsDropDown(netmodeChange, 0, DensityUtil.dip2px(this, 13));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mRunnable);
        mHandler = null;
    }
}
