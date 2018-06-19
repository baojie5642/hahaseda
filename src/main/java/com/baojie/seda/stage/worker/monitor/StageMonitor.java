package com.baojie.seda.stage.worker.monitor;

import com.baojie.showdeep.ShowDeep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class StageMonitor implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(StageMonitor.class);
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final CountDownLatch latch = new CountDownLatch(1);
    private final String dogName;
    private volatile Thread t;

    protected StageMonitor(String dn) {
        this.dogName = dn;
        this.t = createThread(dogName);
    }

    private Thread createThread(String dn) {
        return new Thread(this, dn);
    }

    public final void startWatch() {
        try {
            if (t != null) {
                t.start();
            }
        } finally {
            latch.countDown();
        }
    }

    @Override
    public void run() {
        waitRun();
        Throwable te = null;
        try {
            watch();
        } catch (Throwable t) {
            te = t;
            showErr(t);
        } finally {
            if (!stop.get() || null != te) {
                log.error("occur err, dog name=" + dogName + ", throwable=" + te, te);
                rebuildThread();
            }
        }
    }

    // CountDownLatch不可重复使用，所以如果之后重复调用，不会阻塞
    private void waitRun() {
        boolean suc = false;
        Throwable err = null;
        try {
            suc = latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            err = e;
            log.error(e.toString() + ", dogName=" + dogName, e);
        } catch (Throwable t) {
            err = t;
            log.error(t.toString() + ", dogName=" + dogName, t);
        }
        if (suc) {
            return;
        }
        if (null == err) {
            throw new Error("stage monitor, dogName=" + dogName);
        } else {
            throw new Error(err);
        }
    }

    private void showErr(Throwable t) {
        if (null == t) {
            return;
        } else {
            log.error(t.toString() + ", dogName=" + dogName, t);
            ShowDeep.deep(t);
        }
    }

    private void rebuildThread() {
        if (isMonitorStop()) {
            return;
        }
        synchronized (StageMonitor.class) {
            if (null == t) {
                return;
            } else {
                // 在将中断状态设置
                t.interrupt();
                t = null;
                t = createThread(dogName);
                startWatch();
            }
        }
    }

    protected abstract void watch();

    public boolean isMonitorStop() {
        return stop.get();
    }

    public void stop() {
        if (isMonitorStop()) {
            return;
        } else {
            if (stop.compareAndSet(false, true)) {
                if (null != t) {
                    t.interrupt();
                    t = null;
                } else {
                    return;
                }
            }
        }
    }

    public String getDogName() {
        return dogName;
    }

    @Override
    public String toString() {
        return "StageMonitor{" +
                "dogName='" + dogName + '\'' +
                '}';
    }

}
