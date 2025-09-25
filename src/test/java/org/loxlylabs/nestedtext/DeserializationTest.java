package org.loxlylabs.nestedtext;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class DeserializationTest {

    @Nested
    public class RecordTests {
        record BasicRecord(int age, String name, double weight, boolean active) {}

        @Test
        void testBasicRecordFieldMappings() {
            String nt = """
                age: 5
                name: Alice
                weight: 1.5
                active: true
                """;
            var expected = new BasicRecord(5, "Alice", 1.5, true);
            assertEquals(expected, new NestedText().load(nt, BasicRecord.class));
        }

        record AddressRecord(String street, String city, String state, String zip) {}

        record ParentRecord(AddressRecord address, List<BasicRecord> children) {}

        @Test
        void testNestedRecordFieldMappings() {
            String nt = """
                address:
                  street: 123 Main Street
                  city: New York
                  state: NY
                  zip: 10001
                children:
                  -
                    age: 5
                    name: Alice
                    weight: 1.5
                    active: true
                  -
                    age: 12
                    name: Bob
                    weight: 2
                    active: false
                  
                """;
            var expected1 = new BasicRecord(5, "Alice", 1.5, true);
            var expected2 = new BasicRecord(12, "Bob", 2.0, false);
            var expectedAddress = new AddressRecord("123 Main Street", "New York", "NY", "10001");
            var expected = new ParentRecord(expectedAddress, List.of(expected1, expected2));
            assertEquals(expected, new NestedText().load(nt, ParentRecord.class));
        }

        @Test
        void testRecordWithEmptyList() {
            String nt = """
                address:
                  street: 123 Main Street
                  city: New York
                  state: NY
                  zip: 10001
                children:
                """;
            var expectedAddress = new AddressRecord("123 Main Street", "New York", "NY", "10001");
            var expected = new ParentRecord(expectedAddress, List.of());
            assertEquals(expected, new NestedText().load(nt, ParentRecord.class));
        }

        @Test
        void testRecordWithEmptyMap() {
            String nt = """
                address:
                children:
                  -
                    age: 5
                    name: Alice
                    weight: 1.5
                    active: true
                """;
            var expected = new ParentRecord(null, List.of(new BasicRecord(5, "Alice", 1.5, true)));
            assertEquals(expected, new NestedText().load(nt, ParentRecord.class));
        }

        public record User(String name, String address, String phone) {}

        public record Team(String team, List<User> users) {}

        @Test
        void testMissingFields() {
            String nt = """
                    team: Developers
                    users:
                      -
                        name: Alice
                        address:
                          > 123 Main St
                          > New York, NY 10001
                      -
                        name: Bob
                        phone: (555) 123-1234
                    """;
            Team actual = new NestedText().load(nt, Team.class);
            assertEquals(new Team("Developers", List.of(
                    new User("Alice", "123 Main St\nNew York, NY 10001", null),
                    new User("Bob", null, "(555) 123-1234")
            )), actual);
        }

        @Test
        void testMissingFieldsWithoutWrappingRecord() {
            String nt = """
                    -
                      name: Alice
                      address:
                        > 123 Main St
                        > New York, NY 10001
                    -
                      name: Bob
                      phone: (555) 123-1234
                    """;
            List<User> actual = new NestedText().load(nt, new TypeReference<>() {});
            assertEquals(List.of(
                    new User("Alice", "123 Main St\nNew York, NY 10001", null),
                    new User("Bob", null, "(555) 123-1234")
            ), actual);
        }
    }

    @Nested
    public class PojoTests {
        public static class BasicPojo {
            private int age;
            private String name;
            private double weight;
            private boolean active;

            public BasicPojo() {
            }

            public int getAge() {
                return age;
            }

            public void setAge(int age) {
                this.age = age;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public double getWeight() {
                return weight;
            }

            public void setWeight(double weight) {
                this.weight = weight;
            }

            public boolean isActive() {
                return active;
            }

            public void setActive(boolean active) {
                this.active = active;
            }

            @Override
            public boolean equals(Object object) {
                if (object == null || getClass() != object.getClass()) return false;

                BasicPojo basicPojo = (BasicPojo) object;
                return age == basicPojo.age && Double.compare(weight, basicPojo.weight) == 0 && active == basicPojo.active && Objects.equals(name, basicPojo.name);
            }

            @Override
            public int hashCode() {
                int result = age;
                result = 31 * result + Objects.hashCode(name);
                result = 31 * result + Double.hashCode(weight);
                result = 31 * result + Boolean.hashCode(active);
                return result;
            }
        }

        @Test
        void testBasicPojo() {
            NestedText nt = new NestedText();
            BasicPojo actual = nt.load("""
                    age: 5
                    name: Alice
                    weight: 1.5
                    active: true
                    """, BasicPojo.class);
            var expected = new BasicPojo();
            expected.setAge(5);
            expected.setName("Alice");
            expected.setWeight(1.5);
            expected.setActive(true);
            assertEquals(expected, actual);
        }

        static final class NoDefaultConstructor {
            private final int age;
            private final String name;
            private final double weight;
            private final boolean active;

            NoDefaultConstructor(int age, String name, double weight, boolean active) {
                this.age = age;
                this.name = name;
                this.weight = weight;
                this.active = active;
            }

            public int age() {
                return age;
            }

            public String name() {
                return name;
            }

            public double weight() {
                return weight;
            }

            public boolean active() {
                return active;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == this) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                var that = (NoDefaultConstructor) obj;
                return this.age == that.age &&
                        Objects.equals(this.name, that.name) &&
                        Double.doubleToLongBits(this.weight) == Double.doubleToLongBits(that.weight) &&
                        this.active == that.active;
            }

            @Override
            public int hashCode() {
                return Objects.hash(age, name, weight, active);
            }

            @Override
            public String toString() {
                return "BasicPojo[" +
                        "age=" + age + ", " +
                        "name=" + name + ", " +
                        "weight=" + weight + ", " +
                        "active=" + active + ']';
            }
        }

        @Test
        void testNoDefaultConstructor() {
            NestedText nt = new NestedText();
            nt.registerDeserializer(NoDefaultConstructor.class, (value, context) -> {
                if (!(value instanceof Map<?,?> m)) {
                    throw new RuntimeException("Not a Map: " + value.getClass().getSimpleName());
                }
                Integer age = context.convert(m.get("age"), Integer.class);
                String name = context.convert(m.get("name"), String.class);
                Double weight = context.convert(m.get("weight"), Double.class);
                Boolean active = context.convert(m.get("active"), Boolean.class);
                return new NoDefaultConstructor(age, name, weight, active);
            });
            NoDefaultConstructor actual = nt.load("""
                    age: 5
                    name: Alice
                    weight: 1.5
                    active: true
                    """, NoDefaultConstructor.class);
            var expected = new NoDefaultConstructor(5, "Alice", 1.5, true);
            assertEquals(expected, actual);
        }

        public static class PojoWithMapAndList {
            private String foo;
            private Map<String, List<String>> complexMap;
            private List<Map<String,String>> listOfMaps;

            public PojoWithMapAndList() {
            }

            public String getFoo() {
                return foo;
            }

            public void setFoo(String foo) {
                this.foo = foo;
            }

            public Map<String, List<String>> getComplexMap() {
                return complexMap;
            }

            public void setComplexMap(Map<String, List<String>> complexMap) {
                this.complexMap = complexMap;
            }

            public List<Map<String, String>> getListOfMaps() {
                return listOfMaps;
            }

            public void setListOfMaps(List<Map<String, String>> listOfMaps) {
                this.listOfMaps = listOfMaps;
            }

            @Override
            public boolean equals(Object object) {
                if (object == null || getClass() != object.getClass()) return false;

                PojoWithMapAndList that = (PojoWithMapAndList) object;
                return Objects.equals(foo, that.foo) && Objects.equals(complexMap, that.complexMap) && Objects.equals(listOfMaps, that.listOfMaps);
            }

            @Override
            public int hashCode() {
                int result = Objects.hashCode(foo);
                result = 31 * result + Objects.hashCode(complexMap);
                result = 31 * result + Objects.hashCode(listOfMaps);
                return result;
            }
        }

        @Test
        void testPojoWithMapAndListFields() {
            String content = """
                foo:
                  > 23 Main St
                  > New York, NY 10001
                complexMap:
                  a:
                    - a1
                    - a2
                    - a3
                  b:
                    - b1
                    - b2
                listOfMaps:
                  -
                    name: bob
                  -
                    name: alice
                """;
            PojoWithMapAndList obj = new NestedText().load(content, PojoWithMapAndList.class);
            assertEquals("23 Main St\nNew York, NY 10001", obj.getFoo());
        }
    }

    @Nested
    class RootTypes {

        @Test
        void testListRoot() {
            TypeReference<List<Integer>> typeRef = new TypeReference<>() {};
            List<Integer> numbers = new NestedText().load("""
                    - 5
                    - 4
                    - 1
                    - 9
                    """, typeRef);
            assertEquals(List.of(5,4,1,9), numbers);
        }

        @Test
        void testMapRoot() {
            Map<Integer, Double> numberMap = new NestedText().load("""
                    5: 3.14
                    4: 5
                    1: 8.2
                    9: 2.1
                    """, new TypeReference<>() {});
            assertEquals(Map.of(5, 3.14,
                    4, 5.0,
                    1, 8.2,
                    9, 2.1), numberMap);
        }

        @Test
        void testStringRoot() {
            String address = new NestedText().load("""
                    > 123 Main Street
                    > New York, NY 10001
                    """, String.class);
            assertEquals("123 Main Street\nNew York, NY 10001", address);
        }
    }
}