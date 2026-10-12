# Combat model and sources

Research date: 12 October 2026. This is a normal-attack, single-target equipment
optimizer. It estimates continuous attacks against the selected NPC variant and
state. It does not simulate an entire fight or prove a globally optimal loadout.
The Java implementation is independently written; no Wiki calculator source was
copied into the plugin.

## Corrected calculation paths

- Melee chooses legal weapon-category stances. A whip cannot select aggressive
  stab; spear attacks use controlled bonuses. Unarmed attacks remain available.
- Void modifies effective melee levels before attack/max-hit rolls. Ranged and
  magic Void, elite Void magic damage and Ancient-spell Virtus bonuses apply.
- Salve and Slayer bonuses exclude one another while allowing dragonbane,
  demonbane, crystal armour and Twisted bow bonuses. Crystal bonuses occur before
  Slayer rounding; ranged Slayer damage stacks additively with relevant bane and
  Wilderness weapon damage bonuses. Melee Salve recognizes enchanted imbued forms.
- Powered staves use their own max hits, accurate-stance magic levels and attack
  interval. Ordinary spells use five ticks; Harmonised standard spells use four and Twinflame spells use six. Explicitly selected spells still use manual-casting rules when holding a powered staff.
- Shadow multiplies equipment magic accuracy and damage by three, or four in ToA;
  its equipment damage bonus is capped at 100%. These multipliers apply to its powered attack, not manually selected spells. Prayers are added separately.
- Elemental spells scale to the highest unlocked element in their spell tier.
  Chaos gauntlets and matching elemental amulets add to base spell damage.
  Elemental weakness adds to base accuracy/damage; charged tomes apply afterwards.
- Fang rerolls only attack accuracy outside ToA and the full accuracy check inside
  ToA. Its restricted damage interval applies on both stab and slash.
- Flat armour applies to individual successful melee/ranged splats, after rolling
  damage, and does not apply to magic. Accurate zeros become one before reductions.
  Ordinary distributions use a constant-time expectation; special NPC transformations
  enumerate the small integer damage range.
- Scythe uses one, two or three independent splats according to target size. Dark
  bow fires two arrows. Dual macuahuitl's second accuracy roll requires the first
  to succeed; Torag's hammers, sulphur blades and glacial temotli split their hits.
- Blood Moon changes expected attack interval. Dharok uses entered current/max HP;
  Soulreaper uses entered stacks. Verac and the Amulet of the damned combinations
  for Ahrim/Karil include their passive damage distributions.
- Blowpipe ammunition is represented by a virtual loaded-dart row: darts add
  ranged strength, not their thrown-weapon accuracy. Empty Toxic/Blazing blowpipe
  purchase entries use charged-weapon base strength. Projectile compatibility is
  enforced. Seeking arrows use their normal arrow tier, +20 bow accuracy and a
  successful-damage floor of three. Atlatl damage uses Strength and melee strength gear, with ranged damage prayers.
- Normal enchanted ruby, diamond, onyx, dragonstone, opal and pearl bolt damage is
  included, including Zaryte modifiers and the optional Kandarin diary proc bonus.
  Ruby damage uses the selected target HP and requires player HP of at least ten.
- Brimstone averages its reduced-defence proc. Confliction uses the steady-state
  probability of its extra accuracy roll and only applies with one-handed weapons.
- The July 2026 Sanguinesti change uses floor(Magic/3) base damage and its 20%
  successful-hit +8 damage proc. Inquisitor's current crush bonuses are 0.5% for
  the helm and 1% each for body/legs. Twisted bow scaling is clamped.
- Wilderness weapon bonuses require the Wilderness toggle. Mark of Darkness and
  Purging staff modify demonbane spells; Duke and normal Yama adjust demonbane
  vulnerability. Vampyrebane, silver and Efaritay effects use target attributes.
- Dinh's defence-to-strength, rat-bane damage, leaf-bladed battleaxe, Colossal blade,
  Keris triple-hit chance (including the Amascut variant), Gadderhammer shade damage and charged quiver bonuses
  have explicit calculations.

NPC scaling, immunities, reachability and per-hit boss transformations live in
`EncounterRules`. Calculator integration includes Wardens phase-two damage bands,
exposed-core maximum hits, Tormented Demon state-specific accuracy/damage/speed,
Guardian Mining-based damage, Salarin's fixed successful strike damage, and true maximum damage caps separately from
expected damage. See the encounter documentation for supported variants and limits.

## Inputs and boundaries

Set target variant, raid/party/path settings, target HP and any defence override
before comparing setups. Set player current/max HP, Soulreaper stacks, Mark of
Darkness, Kandarin diary and Wilderness explicitly; optional buffs default off.
Confliction is a sustained-attack estimate and does not model its opening state.

Tradeable uncharged gear is evaluated in its usable charged condition. Ownership
of an uncharged item does not prove sufficient charges, runes, pages, scales,
crystal charges or ether. Purchase totals exclude charging and recurring attack
costs. Loaded darts are shown as ammunition despite physically living inside the
blowpipe; a separate simultaneous ammo-slot blessing is not modeled for blowpipes.

