# Optimizer reference checks

Checked on 12 October 2026. These checks cover candidate legality, search choices
and gear funding. They do **not** establish a global best-in-slot result or verify
every NPC mechanic. Independent combat-formula comparisons are separate from the
search checks described here.

## Reference and reproducibility

The ammunition reference is the OSRS Wiki DPS calculator at commit
`89c3e25b344aea90d0189746e4b5f73dde0f0383`:

- [Equipment compatibility data](https://github.com/weirdgloop/osrs-dps-calc/blob/89c3e25b344aea90d0189746e4b5f73dde0f0383/src/lib/Equipment.ts)
- [Equipment aliases](https://github.com/weirdgloop/osrs-dps-calc/blob/89c3e25b344aea90d0189746e4b5f73dde0f0383/src/lib/EquipmentAliases.ts)
- [Wiki bow ammunition table](https://oldschool.runescape.wiki/w/Ranged_bows)
- [Wiki crossbow table](https://oldschool.runescape.wiki/w/Crossbow_list)

`src/test/resources/com/bestdps/optimizer-ammo-reference.json` records factual
weapon/ammunition item-ID compatibility and the source revision. It does not
contain the reference calculator implementation. The four `OptimizerReference*`
test classes use the bundled real equipment catalogue.

Run the scoped checks with a warm Gradle daemon:

```sh
./gradlew test --tests 'com.bestdps.calc.OptimizerReference*'
```

The final scoped run passed 12 test methods. Its evidence included:

| Check | Cases | Outcome |
| --- | ---: | --- |
| Ammunition compatibility | 73 standard weapon variants, 15,622 weapon/ammo pairs | Zero differences from the pinned Wiki table |
| Small melee/ranged budget pools | 36 requests, 15,807 feasible loadouts | Same best DPS as full enumeration |
| Larger budget/set tradeoffs | 8 requests, 133,656 feasible loadouts | Same best DPS as full enumeration |
| Atlatl, bulwark and elemental amulet pools | 3 requests, 33 feasible loadouts | Same best DPS as full enumeration |
| Independent sale-funding accounting | 108 retained-gear/cash combinations | Same affordability; selected items retained |
| Weapon shortlist coverage | 1,494 catalogue variants, 933 named families | No family removed by a raw-stat weapon cutoff |

Enumeration is an independently implemented Cartesian search using the plugin's
combat calculator, so the 149,496 loadout comparisons test **search correctness**,
not the correctness of that shared combat formula. The ammunition comparison uses
an independently sourced compatibility table. Weapon-family coverage checks the
shortlist itself; normal availability, ownership, requirements, broken-item and
style checks still apply before a weapon can be recommended.

## Concrete defects fixed

The ammunition differential initially found **376 mismatches**. Corrections
include yew bows accepting ammunition above rune, the tier of ice arrows, missing
training/ogre/comp-ogre restrictions, third-age and Echo venator limits, and a
missing lit bronze fire-arrow variant. Dorgeshuun's unusual compatibility remains
the exact pinned list; it is not inferred from bolt strength.

The atlatl fixture returned no result while full enumeration found **5.3803804278
DPS** with Eclipse atlatl `29000`, atlatl dart `28991`, amulet of strength `1725`
and Bandos chestplate `11832`. A positive-stat filter had discarded its mandatory
zero-stat dart. Eligible zero-stat items now enter the candidate pool. Atlatl and
hunter's spear armour ranking also uses melee strength; bulwark ranking includes
its relevant defensive bonuses.

An owned-only fixture could recommend broken Karil's crossbow `4938` with bolt
racks. Broken variants and zero-durability Barrows equipment are now excluded
from equippable candidates. Such an item may still appear as a sale if its actual
tradeability and fresh price allow it; selling and equipping have different rules.

The previous raw-stat weapon cutoff could discard distinct effects before DPS
was evaluated. Eligible named weapon variants now get initial setups before
refinement. Verac's set is retained and seeded alongside the other modeled sets;
elemental amulets, Efaritay's aid and amulet of avarice also survive nonweapon
shortlisting. Explicit manual spells can use powered staves; dragon-hunter melee
and ranged weapons remain candidates for manual magic against dragons.

## Funding and ownership

The accounting fixtures use real gear with deliberately fixed **hypothetical**
fresh quote values. This isolates arithmetic from price changes and is not a
market-price accuracy check. Independent calculations subtract the reserved 2%
GE tax, capped at 5m per item. Checks cover owned quantities, full retention of
selected gear/ammunition, no sales when cash suffices, stale prices, the sale
toggle, base-level requirements despite boosted combat levels, and disabled
untradeables. Three 2b purchases remain a 6b total and cannot wrap into an
affordable integer amount.

A separate fixture compares selling against the same request without selling:
each recommendation requiring sales must improve on the no-sale result, carry
that baseline DPS and have an affordable plan. No test places a trade.

## Search bounds and measured time

When the conservative candidate-product bound is at most 20,000, the optimizer
enumerates the complete eligible gear pool, including empty slots, within each
weapon's legality constraints. It still uses the available spell candidates and
modeled combat rules. Larger pools use seeded coordinate improvement and limited
two-slot budget exchanges, capped at 24,000 evaluated loadouts per style.
Nonweapon shortlists preserve modeled effects and set pieces, but remain bounded.
There is no proof of a global optimum for the full catalogue.

One final targeted run on the development machine measured the following real
catalogue requests. Variant IDs are intentional; these numbers must not be
presented as normal Vorkath or normal Vardorvis benchmarks.

| Request | Time | Best found DPS | Weapon ID |
| --- | ---: | ---: | ---: |
| Vorkath, Dragon Slayer II `8058`, ranged, 100m | 1,746 ms | 7.694871 | 21012 |
| Zulrah, magma `2043`, any style, 100m | 6,070 ms | 6.161323 | 12000 |
| Vardorvis, awakened `12223`, melee, 100m | 1,726 ms | 3.530402 | 29796 |
| Vorkath, Dragon Slayer II `8058`, any style, illustrative owned bank | 145 ms | 6.814310 | 21012 |

These are sample wall times from a shared development machine, not latency
guarantees or in-game observations. All-style catalogue searches perform more
work than a narrow bank search. The search stays cancellable and runs off the
client and UI threads. Future equipment, unmodeled effects, changing phases,
full-fight rotations, movement, consumable costs and unknown unlock state remain
outside what these optimizer checks prove. Results remain labelled **best found ·
not proven BiS**.
