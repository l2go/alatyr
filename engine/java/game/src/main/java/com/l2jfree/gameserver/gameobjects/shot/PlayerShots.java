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
package com.l2jfree.gameserver.gameobjects.shot;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.l2jfree.gameserver.datatables.ShotTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.ItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ExAutoSoulShot;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.util.LazyFastSet;

/**
 * @author NB4L1
 */
public final class PlayerShots extends CreatureShots
{
	private static final Map<Integer, ShotInfo> SHOTS = new HashMap<Integer, ShotInfo>();
	
	private static final class ShotInfo
	{
		private final int _itemId;
		private final int _skillId;
		private final int _crystalType;
		
		public ShotInfo(int itemId, int skillId, int crystalType)
		{
			_itemId = itemId;
			_skillId = skillId;
			_crystalType = crystalType;
			
			SHOTS.put(getItemId(), this);
		}
		
		public int getItemId()
		{
			return _itemId;
		}
		
		public int getSkillId()
		{
			return _skillId;
		}
		
		public int getCrystalType()
		{
			return _crystalType;
		}
	}
	
	static
	{
		new ShotInfo(1463, 2150, L2Item.CRYSTAL_D); // Soulshot: D-grade
		new ShotInfo(1464, 2151, L2Item.CRYSTAL_C); // Soulshot: C-grade
		new ShotInfo(1465, 2152, L2Item.CRYSTAL_B); // Soulshot: B-grade
		new ShotInfo(1466, 2153, L2Item.CRYSTAL_A); // Soulshot: A-grade
		new ShotInfo(1467, 2154, L2Item.CRYSTAL_S); // Soulshot: S-grade
		new ShotInfo(1835, 2039, L2Item.CRYSTAL_NONE); // Soulshot: No Grade
		
		new ShotInfo(2509, 2061, L2Item.CRYSTAL_NONE); // Spiritshot: No Grade
		new ShotInfo(2510, 2155, L2Item.CRYSTAL_D); // Spiritshot: D-grade
		new ShotInfo(2511, 2156, L2Item.CRYSTAL_C); // Spiritshot: C-grade
		new ShotInfo(2512, 2157, L2Item.CRYSTAL_B); // Spiritshot: B-grade
		new ShotInfo(2513, 2158, L2Item.CRYSTAL_A); // Spiritshot: A-grade
		new ShotInfo(2514, 2159, L2Item.CRYSTAL_S); // Spiritshot: S-grade
		
		new ShotInfo(3947, 2061, L2Item.CRYSTAL_NONE); // Blessed Spiritshot: No Grade
		new ShotInfo(3948, 2160, L2Item.CRYSTAL_D); // Blessed Spiritshot: D-grade
		new ShotInfo(3949, 2161, L2Item.CRYSTAL_C); // Blessed Spiritshot: C-grade
		new ShotInfo(3950, 2162, L2Item.CRYSTAL_B); // Blessed Spiritshot: B-grade
		new ShotInfo(3951, 2163, L2Item.CRYSTAL_A); // Blessed Spiritshot: A-grade
		new ShotInfo(3952, 2164, L2Item.CRYSTAL_S); // Blessed Spiritshot: S-grade
		
		new ShotInfo(5789, 2039, L2Item.CRYSTAL_NONE); // Soulshot: No Grade for Beginners
		new ShotInfo(5790, 2061, L2Item.CRYSTAL_NONE); // Spiritshot: No Grade for Beginners
		
		new ShotInfo(6535, 2181, L2Item.CRYSTAL_NONE); // Fishing Shot: No Grade
		new ShotInfo(6536, 2182, L2Item.CRYSTAL_D); // Fishing Shot: D-grade
		new ShotInfo(6537, 2183, L2Item.CRYSTAL_C); // Fishing Shot: C-grade
		new ShotInfo(6538, 2184, L2Item.CRYSTAL_B); // Fishing Shot: B-grade
		new ShotInfo(6539, 2185, L2Item.CRYSTAL_A); // Fishing Shot: A-grade
		new ShotInfo(6540, 2186, L2Item.CRYSTAL_S); // Fishing Shot: S-grade
		
		new ShotInfo(22072, 26050, L2Item.CRYSTAL_D); // Blessed Spiritshot - D-grade
		new ShotInfo(22073, 26051, L2Item.CRYSTAL_C); // Blessed Spiritshot - C-grade
		new ShotInfo(22074, 26052, L2Item.CRYSTAL_B); // Blessed Spiritshot - B-grade
		new ShotInfo(22075, 26053, L2Item.CRYSTAL_A); // Blessed Spiritshot - A-grade
		new ShotInfo(22076, 26054, L2Item.CRYSTAL_S); // Blessed Spiritshot - S-grade
		
		new ShotInfo(22077, 26055, L2Item.CRYSTAL_D); // Spiritshot - D-grade
		new ShotInfo(22078, 26056, L2Item.CRYSTAL_C); // Spiritshot - C-grade
		new ShotInfo(22079, 26057, L2Item.CRYSTAL_B); // Spiritshot - B-grade
		new ShotInfo(22080, 26058, L2Item.CRYSTAL_A); // Spiritshot - A-grade
		new ShotInfo(22081, 26059, L2Item.CRYSTAL_S); // Spiritshot - S-grade
		
		new ShotInfo(22082, 26060, L2Item.CRYSTAL_D); // Soulshot - D-grade
		new ShotInfo(22083, 26061, L2Item.CRYSTAL_C); // Soulshot - C-grade
		new ShotInfo(22084, 26062, L2Item.CRYSTAL_B); // Soulshot - B-grade
		new ShotInfo(22085, 26063, L2Item.CRYSTAL_A); // Soulshot - A-grade
		new ShotInfo(22086, 26064, L2Item.CRYSTAL_S); // Soulshot - S-grade
	}
	
