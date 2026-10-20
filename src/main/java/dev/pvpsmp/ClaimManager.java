package dev.pvpsmp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Chunk claims (anti-griefing). One owner per chunk, trust list per owner. Stored in claims.yml. */
public final class ClaimManager {
    private final PvPSMPPlugin plugin;
    private final File file;
    private final Map<String, UUID> owners = new HashMap<>();
    private final Map<UUID, Set<UUID>> trust = new HashMap<>();
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    public ClaimManager(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        load();
    }

    private static String key(String world, int cx, int cz) { return world + ";" + cx + ";" + cz; }

    public UUID ownerAt(Location l) {
        if (l == null || l.getWorld() == null) return null;
        return owners.get(key(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4));
    }

    public UUID ownerAt(Block b) { return owners.get(key(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4)); }

    public boolean isTrusted(UUID owner, UUID other) { return trust.getOrDefault(owner, Set.of()).contains(other); }

    /** Remember when an owner was last online (called on join/quit). */
    public void touch(UUID id) { lastSeen.put(id, System.currentTimeMillis()); }

    /** Claims open up for raiding once the owner has been offline longer than claims.raidable-after-offline-hours (0 = never). */
    public boolean raidable(UUID owner) {
        double hours = plugin.getConfig().getDouble("claims.raidable-after-offline-hours", 72);
        if (hours <= 0 || Bukkit.getPlayer(owner) != null) return false;
        long ls = lastSeen.computeIfAbsent(owner, id -> Bukkit.getOfflinePlayer(id).getLastSeen());
        return ls > 0 && System.currentTimeMillis() - ls > (long) (hours * 3_600_000L);
    }

    /** owner == null means unclaimed (always allowed). Explosions are handled separately and are ALWAYS blocked. */
    public boolean allowed(Player p, UUID owner) {
        return owner == null || owner.equals(p.getUniqueId()) || isTrusted(owner, p.getUniqueId())
                || p.hasPermission("pvpsmp.bypass") || raidable(owner);
    }

    /** Claim slots: base + 1 per N hours played, capped at max-per-player. */
    public int limitFor(Player p) {
        int base = plugin.getConfig().getInt("claims.base-limit", 3);
        int perHours = Math.max(1, plugin.getConfig().getInt("claims.extra-claim-per-hours-played", 5));
        int max = plugin.getConfig().getInt("claims.max-per-player", 15);
        long hours = p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L / 3600L;
        return (int) Math.min(max, base + hours / perHours);
    }

    public int count(UUID owner) {
        int n = 0;
        for (UUID u : owners.values()) if (u.equals(owner)) n++;
        return n;
    }

    public void claim(UUID owner, Location l) {
        owners.put(key(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4), owner);
        save();
    }

    public void unclaim(Location l) {
        owners.remove(key(l.getWorld().getName(), l.getBlockX() >> 4, l.getBlockZ() >> 4));
        save();
    }

    public Set<UUID> trusted(UUID owner) { return trust.getOrDefault(owner, Set.of()); }

    public void trust(UUID owner, UUID other) { trust.computeIfAbsent(owner, k -> new HashSet<>()).add(other); save(); }

    public void untrust(UUID owner, UUID other) {
        Set<UUID> s = trust.get(owner);
        if (s != null) s.remove(other);
        save();
    }

    public String name(UUID id) {
        String n = id == null ? null : Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "someone" : n;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> m : y.getMapList("claims")) {
            try {
                String w = String.valueOf(m.get("world"));
                int x = ((Number) m.get("x")).intValue(), z = ((Number) m.get("z")).intValue();
                owners.put(key(w, x, z), UUID.fromString(String.valueOf(m.get("owner"))));
            } catch (Exception ignored) { }
        }
        ConfigurationSection t = y.getConfigurationSection("trust");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                try {
                    Set<UUID> s = new HashSet<>();
                    for (String u : t.getStringList(k)) s.add(UUID.fromString(u));
                    trust.put(UUID.fromString(k), s);
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, UUID> e : owners.entrySet()) {
            String[] p = e.getKey().split(";");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", p[0]);
            m.put("x", Integer.parseInt(p[1]));
            m.put("z", Integer.parseInt(p[2]));
            m.put("owner", e.getValue().toString());
            list.add(m);
        }
        y.set("claims", list);
        for (Map.Entry<UUID, Set<UUID>> e : trust.entrySet()) {
            y.set("trust." + e.getKey(), e.getValue().stream().map(UUID::toString).toList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save claims.yml: " + ex.getMessage());
        }
    }
}
