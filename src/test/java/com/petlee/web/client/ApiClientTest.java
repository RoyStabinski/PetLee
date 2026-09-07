package com.petlee.web.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of {@link ApiClient} that can be judged without a server.
 *
 * <p>The HTTP behaviour itself — cookie forwarding, session rotation, status translation — needs a
 * deployed WAR and is demonstrated against one in T-24's acceptance criteria, then automated in
 * T-39. What is left is still worth pinning down here, because each piece has a failure mode a
 * passing deployment would hide: a URI built with a literal host works perfectly on a laptop, a
 * multipart body missing one CRLF is accepted by one server and rejected by the next, and an
 * unsanitised filename only matters once somebody puts a newline in it.
 */
class ApiClientTest {

    @Nested
    @DisplayName("the base URI is derived from the request, never configured")
    class BaseUri {

        @Test
        void usesScheme_host_port_andContextPath() {
            assertEquals("http://localhost:8080/pet-lee/api",
                    ApiClient.baseUri(FakeServletApi.request("http", "localhost", 8080, "/pet-lee"))
                            .toString());
        }

        /** T-24 criterion 6: a different port must work with no code change. */
        @Test
        void followsThePortItWasCalledOn() {
            assertEquals("http://localhost:9090/pet-lee/api",
                    ApiClient.baseUri(FakeServletApi.request("http", "localhost", 9090, "/pet-lee"))
                            .toString());
        }

        /** The same WAR under a different context path — the other half of criterion 6. */
        @Test
        void followsTheContextPathItWasDeployedUnder() {
            assertEquals("https://pets.example.org:8181/adopt/api",
                    ApiClient.baseUri(FakeServletApi.request("https", "pets.example.org", 8181, "/adopt"))
                            .toString());
        }

        /** A WAR deployed as ROOT reports an empty context path, and must not produce {@code //api}. */
        @Test
        void handlesTheRootContext() {
            assertEquals("http://localhost:8080/api",
                    ApiClient.baseUri(FakeServletApi.request("http", "localhost", 8080, ""))
                            .toString());
        }

        @Test
        void carriesNoHostOrPortLiteral() {
            assertEquals("http://elsewhere:1234/x/api",
                    ApiClient.baseUri(FakeServletApi.request("http", "elsewhere", 1234, "/x"))
                            .toString());
        }
    }

    @Nested
    @DisplayName("the session cookie's name comes from the deployment")
    class SessionCookieName {

        @Test
        void defaultsToJsessionidWhenTheDeploymentNeverRenamedIt() {
            assertEquals("JSESSIONID", ApiClient.sessionCookieName(FakeServletApi.context(null)));
        }

        @Test
        void honoursAConfiguredName() {
            assertEquals("PETLEESESSION",
                    ApiClient.sessionCookieName(FakeServletApi.context("PETLEESESSION")));
        }

        @Test
        void treatsABlankNameAsUnset() {
            assertEquals("JSESSIONID", ApiClient.sessionCookieName(FakeServletApi.context("   ")));
        }

        /** Called before the context is known; the default beats a NullPointerException. */
        @Test
        void toleratesNoContextAtAll() {
            assertEquals("JSESSIONID", ApiClient.sessionCookieName(null));
        }
    }

    @Nested
    @DisplayName("the multipart body is the one T-23 reads back")
    class Multipart {

        private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};

        private String encode(String filename, boolean isMain) {
            return new String(ApiClient.multipartBody("B0UND", JPEG, filename, isMain),
                    StandardCharsets.ISO_8859_1);
        }

        /** {@code PetImageResource} calls {@code getPart("file")}; the name has to be exactly that. */
        @Test
        void namesTheFilePartFile() {
            assertTrue(encode("rex.jpg", true)
                    .contains("Content-Disposition: form-data; name=\"file\"; filename=\"rex.jpg\""));
        }

        /** And {@code getParameter("isMain")}, which only sees a part carrying no filename. */
        @Test
        void sendsIsMainAsAFieldWithNoFilename() {
            String body = encode("rex.jpg", true);
            assertTrue(body.contains("Content-Disposition: form-data; name=\"isMain\"\r\n\r\ntrue"));
            assertFalse(body.contains("name=\"isMain\"; filename"));
        }

        @Test
        void sendsIsMainFalseWhenItIsFalse() {
            assertTrue(encode("rex.jpg", false).contains("name=\"isMain\"\r\n\r\nfalse"));
        }

