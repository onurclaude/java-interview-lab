# java-interview-lab

A **Java/Spring Interview Laboratory** — not a CRUD app. Every major interview topic
(transactions, locking, concurrency, Spring internals, JPA, design patterns) is demonstrated
as a **bad** (compiles, but wrong) implementation next to a **good** one, with a real test
that *proves* the difference, run against a real PostgreSQL database via Testcontainers.

The goal: after going through this repo, "optimistic locking nedir?" gets an answer like
*"I tried it — two transactions read the same version, the first committed, the second's
`UPDATE ... WHERE version=?` matched zero rows and Hibernate threw
`ObjectOptimisticLockingFailureException`"* — not a textbook definition.

## Tech stack

Java 21 · Spring Boot 3.3 · Maven + Maven Wrapper · Spring Data JPA / Hibernate · PostgreSQL
· Testcontainers · H2 (only for pure in-memory Spring-context tests) · JUnit 5 · AssertJ ·
Awaitility · Spring AOP · Bean Validation · Docker Compose. No Lombok. Constructor injection
everywhere except the one documented, justified exception (`EntityManager` field injection).

## Running it

```bash
docker compose up -d       # starts PostgreSQL for `./mvnw spring-boot:run`
./mvnw clean test          # runs the whole suite (Testcontainers starts its own Postgres)
./mvnw spring-boot:run     # starts the app on :8080 for the Demo API (see below)
```

Tests do **not** need `docker compose up` — they start their own Testcontainers PostgreSQL
(reused across test classes if `~/.testcontainers.properties` has
`testcontainers.reuse.enable=true`).

## Topic map

| # | Topic | Bad | Good | Test | Doc |
|---|---|---|---|---|---|
| 1 | Persistence context / flush | `persistence.bad.*` | `persistence.good.*` | `PersistenceLifecycleServiceTest` | [docs/persistence-context.md](docs/persistence-context.md) |
| 2 | Transaction rollback | `transaction.rollback.bad.*` | `transaction.rollback.good.*` | `RollbackBehaviorTest` | [docs/transactions.md](docs/transactions.md) |
| 3 | Transaction proxy / self-invocation | `transaction.propagation.bad.*` | `transaction.propagation.good.*` | `PropagationSelfInvocationTest` | [docs/transactions.md](docs/transactions.md) |
| 4 | Isolation levels | — | `transaction.isolation.*` | `IsolationLevelsTest` | [docs/isolation.md](docs/isolation.md) |
| 5 | Propagation (all 7 types) | — | `transaction.propagation.demo.*` | `PropagationShowcaseTest` | [docs/propagation.md](docs/propagation.md) |
| 6 | Optimistic locking | `locking.optimistic.bad.*` | `locking.optimistic.good.*` | `OptimisticLockingTest` | [docs/optimistic-locking.md](docs/optimistic-locking.md) |
| 7 | Pessimistic locking + DB deadlock | `locking.pessimistic.bad.*`, `locking.deadlock.*` | `locking.pessimistic.good.*`, `locking.deadlock.*` | `PessimisticLockingTest` | [docs/pessimistic-locking.md](docs/pessimistic-locking.md) |
| 8 | Thread basics / lifecycle | `concurrency.thread.bad.*` | `concurrency.thread.good.*` | `ThreadLifecycleTest`, `ThreadCreationBoundsTest` | [docs/java-locks.md](docs/java-locks.md) |
| 9 | Race conditions | `concurrency.race.bad.*` | `concurrency.race.good.*` | `RaceConditionTest` | [docs/java-locks.md](docs/java-locks.md) |
| 10 | synchronized / ReentrantLock / ReadWriteLock / StampedLock | `concurrency.synchronization.bad.*` | `concurrency.synchronization.good.*` | `SynchronizationTest` | [docs/java-locks.md](docs/java-locks.md) |
| 11 | volatile / Atomic / ABA | `concurrency.volatiletopic.bad.*` | `concurrency.volatiletopic.good.*`, `concurrency.atomic.*` | `VolatileAndAtomicTest` | [docs/atomic.md](docs/atomic.md) |
| 12 | ThreadLocal | `concurrency.threadlocal.bad.*` | `concurrency.threadlocal.good.*` | `ThreadLocalTest` | [docs/java-locks.md](docs/java-locks.md) |
| 13 | Deadlock / Livelock / Starvation | `concurrency.pathologies.*` | `concurrency.pathologies.*` | `DeadlockLivelockStarvationTest` | [docs/java-locks.md](docs/java-locks.md) |
| 14 | ExecutorService / ThreadPoolExecutor | `executor.bad.*` | `executor.good.*` | `ExecutorServiceTest` | [docs/executor-service.md](docs/executor-service.md) |
| 15 | CompletableFuture | `async.completablefuture.bad.*` | `async.completablefuture.good.*` | `CompletableFutureTest` | [docs/completable-future.md](docs/completable-future.md) |
| 16 | Spring @Async | `async.spring.bad.*` | `async.spring.good.*` | `AsyncSelfInvocationTest` | [docs/async.md](docs/async.md) |
| 17 | Bean scopes | `scopes.singleton.bad.*`, `scopes.prototype.bad.*` | `scopes.singleton.good.*`, `scopes.prototype.good.*`, `scopes.webscopes.*` | `BeanScopesTest` | [docs/bean-scopes.md](docs/bean-scopes.md) |
| 18 | Spring AOP | `aop.bad.*` | `aop.good.*` | `AopSelfInvocationTest` | [docs/aop.md](docs/aop.md) |
| 19 | Exception hierarchy | `exception.bad.*` | `exception.good.*` | `ExceptionHierarchyTest` | [docs/exceptions.md](docs/exceptions.md) |
| 20 | Design patterns (11) | `patterns.*.bad.*` | `patterns.*` | `DesignPatternsTest` | [docs/design-patterns.md](docs/design-patterns.md) |
| 21 | Collections / concurrent collections | `collectionstopic.bad.*` | `collectionstopic.good.*` | `CollectionsConcurrencyTest` | [docs/cheat-sheet.md](docs/cheat-sheet.md) |
| 22 | Immutability | `immutability.bad.*` | `immutability.good.*` | `ImmutabilityTest` | [docs/cheat-sheet.md](docs/cheat-sheet.md) |
| 23 | equals/hashCode | `equalshashcode.bad.*` | `equalshashcode.good.*` | `EqualsHashCodeTest` | [docs/cheat-sheet.md](docs/cheat-sheet.md) |
| 24 | Optional | `optionaltopic.bad.*` | `optionaltopic.good.*` | `OptionalTest` | [docs/cheat-sheet.md](docs/cheat-sheet.md) |
| 25 | Stream API | `streamtopic.bad.*` | `streamtopic.good.*` | `StreamApiTest` | [docs/cheat-sheet.md](docs/cheat-sheet.md) |
| 26 | N+1 / lazy / fetch strategies | `jpa.bad.*` | `jpa.good.*` | `NPlusOneTest` | [docs/n-plus-one.md](docs/n-plus-one.md) |

