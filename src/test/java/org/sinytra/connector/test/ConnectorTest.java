package org.sinytra.connector.test;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod("testconnector")
public class ConnectorTest {
    public ConnectorTest() {
        MinecraftForge.EVENT_BUS.addListener((final ScreenEvent.Opening event) -> {
            if (event.getNewScreen() instanceof PauseScreen) {
                Minecraft.getInstance().setScreen(null);
                Minecraft.getInstance().mouseHandler.grabMouse();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((final ScreenEvent.Closing event) -> {
            if (event.getScreen() instanceof PauseScreen || event.getScreen() instanceof ReceivingLevelScreen) {
                LogUtils.getLogger().info("Screen reset, scheduling shutdown in 5 seconds");
                Executors.newSingleThreadScheduledExecutor().schedule(() -> System.exit(0), 5, TimeUnit.SECONDS);
            }
        });
    }
}