DPS excludes special-attack rotations, movement, boss downtime, pathing, survivability,
healing, defence-draining sequences, poison/venom/burn damage, thralls, ricochets,
and damage to additional targets. Eclipse Moon burn is explicitly excluded.
Chinchompas assume optimal distance for the selected fuse. Sunfire-rune and Charge
spell buffs are not enabled. Equipment searches are bounded: valid returned setups
are candidates, not a mathematical proof that no better combination exists.

A covered NPC or formula is not evidence of live gameplay acceptance. New NPCs,
new equipment, changing mechanics and encounter-specific transitions still require
updates. Unsupported encounter states must remain labelled or blocked instead of
being presented as verified BiS for every NPC.

## Primary references

- [Melee DPS formulas](https://oldschool.runescape.wiki/w/Damage_per_second/Melee),
  [ranged DPS formulas](https://oldschool.runescape.wiki/w/Damage_per_second/Ranged),
  [magic DPS formulas](https://oldschool.runescape.wiki/w/Damage_per_second/Magic).
- [Combat options](https://oldschool.runescape.wiki/w/Combat_Options),
  [flat armour](https://oldschool.runescape.wiki/w/Flat_armour),
  [elemental weakness](https://oldschool.runescape.wiki/w/Elemental_weakness).
- [Fang](https://oldschool.runescape.wiki/w/Osmumten%27s_fang),
  [Shadow](https://oldschool.runescape.wiki/w/Tumeken%27s_shadow),
  [Twisted bow](https://oldschool.runescape.wiki/w/Twisted_bow),
  [Scythe](https://oldschool.runescape.wiki/w/Scythe_of_vitur),
  [Dual macuahuitl](https://oldschool.runescape.wiki/w/Dual_macuahuitl).
- [Confliction](https://oldschool.runescape.wiki/w/Confliction_gauntlets),
  [elemental amulet](https://oldschool.runescape.wiki/w/Elemental_amulet),
  [Vampyre attribute](https://oldschool.runescape.wiki/w/Vampyre_(attribute)),
  [Duke strategies](https://oldschool.runescape.wiki/w/Duke_Sucellus/Strategies),
  [Tormented Demon strategies](https://oldschool.runescape.wiki/w/Tormented_Demon/Strategies).
- Current Wiki calculator change records resolve older search-index snapshots:
  [Sanguinesti buff, 23 July 2026](https://github.com/weirdgloop/osrs-dps-calc/commit/54ab4b01fdaf4a5b3c07bb6eb6ccd8bc8a5b7069),
  [summer balance changes, 22 July 2026](https://github.com/weirdgloop/osrs-dps-calc/commit/ad767bb3622f8a8d1742bf581fc3a88b7e91fb51),
  [Twisted bow clamp, 24 July 2026](https://github.com/weirdgloop/osrs-dps-calc/commit/a6599a08d87cf6193af1de9627e9167339567698).

Spell eligibility uses the saved quest and skill profile: Ancient Magicks require
Desert Treasure I; Arceuus combat spells require A Kingdom Divided except Ghostly
Grasp and Inferior Demonbane; Iban Blast requires Underground Pass; Magic Dart
requires 55 Slayer. Mark of Darkness additionally requires A Kingdom Divided and
59 Magic. These gates apply to automatic and manually selected spells; the maxed
simulation profile retains all unlocks. Individual Mage Arena 100-cast counters
are not captured, so real-profile god spells are conservatively excluded unless
Mage Arena II is complete, which proves all three casting unlocks.
Sources: [Ancient Magicks](https://oldschool.runescape.wiki/w/Ancient_Magicks),
[Arceuus combat spells](https://oldschool.runescape.wiki/w/Arceuus_spellbook#Combat_spells),
[Underground Pass](https://oldschool.runescape.wiki/w/Underground_Pass),
[Slayer unlocks](https://oldschool.runescape.wiki/w/Slayer/Level_up_table),
[Mage Arena II requirements](https://oldschool.runescape.wiki/w/Mage_Arena_II/Quick_guide).

## External-calculator verification, 12 October 2026

The independent comparison uses the published OSRS Wiki calculator at revision
[`89c3e25b344aea90d0189746e4b5f73dde0f0383`](https://github.com/weirdgloop/osrs-dps-calc/tree/89c3e25b344aea90d0189746e4b5f73dde0f0383).
The reference runs in a separate temporary checkout. Its TypeScript is not included
in the plugin. Comparisons use matching item IDs, NPC variants, levels, prayers,
spells and state inputs; they compare numerical results, not gameplay outcomes.

Confirmed corrections from this pass:

- Prayer multipliers use integer percentage arithmetic. A level of 100 with a
  15% prayer becomes 115, not 114 from floating-point truncation. Burst of Strength
  and Sharp Eye receive their minimum one-level damage boost below level 20.
- Magic damage is added with integer thousandths. A base hit of 25 with 16%
  magic damage becomes 29, not 28.
- Atlatl and Hunter's spear damage uses ranged prayers even though the damage
  stat and equipment strength are melee values. Piety does not raise their damage.
- Twinflame receives the smoke-staff standard-spell bonus and casts at six ticks.
  Its secondary hit is 40% of the main hit after the accurate-zero floor, rounded
  down; the secondary may remain zero through target damage reductions.
- The displayed maximum includes the whole attack: secondary splats, scythe hits,
  possible enchanted-bolt effects and modeled normal-attack passive procs.
- Mark of Darkness is applied before Ahrim's damage proc and the Tormented
  Demon's flat unshielded bonus. Ahrim's proc also preserves the Warden
  phase-two minimum-damage band instead of restarting its roll at zero.
- Amascut's Keris retains the 1/51 triple-damage proc. Its ordinary Kalphite damage
  bonus is 15%, while other Keris variants use 33%.
- Manual casting while holding Shadow does not multiply accuracy/damage. Dragon
  hunter weapons retain the relevant dragon bonuses when manually casting, and
  the wand retains its bonuses when used for melee.
- Avarice applies its ordinary 20% revenant bonus with the usual Slayer exclusion.
  Forinthry Surge remains an explicitly unmodeled optional buff.
- Ogre bows use their dedicated damage formula, excluding ordinary damage prayer,
  stance and armour-strength bonuses. Holy water is restricted to demons and uses
  its dedicated damage and demonbane calculation.
- NPC-specific guaranteed maximum, guaranteed accuracy, minimum damage and
  instant-kill cases are distinct hooks. They are not inferred from a target's name
  alone. See [encounter coverage](encounter-coverage.md) for their primary sources.

The focused `CombatReferenceRegressionTest` cases cover exact numerical boundaries
and independently derived distributions, including the Karil/Amulet name-casing
discrepancy, Mark of Darkness ordering and Tormented Demon flat damage. Independent exact fixtures
include a 99-stat Atlatl with +87 ranged accuracy/+40 melee strength: Piety gives
max 17 and attack roll 16157, while Rigour gives max 21 and attack roll 19026.
A 99-Magic Twinflame with +12 magic accuracy casting Fire Bolt against a target
with Magic 1 and zero magic defence gives attack roll 9028, full maximum 18,
six ticks and expected damage 8.473363606157935. These tests also cover the two
integer-rounding boundaries, manual Shadow casting and ordinary Avarice damage.

### Reference disagreements retained deliberately

A calculator is a comparison source, not an override for newer item rules. The
following differences have been checked against the live Wiki pages:

- The pinned reference grants Dizana's charged bonus to Atlatl darts. The live
  quiver page restricts its extra +10 accuracy/+1 ranged strength to arrows and
  bolts that can be stored in the quiver; darts and javelins are explicitly excluded.
  Best DPS keeps the arrow/bolt restriction. Cosmetic/poisoned arrow aliases can
  also cause the reference to omit a qualifying quiver bonus.
- The pinned reference suppresses the accurate-zero floor for Magic Dart because
  its spell metadata stores a zero placeholder before calculating its dynamic
  base hit. Best DPS follows the successful-hit rule for this damaging spell.
  With an enchanted Slayer's staff at 99 Magic against Magic 1/zero defence,
  the shared accuracy is 0.9608965769277622. The reference expected damage is
  9.128517480813743; Best DPS gives 9.17656230966013, the additional probability
  of a successful roll of zero becoming one.
- The reference checks the Karil passive against `Amulet of the damned`, but its
  item dataset uses `Amulet of the Damned`. Its case-sensitive item matching omits
  the entire 25% chance of a second half-damage hit. Best DPS recognizes the item
  and keeps the documented passive. Degraded Karil crossbow aliases can also omit
  the reference quiver bonus even when the equivalent undamaged weapon receives it.
- Target restrictions, named raid phases, current silver-weapon rules and current
  Corp-bane exceptions can differ from the pinned reference. These are recorded
  with live-page evidence in the encounter coverage document, rather than being
  silently changed to match older reference behavior.

Additional primary sources:
[prayer modifiers](https://oldschool.runescape.wiki/w/Prayer),
[Eclipse atlatl](https://oldschool.runescape.wiki/w/Eclipse_atlatl),
[Twinflame staff](https://oldschool.runescape.wiki/w/Twinflame_staff),
[Keris partisan of Amascut](https://oldschool.runescape.wiki/w/Keris_partisan_of_amascut),
[Dizana's quiver](https://oldschool.runescape.wiki/w/Dizana%27s_quiver),
[successful hits](https://oldschool.runescape.wiki/w/Successful_hit),
[Magic Dart](https://oldschool.runescape.wiki/w/Magic_Dart),
[Amulet of avarice](https://oldschool.runescape.wiki/w/Amulet_of_avarice),
[Amulet of the Damned](https://oldschool.runescape.wiki/w/Amulet_of_the_Damned),
[Demonbane spells](https://oldschool.runescape.wiki/w/Demonbane_spells),
[Purging staff](https://oldschool.runescape.wiki/w/Purging_staff),
[Ogre bow](https://oldschool.runescape.wiki/w/Ogre_bow),
[Holy water](https://oldschool.runescape.wiki/w/Holy_water).
