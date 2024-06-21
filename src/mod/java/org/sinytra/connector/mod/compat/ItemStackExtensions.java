package org.sinytra.connector.mod.compat;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

public interface ItemStackExtensions {
    InteractionResult connector_useOn(UseOnContext context);
}
