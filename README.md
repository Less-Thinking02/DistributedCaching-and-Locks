# DistributedCaching-and-Locks
Spring Boot Redis Distributed Locking demonstrates cache stampede prevention using Redis (Redisson), PostgreSQL, and a 3-layer locking strategy (Redis Cache → Local Lock → Distributed Lock). Under high concurrency, only one request hits the DB while others serve cached data efficiently.
