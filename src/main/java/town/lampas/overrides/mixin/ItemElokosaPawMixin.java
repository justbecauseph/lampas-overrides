package town.lampas.overrides.mixin;

import com.bobmowzie.mowziesmobs.server.config.ConfigHandler;
import com.bobmowzie.mowziesmobs.server.entity.elokosa.PawType;
import com.bobmowzie.mowziesmobs.server.item.ItemElokosaPaw;
import com.bobmowzie.mowziesmobs.server.item.ItemHandler;
import com.bobmowzie.mowziesmobs.server.sound.MMSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/**
 * Fixes a hard server crash when any player right-clicks a Mowzie's Mobs Elokosa Paw.
 *
 * <p>{@code ItemElokosaPaw#use} runs on both logical sides. Its cosmetic particle work calls
 * {@code AdvancedParticleBase.spawnParticle(...)} and references client-only classes
 * ({@code ParticleHandler}, {@code ParticleComponent}, ...), which transitively need
 * {@code net.minecraft.client.particle.TextureSheetParticle} &mdash; a class that does not exist on a
 * dedicated server. The first invocation of {@code use} therefore fails JVM bytecode verification
 * (verification is eager over the whole method, including the {@code level.isClientSide()}-guarded
 * branch), throwing {@code NoClassDefFoundError: net/minecraft/client/particle/TextureSheetParticle}
 * and crashing the server tick. The per-target AOE particle loop is additionally <em>unguarded</em>,
 * so even past verification it would hit the missing class at runtime. Because systemd auto-restarts
 * the server, this is effectively a player-triggerable crash-restart exploit. 1.8.2 is the newest
 * 1.21.1 build, so there is no upstream version to update to.
 *
 * <p>This mixin is registered in the {@code "server"} (dedicated-server-only) list of
 * {@code lampas_overrides.mixins.json}, so it never applies on a client or integrated server. We
 * {@link Overwrite} {@code use} with the server-authoritative logic only &mdash; sound, item
 * cooldowns, durability, and the area-of-effect potion application &mdash; and drop every particle
 * call (particles are client-rendered and irrelevant to the dedicated server). Clients run the
 * unmodified method and keep the full visual effect. Logic mirrors {@code ItemElokosaPaw#use} in
 * mowziesmobs-1.21.1-1.8.2.
 */
@Mixin(value = ItemElokosaPaw.class, remap = false)
public abstract class ItemElokosaPawMixin {

    @Shadow @Final private PawType pawType;

    /**
     * @author lampas-overrides
     * @reason Strip client-only particle calls from the dedicated-server code path; the original
     *         method cannot be verified or executed on a server without {@code TextureSheetParticle}.
     */
    @Overwrite
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemstack = player.getItemInHand(usedHand);
        player.playSound((SoundEvent) MMSounds.ENTITY_ELOKOSA_PAW.get(), 1.0F, 1.15F - 0.06F * (float) this.pawType.ordinal());
        int cooldown = ConfigHandler.COMMON.TOOLS_AND_ABILITIES.ELOKOSA_PAW.cooldown.getAsInt();
        if (!player.hasInfiniteMaterials()) {
            for (var paw : ItemHandler.ELOKOSA_PAWS) {
                player.getCooldowns().addCooldown(paw.get(), cooldown);
            }
        }
        itemstack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(usedHand));

        float radius = 10.0F;
        List<LivingEntity> entitiesNearby = level.getEntitiesOfClass(
                LivingEntity.class,
                player.getBoundingBox().inflate((double) radius, (double) radius, (double) radius),
                e -> e.distanceToSqr(player) <= (double) (radius * radius) && e != player);
        int effectDuration = ConfigHandler.COMMON.TOOLS_AND_ABILITIES.ELOKOSA_PAW.effectDuration.getAsInt();
        for (LivingEntity entity : entitiesNearby) {
            entity.addEffect(new MobEffectInstance(this.pawType.getPotion(), effectDuration));
        }

        return InteractionResultHolder.consume(itemstack);
    }
}
