package com.petlee.web.bean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registration form has to accept the contract's own example.
 *
 * <p>{@code api-contract.md} registers with {@code "phone": "050-1234567"} — eleven characters.
 * T-27 requirement 9 asks for {@code maxlength="10"} on that field, which would make the form
 * reject the very body the contract publishes; ADR-002 deviation #9 had already widened the column
 * and {@code UserService.PHONE_MAX} to 20 after that example failed the {@code INSERT}.
 *
 * <p>This is the guard against the two drifting apart again. It reads the page rather than a
 * constant, because the number that matters is the one in the markup: a browser stops typing at
 * {@code maxlength} and the user never finds out why. If somebody later applies requirement 9 as
 * written, the build fails here instead of the registration failing for a real person.
 *
 * <p>Reading a view from a test is unusual and deliberate. The value cannot be shared with the
 * server's copy: ADR-001 forbids {@code com.petlee.web} from importing {@code com.petlee.service},
 * so the page necessarily keeps its own number. What can be shared is the assertion.
 */
@DisplayName("the registration form and the contract's example")
class RegisterFormTest {

    /** Straight out of {@code api-contract.md}'s registration request body. */
    private static final String CONTRACT_PHONE = "050-1234567";

    /** ADR-002 #9: the width of {@code users.phone_number} and of {@code UserService.PHONE_MAX}. */
    private static final int PHONE_MAX = 20;

    private static final Path REGISTER_PAGE = Path.of("src", "main", "webapp", "register.xhtml");

    @Test
    @DisplayName("the phone field is long enough for the contract's own example")
    void phoneFieldAcceptsTheContractExample() throws IOException {
        int maxLength = maxLengthOf("phone");

        assertTrue(maxLength >= CONTRACT_PHONE.length(),
                "maxlength=" + maxLength + " would truncate the contract's example \""
                        + CONTRACT_PHONE + "\" (" + CONTRACT_PHONE.length() + " characters)");
    }

    @Test
    @DisplayName("and matches the width the server actually enforces")
    void phoneFieldMatchesTheServersLimit() throws IOException {
        assertEquals(PHONE_MAX, maxLengthOf("phone"),
                "the form's limit and UserService.PHONE_MAX must be the same number");
    }

    @Test
    @DisplayName("the example is shown to the user, not just tolerated")
    void theExampleIsOnThePage() throws IOException {
        String bundle = Files.readString(Path.of("src", "main", "resources", "messages.properties"),
                StandardCharsets.ISO_8859_1);

        assertTrue(bundle.contains("auth.phone.example=" + CONTRACT_PHONE),
                "the bundle should offer the contract's example as the field's placeholder");
        // p: is the passthrough namespace. h:inputText has no placeholder attribute of its own,
        // and a plain one is silently dropped - the field renders with no hint and nothing says so.
        assertTrue(page().contains("p:placeholder=\"#{msg['auth.phone.example']}\""),
                "the phone field should render that example as its placeholder");
    }

    /**
     * @param fieldId the input's Facelets id
     * @return the {@code maxlength} it declares
     */
    private static int maxLengthOf(String fieldId) throws IOException {
        Matcher input = Pattern
                .compile("<h:inputText\\s+id=\"" + fieldId + "\"(.*?)/?>", Pattern.DOTALL)
                .matcher(page());
        assertTrue(input.find(), "register.xhtml should declare an input with id \"" + fieldId + "\"");

        Matcher max = Pattern.compile("maxlength=\"(\\d+)\"").matcher(input.group(1));
        assertTrue(max.find(), "the \"" + fieldId + "\" field should declare a maxlength");
        return Integer.parseInt(max.group(1));
    }

    private static String page() throws IOException {
        return Files.readString(REGISTER_PAGE, StandardCharsets.UTF_8);
    }
}
