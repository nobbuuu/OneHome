package com.dream.onehome.ui.Activity;

import android.content.Intent;
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
import com.dream.onehome.utils.ToastUtils;
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

    private int time = 60;

    private CountDownTimer mCountDownTimer;

    @Override
    public int getLayoutId() {
        return R.layout.activity_register;
    }

    @Override
    public void initView() {
        mCountDownTimer = new CountDownTimer(6000,1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                onvarcodeTv.setText(time-- + "s");
            }

            @Override
            public void onFinish() {
                onvarcodeTv.setText("重新发送");
                mCountDownTimer.cancel();
            }
        };

    }

    @Override
    public void OnResume() {

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
                if (!userphoneEdt.getText().toString().isEmpty() && s.length() == 4) {
                    pwdLay.setVisibility(View.VISIBLE);
                    onvarcodeTv.setText("重新发送");
                    mCountDownTimer.cancel();
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
                if (s.length()>= 6){
                    login.setBackgroundResource(R.drawable.select_surebtn);
                    login.setEnabled(true);
                }
            }
        });
    }


    @OnClick({R.id.back_iv, R.id.onvarcode_tv, R.id.login})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.onvarcode_tv:
                String phone = userphoneEdt.getText().toString();
                if (phone.isEmpty()) {
                    ToastUtils.Toast_long(this, "请先输入手机号");
                } else {
                    PhoneServerManager.getInstance().getSmsCode(phone, Const.APP_NAME, Const.oemId, Const.APP_ID, Const.AYLA_SECRET, Const.TYPE_register,
                            "chinese", Const.AreaCode, Const.AYLA_Appid, new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                                @Override
                                public void onResponse(AylaAPIRequest.EmptyResponse response) {
                                    Log.d(getLocalClassName(), "getSmsCode  onResponse ..");

                                }
                            }, new ErrorListener() {
                                @Override
                                public void onErrorResponse(AylaError error) {
                                    Log.d(getLocalClassName(), "getSmsCode  onErrorResponse ..");
                                    Log.d(getLocalClassName(), "error = " + error.getMessage());
                                }
                            });
                    mCountDownTimer.start();
                    varifyEdt.requestFocus();
                }
                break;
            case R.id.login:
                String pwd = passwordEdt.getText().toString();
                String phoneNum = userphoneEdt.getText().toString();
                String valiCode = varifyEdt.getText().toString();
                if (pwd.isEmpty()){
                    ToastUtils.Toast_long("请输入密码");
                }else {
                    PhoneServerManager.getInstance().idpSignUp(phoneNum, pwd, "条子", "tiaozi", "axiba", valiCode, Const.AreaCode,
                            Const.oemId, new Response.Listener<PhoneServerManager.IdentityProviderAuth.UserBean>() {
                                @Override
                                public void onResponse(PhoneServerManager.IdentityProviderAuth.UserBean response) {
                                    Log.d(getLocalClassName(), "idpSignUp  onResponse ..");
                                    ToastUtils.Toast_long("注册成功");
                                    Intent intent = getIntent();
                                    intent.putExtra("phone",phoneNum);
                                    setResult(1213,intent);
                                    onBackPressed();
                                }
                            }, new ErrorListener() {
                                @Override
                                public void onErrorResponse(AylaError error) {
                                    Log.d(getLocalClassName(), "idpSignUp  error = " + error.getMessage());
                                    ToastUtils.Toast_long(error.getMessage());
                                }
                            });
                }
                break;
        }
    }

}
