package com.baojie.seda.stage.worker.stagehandler;

public interface QueueAdaptor<T> {

    T pollStamp();

    boolean offerStamp(T ost);

}
