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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.CastleManorManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/**
 * @author l3x
 */
public class L2CastleBlacksmithInstance extends L2NpcInstance
{
	protected static final int COND_ALL_FALSE = 0;
	protected static final int COND_BUSY_BECAUSE_OF_SIEGE = 1;
	protected static final int COND_OWNER = 2;
	
	public L2CastleBlacksmithInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		player.setLastFolkNPC(this);
		
		// Check if the L2Player already target the L2NpcInstance
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
			else
			{
				if (CastleManorManager.getInstance().isDisabled())
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile("data/html/npcdefault.htm");
					html.replace("%objectId%", String.valueOf(getObjectId()));
					html.replace("%npcname%", getName());
					player.sendPacket(html);
				}
				else
				{
					showMessageWindow(player, 0);
				}
			}
		}
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (CastleManorManager.getInstance().isDisabled())
		{
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setFile("data/html/npcdefault.htm");
			html.replace("%objectId%", String.valueOf(getObjectId()));
			html.replace("%npcname%", getName());
			player.sendPacket(html);
			return;
		}
		
		int condition = validateCondition(player);
		if (condition <= COND_ALL_FALSE)
			return;
		
		if (condition == COND_BUSY_BECAUSE_OF_SIEGE)
			return;
		else if (condition == COND_OWNER)
		{
			if (command.startsWith("Chat"))
			{
				int val = 0;
				try
				{
					val = Integer.parseInt(command.substring(5));
				}
				catch (IndexOutOfBoundsException ioobe)
				{
				}
				catch (NumberFormatException nfe)
				{
				}
				showMessageWindow(player, val);
			}
			else
			{
				super.onBypassFeedback(player, command);
			}
		}
	}
	
	private void showMessageWindow(L2Player player, int val)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		String filename = "data/html/castleblacksmith/castleblacksmith-no.htm";
		
		int condition = validateCondition(player);
		if (condition > COND_ALL_FALSE)
		{
			if (condition == COND_BUSY_BECAUSE_OF_SIEGE)
				filename = "data/html/castleblacksmith/castleblacksmith-busy.htm"; // Busy because of siege
			else if (condition == COND_OWNER)
			{ // Clan owns castle
				if (val == 0)
					filename = "data/html/castleblacksmith/castleblacksmith.htm";
				else
					filename = "data/html/castleblacksmith/castleblacksmith-" + val + ".htm";
			}
		}
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", getName());
		html.replace("%castleid%", Integer.toString(getCastle().getCastleId()));
		player.sendPacket(html);
	}
	
	protected int validateCondition(L2Player player)
	{
		if (player.isGM())
			return COND_OWNER;
		if (getCastle() != null && getCastle().getCastleId() > 0)
		{
			if (player.getClan() != null)
			{
				if (getCastle().getSiege().getIsInProgress())
					return COND_BUSY_BECAUSE_OF_SIEGE; // Busy because of siege
				else if (getCastle().getOwnerId() == player.getClanId() // Clan owns castle
						&& L2Clan.checkPrivileges(player, L2Clan.CP_CS_MANOR_ADMIN))
					return COND_OWNER; // Owner
			}
		}
		return COND_ALL_FALSE;
	}
}
