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
package com.l2jfree.gameserver.handler.voicedcommands;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.handler.IVoicedCommandHandler;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;

/**
 * This class trades Gold Bars for Adena and vice versa.
 *
 * @author Ahmed
 */
public class Banking implements IVoicedCommandHandler
{
	private static final String[] _voicedCommands = { "bank", "withdraw", "deposit" };
	
	/**
	 * 
	 * @see com.l2jfree.gameserver.handler.IVoicedCommandHandler#useVoicedCommand(java.lang.String, L2Player.sf.l2j.gameserver.model.actor.instance.L2Player, java.lang.String)
	 */
	@Override
	public boolean useVoicedCommand(String command, L2Player activeChar, String target)
	{
		if (!Config.BANKING_SYSTEM_ENABLED)
			return false;
		
		if (command.equalsIgnoreCase("bank"))
		{
			activeChar.sendMessage(".deposit (" + Config.BANKING_SYSTEM_ADENA + " Adena = "
					+ Config.BANKING_SYSTEM_GOLDBARS + " Goldbar) / .withdraw (" + Config.BANKING_SYSTEM_GOLDBARS
					+ " Goldbar = " + Config.BANKING_SYSTEM_ADENA + " Adena)");
			return true;
		}
		else if (command.equalsIgnoreCase("deposit"))
		{
			if (activeChar.getInventory().getInventoryItemCount(PlayerInventory.ADENA_ID, 0) >= Config.BANKING_SYSTEM_ADENA)
			{
				InventoryUpdate iu = new InventoryUpdate();
				activeChar.getInventory().reduceAdena("Goldbar", Config.BANKING_SYSTEM_ADENA, activeChar, null);
				activeChar.getInventory().addItem("Goldbar", 3470, Config.BANKING_SYSTEM_GOLDBARS, activeChar, null);
				
				// No need to update every item in the inventory
				//activeChar.getInventory().updateDatabase();
				
				activeChar.sendPacket(iu);
				activeChar.sendMessage("Thank you, you now have " + Config.BANKING_SYSTEM_GOLDBARS
						+ " Goldbar(s), and " + Config.BANKING_SYSTEM_ADENA + " less adena.");
			}
			else
			{
				activeChar.sendMessage("You do not have enough Adena to convert to Goldbar(s), you need "
						+ Config.BANKING_SYSTEM_ADENA + " Adena.");
			}
			return true;
		}
		else if (command.equalsIgnoreCase("withdraw"))
		{
			if (activeChar.getInventory().getInventoryItemCount(3470, 0) >= Config.BANKING_SYSTEM_GOLDBARS)
			{
				InventoryUpdate iu = new InventoryUpdate();
				activeChar.getInventory().destroyItemByItemId("Adena", 3470, Config.BANKING_SYSTEM_GOLDBARS,
						activeChar, null);
				activeChar.getInventory().addAdena("Adena", Config.BANKING_SYSTEM_ADENA, activeChar, null);
				
				// No need to update every item in the inventory
				//activeChar.getInventory().updateDatabase();
				
				activeChar.sendPacket(iu);
				activeChar.sendMessage("Thank you, you now have " + Config.BANKING_SYSTEM_ADENA + " Adena, and "
						+ Config.BANKING_SYSTEM_GOLDBARS + " less Goldbar(s).");
			}
			else
			{
				activeChar.sendMessage("You do not have any Goldbars to turn into " + Config.BANKING_SYSTEM_ADENA
						+ " Adena.");
			}
			return true;
		}
		
		return false;
	}
	
	/**
	 * 
	 * @see com.l2jfree.gameserver.handler.IVoicedCommandHandler#getVoicedCommandList()
	 */
	@Override
	public String[] getVoicedCommandList()
	{
		return _voicedCommands;
	}
}
