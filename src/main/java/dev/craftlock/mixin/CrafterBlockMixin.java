package dev.craftlock.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.craftlock.CraftRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Crafter (autocraft do vanilla): não tem jogador, então segue a regra "crafter_bloqueia_itens_de_mod". */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @ModifyExpressionValue(
            method = "dispenseFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack craftlock$filterResult(ItemStack result) {
        return CraftRules.filterAutomated(result);
    }
}
