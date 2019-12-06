package com.dream.onehome.ui.Activity;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Toast;

import com.dream.onehome.R;
import com.dream.onehome.base.BaseActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

public class MainActivity extends BaseActivity {


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
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
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

}
