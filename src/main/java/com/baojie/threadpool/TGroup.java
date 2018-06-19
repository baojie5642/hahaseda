package com.baojie.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TGroup {

	private static final Logger log = LoggerFactory.getLogger(TGroup.class);

	private TGroup() {

	}

	public static final ThreadGroup getGroup() {
		ThreadGroup threadGroup = null;
		final SecurityManager sm = System.getSecurityManager();
		if (null != sm) {
			threadGroup = sm.getThreadGroup();
		} else {
			log.warn("SecurityManager can be null when get ThreadGroup, ignore this");
			threadGroup = Thread.currentThread().getThreadGroup();
		}
		if (null == threadGroup) {
			log.error("ThreadGroup get from Main(JVM) must not be null");
			throw new NullPointerException("ThreadGroup get from Main(JVM) must not be null");
		}
		return threadGroup;
	}

}
