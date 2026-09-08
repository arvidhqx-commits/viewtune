package dev.viewtune;

import com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ViewTunePlugin extends JavaPlugin implements Listener {

    /** Zeitpunkt, ab dem ein Absenken erlaubt ist — verhindert Zappeln am Schieberegler. */
    private final Map<UUID, Long> lowerAfter = new HashMap<>();
    /** Zuletzt gesetzte Werte, damit nur bei echter Aenderung in die API geschrieben wird. */
    private final Map<UUID, int[]> applied = new HashMap<>();

    private int adaptiveCap;

    // --- Konfiguration, bei jedem Reload frisch eingelesen ---------------------------------
    private int intervalSeconds;
    private int viewMin, viewMaxCfg, lowerDelaySeconds;
    private boolean simEnabled, simNeverAboveView;
    private int simMin, simMaxCfg;
    private boolean adaptiveEnabled;
    private double lowTps, recoverTps;
    private int adaptiveFloor;
    private boolean scalingEnabled;
    private int[][] scalingThresholds;
    private boolean permCapsEnabled;
    private int permScanUpTo;
    private String prefix, msgNoPermission, msgReloaded;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        load();
        adaptiveCap = serverView();
        getServer().getPluginManager().registerEvents(this, this);
        long period = Math.max(1L, intervalSeconds) * 20L;
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, t -> sweep(), period, period);
        getLogger().info("ViewTune " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        restoreAll(getServer().getOnlinePlayers());
        lowerAfter.clear();
        applied.clear();
    }

    /**
     * Setzt jeden Spieler, dem ViewTune eine eigene Distanz gegeben hat, auf die
     * Servervorgabe zurueck. Sicht- und Simulationsdistanz gehoeren zur Verbindung des
     * Spielers, nicht zum Plugin: ohne dieses Aufraeumen behaelt ein beim TPS-Einbruch
     * auf 2 Chunks gedrosselter Spieler diese 2 Chunks, bis er sich neu verbindet —
     * auch wenn ViewTune laengst entfernt ist (gefunden 07.09.2026).
     */
    void restoreAll(java.util.Collection<? extends Player> online) {
        for (Player p : online) {
            if (applied.remove(p.getUniqueId()) == null) continue;
            p.setViewDistance(serverView());
            if (simEnabled) p.setSimulationDistance(serverSim());
        }
    }

    // --- Konfiguration --------------------------------------------------------------------

    private void load() {
        reloadConfig();
        var c = getConfig();
        intervalSeconds = Math.max(1, c.getInt("interval-seconds", 5));
        viewMin = c.getInt("view.min", 2);
        viewMaxCfg = c.getInt("view.max", -1);
        lowerDelaySeconds = Math.max(0, c.getInt("view.lower-delay-seconds", 10));
        simEnabled = c.getBoolean("simulation.enabled", true);
        simMin = c.getInt("simulation.min", 2);
        simMaxCfg = c.getInt("simulation.max", -1);
        simNeverAboveView = c.getBoolean("simulation.never-above-view", true);
        adaptiveEnabled = c.getBoolean("adaptive.enabled", true);
        lowTps = c.getDouble("adaptive.low-tps", 18.0);
        recoverTps = c.getDouble("adaptive.recover-tps", 19.5);
        adaptiveFloor = c.getInt("adaptive.floor", 4);
        scalingEnabled = c.getBoolean("player-scaling.enabled", false);
        scalingThresholds = readThresholds(c.getMapList("player-scaling.thresholds"));
        permCapsEnabled = c.getBoolean("permission-caps.enabled", true);
        permScanUpTo = Math.max(2, Math.min(64, c.getInt("permission-caps.scan-up-to", 32)));
        prefix = c.getString("messages.prefix", "");
        msgNoPermission = c.getString("messages.no-permission", "<red>You may not do that.</red>");
        msgReloaded = c.getString("messages.reloaded", "<green>Configuration reloaded.</green>");
    }

    private int[][] readThresholds(List<Map<?, ?>> raw) {
        List<int[]> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            Object p = m.get("players");
            Object v = m.get("max-view");
            if (p instanceof Number np && v instanceof Number nv) {
                out.add(new int[]{np.intValue(), nv.intValue()});
            }
        }
        out.sort((a, b) -> Integer.compare(a[0], b[0]));
        return out.toArray(new int[0][]);
    }

    private int serverView() {
        return Math.max(Tuner.HARD_MIN, getServer().getViewDistance());
    }

    private int serverSim() {
        return Math.max(Tuner.HARD_MIN, getServer().getSimulationDistance());
    }

    /** Konfigwert fuer eine Welt: Welt-Ueberschreibung, sonst global, {@code -1} = Serverwert. */
    private int worldInt(World w, String path, int global, int serverFallback) {
        int v = global;
        ConfigurationSection s = getConfig().getConfigurationSection("worlds." + w.getName());
        if (s != null && s.contains(path)) {
            v = s.getInt(path);
        }
        return v < 0 ? serverFallback : v;
    }

    // --- Kernschleife ---------------------------------------------------------------------

    private void sweep() {
        if (adaptiveEnabled) {
            double tps = getServer().getTPS()[0];
            adaptiveCap = Tuner.adaptiveStep(adaptiveCap, tps, lowTps, recoverTps,
                    adaptiveFloor, serverView());
        } else {
            adaptiveCap = serverView();
        }
        for (Player p : getServer().getOnlinePlayers()) {
            apply(p, false);
        }
    }

    /**
     * @param immediate beim Join und Weltwechsel gibt es nichts zu entzappeln — dort waere
     *                  die Absenk-Verzoegerung genau die Chunk-Last, die wir sparen wollen.
     */
    private void apply(Player p, boolean immediate) {
        World w = p.getWorld();
        int online = getServer().getOnlinePlayers().size();

        int ceiling = worldInt(w, "view.max", viewMaxCfg, serverView());
        if (scalingEnabled) {
            ceiling = Math.min(ceiling, Tuner.scalingCap(online, scalingThresholds, ceiling));
        }
        ceiling = Math.min(ceiling, adaptiveCap);
        if (permCapsEnabled) {
            ceiling = Math.min(ceiling, permCap(p, "viewtune.view."));
        }
        int min = worldInt(w, "view.min", viewMin, viewMin);
        int targetView = Tuner.viewFor(p.getClientViewDistance(), min, ceiling);

        int targetSim = p.getSimulationDistance();
        if (simEnabled) {
            int simCeil = worldInt(w, "simulation.max", simMaxCfg, serverSim());
            if (permCapsEnabled) {
                simCeil = Math.min(simCeil, permCap(p, "viewtune.sim."));
            }
            int simLow = worldInt(w, "simulation.min", simMin, simMin);
            targetSim = Tuner.simFor(targetView, simLow, simCeil, simNeverAboveView);
        }

        int[] last = applied.get(p.getUniqueId());
        int currentView = last != null ? last[0] : p.getViewDistance();
        if (!immediate && targetView < currentView) {
            // Absenken verzoegern; Anheben passiert sofort.
            long now = System.currentTimeMillis();
            Long due = lowerAfter.get(p.getUniqueId());
            if (due == null) {
                lowerAfter.put(p.getUniqueId(), now + lowerDelaySeconds * 1000L);
                return;
            }
            if (now < due) {
                return;
            }
        }
        lowerAfter.remove(p.getUniqueId());

        if (last == null || last[0] != targetView) {
            p.setViewDistance(targetView);
        }
        if (simEnabled && (last == null || last[1] != targetSim)) {
            p.setSimulationDistance(targetSim);
        }
        applied.put(p.getUniqueId(), new int[]{targetView, targetSim});
    }

    /** Hoechster Zahlenknoten, den der Spieler hat; ohne Knoten keine Begrenzung. */
    private int permCap(Player p, String node) {
        for (int n = permScanUpTo; n >= Tuner.HARD_MIN; n--) {
            if (p.hasPermission(node + n)) {
                return n;
            }
        }
        return Integer.MAX_VALUE;
    }

    // --- Ereignisse -----------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Die Client-Einstellungen treffen erst kurz NACH dem Join ein.
        p.getScheduler().runDelayed(this, t -> apply(p, true), null, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lowerAfter.remove(e.getPlayer().getUniqueId());
        applied.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        applied.remove(e.getPlayer().getUniqueId());
        lowerAfter.remove(e.getPlayer().getUniqueId());
        apply(e.getPlayer(), true);
    }

    @EventHandler
    public void onClientOptions(PlayerClientOptionsChangeEvent e) {
        if (!e.hasViewDistanceChanged()) {
            return;
        }
        Player p = e.getPlayer();
        // Das Ereignis feuert, BEVOR der neue Wert am Spieler steht — einen Tick warten.
        p.getScheduler().runDelayed(this, t -> apply(p, false), null, 1L);
    }

    // --- Befehle --------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("viewtune.admin")) {
            sender.sendMessage(Msg.parse(prefix + msgNoPermission));
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "reload" -> {
                load();
                adaptiveCap = Math.min(adaptiveCap, serverView());
                applied.clear();
                lowerAfter.clear();
                sender.sendMessage(Msg.parse(prefix + msgReloaded));
            }
            case "players" -> {
                if (getServer().getOnlinePlayers().isEmpty()) {
                    sender.sendMessage(Msg.parse(prefix + "<gray>No players online.</gray>"));
                }
                for (Player p : getServer().getOnlinePlayers()) {
                    sender.sendMessage(Component.text(String.format(
                            "%s  client=%d  view=%d  sim=%d  world=%s",
                            p.getName(), p.getClientViewDistance(), p.getViewDistance(),
                            p.getSimulationDistance(), p.getWorld().getName())));
                }
            }
            case "savings" -> {
                int sv = serverView();
                long saved = 0L;
                for (Player p : getServer().getOnlinePlayers()) {
                    saved += Tuner.chunksSaved(sv, p.getViewDistance());
                }
                sender.sendMessage(Msg.parse(prefix + "<green>" + saved
                        + "</green><gray> chunks not loaded, versus a flat view-distance of "
                        + sv + ".</gray>"));
            }
            default -> {
                sender.sendMessage(Msg.parse(prefix + "<white>v" + getPluginMeta().getVersion()
                        + "</white>"));
                sender.sendMessage(Component.text(String.format(
                        "TPS %.2f | adaptive cap %d | server view %d / sim %d | players %d",
                        getServer().getTPS()[0], adaptiveCap, serverView(), serverSim(),
                        getServer().getOnlinePlayers().size())));
                sender.sendMessage(Component.text(
                        "/viewtune players | /viewtune savings | /viewtune reload"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("status", "players", "savings", "reload")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
