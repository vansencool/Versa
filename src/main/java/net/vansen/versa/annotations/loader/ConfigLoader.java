package net.vansen.versa.annotations.loader;

import net.vansen.versa.Versa;
import net.vansen.versa.annotations.Branch;
import net.vansen.versa.annotations.ConfigBranchComment;
import net.vansen.versa.annotations.ConfigBranchHeader;
import net.vansen.versa.annotations.ConfigBranchSpace;
import net.vansen.versa.annotations.ConfigComment;
import net.vansen.versa.annotations.ConfigFile;
import net.vansen.versa.annotations.ConfigPath;
import net.vansen.versa.annotations.ConfigSpace;
import net.vansen.versa.annotations.adapter.Adapters;
import net.vansen.versa.annotations.adapter.ConfigAdapter;
import net.vansen.versa.builder.NodeBuilder;
import net.vansen.versa.builder.ValueBuilder;
import net.vansen.versa.comments.CommentType;
import net.vansen.versa.node.Node;
import net.vansen.versa.node.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and maps config values into Java static fields using {@code @ConfigFile} and {@code @ConfigPath}.
 * If the config file does not exist, it will be generated automatically using the default field values.
 * <p>
 * <b>Supports:</b>
 * <ul>
 *     <li>Primitive types &amp; wrappers (String, int, long, boolean, double...)</li>
 *     <li>{@code List<T>} where T is either primitive-like or adapter-supported</li>
 *     <li>Custom objects using {@link ConfigAdapter}</li>
 * </ul>
 *
 * <p>Example:</p>
 * <pre>{@code
 * @ConfigFile("config.versa")
 * public class MyConfig {
 *     @ConfigPath("name") public static String name = "Server";
 *     @ConfigPath("port") public static int port = 25565;
 * }
 *
 * public static void main(String[] args) {
 *     ConfigLoader.load(MyConfig.class);
 *     System.out.println(MyConfig.name);
 * }
 * }</pre>
 *
 * <p><b>Note:</b> Versa Config Loader is still in active development.
 * Expect API changes and occasional bugs.</p>
 */

@SuppressWarnings({"unused", "unchecked"})
public final class ConfigLoader {

    private static final List<Class<?>> loaded = new ArrayList<>();
    private static final Map<Class<?>, Node> nodes = new HashMap<>();
    private static final Map<Field, Object> defaults = new HashMap<>();

    private ConfigLoader() {
    }

    /**
     * Registers and loads a configuration class.
     * Generates the config file from defaults on first run.
     *
     * @param cls class annotated with {@link ConfigFile}
     */
    public static void load(@NotNull Class<?> cls) {
        ConfigFile fileAnn = cls.getAnnotation(ConfigFile.class);
        if (fileAnn == null) return;

        saveDefaults(cls);

        Path file = Path.of(fileAnn.value());
        if (!Files.exists(file)) {
            Node built = buildFromDefaults(cls);
            built.save(file);
        }

        apply(cls);

        if (!loaded.contains(cls)) loaded.add(cls);
    }

    /**
     * Reloads all previously loaded configuration classes from disk.
     * Restores values from the config while keeping defaults for missing keys.
     */
    public static void reload() {
        for (Class<?> c : loaded) apply(c);
    }

    /**
     * Returns the parsed root node of a configuration class.
     * The class must have been loaded using {@link #load(Class)} first.
     * If the config was not loaded yet, this returns null.
     *
     * @param cls config class
     * @return root {@link Node} for this config, or null
     */
    public static Node node(Class<?> cls) {
        return nodes.get(cls);
    }

