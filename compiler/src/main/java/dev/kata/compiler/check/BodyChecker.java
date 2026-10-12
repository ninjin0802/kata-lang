package dev.kata.compiler.check;

import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.check.ClassTable.FieldInfo;
import dev.kata.compiler.check.ClassTable.InterfaceInfo;
import dev.kata.compiler.check.ClassTable.MethodInfo;
import dev.kata.compiler.check.ClassTable.ParamInfo;
import dev.kata.compiler.diag.Diagnostic;
import dev.kata.compiler.diag.Span;
import dev.kata.compiler.syntax.Ast;
import dev.kata.compiler.syntax.Ast.BinaryOp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Checks field initializers, method bodies and the app root (spec chapter 04). */
final class BodyChecker {
    private record Local(Type type, boolean mutable) {}

    private enum SelfMode { METHOD, FIELD_INIT, APP_ROOT }

    private final ClassTable table;
    private final FileScope scope;
    private final SourceText text;
    private final CheckedProgram program;
    private final List<Diagnostic> out;
    private final String path;

    private ClassInfo currentClass;
    private Type returnType;
    private SelfMode selfMode;
    private final Deque<Map<String, Local>> locals = new ArrayDeque<>();

    BodyChecker(ClassTable table, FileScope scope, SourceText text, CheckedProgram program, List<Diagnostic> out) {
        this.table = table;
        this.scope = scope;
        this.text = text;
        this.program = program;
        this.out = out;
        this.path = scope.file.path();
    }

    void checkClass(Ast.ClassDecl decl, ClassInfo info) {
        currentClass = info;
        for (Ast.FieldDecl field : decl.fields()) {
            if (field.init() != null) {
                selfMode = SelfMode.FIELD_INIT;
                expect(field.init(), program.typeRefs.get(field.type()));
            }
        }
        selfMode = SelfMode.METHOD;
        for (Ast.MethodDecl method : decl.methods()) {
            returnType = program.typeRefs.get(method.returnType());
            locals.clear();
            Map<String, Local> params = new HashMap<>();
            for (Ast.Param param : method.params()) {
                params.putIfAbsent(param.name(), new Local(program.typeRefs.get(param.type()), false));
            }
            locals.push(params);
            block(method.body());
            locals.pop();
            if (!returnType.equals(Type.UNIT) && !(returnType instanceof Type.Unknown)
                    && !Flow.terminates(method.body())) {
                out.add(Diagnostic.of("E0404", "not every path of '" + method.name() + "' ends with return", path,
                        method.nameSpan()).withExpected(returnType.display(), null));
            }
        }
        currentClass = null;
    }

    void checkAppRoot(Ast.Expr root) {
        currentClass = null;
        selfMode = SelfMode.APP_ROOT;
        locals.clear();
        Type screen = new Type.InterfaceType(Builtins.SCREEN);
        Type actual = infer(root, screen);
        if (!table.assignable(actual, screen)) {
            out.add(Diagnostic.of("E0604", "app root must be a Screen", path, root.span())
                    .withExpected("Screen", actual.display()));
        }
    }

    // ---- statements

    private void block(Ast.Block block) {
        locals.push(new HashMap<>());
        boolean ended = false;
        boolean reported = false;
        for (Ast.Stmt stmt : block.stmts()) {
            if (ended && !reported) {
                out.add(Diagnostic.of("E0409", "statement after return can never run", path, stmt.span()));
                reported = true;
            }
            stmt(stmt);
            ended = ended || Flow.terminates(stmt);
        }
        locals.pop();
    }

    private void stmt(Ast.Stmt stmt) {
        if (stmt instanceof Ast.Let let) {
            Type type = scope.resolve(let.type(), false);
            expect(let.value(), type);
            declare(let.name(), let.nameSpan(), new Local(type, false));
        } else if (stmt instanceof Ast.Var var) {
            Type type = scope.resolve(var.type(), false);
            expect(var.value(), type);
            declare(var.name(), var.nameSpan(), new Local(type, true));
        } else if (stmt instanceof Ast.Assign assign) {
            assign(assign);
        } else if (stmt instanceof Ast.Return ret) {
            returnStmt(ret);
        } else if (stmt instanceof Ast.If branch) {
            condition(branch.cond(), "if");
            block(branch.then());
            if (branch.orElse() != null) {
                stmt(branch.orElse());
            }
        } else if (stmt instanceof Ast.While loop) {
            condition(loop.cond(), "while");
            block(loop.body());
        } else if (stmt instanceof Ast.For loop) {
            forStmt(loop);
        } else if (stmt instanceof Ast.Block nested) {
            block(nested);
        } else if (stmt instanceof Ast.ExprStmt exprStmt) {
            Type type = infer(exprStmt.expr(), null);
            boolean isCall = exprStmt.expr() instanceof Ast.Call;
            if (!isCall || !(type.equals(Type.UNIT) || type instanceof Type.Unknown)) {
                out.add(Diagnostic.of("E0206", isCall
                        ? "the result of this call must be used"
                        : "only a method call returning Unit can be used as a statement", path,
                        exprStmt.expr().span()).withExpected("Unit", isCall ? type.display() : null));
            }
        }
    }

