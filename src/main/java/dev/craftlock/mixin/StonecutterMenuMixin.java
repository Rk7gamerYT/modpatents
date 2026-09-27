package dev.craftlock.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.craftlock.CraftRules;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cortador de pedras (o menu não guarda o jogador, então guardamos na construção). */
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
    @Unique
    private Player craftlock$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void craftlock$capturePlayer(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        this.craftlock$player = inventory.player;
    }

    @ModifyExpressionValue(
            method = "setupResultSlot",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/StonecutterRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack craftlock$filterResult(ItemStack result) {
        return CraftRules.filter(this.craftlock$player, result);
    }
}
