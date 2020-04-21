package com.dream.onehome.ui.Activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;
import com.dream.onehome.common.Const;
import com.dream.onehome.constract.IDialogLisrener;
import com.dream.onehome.dialog.DialogUtils;
import com.dream.onehome.utils.DeviceUtils;
import com.dream.onehome.utils.EditTextUtils;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.ToastUtils;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.sunseaiot.phoneservice.PhoneServerManager;

import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Time:2019/12/12
 * Author:TiaoZi
 */
public class RegisterValiActivity extends BaseActivity {
    @BindView(R.id.userphone_edt)
    EditText userphoneEdt;
    @BindView(R.id.line_phone)
    View linePhone;
    @BindView(R.id.password_edt)
    EditText passwordEdt;
    @BindView(R.id.line_pwd)
    View linePwd;
    @BindView(R.id.back_iv)
    ImageView backIv;
    @BindView(R.id.logo_iv)
    ImageView logoIv;
    @BindView(R.id.varify_tv)
    TextView varifyTv;
    @BindView(R.id.varify_edt)
    EditText varifyEdt;
    @BindView(R.id.onvarcode_tv)
    TextView onvarcodeTv;
    @BindView(R.id.line_vary)
    View lineVary;
    @BindView(R.id.pwd_lay)
    LinearLayout pwdLay;
    @BindView(R.id.login)
    Button login;
    @BindView(R.id.wechat_icon)
    TextView wechatIcon;
    @BindView(R.id.password_icon)
    TextView passwordIcon;

    private final int COUNTER = 0;
    private final int RESEND = 1;

