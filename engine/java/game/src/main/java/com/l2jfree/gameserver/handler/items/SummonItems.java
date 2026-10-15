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
package com.l2jfree.gameserver.handler.items;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SummonItemsData;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.ClanHallManager;
import com.l2jfree.gameserver.model.L2SummonItem;
import com.l2jfree.gameserver.model.entity.ClanHall;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.PetItemList;
import com.l2jfree.gameserver.util.Broadcast;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;

/**
 * 
 * @author FBIagent
 * 
 */
public class SummonItems implements IItemHandler
{
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		if (!(playable instanceof L2Player))
			return;
		
		final L2Player activeChar = (L2Player)playable;
		
		if (!FloodProtector.tryPerformAction(activeChar, Protected.ITEMPETSUMMON))
			return;
		
		if (activeChar.isSitting())
		{
			activeChar.sendPacket(SystemMessageId.CANT_MOVE_SITTING);
			return;
		}
		
		if (activeChar.inObserverMode())
			return;
		
		if (activeChar.isAllSkillsDisabled() || activeChar.isCastingNow())
			return;
		
		final L2SummonItem sitem = SummonItemsData.getInstance().getSummonItem(item.getItemId());
		
		if ((activeChar.getPet() != null || activeChar.isMounted()) && sitem.isPetSummon())
		{
			activeChar.sendPacket(SystemMessageId.YOU_ALREADY_HAVE_A_PET);
			return;
		}
		
		if (activeChar.isAttackingNow())
		{
			activeChar.sendPacket(SystemMessageId.YOU_CANNOT_SUMMON_IN_COMBAT);
			return;
		}
		
		if (activeChar.isCursedWeaponEquipped() && sitem.isPetSummon())
		{
			activeChar.sendPacket(SystemMessageId.STRIDER_CANT_BE_RIDDEN_WHILE_IN_BATTLE);
			return;
		}
		
		final int npcID = sitem.getNpcId();
		
		if (npcID == 0)
			return;
		
		final L2NpcTemplate npcTemplate = NpcTable.getInstance().getTemplate(npcID);
		
		if (npcTemplate == null)
			return;
		
		activeChar.stopMove(null, false);
		
