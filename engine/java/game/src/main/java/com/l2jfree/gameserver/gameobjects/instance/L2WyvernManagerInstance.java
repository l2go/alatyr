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
import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;

public class L2WyvernManagerInstance extends L2Npc
{
	public L2WyvernManagerInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.startsWith("RideWyvern"))
		{
			if (!isOwnerClan(player))
				return;
			
			if ((SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) == SevenSigns.CABAL_DUSK)
					&& SevenSigns.getInstance().isSealValidationPeriod())
			{
				player.sendPacket(SystemMessageId.SEAL_OF_STRIFE_FORBIDS_SUMMONING);
				return;
			}
			
			int petItemId = 0;
			L2ItemInstance petItem = null;
			
			if (player.getPet() == null)
			{
				if (player.isMounted())
				{
					petItem = player.getInventory().getItemByObjectId(player.getMountObjectID());
					if (petItem != null)
						petItemId = petItem.getItemId();
				}
			}
			else
				petItemId = player.getPet().getControlItemId();
			
			if (petItemId == 0 || !player.isMounted()
					|| !PetDataTable.isStrider(PetDataTable.getPetIdByItemId(petItemId)))
			{
				player.sendPacket(SystemMessageId.YOU_MAY_ONLY_RIDE_WYVERN_WHILE_RIDING_STRIDER);
				if (!isCastleManager())
					sendNotPossibleMessage(player);
				
				return;
			}
			else if (player.isMounted() && PetDataTable.isStrider(PetDataTable.getPetIdByItemId(petItemId))
					&& petItem != null && petItem.getEnchantLevel() < 55)
			{
				player.sendMessage("Your Strider has not reached the required level.");
				
				if (!isCastleManager())
					sendNotPossibleMessage(player);
				
				return;
			}
			
			// Wyvern requires Config.MANAGER_CRYSTAL_COUNT crystal for ride...
			if (player.getInventory().getItemByItemId(1460) != null
					&& player.getInventory().getItemByItemId(1460).getCount() >= Config.ALT_MANAGER_CRYSTAL_COUNT)
			{
				if (!player.disarmWeapons(true))
					return;
				
				if (player.isMounted())
					player.dismount();
				
				if (player.getPet() != null)
					player.getPet().unSummon(player);
				
				if (player.mount(12621, 0, true))
				{
					player.getInventory().destroyItemByItemId("Wyvern", 1460, Config.ALT_MANAGER_CRYSTAL_COUNT, player,
							player.getTarget());
					player.addSkill(SkillTable.getInstance().getInfo(4289, 1));
					player.sendMessage("The Wyvern has been summoned successfully!");
				}
			}
			else
			{
				if (!isCastleManager())
					sendNotPossibleMessage(player);
				
				player.sendMessage("You need " + Config.ALT_MANAGER_CRYSTAL_COUNT + " Crystals: B Grade.");
			}
		}
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
				// Notify the L2Player AI with AI_INTENTION_INTERACT
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
			else
				showMessageWindow(player);
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	private void showMessageWindow(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		
		String filename = "data/html/wyvernmanager/fortress-wyvernmanager-no.htm";
		
		if (isCastleManager())
			filename = "data/html/wyvernmanager/castle-wyvernmanager-no.htm";
		
		if (isOwnerClan(player))
		{
			if (isCastleManager())
				filename = "data/html/wyvernmanager/castle-wyvernmanager.htm"; // Castle Owner message window
			else
				filename = "data/html/wyvernmanager/fortress-wyvernmanager.htm"; // Fort Owner message window
		}
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", getName());
		html.replace("%count%", String.valueOf(Config.ALT_MANAGER_CRYSTAL_COUNT));
		player.sendPacket(html);
	}
	
	protected boolean isOwnerClan(L2Player player)
	{
		return true;
	}
	
	private boolean isCastleManager()
	{
		int npcId = getNpcId();
		
		if (npcId >= 36457 && npcId <= 36477)
			return false;
		
		return true;
	}
	
	private void sendNotPossibleMessage(L2Player player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setFile("data/html/wyvernmanager/fortress-wyvernmanager-notpossible.htm");
		html.replace("%count%", String.valueOf(Config.ALT_MANAGER_CRYSTAL_COUNT));
		player.sendPacket(html);
	}
}
