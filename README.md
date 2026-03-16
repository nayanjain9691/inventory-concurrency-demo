# Inventory Concurrency Demo (Spring Boot)

This project is a **learning lab** for understanding **concurrency control** and **transaction isolation** in a realistic Spring Boot + JPA application.

It shows, in code, how systems handle:

- **Optimistic locking** (using a version column)
- **Pessimistic locking** (using database row locks)
- **Transaction isolation levels** and read phenomena:
  - Dirty reads
  - Non-repeatable reads
  - Phantom reads

---

## Domain Model

We model a small **inventory / ordering** system:

- **`Product`**
  - Fields: `id`, `sku`, `name`, `stock`, `version`
  - Important: `@Version Long version;` for optimistic locking
  - `stock` is the critical field that concurrent transactions update.

- **`CustomerOrder`**
  - Fields: `id`, `customerId`, `product`, `quantity`, `createdAt`
  - Represents a user reserving stock of a product.

This is enough to simulate multiple users trying to buy the same product at the same time.

---

## Tech Stack

- Java 17+
- Spring Boot 3.x
- Spring Data JPA (Hibernate)
- H2 in-memory database (configured in `application.yml`)
- Gradle (Groovy DSL: `build.gradle`, `settings.gradle`)

---

## Running the App

From the project root:

```bash
cd /Users/najain/learn_lld_hld
gradle bootRun
```

Then open:

- App: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:inventorydb`
  - User: `sa`
  - Password: (empty)

### Seed Data

In the H2 console, insert a product:

```sql
insert into products (sku, name, stock, version)
values ('SKU-1', 'Demo Product', 1, 0);
```

This gives you a product with stock = 1 to race on.

---

## Optimistic Locking

**Goal:** Allow concurrent transactions to proceed without DB locks and detect conflicts at commit time.

### Implementation

- **Entity:** `Product` has a version column:

```java
@Version
private Long version;
```

- **Service:** `InventoryService.placeOrderOptimistic`:
  - Loads product by `sku`
  - Checks `stock`
  - Decrements `stock`
  - Saves an order

At flush/commit time, Hibernate generates an `UPDATE` like:

```sql
update products
   set stock = ?, version = version + 1
 where id = ? and version = ?;
```

If another transaction has already updated the row, the `version` differs and **0 rows are updated**. Hibernate then throws an `OptimisticLockException`.

There is also a retry variant (`placeOrderOptimisticWithRetry`) that catches `OptimisticLockException` and retries up to a configured number of times. This is a typical production pattern.

### How to See It

1. Make sure `SKU-1` has `stock = 1`.
2. Call these two requests nearly at the same time (two terminals or Postman windows):

```bash
curl -X POST "http://localhost:8080/api/inventory/optimistic/order?customerId=A&sku=SKU-1&qty=1"
curl -X POST "http://localhost:8080/api/inventory/optimistic/order?customerId=B&sku=SKU-1&qty=1"
```

**Expected:**

- One request succeeds and decrements stock to 0.
- The other eventually fails with an `OptimisticLockException` in the logs.

This shows **optimistic concurrency control**: we do not lock rows up front, but detect write-write conflicts at commit time.

---

## Pessimistic Locking

**Goal:** Prevent conflicts by locking rows in the database so other transactions must wait.

### Implementation

- **Repository:** `ProductRepository` has locked queries:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Product p where p.sku = :sku")
Optional<Product> findBySkuForUpdate(String sku);
```

This usually becomes `SELECT ... FOR UPDATE` (or vendor equivalent), acquiring an **exclusive row lock**.

- **Service:** `InventoryPessimisticService.placeOrderPessimistic`:
  - Calls `findBySkuForUpdate(sku)` inside a transaction
  - Gets a locked product row
  - Checks and decrements `stock`
  - Saves an order

### What Happens

1. Transaction T1:
   - Calls `findBySkuForUpdate("SKU-1")` → DB locks that row.
2. Transaction T2:
   - Calls the same method for the same SKU.
   - It **blocks**, waiting for T1 to commit/rollback.

Writers are serialized by the DB lock rather than by throwing an exception.

### How to See It

1. Reset product data (in H2 console):

```sql
update products set stock = 1 where sku = 'SKU-1';
delete from customer_orders;
```

2. Fire two pessimistic calls:

```bash
curl -X POST "http://localhost:8080/api/inventory/pessimistic/order?customerId=C&sku=SKU-1&qty=1"
curl -X POST "http://localhost:8080/api/inventory/pessimistic/order?customerId=D&sku=SKU-1&qty=1"
```

**Expected:**

- The second call appears to **hang** until the first one finishes.
- Once the first commits, the second reads the updated stock (likely 0) and fails with "Insufficient stock".

This demonstrates **pessimistic concurrency control**: we prevent conflicts by locking, at the cost of blocking and possible deadlocks.

---

## Isolation Levels & Read Phenomena

In addition to locks, databases control **which data you can see** inside a transaction using **isolation levels**.

This project demonstrates:

- **Dirty read**
- **Non-repeatable read**
- **Phantom read**

