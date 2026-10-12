package dev.kata.compiler.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.check.ClassTable.FieldInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The compiler's kata.ui declarations must match the runtime classes they compile against. */
class BuiltinsParityTest {
    private static final Set<String> SPEC_NAMES = Set.of("Ui", "Screen", "Column", "Row", "Text", "Button",
            "TextField", "Scroll", "HorizontalScroll", "Color", "StyledText", "StyledButton", "Padding",
            "PaddingEdges", "Background", "Width", "Height", "FillWidth", "FillHeight", "Expand", "Space", "Center",
            "CenterHorizontal", "CenterVertical", "AlignEnd", "AlignBottom", "Checkbox", "Switch", "Slider",
            "Progress", "Loading", "Divider", "TextArea", "Tappable", "Rounded");

    @Test
    void exportsExactlyTheNamesInTheSpec() {
        assertEquals(SPEC_NAMES, Set.copyOf(Builtins.uiNames()));
    }

    @Test
    void everyClassMatchesItsRuntimeClass() throws Exception {
        ClassTable table = new ClassTable();
        Builtins.register(table);
        for (String name : Builtins.uiNames()) {
            if (Builtins.isUiInterface(name)) {
                assertTrue(Class.forName("kata.rt." + name).isInterface(), name);
                continue;
            }
            ClassInfo info = table.lookup("kata.ui." + name);
            Class<?> runtime = Class.forName("kata.rt." + name);
            assertTrue(Modifier.isFinal(runtime.getModifiers()), name);

            List<String> expectedFields = new ArrayList<>();
            List<Class<?>> expectedTypes = new ArrayList<>();
            for (FieldInfo field : info.fields()) {
                expectedFields.add(field.name());
                expectedTypes.add(runtimeType(field.type()));
            }
            List<String> actualFields = new ArrayList<>();
            List<Class<?>> actualTypes = new ArrayList<>();
            for (Field field : runtime.getDeclaredFields()) {
                assertTrue(Modifier.isPublic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()),
                        name + "." + field.getName());
                actualFields.add(field.getName());
                actualTypes.add(field.getType());
            }
            assertEquals(expectedFields, actualFields, name);
            assertEquals(expectedTypes, actualTypes, name);

            Constructor<?>[] constructors = runtime.getConstructors();
            assertEquals(1, constructors.length, name);
            assertEquals(expectedTypes, List.of(constructors[0].getParameterTypes()), name);

            boolean isUi = kata.rt.Ui.class.isAssignableFrom(runtime);
            assertEquals(isUi, info.interfaces().contains(Builtins.UI), name);
        }
    }

    private static Class<?> runtimeType(Type type) throws Exception {
        if (type.equals(Type.INT)) {
            return long.class;
        }
        if (type.equals(Type.BOOL)) {
            return boolean.class;
        }
        if (type.equals(Type.STRING)) {
            return String.class;
        }
        if (type.equals(Type.ACTION)) {
            return kata.rt.Action.class;
        }
        if (type instanceof Type.HandlerType) {
            return kata.rt.Handler.class;
        }
        if (type instanceof Type.ListType) {
            return kata.rt.KList.class;
        }
        return Class.forName("kata.rt." + type.display());
    }
}
