package com.gordonfreemanq.sabre.blocks;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.gordonfreemanq.sabre.SabrePlugin;

/**
 * Holds the custom item types
 * @author GFQ
 */
public class CustomItems {
	
	private File folder = null;
	private final SabrePlugin plugin;
	private HashMap<String, SabreItemStack> customItems;
	
	private static CustomItems instance;
	
	public static CustomItems getInstance() {
		return instance;
	}
	
	
	/**
	 * Creates a new CustomItems instance
	 */
	public CustomItems() {
		this.plugin = SabrePlugin.getPlugin();
		
		instance = this;
	}

	
	/**
	 * Reloads the recipe configuration
	 */
	public void reload() {
	    if (folder == null) {
	    	folder = new File(plugin.getDataFolder(), "custom_items");
	    }
	    
		this.customItems = new HashMap<String, SabreItemStack>();
		
		

	    File[] files = folder.listFiles();
	    
	    for (int i = 0; i < files.length; i++) {
	    	File f = files[i];
	    	
	    	// Ignore non-yml files
	    	if (!f.getName().endsWith(".yml")) {
	    		continue;
	    	}
	    	
	    	// Load the custom items
	    	try {
		    	FileConfiguration config = YamlConfiguration.loadConfiguration(f);
		    	String itemName = config.getString("name");
		    	String materialName = config.getString("material", "");
				Material material = Material.getMaterial(materialName);
				if (material == null)
				{
					plugin.getLogger().severe(f.getName() + " has invalid material " + materialName);
					continue;
				}
		    	
		    	int amount = config.getInt("amount");
		    	
		    	List<String> loreStrings = config.getStringList("lore");
		    	List<String> lore = new ArrayList<String>();
		    	lore.add(plugin.txt.parse("<l>%s", itemName));
		    	
		    	for (String s : loreStrings) {
		    		lore.add(plugin.txt.parse(s));
		    	}
		    	
		    	SabreItemStack item = new SabreItemStack(material, itemName, amount, lore);
		    	
		    	String className = config.getString("class");
		    	if (className != null) {
			    	Class<? extends SabreBlock> blockClass = Class.forName(className).asSubclass(SabreBlock.class);
			    	if (blockClass != null) {
			    		item.setBlockClass(blockClass);
			    	}
		    	}
		    	
	    		// Add to the collection
		    	customItems.put(itemName, item);
			    
	    	} catch (Exception ex) {
	    		plugin.log(Level.SEVERE, "Failed to read factory config file %s", f.getName());
	    	}
	    }
	}
	
	
	/**
	 * Gets an item stack by name
	 * @param name The item name
	 * @return The item stack, if it exists
	 */
	public SabreItemStack getByName(String name) {
		return customItems.get(name).clone();
	}
	
	
	/**
	 * Gets the block class for an item
	 * @param name The item name
	 * @return The block class if it exists
	 */
	public Class<? extends SabreBlock> getItemClass(String name) {
		
		SabreItemStack item = customItems.get(name);
		if (item != null) {
			return item.getBlockClass();
		}
		
		return null;
	}
}