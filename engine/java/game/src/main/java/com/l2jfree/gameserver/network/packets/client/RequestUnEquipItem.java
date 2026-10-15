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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.events.CTF.CTFPlayerInfo;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class ...
 * 
 * @version $Revision: 1.8.2.3.2.7 $ $Date: 2005/03/27 15:29:30 $
 */
public class RequestUnEquipItem extends L2ClientPacket
{
	private static final String _C__11_REQUESTUNEQUIPITEM = "[C] 11 RequestUnequipItem";
	
	// cd
	private int _slot;
	
	/**
	 * packet type id 0x11
	 * format:		cd
	 * @param decrypt
	 */
	@Override
	protected void readImpl()
	{
		_slot = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (_log.isDebugEnabled())
			_log.debug("request unequip slot " + _slot);
		
		if (activeChar.isInEvent(CTFPlayerInfo.class) && activeChar.as(CTFPlayerInfo.class)._haveFlagCTF)
		{
			activeChar.sendMessage("You can't unequip a CTF flag.");
			sendAF();
			return;
		}
		
		L2ItemInstance item = activeChar.getInventory().getPaperdollItemByL2ItemId(_slot);
		if (item == null
				|| item.isWear()
				|| // Wear-items are not to be unequipped
				item.getItemId() == 9819
				|| // Fortress siege combat flags can't be unequipped
				// Prevent player from unequipping items in special conditions
				activeChar.isStunned() || activeChar.isSleeping() || activeChar.isParalyzed()
				|| activeChar.isAlikeDead())
		{
			sendAF();
			return;
		}
		
		// Prevent of unequiping a cursed weapon
		else if (_slot == L2Item.SLOT_LR_HAND && activeChar.isCursedWeaponEquipped())
		{
			sendAF();
			return;
		}
		
		else if (activeChar.isCastingNow() || activeChar.isCastingSimultaneouslyNow())
		{
			requestFailed(SystemMessageId.CANNOT_USE_ITEM_WHILE_USING_MAGIC);
			return;
		}
		
		L2ItemInstance[] unequiped = activeChar.getInventory().unEquipItemInBodySlotAndRecord(_slot);
		
		for (L2ItemInstance element : unequiped)
			activeChar.getInventory().updateInventory(element);
		activeChar.broadcastUserInfo();
		
		// this can be 0 if the user pressed the right mouse button twice very fast
		if (unequiped.length > 0)
		{
			SystemMessage sm = null;
			if (unequiped[0].getEnchantLevel() > 0)
			{
				sm = new SystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
				sm.addNumber(unequiped[0].getEnchantLevel());
				sm.addItemName(unequiped[0]);
			}
			else
			{
				sm = new SystemMessage(SystemMessageId.S1_DISARMED);
				sm.addItemName(unequiped[0]);
			}
			sendPacket(sm);
		}
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__11_REQUESTUNEQUIPITEM;
	}
}
