# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v1.0.72] - 2026-06-24

### Fixed
- fix: Farmer's Delight cooking pot glass-bottle duplication (Ube's Delight bottled drinks, e.g. ube milk tea) — suppress the ingredient bottle-remainder eject when the recipe output is itself a bottled drink
- fix: CookingPotBottleDupeMixin crashed FD on load in v1.0.71 (`@WrapOperation` receiver was `Object`; MixinExtras requires the exact `CookingPotBlockEntity` type). Added Farmer's Delight as a compileOnly dep and typed the receiver correctly

## [v1.0.69] - 2026-06-23

### Added / Changed
- feat: block auditor

## [v1.0.68] - 2026-06-22

### Added / Changed
- feat: meow

## [v1.0.67] - 2026-06-21

### Added / Changed
- feat: improve bounty paper

## [v1.0.66] - 2026-06-20

### Added / Changed
- feat: minim tax enforcement

## [v1.0.65] - 2026-06-19

### Added / Changed
- feat: pronounDB integration

## [v1.0.64] - 2026-06-19

### Added / Changed
- feat: update Jade to use customname
- feat: update book effect name

## [v1.0.63] - 2026-06-18

### Added / Changed
- feat: fix player names on live toggle

## [v1.0.62] - 2026-06-16

### Added / Changed
- feat: migrate applied effects live on /plaguemode toggle

## [v1.0.61] - 2026-06-16

### Added / Changed
- fix: apply ProjectileUtilMixin server-side and tidy faction/merchant mixins

## [v1.0.60] - 2026-06-16

### Added / Changed
- feat: rat

## [v1.0.59] - 2026-06-15

### Added / Changed
- perf: trim hot-path allocations and warm social cache on join

## [v1.0.58] - 2026-06-15

### Added / Changed
- feat: limit Black Sharestone usage to permission level 2+

## [v1.0.57] - 2026-06-15

### Added / Changed
- feat: build default api URL for clients

## [v1.0.56] - 2026-06-15

### Added / Changed
- feat: implement [LIVE] toggle

## [v1.0.55] - 2026-06-15

### Added / Changed
- feat: implement social links in chat

## [v1.0.54] - 2026-06-12

### Added / Changed
- feat: send player death events to The Faith

## [v1.0.53] - 2026-06-11

### Added / Changed
- feat: boost more lureSpeed

## [v1.0.52] - 2026-06-11

### Added / Changed
- fix: correct lureSpeed increment to +10 representing 10 seconds of reduction and update docs
- fix: correct lureSpeed increment to represent levels (+2) instead of ticks (+200) and update docs

## [v1.0.51] - 2026-06-11

### Added / Changed
- feat: increase lure speed bonus to 10s and update docs

## [v1.0.50] - 2026-06-11

### Added / Changed
- docs & feat: implement optimized fisheries boon status effect and update docs

## [v1.0.49] - 2026-06-08

### Added / Changed
- feat: change Player Ladder system default state to opt-in (disabled by default)

## [v1.0.48] - 2026-06-08

### Added / Changed
- fix: remove unnecessary canSerialize wrap operation from EntityMixin to resolve server startup crash on NeoForge

## [v1.0.47] - 2026-06-08

### Added / Changed
- build: add static refmap to resources to ensure it is always packaged in CI environments

## [v1.0.46] - 2026-06-08

### Added / Changed
- build: update net.neoforged.moddev to version 2.0.141

## [v1.0.45] - 2026-06-08

### Added / Changed
- build: migrate to net.neoforged.moddev and configure refmap

## [v1.0.44] - 2026-06-08

### Added / Changed
- feat: implement Player Ladder stacking/riding system and document rules

## [v1.0.43] - 2026-06-08

### Added / Changed
- fix: format bounty reward as NONE if prizeAmount is 0 or prizeType is NONE

## [v1.0.42] - 2026-06-08

### Added / Changed
- Allow all players to use fishing rods while keeping Fisheries faction buffs

## [v1.0.41] - 2026-06-08

### Added / Changed
- feat: parse poster field from bounty API and display From poster under title in book

## [v1.0.40] - 2026-06-08

### Added / Changed
- feat: optimize virtual book layout by removing Title and Objective labels, underlining title, and italicizing objective

## [v1.0.39] - 2026-06-08

### Added / Changed
- fix: adjust virtual written book page layout and character budgeting to prevent text overflowing page limits

## [v1.0.38] - 2026-06-08

### Added / Changed
- feat: disable crafting of Warp Stone, make sharestone warping free, and restrict colored sharestone usage by faction

