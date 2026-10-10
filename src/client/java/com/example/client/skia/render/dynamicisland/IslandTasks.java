package com.example.client.skia.render.dynamicisland;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Live tasks are independent of which two presentations currently occupy the island. */
final class IslandTasks {
    static final class Task {
        final String id;
        final DynamicIsland.Activity activity;
        final long order;
        int priority;
        boolean playing = true;
        boolean timerRunning = true;
        double elapsed;
        double musicTime = 42;
        double timerSeconds = 300;
        IslandLiveData live;
        final IslandCountdown countdown = new IslandCountdown();

        Task(String id, DynamicIsland.Activity activity, int priority, long order) {
            this.id = id;
            this.activity = activity;
            this.priority = priority;
            this.order = order;
        }

        void advance(double seconds) {
            elapsed += seconds;
            countdown.advance(seconds);
            if (live != null) return;
            if (playing) musicTime = (musicTime + seconds) % 214;
            if (timerRunning) timerSeconds = Math.max(0, timerSeconds - seconds);
        }
    }

    private final Map<String, Task> tasks = new LinkedHashMap<>();
    private long sequence;

    void start(String id, DynamicIsland.Activity activity, int priority) {
        Objects.requireNonNull(id, "task id");
        Objects.requireNonNull(activity, "activity");
        if (id.isBlank()) throw new IllegalArgumentException("Task id must not be blank");
        Task old = tasks.get(id);
        if (old != null && old.activity == activity) { old.priority = priority; return; }
        Task task = new Task(id, activity, priority, old == null ? sequence++ : old.order);
        if (old != null) {
            task.playing = old.playing;
            task.timerRunning = old.timerRunning;
            task.elapsed = old.elapsed;
            task.musicTime = old.musicTime;
            task.timerSeconds = old.timerSeconds;
        }
        tasks.put(id, task);
    }

    Task get(String id) { return tasks.get(id); }

    void end(String id) { tasks.remove(id); }

    List<Task> visible() {
        // Stable ordering for equal priority prevents the two slots from swapping on updates.
        return tasks.values().stream().sorted(Comparator
                .comparingInt((Task task) -> task.priority).reversed()
                .thenComparingLong(task -> task.order)).limit(2).toList();
    }

    boolean advance(double seconds) {
        tasks.values().forEach(task -> task.advance(seconds));
        return tasks.values().removeIf(task -> task.live == null && task.activity == DynamicIsland.Activity.TIMER && task.timerSeconds <= 0);
    }
}
