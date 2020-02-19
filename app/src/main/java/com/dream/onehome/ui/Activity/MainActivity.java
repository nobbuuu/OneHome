package com.dream.onehome.ui.Activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;
import com.dream.onehome.common.Const;
import com.dream.onehome.utils.LocationUtil;
import com.dream.onehome.utils.SP;
import com.dream.onehome.utils.SpUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

public class MainActivity extends BaseActivity {

    private static final String TAG = "location";

    @Override
    public int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    public void initView() {
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home,R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);

        DisplayMetrics metrics = this.getResources().getDisplayMetrics();
        float density = metrics.density;
        int dpi = metrics.densityDpi;
        int heightPixels = metrics.heightPixels;
        int widthPixels = metrics.widthPixels;
        String screenInfo = "比例:" + density + "dpi:" + dpi + "高像素:" + heightPixels + "宽像素:" + widthPixels;
        Toast.makeText(this,screenInfo,Toast.LENGTH_LONG);
        Log.e("---metrics---", screenInfo);
        requestPermission();
    }

    private void initLocation() {
        LocationUtil.getCurrentLocation(this, new LocationUtil.LocationCallBack() {
            @Override
            public void onSuccess(Location location) {
                Log.d(TAG,"Longitude = " + location.getLongitude() +  "  Latitude = " + location.getLatitude());
                SpUtils.savaUserInfo(Const.Latitude,String.valueOf(location.getLatitude()));
                SpUtils.savaUserInfo(Const.Longitude,String.valueOf(location.getLongitude()));
            }

            @Override
            public void onFail(String msg) {
                Log.e(TAG,"errorMsg = " + msg);

            }
        });
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

    public void requestPermission() {
        // checkSelfPermission 判断是否已经申请了此权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            //如果应用之前请求过此权限但用户拒绝了请求，shouldShowRequestPermissionRationale将返回 true。
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, 121);
            }
        }else {
            initLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 121){
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED){
                initLocation();
            }else {

            }
        }
    }

}
