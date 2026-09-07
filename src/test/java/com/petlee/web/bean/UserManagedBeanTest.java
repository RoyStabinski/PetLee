package com.petlee.web.bean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of {@link UserManagedBean} that do not need a {@code FacesContext}.
 *
 * <p>Its three actions all call {@code ApiClient} and all report through {@code FacesMessage},
 * neither of which exists outside a request, so they are demonstrated against the running
 * application in T-26's acceptance criteria. What is left is the open-redirect guard and the
 * serialization contract — two things a passing screen would not reveal, and the first of which is
 * a security control.
 */
class UserManagedBeanTest {

    /**
     * The whole point of the guard. Each of these is a real open-redirect payload: an absolute URL,
     * a protocol-relative one that a browser resolves against the current scheme, a backslash
     * variant that some parsers normalise into one, and a scheme smuggled past a naive prefix test.
     */
    @ParameterizedTest
    @DisplayName("refuses to send the user anywhere but this application")
    @ValueSource(strings = {
            "https://example.invalid/",
            "http://example.invalid/profile.xhtml",
            "//example.invalid/profile.xhtml",
            "/\\example.invalid/profile.xhtml",
            "/\\/example.invalid",
            "javascript:alert(1)",
            "/profile.xhtml:evil",
            "profile.xhtml",
            "/profile",
            "/profile.jsp",
            ""
    })
    void rejectsAnythingThatIsNotALocalView(String hostile) {
        assertFalse(UserManagedBean.isLocalView(hostile), hostile);
    }

    @Test
    @DisplayName("refuses a missing destination")
    void rejectsNull() {
        assertFalse(UserManagedBean.isLocalView(null));
    }

    @ParameterizedTest
    @DisplayName("accepts the pages T-33 actually redirects from")
    @ValueSource(strings = {"/profile.xhtml", "/addPet.xhtml", "/editPet.xhtml", "/admin.xhtml"})
    void acceptsALocalView(String local) {
        assertTrue(UserManagedBean.isLocalView(local));
    }

    /**
     * Criterion 9. A {@code @SessionScoped} bean is passivated whenever the container moves or
     * persists a session; one that cannot be serialized fails at that moment and not before, which
     * is to say in production and not in testing.
     */
    @Test
    @DisplayName("criterion 9 — survives a passivation and activation cycle")
    void survivesPassivation() throws IOException, ClassNotFoundException {
        UserManagedBean bean = new UserManagedBean();
        bean.setUsername("donaldt");
        bean.setFullName("Donald Trump");
        bean.setEmail("donaldt@example.org");
        bean.setPassword("secret-while-the-request-lasts");

        UserManagedBean revived = roundTrip(bean);

        assertEquals("donaldt", revived.getUsername());
        assertEquals("Donald Trump", revived.getFullName());
        assertEquals("donaldt@example.org", revived.getEmail());
        assertFalse(revived.isLoggedIn());
        assertNull(revived.getDisplayName());
        assertNull(revived.getCurrentUserId());
    }

    /**
     * The password fields are {@code transient}, so a passivated session file never contains one
     * even if a bug left it set. Belt and braces over the {@code finally} that clears them.
     */
    @Test
    @DisplayName("a passivated session carries no password")
    void passwordsDoNotSurvivePassivation() throws IOException, ClassNotFoundException {
        UserManagedBean bean = new UserManagedBean();
        bean.setPassword("hunter2");
        bean.setConfirmPassword("hunter2");

        byte[] passivated = serialize(bean);
        assertFalse(new String(passivated, java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains("hunter2"), "the serialized form must not contain the password");

        UserManagedBean revived = deserialize(passivated);
        assertNull(revived.getPassword());
        assertNull(revived.getConfirmPassword());
    }

    @Test
    @DisplayName("signed out, every accessor the shell reads answers safely")
    void signedOutIsNotAnException() {
        UserManagedBean bean = new UserManagedBean();

        assertFalse(bean.isLoggedIn());
        assertFalse(bean.isAdmin());
        assertNull(bean.getDisplayName());
        assertNull(bean.getCurrentUserId());
        assertNull(bean.getCurrentUser());
    }

    private static UserManagedBean roundTrip(UserManagedBean bean)
            throws IOException, ClassNotFoundException {
        return deserialize(serialize(bean));
    }

    private static byte[] serialize(UserManagedBean bean) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(bean);
        }
        return bytes.toByteArray();
    }

    private static UserManagedBean deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (UserManagedBean) in.readObject();
        }
    }
}
