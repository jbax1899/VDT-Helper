# **VDT-Helper**

A lightweight companion plugin for **ViewDistanceTweaks** that automatically lowers view and simulation distances when a world is empty, and restores VDT’s automatic scaling when players return.

## Features

* Per-world “vacant minimum” view/simulation distances
* Cooldown before applying reductions
* Automatically reloads ViewDistanceTweaks when a world becomes populated again
* No commands or permissions required

## How It Works

* When a world has **0 players**, it is marked vacant.
* After `cooldown-seconds`, if still empty, VDT-Helper sets the world’s distances to its configured minimums.
* When a player enters a previously empty world, VDT-Helper runs

  ```
  viewdistancetweaks reload
  ```

  so VDT can resume dynamic distance adjustment.

## Configuration

```yaml
cooldown-seconds: 10

worlds:
  world:
    view-distance: 5
    simulation-distance: 5
  world_nether:
    view-distance: 3
    simulation-distance: 2
  world_the_end:
    view-distance: 3
    simulation-distance: 2
```

Only listed worlds are managed.

## Requirements

* Paper or Purpur
* ViewDistanceTweaks installed

## Installation

1. Install ViewDistanceTweaks
2. Place VDT-Helper in `plugins/`
3. Start server and edit generated `config.yml`
