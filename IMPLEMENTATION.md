# Faction Rules and Overrides Implementation

This document enumerates the custom rules, restrictions, and behaviors applied to players based on their faction memberships in the `lampas-overrides` Minecraft mod.

---

## 1. Overview of Factions

The factions are defined as enum constants in [Faction.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/Faction.java):

*   **`NAVY`**: Controls the Cyan Sharestone.
*   **`TOURISM`**: Controls the Magenta Sharestone.
*   **`LMI`**: Lampas Marine Institute (controls metal smelting and metallurgy).
*   **`MERCHANTS`**: Controls commerce and trade, restricted from heavy weaponry, has exclusive access to the Large Black Vending Machine, and controls the Lime Sharestone.
*   **`RELIGION`**: Exclusive access to arcane/eldritch artifacts, and controls the Red Sharestone.
*   **`FISHERIES`**: Controls brewing, smoking, food prep, fishing (only faction allowed to use fishing rods), and controls the Orange Sharestone.
*   **`NOBILITY`**: Exclusive control over city tax infrastructure, and controls the Purple Sharestone.
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
*   **Large Black Vending Machine Restrictions**:
    *   Only `MERCHANTS` members can interact with or craft the Large Black Vending Machine (`lightmanscurrency:vending_machine_large_black`).
