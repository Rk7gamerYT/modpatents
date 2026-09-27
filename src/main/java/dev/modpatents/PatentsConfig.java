package dev.modpatents;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Conteúdo do config/modpatents.json.
 *
 * Cada entrada de lista pode ser:
 *   "create"        -> todos os itens do mod create
 *   "mekanism*"     -> todos os mods cujo id começa com "mekanism"
 *   "create:wrench" -> um item específico ("create:*_casing" também funciona)
 *   "#c:ingots"     -> todos os itens de uma tag
 */
public final class PatentsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @SerializedName("sempre_liberados")
    public List<String> alwaysAllowed = new ArrayList<>(List.of("minecraft"));

    @SerializedName("bloqueados_para_todos")
    public List<String> blockedForAll = new ArrayList<>();

    @SerializedName("ignoram_bloqueio")
    public List<String> bypass = new ArrayList<>();

    /** Se true, o Crafter (autocraft do vanilla) só faz itens que estão em sempre_liberados. */
    @SerializedName("crafter_bloqueia_itens_de_mod")
    public boolean crafterBlocksModItems = true;

    /** {mod} = nome do mod, {item} = nome do item. */
    @SerializedName("mensagem_bloqueio")
    public String blockedMessage = "Você não tem permissão pra craftar itens de {mod}!";

    @SerializedName("jogadores")
    public Map<String, List<String>> players = new LinkedHashMap<>();

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("modpatents.json");
    }

    public static PatentsConfig createDefault() {
        PatentsConfig config = new PatentsConfig();
        for (int i = 1; i <= 6; i++) {
            config.players.put("Jogador" + i, new ArrayList<>());
        }
        return config;
    }

    /** Lê o arquivo; se não existir, cria um modelo. Lança exceção se o JSON estiver inválido. */
    public static PatentsConfig loadOrCreate() throws IOException {
        Path path = path();
        if (Files.notExists(path)) {
            PatentsConfig config = createDefault();
            config.save();
            ModPatents.LOGGER.info("[ModPatents] Criado arquivo modelo em {}", path);
            return config;
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        PatentsConfig config = GSON.fromJson(json, PatentsConfig.class);
        if (config == null) {
            throw new IOException("arquivo vazio");
        }
        config.fillNulls();
        return config;
    }

    public void save() throws IOException {
        Path path = path();
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void fillNulls() {
        if (alwaysAllowed == null) alwaysAllowed = new ArrayList<>();
        if (blockedForAll == null) blockedForAll = new ArrayList<>();
        if (bypass == null) bypass = new ArrayList<>();
        if (blockedMessage == null) blockedMessage = new PatentsConfig().blockedMessage;
        if (players == null) players = new LinkedHashMap<>();
        players.replaceAll((name, entries) -> entries == null ? new ArrayList<>() : new ArrayList<>(entries));
    }

    /**
     * Escreve config/modpatents_mods_disponiveis.txt com todos os ids de mod que têm itens,
     * pra facilitar o preenchimento do JSON.
     */
    public static void writeAvailableModsFile() {
        Map<String, Integer> itemCounts = new TreeMap<>();
        for (var key : BuiltInRegistries.ITEM.keySet()) {
            itemCounts.merge(key.getNamespace(), 1, Integer::sum);
        }

        StringBuilder out = new StringBuilder();
        out.append("# Gerado automaticamente pelo Mod Patents toda vez que o servidor abre.\n");
        out.append("# Use o id (primeira coluna) no config/modpatents.json.\n");
        out.append("# Dica: \"mekanism*\" pega mekanism, mekanismgenerators, mekanismtools...\n\n");
        itemCounts.forEach((modId, count) -> out.append(String.format("%-32s %-40s %d itens%n",
                modId, displayName(modId), count)));

        Path path = FMLPaths.CONFIGDIR.get().resolve("modpatents_mods_disponiveis.txt");
        try {
            Files.writeString(path, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            ModPatents.LOGGER.warn("[ModPatents] Não consegui escrever {}", path, e);
        }
    }

    public static String displayName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(modId);
    }
}
