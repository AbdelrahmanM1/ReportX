package me.abdoabk.reportx.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that maps a player UUID to their currently open GUI instance.
 *
 * @param <T> The GUI type managed by this registry.
 */
public class GUIManager<T> {

    private final Map<UUID, T> openGUIs = new ConcurrentHashMap<>();

    /** Register (or replace) the active GUI for a player. */
    public void registerGUI(UUID playerUuid, T gui) {
        openGUIs.put(playerUuid, gui);
    }

    /** Returns the active GUI for a player, or {@code null} if none is registered. */
    public T getOpenGUI(UUID playerUuid) {
        return openGUIs.get(playerUuid);
    }

    /** Removes the active GUI entry for a player. */
    public void removeGUI(UUID playerUuid) {
        openGUIs.remove(playerUuid);
    }

    /** Returns true if the player has an active GUI registered. */
    public boolean hasOpenGUI(UUID playerUuid) {
        return openGUIs.containsKey(playerUuid);
    }
}
