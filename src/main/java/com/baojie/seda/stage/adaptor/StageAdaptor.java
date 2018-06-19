package com.baojie.seda.stage.adaptor;

import com.baojie.seda.stage.worker.stagehandler.QueueAdaptor;
import com.baojie.showdeep.Sleep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StageAdaptor<T> implements QueueAdaptor<T> {

    private static final Logger log = LoggerFactory.getLogger(StageAdaptor.class);
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;
    private final LinkedBlockingQueue<T> queue;
    private volatile long timeOutSecs = 180;
    private final AtomicBoolean stop;
    private final String name;
    private final int size;

    public StageAdaptor(String name, int size, long timeOutSecs, AtomicBoolean stop) {
        if (null == name || 0 >= size || 0 >= timeOutSecs) {
            throw new IllegalArgumentException();
        }
        this.stop = stop;
        this.name = name;
        this.size = size;
        this.timeOutSecs = timeOutSecs;
        this.queue = new LinkedBlockingQueue<>(size);
    }

    @Override
    public T pollStamp() {
        // 先poll一次如果成功了直接返回
        T ostp = normalPoll();
        if (null != ostp) {
            return ostp;
        } else {
            ostp = spinsPoll();
        }
        if (null != ostp) {
            return ostp;
        } else {
            return timedPoll();
        }
    }

    public T normalPoll() {
        return queue.poll();
    }

    public T normalPeek() {
        return queue.peek();
    }

    private T spinsPoll() {
        final int spins = getSpins();
        if (spins <= 0) {
            return normalPoll();
        } else {
            T ostp = null;
            for (int i = 0; i < spins; i++) {
                if (isStop()) {
                    return null;
                } else {
                    ostp = normalPoll();
                    if (null != ostp) {
                        break;
                    } else {
                        Thread.yield();
                    }
                }
            }
            return ostp;
        }
    }

    public int getSpins() {
        return (Sleep.NCPU < 2) ? 0 : 16;
    }

    private T timedPoll() {
        if (isStop()) {
            return null;
        }
        try {
            return queue.poll(timeOutSecs, TIME_UNIT);
        } catch (InterruptedException i) {
            log.error(i.toString() + ", queue adaptor name=" + name, i);
        } catch (Throwable t) {
            log.error(t.toString() + ", queue adaptor name=" + name, t);
        }
        return null;
    }

    @Override
    public boolean offerStamp(T ost) {
        if (null == ost) {
            return false;
        }
        if (queue.offer(ost)) {
            return true;
        }
        final int spins = getSpins();
        for (int i = 0; i < spins; i++) {
            if (queue.offer(ost)) {
                return true;
            } else {
                if (isStop()) {
                    return false;
                } else {
                    Thread.yield();
                }
            }
        }
        return false;
    }

    public boolean isStop() {
        return stop.get();
    }

    public String getName() {
        return name;
    }

    public int getSize() {
        return size;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public void setTimeOutSecs(int timeOutSecs) {
        this.timeOutSecs = timeOutSecs;
    }

    @Override
    public String toString() {
        return "StageAdaptor{" +
                "timeOutSecs=" + timeOutSecs +
                ", stop=" + stop +
                ", name='" + name + '\'' +
                ", size=" + size +
                '}';
    }

}
