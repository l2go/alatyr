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
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.util.Evolve;

/**
 * This class ...
 *
 * @version $Revision$ $Date$
 */
public class L2PetManagerInstance extends L2MerchantInstance
{
	public L2PetManagerInstance(int objectID, L2NpcTemplate template)
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
		String filename = "data/html/petmanager/" + getNpcId() + ".htm";
		
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		if (Config.ALLOW_RENTPET && Config.LIST_PET_RENT_NPC.contains(getNpcId()))
			html.replace("_Quest", "_RentPet\">Rent Pet</a><br><a action=\"bypass -h npc_%objectId%_Quest");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", getName());
		player.sendPacket(html);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		
		if (val == 0)
			pom = "" + npcId;
		else
			pom = npcId + "-" + val;
		
		return "data/html/petmanager/" + pom + ".htm";
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.startsWith("exchange"))
		{
			String[] params = command.split(" ");
			int val = Integer.parseInt(params[1]);
			switch (val)
			{
				case 1:
					exchange(player, 7585, 6650);
					break;
				case 2:
					exchange(player, 7583, 6648);
					break;
				case 3:
					exchange(player, 7584, 6649);
					break;
			}
		}
		else if (command.startsWith("evolve"))
		{
			String[] params = command.split(" ");
			int val = Integer.parseInt(params[1]);
			boolean ok = false;
			switch (val)
			{
			// Info evolve(player, "curent pet summon item", "new pet summon item", "lvl required to evolve")
			// To ignore evolve just put value 0 where do you like example: evolve(player, 0, 9882, 55);
				case 1:
					ok = Evolve.doEvolve(player, this, 2375, 9882, 55);
					break;
				case 2:
					ok = Evolve.doEvolve(player, this, 9882, 10426, 70);
					break;
				case 3:
					ok = Evolve.doEvolve(player, this, 6648, 10311, 55);
					break;
				case 4:
					ok = Evolve.doEvolve(player, this, 6650, 10313, 55);
					break;
				case 5:
					ok = Evolve.doEvolve(player, this, 6649, 10312, 55);
					break;
			}
			if (!ok)
			{
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/petmanager/evolve_no.htm");
				player.sendPacket(html);
			}
		}
		else if (command.startsWith("restore"))
		{
			String[] params = command.split(" ");
			int val = Integer.parseInt(params[1]);
			boolean ok = false;
			switch (val)
			{
			// Info evolve(player, "curent pet summon item", "new pet summon item", "lvl required to evolve")
				case 1:
					ok = Evolve.doEvolve(player, this, 10307, 9882, 55);
					break;
				case 2:
					ok = Evolve.doEvolve(player, this, 10611, 10426, 70);
					break;
				case 3:
					ok = Evolve.doEvolve(player, this, 10308, 4422, 55);
					break;
				case 4:
					ok = Evolve.doEvolve(player, this, 10309, 4423, 55);
					break;
				case 5:
					ok = Evolve.doEvolve(player, this, 10310, 4424, 55);
					break;
			}
			if (!ok)
			{
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/petmanager/evolve_no.htm");
				player.sendPacket(html);
			}
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	public final void exchange(L2Player player, int itemIdtake, int itemIdgive)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		if (player.destroyItemByItemId("Consume", itemIdtake, 1, this, true))
		{
			player.addItem("", itemIdgive, 1, this, true);
			html.setFile("data/html/petmanager/" + getNpcId() + ".htm");
			player.sendPacket(html);
		}
		else
		{
			html.setFile("data/html/petmanager/exchange_no.htm");
			player.sendPacket(html);
		}
	}
}
