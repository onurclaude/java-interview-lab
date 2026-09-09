# java-interview-lab — Project Plan

This is not a CRUD app. It is a "Java/Spring Interview Laboratory": every topic below is
demonstrated with a **bad/** (wrong but compiling) implementation, a **good/** implementation,
an explanation of *why* the bad version is wrong, and at least one test that *proves* the
behavior (not just exercises the code).

## Architecture

Single Spring Boot 3.x / Java 21 / Maven module. Packages are organized by **domain topic**,
not by technical layer:

```
src/main/java/com/interviewlab/
    persistence/    Phase 1 - persistence context, dirty checking, flush, entity lifecycle
    transaction/    Phase 1 - rollback, isolation, propagation, self-invocation/proxy
    locking/        Phase 2 - optimistic locking, pessimistic locking, deadlock
    concurrency/    Phase 3 - Thread/Runnable/Callable, race conditions, synchronized,
                    ReentrantLock, ReadWriteLock, volatile, atomics, ThreadLocal,
                    deadlock/livelock/starvation
    executor/       Phase 4 - ExecutorService, ThreadPoolExecutor tuning
    async/          Phase 4 - CompletableFuture, Spring @Async
    scopes/         Phase 5 - singleton/prototype/request/session/application
    aop/            Phase 5 - custom annotation + @Around aspect, self-invocation
    exception/      Phase 5 - exception hierarchy, @RestControllerAdvice, rollback rules
    patterns/       Phase 6 - Strategy, Factory, Template Method, Observer, Decorator,
                    Adapter, Facade, Builder, Singleton, Proxy, Chain of Responsibility
    collections/    Phase 6 - HashMap vs ConcurrentHashMap, compound ops, CopyOnWriteArrayList
    immutability/   Phase 6 - mutable shared state vs immutable/record
    equalshashcode/ Phase 6 - equals/hashCode contract, JPA entity pitfalls
    optionaltopic/  Phase 6 - Optional misuse/correct use, orElse vs orElseGet
    streamtopic/    Phase 6 - Stream API misuse/correct use, parallelStream pitfalls
    jpa/            Phase 7 - N+1, lazy/eager, fetch join, EntityGraph, DTO projection
    web/            REST endpoints ("Demo API") wiring into the labs above
```

Within a topic package, where a bad/good contrast applies:

```
locking/optimistic/bad/...
locking/optimistic/good/...
```

Docs live in `docs/*.md` (one per topic, format: Problem / Bad Code / Why It Is Wrong /
What Actually Happens / How To Reproduce / Correct Code / Trade-offs / Interview Q&A /
30-second answer / Follow-ups) plus `docs/interview-scenarios.md` (50+ chained interview
questions) and `docs/cheat-sheet.md`.

## Technology

Java 21, Spring Boot 3.3.x, Maven + Maven Wrapper, Spring Data JPA/Hibernate, PostgreSQL,
Testcontainers (PostgreSQL), H2 (only for pure in-memory Spring-context tests where a real
DB adds nothing — bean scopes/AOP/exceptions), JUnit 5, AssertJ, Awaitility, Spring AOP,
Bean Validation, Docker Compose. No Lombok. Constructor injection everywhere.

## Checklist

Legend: `[ ]` not started, `[~]` in progress, `[x]` done (bad+good+why+repro+test+docs present).

### Phase 1 — Persistence / Transaction
- [x] 1. Persistence context (transient/managed/detached/removed, dirty checking, flush)
- [x] 2. Flush / dirty checking explicit demo (save vs flush vs saveAndFlush)
- [x] 3. Transaction rollback (checked vs unchecked, rollbackFor)
- [x] 4. Isolation levels (dirty/non-repeatable/phantom read, lost update on Postgres)
- [x] 5. Propagation (REQUIRED, REQUIRES_NEW, NESTED + the other 4 briefly)
- [x] 6. Transaction proxy / self-invocation

### Phase 2 — Locking
- [x] 7. Optimistic locking (@Version, bad variants, bounded retry+backoff)
- [x] 8. Pessimistic locking (PESSIMISTIC_WRITE, FOR UPDATE, blocking)
- [x] 9. Lost update (shown under optimistic/pessimistic + isolation)
- [x] 10. Deadlock (lock-ordering deadlock + fix via deterministic ordering)

### Phase 3 — Java Concurrency
- [x] 11. Thread / Runnable / Callable / Future basics + lifecycle
- [x] 12. Race condition (bank account balance)
- [x] 13. synchronized (method + block, intrinsic monitor)
- [x] 14. ReentrantLock / ReadWriteLock / StampedLock
- [x] 15. volatile (visibility vs atomicity)
- [x] 16. Atomic primitives (CAS, ABA + AtomicStampedReference)
- [x] 17. ThreadLocal (leak on reuse vs try/finally remove)
- [x] 18. Deadlock / Livelock / Starvation / Race condition write-ups (dedicated demos)

### Phase 4 — Executors / Async
- [x] 19. ExecutorService (fixed/cached/single-thread pitfalls)
- [x] 20. ThreadPoolExecutor production tuning (bounded queue, rejection policies)
- [x] 21. CompletableFuture (composition, custom executor, exceptions)
- [x] 22. Spring @Async (self-invocation, custom executor, exception handling)

### Phase 5 — Spring
- [x] 23. Bean scopes (singleton mutable-state bug, prototype-in-singleton, request/session/application)
- [x] 24. AOP (@TrackExecutionTime aspect, self-invocation)
- [x] 25. Exception handling (hierarchy, @RestControllerAdvice, swallowing/wrapping)

### Phase 6 — Java / Patterns
- [x] 26. Design patterns (Strategy, Factory, Template Method, Observer, Decorator, Adapter, Facade, Builder, Singleton, Proxy, Chain of Responsibility)
- [x] 27. Collections / concurrent collections (HashMap CME, ConcurrentHashMap, compound ops, CopyOnWriteArrayList)
- [x] 28. Immutability (mutable shared state, defensive copy, records)
- [x] 29. equals/hashCode (HashSet corruption, mutable key, JPA entity pitfalls)
- [x] 30. Optional (get() misuse, orElse vs orElseGet eager/lazy)
- [x] 31. Stream API (side effects, parallelStream shared mutable state)

### Phase 7 — Hibernate
- [x] 32. Lazy loading / LazyInitializationException / Open Session in View
- [x] 33. N+1 problem (Order/OrderItem/Product)
- [x] 34. Fetch strategies (fetch join, @EntityGraph, DTO projection) + EAGER pitfall

### Phase 8 — Interview material
- [x] 35. docs/interview-scenarios.md (50+ chained Q&A)
- [x] 36. docs/cheat-sheet.md
- [x] 37. Final root README.md with topic table + START HERE

## Demo REST API (subset of topics, not all)
- [x] POST /lab/optimistic-lock/reset, /run
- [x] POST /lab/pessimistic-lock/run
- [x] POST /lab/scopes/singleton/race, GET /lab/scopes/request
- [x] POST /lab/executor/run
- [x] POST /lab/completable-future/run
- [x] POST /lab/transaction/rollback, /lab/transaction/requires-new

## Definition of Done (per topic)
bad example, good example, written explanation of why bad is wrong, reproducible failure,
passing test(s), docs entry, one-paragraph interview answer.
