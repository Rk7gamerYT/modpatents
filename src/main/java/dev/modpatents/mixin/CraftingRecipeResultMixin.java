package dev.modpatents.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.modpatents.CraftRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pega o resultado de toda receita shaped/shapeless, não importa quem pediu o craft
 * (autocraft do AE2, Refined Storage, Create, Mekanism, grades de craft de outros mods...).
 */
@Mixin({ShapedRecipe.class, ShapelessRecipe.class})
public abstract class CraftingRecipeResultMixin {
    @ModifyReturnValue(
            method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private ItemStack modpatents$filterResult(ItemStack result) {
        return CraftRules.filterRecipeResult(result);
    }
}
