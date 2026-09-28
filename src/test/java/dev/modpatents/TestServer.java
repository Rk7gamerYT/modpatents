package dev.modpatents;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * O servidor de teste do NeoForge sobe sem mundo. Carregamos o mundo durante o startup,
 * antes do primeiro tick, porque mods como o Mekanism quebram se ticarem sem overworld.
 */
@EventBusSubscriber(modid = ModPatents.MOD_ID)
final class TestServer {
    private TestServer() {
    }

    /** Prioridade máxima: mods como o Project MMO também leem o mundo no ServerStartingEvent. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onServerStarting(ServerStartingEvent event) {
        ensureWorld(event.getServer());
    }

    static void ensureWorld(MinecraftServer server) {
        if (server.overworld() != null) {
            return;
        }
        try {
            Method loadLevel = MinecraftServer.class.getDeclaredMethod("loadLevel");
            loadLevel.setAccessible(true);
            loadLevel.invoke(server);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Roda o teste na thread do servidor, como no jogo de verdade. Nunca espera mais que 2 minutos. */
    static void onServer(MinecraftServer server, Runnable body) {
        try {
            server.submit(() -> {
                ensureWorld(server);
                body.run();
            }).get(2, TimeUnit.MINUTES);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) throw error;
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException(e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("o servidor de teste não respondeu (provavelmente crashou; veja build/minecraft-junit/crash-reports)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
