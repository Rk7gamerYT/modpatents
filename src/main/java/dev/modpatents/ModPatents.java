package dev.modpatents;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

/**
 * Mod Patents: bloqueia crafts de itens de mods por jogador.
 * Roda só no servidor; os jogadores não precisam instalar.
 */
@Mod(ModPatents.MOD_ID)
public final class ModPatents {
    public static final String MOD_ID = "modpatents";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ModPatents() {
        NeoForge.EVENT_BUS.addListener(ModPatents::onServerStarting);
        NeoForge.EVENT_BUS.addListener(ModPatents::onRegisterCommands);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        PatentsConfig.writeAvailableModsFile();
        CraftRules.reload();
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        PatentsCommands.register(event.getDispatcher());
    }
}
