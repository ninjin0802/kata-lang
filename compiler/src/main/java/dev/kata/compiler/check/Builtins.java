package dev.kata.compiler.check;

import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.check.ClassTable.FieldInfo;
import dev.kata.compiler.check.ClassTable.InterfaceInfo;
import dev.kata.compiler.check.ClassTable.MethodInfo;
import dev.kata.compiler.check.ClassTable.ParamInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The declarations of the built-in module kata.ui (spec 05) and the built-in methods (spec 04). */
public final class Builtins {
    private Builtins() {}

    public static final String UI_MODULE = "kata.ui";
    public static final String UI = UI_MODULE + ".Ui";
    public static final String SCREEN = UI_MODULE + ".Screen";

    /** Type names usable without an import. */
    public static final Set<String> CORE_TYPES = Set.of("Int", "Bool", "String", "Unit", "Action", "Handler", "List");

    private static final Type UI_TYPE = new Type.InterfaceType(UI);
    private static final Map<String, List<FieldInfo>> UI_CLASSES = new LinkedHashMap<>();

    static {
        ui("Column", field("children", new Type.ListType(UI_TYPE)));
        ui("Row", field("children", new Type.ListType(UI_TYPE)));
        ui("Text", field("text", Type.STRING));
        ui("Button", field("label", Type.STRING), field("onTap", Type.ACTION));
    }

    /** Classes that are values passed to components rather than components themselves. */
    private static final Set<String> NOT_UI = Set.of("Color");

    private static FieldInfo field(String name, Type type) {
        return new FieldInfo(name, type, false, false);
    }

    private static void ui(String name, FieldInfo... fields) {
        UI_CLASSES.put(name, List.of(fields));
    }

    /** Simple names exported by kata.ui. */
    public static Set<String> uiNames() {
        Set<String> names = new java.util.LinkedHashSet<>(List.of("Ui", "Screen"));
        names.addAll(UI_CLASSES.keySet());
        return names;
    }

    public static boolean isUiInterface(String simpleName) {
        return simpleName.equals("Ui") || simpleName.equals("Screen");
    }

    public static void register(ClassTable table) {
        table.add(new InterfaceInfo(UI, List.of(), false));
        table.add(new InterfaceInfo(SCREEN, List.of(new MethodInfo("view", List.of(), UI_TYPE)), true));
        for (Map.Entry<String, List<FieldInfo>> entry : UI_CLASSES.entrySet()) {
            List<String> interfaces = NOT_UI.contains(entry.getKey()) ? List.of() : List.of(UI);
            table.add(new ClassInfo(UI_MODULE + "." + entry.getKey(), interfaces, entry.getValue(), List.of(), true));
        }
    }

    /** The built-in method `name` on a value of the given type, or null. */
    public static MethodInfo method(Type receiver, String name) {
        for (MethodInfo method : methods(receiver)) {
            if (method.name().equals(name)) {
                return method;
            }
        }
        return null;
    }

    private static List<MethodInfo> methods(Type receiver) {
        List<MethodInfo> methods = new ArrayList<>();
        if (receiver.equals(Type.INT)) {
            methods.add(new MethodInfo("toString", List.of(), Type.STRING));
        } else if (receiver.equals(Type.STRING)) {
            methods.add(new MethodInfo("concat", List.of(new ParamInfo("other", Type.STRING)), Type.STRING));
            methods.add(new MethodInfo("length", List.of(), Type.INT));
        } else if (receiver instanceof Type.ListType list) {
            methods.add(new MethodInfo("size", List.of(), Type.INT));
            methods.add(new MethodInfo("get", List.of(new ParamInfo("index", Type.INT)), list.elem()));
            methods.add(new MethodInfo("append", List.of(new ParamInfo("item", list.elem())), list));
        }
        return methods;
    }
}
