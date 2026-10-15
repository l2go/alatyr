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

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.EnchantResult;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

public class RequestEnchantItem extends AbstractEnchantPacket
{
	private static final String _C__REQUESTENCHANTITEM = "[C] 5F RequestEnchantItem c[dd]";
	
	private int _objectId = 0;
	
	//private int				_unk;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		/*_unk = */readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		if (activeChar.isOnline() == 0)
		{
			activeChar.setActiveEnchantItem(null);
			return;
		}
		
		if (_objectId == 0)
		{
			sendAF();
			return;
		}
		
		// Restrict enchant during restart/shutdown (because of an existing exploit)
		if (Shutdown.isActionDisabled(DisableType.ENCHANT))
		{
			requestFailed(SystemMessageId.FUNCTION_INACCESSIBLE_NOW);
			return;
		}
		
		// Restrict enchant during a trade (bug if enchant fails)
		if (activeChar.isProcessingTransaction())
		{
			// Cancel trade
			activeChar.cancelActiveTrade();
			activeChar.setActiveEnchantItem(null);
			requestFailed(SystemMessageId.CANNOT_PICKUP_OR_USE_ITEM_WHILE_TRADING);
			return;
		}
		else if (activeChar.getPrivateStoreType() != L2Player.STORE_PRIVATE_NONE)
		{
			activeChar.setActiveEnchantItem(null);
			requestFailed(SystemMessageId.CANNOT_ENCHANT_WHILE_STORE);
			return;
		}
		
