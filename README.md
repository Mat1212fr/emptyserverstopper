# Empty Server Stopper

## Usage
For now, works only with `1.20.2`.

Requires [fabric-api](https://fabricmc.net/use/installer/).

Works best with: [SleepingServerStarter](https://github.com/vincss/mcsleepingserverstarter).

## Config
After you boot the server a new config file will be generated in the path `./config/emptyserverstopper/config.json`.

| Key                            | Default Value | Description                                                                                                                    |
|--------------------------------|---------------|--------------------------------------------------------------------------------------------------------------------------------|
| `shutdownTimeMinutes`          | 5             | The number of minutes until the server shuts down.                                                                             |
| `minimumPlayersBeforeShutdown` | 1             | The minimum number of players required to keep the server running. By default, the server shuts down if it is empty.           |
| `shutdownOnLaunch`             | true          | If true, the server shutdown timer starts after the server starts. If false, the timer starts only after a player disconnects. |
