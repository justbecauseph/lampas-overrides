# Faction Rules and Overrides Implementation

This document enumerates the custom rules, restrictions, and behaviors applied to players based on their faction memberships in the `lampas-overrides` Minecraft mod.

---

## 1. Overview of Factions

The factions are defined as enum constants in [Faction.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/Faction.java):

*   **`NAVY`**: No active overrides in the codebase.
*   **`TOURISM`**: No active overrides in the codebase.
*   **`LMI`**: Lampas Marine Institute (controls metal smelting and metallurgy).
*   **`MERCHANTS`**: Controls commerce and trade, restricted from heavy weaponry.
*   **`RELIGION`**: Exclusive access to arcane/eldritch artifacts.
*   **`FISHERIES`**: Controls brewing, smoking, food prep, and fishing (only faction allowed to use fishing rods).
*   **`NOBILITY`**: Exclusive control over city tax infrastructure.
*   **`NONE`**: Default state for players with no faction assigned.

---

## 2. Core Mechanics

### 2.1 Faction Syncing & Caching
Faction statuses are fetched from an external API and handled asynchronously in [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java):
*   **API Retrieval**: On login, a player's faction is requested via a HTTP GET call to the endpoint configured by `ModConfig.PLAYER_API_URL` (with `?uuid=<player_uuid>`).
*   **Caching**: Player factions are stored in the memory map `PLAYER_FACTIONS`.
*   **Automatic Refresh (TTL)**: If a faction query occurs and the cached data is older than 30 seconds (30,000ms), a background refresh is dispatched asynchronously.
*   **Cleanup**: On player logout, the cache entries are removed to prevent memory leaks.

### 2.2 OP / Admin Bypass
All faction restrictions (block interactions, item usage, crafting rules) check whether the player has operator permissions:
*   Players with **permission level 2 or greater** (`player.hasPermissions(2)`) bypass all faction checks and can interact/craft freely.

### 2.3 Player State Webhooks
Logins and logouts trigger async HTTP POST requests to `ModConfig.WEBHOOK_URL` containing `{uuid, username, event: "login"/"logout"}` with the authorization header `x-api-key`.

---

## 3. Faction-Specific Rules and Overrides

### 3.1 LMI (Lampas Marine Institute)
*   **Furnace Restrictions**:
    *   **Interaction**: Only players in the `LMI` faction can interact (right-click) with Furnace (`Blocks.FURNACE`), Blast Furnace (`Blocks.BLAST_FURNACE`), or any block with `furnace` in its namespace/path (excluding minecarts).
    *   **Crafting**: Only `LMI` faction members can craft Furnace/Blast Furnace items.
*   **Source References**: [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java#L139-L143) and [CraftingMenuMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/CraftingMenuMixin.java#L74-L85).

### 3.2 FISHERIES
*   **Brewing & Smoker Restrictions**:
    *   **Interaction**: Only `FISHERIES` members can interact with Brewing Stands (`Blocks.BREWING_STAND`) and Smokers (`Blocks.SMOKER`).
    *   **Crafting**: Only `FISHERIES` members can craft Brewing Stands (`Items.BREWING_STAND`) and Smokers (`Items.SMOKER`).
*   **Fishing Rod Restrictions**:
    *   Only `FISHERIES` members are allowed to use Fishing Rods.
    *   Attempts to right-click blocks/items, or interact with entities/specific entities with a fishing rod in hand are canceled for non-Fisheries players.
    *   **Target Criteria**: Any item that extends `FishingRodItem`.
*   **Fishing Luck and Speed Passive Buffs**:
    *   `FISHERIES` faction members receive a passive boost to their fishing efficiency and quality.
    *   When casting a line, the generated `FishingHook` is intercepted via a mixin:
        *   `lureSpeed` is increased by `100` ticks (equivalent to **+1 level of Lure** / reducing bite wait time by 5 seconds).
        *   `luck` is increased by `2` (equivalent to **+2 levels of Luck of the Sea**).
    *   These passive buffs fully stack with Lure and Luck of the Sea enchantments applied to the player's fishing rod.
*   **Source References**:
    *   Blocks: [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java#L144-L153) & [CraftingMenuMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/CraftingMenuMixin.java#L87-L93)
    *   Fishing Rod Restriction: [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java#L327-L352)
    *   Fishing Rod Buffs: [FishingHookMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/FishingHookMixin.java)

### 3.3 NOBILITY
*   **Tax Block Restrictions**:
    *   **Interaction**: Only `NOBILITY` members can interact with the Lightman's Currency Tax Block (`lightmanscurrency:tax_block`).
    *   **Crafting**: Only `NOBILITY` members can craft the Tax Block.
*   **Source References**: [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java#L155-L162) and [CraftingMenuMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/CraftingMenuMixin.java#L95-L97).

### 3.4 MERCHANTS
*   **Combat Crafting Restrictions**:
    *   `MERCHANTS` are explicitly **blocked** from crafting combat items.
    *   **Impacted items**: Any instance of `SwordItem`, `BowItem`, `CrossbowItem`, `TridentItem`, or `MaceItem`.
*   **Source References**: [CraftingMenuMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/CraftingMenuMixin.java#L68-L72) and [CraftingMenuMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/CraftingMenuMixin.java#L102-L109).

### 3.5 RELIGION
*   **Arcane Book of Eldritch Access**:
    *   Only `RELIGION` members can use [BookOfEldritchItem.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/BookOfEldritchItem.java).
    *   **Item Details**:
        *   Recipe: Shapeless crafting using 1 `minecraft:book` and 1 `lampas_overrides:eldritch_stone` (defined in [book_of_eldritch.json](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/resources/data/lampas_overrides/recipe/book_of_eldritch.json)).
        *   Use Effect: Applies the custom Totem of Undying effect (`ModEffects.TOTEM_EFFECT`) for 1 hour (72,000 ticks) to the user and nearby players in a 10x10 area (5-block radius inflation).
        *   Consumes the item upon use (unless in creative mode).
        *   Unauthorized users receive the system message: *"Only members of the RELIGION faction can use this book."*
    *   **Totem of Undying Effect Mechanics**:
        *   If a player possessing the `totem_of_undying` status effect dies, the death is canceled (`LivingDeathEvent`).
        *   The effect is removed, all other active status effects are cleared, and their health is set to `1.0F` (half a heart).
        *   Vanilla Totem of Undying recovery buffs are applied: Regeneration II (900 ticks), Absorption II (100 ticks), and Fire Resistance I (800 ticks).
        *   The Totem of Undying use animation and sound are broadcast (Entity Event Status 35).
        *   The player is sent the message: *"Your Eldritch blessing saved you from death!"*
*   **Source References**: [BookOfEldritchItem.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/BookOfEldritchItem.java) and [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java#L256-L281).
