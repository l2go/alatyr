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
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/**
 * This class ...
 * 
 * @version $Revision$ $Date$
 */
public class L2DooropenInstance extends L2NpcInstance
{
	/**
	 * @param template
	 */
	public L2DooropenInstance(int objectID, L2NpcTemplate template)
	{
		super(objectID, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.startsWith("Chat"))
		{
			showMessageWindow(player);
			return;
		}
		else if (command.startsWith("open_doors"))
		{
			DoorTable doorTable = DoorTable.getInstance();
			StringTokenizer st = new StringTokenizer(command.substring(10), ", ");
			
			while (st.hasMoreTokens())
			{
				int _doorid = Integer.parseInt(st.nextToken());
				doorTable.getDoor(_doorid).openMe();
			}
			return;
			
		}
		else
			super.onBypassFeedback(player, command);
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
				showMessageWindow(player);
			}
		}
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	public void showMessageWindow(L2Player player)
	{
		//player.sendPacket(new ActionFailed());
		String filename = "data/html/dooropen/" + getTemplate().getNpcId() + ".htm";
		
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setFile(filename);
		
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}
}
