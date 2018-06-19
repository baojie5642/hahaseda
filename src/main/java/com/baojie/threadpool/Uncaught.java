package com.baojie.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;

public class Uncaught implements UncaughtExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(Uncaught.class);
	private static volatile Uncaught Instance;

	public Uncaught() {

	}

	public static Uncaught getInstance() {
		if (null != Instance) {
			return Instance;
		} else {
			synchronized (Uncaught.class) {
				if (null == Instance) {
					Instance = new Uncaught();
				}
				return Instance;
			}
		}
	}

	@Override
	public void uncaughtException(final Thread t, final Throwable e) {
		if (null == t) {
			log.error("thread must not be null");
			return;
		}
		final String threadName = Thread.currentThread().getName();
		firstInterrupt(t);
		if (null == e) {
			log.error("throwable must not be null, threadName=" + threadName);
			return;
		}
		log.error("threadName=" + threadName + ", uncaughtException=" + e.toString());
		log.error(e.toString(),e);
	}

	private void firstInterrupt(final Thread t) {
		try {
			t.interrupt();
		} finally {
			alwaysInterrupt(t);
		}
	}

	private void alwaysInterrupt(final Thread t) {
		if (!t.isInterrupted()) {
			t.interrupt();
		}
		if (t.isAlive()) {
			t.interrupt();
		}
	}
}
