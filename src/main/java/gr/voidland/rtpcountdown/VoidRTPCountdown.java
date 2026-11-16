package gr.voidland.rtpcountdown;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VoidRTPCountdown extends JavaPlugin implements Listener {

    // Glyphs from the resource pack (font/default.json)
    // E600 = 5 full ... E604 = 1 full, E605 = empty
    private static final char[] BAR = {'\uE600', '\uE601', '\uE602', '\uE603', '\uE604', '\uE605'};
    private static final char BAR_CANCEL = '\uE606';
    private static final char BAR_GO = '\uE607';

    private static final class Session {
        final Location start;
        final String world;
        int left;
        BukkitTask task;
        BukkitTask warp;

        Session(Location start, String world, int left) {
            this.start = start;
            this.world = world;
            this.left = left;
        }
    }

    private final Map<UUID, Session> sessions = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (Session s : sessions.values()) {
            if (s.task != null) s.task.cancel();
        }
        sessions.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("msg-player-only"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(msg("msg-no-world"));
            return true;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            player.sendMessage(msg("msg-already"));
            return true;
        }

        String world = args[0];

        if (player.hasPermission("voidrtp.bypass")) {
            runTeleport(player, world);
            return true;
        }

        int total = Math.max(1, getConfig().getInt("countdown-seconds", 5));
        Session s = new Session(player.getLocation().clone(), world, total);
        sessions.put(player.getUniqueId(), s);

        s.task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline()) {
                end(player.getUniqueId());
                return;
            }
            if (s.left > 0) {
                int fill = (int) Math.ceil(s.left * 5.0 / total); // 5..1
                char bar = BAR[Math.min(5, 5 - fill)];
                show(player, "title-tick", "subtitle-tick", s.left, bar, 0, 25, 0);
                play(player, "sound-tick", 0.8f, 0.6f + 0.1f * (total - s.left));
                if (s.left == 1 && getConfig().getBoolean("warp-animation", true)) {
                    startWarp(player, s);
                }
                s.left--;
            } else {
                end(player.getUniqueId());
                show(player, "title-go", "subtitle-go", 0, BAR_GO, 0, 30, 10);
                play(player, "sound-go", 1f, 1f);
                if (getConfig().getBoolean("warp-animation", true)) {
                    burst(player.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 120);
                }
                runTeleport(player, s.world);
            }
        }, 0L, 20L);

        return true;
    }

    private void runTeleport(Player player, String world) {
        String cmd = getConfig().getString("teleport-command", "rtp world {world}")
                .replace("{player}", player.getName())
                .replace("{world}", world);
        if ("console".equalsIgnoreCase(getConfig().getString("teleport-as", "player"))) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            player.performCommand(cmd);
        }
    }

    private void end(UUID id) {
        Session s = sessions.remove(id);
        if (s != null && s.task != null) s.task.cancel();
        if (s != null && s.warp != null) s.warp.cancel();
    }

    private void cancel(Player player) {
        if (!sessions.containsKey(player.getUniqueId())) return;
        end(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        show(player, "title-cancel", "subtitle-cancel", 0, BAR_CANCEL, 0, 40, 10);
        play(player, "sound-cancel", 1f, 1f);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!getConfig().getBoolean("cancel-on-move", true)) return;
        Session s = sessions.get(e.getPlayer().getUniqueId());
        if (s == null || e.getTo() == null) return;
        Location a = s.start, b = e.getTo();
        if (a.getBlockX() != b.getBlockX() || a.getBlockY() != b.getBlockY()
                || a.getBlockZ() != b.getBlockZ() || a.getWorld() != b.getWorld()) {
            cancel(e.getPlayer());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!getConfig().getBoolean("cancel-on-damage", true)) return;
        if (e.getEntity() instanceof Player p) cancel(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        end(e.getPlayer().getUniqueId());
    }

    // ---------- warp animation (last second) ----------

    private void startWarp(Player player, Session s) {
        if (getConfig().getBoolean("warp-effects", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
        }
        play(player, "sound-warp", 0.5f, 1.2f);

        s.warp = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            int t = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !sessions.containsKey(player.getUniqueId()) || t >= 20) {
                    if (s.warp != null) s.warp.cancel();
                    return;
                }
                Location base = player.getLocation();
                double angle = t * 0.55;
                double radius = 1.3 - t * 0.05;
                double y = 0.1 + t * 0.1;
                for (int k = 0; k < 2; k++) {
                    double a = angle + k * Math.PI;
                    double x = base.getX() + Math.cos(a) * radius;
                    double z = base.getZ() + Math.sin(a) * radius;
                    double yy = base.getY() + y;
                    base.getWorld().spawnParticle(Particle.END_ROD, x, yy, z, 1, 0, 0, 0, 0);
                    base.getWorld().spawnParticle(Particle.PORTAL, x, yy, z, 6, 0.1, 0.1, 0.1, 0.2);
                }
                // flicker the bar between purple (1 segment) and cyan
                if (t > 0 && t % 4 == 0) {
                    char bar = (t / 4) % 2 == 0 ? BAR[4] : BAR_GO;
                    show(player, "title-tick", "subtitle-tick", 1, bar, 0, 6, 0);
                }
                if (t == 19) {
                    burst(base.clone().add(0, 1, 0), Particle.PORTAL, 80);
                }
                t++;
            }
        }, 0L, 1L);
    }

    private void burst(Location loc, Particle type, int count) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(type, loc, count, 0.3, 0.7, 0.3, 0.8);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, count / 4, 0.3, 0.7, 0.3, 0.15);
    }

    // ---------- helpers ----------

    private void show(Player p, String titleKey, String subKey, int time, char bar,
                      int in, int stay, int out) {
        String t = color(getConfig().getString(titleKey, "")
                .replace("{time}", String.valueOf(time)).replace("{bar}", String.valueOf(bar)));
        String s = color(getConfig().getString(subKey, "")
                .replace("{time}", String.valueOf(time)).replace("{bar}", String.valueOf(bar)));
        p.sendTitle(t, s, in, stay, out);
    }

    private void play(Player p, String key, float vol, float pitch) {
        String name = getConfig().getString(key, "");
        if (name == null || name.isEmpty()) return;
        try {
            p.playSound(p.getLocation(), Sound.valueOf(name.toUpperCase()), vol, Math.min(2f, pitch));
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Unknown sound in config: " + name);
        }
    }

    private String msg(String key) {
        return color(getConfig().getString(key, ""));
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
