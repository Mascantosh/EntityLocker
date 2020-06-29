package utils;

import java.util.List;

import static org.junit.Assert.fail;

/**
 * Util class to simplify and speed up tests writing
 */
public class ThreadUtils {
    public static final ThreadStarter THREAD_STARTER = new ThreadStarter("subThread");

    /**
     * @throws java.lang.UnsupportedOperationException for reflection users
     */

    private ThreadUtils() {
        throw new UnsupportedOperationException();
    }

    public static void sleep(double sec) {
        try {
            Thread.sleep((long) (sec * 1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }
    }

    public static void waitThread(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void checkException() throws Throwable {
        final List<ThrowableThread> throwableThreads = THREAD_STARTER.runnedThreads();
        for (int i = 0; i < throwableThreads.size(); ++i) {
            final Throwable exception = throwableThreads.get(i).exception();
            if (exception != null) {
                throw exception;
            }
        }
    }
}
