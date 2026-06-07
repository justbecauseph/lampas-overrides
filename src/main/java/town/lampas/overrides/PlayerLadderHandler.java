package town.lampas.overrides;

import com.google.common.collect.Sets;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;
import java.util.Set;

public class PlayerLadderHandler {
    private static final Set<EntityType<?>> entityTypesToExclude = Sets.newHashSet();
    private static final Set<TagKey<EntityType<?>>> entityTagsToExclude = Sets.newHashSet();

    private static final String NBT_KEY_DISABLE_RIDING = "PlayerLadder_DisableRiding";

    public static void onConfigEvent(final ModConfigEvent event) {
        if (event.getConfig().getSpec() == ModConfig.SPEC) {
            setExcludedEntries(ModConfig.LADDER_EXCLUDED_LIVING_ENTITIES.get());
        }
    }

    public static boolean isRidingDisabledByPlayer(Player player) {
        return player.getPersistentData().getBoolean(NBT_KEY_DISABLE_RIDING);
    }

    public static void toggleRiding(ServerPlayer player) {
        boolean currentValue = player.getPersistentData().getBoolean(NBT_KEY_DISABLE_RIDING);
        player.getPersistentData().putBoolean(NBT_KEY_DISABLE_RIDING, !currentValue);
        if (!currentValue) {
            player.sendSystemMessage(Component.literal("Player Ladder interactions disabled for you.").withStyle(net.minecraft.ChatFormatting.RED));
            // Force dismount any passengers
            if (player.isVehicle()) {
                for (Entity passenger : player.getPassengers()) {
                    passenger.stopRiding();
                }
            }
            if (player.isPassenger()) {
                player.stopRiding();
            }
        } else {
            player.sendSystemMessage(Component.literal("Player Ladder interactions enabled for you.").withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // Register /playerladder toggle
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("playerladder")
                .then(net.minecraft.commands.Commands.literal("toggle")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        toggleRiding(player);
                        return 1;
                    })
                )
        );

        // Register /ladder toggle alias
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("ladder")
                .then(net.minecraft.commands.Commands.literal("toggle")
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        toggleRiding(player);
                        return 1;
                    })
                )
        );
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.onGround() && player.isVehicle() && player.isCrouching()) {
            player.getFirstPassenger().stopRiding();
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.isSpectator()) return;
        InteractionResult result = switch (ModConfig.LADDER_MODE.get()) {
            case RIDE -> rideEntity(player, event.getTarget(), event.getLevel(), event.getHand());
            case PICK_UP -> pickUpEntity(player, event.getTarget(), event.getLevel(), event.getHand());
            case DO_NOTHING -> InteractionResult.PASS;
        };
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    @SubscribeEvent
    public void onPlayerLogOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.isPassenger() && player.getVehicle() instanceof Player) {
            player.stopRiding();
        }
    }

    @SubscribeEvent
    public void onPlayerGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        Player player = event.getEntity();
        if (player.isVehicle()) {
            player.getFirstPassenger().stopRiding();
        }
    }

    public static InteractionResult rideEntity(Player player, Entity newVehicle, Level level, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (isRidingDisabledByPlayer(player)) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND && canPickUpOrRideLiving(newVehicle) && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide()) {
                Entity vehicle = getHighestOrSelf(newVehicle, player, ModConfig.LADDER_STEP_UP_LIMIT.get());

                if (vehicle == null) return InteractionResult.FAIL;
                player.startRiding(vehicle);
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    public static InteractionResult pickUpEntity(Player player, Entity newPassenger, Level level, InteractionHand hand) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (isRidingDisabledByPlayer(player)) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND && canPickUpOrRideLiving(newPassenger) && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide()) {
                Entity vehicle = getHighestOrSelf(player, newPassenger, ModConfig.LADDER_PICK_UP_LIMIT.get());

                if (vehicle == null) return InteractionResult.FAIL;
                newPassenger.startRiding(vehicle);
            }

            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    private static Entity getHighestOrSelf(Entity vehicle, Entity newPassenger, int limit) {
        int count = -1;
        while (vehicle.isVehicle()) {
            count++;
            vehicle = vehicle.getFirstPassenger();
            if (vehicle == newPassenger || count >= limit) return null;
        }
        return vehicle;
    }

    private static boolean canPickUpOrRideLiving(Entity entity) {
        if (entity instanceof Player player) {
            if (player.isSpectator()) return false;
            if (isRidingDisabledByPlayer(player)) return false;
            return ModConfig.LADDER_ALLOW_PLAYERS.get();
        }

        return ModConfig.LADDER_ALLOW_LIVING_ENTITIES.get() &&
                !entityTypesToExclude.contains(entity.getType()) &&
                entityTagsToExclude.stream().noneMatch(tag -> entity.getType().is(tag));
    }

    public static void onMount(Entity vehicle, Entity passenger) {
        if (!vehicle.level().isClientSide && vehicle instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.connection != null) {
                serverPlayer.connection.send(new ClientboundSetPassengersPacket(vehicle));
            }
        }
    }

    public static void onDismount(Entity vehicle) {
        if (!vehicle.level().isClientSide && vehicle instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.connection != null) {
                serverPlayer.connection.send(new ClientboundSetPassengersPacket(vehicle));
            }
        }
    }

    private static void addExcludedEntityType(String entity) {
        try {
            Optional<EntityType<?>> type = EntityType.byString(entity);
            type.ifPresent(entityTypesToExclude::add);
        } catch (ResourceLocationException ignored) {}
    }

    private static void addExcludedEntityTag(String tag) {
        try {
            TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(tag.substring(1)));
            entityTagsToExclude.add(tagKey);
        } catch (ResourceLocationException ignored) {}
    }

    public static void setExcludedEntries(java.util.List<? extends String> entries) {
        entityTagsToExclude.clear();
        entityTypesToExclude.clear();

        for (String entry : entries) {
            if (entry.isEmpty()) continue;

            if (entry.startsWith("#")) {
                addExcludedEntityTag(entry);
            } else {
                addExcludedEntityType(entry);
            }
        }
    }
}