    private int time = 60;
    private boolean isActive;
    private boolean isResetPwd;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case COUNTER :
                    if (time > 0) {
                        onvarcodeTv.setText(new StringBuilder().append(time).append("s").toString());
                        time--;
                        mHandler.sendEmptyMessageDelayed(COUNTER, 1000);
                    } else {
                        resendCode();
                    }
                    break;
                case RESEND :
                    onvarcodeTv.setText("重新发送");
                    time = 60;
                    break;
            }
        }
    };



    @Override
    public int getLayoutId() {
        return R.layout.activity_register;
    }

    @Override
    public void initView() {
        ImmersionBar.with(this).statusBarDarkFont(true).hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR).fitsSystemWindows(true).barColor(R.color.colorPrimaryDark).keyboardEnable(true).init();

        String phone = getIntent().getStringExtra(Const.PHONE);
        if (phone != null && !phone.isEmpty()) {
            userphoneEdt.setText(phone);
            login.setText("确定");
            isResetPwd = true;
        }
        EditTextUtils.setEditTextLimitInputChat(userphoneEdt, 11);
        EditTextUtils.setEditTextLimitInputChat(varifyEdt, 4);
    }

    private void resendCode() {
        mHandler.sendEmptyMessage(RESEND);
    }

    @Override
    public void OnResume() {
        isActive = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mHandler != null) {
            mHandler = null;
            time = 0;
        }
    }

    @Override
    public void loadDatas() {

    }

    @Override
    public void eventListener() {
        userphoneEdt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    linePhone.setBackgroundColor(getResources().getColor(R.color.color_them));
                    linePwd.setBackgroundColor(getResources().getColor(R.color.color666));
                    lineVary.setBackgroundColor(getResources().getColor(R.color.color666));
                }
            }
        });

        passwordEdt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    lineVary.setBackgroundColor(getResources().getColor(R.color.color666));
                    linePhone.setBackgroundColor(getResources().getColor(R.color.color666));
                    linePwd.setBackgroundColor(getResources().getColor(R.color.color_them));
                }
            }
        });

        varifyEdt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                lineVary.setBackgroundColor(getResources().getColor(R.color.color_them));
                linePhone.setBackgroundColor(getResources().getColor(R.color.color666));
                linePwd.setBackgroundColor(getResources().getColor(R.color.color666));
            }
        });

        varifyEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!userphoneEdt.getText().toString().isEmpty() && !phone.isEmpty() && s.length() == 4) {
                    pwdLay.setVisibility(View.VISIBLE);
//                    resendCode();
                } else {
                    pwdLay.setVisibility(View.GONE);
                }
            }
        });

        passwordEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= 6 && !phone.isEmpty()) {
                    login.setBackgroundResource(R.drawable.select_surebtn);
                    login.setEnabled(true);
                } else {
                    login.setBackgroundResource(R.drawable.shape_voice_unenable_btn);
                    login.setEnabled(false);
                }
            }
        });
        userphoneEdt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= 11) {
                    if (!DeviceUtils.isMobile(s.toString())) {
                        ToastUtils.Toast_long("请输入正确的手机号");
                    }
                }
            }
        });
    }


    private String phone = "";

    @OnClick({R.id.back_iv, R.id.onvarcode_tv, R.id.login})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.onvarcode_tv:
                phone = userphoneEdt.getText().toString();
                if (phone.isEmpty()) {
                    ToastUtils.Toast_long(this, "请先输入手机号");
                } else if (!DeviceUtils.isMobile(phone)) {
                    ToastUtils.Toast_long(this, "请输入正确的手机号");
                } else {
                    String type = Const.TYPE_register;
                    if (isResetPwd) {
                        type = Const.TYPE_forgotpwd;
                    }
                    PhoneServerManager.getInstance().getSmsCode(phone, Const.APP_NAME, Const.oemId, Const.APP_ID, Const.AYLA_SECRET, type,
                            "chinese", Const.AreaCode, Const.AYLA_Appid, new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                                @Override
                                public void onResponse(AylaAPIRequest.EmptyResponse response) {
                                    Log.d(getLocalClassName(), "getSmsCode  onResponse ..");
//                                    resendCode();
                                }
                            }, new ErrorListener() {
                                @Override
                                public void onErrorResponse(AylaError error) {
                                    LogUtils.e(error.getMessage());
                                    if (error.getMessage().contains("phone registered")) {
                                        ToastUtils.Toast_long("手机号已注册");
                                        resendCode();
                                    }

                                    if (error.getMessage().contains("phone not exist")) {//手机号未注册 弹窗提示
                                        DialogUtils.getTipDialog(mContext, "温馨提示", "手机号未注册，是否确认注册？", new IDialogLisrener() {
                                            @Override
                                            public void onCancel() {
                                                resendCode();
                                            }

                                            @Override
                                            public void onSure() {
                                                PhoneServerManager.getInstance().getSmsCode(phone, Const.APP_NAME, Const.oemId, Const.APP_ID, Const.AYLA_SECRET, Const.TYPE_register,
                                                        "chinese", Const.AreaCode, Const.AYLA_Appid, new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                                                            @Override
                                                            public void onResponse(AylaAPIRequest.EmptyResponse response) {
                                                                Log.d(getLocalClassName(), "getSmsCode  onResponse ..");
                                                                login.setText("注册");
                                                                isResetPwd = false;
                                                            }
                                                        }, new ErrorListener() {
                                                            @Override
                                                            public void onErrorResponse(AylaError error) {
                                                                LogUtils.e(error.getMessage());
                                                            }
                                                        });
                                            }
                                        }).show();
                                    }
                                }
                            });
                    mHandler.sendEmptyMessage(COUNTER);
                    varifyEdt.requestFocus();
                }
                break;
            case R.id.login:
                String pwd = passwordEdt.getText().toString();
                String phoneNum = userphoneEdt.getText().toString();
                String valiCode = varifyEdt.getText().toString();
                String nickName = "用户" + phoneNum.substring(phoneNum.length() - 4, phoneNum.length());
                Log.d("testLog", "nickName = " + nickName);
                if (phoneNum.isEmpty()) {
                    ToastUtils.Toast_long(this, "请先输入手机号");
                } else if (!DeviceUtils.isMobile(phoneNum)) {
                    ToastUtils.Toast_long(this, "请输入正确的手机号");
                } else if (!phone.equals(phoneNum)) {
                    if (phone.isEmpty()) {
                        ToastUtils.Toast_long(this, "请先获取验证码");
                    } else {
                        ToastUtils.Toast_long(this, "请重新获取验证码");
                    }
                } else if (checkInput()) {

                    if (isResetPwd) {
                        PhoneServerManager.getInstance().requestPasswordUpdate(valiCode, pwd, phoneNum, Const.oemId, Const.APP_ID, Const.AYLA_SECRET, new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                            @Override
                            public void onResponse(AylaAPIRequest.EmptyResponse response) {
                                ToastUtils.Toast_long("重置成功");
                                setResultAction(phoneNum, pwd);
                            }
                        }, new ErrorListener() {
                            @Override
                            public void onErrorResponse(AylaError aylaError) {

                            }
                        });
                    } else {

                        PhoneServerManager.getInstance().idpSignUp(phoneNum, pwd, phoneNum, "yk", nickName, valiCode, Const.AreaCode,
                                Const.oemId, new Response.Listener<PhoneServerManager.IdentityProviderAuth.UserBean>() {
                                    @Override
                                    public void onResponse(PhoneServerManager.IdentityProviderAuth.UserBean response) {
                                        ToastUtils.Toast_long("注册成功");
                                        setResultAction(phoneNum, pwd);
                                    }
                                }, new ErrorListener() {
                                    @Override
                                    public void onErrorResponse(AylaError error) {
                                        Log.d(getLocalClassName(), "idpSignUp  error = " + error.getMessage());
                                        if (error.getMessage().contains("code wrong")) {
                                            ToastUtils.Toast_long("验证码错误");
                                        }
//                                        com.aylanetworks.aylasdk.error.ServerError: Server error: 500 {"code":"1011","msg":"sms code wrong!"}
                                    }
                                });
                    }

                }
                break;
        }
    }

    private void setResultAction(String phoneNum, String pwd) {
        Intent intent = getIntent();
        intent.putExtra(Const.PHONE, phoneNum);
        intent.putExtra(Const.PWD, pwd);
        setResult(1213, intent);
        onBackPressed();
    }

    private boolean checkInput() {
        String pwd = passwordEdt.getText().toString();
        String phoneNum = userphoneEdt.getText().toString();
        String valiCode = varifyEdt.getText().toString();
        if (phoneNum.isEmpty()) {
            ToastUtils.Toast_long("请输入手机号");
            return false;
        }
        if (valiCode.isEmpty()) {
            ToastUtils.Toast_long("请输入验证码");
            return false;
        }
        if (pwd.isEmpty()) {
            ToastUtils.Toast_long("请输入密码");
            return false;
        }

        return true;
    }

}
