# Encounter model and data provenance

Updated 2026-10-12. This is a **single-target, selected-phase sustained-attack comparison**, not a simulator of an entire boss kill or raid. A ranking does not account for movement, missed ticks, damage taken, healing, special-attack rotations, team roles, inventory switches or travel. The UI shows this boundary for every target. A stat row is not proof that every mechanic of that NPC is covered.

## Data

The bundled numerical dataset is pinned to OSRS Wiki DPS calculator revision [`89c3e25b344aea90d0189746e4b5f73dde0f0383`](https://github.com/weirdgloop/osrs-dps-calc/tree/89c3e25b344aea90d0189746e4b5f73dde0f0383/cdn/json):

| Dataset | Bundled coverage |
| --- | --- |
| NPC records | 2,860 stat variants across 2,817 IDs; previously 2,829 variants across 2,793 IDs |
| Searchable encounter choices | 2,871 after explicit Tormented Demon, Hueycoatl and Yama state choices |
| Equipment | 5,436 variants |
| Spells | 61 source rows; non-damage spells are not offered as damage attacks |
| Gear metadata fixes | 168 researched overrides; sources attached to each override |
| Gear awaiting metadata | 3 intermediate quest items excluded until eligibility, requirements and availability are resolved |

The loader uses the Wiki's actual Slayer flag, preserving distinct variants even when they share an NPC ID. It reads the separate light/standard/heavy ranged defences and elemental weakness fields. Searches cache normalized names and attributes instead of constructing them for every comparison. Unknown spell requirements fail closed rather than silently becoming level 1.

`scripts/refresh_wiki_data.py` refreshes numerical facts at an explicit immutable revision. It preserves existing prices and local eligibility fields, reapplies `scripts/gear_metadata_overrides.json`, retains the unresolved gear list across runs, and writes counts and uncompressed SHA-256 digests to `src/main/resources/com/bestdps/data/wiki-data-provenance.json`. Old price fields are fallback estimates, not current quotes. The separate live-price service handles current buying and selling.

NPC identity and numerical combat data originate from [OSRS Wiki contributors](https://oldschool.runescape.wiki/); see [Wiki attribution and copyright terms](https://oldschool.runescape.wiki/w/RuneScape:Copyrights). The upstream calculator code is GPL-3.0. Its implementation is not vendored or copied into this BSD plugin; Java encounter logic and the refresh script are original implementations of researched game rules. No descriptions or artwork are imported by the refresh script.

## Implemented encounter inputs

`EncounterContext` captures raid level, party size, path level, CoX Challenge Mode, highest party Combat/Hitpoints, target HP percentage and an optional current Defence level. `EncounterRules.scale` runs once before searching gear; the selected target remains immutable.

| Encounter | Model |
| --- | --- |
| Tombs of Amascut | Invocation modifies the **final defence roll**, not Defence level. HP scales with invocation, path and party; the core uses its separate invocation HP factor. Path affects HP, not defence. Shadow and Fang receive their ToA effects in the combat calculator. |
| Chambers of Xeric | Party size, highest Combat/HP and CM scaling, including solo-scaling scavengers/soldiers, linked magic/offensive/defensive levels, CM Tekton defence and special Olm HP/hand scaling. |
| Theatre of Blood | Party HP scaling with separate Entry Mode factors; Defence is not scaled by party size. Select the mode and phase variant. |
| Vardorvis | Defence interpolates with the entered HP percentage: normal 215–145, quest 180–130, awakened 268–181. No zero-Defence placeholder. |
| Manual drains | Current Defence override is applied after scaling. It does not simulate special attacks, regeneration or validate every encounter's drain floor. |

Sources: [ToA strategies](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies), [CoX](https://oldschool.runescape.wiki/w/Chambers_of_Xeric), [ToB](https://oldschool.runescape.wiki/w/Theatre_of_Blood), [Vardorvis](https://oldschool.runescape.wiki/w/Vardorvis). Exact scaling constants were cross-referenced against the [Wiki-maintained scaling reference](https://github.com/weirdgloop/osrs-dps-calc/tree/89c3e25b344aea90d0189746e4b5f73dde0f0383/src/lib/scaling).

## Target-specific rules

| Target | Rule and source |
| --- | --- |
| Zulrah | Halberds can reach it; ordinary melee cannot. Each hit above 50 is rerolled uniformly from 45 to 50. Separate colour variants keep distinct defences. [Wiki](https://oldschool.runescape.wiki/w/Zulrah) |
| Corporeal Beast | Non-magic, non-Corpbane attacks lose half damage. Eligible two-handed spears/halberds and Sunspear need stab. Ruby proc handling belongs to the combat calculator. [Wiki](https://oldschool.runescape.wiki/w/Corporeal_Beast), [Sunspear](https://oldschool.runescape.wiki/w/Sunspear) |
| Great Olm | Non-matching damage is divided by three: head ranged, left claw melee, right claw magic. Head comparison assumes the final phase. [Wiki](https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies) |
| Tekton | Ranged immune; magic damage divided by five. [Wiki](https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies) |
| Ice demon | Magic accuracy uses Defence level. Non-fire attacks deal one third; fire weakness is supplied by the data. [Wiki](https://oldschool.runescape.wiki/w/Ice_demon) |
| Glowing crystal | Ranged immune, magic damage divided by three. [Wiki](https://oldschool.runescape.wiki/w/Glowing_crystal) |
| CoX Guardian | Pickaxe melee only. Individual damage is multiplied by `(50 + Mining + pickaxe Mining requirement) / 150`, floored, with pickaxe requirement capped at 61. Uses base player Mining; temporary boosts are not captured. HP assumes party average Mining 99. [Wiki](https://oldschool.runescape.wiki/w/Guardian_(Chambers_of_Xeric)) |
| Warden phase 2 | Magic/ranged only, selected style assumed unprotected. Combat calculator uses the phase's accuracy-to-damage formula. [Wiki](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies) |
| Warden core | Melee maximum hits; comparison shows core damage. The Warden loses five times the core damage. Exposure timing and special energy are outside the ranking. [Wiki](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies) |
| Verzik phase 1 | Defence-based magic defence; non-Dawnbringer hits capped by independent uniform `0..10` melee or `0..3` ranged/magic rolls. Dawnbringer normal attacks are supported separately from its special. [Wiki](https://oldschool.runescape.wiki/w/Verzik_Vitur) |
| Tormented Demon | Explicit Shielded, Defenceless (still shielded), and Unshielded bonus-hit choices. Non-demonbane/non-abyssal shield damage is 80%; defenceless/unshielded accuracy is guaranteed. The one unshielded hit adds `speed² − 16` for crush, heavy ranged or cast spells and uses a four-tick interval against one demon. It is **one hit**, not permanent sustained DPS. [Wiki](https://oldschool.runescape.wiki/w/Tormented_Demon/Strategies) |
| Hueycoatl | Separate body, head and tail. Tail damage uses random `0..4` limits, or `0..9` for dominant crush/earth spells; dominant crush converts misses and zeros to one. Explicit pillar variants multiply damage by 1.3 after the cap. Shielded/broken parts cannot be ranked. [Wiki](https://oldschool.runescape.wiki/w/The_Hueycoatl/Strategies) |
| Yama | Normal variants explicitly distinguish tank melee/ranged (-30 magic defence) and tank magic (+60); phase 3 uses +81. Demonbane effect bonus is increased by 20%. Pre-teleport resistance, team switching and unselected contract effects are excluded. [Wiki](https://oldschool.runescape.wiki/w/Yama/Strategies) |
| Kraken/cave kraken | No melee; ranged damage divided by seven with minimum one on a successful hit. Whirlpool state cannot be ranked. [Wiki](https://oldschool.runescape.wiki/w/Kraken) |
| Fragment of Seren | Defence-based magic defence and per-hit random caps 22–24. [Wiki](https://oldschool.runescape.wiki/w/Fragment_of_Seren) |
| Turoth/kurask | Require leaf-bladed melee, broad ammunition or Magic Dart. [Wiki](https://oldschool.runescape.wiki/w/Leafy_(attribute)) |
| Warriors' Guild / Fire Warrior / Fareed | Guild cyclopes and animated armour require melee. Fire Warrior requires ranged with ice arrows equipped. Fareed magic requires water spells; melee requires ice gloves. [Guild](https://oldschool.runescape.wiki/w/Warriors%27_Guild), [Cyclops](https://oldschool.runescape.wiki/w/Cyclops), [Fire Warrior](https://oldschool.runescape.wiki/w/Fire_Warrior_of_Lesarkus), [Fareed](https://oldschool.runescape.wiki/w/Fareed) |
| Salarin the twisted | Strike spells only; successful hits use the highest unlocked bolt tier (9/10/11/12), with the Elemental amulet or matching elemental amulet adding 2. Normal accuracy/splashing applies. [Wiki](https://oldschool.runescape.wiki/w/Salarin_the_twisted) |
| Vampyres | Tier-specific weapon eligibility, silver/vampyrebane/Efaritay effects, full-damage silver tier-2 attacks and Efaritay regular-weapon half damage. Finishing actions and cremation mastery are excluded. [Wiki](https://oldschool.runescape.wiki/w/Vampyre_(attribute)) |
| Slagilith | Damage divided by three unless a pickaxe is equipped, including spellcasts. It is not wholly immune to other weapons. [Wiki](https://oldschool.runescape.wiki/w/Slagilith) |
| Nightmare totems | Magic damage doubled. [Wiki](https://oldschool.runescape.wiki/w/The_Nightmare/Strategies) |
| Style-immune NPCs | Dusk, flying/unreachable targets, aviansies and small Nylocas enforce applicable attack restrictions. [Dusk](https://oldschool.runescape.wiki/w/Dusk), [Aviansie](https://oldschool.runescape.wiki/w/Aviansie), [Nylocas](https://oldschool.runescape.wiki/w/Nylocas) |

For random caps, expected damage and displayed maximum hit use separate functions. Showing the expected cap as a maximum would understate Zulrah, Verzik and Hueycoatl maximum hits.

The current live Wiki TD strategies page says the one unshielded attack uses **four ticks**, while the inspected calculator revision still subtracts one tick. This implementation follows the live Wiki page. This is one reason neither dataset presence nor agreement with another calculator is treated as universal gameplay proof.

## Deliberate boundaries

Changing-prayer NPCs require an explicit combat style for the currently vulnerable phase; an `Any` search cannot return one alleged whole-fight setup for Akkha, Nylocas bosses, demonic/tormented demons or Warden phase 2.

Invulnerable, immune, whirlpool, teleporting and broken/defeated states are excluded where identified. Muspah's prayer shield, Tanglefoot's skill-dependent formula, Zalcano, rotating elemental mother/Chronozon mechanics, Eclipse clone timing, Sire phase 1, Barbarian Assault calls, Gauntlet equipment, PvM Arena and seasonal mode modifiers remain excluded. Their UI messages explain why.

The Dagannoth Kings use their supplied defensive stats; their combat-style weaknesses are not blanket immunities. [Wiki strategies](https://oldschool.runescape.wiki/w/Dagannoth_Kings/Strategies).

Other phase bosses carry visible assumptions: Hydra is already vent-weakened; Duke/Bloat/Hespori/Nightmare/Muspah/Sol/Leviathan/Araxxor comparisons use the selected attackable state and do not estimate mechanic uptime. Doom of Mokhaiotl uses the selected normal form and delve stats; demonbane shield phases are not represented by its base variant.

No claim is made that this dataset enumerates every NPC released after the pinned revision, that every existing NPC mechanic is covered, or that one equipment set is globally best across every fight. The live client has not been used to verify every variant. Unsupported models should be added with an explicit state, primary source and correct hit distribution before removing their boundary.
