package dev.kata.compiler.check;

import dev.kata.compiler.build.AppInfo;
import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.syntax.Ast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Checks parsed files against spec chapters 04 and 05. */
public final class Checker {
    private Checker() {}

    private static final Pattern APP_ID = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+");
    private static final List<String> APP_ENTRIES = List.of("id", "name", "root");

    /**
     * @param files parsed files without syntax errors, in path order
     * @param texts source text by file path, used to build fix replacements
     */
    public static CheckedProgram check(List<Ast.File> files, Map<String, String> texts, List<Diagnostic> out) {
        ClassTable table = new ClassTable();
        Builtins.register(table);
        CheckedProgram program = new CheckedProgram(files, table);
        DeclChecker decls = new DeclChecker(table, program, out);

        List<FileScope> scopes = new ArrayList<>();
        List<List<Ast.ClassDecl>> accepted = new ArrayList<>();
        for (Ast.File file : files) {
            FileScope scope = new FileScope(file, table, program, out);
            checkModuleName(file, out);
            scope.addImports();
            scopes.add(scope);
            accepted.add(decls.declareNames(scope));
        }
        for (int i = 0; i < scopes.size(); i++) {
            for (Ast.ClassDecl decl : accepted.get(i)) {
                decls.declareMembers(scopes.get(i), decl);
            }
        }
        boolean appSeen = false;
        for (int i = 0; i < scopes.size(); i++) {
            FileScope scope = scopes.get(i);
            BodyChecker bodies = new BodyChecker(table, scope, new SourceText(texts.get(scope.file.path())),
                    program, out);
            for (Ast.ClassDecl decl : accepted.get(i)) {
                decls.checkInterfaces(scope, decl);
                bodies.checkClass(decl, program.classInfos.get(decl));
            }
            Ast.AppDecl app = scope.file.app();
            if (app != null) {
                if (appSeen) {
                    out.add(Diagnostic.of("E0602", "the project already has an app declaration", scope.file.path(),
                            app.span()));
                } else {
                    appSeen = true;
                    checkApp(scope, bodies, app, program, out);
                }
            }
            scope.reportUnusedImports();
        }
        if (!appSeen) {
            out.add(Diagnostic.of("E0602", "no app declaration in project", null, null));
        }
        return program;
    }

    private static void checkModuleName(Ast.File file, List<Diagnostic> out) {
        String path = file.path();
        String expected = path.substring("src/".length(), path.length() - ".kata".length()).replace('/', '.');
        String actual = file.module().name();
        if (file.module().parts().get(0).equals("kata")) {
            out.add(Diagnostic.of("E0601", "module names starting with 'kata' are reserved", path,
                    file.module().span()));
        } else if (!actual.equals(expected)) {
            out.add(Diagnostic.of("E0601", "module name does not match the file location", path,
                    file.module().span()).withExpected(expected, actual).withFix(file.module().span(), expected));
        }
    }

    private static void checkApp(FileScope scope, BodyChecker bodies, Ast.AppDecl app, CheckedProgram program,
            List<Diagnostic> out) {
        String path = scope.file.path();
        List<String> names = app.inits().stream().map(Ast.Named::name).toList();
        if (!names.equals(APP_ENTRIES)) {
            out.add(Diagnostic.of("E0603", "app must have exactly id, name and root, in this order", path,
                    app.span()).withExpected(String.join(", ", APP_ENTRIES), String.join(", ", names)));
            return;
        }
        String id = stringEntry(app.inits().get(0), path, out);
        String name = stringEntry(app.inits().get(1), path, out);
        boolean valid = id != null && name != null;
        if (id != null && !APP_ID.matcher(id).matches()) {
            out.add(Diagnostic.of("E0603", "app id must be lowercase words joined by periods, such as "
                    + "\"dev.kata.counter\"", path, app.inits().get(0).value().span()));
            valid = false;
        }
        if (name != null && name.isEmpty()) {
            out.add(Diagnostic.of("E0603", "app name must not be empty", path, app.inits().get(1).value().span()));
            valid = false;
        }
        Ast.Expr root = app.inits().get(2).value();
        bodies.checkAppRoot(root);
        if (valid) {
            program.app = new AppInfo(id, name);
            program.rootExpr = root;
        }
    }

    private static String stringEntry(Ast.Named entry, String path, List<Diagnostic> out) {
        if (entry.value() instanceof Ast.StringLit literal) {
            return literal.value();
        }
        out.add(Diagnostic.of("E0603", "app " + entry.name() + " must be a string literal", path,
                entry.value().span()));
        return null;
    }
}
