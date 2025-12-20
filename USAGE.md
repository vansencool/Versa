# Versa Documentation

This document shows how to use Versa in practice.

---

## Parsing

```java
Node root = Versa.parseText(text);
Node root = Versa.parse(file);
Node root = Versa.parse(path);
```

Parsing preserves comments, spacing, order, and assignment style.

---

## Reading values

### Path lookup

```java
root.getString("database.host");
root.getInteger("database.port");
```

Looks only along the given path.

---

### Deep lookup

```java
root.getValueFromAnywhere("port");
```

Searches all branches recursively and returns the first match.

---

## Lists

```java
List<String> names = root.getStringList("names");
List<Integer> nums = root.getIntegerList("numbers");
```

For lists of branches:

```java
List<Node> servers = root.getBranchList("servers");
```

Each entry is a normal `Node`.

---

## Editing values

```java
root.setValue("enabled", true);
root.setValue("port", 25565);
```

Inline comments:

```java
root.setValueComment("port", " game port");
```

Printed inline and preserved.

---

## Formatting helpers

```java
root.addLineComment(" header");
root.emptyLine();
```

Relative placement:

```java
root.before("port").comment(" before port");
root.after("name").emptyLine();
```

---

## Branch comments

```java
root.addStartComment(" settings");
root.addEndComment(" end");
```

Printed at the opening and closing brace.

---

## Merge

Merge behavior matters and is shown fully.

Defaults:

```hocon
a = 1
b = "x"
```

User config:

```hocon
b = "y"
c = true
```

Code:

```java
Node merged = NodeMerge.mergeNodes(user, defaults);
```

Result:

```hocon
a = 1
b = "y"
```

Only known keys are merged. Layout comes from defaults.

---

## ValueBuilder

Used to construct values programmatically.

Simple value:

```java
Value v = ValueBuilder.builder()
    .name("enabled")
    .bool(true)
    .build();
```

Inline comment:

```java
Value v = ValueBuilder.builder()
    .name("enabled")
    .bool(true)
    .comment(CommentType.INLINE_VALUE, "toggle feature")
    .build();
```

List value:

```java
Value v = ValueBuilder.builder()
    .name("numbers")
    .list(
        ValueBuilder.builder().intVal(1).build(),
        ValueBuilder.builder().intVal(2).build()
    )
    .build();
```

---

## NodeBuilder

Used to construct branches.

Basic branch:

```java
Node n = new NodeBuilder()
    .name("database")
    .add(ValueBuilder.builder().name("host").string("localhost"))
    .add(ValueBuilder.builder().name("port").intVal(3306))
    .build();
```

Nested branch:

```java
Node n = new NodeBuilder()
    .name("app")
    .child(
        new NodeBuilder()
            .name("database")
            .add(ValueBuilder.builder().name("host").string("localhost"))
    )
    .build();
```

Branch comments:

```java
Node n = new NodeBuilder()
    .name("database")
    .branchStartComment(" settings")
    .branchEndComment(" end")
    .build();
```

Insertion points:

```java
NodeBuilder b = new NodeBuilder()
    .name("root")
    .add(ValueBuilder.builder().name("a").intVal(1))
    .add(ValueBuilder.builder().name("b").intVal(2));

b.before("b").comment(" before b");
b.after("a").emptyLine();
```