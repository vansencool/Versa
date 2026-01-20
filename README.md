<div align="center">

# **Versa**
### *High performance, feature packed configuration system*

**Successor of FursConfig, rewritten from zero**
  
Readable, fast, flexible, modifiable.  
Works great with small configs, scales to massive ones just as easily.

</div>

---

## ⭐ Features at a glance

✔ Very fast parsing (around **3-6x faster** than Typesafe's Config, see below for benchmarks)  
✔ Human readable syntax with **spaces in keys supported**  
✔ Uses `=` or `:` assignment (`name = "v"`, `name: "v"`) (preserved)  
✔ `//` and `#` comments supported (inline and standalone) (preserved)  
✔ **Formatting preserved** when writing back to file  
✔ **Runtime editing** of config nodes  
✔ **NodeBuilder API** for generating configs in code  
✔ **Merge support** for default + user configs  
✔ Can load, modify, save and reformat configs easily  
✔ Config Loader & Adapters let you bind config data to real objects

---

<div align="center">

## **Installation**

### **Gradle**
</div>

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.vansencool:Versa:2.5.1'
}
```

<div align="center">

### **Maven**

</div>

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.vansencool</groupId>
    <artifactId>Versa</artifactId>
    <version>2.5.1</version>
</dependency>
```

---

## 📌 Syntax Overview

### Root values

```hocon
enabled = true
welcome: "Hello world"
answer is = 42         # spaces in keys work fine
````

### Branches

```hocon
server {
    name = "MyServer"
    port = 25565
}
```

### Nested structure

```hocon
app {
    database {
        host = "localhost"
        port = 3306
    }

    logging {
        level = "INFO"
    }
}
```

### Comments

```hocon
// standalone comment
# also supported

value = 10 // inline here
text = "Hi" # works too
```

### Lists

```hocon
names = [ "Alice", "Bob", "Charlie" ]
numbers: [1,2,3,4]
```

### List of branches (complex lists)

```hocon
servers = [
    {
      name = "prod"     
      secure = true
    },
    {
      name = "testing"  
      secure = false
    }
]
```

## NodeBuilder Example

Build configuration programmatically with full control over layout.

```java
Node cfg = new NodeBuilder()
    .name("root")
    .child(NodeBuilder.builder()
            .name("database")
            .startComment(" Connection settings") // branch start comment
            .add(new ValueBuilder().name("host").string("localhost"))
            .add(new ValueBuilder().name("port").intVal(3306))
            .emptyLine()
            .child(
                    new NodeBuilder()
                            .name("pool")
                            .add(new ValueBuilder().name("size").intVal(10))
                            .build()
            ).build()
    )
    .build();

System.out.println(cfg);
```

Output:

```hocon
database { // Connection settings
    host = "localhost"
    port = 3306

    pool {
        size = 10
    }
}
```

---

## Object Binding & Adapters Example

Bind config files directly to Java classes.
Supports branches, nested objects, lists, and custom serialized types.

### Example configuration class

```java
@ConfigFile("config.conf")
public static class AppConfig {

    @ConfigPath("name")
    public static String name = "VersaApp";

    @ConfigPath("debug")
    @ConfigSpace
    public static boolean debug = false;

    @ConfigPath("tags")
    @ConfigSpace
    public static List<String> tags = List.of("fast", "simple", "clean");

    @ConfigPath("maintenance_window")
    public static Duration maintenance = Duration.ofMinutes(30);

    @Branch
    @ConfigPath("database")
    public static Database database = new Database();

    @ConfigBranchSpace(before = true)
    public static class Database {

        @ConfigPath("host")
        public String host = "localhost";

        @ConfigPath("port")
        public int port = 3306;

        @Branch
        @ConfigPath("auth")
        public Auth auth = new Auth();

        @ConfigBranchSpace(before = true)
        public static class Auth {
            @ConfigPath("user") public String user = "admin";
            @ConfigPath("password") public String pass = "secret";
        }
    }
}
```

### Duration Adapter

```java
public static class DurationAdapter implements ConfigAdapter<Duration> {
    public @NotNull Duration fromNode(@NotNull Node node) {
        return Duration.ofMinutes(node.getInteger("minutes"));
    }

    public void toNode(Duration value, NodeBuilder builder) {
        builder.add(new ValueBuilder().name("minutes").intVal((int) value.toMinutes()));
    }
}
```

Output:

```hocon
name = "VersaApp"
debug = false

tags = ["fast", "simple", "clean"]

maintenance_window {
    minutes = 30
}

database {
    host = "localhost"
    port = 3306

    auth {
        user = "admin"
        password = "secret"
    }
}
```

### What the annotations do

| Annotation                         | Meaning                                                 |
| ---------------------------------- | ------------------------------------------------------- |
| `@ConfigFile("config.versa")`      | Binds this class to a config file on disk.              |
| `@ConfigPath("name")`              | Maps a field to a config key with that path.            |
| `@ConfigSpace`                     | Preserves spacing around values when saving.            |
| `@Branch`                          | Marks a class as a nested config section.               |
| `@ConfigBranchSpace(before/after)` | Adds blank lines for readability.                       |
| `ConfigAdapter`                    | Converts a complex type (like Duration) to/from config. |

---

## Benchmarks

Versa aims to be fast while staying readable and flexible.  
Below are JMH results comparing `Versa.parseText()` versus `ConfigFactory.parseString()`  
on different config sizes and structures.

### **Performance Chart (ms/op | lower is faster)**

<img src="images/benchmark.png" width="650"/>

### Results

| Size / Type | Typesafe | Versa | Faster By |
|---|---:|---:|---:|
| very_small | 0.018 ms | **0.005 ms** | ~3.6x |
| small | 0.072 ms | **0.021 ms** | ~3.4x |
| medium | 1.706 ms | **0.450 ms** | ~3.8x |
| large | 116.637 ms | **18.485 ms** | ~6.3x |
| insane | 5857.540 ms | **1650.671 ms** | ~3.5x |

---

### Benchmark Setup

CPU: Ryzen 7 3700X
JMH Settings:  
Warmup: iterations = 3, time = 1 (seconds)  
Measurement: iterations = 5, time = 1 (seconds)  
Forks: 2

> Parsing benchmark uses Versa.parseText(...) and ConfigFactory.parseString(...)  
> Same config for both libraries.

### Important difference

Versa **preserves everything** when loading files:

- comments  
- empty lines  
- order

Typesafe **does not preserve layout**, `render()` usually produces a new formatted output,  
losing original comments, spacing and config structure.

> Versa keeps your config files looking the same as before.

---

## Notes

[`USAGE.md`](USAGE.md) covers common and practical usage examples, but does not document every available method.
Versa is flexible by design, so exploring the API and its Javadocs is encouraged.