Also see [docs/interview-scenarios.md](docs/interview-scenarios.md) (50+ chained interview
Q&A) and [docs/cheat-sheet.md](docs/cheat-sheet.md) (one-line-per-fact quick reference).

## Demo API (Postman-friendly, subset of topics)

With the app running (`./mvnw spring-boot:run`, Postgres via `docker compose up -d`):

```
POST /lab/optimistic-lock/reset                                  -> { productId, stock, version }
POST /lab/optimistic-lock/run?productId=..&amount=1
POST /lab/pessimistic-lock/run?productId=..&amount=1
POST /lab/executor/run?tasks=20
POST /lab/completable-future/run
POST /lab/transaction/rollback?mode=checked|fixed
POST /lab/transaction/requires-new?selfInvocation=true|false
GET  /lab/scopes/request
GET  /lab/scopes/session
GET  /lab/scopes/application
GET  /lab/scopes/prototype
POST /lab/scopes/singleton/race?basePrice=100&discountPercentage=0.1
POST /lab/exceptions/charge?cardToken=tok_123&amount=50.0
```

Not every topic has an endpoint — concurrency/locking races are far more reliably
demonstrated as tests (deterministic latches) than as HTTP calls, so those stay in the test
suite. The endpoints above exist for interactive, manual exploration.

## START HERE

This repository is meant to be worked through, not skimmed. Suggested loop, per topic:

1. Read the **bad** implementation's Javadoc (WHAT IS WRONG / WHY / WHAT CAN HAPPEN IN
   PRODUCTION / HOW TO REPRODUCE / HOW TO FIX).
2. Run the matching test (`./mvnw test -Dtest=ClassName`) and watch it **prove** the bad
   behavior — read the console log (thread names, SQL statements, versions) as it runs.
3. Read the **good** implementation and see exactly what changed.
4. Run the good test and confirm it passes.
5. Read the topic's `docs/*.md` — especially the "30-Second Interview Answer".
6. **Before looking at the code again**, answer the doc's "Common Interview Questions" out
   loud, in your own words, as if in an interview.
7. Move to the next topic in `PROJECT_PLAN.md`'s phase order — Persistence/Transaction →
   Locking → Java Concurrency → Executors/Async → Spring → Patterns/Java → Hibernate.

Suggested order for a first pass (matches how the phases build on each other):
persistence-context → transactions → propagation → isolation → optimistic-locking →
pessimistic-locking → java-locks → atomic → executor-service → completable-future → async →
bean-scopes → aop → exceptions → design-patterns → n-plus-one → cheat-sheet →
interview-scenarios.

See `PROJECT_STATUS.md` for exactly what's implemented and tested right now.
