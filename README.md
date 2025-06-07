# PlaytimeTrackerTGE

---

## Overview

PlaytimeTrackerTGE is a Minecraft server plugin designed to track and manage players playtime efficiently. It supports detailed playtime tracking, player ranking by playtime, and offers flexible management tools. The plugin also includes a feature that allows importing playtime data from the popular **PlayTimes** plugin, making migration easy for existing servers.

---

## Features

- Track individual player playtime with precision.
- View your own or others playtime using simple commands.
- Display the top 10 players sorted by playtime.
- Add, set, or reset playtime manually for players.
- **AFK time is not counted towards playtime**, and AFK detection settings are configurable.
- **Import playtime data from the PlayTimes plugin** for easy migration.
- Automatically assign LuckPerms ranks based on playtime — all rank requirements are configurable.
- **Fully customizable messages and system behavior via `config.yml`**:
  - Supports `&` color codes and **PlaceholderAPI** placeholders.
  - Customize messages for `/playtime`, `/playtimetop`, AFK status, rankups, and more.
  - Adjust intervals for playtime updates, AFK detection, and autorank checks.
  - Configure MySQL or SQLite storage.
- **BungeeCord compatibility**:
  - Works in BungeeCord networks even without the proxy plugin.
  - To broadcast **first join** and **rank-up messages** across all servers, install the `PlaytimeTrackerTGEBungee` plugin on the proxy and set `use_bungee: true` in the config.
- Reload the plugin configuration live with a command.
- Permissions-based control over every command and feature.

---

## Commands

| Command           | Description                                   | Usage                           | Permission                     |
|-------------------|-----------------------------------------------|---------------------------------|-------------------------------|
| `/playtime`       | Shows your own or another player's playtime. | `/playtime [player]`            | `playtime.use`                 |
| `/playtimetop`    | Shows the top 10 players by playtime.         | `/playtimetop`                  | `playtimetop.use`              |
| `/playtimeadd`    | Adds playtime to a player.                     | `/playtimeadd <player> <minutes>` | `playtime.add`              |
| `/playtimeset`    | Sets a player's playtime.                     | `/playtimeset <player> <minutes>` | `playtime.set`              |
| `/playtimereset`  | Resets a player's playtime.                   | `/playtimereset <player>`       | `playtime.reset`               |
| `/playtimetracker`| Reloads the configuration file.               | `/playtimetracker reload`       | `playtimetracker.reload`       |
| `/removelastrank` | Removes the player's last received rank.      | `/removelastrank <player>`      | `playtimetracker.removelastrank` |
| `/importplaytimes`| **Imports playtime data from the PlayTimes plugin.** | `/importplaytimes`         | `playtimetracker.import`       |

---

## Permissions

| Permission                      | Description                                  | Default  |
|--------------------------------|----------------------------------------------|----------|
| `playtime.use`                 | Allows usage of the `/playtime` command.     | True     |
| `playtimetop.use`              | Allows usage of the `/playtimetop` command.  | True     |
| `playtime.reset`               | Allows resetting player playtime.            | OP       |
| `playtime.add`                 | Allows adding playtime to a player.          | OP       |
| `playtime.set`                 | Allows setting a player's playtime.          | OP       |
| `playtimetracker.reload`       | Allows reloading the plugin configuration.   | OP       |
| `playtimetracker.removelastrank` | Allows using the `/removelastrank` command.| OP       |
| `playtimetracker.import`       | Allows importing PlayTimes data into database.| OP      |

---

## Installation

1. Download the latest `.jar` file from the releases.
2. Place the `.jar` file into your server's `plugins` folder.
3. Restart your server.
4. Configure the plugin via the generated configuration files as needed.

---

## Additional Information

- This plugin supports MySQL for storing playtime data.
- **Playtime data from the PlayTimes plugin can be imported directly, enabling smooth transitions for existing servers.**
- Provides seamless integration with permission plugins to control access.

---

## Support

For bug reports, feature requests, or help, please open an issue on the GitHub repository.

---

**Thank you for using PlaytimeTrackerTGE!**
