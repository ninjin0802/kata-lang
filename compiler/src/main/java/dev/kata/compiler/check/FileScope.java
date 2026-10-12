package dev.kata.compiler.check;

import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Span;
import dev.kata.compiler.syntax.Ast;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The type names visible in one file: core types, the file's classes, and its imports. */
final class FileScope {
    final Ast.File file;
    final String module;
    private final ClassTable table;
    private final CheckedProgram program;
    private final List<Diagnostic> out;
    private final Set<String> localClasses = new HashSet<>();
    private final Set<String> localEnums = new HashSet<>();
    private final Map<String, Ast.Import> imports = new LinkedHashMap<>();
    private final Set<String> usedImports = new HashSet<>();
    private final Set<String> reportedMissingImports = new HashSet<>();
    /** Imported names that come from another module of this project, with their resolved type. */
    private final Map<String, Type> userImports = new LinkedHashMap<>();
    private final Map<String, ModuleNames> modules;

    /** The class and enum names one module of the project declares. */
    record ModuleNames(Set<String> classes, Set<String> enums) {
        static ModuleNames of(Ast.File file) {
            Set<String> classes = new HashSet<>();
            Set<String> enums = new HashSet<>();
            file.classes().forEach(decl -> classes.add(decl.name()));
            file.enums().forEach(decl -> enums.add(decl.name()));
            return new ModuleNames(classes, enums);
        }
    }

    /** @param modules what every module of the project declares, by module name */
    FileScope(Ast.File file, ClassTable table, CheckedProgram program, Map<String, ModuleNames> modules,
            List<Diagnostic> out) {
        this.modules = modules;
        this.file = file;
        this.module = file.module().name();
        this.table = table;
        this.program = program;
        this.out = out;
    }

    void addImports() {
        for (Ast.Import decl : file.imports()) {
            String from = decl.module().name();
            Set<String> exported = Builtins.namesOf(from);
            ModuleNames userModule = exported == null ? modules.get(from) : null;
            if (userModule != null && !from.equals(module)) {
                boolean isClass = userModule.classes().contains(decl.name());
                if (!isClass && !userModule.enums().contains(decl.name())) {
                    out.add(Diagnostic.of("E0307", from + " has no '" + decl.name() + "'", file.path(), decl.span()));
                } else if (imports.containsKey(decl.name())) {
                    out.add(Diagnostic.of("E0303", "'" + decl.name() + "' is already imported", file.path(),
                            decl.span()));
                } else {
                    imports.put(decl.name(), decl);
                    String qualified = from + "." + decl.name();
                    userImports.put(decl.name(), isClass ? new Type.ClassType(qualified) : new Type.EnumType(qualified));
                }
            } else if (exported == null) {
                out.add(Diagnostic.of("E0307", from.equals(module)
                        ? "a module cannot import from itself"
                        : "unknown module '" + from + "'", file.path(), decl.span()));
            } else if (!exported.contains(decl.name())) {
                out.add(Diagnostic.of("E0307", decl.module().name() + " has no '" + decl.name() + "'", file.path(),
                        decl.span()));
            } else if (imports.containsKey(decl.name())) {
                out.add(Diagnostic.of("E0303", "'" + decl.name() + "' is already imported", file.path(),
                        decl.span()));
            } else {
                imports.put(decl.name(), decl);
            }
        }
    }

    /** Declares a class of this file. Returns false, with a diagnostic, when the name is taken. */
    boolean addClass(Ast.ClassDecl decl) {
        String name = decl.name();
        if (Builtins.CORE_TYPES.contains(name) || imports.containsKey(name) || localEnums.contains(name)
                || !localClasses.add(name)) {
            out.add(Diagnostic.of("E0303", "'" + name + "' is already defined", file.path(), decl.nameSpan()));
            return false;
        }
        return true;
    }

    /** Declares an enum of this file. Returns false, with a diagnostic, when the name is taken. */
    boolean addEnum(Ast.EnumDecl decl) {
        String name = decl.name();
        if (Builtins.CORE_TYPES.contains(name) || imports.containsKey(name) || localClasses.contains(name)
                || !localEnums.add(name)) {
            out.add(Diagnostic.of("E0303", "'" + name + "' is already defined", file.path(), decl.nameSpan()));
            return false;
        }
        return true;
    }

    /** The qualified name of the enum this file calls `name` (its own or an imported one), or null. */
    String enumName(String name) {
        if (localEnums.contains(name)) {
            return qualify(name);
        }
        if (userImports.get(name) instanceof Type.EnumType imported) {
            usedImports.add(name);
            return imported.qualifiedName();
        }
        return null;
    }

    String qualify(String className) {
        return module + "." + className;
    }

