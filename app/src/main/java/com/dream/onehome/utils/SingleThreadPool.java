package com.dream.onehome.utils;

import android.os.Handler;


import com.dream.onehome.constract.IResultListener;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SingleThreadPool extends ThreadPoolExecutor {

    private static SingleThreadPool mThreadPool;
    private IResultListener<Object> mListener;
    private android.os.Handler mHandler;
    public SingleThreadPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public static synchronized SingleThreadPool getInstance() {
        if (mThreadPool == null) {
            mThreadPool = new SingleThreadPool(10, 20, 10,
                    TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
                    Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        }
        return mThreadPool;
    }

    public void startPhotoTime(Handler handler) {
        mHandler = handler;
        mThreadPool.remove(mRunnable);
        mThreadPool.execute(mRunnable);
    }

    public SingleThreadPool setOnRuningListner(IResultListener<Object> listner) {
        mListener = listner;
        return this;
    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mListener.onResult(1);
            mHandler.postDelayed(this,1000);
        }
    };

}