        @Test
        void opensEveryPartWithTheBoundaryAndClosesWithTheTerminator() {
            String body = encode("rex.jpg", true);
            assertTrue(body.startsWith("--B0UND\r\n"));
            assertTrue(body.endsWith("--B0UND--\r\n"));
            assertEquals(2, body.split("--B0UND\r\n", -1).length - 1);
        }

        /** A blank CRLF separates a part's headers from its payload; without it the part is header. */
        @Test
        void separatesHeadersFromThePayloadWithABlankLine() {
            byte[] body = ApiClient.multipartBody("B0UND", JPEG, "rex.jpg", true);
            int payload = indexOf(body, JPEG);
            String beforePayload = new String(body, 0, payload, StandardCharsets.ISO_8859_1);
            assertTrue(beforePayload.endsWith("Content-Type: image/jpeg\r\n\r\n"), beforePayload);
        }

        /** A byte-for-byte copy: an image mangled in transit fails T-16's signature check. */
        @Test
        void carriesTheFileBytesUnaltered() {
            byte[] body = ApiClient.multipartBody("B0UND", JPEG, "rex.jpg", true);
            int start = indexOf(body, JPEG);
            assertTrue(start > 0, "the payload should appear in the body");
            for (int i = 0; i < JPEG.length; i++) {
                assertEquals(JPEG[i], body[start + i]);
            }
        }

        /** And a CRLF after it, so the boundary that follows starts on a line of its own. */
        @Test
        void separatesThePayloadFromTheNextBoundaryWithCrLf() {
            byte[] body = ApiClient.multipartBody("B0UND", JPEG, "rex.jpg", true);
            int after = indexOf(body, JPEG) + JPEG.length;
            String rest = new String(body, after, body.length - after, StandardCharsets.ISO_8859_1);
            assertTrue(rest.startsWith("\r\n--B0UND\r\n"), rest);
        }

        private static int indexOf(byte[] haystack, byte[] needle) {
            outer:
            for (int i = 0; i <= haystack.length - needle.length; i++) {
                for (int j = 0; j < needle.length; j++) {
                    if (haystack[i + j] != needle[j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }

    @Nested
    @DisplayName("a browser filename cannot forge a header")
    class HeaderSafety {

        /**
         * The filename is attacker-controlled text going into a structured header. A CR or an LF in
         * it would let the caller write a header — or a whole extra part — of their choosing.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "rex\r\nContent-Type: text/html",
                "rex\nX-Evil: yes.jpg",
                "rex\r.jpg",
                "re\"x.jpg",
                "../../etc/passwd",
                "..\\..\\windows\\win.ini"
        })
        void stripsEverythingThatCouldBreakOutOfThePartHeader(String hostile) {
            String safe = ApiClient.headerSafe(hostile);
            assertFalse(safe.contains("\r"), safe);
            assertFalse(safe.contains("\n"), safe);
            assertFalse(safe.contains("\""), safe);
            assertFalse(safe.contains("/"), safe);
            assertFalse(safe.contains("\\"), safe);
        }

        @Test
        void keepsAnOrdinaryName() {
            assertEquals("rex-2024.jpg", ApiClient.headerSafe("rex-2024.jpg"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void substitutesANameWhenThereIsNone(String empty) {
            assertEquals("upload", ApiClient.headerSafe(empty));
        }

        @Test
        void substitutesANameWhenThereIsNoFilenameAtAll() {
            assertEquals("upload", ApiClient.headerSafe(null));
        }

        @Test
        void substitutesANameWhenNothingSurvivesStripping() {
            assertEquals("upload", ApiClient.headerSafe("///"));
        }

        /** A five-hundred-character filename is not a filename; the tail keeps the extension. */
        @Test
        void boundsTheLength() {
            String safe = ApiClient.headerSafe("x".repeat(500) + ".jpg");
            assertEquals(100, safe.length());
            assertTrue(safe.endsWith(".jpg"));
        }
    }

    @Nested
    @DisplayName("the declared content type is a courtesy, taken from the extension")
    class DeclaredType {

        @ParameterizedTest
        @CsvSource({
                "rex.jpg,  image/jpeg",
                "rex.JPEG, image/jpeg",
                "rex.png,  image/png",
                "rex.GIF,  image/gif",
                "rex.webp, image/webp",
                "rex.txt,  application/octet-stream",
                "rex,      application/octet-stream"
        })
        void mapsTheExtension(String filename, String expected) {
            assertEquals(expected, ApiClient.contentTypeFor(filename));
        }
    }
}
