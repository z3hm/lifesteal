package me.z3hm.lifesteal;

import io.papermc.paper.ban.BanListType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public final class Lifesteal extends JavaPlugin implements Listener, CommandExecutor {

    private HashSet<Player> usingRevive = new HashSet<>();

    private final File dataFile = new File(getDataFolder(), "data.yml");
    private FileConfiguration data;


    @Override
    public void onEnable() {
        if (!this.dataFile.exists()) {
            getDataFolder().mkdirs();
            try {
                this.dataFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);


        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("lifesteal").setExecutor(this);

        NamespacedKey key = new NamespacedKey(this, "revive_recipe");
        ShapedRecipe recipe = new ShapedRecipe(key, getReviveItem(1));
        recipe.shape(
                "DTD",
                "TBT",
                "DTD"
        );
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
        recipe.setIngredient('B', Material.BEACON);

        Bukkit.addRecipe(recipe);

        saveDefaultConfig();
    }

    @Override
    public void onDisable() {
        try {
            this.data.save(dataFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {

        String commandString = command.getName();
        if (!commandString.equalsIgnoreCase("lifesteal") && !commandString.equalsIgnoreCase("ls")) return true;

        if (args.length == 0) return true;

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("lifesteal.admin") && !sender.isOp()) return true;

            if (args.length < 3) return true;

            Player player = Bukkit.getPlayer(args[1]);

            if (player == null) return true;

            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    return true;
                }
            }

            ItemStack item = null;
            if (args[2].equalsIgnoreCase("heart")) {
                item = getHeartItem(amount);
            }
            else if (args[2].equalsIgnoreCase("revive")) {
                item = getReviveItem(amount);
            }
            if (item == null) {
                sender.sendMessage("§cThis item does not exist!");
                return true;
            }

            giveSafe(player, item);
            return true;
        }
        else if (args[0].equalsIgnoreCase("withdraw")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            if (getMaxHealth(player) <= 2) {
                player.sendMessage("§cYou don't have enough hearts to do that!");
                return true;
            }

            setMaxHealth(player, getMaxHealth(player) - 2);
            giveSafe(player, getHeartItem(1));

            player.sendMessage("§aYou have withdrawn 1 heart!");
            return true;
        }
        else if (args[0].equalsIgnoreCase("revive")) {
            if (!sender.hasPermission("lifesteal.admin") && !sender.isOp()) return true;
            if (args.length < 2) return true;

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);

            if (revive(target)) {
                sender.sendMessage("§aPlayer §2" + target.getName() + "§a revived! Tell them to join again!");
            }
            else {
                sender.sendMessage("§cYou can't revive that player!");
            }
            return true;
        }
        else if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lifesteal.admin") && !sender.isOp()) return true;

            reloadConfig();
            return true;
        }


        return true;
    }

    private void setMaxHealth(Player player, double health) {
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
    }

    private double getMaxHealth(Player player) {
        return player.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
    }

    private void giveSafe(Player player, ItemStack item) {
        Inventory inv = player.getInventory();
        if (inv.firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), item);
            return;
        }
        inv.addItem(item);
    }

    private ItemStack getReviveItem(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text("Revive").color(NamedTextColor.AQUA));
        meta.lore(Arrays.asList(
                Component.text("Revives a dead player").color(NamedTextColor.GRAY),
                Component.text("Click to use").color(NamedTextColor.GRAY),
                Component.text("lifesteal:revive").color(NamedTextColor.DARK_GRAY)
        ));
        meta.setItemModel(NamespacedKey.fromString(getConfig().getString("reviveTexture", "smp:revive")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getHeartItem(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text("Heart").color(NamedTextColor.RED));
        meta.lore(Arrays.asList(
                Component.text("Gives the user a full heart").color(NamedTextColor.GRAY),
                Component.text("Click to use").color(NamedTextColor.GRAY),
                Component.text("lifesteal:heart").color(NamedTextColor.DARK_GRAY)
                ));
        meta.setItemModel(NamespacedKey.fromString(getConfig().getString("heartTexture", "smp:heart")));
        item.setItemMeta(meta);
        return item;
    }

    private boolean loreContains(ItemStack item, String string) {
        if (!item.hasItemMeta()) return false;
        if (!item.getItemMeta().hasLore()) return false;

        for (Component line : item.getItemMeta().lore()) {
            if (line.toString().contains(string)) return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (event.getPlayer().hasPermission("lifesteal.bypass") || event.getPlayer().isOp()) return;

        Player player = event.getPlayer();

        if (getMaxHealth(player) <= 2) {
            player.ban("§fYou've got §cno hearts left§f!", Duration.ofDays(9999L), "§fAsk your teammates to §brevive §fyou, or §bbuy a revive §fat our store: §awww.website.com", true);
            this.data.set("deadPlayers." + player.getUniqueId().toString(), true);
        }else {
            setMaxHealth(player, getMaxHealth(player)-2);
        }

        Entity entity = event.getDamageSource().getCausingEntity();

        if (!(entity instanceof Player)) return;
        Player killer = (Player) entity;
        setMaxHealth(killer, getMaxHealth(killer)+2);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().equals(Action.RIGHT_CLICK_AIR) && !event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
        if (!event.hasItem()) return;

        ItemStack item = event.getItem();
        assert item != null;
        if (loreContains(item, "lifesteal:heart")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (getMaxHealth(player) >= getConfig().getInt("maxHealth", 60)) {
                player.sendMessage("§cYou already have the max amount of hearts!");
                return;
            }


            item.setAmount(item.getAmount()-1);

            setMaxHealth(player, getMaxHealth(player)+2);
        }


        else if (loreContains(item, "lifesteal:revive")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (this.usingRevive.contains(player)) {
                player.sendMessage("§cYou're already using this item!");
                player.sendMessage("§fType the name of the player you want to revive in chat! Or '§cCancel§f' to cancel.");
                return;
            }
            this.usingRevive.add(player);
            player.sendMessage("§fReviving player! Type the name of the player you want to revive, or '§cCancel§f' to cancel.");
            item.setAmount(item.getAmount()-1);
        }
    }

    @EventHandler
    public void onPlayerMessage(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!this.usingRevive.contains(player)) return;

        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (message.equalsIgnoreCase("cancel")) {
            this.usingRevive.remove(player);
            giveSafe(player, getReviveItem(1));
            player.sendMessage("§cCancelled §frevive operation!");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(message);

        if (revive(target)) {
            player.sendMessage("§aPlayer §2" + target.getName() + "§a revived! Tell them to join again!");
        }
        else {
            player.sendMessage("§cYou can't revive that player!");
            giveSafe(player, getReviveItem(1));
        }
        this.usingRevive.remove(player);

    }

    public boolean revive(OfflinePlayer target) {
        if (!target.isBanned()) {
            return false;
        }
        ConfigurationSection section = this.data.getConfigurationSection("deadPlayers");
        if (section == null) {
            return false;
        }

        Set<String> deadPlayers = section.getKeys(false);
        if (!deadPlayers.contains(target.getUniqueId().toString())) {
            return false;
        }
        section.set(target.getUniqueId().toString(), null);
        Bukkit.getBanList(BanListType.PROFILE).pardon(target.getPlayerProfile());

        return true;
    }
}
