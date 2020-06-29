package entitylocker;

import log.utils.LogUtils;
import utils.counter.Counter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class EscalatorDetector {
    private final Map<Thread, Counter> lockedEntitiesByThread;

    private final AtomicInteger totalLocks;
    private final int minLocksBeforeGlobal;
    private Thread escalatedThread;

    public EscalatorDetector(final int minLocksBeforeGlobal) {
        this.minLocksBeforeGlobal = minLocksBeforeGlobal;
        this.lockedEntitiesByThread = new ConcurrentHashMap<>();
        this.totalLocks = new AtomicInteger();
    }

    /**
     * Increase entity counter which current thread holds and return true if escalation needs
     *
     * @return true - if it needs escalate thread lock to global lock.
     * See {@link EscalatorDetector#isNeedEscalation(int)}
     */
    public boolean incThreadEntityCounter() {
        final Thread currentThread = Thread.currentThread();
        Counter counter = lockedEntitiesByThread.get(currentThread);

        if (counter == null) {
            counter = new Counter();
            lockedEntitiesByThread.put(currentThread, counter);
        } else {
            counter.inc();
        }

        totalLocks.incrementAndGet();

        return acquireEscalatedThread(isNeedEscalation(counter.count()));
    }

    private synchronized boolean acquireEscalatedThread(final boolean isNeedEscalation) {
        if (isNeedEscalation && escalatedThread == null) {
            escalatedThread = Thread.currentThread();
            return true;
        }

        return false;
    }

    /**
     * Decrement entity counter and removes thread from {@link #lockedEntitiesByThread} if its count is zero
     *
     * @return true if need cancel escalation
     */
    public boolean decThreadEntityCounter() {
        final Thread currentThread = Thread.currentThread();

        final Counter counter = lockedEntitiesByThread.get(currentThread);

        counter.dec();

        if (counter.count() == 0) {
            lockedEntitiesByThread.remove(currentThread);
        }

        totalLocks.decrementAndGet();

        return deescalateThread(!isNeedEscalation(counter.count()));
    }

    /**
     * Shows if we need deescalate escalated thread
     * @param isNeedDeescalation
     * @return true - deescalation needs otherwise false
     */
    private synchronized boolean deescalateThread(final boolean isNeedDeescalation) {
        return isNeedDeescalation && escalatedThread == Thread.currentThread();
    }

    /**
     * Reseting {@link #escalatedThread} variable in separated thread and not in {@link #deescalateThread(boolean)}
     * to prevent race condition with {@link #isNeedEscalation(int)}
     */
    public synchronized void cancelEscalation() {
        escalatedThread = null;
    }

    /**
     * @return count of entities locked by caller thread
     */
    public int currentThreadLockedEntities() {
        final Counter counter = lockedEntitiesByThread.get(Thread.currentThread());
        return counter == null ? 0 : counter.count();
    }

    /**
     * @param count - current holden entities by current thread
     * @return true if {@link EscalatorDetector#totalLocks} great or equal then {@link EscalatorDetector#minLocksBeforeGlobal} and
     * current thread holden entities greater then {@link EscalatorDetector#totalLocks} divided by 2
     */
    private boolean isNeedEscalation(final int count) {
        final int allLocks = totalLocks.get();
        LogUtils.logDebug("Total lock/Current Thread locks = " + allLocks + "/" + count);
        return count >= minLocksBeforeGlobal && (count > (allLocks >> 1));
    }
}