    private void condition(Ast.Expr cond, String keyword) {
        Type type = infer(cond, Type.BOOL);
        if (!table.assignable(type, Type.BOOL)) {
            out.add(Diagnostic.of("E0403", keyword + " condition must be Bool", path, cond.span())
                    .withExpected("Bool", type.display()));
        }
    }

    private void forStmt(Ast.For loop) {
        Type element = scope.resolve(loop.type(), false);
        Type expected = new Type.ListType(element);
        Type actual = infer(loop.iterable(), expected);
        if (!(element instanceof Type.Unknown) && !(actual instanceof Type.Unknown) && !actual.equals(expected)) {
            out.add(Diagnostic.of("E0401", "for needs a " + expected.display(), path, loop.iterable().span())
                    .withExpected(expected.display(), actual.display()));
        }
        locals.push(new HashMap<>());
        declare(loop.name(), loop.nameSpan(), new Local(element, false));
        block(loop.body());
        locals.pop();
    }

    private void returnStmt(Ast.Return ret) {
        if (returnType.equals(Type.UNIT)) {
            if (ret.value() != null) {
                Type type = infer(ret.value(), null);
                out.add(Diagnostic.of("E0405", "a method returning Unit cannot return a value", path,
                        ret.value().span()).withExpected(null, type.display()));
            }
        } else if (ret.value() == null) {
            out.add(Diagnostic.of("E0405", "return needs a value of type " + returnType.display(), path, ret.span())
                    .withExpected(returnType.display(), null));
        } else {
            expect(ret.value(), returnType);
        }
    }

    private void assign(Ast.Assign assign) {
        Ast.Expr target = assign.target();
        if (target instanceof Ast.Name name) {
            Local local = lookup(name.name());
            if (local != null) {
                program.exprTypes.put(name, local.type());
                if (!local.mutable()) {
                    out.add(Diagnostic.of("E0504", "'" + name.name() + "' cannot be reassigned", path,
                            name.span()));
                }
                expect(assign.value(), local.type());
            } else {
                Type type = infer(name, null);
                expect(assign.value(), type);
            }
            return;
        }
        Ast.Member member = (Ast.Member) target;
        if (member.target() instanceof Ast.SelfRef && selfMode == SelfMode.METHOD) {
            infer(member.target(), null);
            FieldInfo field = currentClass.field(member.name());
            if (field == null) {
                boolean isMethod = currentClass.method(member.name()) != null;
                out.add(Diagnostic.of(isMethod ? "E0504" : "E0501", isMethod
                        ? "a method cannot be assigned"
                        : "'" + currentClass.simpleName() + "' has no field '" + member.name() + "'", path,
                        member.nameSpan()));
                infer(assign.value(), null);
                return;
            }
            program.exprTypes.put(member, field.type());
            program.members.put(member, new MemberRef.FieldRef(currentClass.qualifiedName(), field));
            if (!field.mutable()) {
                out.add(Diagnostic.of("E0504", "field '" + field.name() + "' is declared with let and cannot be "
                        + "reassigned", path, member.span()));
            }
            expect(assign.value(), field.type());
            return;
        }
        Type type = infer(member, null);
        out.add(Diagnostic.of("E0505", "a field can only be assigned through self, inside its own class", path,
                member.span()));
        expect(assign.value(), type);
    }

    private void declare(String name, Span span, Local local) {
        if (lookup(name) != null) {
            out.add(Diagnostic.of("E0303", "'" + name + "' is already defined in this scope", path, span));
            return;
        }
        locals.peek().put(name, local);
    }

    private Local lookup(String name) {
        for (Map<String, Local> frame : locals) {
            Local local = frame.get(name);
            if (local != null) {
                return local;
            }
        }
        return null;
    }

