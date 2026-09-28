package dev.modpatents;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Auditoria com mods de verdade. Só roda se houver mods de terceiros em build/minecraft-junit/mods
 * (copie os .jar do seu modpack pra lá). Sem eles, os testes são pulados.
 *
 * Monta toda receita de craft desses mods "por fora" (como um autocrafter faria) e conta
 * quantas escapam do bloqueio, agrupado pela classe da receita.
 */
@ExtendWith(EphemeralTestServerProvider.class)
class PackAuditTest {
    private static final GameProfile TESTER = new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000beef"), "Auditor");

    private static void assumePackMods() {
        long thirdParty = ModList.get().getMods().stream()
                .map(m -> m.getModId())
                .filter(id -> !List.of("minecraft", "neoforge", "modpatents").contains(id))
                .count();
        Assumptions.assumeTrue(thirdParty > 0, "sem mods do pack em build/minecraft-junit/mods");
    }

    private static void onServer(MinecraftServer server, Runnable body) {
        TestServer.onServer(server, body);
    }

    private static PatentsConfig onlyVanilla() {
        PatentsConfig config = new PatentsConfig();
        config.alwaysAllowed = new ArrayList<>(List.of("minecraft"));
        return config;
    }

    private static boolean isModItem(ItemStack stack) {
        return !stack.isEmpty() && !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft");
    }

    /** Monta uma grade que casa com a receita, usando o primeiro item de cada ingrediente. */
    static CraftingInput inputFor(CraftingRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        int width;
        int height;
        if (recipe instanceof ShapedRecipe shaped) {
            width = shaped.getWidth();
            height = shaped.getHeight();
        } else {
            int[] dims = reflectDims(recipe);
            if (dims != null) {
                width = dims[0];
                height = dims[1];
            } else {
                width = Math.min(3, Math.max(1, ingredients.size()));
                height = Math.max(1, (ingredients.size() + 2) / 3);
            }
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < width * height; i++) {
            Ingredient ingredient = i < ingredients.size() ? ingredients.get(i) : Ingredient.EMPTY;
            ItemStack[] options = ingredient.getItems();
            items.add(options.length == 0 ? ItemStack.EMPTY : options[0].copy());
        }
        return CraftingInput.of(width, height, items);
    }

    private static int[] reflectDims(Object recipe) {
        try {
            Method w = recipe.getClass().getMethod("getWidth");
            Method h = recipe.getClass().getMethod("getHeight");
            return new int[]{(int) w.invoke(recipe), (int) h.invoke(recipe)};
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    @Test
    void autocraftNaoEscapaNasReceitasDosMods(MinecraftServer server) {
        assumePackMods();
        onServer(server, () -> {
            ServerLevel level = server.overworld();
            PatentsConfig semBloqueio = onlyVanilla();
            semBloqueio.autocraftBlocksModItems = false;
            PatentsConfig comBloqueio = onlyVanilla();
            Map<String, int[]> byClass = new TreeMap<>(); // [testadas, escaparam]
            Map<String, List<String>> escapedExamples = new TreeMap<>();
            int untestable = 0;

            for (RecipeHolder<CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                ItemStack preview = recipe.getResultItem(level.registryAccess());
                if (!isModItem(preview)) {
                    continue;
                }
                CraftingInput input = inputFor(recipe);
                // primeiro confirma que a grade monta a receita quando não há bloqueio
                CraftRules.apply(semBloqueio);
                boolean montavel;
                try {
                    montavel = recipe.matches(input, level) && isModItem(recipe.assemble(input, level.registryAccess()));
                } catch (RuntimeException e) {
                    montavel = false;
                }
                if (!montavel) {
                    untestable++;
                    continue;
                }
                String cls = recipe.getClass().getName();
                int[] counts = byClass.computeIfAbsent(cls, k -> new int[2]);
                counts[0]++;

                // agora com bloqueio: escapa se ainda casa E ainda entrega o item
                CraftRules.apply(comBloqueio);
                boolean escapou;
                try {
                    escapou = recipe.matches(input, level) && isModItem(recipe.assemble(input, level.registryAccess()));
                } catch (RuntimeException e) {
                    escapou = false;
                }
                if (escapou) {
                    counts[1]++;
                    List<String> examples = escapedExamples.computeIfAbsent(cls, k -> new ArrayList<>());
                    if (examples.size() < 3) {
                        examples.add(holder.id().toString());
                    }
                }
            }

            int tested = byClass.values().stream().mapToInt(c -> c[0]).sum();
            int escaped = byClass.values().stream().mapToInt(c -> c[1]).sum();
            StringBuilder report = new StringBuilder("\n===== AUDITORIA DE AUTOCRAFT =====\n");
            report.append(String.format("receitas de itens de mod testadas: %d, escaparam: %d, não montáveis automaticamente: %d%n",
                    tested, escaped, untestable));
            byClass.forEach((cls, c) -> report.append(String.format("  %-90s %5d testadas %5d escaparam %s%n",
                    cls, c[0], c[1], c[1] > 0 ? escapedExamples.get(cls) : "")));
            System.out.println(report);

            assertTrue(tested > 0, "nenhuma receita de mod foi testada");
            assertEquals(0, escaped, "receitas de mod escaparam do bloqueio de autocraft:" + report);
        });
    }

    @Test
    void jogadorComPatenteCraftaItemDeModNaBancada(MinecraftServer server) {
        assumePackMods();
        onServer(server, () -> {
            ServerLevel level = server.overworld();
            RecipeHolder<CraftingRecipe> target = null;
            CraftingInput input = null;
            for (RecipeHolder<CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                ItemStack preview = holder.value().getResultItem(level.registryAccess());
                if (!isModItem(preview) || !(holder.value() instanceof ShapedRecipe)) {
                    continue;
                }
                CraftingInput candidate = inputFor(holder.value());
                if (candidate.width() <= 3 && candidate.height() <= 3 && holder.value().matches(candidate, level)) {
                    target = holder;
                    input = candidate;
                    break;
                }
            }
            assertTrue(target != null, "nenhuma receita shaped de mod encontrada");
            String modId = BuiltInRegistries.ITEM.getKey(target.value().getResultItem(level.registryAccess()).getItem()).getNamespace();

            var player = FakePlayerFactory.get(level, TESTER);
            CraftingMenu menu = new CraftingMenu(9, player.getInventory(), ContainerLevelAccess.create(level, BlockPos.ZERO));

            PatentsConfig semPatente = onlyVanilla();
            semPatente.players.put("Auditor", new ArrayList<>());
            CraftRules.apply(semPatente);
            fill(menu, input);
            assertTrue(menu.getSlot(0).getItem().isEmpty(), "sem patente de " + modId + " -> bloqueado (" + target.id() + ")");

            PatentsConfig comPatente = onlyVanilla();
            comPatente.players.put("Auditor", new ArrayList<>(List.of(modId)));
            CraftRules.apply(comPatente);
            fill(menu, input);
            assertFalse(menu.getSlot(0).getItem().isEmpty(), "com patente de " + modId + " -> pode craftar (" + target.id() + ")");
            System.out.println("[auditoria] bancada ok com " + target.id() + " (mod " + modId + ")");
        });
    }

    private static void fill(CraftingMenu menu, CraftingInput input) {
        for (int i = 1; i <= 9; i++) {
            menu.getSlot(i).set(ItemStack.EMPTY);
        }
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                menu.getSlot(1 + x + y * 3).set(input.getItem(x, y).copy());
            }
        }
    }
}
