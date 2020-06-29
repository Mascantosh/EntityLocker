package locks;

import entitylocker.EntityLocker;
import entitylocker.MultiEntityLocker;
import org.junit.*;
import org.junit.rules.Timeout;
import utils.CountDownLatchSilentWaiter;
import utils.SilentLocker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.junit.Assert.*;
import static utils.ThreadUtils.THREAD_STARTER;
import static utils.ThreadUtils.checkException;

public class LockingUnlocking {
    private EntityLocker<Integer> entityLocker;
    private SilentLocker<Integer> silentLocker;
    private final static int ENTITY_ID = 1;

    private final static int TEST_TIMEOUT = 15;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;

    private final CountDownLatchSilentWaiter silentWaiter = new CountDownLatchSilentWaiter(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;

    @Rule
    public Timeout testTimeout = new Timeout(TEST_TIMEOUT, TEST_TIME_UNIT);

    @Before
    public void setUp() {
        entityLocker = new MultiEntityLocker<>();
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        silentLocker = new SilentLocker<>(entityLocker);
    }

    @After
    public void checkThreadsException() throws Throwable {
        checkException();
    }

    @Test(expected = IllegalMonitorStateException.class)
    public void testUnlockNonLockingThread() {
        entityLocker.unlock(ENTITY_ID);
    }

    @Test
    public void testUnlockDeletion() {
        silentLocker.lock(ENTITY_ID);
        entityLocker.unlock(1);
        assertEquals(0, entityLocker.currentSize());
    }

    @Test(expected = IllegalAccessError.class)
    public void testUnlockFromOtherThread() {
        THREAD_STARTER.startThread(() -> {
            silentLocker.lock(ENTITY_ID);

            mainThreadWaiter.countDown();
            silentWaiter.await(subThreadWaiter);

            entityLocker.unlock(ENTITY_ID);
        });

        silentWaiter.await(mainThreadWaiter);

        assertFalse(entityLocker.isLockedByCurrentThread(ENTITY_ID));

        try {
            entityLocker.unlock(ENTITY_ID);
        } finally {
            subThreadWaiter.countDown();
        }
    }

    @Test
    public void testReentrantLocking() {
        THREAD_STARTER.startThread(() -> {
            multipleEntityLockerEvaluator(silentLocker::lock, ENTITY_ID, 2);

            mainThreadWaiter.countDown();
            silentWaiter.await(subThreadWaiter);

            multipleEntityLockerEvaluator(entityLocker::unlock, ENTITY_ID, 2);
            assertFalse(entityLocker.isLockedByCurrentThread(ENTITY_ID));
        });

        silentWaiter.await(mainThreadWaiter);

        assertFalse(entityLocker.isLockedByCurrentThread(ENTITY_ID));

        subThreadWaiter.countDown();

        multipleEntityLockerEvaluator(silentLocker::lock, ENTITY_ID, 2);
        multipleEntityLockerEvaluator(entityLocker::unlock, ENTITY_ID, 2);
        assertFalse(entityLocker.isLockedByCurrentThread(ENTITY_ID));
    }

    @Test
    public void testReentrantTryLocking() {
        final int waitTime = 1;
        final TimeUnit seconds = TimeUnit.SECONDS;
        THREAD_STARTER.startThread(() -> {
            assertTrue(silentLocker.tryLockWithTime(ENTITY_ID, waitTime, seconds));
            assertTrue(silentLocker.tryLockWithTime(ENTITY_ID, waitTime, seconds));

            entityLocker.unlock(ENTITY_ID);

            mainThreadWaiter.countDown();
            silentWaiter.await(subThreadWaiter);

            entityLocker.unlock(ENTITY_ID);
        });

        silentWaiter.await(mainThreadWaiter);

        assertFalse(silentLocker.tryLockWithTime(ENTITY_ID, waitTime, seconds));

        subThreadWaiter.countDown();

        assertTrue(silentLocker.tryLockWithTime(ENTITY_ID, waitTime, seconds));
        assertTrue(silentLocker.tryLockWithTime(ENTITY_ID, waitTime, seconds));
        multipleEntityLockerEvaluator(entityLocker::unlock, ENTITY_ID, 2);
    }

    @Test
    public void testTryLockingWithoutTimeOut() {
        THREAD_STARTER.startThread(() -> {
            silentLocker.tryLockWithoutTime(ENTITY_ID);

            mainThreadWaiter.countDown();
            silentWaiter.await(subThreadWaiter);

            entityLocker.unlock(ENTITY_ID);
        });

        silentWaiter.await(mainThreadWaiter);

        assertFalse(silentLocker.tryLockWithoutTime(ENTITY_ID));

        subThreadWaiter.countDown();
    }

    @Test
    public void testTryLockWithTimeOut() {
        final int waitTime = 1;
        final TimeUnit seconds = TimeUnit.SECONDS;
        THREAD_STARTER.startThread(() -> {
            assertTrue(silentLocker.tryLockWithTime(ENTITY_ID, waitTime, seconds));

            mainThreadWaiter.countDown();
            silentWaiter.await(subThreadWaiter);

            entityLocker.unlock(ENTITY_ID);
        });

        silentWaiter.await(mainThreadWaiter);

        assertFalse(silentLocker.tryLockWithTime(ENTITY_ID, waitTime, seconds));

        subThreadWaiter.countDown();
    }

    @Test
    public void testReentrantGlobalLock() {
        silentLocker.globalLock();
        silentLocker.globalLock();

        entityLocker.globalUnlock();
        entityLocker.globalUnlock();
    }

    @Test
    public void testTryReentrantGlobalLock() {
        silentLocker.tryGlobalLock();
        silentLocker.tryGlobalLock();

        entityLocker.globalUnlock();
        entityLocker.globalUnlock();
    }

    @Test
    public void testTryReentrantGlobalLockWithTime() {
        final int amount = 1;
        final TimeUnit seconds = TimeUnit.SECONDS;

        silentLocker.tryGlobalLock(amount, seconds);
        silentLocker.tryGlobalLock(amount, seconds);
        entityLocker.globalUnlock();
        entityLocker.globalUnlock();
    }

    private void multipleEntityLockerEvaluator(IntConsumer entityConsumer, int entityId, int times) {
        for (int i = 0; i < times; ++i) {
            entityConsumer.accept(entityId);
        }
    }
}
