package dev.kata.compiler.codegen;

import dev.kata.compiler.check.Builtins;
import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.check.ClassTable.FieldInfo;
import dev.kata.compiler.check.ClassTable.MethodInfo;
import dev.kata.compiler.check.ClassTable.ParamInfo;
import dev.kata.compiler.check.Type;

/** JVM names and descriptors for kata types (spec 06 #type-mapping). */
final class Names {
    private Names() {}

    static final String OBJECT = "java/lang/Object";
    static final String STRING = "java/lang/String";
    static final String KLIST = "kata/rt/KList";
    static final String FLOATS = "kata/rt/Floats";
    static final String ACTION = "kata/rt/Action";
    static final String HANDLER = "kata/rt/Handler";
    static final String FN = "kata/rt/Fn";
    static final String SCREEN = "kata/rt/Screen";
    static final String KATA_ACTIVITY = "kata/rt/KataActivity";
    static final String MAIN_ACTIVITY = "kata/gen/MainActivity";

    /** Names that would override a final (or specially treated) method of java.lang.Object. */
    private static final java.util.Set<String> OBJECT_METHODS = java.util.Set.of("wait", "notify", "notifyAll",
            "finalize");

    /** The JVM name of a kata method. A few names are suffixed so that they never touch Object's own. */
    static String method(String kataName) {
        return OBJECT_METHODS.contains(kataName) ? kataName + "$kata" : kataName;
    }

    /** kata.ui.X lives in kata/rt/X; a user class a.b.C in a/b/C. */
    static String internal(String qualifiedName) {
        for (String module : new String[] {Builtins.UI_MODULE, Builtins.SYS_MODULE}) {
            String prefix = module + ".";
            if (qualifiedName.startsWith(prefix)) {
                return "kata/rt/" + qualifiedName.substring(prefix.length());
            }
        }
        return qualifiedName.replace('.', '/');
    }

    /** The class of one variant: nested under its enum's class, e.g. app/main/Shape$Circle. */
    static String variant(String enumQualifiedName, String variantName) {
        return internal(enumQualifiedName) + "$" + variantName;
    }

    /** The internal name of a reference type. */
    static String internal(Type type) {
        if (type.equals(Type.STRING)) {
            return STRING;
        }
        if (type.equals(Type.ACTION)) {
            return ACTION;
        }
        if (type instanceof Type.ListType) {
            return KLIST;
        }
        if (type instanceof Type.HandlerType) {
            return HANDLER;
        }
        if (type instanceof Type.FnType) {
            return FN;
        }
        if (type instanceof Type.ClassType classType) {
            return internal(classType.qualifiedName());
        }
        if (type instanceof Type.InterfaceType interfaceType) {
            return internal(interfaceType.qualifiedName());
        }
        if (type instanceof Type.EnumType enumType) {
            return internal(enumType.qualifiedName());
        }
        throw new IllegalStateException("not a reference type: " + type.display());
    }

    static String descriptor(Type type) {
        if (type.equals(Type.INT)) {
            return "J";
        }
        if (type.equals(Type.FLOAT)) {
            return "D";
        }
        if (type.equals(Type.BOOL)) {
            return "Z";
        }
        if (type.equals(Type.UNIT)) {
            return "V";
        }
        return "L" + internal(type) + ";";
    }

    static String methodDescriptor(MethodInfo method) {
        StringBuilder out = new StringBuilder("(");
        for (ParamInfo param : method.params()) {
            out.append(descriptor(param.type()));
        }
        return out.append(")").append(descriptor(method.returnType())).toString();
    }

    static String constructorDescriptor(ClassInfo info) {
        StringBuilder out = new StringBuilder("(");
        for (FieldInfo field : info.constructorFields()) {
            out.append(descriptor(field.type()));
        }
        return out.append(")V").toString();
    }

    /** The descriptor of the type as an object: Int and Bool become their wrapper classes. */
    static String boxedDescriptor(Type type) {
        if (type.equals(Type.INT)) {
            return "Ljava/lang/Long;";
        }
        if (type.equals(Type.FLOAT)) {
            return "Ljava/lang/Double;";
        }
        if (type.equals(Type.BOOL)) {
            return "Ljava/lang/Boolean;";
        }
        return descriptor(type);
    }
}
