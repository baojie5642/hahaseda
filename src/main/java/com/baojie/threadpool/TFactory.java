package com.baojie.threadpool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TFactory implements ThreadFactory {

	private static final Uncaught UNCAUGHT_EXCEPTION_HANDLER = Uncaught.getInstance();
	private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
	private static final int NO_THREAD_PRIORITY = Thread.NORM_PRIORITY;
	private final AtomicLong t_number = new AtomicLong(1);
	private final ThreadGroup group;
	private final String factoryName;
	private final int t_Priority;
	private final String poolName;
	private final boolean isDaemon;

	public static TFactory create(final String name) {
		return new TFactory(name, false, NO_THREAD_PRIORITY);
	}

	public static TFactory create(final String name, final boolean isDaemon) {
		return new TFactory(name, isDaemon, NO_THREAD_PRIORITY);
	}

	public static TFactory create(final String name, final int threadPriority) {
		return new TFactory(name, false, threadPriority);
	}

	public static TFactory create(final String name, final boolean isDaemon, final int threadPriority) {
		return new TFactory(name, isDaemon, threadPriority);
	}

	private TFactory(final String name, final boolean isDaemon, final int threadPriority) {
		this.group = TGroup.getGroup();
		this.factoryName = name;
		this.isDaemon = isDaemon;
		this.t_Priority = threadPriority;
		this.poolName = factoryName + "_" + POOL_NUMBER.getAndIncrement();
	}

	@Override
	public Thread newThread(final Runnable r) {
		final String threadNum = String.valueOf(t_number.getAndIncrement());
		final Thread thread = new Thread(group, r, poolName + "_thread_" + threadNum, 0);
		setProperties(thread);
		return thread;
	}

	private void setProperties(final Thread thread) {
		setDaemon(thread);
		setPriority(thread);
		thread.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
	}

	private void setDaemon(final Thread thread) {
		if (isDaemon == true) {
			thread.setDaemon(true);
		} else {
			if (thread.isDaemon()) {
				thread.setDaemon(false);
			}
		}
	}

	private void setPriority(final Thread thread) {
		if (t_Priority == NO_THREAD_PRIORITY) {
			if (thread.getPriority() != Thread.NORM_PRIORITY) {
				thread.setPriority(Thread.NORM_PRIORITY);
			}
		} else {
			final int priority = checkPriority();
			thread.setPriority(priority);
		}
	}

	private int checkPriority() {
		if (t_Priority <= Thread.MIN_PRIORITY) {
			return Thread.MIN_PRIORITY;
		} else {
			if (t_Priority >= Thread.MAX_PRIORITY) {
				return Thread.MAX_PRIORITY;
			} else {
				return t_Priority;
			}
		}
	}

	public static Uncaught getUncaught() {
		return UNCAUGHT_EXCEPTION_HANDLER;
	}

	public int getPoolNumber() {
		return POOL_NUMBER.get();
	}

	public long getThreadNumber() {
		return t_number.get();
	}

	public String getFactoryName() {
		return factoryName;
	}

	public int getThreadPriority() {
		return t_Priority;
	}

	public String getPoolName() {
		return poolName;
	}

	public boolean isDaemon() {
		return isDaemon;
	}

}