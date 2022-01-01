package net.devjoe.reviveme;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class RevivePlugin extends JavaPlugin implements Listener {
    public Objective waitCounter;
    public Objective deathCounter;
    public Scoreboard mainBoard;
    public Scoreboard reviveBoard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mainBoard = getServer().getScoreboardManager().getMainScoreboard();
        deathCounter = mainBoard.getObjective("deaths");
        if(deathCounter == null)
            deathCounter = mainBoard.registerNewObjective("deaths", "deathCount");
        reviveBoard = getServer().getScoreboardManager().getNewScoreboard();
        waitCounter = reviveBoard.registerNewObjective("wait", "dummy");
        waitCounter.setDisplayName("Minutes until revive");
        waitCounter.setDisplaySlot(DisplaySlot.SIDEBAR);
        deathCounter.setDisplaySlot(DisplaySlot.SIDEBAR);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                evaluateSidebar();
            }
        }, 10, 20 * 15);
        getServer().getPluginManager().registerEvents(this, this);
    }

    public void evaluateSidebar() {
        boolean hideRevives = true;
        for(String p : mainBoard.getEntries()) {
            OfflinePlayer pl = getServer().getOfflinePlayer(p);
            if(pl == null)
            {
                System.out.println("[WARN] Player " + p + " in scoreboard but does not have an associated OfflinePlayer");
                continue;
            }
            String uuid = pl.getUniqueId().toString();
            long remain = getConfig().getLong("pendingRevives." + uuid, -1);
            if(remain != -1) remain -= System.currentTimeMillis();
            int in_mins = (int)(remain / 60000);
            if(remain == -1) {
                if(waitCounter.getScore(pl.getName()).isScoreSet())
                    reviveBoard.resetScores(pl.getName());
                continue;
            }
            hideRevives = false;
            waitCounter.getScore(pl.getName()).setScore(in_mins+1);
        }
        for (Player p : getServer().getOnlinePlayers()) {
            p.setScoreboard(hideRevives ? mainBoard : reviveBoard);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!command.getName().equalsIgnoreCase("revive")) return false;
        if(!(sender instanceof Player)) return true;
        Player pl = (Player) sender;
        long timeNow = System.currentTimeMillis();
        long reviveTime = getConfig().getLong("pendingRevives." + pl.getUniqueId().toString(), -1);
        if(reviveTime == -1) {
            pl.sendMessage(formatMessage("You're already alive, but I'll reset your gamemode just in case."));
            pl.setGameMode(GameMode.SURVIVAL);
            return true;
        }
        if(reviveTime > timeNow) {
            pl.sendMessage(formatMessage("You're not ready to be revived yet."));
            return true;
        }
        pl.teleport(new Location(getServer().getWorld("world"), getConfig().getInt("hospital.x"), getConfig().getInt("hospital.y"), getConfig().getInt("hospital.z")));
        pl.setGameMode(GameMode.SURVIVAL);
        getConfig().set("pendingRevives." + pl.getUniqueId().toString(), null);
        saveConfig();
        getServer().broadcastMessage(formatMessage(pl.getName() + " has been revived."));
        evaluateSidebar();
        return true;
    }

    public String formatMessage(String message) {
        return ChatColor.GOLD + "" + ChatColor.BOLD + "> " + ChatColor.GRAY + message;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        String uuid = event.getEntity().getUniqueId().toString();
        int deathCount = deathCounter.getScore(event.getEntity().getName()).getScore() + 1;
        int timeout_m = deathCount * 10;
        int timeout_ms = timeout_m * 60000;
        long untimeout_at = System.currentTimeMillis() + timeout_ms;
        event.setDeathMessage(formatMessage(event.getDeathMessage()));
        Calendar c = Calendar.getInstance();
        SimpleDateFormat form = new SimpleDateFormat("hh:mm");
        String tod = form.format(c.getTime());
        event.getEntity().sendMessage(formatMessage("You may respawn in " + timeout_m + " minutes"));
        event.getEntity().sendMessage(formatMessage("Time of death: " + tod));
        event.getEntity().sendMessage(formatMessage("New death count: " + deathCount));
        getConfig().set("pendingRevives." + uuid, untimeout_at);
        saveConfig();
        evaluateSidebar();
    }
}
















