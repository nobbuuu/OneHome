package com.dream.onehome.ui.mine;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaAPIRequest;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.common.Const;
import com.dream.onehome.ui.Activity.LoginActivity;
import com.dream.onehome.utils.ToastUtils;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;

public class MineFragment extends Fragment {

    private MineViewModel viewModel;
    private AylaSessionManager mSessionManager;
    private static final String TAG = "AylaLog";

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        viewModel = ViewModelProviders.of(this).get(MineViewModel.class);
        View root = inflater.inflate(R.layout.fragment_mine, container, false);
        mSessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
        RelativeLayout setLay = root.findViewById(R.id.signout);

        setLay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ( mSessionManager != null){
                    mSessionManager.shutDown(new Response.Listener<AylaAPIRequest.EmptyResponse>() {
                        @Override
                        public void onResponse(AylaAPIRequest.EmptyResponse response) {
                            ToastUtils.Toast_long("退出成功");
                            Intent intent = new Intent(getContext(), LoginActivity.class);
                            if (getActivity() != null) {
                                startActivity(intent);
                                getActivity().finish();
                            } 
                        }
                    }, new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError aylaError) {

                        }
                    });
                }else {
                    ToastUtils.Toast_long("账号异常  请重新登录");
                    Intent intent = new Intent(getContext(),LoginActivity.class);
                    startActivity(intent);
                    getActivity().finish();
                }
            }
        });

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //沉浸式
        ImmersionBar.with(this).statusBarDarkFont(true).hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR).keyboardEnable(true).init();
    }
}