    // ---- expressions

    /** Infers the type and reports E0401 when it cannot be placed where `expected` is required. */
    private Type expect(Ast.Expr expr, Type expected) {
        Type actual = infer(expr, expected);
        if (expected != null && !table.assignable(actual, expected)) {
            Diagnostic diagnostic = Diagnostic.of("E0401", "type mismatch", path, expr.span())
                    .withExpected(expected.display(), actual.display());
            if (expected.equals(Type.STRING) && actual.equals(Type.INT)) {
                diagnostic = diagnostic.withFix(expr.span(), receiverText(expr) + ".toString()");
            }
            out.add(diagnostic);
        }
        return actual;
    }

    /** The type of the expression. `expected` (may be null) only guides list literals. */
    private Type infer(Ast.Expr expr, Type expected) {
        Type type = inferUnrecorded(expr, expected);
        program.exprTypes.put(expr, type);
        return type;
    }

    private Type inferUnrecorded(Ast.Expr expr, Type expected) {
        if (expr instanceof Ast.IntLit) {
            return Type.INT;
        }
        if (expr instanceof Ast.StringLit) {
            return Type.STRING;
        }
        if (expr instanceof Ast.BoolLit) {
            return Type.BOOL;
        }
        if (expr instanceof Ast.Paren paren) {
            return infer(paren.inner(), expected);
        }
        if (expr instanceof Ast.SelfRef self) {
            return self(self);
        }
        if (expr instanceof Ast.Name name) {
            return name(name);
        }
        if (expr instanceof Ast.Member member) {
            return member(member);
        }
        if (expr instanceof Ast.Call call) {
            return call(call);
        }
        if (expr instanceof Ast.Construct construct) {
            return construct(construct);
        }
        if (expr instanceof Ast.ListLit list) {
            return list(list, expected);
        }
        if (expr instanceof Ast.Unary unary) {
            return unary(unary);
        }
        return binary((Ast.Binary) expr);
    }

    private Type self(Ast.SelfRef self) {
        switch (selfMode) {
            case FIELD_INIT -> {
                out.add(Diagnostic.of("E0509", "a field initializer cannot use self", path, self.span()));
                return new Type.ClassType(currentClass.qualifiedName());
            }
            case APP_ROOT -> {
                out.add(Diagnostic.of("E0603", "app root cannot use self", path, self.span()));
                return Type.UNKNOWN;
            }
            default -> {
                return new Type.ClassType(currentClass.qualifiedName());
            }
        }
    }

    private Type name(Ast.Name name) {
        Local local = lookup(name.name());
        if (local != null) {
            return local.type();
        }
        if (selfMode == SelfMode.METHOD && currentClass != null) {
            FieldInfo field = currentClass.field(name.name());
            boolean isMethod = currentClass.method(name.name()) != null;
            if (field != null || isMethod) {
                out.add(Diagnostic.of("E0304", "'" + name.name() + "' is a " + (isMethod ? "method" : "field")
                        + " of this class; write 'self." + name.name() + "'", path, name.span())
                        .withFix(name.span(), "self." + name.name()));
                return field != null ? field.type() : Type.UNKNOWN;
            }
        }
        out.add(Diagnostic.of("E0301", "unknown name '" + name.name() + "'", path, name.span()));
        return Type.UNKNOWN;
    }

    private Type member(Ast.Member member) {
        Type receiver = infer(member.target(), null);
        if (receiver instanceof Type.Unknown) {
            return Type.UNKNOWN;
        }
        MethodInfo method = null;
        if (receiver instanceof Type.ClassType classType) {
            ClassInfo info = table.lookup(classType.qualifiedName());
            FieldInfo field = info.field(member.name());
            if (field != null) {
                program.members.put(member, new MemberRef.FieldRef(info.qualifiedName(), field));
                return field.type();
            }
            method = info.method(member.name());
            if (method != null) {
                program.members.put(member, new MemberRef.MethodRef(info.qualifiedName(), false, method));
            }
        } else if (receiver instanceof Type.InterfaceType ifaceType) {
            method = table.lookupInterface(ifaceType.qualifiedName()).method(member.name());
            if (method != null) {
                program.members.put(member, new MemberRef.MethodRef(ifaceType.qualifiedName(), true, method));
            }
        } else {
            method = Builtins.method(receiver, member.name());
        }
        if (method == null) {
            out.add(Diagnostic.of("E0501", "'" + receiver.display() + "' has no member '" + member.name() + "'",
                    path, member.nameSpan()));
            return Type.UNKNOWN;
        }
        boolean userMethod = program.members.containsKey(member);
        if (userMethod && method.returnType().equals(Type.UNIT) && method.params().isEmpty()) {
            return Type.ACTION;
        }
        if (userMethod && method.returnType().equals(Type.UNIT) && method.params().size() == 1) {
            return new Type.HandlerType(method.params().get(0).type());
        }
        program.members.remove(member);
        out.add(Diagnostic.of("E0507", "only a method taking at most one argument and returning Unit can be "
                + "referenced without calling it", path, member.span()).withExpected(null, method.signature()));
        return Type.UNKNOWN;
    }

