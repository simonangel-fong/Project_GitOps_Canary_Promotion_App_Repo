package com.gitops.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

@Component
public class OomTrigger {

    private final AppProperties props;
    private ScheduledExecutorService scheduler;

    public OomTrigger(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void scheduleOom() {
        if (!props.isOomEnable()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "oom-trigger");
            t.setDaemon(true);
            return t;
        });
        scheduler.schedule(this::allocateUntilOom, props.getOomTime(), TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void allocateUntilOom() {
        List<byte[]> leak = new ArrayList<>();
        while (true) {
            leak.add(new byte[10 * 1024 * 1024]);
        }
    }
}
