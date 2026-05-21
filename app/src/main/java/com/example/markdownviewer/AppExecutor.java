package com.example.markdownviewer;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutor {

    private static volatile AppExecutor sInstance;

    private final ExecutorService mDiskIO;
    private final Handler mMainThread;

    private AppExecutor() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        mDiskIO = Executors.newFixedThreadPool(Math.max(2, cpuCount));
        mMainThread = new Handler(Looper.getMainLooper());
    }

    public static AppExecutor getInstance() {
        if (sInstance == null) {
            synchronized (AppExecutor.class) {
                if (sInstance == null) {
                    sInstance = new AppExecutor();
                }
            }
        }
        return sInstance;
    }

    public ExecutorService diskIO() {
        return mDiskIO;
    }

    public Handler mainThread() {
        return mMainThread;
    }

    public void shutdown() {
        mDiskIO.shutdownNow();
    }
}
