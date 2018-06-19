package com.baojie;

import com.baojie.seda.stage.worker.executor.Worker;

public interface Manager<T> {

    Worker<T> createWorker();

}
