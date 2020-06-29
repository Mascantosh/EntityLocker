package utils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

/**
 * Util class to simplify and speed up tests writing
 */
public class CountDownLatchSilentWaiter {
    private final int timeOut;
    private final TimeUnit timeUnit;

    public CountDownLatchSilentWaiter(int timeOut, TimeUnit timeUnit) {
        this.timeOut = timeOut;
        this.timeUnit = timeUnit;
    }

    public void await(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await(timeOut, timeUnit);
        } catch (InterruptedException e) {
            fail();
        }
    }
}
