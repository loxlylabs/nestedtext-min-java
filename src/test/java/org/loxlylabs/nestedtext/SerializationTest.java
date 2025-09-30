package org.loxlylabs.nestedtext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerializationTest {

    record Person(int age, String fullName) {}

    @Test
    void testRename() {
        Person p = new Person(20, "Alice Smith");
        NestedText nt = NestedText.builder()
                .forType(Person.class, type -> {
                    type.renameField("age").to("ageInYears");
                    type.renameField("fullName").to("name");
                }).build();
        String out = nt.dump(p);
        assertEquals("""
                    ageInYears: 20
                    name: Alice Smith""", out);
    }

    @Test
    void shouldNotIgnoreFieldWhenNull() {
        Person p = new Person(20, null);
        String out = NestedTexts.dump(p);
        assertEquals("""
                    age: 20
                    fullName:""", out);
    }

    @Test
    void shouldIgnoreFieldWhenNull() {
        Person p = new Person(20, null);
        NestedText nt = NestedText.builder()
                .forType(Person.class, type -> {
                    type.ignoreFieldWhenNull("fullName");
                }).build();
        String out = nt.dump(p);
        assertEquals("""
                    age: 20""", out);
    }

    @Test
    void shouldAlwaysIgnoreField() {
        Person p = new Person(20, "Alice Smith");
        NestedText nt = NestedText.builder()
                .forType(Person.class, type -> {
                    type.ignoreField("fullName");
                }).build();
        String out = nt.dump(p);
        assertEquals("""
                    age: 20""", out);
    }
}
