package dev.su5ed.sinytra.connector.transformer.patch;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

public interface Patch {
    Predicate<String> REDIRECT = PatchImpl.REDIRECT_ANN::equals;

    static Builder builder() {
        return new PatchImpl.BuilderImpl();
    }

    boolean apply(ClassNode classNode);

    interface Builder {
        Builder targetClass(String... targets);

        Builder targetMethod(String... targets);

        Builder targetMixinType(Predicate<String> annotationDescPredicate);

        Builder targetInjectionPoint(String target);

        Builder targetInjectionPoint(String value, String target);

        Builder modifyInjectionPoint(String target);

        Builder modifyParams(Consumer<List<Type>> operator);

        Builder modifyTarget(String... methods);

        Builder modifyVariableIndex(IntUnaryOperator operator);

        Builder disable();

        Patch build();
    }
}
