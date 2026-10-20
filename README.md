# PvPSMP (Paper plugin)

Build: `mvn clean package` -> `target/PvPSMP.jar` (drop in `plugins/`).
Set `<paper.version>` in `pom.xml` to the Paper API your server runs (spears need 1.21.11+/26.x).
Keep the `keepInventory` gamerule **false**; the plugin handles death drops itself.

## Implemented
- Abilities (Axe / Sword / Mace / Spear), 1 at a time, rolled with the Ability Book
  (D G D / G Nautilus G / D G D; D = diamond block, G = gold block; stack 5; 20 min cooldown). Cooldowns show on the action bar.
- Shield needs 2 axe hits; Shield Breaker potions (brewing below) make any hit count as 1 or 2 axe hits.
- Lifesteal (kill = +1 heart, death by player = -1 heart), custom Strength (starts at 2, victim loses 1 and drops a Strength Vial).
- Leaderboard `/leaderboard`, kill-farming ban (3h) after 7 kills of the same player while "together a lot".
- Normal deaths keep items/XP; deaths in PvP drop everything.
- Combat tag (10s): no pearls/stasis, no logout (logging out kills you), only /msg and /w, no elytra.
- Custom brewing (OP Potion -> Shield Breaker I -> redstone 6:00 / glowstone II / gunpowder splash).
- Celestified nuggets (2.8%) / upgrade template (0.8%, ancient city only), 9 nuggets -> ingot,
  template duplication with ancient debris, smithing to Celestified armor.
- More ancient debris (Nether populator), diamond veins in deep dark chunks, more trapping materials in chests.
- Attribute swapping: nothing to do; the plugin does not block it.

## Assumptions / things to know
- Ability effects only trigger when the player owns that ability AND holds the matching weapon.
- Mace "success" = a smash (fall > 1.5 blocks). Smash damage x2, 8 dmg + knockback to everything within 5 blocks, 10s cooldown.
- Sword: each crit adds attack speed until the attack cooldown reaches 0.5s; a non-crit hit resets the streak.
- Spear: right-click toggles Rush (attribute modifier, not a potion). Speed ramps up while walking, drops when standing.
- "Stasis rods" is ambiguous: `combat.blocked-items` (default ENDER_PEARL) + pearl teleports are blocked in combat. Add materials in config.
- "Overworld ruins" chests: configured in `loot.nugget-tables` (ancient city, end city, ruined portal, underwater ruins).
- Custom base items: nugget = echo shard, ingot = copper ingot, template = netherite template (all tagged; blocked from vanilla recipes).
- Brewing stands refuse these items in vanilla, so the plugin places them manually and runs its own 20s timer; 1 fuel item per brew
  (blaze powder; breeze rod for Shield Breaker).
- Custom Strength damage per level defaults to 1.5 (vanilla would be 3.0) because everyone starts at level 2. Change in config.
- Ability HUD is on the action bar (bottom-centre); vanilla has no bottom-left slot.

## Cannot be done by a plugin
Huge mountain generation and ancient cities under snowy mountains are terrain/worldgen. The bundled datapack
(`datapack/pvpsmp_worldgen`) lets ancient cities generate under snowy/stony peaks and more often. Copy it to
`world/datapacks/` BEFORE exploring new chunks, and adjust `pack_format` in `pack.mcmeta` to your server version.
Plugin worldgen only affects newly generated chunks.

I could not compile this here (no access to the Paper repo); if the compiler flags an API name for your exact Paper version, it is a one-line rename.

## Custom textures (ingot + nugget)
Resource pack is in `resourcepack/`. Zip the CONTENTS of that folder (pack.mcmeta must be at the zip root),
host it at a direct link, then in server.properties set `resource-pack=<link>`, `resource-pack-sha1=<sha1>`,
`require-resource-pack=true`. Nugget is now an iron nugget, ingot is a copper ingot (both with custom models).
To replace the art, overwrite the PNGs in `assets/pvpsmp/textures/item/` (16x16).

## New-player protection
New players get 30 online-minutes where they can't hurt or be hurt by players (melee, projectiles, splash potions).
`/protection` shows time left, `/protection off confirm` ends it early. Set `protection.minutes` in config (0 = off).

## Anti-griefing (chunk claims)
`/claim` claims the chunk you stand in (limit `claims.max-per-player`). `/claim trust <player>` lets a friend build on ALL your claims,
`/claim untrust`, `/claim remove`, `/claim info`. In other people's claims you can't break/place, open chests/doors, use buckets,
light fires, hurt animals/villagers/armor stands/item frames, or blow things up; liquids, pistons and hoppers can't cross the border.
Players can still fight each other inside claims. Ops (`pvpsmp.bypass`) ignore claims.
Limitations: no raiding mode, no claim-block purchase, claims are whole chunks only.

### Claim costs, limits and raiding (update)
Each claim costs `claims.cost-diamonds` diamonds. Everyone starts with `claims.base-limit` slots and gains +1 per
`claims.extra-claim-per-hours-played` hours of playtime (cap `claims.max-per-player`).
If the owner has been offline longer than `claims.raidable-after-offline-hours` (default 72h, 0 = never), anyone can
break/build/open chests there. Explosions never damage claims, raidable or not.

## Resource pack auto-download
Set `resource-pack.url` in config.yml (GitHub: `https://github.com/<user>/<repo>/releases/latest/download/PvPSMP-ResourcePack.zip`,
repo must be PUBLIC). Players are prompted/downloaded on join. pack.mcmeta uses only min_format/max_format (Minecraft 1.21.9+/26.x).
Important: pack.mcmeta and the `assets` folder must be at the ROOT of the zip, not inside an extra folder.
