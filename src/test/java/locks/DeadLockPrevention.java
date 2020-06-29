package locks;

import deadlockpreventer.exceptions.DeadlockPreventedException;
import entitylocker.EntityLocker;
import entitylocker.MultiEntityLocker;
import org.junit.*;
import org.junit.rules.Timeout;
import utils.CountDownLatchSilentWaiter;
import utils.SilentLocker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static utils.ThreadUtils.*;

public class DeadLockPrevention {
    private EntityLocker<Integer> entityLocker;
    private SilentLocker<Integer> silentLocker;

    private final static int TEST_TIMEOUT = 10;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;

    private final CountDownLatchSilentWaiter silentWaiter = new CountDownLatchSilentWaiter(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;

    private Throwable expectedException;

    @Rule
    public Timeout testTimeout = new Timeout(TEST_TIMEOUT, TEST_TIME_UNIT);

    @Before
    public void setUp() {
        entityLocker = new MultiEntityLocker<>();
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        silentLocker = new SilentLocker<>(entityLocker);
        expectedException = null;
    }

    @After
    public void checkThreadsException() throws Throwable {
        checkException();
    }

    @Test
    public void testClassicDeadlock() {
        final int[] entitiesIds = entitiesIds(2);

        final Thread subThread = THREAD_STARTER.startThread(() -> {
            silentLocker.lock(entitiesIds[0]);

            silentWaiter.await(subThreadWaiter);

            silentLocker.lock(entitiesIds[1]);
        });

        silentLocker.lock(entitiesIds[1]);

        subThreadWaiter.countDown();
        sleep(1);

        if (assertRightDeadlockPrevented(entitiesIds[0], Thread.currentThread(), subThread)) {
            return;
        }

        fail();
    }

    @Test
    public void testChainDeadLock() {
        final int[] entitiesIds = entitiesIds(3);
        //For more readability
        final CountDownLatch firstSubThreadWaiter = mainThreadWaiter;
        final CountDownLatch secondSubThreadWaiter = subThreadWaiter;

        final Thread firstSubThread = THREAD_STARTER.startThread(() -> {
            silentLocker.lock(entitiesIds[0]);

            silentWaiter.await(firstSubThreadWaiter);

            silentLocker.lock(entitiesIds[1]);
        });

        final Thread secondSubThread = THREAD_STARTER.startThread(() -> {
            silentLocker.lock(entitiesIds[1]);

            firstSubThreadWaiter.countDown();
            silentWaiter.await(secondSubThreadWaiter);

            silentLocker.lock(entitiesIds[2]);
        });

        silentLocker.lock(entitiesIds[2]);
        secondSubThreadWaiter.countDown();
        sleep(1);

        final Thread currentThread = Thread.currentThread();
        assertRightDeadlockPrevented(entitiesIds[0], currentThread, firstSubThread);
        assertRightDeadlockPrevented(entitiesIds[1], currentThread, secondSubThread);
    }

    @Test
    //TODO we cannot lock entity 1 because it's locked before global lock
    public void testCancelGlobalLockDueDeadlock() {
        final int[] entities = entitiesIds(2);

        final Thread subThread = new Thread(() -> {
            silentLocker.lock(entities[0]);

            silentWaiter.await(subThreadWaiter);

            sleep(1);

            DeadlockPreventedException exception = null;
            try {
                entityLocker.lock(entities[1]);
            } catch (DeadlockPreventedException e) {
                exception = e;
            }
            entityLocker.unlock(entities[0]);
            throw new RuntimeException(exception);
        });
        subThread.setUncaughtExceptionHandler((t, e) -> {
            expectedException = e;
        });

        subThread.start();

        silentLocker.lock(entities[1]);
        subThreadWaiter.countDown();
        silentLocker.globalLock();

        sleep(1);

        assertTrue(expectedException.getCause() instanceof DeadlockPreventedException);
        assertTrue(silentLocker.tryLockWithoutTime(1));
    }

    @Test(expected = DeadlockPreventedException.class)
    public void doubleGlobalLockWithEntitiesPrevented() throws DeadlockPreventedException {
        final int[] entitiesIds = entitiesIds(2);

        THREAD_STARTER.startThread(() -> {
            silentLocker.lock(entitiesIds[0]);
            silentLocker.globalLock();
        });

        silentLocker.lock(entitiesIds[1]);
        sleep(1);
        entityLocker.globalLock();
    }

    private boolean assertRightDeadlockPrevented(int entityId, Thread expectedFailThread, Thread expectedLockerThread) {
        try {
            entityLocker.lock(entityId);
        } catch (DeadlockPreventedException e) {
            assertEquals(expectedFailThread, e.failThread());
            assertEquals(expectedLockerThread, e.lockerThread());
            return true;
        }

        return false;
    }

    private int[] entitiesIds(int size) {
        return IntStream.iterate(0, operand -> ++operand)
                .limit(size)
                .toArray();
    }
}
