var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');

function initializeCoreMod() {
    return {
        'updateItemUseStartTreshold': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraft.world.entity.LivingEntity',
                'methodName': 'm_6672_',
                'methodDesc': '(Lnet/minecraft/world/InteractionHand;)V'
            },
            'transformer': function (node) {
                var insn = ASMAPI.findFirstInstruction(node, Opcodes.IFGT);
                if (insn != null && insn.previous.getOpcode() === Opcodes.ILOAD && insn.next.getOpcode() === Opcodes.RETURN) {
                    node.instructions.set(insn, new JumpInsnNode(Opcodes.IFGE, insn.label));
                    ASMAPI.log('DEBUG', 'Updated onItemUseStart duration threshold');
                }
                return node;
            }
        }
    }
}