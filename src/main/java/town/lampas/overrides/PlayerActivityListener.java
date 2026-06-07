package town.lampas.overrides;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.server.network.Filterable;
import java.util.ArrayList;

public class PlayerActivityListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final java.util.Map<java.util.UUID, Faction> PLAYER_FACTIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> LAST_FETCH_TIME = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, BlockPos> LAST_BOUNTY_BOARD_POS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final ThreadLocal<Boolean> IS_MERGING_INVENTORY = ThreadLocal.withInitial(() -> false);

    public static Faction getPlayerFaction(java.util.UUID uuid) {
        long now = System.currentTimeMillis();
        long lastFetch = LAST_FETCH_TIME.getOrDefault(uuid, 0L);
        // Refresh in background if missing or fetched more than 30 seconds ago
        if (now - lastFetch > 30000L) {
            LAST_FETCH_TIME.put(uuid, now);
            fetchPlayerFactionAsync(uuid);
        }
        return PLAYER_FACTIONS.getOrDefault(uuid, Faction.NONE);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        String uuid = player.getUUID().toString();
        String username = player.getName().getString();
        LOGGER.info("Player logged in: {} ({})", username, uuid);
        sendWebhookAsync(uuid, username, "login");
        // Fetch faction immediately on login
        fetchPlayerFactionAsync(player.getUUID());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        String uuid = player.getUUID().toString();
        String username = player.getName().getString();
        LOGGER.info("Player logged out: {} ({})", username, uuid);
        sendWebhookAsync(uuid, username, "logout");
        // Clean up maps
        PLAYER_FACTIONS.remove(player.getUUID());
        LAST_FETCH_TIME.remove(player.getUUID());
        LAST_BOUNTY_BOARD_POS.remove(player.getUUID());
    }

    private void sendWebhookAsync(String uuid, String username, String eventType) {
        String webhookUrl = ModConfig.WEBHOOK_URL.get();
        String apiKey = ModConfig.API_KEY.get();

        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOGGER.warn("Webhook URL is not configured. Skipping event sync.");
            return;
        }

        com.google.gson.JsonObject jsonObject = new com.google.gson.JsonObject();
        jsonObject.addProperty("uuid", uuid);
        jsonObject.addProperty("username", username);
        jsonObject.addProperty("event", eventType);

        String json = jsonObject.toString();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpUtil.HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            LOGGER.info("Successfully synced player {} event: {}", username, eventType);
                        } else {
                            LOGGER.error("Failed to sync player event for {}. Status code: {}, Response: {}",
                                    username, response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Error sending webhook for player {}: {}", username, ex.getMessage(), ex);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to build HTTP request for player event: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof net.satisfy.wildernature.core.block.BountyBoardBlock) {
            event.setCanceled(true);
            if (event.getLevel().isClientSide) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            Player player = event.getEntity();
            if (player instanceof ServerPlayer serverPlayer) {
                LAST_BOUNTY_BOARD_POS.put(player.getUUID(), event.getPos());
                serverPlayer.sendSystemMessage(Component.literal("Loading bounties...").withStyle(ChatFormatting.YELLOW));
                BountyApiFetcher.getBountiesAsync(bounties -> {
                    serverPlayer.server.execute(() -> {
                        openBountyBoardBook(serverPlayer, bounties);
                    });
                });
            }
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        if (event.getLevel().isClientSide) {
            return;
        }

        Player player = event.getEntity();
        if (checkAndCancelFishingRodUse(event, player)) {
            return;
        }
        if (player != null && !player.hasPermissions(2)) {
            Faction requiredRole = getRequiredFactionForBlock(state);
            if (requiredRole != Faction.NONE) {
                Faction role = getPlayerFaction(player.getUUID());
                if (role != requiredRole) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "You must have the " + requiredRole.name() + " role to interact with this block!"
                    ).withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    /**
     * Single-pass determination of which faction is required to interact with a block.
     * Returns Faction.NONE if the block is unrestricted.
     */
    static Faction getRequiredFactionForBlock(BlockState state) {
        if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE)) {
            return Faction.LMI;
        }
        if (state.is(Blocks.BREWING_STAND) || state.is(Blocks.SMOKER)) {
            return Faction.FISHERIES;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id != null) {
            String path = id.getPath();
            if (path.contains("furnace") && !path.contains("minecart")) {
                return Faction.LMI;
            }
            if (id.getNamespace().equals("lightmanscurrency")) {
                if (path.equals("tax_block")) return Faction.NOBILITY;
                if (path.equals("vending_machine_large_black")) return Faction.MERCHANTS;
            }
        }
        return Faction.NONE;
    }

    public static void fetchPlayerFactionAsync(java.util.UUID uuid) {
        String url = getPlayerApiUrl(uuid.toString());
        String apiKey = ModConfig.API_KEY.get();

        if (url == null || url.isBlank()) {
            LOGGER.warn("API URL is not configured. Skipping player faction fetch.");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-api-key", apiKey)
                    .GET()
                    .build();

            HttpUtil.HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
                                Faction faction = Faction.NONE;
                                if (json.has("faction") && !json.get("faction").isJsonNull()) {
                                    String factionStr = json.get("faction").getAsString();
                                    faction = Faction.fromString(factionStr);
                                }
                                final Faction finalFaction = faction;
                                net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                                if (server != null) {
                                    server.execute(() -> {
                                        if (server.getPlayerList().getPlayer(uuid) != null) {
                                            PLAYER_FACTIONS.put(uuid, finalFaction);
                                            LOGGER.info("Fetched faction for player UUID {}: {}", uuid, finalFaction);
                                        } else {
                                            LOGGER.info("Discarded fetched faction for player UUID {} because they are no longer online.", uuid);
                                        }
                                    });
                                }
                            } catch (Exception ex) {
                                LOGGER.error("Failed to parse player API response: {}", ex.getMessage(), ex);
                            }
                        } else {
                            LOGGER.error("Failed to fetch player faction. Status code: {}, Response: {}",
                                    response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.error("Error fetching player faction: {}", ex.getMessage(), ex);
                        return null;
                    });
        } catch (Exception e) {
            LOGGER.error("Failed to build player faction HTTP request: {}", e.getMessage(), e);
        }
    }

    private static String getPlayerApiUrl(String playerUuid) {
        String playerApiUrl = ModConfig.PLAYER_API_URL.get();
        if (playerApiUrl == null || playerApiUrl.isBlank()) {
            return null;
        }
        return playerApiUrl + "?uuid=" + playerUuid;
    }

    @SubscribeEvent
    public void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!player.level().isClientSide) {
                if (player.hasEffect(ModEffects.TOTEM_EFFECT)) {
                    event.setCanceled(true);
                    player.removeEffect(ModEffects.TOTEM_EFFECT);
                    
                    // Clear all potion/mob effects
                    player.removeAllEffects();
                    
                    // Set health to 1.0F (half a heart)
                    player.setHealth(1.0F);
                    
                    // Apply vanilla totem of undying effects: Regeneration II (900 ticks), Absorption II (100 ticks), Fire Resistance I (800 ticks)
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 900, 1));
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION, 100, 1));
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 800, 0));
                    
                    // Broadcast entity event status 35 (Totem of Undying use animation and sound)
                    player.level().broadcastEntityEvent(player, (byte) 35);
                    
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Your Eldritch blessing saved you from death!").withStyle(ChatFormatting.GOLD));
                }
            }
        }
    }

    private boolean isFishingRod(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof net.minecraft.world.item.FishingRodItem;
    }

    private boolean checkAndCancelFishingRodUse(PlayerInteractEvent event, Player player) {
        if (player != null && !player.hasPermissions(2)) {
            net.minecraft.world.item.ItemStack stack = event.getItemStack();
            if (isFishingRod(stack)) {
                Faction role = getPlayerFaction(player.getUUID());
                if (role != Faction.FISHERIES) {
                    if (event instanceof net.neoforged.bus.api.ICancellableEvent cancellable) {
                        cancellable.setCanceled(true);
                    }
                    if (event instanceof PlayerInteractEvent.RightClickBlock rcb) {
                        rcb.setCancellationResult(InteractionResult.FAIL);
                    } else if (event instanceof PlayerInteractEvent.RightClickItem rci) {
                        rci.setCancellationResult(InteractionResult.FAIL);
                    } else if (event instanceof PlayerInteractEvent.EntityInteract ei) {
                        ei.setCancellationResult(InteractionResult.FAIL);
                    } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific eis) {
                        eis.setCancellationResult(InteractionResult.FAIL);
                    }
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "You must have the FISHERIES role to use fishing rods!"
                    ).withStyle(ChatFormatting.RED));
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        checkAndCancelFishingRodUse(event, event.getEntity());
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        checkAndCancelFishingRodUse(event, event.getEntity());
    }

    @SubscribeEvent
    public void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        checkAndCancelFishingRodUse(event, event.getEntity());
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
            net.minecraft.commands.Commands.literal("claimbounty")
                .then(net.minecraft.commands.Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                    .executes(context -> {
                        String idStr = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
                        net.minecraft.server.level.ServerPlayer player = context.getSource().getPlayerOrException();
                        
                        // Check cached bounty board position first (O(1)), fallback to area scan
                        BlockPos playerPos = player.blockPosition();
                        boolean nearBoard = false;
                        BlockPos cachedBoard = LAST_BOUNTY_BOARD_POS.get(player.getUUID());
                        if (cachedBoard != null
                                && Math.abs(playerPos.getX() - cachedBoard.getX()) <= 8
                                && Math.abs(playerPos.getY() - cachedBoard.getY()) <= 4
                                && Math.abs(playerPos.getZ() - cachedBoard.getZ()) <= 8
                                && player.level().getBlockState(cachedBoard).getBlock() instanceof net.satisfy.wildernature.core.block.BountyBoardBlock) {
                            nearBoard = true;
                        }
                        if (!nearBoard) {
                            // Fallback: scan nearby blocks for manual /claimbounty usage
                            for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-8, -4, -8), playerPos.offset(8, 4, 8))) {
                                if (player.level().getBlockState(pos).getBlock() instanceof net.satisfy.wildernature.core.block.BountyBoardBlock) {
                                    nearBoard = true;
                                    break;
                                }
                            }
                        }
                        
                        if (!nearBoard) {
                            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("You must be near a Bounty Board to claim a contract."));
                            return 0;
                        }
                        
                        java.util.UUID bountyId;
                        try {
                            bountyId = java.util.UUID.fromString(idStr);
                        } catch (IllegalArgumentException e) {
                            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Invalid bounty ID format."));
                            return 0;
                        }
                        
                        List<BountyApiFetcher.Bounty> bounties = BountyApiFetcher.getBounties();
                        BountyApiFetcher.Bounty target = null;
                        for (BountyApiFetcher.Bounty b : bounties) {
                            if (b.id().equals(bountyId)) {
                                target = b;
                                break;
                            }
                        }
                        
                        if (target == null) {
                            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Contract has expired or was not found."));
                            return 0;
                        }
                        
                        if (target.isClaimedBy(player.getUUID())) {
                            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("You have already accepted this contract!"));
                            return 0;
                        }
                        
                        BountyApiFetcher.Bounty finalTarget = target;
                        BountyApiFetcher.claimBountyAsync(player, bountyId, () -> {
                            // On Success
                            // Create custom contract paper stack
                            net.minecraft.world.item.ItemStack contract = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
                            contract.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(finalTarget.title()).withStyle(ChatFormatting.GOLD));
                            
                            java.util.List<net.minecraft.network.chat.Component> lore = new java.util.ArrayList<>();
                            java.util.List<String> wrappedDesc = wrapTextWithOffset(finalTarget.description(), 35, 0);
                            for (String line : wrappedDesc) {
                                lore.add(net.minecraft.network.chat.Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                            }
                            lore.add(net.minecraft.network.chat.Component.literal("Reward: " + finalTarget.prizeAmount() + " " + finalTarget.prizeType()).withStyle(ChatFormatting.DARK_GREEN));
                            contract.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
                            
                            if (!player.getInventory().add(contract)) {
                                player.drop(contract, false);
                            }
                            
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Contract '" + finalTarget.title() + "' accepted successfully! Details added to your inventory.")
                                    .withStyle(ChatFormatting.GREEN));
                            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                        }, (error) -> {
                            // On Failure
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Failed to accept contract: " + error).withStyle(ChatFormatting.RED));
                        });
                        return 1;
                    })
                )
        );
    }

    private void openBountyBoardBook(ServerPlayer player, java.util.List<BountyApiFetcher.Bounty> bounties) {
        java.util.List<Filterable<Component>> pages = new ArrayList<>();

        // Page 1: Welcome/Index
        net.minecraft.network.chat.MutableComponent welcome = Component.literal("=== BOUNTY BOARD ===\n\n").withStyle(ChatFormatting.GOLD);
        welcome.append(Component.literal("Flip the pages to view active contracts.\n\n").withStyle(ChatFormatting.BLACK));
        welcome.append(Component.literal("Click ").withStyle(ChatFormatting.BLACK));
        welcome.append(Component.literal("[ACCEPT CONTRACT]").withStyle(ChatFormatting.GREEN));
        welcome.append(Component.literal(" on any page to claim it.\n\n").withStyle(ChatFormatting.BLACK));
        welcome.append(Component.literal("Available today: " + bounties.size()).withStyle(ChatFormatting.DARK_BLUE));
        pages.add(Filterable.passThrough(welcome));

        // Add a page for each bounty
        for (BountyApiFetcher.Bounty bounty : bounties) {
            int maxLines = 15;
            int maxLineWidth = 25;

            // Compute reward lines wrapped dynamically
            String rewardText = bounty.prizeAmount() + " " + bounty.prizeType();
            java.util.List<String> rewardLinesWrapped = wrapTextWithOffset(rewardText, maxLineWidth, 8); // "Reward: " is 8 chars
            int rewardLines = Math.max(1, rewardLinesWrapped.size());

            // Total budget for title + description = maxLines - 7 - rewardLines
            int combinedBudget = maxLines - 7 - rewardLines;
            if (combinedBudget < 2) {
                combinedBudget = 2; // fallback safety
            }

            // Allocate at most 2 lines for the title
            int maxTitleLines = 2;
            java.util.List<String> tempTitleLines = wrapTextWithOffset(bounty.title(), maxLineWidth, 7); // "Title: " is 7 chars
            int titleLines = Math.min(maxTitleLines, Math.max(1, tempTitleLines.size()));

            // Remaining budget goes to description
            int descBudget = combinedBudget - titleLines;
            if (descBudget < 1) {
                descBudget = 1; // fallback safety
            }

            // Apply truncation/wrapping
            String finalTitle = truncateTextWithOffset(bounty.title(), maxLineWidth, 7, titleLines);
            String finalDesc = truncateTextWithOffset(bounty.description(), maxLineWidth, 0, descBudget);
            String finalReward = String.join("\n", rewardLinesWrapped);

            net.minecraft.network.chat.MutableComponent page = Component.literal("=== CONTRACT ===\n\n").withStyle(ChatFormatting.GOLD);
            page.append(Component.literal("Title: ").withStyle(ChatFormatting.DARK_GRAY));
            page.append(Component.literal(finalTitle + "\n\n").withStyle(ChatFormatting.DARK_BLUE));

            page.append(Component.literal("Objective:\n").withStyle(ChatFormatting.DARK_GRAY));
            page.append(Component.literal(finalDesc + "\n\n").withStyle(ChatFormatting.ITALIC, ChatFormatting.BLACK));

            page.append(Component.literal("Reward: ").withStyle(ChatFormatting.DARK_GRAY));
            page.append(Component.literal(finalReward + "\n\n").withStyle(ChatFormatting.DARK_GREEN));

            if (bounty.isClaimedBy(player.getUUID())) {
                page.append(Component.literal("[ ALREADY CLAIMED ]").withStyle(ChatFormatting.RED));
            } else {
                page.append(Component.literal("[ ACCEPT CONTRACT ]")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claimbounty " + bounty.id()))
                    )
                );
            }
            pages.add(Filterable.passThrough(page));
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
            Filterable.passThrough("Bounty Board"),
            "Bounty Board",
            0,
            pages,
            true
        ));

        // Swapping trick
        ItemStack originalItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        
        // Update slot on client
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
            0, player.inventoryMenu.getStateId(), 36 + player.getInventory().selected, book
        ));
        
        // Use NeoForge openItemGui method to open the book UI
        player.openItemGui(book, InteractionHand.MAIN_HAND);
        
        // Restore hand on server
        player.setItemInHand(InteractionHand.MAIN_HAND, originalItem);
        
        // Update slot on client back to original
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
            0, player.inventoryMenu.getStateId(), 36 + player.getInventory().selected, originalItem
        ));
    }

    private static java.util.List<String> wrapTextWithOffset(String text, int maxLineWidth, int firstLineOffset) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] paragraphs = text.split("\n", -1);
        boolean isFirst = true;
        for (String paragraph : paragraphs) {
            int index = 0;
            while (index < paragraph.length()) {
                int limitOffset = isFirst ? firstLineOffset : 0;
                int currentMax = maxLineWidth - limitOffset;
                if (currentMax <= 0) {
                    currentMax = 1;
                }
                if (paragraph.length() - index <= currentMax) {
                    lines.add(paragraph.substring(index));
                    isFirst = false;
                    break;
                }
                int limit = index + currentMax;
                int spaceIndex = paragraph.lastIndexOf(' ', limit);
                if (spaceIndex > index) {
                    lines.add(paragraph.substring(index, spaceIndex));
                    index = spaceIndex + 1;
                } else {
                    lines.add(paragraph.substring(index, limit));
                    index = limit;
                }
                isFirst = false;
            }
            if (paragraph.isEmpty()) {
                lines.add("");
            }
            isFirst = false;
        }
        return lines;
    }

    private static String truncateTextWithOffset(String text, int maxLineWidth, int firstLineOffset, int allowedLines) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (allowedLines <= 0) {
            return "...";
        }
        java.util.List<String> lines = wrapTextWithOffset(text, maxLineWidth, firstLineOffset);
        if (lines.size() <= allowedLines) {
            return String.join("\n", lines);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < allowedLines - 1; i++) {
            sb.append(lines.get(i)).append("\n");
        }
        String lastLine = lines.get(allowedLines - 1);
        int currentMax = maxLineWidth;
        if (allowedLines == 1) {
            currentMax = maxLineWidth - firstLineOffset;
            if (currentMax <= 0) currentMax = 1;
        }
        if (lastLine.length() > currentMax - 3) {
            int truncateLen = currentMax - 3;
            if (truncateLen < 0) truncateLen = 0;
            lastLine = lastLine.substring(0, truncateLen);
        }
        sb.append(lastLine).append("...");
        return sb.toString();
    }
}

