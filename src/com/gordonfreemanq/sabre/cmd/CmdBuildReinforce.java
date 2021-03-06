package com.gordonfreemanq.sabre.cmd;

import org.bukkit.inventory.ItemStack;

import com.gordonfreemanq.sabre.Lang;
import com.gordonfreemanq.sabre.blocks.BuildMode;
import com.gordonfreemanq.sabre.blocks.BuildState;
import com.gordonfreemanq.sabre.blocks.ReinforcementMaterial;
import com.gordonfreemanq.sabre.groups.SabreGroup;
import com.gordonfreemanq.sabre.groups.SabreMember;


public class CmdBuildReinforce extends SabreCommand {

	public CmdBuildReinforce()
	{
		super();
		this.aliases.add("bdr");
		this.aliases.add("ctr");
		
		this.requiredArgs.add("group");
		this.optionalArgs.put("mode", "public,insecure");

		this.setHelpShort("Reinforces existing blocks");

		senderMustBePlayer = true;
	}

	@Override
	public void perform() 
	{
		String groupName = this.argAsString(0);
		boolean publicMode = false;
		boolean insecureMode = false;

		SabreGroup g = checkGroupExists(groupName);
		if (g == null) {
			return;
		}
		
		// Get the correct name
		groupName = g.getName();
		
		SabreMember memberMe = g.getMember(me);
		if (memberMe == null) {
			msg(Lang.groupNotMember, groupName);
			return;
		}
		
		if (!memberMe.canBuild()) {
			msg(Lang.noPermission);
			return;
		}
		
		
		BuildState s = me.getBuildState();
		
		if (s.getGroup() != null && s.getMaterial() != null) {
			if (s.getGroup().equals(g) && s.getPublic() == publicMode && s.getInsecure() == insecureMode) {
				me.getBuildState().reset();
				msg(Lang.blockBuildMode, BuildMode.OFF.name());
				return;
			}
		}
		
		ItemStack handItem = me.getPlayer().getItemInHand();
		
		ReinforcementMaterial rm = config.getReinforcementMaterial(me, handItem);
		if (rm == null) {
			msg(Lang.blockNotMaterial);
			return;
		}
		
		if (handItem.hasItemMeta() && (handItem.getItemMeta().hasLore() || handItem.getItemMeta().hasDisplayName())) {
			msg(Lang.blockMaterialHasLore);
			return;
		}
		
		if (args.size() > 1) {
			String mode = this.argAsString(1);
			if (mode.equalsIgnoreCase("public")) {
				publicMode = true;
			} else if (mode.equalsIgnoreCase("insecure")) {
				insecureMode = true;
			}
		}
		
		if (publicMode) {
			groupName += "-PUBLIC";
		} else if (insecureMode) {
			groupName += "-INSECURE";
		}

		
		s.setMode(BuildMode.REINFORCE);
		s.setGroup(g);
		s.setMaterial(rm);
		s.setPublic(publicMode);
		s.setInsecure(insecureMode);
		msg(Lang.blockBuildModeGroup, BuildMode.REINFORCE.name(), groupName);
	}
}
