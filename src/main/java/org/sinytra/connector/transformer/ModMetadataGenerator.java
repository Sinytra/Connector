package org.sinytra.connector.transformer;

import net.minecraftforge.fart.api.Transformer;
import org.apache.commons.lang3.RandomStringUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.sinytra.connector.util.ConnectorUtil;

import java.util.Collection;
import java.util.List;

public class ModMetadataGenerator implements Transformer {
    private static final String MOD_ANNOTATION_DESC = "Lnet/neoforged/fml/common/Mod;";

    private final String modid;

    public ModMetadataGenerator(String modid) {
        this.modid = modid;
    }

    @Override
    public Collection<? extends Entry> getExtras() {
        // Generate FML mod class
        // Include a random string for uniqueness, just in case
        String className = "org/sinytra/generated/%s_%s/Entrypoint_%s".formatted(this.modid, RandomStringUtils.randomAlphabetic(5), this.modid);
        byte[] classData = generateFMLModEntrypoint(className);
        return List.of(ClassEntry.create(className + ".class", ConnectorUtil.ZIP_TIME, classData));
    }

    private byte[] generateFMLModEntrypoint(String className) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, className, null, "java/lang/Object", null);

        // Annotate the class with FML's @Mod annotation
        AnnotationVisitor modAnnotation = cw.visitAnnotation(MOD_ANNOTATION_DESC, true);
        modAnnotation.visit("value", this.modid);
        modAnnotation.visitEnd();

        // Add a default constructor to the class
        MethodVisitor constructor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        Label start = new Label();
        constructor.visitLabel(start);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        Label end = new Label();
        constructor.visitLabel(end);
        constructor.visitLocalVariable("this", "L" + className + ";", null, start, end, 0);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        cw.visitEnd();

        return cw.toByteArray();
    }
}
