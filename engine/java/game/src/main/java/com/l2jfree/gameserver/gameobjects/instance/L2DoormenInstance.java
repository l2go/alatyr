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

import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.TeleportLocationTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2TeleportLocation;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/**
 * This class ...
 * 
 * @version $Revision$ $Date$
 */
public class L2DoormenInstance extends L2NpcInstance
{
	/**
	 * @param template
	 */
	public L2DoormenInstance(int objectID, L2NpcTemplate template)
	{
		super(objectID, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.startsWith("Chat"))
		{
			showMessageWindow(player);
		}
		else if (command.startsWith("open_doors"))
		{
			if (isOwnerClan(player))
			{
				if (isUnderSiege())
					cannotManageDoors(player);
				else
					openDoors(player, command);
			}
		}
		else if (command.startsWith("close_doors"))
		{
			if (isOwnerClan(player))
			{
				if (isUnderSiege())
					cannotManageDoors(player);
				else
					closeDoors(player, command);
			}
		}
		else if (command.startsWith("tele"))
		{
			if (isOwnerClan(player))
				doTeleport(player, command);
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	/**
	 * this is called when a player interacts with this NPC
	 * 
	 * @param player
	 */
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
			// Calculate the distance between the L2Player and the
			// L2NpcInstance
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
		// Send a Server->Client ActionFailed to the L2Player in order to
		// avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	public void showMessageWindow(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		
		if (!isOwnerClan(player))
		{
			html.setFile("data/html/doormen/" + getTemplate().getNpcId() + "-no.htm");
		}
		// TODO: maybe this should be removed
		else if (isUnderSiege())
		{
			html.setFile("data/html/doormen/" + getTemplate().getNpcId() + "-busy.htm");
		}
		else
		{
			html.setFile("data/html/doormen/" + getTemplate().getNpcId() + ".htm");
		}
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}
	
	protected void openDoors(L2Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command.substring(10), ", ");
		st.nextToken();
		
		while (st.hasMoreTokens())
		{
			DoorTable.getInstance().getDoor(Integer.parseInt(st.nextToken())).openMe();
		}
	}
	
	protected void closeDoors(L2Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command.substring(11), ", ");
		st.nextToken();
		
		while (st.hasMoreTokens())
		{
			DoorTable.getInstance().getDoor(Integer.parseInt(st.nextToken())).closeMe();
		}
	}
	
	protected void cannotManageDoors(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/doormen/" + getTemplate().getNpcId() + "-busy.htm");
		player.sendPacket(html);
	}
	
	protected void doTeleport(L2Player player, String command)
	{
		final int whereTo = Integer.parseInt(command.substring(5).trim());
		L2TeleportLocation list = TeleportLocationTable.getInstance().getTemplate(whereTo);
		if (list != null)
		{
			if (!player.isAlikeDead())
				player.teleToLocation(list.getLocX(), list.getLocY(), list.getLocZ(), false);
		}
		else
			_log.warn("No teleport destination with id:" + whereTo);
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	protected boolean isOwnerClan(L2Player player)
	{
		return true;
	}
	
	protected boolean isUnderSiege()
	{
		return false;
	}
}
