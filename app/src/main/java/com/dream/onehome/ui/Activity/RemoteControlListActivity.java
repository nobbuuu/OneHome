package com.dream.onehome.ui.Activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaDatum;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.adapter.RemoteControlListAdapter;
import com.dream.onehome.base.BaseMVVMActivity;
import com.dream.onehome.base.NoDoubleClickListener;
import com.dream.onehome.base.NoViewModel;
import com.dream.onehome.bean.RemoteControlBean;
import com.dream.onehome.common.Const;
import com.dream.onehome.databinding.ActivityRemotecontrollistBinding;
import com.dream.onehome.utils.LogUtils;
import com.dream.onehome.utils.SpUtils;
import com.dream.onehome.utils.annotations.ContentView;
import com.google.gson.Gson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Time:2019/12/20
 * Author:TiaoZi
 */
@ContentView(R.layout.activity_remotecontrollist)
public class RemoteControlListActivity extends BaseMVVMActivity<NoViewModel, ActivityRemotecontrollistBinding> {


    private RemoteControlListAdapter mListAdapter;
    private List<RemoteControlBean> dataList = new ArrayList<>();

    private String dsn;
    @Override
    protected void initIntent() {
        dsn = (String) SpUtils.getParam(Const.DSN,"");
        mListAdapter = new RemoteControlListAdapter(RemoteControlListActivity.this, dataList, R.layout.rvitem_remotecontrol);
        bindingView.remoteRv.setAdapter(mListAdapter);
    }

    @Override
    protected void onEvent() {
        bindingView.addimgIv.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View view) {
                startActivity(new Intent(getBaseContext(), SelectDeviceTypeActivity.class));
            }
        });

        bindingView.backIv.setOnClickListener(new NoDoubleClickListener() {
            @Override
            public void onNoDoubleClick(View v) {
                onBackPressed();
            }
        });

        mListAdapter.setDeleteListener(new RemoteControlListAdapter.IActionListener() {
            @Override
            public void onResult() {
                refreshData();
            }
        });

        bindingView.remoteRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                Log.d("xy","dx = " + dx);
                Log.d("xy","dy = " + dy);
            }
        });
    }

    @Override
    protected void initView(Bundle savedInstanceState) {

    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
    }

    private void refreshData() {
        if (!dsn.isEmpty()) {
            AylaSessionManager sessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
            if (sessionManager != null){
                AylaDevice aylaDevice = sessionManager.getDeviceManager().deviceWithDSN(dsn);
                if (aylaDevice != null){
                    aylaDevice.fetchAylaDatums(new Response.Listener<AylaDatum[]>() {
                        @Override
                        public void onResponse(AylaDatum[] response) {
                            Log.d("AylaLog", "response.length = " + response.length);
                            Gson gson = new Gson();
                            dataList.clear();
                            for (int i = 0; i < response.length; i++) {
                                RemoteControlBean remoteControlBean = gson.fromJson(response[i].getValue(), RemoteControlBean.class);
                                if (remoteControlBean != null) {
                                    dataList.add(remoteControlBean);
                                }
                            }
                            mListAdapter.notifyDataSetChanged();
                        }
                    }, new ErrorListener() {
                        @Override
                        public void onErrorResponse(AylaError aylaError) {
                            LogUtils.e(aylaError.getMessage());
                        }
                    });
                }
            }
        }
    }
}
