package com.raglaw.agentscope.agui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class TaskCancellationRegistry {

    private final Map<String, AtomicBoolean> tasks = new ConcurrentHashMap<>();

    public void register(String taskId) {
        tasks.put(taskId, new AtomicBoolean(false));
    }

    public void cancel(String taskId) {
        AtomicBoolean flag = tasks.get(taskId);
        if (flag != null) {
            flag.set(true);
        }
    }

    public boolean isCancelled(String taskId) {
        AtomicBoolean flag = tasks.get(taskId);
        return flag != null && flag.get();
    }

    public void unregister(String taskId) {
        tasks.remove(taskId);
    }
}
