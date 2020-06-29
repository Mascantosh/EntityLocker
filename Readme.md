EntityLocker
------------

The task is to create a reusable utility class that provides synchronization mechanism similar to row-level DB locking.

The class is supposed to be used by the components that are responsible for managing storage and caching of different type of entities in the application. EntityLocker itself does not deal with the entities, only with the IDs (primary keys) of the entities.

Requirements:

-[x] EntityLocker should support different types of entity IDs.

-[x] EntityLocker’s interface should allow the caller to specify which entity does it want to work with (using entity ID), and designate the boundaries of the code that should have exclusive access to the entity (called “protected code”).

-[x] For any given entity, EntityLocker should guarantee that at most one thread executes protected code on that entity. If there’s a concurrent request to lock the same entity, the other thread should wait until the entity becomes available.

-[x] EntityLocker should allow concurrent execution of protected code on different entities.


Bonus requirements (optional):

-[x] Allow reentrant locking.

-[x] Allow the caller to specify timeout for locking an entity.

-[x] Implement protection from deadlocks (but not taking into account possible locks outside EntityLocker).

-[x] Implement global lock. Protected code that executes under a global lock must not execute concurrently with any other protected code.

-[x] Implement lock escalation. If a single thread has locked too many entities, escalate its lock to be a global lock.

Extra:

-[x] Add logging. By default, it's disabled in "production" mode and enabled in tests.
