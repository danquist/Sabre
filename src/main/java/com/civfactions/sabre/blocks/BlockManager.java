package com.civfactions.sabre.blocks;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.logging.Level;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.civfactions.sabre.IPlayer;
import com.civfactions.sabre.SabrePlugin;
import com.civfactions.sabre.customitems.SecureSign;
import com.civfactions.sabre.data.IDataAccess;
import com.civfactions.sabre.factory.BaseFactory;
import com.civfactions.sabre.factory.FactoryCollection;
import com.civfactions.sabre.groups.SabreGroup;
import com.civfactions.sabre.groups.SabreMember;
import com.civfactions.sabre.snitch.Snitch;
import com.civfactions.sabre.snitch.SnitchCollection;
import com.google.common.base.CharMatcher;

/**
 * Class for managing the block records
 * @author GFQ
 */
public class BlockManager {

	private final SabrePlugin plugin;
	private final IDataAccess db;
	
	private final BlockCollection allBlocks;
	private final SignCollection secureSigns;
	private final SnitchCollection snitches;
	private final FactoryCollection factories;


	/**
	 * Creates a  new BlockManager instance
	 */
	public BlockManager(SabrePlugin plugin, IDataAccess db) {
		this.plugin = plugin;
		this.db = db;
		
		this.allBlocks = new BlockCollection();
		
		// Holds the secure signs
		this.secureSigns = new SignCollection();
		this.snitches = new SnitchCollection();
		this.factories = new FactoryCollection();
		allBlocks.addSub(secureSigns);
		allBlocks.addSub(snitches);
		allBlocks.addSub(factories);
	}
	
	
	public SignCollection getSigns() {
		return secureSigns;
	}
	
	
	public SnitchCollection getSnitches() {
		return snitches;
	}
	
	
	public FactoryCollection getFactories() {
		return factories;
	}
	
	
	/**
	 * Loads all the block data for a chunk
	 */
	public void loadChunk(Chunk c) {
		
		HashMap<Location, SabreBlock> all = new HashMap<Location, SabreBlock>();
		HashMap<Location, SabreBlock> signs = new HashMap<Location, SabreBlock>();
		HashMap<Location, SabreBlock> snitches = new HashMap<Location, SabreBlock>();
		HashMap<Location, SabreBlock> factories = new HashMap<Location, SabreBlock>();
		Location l;
		
		for (SabreBlock b : db.blockGetChunkRecords(c)) {
			l = b.getLocation();
			all.put(l, b);
			
			if (b.isSpecial()) { 
				if (b instanceof SecureSign) {
					signs.put(l, b);
				} else if (b instanceof Snitch) {
					snitches.put(l,  b);
				} else if (b instanceof BaseFactory) {
					factories.put(l, b);
				}
			}
		}
		
		allBlocks.putChunk(c, all);
		secureSigns.putChunk(c, signs);
		this.snitches.putChunk(c, snitches);
		this.factories.putChunk(c, factories);
	}
	
	
	/**
	 * Unloads all the block data for a chunk
	 */
	public void unloadChunk(Chunk c) {
		
		allBlocks.unloadChunk(c);
	}
	
	
	/**
	 * Loads all the running factories from the DB
	 */
	public void loadRunningFactories() {
		for(SabreBlock b : db.blockGetRunningFactories()) {
			if (b instanceof BaseFactory) {
				BaseFactory bf = (BaseFactory)b;
				if (bf.runUnloaded()) {
					plugin.getFactoryWorker().addRunning(bf);
				}
			}
		}
	}


	/**
	 * Adds a new block record
	 * @param b The block to add
	 */
	public void addBlock(SabreBlock b) {
		allBlocks.add(b);
		db.blockInsert(b);
	}


