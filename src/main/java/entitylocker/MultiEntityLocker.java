package entitylocker;

import deadlockpreventer.DeadlockPreventer;
import deadlockpreventer.exceptions.DeadlockPreventedException;
import utils.function.BooleanReturnFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static log.utils.LogUtils.logDebugCurrentThread;
import static log.utils.LogUtils.logError;

/**
 * This entity locker doesn't support null entities
 * @see java.util.concurrent.ConcurrentHashMap
 *
 * Also here I put a lot of debug messages
 * It can be removed, but from my point of view logging in any application a unnecessary thing and
 * it's better to be a async logging
 *
 * @param <T>
 */
public class MultiEntityLocker<T> implements EntityLocker<T> {
    private final Map<T, ReentrantLock> entityLocks;
    private final DeadlockPreventer<T> deadlockPreventer;
    private final EscalatorDetector escalatorDetector;

    /**
     * The main idea of implementing global lock and escalation is using double read write locks
     * Explanation:
     * We lock globalLock.writeLock only in {@link #globalLock()}, {@link #tryGlobalLock()} and
     * {@link #tryGlobalLock(long, TimeUnit)}
     * <p>
     * After acquiring the write lock we need to prevent other threads to lock anything for this purpose we using
     * read lock of global lock
     * <p>
     * After that we waiting write lock of nonGlobalLock as indicator that all other threads complete their work
     *
     * P.S I think it's good to move implementation of global lock to separate class, but I found a solution very late
     */
    private final ReentrantReadWriteLock globalLock;
    private final ReentrantReadWriteLock nonGlobalLock;

    /**
     * This field used in {@link #unlockReadLock()} and {@link #restoreReadLock()}
     * <p>
     * To upgrade read lock to write lock (due escalation or global lock call)
     * firstly, we need unlock all read lock of this thread
     */
    private int globalLocks;

    public MultiEntityLocker() {
        this(100);
    }

    public MultiEntityLocker(final int minLocksBeforeGlobal) {
        this.entityLocks = new ConcurrentHashMap<>();
        this.deadlockPreventer = new DeadlockPreventer<>();
        this.escalatorDetector = new EscalatorDetector(minLocksBeforeGlobal);
        globalLock = new ReentrantReadWriteLock();
        nonGlobalLock = new ReentrantReadWriteLock();
    }

    @Override
    public void lock(final T entityId) throws DeadlockPreventedException {
        lock(entityId, waitingLock());
    }

    @Override
    public boolean tryLock(final T entityId) throws DeadlockPreventedException {
        return lock(entityId, Lock::tryLock);
    }


    @Override
    public boolean tryLock(final T entityId, final long timeout, final TimeUnit timeUnit) throws DeadlockPreventedException {
        return lock(entityId, silentTryLockFunction(timeout, timeUnit));
    }

    @Override
    public void unlock(final T entityId) {
        final ReentrantLock entityLock = entityLocks.get(entityId);

        if (entityLock == null) {
            final String message = "There is no locks for entity {" + entityId + "}";
            logError(message);
            throw new IllegalMonitorStateException(message);
        }

        final Thread currentThread = Thread.currentThread();
        if (!entityLock.isHeldByCurrentThread()) {
            final String message = currentThread + " cannot unlock entity - {" + entityId + "} because it's hold by other thread";
            logError(message);
            throw new IllegalAccessError(message);
        }

        if (entityLock.getHoldCount() == 1) {
            logDebugCurrentThread("It's last lock for entity {" + entityId + "} removing it");
            deadlockPreventer.beforeUnlocking(entityId);
            entityLocks.remove(entityId);
        } else {
            logDebugCurrentThread("unlock entity {" + entityId + "} current hold count is " + (entityLock.getHoldCount() - 1));
        }

        nonGlobalLock.readLock().unlock();
        entityLock.unlock();

        if (escalatorDetector.decThreadEntityCounter()) {
            logDebugCurrentThread("deescalate global lock");
            globalUnlock();
        }
    }

