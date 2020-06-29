package entitylocker;

import deadlockpreventer.exceptions.DeadlockPreventedException;

import java.util.concurrent.TimeUnit;

/**
 * Utility class that provides synchronization mechanism similar to row-level DB locking. The class is supposed to be used by the components
 * that are responsible for managing storage and caching of different type of entities in the application. It does not deal with the entities,
 * only with the IDs (primary keys) of the entities.
 * Main features are:
 * <p>
 * Support different entity ID types
 * The caller is able to specify which entity it wants to work with (using entity ID), and designate the boundaries of the code that should
 * have exclusive access to the entity (called “protected code”)
 * For any given entity, is's guaranteed that at most one thread executes protected code on that entity. If there’s a concurrent request to
 * lock the same entity, the other thread should wait until the entity becomes available
 * Support of concurrent execution of protected code on different entities
 * Support of reentrant locking
 * Support of timeout for entity locking
 * Protection from deadlocks
 * <p>
 * Probably:
 * GlobalLocking
 * LockEscalation
 * For more details please read Readme.md
 *
 * @param <T> the type of element id
 *            Locks methods under hood using {@link java.util.concurrent.locks.ReentrantLock}
 */
public interface EntityLocker<T> {
    /**
     * Lock specified entity if it isn't acquire deadlock.
     * Don't throws {@link deadlockpreventer.exceptions.DeadlockPreventedException} during escalation
     *
     * @param entityId
     * @throws deadlockpreventer.exceptions.DeadlockPreventedException if locking entity will case of deadlock
     */
    void lock(T entityId) throws DeadlockPreventedException;

    /**
     * Same as {@link #lock(T)} but don't suspend a caller thread
     *
     * @param entityId
     * @return true if lock success and false otherwise
     * @throws InterruptedException
     */
    boolean tryLock(T entityId) throws DeadlockPreventedException;


    /**
     * Same as {@link #tryLock(T)} with timeout that suspend a caller thread
     *
     * @param entityId
     * @param timeout
     * @param unit
     * @return true if lock success and false otherwise
     * @throws InterruptedException
     */
    boolean tryLock(T entityId, long timeout, TimeUnit unit) throws InterruptedException, DeadlockPreventedException;

    /**
     * Unlock specified entity
     *
     * @param entityId
     * @throws {@link java.lang.IllegalMonitorStateException} if entity doesn't have any lock
     * @throws {@link java.lang.IllegalAccessError} if entity locked by other thread
     */
    void unlock(T entityId);

    /**
     * @param entityId
     * @return true if caller thread lock specified entity otherwise false
     */
    boolean isLockedByCurrentThread(T entityId);

    /**
     * Acquire global lock
     * While this lock activated no other thread cannot lock any entity except caller thread
     */
    void globalLock() throws DeadlockPreventedException;

    /**
     * Same as {@link #globalLock()} but don't suspend a caller thread
     * @return
     * @throws DeadlockPreventedException
     */
    boolean tryGlobalLock() throws DeadlockPreventedException;

    /**
     * Work the same as {@link EntityLocker#tryLock(T, long, TimeUnit)} but for global lock
     *
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    boolean tryGlobalLock(long timeout, TimeUnit unit) throws DeadlockPreventedException;

    /**
     * Release global lock
     */
    void globalUnlock();

    /**
     * @return current count of locked entities
     */
    int currentSize();
}
