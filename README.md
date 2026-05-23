# Lampas Trade Overrides

A server-side NeoForge 1.21.1 mod designed for the Lampas server.

## Purpose

This mod dynamically overrides all villager and wandering trader trades to use the **Elandrian Aur** (Emerald Coin from Lightman's Currency - `lightmanscurrency:coin_emerald`) instead of standard vanilla `minecraft:emerald`. 

## How It Works

* The mod registers event handlers for `VillagerTradesEvent` and `WandererTradesEvent`.
* To ensure compatibility with modded professions and other custom trade additions, these event handlers run at **`EventPriority.LOWEST`**.
* It programmatically wraps each trade offer's `ItemListing` with a decorator that swaps out any emerald costs (`ItemCost`) or result rewards (`ItemStack`) for the emerald coin.
* Since it only mutates existing trade definitions using standard items present in the modlist, it is a **server-side only mod** — clients do not need to install it to connect.

## Compilation

To compile the mod and generate the JAR, run:

```bash
./gradlew build
```

The compiled mod JAR will be located under `build/libs/lampas_overrides-1.0.0.jar`.
