package locks;

import entitylocker.EntityLocker;
import entitylocker.MultiEntityLocker;
import locks.entity.SimpleEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import utils.CountDownLatchSilentWaiter;
import utils.SilentLocker;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static utils.ThreadUtils.*;

public class ConcurrentEvalutaion {
    private final static int TEST_TIMEOUT = 15;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;
    private SilentLocker<Integer> silentLocker;

    private SimpleEntity mainEntity;

    private final CountDownLatchSilentWaiter silentWaiter = new CountDownLatchSilentWaiter(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;

    private EntityLocker<Integer> entityLocker;

    @Before
    public void setUp() throws Exception {
        entityLocker = new MultiEntityLocker<>();
        silentLocker = new SilentLocker<>(entityLocker);
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        mainEntity = new SimpleEntity(1);
    }

    @After
    public void checkThreadsException() throws Throwable {
        checkException();
    }

    @Test
    public void testSimultaneouslyRunOfOneEntity() {
        THREAD_STARTER.startThread(() -> {
            silentLocker.lock(mainEntity.id);
            mainEntity.value = 1;

            mainThreadWaiter.countDown();
            silentWaiter.await(subThreadWaiter);

            entityLocker.unlock(mainEntity.id);
        });
        silentWaiter.await(mainThreadWaiter);

        assertEquals(1, mainEntity.value);

        assertFalse(silentLocker.tryLockWithTime(mainEntity.id, 1, TimeUnit.SECONDS));

        subThreadWaiter.countDown();

        assertTrue(silentLocker.tryLockWithTime(mainEntity.id, 1, TimeUnit.SECONDS));

        mainEntity.value = 2;

        entityLocker.unlock(mainEntity.id);
        assertEquals(2, mainEntity.value);
    }

    @Test
    public void testThatThreadWaitsEntityUnlock() {
        final int expected = 3;
        THREAD_STARTER.startThread(() -> {
            silentLocker.lock(mainEntity.id);

            mainThreadWaiter.countDown();

            mainEntity.value = 2;

            entityLocker.unlock(mainEntity.id);
        });
        silentWaiter.await(mainThreadWaiter);

        silentLocker.lock(mainEntity.id);
        mainEntity.value = expected;
        entityLocker.unlock(mainEntity.id);

        assertEquals(expected, mainEntity.value);
    }

    @Test
    public void testEvaluateConcurrentlyDifferentEntities() {
        final int singleAction = 1;
        final int threadsCount = 1_000;

        CountDownLatch waitThreadsLockEntities = new CountDownLatch(threadsCount);
        CountDownLatch allThreadsCompleteWork = new CountDownLatch(singleAction);
        CountDownLatch threadsUnlockEntities = new CountDownLatch(threadsCount);

        for (int i = 0; i < threadsCount; i++) {
            int entityId = i;
            THREAD_STARTER.startThread(() -> {
                silentLocker.lock(entityId);
                waitThreadsLockEntities.countDown();

                silentWaiter.await(allThreadsCompleteWork);

                entityLocker.unlock(entityId);
                threadsUnlockEntities.countDown();
            });
        }
        silentWaiter.await(waitThreadsLockEntities);

        allThreadsCompleteWork.countDown();

        silentWaiter.await(threadsUnlockEntities);

        assertEquals(0, entityLocker.currentSize());
    }
}