These are implemented in `ReportingService` and exposed via `TestScenarioController`.

### Dirty Read

**Definition:** A transaction reads data written by another transaction that has **not yet committed**. If the writer rolls back, the reader saw a value that never really existed.

#### Implementation

- Writer (long-running transaction):

```java
@Transactional
public void updateStockWithoutCommit(String sku, int delta) {
    Product product = productRepository.findBySku(sku).orElseThrow();
    product.setStock(product.getStock() + delta);
    sleep(10000); // keep TX open
}
```

- Reader at `READ_UNCOMMITTED`:

```java
@Transactional(isolation = Isolation.READ_UNCOMMITTED)
public int readStockReadUncommitted(String sku) {
    Product product = productRepository.findBySku(sku).orElseThrow();
    return product.getStock();
}
```

- Endpoints:
  - Writer: `POST /api/tests/dirty-writer?sku=SKU-1&delta=10`
  - Reader: `GET /api/tests/dirty-reader?sku=SKU-1`

#### How to See It

1. Call the **writer** endpoint, which updates the stock and then sleeps for 10 seconds before completing the transaction.
2. During that 10-second window, call the **reader** endpoint.

At `READ_UNCOMMITTED`, the reader can see the **uncommitted** new stock. If you modify the writer to roll back instead of committing, the reader would have observed data that never truly committed → a **dirty read**.

---

### Non-Repeatable Read

**Definition:** Within a single transaction, reading the same row twice returns **different values** because another committed transaction modified that row in between.

#### Implementation

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public int nonRepeatableRead(String sku, long delayMillis) {
    Product p1 = productRepository.findBySku(sku).orElseThrow();
    int first = p1.getStock();
    sleep(delayMillis);
    Product p2 = productRepository.findBySku(sku).orElseThrow();
    int second = p2.getStock();
    return second - first;
}
```

- Endpoint: `GET /api/tests/non-repeatable-read?sku=SKU-1&delayMillis=10000`

#### How to See It

1. Call the non-repeatable read endpoint: it starts a transaction, reads `stock` (first read), sleeps, and then reads `stock` again (second read).
2. While it is sleeping, use another request (or the H2 console) to change the `stock` for `SKU-1` and commit.
3. When the first transaction wakes up and reads again, it sees the updated `stock`.

The return value (second - first) is non-zero, showing a **non-repeatable read**.

---

### Phantom Read

**Definition:** Within a single transaction, a **range query** or aggregate (e.g., `count`) returns a different set of rows because another committed transaction inserted or deleted rows that match the query.

#### Implementation

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public long phantomReadCount(long delayMillis) {
    long count1 = productRepository.count();
    sleep(delayMillis);
    long count2 = productRepository.count();
    return count2 - count1;
}
```

- Endpoint: `GET /api/tests/phantom-read?delayMillis=10000`

#### How to See It

1. Call the phantom read endpoint: it starts a transaction, records `count1`, sleeps, then records `count2`.
2. While it is sleeping, insert a new `Product` in another transaction (via H2 console or a dedicated endpoint) and commit.
3. When the first transaction wakes up and counts again, it may see a higher count.

The difference (`count2 - count1`) represents **phantom rows** that appeared during the transaction.

Note: Actual behavior depends on the database and how strictly it enforces `REPEATABLE_READ` or `SERIALIZABLE` semantics.

---

## Mapping to Theory

- **Shared lock / exclusive lock:**
  - `PESSIMISTIC_READ` behaves like a shared lock (multiple readers, no writers).
  - `PESSIMISTIC_WRITE` behaves like an exclusive lock (single writer, readers often blocked).

- **Optimistic concurrency control:**
  - Implemented via `@Version` on `Product` and optimistic updates in `InventoryService`.
  - Detects conflicts at commit time with `OptimisticLockException`.

- **Pessimistic concurrency control:**
  - Implemented via `@Lock(PESSIMISTIC_WRITE)` queries in `ProductRepository` and `InventoryPessimisticService`.
  - Avoids many conflicts by locking rows and forcing other transactions to wait.

- **Isolation levels:**
  - `READ_UNCOMMITTED` → allows dirty reads (demonstrated in dirty read scenario).
  - `READ_COMMITTED` → prevents dirty reads but allows non-repeatable reads.
  - `REPEATABLE_READ` → prevents non-repeatable reads; phantom reads depend on DB implementation.
  - `SERIALIZABLE` (not explicitly used here, but conceptually) → highest isolation; DB prevents both non-repeatable and phantom reads, typically via locking or aborting transactions.

---

## Summary

This project is a compact, practical reference for:

- How to **configure and use optimistic locking** in JPA (`@Version`).
- How to **use pessimistic locks** (`@Lock`, `PESSIMISTIC_WRITE` / `PESSIMISTIC_READ`).
- How **different isolation levels** affect what anomalies (dirty, non-repeatable, phantom reads) your code can observe.

You can re-run the scenarios any time by:

1. Starting the app (`gradle bootRun`).
2. Seeding a product (`SKU-1`).
3. Hitting the described endpoints with concurrent requests to observe each behavior.

