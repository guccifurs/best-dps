# DPS verification — 0.5.0

Checked 12 October 2026. **This is numerical and source verification, not proof
of universal best-in-slot gear or observed gameplay.** The result remains “best
found · not proven BiS.”

## References and outcomes

The live [Wiki calculator](https://tools.runescape.wiki/osrs-dps/) identified
revision `89c3e25`; comparisons execute its separately cloned
[source](https://github.com/weirdgloop/osrs-dps-calc/tree/89c3e25b344aea90d0189746e4b5f73dde0f0383).
The GPL implementation is not included in the plugin or this repository.

| Check | Scope | Result |
| --- | --- | --- |
| Wiki full bestiary sweep | 2,860 raw variants × melee/ranged/magic = 8,580 inputs | 8,245 matching DPS; 92 explained reference differences; 243 explicitly blocked inputs |
| Wiki equipment/mechanics matrix | 11,230 inputs across 52 boss/phase variants, sets, prayers, levels, raid settings and HP thresholds | 10,347 matching DPS; 483 explained reference differences; 400 explicitly blocked inputs |
| GearScape live UI | Four identical Vorkath loadouts | All match the displayed precision |
| Bitterkoekje published workbook example | Scythe against Olm's melee hand at 150 remaining Defence | DPS matches the displayed precision |
| Combat Insight executable primitives | 4,698 normal/fang accuracy and flat-armour expectations, including negative rolls | All match within `1e-10` |
| Focused checks | 33 test methods | Passed |
| Optimizer vs complete enumeration | 47 requests, 149,496 feasible loadouts | Same best DPS |
| Wiki ammunition table | 15,622 pairs covering 73 weapon variants | All match after fixing 376 initial mismatches |
| Independent funding arithmetic | 108 retained-gear/cash combinations | All match |

The bestiary comparison uses bare whip/Piety, magic shortbow/rune arrows/Rigour,
and fire staff/Fire Wave/Augury. It does not exercise every weapon on every NPC.
The larger matrix includes Void, Crystal, Dharok, Verac, Blood Moon, Inquisitor,
Obsidian, Ahrim and Karil sets; powered and manual magic; demonbane and dragonbane;
ruby-bolt HP/diary boundaries; and raid scaling. Low-level prayer fixtures isolate
arithmetic; they are not claims that those players can equip high-level weapons.

DPS matching uses absolute tolerance `1e-7`. Both adapters emit a hash of the exact
input line. Missing rows, duplicate fixture keys, different inputs and adapter
errors fail verification. Targets match ID, source variant, name and base HP:
the source has three repeated ID/version pairs with different records. Plugin
phase choices are mapped explicitly. Ruby current HP is derived **after** raid
scaling. The oracle's misleadingly named `scaleMonsterHpOnly` only updates
Vardorvis and must not be used to derive raid HP.

## Independent displayed examples

The four [GearScape](https://gearscape.net/calculators/dps) cases use post-quest
Vorkath, all base levels 99, no potion boost, no other gear and normal attacks.

| Setup | Online DPS | Plugin DPS |
| --- | ---: | ---: |
| Abyssal whip, no prayer | 1.07383 | 1.073828666315 |
| Abyssal whip, Piety | 1.51389 | 1.513892124385 |
| Magic shortbow, rune arrows, Rigour | 2.67682 | 2.676820204939 |
| Trident of the seas, Augury | 0.66310 | 0.663095673202 |

The [Bitterkoekje workbook](https://docs.google.com/spreadsheets/d/1wBXIlvAmqoQpu5u9XBfD4B0PW7D8owyO_CnRDiTHBKQ/edit)
example uses Scythe, Torva, infernal cape, rancour, ferocious gloves, maximum
Avernic treads, Ultor, Piety, 118 Attack/Strength and Olm's melee hand reduced to
150 Defence. Online DPS `10.96714375`; plugin `10.967143745842`. Its displayed
maximum `51` is the first splat; the plugin's complete-attack maximum is `88`.

The independent [Combat Insight](https://github.com/BornDied/combat-insight/tree/98e49fd2c18534f376b1004a8adf1b3aec1c4813)
comparison executes `CombatPrediction` and `DamageRoll` at that pinned revision.
It verifies these primitives, not that plugin's entire encounter model.

## Explained differences are not passes

The 92 bestiary differences have 20 source-backed groups in
[the exception ledger](reference-source-exceptions.json). These include unreachable
or protected attacks, quest/NMZ variants, add-kill semantics and omitted NPC IDs.
They are not blanket exemptions for other gear or phases.

The larger matrix retains these actual differences:

| Difference | Inputs | Reason |
| --- | ---: | --- |
| Atlatl/quiver | 47 | Quiver's extra accuracy applies to arrows/bolts, not atlatl darts; the reference over-applies it |
| Magic Dart | 92 | Reference disables accurate-zero uplift using static spell metadata despite Magic Dart's dynamic positive maximum |
| Karil/amulet of the damned | 96 | Reference's case-sensitive amulet name check misses the 25% secondary-hit passive |
| Active Warden melee | 108 | Plugin rejects melee against the active Protect from Melee phase |
| Void Flare | 140 | Guaranteed maximum applies to magic/standard ranged; the reference applies it more broadly |

Sources, exact formulas and targeted expectations are recorded in
[combat maths](combat-math.md) and [encounter coverage](encounter-coverage.md).
All differences in these particular fixtures are classified; classification is
not numerical agreement. A separate independent Karil expectation checks the
passive rather than accepting the broken reference branch.

Diagnostic fields can differ with equal DPS. Zero-DPS rejections intentionally
omit meaningless rolls. Five low-level bow inputs tie between rapid and accurate
stances. Blood Moon's nominal four-tick attack differs from its expected interval;
the UI now displays the average when relevant. Guaranteed one-hit rats have
accuracy one even where the reference only overrides final damage.

## What changed and what remains

Corrections cover exact prayer/damage rounding, atlatl prayers and strength gear,
Twinflame's speed and secondary splat, Amascut Keris procs, full-attack maximums,
Mark/proc ordering, explicit spell restrictions, raid scaling, NPC guarantees and
immunities, distinct Jhallan forms, ammunition legality, missing mandatory ammo,
broken Barrows gear and effect/set preservation. See
[optimizer verification](optimizer-verification.md) for reproducible counterexamples
and measured bank/search latency.

The runtime exposes 2,885 target/phase choices. Of the 2,860 raw bestiary records,
81 produce an explicit unsupported block in the baseline sweep. Restricted
activities, unattackable phases and unmodeled special mechanics must not be
reported as verified recommendations. The 400 blocked larger-matrix inputs are
unattackable Hueycoatl parts.

The model still compares uninterrupted ordinary attacks in a selected state.
Movement, survival, complete fights, equipment switches, special rotations,
burn/venom and recurring supplies are not comprehensively modeled. Large gear
pools remain a bounded search; small pools are fully enumerated only within
the available spell candidates and modeled mechanics. No result proves universal
BiS or that every new game update is covered.

## Reproduce

Use separate checkouts at the exact Wiki and Combat Insight revisions above.
Install `esbuild@0.28.2`, `lodash.isequal`, `lodash.mergewith`, `d3-array` and `axios`
in an external runner directory (for example `npm install --prefix /tmp/dps-runner ...`).
Then run:

```sh
python3 scripts/verification/run.py \
  --wiki-source /path/to/osrs-dps-calc \
  --runner-deps /tmp/dps-runner \
  --insight-source /path/to/combat-insight
./gradlew test --tests '*Reference*'
```

Raw fixtures, adapter outputs, unmatched triples, classifications and summaries
are written under ignored `build/verification/`. Reference code stays outside
the shipped JAR. `docs/verification-summary.json` stores the final numerical receipt.
