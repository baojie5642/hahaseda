package com.baojie.showdeep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShowDeep {
    private static final Logger log = LoggerFactory.getLogger(ShowDeep.class);

    private ShowDeep() {
        throw new IllegalArgumentException();
    }

    public static void deep(Throwable t) {
        if (null == t) {
            return;
        }
        Throwable ts[] = t.getSuppressed();
        if (null == ts) {
            return;
        }
        int size = ts.length;
        if (size <= 0) {
            return;
        }
        final String tn = Thread.currentThread().getName();
        for (int i = 0; i < size; i++) {
            Throwable tt = ts[i];
            if (null != tt) {
                log.error("thread name=" + tn + ", occur err:" + tt.toString(), tt);
            }
        }
    }

}
