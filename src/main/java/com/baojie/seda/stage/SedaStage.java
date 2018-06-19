package com.baojie.seda.stage;

import com.baojie.Manager;
import com.baojie.seda.stage.adaptor.StageAdaptor;
import com.baojie.seda.stage.worker.executor.Worker;
import com.baojie.seda.stage.worker.monitor.StageMonitor;
import com.baojie.showdeep.ShowDeep;
import com.baojie.showdeep.Sleep;
import com.baojie.threadpool.FutureCancel;
import com.baojie.threadpool.PoolShutDown;
import com.baojie.threadpool.TFactory;
import com.baojie.threadpool.ThreadPool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class SedaStage<T> {

    private static final Logger log = LoggerFactory.getLogger(SedaStage.class);
    private static final int DEFAULT_T_N = Sleep.NCPU * 2;     // 默认核心线程数
    private static final TimeUnit T_U = TimeUnit.SECONDS;      // 空闲线程的最大超时的时间单位
    private static final int MAX_ACTIVE = 256;                 // 最大活跃任务数,pool中的线程数不能够大于次数
    private static final int W_Q = 8192;                       // seda中任务队列的最大长度
    private static final int M_W = 180;                        // 空闲线程的最大超时时间

    private final CopyOnWriteArrayList<Future<?>> fu4Monitor = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Worker> workers = new CopyOnWriteArrayList<>();   // 可以使用同步容器
    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final ReentrantLock mainLock = new ReentrantLock();
    private final StageAdaptor<T> adaptor;
    private final Manager manager;

    private final int tNum;
    private final ThreadPool pool;
    private final StagePoolMonitor poolMonitor;
    private final StageQueueMonitor queueMonitor;

    private final String name;

    public SedaStage(String name, Manager manager) {
        this(name, DEFAULT_T_N, manager);
    }

    public SedaStage(String name, int tNum, Manager manager) {
        checkParams(name, tNum, manager);
        this.tNum = tNum;
        this.name = name;
        this.manager = manager;
        this.pool = createPool(name, tNum);
        this.adaptor = createAdaptor();
        this.poolMonitor = new StagePoolMonitor(name);
        this.queueMonitor = new StageQueueMonitor(name);
        this.poolMonitor.startWatch();
        this.queueMonitor.startWatch();
    }

    private void checkParams(String name, int tNum, Manager manager) {
        if (null == manager) {
            throw new NullPointerException("manager");
        }
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("seda name blank");
        }
        if (0 >= tNum || tNum > (MAX_ACTIVE / 2)) {
            throw new IllegalArgumentException("err seda thread num=" + tNum);
        }
    }

    // 使用弹性队列，同时在monitor中进行监控，超过允许的最大值，对线程数量进行重置，这时业务会有短暂的延迟
    // 但是，由于是因为超过了允许的最大活跃数量，说明业务很繁忙了，这时应该考虑重新设计，或者调整业务规模
    private ThreadPool createPool(String name, int tNum) {
        final int max = maxPoolSize(tNum);
        // 使用默认的corePoolSize的超时时间
        return new ThreadPool(tNum, max, M_W, T_U, new SynchronousQueue<>(), TFactory.create(name));
    }

    private int maxPoolSize(int core) {
        return core * 2;
    }

    private StageAdaptor<T> createAdaptor() {
        return new StageAdaptor<>(name, W_Q, getTimeout2Secs(), stop);
    }

    private long getTimeout2Secs() {
        return pool.getKeepAliveTime(T_U);
    }

    public boolean addWorker() {
        final Worker<T> worker = manager.createWorker();
        if (null == worker) {
            return false;
        }
        if (isStop()) {
            return false;
        } else {
            final ReentrantLock lock = mainLock;
            lock.lock();
            try {
                if (isStop()) {
                    return false;
                } else {
                    workers.addIfAbsent(worker);
                    Future<?> f = pool.submit(worker);
                    fu4Monitor.addIfAbsent(f);
                    return true;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public final void notifyMonitor() {
        try {
            notifyPoolWatch();
        } finally {
            notifyStageWatch();
        }
    }

    private void notifyPoolWatch() {
        boolean suc = false;
        try {
            suc = poolMonitor.offer(POOL_WATCH);
        } catch (InterruptedException e) {
            log.error(e.toString() + ", stage name=" + name, e);
        } catch (Throwable t) {
            log.error(t.toString() + ", stage name=" + name, t);
        } finally {
            notifyErr(suc, "poolWatch offer error");
        }
    }

    private void notifyStageWatch() {
        boolean suc = false;
        try {
            suc = queueMonitor.offer(STAGE_WATCH);
        } catch (InterruptedException e) {
            log.error(e.toString() + ", stage name=" + name, e);
        } catch (Throwable t) {
            log.error(t.toString() + ", stage name=" + name, t);
        } finally {
            notifyErr(suc, "stageWatch offer error");
        }
    }

    private void notifyErr(boolean suc, String reason) {
        if (suc) {
            return;
        } else {
            if (isStop()) {
                return;
            } else {
                log.error(reason + " fail, stage name=" + name);
            }
        }
    }

    public boolean needPurge() {
        final int core = getCoreWorker();
        final int active = getActiveWorker();
        if (active <= core) {
            return false;
        } else {
            log.debug("need purge, stage name=" + name + ", core=" + core + ", active=" + active);
            return true;
        }
    }

    public void purge() {
        pool.purge();
    }

    public int getActiveWorker() {
        return pool.getActiveCount();
    }

    public int getCoreWorker() {
        return pool.getCorePoolSize();
    }

    public int getMaxWorker() {
        return pool.getMaximumPoolSize();
    }

    public boolean offer(T ost) {
        if (null == ost) {
            return false;
        }
        if (isStop(ost)) {
            return false;
        }
        if (adaptor.offerStamp(ost)) {
            return true;
        } else {
            try {
                addWorker();
                spinYield();
            } finally {
                return adaptor.offerStamp(ost);
            }
        }
    }

    private void spinYield() {
        for (int i = 0; i < 8; i++) {
            Thread.yield();
        }
    }

    public boolean isStop() {
        return isStop(null);
    }

    public boolean isStop(T ost) {
        if (stop.get()) {
            printStamp(ost);
            return true;
        } else {
            return false;
        }
    }

    private void printStamp(T ost) {
        if (null != ost) {
            log.error("seda has stop, ost=" + ost);
        }
    }

    public StageAdaptor<T> getAdaptor() {
        return adaptor;
    }

    public String getName() {
        return name;
    }

    // 这里使用同步是为了确保使用了lock的地方的一致性问题
    public void shutDownStage() {
        if (stop.get()) {
            return;
        } else {
            if (stop.compareAndSet(false, true)) {
                final ReentrantLock l = mainLock;
                l.lock();
                try {
                    stop.set(true);
                    stopMonitor();
                    cancelFuture();
                    shutPool();
                } catch (Throwable t) {
                    log.error(t.toString(), t);
                } finally {
                    l.unlock();
                }
            }
        }
    }

    private void stopMonitor() {
        try {
            poolMonitor.stop();
        } finally {
            queueMonitor.stop();
        }
    }

    private void cancelFuture() {
        FutureCancel.cancel(fu4Monitor, true);
    }

    private void shutPool() {
        PoolShutDown.threadPool(pool, name);
    }

    private boolean error(Future<?> f) {
        Object o = get(f);
        if (null == o) {
            return false;
        } else {
            if (!(o instanceof Throwable)) {
                Class<?> cl = o.getClass();
                if (null != cl) {
                    String cn = cl.getName();
                    if (null != cn) {
                        log.error("object class=" + cn + ", stage name=" + name);
                    }
                }
                return false;
            } else {
                Throwable t = (Throwable) o;
                log.error(t.toString() + ", stage name=" + name, t);
                ShowDeep.deep(t);
                return true;
            }
        }
    }

    private Object get(Future<?> f) {
        if (null == f) {
            return null;
        }
        try {
            return f.get(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.error(e.toString() + ", stage name=" + name, e);
        } catch (ExecutionException e) {
            log.error(e.toString() + ", stage name=" + name, e);
        } catch (TimeoutException e) {
            log.error(e.toString() + ", stage name=" + name, e);
        } catch (Throwable e) {
            log.error(e.toString() + ", stage name=" + name, e);
        }
        return null;
    }

    @Override
    public String toString() {
        return "SedaStage{" +
                "tNum=" + tNum +
                ", name='" + name + '\'' +
                '}';
    }

    private final class StagePoolMonitor extends StageMonitor {
        private final Logger log = LoggerFactory.getLogger(StageQueueMonitor.class);
        private final BlockingQueue<PoolWatch> queue = new SynchronousQueue<>();

        protected StagePoolMonitor(String dn) {
            super(dn + "_seda_poll_monitor");
        }

        @Override
        public void watch() {
            PoolWatch pw = null;
            for (; ; ) {
                if (isStop()) {
                    return;
                } else {
                    pw = getPill();
                }
                log.debug("StagePoolMonitor work, stageName=" + getName() + ", dogName=" + getDogName());
                if (null == pw) {
                    continue;
                }
                if (pw == POOL_WATCH) {
                    monitor();
                } else {
                    continue;
                }
            }
        }

        public boolean offer(PoolWatch pw) throws InterruptedException {
            if (queue.offer(pw)) {
                return true;
            }
            int sp = adaptor.getSpins();
            boolean suc = false;
            for (int i = 0; i < sp; i++) {
                if (isStop()) {
                    return false;
                } else {
                    suc = queue.offer(pw);
                    if (suc) {
                        return suc;
                    } else {
                        Thread.yield();
                    }
                }
            }
            if (!suc) {
                return queue.offer(pw, 100, TimeUnit.MILLISECONDS);
            } else {
                return suc;
            }
        }

        private PoolWatch getPill() {
            try {
                return queue.poll(8, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error(e.toString() + ", dogName=" + getDogName(), e);
            } catch (Throwable t) {
                log.error(t.toString() + ", dogName=" + getDogName(), t);
            }
            return null;
        }

        private void monitor() {
            final CopyOnWriteArrayList<Future<?>> ful = fu4Monitor;
            if (null == ful) {
                return;
            }
            for (Future<?> f : ful) {
                if (isStop()) {
                    return;
                }
                if (null == f) {
                    continue;
                }
                if (!f.isDone()) {
                    continue;
                }
                error(f);
                f.cancel(true);
                ful.remove(f);
                shutIdleAndAdd();
            }
        }

        private void shutIdleAndAdd() {
            purge();
            // 如果活动线程数大于等于默认最大，直接返回
            // 说明业务很繁忙
            if (getActiveWorker() >= MAX_ACTIVE) {
                return;
            }
            // 如果pool的最大线程数大于默认最大，直接返回
            // 说明外部客户端已经修改了初始值
            if (getMaxWorker() >= MAX_ACTIVE) {
                return;
            }
            // 如果需要清除空闲线程，说明活跃的很多
            // 不需添加
            if (needPurge()) {
                return;
            }
            final ReentrantLock l = mainLock;
            try {
                l.lockInterruptibly();
                if (isStop()) {
                    return;
                }
                // 如果不需要清楚空闲线程
                // 说明活跃的<=core
                // 那么再次添加
                if (!needPurge()) {
                    addWorker();
                }
            } catch (InterruptedException e) {
                log.error(e.toString(), e);
            } catch (Throwable t) {
                log.error(t.toString(), t);
            } finally {
                l.unlock();
            }
        }

        @Override
        public String toString() {
            return "StagePoolMonitor{dogName=" + super.toString() + '}';
        }

    }

    private final class StageQueueMonitor extends StageMonitor {
        private final Logger log = LoggerFactory.getLogger(StageQueueMonitor.class);
        private final ThreadLocalRandom random = ThreadLocalRandom.current();
        private final BlockingQueue<StageWatch> queue = new SynchronousQueue<>();

        protected StageQueueMonitor(String dn) {
            super(dn + "_seda_queue_monitor");
        }

        @Override
        public void watch() {
            StageWatch sw = null;
            for (; ; ) {
                if (isStop()) {
                    return;
                } else {
                    sw = getPill();
                }
                log.debug("StageQueueMonitor work, stageName=" + getName() + ", dogName=" + getDogName());
                if (null == sw) {
                    continue;
                }
                if (sw == STAGE_WATCH) {
                    checkState();
                } else {
                    continue;
                }
            }
        }

        public boolean offer(StageWatch sw) throws InterruptedException {
            if (queue.offer(sw)) {
                return true;
            }
            int sp = adaptor.getSpins();
            boolean suc = false;
            for (int i = 0; i < sp; i++) {
                if (isStop()) {
                    return false;
                } else {
                    suc = queue.offer(sw);
                    if (suc) {
                        return suc;
                    } else {
                        Thread.yield();
                    }
                }
            }
            if (!suc) {
                return queue.offer(sw, 100, TimeUnit.MILLISECONDS);
            } else {
                return suc;
            }
        }

        private StageWatch getPill() {
            final int secs = random.nextInt(180, 600);
            try {
                return queue.poll(secs, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error(e.toString() + ", dogName=" + getDogName(), e);
            } catch (Throwable t) {
                log.error(t.toString() + ", dogName=" + getDogName(), t);
            }
            return null;
        }

        private void checkState() {
            final ThreadPool p = pool;
            final ReentrantLock l = mainLock;
            try {
                l.lockInterruptibly();
                removeWorker();
                removeFuture();
                if (p.getActiveCount() < p.getCorePoolSize()) {
                    addWorker();
                }
            } catch (InterruptedException e) {
                log.error(e.toString(), e);
            } catch (Throwable t) {
                log.error(t.toString(), t);
            } finally {
                l.unlock();
            }
        }

        private void removeWorker() {
            final CopyOnWriteArrayList<Worker> ws = workers;
            if (null == ws) {
                return;
            }
            for (Worker w : ws) {
                try {
                    if (null == w) {
                        continue;
                    }
                    if (!w.isAlive()) {
                        ws.remove(w);
                        log.warn("work died, work=" + w + ", stage name=" + getName() + ", dogName=" + getDogName());
                    }
                } catch (Throwable te) {
                    log.error(te.toString(), te);
                }
            }
        }

        private void removeFuture() {
            final CopyOnWriteArrayList<Future<?>> futures = fu4Monitor;
            for (Future<?> fu : futures) {
                try {
                    if (null == fu) {
                        continue;
                    }
                    if (fu.isDone()) {
                        futures.remove(fu);
                    }
                } catch (Throwable te) {
                    log.error(te.toString(), te);
                }
            }
        }

        @Override
        public String toString() {
            return "StageQueueMonitor{dogName=" + super.toString() + '}';
        }
    }

    private static final PoolWatch POOL_WATCH = PoolWatch.create();

    private static final class PoolWatch {
        private PoolWatch() {

        }

        public static PoolWatch create() {
            return new PoolWatch();
        }
    }

    private static final StageWatch STAGE_WATCH = StageWatch.create();

    private static final class StageWatch {
        private StageWatch() {

        }

        public static StageWatch create() {
            return new StageWatch();
        }
    }
}
