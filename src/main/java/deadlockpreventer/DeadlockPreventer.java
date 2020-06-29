package deadlockpreventer;

import deadlockpreventer.exceptions.DeadlockPreventedException;

import java.util.*;

public class DeadlockPreventer<T> {
    private final Map<T, Thread> lockedEntities;
    private final Map<Thread, T> waitingThreads;

    /**
     * This map using to fast check if a thread has any locked entities
     */
    private final Map<Thread, Set<T>> threadLockedEntities;
    private Thread globalThread;

    public DeadlockPreventer() {
        this.lockedEntities = new HashMap<>();
        this.waitingThreads = new HashMap<>();
        this.threadLockedEntities = new HashMap<>();
    }

    /**
     * Check deadlock and if all is good add thread to waiting threads
     *
     * @param entityId
     * @throws DeadlockPreventedException
     */
    public synchronized void beforeLocking(final T entityId) throws DeadlockPreventedException {
        checkOnDeadlock(entityId);

        waitingThreads.put(Thread.currentThread(), entityId);
    }

    /**
     * Deadlock prevention mechanism works with global locking in next way:
     * If we have 2 threads and each lock at least locks one entity
     * then {@link DeadlockPreventedException} will be throw or if thread which called global lock holds an entity
     * which some thread is waiting or if some thread want lock an entity which currently hold by global thread
     * otherwise if thread doesn't holds any entity it will be wait global lock
     * @throws DeadlockPreventedException
     */
    public synchronized void beforeGlobalLocking() throws DeadlockPreventedException {
        final Thread currentGlobalThread = Thread.currentThread();

        if (globalThread != null && globalThread != currentGlobalThread) {
            if (lockAnyEntity()) {
                final String message = globalDeadlockPreventedMessage(currentGlobalThread, globalThread);
                throw new DeadlockPreventedException(message, currentGlobalThread, globalThread);
            }
        } else {
            globalThread = currentGlobalThread;
        }

        //This algorithm can be improved by adding 2 hashmaps, but I think it isn't worth

        //I know that we can replace it to stream, but I need a full entry to get a locker thread
        //and it a lot of boilerplate code in streams when we use map entry
        //also I don't like Java streams because they are generate more garbage and less performance
        for (final Map.Entry<Thread, T> entry : waitingThreads.entrySet()) {

            final Thread lockedThread = lockedEntities.get(entry.getValue());

            if (lockedThread == currentGlobalThread) {
                final Thread failThread = entry.getKey();
                final String message = globalDeadlockPreventedMessage(failThread, currentGlobalThread);
                throw new DeadlockPreventedException(message, failThread, currentGlobalThread);
            }
        }

        //puts null as indicator that we wait all other threads
        waitingThreads.put(currentGlobalThread, null);
    }

    /**
     * Remove global thread from waiting threads and reset global thread variable
     */
    public synchronized void beforeGlobalUnlocking() {
        waitingThreads.remove(globalThread);
        globalThread = null;
    }

    /**
     * Switch thread from waiting thread to locked thread
     * Also fill threadLockedEntities map which used in {@link #beforeGlobalLocking()}
     * @param entityId
     * @param isLocked
     */
    public synchronized void afterLocking(final T entityId, final boolean isLocked) {
        final Thread currentThread = Thread.currentThread();

        waitingThreads.remove(currentThread);
        if (isLocked) {
            lockedEntities.put(entityId, currentThread);

            Set<T> threadEntities = threadLockedEntities.get(currentThread);

            if (threadEntities == null) {
                threadEntities = new HashSet<>();
            }

            threadEntities.add(entityId);

            threadLockedEntities.putIfAbsent(currentThread, threadEntities);
        }
    }

    /**
     * Check if thread holds any entity
     * @return
     */
    private boolean lockAnyEntity() {
        return threadLockedEntities.get(Thread.currentThread()) != null;
    }

    /**
     * Before unlocking remove thread from locked entities map and remove thread from entities map
     * if thread doesn't hold any more entities
     * @param entityId
     */
    public synchronized void beforeUnlocking(final T entityId) {
        final Thread currentThread = Thread.currentThread();
        final Set<T> threadEntities = threadLockedEntities.get(currentThread);

        threadEntities.remove(entityId);

        if (threadEntities.isEmpty()) {
            threadLockedEntities.remove(currentThread);
        }

        lockedEntities.remove(entityId);
    }

    /**
     * Check will locking of entityId will case of deadlock
     * <p>
     * Algorithm here is based on cycle detection:
     * Here we get which thread locked the entity
     * If this thread is waiting some entity - search continues
     * While it's get null entity or thread with which we start search
     *
     * @param entityId
     * @throws DeadlockPreventedException if locking entityId will case of deadlock
     */
    private void checkOnDeadlock(T entityId) throws DeadlockPreventedException {
        final T originEntity = entityId;
        final Thread currentThread = Thread.currentThread();

        while (lockedEntities.containsKey(entityId)) {

            final Thread entityThread = lockedEntities.get(entityId);
            entityId = waitingThreads.get(entityThread);

            if (entityThread == currentThread || entityThread == globalThread) {
                final Thread lockerThread = lockedEntities.get(originEntity);
                final String message = deadlockPreventedMessage(currentThread, originEntity, lockerThread);
                throw new DeadlockPreventedException(message, currentThread, lockerThread);
            }
        }
    }

    /**
     * Helper method to create exception message
     * @param failThread
     * @param lockedThread
     * @return
     */
    private String globalDeadlockPreventedMessage(final Thread failThread, final Thread lockedThread) {
        return "Thread {" + failThread + "} unable to acquire global lock due case of deadlock." +
                " Entity pending by {" + lockedThread + "}";
    }

    /**
     * Helper method to create exception message
     * @param failThread
     * @param originEntity
     * @param lockedThread
     * @return
     */
    private String deadlockPreventedMessage(final Thread failThread, final T originEntity, final Thread lockedThread) {
        return "Thread {" + failThread + "} unable to lock entity {" + originEntity + "} due case of deadlock." +
                " Entity pending by {" + lockedThread + "}";
    }
}
