/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfree.gameserver.gameobjects.instance;

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.status.ArtefactStatus;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

/**
 * This class manages all Castle Siege Artefacts.<BR><BR>
 *
 * @version $Revision: 1.11.2.1.2.7 $ $Date: 2005/04/06 16:13:40 $
 */
public final class L2ArtefactInstance extends L2Npc
{
	/**
	 * Constructor of L2ArtefactInstance (use L2Creature and L2NpcInstance constructor).<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Call the L2Creature constructor to set the _template of the L2ArtefactInstance (copy skills from template to object and link _calculators to NPC_STD_CALCULATOR) </li>
	 * <li>Set the name of the L2ArtefactInstance</li>
	 * <li>Create a RandomAnimation Task that will be launched after the calculated delay if the server allow it </li><BR><BR>
	 *
	 * @param objectId Identifier of the object to initialized
	 * @param L2NpcTemplate Template to apply to the NPC
	 */
	public L2ArtefactInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public boolean isAttackable()
	{
		return false;
	}
	
	/**
	 * Manage actions when a player click on the L2ArtefactInstance.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Set the L2NpcInstance as target of the L2Player player (if necessary)</li>
	 * <li>Send a Server->Client packet MyTargetSelected to the L2Player player (display the select window)</li>
	 * <li>Send a Server->Client packet ValidateLocation to correct the L2NpcInstance position and heading on the client </li><BR><BR>
	 *
	 * <B><U> Example of use </U> :</B><BR><BR>
	 * <li> Client packet : Action, AttackRequest</li><BR><BR>
	 *
	 * @param player The L2Player that start an action on the L2ArtefactInstance
	 *
	 */
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		if (this != player.getTarget())
		{
			// Set the target of the L2Player player
			player.setTarget(this);
		}
		else
		{
			// Calculate the distance between the L2Player and the L2NpcInstance
			if (!canInteract(player))
			{
				// Notify the L2Player AI with AI_INTENTION_INTERACT
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
			}
		}
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void onForcedAttack(L2Player player)
	{
		onAction(player);
	}
	
	@Override
	protected CreatureStatus initStatus()
	{
		return new ArtefactStatus(this);
	}
	
	@Override
	public ArtefactStatus getStatus()
	{
		return (ArtefactStatus)_status;
	}
}
