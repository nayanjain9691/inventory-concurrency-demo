## Concepts Cheat Sheet

This section is a quick refresher on the main concurrency / isolation concepts demonstrated in this project.

### 1. Optimistic Locking

- **Idea:** Assume conflicts are rare. Let transactions proceed without taking DB locks up front; detect conflicts at commit time.
- **How it works (JPA):**
    - Add a `@Version` column to the entity.
    - On update, the SQL includes `where id = ? and version = ?`.
    - If another transaction has already changed the row, the `update` affects 0 rows → `OptimisticLockException`.
- **Pros:**
    - High throughput when contention is low.
    - No long‑held locks → less blocking, fewer deadlocks.
- **Cons:**
    - Conflicts appear as failures at commit time → caller must handle retries.
    - Not great for highly contended “hot” rows.

### 2. Pessimistic Locking

- **Idea:** Assume conflicts are likely. Lock the row in the database as soon as you read it so others must wait.
- **How it works (JPA):**
    - Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` or `PESSIMISTIC_READ` on queries.
    - This typically generates `SELECT ... FOR UPDATE` (or vendor equivalent).
    - Other transactions trying to read/write the same row will block or fail, depending on DB and lock mode.
- **Pros:**
    - Avoids optimistic conflicts by serializing access.
    - Good for very contended resources where retries would be constant.
- **Cons:**
    - Can cause blocking and reduce concurrency.
    - Risk of deadlocks if locks are taken in different orders.
    - Needs careful timeout and error handling.

### 3. Database Locks (Shared vs Exclusive)

- **Shared lock (read lock):**
    - Allows multiple readers at the same time.
    - Blocks writers from updating the locked resource.
    - Conceptually similar to `PESSIMISTIC_READ`.
- **Exclusive lock (write lock):**
    - Only one holder; blocks other readers and writers (depending on DB).
    - Conceptually similar to `PESSIMISTIC_WRITE`.
- **Granularity:**
    - Locks can be on rows, pages, tables, or even databases.
    - Finer‑grained locks → more concurrency but more overhead.

### 4. Transaction Isolation Levels

Isolation level controls **what changes from other transactions you can see** while your transaction is running.

From weakest to strongest (SQL standard):

1. **READ UNCOMMITTED**
    - Can see uncommitted changes from other transactions.
    - Allows: dirty reads, non‑repeatable reads, phantom reads.
2. **READ COMMITTED** (very common default)
    - Only sees data that has been committed.
    - Prevents: dirty reads.
    - Still allows: non‑repeatable reads and phantom reads.
3. **REPEATABLE READ**
    - Once you read a row, you will see the **same values** for that row in the same transaction.
    - Prevents: dirty reads, non‑repeatable reads.
    - Phantom reads may still occur (DB‑dependent).
4. **SERIALIZABLE**
    - Strongest isolation; behaves as if transactions run one after another.
    - Prevents: dirty reads, non‑repeatable reads, phantom reads.
    - Often implemented by more locking, or by aborting conflicting transactions.

### 5. Read Phenomena (Anomalies)

These are the “weird behaviors” that weaker isolation levels can allow.

- **Dirty Read**
    - A transaction reads data that another transaction has written but **not yet committed**.
    - If the writer rolls back, the reader has seen a value that never truly existed.
    - Only possible at `READ UNCOMMITTED` (or vendor‑specific extensions).

- **Non‑Repeatable Read**
    - Within a single transaction, you read the **same row twice** and get **different values**.
    - Happens when another transaction commits an update between your two reads.
    - Allowed at `READ COMMITTED`.
    - Prevented at `REPEATABLE READ` and `SERIALIZABLE`.

- **Phantom Read**
    - Within a single transaction, you run the **same query over a range** (e.g., `WHERE price > 100`) twice and get **a different set of rows**.
    - Happens when another transaction inserts or deletes rows that match your query between your two reads.
    - Allowed at `READ COMMITTED` and often at `REPEATABLE READ`.
    - Prevented at `SERIALIZABLE`.

### 6. How These Fit Together

- **Optimistic vs Pessimistic:**
    - Optimistic: let conflicts happen, detect on commit, retry if needed.
    - Pessimistic: prevent conflicts by locking, at the cost of blocking.
- **Locks vs Isolation:**
    - Locks protect **who can read/write** a resource concurrently.
    - Isolation levels define **which committed/uncommitted changes you can see** while your transaction is running.
- **Design trade‑offs:**
    - High‑throughput, low contention → optimistic locking + moderate isolation (e.g., READ COMMITTED).
    - Highly contended, correctness‑critical resources → pessimistic locking + stronger isolation (REPEATABLE READ or SERIALIZABLE).