	private final Set<Integer> _activeSoulShots = new LazyFastSet<Integer>().setShared();
	
	public PlayerShots(L2Player activeChar)
	{
		super(activeChar);
	}
	
	public void addAutoSoulShot(int itemId)
	{
		if (_activeSoulShots.add(itemId))
		{
			getActiveChar().sendPacket(new ExAutoSoulShot(itemId, 1));
			getActiveChar().sendPacket(new SystemMessage(SystemMessageId.USE_OF_S1_WILL_BE_AUTO).addItemName(itemId));
		}
	}
	
	public void removeAutoSoulShot(int itemId)
	{
		if (_activeSoulShots.remove(itemId))
		{
			getActiveChar().sendPacket(new ExAutoSoulShot(itemId, 0));
			getActiveChar().sendPacket(new SystemMessage(SystemMessageId.AUTO_USE_OF_S1_CANCELLED).addItemName(itemId));
		}
	}
	
	public boolean hasAutoSoulShot(int itemId)
	{
		return _activeSoulShots.contains(itemId);
	}
	
	public Set<Integer> getAutoSoulShots()
	{
		return _activeSoulShots;
	}
	
	@Override
	protected L2Player getActiveChar()
	{
		return (L2Player)_activeChar;
	}
	
	@Override
	protected ShotState getShotState()
	{
		L2ItemInstance weaponInst = getActiveChar().getActiveWeaponInstance();
		
		if (weaponInst != null)
			return weaponInst.getShotState();
		
		return ShotState.getEmptyInstance();
	}
	
	@Override
	public void rechargeShots()
	{
		/**
		 * Clears shot state, if it was marked to be recharged.<br>
		 * This has to be done to avoid being charged forever, because we don't call the recharge directly.<br>
		 * It's called through itemhandlers, and that's called only if the shot is automatic.
		 */
		chargeSoulshot(null);
		chargeSpiritshot(null);
		chargeBlessedSpiritshot(null);
		chargeFishshot(null);
		
		for (int itemId : getAutoSoulShots())
		{
			if (ShotTable.isPcShot(itemId))
			{
				L2ItemInstance item = getActiveChar().getInventory().getItemByItemId(itemId);
				
				ItemHandler.getInstance().useItem(itemId, getActiveChar(), item);
				
				if (item == null)
					removeAutoSoulShot(itemId);
			}
		}
	}
	
