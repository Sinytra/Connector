var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');

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
        },
        'insertBooleanInjectionTarget': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraft.world.level.BaseSpawner',
                'methodName': 'm_151311_',
                'methodDesc': '(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V'
            },
            'transformer': function (node) {
                var targetLocal = null;
                for (var i = 0; i < node.localVariables.size(); i++) {
                    var lvn = node.localVariables.get(i);
                    if (lvn.desc === 'Z') {
                        targetLocal = lvn;
                        break;
                    }
                }

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
                    if (lvn === targetLocal) {
                        lvn.start = instance.start;
                        break;
                    }
                }

                var endLabel = null;
                for (var i = node.instructions.size() - 1; i > 0; i--) {
                    if (node.instructions.get(i) instanceof LabelNode) {
                        endLabel = node.instructions.get(i);
                        break;
                    }
                }

                for (var i = 0; i < node.localVariables.size(); i++) {
                    var lvn = node.localVariables.get(i);
                    if (lvn === targetLocal) {
                        lvn.end = endLabel;
                        break;
                    }
                }

                node.instructions.insert(instance.start, ASMAPI.listOf(
                    new InsnNode(Opcodes.ICONST_0),
                    new VarInsnNode(Opcodes.ISTORE, targetLocal.index)
                ));
                ASMAPI.log('DEBUG', 'Expanded local variable scope for BaseSpawner#serverTick index 3');
                return node;
            }
        }
    }
}