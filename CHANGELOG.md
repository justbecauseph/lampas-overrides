# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