    private Type call(Ast.Call call) {
        Type receiver = infer(call.target(), null);
        MethodInfo method = null;
        if (receiver instanceof Type.ClassType classType) {
            ClassInfo info = table.lookup(classType.qualifiedName());
            method = info.method(call.name());
            if (method != null) {
                program.members.put(call, new MemberRef.MethodRef(info.qualifiedName(), false, method));
            } else if (info.field(call.name()) != null) {
                out.add(Diagnostic.of("E0508", "'" + call.name() + "' is a field, not a method", path,
                        call.argsSpan()));
                inferAll(call.args());
                return Type.UNKNOWN;
            }
        } else if (receiver instanceof Type.InterfaceType ifaceType) {
            InterfaceInfo info = table.lookupInterface(ifaceType.qualifiedName());
            method = info.method(call.name());
            if (method != null) {
                program.members.put(call, new MemberRef.MethodRef(info.qualifiedName(), true, method));
            }
        } else if (!(receiver instanceof Type.Unknown)) {
            method = Builtins.method(receiver, call.name());
            if (method != null) {
                program.members.put(call, new MemberRef.BuiltinMethod(receiver, method));
            }
        }
        if (method == null) {
            if (!(receiver instanceof Type.Unknown)) {
                out.add(Diagnostic.of("E0501", "'" + receiver.display() + "' has no method '" + call.name() + "'",
                        path, call.nameSpan()));
            }
            inferAll(call.args());
            return Type.UNKNOWN;
        }
        List<String> names = method.params().stream().map(ParamInfo::name).toList();
        List<Type> types = method.params().stream().map(ParamInfo::type).toList();
        matchNamed(call.args(), names, types, call.argsSpan(), "E0502",
                "arguments of '" + call.name() + "' must be exactly: ", "(", ")");
        return method.returnType();
    }

    private Type construct(Ast.Construct construct) {
        Type type = scope.resolveNamed(construct.type(), construct.typeSpan());
        if (!(type instanceof Type.ClassType classType)) {
            if (!(type instanceof Type.Unknown)) {
                out.add(Diagnostic.of("E0302", "'" + construct.type() + "' is not a class and cannot be "
                        + "constructed", path, construct.typeSpan()));
            }
            inferAll(construct.inits());
            return Type.UNKNOWN;
        }
        ClassInfo info = table.lookup(classType.qualifiedName());
        program.constructs.put(construct, info);
        List<FieldInfo> fields = info.constructorFields();
        List<String> names = fields.stream().map(FieldInfo::name).toList();
        List<Type> types = fields.stream().map(FieldInfo::type).toList();
        boolean empty = names.isEmpty();
        matchNamed(construct.inits(), names, types, construct.initsSpan(), "E0503",
                "'" + construct.type() + "' must be constructed with exactly: ", empty ? "{" : "{ ",
                empty ? "}" : " }");
        return type;
    }

    /** Checks `name: value` pairs against the declared names, which must appear all, once, in order. */
    private void matchNamed(List<Ast.Named> given, List<String> names, List<Type> types, Span listSpan,
            String code, String message, String open, String close) {
        List<String> givenNames = given.stream().map(Ast.Named::name).toList();
        if (givenNames.equals(names)) {
            for (int i = 0; i < given.size(); i++) {
                expect(given.get(i).value(), types.get(i));
            }
            return;
        }
        Diagnostic diagnostic = Diagnostic.of(code, message + (names.isEmpty() ? "nothing" : String.join(", ", names)),
                path, listSpan).withExpected(String.join(", ", names), String.join(", ", givenNames));
        boolean sameSet = givenNames.size() == names.size() && new HashSet<>(givenNames).equals(new HashSet<>(names));
        if (sameSet) {
            List<String> parts = new ArrayList<>();
            for (String name : names) {
                Ast.Named match = given.get(givenNames.indexOf(name));
                parts.add(name + ": " + text.slice(match.value().span()));
            }
            diagnostic = diagnostic.withFix(listSpan, open + String.join(", ", parts) + close);
        }
        out.add(diagnostic);
        for (Ast.Named named : given) {
            int index = names.indexOf(named.name());
            if (index >= 0) {
                expect(named.value(), types.get(index));
            } else {
                infer(named.value(), null);
            }
        }
    }

