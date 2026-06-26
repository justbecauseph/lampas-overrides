package town.lampas.overrides.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import town.lampas.overrides.Faction;
import town.lampas.overrides.ModEffects;
import town.lampas.overrides.PlayerActivityListener;

/**
 * Extends the Fisheries-faction fishing perk to Starcatcher rods.
 *
 * <p>Starcatcher replaces vanilla fishing entirely with its own {@code FishingBobEntity} + minigame,
 * so the vanilla {@code FishingHookMixin} (which bumps {@code luck}/{@code lureSpeed} and applies the
 * Fisheries Boon) has no effect on Starcatcher rods. This mixin mirrors that perk:
 *
 * <ul>
 *   <li><b>Faster bites:</b> Starcatcher's bite timing ({@code minTicksToFish}, {@code maxTicksToFish},
 *       {@code chanceToFishEachTick}) is computed in the server-side constructor after bait/hook
 *       modifiers; we shorten it for {@code FISHERIES} members (×0.7 / ×0.8 / ×1.3 &mdash; mirroring
 *       Starcatcher's standard lure-time bait) so their catches bite faster. Catch value and rarity
 *       odds are intentionally left unchanged.</li>
 *   <li><b>Mother's Grace:</b> while the bob is active we refresh the {@code fisheries_boon}
 *       ("Mother's Grace") status icon on the player, exactly as the vanilla hook does, so the perk
 *       reads identically on Starcatcher rods.</li>
 * </ul>
 *
 * <p>Targeted by fully-qualified name with {@code remap = false} (non-Minecraft class) and gated on
 * {@code starcatcher} being loaded in {@link LampasMixinPlugin}.
 */
@Mixin(targets = "com.wdiscute.starcatcher.bobberentity.FishingBobEntity", remap = false)
public class StarcatcherFishingBobMixin {

    @Shadow
    @Final
    public Player player;

    @Shadow
    public int minTicksToFish;

    @Shadow
    public int maxTicksToFish;

    @Shadow
    public float chanceToFishEachTick;

    @Unique
    private boolean lampasOverrides$isFisheriesOwner;

    @Inject(
            method = "<init>(Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("TAIL"))
    private void lampasOverrides$fisheriesFasterBites(Level level, Player player, ItemStack rod, CallbackInfo ci) {
        if (level.isClientSide() || player == null) {
            return;
        }
        if (PlayerActivityListener.getPlayerFaction(player.getUUID()) != Faction.FISHERIES) {
            return;
        }
        this.lampasOverrides$isFisheriesOwner = true;

        // Mirror Starcatcher's standard lure-time bait (0.7 / 0.8 ticks, 1.3x bite chance): fish
        // bite sooner without changing what is caught.
        this.minTicksToFish = Math.max(1, Math.round(this.minTicksToFish * 0.7f));
        this.maxTicksToFish = Math.max(1, Math.round(this.maxTicksToFish * 0.8f));
        this.chanceToFishEachTick = this.chanceToFishEachTick * 1.3f;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void lampasOverrides$fisheriesBoonTick(CallbackInfo ci) {
        if (!this.lampasOverrides$isFisheriesOwner) {
            return;
        }
        Player owner = this.player;
        if (owner == null || owner.level().isClientSide()) {
            return;
        }
        // Refresh "Mother's Grace" only when missing or expiring within 1s, matching the vanilla
        // hook and avoiding tick-by-tick addEffect overhead.
        MobEffectInstance activeBoon = owner.getEffect(ModEffects.FISHERIES_BOON);
        if (activeBoon == null || activeBoon.getDuration() <= 20) {
            owner.addEffect(new MobEffectInstance(
                    ModEffects.FISHERIES_BOON,
                    100, // 100 ticks = 5s
                    0,
                    true,  // ambient
                    false, // visibleParticles
                    true   // showIcon
            ));
        }
    }
}
