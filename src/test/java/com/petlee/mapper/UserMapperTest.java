package com.petlee.mapper;

import com.petlee.dto.UserDTO;
import com.petlee.model.User;
import com.petlee.test.TestData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UserMapper}, and the shape of the one DTO that is next to a password.
 *
 * <p>{@code api-contract.md}'s registration response is <em>"UserDTO — never includes password"</em>
 * and lists exactly six keys. Both halves are asserted structurally rather than by example, so a
 * field added carelessly later fails here rather than in somebody's browser.
 */
class UserMapperTest {

    /** The contract's own response body, key for key. */
    private static final List<String> CONTRACT_KEYS =
            List.of("email", "fullName", "id", "phone", "role", "username");

    @Test
    @DisplayName("contract: a UserDTO has no field that could hold password material")
    void userDto_hasNoPasswordField() {
        List<String> suspicious = Arrays.stream(UserDTO.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(Field::getName)
                .filter(name -> name.toLowerCase().contains("password")
                        || name.toLowerCase().contains("hash")
                        || name.toLowerCase().contains("secret"))
                .toList();

        assertTrue(suspicious.isEmpty(), "UserDTO must carry no credential material: " + suspicious);
    }

    /**
     * JSON-B derives its keys from the bean's getters, so the readable properties are the JSON
     * document. Asserting them needs no JSON-B implementation on the test classpath — and catches
     * an extra key, which is the failure that matters: a field nobody meant to publish.
     */
    @Test
    @DisplayName("contract: the serialised UserDTO has exactly the six documented keys")
    void userDto_serialisesToExactlyTheContractKeys() throws java.beans.IntrospectionException {
        List<String> keys = Arrays.stream(Introspector.getBeanInfo(UserDTO.class, Object.class)
                        .getPropertyDescriptors())
                .filter(property -> property.getReadMethod() != null)
                .map(PropertyDescriptor::getName)
                .sorted()
                .toList();

        assertEquals(CONTRACT_KEYS, keys);
    }

    @Test
    @DisplayName("every contract field is copied across, and the role is the entity's")
    void toDto_copiesTheContractFields() {
        User user = TestData.aUser()
                .username("donaldt")
                .fullName("Donald Trump")
                .email("djt@usa.com")
                .phone("050-1234567")
                .region("Washington DC")
                .build();
        user.setUserId(1L);

        UserDTO dto = UserMapper.toDto(user);

        assertEquals(1L, dto.getId());
        assertEquals("donaldt", dto.getUsername());
        assertEquals("Donald Trump", dto.getFullName());
        assertEquals("djt@usa.com", dto.getEmail());
        assertEquals("050-1234567", dto.getPhone());
        assertEquals("USER", dto.getRole());
    }

    /**
     * ADR-002 #1: {@code region} was added to the entity because the contract's request body sends
     * it, and deliberately left out of the response because the contract's response body does not.
     */
    @Test
    @DisplayName("ADR-002 #1: region is accepted on registration and never returned")
    void toDto_doesNotPublishRegion() {
        assertTrue(Arrays.stream(UserDTO.class.getDeclaredFields())
                .noneMatch(field -> "region".equals(field.getName())));
    }

    @Test
    @DisplayName("an administrator maps to role ADMIN, which is what T-18 reads")
    void toDto_carriesTheAdminRole() {
        User admin = TestData.anAdmin().build();
        admin.setUserId(2L);

        assertEquals("ADMIN", UserMapper.toDto(admin).getRole());
    }

    @Test
    @DisplayName("a null user maps to null")
    void mapsNullToNull() {
        assertNull(UserMapper.toDto(null));
    }
}
