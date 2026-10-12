package dev.kata.compiler.codegen;

import dev.kata.compiler.check.CheckedProgram;
import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.check.ClassTable.FieldInfo;
import dev.kata.compiler.check.ClassTable.MethodInfo;
import dev.kata.compiler.check.Type;
import dev.kata.compiler.syntax.Ast;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Generates JVM classes from a checked program (spec chapter 06). */
public final class ClassGenerator {
    private ClassGenerator() {}

    /** @return class bytes by internal name, e.g. app/counter/Counter */
    public static Map<String, byte[]> generate(CheckedProgram program) {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        for (Ast.File file : program.files()) {
            String sourceFile = file.path().substring(file.path().lastIndexOf('/') + 1);
            for (Ast.ClassDecl decl : file.classes()) {
                ClassInfo info = program.classOf(decl);
                classes.put(Names.internal(info.qualifiedName()), generateClass(program, decl, info, sourceFile));
            }
            if (file.app() != null && program.rootExpr() != null) {
                classes.put(Names.MAIN_ACTIVITY, AppGenerator.generate(program, sourceFile));
            }
        }
        return classes;
    }

    /** Frames are computed by ASM; kata never merges two different reference types, so Object is enough. */
    static ClassWriter newWriter() {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return Names.OBJECT;
            }
        };
    }

    private static byte[] generateClass(CheckedProgram program, Ast.ClassDecl decl, ClassInfo info,
            String sourceFile) {
        String name = Names.internal(info.qualifiedName());
        String[] interfaces = info.interfaces().stream().map(Names::internal).toArray(String[]::new);
        ClassWriter writer = newWriter();
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, name, null,
                Names.OBJECT, interfaces);
        writer.visitSource(sourceFile, null);

        for (FieldInfo field : info.fields()) {
            int access = Opcodes.ACC_PUBLIC | (field.mutable() ? 0 : Opcodes.ACC_FINAL);
            writer.visitField(access, field.name(), Names.descriptor(field.type()), null, null).visitEnd();
        }

        constructor(program, writer, decl, info, name);

        for (Ast.MethodDecl method : decl.methods()) {
            MethodInfo methodInfo = info.method(method.name());
            MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                    Names.method(method.name()),
                    Names.methodDescriptor(methodInfo), null, null);
            mv.visitCode();
            MethodGenerator generator = new MethodGenerator(program, mv, 1);
            for (int i = 0; i < method.params().size(); i++) {
                generator.declareParameter(method.params().get(i).name(), methodInfo.params().get(i).type());
            }
            generator.body(method.body());
            if (methodInfo.returnType().equals(Type.UNIT)) {
                mv.visitInsn(Opcodes.RETURN);
            }
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** One constructor taking the fields without an initializer; all fields are set in declaration order. */
    private static void constructor(CheckedProgram program, ClassWriter writer, Ast.ClassDecl decl, ClassInfo info,
            String name) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", Names.constructorDescriptor(info), null,
                null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Names.OBJECT, "<init>", "()V", false);
        MethodGenerator generator = new MethodGenerator(program, mv, 1);
        for (FieldInfo field : info.constructorFields()) {
            generator.declareParameter(field.name(), field.type());
        }
        for (Ast.FieldDecl field : decl.fields()) {
            FieldInfo fieldInfo = info.field(field.name());
            Label label = new Label();
            mv.visitLabel(label);
            mv.visitLineNumber(field.span().line(), label);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            if (field.init() != null) {
                generator.expr(field.init());
            } else {
                generator.loadLocal(field.name());
            }
            mv.visitFieldInsn(Opcodes.PUTFIELD, name, field.name(), Names.descriptor(fieldInfo.type()));
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
