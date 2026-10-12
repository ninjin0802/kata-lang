package dev.kata.compiler.check;

import dev.kata.compiler.build.AppInfo;
import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.syntax.Ast;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The syntax trees plus everything the checker resolved. Complete only when there are no diagnostics. */
public final class CheckedProgram {
    private final List<Ast.File> files;
    private final ClassTable classes;
    final Map<Ast.Expr, Type> exprTypes = new IdentityHashMap<>();
    final Map<Ast.Expr, MemberRef> members = new IdentityHashMap<>();
    final Map<Ast.TypeRef, Type> typeRefs = new IdentityHashMap<>();
    final Map<Ast.ClassDecl, ClassInfo> classInfos = new IdentityHashMap<>();
    final Map<Ast.Construct, ClassInfo> constructs = new IdentityHashMap<>();
    AppInfo app;
    Ast.Expr rootExpr;

    CheckedProgram(List<Ast.File> files, ClassTable classes) {
        this.files = files;
        this.classes = classes;
    }

    public List<Ast.File> files() {
        return files;
    }

    public ClassTable classes() {
        return classes;
    }

    public Type typeOf(Ast.Expr expr) {
        return exprTypes.get(expr);
    }

    /** The field or method a Member or Call expression refers to. */
    public MemberRef memberOf(Ast.Expr expr) {
        return members.get(expr);
    }

    public Type typeOf(Ast.TypeRef ref) {
        return typeRefs.get(ref);
    }

    public ClassInfo classOf(Ast.ClassDecl decl) {
        return classInfos.get(decl);
    }

    public ClassInfo classOf(Ast.Construct construct) {
        return constructs.get(construct);
    }

    /** Whether any construction expression in the program builds the given class. */
    public boolean constructs(String qualifiedName) {
        for (ClassInfo info : constructs.values()) {
            if (info.qualifiedName().equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    public AppInfo app() {
        return app;
    }

    public Ast.Expr rootExpr() {
        return rootExpr;
    }
}
