package com.baojie.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class RejectedHandler implements RejectedExecutionHandler {
	private static final Logger log = LoggerFactory.getLogger(RejectedHandler.class);
	private final String rejectedHandlerName;
	// inner loop times
	private static final int LoopSubmit = 6;
	private static final int LoopTime = 60;

	private RejectedHandler(final String rejectedHandlerName) {
		this.rejectedHandlerName = rejectedHandlerName;
	}

	public static RejectedHandler create(final String rejectedHandlerName) {
		return new RejectedHandler(rejectedHandlerName);
	}

	@Override
	public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
		final Queue<Runnable> taskQueue = executor.getQueue();
		if (taskQueue.offer(runnable)) {
			log.debug("Resubmit success. RejectedHandlerName is : " + rejectedHandlerName
					+ ", TaskQueue in threadpool is : " + taskQueue.size());
		} else {
			log.info("Loopsubmit start, RejectedHandlerName is : " + rejectedHandlerName + ", loopsubmit times is : "
					+ LoopSubmit + ", loopsubmit period time is : " + LoopTime + " milliseconds.");
			innerLoopSubmit(runnable, taskQueue);
		}
	}

	private void innerLoopSubmit(final Runnable runnable, final Queue<Runnable> taskQueue) {
		int testLoop = 0;
		boolean loopSuccess = false;
		while (testLoop <= LoopSubmit) {
			if (taskQueue.offer(runnable)) {
				loopSuccess = true;
				log.debug("Loopsubmit success. RejectedHandlerName is ：" + rejectedHandlerName
						+ ", TaskQueue in threadpool is : " + taskQueue.size());
				break;
			} else {
				testLoop++;
				LockSupport.parkNanos(TimeUnit.NANOSECONDS.convert(LoopTime, TimeUnit.MILLISECONDS));
			}
		}
		checkLoopState(loopSuccess, runnable);
	}

	private void checkLoopState(final boolean loopSuccess, final Runnable runnable) {
		if (loopSuccess) {
			return;
		} else {
			submitRunnableIntoRejectedThreadPool(runnable);
			log.warn("Loopsubmit failue. Submit task into YunRejectedThreadPool. RejectedHandlerName is ： "
					+ rejectedHandlerName + ".");
		}
	}

	private void submitRunnableIntoRejectedThreadPool(final Runnable runnable) {
		RejectedPool.getInstance().submit(runnable);
	}

	public String getRejectedHandlerName() {
		return rejectedHandlerName;
	}
}
