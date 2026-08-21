# Spring Boot Redis Distributed Locking

A mini project demonstrating **distributed locking** and **cache stampede prevention** using Spring Boot, Redis (Redisson), and PostgreSQL.

## Problem Statement

When100 concurrent requests hit the same endpoint and the cache is empty, without proper locking:
- All100 threads hit the database simultaneously → DB overload
- Duplicate queries waste resources
- Response times spike

This is called the **Thundering Herd Problem** or **Cache Stampede**.

## Solution: 3-Layer Locking Strategy

```
Request → Layer 1: Redis Cache Check → Layer 2: Local JVM Lock → Layer 3: Distributed Lock (Redisson) → DB
```

| Layer | Type | Purpose |
|-------|------|---------|
| Layer 1 | Redis Cache | Fast path — skip everything if data exists |
| Layer 2 | Local Lock (`ReentrantLock`) | Prevents duplicate DB calls within same JVM |
| Layer 3 | Distributed Lock (Redisson) | Prevents duplicate DB calls across multiple instances |

## How It Works

```
Thread 1:  Cache empty → Acquires lock → Hits DB → Saves to Redis → Returns
Thread 2:  Cache empty → Waits for lock → Cache populated → Returns (no DB call)
Thread 3:  Cache empty → Waits for lock → Cache populated → Returns (no DB call)
...
Thread 100: Cache populated at Step 1 → Returns immediately (no lock needed)
```

## Tech Stack

- Java 17
- Spring Boot 3.x
- Redis (via Redisson)
- PostgreSQL
- Docker Compose (Redis + PostgreSQL)

## Prerequisites

- Java 17+
- Docker & Docker Compose
- Maven

## Setup

###1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- Redis on `localhost:6379`
- PostgreSQL on `localhost:5432`

###2. Run the Application

```bash
mvn spring-boot:run
```

###3. Load Test

```bash
# Install k6 (https://k6.io/)
k6 run loadtest.js --vus 200 --duration 30s
```

Or use any load testing tool (JMeter, Apache Bench, etc.) to send200 concurrent requests to:

```
GET http://localhost:8080/api/users/1
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | Get all users |
| GET | `/api/users/{id}` | Get user by ID (with caching) |
| POST | `/api/users` | Create user |
| PUT | `/api/users/{id}` | Update user |
| DELETE | `/api/users/{id}` | Delete user |

## Expected Output (Load Test)

```
👑 REDISSON GLOBAL WINNER! Hitting PostgreSQL for ID: 1
💾 Stored user 1 in Redis cache (TTL: 10 minutes)
✅ CACHE HIT after local lock! Serving from Redis for ID: 1
✅ CACHE HIT after local lock! Serving from Redis for ID: 1
✅ CACHE HIT! Serving from Redis for ID: 1
✅ CACHE HIT! Serving from Redis for ID: 1
...
```

- **1 thread** hits the database (the winner)
- **Threads 2-5** wait at local lock, then get cache hit
- **Threads 6-200** find cache populated at Step 1, return immediately (zero contention)

## Project Structure

```
src/main/java/com/example/springpractice/
├── config/
│   └── RedissonConfig.java      # Redisson client configuration
├── controller/
│   └── UserController.java      # REST endpoints
├── dto/
│   ├── UserRequest.java
│   └── UserResponse.java
├── model/
│   └── User.java                # JPA entity
├── repository/
│   └── UserRepository.java      # Spring Data JPA
├── service/
│   └── UserService.java         # Business logic + locking
└── util/
    └── UserMapper.java
```

## Key Concepts Learned

- **Cache Stampede**: When multiple threads try to populate an empty cache simultaneously
- **Thundering Herd Problem**: Uncontrolled concurrent requests hitting the database
- **Distributed Locking**: Using Redisson to coordinate locks across JVM instances
- **Local vs Distributed Locks**: JVM-level vs network-level synchronization
- **Cache Bypass Pattern**: Threads that find populated cache skip the lock entirely

## License

MIT
