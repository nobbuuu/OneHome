package com.dream.onehome.dialog;

import android.app.Dialog;
import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.dream.onehome.R;
import com.dream.onehome.adapter.ExtGvAdapter;
import com.dream.onehome.bean.KeysBean;
import com.dream.onehome.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Time:2020311
 * Author:TiaoZi
 */
public class ExtDialog extends Dialog {

    private ExtGvAdapter mExtGvAdapter;
    private List<String> dataList = new ArrayList<>();
    public ExtDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_extention);
        setCancelable(false);
        DialogUtils.setDialogGravity(this, Gravity.BOTTOM,1);
        GridView extGv = findViewById(R.id.extention_gv);
        ImageView closeIv = findViewById(R.id.closeiv);
        closeIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        extGv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ToastUtils.Toast_long(""+dataList.get(position));
            }
        });
        mExtGvAdapter = new ExtGvAdapter(context,dataList,R.layout.rvitem_extention);
        extGv.setAdapter(mExtGvAdapter);

    }

    public ExtDialog setData(KeysBean keysBean){
        dataList.clear();
        dataList.addAll(keysBean.getKeylist());
        mExtGvAdapter.notifyDataSetChanged();
        return this;
    }

}
