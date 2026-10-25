package com.example.starmagic;

import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/** 몇 틱 뒤에 실행할 작업을 예약하는 간단한 스케줄러. 마법 연출의 "타이밍"을 담당한다. */
public final class DelayedTasks {
    private record Task(long runAt, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long ticks = 0;

    private DelayedTasks() {}

    public static void add(int delayTicks, Runnable action) {
        TASKS.add(new Task(ticks + delayTicks, action));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        if (TASKS.isEmpty()) return;

        List<Task> due = new ArrayList<>();
        TASKS.removeIf(task -> {
            if (task.runAt() <= ticks) {
                due.add(task);
                return true;
            }
            return false;
        });
        for (Task task : due) {
            task.action().run();
        }
    }
}
