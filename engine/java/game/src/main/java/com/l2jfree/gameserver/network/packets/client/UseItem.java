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
import com.l2jfree.gameserver.datatables.ShotTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.handler.ItemHandler;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Armor;
import com.l2jfree.gameserver.model.items.templates.L2ArmorType;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.ShowCalculator;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

public final class UseItem extends L2ClientPacket
{
	private static final String _C__USEITEM = "[C] 19 UseItem c[dd]";
	
	private int _objectId;
	
	//private int				_unk;
	
	/** Weapon Equip Task */
	public class WeaponEquipTask implements Runnable
	{
		L2ItemInstance item;
		L2Player activeChar;
		
		public WeaponEquipTask(L2ItemInstance it, L2Player character)
		{
			item = it;
			activeChar = character;
		}
		
		@Override
		public void run()
		{
			// Equip or unEquip
			activeChar.useEquippableItem(item, false);
		}
	}
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		/*_unk = */readD();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		// Flood protect UseItem
		if (!FloodProtector.tryPerformAction(activeChar, Protected.USEITEM))
			return;
		
		if (activeChar.getPrivateStoreType() != 0)
		{
			requestFailed(SystemMessageId.NOT_USE_ITEMS_IN_PRIVATE_STORE);
			return;
		}
		
		if (activeChar.getActiveTradeList() != null)
			activeChar.cancelActiveTrade();
		
		// NOTE: disabled due to deadlocks
		// synchronized (activeChar.getInventory())
		// 	{
		L2ItemInstance item = activeChar.getInventory().getItemByObjectId(_objectId);
		if (item == null)
		{
			sendAF();
			return;
		}
		
		if (item.isWear())
		{
			sendAF();
			return;
		}
		if (item.getItem().getType2() == L2Item.TYPE2_QUEST)
		{
			requestFailed(SystemMessageId.CANNOT_USE_QUEST_ITEMS);
			return;
		}
		int itemId = item.getItemId();
		/*
		 * Alt game - Karma punishment // SOE
		 * 736  	Scroll of Escape
		 * 1538  	Blessed Scroll of Escape
		 * 1829  	Scroll of Escape: Clan Hall
		 * 1830  	Scroll of Escape: Castle
		 * 3958  	L2Day - Blessed Scroll of Escape
		 * 5858  	Blessed Scroll of Escape: Clan Hall
		 * 5859  	Blessed Scroll of Escape: Castle
		 * 6663  	Scroll of Escape: Orc Village
		 * 6664  	Scroll of Escape: Silenos Village
		 * 7117  	Scroll of Escape to Talking Island
		 * 7118  	Scroll of Escape to Elven Village
		 * 7119  	Scroll of Escape to Dark Elf Village
		 * 7120  	Scroll of Escape to Orc Village
		 * 7121  	Scroll of Escape to Dwarven Village
		 * 7122  	Scroll of Escape to Gludin Village
		 * 7123  	Scroll of Escape to the Town of Gludio
		 * 7124  	Scroll of Escape to the Town of Dion
		 * 7125  	Scroll of Escape to Floran
		 * 7126  	Scroll of Escape to Giran Castle Town
		 * 7127  	Scroll of Escape to Hardin's Private Academy
		 * 7128  	Scroll of Escape to Heine
		 * 7129  	Scroll of Escape to the Town of Oren
		 * 7130  	Scroll of Escape to Ivory Tower
		 * 7131  	Scroll of Escape to Hunters Village
		 * 7132  	Scroll of Escape to Aden Castle Town
		 * 7133  	Scroll of Escape to the Town of Goddard
		 * 7134  	Scroll of Escape to the Rune Township
		 * 7135  	Scroll of Escape to the Town of Schuttgart.
		 * 7554  	Scroll of Escape to Talking Island
		 * 7555  	Scroll of Escape to Elven Village
		 * 7556  	Scroll of Escape to Dark Elf Village
		 * 7557  	Scroll of Escape to Orc Village
		 * 7558  	Scroll of Escape to Dwarven Village
		 * 7559  	Scroll of Escape to Giran Castle Town
		 * 7618  	Scroll of Escape - Ketra Orc Village
		 * 7619  	Scroll of Escape - Varka Silenos Village
		 * 10129    Scroll of Escape : Fortress
		 * 10130    Blessed Scroll of Escape : Fortress
		 */
		if (!Config.ALT_GAME_KARMA_PLAYER_CAN_TELEPORT && activeChar.getKarma() > 0)
		{
			switch (itemId)
			{
				case 736:
				case 1538:
				case 1829:
				case 1830:
				case 3958:
				case 5858:
				case 5859:
				case 6663:
				case 6664:
				case 7554:
				case 7555:
				case 7556:
				case 7557:
				case 7558:
				case 7559:
				case 7618:
				case 7619:
				case 10129:
				case 10130:
					sendAF();
					return;
			}
			if (itemId >= 7117 && itemId <= 7135)
			{
				sendAF();
				return;
			}
		}
		
		// Items that cannot be used
		if (itemId == PlayerInventory.ADENA_ID)
		{
			sendAF();
			return;
		}
		
		if (activeChar.isFishing() && !ShotTable.isFishingShot(itemId))
		{
			// You cannot do anything else while fishing
			requestFailed(SystemMessageId.CANNOT_DO_WHILE_FISHING_3);
			return;
		}
		
		if (!GlobalRestrictions.canUseItemHandler(null, itemId, activeChar, item))
			return;
		
		// Char cannot use item when dead
		if (activeChar.isDead())
		{
			requestFailed(new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addItemName(item));
			return;
		}
		