    void reportUnusedImports() {
        for (Ast.Import decl : imports.values()) {
            if (!usedImports.contains(decl.name())) {
                out.add(Diagnostic.of("E0306", "unused import '" + decl.name() + "'", file.path(), decl.span())
                        .withFix(decl.span(), ""));
            }
        }
    }

    /** Resolves a written type. Unit is accepted only where allowUnit is set (a method's return type). */
    Type resolve(Ast.TypeRef ref, boolean allowUnit) {
        Type type = resolveUnrecorded(ref, allowUnit);
        program.typeRefs.put(ref, type);
        return type;
    }

    private Type resolveUnrecorded(Ast.TypeRef ref, boolean allowUnit) {
        String name = ref.name();
        int arity = name.equals("List") || name.equals("Handler") ? 1 : name.equals("Fn") ? 2 : 0;
        boolean known = Builtins.CORE_TYPES.contains(name) || localClasses.contains(name)
                || localEnums.contains(name) || userImports.containsKey(name)
                || Builtins.moduleOf(name) != null;
        if (known && ref.args().size() != arity) {
            out.add(Diagnostic.of("E0408", "'" + name + "' takes " + arity + " type argument"
                    + (arity == 1 ? "" : "s"), file.path(), ref.span())
                    .withExpected(Integer.toString(arity), Integer.toString(ref.args().size())));
            for (Ast.TypeRef arg : ref.args()) {
                resolve(arg, false);
            }
            return Type.UNKNOWN;
        }
        switch (name) {
            case "Int":
                return Type.INT;
            case "Float":
                return Type.FLOAT;
            case "Bool":
                return Type.BOOL;
            case "String":
                return Type.STRING;
            case "Action":
                return Type.ACTION;
            case "Unit":
                if (!allowUnit) {
                    out.add(Diagnostic.of("E0407", "Unit can only be used as a return type", file.path(),
                            ref.span()));
                    return Type.UNKNOWN;
                }
                return Type.UNIT;
            case "List":
                return new Type.ListType(resolve(ref.args().get(0), false));
            case "Handler":
                return new Type.HandlerType(resolve(ref.args().get(0), false));
            case "Fn":
                return new Type.FnType(resolve(ref.args().get(0), false), resolve(ref.args().get(1), false));
            default:
                return resolveNamed(name, ref.span());
        }
    }

    /** Resolves a class or interface name used as a type or in a construction expression. */
    Type resolveNamed(String name, Span span) {
        if (localClasses.contains(name)) {
            return new Type.ClassType(qualify(name));
        }
        if (localEnums.contains(name)) {
            return new Type.EnumType(qualify(name));
        }
        if (userImports.containsKey(name)) {
            usedImports.add(name);
            return userImports.get(name);
        }
        String builtinModule = Builtins.moduleOf(name);
        if (builtinModule != null) {
            if (imports.containsKey(name)) {
                usedImports.add(name);
            } else if (reportedMissingImports.add(name)) {
                Span insertAt = importInsertionPoint();
                out.add(Diagnostic.of("E0305", "'" + name + "' must be imported from " + builtinModule, file.path(),
                        span).withFix(insertAt, "\nimport " + builtinModule + "." + name + ";"));
            }
            String qualified = builtinModule + "." + name;
            return Builtins.isUiInterface(name) ? new Type.InterfaceType(qualified) : new Type.ClassType(qualified);
        }
        // Declared by exactly one other module of the project: it only lacks the import.
        List<String> owners = new java.util.ArrayList<>();
        for (Map.Entry<String, ModuleNames> other : modules.entrySet()) {
            if (!other.getKey().equals(module)
                    && (other.getValue().classes().contains(name) || other.getValue().enums().contains(name))) {
                owners.add(other.getKey());
            }
        }
        if (owners.size() == 1) {
            if (reportedMissingImports.add(name)) {
                out.add(Diagnostic.of("E0305", "'" + name + "' must be imported from " + owners.get(0), file.path(),
                        span).withFix(importInsertionPoint(), "\nimport " + owners.get(0) + "." + name + ";"));
            }
            ModuleNames owner = modules.get(owners.get(0));
            String qualified = owners.get(0) + "." + name;
            return owner.classes().contains(name) ? new Type.ClassType(qualified) : new Type.EnumType(qualified);
        }
        out.add(Diagnostic.of("E0302", "unknown type '" + name + "'", file.path(), span));
        return Type.UNKNOWN;
    }

    /** Just after the last import, or after the module declaration when there are no imports. */
    private Span importInsertionPoint() {
        Span last = file.moduleDeclSpan();
        for (Ast.Import decl : file.imports()) {
            last = decl.span();
        }
        return last.endPoint();
    }
}
