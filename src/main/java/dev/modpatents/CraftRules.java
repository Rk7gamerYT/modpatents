package dev.modpatents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Versão "compilada" do config, usada nas checagens de craft.
 * Todo acesso acontece na thread do servidor.
 */
public final class CraftRules {
    private static final long MESSAGE_COOLDOWN_MS = 1500;

    private static CraftRules current = new CraftRules(new PatentsConfig());
    private static boolean lastLoadFailed = false;
    private static final Map<UUID, Long> lastMessageAt = new HashMap<>();
    private static boolean pmmoWarningLogged = false;
    /** Jogador cujo craft na bancada/inventário está sendo processado agora (os mixins dos menus cuidam dele). */
    private static final ThreadLocal<Player> playerCraft = new ThreadLocal<>();

    private final PatentsConfig config;
    private final List<Predicate<ItemStack>> alwaysAllowed;
    private final List<Predicate<ItemStack>> blockedForAll;
    private final List<ProficiencyRule> proficiencyRequirements;
    private final Set<String> bypass = new HashSet<>();
    /** Chave: nome em minúsculas ou UUID. */
    private final Map<String, List<Predicate<ItemStack>>> perPlayer = new HashMap<>();

    private CraftRules(PatentsConfig config) {
        this.config = config;
        this.alwaysAllowed = compileAll(config.alwaysAllowed);
        this.blockedForAll = compileAll(config.blockedForAll);
        this.proficiencyRequirements = compileProficiencies(config.proficiencyRequirements);
        config.bypass.forEach(name -> bypass.add(normalize(name)));
        config.players.forEach((name, entries) -> perPlayer.put(normalize(name), compileAll(entries)));
    }

    // ---------------------------------------------------------------- estado global

    public static PatentsConfig config() {
        return current.config;
    }

    public static boolean lastLoadFailed() {
        return lastLoadFailed;
    }

    /** Relê o JSON do disco. Se estiver inválido, mantém as regras anteriores e devolve a mensagem de erro. */
    public static String reload() {
        try {
            apply(PatentsConfig.loadOrCreate());
            lastLoadFailed = false;
            ModPatents.LOGGER.info("[ModPatents] Config carregado: {} jogador(es)", current.perPlayer.size());
            return null;
        } catch (Exception e) {
            lastLoadFailed = true;
            ModPatents.LOGGER.error("[ModPatents] Erro lendo {}: mantendo as regras anteriores", PatentsConfig.path(), e);
            return e.getMessage();
        }
    }

    /** Aplica um config já em memória (usado pelos comandos e pelos testes). */
    public static void apply(PatentsConfig config) {
        current = new CraftRules(config);
    }

    // ---------------------------------------------------------------- checagens

    public static boolean canCraft(String playerName, UUID playerId, ItemStack result) {
        CraftRules rules = current;
        String name = normalize(playerName);
        String id = playerId.toString();

        if (result.isEmpty() || rules.bypass.contains(name) || rules.bypass.contains(id)) {
            return true;
        }
        if (matchesAny(rules.blockedForAll, result)) {
            return false;
        }
        if (matchesAny(rules.alwaysAllowed, result)) {
            return true;
        }
        // o jogador pode aparecer pelo nome e/ou pelo UUID; vale a soma dos dois
        return matchesAny(rules.perPlayer.getOrDefault(name, List.of()), result)
                || matchesAny(rules.perPlayer.getOrDefault(id, List.of()), result);
    }

    /** Crafts sem jogador: Crafter do vanilla, autocraft e grades de craft de outros mods. */
    public static boolean canCraftAutomated(ItemStack result) {
        CraftRules rules = current;
        if (result.isEmpty()) {
            return true;
        }
        if (matchesAny(rules.blockedForAll, result)) {
            return false;
        }
        return !rules.config.autocraftBlocksModItems || matchesAny(rules.alwaysAllowed, result);
    }

    /**
     * Usado pelos mixins: devolve o resultado original se o jogador pode craftar,
     * ou ItemStack.EMPTY (e avisa o jogador) se não pode.
     * No cliente não faz nada; quem manda é o servidor.
     */
    public static ItemStack filter(Player player, ItemStack result) {
        if (!(player instanceof ServerPlayer serverPlayer) || result.isEmpty()) {
            return result;
        }
        if (!canCraft(serverPlayer.getGameProfile().getName(), serverPlayer.getUUID(), result)) {
            notifyBlocked(serverPlayer, result);
            return ItemStack.EMPTY;
        }

        CraftRules rules = current;
        String name = normalize(serverPlayer.getGameProfile().getName());
        String id = serverPlayer.getUUID().toString();
        // The explicit admin bypass retains its original meaning and skips both checks.
        if (rules.bypass.contains(name) || rules.bypass.contains(id)) return result;

        Map<String, Long> requirements = rules.proficiencyRequirements(result);
        if (requirements.isEmpty() || rules.playerMeetsProficiency(serverPlayer, requirements)) return result;

        notifyProficiencyBlocked(serverPlayer, result, requirements);
        return ItemStack.EMPTY;
    }

    public static ItemStack filterAutomated(ItemStack result) {
        return canCraftAutomated(result) ? result : ItemStack.EMPTY;
    }

    /** Marca que o código a seguir é um craft de jogador na bancada/inventário. Devolve o valor anterior para restaurar. */
    public static Player enterPlayerCraft(Player player) {
        Player previous = playerCraft.get();
        playerCraft.set(player);
        return previous;
    }

    public static void exitPlayerCraft(Player previous) {
        if (previous == null) {
            playerCraft.remove();
        } else {
            playerCraft.set(previous);
        }
    }

