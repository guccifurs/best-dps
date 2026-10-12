# Encounter model and data provenance

Updated 2026-10-12. This is a **single-target, selected-phase sustained-attack comparison**, not a simulator of an entire boss kill or raid. A ranking does not account for movement, missed ticks, damage taken, healing, special-attack rotations, team roles, inventory switches or travel. The UI shows this boundary for every target. A stat row is not proof that every mechanic of that NPC is covered.

## Data

The bundled numerical dataset is pinned to OSRS Wiki DPS calculator revision [`89c3e25b344aea90d0189746e4b5f73dde0f0383`](https://github.com/weirdgloop/osrs-dps-calc/tree/89c3e25b344aea90d0189746e4b5f73dde0f0383/cdn/json):

| Dataset | Bundled coverage |
| --- | --- |
| NPC records | 2,860 stat variants across 2,817 IDs; previously 2,829 variants across 2,793 IDs |
| Searchable encounter choices | 2,885 after explicit Tormented Demon, Hueycoatl, Yama, Vorkath, Royal Titan, respiratory-system and Void Flare state choices |
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
| Tombs of Amascut | Invocation multiplies the **final defence roll** by `(250 + raid level) / 250`, floored after multiplication; it does not change Defence level. Kephri overlords are exempt from this roll multiplier. HP scales with invocation, path and party; the core starts at 4,500 HP and uses its separate invocation HP factor. Path affects HP, not defence. Shadow and Fang receive their ToA effects in the combat calculator. |
| Chambers of Xeric | Party size, highest Combat/HP and CM scaling, including solo-scaling scavengers/soldiers, linked magic/offensive/defensive levels, CM Tekton defence and special Olm HP/hand scaling. |
| Theatre of Blood | Party HP scaling with separate Entry Mode factors; Defence is not scaled by party size. Select the mode and phase variant. |
| Vardorvis | Defence interpolates with the entered HP percentage: normal 215–145, quest 180–130, awakened 268–181. No zero-Defence placeholder. |
| Manual drains | Current Defence override is applied after scaling. It does not simulate special attacks, regeneration or validate every encounter's drain floor. |

Sources: [ToA strategies](https://oldschool.runescape.wiki/w/Tombs_of_Amascut/Strategies), [CoX](https://oldschool.runescape.wiki/w/Chambers_of_Xeric), [ToB](https://oldschool.runescape.wiki/w/Theatre_of_Blood), [Vardorvis](https://oldschool.runescape.wiki/w/Vardorvis). Exact scaling constants were cross-referenced against the [Wiki-maintained scaling reference](https://github.com/weirdgloop/osrs-dps-calc/tree/89c3e25b344aea90d0189746e4b5f73dde0f0383/src/lib/scaling).

## Target-specific rules

| Target | Rule and source |
| --- | --- |
| Vorkath | Existing quest/post-quest choices mean the normal attack phase. Explicit Acid attack halves individual damage; Frozen invulnerable cannot be ranked. Salve (ei) stacks with dragonbane and Void, not Slayer headgear. Antifire, venom, acid movement and whole-kill bolt switches are not simulated. [Wiki](https://oldschool.runescape.wiki/w/Vorkath/Strategies), [Salve](https://oldschool.runescape.wiki/w/Salve_amulet(ei)) |
| Zombified Spawn | Crumble Undead kills the selected spawn outright when magic attack bonus is greater than -64; otherwise it splashes. This is a single kill action, not sustained training DPS. [Wiki](https://oldschool.runescape.wiki/w/Vorkath/Strategies) |
| Zulrah | Halberds can reach it; ordinary melee cannot. Each hit above 50 is rerolled uniformly from 45 to 50. Separate colour variants keep distinct defences. [Wiki](https://oldschool.runescape.wiki/w/Zulrah) |
| Corporeal Beast | Non-magic, non-Corpbane attacks lose half damage. Eligible two-handed spears/halberds, Sunspear, Osmumten’s fang, Thunder khopesh and King’s barrage need stab. Ruby proc handling belongs to the combat calculator. [Wiki](https://oldschool.runescape.wiki/w/Corporeal_Beast), [Corpbane list](https://oldschool.runescape.wiki/w/Corpbane_weapons) |
| Great Olm | Magic damage against the head/left claw and ranged damage against either claw are divided by three. Melee damage against the right claw is not divided. Head comparison assumes the final phase. [Wiki](https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies) |
| Tekton | Ranged immune; magic damage divided by five. [Wiki](https://oldschool.runescape.wiki/w/Chambers_of_Xeric/Strategies) |
| Ice demon | Magic accuracy uses Defence level. Non-fire, non-demonbane attacks deal one third; fire weakness is supplied by the data, and demonbane vulnerability is 115%. [Wiki](https://oldschool.runescape.wiki/w/Ice_demon) |
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
| Slagilith, including Nightmare Zone | Damage divided by three unless a pickaxe is equipped, including spellcasts. It is not wholly immune to other weapons. [Wiki](https://oldschool.runescape.wiki/w/Slagilith) |
| Nightmare totems | Magic damage doubled. [Wiki](https://oldschool.runescape.wiki/w/The_Nightmare/Strategies) |
| Style-immune NPCs | Dusk, flying/unreachable targets, aviansies and small Nylocas enforce applicable attack restrictions. [Dusk](https://oldschool.runescape.wiki/w/Dusk), [Aviansie](https://oldschool.runescape.wiki/w/Aviansie), [Nylocas](https://oldschool.runescape.wiki/w/Nylocas) |

For random caps, expected damage and displayed maximum hit use separate functions. Showing the expected cap as a maximum would understate Zulrah, Verzik and Hueycoatl maximum hits.

The current live Wiki TD strategies page says the one unshielded attack uses **four ticks**, while the inspected calculator revision still subtracts one tick. This implementation follows the live Wiki page. This is one reason neither dataset presence nor agreement with another calculator is treated as universal gameplay proof.

## Deliberate boundaries

Changing-prayer NPCs require an explicit combat style for the currently vulnerable phase; an `Any` search cannot return one alleged whole-fight setup for Akkha, Nylocas bosses, demonic/tormented demons Warden phase 2 or the Judge of Yama.

Invulnerable, immune, whirlpool, teleporting and broken/defeated states are excluded where identified. Muspah's prayer shield, Tanglefoot's skill-dependent formula, Zalcano, rotating elemental mother/Chronozon mechanics, Eclipse clone timing, Sire phase 1, Barbarian Assault calls, Gauntlet equipment, PvM Arena and seasonal mode modifiers remain excluded. Shielded Pest Control portals, submerged Temple Trekking tentacles, unattackable Sire tentacles, and the currently unmodelled Mad Angel/Maggot King counterattack or burn rules are also blocked. Their UI messages explain why.

The Dagannoth Kings use their supplied defensive stats; their combat-style weaknesses are not blanket immunities. [Wiki strategies](https://oldschool.runescape.wiki/w/Dagannoth_Kings/Strategies).

Other phase bosses carry visible assumptions: Hydra is already vent-weakened; Duke/Bloat/Hespori/Nightmare/Muspah/Sol/Leviathan/Araxxor comparisons use the selected attackable state and do not estimate mechanic uptime. Doom of Mokhaiotl uses the selected normal form and delve stats; demonbane shield phases are not represented by its base variant.

No claim is made that this dataset enumerates every NPC released after the pinned revision, that every existing NPC mechanic is covered, or that one equipment set is globally best across every fight. The live client has not been used to verify every variant. Unsupported models should be added with an explicit state, primary source and correct hit distribution before removing their boundary.

## Concrete ranking limits and spell fix

A fast search is not a global optimum proof: it shortlists gear (12 top ordinary items per slot and 48 weapons, plus retained alternatives), starts from several seeds, improves individual slots and budget exchanges, and stops at 24,000 loadout evaluations per style. Every NPC has not been verified against an exhaustive enumeration or a live fight.

A confirmed counterexample was fixed: at 41 current Magic (for example after a drain on a character who has completed Family Crest), Wind Bolt reaches base 12 and Chaos gauntlets add 3, while the newly unlocked Wind Blast has base 13. The old spell-family shortlist dropped Wind Bolt as soon as Wind Blast became available, and the gauntlets' zero raw magic stats also removed them from the gear pool. The shortlist now preserves these bolt alternatives below 59 Magic and retains the gauntlets when applicable. At 59 Magic all blast elements reach base 16, exceeding a bolt's 12 + 3. Sources: [Wind Bolt](https://oldschool.runescape.wiki/w/Wind_Bolt), [Wind Blast](https://oldschool.runescape.wiki/w/Wind_Blast), [Chaos gauntlets](https://oldschool.runescape.wiki/w/Chaos_gauntlets).

A Vorkath search with no owned bank items and untradeables disabled excludes Salve amulets and Ava's equipment by design. Its tradeable necklace and cape recommendations should not be compared with unrestricted BiS lists. An owned Salve (ei), with untradeables enabled, is included in the search. The calculator applies its 20% accuracy/damage bonus against Vorkath and keeps it mutually exclusive with Slayer headgear. This supports that specific rule, not a claim that every boss recommendation is verified best-in-slot.

The existing calculator was invoked directly for a bounded comparison (no test suite): 41 current Magic, no prayer, only Chaos gauntlets, post-quest Vorkath gave Wind Bolt max 15 / DPS 0.083442 versus Wind Blast max 13 / DPS 0.072507, at identical 0.033101 accuracy. This isolates the spell-tier error; it is not a recommendation to mage Vorkath. A second controlled comparison at unboosted 99 Ranged, no prayer, dragon hunter crossbow, diamond dragon bolts (e), Masori head/body/legs and no other gear gave Necklace of fangs 5.185336 DPS versus Salve (ei) 6.196477 DPS. This confirms Salve's calculation in that loadout, without proving the whole search globally optimal.


## October 12 reference comparison corrections

The reference is the deployed Wiki calculator revision named above. It is independent code, not an infallible ground truth. Comparisons must use matching target phases, equipment, spell stance and context. In particular, its Doom input must explicitly use `phase: "Normal"`; omitting that field activates the calculator's charging-phase guaranteed accuracy. The dataset sweep checks finite selected attacks per stat row, not all possible equipment, levels or fight phases.

Added source-backed encounter cases:

| Case | Applied rule | Primary reference |
| --- | --- | --- |
| Royal Titans, IDs 12596/14147 | Explicit melee-range, out-of-melee-range and Elemental blast choices. The last two multiply ranged attack rolls by six; out-of-range rejects melee. | [Strategies and quoted Jagex update](https://oldschool.runescape.wiki/w/Royal_Titans/Strategies) |
| Titan elementals, 14150/14151 | Magic success chance is `min(1, max(0, equipment magic bonus)/100 + .3)`, with mage Void's 1.45 multiplier. Wind Strike always hits. Successful magic attacks deal max damage; failed attacks remain misses. | [Fire elemental](https://oldschool.runescape.wiki/w/Fire_elemental_(Royal_Titans)), [Ice elemental](https://oldschool.runescape.wiki/w/Ice_elemental_(Royal_Titans)) |
| Fremennik warband, 12814/12815/12816 | Archer takes guaranteed melee max hits, seer ranged max hits, berserker magic max hits. | [Archer](https://oldschool.runescape.wiki/w/Fremennik_warband_archer), [Seer](https://oldschool.runescape.wiki/w/Fremennik_warband_seer), [Berserker](https://oldschool.runescape.wiki/w/Fremennik_warband_berserker) |
| ToA baboons, 11709–11715/11717 | Matching weaknesses guarantee max hits: brawler magic, thrower melee, mage/shaman/cursed ranged. | [Brawler](https://oldschool.runescape.wiki/w/Baboon_Brawler), [Thrower](https://oldschool.runescape.wiki/w/Baboon_Thrower), [Mage](https://oldschool.runescape.wiki/w/Baboon_Mage), [Shaman](https://oldschool.runescape.wiki/w/Baboon_Shaman), [Cursed](https://oldschool.runescape.wiki/w/Cursed_Baboon) |
| Phosani husks 9466/9467 and weakened parasite 9469 | Crush attacks guarantee maximum damage. Unweakened parasite does not; its extra HP from emerging damage is outside the base-HP comparison. | [Strategies](https://oldschool.runescape.wiki/w/Phosani%27s_Nightmare/Strategies) |
| Araxytes 13671/13675 | Noxious halberd guarantees maximum hits. Dominant crush or heavy ranged with ranged as the highest accuracy bonus does likewise. | [Acidic](https://oldschool.runescape.wiki/w/Acidic_Araxyte), [Mirrorback](https://oldschool.runescape.wiki/w/Mirrorback_Araxyte) |
| Respiratory system 5914 | Only ranged, magic or halberds reach it. When Sire is stunned, minimum damage is half max; demonbane except holy water kills on a successful hit. Protected state is blocked. | [Respiratory system](https://oldschool.runescape.wiki/w/Respiratory_system) |
| Judge of Yama 14180 | Unprotected attacks always hit maximum. An explicit style is required because its prayer responds to the last style used. | [Judge](https://oldschool.runescape.wiki/w/Judge_of_Yama) |
| Void Flare 14179 | 200% demonbane vulnerability. Magic and standard ranged guarantee max hits; Bloodied Blows removes this. Explicit shadow phase-3 HP choices are 71, or 81 with Divine Severance/Familiar Acquisition. | [Void Flare](https://oldschool.runescape.wiki/w/Void_Flare) |
| Zogre/Skogre/Slash Bash | Ordinary individual damage divided by four, Crumble Undead divided by two, comp ogre bow with brutal arrows full damage. | [Zogre](https://oldschool.runescape.wiki/w/Zogre), [Skogre](https://oldschool.runescape.wiki/w/Skogre), [Slash Bash](https://oldschool.runescape.wiki/w/Slash_Bash) |
| Nylocas Prinkipas 10803/10805/10806 | Explicit melee/magic/ranged variants reject other styles, which would heal it and reflect damage. | [Prinkipas](https://oldschool.runescape.wiki/w/Nylocas_Prinkipas) |

Twelve `EncounterReferenceRulesTest` checks cover these critical distinctions using real bundled item/NPC records, including invocation 1 producing a 25,100 defence roll from 25,000 while an overlord stays at 25,000; a 100-invocation solo core has 4,950 HP. These are targeted source assertions, not a proof of full gameplay coverage.

### Deliberate disagreements with the pinned oracle

| IDs | Current primary evidence | Plugin decision |
| --- | --- | --- |
| 14150/14151 | Titan elemental max hits require a successful hit; magic equipment affects success. | Keep misses. The oracle reports accuracy 0.4 with a basic staff but its final damage distribution treats the hit as guaranteed. |
| 14179 | Void Flare max-hit guarantee applies to magic and **standard ranged**, not melee/heavy/light ranged; Bloodied Blows removes it. | Enforce style and selected contract. |
| 5916 | [Sire Spawn update](https://oldschool.runescape.wiki/w/Spawn) guarantees **demonbane** hits. | Ordinary weapons use their calculated accuracy; the oracle currently guarantees every attack. |
| 11193 | [Quest flower](https://oldschool.runescape.wiki/w/Flower_(A_Night_at_the_Theatre)) explicitly does **not** always die in one hit. | Ordinary damage applies. Farming Guild flower 8584 retains the one-hit rule. |
| 6324/6385 | [NMZ Slagilith](https://oldschool.runescape.wiki/w/Slagilith_(Nightmare_Zone)) reduces non-pickaxe damage too. | Apply the reduction to these variants; the oracle matches only the plain name. |
| 9436 | [Nightmare totems](https://oldschool.runescape.wiki/w/Totem_(The_Nightmare)) take double magic damage. | Apply to both supplied rows sharing 9436, which the oracle's totem ID list omits. |
| 9446–9451/9470 | [Normal sleepwalkers](https://oldschool.runescape.wiki/w/Sleepwalker_(monster)) and [Phosani sleepwalkers](https://oldschool.runescape.wiki/w/Sleepwalker_(Phosani%27s_Nightmare)) die from any positive damage. | Model the successful kill as their HP removed; displayed rate is a kill-action comparison. The oracle leaves ordinary uncapped damage. |
| Warden active IDs 11753/11754/11756/11757 | [Elidinis](https://oldschool.runescape.wiki/w/Elidinis%27_Warden) and [Tumeken](https://oldschool.runescape.wiki/w/Tumeken%27s_Warden) both use Protect from Melee plus another prayer while active. These rows are phase 2; the charging phase-1 IDs are 11746–11749. | Reject melee; require an explicit currently unprotected ranged/magic style. An oracle fixture allowing melee does not represent an attackable phase. |
| Tormented Demon variants | Live strategies specify four ticks for the one unshielded bonus attack. | Follow the live page; the pinned oracle subtracts one tick. |

Known blockers were expanded after inspecting the complete bundled name/variant inventory. Every numerical row being loadable or matching three baseline attacks does not certify every mechanic: new or obscure mechanics without an identified rule can still be absent. Mad Angel and Maggot King are now explicitly blocked because their counterattack guarantees, first-hitsplat rules, burn stacks and defence effects materially affect gear rankings. Sources: [Mad Angel](https://oldschool.runescape.wiki/w/Mad_Angel/Strategies), [Maggot King](https://oldschool.runescape.wiki/w/Maggot_King/Strategies). No universal best-in-slot claim is justified by this comparison.

Rat bone weapons are restricted to rats for every style, including manual spells with the bone staff. [Bone staff](https://oldschool.runescape.wiki/w/Bone_staff).


The expanded reference matrix also found that direct `DpsCalculator` calls bypassed the optimizer's spell filters. The encounter entry point now rejects Crumble Undead against non-undead targets and all Demonbane spells against non-demons, matching the Wiki reference and the primary spell pages. This includes NMZ tree spirits, which explicitly lose the undead attribute: [Crumble Undead](https://oldschool.runescape.wiki/w/Crumble_Undead), [Demonbane spells](https://oldschool.runescape.wiki/w/Demonbane_spells), [NMZ tree spirit](https://oldschool.runescape.wiki/w/Tree_spirit_(Nightmare_Zone)).

Keris partisan of Amascut retains the 1/51 triple-damage proc against kalphites/scabarites; only its ordinary damage bonus drops from 33% to 15%. Removing the incorrect proc exclusion fixes the controlled Kephri case from 3.803207 DPS to the reference's 3.951929 DPS (weapon only, unboosted 99 stats, no prayers). [Primary item page](https://oldschool.runescape.wiki/w/Keris_partisan_of_amascut). The combat calculator owner applies this correction; this number describes the identified expected result, pending the combined reference rerun.

Minimum-one target reductions preserve an already-zero secondary hitsplat, rather than fabricating one damage after the reduction. This matters for Twinflame and other multi-hit effects at Tekton, Kraken and shielded Tormented Demons.


The final catalog pass found two more distinctions. The [Scurrius strategy page](https://oldschool.runescape.wiki/w/Scurrius/Strategies) states summoned rats instantly die upon being attacked; their magic kill action now also has guaranteed accuracy. Rat bone weapons' zero-cooldown target-switching is explicitly outside the sustained interval model. Warden cores permit ordinary ranged/magic damage; only melee gets the guaranteed maximum. The earlier blanket non-melee core guard was removed rather than classifying that disagreement as intentional.

The source has three duplicate `(ID, version)` keys: Jhallan 12353 has 1,500-HP/250-Defence main and 1-HP/83-Defence clone rows; totem IDs 9435 and 9436 each occur once for Phosani (200 HP) and once for The Nightmare (300 HP). The loader labels Jhallan **Main opponent** and **Clone**, while the totem names already distinguish the encounters. Reference adapters must match name and base HP too. [Jhallan's separate combat/clone infoboxes](https://oldschool.runescape.wiki/w/Jhallan).

`docs/reference-source-exceptions.json` records the remaining baseline catalog difference groups by NPC ID, style, explanation and primary source. It is a classification guide, not an automatic waiver for arbitrary fixtures or future changes.
