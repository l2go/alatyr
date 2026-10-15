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

import com.l2jfree.gameserver.datatables.AugmentationData;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.L2Augmentation;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ExVariationResult;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;

/**
 * Format:(ch) dddd
 * @author  -Wooden-
 */
public final class RequestRefine extends AbstractRefinePacket
{
	private static final String _C__D0_2C_REQUESTREFINE = "[C] D0:2C RequestRefine";
	
	private int _targetItemObjId;
	private int _refinerItemObjId;
	private int _gemStoneItemObjId;
	private long _gemStoneCount;
	
	@Override
	protected void readImpl()
	{
		_targetItemObjId = readD();
		_refinerItemObjId = readD();
		_gemStoneItemObjId = readD();
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
		L2ItemInstance gemStoneItem = activeChar.getInventory().getItemByObjectId(_gemStoneItemObjId);
		
		if (targetItem == null || refinerItem == null || gemStoneItem == null
				|| !isValid(activeChar, targetItem, refinerItem, gemStoneItem))
		{
			sendPacket(new ExVariationResult(0, 0, 0));
			requestFailed(SystemMessageId.AUGMENTATION_FAILED_DUE_TO_INAPPROPRIATE_CONDITIONS);
			return;
		}
		
		final LifeStone ls = getLifeStone(refinerItem.getItemId());
		if (ls == null)
			return;
		final int lifeStoneLevel = ls.getLevel();
		final int lifeStoneGrade = ls.getGrade();
		if (_gemStoneCount != getGemStoneCount(targetItem.getItem().getItemGrade(), lifeStoneGrade))
		{
			sendPacket(new ExVariationResult(0, 0, 0));
			requestFailed(SystemMessageId.AUGMENTATION_FAILED_DUE_TO_INAPPROPRIATE_CONDITIONS);
			return;
		}
		
		// unequip item
		if (targetItem.isEquipped())
		{
			L2ItemInstance[] unequiped =
					activeChar.getInventory().unEquipItemInSlotAndRecord(targetItem.getLocationSlot());
			InventoryUpdate iu = new InventoryUpdate();
			for (L2ItemInstance itm : unequiped)
				iu.addModifiedItem(itm);
			sendPacket(iu);
			activeChar.broadcastUserInfo();
		}
		
		boolean fail = false;
		if (!activeChar.destroyItem("RequestRefine", refinerItem, 1, null, false))
			fail = true;
		if (!fail && !activeChar.destroyItem("RequestRefine", gemStoneItem, _gemStoneCount, null, false))
			fail = true;
		if (fail)
		{
			sendPacket(new ExVariationResult(0, 0, 0));
			requestFailed(SystemMessageId.AUGMENTATION_FAILED_DUE_TO_INAPPROPRIATE_CONDITIONS);
			return;
		}
		
		final L2Augmentation aug =
				AugmentationData.getInstance().generateRandomAugmentation(lifeStoneLevel, lifeStoneGrade,
						targetItem.getItem().getBodyPart());
		targetItem.setAugmentation(aug);
		
		final int stat12 = 0x0000FFFF & aug.getAugmentationId();
		final int stat34 = aug.getAugmentationId() >> 16;
		sendPacket(new ExVariationResult(stat12, stat34, 1));
		sendPacket(SystemMessageId.THE_ITEM_WAS_SUCCESSFULLY_AUGMENTED);
		InventoryUpdate iu = new InventoryUpdate();
		iu.addModifiedItem(targetItem);
		sendPacket(iu);
		StatusUpdate su = new StatusUpdate(activeChar.getObjectId());
		su.addAttribute(StatusUpdate.CUR_LOAD, activeChar.getCurrentLoad());
		sendPacket(su);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__D0_2C_REQUESTREFINE;
	}
}
