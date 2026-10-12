package dev.kata.compiler.codegen;

import dev.kata.compiler.check.CheckedProgram;
import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.check.ClassTable.MethodInfo;
import dev.kata.compiler.check.Flow;
import dev.kata.compiler.check.MemberRef;
import dev.kata.compiler.check.Type;
import dev.kata.compiler.syntax.Ast;
import dev.kata.compiler.syntax.Ast.BinaryOp;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Emits the bytecode of one method body (spec 06 #expr-mapping). */
final class MethodGenerator {
    private record Local(int slot, Type type) {}

    private static final Handle LAMBDA_BOOTSTRAP = new Handle(Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory", "metafactory",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;", false);

    private final CheckedProgram program;
    private final MethodVisitor mv;
    private final Deque<Map<String, Local>> scopes = new ArrayDeque<>();
    private int nextSlot;

    /** @param firstSlot the first free local slot (1 for an instance method: slot 0 is self) */
    MethodGenerator(CheckedProgram program, MethodVisitor mv, int firstSlot) {
        this.program = program;
        this.mv = mv;
        this.nextSlot = firstSlot;
        scopes.push(new HashMap<>());
    }

    void declareParameter(String name, Type type) {
        declare(name, type);
    }

    void body(Ast.Block block) {
        block(block);
    }

    // ---- locals

    /** Slots are never reused, so a slot always holds one type. */
    private Local declare(String name, Type type) {
        Local local = new Local(nextSlot, type);
        nextSlot += type.equals(Type.INT) ? 2 : 1;
        scopes.peek().put(name, local);
        return local;
    }

    private Local lookup(String name) {
        for (Map<String, Local> scope : scopes) {
            Local local = scope.get(name);
            if (local != null) {
                return local;
            }
        }
        throw new IllegalStateException("no local named " + name);
    }

    void loadLocal(String name) {
        Local local = lookup(name);
        mv.visitVarInsn(loadOpcode(local.type()), local.slot());
    }

    private void store(Local local) {
        mv.visitVarInsn(storeOpcode(local.type()), local.slot());
    }

    private static int loadOpcode(Type type) {
        return type.equals(Type.INT) ? Opcodes.LLOAD : type.equals(Type.BOOL) ? Opcodes.ILOAD : Opcodes.ALOAD;
    }

    private static int storeOpcode(Type type) {
        return type.equals(Type.INT) ? Opcodes.LSTORE : type.equals(Type.BOOL) ? Opcodes.ISTORE : Opcodes.ASTORE;
    }

    // ---- statements

    private void block(Ast.Block block) {
        scopes.push(new HashMap<>());
        for (Ast.Stmt stmt : block.stmts()) {
            stmt(stmt);
        }
        scopes.pop();
    }

    private void stmt(Ast.Stmt stmt) {
        Label line = new Label();
        mv.visitLabel(line);
        mv.visitLineNumber(stmt.span().line(), line);
        if (stmt instanceof Ast.Let let) {
            expr(let.value());
            store(declare(let.name(), program.typeOf(let.type())));
        } else if (stmt instanceof Ast.Var var) {
            expr(var.value());
            store(declare(var.name(), program.typeOf(var.type())));
        } else if (stmt instanceof Ast.Assign assign) {
            if (assign.target() instanceof Ast.Name name) {
                expr(assign.value());
                store(lookup(name.name()));
            } else {
                Ast.Member member = (Ast.Member) assign.target();
                MemberRef.FieldRef field = (MemberRef.FieldRef) program.memberOf(member);
                expr(member.target());
                expr(assign.value());
                mv.visitFieldInsn(Opcodes.PUTFIELD, Names.internal(field.owner()), field.field().name(),
                        Names.descriptor(field.field().type()));
            }
        } else if (stmt instanceof Ast.Return ret) {
            if (ret.value() == null) {
                mv.visitInsn(Opcodes.RETURN);
            } else {
                expr(ret.value());
                Type type = program.typeOf(ret.value());
                mv.visitInsn(type.equals(Type.INT) ? Opcodes.LRETURN
                        : type.equals(Type.BOOL) ? Opcodes.IRETURN : Opcodes.ARETURN);
            }
        } else if (stmt instanceof Ast.If branch) {
            ifStmt(branch);
        } else if (stmt instanceof Ast.While loop) {
            Label start = new Label();
            Label end = new Label();
            mv.visitLabel(start);
            expr(loop.cond());
            mv.visitJumpInsn(Opcodes.IFEQ, end);
            block(loop.body());
            mv.visitJumpInsn(Opcodes.GOTO, start);
            mv.visitLabel(end);
        } else if (stmt instanceof Ast.For loop) {
            forStmt(loop);
        } else if (stmt instanceof Ast.Block nested) {
            block(nested);
        } else {
            expr(((Ast.ExprStmt) stmt).expr());
        }
    }

    private void ifStmt(Ast.If branch) {
        Label otherwise = new Label();
        Label end = new Label();
        expr(branch.cond());
        mv.visitJumpInsn(Opcodes.IFEQ, otherwise);
        block(branch.then());
        if (branch.orElse() == null) {
            mv.visitLabel(otherwise);
            return;
        }
        if (!Flow.terminates(branch.then())) {
            mv.visitJumpInsn(Opcodes.GOTO, end);
        }
        mv.visitLabel(otherwise);
        stmt(branch.orElse());
        mv.visitLabel(end);
    }

    /** The list is evaluated once; an index walks from 0 to size. */
    private void forStmt(Ast.For loop) {
        Type element = program.typeOf(loop.type());
        scopes.push(new HashMap<>());
        Local list = declare("<list>", new Type.ListType(element));
        Local index = declare("<index>", Type.INT);
        Local item = declare(loop.name(), element);
        expr(loop.iterable());
        store(list);
        mv.visitInsn(Opcodes.LCONST_0);
        store(index);
        Label start = new Label();
        Label end = new Label();
        mv.visitLabel(start);
        mv.visitVarInsn(Opcodes.LLOAD, index.slot());
        mv.visitVarInsn(Opcodes.ALOAD, list.slot());
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.KLIST, "size", "()J", false);
        mv.visitInsn(Opcodes.LCMP);
        mv.visitJumpInsn(Opcodes.IFGE, end);
        mv.visitVarInsn(Opcodes.ALOAD, list.slot());
        mv.visitVarInsn(Opcodes.LLOAD, index.slot());
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.KLIST, "get", "(J)Ljava/lang/Object;", false);
        unbox(element);
        store(item);
        block(loop.body());
        mv.visitVarInsn(Opcodes.LLOAD, index.slot());
        mv.visitInsn(Opcodes.LCONST_1);
        mv.visitInsn(Opcodes.LADD);
        store(index);
        mv.visitJumpInsn(Opcodes.GOTO, start);
        mv.visitLabel(end);
        scopes.pop();
    }

    // ---- expressions

    void expr(Ast.Expr expr) {
        if (expr instanceof Ast.IntLit literal) {
            mv.visitLdcInsn(literal.value());
        } else if (expr instanceof Ast.StringLit literal) {
            mv.visitLdcInsn(literal.value());
        } else if (expr instanceof Ast.BoolLit literal) {
            mv.visitInsn(literal.value() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        } else if (expr instanceof Ast.SelfRef) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
        } else if (expr instanceof Ast.Name name) {
            loadLocal(name.name());
        } else if (expr instanceof Ast.Paren paren) {
            expr(paren.inner());
        } else if (expr instanceof Ast.Member member) {
            member(member);
        } else if (expr instanceof Ast.Call call) {
            call(call);
        } else if (expr instanceof Ast.Construct construct) {
            ClassInfo info = program.classOf(construct);
            String name = Names.internal(info.qualifiedName());
            mv.visitTypeInsn(Opcodes.NEW, name);
            mv.visitInsn(Opcodes.DUP);
            for (Ast.Named init : construct.inits()) {
                expr(init.value());
            }
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", Names.constructorDescriptor(info), false);
        } else if (expr instanceof Ast.ListLit list) {
            pushInt(list.items().size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, Names.OBJECT);
            for (int i = 0; i < list.items().size(); i++) {
                mv.visitInsn(Opcodes.DUP);
                pushInt(i);
                expr(list.items().get(i));
                box(program.typeOf(list.items().get(i)));
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, Names.KLIST, "of", "([Ljava/lang/Object;)L" + Names.KLIST + ";",
                    false);
        } else if (expr instanceof Ast.Unary unary) {
            expr(unary.operand());
            if (unary.op() == Ast.UnaryOp.NEG) {
                mv.visitInsn(Opcodes.LNEG);
            } else {
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IXOR);
            }
        } else {
            binary((Ast.Binary) expr);
        }
    }

    private void member(Ast.Member member) {
        MemberRef ref = program.memberOf(member);
        expr(member.target());
        if (ref instanceof MemberRef.FieldRef field) {
            mv.visitFieldInsn(Opcodes.GETFIELD, Names.internal(field.owner()), field.field().name(),
                    Names.descriptor(field.field().type()));
            return;
        }
        // A method reference: bind the receiver now and produce an Action or a Handler.
        MemberRef.MethodRef method = (MemberRef.MethodRef) ref;
        String owner = Names.internal(method.owner());
        MethodInfo info = method.method();
        Handle target = new Handle(method.ownerIsInterface() ? Opcodes.H_INVOKEINTERFACE : Opcodes.H_INVOKEVIRTUAL,
                owner, info.name(), Names.methodDescriptor(info), method.ownerIsInterface());
        if (info.params().isEmpty()) {
            mv.visitInvokeDynamicInsn("run", "(L" + owner + ";)L" + Names.ACTION + ";", LAMBDA_BOOTSTRAP,
                    org.objectweb.asm.Type.getType("()V"), target, org.objectweb.asm.Type.getType("()V"));
        } else {
            String boxed = Names.boxedDescriptor(info.params().get(0).type());
            mv.visitInvokeDynamicInsn("run", "(L" + owner + ";)L" + Names.HANDLER + ";", LAMBDA_BOOTSTRAP,
                    org.objectweb.asm.Type.getType("(Ljava/lang/Object;)V"), target,
                    org.objectweb.asm.Type.getType("(" + boxed + ")V"));
        }
    }

    private void call(Ast.Call call) {
        MemberRef ref = program.memberOf(call);
        expr(call.target());
        if (ref instanceof MemberRef.MethodRef method) {
            for (Ast.Named arg : call.args()) {
                expr(arg.value());
            }
            mv.visitMethodInsn(method.ownerIsInterface() ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
                    Names.internal(method.owner()), method.method().name(),
                    Names.methodDescriptor(method.method()), method.ownerIsInterface());
            return;
        }
        MemberRef.BuiltinMethod builtin = (MemberRef.BuiltinMethod) ref;
        String name = builtin.method().name();
        Type receiver = builtin.receiver();
        if (receiver.equals(Type.INT)) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "toString", "(J)Ljava/lang/String;", false);
        } else if (receiver.equals(Type.STRING) && name.equals("concat")) {
            expr(call.args().get(0).value());
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.STRING, "concat",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);
        } else if (receiver.equals(Type.STRING)) {
            // length(): the number of code points
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.STRING, "length", "()I", false);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitInsn(Opcodes.SWAP);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.STRING, "codePointCount", "(II)I", false);
            mv.visitInsn(Opcodes.I2L);
        } else if (name.equals("size")) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.KLIST, "size", "()J", false);
        } else if (name.equals("get")) {
            expr(call.args().get(0).value());
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.KLIST, "get", "(J)Ljava/lang/Object;", false);
            unbox(((Type.ListType) receiver).elem());
        } else {
            expr(call.args().get(0).value());
            box(program.typeOf(call.args().get(0).value()));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.KLIST, "append",
                    "(Ljava/lang/Object;)L" + Names.KLIST + ";", false);
        }
    }

    private void binary(Ast.Binary binary) {
        BinaryOp op = binary.op();
        List<Ast.Expr> operands = binary.operands();
        switch (op) {
            case ADD, SUB, MUL, DIV, REM -> {
                expr(operands.get(0));
                for (int i = 1; i < operands.size(); i++) {
                    expr(operands.get(i));
                    mv.visitInsn(switch (op) {
                        case ADD -> Opcodes.LADD;
                        case SUB -> Opcodes.LSUB;
                        case MUL -> Opcodes.LMUL;
                        case DIV -> Opcodes.LDIV;
                        default -> Opcodes.LREM;
                    });
                }
            }
            case AND, OR -> {
                // Short-circuit: stop at the first operand that decides the result.
                boolean isAnd = op == BinaryOp.AND;
                Label decided = new Label();
                Label end = new Label();
                for (Ast.Expr operand : operands) {
                    expr(operand);
                    mv.visitJumpInsn(isAnd ? Opcodes.IFEQ : Opcodes.IFNE, decided);
                }
                mv.visitInsn(isAnd ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                mv.visitJumpInsn(Opcodes.GOTO, end);
                mv.visitLabel(decided);
                mv.visitInsn(isAnd ? Opcodes.ICONST_0 : Opcodes.ICONST_1);
                mv.visitLabel(end);
            }
            case LT, LE, GT, GE -> {
                expr(operands.get(0));
                expr(operands.get(1));
                mv.visitInsn(Opcodes.LCMP);
                booleanFromJump(switch (op) {
                    case LT -> Opcodes.IFLT;
                    case LE -> Opcodes.IFLE;
                    case GT -> Opcodes.IFGT;
                    default -> Opcodes.IFGE;
                });
            }
            default -> {
                boolean equal = op == BinaryOp.EQ;
                Type type = program.typeOf(operands.get(0));
                expr(operands.get(0));
                expr(operands.get(1));
                if (type.equals(Type.INT)) {
                    mv.visitInsn(Opcodes.LCMP);
                    booleanFromJump(equal ? Opcodes.IFEQ : Opcodes.IFNE);
                } else if (type.equals(Type.BOOL)) {
                    booleanFromJump(equal ? Opcodes.IF_ICMPEQ : Opcodes.IF_ICMPNE);
                } else {
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Names.STRING, "equals", "(Ljava/lang/Object;)Z", false);
                    if (!equal) {
                        mv.visitInsn(Opcodes.ICONST_1);
                        mv.visitInsn(Opcodes.IXOR);
                    }
                }
            }
        }
    }

    /** Leaves 1 when the jump is taken and 0 otherwise. */
    private void booleanFromJump(int jumpOpcode) {
        Label taken = new Label();
        Label end = new Label();
        mv.visitJumpInsn(jumpOpcode, taken);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitJumpInsn(Opcodes.GOTO, end);
        mv.visitLabel(taken);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitLabel(end);
    }

    private void pushInt(int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    /** Int and Bool are stored in lists as Long and Boolean. */
    private void box(Type type) {
        if (type.equals(Type.INT)) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
        } else if (type.equals(Type.BOOL)) {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;",
                    false);
        }
    }

    private void unbox(Type type) {
        if (type.equals(Type.INT)) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
        } else if (type.equals(Type.BOOL)) {
            mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
        } else {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Names.internal(type));
        }
    }
}
