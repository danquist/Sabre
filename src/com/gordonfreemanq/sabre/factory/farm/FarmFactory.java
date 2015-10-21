package com.gordonfreemanq.sabre.factory.farm;

import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.gordonfreemanq.sabre.SabrePlayer;
import com.gordonfreemanq.sabre.SabrePlugin;
import com.gordonfreemanq.sabre.factory.BaseFactory;
import com.gordonfreemanq.sabre.factory.FactoryWorker;
import com.gordonfreemanq.sabre.factory.recipe.FarmRecipe;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * Class that provides core functionality for all the farm factories
 * @author GFQ
 *
 */
public class FarmFactory extends BaseFactory {
	
	// How many factory ticks to wait before producing
	private final int farmProductionTicks;
	
	// How far away it has to be away from another running farm
	private final int farmProximity;
	
	// How many minutes between each survey
	private final int surveyPeriodMin;
	
	// The current production tick counter for when the farm is running
	private int farmTickCounter;
	
	// The crop coverage from 0 to 1.0
	private double layoutFactor;
	
	// Efficiency factory based on proximity to other farms from 0 to 1.0
	private double proximityFactor;
	
	// Biome specific efficiency factor
	private double biomeFactor;
	
	// The amount of farmed goods
	private HashMap<CropType, Integer> farmedCrops;
	
	// The farm surveyor
	private FarmSurveyor surveyor;
	
	private Date lastSurvey;
	
	// The most crops the factory can hold at a time
	private int storageSize;

	/**
	 * Creates a new FarmFactory instance
	 * @param location The factory location
	 * @param typeName The factory name
	 */
	public FarmFactory(Location location, String typeName) {
		super(location, typeName);
		this.farmProductionTicks = SabrePlugin.getPlugin().getSabreConfig().getFarmProductionTicks();
		this.farmProximity = SabrePlugin.getPlugin().getSabreConfig().getFarmProductionTicks();
		this.surveyPeriodMin = SabrePlugin.getPlugin().getSabreConfig().getFarmSurveyPeriod();
		this.farmTickCounter = 0;
		this.layoutFactor = 1.0;
		this.proximityFactor = 1.0;
		this.biomeFactor = 1.0;
		this.farmedCrops = new HashMap<CropType, Integer>();
		this.lastSurvey = new Date(0);
		this.storageSize = 64;
		readCustomConfig();
	}
	
	
	/**
	 * Reads the custom farm config
	 */
	private void readCustomConfig() {
		ConfigurationSection farmConfig = this.properties.getCustomConfig();
		if (farmConfig != null) {
			this.storageSize = farmConfig.getInt("storage_size", storageSize);
		}
	}
	
	
	/**
	 * Handles hitting the block with a stick
	 * @param p The player interacting
	 */
	@Override
	public void onStickInteract(PlayerInteractEvent e, SabrePlayer sp) {
		
		Action a = e.getAction();
		if (a == Action.RIGHT_CLICK_BLOCK) {
			if (running) {
				if (recipe instanceof FarmRecipe) {
					FarmRecipe fr = (FarmRecipe)recipe;
					sp.msg("<a>Status: <n>Farming %s, %d ready for harvest", fr.getCrop().toString(), 
							this.farmedCrops.get(((FarmRecipe) recipe).getCrop()));

				} else {
					sp.msg("<a>Status: <n>%d%%", this.getPercentComplete());
				}
			} else {
				cycleRecipe(sp);
				checkSurvey(true);
			}
		} else {
			if (!running && farmIsFull() && (recipe instanceof FarmRecipe)) {
				sp.msg("<i>The farm is already full.");
				return;
			}
			
			cyclePower(sp);
		}
		
		// Create a controller
		if (this.canPlayerModify(sp)) {
			createController(sp);
		}
	}
	
	protected void onPowerOn() {
		checkSurvey(false);
		saveSettings();
	}
	
	
	/**
	 * Creates the factory controller
	 * @param sp The player
	 */
	@Override
	protected void createController(SabrePlayer sp) {
		ItemStack is = (new FarmController(this)).toItemStack();
		sp.getPlayer().getInventory().setItemInHand(is);
	}
	
	
	/**
	 * Update method for running the factory
	 */
	@Override
	public void update() {
		
		if (!running) {
			return;
		}
		
		checkSurvey(false);
		if (recipe instanceof FarmRecipe) {
			if (farmTickCounter++ >= farmProductionTicks) {
				farmTickCounter = 0;
				runFarmRecipe();
				
				if (farmIsFull()) {
					powerOff();
				}
			}
		} else {
			super.update();
		}
	}
	
