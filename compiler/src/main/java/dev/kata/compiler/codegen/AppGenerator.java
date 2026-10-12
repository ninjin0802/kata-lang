package dev.kata.compiler.codegen;

import dev.kata.compiler.check.CheckedProgram;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Generates kata.gen.MainActivity from the app declaration (spec 06 #app-mapping). */
final class AppGenerator {
    private AppGenerator() {}

    static byte[] generate(CheckedProgram program, String sourceFile) {
        ClassWriter writer = ClassGenerator.newWriter();
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, Names.MAIN_ACTIVITY,
                null, Names.KATA_ACTIVITY, null);
        writer.visitSource(sourceFile, null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, Names.KATA_ACTIVITY, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();

        MethodVisitor create = writer.visitMethod(Opcodes.ACC_PROTECTED, "createRoot", "()L" + Names.SCREEN + ";",
                null, null);
        create.visitCode();
        Label label = new Label();
        create.visitLabel(label);
        create.visitLineNumber(program.rootExpr().span().line(), label);
        new MethodGenerator(program, create, 1, Names.MAIN_ACTIVITY, new java.util.ArrayList<>())
                .expr(program.rootExpr());
        create.visitInsn(Opcodes.ARETURN);
        create.visitMaxs(0, 0);
        create.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
