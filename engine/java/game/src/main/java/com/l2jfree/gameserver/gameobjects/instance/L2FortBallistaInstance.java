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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

public class L2FortBallistaInstance extends L2Npc
{
	public L2FortBallistaInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		getKnownList(); // init knownlist
	}
	
	@Override
	public boolean isAutoAttackable(L2Creature attacker)
	{
		return true;
	}
	
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (getFort().getSiege().getIsInProgress())
		{
			if (killer instanceof L2Player)
			{
				L2Player player = ((L2Player)killer);
				if (player.getClan() != null && player.getClan().getLevel() >= 5)
				{
					player.getClan().setReputationScore(player.getClan().getReputationScore() + Config.BALLISTA_POINTS,
							true);
				}
				player.sendPacket(SystemMessageId.BALLISTA_DESTROYED_CLAN_REPU_INCREASED);
			}
		}
		
		return true;
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		// Check if the L2Player already target the L2NpcInstance
		if (this != player.getTarget())
		{
			// Set the target of the L2Player player
			player.setTarget(this);
		}
		else
		{
			if (isAutoAttackable(player) && !isAlikeDead())
			{
				if (Math.abs(player.getZ() - getZ()) < 600) // this max heigth difference might need some tweaking
				{
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
				}
			}
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
	public boolean hasRandomAnimation()
	{
		return false;
	}
}
