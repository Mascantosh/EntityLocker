package utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Util class to simplify and speed up tests writing
 */
public class ThreadStarter {
    private final String threadPrefix;
    private int threadCount;
    private final List<ThrowableThread> runnedThreads;

    public ThreadStarter(String threadPrefix) {
        this.threadPrefix = threadPrefix;
        this.threadCount = 0;
        runnedThreads = new ArrayList<>();
    }

    public ThrowableThread startThread(Runnable runnable) {
        final ThrowableThread thread = new ThrowableThread(runnable);
        thread.setName(createName());
        runnedThreads.add(thread);
        thread.start();
        return thread;
    }

    public List<ThrowableThread> runnedThreads() {
        final List<ThrowableThread> runnedThreads = new ArrayList<>(this.runnedThreads);
        this.runnedThreads.clear();
        return runnedThreads;
    }

    private String createName() {
        return threadPrefix + "-" + threadCount++;
    }
}