	@Override
	protected boolean canChargeSoulshot(L2ItemInstance item)
	{
		L2Weapon weaponItem = getActiveChar().getActiveWeaponItem();
		
		int saSSCount = (int)getActiveChar().getStat().calcStat(Stats.SOULSHOT_COUNT, 0, null, null);
		
		if (!canCharge(ShotType.SOUL, weaponItem, item, saSSCount == 0 ? weaponItem.getSoulShotCount() : saSSCount))
			return false;
		
		if (saSSCount > 0)
			getActiveChar().sendMessage("Miser consumed only " + saSSCount + " soulshots.");
		
		return true;
	}
	
	@Override
	protected boolean canChargeSpiritshot(L2ItemInstance item)
	{
		L2Weapon weaponItem = getActiveChar().getActiveWeaponItem();
		
		return canCharge(ShotType.SPIRIT, weaponItem, item, weaponItem.getSpiritShotCount());
	}
	
	@Override
	protected boolean canChargeBlessedSpiritshot(L2ItemInstance item)
	{
		L2Weapon weaponItem = getActiveChar().getActiveWeaponItem();
		
		return canCharge(ShotType.BLESSED_SPIRIT, weaponItem, item, weaponItem.getSpiritShotCount());
	}
	
	@Override
	protected boolean canChargeFishshot(L2ItemInstance item)
	{
		L2Weapon weaponItem = getActiveChar().getActiveWeaponItem();
		
		if (weaponItem.getItemType() != L2WeaponType.ROD)
			return false;
		
		return canCharge(ShotType.FISH, weaponItem, item, 1);
	}
	
	private boolean canCharge(ShotType type, L2Weapon weapon, L2ItemInstance item, int count)
	{
		if (item == null)
			return false;
		
		L2Player activeChar = getActiveChar();
		
		// Since CT2.3, Blessed Shots can be used in Olympiad.
		/*
		if (type == ShotType.BLESSED_SPIRIT)
		{
			// Blessed Spiritshot cannot be used in olympiad.
			if (activeChar.isInOlympiadMode())
			{
				activeChar.sendPacket(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
				return false;
			}
		}
		*/
		
		if (count == 0)
		{
			if (!hasAutoSoulShot(item.getItemId()))
			{
				if (type == ShotType.SOUL)
					activeChar.sendPacket(SystemMessageId.CANNOT_USE_SOULSHOTS);
				else if (type != ShotType.FISH)
					activeChar.sendPacket(SystemMessageId.CANNOT_USE_SPIRITSHOTS);
			}
			
			return false;
		}
		
		final ShotInfo shotInfo = SHOTS.get(item.getItemId());
		
		if (shotInfo == null || shotInfo.getCrystalType() != weapon.getCrystalGrade())
		{
			if (!hasAutoSoulShot(item.getItemId()))
			{
				if (type == ShotType.SOUL)
					activeChar.sendPacket(SystemMessageId.SOULSHOTS_GRADE_MISMATCH);
				else if (type == ShotType.FISH)
					activeChar.sendPacket(SystemMessageId.WRONG_FISHINGSHOT_GRADE);
				else
					activeChar.sendPacket(SystemMessageId.SPIRITSHOTS_GRADE_MISMATCH);
			}
			
			return false;
		}
		
		if (!activeChar.destroyItemWithoutTrace("Consume", item.getObjectId(), count, null, false))
		{
			if (type == ShotType.SOUL)
				activeChar.sendPacket(SystemMessageId.NOT_ENOUGH_SOULSHOTS);
			else if (type != ShotType.FISH)
				activeChar.sendPacket(SystemMessageId.NOT_ENOUGH_SPIRITSHOTS);
			
			removeAutoSoulShot(item.getItemId());
			return false;
		}
		
		if (type == ShotType.SOUL)
			activeChar.sendPacket(SystemMessageId.ENABLED_SOULSHOT);
		else if (type != ShotType.FISH)
			activeChar.sendPacket(SystemMessageId.ENABLED_SPIRITSHOT);
		
		activeChar.broadcastPacket(new MagicSkillUse(activeChar, shotInfo.getSkillId(), 1, 0, 0));
		return true;
	}
}
