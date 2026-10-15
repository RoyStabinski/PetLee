package com.petlee.rest.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenStoreTest {

    private static final SessionUser ALICE = new SessionUser(1L, "alice", false);
    private static final SessionUser BOB = new SessionUser(2L, "bob", true);

    private final TokenStore store = new TokenStore();

    @Test
    @DisplayName("An issued token resolves to the user it was issued for")
    void should_resolve_same_user_when_token_was_issued() {
        String token = store.issue(ALICE);

        assertEquals(Optional.of(ALICE), store.resolve(token));
    }

    @Test
    @DisplayName("A revoked token no longer resolves")
    void should_resolve_empty_when_token_was_revoked() {
        String token = store.issue(ALICE);

        store.revoke(token);

        assertEquals(Optional.empty(), store.resolve(token));
    }

    @Test
    @DisplayName("A token that was never issued resolves to nothing")
    void should_resolve_empty_when_token_is_unknown() {
        assertEquals(Optional.empty(), store.resolve("never-issued"));
    }

    @Test
    @DisplayName("A token left unused for more than 30 minutes expires")
    void should_expire_token_when_idle_for_thirty_minutes() {
        MutableClock clock = new MutableClock();
        TokenStore timed = new TokenStore(clock);
        String token = timed.issue(ALICE);

        clock.advance(Duration.ofMinutes(30).plusSeconds(1));

        assertEquals(Optional.empty(), timed.resolve(token));
    }

    @Test
    @DisplayName("Using a token restarts its idle timer")
    void should_slide_expiry_when_token_is_used_before_timeout() {
        MutableClock clock = new MutableClock();
        TokenStore timed = new TokenStore(clock);
        String token = timed.issue(ALICE);

        clock.advance(Duration.ofMinutes(20));
        assertEquals(Optional.of(ALICE), timed.resolve(token));

        clock.advance(Duration.ofMinutes(20)); // minute 40: 40 since issue, only 20 since use
        assertEquals(Optional.of(ALICE), timed.resolve(token));
    }

    /*
     * Every thread issues, resolves and revokes on the one shared store at the same moment, which
     * is what request threads do to the application-scoped bean. With a plain HashMap, concurrent
     * puts can drop entries or corrupt a bucket during a resize. Passing here shows the
     * ConcurrentHashMap keeps every write: no token is lost, none comes back after its revoke,
     * and no thread sees an exception.
     */
    @Test
    @DisplayName("Concurrent issue, resolve and revoke lose no tokens and revive none")
    void should_keep_every_live_token_and_drop_every_revoked_one_when_used_concurrently()
            throws Exception {
        int threads = 32;
        int tokensPerThread = 200;
        CountDownLatch gate = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> kept = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> revoked = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                SessionUser user = new SessionUser((long) t, "user" + t, false);
                futures.add(pool.submit(() -> {
                    gate.await();
                    for (int i = 0; i < tokensPerThread; i++) {
                        String token = store.issue(user);
                        if (!store.resolve(token).equals(Optional.of(user))) {
                            throw new AssertionError("a just-issued token did not resolve");
                        }
                        if (i % 3 == 0) {
                            store.revoke(token);
                            revoked.add(token);
                        } else {
                            kept.add(token);
                        }
                    }
                    return null;
                }));
            }

            gate.countDown();

            // Future.get rethrows, wrapped, anything a thread threw, failing the test.
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "pool did not shut down");
        }

        assertEquals(threads * tokensPerThread, kept.size() + revoked.size());
        for (String token : kept) {
            assertTrue(store.resolve(token).isPresent(), "a kept token was lost");
        }
        for (String token : revoked) {
            assertTrue(store.resolve(token).isEmpty(), "a revoked token resolved");
        }
    }

    /** A clock that stands still until the test moves it. */
    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-01-01T12:00:00Z");

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