    /**
     * Chamado toda vez que uma receita de craft (shaped/shapeless) monta o resultado, venha de onde vier.
     * Se não é um craft de jogador na bancada, é autocraft ou a grade de algum outro mod: vale a regra de autocraft.
     */
    public static ItemStack filterRecipeResult(ItemStack result) {
        if (result.isEmpty() || playerCraft.get() != null || !isServerThread()) {
            return result;
        }
        return filterAutomated(result);
    }

    /** Só o servidor aplica regras; no singleplayer o cliente roda em outra thread e não é afetado. */
    private static boolean isServerThread() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread();
    }

    private static void notifyBlocked(ServerPlayer player, ItemStack result) {
        long now = System.currentTimeMillis();
        Long last = lastMessageAt.get(player.getUUID());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) {
            return;
        }
        lastMessageAt.put(player.getUUID(), now);

        String modId = BuiltInRegistries.ITEM.getKey(result.getItem()).getNamespace();
        String modName = PatentsConfig.displayName(modId);
        String text = current.config.blockedMessage
                .replace("{mod}", modName)
                .replace("{item}", result.getHoverName().getString());
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.RED), true);
        ModPatents.LOGGER.info("[ModPatents] {} tentou craftar {} ({}) e foi bloqueado",
                player.getGameProfile().getName(), BuiltInRegistries.ITEM.getKey(result.getItem()), modName);
    }

    private static void notifyProficiencyBlocked(ServerPlayer player, ItemStack result,
                                                 Map<String, Long> requirements) {
        long now = System.currentTimeMillis();
        Long last = lastMessageAt.get(player.getUUID());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return;
        lastMessageAt.put(player.getUUID(), now);

        String required = requirements.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        String text = current.config.proficiencyBlockedMessage
                .replace("{requisitos}", required)
                .replace("{item}", result.getHoverName().getString());
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }

    /** Ask PMMO to evaluate levels, including any PMMO skill groups configured by the pack. */
    private boolean playerMeetsProficiency(ServerPlayer player, Map<String, Long> requirements) {
        if (!ModList.get().isLoaded("pmmo")) return false;
        try {
            Class<?> coreClass = Class.forName("harmonised.pmmo.core.Core");
            Method getCore = coreClass.getMethod("get", Level.class);
            Object core = getCore.invoke(null, player.level());
            Method check = coreClass.getMethod("doesPlayerMeetReq", java.util.UUID.class, Map.class);
            return Boolean.TRUE.equals(check.invoke(core, player.getUUID(), requirements));
        } catch (ReflectiveOperationException | LinkageError e) {
            if (!pmmoWarningLogged) {
                pmmoWarningLogged = true;
                ModPatents.LOGGER.error("[ModPatents] Não consegui consultar os requisitos do PMMO; crafts com proficiência ficam bloqueados.", e);
            }
            return false;
        }
    }

    // ---------------------------------------------------------------- compilação das entradas

    private static boolean matchesAny(List<Predicate<ItemStack>> predicates, ItemStack stack) {
        for (Predicate<ItemStack> predicate : predicates) {
            if (predicate.test(stack)) {
                return true;
            }
        }
        return false;
    }

    private static List<Predicate<ItemStack>> compileAll(List<String> entries) {
        List<Predicate<ItemStack>> out = new ArrayList<>();
        for (String entry : entries) {
            Predicate<ItemStack> predicate = compile(entry);
            if (predicate != null) {
                out.add(predicate);
            }
        }
        return out;
    }

    private static List<ProficiencyRule> compileProficiencies(Map<String, Map<String, Long>> entries) {
        List<ProficiencyRule> out = new ArrayList<>();
        if (entries == null) return out;
        entries.forEach((itemPattern, skills) -> {
            Predicate<ItemStack> matcher = compile(itemPattern);
            if (matcher == null || skills == null || skills.isEmpty()) return;
            Map<String, Long> requirements = new HashMap<>();
            skills.forEach((skill, level) -> {
                if (skill != null && !skill.isBlank() && level != null && level > 0) {
                    requirements.merge(skill.trim(), level, Math::max);
                }
            });
            if (!requirements.isEmpty()) out.add(new ProficiencyRule(matcher, Map.copyOf(requirements)));
        });
        return out;
    }

    private Map<String, Long> proficiencyRequirements(ItemStack stack) {
        Map<String, Long> requirements = new HashMap<>();
        for (ProficiencyRule rule : proficiencyRequirements) {
            if (rule.matcher().test(stack)) {
                rule.requirements().forEach((skill, level) -> requirements.merge(skill, level, Math::max));
            }
        }
        return requirements;
    }

    private record ProficiencyRule(Predicate<ItemStack> matcher, Map<String, Long> requirements) {}

    private static Predicate<ItemStack> compile(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String entry = raw.trim().toLowerCase(Locale.ROOT);

        if (entry.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(entry.substring(1));
            if (tagId == null) {
                ModPatents.LOGGER.warn("[ModPatents] Tag inválida ignorada: {}", raw);
                return null;
            }
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            return stack -> stack.is(tag);
        }

        Pattern pattern = glob(entry);
        if (entry.contains(":")) {
            return stack -> pattern.matcher(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).matches();
        }
        return stack -> pattern.matcher(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()).matches();
    }

    /** "mekanism*" -> mekanism seguido de qualquer coisa; o resto é literal. */
    private static Pattern glob(String text) {
        List<String> parts = new ArrayList<>();
        for (String part : text.split("\\*", -1)) {
            parts.add(Pattern.quote(part));
        }
        return Pattern.compile(String.join(".*", parts));
    }

    static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
