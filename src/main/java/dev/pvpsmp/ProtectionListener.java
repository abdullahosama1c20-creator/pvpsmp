package dev.pvpsmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** New-player PvP protection: counts down only while online; protected players can't hurt or be hurt by players. */
public final class ProtectionListener implements Listener {
    private final PvPSMPPlugin plugin;

    public ProtectionListener(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public boolean isProtected(Player p) { return plugin.data().get(p.getUniqueId()).protection > 0; }

    private Player shooter(Entity d) {
        if (d instanceof Player p) return p;
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            DataStore.PData d = plugin.data().get(p.getUniqueId());
            if (d.protection > 0 && --d.protection == 0) {
                p.sendMessage(Txt.c("Your new-player protection has ended. PvP is now ON.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        int left = plugin.data().get(e.getPlayer().getUniqueId()).protection;
        if (left > 0) e.getPlayer().sendMessage(Txt.c("You have new-player protection for " + (left / 60 + 1)
                + " more minutes (only counts while online). Use /protection off to end it early.", NamedTextColor.GREEN));
    }

    // LOW = runs before the ability/combat handlers, which then skip the cancelled event
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = shooter(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (isProtected(victim)) {
            e.setCancelled(true);
            attacker.sendActionBar(Txt.c(victim.getName() + " is a protected new player.", NamedTextColor.GREEN));
        } else if (isProtected(attacker)) {
            e.setCancelled(true);
            attacker.sendActionBar(Txt.c("You can't attack players while protected (/protection off).", NamedTextColor.GREEN));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player thrower)) return;
        for (LivingEntity le : e.getAffectedEntities()) {
            if (le instanceof Player p && !p.equals(thrower) && (isProtected(p) || isProtected(thrower))) {
                e.setIntensity(le, 0);
            }
        }
    }
}
