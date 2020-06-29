package locks;

import deadlockpreventer.exceptions.DeadlockPreventedException;
import entitylocker.EntityLocker;
import entitylocker.MultiEntityLocker;
import locks.entity.SimpleEntity;
import org.junit.*;
import org.junit.rules.Timeout;
import utils.CountDownLatchSilentWaiter;
import utils.SilentLocker;
import utils.ThreadUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static utils.ThreadUtils.*;

public class GlobalLocking {
    private EntityLocker<Integer> entityLocker;
    private SilentLocker<Integer> silentLocker;

    private final static int TEST_TIMEOUT = 300;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;

    private final CountDownLatchSilentWaiter silentWaiter = new CountDownLatchSilentWaiter(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;
    private SimpleEntity mainEntity;
    private SimpleEntity subEntity;

    @Rule
    public Timeout testTimeout = new Timeout(TEST_TIMEOUT, TEST_TIME_UNIT);

    @Before
    public void setUp() {
        entityLocker = new MultiEntityLocker<>();
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        mainEntity = new SimpleEntity(1);
        subEntity = new SimpleEntity(2);
        silentLocker = new SilentLocker<>(entityLocker);
    }

    @After
    public void checkThreadsException() throws Throwable {
        ThreadUtils.checkException();
    }

    @Test
    public void testGlobalLocking() {
        final int time = 1;
        final TimeUnit second = TimeUnit.SECONDS;
        THREAD_STARTER.startThread(() -> {
            silentWaiter.await(subThreadWaiter);
            assertFalse(silentLocker.tryLockWithTime(mainEntity.id, time, second));
        });

        THREAD_STARTER.startThread(() -> {
            silentWaiter.await(subThreadWaiter);
            assertFalse(silentLocker.tryLockWithTime(subEntity.id, time, second));
        });

        silentLocker.globalLock();

        subThreadWaiter.countDown();

        sleep(2);

        entityLocker.globalUnlock();

        silentLocker.globalLock();
        entityLocker.globalUnlock();
    }

    @Test
    public void testGlobalLockingCorrectlyWorksWithOtherThreads() {
        final int expected = 3;
        THREAD_STARTER.startThread(() -> {
            silentLocker.lock(mainEntity.id);
            mainThreadWaiter.countDown();

            mainEntity.value = 2;
            sleep(2);

            entityLocker.unlock(mainEntity.id);
        });

        final Thread secondThread = THREAD_STARTER.startThread(() -> {
            silentWaiter.await(subThreadWaiter);
            silentLocker.lock(subEntity.id);

            subEntity.value = 2;

            entityLocker.unlock(subEntity.id);
        });

        silentWaiter.await(mainThreadWaiter);

        silentLocker.globalLock();
        subThreadWaiter.countDown();

        mainEntity.value = expected;
        subEntity.value = expected;

        entityLocker.globalUnlock();

        waitThread(secondThread);

        assertEquals(expected, mainEntity.value);
        assertEquals(expected - 1, subEntity.value);
    }

    @Test
    public void testTryGlobalLocking() {
        THREAD_STARTER.startThread(() -> {
            assertTrue(silentLocker.tryGlobalLock(1, TimeUnit.SECONDS));

            mainThreadWaiter.countDown();
            silentWaiter.await(subThreadWaiter);

            entityLocker.globalUnlock();
        });

        silentWaiter.await(mainThreadWaiter);

        assertFalse(silentLocker.tryGlobalLock(1, TimeUnit.SECONDS));
        subThreadWaiter.countDown();

        assertTrue(silentLocker.tryGlobalLock(1, TimeUnit.SECONDS));
        entityLocker.globalUnlock();
    }

    @Test
    //TODO race condition if we have locked threads allow them to lock other entities
    public void testGlobalLockAllowLockEntitiesFromTheSameThread() {
        THREAD_STARTER.startThread(() -> {
            silentLocker.lock(2);

            mainThreadWaiter.countDown();
            sleep(1);
            silentLocker.lock(1);
            sleep(2);
            entityLocker.unlock(1);
            entityLocker.unlock(2);
        });
        THREAD_STARTER.startThread(() -> {
            silentWaiter.await(subThreadWaiter);
            silentLocker.lock(3);
            entityLocker.unlock(3);
        });
        silentWaiter.await(mainThreadWaiter);
        silentLocker.globalLock();

        subThreadWaiter.countDown();

        assertTrue(silentLocker.tryLock(1));
        assertTrue(silentLocker.tryLock(2));

        subThreadWaiter.countDown();
        sleep(1);

        entityLocker.globalUnlock();
    }

    @Test(expected = DeadlockPreventedException.class)
    public void cannotAcquireTwoGlobalLocksIfThreadsLocksEntities() throws DeadlockPreventedException {
        THREAD_STARTER.startThread(() -> {
            silentWaiter.await(subThreadWaiter);
            silentLocker.globalLock();
            mainThreadWaiter.countDown();
        });

        silentLocker.lock(1);
        subThreadWaiter.countDown();
        sleep(1);
        entityLocker.globalLock();
    }
}