    private void inferAll(List<Ast.Named> values) {
        for (Ast.Named named : values) {
            infer(named.value(), null);
        }
    }

    private Type list(Ast.ListLit list, Type expected) {
        if (expected instanceof Type.ListType listType) {
            for (Ast.Expr item : list.items()) {
                expect(item, listType.elem());
            }
            return expected;
        }
        for (Ast.Expr item : list.items()) {
            infer(item, null);
        }
        if (expected == null) {
            out.add(Diagnostic.of("E0406", "the element type of this list cannot be determined here; assign it to "
                    + "a variable with a List type first", path, list.span()));
        } else if (!(expected instanceof Type.Unknown)) {
            out.add(Diagnostic.of("E0401", "type mismatch", path, list.span())
                    .withExpected(expected.display(), "List"));
        }
        return Type.UNKNOWN;
    }

    private Type unary(Ast.Unary unary) {
        Type required = unary.op() == Ast.UnaryOp.NEG ? Type.INT : Type.BOOL;
        Type operand = infer(unary.operand(), required);
        if (!table.assignable(operand, required)) {
            out.add(Diagnostic.of("E0402", "operator '" + unary.op().symbol + "' needs " + required.display(),
                    path, unary.operand().span()).withExpected(required.display(), operand.display()));
        }
        return required;
    }

    private Type binary(Ast.Binary binary) {
        BinaryOp op = binary.op();
        List<Type> types = new ArrayList<>();
        for (Ast.Expr operand : binary.operands()) {
            types.add(infer(operand, null));
        }
        boolean anyUnknown = types.stream().anyMatch(t -> t instanceof Type.Unknown);
        switch (op) {
            case ADD, SUB, MUL, DIV, REM -> {
                if (!anyUnknown && op == BinaryOp.ADD && types.stream().allMatch(Type.STRING::equals)) {
                    StringBuilder fix = new StringBuilder(receiverText(binary.operands().get(0)));
                    for (int i = 1; i < binary.operands().size(); i++) {
                        fix.append(".concat(other: ").append(text.slice(binary.operands().get(i).span())).append(")");
                    }
                    out.add(Diagnostic.of("E0402", "'+' does not join strings; use concat", path, binary.span())
                            .withExpected("Int", "String").withFix(binary.span(), fix.toString()));
                    return Type.STRING;
                }
                requireAll(binary, types, Type.INT);
                return Type.INT;
            }
            case LT, LE, GT, GE -> {
                requireAll(binary, types, Type.INT);
                return Type.BOOL;
            }
            case AND, OR -> {
                requireAll(binary, types, Type.BOOL);
                return Type.BOOL;
            }
            default -> {
                Type left = types.get(0);
                Type right = types.get(1);
                boolean comparable = left.equals(Type.INT) || left.equals(Type.BOOL) || left.equals(Type.STRING);
                if (!anyUnknown && (!comparable || !left.equals(right))) {
                    out.add(Diagnostic.of("E0402", "'" + op.symbol + "' compares two values of the same type "
                            + "(Int, Bool or String)", path, binary.span())
                            .withExpected(comparable ? left.display() : "Int, Bool or String",
                                    comparable ? right.display() : left.display()));
                }
                return Type.BOOL;
            }
        }
    }

    private void requireAll(Ast.Binary binary, List<Type> types, Type required) {
        for (int i = 0; i < types.size(); i++) {
            if (!table.assignable(types.get(i), required)) {
                out.add(Diagnostic.of("E0402", "operator '" + binary.op().symbol + "' needs " + required.display(),
                        path, binary.operands().get(i).span())
                        .withExpected(required.display(), types.get(i).display()));
                return;
            }
        }
    }

    /** The expression's source, parenthesized when a `.method()` could not be appended directly. */
    private String receiverText(Ast.Expr expr) {
        String source = text.slice(expr.span());
        return expr instanceof Ast.Binary || expr instanceof Ast.Unary ? "(" + source + ")" : source;
    }
}
