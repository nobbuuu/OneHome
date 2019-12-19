package com.dream.onehome.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.dream.onehome.common.OneHomeAplication;
import com.dream.onehome.listener.NoDoubleClickListener;

import java.io.File;

public class BaseViewHolder {
    private final SparseArray<View> mViews;  
    private int mPosition;  
    public View itemView;
  
    private BaseViewHolder(Context context, ViewGroup parent, int layoutId,
                           int position)
    {  
        this.mPosition = position;  
        this.mViews = new SparseArray<View>();
        itemView = LayoutInflater.from(context).inflate(layoutId, parent, false);
        // setTag  
        itemView.setTag(this);
    }  
  
    /** 
     * 拿到一个ViewHolder对象 
     *  
     * @param context 
     * @param convertView 
     * @param parent 
     * @param layoutId 
     * @param position 
     * @return 
     */  
    public static BaseViewHolder get(Context context, View convertView,
                                     ViewGroup parent, int layoutId, int position)
    {  
        if (convertView == null)  
        {  
            return new BaseViewHolder(context, parent, layoutId, position);
        }  
        return (BaseViewHolder) convertView.getTag();
    }  
  
    /**
     * 通过控件的Id获取对于的控件，如果没有则加入views 
     *  
     * @param viewId 
     * @return 
     */  
    public <T extends View> T getView(int viewId)  
    {  
        View view = mViews.get(viewId);  
        if (view == null)  
        {  
            view = itemView.findViewById(viewId);
            mViews.put(viewId, view);  
        }  
        return (T) view;  
    }  
  

    public BaseViewHolder setOnClickListener(int viewId, NoDoubleClickListener listener)
    {
        View view = getView(viewId);
        view.setOnClickListener(listener);
        return this;
    }

    /**
     * 为TextView设置字符串
     *
     * @param viewId
     * @param text
     * @return
     */
    public BaseViewHolder setText(int viewId, String text)
    {
        TextView view = getView(viewId);
        view.setText(text);
        return this;
    }

    /** 
     * 为ImageView设置图片 
     *  
     * @param viewId 
     * @param drawableId 
     * @return 
     */  
    public BaseViewHolder setImageResource(int viewId, int drawableId)
    {  
        ImageView view = getView(viewId);  
        view.setImageResource(drawableId);  
  
        return this;  
    }  
  
    /** 
     * 为ImageView设置图片 
     *  
     * @param viewId 
     * @return
     */  
    public BaseViewHolder setImageBitmap(int viewId, Bitmap bm)
    {  
        ImageView view = getView(viewId);  
        view.setImageBitmap(bm);  
        return this;  
    }  
  
    /** 
     * 为ImageView设置图片 
     *  
     * @param viewId 
     * @return
     */  
    public BaseViewHolder setImageByUrl(int viewId, String url,boolean isCircle)
    {  
       /* ImageLoader.getInstance(3, Type.LIFO).loadImage(url,
                (ImageView) getView(viewId));*/
        OneHomeAplication instance = OneHomeAplication.getInstance();
        if (isCircle){
            Glide.with(instance)
                    .load(url)
//                    .transform(new GlideCircleTransform(instance))
                    .into((ImageView) getView(viewId));
        }else {
            Glide.with(instance)
                    .load(url)
                    .into((ImageView) getView(viewId));
        }
        return this;
    }  
    /**
     * 为ImageView设置图片
     *
     * @param viewId
     * @return
     */
    public BaseViewHolder setImageByUrl(int viewId, String url)
    {
       /* ImageLoader.getInstance(3, Type.LIFO).loadImage(url,
                (ImageView) getView(viewId));*/
        OneHomeAplication instance = OneHomeAplication.getInstance();
       /* Glide.with(instance)
                .load(Const.BASE_PICURL+PicUrlUtils.getRealUrl(url))
                .into((ImageView) getView(viewId));*/
        return this;
    }
    /**
     * 为ImageView设置图片
     *
     * @param viewId
     * @return
     */
    public BaseViewHolder setImageByFile(int viewId, File mFile)
    {
        OneHomeAplication instance = OneHomeAplication.getInstance();
        Glide.with(instance)
                .load(mFile)
                .into((ImageView) getView(viewId));
        return this;
    }

    public int getPosition()  
    {  
        return mPosition;  
    }  
  
}  