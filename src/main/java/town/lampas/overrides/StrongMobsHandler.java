package town.lampas.overrides;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Buffs a configured set of mob types to be much stronger by attaching <em>permanent</em>
 * attribute modifiers (max health + attack damage) the first time each mob joins the world.
 *
 * <p>Implemented on {@link EntityJoinLevelEvent} rather than only on spawn so it also covers mobs
 * that already exist in the world: every time such a mob loads from a chunk it fires here, and we
 * only add the modifier if it isn't present yet. Because the modifiers are <em>permanent</em> they
 * persist in the entity's NBT, so an already-buffed mob simply skips re-application on later loads
 * (no stacking, no repeated top-up heal).
 *
 * <p>The multiplier is applied with {@link AttributeModifier.Operation#ADD_MULTIPLIED_BASE}: a
 * configured multiplier of {@code 5.0} stores an amount of {@code 4.0}, i.e. {@code base * (1 + 4)}
 * = 5x. Mobs that lack a given attribute (e.g. a Ghast has no {@code ATTACK_DAMAGE}) are simply
 * skipped for that axis. Set either multiplier to {@code 1.0} to disable that axis entirely.
 *
 * <p>Hot path: this fires for every entity join on every chunk load, so all config values are
 * cached in volatile fields on config (re)load, and the configured id strings are pre-resolved
 * into a {@code Set<EntityType<?>>} for an allocation-free identity lookup per event (same
 * pattern as {@code BlockAuditHandler}'s block/item sets).
 */
public class StrongMobsHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(LampasOverridesMod.MODID, "strong_mobs_health");
    public static final ResourceLocation DAMAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(LampasOverridesMod.MODID, "strong_mobs_damage");

    /** Config values cached on (re)load so the join hot path never touches the config spec. */
    private static volatile boolean enabled = false;
    private static volatile double healthMult = 1.0;
    private static volatile double damageMult = 1.0;
    private static volatile Set<String> strongMobIds = Set.of();

    /** Id strings resolved to registry objects lazily (registry may not be ready at config load). */
    private static volatile Set<EntityType<?>> strongTypes = Set.of();
    private static volatile boolean typesResolved = false;

    /** Refreshes the cached config values whenever this mod's config is loaded or reloaded. */
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            enabled = ModConfig.STRONG_MOBS_ENABLED.get();
            healthMult = ModConfig.STRONG_MOBS_HEALTH_MULT.get();
            damageMult = ModConfig.STRONG_MOBS_DAMAGE_MULT.get();
            strongMobIds = Set.copyOf(ModConfig.STRONG_MOBS_LIST.get());
            typesResolved = false;
        }
    }

    private static void ensureTypesResolved() {
        if (typesResolved) {
            return;
        }
        synchronized (StrongMobsHandler.class) {
            if (typesResolved) {
                return;
            }
            Set<EntityType<?>> newTypes = new HashSet<>();
            for (String id : strongMobIds) {
                try {
                    ResourceLocation rl = ResourceLocation.parse(id);
                    // ENTITY_TYPE is a defaulted registry (unknown ids resolve to pig), so an
                    // explicit containsKey guard is required to reject typos/missing mods.
                    if (BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) {
                        newTypes.add(BuiltInRegistries.ENTITY_TYPE.get(rl));
                    } else {
                        LOGGER.warn("[StrongMobs] Unknown entity type id in strongMobsList: {}", id);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[StrongMobs] Invalid entity type id in strongMobsList: {}", id);
                }
            }
            strongTypes = Set.copyOf(newTypes);
            typesResolved = true;
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!enabled) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (event.getLevel().isClientSide()) return;

        ensureTypesResolved();
        if (!strongTypes.contains(living.getType())) return;

        boolean healthAdded = applyModifier(living, Attributes.MAX_HEALTH, HEALTH_MODIFIER_ID, healthMult);
        applyModifier(living, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER_ID, damageMult);

        // Only when the health modifier was freshly added: top the mob up to its new maximum so it
        // doesn't spawn/load sitting at a low health percentage. Not done on later loads.
        if (healthAdded) {
            living.setHealth(living.getMaxHealth());
        }
    }

    /**
     * Adds our permanent multiplier modifier to {@code attr} if it isn't already present.
     *
     * @return {@code true} only if the modifier was newly added this call.
     */
    private static boolean applyModifier(LivingEntity living, Holder<Attribute> attr,
                                         ResourceLocation id, double multiplier) {
        if (multiplier <= 1.0) return false;               // 1.0 (or less) = no buff on this axis
        AttributeInstance inst = living.getAttribute(attr);
        if (inst == null) return false;                    // entity lacks this attribute
        if (inst.getModifier(id) != null) return false;    // already buffed (persisted in NBT)
        inst.addPermanentModifier(new AttributeModifier(
                id, multiplier - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        return true;
    }
}