    @Override
    public boolean isLockedByCurrentThread(final T entityId) {
        final ReentrantLock entityLock = entityLocks.get(entityId);

        return entityLock != null && entityLock.isHeldByCurrentThread() || globalLock.isWriteLockedByCurrentThread();
    }

    @Override
    public void globalLock() throws DeadlockPreventedException {
        globalLock(waitingLock());
    }

    @Override
    public boolean tryGlobalLock() throws DeadlockPreventedException {
        return globalLock(Lock::tryLock);
    }

    @Override
    public boolean tryGlobalLock(final long timeout, final TimeUnit unit) throws DeadlockPreventedException {
        deadlockPreventer.beforeGlobalLocking();

        final long nanos = unit.toNanos(timeout);

        //I know that is bad to use System.nanoTime(), but we need guaranteed that we wait approximate expected time
        //If we will try locking both locks with given timeout it's 2 times greater
        //But maybe it's just wrong implementation using double read write locks
        final long start = System.nanoTime();
        if (!silentTryLockWithNanos(this.globalLock::writeLock, nanos)) {
            return false;
        }
        final long end = System.nanoTime();

        unlockReadLock();

        final boolean isLockGranted = silentTryLockWithNanos(nonGlobalLock::writeLock, nanos - (end - start));

        restoreReadLock();

        return isLockGranted;
    }

    @Override
    public void globalUnlock() {
        logDebugCurrentThread("release global lock");
        globalLocks = 0;
        deadlockPreventer.beforeGlobalUnlocking();
        escalatorDetector.cancelEscalation();
        nonGlobalLock.writeLock().unlock();
        globalLock.writeLock().unlock();
    }

    @Override
    public int currentSize() {
        return entityLocks.size();
    }

    /**
     * Steps to acquire non global lock
     * When we call non global locking
     * firstly, we need lock global read lock to prevent parallel global locking
     * <p>
     * if we have a thread which locked any entity before global lock we pass it
     * because we need to wait other threads executions.
     * If we will have a deadlock situation we have 2 cases here:
     * If fail thread was a global lock - we cancel global locking
     * otherwise user who try to lock an entity which locked by global lock should be unlocked
     *
     * @param entityId
     * @param lockFunction
     * @return
     * @throws DeadlockPreventedException
     */
    private boolean lock(final T entityId, final BooleanReturnFunction<Lock> lockFunction) throws DeadlockPreventedException {
        logDebugCurrentThread("try gain lock for entity {" + entityId + "}");
        logDebugCurrentThread("check global lock");

        if (escalatorDetector.currentThreadLockedEntities() == 0) {
            if (!lockFunction.apply(globalLock.readLock())) {
                return false;
            }

            //Here we lock both to guaranteed that no one else will locked global write lock
            nonGlobalLock.readLock().lock();
            globalLock.readLock().unlock();
        } else {
            nonGlobalLock.readLock().lock();
        }

        final ReentrantLock entityLock;
        try {
            entityLock = getEntityLock(entityId);
        } catch (DeadlockPreventedException e) {
            nonGlobalLock.readLock().unlock();
            throw e;
        }

        if (entityLock.isLocked() && !entityLock.isHeldByCurrentThread()) {
            logDebugCurrentThread("waiting lock for entity {" + entityId + "}");
        }

        final boolean isLockGranted = lockFunction.apply(entityLock);

        if (isLockGranted) {
            logDebugCurrentThread("gain lock for entity {" + entityId + "}");
        } else {
            logDebugCurrentThread("cannot gain lock for entity {" + entityId + "}");
        }

        //Using putIfAbsent here because we can get a lock which other thread can remove in unlock() method
        entityLocks.putIfAbsent(entityId, entityLock);

        afterLocking(entityId, isLockGranted);

        return isLockGranted;
    }

