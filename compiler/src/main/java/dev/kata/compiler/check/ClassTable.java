package dev.kata.compiler.check;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every class and interface known to the program, built-in and user-defined. */
public final class ClassTable {
    public record ParamInfo(String name, Type type) {}

    public record FieldInfo(String name, Type type, boolean mutable, boolean hasInit) {}

    public record MethodInfo(String name, List<ParamInfo> params, Type returnType) {
        /** The declaration as written in kata, e.g. `fn view(self) -> Ui`. */
        public String signature() {
            StringBuilder out = new StringBuilder("fn ").append(name).append("(self");
            for (ParamInfo param : params) {
                out.append(", ").append(param.name()).append(": ").append(param.type().display());
            }
            return out.append(") -> ").append(returnType.display()).toString();
        }
    }

    public record ClassInfo(String qualifiedName, List<String> interfaces, List<FieldInfo> fields,
            List<MethodInfo> methods, boolean builtin) {
        public FieldInfo field(String name) {
            return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
        }

        public MethodInfo method(String name) {
            return methods.stream().filter(m -> m.name().equals(name)).findFirst().orElse(null);
        }

        /** The fields a construction expression supplies, in declaration order. */
        public List<FieldInfo> constructorFields() {
            return fields.stream().filter(f -> !f.hasInit()).toList();
        }

        public String simpleName() {
            return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
        }
    }

    public record InterfaceInfo(String qualifiedName, List<MethodInfo> methods, boolean implementable) {
        public MethodInfo method(String name) {
            return methods.stream().filter(m -> m.name().equals(name)).findFirst().orElse(null);
        }
    }

    public record VariantInfo(String name, List<FieldInfo> fields) {}

    public record EnumInfo(String qualifiedName, List<VariantInfo> variants) {
        public VariantInfo variant(String name) {
            return variants.stream().filter(v -> v.name().equals(name)).findFirst().orElse(null);
        }
    }

    private final Map<String, EnumInfo> enums = new LinkedHashMap<>();
    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();
    private final Map<String, InterfaceInfo> interfaces = new LinkedHashMap<>();

    public void add(ClassInfo info) {
        classes.put(info.qualifiedName(), info);
    }

    public void add(InterfaceInfo info) {
        interfaces.put(info.qualifiedName(), info);
    }

    public void add(EnumInfo info) {
        enums.put(info.qualifiedName(), info);
    }

    public EnumInfo lookupEnum(String qualifiedName) {
        return enums.get(qualifiedName);
    }

    public ClassInfo lookup(String qualifiedName) {
        return classes.get(qualifiedName);
    }

    public InterfaceInfo lookupInterface(String qualifiedName) {
        return interfaces.get(qualifiedName);
    }

    /** Spec 04 #assignability: same type, or a class to an interface it implements. */
    public boolean assignable(Type from, Type to) {
        if (from instanceof Type.Unknown || to instanceof Type.Unknown || from.equals(to)) {
            return true;
        }
        if (to instanceof Type.InterfaceType target && from instanceof Type.ClassType source) {
            ClassInfo info = classes.get(source.qualifiedName());
            return info != null && info.interfaces().contains(target.qualifiedName());
        }
        return false;
    }
}
