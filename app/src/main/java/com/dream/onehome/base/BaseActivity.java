package com.dream.onehome.base;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;


import com.dream.onehome.dialog.LoadingDialog;

import butterknife.ButterKnife;
import butterknife.Unbinder;

/**
 * Created by Administrator on 2017/4/7/007.
 */
public abstract class BaseActivity extends AppCompatActivity {

    public Bundle savedInstanceState;
    public Activity mActivity;
    public Context mContext;
    public Bundle msaveInstance;
    private Unbinder mUnbinder;
   /* @Bind(R.id.root_lay)
    public LinearLayout root_lay;*/
    private static final int PERMISSION_REQUESTCODE = 100;
    public Dialog mLoading;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//       this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        getSupportActionBar().hide();
        setContentView(getLayoutId());
        mUnbinder = ButterKnife.bind(this);
//        XuniKeyWord.setShiPei(this,root_lay);
        this.savedInstanceState = savedInstanceState;
        this.mActivity = this;
        this.mContext = this;
        this.msaveInstance = savedInstanceState;
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        initView();
        loadDatas();
        eventListener();
        mLoading = LoadingDialog.initLoadingDialog(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        OnResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUnbinder.unbind();
    }

    public abstract int getLayoutId();

    public abstract void initView();
    public abstract void OnResume();
    public abstract void loadDatas();

    public abstract void eventListener();
    /**
     * Intent的简单跳转
     * @param cls
     * @param bundle
     */
    protected void GoToActivity(Class<?>cls,Bundle bundle){
        Intent intent=new Intent(this,cls);
        if (null!=bundle){
            intent.putExtras(bundle);
        }
        startActivity(intent);
//        overridePendingTransition(R.anim.dync_in_from_right,R.anim.dync_out_to_left);
    }

    /**
     * Intent简单的跳转关闭
     * @param cls
     * @param bundle
     */
    protected void GoToActivityFinish(Class<?>cls,Bundle bundle){
        Intent intent=new Intent(this,cls);
        if (null!=bundle){
            intent.putExtras(bundle);
        }
        startActivity(new Intent(this,cls));
//        overridePendingTransition(R.anim.slide_left_out,R.anim.slide_left_in);

        this.finish();
    }

}