    private static void saveDefaults(@NotNull Class<?> cls) {
        for (Field f : cls.getDeclaredFields()) {
            ConfigPath cp = f.getAnnotation(ConfigPath.class);
            Branch br = f.getAnnotation(Branch.class);
            if (cp == null && br == null) continue;
            if (!Modifier.isStatic(f.getModifiers()))
                throw new RuntimeException("Config field '" + f.getName() + "' in " + cls.getSimpleName() + " must be static");
            if (Modifier.isFinal(f.getModifiers()))
                throw new RuntimeException("Config field '" + f.getName() + "' in " + cls.getSimpleName() + " cannot be final");
            try {
                f.setAccessible(true);
                defaults.putIfAbsent(f, f.get(null));
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void apply(@NotNull Class<?> rootCls) {
        try {
            ConfigFile file = rootCls.getAnnotation(ConfigFile.class);
            if (file == null) return;

            Node root = Versa.parse(file.value());
            nodes.put(rootCls, root);

            for (Field f : rootCls.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;

                Branch br = f.getAnnotation(Branch.class);
                ConfigPath cp = f.getAnnotation(ConfigPath.class);

                if (br != null) {
                    loadBranchField(root, f);
                    continue;
                }

                if (cp == null) continue;

                Object v = fetch(root, cp.value(), f);
                if (v == null) v = defaults.get(f);
                setStatic(f, v);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void loadBranchField(@NotNull Node root, @NotNull Field branchField) {
        Class<?> branchType = branchField.getType();
        ConfigPath clsPath = branchType.getAnnotation(ConfigPath.class);
        String name = clsPath != null ? clsPath.value() : branchType.getSimpleName().toLowerCase();

        Node node = root.getBranch(name);
        try {
            branchField.setAccessible(true);
            Object instance = branchField.get(null);
            if (instance == null) instance = branchType.getDeclaredConstructor().newInstance();
            if (node != null) loadBranch(node, branchType, instance);
            setStatic(branchField, instance);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void loadBranch(@NotNull Node node, @NotNull Class<?> type, @NotNull Object instance) {
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;

            Branch br = f.getAnnotation(Branch.class);
            ConfigPath cp = f.getAnnotation(ConfigPath.class);

            try {
                f.setAccessible(true);

                if (br != null) {
                    Class<?> childType = f.getType();
                    ConfigPath clsPath = childType.getAnnotation(ConfigPath.class);
                    String childName = clsPath != null ? clsPath.value() : childType.getSimpleName().toLowerCase();
                    Node childNode = node.getBranch(childName);

                    Object childInstance = f.get(instance);
                    if (childInstance == null) childInstance = childType.getDeclaredConstructor().newInstance();
                    if (childNode != null) loadBranch(childNode, childType, childInstance);
                    f.set(instance, childInstance);
                    continue;
                }

                if (cp == null) continue;

                Object def = f.get(instance);
                Object v = fetchInNode(node, cp.value(), f);
                if (v == null) v = def;
                f.set(instance, v);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static @Nullable Object fetch(@NotNull Node root, @NotNull String path, @NotNull Field f) {
        Class<?> t = f.getType();

        if (t == String.class) return root.getString(path);
        if (t == int.class || t == Integer.class) return root.getInteger(path);
        if (t == long.class || t == Long.class) return root.getLong(path);
        if (t == boolean.class || t == Boolean.class) return root.getBool(path);
        if (t == double.class || t == Double.class) return root.getDouble(path);
        if (t == float.class || t == Float.class) {
            Double d = root.getDouble(path);
            return d == null ? null : d.floatValue();
        }

        if (List.class.isAssignableFrom(t)) {
            Value v = root.getValue(path);
            if (v == null) return null;

            if (!(f.getGenericType() instanceof ParameterizedType p)) return null;
            Class<?> comp = (Class<?>) p.getActualTypeArguments()[0];
            ConfigAdapter<?> ad = Adapters.get(comp);

            if (ad != null && v.branchList != null) {
                List<Object> list = new ArrayList<>();
                for (Node nd : v.branchList)
                    list.add(((ConfigAdapter<Object>) ad).fromNode(nd));
                return list;
            }

            if (v.list != null) {
                List<Object> out = new ArrayList<>();
                for (Value x : v.list) out.add(x.raw());
                return out;
            }
            return null;
        }

        ConfigAdapter<?> ad = Adapters.get(t);
        if (ad != null) {
            Node nd = find(root, path);
            if (nd != null) return ((ConfigAdapter<Object>) ad).fromNode(nd);
            return null;
        }

        return null;
    }

    private static @Nullable Object fetchInNode(@NotNull Node node, @NotNull String key, @NotNull Field f) {
        Class<?> t = f.getType();

        if (t == String.class) return node.getString(key);
        if (t == int.class || t == Integer.class) return node.getInteger(key);
        if (t == long.class || t == Long.class) return node.getLong(key);
        if (t == boolean.class || t == Boolean.class) return node.getBool(key);
        if (t == double.class || t == Double.class) return node.getDouble(key);
        if (t == float.class || t == Float.class) {
            Double d = node.getDouble(key);
            return d == null ? null : d.floatValue();
        }

        if (List.class.isAssignableFrom(t)) {
            Value v = node.getValue(key);
            if (v == null) return null;

            if (!(f.getGenericType() instanceof ParameterizedType p)) return null;
            Class<?> comp = (Class<?>) p.getActualTypeArguments()[0];
            ConfigAdapter<?> ad = Adapters.get(comp);

            if (ad != null && v.branchList != null) {
                List<Object> list = new ArrayList<>();
                for (Node nd : v.branchList)
                    list.add(((ConfigAdapter<Object>) ad).fromNode(nd));
                return list;
            }

            if (v.list != null) {
                List<Object> out = new ArrayList<>();
                for (Value x : v.list) out.add(x.raw());
                return out;
            }
            return null;
        }

        ConfigAdapter<?> ad = Adapters.get(t);
        if (ad != null) {
            Node nd = node.getBranch(key);
            if (nd != null) return ((ConfigAdapter<Object>) ad).fromNode(nd);
            return null;
        }

        return null;
    }

    private static @Nullable Node find(@NotNull Node n, @NotNull String path) {
        Node cur = n;
        for (String p : path.split("\\.")) {
            boolean ok = false;
            for (Node c : cur.children) {
                if (p.equals(c.name)) {
                    cur = c;
                    ok = true;
                    break;
                }
            }
            if (!ok) return null;
        }
        return cur;
    }

    private static void setStatic(@NotNull Field f, @Nullable Object v) {
        try {
            VarHandle h = MethodHandles.privateLookupIn(f.getDeclaringClass(), MethodHandles.lookup())
                    .findStaticVarHandle(f.getDeclaringClass(), f.getName(), f.getType());
            h.set(v);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull Node buildFromDefaults(@NotNull Class<?> rootCls) {
        NodeBuilder root = new NodeBuilder();

        for (Field f : rootCls.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            writeField(root, f, null);
        }

        return root.build();
    }

    private static void writeField(
            @NotNull NodeBuilder base,
            @NotNull Field f,
            Object instance
    ) {
        Branch br = f.getAnnotation(Branch.class);
        ConfigPath cp = f.getAnnotation(ConfigPath.class);
        if (cp == null && br == null) return;

        try {
            f.setAccessible(true);

            Object def = instance == null ? defaults.get(f) : f.get(instance);
            Class<?> type = f.getType();

            ConfigAdapter<?> adType = Adapters.get(type);
            ConfigSpace sp = f.getAnnotation(ConfigSpace.class);
            ConfigComment cc = f.getAnnotation(ConfigComment.class);

            String path = cp != null ? cp.value() : null;

            if (adType != null && cp != null) {
                emitBefore(base, path, sp);

                NodeBuilder nb = new NodeBuilder().name(path);
                ((ConfigAdapter<Object>) adType).toNode(def, nb);
                base.child(nb);

                emitAfterBranch(base, path, sp, cc);
                return;
            }

            if (br != null) {
                writeBranch(base, f, def);
                return;
            }

            String[] parts = path.split("\\.");
            NodeBuilder t = base;
            for (int i = 0; i < parts.length - 1; i++)
                t = getOrCreate(t, parts[i]);

            String key = parts[parts.length - 1];

            if (List.class.isAssignableFrom(type)) {
                if (def == null) def = List.of();
                emitBefore(t, key, sp);

                emitListValue(t, key, f, def, cc);

                emitAfterValue(t, key, sp, cc);
                return;
            }

            if (def == null) return;

            emitBefore(t, key, sp);

            ValueBuilder vb = ValueBuilder.builder(asValue(def)).name(key);

            if (cc != null && cc.inline())
                vb.comment(CommentType.INLINE_VALUE, cc.value());

            t.add(vb.build());

            emitAfterValue(t, key, sp, cc);

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeBranch(
            @NotNull NodeBuilder base,
            @NotNull Field branchField,
            Object instance
    ) {
        Class<?> branchType = branchField.getType();

        ConfigPath cpType = branchType.getAnnotation(ConfigPath.class);
        ConfigPath cpField = branchField.getAnnotation(ConfigPath.class);

        String name;
        if (cpField != null) name = cpField.value();
        else if (cpType != null) name = cpType.value();
        else name = branchType.getSimpleName().toLowerCase();

        Node existing = base.build().getBranch(name);
        NodeBuilder nb = existing != null
                ? NodeBuilder.fromNode(existing)
                : new NodeBuilder().name(name);

        ConfigBranchComment bc = branchType.getAnnotation(ConfigBranchComment.class);
        ConfigBranchHeader bh = branchField.getAnnotation(ConfigBranchHeader.class);

        ConfigBranchSpace spClass = branchType.getAnnotation(ConfigBranchSpace.class);
        ConfigBranchSpace spField = branchField.getAnnotation(ConfigBranchSpace.class);
        ConfigBranchSpace sp = spField != null ? spField : spClass;

        Object inst = instance;
        if (inst == null) {
            try {
                inst = branchType.getDeclaredConstructor().newInstance();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        for (Field sub : branchType.getDeclaredFields()) {
            if (Modifier.isStatic(sub.getModifiers())) continue;
            writeField(nb, sub, inst);
        }

        if (existing == null) {

            if (sp != null && sp.before())
                base.beforeBranch(name).emptyLine();

            if (bh != null && !bh.start().isEmpty())
                base.beforeBranch(name).comment(bh.start());

            if (bc != null && !bc.start().isEmpty())
                nb.branchStartComment(bc.start());

            base.child(nb);

            if (bc != null && !bc.end().isEmpty())
                nb.branchEndComment(bc.end());

            if (bh != null && !bh.after().isEmpty())
                base.afterBranch(name).comment(bh.after());

            if (sp != null && sp.after())
                base.afterBranch(name).emptyLine();
        }
    }

    private static void emitListValue(
            @NotNull NodeBuilder base,
            @NotNull String key,
            @NotNull Field f,
            @NotNull Object def,
            ConfigComment cc
    ) {
        List<?> list = (List<?>) def;
        ValueBuilder vb = new ValueBuilder().name(key);

        if (list.isEmpty()) {
            base.add(vb.list().build());
            return;
        }

        ParameterizedType p = (ParameterizedType) f.getGenericType();
        Class<?> comp = (Class<?>) p.getActualTypeArguments()[0];
        ConfigAdapter<?> cad = Adapters.get(comp);

        if (cad != null) {
            Node[] arr = new Node[list.size()];
            for (int i = 0; i < list.size(); i++) {
                NodeBuilder nb = new NodeBuilder().name(key + "_" + i);
                ((ConfigAdapter<Object>) cad).toNode(list.get(i), nb);
                Node n = nb.build();
                n.name = null;
                arr[i] = n;
            }
            base.add(vb.branches(arr).build());
            return;
        }

        List<Value> vals = new ArrayList<>();
        for (Object o : list)
            vals.add(asValue(o));

        base.add(vb.list(vals.toArray(new Value[0])).build());
    }

    private static void emitBefore(NodeBuilder base, String key, ConfigSpace sp) {
        if (sp != null && sp.before())
            base.before(key).emptyLine();
    }

    private static void emitAfterValue(
            NodeBuilder base,
            String key,
            ConfigSpace sp,
            ConfigComment cc
    ) {
        if (cc != null && !cc.inline())
            base.before(key).comment(cc.value());

        if (sp != null && sp.after())
            base.after(key).emptyLine();
    }

    private static void emitAfterBranch(
            NodeBuilder base,
            String key,
            ConfigSpace sp,
            ConfigComment cc
    ) {
        if (cc != null && !cc.inline())
            base.afterBranch(key).comment(cc.value());

        if (sp != null && sp.after())
            base.afterBranch(key).emptyLine();
    }

    private static @NotNull NodeBuilder getOrCreate(
            @NotNull NodeBuilder parent,
            @NotNull String name
    ) {
        Node ex = parent.build().getBranch(name);
        if (ex != null) return NodeBuilder.fromNode(ex);
        NodeBuilder nb = new NodeBuilder().name(name);
        parent.child(nb);
        return nb;
    }

    private static @NotNull Value asValue(@NotNull Object o) {
        ValueBuilder v = new ValueBuilder();
        if (o instanceof String s) return v.string(s).build();
        if (o instanceof Integer i) return v.intVal(i).build();
        if (o instanceof Long l) return v.longVal(l).build();
        if (o instanceof Boolean b) return v.bool(b).build();
        if (o instanceof Double d) return v.doubleVal(d).build();
        if (o instanceof Float f) return v.floatVal(f.doubleValue()).build();
        return v.string(o.toString()).build();
    }
}