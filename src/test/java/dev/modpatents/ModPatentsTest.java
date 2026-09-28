package dev.modpatents;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sobe um servidor Minecraft de verdade e abre os menus de craft com um jogador falso.
 * Como no ambiente de teste só existe o vanilla, os testes tiram "minecraft" dos
 * sempre_liberados pra simular "item de um mod que o jogador não tem".
 */
@ExtendWith(EphemeralTestServerProvider.class)
class ModPatentsTest {
    private static final GameProfile TESTER = new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000abcd"), "Tester");

    private static PatentsConfig config(String... testerEntries) {
        PatentsConfig config = new PatentsConfig();
        config.alwaysAllowed = new ArrayList<>();
        config.players.put("TESTER", new ArrayList<>(List.of(testerEntries))); // maiúsculas de propósito
        return config;
    }

    private static void onServer(MinecraftServer server, Runnable body) {
        TestServer.onServer(server, body);
    }

    private static FakePlayer player(MinecraftServer server) {
        return FakePlayerFactory.get(server.overworld(), TESTER);
    }

    /** Coloca os itens na grade (a partir do slot 1) e devolve o que apareceu no slot de resultado (slot 0). */
    private static ItemStack craft(AbstractContainerMenu menu, ItemStack... grid) {
        for (int i = 0; i < grid.length; i++) {
            menu.getSlot(1 + i).set(grid[i].copy());
        }
        return menu.getSlot(0).getItem();
    }

    private static ItemStack craftInInventory(MinecraftServer server, ItemStack... grid) {
        FakePlayer player = player(server);
        for (int i = 1; i <= 4; i++) {
            player.inventoryMenu.getSlot(i).set(ItemStack.EMPTY);
        }
        return craft(player.inventoryMenu, grid);
    }

