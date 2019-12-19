package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;

import butterknife.BindView;
import butterknife.OnClick;

/**
 * Time:2019/12/12
 * Author:TiaoZi
 */
public class PasswordLoginActivity extends BaseActivity {
    @BindView(R.id.back_iv)
    ImageView backIv;
    @BindView(R.id.logo_iv)
    ImageView logoIv;
    @BindView(R.id.varify_tv)
    TextView varifyTv;
    @BindView(R.id.userphone_edt)
    EditText userphoneEdt;
    @BindView(R.id.line_phone)
    View linePhone;
    @BindView(R.id.password_edt)
    EditText passwordEdt;
    @BindView(R.id.onvarcode_tv)
    TextView onvarcodeTv;
    @BindView(R.id.line_pwd)
    View linePwd;
    @BindView(R.id.login)
    Button login;
    @BindView(R.id.wechat_icon)
    TextView wechatIcon;
    @BindView(R.id.password_icon)
    TextView passwordIcon;
    @BindView(R.id.container)
    ConstraintLayout container;

    @Override
    public int getLayoutId() {
        return R.layout.activity_pwd_login;
    }

    @Override
    public void initView() {

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


    @OnClick({R.id.back_iv, R.id.onvarcode_tv, R.id.login})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.back_iv:
                onBackPressed();
                break;
            case R.id.onvarcode_tv:
                startActivity(new Intent(this, RegisterValiActivity.class));
                break;
            case R.id.login:
                break;
        }
    }
}