		// Char cannot use pet items
		if ((item.getItem() instanceof L2Armor && item.getItem().getItemType() == L2ArmorType.PET)
				|| (item.getItem() instanceof L2Weapon && item.getItem().getItemType() == L2WeaponType.PET))
		{
			requestFailed(SystemMessageId.CANNOT_EQUIP_PET_ITEM);
			return;
		}
		
		if (_log.isDebugEnabled())
			_log.info(activeChar.getObjectId() + ": use item " + _objectId);
		
		if (!item.isEquipped())
		{
			if (!item.getItem().checkCondition(activeChar, true))
			{
				sendAF();
				return;
			}
		}
		
		if (item.isEquipable())
		{
			// No unequipping/equipping while the player is in special conditions
			if (activeChar.isStunned() || activeChar.isSleeping() || activeChar.isParalyzed()
					|| activeChar.isAlikeDead())
			{
				sendAF();
				return;
			}
			
			// Don't allow hero equipment and restricted items during Olympiad
			if (activeChar.isInOlympiadMode() && (item.isHeroItem() || item.isOlyRestrictedItem()))
			{
				requestFailed(SystemMessageId.THIS_ITEM_CANT_BE_EQUIPPED_FOR_THE_OLYMPIAD_EVENT);
				return;
			}
			
			switch (item.getItem().getBodyPart())
			{
				case L2Item.SLOT_LR_HAND:
				case L2Item.SLOT_L_HAND:
				case L2Item.SLOT_R_HAND:
				{
					// prevent players to equip weapon while wearing combat flag
					if (activeChar.getActiveWeaponItem() != null
							&& activeChar.getActiveWeaponItem().getItemId() == 9819)
					{
						requestFailed(SystemMessageId.NO_CONDITION_TO_EQUIP);
						return;
					}
					// Prevent player to remove the weapon on special conditions
					else if (activeChar.isCastingNow() || activeChar.isCastingSimultaneouslyNow())
					{
						requestFailed(SystemMessageId.CANNOT_USE_ITEM_WHILE_USING_MAGIC);
						return;
					}
					else if (activeChar.isMounted() || activeChar.isDisarmed())
					{
						requestFailed(SystemMessageId.NO_CONDITION_TO_EQUIP);
						return;
					}
					// Don't allow weapon/shield equipment if a cursed weapon is equipped
					else if (activeChar.isCursedWeaponEquipped())
					{
						sendAF();
						return;
					}
					
					// Don't allow other Race to Wear Kamael exclusive Weapons.
					if (!item.isEquipped() && item.getItem() instanceof L2Weapon && !activeChar.isGM())
					{
						if (activeChar.isKamaelic())
						{
							if (item.getItemType() == L2WeaponType.NONE)
							{
								requestFailed(SystemMessageId.NO_CONDITION_TO_EQUIP);
								return;
							}
						}
						else if (item.getItemType() == L2WeaponType.CROSSBOW
								|| item.getItemType() == L2WeaponType.RAPIER
								|| item.getItemType() == L2WeaponType.ANCIENT_SWORD)
						{
							requestFailed(SystemMessageId.NO_CONDITION_TO_EQUIP);
							return;
						}
					}
					break;
				}
				case L2Item.SLOT_CHEST:
				case L2Item.SLOT_BACK:
				case L2Item.SLOT_GLOVES:
				case L2Item.SLOT_FEET:
				case L2Item.SLOT_HEAD:
				case L2Item.SLOT_FULL_ARMOR:
				case L2Item.SLOT_LEGS:
				{
					if (activeChar.isKamaelic()
							&& (item.getItem().getItemType() == L2ArmorType.HEAVY || item.getItem().getItemType() == L2ArmorType.MAGIC))
					{
						requestFailed(SystemMessageId.NO_CONDITION_TO_EQUIP);
						return;
					}
					break;
				}
			}
			
			// All talisman slots full. Verified.
			if (!item.isEquipped() && item.getItem().getBodyPart() == L2Item.SLOT_DECO)
			{
				if (activeChar.getInventory().getMaxTalismanCount() <= activeChar.getInventory()
						.getEquippedTalismanCount())
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.NO_SPACE_TO_WEAR_S1);
					sm.addItemName(item);
					requestFailed(sm);
					return;
				}
			}
			
			if (activeChar.isCursedWeaponEquipped() && itemId == 6408) // Don't allow to put formal wear
			{
				sendAF();
				return;
			}
			/*
			if (activeChar.isAttackingNow())
			{
				ThreadPoolManager.getInstance().scheduleGeneral(new WeaponEquipTask(item, activeChar),
					activeChar.getAttackEndTime() - L2System.milliTime());
				return;
			}
			*/
			// Equip or unEquip
			if (FortSiegeManager.getInstance().isCombat(item.getItemId()))
			{
				sendAF();
				return; //no message
			}
			activeChar.useEquippableItem(item, true);
		}
		else
		{
			L2Weapon weaponItem = activeChar.getActiveWeaponItem();
			int itemid = item.getItemId();
			if (itemid == 4393)
				activeChar.sendPacket(new ShowCalculator(4393));
			else if ((weaponItem != null && weaponItem.getItemType() == L2WeaponType.ROD)
					&& ((itemid >= 6519 && itemid <= 6527) || (itemid >= 7610 && itemid <= 7613)
							|| (itemid >= 7807 && itemid <= 7809) || (itemid >= 8484 && itemid <= 8486) || (itemid >= 8505 && itemid <= 8513)))
			{
				activeChar.getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, item);
				activeChar.broadcastUserInfo();
				// Send a Server->Client packet ItemList to this L2Player to update left hand equipement
				sendPacket(new ItemList(activeChar, false));
				sendPacket(new InventoryUpdate());
				return;
			}
			else
				ItemHandler.getInstance().useItem(item.getItemId(), activeChar, item, false);
		}
		//		}
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__USEITEM;
	}
}
