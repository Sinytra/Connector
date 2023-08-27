var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');

function initializeCoreMod() {
    return {
        'injectFabricASM': {
            'target': {
                'type': 'METHOD',
                'class': 'com.chocohead.mm.Plugin',
                'methodName': 'fishAddURL',
                'methodDesc': '()Ljava/util/function/Consumer;'
            },
            'transformer': function (node) {
                var insns = new InsnList();
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, 'dev/su5ed/sinytra/connector/service/FabricASMFixer', 'fishAddURL', '()Ljava/util/function/Consumer;'));
                insns.add(new InsnNode(Opcodes.ARETURN));
                node.instructions.insert(insns);
                ASMAPI.log('DEBUG', 'Injected fishAddURL hook into FabricASM plugin');
                return node;
            }
        },
        'renameGeneratedMixinClassName': {
            'target': {
                'type': 'METHOD',
                'class': 'com.chocohead.mm.Plugin$1',
                'methodName': 'generate',
                'methodDesc': '(Ljava/lang/String;Ljava/util/Collection;)V'
            },
            'transformer': function (node) {
                var insns = new InsnList();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, 'dev/su5ed/sinytra/connector/service/FabricASMFixer', 'flattenMixinClass', '(Ljava/lang/String;)Ljava/lang/String;'));
                insns.add(new VarInsnNode(Opcodes.ASTORE, 1));
                node.instructions.insert(insns);
                ASMAPI.log('DEBUG', 'Injected flattenMixinClass modifier into FabricASM Plugin$1');
                return node;
            }
        },
        'permitEnumSubclass': {
            'target': {
                'type': 'METHOD',
                'class': 'com.chocohead.mm.EnumSubclasser',
                'methodName': 'defineAnonymousSubclass',
                'methodDesc': '(Lorg/objectweb/asm/tree/ClassNode;Lcom/chocohead/mm/api/EnumAdder$EnumAddition;Ljava/lang/String;Ljava/lang/String;)[B'
            },
            'transformer': function (node) {
                var insns = new InsnList();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, 'dev/su5ed/sinytra/connector/service/FabricASMFixer', 'permitEnumSubclass', '(Lorg/objectweb/asm/tree/ClassNode;Ljava/lang/String;)V'));
                node.instructions.insert(insns);
                ASMAPI.log('DEBUG', 'Injected permitEnumSubclass modifier into FabricASM EnumSubclasser');
                return node;
            }
        }
    }
}