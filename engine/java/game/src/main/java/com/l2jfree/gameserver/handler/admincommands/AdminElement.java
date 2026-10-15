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
package com.l2jfree.gameserver.handler.admincommands;

import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;

public class AdminElement implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = { "admin_setlh", "admin_setlc", "admin_setll", "admin_setlg",
			"admin_setlb", "admin_setlw", "admin_setls" };
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		int armorType = -1;
		
		if (command.startsWith("admin_setlh"))
			armorType = Inventory.PAPERDOLL_HEAD;
		else if (command.startsWith("admin_setlc"))
			armorType = Inventory.PAPERDOLL_CHEST;
		else if (command.startsWith("admin_setlg"))
			armorType = Inventory.PAPERDOLL_GLOVES;
		else if (command.startsWith("admin_setlb"))
			armorType = Inventory.PAPERDOLL_FEET;
		else if (command.startsWith("admin_setll"))
			armorType = Inventory.PAPERDOLL_LEGS;
		else if (command.startsWith("admin_setlw"))
			armorType = Inventory.PAPERDOLL_RHAND;
		else if (command.startsWith("admin_setls"))
			armorType = Inventory.PAPERDOLL_LHAND;
		
		if (armorType != -1)
		{
			try
			{
				String[] args = command.split(" ");
				
				byte element = Elementals.getElementId(args[1]);
				int value = Integer.parseInt(args[2]);
				if (element < -1 || element > 5 || value < 0 || value > 600)
				{
					activeChar
							.sendMessage("Usage: //setlh/setlc/setlg/setlb/setll/setlw/setls <element> <value>[0-600]");
					return false;
				}
				
				setElement(activeChar, element, value, armorType);
			}
			catch (Exception e)
			{
				activeChar
						.sendMessage("Usage: //setlh/setlc/setlg/setlb/setll/setlw/setls <element>[0-5] <value>[0-600]");
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void setElement(L2Player activeChar, byte type, int value, int armorType)
	{
		// get the target
		L2Object target = activeChar.getTarget();
		if (target == null)
			target = activeChar;
		L2Player player = null;
		if (target instanceof L2Player)
		{
			player = (L2Player)target;
		}
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		
		L2ItemInstance itemInstance = null;
		
		// only attempt to enchant if there is a weapon equipped
		L2ItemInstance parmorInstance = player.getInventory().getPaperdollItem(armorType);
		if (parmorInstance != null && parmorInstance.getLocationSlot() == armorType)
		{
			itemInstance = parmorInstance;
		}
		else
		{
			// for bows and double handed weapons
			parmorInstance = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LRHAND);
			if (parmorInstance != null && parmorInstance.getLocationSlot() == Inventory.PAPERDOLL_LRHAND)
				itemInstance = parmorInstance;
		}
		
		if (itemInstance != null)
		{
			String old, current;
			Elementals element = itemInstance.getElementals();
			if (element == null)
				old = "None";
			else
				old = element.toString();
			
			// set enchant value
			player.getInventory().unEquipItemInSlotAndRecord(armorType);
			if (type == -1)
				itemInstance.clearElementAttr();
			else
				itemInstance.setElementAttr(type, value);
			player.getInventory().equipItemAndRecord(itemInstance);
			
			if (itemInstance.getElementals() == null)
				current = "None";
			else
				current = itemInstance.getElementals().toString();
			
			// send packets
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(itemInstance);
			player.sendPacket(iu);
			
			// informations
			activeChar.sendMessage("Changed elemental power of " + player.getName() + "'s "
					+ itemInstance.getItem().getName() + " from " + old + " to " + current + ".");
			if (player != activeChar)
			{
				player.sendMessage(activeChar.getName() + " has changed the elemental power of your "
						+ itemInstance.getItem().getName() + " from " + old + " to " + current + ".");
			}
		}
	}
}