*   **Source References**:
    *   Combat Restrictions: [CraftingMenuMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/CraftingMenuMixin.java#L68-L72) & [CraftingMenuMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/CraftingMenuMixin.java#L107-L114)
    *   Vending Machine Crafting: [CraftingMenuMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/CraftingMenuMixin.java#L99-L101)
    *   Vending Machine Interaction: [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java#L162-L167)

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

---

## 4. Bounty Board System

The Bounty Board system enables players to view and claim custom community/server-defined bounties posted on the web portal database directly within Minecraft by interacting with a WilderNature Bounty Board block.

### 4.1 Next.js Bounty API
The web application hosts REST endpoints at `/api/minecraft/bounties` (defined in [route.ts](file:///C:/Users/markj/source/repos/lampas/app/src/app/api/minecraft/bounties/route.ts)), which are secured by verifying the `x-api-key` header matching the server's configured key.
*   **`GET`**: Returns a list of all `OPEN` bounties. Each entry includes:
    *   `id`: The unique bounty UUID.
    *   `title` / `description`: Text parameters of the bounty.
    *   `prizeType` / `prizeAmount`: Rewards configured on the database.
    *   `claims`: A list of player UUID strings that have already claimed/accepted the bounty.
*   **`POST`**: Accepts `{ uuid, bountyId }` to claim/accept a bounty.
    *   Validates player existence and ensures the bounty is currently `OPEN`.
    *   Prevents posters from claiming their own bounties.
    *   Ensures a player cannot claim a bounty multiple times.
    *   Creates a new `bountyClaim` record and registers a database `AuditLog` entry.

### 4.2 Decoupled API Fetcher
The Java class [BountyApiFetcher.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/BountyApiFetcher.java) communicates asynchronously with the web portal API.
*   **Configurable Endpoint**: Fetches the target API server endpoint directly from the `bountiesApiUrl` configuration setting (`ModConfig.BOUNTIES_API_URL`) inside `General Settings`.
*   **Bounty POJO**: Declares a lightweight, independent `Bounty` record containing the fields retrieved from the API, resolving any direct binary dependencies on WilderNature class files. It supports non-monetary rewards (e.g., `ITEM` and `OTHER` prize types) by storing `prizeItem` and `prizeOther` fields and exposing them directly as plain text via a formatting helper.
*   **Caching & TTL**: Implements a memory cache (`cachedBounties`) that expires every 15 seconds. Requests made within the 15-second TTL window receive the cached list immediately, avoiding API rate limits.
*   **Async Dispatch**: Dispatches HTTP GET and POST requests using Java 21's asynchronous `HttpClient.sendAsync`.

### 4.3 Virtual Written Book UI
When a player right-clicks a Bounty Board block (`lets-do-wildernature:bounty_board`), the default screen UI is bypassed. Instead, the right-click block interaction event handler in [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java) intercepts the event on both client and server sides, cancels the default action, and opens a temporary graphical book screen:
*   **Book Assembly**: Generates a virtual `Items.WRITTEN_BOOK` with pages formatted dynamically:
    *   *Welcome Page*: Explains how to navigate and accept active contracts.
    *   *Bounty Pages*: Creates a page for each active bounty containing details (Title, Objective, Reward).
    *   *Interaction ClickEvent*: Appends a clickable element running the command `/claimbounty <bountyId>`. If a bounty is already claimed by the player, it displays `[ ALREADY CLAIMED ]` instead.
    *   *Line-Wrapping & Page Budget Validation*: Implements server-side word-wrapping (at 19 characters/line to safely accommodate variable-width font sizes) and page height limits (maximum of 14 lines). To prevent double newlines from consuming too much vertical space, sections are separated by single newlines. This ensures that verbose bounty details never push the `[ ACCEPT CONTRACT ]` claim link off the page. The system dynamically allocates the remaining line budget to the title (max 2 lines) and objective description, truncating the text with an ellipsis (`...`) if they exceed their budget. Bold styling is omitted to maximize horizontal text space.
*   **Hand Swapping Trick**: To trigger the native Minecraft book reading overlay without permanently placing a written book in the player's inventory:
    1.  Temporarily replaces the player's main-hand item with the virtual book.
    2.  Sends a `ClientboundContainerSetSlotPacket` to force the client to recognize the book.
    3.  Calls NeoForge's `player.openItemGui` to open the screen.
    4.  Restores the player's original main-hand item on the server.
    5.  Sends another `ClientboundContainerSetSlotPacket` to sync the restored hand item back to the client.

### 4.4 Proximity-Restricted Claim Command
The `/claimbounty <id>` command is registered during the command registration event in [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java#L352-L429).
*   **Proximity Validation**: Before dispatching a claim query, the command verifies that the executing player is within an 8-block horizontal range and a 4-block vertical range of a valid `BountyBoardBlock`.
*   **API Execution**: Executes `BountyApiFetcher.claimBountyAsync` to claim the bounty.
*   **Reward/Contract Delivery**: On successful API acknowledgment:
    *   Fashions a custom `Items.PAPER` item with the bounty's title (colored gold) and the description and reward detailed in the item's lore components. Monetary rewards are formatted using "Aur" or "Aurs" labels (e.g., "1 Aur" or "99 Aurs").
    *   Applies a 35-character word-wrap to the description in the contract paper's lore so the tooltip breaks into readable lines without stretching off-screen, while keeping the full text unabridged (no truncation).
    *   Attempts to add the contract to the player's inventory, dropping it at their feet if full.
    *   Plays the `UI_TOAST_CHALLENGE_COMPLETE` sound to confirm success.

---

## 5. Waystones & Sharestones Overrides

These overrides govern the behaviors, costs, and availability of the Waystones mod elements.

### 5.1 Warp Stone Crafting Disablement
*   **Recipe Override**: The default crafting recipe for the Warp Stone (`waystones:warp_stone`) is overridden via a custom datapack recipe file located at [warp_stone.json (recipe)](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/resources/data/waystones/recipe/warp_stone.json).
*   **False Condition**: A `"neoforge:conditions"` array featuring `neoforge:false` is injected into the recipe and advancement files ([warp_stone.json (advancement)](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/resources/data/waystones/advancement/recipes/decorations/warp_stone.json)). This causes the recipe to be ignored by Minecraft's recipe manager, effectively disabling its crafting entirely.

### 5.2 Sharestone-to-Sharestone Free Warping
*   **Mixin Interception**: [InternalMethodsImplMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/InternalMethodsImplMixin.java) intercepts `resolveRequirements`.
*   **Warp Cost Override**: If both the source (`fromWaystone`) and destination (`targetWaystone`) blocks are sharestones (identified by registry paths ending with `_sharestone`), the mixin cancels the default cost calculation and returns `NoRequirement.INSTANCE`. This results in completely free teleportation between sharestones, and removes the cost tooltip/indicator on the UI buttons.

### 5.3 Faction-Restricted Colored Sharestones
*   **Usage Block**: To restrict colored sharestones to specific factions, interactions (right-clicking) are checked in [PlayerActivityListener.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/PlayerActivityListener.java#L187-L200) inside `getRequiredFactionForBlock`.
*   **Destination Check**: Teleporting to a faction-restricted sharestone from other locations is blocked in [InternalMethodsImplMixin.java](file:///C:/Users/markj/source/repos/lampas-overrides/src/main/java/town/lampas/overrides/mixin/InternalMethodsImplMixin.java). If a player attempts to teleport to a restricted sharestone without belonging to that faction, the mixin returns `new RefuseRequirement(...)` with a message stating: *"You must belong to the <FACTION> faction to use this sharestone."*
*   **Mappings**:
    *   **Cyan Sharestone** $\rightarrow$ `NAVY`
    *   **Magenta Sharestone** $\rightarrow$ `TOURISM`
    *   **Lime Sharestone** $\rightarrow$ `MERCHANTS`
    *   **Red Sharestone** $\rightarrow$ `RELIGION`
    *   **Orange Sharestone** $\rightarrow$ `FISHERIES`
    *   **Purple Sharestone** $\rightarrow$ `NOBILITY`

