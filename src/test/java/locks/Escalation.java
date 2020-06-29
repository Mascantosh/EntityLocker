package locks;

import deadlockpreventer.exceptions.DeadlockPreventedException;
import entitylocker.EntityLocker;
import entitylocker.MultiEntityLocker;
import org.junit.*;
import org.junit.rules.Timeout;
import utils.CountDownLatchSilentWaiter;
import utils.SilentLocker;
import utils.ThrowableThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static utils.ThreadUtils.*;

public class Escalation {
    private EntityLocker<Integer> entityLocker;
    private SilentLocker<Integer> silentLocker;

    private final static int TEST_TIMEOUT = 15;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;

    private final CountDownLatchSilentWaiter silentWaiter = new CountDownLatchSilentWaiter(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;

    @Rule
    public Timeout testTimeout = new Timeout(TEST_TIMEOUT, TEST_TIME_UNIT);

    @Before
    public void setUp() {
        entityLocker = new MultiEntityLocker<>(5);
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        silentLocker = new SilentLocker<>(entityLocker);
    }

    @After
    public void checkThreadsException() throws Throwable {
        checkException();
    }

    @Test
    public void testEscalation() {
        final CountDownLatch afterTryLock = new CountDownLatch(1);
        final ThrowableThread firstSubThread = THREAD_STARTER.startThread(() -> {
            final int startEntity = 0;
            final int lockEntities = 2;
            multiEntitiesEvaluator(silentLocker::lock, startEntity, lockEntities);

            mainThreadWaiter.countDown();
            silentWaiter.await(afterTryLock);

            multiEntitiesEvaluator(entityLocker::unlock, startEntity, lockEntities);
            subThreadWaiter.countDown();
        });

        final ThrowableThread secondSubThread = THREAD_STARTER.startThread(() -> {
            silentWaiter.await(mainThreadWaiter);
            sleep(1);
            for (int i = 0; i < 5; i++) {
                assertFalse(silentLocker.tryLockWithTime(i, 100, TimeUnit.MICROSECONDS));
            }
            afterTryLock.countDown();
        });

        final int startEntity = 2;
        final int lockEntities = 4;
        final int lastEntity = multiEntitiesEvaluator(silentLocker::lock, startEntity, lockEntities);

        silentWaiter.await(mainThreadWaiter);
        silentLocker.lock(lastEntity);

        silentWaiter.await(afterTryLock);
        silentWaiter.await(subThreadWaiter);

        multiEntitiesEvaluator(entityLocker::unlock, startEntity, lockEntities + 1);
        waitThread(firstSubThread);
        waitThread(secondSubThread);
    }

    //TODO
    @Test(expected = DeadlockPreventedException.class)
    public void escalationInterruptDueDeadLock() {
        final int startEntity = 2;
        final int lockEntities = 4;
        final int lastEntity = multiEntitiesEvaluator(silentLocker::lock, startEntity, lockEntities);

        final CountDownLatch escalationPreventLock = new CountDownLatch(1);
        final CountDownLatch mainEscalation = new CountDownLatch(1);

        final ThrowableThread subThread = THREAD_STARTER.startThread(() -> {
            final int entityId = 0;

            silentLocker.lock(entityId);
            silentLocker.lock(entityId + 1);

            mainThreadWaiter.countDown();

            sleep(1);

            lockWithIgnoreException(entityId + 2);

            sleep(1);

            entityLocker.unlock(entityId + 1);
            entityLocker.unlock(entityId);

            silentWaiter.await(subThreadWaiter);
            assertTrue(silentLocker.tryLock(lastEntity));
            mainEscalation.countDown();
            entityLocker.unlock(lastEntity);

            silentWaiter.await(escalationPreventLock);
            assertFalse(silentLocker.tryLock(lastEntity + 1));
        });

        silentWaiter.await(mainThreadWaiter);

        silentLocker.lock(lastEntity);

        entityLocker.unlock(lastEntity);
        subThreadWaiter.countDown();
        silentWaiter.await(mainEscalation);

        silentLocker.lock(lastEntity);
        escalationPreventLock.countDown();

        waitThread(subThread);
    }

    private int multiEntitiesEvaluator(IntConsumer entityLocker, int start, int count) {
        final int end = start + count;
        for (int i = start; i < end; ++i) {
            entityLocker.accept(i);
        }

        return end;
    }

    private void lockWithIgnoreException(int entityId) {
        try {
            entityLocker.lock(entityId);
        } catch (DeadlockPreventedException ignore) {

        }
    }
}
