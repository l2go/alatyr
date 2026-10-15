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

import java.util.StringTokenizer;

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.UserInfo;

/**
 * Reputation score manager
 * @author Kerberos
 */
public class L2FameManagerInstance extends L2Npc
{
	public L2FameManagerInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	/**
	 * this is called when a player interacts with this NPC
	 * @param player
	 */
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		player.setLastFolkNPC(this);
		
		// Check if the L2Player already target the L2Npc
		if (this != player.getTarget())
		{
			// Set the target of the L2Player player
			player.setTarget(this);
		}
		else
		{
			// Calculate the distance between the L2Player and the L2Npc
			if (!canInteract(player))
			{
				// Notify the L2Player AI with AI_INTENTION_INTERACT
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
			}
			else
			{
				showMessageWindow(player);
			}
		}
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken();
		if (actualCommand.equalsIgnoreCase("PK_Count"))
		{
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			if (player.getFame() >= 5000 && player.getClassId().level() >= 2 && player.getClan() != null
					&& player.getClan().getLevel() >= 5)
			{
				if (player.getPkKills() > 0)
				{
					player.setFame(player.getFame() - 5000);
					player.setPkKills(player.getPkKills() - 1);
					player.sendPacket(new UserInfo(player));
					html.setFile("data/html/famemanager/" + getNpcId() + "-3.htm");
				}
				else
				{
					html.setFile("data/html/famemanager/" + getNpcId() + "-4.htm");
				}
			}
			else
			{
				html.setFile("data/html/famemanager/" + getNpcId() + "-lowfame.htm");
			}
			sendHtmlMessage(player, html);
		}
		else if (actualCommand.equalsIgnoreCase("CRP"))
		{
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			if (player.getFame() >= 1000 && player.getClassId().level() >= 2 && player.getClan() != null
					&& player.getClan().getLevel() >= 5)
			{
				player.setFame(player.getFame() - 1000);
				player.getClan().setReputationScore(player.getClan().getReputationScore() + 50, true);
				player.sendPacket(SystemMessageId.ACQUIRED_50_CLAN_FAME_POINTS);
				html.setFile("data/html/famemanager/" + getNpcId() + "-5.htm");
			}
			else
			{
				html.setFile("data/html/famemanager/" + getNpcId() + "-lowfame.htm");
			}
			sendHtmlMessage(player, html);
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	private void sendHtmlMessage(L2Player player, NpcHtmlMessage html)
	{
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}
	
	private void showMessageWindow(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		String filename = "data/html/famemanager/" + getNpcId() + "-lowfame.htm";
		
		if (player.getFame() > 0)
			filename = "data/html/famemanager/" + getNpcId() + ".htm";
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}
}
