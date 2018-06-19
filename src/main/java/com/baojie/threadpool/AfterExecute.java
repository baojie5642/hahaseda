package com.baojie.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AfterExecute {

    private static final Logger log = LoggerFactory.getLogger(AfterExecute.class);

    private AfterExecute() {

    }

    public static final void afterExecute(final Runnable runnable, final Throwable throwable) {
        final String threadName = Thread.currentThread().getName();
        if (null == runnable) {
            log.error("threadName=" + threadName + ", 'runnable' must not be null in 'afterExecute()'");
        }
        if (null != throwable) {
            log.warn("threadName=" + threadName + ", 'throwable' is not null in 'afterExecute()', occur error");
            if (throwable instanceof RuntimeException) {
                innerPrint(threadName, throwable, "RuntimeException");
                return;
            }
            if (throwable instanceof InterruptedException) {
                innerPrint(threadName, throwable, "InterruptedException");
                return;
            }
            if (throwable instanceof Exception) {
                innerPrint(threadName, throwable, "Exception");
                return;
            }
            if (throwable instanceof Error) {
                innerPrint(threadName, throwable, "Error");
                return;
            }
            innerPrint(threadName, throwable, "Throwable");
        } else {
            return;// 没有出错不打印任何信息
        }
    }

    private static final void innerPrint(final String threadName, final Throwable throwable, final String errorType) {
        log.error("threadName=" + threadName + ", occur '" + errorType + "', throwable.getMessage="
                + throwable.getMessage() + ", throwable.toString=" + throwable.toString());
        log.error(threadName + throwable.toString(), throwable);
    }

}
