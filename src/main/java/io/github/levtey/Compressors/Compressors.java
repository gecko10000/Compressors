package io.github.levtey.Compressors;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Dispenser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class Compressors extends JavaPlugin implements Listener {
	
	FileConfiguration config;
	File langFile;
	FileConfiguration lang;
	NamespacedKey compressorKey = new NamespacedKey(this, "compressor");
	
	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		config = this.getConfig();
		createLangFile();
		Bukkit.getServer().getPluginManager().registerEvents(this, this);
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("compressors")) {
			if (args.length > 0) {
				if (args[0].equalsIgnoreCase("give")) {
					if (sender.hasPermission("compressors.give")) {
						if (args.length == 1 && sender instanceof Player) {
							((Player)sender).getInventory().addItem(createCompressor());
							sender.sendMessage(processColor(lang.getString("successfulGive"), sender.getName()));
						} else if (args.length > 1) {
							Player targetPlayer = Bukkit.getPlayer(args[1]);
							if (targetPlayer != null) {
								targetPlayer.getInventory().addItem(createCompressor());
								sender.sendMessage(processColor(lang.getString("successfulGive"), targetPlayer.getName()));
								if (!targetPlayer.equals((Player)sender)) {
									targetPlayer.sendMessage(processColor(lang.getString("successfulGiveOtherPlayer"), sender.getName()));
								}
							}
						}
					} else {
						sender.sendMessage(processColor(lang.getString("noPerms"), null));
					}
				} else if (args[0].equalsIgnoreCase("reload")) {
					if (sender.hasPermission("compressors.reload")) {
						this.saveDefaultConfig();
						this.reloadConfig();
						config = this.getConfig();
						createLangFile();
						sender.sendMessage(processColor(lang.getString("reload"), null));
					} else {
						sender.sendMessage(processColor(lang.getString("noPerms"), null));
					}
				}
			}
		}
		return true;
	}
	
	@EventHandler (ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent evt) {
		if (evt.getItemInHand().getType().equals(Material.DISPENSER) && evt.getItemInHand().getItemMeta().getPersistentDataContainer().has(compressorKey, PersistentDataType.BYTE)) {
			if (evt.getPlayer().hasPermission("compressors.place")) {
				Dispenser compressor = ((Dispenser)evt.getBlock().getState());
				compressor.getPersistentDataContainer().set(compressorKey, PersistentDataType.BYTE, (byte)1);
				compressor.update(true);
			} else {
				evt.getPlayer().sendMessage(processColor(lang.getString("noPlacePerms"), null));
				evt.setCancelled(true);
			}
		}
	}
	
	@EventHandler (ignoreCancelled = true)
	public void onBreak(BlockBreakEvent evt) {
		if (evt.getBlock().getType().equals(Material.DISPENSER) && ((Dispenser)evt.getBlock().getState()).getPersistentDataContainer().has(compressorKey, PersistentDataType.BYTE) && evt.isDropItems()) {
			evt.setDropItems(false);
			evt.getBlock().getWorld().dropItemNaturally(evt.getBlock().getLocation(), createCompressor());
		}
	}
	
	@EventHandler (ignoreCancelled = true)
	public void onDispense(BlockDispenseEvent evt) {
		config.set("recipes.test.from", new ItemStack(Material.BARRIER));
		this.saveConfig();
		if (evt.getBlock().getType().equals(Material.DISPENSER) && ((Dispenser)evt.getBlock().getState()).getPersistentDataContainer().has(compressorKey, PersistentDataType.BYTE)) {
			final Dispenser compressor = (Dispenser)evt.getBlock().getState();
			for (String key : config.getConfigurationSection("recipes").getKeys(false)) {
				if (evt.getItem().isSimilar(config.getItemStack("recipes." + key + ".from"))) {
					if (compressor.getInventory().containsAtLeast(config.getItemStack("recipes." + key + ".from"), config.getInt("recipes." + key + ".amount"))) {
						evt.setItem(config.getItemStack("recipes." + key + ".to"));
						ItemStack toRemove = config.getItemStack("recipes." + key + ".from");
						toRemove.setAmount(config.getInt("recipes." + key + ".amount"));
						Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
							public void run() {
								Inventory compressorInventory = compressor.getInventory();
								compressorInventory.removeItem(toRemove);
							}
						}, 1L);
					}
				}
			}
		}
	}
	
	public ItemStack createCompressor() {
		ItemStack compressor = new ItemStack(Material.DISPENSER);
		ItemMeta compressorMeta = compressor.getItemMeta();
		compressorMeta.setDisplayName(processColor(config.getString("item.name"), null));
		List<String> lore = config.getStringList("item.lore");
		lore.forEach(loreLine -> processColor(loreLine, null));
		compressorMeta.setLore(lore);
		compressorMeta.getPersistentDataContainer().set(compressorKey, PersistentDataType.BYTE, (byte)1);
		compressor.setItemMeta(compressorMeta);
		return compressor;
	}
	
	public String processColor(String input, String playerName) {
		return ChatColor.translateAlternateColorCodes('&', input.replaceAll("%prefix%", lang.getString("prefix")).replaceAll("%player%", playerName));
	}
	
	public void createLangFile() {
		langFile = new File(getDataFolder(), "lang.yml");
		if (!langFile.exists()) {
			langFile.getParentFile().mkdirs();
			saveResource("lang.yml", false);
		}
		lang = new YamlConfiguration();
		try {
			lang.load(langFile);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}
}
