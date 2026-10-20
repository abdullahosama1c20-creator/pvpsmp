package dev.pvpsmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.*;

import java.util.List;
import java.util.UUID;

/** Enforces chunk claims: no breaking, placing, opening, burning, exploding or flowing into other people's land. */
public final class ClaimListener implements Listener {
    private final PvPSMPPlugin plugin;
    private final ClaimManager cm;

    public ClaimListener(PvPSMPPlugin plugin) {
        this.plugin = plugin;
        this.cm = plugin.claims();
    }

    private boolean deny(Player p, Location l) {
        UUID o = cm.ownerAt(l);
        if (cm.allowed(p, o)) return false;
        p.sendActionBar(Txt.c("This land is claimed by " + cm.name(o) + ".", NamedTextColor.RED));
        return true;
    }

    private boolean deny(Player p, Block b) {
        UUID o = cm.ownerAt(b);
        if (cm.allowed(p, o)) return false;
        p.sendActionBar(Txt.c("This land is claimed by " + cm.name(o) + ".", NamedTextColor.RED));
        return true;
    }

    private Player playerOf(Entity e) {
        if (e instanceof Player p) return p;
        if (e instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    // ---- building ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) { if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null) {
            if (deny(e.getPlayer(), e.getBlock())) e.setCancelled(true);
        } else if (cm.ownerAt(e.getBlock()) != null
                && (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (cm.ownerAt(e.getBlock()) != null) e.setCancelled(true); }

    // ---- using blocks (chests, doors, buttons, farmland...) ----
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (deny(e.getPlayer(), b)) e.setUseInteractedBlock(Event.Result.DENY);
        } else if (e.getAction() == Action.PHYSICAL) {
            if (!cm.allowed(e.getPlayer(), cm.ownerAt(b))) e.setUseInteractedBlock(Event.Result.DENY); // trampling, plates
        }
    }

    // ---- entities ----
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (e.getPlayer() != null && deny(e.getPlayer(), e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && deny(e.getPlayer(), e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player p = playerOf(e.getRemover());
        if (p != null && deny(p, e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Entity t = e.getRightClicked();
        if ((t instanceof ItemFrame || t instanceof ArmorStand) && deny(e.getPlayer(), t.getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (deny(e.getPlayer(), e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    /** Animals, villagers, armor stands etc. inside claims. Hostile mobs and players are still fair game. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity v = e.getEntity();
        if (v instanceof Player || v instanceof Monster) return;
        Player p = playerOf(e.getDamager());
        if (p != null && deny(p, v.getLocation())) e.setCancelled(true);
    }

    // ---- explosions, pistons, liquids, hoppers ----
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> cm.ownerAt(b) != null);   // explosions NEVER damage claims, even raidable ones
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> cm.ownerAt(b) != null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { cm.touch(e.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { cm.touch(e.getPlayer().getUniqueId()); }

    private boolean pistonCrosses(Block piston, List<Block> moved, org.bukkit.block.BlockFace dir) {
        UUID po = cm.ownerAt(piston);
        for (Block b : moved) {
            UUID from = cm.ownerAt(b), to = cm.ownerAt(b.getRelative(dir));
            if (from != null && !from.equals(po)) return true;
            if (to != null && !to.equals(po)) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (pistonCrosses(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (pistonCrosses(e.getBlock(), e.getBlocks(), e.getDirection())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block a = e.getBlock(), b = e.getToBlock();
        if ((a.getX() >> 4) == (b.getX() >> 4) && (a.getZ() >> 4) == (b.getZ() >> 4)) return;
        UUID to = cm.ownerAt(b);
        if (to != null && !to.equals(cm.ownerAt(a))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        Location s = e.getSource().getLocation(), d = e.getDestination().getLocation();
        if (s == null || d == null) return;
        UUID so = cm.ownerAt(s);
        if (so == null) return;
        UUID dest = cm.ownerAt(d);
        if (so.equals(dest) || (dest != null && cm.isTrusted(so, dest))) return;
        e.setCancelled(true);
    }

    // ---- entering / leaving notice ----
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if (t == null || ((f.getBlockX() >> 4) == (t.getBlockX() >> 4) && (f.getBlockZ() >> 4) == (t.getBlockZ() >> 4)
                && f.getWorld() == t.getWorld())) return;
        UUID from = cm.ownerAt(f), to = cm.ownerAt(t);
        if (java.util.Objects.equals(from, to)) return;
        if (to != null) e.getPlayer().sendActionBar(Txt.c("Entering " + cm.name(to) + "'s land", NamedTextColor.YELLOW));
        else e.getPlayer().sendActionBar(Txt.c("Leaving claimed land", NamedTextColor.GRAY));
    }
}