		// Restricting Red Striders/Snow Wolves/Snow Fenrir
		if (!Config.ALT_SPECIAL_PETS_FOR_ALL)
		{
			int _itemId = item.getItemId();
			if ((_itemId == 10307 || _itemId == 10611 || _itemId == 10308 || _itemId == 10309 || _itemId == 10310)
					&& !activeChar.isGM())
			{
				if (activeChar.getClan() != null
						&& ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan()) != null)
				{
					ClanHall clanHall = ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan());
					
					int clanHallId = clanHall.getId();
					if ((clanHallId < 36 || clanHallId > 41) && (clanHallId < 51 || clanHallId > 57))
					{
						activeChar
								.sendMessage("Cannot use special pets if you're not member of a clan that is owning a clanhall in Aden or Rune");
						return;
					}
				}
				else
				{
					activeChar
							.sendMessage("Cannot use special pets if you're not member of a clan that is owning a clanhall in Aden or Rune");
					return;
				}
			}
		}
		
		switch (sitem.getType())
		{
			case 0: // Static Summons (like christmas tree)
				final L2Spawn spawn = new L2Spawn(npcTemplate);
				
				spawn.setId(IdFactory.getInstance().getNextId());
				spawn.setLocx(activeChar.getX());
				spawn.setLocy(activeChar.getY());
				spawn.setLocz(activeChar.getZ());
				L2World.getInstance().storeObject(spawn.spawnOne(true));
				activeChar.destroyItem("Summon", item.getObjectId(), 1, null, false);
				activeChar.sendMessage("Created " + npcTemplate.getName() + " at x: " + spawn.getLocx() + " y: "
						+ spawn.getLocy() + " z: " + spawn.getLocz());
				break;
			case 1: // Pet Summons
				Broadcast.toSelfAndKnownPlayersInRadius(activeChar, new MagicSkillUse(activeChar, activeChar, 2046, 1,
						5000, 0), 2000);
				
				activeChar.sendPacket(SystemMessageId.SUMMON_A_PET);
				
				activeChar.setSkillCast(new PetSummonFinalizer(activeChar, npcTemplate, item), 5000);
				break;
			case 2: // Wyvern
				activeChar.mount(sitem.getNpcId(), item.getObjectId(), true);
				break;
			case 3: // Great Wolf
				activeChar.mount(sitem.getNpcId(), item.getObjectId(), false);
				break;
			case 4: // Light Purple Maned Horse
				activeChar.mount(sitem.getNpcId(), item.getObjectId(), false);
				break;
		}
	}
	
	static class PetSummonFeedWait implements Runnable
	{
		private final L2Player _activeChar;
		private final L2PetInstance _petSummon;
		
		PetSummonFeedWait(L2Player activeChar, L2PetInstance petSummon)
		{
			_activeChar = activeChar;
			_petSummon = petSummon;
		}
		
		@Override
		public void run()
		{
			if (_petSummon.getCurrentFed() <= 0)
				_petSummon.unSummon(_activeChar);
			else
				_petSummon.startFeed();
		}
	}
	
	// TODO: this should be inside skill handler
	static class PetSummonFinalizer implements Runnable
	{
		private final L2Player _activeChar;
		private final L2ItemInstance _item;
		private final L2NpcTemplate _npcTemplate;
		
		PetSummonFinalizer(L2Player activeChar, L2NpcTemplate npcTemplate, L2ItemInstance item)
		{
			_activeChar = activeChar;
			_npcTemplate = npcTemplate;
			_item = item;
		}
		
		@Override
		public void run()
		{
			_activeChar.sendPacket(new MagicSkillLaunched(_activeChar, 2046, 1));
			
			// check for summon item validity
			if (_item == null || _item.getOwnerId() != _activeChar.getObjectId()
					|| _item.getLocation() != L2ItemInstance.ItemLocation.INVENTORY)
				return;
			
			final L2PetInstance petSummon = L2PetInstance.spawnPet(_npcTemplate, _activeChar, _item);
			
			if (petSummon == null)
				return;
			
			petSummon.setTitle(_activeChar.getName());
			
			if (!petSummon.isRespawned())
			{
				petSummon.getStatus().setCurrentHp(petSummon.getMaxHp());
				petSummon.getStatus().setCurrentMp(petSummon.getMaxMp());
				petSummon.getStat().setExp(petSummon.getExpForThisLevel());
				petSummon.setCurrentFed(petSummon.getMaxFed());
			}
			
			petSummon.setRunning();
			
			if (!petSummon.isRespawned())
				petSummon.store();
			
			_activeChar.setPet(petSummon);
			
			L2World.getInstance().storeObject(petSummon);
			petSummon.spawnMe(_activeChar.getX() + 50, _activeChar.getY() + 100, _activeChar.getZ());
			petSummon.startFeed();
			_item.setEnchantLevel(petSummon.getLevel());
			
			if (petSummon.getCurrentFed() <= 0)
				ThreadPoolManager.getInstance().scheduleGeneral(new PetSummonFeedWait(_activeChar, petSummon), 60000);
			else
				petSummon.startFeed();
			
			petSummon.setFollowStatus(true);
			petSummon.setShowSummonAnimation(false); // shouldn't be this always true?
			final int weaponId = petSummon.getWeapon();
			final int armorId = petSummon.getArmor();
			final int jewelId = petSummon.getJewel();
			if (weaponId > 0 && petSummon.getOwner().getInventory().getItemByItemId(weaponId) != null)
			{
				final L2ItemInstance item = petSummon.getOwner().getInventory().getItemByItemId(weaponId);
				final L2ItemInstance newItem =
						petSummon.getOwner().transferItem("Transfer", item.getObjectId(), 1, petSummon.getInventory(),
								petSummon);
				if (newItem == null)
				{
					_log.warn("Invalid item transfer request: " + petSummon.getName() + "(pet) --> "
							+ petSummon.getOwner().getName());
					petSummon.setWeapon(0);
				}
				else
					petSummon.getInventory().equipItem(newItem);
			}
			else
				petSummon.setWeapon(0);
			if (armorId > 0 && petSummon.getOwner().getInventory().getItemByItemId(armorId) != null)
			{
				final L2ItemInstance item = petSummon.getOwner().getInventory().getItemByItemId(armorId);
				final L2ItemInstance newItem =
						petSummon.getOwner().transferItem("Transfer", item.getObjectId(), 1, petSummon.getInventory(),
								petSummon);
				if (newItem == null)
				{
					_log.warn("Invalid item transfer request: " + petSummon.getName() + "(pet) --> "
							+ petSummon.getOwner().getName());
					petSummon.setArmor(0);
				}
				else
					petSummon.getInventory().equipItem(newItem);
			}
			else
				petSummon.setArmor(0);
			if (jewelId > 0 && petSummon.getOwner().getInventory().getItemByItemId(jewelId) != null)
			{
				final L2ItemInstance item = petSummon.getOwner().getInventory().getItemByItemId(jewelId);
				final L2ItemInstance newItem =
						petSummon.getOwner().transferItem("Transfer", item.getObjectId(), 1, petSummon.getInventory(),
								petSummon);
				if (newItem == null)
				{
					_log.warn("Invalid item transfer request: " + petSummon.getName() + "(pet) --> "
							+ petSummon.getOwner().getName());
					petSummon.setJewel(0);
				}
				else
					petSummon.getInventory().equipItem(newItem);
			}
			else
				petSummon.setJewel(0);
			petSummon.getOwner().sendPacket(new PetItemList(petSummon));
			petSummon.broadcastStatusUpdate();
		}
	}
	
	@Override
	public int[] getItemIds()
	{
		return SummonItemsData.getInstance().itemIDs();
	}
}
