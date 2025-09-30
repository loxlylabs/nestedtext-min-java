package org.loxlylabs.nestedtext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NestedTextTest {

    private NestedText nt;

    @BeforeEach
    void setUp() {
        nt = NestedText.builder().build();
    }

    @Nested
    class LoadTests {

        @Test
        void shouldLoadSimpleDictionary() {
            String input = """
                    name: John Doe
                    age: 42
                    """;
            Object result = nt.from(input).asObject();
            assertInstanceOf(Map.class, result);
            Map<?, ?> map = (Map<?, ?>) result;
            assertEquals("John Doe", map.get("name"));
            assertEquals("42", map.get("age"));
        }

        @Test
        void shouldLoadSimpleList() {
            String input = """
                    - apple
                    - banana
                    - orange
                    """;
            Object result = nt.from(input).asObject();
            assertInstanceOf(List.class, result);
            assertEquals(List.of("apple", "banana", "orange"), result);
        }

        @Test
        void shouldLoadComplexNestedStructure() {
            String input = """
                    # Contact information for our officers
                    president:
                        name: Katheryn McDaniel
                        address:
                            > 138 Almond Street
                            > Topeka, Kansas 20697
                        phone:
                            - 1-470-555-0398
                            - 1-470-555-7570
                    vice-president:
                        name: Margaret Hodge
                    """;
            Object result = nt.from(input).asObject();
            assertInstanceOf(Map.class, result);
            Map<String, Object> map = (Map<String, Object>) result;

            Map<String, Object> president = (Map<String, Object>) map.get("president");
            assertEquals("Katheryn McDaniel", president.get("name"));
            assertEquals("138 Almond Street\nTopeka, Kansas 20697", president.get("address"));
            assertEquals(List.of("1-470-555-0398", "1-470-555-7570"), president.get("phone"));

            Map<String, Object> vicePresident = (Map<String, Object>) map.get("vice-president");
            assertEquals("Margaret Hodge", vicePresident.get("name"));
        }

        @Test
        void shouldHandleEmptyValues() {
            String input = """
                    key with empty value:
                    list with empty item:
                        -
                        - item2
                    """;
            Map<String, Object> result = (Map<String, Object>) nt.from(input).asObject();
            assertEquals("", result.get("key with empty value"));
            List<String> list = (List<String>) result.get("list with empty item");
            assertEquals(List.of("", "item2"), list);
        }

        @Test
        void shouldLoadTopLevelMultilineString() {
            String input = """
                    > A man, a plan, a canal.
                    > Panama.
                    """;
            Object result = nt.from(input).asObject();
            assertEquals("A man, a plan, a canal.\nPanama.", result);
        }

        @Test
        void shouldReturnNullForEmptyInput() {
            assertNull(nt.from("").asObject());
            assertNull(nt.from("   ").asObject());
            String commentOnly = """
                    # This is a comment
                       # This is another indented comment
                    """;
            assertNull(nt.from(commentOnly).asObject());
        }

        @Test
        void shouldTrimWhitespaceFromKeys() {
            String input = "key with spaces   : value";
            Map<String, Object> result = (Map<String, Object>) nt.from(input).asObject();
            assertTrue(result.containsKey("key with spaces"));
            assertEquals("value", result.get("key with spaces"));
        }

        @Test
        void shouldTreatInlineSyntaxAsStrings() {
            String input = """
                    inline_dict: {key: value}
                    inline_list: [item1, item2]
                    """;
            Map<String, Object> result = (Map<String, Object>) nt.from(input).asObject();
            assertEquals("{key: value}", result.get("inline_dict"));
            assertEquals("[item1, item2]", result.get("inline_list"));
        }

        @Test
        void shouldIgnoreLastNewlineInStrings() {
            // > a⏎
            // > b⏎
            // > c⏎ <-- ignore
            var input1 = "> a\n> b\n> c";
            var input2 = "> a\n> b\n> c\n";
            assertEquals("a\nb\nc", nt.from(input1).asObject());
            assertEquals("a\nb\nc", nt.from(input2).asObject());
        }
    }

    @Nested
    class DumpTests {

        record Person(String name, int age, List<String> children, boolean active) {}

        class Company {
            public String companyName;
            private int yearFounded;

            public Company() {
            }

            public int getYearFounded() {
                return yearFounded;
            }

            public void setYearFounded(int yearFounded) {
                this.yearFounded = yearFounded;
            }
        }

        enum Status { PENDING, COMPLETE, FAILED }

        @Test
        void shouldDumpMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "John Doe");
            data.put("planet", "Proxima Centauri b");

            String expected = """
                    name: John Doe
                    planet: Proxima Centauri b""";
            String result = nt.dump(data);
            assertEquals(expected, result);
        }

        @Test
        void shouldDumpList() {
            List<String> data = List.of("one", "two", "three");
            String expected = """
                    - one
                    - two
                    - three""";
            String result = nt.dump(data);
            assertEquals(expected, result);
        }

        @Test
        void shouldDumpString() {
            String data = """
                    one
                    two
                    three""";
            String expected = """
                    > one
                    > two
                    > three""";
            String result = nt.dump(data);
            assertEquals(expected, result);
        }

        @Test
        void shouldHandleNewlineProperly() {
            var input = "a\nb\nc";
            String actual = nt.dump(input);
            var expected = "> a\n> b\n> c";
            assertEquals(expected, actual);
        }

        @Test
        void shouldDumpEmptyString() {
            String data = "\n";
            String expected = ">\n>";
            String actual = nt.dump(data);
            assertEquals(expected, actual);
        }

        @Test
        void shouldDumpComplexStructure() {
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("street", "123 Main St");
            address.put("city", "Anytown");

            Map<String, Object> person = new LinkedHashMap<>();
            person.put("name", "Jane Doe");
            person.put("address", address);
            person.put("notes", "First line.\nSecond line.");

            String expected = """
                    name: Jane Doe
                    address:
                      street: 123 Main St
                      city: Anytown
                    notes:
                      > First line.
                      > Second line.""";

            String result = nt
                    .dump(person, new DumpOptions(System.lineSeparator(), 2));
            assertEquals(expected, result);
        }

        @Test
        void shouldDumpRecord() {
            Person person = new Person("Arthur Dent",
                    42,
                    List.of("Random", "Fenchurch"),
                    true);
            String expected = """
                    name: Arthur Dent
                    age: 42
                    children:
                        - Random
                        - Fenchurch
                    active: true"""
                    .replace("\n", System.lineSeparator());

            String result = nt.dump(person);
            assertEquals(expected, result);
        }

        @Test
        void shouldDumpClass() {
            Company company = new Company();
            company.companyName = "Acme";
            company.setYearFounded(2025);

            String expected = """
                    companyName: Acme
                    yearFounded: 2025""";

            String result = nt.dump(company);
            assertEquals(expected.trim(), result.trim());
        }

        @Test
        void shouldDumpVariousScalarAndArrayTypes() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("active", true);
            data.put("status", Status.COMPLETE);
            data.put("initial", 'A');
            data.put("pi", 3.14159);
            data.put("object_array", new String[]{"a", "b", "c"});
            data.put("primitive_array", new int[]{1, 2, 3});

            String expected = """
                    active: true
                    status: COMPLETE
                    initial: A
                    pi: 3.14159
                    object_array:
                        - a
                        - b
                        - c
                    primitive_array:
                        - 1
                        - 2
                        - 3""";

            String result = nt.dump(data);
            assertEquals(expected, result);
        }
    }

    @Nested
    class ErrorTests {

        @Test
        void dictKeysCannotStartWithBracket() {
            String input = """
                    [key: value
                    """;
            NestedTextException ex = assertThrows(NestedTextException.class, () -> nt.from(input).asObject());
            assertEquals("key may not start with '['.", ex.getMessage());
            assertEquals(0, ex.getLine());
        }

        @Test
        void dictKeysCannotStartWithBrace() {
            String input = """
                    {key: value
                    """;
            NestedTextException ex = assertThrows(NestedTextException.class, () -> nt.from(input).asObject());
            assertEquals("key may not start with '{'.", ex.getMessage());
            assertEquals(0, ex.getLine());
        }
        
        @Test
        void shouldThrowOnTabIndentation() {
            String input = "key:\n\t- list item";
            NestedTextException ex = assertThrows(NestedTextException.class, () -> nt.from(input).asObject());
            assertEquals("invalid character in indentation: '\\t'.", ex.getMessage());
            assertEquals(1, ex.getLine());
        }

        @Test
        void shouldThrowOnMismatchedDedent() {
            String input = """
                    key1:
                        key2: value
                      key3: value
                    """;
            NestedTextException ex = assertThrows(NestedTextException.class, () -> nt.from(input).asObject());
            assertEquals("invalid indentation, partial dedent.", ex.getMessage());
            assertEquals(2, ex.getLine());
        }

        @Test
        void shouldThrowOnTopLevelIndent() {
            String input = "  key: value";
            NestedTextException ex = assertThrows(NestedTextException.class, () -> nt.from(input).asObject());
            assertEquals("top-level content must start in column 1.", ex.getMessage());
        }

        @Test
        void shouldThrowOnKeyWithoutColon() {
            // The parser looks for ": " or ":\n" to delimit a key. A key on a line
            // by itself with no value is valid ("key:"), but a key with text
            // following it and no colon is not.
            String input = "a key but no colon";
            NestedTextException ex = assertThrows(NestedTextException.class, () -> nt.from(input).asObject());
            assertEquals("unrecognized line.", ex.getMessage());
        }
    }
}