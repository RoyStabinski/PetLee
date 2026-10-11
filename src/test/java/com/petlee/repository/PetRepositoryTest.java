package com.petlee.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PetRepositoryTest {

    // In a Java text block each \\ is one backslash, so '%\\%%' below is the pattern %\%%.
    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', textBlock = """
            rex                 | %rex%
            Golden RETRIEVER    | %golden retriever%
            '%'                 | '%\\%%'
            '_'                 | '%\\_%'
            '\\'                | '%\\\\%'
            50%_off\\now        | %50\\%\\_off\\\\now%
            """)
    @DisplayName("Search text becomes a lower-case contains pattern with its wildcards escaped")
    void should_escape_wildcards_and_lower_case_when_building_pattern(String text, String expected) {
        assertEquals(expected, PetRepository.containsPattern(text));
    }
}
