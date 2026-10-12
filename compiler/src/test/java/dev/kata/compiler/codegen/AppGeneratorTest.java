package dev.kata.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kata.compiler.syntax.ParserTestSources;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class AppGeneratorTest {
    @Test
    void generatesMainActivityThatCreatesTheRoot() {
        Map<String, byte[]> classes = ExecSupport.generate(
                Map.of("src/app/counter.kata", ParserTestSources.COUNTER));
        assertTrue(classes.containsKey("app/counter/Counter"), classes.keySet().toString());
        byte[] main = classes.get("kata/gen/MainActivity");
        assertNotNull(main);
        ClassReader reader = new ClassReader(main);
        assertEquals("kata/rt/KataActivity", reader.getSuperName());
        assertEquals(52, reader.readShort(6));
        List<String> methods = new ArrayList<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                methods.add(name + descriptor);
                return null;
            }
        }, 0);
        assertTrue(methods.contains("createRoot()Lkata/rt/Screen;"), methods.toString());
        assertTrue(methods.contains("<init>()V"), methods.toString());
    }
}
