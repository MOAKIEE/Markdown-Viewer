package com.example.markdownviewer;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 全局共享的异步任务调度管理器。
 * 采用双重检查锁定 (Double-Checked Locking) 保证线程安全的单例模式。
 * 避免了频繁创建和销毁临时线程带来的系统开销。
 */
public final class AppExecutor {

    private static volatile AppExecutor sInstance;

    private final ExecutorService mDiskIO;
    private final Handler mMainThread;

    private AppExecutor() {
        // 基于可用的 CPU 核心数自动计算线程池大小，保证多核并发效能，最小核心数为 2
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

    /**
     * 获取用于磁盘读取、大纲解析等耗时后台任务的线程池
     */
    public ExecutorService diskIO() {
        return mDiskIO;
    }

    /**
     * 获取绑定主线程 Looper 的 Handler，方便进行 UI 刷新
     */
    public Handler mainThread() {
        return mMainThread;
    }
}
