package com.example.markdownviewer;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class AppExecutor {

    private static volatile AppExecutor sInstance;

    private final ThreadPoolExecutor mDiskIO;
    private final ThreadPoolExecutor mComputation;
    private final Handler mMainThread;

    private AppExecutor() {
        // IO 线程池：处理文件读写、数据库查询、网络请求
        // 核心4线程，最大8线程，空闲60秒回收，队列容量100
        mDiskIO = new ThreadPoolExecutor(
                4, 8, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new NamedThreadFactory("disk-io"),
                new ThreadPoolExecutor.CallerRunsPolicy());

        // 计算线程池：处理 Markdown 渲染等 CPU 密集型任务
        // 核心2线程（CPU 核心数），最大4线程
        int cpuCount = Runtime.getRuntime().availableProcessors();
        mComputation = new ThreadPoolExecutor(
                Math.max(2, cpuCount), Math.max(4, cpuCount * 2),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                new NamedThreadFactory("compute"),
                new ThreadPoolExecutor.CallerRunsPolicy());

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

    public ThreadPoolExecutor diskIO() {
        return mDiskIO;
    }

    public ThreadPoolExecutor computation() {
        return mComputation;
    }

    public Handler mainThread() {
        return mMainThread;
    }

    /**
     * 应用退出时调用，优雅关闭线程池。
     * 应在 Application.onTerminate() 或最后一个 Activity 销毁时调用。
     */
    public void shutdown() {
        mDiskIO.shutdown();
        mComputation.shutdown();
        try {
            if (!mDiskIO.awaitTermination(5, TimeUnit.SECONDS)) {
                mDiskIO.shutdownNow();
            }
            if (!mComputation.awaitTermination(5, TimeUnit.SECONDS)) {
                mComputation.shutdownNow();
            }
        } catch (InterruptedException e) {
            mDiskIO.shutdownNow();
            mComputation.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final String prefix;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
