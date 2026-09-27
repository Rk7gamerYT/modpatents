package dev.modpatents;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * /patents recarregar
 * /patents lista
 * /patents liberar <jogador> <entrada>
 * /patents remover <jogador> <entrada>
 * /patents testar <jogador>   (testa o item que você está segurando)
 *
 * /patentes é um apelido de /patents.
 */
public final class PatentsCommands {
    private static final SuggestionProvider<CommandSourceStack> PLAYERS = (ctx, builder) -> {
        TreeSet<String> names = new TreeSet<>(CraftRules.config().players.keySet());
        names.addAll(List.of(ctx.getSource().getServer().getPlayerNames()));
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> MOD_IDS = (ctx, builder) -> {
        TreeSet<String> ids = new TreeSet<>();
        BuiltInRegistries.ITEM.keySet().forEach(key -> ids.add(key.getNamespace()));
        return SharedSuggestionProvider.suggest(ids, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> PLAYER_ENTRIES = (ctx, builder) -> {
        String player = StringArgumentType.getString(ctx, "jogador");
        String key = findPlayerKey(CraftRules.config(), player);
        List<String> entries = key == null ? List.of() : CraftRules.config().players.get(key);
        return SharedSuggestionProvider.suggest(entries, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("patents")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("recarregar").executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("lista").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("liberar")
                        .then(Commands.argument("jogador", StringArgumentType.word()).suggests(PLAYERS)
                                .then(Commands.argument("entrada", StringArgumentType.greedyString()).suggests(MOD_IDS)
                                        .executes(ctx -> allow(ctx, true)))))
                .then(Commands.literal("remover")
                        .then(Commands.argument("jogador", StringArgumentType.word()).suggests(PLAYERS)
                                .then(Commands.argument("entrada", StringArgumentType.greedyString()).suggests(PLAYER_ENTRIES)
                                        .executes(ctx -> allow(ctx, false)))))
                .then(Commands.literal("testar")
                        .then(Commands.argument("jogador", StringArgumentType.word()).suggests(PLAYERS)
                                .executes(PatentsCommands::test))));

        dispatcher.register(Commands.literal("patentes")
                .requires(source -> source.hasPermission(2))
                .redirect(root));
    }

    private static int reload(CommandSourceStack source) {
        String error = CraftRules.reload();
        if (error != null) {
            source.sendFailure(Component.literal("Erro no modpatents.json (regras antigas mantidas): " + error));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Mod Patents recarregado.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        PatentsConfig config = CraftRules.config();
        source.sendSuccess(() -> Component.literal("Sempre liberados: " + join(config.alwaysAllowed)).withStyle(ChatFormatting.GRAY), false);
        if (!config.blockedForAll.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Bloqueados pra todos: " + join(config.blockedForAll)).withStyle(ChatFormatting.GRAY), false);
        }
        if (!config.bypass.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Ignoram bloqueio: " + join(config.bypass)).withStyle(ChatFormatting.GRAY), false);
        }
        for (Map.Entry<String, List<String>> entry : config.players.entrySet()) {
            source.sendSuccess(() -> Component.literal(entry.getKey()).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(": " + join(entry.getValue())).withStyle(ChatFormatting.WHITE)), false);
        }
        return config.players.size();
    }

    private static int allow(CommandContext<CommandSourceStack> ctx, boolean add) {
        CommandSourceStack source = ctx.getSource();
        if (CraftRules.lastLoadFailed()) {
            source.sendFailure(Component.literal("O modpatents.json está com erro. Corrija e use /patents recarregar antes."));
            return 0;
        }
        String player = StringArgumentType.getString(ctx, "jogador");
        String entry = StringArgumentType.getString(ctx, "entrada").trim().toLowerCase(Locale.ROOT);
        PatentsConfig config = CraftRules.config();

        String key = findPlayerKey(config, player);
        if (key == null) {
            if (!add) {
                source.sendFailure(Component.literal(player + " não está no config."));
                return 0;
            }
            key = player;
            config.players.put(key, new ArrayList<>());
        }
        List<String> entries = config.players.get(key);
        boolean changed = add ? !entries.contains(entry) && entries.add(entry) : entries.removeIf(e -> e.equalsIgnoreCase(entry));
        if (!changed) {
            source.sendFailure(Component.literal(add ? key + " já tem " + entry : key + " não tem " + entry));
            return 0;
        }

        try {
            config.save();
        } catch (IOException e) {
            ModPatents.LOGGER.error("[ModPatents] Erro salvando config", e);
            source.sendFailure(Component.literal("Mudança aplicada, mas não consegui salvar o arquivo: " + e.getMessage()));
        }
        CraftRules.apply(config);

        String finalKey = key;
        source.sendSuccess(() -> Component.literal((add ? "Liberado " : "Removido ") + entry + (add ? " para " : " de ") + finalKey)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ItemStack held = source.getPlayerOrException().getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Segure um item na mão pra testar."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "jogador");
        UUID id = resolveUuid(source, name);
        boolean allowed = CraftRules.canCraft(name, id, held);
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        source.sendSuccess(() -> Component.literal(name + (allowed ? " PODE " : " NÃO PODE ") + "craftar " + itemId)
                .withStyle(allowed ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return allowed ? 1 : 0;
    }

    private static UUID resolveUuid(CommandSourceStack source, String name) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        var cache = source.getServer().getProfileCache();
        return cache == null ? new UUID(0, 0) : cache.get(name).map(GameProfile::getId).orElse(new UUID(0, 0));
    }

    /** Acha a chave do jogador no mapa ignorando maiúsculas/minúsculas. */
    static String findPlayerKey(PatentsConfig config, String player) {
        for (String key : config.players.keySet()) {
            if (key.equalsIgnoreCase(player)) {
                return key;
            }
        }
        return null;
    }

    private static String join(List<String> entries) {
        return entries.isEmpty() ? "(nada)" : String.join(", ", entries);
    }
}
