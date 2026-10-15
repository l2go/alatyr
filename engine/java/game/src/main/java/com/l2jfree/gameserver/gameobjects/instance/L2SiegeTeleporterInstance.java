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

import com.l2jfree.gameserver.datatables.TeleportLocationTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2TeleportLocation;
import com.l2jfree.gameserver.model.restriction.AvailableRestriction;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

/**
 * Represents a hireable mercenary teleporter, that can be manually positioned.
 * @author savormix
 */
public final class L2SiegeTeleporterInstance extends L2Npc
{
	/**
	 * @param objectId
	 * @param template
	 */
	public L2SiegeTeleporterInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		// IDK if needed, must test
		if (ObjectRestrictions.getInstance().checkRestriction(player, AvailableRestriction.PlayerTeleport))
		{
			player.sendMessage("You cannot teleport due to a restriction.");
			return;
		}
		if (getCastle() == null || !getCastle().getSiege().getIsInProgress()
				|| player.getClanId() != getCastle().getOwnerId())
		{
			showChatWindow(player);
			return;
		}
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken(); // Get actual command
		if (actualCommand.equals("goto"))
		{
			if (st.countTokens() <= 0)
			{
				showChatWindow(player);
				return;
			}
			int where = -1;
			try
			{
				where = Integer.parseInt(st.nextToken());
			}
			catch (NumberFormatException e)
			{
			}
			if (where == -1 || player.isAlikeDead())
				return;
			L2TeleportLocation list = TeleportLocationTable.getInstance().getTemplate(where);
			player.teleToLocation(list.getLocX(), list.getLocY(), list.getLocZ(), true);
		}
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		return "data/html/teleporter/" + npcId + ".htm";
	}
	
	@Override
	public void showChatWindow(L2Player player, int val)
	{
		String filename = "data/html/npcdefault.htm";
		if (getCastle() != null && getCastle().getSiege().getIsInProgress())
		{
			if (player.getClanId() == getCastle().getOwnerId())
				filename = getHtmlPath(getNpcId(), val);
			else
				filename = "data/html/teleporter/castleteleporter-no.htm";
		}
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", getName());
		player.sendPacket(html);
	}
}
