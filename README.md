# Best DPS

RuneLite gear search using your equipment, inventory and account-specific bank cache.
Compare sustained DPS against a selected NPC **variant and phase**, with explicit
raid, combat, level, prayer and budget assumptions.

## Use it

1. Log in and open your bank. The last bank snapshot is saved separately for each
   RuneScape profile; its age is shown in the bank status tooltip. Reopen the bank
   after changing bank contents on another client.
2. Search by NPC name or ID and choose its variant. For changing protection prayers,
   choose the style for the vulnerable phase.
3. Under **Build from**, choose owned gear, owned gear plus cash, or buying a setup. Cash
   means the amount available to spend, excluding possible gear-sale proceeds.
   `k`, `m` and `b` suffixes work, up to 2,147,483,647 cash GP.
4. Expand **Encounter settings** for ToA invocation/path levels, party size, CoX challenge
   mode and party stats, target HP, or remaining Defence after a drain.
5. Expand **Combat effects** for player HP, Soulreaper stacks, Mark of Darkness, Kandarin
   diary and Wilderness state. Live HP and actual prayer unlocks are read on the
   client thread when using your character's levels.
6. Press **Find best gear** or **Ctrl+Enter**. Ranked alternatives sit beside the
   selected equipment setup. Use **Equipment**, **Trade plan** and **Assumptions**
   to inspect each result. Changing inputs marks old results as stale and cancels
   obsolete work; **Cancel**, logout and account changes also stop searches.
   **Ctrl+L** focuses the target search.

**Use my levels & unlocks** and **Include owned untradeables** default on. Untradeables must actually
have been observed on your account. Notes are converted to item IDs; placeholders
are excluded. A restored bank cache is labelled separately from a bank read this
session.

## Optional selling

**Allow gear sales** defaults off and is saved as a plugin setting.
It applies to **Owned gear + cash** and requires a bank read this session.

When enabled, the search compares against a separate no-sale baseline. It only
shows a sale-funded setup when its DPS exceeds the best no-sale result found.
The loadout lists item quantities to sell, purchase costs, net proceeds, cash
needed and the DPS gain. Items used by the chosen setup, including its ammunition
stack, are retained. Unknown, untradeable and stale-priced items cannot fund a plan.
Only gear in the equipment catalogue is considered for selling; bank supplies are
not liquidated.

Prices use the public [OSRS Wiki price API](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices),
with at most one bulk request per five minutes. No account or bank contents are
sent. Buy estimates include a 2% price buffer; sale estimates take 2% off the
observed sell quote and reserve the 2% GE tax, capped at 5m per item. Reserving tax
on exempt items is deliberately conservative. Quotes older than 24 hours cannot
fund sales. Bundled buy prices remain clearly labelled estimates when live data is
missing. This is a recommendation list: the plugin never places trades.

## Search and coverage

The engine indexes candidates once per style, preserves meaningful set pieces,
seeds complete sets, filters ammunition for each weapon, and uses bounded
multi-start coordinate improvement plus two-slot budget exchanges. Search work
is cancellable and runs away from the client/UI threads.

Measured on the development machine using the bundled dataset (12 October 2026):
an owned-only, all-style search took 22 ms; a 100m ranged Vorkath search took
665 ms; an all-style resale search, including its no-sale comparison, took
4.85 seconds. The resale example improved estimated DPS from 8.536 to 8.992
while requiring 99.40m cash. These are sample timings, not limits for every bank
or target.

Results are labelled **best found**, not a mathematically exhaustive optimum.
NPC data coverage is not the same as verified mechanics coverage. Ordinary attacks,
raid scaling, special damage rules and supported equipment effects are detailed in
[encounter coverage](docs/encounter-coverage.md) and [combat maths](docs/combat-math.md).
Unmodelled encounters are blocked or carry specific warnings rather than being
presented as reliable best-in-slot advice.

DPS assumes uninterrupted attacks in the selected phase. It does not simulate
movement, survival, supplies, gear switches, special-attack rotations or full raid
completion. Gear purchases exclude recurring charges, runes and ammunition;
charged weapons assume usable charges. Blowpipe's ammo row represents loaded darts.
An individual NPC comparison is not a whole-raid loadout recommendation.

## Data maintenance

Numerical Wiki data is pinned to an explicit revision; attribution and counts are
in `src/main/resources/com/bestdps/data/wiki-data-provenance.json`. Reviewed item
requirements and availability overrides are in `scripts/gear_metadata_overrides.json`.
New gear lacking verified metadata is excluded from recommendations.

```bash
python3 scripts/refresh_wiki_data.py --revision <full-wiki-calculator-commit>
./gradlew jar
```

The refresh script imports factual data, not the GPL calculator implementation.
Java implementation remains under this repository's BSD-2-Clause license; Wiki
source attribution and data licensing are documented in the coverage notes.

## Build

```bash
./gradlew jar
```

Java 11; RuneLite's current release API. Output: `build/libs/best-dps-0.3.0.jar`.
Building does not publish a Plugin Hub update or prove in-game behaviour.
