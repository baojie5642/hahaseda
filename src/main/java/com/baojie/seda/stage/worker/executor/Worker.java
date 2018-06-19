package com.baojie.seda.stage.worker.executor;

import com.baojie.seda.stage.SedaStage;
import com.baojie.seda.stage.adaptor.StageAdaptor;
import com.baojie.showdeep.ShowDeep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 采用简单形式，直接使用同步阻塞队列
public abstract class Worker<T> implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Worker.class);
    private volatile boolean alive = true;
    private final StageAdaptor<T> adaptor;
    private final SedaStage<T> stage;

    protected Worker(SedaStage<T> stage) {
        this.stage = stage;
        this.adaptor = stage.getAdaptor();
    }

    protected abstract void work(String tn, T ost);

    protected abstract void doRemain(String tn);

    // 主要为兼容开通漫游流量的服务提供方便
    protected boolean openSched() {
        return false;
    }

    // 主要为兼容开通漫游流量的服务提供方便
    protected void sched() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void run() {
        Throwable err = null;
        try {
            doWork();
        } catch (Throwable t) {
            error(t);
            err = t;
        } finally {
            notifyStage(err);
        }
    }

    private void doWork() {
        final String tn = Thread.currentThread().getName();
        T ost;
        try {
            work:
            for (; ; ) {
                if (isStop()) {
                    break work;
                }
                ost = adaptor.pollStamp();
                if (null == ost) {
                    if (isStop()) {
                        break work;
                    } else if (purgeIdle(tn)) {
                        break work;
                    } else if (openSched()) {
                        sched();
                    } else {
                        continue work;
                    }
                } else {
                    work(tn, ost);
                }
            }
        } finally {
            doRemain(tn);
        }
    }

    private void error(Throwable t) {
        if (null == t) {
            return;
        } else {
            log.error(t.toString(), t);
            ShowDeep.deep(t);
        }
    }

    // 由于使用的是阻塞队列，所以要判断线程数量来控制资源，防止线程数量只增不减
    protected boolean purgeIdle(String tn) {
        boolean p = stage.needPurge();
        if (p) {
            log.debug("threadName=" + tn + ", stage name=" + stage.getName());
            return true;
        } else {
            return false;
        }
    }

    private void notifyStage(Throwable err) {
        try {
            alive = false;
            if (!stage.isStop()) {
                if (null == err) {
                    log.error("error in Worker, stage name=" + stage.getName());
                } else {
                    log.error("error=" + err.toString() + ", in Worker, stage name=" + stage.getName(), err);
                }
            }
        } finally {
            stage.notifyMonitor();
        }
    }

    public int getQueueSize() {
        return adaptor.getQueueSize();
    }

    public T normalPoll() {
        return adaptor.normalPoll();
    }

    public T normalPeek() {
        return adaptor.normalPeek();
    }

    public boolean isStop() {
        return stage.isStop(null);
    }

    public boolean isAlive() {
        boolean live = alive;
        return live;
    }

    @Override
    public String toString() {
        return "Worker{" +
                "stage=" + stage +
                '}';
    }
}
