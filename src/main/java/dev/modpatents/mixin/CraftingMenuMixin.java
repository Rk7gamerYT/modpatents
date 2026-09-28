package dev.modpatents.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.modpatents.CraftRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mesa de craft e o craft 2x2 do inventário (InventoryMenu chama o mesmo método). */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    /** Durante o craft na bancada, o filtro genérico de receitas deixa a decisão para a regra do jogador abaixo. */
    @WrapMethod(method = "slotChangedCraftingGrid")
    private static void modpatents$markPlayerCraft(AbstractContainerMenu menu, Level level, Player player,
                                                   CraftingContainer craftSlots, ResultContainer resultSlots,
                                                   RecipeHolder<CraftingRecipe> recipe, Operation<Void> original) {
        Player previous = CraftRules.enterPlayerCraft(player);
        try {
            original.call(menu, level, player, craftSlots, resultSlots, recipe);
        } finally {
            CraftRules.exitPlayerCraft(previous);
        }
    }

    @ModifyExpressionValue(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack modpatents$filterResult(ItemStack result, @Local(argsOnly = true) Player player) {
        return CraftRules.filter(player, result);
    }
}
