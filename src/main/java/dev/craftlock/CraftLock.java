package dev.craftlock;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

/**
 * CraftLock: bloqueia crafts de itens de mods por jogador.
 * Roda só no servidor; os jogadores não precisam instalar.
 */
@Mod(CraftLock.MOD_ID)
public final class CraftLock {
    public static final String MOD_ID = "craftlock";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CraftLock() {
        NeoForge.EVENT_BUS.addListener(CraftLock::onServerStarting);
        NeoForge.EVENT_BUS.addListener(CraftLock::onRegisterCommands);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        CraftLockConfig.writeAvailableModsFile();
        CraftRules.reload();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CraftLockCommands.register(event.getDispatcher());
    }
}
