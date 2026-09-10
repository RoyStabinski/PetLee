package com.petlee.test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The base class for every test that touches the real database.
 *
 * <h2>A real PostgreSQL, not an in-memory stand-in</h2>
 * ADR-003 removed H2, so these tests run against the same engine production runs on: the same
 * dialect, the same {@code ON DELETE RESTRICT}, the same partial unique index. That is the point —
 * the constraints T-38 checks are ones only a real database enforces.
 *
 * <h2>Connection details are never committed</h2>
 * They come from system properties, with localhost defaults:
 *
 * <pre>
 * -Dpetlee.test.db.url=jdbc:postgresql://localhost:5432/petlee_test
 * -Dpetlee.test.db.user=postgres
 * -Dpetlee.test.db.password=postgres
 * </pre>
 *
 * <h2>One factory, a fresh EntityManager per test</h2>
 * Building an {@link EntityManagerFactory} costs about a second, so there is one per JVM. Each test
 * method gets its own {@link EntityManager} and starts from an <strong>empty</strong> database:
 * {@link #clean()} runs before every test, not after, so a failed test leaves its rows behind to be
 * looked at while the next test still starts from a known state.
 *
 * <p>Nothing here is shared between test methods, and no test may depend on another's data or on
 * execution order.
 */
public abstract class DatabaseTest {

    /** The four tables, children first — the order a TRUNCATE has to respect. */
    private static final String TABLES = "pet_image, pet, users, category";

    private static EntityManagerFactory factory;

    /** The test's own EntityManager, open for the duration of one test method. */
    protected EntityManager em;

    @BeforeEach
    void openEntityManagerAndClean() {
        em = entityManagerFactory().createEntityManager();
        clean();
    }

    @AfterEach
    void closeEntityManager() {
        if (em != null && em.isOpen()) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
    }

    /**
     * Empties all four tables and resets their identity sequences.
     *
     * <p>{@code TRUNCATE ... CASCADE} rather than a series of deletes: it is one statement, it
     * cannot be defeated by the foreign keys, and {@code RESTART IDENTITY} means a test can assert
     * on generated ids without depending on how many tests ran before it.
     */
    protected void clean() {
        computeInTransaction(manager -> manager
                .createNativeQuery("TRUNCATE TABLE " + TABLES + " RESTART IDENTITY CASCADE")
                .executeUpdate());
    }

    /**
     * Runs the given work in a transaction on this test's {@link EntityManager}, committing on
     * success and rolling back on failure.
     *
     * @param work what to do with the manager
     */
    protected void inTransaction(java.util.function.Consumer<EntityManager> work) {
        computeInTransaction(manager -> {
            work.accept(manager);
            return null;
        });
    }

    /**
     * Runs the given work in a transaction and returns its result.
     *
     * <p>A separate name rather than an overload of {@link #inTransaction}: an expression lambda
     * such as {@code manager -> manager.createQuery(…).executeUpdate()} matches both a
     * {@code Consumer} and a {@code Function}, and the compiler refuses to choose.
     *
     * @param work what to do with the manager
     * @param <T>  what it produces
     * @return whatever the work returned
     */
    protected <T> T computeInTransaction(Function<EntityManager, T> work) {
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        try {
            T result = work.apply(em);
            tx.commit();
            return result;
        } catch (RuntimeException failure) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw failure;
        }
    }

    /**
     * A second, independent {@link EntityManager} with its own persistence context.
     *
     * <p>Two tests need one: the lazy-loading check, which has to read a graph after the manager
     * that loaded it is closed, and the concurrency check, which needs two managers holding the
     * same row. <strong>The caller closes it.</strong>
     *
     * @return a new manager on the same factory
     */
    protected EntityManager freshEntityManager() {
        return entityManagerFactory().createEntityManager();
    }

    /**
     * Persists entities in one transaction, as test setup.
     *
     * @param entities what to store, in an order the foreign keys accept
     */
    protected void persist(Object... entities) {
        inTransaction(manager -> {
            for (Object entity : entities) {
                manager.persist(entity);
            }
        });
    }

    /**
     * Rewrites a listing's {@code created_at}, so a test can decide the gallery's order instead of
     * hoping two inserts land in different milliseconds.
     *
     * <p>It has to be a native statement: {@code Pet.createdAt} is stamped by {@code @PrePersist}
     * and mapped {@code updatable = false} (ADR-002 #4), which is exactly the protection an edit
     * must not be able to defeat — and exactly what a test seeding an ordered gallery needs to
     * bypass.
     *
     * @param petId the listing
     * @param when  the timestamp it should carry
     */
    protected void backdate(Long petId, java.time.LocalDateTime when) {
        computeInTransaction(manager -> manager
                .createNativeQuery("UPDATE pet SET created_at = ?1 WHERE pet_id = ?2")
                .setParameter(1, when)
                .setParameter(2, petId)
                .executeUpdate());
        em.clear();
    }

    /**
     * The factory, built once per JVM and closed when it exits.
     *
     * <p>A failure here is almost always a missing database rather than a broken test, so the
     * message says how to create one instead of only reporting what the driver said.
     */
    private static synchronized EntityManagerFactory entityManagerFactory() {
        if (factory == null) {
            CountingDriver.register();

            Map<String, String> connection = new HashMap<>();
            // Through CountingDriver, which delegates to the PostgreSQL one and counts statements
            // on the way past. It records nothing until a test asks it to, so the only cost here is
            // one extra proxy per call.
            connection.put("jakarta.persistence.jdbc.driver", CountingDriver.class.getName());
            connection.put("jakarta.persistence.jdbc.url", counting(property("url",
                    "jdbc:postgresql://localhost:5432/petlee_test")));
            connection.put("jakarta.persistence.jdbc.user", property("user", "postgres"));
            connection.put("jakarta.persistence.jdbc.password", property("password", "postgres"));

            try {
                factory = Persistence.createEntityManagerFactory("petlee-test-pu", connection);
            } catch (RuntimeException noDatabase) {
                throw new IllegalStateException(
                        "Cannot reach the test database at " + connection.get("jakarta.persistence.jdbc.url")
                                + ". Create it with the commands in src/test/README.md, or point the"
                                + " suite elsewhere with -Dpetlee.test.db.url / .user / .password.",
                        noDatabase);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(factory::close));
        }
        return factory;
    }

    private static String property(String name, String fallback) {
        return System.getProperty("petlee.test.db." + name, fallback);
    }

    /** {@code jdbc:postgresql://…} → {@code jdbc:counting:postgresql://…}. */
    private static String counting(String url) {
        return url.startsWith(CountingDriver.PREFIX) ? url
                : CountingDriver.PREFIX + url.substring("jdbc:".length());
    }

    /**
     * Hands a repository this test's {@link EntityManager}.
     *
     * <p>In production the container injects it: the field is {@code private} and carries
     * {@code @PersistenceContext}, which is exactly right for a deployment and useless in a plain
     * JUnit test, where there is no container to do the injecting. Reflection is the honest way to
     * stand in for it — the alternative would be a setter that exists only for tests and that
     * production code could call by accident.
     *
     * @param repository a repository instance
     * @param <R>        its type
     * @return the same instance, now able to reach the database
     */
    protected <R> R inject(R repository) {
        try {
            java.lang.reflect.Field field =
                    com.petlee.repository.AbstractRepository.class.getDeclaredField("em");
            field.setAccessible(true);
            field.set(repository, em);
            return repository;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "AbstractRepository no longer has an 'em' field for the tests to fill", e);
        }
    }
}
