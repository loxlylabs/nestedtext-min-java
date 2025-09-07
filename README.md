# nestedtext-min-java

nestedtext-min-java is a Java library used for converting to and from
[Minimal NestedText](https://nestedtext.org/en/latest/minimal-nestedtext.html).

### Minimal NestedText

The NestedText format is inspired by YAML, but is much simpler as there are no unexpected
conversions of data. In NestedText, there's only maps, lists, and strings.

Here's a basic example where we have a list of two users.
```
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
bankVaultAccess:
  condition: user.level == "FULL_ACCESS" ? "AUTHORIZED" : "DENIED"
```

### Usage

Loading a NestedText file:
```java
import org.loxlylabs.nestedtext.NestedText;

String dataStr = """
        bankVaultAccess:
          condition: user.level == "FULL_ACCESS" ? "AUTHORIZED" : "DENIED"
        """;

Map data = (Map)new NestedText().load(dataStr);
Map vaultConfig = (Map)data.get("bankVaultAccess");

// Prints: user.level == "FULL_ACCESS" ? "AUTHORIZED" : "DENIED"
System.out.println(vaultConfig.get("condition"));
```

Outputting to a NestedText string:
```java
var data = Map.of("name", "Alice");
String nestedText = new NestedText().dump(data);

// Prints: name: Alice
System.out.println(nestedText);
```

### Download

Maven:
```xml
<dependency>
  <groupId>org.loxlylabs</groupId>
  <artifactId>nestedtext-min-java</artifactId>
  <version>0.3.0</version>
</dependency>
```

### Requirements

nestedtext-min-java is built in modern Java and requires Java 21+
