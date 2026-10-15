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
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

public class L2FortEnvoyInstance extends L2Npc
{
	public L2FortEnvoyInstance(int objectID, L2NpcTemplate template)
	{
		super(objectID, template);
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
				showMessageWindow(player);
			}
		}
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showMessageWindow(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		
		String filename;
		
		if (!player.isClanLeader() || player.getClan() == null
				|| getFort().getFortId() != player.getClan().getHasFort())
			filename = "data/html/fortress/envoy-noclan.htm";
		else if (getFort().getFortState() == 0)
			filename = "data/html/fortress/envoy.htm";
		else
			filename = "data/html/fortress/envoy-no.htm";
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace(
				"%castleName%",
				String.valueOf(CastleManager.getInstance().getCastleById(getFort().getCastleIdFromEnvoy(getNpcId()))
						.getName()));
		
		player.sendPacket(html);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken(); // Get actual command
		
		String par = "";
		if (st.countTokens() >= 1)
			par = st.nextToken();
		
		if (actualCommand.equalsIgnoreCase("select"))
		{
			int val = 0;
			try
			{
				val = Integer.parseInt(par);
			}
			catch (IndexOutOfBoundsException ioobe)
			{
			}
			catch (NumberFormatException nfe)
			{
			}
			int castleId = 0;
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			if (val == 2)
			{
				castleId = getFort().getCastleIdFromEnvoy(getNpcId());
				if (CastleManager.getInstance().getCastleById(castleId).getOwnerId() < 1)
				{
					html.setHtml("<html><body>Contact is currently not possible, "
							+ CastleManager.getInstance().getCastleById(castleId).getName()
							+ " Castle isn't currently owned by any clan.</body></html>");
					player.sendPacket(html);
					return;
				}
			}
			getFort().setFortState(val, castleId);
			html.setFile("data/html/fortress/envoy-ok.htm");
			html.replace(
					"%castleName%",
					String.valueOf(CastleManager.getInstance()
							.getCastleById(getFort().getCastleIdFromEnvoy(getNpcId())).getName()));
			
			player.sendPacket(html);
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
}
