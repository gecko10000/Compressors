package io.github.levtey.Compressors;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
				} else if (args[0].equalsIgnoreCase("create")) {
					if (sender.hasPermission("compressors.create")) {
						if (args.length >= 3 && (args[2].equals("from") || args[2].equals("to"))) {
							if (sender instanceof Player && !(((Player)sender).getInventory().getItemInMainHand().getType().equals(Material.AIR))) {
								ItemStack item = ((Player)sender).getInventory().getItemInMainHand();
								config.set("recipes." + args[1]+ "." + args[2], item);
								this.saveConfig();
								sender.sendMessage(ChatColor.translateAlternateColorCodes('&', lang.getString("itemCreated").replaceAll("%prefix%", lang.getString("prefix")).replaceAll("%recipe%", args[1]).replaceAll("%from/to%", args[2]).replaceAll("%type%", item.getType().toString())));
							} else {
								sender.sendMessage(processColor(lang.getString("holdSomething"), null));
							}
						}
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
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onBreak(BlockBreakEvent evt) {
		Block block = evt.getBlock();
		if (block.getType().equals(Material.DISPENSER) && ((Dispenser)block.getState()).getPersistentDataContainer().has(compressorKey, PersistentDataType.BYTE) && !block.getDrops(evt.getPlayer().getInventory().getItemInMainHand()).isEmpty()) {
			evt.setDropItems(false);
			World world = block.getWorld();
			Location blockLoc = block.getLocation();
			for (ItemStack invItem : ((Dispenser) block.getState()).getInventory()) {
				if (invItem == null) continue;
				world.dropItemNaturally(blockLoc, invItem);
			}
			world.dropItemNaturally(block.getLocation(), createCompressor());
		}
	}
	
	@EventHandler (ignoreCancelled = true)
	public void onDispense(BlockDispenseEvent evt) {
		if (evt.getBlock().getType().equals(Material.DISPENSER) && ((Dispenser)evt.getBlock().getState()).getPersistentDataContainer().has(compressorKey, PersistentDataType.BYTE)) {
			final Dispenser compressor = (Dispenser)evt.getBlock().getState();
			for (String key : config.getConfigurationSection("recipes").getKeys(false)) {
				if (evt.getItem().isSimilar(config.getItemStack("recipes." + key + ".from"))) {
					if (compressor.getInventory().containsAtLeast(config.getItemStack("recipes." + key + ".from"), config.getItemStack("recipes." + key + ".from").getAmount() - 1)) {
						evt.setItem(config.getItemStack("recipes." + key + ".to"));
						final ItemStack toRemove = config.getItemStack("recipes." + key + ".from");
						Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
							public void run() {
								Inventory compressorInventory = compressor.getInventory();
								compressorInventory.removeItem(toRemove);
							}
						}, 1L);
						break;
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
		for (int i = 0; i < lore.size(); i++) {
			lore.set(i, processColor(lore.get(i), null));
		}
		compressorMeta.setLore(lore);
		compressorMeta.getPersistentDataContainer().set(compressorKey, PersistentDataType.BYTE, (byte)1);
		compressor.setItemMeta(compressorMeta);
		return compressor;
	}
	
	public String processColor(String input, String playerName) {
		return ChatColor.translateAlternateColorCodes('&', input.replaceAll("%prefix%", lang.getString("prefix")).replaceAll("%player%", playerName).replaceAll("%type%", playerName));
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
