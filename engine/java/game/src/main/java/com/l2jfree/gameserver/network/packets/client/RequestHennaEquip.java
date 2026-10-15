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

import com.l2jfree.gameserver.datatables.HennaTable;
import com.l2jfree.gameserver.datatables.HennaTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Henna;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class represents a packet sent by the client when a player confirms henna dye
 * selection.
 */
public class RequestHennaEquip extends L2ClientPacket
{
	private static final String _C__BC_RequestHennaEquip = "[C] bc RequestHennaEquip";
	
	private int _symbolId;
	
	/**
	 * packet type id 0xbb
	 * format: cd
	 */
	@Override
	protected void readImpl()
	{
		_symbolId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2Henna henna = HennaTable.getInstance().getTemplate(_symbolId);
		if (henna == null)
		{
			requestFailed(SystemMessageId.SYMBOL_NOT_FOUND);
			return;
		}
		if (activeChar.getHennaEmptySlots() < 1)
		{
			requestFailed(SystemMessageId.SYMBOLS_FULL);
			return;
		}
		if (!HennaTreeTable.getInstance().isDrawable(activeChar, _symbolId))
		{
			requestFailed(SystemMessageId.CANT_DRAW_SYMBOL);
			return;
		}
		
		L2ItemInstance item = activeChar.getInventory().getItemByItemId(henna.getItemId());
		long count = (item == null ? 0 : item.getCount());
		if (count >= henna.getAmount()
				&& activeChar.reduceAdena("Henna", henna.getPrice(), activeChar.getLastFolkNPC(), true))
		{
			activeChar.addHenna(henna);
			L2ItemInstance dye =
					activeChar.getInventory().destroyItemByItemId("Henna", henna.getItemId(), henna.getAmount(),
							activeChar, activeChar.getLastFolkNPC());
			SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
			sm.addItemName(henna.getItemId());
			sm.addItemNumber(henna.getAmount());
			sendPacket(sm);
			// Send inventory update packet
			activeChar.getInventory().updateInventory(dye);
			sendPacket(SystemMessageId.SYMBOL_ADDED);
		}
		else
			sendPacket(SystemMessageId.NUMBER_INCORRECT);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__BC_RequestHennaEquip;
	}
}
