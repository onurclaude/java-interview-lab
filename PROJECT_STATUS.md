# Project Status

Live tracker. Updated as each topic reaches Definition of Done (see PROJECT_PLAN.md).

Status legend: NOT STARTED / IN PROGRESS / DONE

| # | Topic | Status |
|---|-------|--------|
| 1 | Persistence context | DONE |
| 2 | Flush / dirty checking | DONE |
| 3 | Transaction rollback | DONE |
| 4 | Isolation levels | DONE |
| 5 | Propagation | DONE |
| 6 | Transaction proxy / self-invocation | DONE |
| 7 | Optimistic locking | DONE |
| 8 | Pessimistic locking | DONE |
| 9 | Lost update | DONE (covered under optimistic + isolation) |
| 10 | Deadlock (DB) | DONE |
| 11 | Thread/Runnable/Callable/Future | DONE |
| 12 | Race condition | DONE |
| 13 | synchronized | DONE |
| 14 | ReentrantLock/ReadWriteLock/StampedLock | DONE |
| 15 | volatile | DONE |
| 16 | Atomic primitives | DONE |
| 17 | ThreadLocal | DONE |
| 18 | Deadlock/Livelock/Starvation (pure Java) | DONE |
| 19 | ExecutorService | DONE |
| 20 | ThreadPoolExecutor tuning | DONE |
| 21 | CompletableFuture | DONE |
| 22 | Spring @Async | DONE |
| 23 | Bean scopes | DONE |
| 24 | AOP | DONE |
| 25 | Exception handling | DONE |
| 26 | Design patterns | DONE |
| 27 | Collections / concurrent collections | DONE |
| 28 | Immutability | DONE |
| 29 | equals/hashCode | DONE |
| 30 | Optional | DONE |
| 31 | Stream API | DONE |
| 32 | Lazy loading / LazyInitializationException | DONE |
| 33 | N+1 | DONE |
| 34 | Fetch strategies | DONE |
| 35 | interview-scenarios.md | DONE |
| 36 | cheat-sheet.md | DONE |
| 37 | Final README | DONE |

## Demo API
All listed endpoints implemented in `com.interviewlab.web.LabDemoController` and
`com.interviewlab.scopes.webscopes.ScopesController` (scopes) and
`com.interviewlab.exception.web.PaymentDemoController` (exceptions).

## Build log

- Full project scaffold (Maven wrapper, pom.xml, docker-compose.yml, Testcontainers base
  class, SQL statement recorder) — working.
- Fixed during integration: Spring bean-name collisions (`OrderService` used in two
  packages, `Product`/`ProductRepository` used in two packages), a missing `@Component` on
  a simulated third-party SDK class, a livelock-demo synchronization bug (needed a second
  barrier to force true contention), and a flaky wall-clock-based starvation test (replaced
  with a deterministic fairness-contract proof).
- A real, reproducible full-suite hang was root-caused via `jstack` thread dumps (not the
  earlier static-initializer-block theory, which was a red herring): `RaceConditionTest`
  submitted 500 tasks to a fixed 16-thread pool, where each task counts down a
  `taskCount`-sized "ready" latch before waiting on a "start" latch — with `taskCount >
  poolSize`, only 16 tasks could ever run, so `ready` could never reach zero and the main
  thread blocked forever on an un-timed `ready.await()`. Fixed by sizing the pool to
  `taskCount`. Fixing this exposed 9 further real, previously-never-exercised test bugs
  across `SynchronizationTest`, `OptimisticLockingTest`, `PessimisticLockingTest`,
  `PersistenceLifecycleServiceTest`, and `PropagationShowcaseTest` (wrong assertions, a
  same-thread `ReentrantLock` reentrancy blind spot, Postgres/Hibernate 6 emitting `FOR NO
  KEY UPDATE` instead of `FOR UPDATE`, Spring's JPA exception translation wrapping
  `TransactionRequiredException`, a commit-time implicit flush nobody accounted for, and —
  the big one — `Propagation.NESTED` being fundamentally unusable with `HibernateJpaDialect`
  because its transaction-data object never implements `SavepointManager`, confirmed by
  decompiling Spring's own bytecode). All were fixed with verified, evidence-based
  corrections rather than loosened assertions.
- Latest full `./mvnw clean test` run (2026-09-08, port 5433): **132 tests, 0 failures, 0
  errors, 0 skipped, BUILD SUCCESS** across all 27 concrete test classes.

## Known gaps / possible follow-ups
- Design patterns, collections, immutability, equals/hashCode, Optional, and Stream API
  topics are each covered by one focused bad/good pair rather than exhaustively covering
  every sub-variant mentioned in the original spec (e.g. Stream API's "unnecessary stream
  usage" and "nested stream readability" points are covered lightly, in docs/cheat-sheet.md,
  rather than with dedicated bad/good classes).
- No dedicated REST endpoints for every single lab (by design - see README's Demo API
  section for the rationale).
