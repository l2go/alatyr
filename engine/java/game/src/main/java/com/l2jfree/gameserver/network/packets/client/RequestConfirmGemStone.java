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
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ExPutCommissionResultForVariationMake;

/**
 * Format:(ch) dddd
 * @author  -Wooden-
 */
public final class RequestConfirmGemStone extends AbstractRefinePacket
{
	private static final String _C__D0_2B_REQUESTCONFIRMGEMSTONE = "[C] D0:2B RequestConfirmGemStone";
	
	private int _targetItemObjId;
	private int _refinerItemObjId;
	private int _gemstoneItemObjId;
	private long _gemStoneCount;
	
	@Override
	protected void readImpl()
	{
		_targetItemObjId = readD();
		_refinerItemObjId = readD();
		_gemstoneItemObjId = readD();
		_gemStoneCount = readCompQ();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		L2ItemInstance targetItem = activeChar.getInventory().getItemByObjectId(_targetItemObjId);
		L2ItemInstance refinerItem = activeChar.getInventory().getItemByObjectId(_refinerItemObjId);
		L2ItemInstance gemStoneItem = activeChar.getInventory().getItemByObjectId(_gemstoneItemObjId);
		if (targetItem == null || refinerItem == null || gemStoneItem == null)
		{
			requestFailed(SystemMessageId.AUGMENTATION_FAILED_DUE_TO_INAPPROPRIATE_CONDITIONS);
			return;
		}
		
		if (!isValid(activeChar))
		{
			sendAF();
			return;
		}
		// Make sure the item is a gemstone
		if (!isValid(activeChar, targetItem, refinerItem, gemStoneItem))
		{
			requestFailed(SystemMessageId.THIS_IS_NOT_A_SUITABLE_ITEM);
			return;
		}
		
		// Check for gemstone count
		final LifeStone ls = getLifeStone(refinerItem.getItemId());
		if (ls == null)
			return;
		
		if (_gemStoneCount != getGemStoneCount(targetItem.getItem().getItemGrade(), ls.getGrade()))
		{
			requestFailed(SystemMessageId.GEMSTONE_QUANTITY_IS_INCORRECT);
			return;
		}
		
		sendPacket(new ExPutCommissionResultForVariationMake(_gemstoneItemObjId, _gemStoneCount,
				gemStoneItem.getItemId()));
		sendPacket(SystemMessageId.PRESS_THE_AUGMENT_BUTTON_TO_BEGIN);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_2B_REQUESTCONFIRMGEMSTONE;
	}
}