    private static ItemStack craftInTable(MinecraftServer server, ItemStack... grid) {
        ServerLevel level = server.overworld();
        FakePlayer player = player(server);
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(level, BlockPos.ZERO));
        return craft(menu, grid);
    }

    private static final ItemStack LOG = new ItemStack(Items.OAK_LOG);
    private static final ItemStack PLANKS = new ItemStack(Items.OAK_PLANKS);

    @Test
    void semPermissaoOResultadoFicaVazio(MinecraftServer server) {
        onServer(server, () -> {
            CraftRules.apply(config());
            assertTrue(craftInInventory(server, LOG).isEmpty(), "inventário 2x2");
            assertTrue(craftInTable(server, LOG).isEmpty(), "mesa de craft");
        });
    }

    @Test
    void modLiberadoDeixaCraftar(MinecraftServer server) {
        onServer(server, () -> {
            CraftRules.apply(config("minecraft"));
            assertEquals(Items.OAK_PLANKS, craftInInventory(server, LOG).getItem());
            assertEquals(Items.OAK_PLANKS, craftInTable(server, LOG).getItem());
        });
    }

    @Test
    void curingaNoIdDoMod(MinecraftServer server) {
        onServer(server, () -> {
            CraftRules.apply(config("mine*"));
            assertEquals(Items.OAK_PLANKS, craftInTable(server, LOG).getItem());
            CraftRules.apply(config("mek*"));
            assertTrue(craftInTable(server, LOG).isEmpty());
        });
    }

    @Test
    void itemEspecificoETag(MinecraftServer server) {
        onServer(server, () -> {
            CraftRules.apply(config("minecraft:oak_planks"));
            assertEquals(Items.OAK_PLANKS, craftInTable(server, LOG).getItem());
            assertTrue(craftInTable(server, PLANKS, ItemStack.EMPTY, ItemStack.EMPTY, PLANKS).isEmpty(), "graveto não liberado");

            CraftRules.apply(config("#minecraft:planks"));
            assertEquals(Items.OAK_PLANKS, craftInTable(server, LOG).getItem());
            assertTrue(craftInTable(server, PLANKS, ItemStack.EMPTY, ItemStack.EMPTY, PLANKS).isEmpty());

            CraftRules.apply(config("minecraft:*_planks", "minecraft:stick"));
            assertEquals(Items.STICK, craftInTable(server, PLANKS, ItemStack.EMPTY, ItemStack.EMPTY, PLANKS).getItem());
        });
    }

    @Test
    void outroJogadorNaoHerdaPermissao(MinecraftServer server) {
        onServer(server, () -> {
            PatentsConfig config = config();
            config.players.put("OutroCara", new ArrayList<>(List.of("minecraft")));
            CraftRules.apply(config);
            assertTrue(craftInTable(server, LOG).isEmpty());
        });
    }

    @Test
    void permissaoPorUuid(MinecraftServer server) {
        onServer(server, () -> {
            PatentsConfig config = config();
            config.players.put(TESTER.getId().toString(), new ArrayList<>(List.of("minecraft")));
            CraftRules.apply(config);
            assertEquals(Items.OAK_PLANKS, craftInTable(server, LOG).getItem());
        });
    }

    @Test
    void bypassEBloqueioGlobal(MinecraftServer server) {
        onServer(server, () -> {
            PatentsConfig config = config();
            config.bypass.add("tester");
            CraftRules.apply(config);
            assertEquals(Items.OAK_PLANKS, craftInTable(server, LOG).getItem());

            config = config("minecraft");
            config.blockedForAll.add("minecraft:oak_planks");
            CraftRules.apply(config);
            assertTrue(craftInTable(server, LOG).isEmpty(), "bloqueados_para_todos vence a permissão do jogador");
        });
    }

    @Test
    void cortadorDePedras(MinecraftServer server) {
        onServer(server, () -> {
            FakePlayer player = player(server);
            CraftRules.apply(config());
            StonecutterMenu menu = new StonecutterMenu(2, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
            menu.getSlot(0).set(new ItemStack(Items.STONE));
            // procura a receita de tijolo de pedra (com modpacks, a primeira da lista pode ser de outro mod)
            int stoneBricks = -1;
            for (int i = 0; i < menu.getNumRecipes(); i++) {
                if (menu.getRecipes().get(i).value().getResultItem(server.registryAccess()).is(Items.STONE_BRICKS)) {
                    stoneBricks = i;
                }
            }
            assertTrue(stoneBricks >= 0, "receita de tijolo de pedra não encontrada");
            menu.clickMenuButton(player, stoneBricks);
            assertTrue(menu.getSlot(1).getItem().isEmpty());

            CraftRules.apply(config("minecraft"));
            menu.clickMenuButton(player, stoneBricks);
            assertEquals(Items.STONE_BRICKS, menu.getSlot(1).getItem().getItem());
        });
    }

    @Test
    void mesaDeFerraria(MinecraftServer server) {
        onServer(server, () -> {
            FakePlayer player = player(server);
            CraftRules.apply(config());
            SmithingMenu menu = new SmithingMenu(3, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
            menu.getSlot(0).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            menu.getSlot(1).set(new ItemStack(Items.DIAMOND_SWORD));
            menu.getSlot(2).set(new ItemStack(Items.NETHERITE_INGOT));
            assertTrue(menu.getSlot(3).getItem().isEmpty());

            CraftRules.apply(config("minecraft"));
            menu.slotsChanged(menu.getSlot(0).container);
            assertEquals(Items.NETHERITE_SWORD, menu.getSlot(3).getItem().getItem());
        });
    }

    @Test
    void crafterAutomatico(MinecraftServer server) {
        onServer(server, () -> {
            PatentsConfig config = config();
            CraftRules.apply(config);
            assertTrue(CraftRules.filterAutomated(PLANKS.copy()).isEmpty(), "nada liberado -> crafter não faz");

            config.alwaysAllowed.add("minecraft");
            CraftRules.apply(config);
            assertEquals(Items.OAK_PLANKS, CraftRules.filterAutomated(PLANKS.copy()).getItem());

            config.autocraftBlocksModItems = false;
            config.alwaysAllowed.clear();
            CraftRules.apply(config);
            assertEquals(Items.OAK_PLANKS, CraftRules.filterAutomated(PLANKS.copy()).getItem());
        });
    }

    @Test
    void comandosLiberarERemover(MinecraftServer server) {
        onServer(server, () -> {
            CraftRules.apply(config());
            var source = server.createCommandSourceStack();
            server.getCommands().performPrefixedCommand(source, "patents liberar Tester minecraft");
            assertEquals(Items.OAK_PLANKS, craftInTable(server, LOG).getItem(), "/patents liberar");

            server.getCommands().performPrefixedCommand(source, "patentes remover tester minecraft");
            assertTrue(craftInTable(server, LOG).isEmpty(), "/patentes remover (apelido)");

            server.getCommands().performPrefixedCommand(source, "patents liberar NovoJogador create*");
            assertEquals(List.of("create*"), CraftRules.config().players.get("NovoJogador"));
        });
    }

    /** Receita de tábua (shaped/shapeless do vanilla) montada "por fora", como um autocrafter de outro mod faria. */
    private static ItemStack assembleUnattended(MinecraftServer server) {
        ServerLevel level = server.overworld();
        CraftingInput input = CraftingInput.of(1, 1, List.of(LOG.copy()));
        var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
        return recipe.value().assemble(input, level.registryAccess());
    }

    @Test
    void autocraftDeOutrosModsSoFazItensLiberados(MinecraftServer server) {
        onServer(server, () -> {
            CraftRules.apply(config("minecraft")); // o jogador tem a patente, mas autocraft não é jogador
            assertTrue(assembleUnattended(server).isEmpty(), "autocraft bloqueado");
            assertEquals(Items.OAK_PLANKS, craftInTable(server, LOG).getItem(), "na bancada o jogador ainda pode");

            PatentsConfig liberado = config();
            liberado.alwaysAllowed.add("minecraft");
            CraftRules.apply(liberado);
            assertEquals(Items.OAK_PLANKS, assembleUnattended(server).getItem(), "item de sempre_liberados passa");

            PatentsConfig desligado = config();
            desligado.autocraftBlocksModItems = false;
            CraftRules.apply(desligado);
            assertEquals(Items.OAK_PLANKS, assembleUnattended(server).getItem(), "opção desligada");
        });
    }

    @Test
    void foraDaThreadDoServidorNaoFiltra(MinecraftServer server) {
        onServer(server, () -> CraftRules.apply(config()));
        // chamado da thread do teste, como o cliente faria no singleplayer
        assertEquals(Items.OAK_PLANKS, assembleUnattended(server).getItem());
    }

    @Test
    void configAntigoComCrafterBloqueiaAindaFunciona(MinecraftServer server) {
        PatentsConfig config = new com.google.gson.Gson().fromJson("{\"crafter_bloqueia_itens_de_mod\": false}", PatentsConfig.class);
        assertTrue(!config.autocraftBlocksModItems);
    }
}
