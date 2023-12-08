var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');

function initializeCoreMod() {
    return {
        'insertInjectionTarget': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraft.world.entity.LivingEntity',
                'methodName': 'm_147215_',
                'methodDesc': '(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V'
            },
            'transformer': function (node) {
                var instance = null;
                for (var i = 0; i < node.localVariables.size(); i++) {
                    var lvn = node.localVariables.get(i);
                    if (lvn.index === 0) {
                        instance = lvn;
                        break;
                    }
                }
                for (var i = 0; i < node.localVariables.size(); i++) {
                    var lvn = node.localVariables.get(i);
                    if (lvn.index === 3) {
                        lvn.start = instance.start;
                        break;
                    }
                }
                node.instructions.insert(instance.start, ASMAPI.listOf(
                    new InsnNode(Opcodes.ACONST_NULL),
                    new VarInsnNode(Opcodes.ASTORE, 3)
                ));
                ASMAPI.log('DEBUG', 'Expanded local variable scope for LivingEntity#forceAddEffect index 3');
                return node;
            }
        }
    }
}