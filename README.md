# Best DPS

A RuneLite plugin for checking DPS setups against OSRS monsters.

Search a boss or NPC, pick a style, set a budget, and hit **Find**. The plugin shows the best setups it found, what they cost, and the gear in an equipment view.

## Stuff It Handles

- melee, ranged, magic, or any style
- gear you already own
- bank gear after you open the bank once
- untradeables, only if the plugin has seen them
- current levels or maxed assumptions
- boosts and prayers
- Slayer helm, salve, Void, Bowfa/crystal, Twisted bow, Fang, Scythe, dragon hunter, demonbane, Keris, Inquisitor, and elemental weaknesses
- automatic spell picking for magic

## Slayer Helm

If you want Slayer helm included, turn on **On slayer task**.

The helm has to be seen by the plugin first. If it is in your bank, open the bank once. Imbued helm is needed for ranged and magic.

## Building

```bash
./gradlew clean build
```
