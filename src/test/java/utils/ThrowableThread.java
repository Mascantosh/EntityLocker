package utils;

/**
 * Util class for catch exception from threads in tests
 */
public class ThrowableThread extends Thread {
    private Throwable exception;

    public ThrowableThread(Runnable target) {
        super(target);
    }

    public Throwable exception() {
        return exception;
    }

    @Override
    public void run() {
        try {
            super.run();
        } catch (Throwable e) {
            exception = e;
        }
    }
}
