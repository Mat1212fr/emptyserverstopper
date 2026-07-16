# Empty Server Stopper

A Fabric mod that automatically shuts down the server when it's empty or below a certain player threshold. Ideal for use
with hibernation scripts.

## Usage

- **Version:** Minecraft `26.1.2` or higher
- **Requirement:** [Fabric API](https://fabricmc.net/use/installer/)
- **Best with:** [SleepingServerStarter](https://github.com/vincss/mcsleepingserverstarter)

## Commands

All commands require **gamemaster permission level (OP level 2)**, except `time` and `min_players`, which can be opened
to all players via config.

| Command                    | Description                                                                      |
|:---------------------------|:---------------------------------------------------------------------------------|
| `/ess reload`              | Reloads the configuration file and re-evaluates the shutdown state.              |
| `/ess time`                | Shows the remaining time before shutdown.                                        |
| `/ess time set <minutes>`  | Sets and persists a new shutdown delay, then restarts the timer with it.         |
| `/ess enable`              | Enables the mod logic and starts checking player count.                          |
| `/ess disable`             | Disables the mod logic and stops any running timer.                              |
| `/ess restart`             | Manually re-evaluates and restarts the shutdown countdown if applicable.         |
| `/ess min_players`         | Shows the current minimum player threshold.                                      |
| `/ess min_players set <n>` | Sets and persists a new minimum player threshold, then re-evaluates immediately. |

## Configuration

The config file is located at `./config/emptyserverstopper/config.json`.

| Key                            | Default | Description                                                               |
|:-------------------------------|:--------|:--------------------------------------------------------------------------|
| `enabled`                      | `true`  | Globally enable or disable the mod.                                       |
| `shutdownTimeMinutes`          | `5`     | Minutes to wait before shutdown once the threshold is crossed.            |
| `minimumPlayersBeforeShutdown` | `1`     | Server schedules a shutdown once the player count drops below this value. |
| `timerOnLaunch`                | `true`  | Start the timer immediately when the server boots.                        |
| `playersCanAskShutdownTime`    | `true`  | If `true`, non-OP players can use `/ess time`.                            |
| `playersCanAskMinPlayer`       | `true`  | If `true`, non-OP players can use `/ess min_players`.                     |

Any value changed via a `set` command is written back to this file automatically — no manual editing or reload needed
for it to persist across restarts.

## Behavior

- **Announcements:** the mod broadcasts a message when a shutdown is scheduled, another warning 1 minute before it
  fires, and a confirmation if the shutdown is canceled.
- **Auto-cancel:** the timer is automatically canceled if enough players join to bring the count back above the
  threshold, and restarted automatically if it drops below again.

## TODO

- [X] Port to recent version: `26.1.2`
- [ ] ~~*Translations support?*~~ Not practical — the mod is server-side only