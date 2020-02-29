package com.dream.onehome.ui.Activity;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaDatapoint;
import com.aylanetworks.aylasdk.AylaDatum;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaProperty;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.adapter.CustomRemoteAdapter;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.base.NoDoubleClickListener;
import com.dream.onehome.bean.CustomItemBean;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.databinding.ActivityCustomizeBinding;
import com.dream.onehome.ui.ViewModel.CustomRemoteModel;
import com.dream.onehome.utils.SP;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;
import com.dream.onehome.utils.annotations.ContentView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Time:2020/02/28
 * Author:TiaoZi
 */
@ContentView(R.layout.activity_customize)
public class CustomRemoteActivity extends BaseMVVMActivity<CustomRemoteModel, ActivityCustomizeBinding> {

    private Dialog mDialog;
    private List<CustomItemBean> dataList = new ArrayList<>();
    private String mKeyName = "";
    private CustomRemoteAdapter mRemoteAdapter;

    private AylaSessionManager mSessionManager;
    private AylaDevice mAylaDevice;
    private static final String TAG = "AylaLog";
    private AylaProperty mAylaProperty;

    @Override
    protected void initIntent() {

        String kfid = getIntent().getStringExtra(Const.kfid);
        String remoteName = getIntent().getStringExtra(Const.deviceName);
        if (kfid != null){
            bindingView.completeTv.setVisibility(View.GONE);
            bindingView.botDivider.setVisibility(View.GONE);
            bindingView.addbtnBtn.setVisibility(View.GONE);

            String s = SP.get(kfid, "");
            dataList = new Gson().fromJson(s, new TypeToken<List<CustomItemBean>>() {}.getType());
        }else {
            mDialog = showNameDialog(this);
        }

        if (remoteName != null){
            bindingView.centerTv.setText(remoteName);
        }

        mSessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
        if (mSessionManager != null) {
            String dsn = (String) SpUtils.getParam(Const.DSN, "");
            if (!dsn.isEmpty()) {
                mAylaDevice = mSessionManager.getDeviceManager().deviceWithDSN(dsn);
                mAylaProperty = mAylaDevice.getProperty(Const.IR_Send_code);
            }

        } else {
            ToastUtils.Toast_long("aylaSessionManager 初始化失败！");
            Log.e(TAG, "aylaSessionManager  = " + mSessionManager);
        }

    }

    @Override
    protected void onEvent() {
        bindingView.backIv.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                onBackPressed();
            }
        });
        bindingView.addbtnBtn.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                if (mDialog != null) {
                    mDialog.show();
                }
            }
        });

        bindingView.customGv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                updateIrCode(dataList.get(position).getIrCode());
            }
        });

        bindingView.completeTv.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                getCompleteTipDialog(CustomRemoteActivity.this).show();
            }
        });
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mRemoteAdapter = new CustomRemoteAdapter(this,dataList,R.layout.gvitem_custom);
        bindingView.customGv.setAdapter(mRemoteAdapter);
    }

    private Dialog showNameDialog(Context activity) {
        Dialog dialog = new Dialog(activity, R.style.ActionSheetDialogStyle);
        dialog.setContentView(R.layout.dialog_btnname);
        dialog.setCancelable(true);
        setDialogGravity(dialog,Gravity.BOTTOM,0.93);
        EditText nameEdt = dialog.findViewById(R.id.name_edt);
        TextView cancelTv = dialog.findViewById(R.id.cancle_tv);
        TextView sureTv = dialog.findViewById(R.id.sure_tv);
        cancelTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        sureTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String keyName = nameEdt.getText().toString();
                mKeyName = keyName;
                if (keyName.isEmpty()){
                    ToastUtils.Toast_long("请输入按键名称");
                    return;
                }
                Intent intent = new Intent(CustomRemoteActivity.this,LearnActivity.class);
                startActivityForResult(intent,010);
                nameEdt.setText("");
                dialog.dismiss();
            }
        });
        return dialog;
    }
    private Dialog getCompleteTipDialog(Context activity) {
        Dialog dialog = new Dialog(activity, R.style.ActionSheetDialogStyle);
        dialog.setContentView(R.layout.dialog_btnname);
        dialog.setCancelable(true);
        setDialogGravity(dialog,Gravity.CENTER,0.9);
        EditText nameEdt = dialog.findViewById(R.id.name_edt);
        TextView cancelTv = dialog.findViewById(R.id.cancle_tv);
        TextView sureTv = dialog.findViewById(R.id.sure_tv);
        TextView nameTv = dialog.findViewById(R.id.name_tv);
        nameTv.setText("遥控器名称");
        nameEdt.setText("自定义遥控器");
        sureTv.setText("确定");
        cancelTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        sureTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String keyName = nameEdt.getText().toString();
                if (keyName.isEmpty()){
                    ToastUtils.Toast_long("请输入遥控器名称");
                }else {
                    dialog.dismiss();
                    RemoteControlBean bean = new RemoteControlBean();
                    String kfid = SystemClock.elapsedRealtime()+"c";
                    bean.setName(keyName);
                    bean.setType("12");
                    bean.setBrandName("自定义");
                    bean.setKfid(kfid);
                    String value = new Gson().toJson(bean);
                    mLoadingDialog.show();
                    Log.e(TAG,"kfid = " + kfid);
                    mAylaDevice.createDatum(kfid, value, new Response.Listener<AylaDatum>() {
                        @Override
                        public void onResponse(AylaDatum response) {
                            Log.d(TAG,"response = " + response.getValue());
                            mLoadingDialog.dismiss();
                            ToastUtils.Toast_long("添加成功");
                            SP.put(kfid,new Gson().toJson(dataList));
                            Intent intent = new Intent(CustomRemoteActivity.this, CustomRemoteActivity.class);
                            intent.putExtra(Const.kfid,bean.getKfid());
                            intent.putExtra(Const.deviceName,bean.getName());
                            startActivity(intent);
                            finish();
                        }
                    }, new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError aylaError) {
                            Log.e(TAG,"aylaError = " + aylaError.getMessage());
                            mLoadingDialog.dismiss();
                        }
                    });
                }
            }
        });
        return dialog;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data != null){
            String irCode = data.getStringExtra("irCode");
            if (irCode != null && !irCode.isEmpty()){
                dataList.add(new CustomItemBean(mKeyName,irCode));
                mRemoteAdapter.notifyDataSetChanged();
            }
        }
    }

    private void setDialogGravity(Dialog dialog,int gravity,double percentParent) {
        // 设置宽度为屏宽、靠近屏幕底部。
        Window window = dialog.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        WindowManager.LayoutParams wlp = window.getAttributes();
        Display d = window.getWindowManager().getDefaultDisplay();//获取屏幕宽
        wlp.width = (int) (d.getWidth()*percentParent);//宽度按屏幕大小的百分比设置
        wlp.y = 50; //如果是底部显示，则距离底部的距离是20
        wlp.gravity = gravity;
        window.setAttributes(wlp);
    }

    private void updateIrCode(String irCode) {
        Log.d(TAG,"ircode = " + irCode);
        mAylaProperty.createDatapoint(irCode, null, new Response.Listener<AylaDatapoint>() {
            @Override
            public void onResponse(AylaDatapoint response) {
                ToastUtils.Toast_long("码率上传成功！");
            }
        }, new ErrorListener() {
            @Override
            public void onErrorResponse(AylaError aylaError) {
                ToastUtils.Toast_long(aylaError.getMessage());
                Log.e(TAG, "aylaError  = " + aylaError.getMessage());
            }
        });

    }

}
