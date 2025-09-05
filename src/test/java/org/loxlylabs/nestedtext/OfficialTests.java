package org.loxlylabs.nestedtext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class OfficialTests {

    public static class TestSuite {
        public Map<String, TestCase> load_tests;
    }

    public static class TestCase {
        public String load_in;
        public Object load_out;
        public TestError load_err;
        public String encoding;
        public Map<String, Integer> types;
    }

    public static class TestError {
        public String message;
        public String line;
        public Integer lineno;
        public Integer colno;

        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) return false;

            TestError testError = (TestError) object;
            return Objects.equals(message, testError.message)
                    && Objects.equals(line, testError.line)
                    && Objects.equals(lineno, testError.lineno)
                    && Objects.equals(colno, testError.colno);
        }

        @Override
        public int hashCode() {
            int result = Objects.hashCode(message);
            result = 31 * result + Objects.hashCode(line);
            result = 31 * result + Objects.hashCode(lineno);
            result = 31 * result + Objects.hashCode(colno);
            return result;
        }
    }

    public static final TestError NO_ERROR = new TestError();

    protected void assertJsonEquals(String expected, String actual) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode expectedNode = mapper.readTree(expected);
            JsonNode actualNode = mapper.readTree(actual);
            assertEquals(expectedNode, actualNode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @TestFactory
    Collection<DynamicTest> specTests() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream in = getClass().getResourceAsStream("/tests.json")) {
            TestSuite suite = mapper.readValue(in, TestSuite.class);

            return suite.load_tests.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals("asylum"))
                    .filter(entry -> !entry.getKey().equals("academic"))
                    .filter(entry -> !entry.getValue().types.containsKey("inline dict"))
                    .filter(entry -> !entry.getValue().types.containsKey("inline list"))
                    .filter(entry -> !entry.getValue().types.containsKey("key item"))
                    .map(entry ->

                    DynamicTest.dynamicTest(entry.getKey(), () -> {
                        TestCase tc = entry.getValue();

                        byte[] decodedBytes = Base64.getDecoder().decode(tc.load_in);

                        String decodedInput = switch(tc.encoding) {
                            case "utf-8" -> new String(decodedBytes, StandardCharsets.UTF_8);
                            case "utf-16" -> new String(decodedBytes, StandardCharsets.UTF_16);
                            case "latin1" -> new String(decodedBytes, StandardCharsets.ISO_8859_1);
                            case "bytes" -> new String(decodedBytes);
                            default -> throw new RuntimeException("Unsupported encoding " + tc.encoding);
                        };

                        if (tc.load_err != null && !Objects.equals(tc.load_err, NO_ERROR)) {
                            NestedTextException ex = assertThrows(NestedTextException.class, () -> {
                                if (tc.encoding.equals("bytes")) {
                                    new NestedText().load(decodedBytes);
                                } else {
                                    new NestedText().load(decodedInput);
                                }
                            });
                            if (tc.load_err.message != null) {
                                assertEquals(tc.load_err.message, ex.getMessage());
                            }
                            assertEquals(tc.load_err.lineno, ex.getLine());

                            // DEVIATION FROM OFFICIAL LIB
                            // ---------------------------
                            // 1. Official NestedText reports expected indentation
                            //    whereas we report actual indentation as the col.
                            // 2. amendment test reports column where encoding found
                            //    invalid character. In Java, this does not seem
                            //    simple to obtain.
                            if (!entry.getKey().equals("amendment")
                                    && !ex.getMessage().contains("invalid indentation")) {
                                assertEquals(tc.load_err.colno, ex.getColumn());
                            }
                        } else {
                            Object load = tc.encoding.equals("bytes")
                                    ? new NestedText().load(decodedBytes)
                                    : new NestedText().load(decodedInput);
                            String expected = new ObjectMapper().writeValueAsString(load);
                            String actual = new ObjectMapper().writeValueAsString(tc.load_out);
                            assertJsonEquals(expected, actual);
                        }
                    })
            ).toList();
        }
    }
}
