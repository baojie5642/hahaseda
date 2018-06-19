package com.baojie;

import com.baojie.seda.stage.SedaStage;
import com.baojie.seda.stage.worker.executor.Worker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistanceRunner extends Worker<Object> {

    private static final Logger log = LoggerFactory.getLogger(DistanceRunner.class);
    private final Business bus;

    public DistanceRunner(SedaStage<Object> stage, Business bus) {
        super(stage);
        this.bus = bus;
    }

    @Override
    public void work(String tn, Object order) {

    }

    @Override
    public void doRemain(String tn) {
        for (; ; ) {
            Object order = normalPoll();
            if (null != order) {
                work(tn, order);
            } else {
                break;
            }
        }
    }

}