		L2ItemInstance item = activeChar.getInventory().getItemByObjectId(_objectId);
		L2ItemInstance scroll = activeChar.getActiveEnchantItem();
		L2ItemInstance support = activeChar.getActiveEnchantSupportItem();
		if (_log.isDebugEnabled())
			_log.info("ENCHANT: Status bar filled = request enchant " + item);
		if (item == null || scroll == null || item.isWear())
		{
			activeChar.setActiveEnchantItem(null);
			requestFailed(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
			return;
		}
		else if (item.getLocation() != L2ItemInstance.ItemLocation.INVENTORY
				&& item.getLocation() != L2ItemInstance.ItemLocation.PAPERDOLL)
		{
			activeChar.setActiveEnchantItem(null);
			requestFailed(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
			return;
		}
		
		// template for scroll
		EnchantScroll scrollTemplate = getEnchantScroll(scroll);
		
		// scroll not found in list
		if (scrollTemplate == null)
			return;
		
		// template for support item, if exist
		EnchantItem supportTemplate = null;
		if (support != null)
			supportTemplate = getSupportItem(support);
		
		// first validation check
		if (!scrollTemplate.isValid(item, supportTemplate) || !isEnchantable(item))
		{
			requestFailed(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
			activeChar.setActiveEnchantItem(null);
			activeChar.sendPacket(new EnchantResult(2, 0, 0));
			return;
		}
		
		// fast auto-enchant cheat check
		if (activeChar.getActiveEnchantTimestamp() == 0
				|| System.currentTimeMillis() - activeChar.getActiveEnchantTimestamp() < 2000)
		{
			Util.handleIllegalPlayerAction(activeChar, "Player " + activeChar.getName() + " use autoenchant program ",
					Config.DEFAULT_PUNISH);
			activeChar.setActiveEnchantItem(null);
			activeChar.sendPacket(new EnchantResult(2, 0, 0));
			return;
		}
		
		// attempting to destroy scroll
		scroll = activeChar.getInventory().destroyItem("Enchant", scroll.getObjectId(), 1, activeChar, item);
		if (scroll == null)
		{
			requestFailed(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
			activeChar.setActiveEnchantItem(null);
			activeChar.sendPacket(new EnchantResult(2, 0, 0));
			return;
		}
		activeChar.getInventory().updateInventory(scroll);
		
		// attempting to destroy support if exist
		if (support != null)
		{
			support = activeChar.getInventory().destroyItem("Enchant", support.getObjectId(), 1, activeChar, item);
			if (support == null)
			{
				requestFailed(SystemMessageId.NOT_ENOUGH_ITEMS);
				Util.handleIllegalPlayerAction(activeChar, "Player " + activeChar.getName()
						+ " tried to enchant with a support item he doesn't have", Config.DEFAULT_PUNISH);
				activeChar.setActiveEnchantItem(null);
				activeChar.sendPacket(new EnchantResult(2, 0, 0));
				return;
			}
			activeChar.getInventory().updateInventory(support);
		}
		
		synchronized (item)
		{
			int chance = scrollTemplate.getChance(item, supportTemplate, activeChar);
			
			// last validation check
			if (item.getOwnerId() != activeChar.getObjectId() || !isEnchantable(item) || chance < 0)
			{
				activeChar.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
				activeChar.setActiveEnchantItem(null);
				activeChar.sendPacket(new EnchantResult(2, 0, 0));
				return;
			}
			
			SystemMessage sm;
			
			if (scroll.getItemId() == 13540)
				if (item.getEnchantLevel() < 3)
					chance = 100;
				else
					chance = 65;
			
			if (Rnd.get(100) < chance)
			{
				// If chance is 100, the item must be below safe!
				// Otherwise we found the exploit
				if (_log.isDebugEnabled())
					_log.info("ENCHANT: Success enchanting " + item + ", chance used = " + chance);
				// success
				item.setEnchantLevel(item.getEnchantLevel() + 1);
				item.setLastChange(L2ItemInstance.MODIFIED);
				item.updateDatabase();
				activeChar.sendPacket(new EnchantResult(0, 0, 0));
				
				// Master of Enchanting event
				if (scroll.getItemId() == 13540 && item.getEnchantLevel() > 3)
				{
					L2Skill skill = SkillTable.getInstance().getInfo(5965, 1);
					if (skill != null)
					{
						MagicSkillUse MSU = new MagicSkillUse(activeChar, activeChar, 5965, 1, 1, 0);
						activeChar.sendPacket(MSU);
						activeChar.broadcastPacket(MSU);
						activeChar.useMagic(skill, false, false);
					}
				}
			}
			else
			{
				if (scrollTemplate.isSafe())
				{
					// safe enchant - remain old value
					// need retail message
					activeChar.sendPacket(new EnchantResult(5, 0, 0));
				}
				else
				{
					// unequip item on enchant failure to avoid item skills stack
					if (item.isEquipped())
					{
						if (item.getEnchantLevel() > 0)
						{
							sm = new SystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
							sm.addNumber(item.getEnchantLevel());
							sm.addItemName(item);
							sendPacket(sm);
						}
						else
						{
							sm = new SystemMessage(SystemMessageId.S1_DISARMED);
							sm.addItemName(item);
							sendPacket(sm);
						}
						L2ItemInstance[] unequiped =
								activeChar.getInventory().unEquipItemInSlotAndRecord(item.getLocationSlot());
						InventoryUpdate iu = new InventoryUpdate();
						for (L2ItemInstance element : unequiped)
							iu.addItem(element);
						sendPacket(iu);
						iu = null;
					}
					
					if (scrollTemplate.isBlessed())
					{
						// blessed enchant - clear enchant value
						activeChar.sendPacket(SystemMessageId.BLESSED_ENCHANT_FAILED);
						
						item.setEnchantLevel(0);
						item.updateDatabase();
						activeChar.sendPacket(new EnchantResult(3, 0, 0));
					}
					else
					{
						// enchant failed, destroy item
						int crystalId = item.getItem().getCrystalItemId();
						long count = item.getCrystalCount() - (item.getItem().getCrystalCount() + 1) / 2;
						if (count < 1)
							count = 1;
						
						L2ItemInstance destroyItem =
								activeChar.getInventory().destroyItem("Enchant", item, activeChar, null);
						if (destroyItem == null)
						{
							// unable to destroy item, cheater ?
							Util.handleIllegalPlayerAction(activeChar,
									"Unable to delete item on enchant failure from player " + activeChar.getName()
											+ ", possible cheater !", Config.DEFAULT_PUNISH);
							if (item.getLocation() != null)
								activeChar.getWarehouse().destroyItem("Enchant", item, activeChar, null);
							
							activeChar.setActiveEnchantItem(null);
							activeChar.sendPacket(new EnchantResult(2, 0, 0));
							return;
						}
						sm = new SystemMessage(SystemMessageId.S1_DISAPPEARED);
						sm.addItemName(destroyItem);
						sendPacket(sm);
						L2World.getInstance().removeObject(destroyItem);
						
						L2ItemInstance crystals = null;
						if (crystalId != 0)
						{
							crystals =
									activeChar.getInventory().addItem("Enchant", crystalId, count, activeChar,
											destroyItem);
							sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
							sm.addItemName(crystals);
							sm.addItemNumber(count);
							sendPacket(sm);
						}
						if (crystals != null)
						{
							activeChar.getInventory().updateInventory(crystals);
							activeChar.sendPacket(new EnchantResult(1, crystalId, count));
						}
						else
						{
							activeChar.sendPacket(new EnchantResult(4, 0, 0));
						}
					}
				}
			}
		}
		
		activeChar.getInventory().updateInventory(item);
		activeChar.broadcastUserInfo();
		activeChar.setActiveEnchantItem(null);
		
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTENCHANTITEM;
	}
}
