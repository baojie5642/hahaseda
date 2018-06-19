package com.baojie.threadpool;

import java.util.concurrent.*;

public class RejectedPool extends ThreadPoolExecutor {

    private static volatile RejectedPool Instance;

    public static RejectedPool getInstance() {
        if (null != Instance) {
            return Instance;
        } else {
            synchronized (RejectedPool.class) {
                if (null == Instance) {
                    Instance = new RejectedPool(2, 512, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(1024),
                            TFactory.create("RejectedPool"), new AbortPolicy());
                }
            }
            return Instance;
        }
    }

    public RejectedPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public RejectedPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public RejectedPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public RejectedPool(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    public void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
    }

    // 防止使用submit方法的时候，当调用run发生异常时，异常被包装在future中
    @Override
    public void afterExecute(final Runnable runnable, final Throwable throwable) {
        super.afterExecute(runnable, throwable);
        AfterExecute.afterExecute(runnable, throwable);
    }

}