package gr.voidland.rtpcountdown;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
                s.left--;
            } else {
                end(player.getUniqueId());
                show(player, "title-go", "subtitle-go", 0, BAR_GO, 0, 30, 10);
                play(player, "sound-go", 1f, 1f);
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
    }

    private void cancel(Player player) {
        if (!sessions.containsKey(player.getUniqueId())) return;
        end(player.getUniqueId());
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
