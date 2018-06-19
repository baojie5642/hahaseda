package com.baojie;

import com.baojie.seda.stage.SedaStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Business {
    private static final Logger log = LoggerFactory.getLogger(Business.class);

    public static final String Dis = "dis_stage";

    // 这个类通过spring代理，全局唯一bean，所以初始化的是final非静态变量
    private final Map<String, SedaStage<?>> stageArray = new ConcurrentHashMap<>();

    @Autowired
    public Business() {

    }

    @PreDestroy
    private void destory() {
        stageArray.clear();
    }

    public boolean fillStage(String name, SedaStage<?> stage) {
        if (null == stageArray.putIfAbsent(name, stage)) {
            return true;
        } else {
            return false;
        }
    }

    //
    public boolean putDis(Object object) {
        if (null == object) {
            return false;
        }
        SedaStage<Object> s = (SedaStage<Object>) stageArray.get(Dis);
        if (null == s) {
            log.error(Dis + " seda stage null, object=" + object);
            return false;
        } else {
            return s.offer(object);
        }
    }

}
