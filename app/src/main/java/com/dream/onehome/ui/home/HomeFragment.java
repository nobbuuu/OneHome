package com.dream.onehome.ui.home;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.Response;
import com.aylanetworks.aylasdk.AylaDatum;
import com.aylanetworks.aylasdk.AylaDevice;
import com.aylanetworks.aylasdk.AylaDeviceManager;
import com.aylanetworks.aylasdk.AylaNetworks;
import com.aylanetworks.aylasdk.AylaSessionManager;
import com.aylanetworks.aylasdk.change.ListChange;
import com.aylanetworks.aylasdk.error.AylaError;
import com.aylanetworks.aylasdk.error.ErrorListener;
import com.dream.onehome.R;
import com.dream.onehome.adapter.DeviceAdapter;
import com.dream.onehome.base.BaseFragment;
import com.dream.onehome.common.Const;
import com.dream.onehome.ui.Activity.AddDeviceActivity;
import com.dream.onehome.ui.Activity.RemoteControlListActivity;
import com.dream.onehome.utils.LogUtils;

import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.OnClick;

public class HomeFragment extends BaseFragment {

    @BindView(R.id.addimg_iv)
    ImageView addimgIv;
    @BindView(R.id.device_rv)
    RecyclerView deviceRv;

    private HomeViewModel homeViewModel;

    @Override
    public void initView() {

    }

    @Override
    public int getLayoutId() {
        return R.layout.fragment_home;
    }

    @Override
    public void resume() {

        AylaSessionManager sessionManager = AylaNetworks.sharedInstance().getSessionManager(Const.APP_NAME);
        if (sessionManager != null) {
            AylaDeviceManager deviceManager = sessionManager.getDeviceManager();
            if (deviceManager != null) {
                List<AylaDevice> devices = deviceManager.getDevices();
                Log.d("AylaLog","devices.size() = " + devices.size());
                if (devices != null) {
                    deviceRv.setAdapter(new DeviceAdapter(getContext(),devices,R.layout.rvitem_device));
                }
                deviceManager.addListener(new AylaDeviceManager.DeviceManagerListener() {
                    @Override
                    public void deviceManagerInitComplete(Map<String, AylaError> map) {

                    }

                    @Override
                    public void deviceManagerInitFailure(AylaError aylaError, AylaDeviceManager.DeviceManagerState deviceManagerState) {

                    }

                    @Override
                    public void deviceListChanged(ListChange listChange) {
                        Log.d("AylaLog","listChange = " + listChange);
                        if (listChange != null && devices.size() == 0){
                            List<AylaDevice> addedItems = listChange.getAddedItems();
                            if (addedItems != null && deviceRv != null) {
                                DeviceAdapter deviceAdapter = new DeviceAdapter(getContext(), addedItems, R.layout.rvitem_device);
                                deviceRv.setAdapter(deviceAdapter);
                            }
                        }
                    }

                    @Override
                    public void deviceManagerError(AylaError aylaError) {

                    }

                    @Override
                    public void deviceManagerStateChanged(AylaDeviceManager.DeviceManagerState deviceManagerState, AylaDeviceManager.DeviceManagerState deviceManagerState1) {
                        Log.d("AylaLog","deviceManagerStateChanged = " + deviceManagerState.name());

                    }
                });

            }
        }
    }

    @Override
    public void onEvents() {

    }

    @Override
    public void initDta() {

    }


    @OnClick({R.id.addimg_iv,R.id.adddevice_iv})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.addimg_iv:
            case R.id.adddevice_iv:
                Intent intent = new Intent(getActivity(), AddDeviceActivity.class);
                startActivity(intent);
                break;
        }
    }
}