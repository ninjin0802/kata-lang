package dev.kata.compiler.check;

import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.check.ClassTable.FieldInfo;
import dev.kata.compiler.check.ClassTable.InterfaceInfo;
import dev.kata.compiler.check.ClassTable.MethodInfo;
import dev.kata.compiler.check.ClassTable.ParamInfo;
import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.syntax.Ast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Checks declarations and builds the ClassInfo of every user class. */
final class DeclChecker {
    private final ClassTable table;
    private final CheckedProgram program;
    private final List<Diagnostic> out;

    DeclChecker(ClassTable table, CheckedProgram program, List<Diagnostic> out) {
        this.table = table;
        this.program = program;
        this.out = out;
    }

    /** Registers the file's class names, so that classes can refer to each other in any order. */
    List<Ast.ClassDecl> declareNames(FileScope scope) {
        List<Ast.ClassDecl> accepted = new ArrayList<>();
        for (Ast.ClassDecl decl : scope.file.classes()) {
            if (scope.addClass(decl)) {
                accepted.add(decl);
            }
        }
        return accepted;
    }

    /** Registers the file's enum names. Must run before any type in the file is resolved. */
    List<Ast.EnumDecl> declareEnumNames(FileScope scope) {
        List<Ast.EnumDecl> accepted = new ArrayList<>();
        for (Ast.EnumDecl decl : scope.file.enums()) {
            if (scope.addEnum(decl)) {
                accepted.add(decl);
            }
        }
        return accepted;
    }

    /** Resolves the variants' field types and registers the EnumInfo. */
    void declareVariants(FileScope scope, Ast.EnumDecl decl) {
        String path = scope.file.path();
        Set<String> variantNames = new HashSet<>();
        List<ClassTable.VariantInfo> variants = new ArrayList<>();
        for (Ast.VariantDecl variant : decl.variants()) {
            Set<String> fieldNames = new HashSet<>();
            List<FieldInfo> fields = new ArrayList<>();
            for (Ast.Param field : variant.fields()) {
                Type type = scope.resolve(field.type(), false);
                if (!fieldNames.add(field.name())) {
                    out.add(Diagnostic.of("E0303", "field '" + field.name() + "' is already defined", path,
                            field.nameSpan()));
                    continue;
                }
                fields.add(new FieldInfo(field.name(), type, false, false));
            }
            if (!variantNames.add(variant.name())) {
                out.add(Diagnostic.of("E0303", "variant '" + variant.name() + "' is already defined", path,
                        variant.nameSpan()));
                continue;
            }
            variants.add(new ClassTable.VariantInfo(variant.name(), fields));
        }
        ClassTable.EnumInfo info = new ClassTable.EnumInfo(scope.qualify(decl.name()), variants);
        table.add(info);
        program.enumInfos.put(decl, info);
    }

    /** Resolves member types and registers the ClassInfo. */
    void declareMembers(FileScope scope, Ast.ClassDecl decl) {
        String path = scope.file.path();
        List<String> interfaces = new ArrayList<>();
        for (Ast.TypeRef ref : decl.interfaces()) {
            Type type = scope.resolve(ref, false);
            if (type instanceof Type.Unknown) {
                continue;
            }
            InterfaceInfo info = type instanceof Type.InterfaceType iface
                    ? table.lookupInterface(iface.qualifiedName()) : null;
            if (info == null) {
                out.add(Diagnostic.of("E0506", "'" + ref.name() + "' is not an interface", path, ref.span()));
            } else if (!info.implementable()) {
                out.add(Diagnostic.of("E0506", "'" + ref.name() + "' cannot be implemented by a class", path,
                        ref.span()));
            } else if (!interfaces.contains(info.qualifiedName())) {
                interfaces.add(info.qualifiedName());
            }
        }

        Set<String> memberNames = new HashSet<>();
        List<FieldInfo> fields = new ArrayList<>();
        for (Ast.FieldDecl field : decl.fields()) {
            Type type = scope.resolve(field.type(), false);
            if (!memberNames.add(field.name())) {
                out.add(Diagnostic.of("E0303", "'" + field.name() + "' is already defined in this class", path,
                        field.nameSpan()));
                continue;
            }
            fields.add(new FieldInfo(field.name(), type, field.mutable(), field.init() != null));
        }
        List<MethodInfo> methods = new ArrayList<>();
        for (Ast.MethodDecl method : decl.methods()) {
            Set<String> paramNames = new HashSet<>();
            List<ParamInfo> params = new ArrayList<>();
            for (Ast.Param param : method.params()) {
                Type type = scope.resolve(param.type(), false);
                if (!paramNames.add(param.name())) {
                    out.add(Diagnostic.of("E0303", "parameter '" + param.name() + "' is already defined", path,
                            param.nameSpan()));
                }
                params.add(new ParamInfo(param.name(), type));
            }
            Type returnType = scope.resolve(method.returnType(), true);
            if (!memberNames.add(method.name())) {
                out.add(Diagnostic.of("E0303", "'" + method.name() + "' is already defined in this class", path,
                        method.nameSpan()));
                continue;
            }
            methods.add(new MethodInfo(method.name(), params, returnType));
        }
        ClassInfo info = new ClassInfo(scope.qualify(decl.name()), interfaces, fields, methods, false);
        table.add(info);
        program.classInfos.put(decl, info);
    }

    /** Spec 04 #classes: every interface method is defined with the same parameters and return type. */
    void checkInterfaces(FileScope scope, Ast.ClassDecl decl) {
        ClassInfo info = program.classInfos.get(decl);
        for (String name : info.interfaces()) {
            InterfaceInfo iface = table.lookupInterface(name);
            for (MethodInfo required : iface.methods()) {
                MethodInfo actual = info.method(required.name());
                if (actual == null) {
                    out.add(Diagnostic.of("E0506", "class '" + decl.name() + "' does not define '"
                            + required.name() + "' required by " + new Type.InterfaceType(name).display(),
                            scope.file.path(), decl.nameSpan()).withExpected(required.signature(), null));
                } else if (!sameSignature(actual, required)) {
                    out.add(Diagnostic.of("E0506", "'" + required.name() + "' does not match the declaration in "
                            + new Type.InterfaceType(name).display(), scope.file.path(), decl.nameSpan())
                            .withExpected(required.signature(), actual.signature()));
                }
            }
        }
    }

    private static boolean sameSignature(MethodInfo a, MethodInfo b) {
        if (a.params().size() != b.params().size() || !compatible(a.returnType(), b.returnType())) {
            return false;
        }
        for (int i = 0; i < a.params().size(); i++) {
            if (!compatible(a.params().get(i).type(), b.params().get(i).type())) {
                return false;
            }
        }
        return true;
    }

    /** An unresolved type already has its own diagnostic. */
    private static boolean compatible(Type a, Type b) {
        return a instanceof Type.Unknown || b instanceof Type.Unknown || a.equals(b);
    }
}
