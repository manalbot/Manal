package com.nalla.guitpa;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class NallaGUITPA extends JavaPlugin implements Listener {
    private final Map<UUID, TpaRequest> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pages = new HashMap<>();
    private final Map<UUID, Boolean> hereMode = new HashMap<>();
    private int pageSize;
    private long timeoutMs, cooldownMs;

    @Override public void onEnable() {
        saveDefaultConfig();
        pageSize = Math.max(9, Math.min(45, getConfig().getInt("page-size", 45)));
        pageSize = (pageSize / 9) * 9;
        timeoutMs = getConfig().getLong("request-timeout-seconds", 60) * 1000L;
        cooldownMs = getConfig().getLong("cooldown-seconds", 5) * 1000L;
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NallaGUITPA enabled - GUI-only TPA");
        Bukkit.getScheduler().runTaskTimer(this, this::expireRequests, 20L, 20L);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        openMain(p, 0);
        return true;
    }

    private String c(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private String msg(String key) { return c(getConfig().getString("messages." + key, "")); }
    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat); ItemMeta m = i.getItemMeta();
        m.setDisplayName(c(name)); m.setLore(lore == null ? List.of() : lore.stream().map(this::c).toList()); i.setItemMeta(m); return i;
    }
    private ItemStack head(Player p) {
        ItemStack i = new ItemStack(Material.PLAYER_HEAD); SkullMeta m = (SkullMeta)i.getItemMeta();
        m.setOwningPlayer(p); m.setDisplayName(c(getConfig().getString("items.tpa.name", "&b%player%").replace("%player%", p.getName())));
        m.setLore(getConfig().getStringList("items.tpa.lore").stream().map(this::c).toList()); i.setItemMeta(m); return i;
    }
    private void filler(Inventory inv) {
        ItemStack glass = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i=pageSize;i<54;i++) inv.setItem(i, glass);
    }

    public void openMain(Player p, int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.remove(p); players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int maxPage = Math.max(0, (players.size()-1) / pageSize);
        page = Math.max(0, Math.min(page, maxPage)); pages.put(p.getUniqueId(), page);
        boolean here = hereMode.getOrDefault(p.getUniqueId(), false);
        Inventory inv = Bukkit.createInventory(null, 54, c(getConfig().getString("title", "&8📱 TPA - Online Players")));
        int start = page * pageSize;
        for (int i=0;i<pageSize && start+i<players.size();i++) inv.setItem(i, head(players.get(start+i)));
        filler(inv);
        if (page > 0) inv.setItem(45, item(Material.ARROW, getConfig().getString("items.previous.name","&e« Previous Page"), List.of()));
        if (page < maxPage) inv.setItem(53, item(Material.ARROW, getConfig().getString("items.next.name","&eNext Page »"), List.of()));
        inv.setItem(48, item(here ? Material.ENDER_PEARL : Material.COMPASS, here ? "&dMode: TPA-HERE" : "&bMode: TPA", List.of("&7Click to switch mode")));
        inv.setItem(49, item(Material.BARRIER, getConfig().getString("items.close.name","&cClose"), List.of()));
        p.openInventory(inv);
    }

    private Player playerFromSlot(Player viewer, int slot) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers()); players.remove(viewer); players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int page = pages.getOrDefault(viewer.getUniqueId(), 0), index = page*pageSize+slot;
        return index >= 0 && index < players.size() ? players.get(index) : null;
    }

    private void sendRequest(Player sender, Player target, boolean here) {
        if (sender.equals(target)) { sender.sendMessage(msg("self")); return; }
        long now = System.currentTimeMillis(), last = cooldowns.getOrDefault(sender.getUniqueId(), 0L);
        if (now-last < cooldownMs) { sender.sendMessage(msg("cooldown").replace("%seconds%", String.valueOf(Math.max(1, (cooldownMs-(now-last)+999)/1000)))); return; }
        TpaRequest r = new TpaRequest(sender.getUniqueId(), target.getUniqueId(), here, now + timeoutMs); requests.put(target.getUniqueId(), r); cooldowns.put(sender.getUniqueId(), now);
        sender.sendMessage((here ? msg("sent-here") : msg("sent")).replace("%target%", target.getName()));
        openRequest(target, r);
    }

    private void openRequest(Player target, TpaRequest r) {
        Player sender = Bukkit.getPlayer(r.sender); if (sender == null) return;
        Inventory inv = Bukkit.createInventory(null, 27, c(getConfig().getString("request-title", "&8📱 Incoming TPA Request")));
        for (int i=0;i<27;i++) inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE," ",List.of()));
        inv.setItem(11, item(Material.LIME_WOOL, "&aACCEPT", List.of("&7Accept &f"+sender.getName()+"&7's request")));
        inv.setItem(15, item(Material.RED_WOOL, "&cDENY", List.of("&7Deny this request")));
        ItemStack h = head(sender); ItemMeta hm=h.getItemMeta(); hm.setLore(List.of(c(r.here ? "&dTPA-HERE request" : "&bTPA request"), c("&7From: &f"+sender.getName()))); h.setItemMeta(hm); inv.setItem(13,h);
        target.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return; String title=e.getView().getTitle(); e.setCancelled(true);
        String main=c(getConfig().getString("title", "&8📱 TPA - Online Players")), req=c(getConfig().getString("request-title", "&8📱 Incoming TPA Request"));
        if (title.equals(main)) {
            int s=e.getRawSlot(); if(s==49){p.closeInventory();return;} if(s==48){hereMode.put(p.getUniqueId(), !hereMode.getOrDefault(p.getUniqueId(), false)); openMain(p,pages.getOrDefault(p.getUniqueId(),0)); return;} if(s==45){openMain(p,pages.getOrDefault(p.getUniqueId(),0)-1);return;} if(s==53){openMain(p,pages.getOrDefault(p.getUniqueId(),0)+1);return;}
            if(s>=0 && s<pageSize){ Player target=playerFromSlot(p,s); if(target!=null){ sendRequest(p,target,hereMode.getOrDefault(p.getUniqueId(), false)); p.closeInventory(); }}
        } else if(title.equals(req)) {
            TpaRequest r=requests.get(p.getUniqueId()); if(r==null){p.closeInventory();return;}
            if(System.currentTimeMillis()>r.expires){requests.remove(p.getUniqueId());p.sendMessage(msg("expired"));p.closeInventory();return;}
            Player sender=Bukkit.getPlayer(r.sender); if(sender==null){requests.remove(p.getUniqueId());p.sendMessage(msg("offline"));p.closeInventory();return;}
            if(e.getRawSlot()==11){ requests.remove(p.getUniqueId()); if(r.here) sender.teleport(p.getLocation()); else p.teleport(sender.getLocation()); p.sendMessage(msg("accepted")); sender.sendMessage(msg("teleported")); p.closeInventory(); }
            else if(e.getRawSlot()==15){requests.remove(p.getUniqueId());p.sendMessage(msg("denied"));sender.sendMessage(msg("denied"));p.closeInventory();}
        }
    }

    @EventHandler public void close(InventoryCloseEvent e){ if(e.getPlayer() instanceof Player p) pages.remove(p.getUniqueId()); }
    @EventHandler public void quit(PlayerQuitEvent e){ requests.remove(e.getPlayer().getUniqueId()); cooldowns.remove(e.getPlayer().getUniqueId()); pages.remove(e.getPlayer().getUniqueId()); hereMode.remove(e.getPlayer().getUniqueId()); }
    private void expireRequests(){ long now=System.currentTimeMillis(); requests.entrySet().removeIf(en -> en.getValue().expires < now); }
    private record TpaRequest(UUID sender, UUID target, boolean here, long expires) {}
}
