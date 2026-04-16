# Empty Server Stopper

A Fabric mod that automatically shuts down the server when it's empty or below a certain player threshold. Ideal for use with hibernation scripts.

## Usage
- **Version :** Minecraft `1.20.2`
- **Requirement :** [Fabric API](https://fabricmc.net/use/installer/)
- **Best with :** [SleepingServerStarter](https://github.com/vincss/mcsleepingserverstarter)

## Commands
All commands require **permission level 2** (OP), except for `time` which can be configured.

| Command        | Description                                             |
|:---------------|:--------------------------------------------------------|
| `/ess reload`  | Reloads the configuration file and resets the timer.    |
| `/ess time`    | Shows the remaining time before shutdown.               |
| `/ess enable`  | Enables the mod logic and starts checking player count. |
| `/ess disable` | Disables the mod logic and stops any running timer.     |
| `/ess restart` | Manually restarts the shutdown countdown.               |

## Configuration
The config file is located at `./config/emptyserverstopper/config.json`.

| Key                            | Default | Description                                        |
|:-------------------------------|:--------|:---------------------------------------------------|
| `enabled`                      | `true`  | Globally enable or disable the mod.                |
| `shutdownTimeMinutes`          | `5`     | Minutes to wait before shutdown.                   |
| `minimumPlayersBeforeShutdown` | `1`     | Server stops if players <= this value.             |
| `timerOnLaunch`                | `true`  | Start the timer immediately when the server boots. |
| `playersCanAskShutdownTime`    | `true`  | If true, non-OP players can use `/ess time`.       |

## Behavior
- **Announcements:** The mod broadcasts a message to the chat when a shutdown is scheduled, another warning 1 minute before the end, and a confirmation if the shutdown is canceled.
- **Auto-Cancel:** The timer is automatically canceled if a player joins, and the count exceeds the threshold.

## TODO
- [ ] Port to recent version (e.g., 1.21.11)
- [ ] Translations support?
