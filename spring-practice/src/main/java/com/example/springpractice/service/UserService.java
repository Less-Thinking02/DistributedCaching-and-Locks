package com.example.springpractice.service;

import com.example.springpractice.model.User;
import com.example.springpractice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedissonClient redissonClient;

    // Layer 1: Local JVM locks (one per user ID)
    private final ConcurrentHashMap<Long, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        String cacheKey = "users::" + id;
        String distributedLockKey = "lock::user::" + id;

        // Step 1: Check Redis cache first (fast path)
        RMapCache<String, User> cache = redissonClient.getMapCache("users");
        User cachedUser = cache.get(cacheKey);
        if (cachedUser != null) {
            System.out.println("✅ CACHE HIT! Serving from Redis for ID: " + id);
            return cachedUser;
        }

        // Step 2: Layer 1 - Local Request Coalescing (JVM-level lock)
        ReentrantLock localLock = localLocks.computeIfAbsent(id, k -> new ReentrantLock());
        localLock.lock();
        try {
            // Double-check cache after acquiring local lock
            cachedUser = cache.get(cacheKey);
            if (cachedUser != null) {
                System.out.println("✅ CACHE HIT after local lock! Serving from Redis for ID: " + id);
                return cachedUser;
            }

            // Step 3: Layer 2 - Distributed Lock (Network-level lock)
            RLock distributedLock = redissonClient.getLock(distributedLockKey);
            distributedLock.lock(10, TimeUnit.SECONDS);
            try {
                // Double-check cache after acquiring distributed lock
                cachedUser = cache.get(cacheKey);
                if (cachedUser != null) {
                    System.out.println("✅ CACHE HIT after distributed lock! Serving from Redis for ID: " + id);
                    return cachedUser;
                }

                // 👑 GLOBAL WINNER! Only 1 thread across entire cluster hits DB
                System.out.println("👑 REDISSON GLOBAL WINNER! Hitting PostgreSQL for ID: " + id);
                User user = userRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                // Store in Redis with 10-minute TTL
                cache.put(cacheKey, user, 10, TimeUnit.MINUTES);
                System.out.println("💾 Stored user " + id + " in Redis cache (TTL: 10 minutes)");
                return user;

            } finally {
                if (distributedLock.isHeldByCurrentThread()) {
                    distributedLock.unlock();
                }
            }
        } finally {
            localLock.unlock();
        }
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setFirstName(userDetails.getFirstName());
        user.setLastName(userDetails.getLastName());
        user.setEmail(userDetails.getEmail());
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
        // Invalidate cache when user is deleted
        RMapCache<String, User> cache = redissonClient.getMapCache("users");
        cache.remove("users::" + id);
    }
}
