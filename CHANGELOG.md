# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0-pre.1] - 2026-06-06

### Added
- **Fisheries Fishing Rod Restrictions**: Only players belonging to the `FISHERIES` faction can now cast and use fishing rods. Non-Fisheries players will have their use actions canceled and receive a system message.
- **Fisheries Passive Fishing Buffs**: Implemented `FishingHookMixin` to grant `FISHERIES` faction members a passive boost of **+100 lure ticks** (equivalent to **+1 Lure level**) and **+2 luck** (equivalent to **+2 Luck of the Sea**). These buffs stack additively with any enchantments on the fishing rod.
- **Technical Documentation**: Created [IMPLEMENTATION.md](file:///C:/Users/markj/source/repos/lampas-overrides/IMPLEMENTATION.md) detailing all active faction rules, interaction limitations, caching logic, and underlying mixins.

### Changed
- **LMI Faction Naming**: Updated the name of the `LMI` faction to **Lampas Marine Institute** in all documentation.

### Removed
- **Fisheries Seed Restriction**: Removed the restriction that limited seed planting and usage exclusively to the `FISHERIES` faction.
