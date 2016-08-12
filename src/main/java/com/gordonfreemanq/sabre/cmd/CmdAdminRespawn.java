package com.gordonfreemanq.sabre.cmd;

import com.gordonfreemanq.sabre.core.CommandVisibility;
import com.gordonfreemanq.sabre.core.Permission;


public class CmdAdminRespawn extends SabreCommand {

	public CmdAdminRespawn()
	{
		super();
		this.aliases.add("respawn");

		this.setHelpShort("Random spawns you");
		
		this.errorOnToManyArgs = false;
		this.senderMustBePlayer = true;
		this.permission = Permission.ADMIN.node;
		this.visibility = CommandVisibility.SECRET;
	}

	@Override
	public void perform() 
	{
		plugin.getSpawner().spawnPlayerRandom(me);
	}
}
