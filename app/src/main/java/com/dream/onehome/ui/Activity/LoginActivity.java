package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.auth.AylaAuthorization;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;
import com.dream.onehome.common.Const;
import com.dream.onehome.utils.DeviceUtils;
import com.dream.onehome.utils.EditTextUtils;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.ToastUtils;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.ldoublem.loadingviewlib.view.LVGhost;
import com.sunseaiot.phoneservice.PhoneAuthProvider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.OnClick;

public class LoginActivity extends BaseActivity {

    @BindView(R.id.varify_tv)
    TextView varifyTv;
    @BindView(R.id.userphone_edt)
    EditText userphoneEdt;
    @BindView(R.id.password_edt)
    EditText passwordEdt;
    @BindView(R.id.line_phone)
    View linePhone;
    @BindView(R.id.line_pwd)
    View linePwd;
    @BindView(R.id.login)
    Button login;

    @Override
    public int getLayoutId() {
        return R.layout.activity_login;
    }

    @Override
    public void initView() {
        ImmersionBar.with(this).statusBarDarkFont(true).hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR).fitsSystemWindows(true).barColor(R.color.colorPrimaryDark).keyboardEnable(true).init();
        String phone = (String) SpUtils.getParam(Const.PHONE, "");
        String pwd = (String) SpUtils.getParam(Const.PWD, "");

        if (!phone.isEmpty()){
            userphoneEdt.setText(phone);
        }else {
            phone = userphoneEdt.getText().toString();
        }

        if (!pwd.isEmpty()){
            passwordEdt.setText(pwd);
        }else {
            pwd = passwordEdt.getText().toString();
        }

        if (pwd.length() >= 6) {
            login.setBackgroundResource(R.drawable.select_surebtn);
            login.setEnabled(true);
        }

        EditTextUtils.setEditTextLimitInputChat(userphoneEdt,11);
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
                }
            }
        });

        passwordEdt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    linePwd.setBackgroundColor(getResources().getColor(R.color.color_them));
                    linePhone.setBackgroundColor(getResources().getColor(R.color.color666));
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
                if (s.length() >= 6) {
                    login.setBackgroundResource(R.drawable.select_surebtn);
                    login.setEnabled(true);
                }
            }
        });
    }

    @OnClick({R.id.back_iv, R.id.login, R.id.onvarcode_tv, R.id.password_icon, R.id.register_tv})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.login:
                String phoneNum = userphoneEdt.getText().toString();
                String password = passwordEdt.getText().toString();
                if (phoneNum.isEmpty()) {
                    ToastUtils.Toast_long("请输入手机号");
                } else if (!DeviceUtils.isMobile(phoneNum)){
                    ToastUtils.Toast_long(this, "请输入正确的手机号");
                } else if (password.isEmpty()) {
                    ToastUtils.Toast_long("请输入密码");
                } else {
                    PhoneAuthProvider phoneAuthProvider = new PhoneAuthProvider(phoneNum, password);
                    AylaNetworks.sharedInstance().getLoginManager().signIn(phoneAuthProvider, Const.APP_NAME, new Response.Listener<AylaAuthorization>() {
                        @Override
                        public void onResponse(AylaAuthorization response) {
                            Log.d(getLocalClassName(),"response = " + response.getAccessToken());
                            SpUtils.savaUserInfo(Const.TOKEN,phoneNum);
                            SpUtils.savaUserInfo(Const.PWD,password);
                            ToastUtils.Toast_long("登录成功");
                            mLoading.dismiss();
                            startActivity(new Intent(getBaseContext(),MainActivity.class));
                            finish();
                        }
                    }, new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError error) {
                            Log.d(getLocalClassName(),"error = " + error.getMessage());
                            if (error.getMessage().contains("password is wrong")){
                                ToastUtils.Toast_long("密码不正确");
                            }
                            if (error.getMessage().contains("phone not exist")){
                                ToastUtils.Toast_long("手机号未注册");
                            }
                            mLoading.dismiss();
                        }
                    });
                    mLoading.show();
                }
                break;
            case R.id.onvarcode_tv://忘记密码
                Intent intent = new Intent(this, RegisterValiActivity.class);
                intent.putExtra(Const.PHONE,userphoneEdt.getText().toString());
                startActivityForResult(intent,1213);

                break;
            case R.id.password_icon:
                startActivity(new Intent(this, PasswordLoginActivity.class));
                break;
            case R.id.register_tv:
                startActivityForResult(new Intent(this, RegisterValiActivity.class),1213);
                break;
        }
    }

    /**
     * 禁止EditText输入空格
     * @param editText
     */
    public static void setEditTextInhibitInputSpace(EditText editText){
        InputFilter filter=new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                if(source.equals(" "))return "";
                else return null;
            }
        };
        editText.setFilters(new InputFilter[]{filter});
    }

    /**
     * 禁止EditText输入特殊字符
     * @param editText
     */
    public static void setEditTextInhibitInputSpeChat(EditText editText){

        InputFilter filter=new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String speChat="[`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
                Pattern pattern = Pattern.compile(speChat);
                Matcher matcher = pattern.matcher(source.toString());
                if(matcher.find())return "";
                else return null;
            }
        };
        editText.setFilters(new InputFilter[]{filter});
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == 1213 && data != null){
            String phone = data.getStringExtra(Const.PHONE);
            String pwd = data.getStringExtra(Const.PWD);
            if (phone!=null){
                userphoneEdt.setText(phone);
//                passwordEdt.requestFocus();
            }

            if (pwd != null){
                passwordEdt.setText(pwd);
            }
        }
    }
}