## [v1.0.37] - 2026-06-07

### Added / Changed
- feat: use Aur/Aurs currency labels for monetary rewards and remove Loading Bounties chat message
- feat: parse prizeItem and prizeOther from bounties API and support non-monetary rewards directly as plain text

## [v1.0.36] - 2026-06-07

### Added / Changed
- feat: optimize performance, refactor, and implement bounty board book page line-budget validation & contract lore wrapping

## [v1.0.35] - 2026-06-07

### Added / Changed
- fix: replace printStackTrace with logger calls to prevent raw connection stack trace spam in logs

## [v1.0.34] - 2026-06-07

### Added / Changed
- feat: make bounties URL explicitly configurable via bountiesApiUrl config setting

## [v1.0.33] - 2026-06-07

### Added / Changed
- fix: resolve BountyBoardBlockMixin crash by migrating to right-click block interaction event handler

## [v1.0.32] - 2026-06-07

### Added / Changed
- feat: implement custom database-backed Bounty Board system and Modrinth Maven integration
- chore: Update CHANGELOG for version 1.0.31 [skip ci]
- feat: fix workflow [skip ci]

## [v1.0.31] - 2026-06-07

### Added / Changed
- feat: restrict Large Black Vending Machine crafting and interactions to MERCHANTS faction

## [v1.0.30] - 2026-06-06

### Added / Changed
- Auto-regenerate changelog via pre-commit hook

## [v1.0.29] - 2026-06-06

### Added / Changed

## [v1.0.28] - 2026-06-06

### Added / Changed

## [v1.0.27] - 2026-06-06

### Added / Changed
- Create CHANGELOG.md and configure release workflow to generate pre-releases using it

## [v1.0.26] - 2026-06-06

### Added / Changed
- Implement fisheries fishing rod restrictions & buffs, remove seed restrictions, and document everything in IMPLEMENTATION.md

## [v1.0.25] - 2026-06-06

### Added / Changed
- feat: optimize mixins

## [v1.0.24] - 2026-06-06

### Added / Changed
- feat: optimize interaction events

## [v1.0.23] - 2026-06-06

### Added / Changed
- feat: only Fisheries can interact with seeds

## [v1.0.22] - 2026-06-06

### Added / Changed
- feat: declare factions enum

## [v1.0.21] - 2026-06-06

### Added / Changed
- feat: refactor

## [v1.0.20] - 2026-06-06

### Added / Changed
- feat: disable crafting of some Combat items

## [v1.0.19] - 2026-06-06

### Added / Changed
- feat: override neondeco behavior

## [v1.0.18] - 2026-06-06

### Added / Changed
- new sprites, item json and lang changes

## [v1.0.17] - 2026-06-06

### Added / Changed
- feat: make The Book of Eldritch craftable

## [v1.0.16] - 2026-06-06

### Added / Changed
- feat: create The Book of Eldritch

## [v1.0.15] - 2026-06-06

### Added / Changed
- feat: restrict Smokers for only Fisheries

## [v1.0.14] - 2026-06-06

### Added / Changed
- feat: force trading machines and all trades for tax acceptance

## [v1.0.13] - 2026-06-05

### Added / Changed
- Restrict LightmansCurrency Tax Collector crafting and interactions to NOBILITY role

## [v1.0.12] - 2026-06-05

### Added / Changed
- Restrict furnace and brewing stand crafting and interactions to LMI and FISHERIES roles via player API

## [v1.0.11] - 2026-06-05

### Added / Changed
- feat: player cannot craft furnaces

## [v1.0.10] - 2026-06-04

### Added / Changed
- feat: update portal balance on widthrawal

## [v1.0.9] - 2026-06-04

### Added / Changed
- feat: support atm cards in merchants

## [v1.0.8] - 2026-06-04

### Added / Changed
- fix: scholars bug fix on 1.1.16

## [v1.0.7] - 2026-05-30

### Added / Changed
- feat: implement bank-sync

## [v1.0.6] - 2026-05-29

### Added / Changed
- chore: revert logging

## [v1.0.5] - 2026-05-28

### Added / Changed
- feat: Log the payload

## [v1.0.4] - 2026-05-26

### Added / Changed
- Add configuration and player activity listener for webhook integration

## [v1.0.3] - 2026-05-24

### Added / Changed
- Implement MerchantOffer Mixin to robustly override all trades using emeralds

## [v1.0.2] - 2026-05-24

### Added / Changed
- Update GitHub Actions workflow to build and publish releases
- Initial commit: custom trade overrides mod