	protected void runFarmRecipe() {
		if (!running) {
			return;
		}
		
		if (!(recipe instanceof FarmRecipe)) {
			return;
		}
		
		FarmRecipe fr = (FarmRecipe)recipe;
		
		// Just a quick sanity check on these numbers
		this.layoutFactor = Math.min(layoutFactor, 1.0);
		this.proximityFactor = Math.min(proximityFactor, 1.0);
		this.biomeFactor = Math.min(biomeFactor, 1.0);
		
		CropType cropType = fr.getCrop();
		int alreadyFarmed = farmedCrops.get(cropType);
		int realOutput = (int)(fr.getProductionRate() * layoutFactor * proximityFactor * biomeFactor);
		int total = alreadyFarmed + realOutput;
		
		// Limit the output to the storage size of the factory
		total = Math.min(this.storageSize, total);
		farmedCrops.put(cropType, total);
		
		// Power the factory off if it's full
		if (total == storageSize) {
			powerOff();
		}
		
		saveSettings();
	}
	
	
	/**
	 * Checks if the farm is full
	 * @return true if it is full
	 */
	protected boolean farmIsFull() {
		
		int totalFarmed = 0;
		for(Entry<CropType, Integer> e : this.farmedCrops.entrySet()) {
			totalFarmed += e.getValue();
		}
		
		if (totalFarmed >= this.storageSize) {
			return true;
		}
		
		return false;
	}
	
	
	/**
	 * Calculates the coverage and proximity factor values
	 */
	public void checkSurvey(boolean force) {
		Date now = new Date();
		long timeDiff = now.getTime() - lastSurvey.getTime();
		long diffMin = TimeUnit.MINUTES.convert(timeDiff, TimeUnit.MILLISECONDS);
		
		if (force || diffMin >= this.surveyPeriodMin) {
			calculateLayoutFactor();
			calculateBiomeFactor();
			calculateProximityFactor();
			lastSurvey = now;
			saveSettings();
		}
	}
	
	
	/**
	 * Calculates the farm layout factor
	 */
	private void calculateLayoutFactor() {
		if (surveyor == null) {
			surveyor = ((FarmRecipe)recipe).getSurveyor();
		}
		
		this.layoutFactor = surveyor.surveyFarm(this.location);
	}
	
	
	/**
	 * Calculates the farm proximity factor
	 */
	private void calculateProximityFactor() {
		double factor = 1.0;
		
		for (BaseFactory f : FactoryWorker.getInstance().getRunningFactories()) {
			if (!(f instanceof FarmFactory)) {
				continue;
			}
			
			if (f.getLocation().equals(this.location)) {
				continue;
			}
			
			double dist = f.getLocation().distance(this.location);
			
			// The proximity factor will drop proportionally to each farm that is within
			// the exclusion zone
			if (dist < farmProximity) {
				factor = Math.max(0, factor - ((farmProximity - dist) / farmProximity));
			}
			
			// No need to go below zero
			if (factor == 0) {
				break;
			}
		}
		
		this.proximityFactor = factor;
	}
	
	
	/**
	 * Calculates the biome factor
	 */
	private void calculateBiomeFactor() {
		this.biomeFactor = 1.0;
		// TODO
	}
	
	
	/**
	 * Gets the farmed crops
	 * @return The farmed crops
	 */
	public HashMap<CropType, Integer> getFarmedCrops() {
		return this.farmedCrops;
	}
	
	
	/**
	 * Clears out the farmed crops
	 */
	public void clearFarmedCrops() {
		farmedCrops.clear();
	}
	
	
    /**
     * Gets the layout factpr percent
     * @return The layout factor percent
     */
    public Long getLayoutFactorPercent() {
    	return Math.round(layoutFactor * 100.0);
    }
    
    
    /**
     * Gets the proximity factor percent
     * @return The layout percent
     */
    public Long getProximityFactorPercent() {
    	return Math.round(proximityFactor * 100.0);
    }
    
    
    /**
     * Gets the biome factor percent
     * @return The biome percent
     */
    public Long getBiomeFactorPercent() {
    	return Math.round(biomeFactor * 100.0);
    }
    
    
    /**
     * Gets the real output rate
     * @return The real output rate
     */
    public int getRealOutput() {
    	if (!(recipe instanceof FarmRecipe)) {
    		return 0;
    	}
    	
    	FarmRecipe fr = (FarmRecipe)recipe;
    	return (int)(fr.getProductionRate() * layoutFactor * proximityFactor * biomeFactor);
    }
    
	/**
	 * Gets the settings specific to this block
	 * @return The mongodb document with the settings
	 */
    @Override
	public BasicDBObject getSettings() {
    	BasicDBObject doc = super.getSettings();

		doc = doc.append("last_survey", lastSurvey);
		
		doc = doc.append("layout_factor", this.layoutFactor);
		doc = doc.append("proximity_factor", this.proximityFactor);
		doc = doc.append("biome_factor", this.biomeFactor);
		
		BasicDBList cropList = new BasicDBList();
		for(Entry<CropType, Integer> e : this.farmedCrops.entrySet()) {
			cropList.add(
					new BasicDBObject()
					.append("name", e.getKey().toString())
					.append("count", e.getValue()));
		}
		
		doc = doc.append("crops", cropList);
    	
    	return doc;
	}
    
	/**
	 * Loads settings from a mongodb document
	 * @param o The db document
	 */
	public void loadSettings(BasicDBObject o) {

		this.lastSurvey = o.getDate("last_survey", lastSurvey);
		
		this.layoutFactor = o.getDouble("layout_factor", 0);
		this.proximityFactor = o.getDouble("proximity_factor", 0);
		this.biomeFactor = o.getDouble("biome_factor", 0);
		
		if (o.containsField("crops")) {
			BasicDBList cropObject = (BasicDBList)o.get("crops");
			for (Object cropObj : cropObject) {
				BasicDBObject crop = (BasicDBObject)cropObj;
				if (crop.containsField("name")) {
					CropType cropType = CropType.valueOf(crop.getString("name"));
					if (cropType != null) {
						int count = crop.getInt("count", 0);
						this.farmedCrops.put(cropType, count);
					}
				}
			}
		}
		
		super.loadSettings(o);
	}
    
    
    /**
     * Whether the factory should run while unloaded
     * @return
     */
    public boolean runUnloaded() {
    	return (recipe instanceof FarmRecipe);
    }
}
