package org.tege56.playtimeTrackerTGE.storage;

import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final String username;
    private final long playMinutes;
    private final long firstJoin;

    public PlayerData(UUID uuid, String username, long playMinutes, long firstJoin) {
        this.uuid = uuid;
        this.username = username;
        this.playMinutes = playMinutes;
        this.firstJoin = firstJoin;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public long getPlayMinutes() {
        return playMinutes;
    }

    public long getFirstJoin() {
        return firstJoin;
    }
}
