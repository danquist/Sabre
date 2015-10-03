package com.gordonfreemanq.sabre.blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.ContainerBlock;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Openable;
import org.bukkit.material.PistonBaseMaterial;
import org.bukkit.material.Sign;

import com.gordonfreemanq.sabre.Lang;
import com.gordonfreemanq.sabre.PlayerManager;
import com.gordonfreemanq.sabre.SabreConfig;
import com.gordonfreemanq.sabre.SabrePlayer;
import com.gordonfreemanq.sabre.SabrePlugin;
import com.gordonfreemanq.sabre.core.ISabreLog;
import com.gordonfreemanq.sabre.groups.SabreGroup;
import com.gordonfreemanq.sabre.groups.SabreMember;
import com.gordonfreemanq.sabre.util.SabreUtil;

@SuppressWarnings("deprecation")
public class BlockListener implements Listener {

	private final PlayerManager pm;
	private final BlockManager bm;
	private final SabreConfig config;
	private final ISabreLog logger;


	public static final List<BlockFace> all_sides = Arrays.asList(
			BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,

			BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);

	public static final List<BlockFace> planar_sides = Arrays.asList(
			BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);


	/**
	 * Creates a  new BlockListener instance
	 * @param pm The player manager
	 * @param gm The group manager
	 * @param bm The block manager
	 * @param logger The logging class
	 */
	public BlockListener(PlayerManager pm, BlockManager bm, SabreConfig config, ISabreLog logger) {
		this.pm = pm;
		this.bm = bm;
		this.config = config;
		this.logger = logger;
	}


	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent e) {
		bm.loadChunk(e.getChunk());
	}


	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onChunkLoad(ChunkUnloadEvent e) {
		bm.unloadChunk(e.getChunk());
	}


	/**
	 * The block place event handler
	 * @param e The event args
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onBlockPlaceEvent(BlockPlaceEvent e) {
		try {
			Reinforcement r = null;
			SabrePlayer p = pm.getPlayerById(e.getPlayer().getUniqueId());

			// Create a new block instance and save it if the item in hand has block data
			SabreBlock b = BlockManager.createBlockFromItem(e.getItemInHand(), e.getBlock().getLocation());

			// Create the reinforcement if we can
			if (p.getBuildState().getMode() == BuildMode.FORTIFY) {

				if (!canPlace(e.getBlock(), p)) {
					p.msg(Lang.blockMismatched);
					e.setCancelled(true);
					return;
				}

				r = this.createReinforcement(p, e.getBlock(), null, true);

				if (r == null) {
					e.setCancelled(true);
				} else {

					// Create a new block instance if it wasn't created already
					if (b == null) {
						b = new SabreBlock(e.getBlock().getLocation());
					}

					// Append the reinforcement to the block
					b.setReinforcement(r);
				}
			}

			// Commit the new block record if it exists
			if (b != null) {
				bm.addBlock(b);
			}

			if (r != null) {
				bm.setReinforcement(b, r);
			}

		} catch (Exception ex) {
			e.getPlayer().sendMessage(SabrePlugin.getPlugin().txt.parse(Lang.exceptionGeneral));
			logger.log(Level.SEVERE, SabreUtil.getExceptionMessage("onBlockPlaceEvent", ex));
			e.setCancelled(true);
			throw ex;
		}
	}


	/**
	 * Low priority even handler to alert that the block is about to be broken
	 * @param e
	 */
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onBlockBreaking(BlockBreakEvent e) {
		try {
			Block b = SabreUtil.getRealBlock(e.getBlock());
			Location l = b.getLocation();
			SabrePlayer p = pm.getPlayerById(e.getPlayer().getUniqueId());

			// Find the record for this block location
			SabreBlock sb = bm.getBlockAt(l);
			if (sb != null) { 
				sb.onBlockBreaking(p, e);
			}

		} catch (Exception ex) {
			e.getPlayer().sendMessage(SabrePlugin.getPlugin().txt.parse(Lang.exceptionGeneral));
			logger.log(Level.SEVERE, SabreUtil.getExceptionMessage("onBlockBreaking", ex));
			throw ex;
		}
	}


	/**
	 * The block break event handler
	 * @param e The event args
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreakEvent(BlockBreakEvent e) {
		try {
			Block b = SabreUtil.getRealBlock(e.getBlock());
			Location l = b.getLocation();
			SabrePlayer p = pm.getPlayerById(e.getPlayer().getUniqueId());

			// Find the record for this block location
			SabreBlock sb = bm.getBlockAt(l);
			if (sb != null) {

				// Default to cancel, we'll manually drop any blocks
				e.setCancelled(true);
				boolean allowBreak = false;
				boolean refund = false;

				Reinforcement r = sb.getReinforcement();
				if (r != null) {
					SabreGroup g = r.getGroup();
					SabreMember m = g.getMember(p);
					if (m != null) {
						if (m.canBuild() && p.getBuildState().getBypass()) {
							allowBreak = true;
						} else if (p.getAdminBypass()) {
							allowBreak = true;
							String groupName = r.getGroup().getName();
							p.msg(Lang.adminYouBypassed, groupName);
						}

						if (allowBreak) {
							refund = true;
						}
					}

					if (!allowBreak) {
						int strength = r.getStrength();

						if (strength > 1) {
							// Decrement the durability
							r.setStrength(strength - 1);
							bm.updateReinforcementStrength(sb);
						} else {
							// Block is worn down, let it break
							allowBreak = true;
						}
					}

				} else {
					allowBreak = true;
				}

				if (allowBreak) {

					// Remove the block record
					bm.removeBlock(sb);
					
					// Perform any logic for when the block breaks
					sb.onBlockBroken(p, e);

					// If it's a special block, override default block drop behavior
					if (sb.isSpecial()) {
						// Create a new item stack for this block type
						ItemStack is = sb.createItemStack(b.getType(), 1);

						if (is != null) {
							// Cancel the break event, set the block to air, and drop the new item, this way
							// we can override the default block that drops
							l.getBlock().setType(Material.AIR);
							l.getWorld().dropItem(l, is);
						}
					}

					if (refund) {
						this.refundItemToPlayer(p.getPlayer(), r.getMaterial(), l);
					}
				}
			}
		} catch (Exception ex) {
			e.getPlayer().sendMessage(SabrePlugin.getPlugin().txt.parse(Lang.exceptionGeneral));
			logger.log(Level.SEVERE, SabreUtil.getExceptionMessage("onBlockBreakEvent", ex));
			throw ex;
		}
	}


	/**
	 * The block interact event handler
	 * @param e The event args
	 */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onBlockInteract(PlayerInteractEvent e) {
		try {			
			// Ignore events with no block 
			if (!e.hasBlock()) {
				return;
			}

			Block b = SabreUtil.getRealBlock(e.getClickedBlock());
			Reinforcement r = null;
			SabrePlayer p = pm.getPlayerById(e.getPlayer().getUniqueId());
			Action a = e.getAction();
			BuildState state = p.getBuildState();
			boolean canAccess = false;

			// Ignore anything except left/right clicks
			if (!a.equals(Action.RIGHT_CLICK_BLOCK) && !a.equals(Action.LEFT_CLICK_BLOCK)) {
				return;
			}

			// Find the record for this block location
			SabreBlock sb = bm.getBlockAt(b.getLocation());
			if (sb != null) {

				canAccess = bm.playerCanAccessBlock(p, sb);

				// Disallow container access for those not in the group
				if (a.equals(Action.RIGHT_CLICK_BLOCK) && config.blockIsLockable(b.getType()) && !canAccess) {

					if (p.getAdminBypass()) {
						String groupName = sb.getReinforcement().getGroup().getName();
						p.msg(Lang.adminYouBypassed, groupName);
					} else {
						if (!state.getInfo()) {
							if (sb.isSpecial()) {
								p.msg(Lang.blockIsLockedSpecial, sb.getTypeName());
							} else {
								p.msg(Lang.blockIsLocked, b.getType());
							}
						}
						
						e.setCancelled(true);
					}
				} 
				if (state.getInfo()) {
					showBlockInfo(p, sb);
				}
			} else if (state.getInfo()) {
				p.msg(Lang.blockNoReinforcement);
			}

			// Do we need to do a block reinforce?
			if (state.getMode().equals(BuildMode.REINFORCE)) {

				// Create a new block instance if we have to
				if (sb == null) {
					sb = new SabreBlock(b.getLocation());
					bm.addBlock(sb);
				}

				Reinforcement existing = sb.getReinforcement();

				// Create the new reinforcement
				r = createReinforcement(p, b, existing, false);
				if (r != null) {
					bm.setReinforcement(sb, r);
				}

				e.setCancelled(true);
			} else if (sb != null && sb.isSpecial()) {
				if (canAccess) {
					sb.onInteract(e, p);
					
					if (p.getPlayer().getItemInHand().getType().equals(Material.STICK)) {
						sb.onStickInteract(e, p);
					}
				}
				
			}

		} catch (Exception ex) {
			e.getPlayer().sendMessage(SabrePlugin.getPlugin().txt.parse(Lang.exceptionGeneral));
			logger.log(Level.SEVERE, SabreUtil.getExceptionMessage("onBlockInteract", ex));
			throw ex;
		}
	}


	/**
	 * Loads data for already loaded chunks
	 */
	public void handleLoadedChunks() {
		for (World w : Bukkit.getWorlds()) {
			Chunk[] chunks = w.getLoadedChunks();
			for (int i = 0; i <  chunks.length; i++ ) {
				bm.loadChunk(chunks[i]);
			}
		}
	}



	/**
	 * Creates a new reinforcement instance
	 * @param p The player
	 * @param b The block
	 * @param existing An existing reinforcement if it exists
	 * @param blockPlace whether this is a block place operation
	 * @return The new instance if it success
	 */
	public Reinforcement createReinforcement(SabrePlayer p, Block b, Reinforcement existing, boolean blockPlace) {

		Location l = b.getLocation();
		BuildState state = p.getBuildState();
		ReinforcementMaterial rm = state.getMaterial();
		SabreGroup g = state.getGroup();
		PlayerInventory inv = p.getPlayer().getInventory();
		int invSize = inv.getSize();

		if (rm == null || g == null) {
			return null;
		}

		if (!inv.contains(rm.material)) {
			state.setMode(BuildMode.OFF);
			p.msg(Lang.blockMaterialDepleted, rm.material.toString());
			p.msg(Lang.blockBuildMode, BuildMode.OFF.toString());
			return null;
		}

		if (config.blockIsNonreinforceable(l.getBlock().getType())) {
			p.msg(Lang.blockNotReinforceable);
			return null;
		}

		// Make sure player has permission to modify
		if (!bm.playerCanModifyBlock(p, b)) {
			p.msg(Lang.noPermission);
			return null;
		}

		boolean found = false;
		int amount = 1;
		ItemStack slotItem = null;
		int slot = 0;


		for (slot = 0; slot < invSize; slot++) {
			slotItem = inv.getItem(slot);
			if (slotItem == null) {
				continue;
			}
			if (slotItem.getType().equals(rm.material)) {

				// Prevent named and lore items from being used
				if (slotItem.hasItemMeta() && (slotItem.getItemMeta().hasLore() || slotItem.getItemMeta().hasDisplayName())) {
					continue;
				}

				found = true;
				break;
			}
		}

		if (!found) {
			state.setMode(BuildMode.OFF);
			p.msg(Lang.blockMaterialDepleted, rm.material.toString());
			p.msg(Lang.blockBuildMode, BuildMode.OFF.toString());
			return null;
		}

		// Create the new instance so we can test
		Reinforcement r = new Reinforcement(l, g.getID(), rm.material, rm.strength, (new Date()).getTime());
		r.setPublic(state.getPublic());
		r.setInsecure(state.getInsecure());

		// Is it already the same as the existing?
		if (existing != null) {
			if (r.isLike(existing)) {
				p.msg(Lang.blockNoChange);
				return null;
			} else {

				// Tell the player the block was updated
				String groupStr = p.getBuildState().getBuildGroupString();
				p.msg(Lang.blockChanged, groupStr, r.getMaterial().toString());
				return r;
			}
		}

		// If you're pulling the reinforcement from the same slot, you need to manually take away 2
		if (inv.getItemInHand().equals(slotItem) && blockPlace) {
			amount = 2;
		}

		// Update the inventory
		slotItem.setAmount(slotItem.getAmount() - amount);
		inv.setItem(slot, slotItem);
		p.getPlayer().updateInventory();

		return r;
	}


	/**
	 * Shows the reinforcement for a block
	 * @param p The player that is checking
	 * @param b The block to check
	 */
	private void showBlockInfo(SabrePlayer p, SabreBlock b) {
		Reinforcement r = b.getReinforcement();
		String typeName = b.getTypeName();
		boolean special = false;

		// Tell the user if this is a special block
		if (!b.getTypeName().equalsIgnoreCase("block")) {
			special = true;
			//p.msg(Lang.blockShowType, b.getTypeName());
		}

		if (r == null) {
			p.msg(Lang.blockNoReinforcement);
			return;
		}

		String verb = "Fortified";
		SabreGroup g = r.getGroup();
		SabreMember m = g.getMember(p);

		if (config.blockIsLockable(b.getLocation().getBlock().getType())) {
			verb = "Locked";
		}

		if (special) {
			verb = verb.toLowerCase();
		}

		String groupName = g.getName();
		if (r.getPublic()) {
			groupName += "-PUBLIC";
		} else if (r.getInsecure()) {
			groupName += "-INSECURE";
		}

		if (m != null || r.getPublic()) {
			// The player is on the group or it is public
			if (special) {
				p.msg(Lang.blockShowInfoAccessSpecial, typeName, verb, groupName, r.getMaterial().toString(), r.getHeathPercent().toString());
			} else {
				p.msg(Lang.blockShowInfoAccess, verb, groupName, r.getMaterial().toString(), r.getHeathPercent().toString());
			}
		} else {
			// The player is not part of the group
			if (special) {
				p.msg(Lang.blockShowInfoSpecial, typeName, verb, r.getMaterial().toString(), r.getHeathPercent().toString());
			} else {
				p.msg(Lang.blockShowInfo, verb, r.getMaterial().toString(), r.getHeathPercent().toString());
			}
		}
	}


	/**
	 * Checks if a player can place a block at a location by checking to see
	 * if there are any nearby container chests that will interfear
	 * @param b
	 * @param player
	 * @return
	 */
	private boolean canPlace(Block b, SabrePlayer p) {
		Material block_mat = b.getType();

		if (block_mat == Material.HOPPER || block_mat == Material.DROPPER){
			for (BlockFace direction : all_sides) {
				Block adjacent = b.getRelative(direction);
				if (!(adjacent.getState() instanceof ContainerBlock)) {
					continue;
				}

				SabreBlock sb = bm.getBlockAt(adjacent.getLocation());
				if (sb != null) {
					Reinforcement r = sb.getReinforcement();
					SabreGroup g = r.getGroup();
					if (!g.isMember(p)) {
						return false;
					}
				}
			}
		}
		else if (block_mat == Material.CHEST || block_mat == Material.TRAPPED_CHEST){
			for (BlockFace direction : planar_sides) {
				Block adjacent = b.getRelative(direction);
				if (!(adjacent.getState() instanceof ContainerBlock)) {
					continue;
				}

				SabreBlock sb = bm.getBlockAt(adjacent.getLocation());
				if (sb != null) {
					Reinforcement r = sb.getReinforcement();
					SabreGroup g = r.getGroup();
					if (!g.isMember(p)) {
						return false;
					}
				}
			}
		}
		
		/*
		else if (block_mat == Material.PISTON_BASE || block_mat == Material.PISTON_STICKY_BASE){
			for (BlockFace direction : all_sides) {
				Block adjacent = b.getRelative(direction);
				
				Block realBlock = SabreUtil.getRealBlock(adjacent);

				SabreBlock sb = bm.getBlockAt(realBlock.getLocation());
				if (sb != null) {
					Reinforcement r = sb.getReinforcement();
					SabreGroup g = r.getGroup();
					if (!g.equals(p.getBuildState().getGroup())) {
						return false;
					}
				}
			}
		} */

		else if (block_mat != Material.AIR) {

			SabreBlock sb = bm.getBlockAt(b.getLocation());
			if (sb != null) {
				Reinforcement r = sb.getReinforcement();
				SabreGroup g = r.getGroup();
				if (!g.isMember(p)) {
					return false;
				}
			}
		}

		return true;
	}


	/**
	 * Prevents water/lava from washing away reinforced items
	 * @param e The event args
	 */
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onBlockFromToEvent(BlockFromToEvent e) {
		Block toBlock = e.getToBlock();
		Material m = toBlock.getType();
		if (!SabreUtil.isRail(m) && !SabreUtil.isPlant(m) && !SabreUtil.isWebString(m)) {
			return;
		}

		// Don't let things get washed away!
		SabreBlock b = bm.getBlockAt(toBlock.getLocation());
		if (b != null) {
			e.setCancelled(true);
		}
	}


	/**
	 * Prevents reinforcements from burning
	 * @param e The event args
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void blockBurn(BlockBurnEvent e) {

		// Don't let reinforced blocks burn!
		SabreBlock b = bm.getBlockAt(e.getBlock().getLocation());
		if (b != null) {
			e.setCancelled(true);
		}
	}


	/**
	 * Prevents special sabre blocks from being moved by pistons
	 * @param e The event args
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void pistonExtend(BlockPistonExtendEvent e) {
		Block piston = e.getBlock();
		BlockState state = piston.getState();
		MaterialData data = state.getData();
		BlockFace direction = null;

		if (data instanceof PistonBaseMaterial) {
			direction = ((PistonBaseMaterial) data).getFacing();
		}

		// if no direction was found, no point in going on
		if (direction == null)
			return;

		// Check the affected blocks
		for (int i = 1; i < e.getLength() + 2; i++) {
			Block block = piston.getRelative(direction, i);

			if (block.getType() == Material.AIR){
				break;
			}

			SabreBlock b = bm.getBlockAt(block.getLocation());
			if (b != null) {
				e.setCancelled(true);
				break;
			}
		}
	}


	/**
	 * Prevents special sabre blocks from being moved by pistons
	 * @param e The event args
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void pistonRetract(BlockPistonRetractEvent e) {
		Block piston = e.getBlock();
		BlockState state = piston.getState();
		MaterialData data = state.getData();
		BlockFace direction = null;

		// Check the block it pushed directly
		if (data instanceof PistonBaseMaterial) {
			direction = ((PistonBaseMaterial) data).getFacing();
		}

		if (direction == null)
			return;

		// The block that the piston moved
		Block moved = piston.getRelative(direction, 2);

		SabreBlock b = bm.getBlockAt(moved.getLocation());
		if (b != null) {
			e.setCancelled(true);
		}
	}


	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void redstonePower(BlockRedstoneEvent e) {
		// This currently only protects against reinforced openable objects,
		//  like doors, from being opened by unauthorizied players.

		// NewCurrent <= 0 means the redstone wire is turning off, so the
		//  container is closing. Closing is good so just return. This also
		//  shaves off some time when dealing with sand generators.
		// OldCurrent > 0 means that the wire was already on, thus the
		//  container was already open by an authorized player. Now it's
		//  either staying open or closing. Just return.
		if (e.getNewCurrent() <= 0 || e.getOldCurrent() > 0) {
			return;
		}
		Block block = e.getBlock();
		MaterialData blockData = block.getState().getData();
		if (!(blockData instanceof Openable)) {
			return;
		}
		Openable openable = (Openable)blockData;
		if (openable.isOpen()) {
			return;
		}

		// Only care about blocks with reinforcement
		SabreBlock sb = bm.getBlockAt(block.getLocation());
		if (sb == null) {
			return;
		}
		
		Reinforcement r = sb.getReinforcement();
		if  (r == null) {
			return;
		}

		if (r.getPublic()) {
			return;
		}

		final int distance = 3;
		if (!isAuthorizedPlayerNear(r, distance)) {
			e.setNewCurrent(e.getOldCurrent());
		}
	}


	/**
	 * Refunds an item to a palyer's inventory
	 * @param p The player
	 * @param m The material
	 * @param l The location to drop, if inventory is full
	 */
	public void refundItemToPlayer(Player p, Material m, Location l) {
		PlayerInventory inv = p.getInventory();

		// Bypass mode, allow the break and credit the item
		ItemStack is = new ItemStack(m, 1);

		for(ItemStack leftover : inv.addItem(
				is)
				.values()) {
			l.getWorld().dropItem(l, leftover);
		}
	}


	/**
	 * Checks if a Redstone player is trying to power a block.
	 * @param The Reinforcement in question.
	 * @param The distance the player is from the block.
	 * @return Returns true if the player is on the group and has permission.
	 * @return Returns false if the player is not on the group or doesn't have permission.
	 */
	public boolean isAuthorizedPlayerNear(Reinforcement r, double distance) {

		Location reinLocation = r.getLocation();
		double min_x = reinLocation.getX() - distance;
		double min_z = reinLocation.getZ() - distance;
		double max_x = reinLocation.getX() + distance;
		double max_z = reinLocation.getZ() + distance;

		boolean result = false;

		try {
			for (SabrePlayer p : pm.getOnlinePlayers()) {
				if (p.getPlayer().isDead()) {
					continue;
				}

				Location playerLocation = p.getPlayer().getLocation();
				double player_x = playerLocation.getX();
				double player_z = playerLocation.getZ();

				// Simple bounding box check to quickly rule out Players
				//  before doing the more expensive playerLocation.distance
				if (player_x < min_x || player_x > max_x ||
						player_z < min_z || player_z > max_z) {
					continue;
				}

				if (!bm.playerCanAccessBlock(p, r.getLocation().getBlock()) && !p.getAdminBypass()) {
					continue;
				}

				double distanceSquared = playerLocation.distance(reinLocation);
				if (distanceSquared <= distance) {
					result = true;
					break;
				}
			}
		} catch (ConcurrentModificationException e) {
			logger.log(Level.SEVERE, "ConcurrentModificationException at redstonePower() in BlockListener");
		}
		return result;
	}


	@EventHandler
	public void signDetachCheck(BlockPhysicsEvent e) {
		Block b = e.getBlock();
		if (b.getType() == Material.WALL_SIGN || b.getType() == Material.SIGN_POST) {
			Sign s = (Sign) b.getState().getData();
			Block attachedBlock = b.getRelative(s.getAttachedFace());
			if (attachedBlock.getType() == Material.AIR) {

				// sign has been popped off!

				Location l = b.getLocation();

				// Find the record for this block location
				SabreBlock sb = bm.getBlockAt(l);

				if (sb != null) {
					bm.removeBlock(sb);

					// Create a new item stack for this block type
					ItemStack is = sb.createItemStack(b.getType(), 1);

					// Cancel the break event, set the block to air, and drop the new item, this way
					// we can override the default block that drops
					l.getBlock().setType(Material.AIR);
					l.getWorld().dropItem(l, is);
					e.setCancelled(true);
				}
			}
		}
	}


	/**
	 * Handles the grow event
	 * @param e The event args
	 */
	@EventHandler(ignoreCancelled = true)
	public void onStructureGrow(StructureGrowEvent e) {
		for (BlockState state : e.getBlocks()) {
			// Find the record for this block location
			SabreBlock sb = bm.getBlockAt(state.getLocation());
			if (sb != null) {
	    		if (sb.getReinforcement() != null) {
	        		e.setCancelled(true);
	    		} else {
	    			bm.removeBlock(sb);
	    		}
			}
		}
	}
	
	
	/**
	 * Removes block records if they turn into a creature
	 * @param e
	 */
	@EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        EntityType type = e.getEntityType();
        if (type != EntityType.IRON_GOLEM && type != EntityType.SNOWMAN) return;

        for (Block b : getGolemBlocks(type, e.getLocation().getBlock())) {
        	SabreBlock sb = bm.getBlockAt(b.getLocation());
        	if (sb != null) {
        		if (sb.getReinforcement() != null) {
            		e.setCancelled(true);
        		} else {
        			bm.removeBlock(sb);
        		}
        	}
        }
    }
	
	
	/**
	 * Gets the blocks for a golen
	 * @param type The entity type
	 * @param base The block base
	 * @return
	 */
    private List<Block> getGolemBlocks(EntityType type, Block base) {
        ArrayList<Block> blocks = new ArrayList<Block>();
        blocks.add(base);
        base = base.getRelative(BlockFace.UP);
        blocks.add(base);
        if (type == EntityType.IRON_GOLEM) {
            for (BlockFace face : new BlockFace[]{ BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST }) {
                Block arm = base.getRelative(face);
                if (arm.getType() == Material.IRON_BLOCK)
                    blocks.add(arm);
            }
        }
        base = base.getRelative(BlockFace.UP);
        blocks.add(base);
        
        return blocks;
    }
    
    
    /**
     * Handles entities breaking doors
     * @param e The event args
     */
    @EventHandler(ignoreCancelled = true)
    public void breakDoor(EntityBreakDoorEvent e) {
    	SabreBlock sb = bm.getBlockAt(e.getBlock().getLocation());
    	if (sb != null) {
    		if (sb.getReinforcement() != null) {
        		e.setCancelled(true);
    		} else {
    			bm.removeBlock(sb);
    		}
    	}
    }

    
    /**
     * Handles entity changing blocks
     * @param e The event args
     */
    @EventHandler(ignoreCancelled = true)
    public void changeBlock(EntityChangeBlockEvent e) {
    	SabreBlock sb = bm.getBlockAt(e.getBlock().getLocation());
    	if (sb != null) {
    		if (sb.getReinforcement() != null) {
        		e.setCancelled(true);
    		} else {
    			bm.removeBlock(sb);
    		}
    	}
    }
    
    
    /**
     * Prevents sabre blocks from taking exploding damage
     * @param eee
     */
    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent eee) {
        Iterator<Block> iterator = eee.blockList().iterator();
        while (iterator.hasNext()) {
            Block b = iterator.next();
        	SabreBlock sb = bm.getBlockAt(b.getLocation());
        	if (sb != null) {
        		if (sb.getReinforcement() != null) {
            		iterator.remove();
        		} else {
        			bm.removeBlock(sb);
        		}
        	}
        }
    }
    
    
    /**
     * Handles inventory movement
     * Prevent hopper minecarts from extracting from reinforced containers
     * or filling up reinforced containers.
     * Prevent misowned hoppers from stealing from reinforced containers.
     * @param e The event args
     */
	@EventHandler(ignoreCancelled = true)
	public void onInventoryMoveItemEvent(InventoryMoveItemEvent e) {
		// Fail safe
		e.setCancelled(true);
		
		// Source
		final Inventory src = e.getSource();
		final Reinforcement srcRein = getReinforcement(src);
		
		// Destination
		final Inventory dest = e.getDestination();
		final Reinforcement destRein = getReinforcement(dest);
		if (srcRein == null) {
			// Public can transfer into any, allow
			// (Public -> Public, Public -> Group X, Public -> Personal Y)
			e.setCancelled(false);
			return;
		}
		
		if (srcRein.getInsecure()) {
			// Insecure source reinforcement allows transfer as if it's
			// a public reinforcement, allow
			e.setCancelled(false);
			return;
		}
		
		// Assertion: srcRein != null
		if (destRein == null) {
			// Non-public cannot transfer into a public, deny
			return;
		}
		
		// Assertion: srcRein != null && destRein != null
		final SabreGroup srcOwner = srcRein.getGroup();
		final SabreGroup destOwner = destRein.getGroup();
		// Check for null group failure
		if (srcOwner == null || destOwner == null) {
			// Unable to determine reinforcement owner match, deny
			return;
		}
		if (srcOwner == destOwner) {
			// Reinforcement owners match, allow
			// (Group X -> Group X, Personal Y -> Personal Y)
			e.setCancelled(false);
			return;
		}
		// Reinforcement owners don't match, deny
	}
	
	
	/**
	 * Gets a reinforcement from an inventory
	 * @param inv The inventory
	 * @return The reinforcement
	 */
	public Reinforcement getReinforcement(Inventory inv) {
		// Returns reinforcement of the inventory's holder or null if none
		// exists
		final InventoryHolder holder = inv.getHolder();
		Location loc;
		if (holder instanceof DoubleChest) {
			loc = SabreUtil.getRealBlock(((DoubleChest) holder).getLocation().getBlock()).getLocation();
		} else if (holder instanceof BlockState) {
			loc = SabreUtil.getRealBlock(((BlockState) holder).getLocation().getBlock()).getLocation();
		} else {
			// Entity or Vehicle inventories
			return null;
		}
		
		SabreBlock sb = bm.getBlockAt(loc);
		if (sb != null) {
			return sb.getReinforcement();
		}
		return null;
	}
}