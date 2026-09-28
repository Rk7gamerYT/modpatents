package dev.modpatents.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.modpatents.CraftRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Receitas de craft de outros mods que montam o resultado sem passar por ShapedRecipe/ShapelessRecipe.
 * Lista levantada pela auditoria (PackAuditTest). Se o mod não estiver instalado, é ignorado;
 * se uma versão nova mudar a classe, não quebra o servidor (require = 0) e a auditoria acusa.
 */
@Pseudo
@Mixin(targets = {
        "mekanism.common.recipe.upgrade.MekanismShapedRecipe",
        "com.refinedmods.refinedstorage.common.support.RecoloringRecipe",
        "com.refinedmods.refinedstorage.common.storage.StorageContainerUpgradeRecipe",
        "appeng.recipes.game.StorageCellUpgradeRecipe",
        "appeng.recipes.quartzcutting.QuartzCuttingRecipe",
        "de.melanx.extradisks.data.recipes.StorageContainerUpgradeRecipe",
        "mekanism.tools.common.recipe.PaxelRecipe",
        "com.blakebr0.cucumber.crafting.recipe.ShapedTransferComponentsRecipe",
        "com.blakebr0.cucumber.crafting.recipe.ShapedTransferDamageRecipe",
        "com.jaquadro.minecraft.storagedrawers.core.recipe.KeyringRecipe",
        "com.jaquadro.minecraft.storagedrawers.core.recipe.RemoteGroupUpgradeRecipe",
        "net.dries007.tfc.common.recipes.AdvancedShapedRecipe",
        "net.dries007.tfc.common.recipes.AdvancedShapelessRecipe",
})
public abstract class ModRecipeResultMixin {
    @ModifyReturnValue(
            method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"),
            require = 0)
    private ItemStack modpatents$filterResult(ItemStack result) {
        return CraftRules.filterRecipeResult(result);
    }
}
