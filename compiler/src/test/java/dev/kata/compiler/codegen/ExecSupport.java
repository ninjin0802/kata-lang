package dev.kata.compiler.codegen;

import static org.junit.jupiter.api.Assertions.fail;

import dev.kata.compiler.check.CheckSupport;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/** Compiles kata sources in memory and runs the generated classes on the desktop JVM. */
final class ExecSupport {
    private ExecSupport() {}

    /** Generates classes for declarations wrapped by CheckSupport.wrap (module app.main, starting on line 5). */
    static ClassLoader compile(String declarations) {
        return compileProgram(Map.of(CheckSupport.PATH, CheckSupport.wrap(declarations)));
    }

    static ClassLoader compileProgram(Map<String, String> sources) {
        Map<String, byte[]> classes = generate(sources);
        return new ClassLoader(ExecSupport.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name.replace('.', '/'));
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
    }

    static Map<String, byte[]> generate(Map<String, String> sources) {
        CheckSupport checked = CheckSupport.checkFiles(sources);
        if (!checked.diagnostics.isEmpty()) {
            fail("test source has errors: " + checked.diagnostics);
        }
        return ClassGenerator.generate(checked.program);
    }

    /** Instantiates a generated class through its only constructor. */
    static Object create(ClassLoader loader, String className, Object... args) throws Exception {
        Constructor<?> constructor = loader.loadClass(className).getConstructors()[0];
        return constructor.newInstance(args);
    }

    /** Calls the method with the given name, rethrowing what the kata code threw. */
    static Object call(Object target, String name, Object... args) throws Throwable {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        }
        throw new NoSuchMethodException(name);
    }

    static Object field(Object target, String name) throws Exception {
        return target.getClass().getField(name).get(target);
    }

    /** The 1-based line of the first occurrence of the snippet in the wrapped source. */
    static int lineOf(String declarations, String snippet) {
        String source = CheckSupport.wrap(declarations);
        int index = source.indexOf(snippet);
        if (index < 0) {
            throw new IllegalArgumentException(snippet);
        }
        return (int) source.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }
}
