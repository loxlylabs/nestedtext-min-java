# nestedtext-min-java

nestedtext-min-java is a Java library used for converting to and from
[Minimal NestedText](https://nestedtext.org/en/latest/minimal-nestedtext.html).

## Minimal NestedText

The NestedText format is inspired by YAML, but is much simpler as there are no unexpected
conversions of data. In NestedText, there's only maps, lists, and strings.

Here's a basic example showing a team with two users:
```
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
```

Because there's no unexpected conversions, this is legal NestedText,
but not legal YAML:

```
role:
  name: Administrator
  condition: user.level == "FULL_ACCESS" ? "AUTHORIZED" : "DENIED"
```

## Usage

### Loading NestedText

You can easily load NestedText and bind it to a Java class.
```java
import org.loxlylabs.nestedtext.NestedText;

public record Team(String team, List<User> users) {}
public record User(String name, String address, String phone) {}

String data = """
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

Team team = new NestedText().load(data, Team.class);
```

Or you can load the NestedText into raw Maps, Lists, and Strings.
```java
String data = """
        role:
          name: Administrator
          condition: user.level == "FULL_ACCESS" ? "AUTHORIZED" : "DENIED"
        """;

Map dataMap = (Map)new NestedText().load(data);
Map roleConfig = (Map)dataMap.get("role");

// Prints: user.level == "FULL_ACCESS" ? "AUTHORIZED" : "DENIED"
System.out.println(roleConfig.get("condition"));
```

If you need to load NestedText without a wrapping Java record or POJO
you can use the `TypeReference` class.

```java
String data = """
        - 1
        - 2
        - 3
        """;
List<Integer> numbers = new NestedText().load(data, new TypeReference<>() {});
```

#### Custom Deserializers

If you're trying to deserialize a class without a default constructor,
you can register a custom deserializer:

```java
var nt = new NestedText();
nt.registerDeserializer(MyUser.class, (value, context) -> {
    if (!(value instanceof Map<?,?> m)) {
        // throw - NestedText type mismatch
    }
    String name = context.convert(m.get("name"), String.class);
    Integer age = context.convert(m.get("age"), Integer.class);
    return new MyUser(name, age);
});
```


### Dumping NestedText

Outputting to a NestedText string:
```java
var data = Map.of("name", "Alice");
String nestedText = new NestedText().dump(data);

// Prints: name: Alice
System.out.println(nestedText);
```

## Download

Maven:
```xml
<dependency>
  <groupId>org.loxlylabs</groupId>
  <artifactId>nestedtext-min-java</artifactId>
  <version>0.4.0</version>
</dependency>
```

### Requirements

nestedtext-min-java is built in modern Java and requires Java 21+
