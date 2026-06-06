# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.21] - 2026-06-06

### Added
- **Fisheries Fishing Rod Restrictions**: Only players belonging to the `FISHERIES` faction can now cast and use fishing rods. Non-Fisheries players will have their use actions canceled and receive a system message.
- **Fisheries Passive Fishing Buffs**: Implemented `FishingHookMixin` to grant `FISHERIES` faction members a passive boost of **+100 lure ticks** (equivalent to **+1 Lure level**) and **+2 luck** (equivalent to **+2 Luck of the Sea**). These buffs stack additively with any enchantments on the fishing rod.
- **Technical Documentation**: Created [IMPLEMENTATION.md](file:///C:/Users/markj/source/repos/lampas-overrides/IMPLEMENTATION.md) detailing all active faction rules, interaction limitations, caching logic, and underlying mixins.

### Changed
- **LMI Faction Naming**: Updated the name of the `LMI` faction to **Lampas Marine Institute** in all documentation.

### Removed
- **Fisheries Seed Restriction**: Removed the restriction that limited seed planting and usage exclusively to the `FISHERIES` faction.

## [1.0.20] - 2026-06-06

### Added
- **Merchant Combat Crafting Restriction**: Restricted members of the `MERCHANTS` faction from crafting combat items (swords, bows, crossbows, tridents, and maces).

## [1.0.19] - 2026-06-06

### Added
- **NeonDeco Overrides**: Implemented `NeonBlockColorProcedureMixin` to override color procedures for the `neoncraft` mod.

## [1.0.18] - 2026-06-06

### Added
- New item models, textures/sprites, and language properties.

## [1.0.17] - 2026-06-06

### Added
- **Book of Eldritch Crafting**: Added a shapeless crafting recipe for the Book of Eldritch using 1 `minecraft:book` and 1 `lampas_overrides:eldritch_stone`.

## [1.0.16] - 2026-06-06

### Added
- **Book of Eldritch**: Added the Book of Eldritch item (`book_of_eldritch`) restricted to `RELIGION` members, which applies a custom Totem of Undying protection effect to players in a 10x10 area.

## [1.0.15] - 2026-06-06

### Added
- **Smoker Restrictions**: Restricted Smoker crafting and interaction to members of the `FISHERIES` faction.

## [1.0.14] - 2026-06-06

### Added
- **Forced Tax Acceptance**: Patched `TaxEntry` using `TaxEntryMixin` to force-default `forceAcceptance` to true for newly created Lightman's Currency tax collectors.

## [1.0.13] - 2026-06-05

### Added
- **Tax Collector Restrictions**: Restricted Lightman's Currency Tax Collector crafting and interactions to players with the `NOBILITY` faction.

## [1.0.12] - 2026-06-05

### Added
- **Furnace and Brewing Stand Restrictions**: Restricted Furnace and Brewing Stand crafting/interactions to `LMI` and `FISHERIES` factions respectively.

## [1.0.11] - 2026-06-05

### Added
- Blocked vanilla furnace crafting for non-LMI players.

## [1.0.10] - 2026-06-04

### Added
- **Portal Balance Sync**: Updated player portal account balance synchronization on withdrawal events.

## [1.0.9] - 2026-06-04

### Added
- **ATM Card Support**: Implemented mixin support for ATM cards inside merchant/transaction handlers.

## [1.0.8] - 2026-06-04

### Fixed
- Fixed a text formatting bug in the `scholar` mod's text editor on version 1.1.16.

## [1.0.7] - 2026-05-30

### Added
- **Bank Synchronization**: Integrated bank webhook to sync player bank balance updates and details.

## [1.0.6] - 2026-05-29

### Changed
- Reverted verbose logger configuration settings.

## [1.0.5] - 2026-05-28

### Added
- Logging of incoming and outgoing transaction payload data for API calls.

## [1.0.4] - 2026-05-26

### Added
- **Webhook Integration**: Added player activity listener for syncing player login/logout events to external webhooks.

## [1.0.3] - 2026-05-24

### Added
- **Emerald Coin Override**: Implemented `MerchantOfferMixin` to substitute vanilla emeralds with Lightman's Currency emerald coins in all villager trades.

## [1.0.2] - 2026-05-24

### Added
- GitHub Actions CI/CD workflow configurations (`build.yml`) for building and releasing.
