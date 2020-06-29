package utils;

import deadlockpreventer.exceptions.DeadlockPreventedException;
import entitylocker.EntityLocker;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

/**
 * Util class to simplify and speed up tests writing
 */
public class SilentLocker<T> {
    private final EntityLocker<T> entityLocker;

    public SilentLocker(EntityLocker<T> entityLocker) {
        this.entityLocker = entityLocker;
    }

    public void lock(T entityId) {
        try {
            entityLocker.lock(entityId);
        } catch (DeadlockPreventedException e) {
            fail();
        }
    }

    public boolean tryLockWithTime(T entityId, int time, TimeUnit timeUnit) {
        try {
            return entityLocker.tryLock(entityId, time, timeUnit);
        } catch (InterruptedException | DeadlockPreventedException e) {
            fail();
        }

        return false;
    }

    public boolean tryLockWithoutTime(T entityId) {
        try {
            return entityLocker.tryLock(entityId);
        } catch (DeadlockPreventedException e) {
            fail();
        }

        return false;
    }

    public void globalLock() {
        try {
            entityLocker.globalLock();
        } catch (DeadlockPreventedException e) {
            fail();
        }
    }

    public boolean tryGlobalLock() {
        try {
            return entityLocker.tryGlobalLock();
        } catch (DeadlockPreventedException e) {
            fail();
        }

        return false;
    }

    public boolean tryGlobalLock(int time, TimeUnit timeUnit) {
        try {
            return entityLocker.tryGlobalLock(time, timeUnit);
        } catch (DeadlockPreventedException e) {
            fail();
        }
        return false;
    }

    public boolean tryLock(T entityId) {
        try {
            return entityLocker.tryLock(entityId);
        } catch (DeadlockPreventedException e) {
            fail();
        }

        return false;
    }
}
