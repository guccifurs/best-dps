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
  interval. Ordinary spells use five ticks; Harmonised standard spells use four.
- Shadow multiplies equipment magic accuracy and damage by three, or four in ToA;
  its equipment damage bonus is capped at 100%. Prayers are added separately.
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
  successful-damage floor of three. Atlatl damage uses Strength and melee strength gear.
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
  Keris triple-hit chance, Gadderhammer shade damage and charged quiver bonuses
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
