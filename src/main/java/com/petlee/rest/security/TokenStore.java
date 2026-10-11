package com.petlee.rest.security;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bearer tokens {@code POST /api/auth/login} hands out, each mapped to the same
 * {@link SessionUser} snapshot a session would hold. In memory only: a restart logs every
 * token holder out, exactly as it does every session holder.
 *
 * <p>A {@link ConcurrentHashMap} because this one application-scoped instance is shared by every
 * request thread at once — logins issue, API calls resolve and slide, logouts revoke — and a plain
 * {@code HashMap} can lose entries or corrupt itself under concurrent writes. Its per-key
 * {@code computeIfPresent} also makes "check the expiry, then slide it or drop it" one atomic
 * step, so a racing revoke cannot be undone by a lookup that read the entry just before.
 */
@ApplicationScoped
public class TokenStore {

    /** Matches {@code <session-timeout>} in {@code web.xml}, so both credentials age alike. */
    static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private static final int TOKEN_BYTES = 32;

    /** One generator for every token: seeding is the expensive part, and it is thread-safe. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    private final Clock clock;

    /** The store the container creates, timed by the system's monotonic clock. */
    public TokenStore() {
        this(new MonotonicClock());
    }

    /**
     * A store timed by the given clock, so tests can move time forward instead of waiting.
     *
     * @param clock the source of "now"; must not be null
     */
    TokenStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Issues a new token for an authenticated user. Also sweeps out tokens that expired without
     * ever being presented again, which a lookup-only cleanup would keep forever.
     *
     * @param user the authenticated user; must not be null
     * @return a new token: 32 random bytes, Base64 URL-safe without padding
     */
    public String issue(SessionUser user) {
        Objects.requireNonNull(user, "user");
        Instant now = clock.instant();
        entries.values().removeIf(entry -> entry.expiredAt(now));

        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = ENCODER.encodeToString(bytes);

        entries.put(token, new Entry(user, now));
        return token;
    }

    /**
     * Looks a token up and, if it is still live, restarts its idle timer. An expired token is
     * removed on the way.
     *
     * @param token the presented token, may be null
     * @return the token's user, or empty when the token is unknown, revoked or expired
     */
    public Optional<SessionUser> resolve(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Entry live = entries.computeIfPresent(token,
                (key, entry) -> entry.expiredAt(now) ? null : new Entry(entry.user(), now));
        return live == null ? Optional.empty() : Optional.of(live.user());
    }

    /**
     * Forgets a token. Revoking an unknown one is not an error.
     *
     * @param token the token, may be null
     */
    public void revoke(String token) {
        if (token != null) {
            entries.remove(token);
        }
    }

    /**
     * Forgets every token of one user except one, for a password change: whoever holds the old
     * password's tokens elsewhere is signed out, and the caller stays signed in.
     *
     * @param userId    the user whose tokens to revoke; null revokes nothing
     * @param keepToken the token to keep, or null to revoke them all
     */
    public void revokeAllExcept(Long userId, String keepToken) {
        if (userId == null) {
            return;
        }
        // remove(key), not entrySet().removeIf: that removes an entry only if its value is
        // unchanged, so a token a concurrent resolve had just slid would survive.
        entries.forEach((token, entry) -> {
            if (userId.equals(entry.user().userId()) && !token.equals(keepToken)) {
                entries.remove(token);
            }
        });
    }

    /** A user and when their token was last used. */
    private record Entry(SessionUser user, Instant lastUsed) {

        boolean expiredAt(Instant now) {
            return Duration.between(lastUsed, now).compareTo(IDLE_TIMEOUT) > 0;
        }
    }

    /**
     * {@link System#nanoTime()} as a {@link Clock}. Only differences between its instants mean
     * anything, which is all an idle timeout needs, and unlike the wall clock it never jumps when
     * the system time is adjusted.
     */
    private static final class MonotonicClock extends Clock {

        @Override
        public Instant instant() {
            return Instant.EPOCH.plusNanos(System.nanoTime());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("a monotonic clock has no zone");
        }
    }
}
