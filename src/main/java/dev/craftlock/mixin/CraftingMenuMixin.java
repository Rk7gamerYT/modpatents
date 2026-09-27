package dev.craftlock.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.craftlock.CraftRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mesa de craft e o craft 2x2 do inventário (InventoryMenu chama o mesmo método). */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @ModifyExpressionValue(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack craftlock$filterResult(ItemStack result, @Local(argsOnly = true) Player player) {
        return CraftRules.filter(player, result);
    }
}
