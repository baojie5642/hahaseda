package com.baojie;

import com.baojie.seda.stage.SedaStage;
import com.baojie.seda.stage.worker.executor.Worker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

@Service
public class Distance implements Manager<Object> {

    private final SedaStage<Object> stage;
    private final Business bus;

    @Autowired
    public Distance(Business bus) {
        if (null == bus) {
            throw new NullPointerException();
        }
        this.bus = bus;
        int tn = 4;      //
        this.stage = new SedaStage<>(Business.Dis, tn, this);
        bus.fillStage(Business.Dis, stage);
        addAll(tn);
    }

    private void addAll(int tn) {
        for (int i = 0; i < tn; i++) {
            stage.addWorker();
        }
    }

    @Override
    public Worker<Object> createWorker() {
        return new DistanceRunner(stage, bus);
    }

    @PreDestroy
    private void destory() {
        if (null != stage) {
            stage.shutDownStage();
        }
    }

}
