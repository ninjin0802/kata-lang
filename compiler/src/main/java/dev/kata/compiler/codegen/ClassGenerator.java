package dev.kata.compiler.codegen;

import dev.kata.compiler.check.CheckedProgram;
import dev.kata.compiler.check.ClassTable.ClassInfo;
import dev.kata.compiler.check.ClassTable.FieldInfo;
import dev.kata.compiler.check.ClassTable.MethodInfo;
import dev.kata.compiler.check.Type;
import dev.kata.compiler.syntax.Ast;
import java.util.LinkedHashMap;
import java.util.List;
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
            for (Ast.EnumDecl decl : file.enums()) {
                generateEnum(classes, program.enumOf(decl), sourceFile);
            }
            if (file.app() != null && program.rootExpr() != null) {
                classes.put(Names.MAIN_ACTIVITY, AppGenerator.generate(program, sourceFile));
            }
        }
        return classes;
    }

    /** An enum is an abstract class; each variant is a final subclass holding its fields (spec 06 #enum-mapping). */
    private static void generateEnum(Map<String, byte[]> classes, dev.kata.compiler.check.ClassTable.EnumInfo info,
            String sourceFile) {
        String base = Names.internal(info.qualifiedName());
        ClassWriter writer = newWriter();
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER, base, null,
                Names.OBJECT, null);
        writer.visitSource(sourceFile, null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PROTECTED, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, Names.OBJECT, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        writer.visitEnd();
        classes.put(base, writer.toByteArray());

        for (dev.kata.compiler.check.ClassTable.VariantInfo variant : info.variants()) {
            String name = Names.variant(info.qualifiedName(), variant.name());
            ClassWriter variantWriter = newWriter();
            variantWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, name, null,
                    base, null);
            variantWriter.visitSource(sourceFile, null);
            StringBuilder descriptor = new StringBuilder("(");
            for (FieldInfo field : variant.fields()) {
                variantWriter.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, field.name(),
                        Names.descriptor(field.type()), null, null).visitEnd();
                descriptor.append(Names.descriptor(field.type()));
            }
            MethodVisitor constructor = variantWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                    descriptor.append(")V").toString(), null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, base, "<init>", "()V", false);
            int slot = 1;
            for (FieldInfo field : variant.fields()) {
                Type type = field.type();
                constructor.visitVarInsn(Opcodes.ALOAD, 0);
                constructor.visitVarInsn(type.equals(Type.INT) ? Opcodes.LLOAD : type.equals(Type.FLOAT) ? Opcodes.DLOAD
                        : type.equals(Type.BOOL) ? Opcodes.ILOAD : Opcodes.ALOAD, slot);
                constructor.visitFieldInsn(Opcodes.PUTFIELD, name, field.name(), Names.descriptor(type));
                slot += type.equals(Type.INT) || type.equals(Type.FLOAT) ? 2 : 1;
            }
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();
            variantWriter.visitEnd();
            classes.put(name, variantWriter.toByteArray());
        }
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

        List<MethodGenerator.PendingLambda> lambdas = new java.util.ArrayList<>();
        constructor(program, writer, decl, info, name, lambdas);

        for (Ast.MethodDecl method : decl.methods()) {
            MethodInfo methodInfo = info.method(method.name());
            MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                    Names.method(method.name()),
                    Names.methodDescriptor(methodInfo), null, null);
            mv.visitCode();
            MethodGenerator generator = new MethodGenerator(program, mv, 1, name, lambdas);
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
        // Function literals found so far; generating one may add more (a literal inside a literal).
        for (int i = 0; i < lambdas.size(); i++) {
            MethodGenerator.PendingLambda pending = lambdas.get(i);
            Ast.Lambda lambda = pending.lambda();
            MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                    pending.name(), pending.descriptor(), null, null);
            mv.visitCode();
            MethodGenerator generator = new MethodGenerator(program, mv, 1, name, lambdas);
            for (CheckedProgram.Capture capture : program.capturesOf(lambda)) {
                generator.declareParameter(capture.name(), capture.type());
            }
            for (Ast.Param param : lambda.params()) {
                generator.declareParameter(param.name(), program.typeOf(param.type()));
            }
            generator.body(lambda.body());
            if (program.typeOf(lambda.returnType()).equals(Type.UNIT)) {
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
            String name, List<MethodGenerator.PendingLambda> lambdas) {
        MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", Names.constructorDescriptor(info), null,
                null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, Names.OBJECT, "<init>", "()V", false);
        MethodGenerator generator = new MethodGenerator(program, mv, 1, name, lambdas);
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
