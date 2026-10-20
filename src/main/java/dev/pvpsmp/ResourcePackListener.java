package dev.pvpsmp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HexFormat;

/** Sends the resource pack to every player on join (config: resource-pack.*). */
public final class ResourcePackListener implements Listener {
    private final PvPSMPPlugin plugin;

    public ResourcePackListener(PvPSMPPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        FileConfiguration c = plugin.getConfig();
        String url = c.getString("resource-pack.url", "").trim();
        if (url.isEmpty()) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            byte[] hash = null;
            String h = c.getString("resource-pack.sha1", "").trim();
            if (!h.isEmpty()) {
                try { hash = HexFormat.of().parseHex(h.toLowerCase()); }
                catch (IllegalArgumentException ex) { plugin.getLogger().warning("resource-pack.sha1 is not valid hex, ignoring it."); }
            }
            Component prompt = Component.text(c.getString("resource-pack.prompt", "Accept the resource pack for custom textures!"));
            p.setResourcePack(url, hash, prompt, c.getBoolean("resource-pack.required", false));
        }, 20L);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (e.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            plugin.getLogger().warning(e.getPlayer().getName() + " failed to download the resource pack. Check resource-pack.url in config.yml.");
        }
    }
}
