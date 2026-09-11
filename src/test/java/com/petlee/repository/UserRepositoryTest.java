package com.petlee.repository;

import com.petlee.model.User;
import com.petlee.test.DatabaseTest;
import com.petlee.test.TestData;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserRepository} against the real database — T-38 requirement 2.
 *
 * <p>The case rules are the point. Username is matched exactly and email is matched
 * case-insensitively, and neither is a preference: {@code ux_users_email_lower} is a unique index
 * on {@code LOWER(email)}, so a lookup that disagreed with it would find nothing and then fail to
 * insert.
 */
class UserRepositoryTest extends DatabaseTest {

    private UserRepository users;

    @BeforeEach
    void injectRepository() {
        users = inject(new UserRepository());
    }

    @Test
    @DisplayName("findByUsername finds a stored user, and reports absence as empty")
    void findsByUsername() {
        persist(TestData.aUser().username("donaldt").email("djt@usa.com").build());

        assertTrue(users.findByUsername("donaldt").isPresent());
        assertTrue(users.findByUsername("nobody").isEmpty());
        assertTrue(users.findByUsername(null).isEmpty());
    }

    @Test
    @DisplayName("username is matched exactly — 'DonaldT' is not 'donaldt'")
    void matchesUsernameCaseSensitively() {
        persist(TestData.aUser().username("donaldt").email("djt@usa.com").build());

        assertTrue(users.findByUsername("DonaldT").isEmpty());
        assertFalse(users.existsByUsername("DONALDT"));
    }

    @Test
    @DisplayName("email is matched ignoring case, exactly as ux_users_email_lower indexes it")
    void matchesEmailCaseInsensitively() {
        persist(TestData.aUser().username("donaldt").email("DJT@usa.com").build());

        assertTrue(users.findByEmail("djt@USA.com").isPresent());
        assertTrue(users.existsByEmail("DJT@usa.com"));
        assertTrue(users.findByEmail("someone@else.com").isEmpty());
    }

    @Test
    @DisplayName("save assigns the identity and stamps created_at")
    void savePopulatesGeneratedFields() {
        User user = TestData.aUser().build();
        assertNull(user.getUserId());

        inTransaction(manager -> users.save(user));

        assertNotNull(user.getUserId(), "the sequence assigned an id");
        assertNotNull(user.getCreatedAt(), "@PrePersist stamped created_at");
    }

    /**
     * The check the service performs is not the guarantee — the database is. T-13 calls
     * {@code existsByUsername} first, and two requests racing past it both arrive here.
     */
    @Test
    @DisplayName("a duplicate username is refused by the database, not merely by the service")
    void duplicateUsernameIsRefusedByTheConstraint() {
        persist(TestData.aUser().username("donaldt").email("first@usa.com").build());

        User clash = TestData.aUser().username("donaldt").email("second@usa.com").build();

        assertThrows(PersistenceException.class, () -> inTransaction(manager -> manager.persist(clash)));
    }

    @Test
    @DisplayName("a duplicate email differing only in case is refused by ux_users_email_lower")
    void duplicateEmailIgnoringCaseIsRefusedByTheIndex() {
        persist(TestData.aUser().username("first").email("djt@usa.com").build());

        User clash = TestData.aUser().username("second").email("DJT@USA.COM").build();

        assertThrows(PersistenceException.class, () -> inTransaction(manager -> manager.persist(clash)));
    }

    @Test
    @DisplayName("existsBy* answer for the empty database as well as the populated one")
    void existsAnswersBothWays() {
        assertFalse(users.existsByUsername("donaldt"));
        assertFalse(users.existsByEmail("djt@usa.com"));

        persist(TestData.aUser().username("donaldt").email("djt@usa.com").build());

        assertTrue(users.existsByUsername("donaldt"));
        assertTrue(users.existsByEmail("djt@usa.com"));
    }
}
