package com.baojie;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.atomic.AtomicBoolean;

public class ServerManager {

    private static final Logger log = LoggerFactory.getLogger(ServerManager.class);
    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static volatile ServerManager sm;
    private volatile AbstractApplicationContext apc = null;
    private volatile Notification notify;

    public void registerNotify(Notification notify) {
        this.notify = notify;
    }

    private ServerManager() {
    }

    public static ServerManager instance() {
        if (null != sm) {
            return sm;
        }
        synchronized (ServerManager.class) {
            if (null == sm) {
                sm = new ServerManager();
            }
            return sm;
        }
    }

    public void startServer() {
        if (INIT.get()) {
            log.warn("seda Server already started");
            return;
        } else {
            if (INIT.compareAndSet(false, true)) {
                log.info("********* start seda server *********");
                new Thread("sms-server-0") {
                    public void run() {
                        apc = new ClassPathXmlApplicationContext("classpath:applicationContext.xml");
                        log.info("********* seda server starts successfully *********");
                    }
                }.start();
                addHook();
            } else {
                log.warn("other thread running");
            }
        }
    }

    private void addHook() {
        if (null != hook) {
            Runtime.getRuntime().addShutdownHook(hook);
        }
    }

    public synchronized void stop() {
        if (null != this.apc) {
            apc.close();
        }
    }

    private void destory() {
        if (apc == null) {
            return;
        }
        apc.close();
        apc = null;
    }

    private void removeHook() {
        if (null != this.hook) {
            try {
                Runtime.getRuntime().removeShutdownHook(this.hook);
            } catch (IllegalStateException ex) {
                // ignore - VM is already shutting down
            }
        }
    }

    private final Thread hook = new Thread() {
        @Override
        public void run() {
            try {
                destory();
                removeHook();
                if (null != notify) {
                    notify.showdown();
                }
            } catch (Throwable te) {
                te.printStackTrace();
            }
        }
    };

    public static interface Notification {
        void showdown();
    }
}
