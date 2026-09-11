package com.petlee.service;

import com.petlee.test.Fakes;
import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.exception.ConflictException;
import com.petlee.exception.UnauthorizedException;
import com.petlee.exception.ValidationException;
import com.petlee.model.User;
import com.petlee.utilities.PasswordHasher;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** T-13's seven acceptance criteria. */
class UserServiceTest {

    /** The exact body from api-contract.md's POST /api/users/register example. */
    private static final String CONTRACT_USERNAME = "donaldt";
    private static final String CONTRACT_PASSWORD = "12345678";
    private static final String CONTRACT_EMAIL = "djt@usa.com";
    private static final String CONTRACT_PHONE = "050-1234567";

    private Fakes.Users users;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = new Fakes.Users();
        service = new UserService(users);
    }

    private static RegisterForm contractExample() {
        RegisterForm form = new RegisterForm();
        form.setUsername(CONTRACT_USERNAME);
        form.setPassword(CONTRACT_PASSWORD);
        form.setFullName("Donald Trump");
        form.setEmail(CONTRACT_EMAIL);
        form.setPhone(CONTRACT_PHONE);
        form.setRegion("Washington DC");
        return form;
    }

    @Test
    @DisplayName("criterion 1: the contract's own example body registers, as USER, with no password")
    void registersTheContractExample() {
        UserDTO dto = service.register(contractExample());

        assertEquals(CONTRACT_USERNAME, dto.getUsername());
        assertEquals("Donald Trump", dto.getFullName());
        assertEquals(CONTRACT_EMAIL, dto.getEmail());
        assertEquals(CONTRACT_PHONE, dto.getPhone());
        assertEquals("USER", dto.getRole());

        // UserDTO cannot carry a password: there is nowhere to put one.
        assertFalse(Arrays.stream(UserDTO.class.getDeclaredFields())
                        .map(Field::getName)
                        .anyMatch(n -> n.toLowerCase().contains("password")),
                "UserDTO must have no password field");
    }

    @Test
    @DisplayName("criterion 2: a duplicate username is a 409")
    void rejectsADuplicateUsername() {
        service.register(contractExample());

        RegisterForm again = contractExample();
        again.setEmail("someone.else@usa.com");

        ConflictException e = assertThrows(ConflictException.class, () -> service.register(again));
        assertEquals("USERNAME_TAKEN", e.getCode());
    }

    @Test
    @DisplayName("criterion 2: a duplicate email differing only in case is a 409")
    void rejectsADuplicateEmailIgnoringCase() {
        service.register(contractExample());

        RegisterForm again = contractExample();
        again.setUsername("someoneelse");
        again.setEmail(CONTRACT_EMAIL.toUpperCase());

        ConflictException e = assertThrows(ConflictException.class, () -> service.register(again));
        assertEquals("EMAIL_TAKEN", e.getCode());
    }

    @Test
    @DisplayName("requirement 4: a constraint violation losing the race is the same 409")
    void translatesAConstraintViolationToAConflict() {
        users.failNextSaveWith = new PersistenceException("duplicate key value violates ux_users_email_lower");

        ConflictException e = assertThrows(ConflictException.class, () -> service.register(contractExample()));
        assertEquals("USER_EXISTS", e.getCode());
    }

    @Test
    @DisplayName("criterion 3: a 7-character password is a 400 naming 'password'")
    void rejectsAShortPassword() {
        RegisterForm form = contractExample();
        form.setPassword("1234567");

        ValidationException e = assertThrows(ValidationException.class, () -> service.register(form));
        assertEquals("password", e.getField());
        assertTrue(users.saved.isEmpty(), "nothing may be stored when validation fails");
    }

    @Test
    @DisplayName("criterion 4: a 21-character phone is a 400, not a database error")
    void rejectsAnOverlongPhone() {
        // 20, not 10: phone_number was widened to VARCHAR(20) by ADR-002 #9, because the
        // contract's own example sends an 11-character number. 20 is accepted, 21 is not.
        RegisterForm ok = contractExample();
        ok.setPhone("+972-50-123456789012".substring(0, 20));
        assertEquals(20, service.register(ok).getPhone().length());

        RegisterForm tooLong = contractExample();
        tooLong.setUsername("another");
        tooLong.setEmail("another@usa.com");
        tooLong.setPhone("012345678901234567890");

        ValidationException e = assertThrows(ValidationException.class, () -> service.register(tooLong));
        assertEquals("phone", e.getField());
    }

    @Test
    @DisplayName("criterion 5: what is stored is a PBKDF2 digest, never the plaintext")
    void storesADigestNotThePlaintext() {
        service.register(contractExample());

        String stored = users.last().getPassword();
        assertNotEquals(CONTRACT_PASSWORD, stored);
        assertTrue(stored.startsWith("pbkdf2_sha256$"), "stored value was: " + stored);
        assertTrue(PasswordHasher.verify(CONTRACT_PASSWORD, stored));
    }

    @Test
    @DisplayName("criterion 6: correct credentials authenticate")
    void authenticatesCorrectCredentials() {
        service.register(contractExample());

        UserDTO dto = service.authenticate(CONTRACT_USERNAME, CONTRACT_PASSWORD);

        assertEquals(CONTRACT_USERNAME, dto.getUsername());
        assertEquals("USER", dto.getRole());
    }

    @Test
    @DisplayName("criterion 6: a wrong password and an unknown username fail identically")
    void doesNotRevealWhetherAUsernameExists() {
        service.register(contractExample());

        UnauthorizedException wrongPassword = assertThrows(UnauthorizedException.class,
                () -> service.authenticate(CONTRACT_USERNAME, "not-the-password"));
        UnauthorizedException noSuchUser = assertThrows(UnauthorizedException.class,
                () -> service.authenticate("nobody", CONTRACT_PASSWORD));

        assertEquals(wrongPassword.getMessage(), noSuchUser.getMessage());
        assertEquals(wrongPassword.getCode(), noSuchUser.getCode());
        assertFalse(wrongPassword.getMessage().contains(CONTRACT_USERNAME),
                "the message must not echo the submitted username back");
    }

    @Test
    @DisplayName("criterion 7: nothing in the request body can produce an ADMIN")
    void cannotSelfElevateToAdmin() {
        // RegisterForm has no role property, so an incoming "role":"ADMIN" has nowhere to bind.
        assertFalse(Arrays.stream(RegisterForm.class.getDeclaredFields())
                        .map(Field::getName)
                        .anyMatch(n -> n.equalsIgnoreCase("role")),
                "RegisterForm must have no role field");
        assertFalse(Arrays.stream(RegisterForm.class.getMethods())
                        .map(Method::getName)
                        .anyMatch(n -> n.equalsIgnoreCase("setRole")),
                "RegisterForm must have no role setter");

        service.register(contractExample());
        assertEquals(User.Role.USER, users.last().getRole());
    }

    @Test
    @DisplayName("requirement 3: findById is how T-18 rehydrates a session user")
    void findsByIdOrReportsAbsence() {
        Long id = service.register(contractExample()).getId();

        assertEquals(CONTRACT_USERNAME, service.findById(id).orElseThrow().getUsername());
        assertTrue(service.findById(9999L).isEmpty());
        assertTrue(service.findById(null).isEmpty());
    }

    // -------------------------------------------------------- T-13 requirement 1, field by field

    /**
     * T-13 requirement 1 lists six rules and the two above cover two of them. These are the rest,
     * one test per field, so a failure names the rule that broke rather than "registration".
     */
    @Test
    @DisplayName("T-13 req 1: a username outside 3-20 characters is a 400 naming 'username'")
    void register_whenUsernameIsTooShortOrTooLong_throwsValidation() {
        assertEquals("username", fieldRejecting(form -> form.setUsername("ab")));
        assertEquals("username", fieldRejecting(form -> form.setUsername("a".repeat(21))));
    }

    @Test
    @DisplayName("T-13 req 1: a username with anything but letters, digits and _ is a 400")
    void register_whenUsernameHasIllegalCharacters_throwsValidation() {
        assertEquals("username", fieldRejecting(form -> form.setUsername("donald trump")));
        assertEquals("username", fieldRejecting(form -> form.setUsername("donald-t")));
    }

    @Test
    @DisplayName("T-13 req 1: a blank or over-long full name is a 400 naming 'fullName'")
    void register_whenFullNameIsBlankOrTooLong_throwsValidation() {
        assertEquals("fullName", fieldRejecting(form -> form.setFullName("   ")));
        assertEquals("fullName", fieldRejecting(form -> form.setFullName("N".repeat(51))));
    }

    @Test
    @DisplayName("T-13 req 1: an address that is not one is a 400 naming 'email'")
    void register_whenEmailIsNotAnAddress_throwsValidation() {
        assertEquals("email", fieldRejecting(form -> form.setEmail("djt-at-usa.com")));
        assertEquals("email", fieldRejecting(form -> form.setEmail("   ")));
    }

    @Test
    @DisplayName("T-13 req 1: region is optional, and over 100 characters is a 400")
    void register_whenRegionIsTooLong_throwsValidation() {
        RegisterForm noRegion = contractExample();
        noRegion.setUsername("noregion");
        noRegion.setEmail("noregion@usa.com");
        noRegion.setRegion(null);
        assertNotNull(service.register(noRegion), "region is optional");

        assertEquals("region", fieldRejecting(form -> form.setRegion("R".repeat(101))));
    }

    /**
     * Applies one mutation to the contract's example body, registers it, and reports which field
     * the 400 named — so each rule above is one line and reads as the rule.
     *
     * @param mutation what to break
     * @return the field named by the {@link ValidationException}
     */
    private String fieldRejecting(java.util.function.Consumer<RegisterForm> mutation) {
        RegisterForm form = contractExample();
        mutation.accept(form);

        ValidationException e = assertThrows(ValidationException.class, () -> service.register(form));
        assertTrue(users.saved.stream().noneMatch(u -> form.getUsername() != null
                        && form.getUsername().equals(u.getUserName())),
                "nothing may be stored when validation fails");
        return e.getField();
    }
}
