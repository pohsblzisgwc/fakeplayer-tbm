# Fake Player TBM 1.1.0

**AI disclosure: This project was developed entirely by OpenAI Codex.**

[简体中文](README.md)

A Minecraft Java 26.3 Fabric client mod that uses Carpet's `/player` command to process a horizontal rectangle with a bounded, reusable pool of fake player names.

## Installation

Install Fabric Loader 0.19.5 or later and Fabric API for Minecraft 26.3. Put the release JAR in the instance's `mods` folder and remove older versions. Java 25 is required. Carpet must be installed on the server (or in the single-player instance), and you must be permitted to use `/player`.

Online-mode servers may require existing player accounts or Carpet's `allowSpawningOfflinePlayers` rule. Whitelists, player limits and permissions may also prevent spawning. First try `/player TBM_1 spawn` and `/player TBM_1 kill` manually. Reserve the entire name pool for this task so that other players and tasks do not use it.

## Usage

Enter `/faketbm` or `/faketbm gui` in chat. Enter the X/Z coordinates of two corners, the target block's Y, a name prefix, the number of fake players, and the work duration. Select **保存并开始** to save and start, or **停止** to stop. The interface is currently in Chinese.

For example, X1=0, Z1=0, X2=9, Z2=4, Y=64, prefix `TBM_`, count 3 and duration 30 process 50 positions with names `TBM_1` through `TBM_3`, holding attack for 30 seconds per attempt. Both corners are inclusive. X/Z are horizontal; Y is the height of the target block. Fake players spawn at `(x+0.5, y+1, z+0.5)`.

The equivalent commands are:

```text
/faketbm area 0 0 9 4 64
/faketbm config TBM_ 3 30
/faketbm start
/faketbm status
/faketbm stop
/faketbm resume
```

Names consist of the prefix and a number starting at 1. Use only letters, digits and underscores; each full name is limited to 16 characters. Count is 1–100 and duration 1–86400 seconds. Each coordinate is processed once and the task finishes after its fake players have been removed.

## Recovery and limitations

- If a fake player disconnects early, its coordinate remains assigned and is retried. Failed spawns and removals are retried periodically while other slots continue. A name already online is left alone until it is free. Persistent server errors will cause repeated attempts; check server feedback.
- When the fake player's entity is visible to the client, a horizontal deviation greater than 1.5 blocks from the target centre causes cleanup and a retry. Entities outside client tracking range or in another dimension cannot be position-checked. Vertical movement alone does not trigger a retry.
- When the user changes dimension, new spawns pause until they return to the original dimension; control and cleanup of already online fake players are still attempted. A disconnected task remains in memory only while the client process is running. Reconnect to the original server and world, then choose **确认原服后恢复** or run `/faketbm resume`. The mod cannot verify that the world is the original one; do not resume on another server. Closing the game loses the task, and fake players may require manual cleanup.
- The client must stay connected to schedule work. Disconnection, game exit and rejected commands can prevent cleanup; use `/player NAME kill` manually when necessary. On stop, a spawn that does not appear within the timeout is abandoned; a very late spawn may require manual cleanup.
- At most one command is sent per 20 client ticks. The work duration uses client ticks and can differ from server time. Delays and command failures can affect results. Carpet's default determines the fake player's game mode. The mod does not supply tools or verify that a block was broken.

## Build and test

Set `MC_DIR` to the Minecraft game directory and run `./build.sh`. The script requires JDK 25, `versions/26.3-Fabric/26.3-Fabric.jar`, `versions/26.3-Fabric/mods/fabric-api-0.160.5+26.3.jar`, and the game dependencies in that directory. `./test.sh` runs scheduler tests using a simulated connection; it does not replace in-game testing.

See the [Carpet command documentation](https://github.com/gnembon/fabric-carpet/wiki/Commands) and [Fabric's 26.3 notes](https://fabricmc.net/2026/09/15/263.html).

## License

This project is licensed under the [MIT License](LICENSE). Minecraft, Fabric API and Carpet have their own respective licenses.