    /**
     * Steps to acquire global lock
     * When we call global locking
     * Firstly, we need to check deadlock
     * Secondly - try to acquire global write lock - if it's succeed then try promote read lock from non global lock
     * to write lock and after that we have fully granted global lock
     *
     * @param lockFunction
     * @return
     * @throws DeadlockPreventedException
     */
    private boolean globalLock(final BooleanReturnFunction<Lock> lockFunction) throws DeadlockPreventedException {
        deadlockPreventer.beforeGlobalLocking();

        logDebugCurrentThread("waiting global lock");
        if (!lockFunction.apply(globalLock.writeLock())) {
            return false;
        }

        unlockReadLock();

        logDebugCurrentThread("waiting other threads completion for acquiring global lock");
        final boolean isLockGranted = lockFunction.apply(nonGlobalLock.writeLock());
        logDebugCurrentThread("acquire global lock");

        restoreReadLock();

        return isLockGranted;
    }

    /**
     * Helper function to exclude boilerplate code
     *
     * @param lock
     * @param timeout
     * @return
     */
    private boolean silentTryLockWithNanos(final Supplier<Lock> lock, final long timeout) {
        try {
            return lock.get().tryLock(timeout, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Helper function to exclude boilerplate code
     *
     * @param timeout
     * @param timeUnit
     * @return
     */
    private BooleanReturnFunction<Lock> silentTryLockFunction(final long timeout, final TimeUnit timeUnit) {
        return lock -> {
            try {
                return lock.tryLock(timeout, timeUnit);
            } catch (InterruptedException e) {
                return false;
            }
        };
    }


    /**
     * Add locked entity to deadlock preventer and check does the thread needs escalation
     */
    private void afterLocking(final T entityId, final boolean isLockGranted) throws DeadlockPreventedException {
        deadlockPreventer.afterLocking(entityId, isLockGranted);
        callEscalationIfNeeds(isLockGranted);
    }

    /**
     * If some thread locks to many entities promote it lock to global lock
     */
    private void callEscalationIfNeeds(final boolean isLockGranted) throws DeadlockPreventedException {
        if (isLockGranted && escalatorDetector.incThreadEntityCounter()) {
            logDebugCurrentThread("start promotion to global lock due escalation");
            globalLock();
        }
    }

    /**
     * @param entityId
     * @return get {@link java.util.concurrent.locks.ReentrantLock} associated with entityId
     */
    private ReentrantLock getEntityLock(final T entityId) throws DeadlockPreventedException {
        final ReentrantLock existingLock = existingLock(entityId);

        if (existingLock.isLocked() && !existingLock.isHeldByCurrentThread()) {
            deadlockPreventer.beforeLocking(entityId);
        }

        return existingLock;
    }

    /**
     * Restore all read locks which was unlocked due lock promotion
     */
    private void restoreReadLock() {
        nonGlobalReadLockEvaluator(Lock::lock);
    }

    /**
     * Unlock all read lock from thread for promotion it to global lock
     */
    private void unlockReadLock() {
        globalLocks = escalatorDetector.currentThreadLockedEntities();
        nonGlobalReadLockEvaluator(Lock::unlock);
    }

    /**
     * Helper function to exclude boilerplate code
     *
     * @return
     */
    private BooleanReturnFunction<Lock> waitingLock() {
        return lock -> {
            lock.lock();
            return true;
        };
    }

    /**
     * Helper function to exclude boilerplate code
     *
     * @param locker
     */
    private void nonGlobalReadLockEvaluator(Consumer<Lock> locker) {
        for (int i = 0; i < globalLocks; ++i) {
            locker.accept(nonGlobalLock.readLock());
        }
    }


    /**
     * @param entityId
     * @return existing lock for entityId if there is no one create new {@link java.util.concurrent.locks.ReentrantLock}
     */
    private ReentrantLock existingLock(final T entityId) {
        return entityLocks.computeIfAbsent(entityId, t -> new ReentrantLock());
    }
}