	/**
	 * Gets a block record at a location
	 * @param l The location
	 * @return The block record, if it exists
	 */
	public SabreBlock getBlockAt(Location l) {
		return allBlocks.get(l);
	}
	
	
	/**
	 * Removes a block record
	 * @param b The block record to remove
	 */
	public void removeBlock(SabreBlock b) {
		allBlocks.remove(b);
		db.blockRemove(b);
	}
	
	
	/**
	 * Creates a new block instance from an item
	 * @return The new block instance
	 */
	public SabreBlock createBlockFromItem(ItemStack is, Location l) {
		
		SabreBlock b = null;
		String displayName = "";
		String typeName = "block";
		
		if (is.hasItemMeta()) {
			ItemMeta im = is.getItemMeta();
			
			// Check display name
			if (im.hasDisplayName()) {
				displayName = im.getDisplayName();
			}
			
			// Check lore
			if (im.hasLore()) {
				// The type name for special blocks is always the first line
				typeName = is.getItemMeta().getLore().get(0);
				typeName = ChatColor.stripColor(typeName);
				
				// Remove any color formatting
				if (!typeName.equals(CharMatcher.ASCII.retainFrom(typeName))) {
					typeName = typeName.substring(2);
				}
			}
			
			b = blockFactory(l, typeName);
			if (b != null) {
				b.setDisplayName(displayName);
			}
		}
		
		return b;
	}
	
	
	/**
	 * Block factory
	 * @param l The block location
	 * @param name The type name
	 * @return The new instance
	 */
	public SabreBlock blockFactory(Location l, String typeName) {
		
		
		if (typeName.equalsIgnoreCase("block")) {
			return new SabreBlock(l, typeName);
		}
		
		Class<? extends SabreBlock> blockClass = plugin.getCustomItems().getItemClass(typeName);
		
		// Check if the class exists
		if (blockClass == null) {
			return null;  // Not found
		}
		
		try {

			Class<?>[] types = { Location.class, String.class };
			Constructor<? extends SabreBlock> c = blockClass.getConstructor(types);
			
			Object[] parameters = { l, typeName };
			SabreBlock blockInstance = c.newInstance(parameters);
			return blockInstance;
			
		} catch (Exception ex) {
			SabrePlugin.log(Level.SEVERE, "Failed to create block instance of type %s.", typeName);
			return null;
		}
	}
	
	
	/**
	 * Sets the reinforcement record for a block
	 * @param b The block to update
	 * @param r The reinforcement to set
	 */
	public void setReinforcement(SabreBlock b, Reinforcement r) {
		b.setReinforcement(r);
		db.blockSetReinforcement(b);
	}
	
	
	/**
	 * Updates the reinforcement strength
	 * @param b The block to update
	 */
	public void updateReinforcementStrength(SabreBlock b) {
		db.blockUpdateReinforcementStrength(b);
	}
	
	
	/**
	 * Updates the settings for a block
	 * @param b The block to update
	 */
	public void updateSettings(SabreBlock b) {
		db.blockSetSettings(b);
	}
	
	
	/**
	 * Checks if a player can access a specific block
	 * @param p The player to check
	 * @param b The block to check
	 * @return true if access is permitted
	 */
	public boolean playerCanAccessBlock(IPlayer p, SabreBlock b) {
		if (b == null) {
			return true;
		}
		
		Reinforcement r = b.getReinforcement();
		if (r == null) {
			return true;
		}
		
		if (r.getPublic()) {
			return true;
		}
		
		SabreGroup g = r.getGroup();
		return g.isMember(p);
	}
	
	
	/**
	 * Checks if a player can access a specific block
	 * @param p The player to check
	 * @param b The block to check
	 * @return true if access is permitted
	 */
	public boolean playerCanAccessBlock(IPlayer p, Block b) {
		return this.playerCanAccessBlock(p, getBlockAt(b.getLocation()));
	}
	
	
	/**
	 * Checks if a player can break a specific block
	 * @param p The player to check
	 * @param b The block to check
	 * @return true if access is permitted
	 */
	public boolean playerCanBreakBlock(IPlayer p, SabreBlock b) {
		if (b == null) {
			return true;
		}
		
		Reinforcement r = b.getReinforcement();
		if (r == null) {
			return true;
		}
		
		SabreGroup g = r.getGroup();
		SabreMember m = g.getMember(p);
		if (m != null && m.canBuild()) {
			return true;
		}
		
		return false;
	}
	
	/**
	 * Checks if a player can break a specific block
	 * @param p The player to check
	 * @param b The block to check
	 * @return true if access is permitted
	 */
	public boolean playerCanModifyBlock(IPlayer p, Block b) {
		return this.playerCanBreakBlock(p, getBlockAt(b.getLocation()));
	}
	
	
	/**
	 * Loads all the factory blocks
	 */
	public void loadAllFactories() {
		
	}
}
