# Copycat Mobs (Fabric, Minecraft 1.20.1)

Every mob (all MobEntity, vanilla or modded) copies what the nearest player does:

- Armor and held items: they wear/hold exactly what you wear/hold, and drop it when they die
  (this duplicates items; turn off with /mimic drops off).
- Dropping (Q / Ctrl+Q): every mob throws its held item the same way (one item, or the whole stack).
  Dropping from inside the inventory screen is not copied.
- Placing blocks: you place a block, each mob places the same block at the same offset from ITS feet.
- Breaking blocks: you break a block, each mob breaks the block at the same offset from its feet.
- Attacking: you swing at something, every mob turns the same way, swings, and hits what is in front of it.
  Mobs without an attack stat (cow, pig, bat...) still deal 2 damage (+ sword damage).

Commands (no permission needed):
- /mimic on | off
- /mimic drops on | off   (default on)
- /mimic range <blocks>   (0 = every loaded mob, which is the default)

## Build

Needs Java 17 and Gradle 8.6+.

    gradle build

The jar is in build/libs/copycat-mobs-1.0.0.jar (not the -sources one).
Put it in .minecraft/mods together with Fabric Loader 0.15.x and Fabric API 0.92.2+1.20.1.

No Java/Gradle? Push this folder to a GitHub repo, open Actions -> build -> Run workflow,
and download the jar from the run's artifacts.
