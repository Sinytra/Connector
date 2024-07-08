package org.sinytra.connector.mod;

import org.objectweb.asm.tree.ClassNode;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Prepare Fabric Loader before game classes and mixins are loaded
 */
public class ConnectorBootstrap implements IMixinConfigPlugin {

    static {
        CrashReportUpgrade.registerCrashLogInfo();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !ConnectorEarlyLoader.hasEncounteredException();
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // TODO
//        if (mixinClassName.equals("dev.su5ed.sinytra.connector.mod.mixin.item.LateItemStackMixin")) {
//            String methodName = ASMAPI.mapMethod("m_41638_");
//            targetClass.methods.stream()
//                .filter(m -> m.name.equals(methodName) && m.desc.equals("(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;"))
//                .findFirst()
//                .ifPresent(method -> {
//                    for (AbstractInsnNode insn : method.instructions) {
//                        if (insn.getOpcode() == Opcodes.ARETURN) {
//                            method.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/google/common/collect/HashMultimap", "create", "(Lcom/google/common/collect/Multimap;)Lcom/google/common/collect/HashMultimap;", false));
//                        }
//                    }
//                });
//
//        }
    }

    // We don't need any of the mixin stuff
    //@formatter:off
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() {return null;}
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() {return null;}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    //@formatter:on
}
