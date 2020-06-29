package deadlockpreventer.exceptions;

import static log.utils.LogUtils.logError;

/**
 * Create this exception as checked because we don't manage all locks in DB as user and someone can lock faster then we
 */
public class DeadlockPreventedException extends Exception {
    private final Thread lockerThread;
    private final Thread failThread;

    public DeadlockPreventedException(String message, Thread failThread, Thread lockedThread) {
        super(message);

        logError(message);

        this.lockerThread = lockedThread;
        this.failThread = failThread;
    }

    public Thread lockerThread() {
        return lockerThread;
    }

    public Thread failThread() {
        return failThread;
    }
}
