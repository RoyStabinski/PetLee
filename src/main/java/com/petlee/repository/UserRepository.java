package com.petlee.repository;

import com.petlee.model.User;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

/**
 * Persistence operations for {@link User}: the lookups T-13 needs to register and authenticate
 * someone (specification §9.2).
 *
 * <h2>Case sensitivity — the asymmetry is deliberate</h2>
 * <ul>
 *   <li><strong>Username comparison is case-sensitive.</strong> A username is the exact identity
 *       the user chose, and {@code admin} and {@code Admin} are two different people.</li>
 *   <li><strong>Email comparison is case-insensitive</strong>, matched as
 *       {@code LOWER(u.email) = LOWER(:email)}. Mailbox names are case-insensitive in practice,
 *       so treating them otherwise would let one address register twice.</li>
 * </ul>
 * T-13 and T-37 both depend on this asymmetry; it is stated here so neither has to infer it.
 *
 * <p>The database agrees with both rules: {@code users.user_name} is {@code UNIQUE} and
 * {@code ux_users_email_lower} is a unique index on {@code LOWER(email)}, so
 * {@link #existsByEmail(String)} is exactly as strict as the constraint behind it. Neither check
 * is the only guarantee — two registrations racing past either one still lose at the database.
 *
 * <h2>Every query is a literal with named parameters</h2>
 * The four queries below are string literals declared at their call site, and every caller-supplied
 * value arrives through {@code setParameter}. Nothing is concatenated into JPQL anywhere in this
 * class — this is the layer where an injection would enter the system, so the rule is absolute.
 *
 * <h2>Not this class's job</h2>
 * No hashing, no password comparison, no validation, no DTO conversion. The repository stores
 * whatever string it is handed; T-10 hashes and T-13 decides.
 */
// @ApplicationScoped is repeated here rather than left to be inherited from
// AbstractRepository. The scope annotation IS @Inherited, so the subclass genuinely has the
// scope — but in an implicit bean archive (no beans.xml) the container only DISCOVERS classes
// carrying a bean-defining annotation of their own, and an inherited one does not count.
// Without this line Payara 6 refuses the deployment outright:
//   WELD-001408: Unsatisfied dependencies for type UserRepository with qualifiers @Default
// Every repository subclass needs it. See AbstractRepository's "Writing a subclass".
@ApplicationScoped
public class UserRepository extends AbstractRepository<User, Long> {

    public UserRepository() {
        super(User.class);
    }

    /**
     * Finds the user with exactly this username.
     *
     * <p>Case-sensitive. A missing user is an ordinary outcome, not an error: the result is
     * {@link Optional#empty()} and no {@code NoResultException} escapes. A {@code null} argument
     * yields an empty result without touching the database.
     *
     * @param username the exact username, may be {@code null}
     * @return the user, or empty when no one holds that username; never {@code null}
     */
    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return getEntityManager()
                .createQuery("SELECT u FROM User u WHERE u.userName = :username", User.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst();
    }

    /**
     * Finds the user registered with this email address, ignoring case.
     *
     * <p>A missing user gives {@link Optional#empty()}; a {@code null} argument does the same
     * without a query. At most one row can match, since {@code ux_users_email_lower} is unique;
     * the result is still read as the first of a stream rather than through
     * {@code getSingleResult()}, which turns "no row" into an exception instead of an empty
     * result.
     *
     * @param email the address, in any case, may be {@code null}
     * @return the user, or empty when the address is unknown; never {@code null}
     */
    public Optional<User> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return getEntityManager()
                .createQuery("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email)", User.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst();
    }

    /**
     * Reports whether the username is already taken, case-sensitively.
     *
     * <p>Counts rather than loading the entity: T-13 calls this on every registration to produce
     * the contract's {@code 409}, and it has no use for the user it would otherwise materialise.
     *
     * @param username the exact username, may be {@code null}
     * @return {@code true} when a user already holds it; {@code false} for a {@code null} argument
     */
    public boolean existsByUsername(String username) {
        if (username == null) {
            return false;
        }
        Long matches = getEntityManager()
                .createQuery("SELECT COUNT(u) FROM User u WHERE u.userName = :username", Long.class)
                .setParameter("username", username)
                .getSingleResult();
        return matches > 0;
    }

    /**
     * Reports whether the email address is already registered, ignoring case.
     *
     * <p>Counts rather than loading the entity, for the same reason as
     * {@link #existsByUsername(String)}. Stricter than the database constraint — see the class
     * Javadoc.
     *
     * @param email the address, in any case, may be {@code null}
     * @return {@code true} when the address is already registered; {@code false} for a
     *         {@code null} argument
     */
    public boolean existsByEmail(String email) {
        if (email == null) {
            return false;
        }
        Long matches = getEntityManager()
                .createQuery("SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email)", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return matches > 0;
    }
}
