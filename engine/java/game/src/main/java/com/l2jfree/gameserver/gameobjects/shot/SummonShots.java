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

import com.l2jfree.gameserver.datatables.ShotTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.handler.ItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;

/**
 * @author NB4L1
 */
public final class SummonShots extends CreatureShots
{
	private ShotState _shotState;
	
	public SummonShots(L2Summon activeChar)
	{
		super(activeChar);
	}
	
	@Override
	protected L2Summon getActiveChar()
	{
		return (L2Summon)_activeChar;
	}
	
	@Override
	protected ShotState getShotState()
	{
		final L2ItemInstance weaponInst = getActiveChar().getActiveWeaponInstance();
		
		if (weaponInst != null)
			return weaponInst.getShotState();
		
		// pet shots should be used even if pet is without weapon
		if (_shotState == null)
			_shotState = new ShotState();
		
		return _shotState;
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
		
		for (int itemId : getActiveChar().getOwner().getShots().getAutoSoulShots())
		{
			if (ShotTable.isBeastShot(itemId))
			{
				L2ItemInstance item = getActiveChar().getOwner().getInventory().getItemByItemId(itemId);
				
				ItemHandler.getInstance().useItem(itemId, getActiveChar().getOwner(), item);
				
				if (item == null)
					getActiveChar().getOwner().getShots().removeAutoSoulShot(itemId);
			}
		}
	}
	
	@Override
	protected boolean canChargeSoulshot(L2ItemInstance item)
	{
		return canCharge(ShotType.SOUL, item);
	}
	
	@Override
	protected boolean canChargeSpiritshot(L2ItemInstance item)
	{
		return canCharge(ShotType.SPIRIT, item);
	}
	
	@Override
	protected boolean canChargeBlessedSpiritshot(L2ItemInstance item)
	{
		return canCharge(ShotType.BLESSED_SPIRIT, item);
	}
	
	private boolean canCharge(ShotType type, L2ItemInstance item)
	{
		L2Player activeOwner = getActiveChar().getOwner();
		
		if (item == null || activeOwner == null)
			return false;
		
		L2Summon activePet = getActiveChar();
		
		if (activePet.isDead())
		{
			activeOwner.sendPacket(SystemMessageId.SOULSHOTS_AND_SPIRITSHOTS_ARE_NOT_AVAILABLE_FOR_A_DEAD_PET);
			return false;
		}
		
		// Since CT2.3, Blessed Shots can be used in Olympiad.
		/*
		if (type == ShotType.BLESSED_SPIRIT)
		{
			// Blessed Beast Spirit Shot cannot be used in olympiad.
			if (activeOwner.isInOlympiadMode())
			{
				activeOwner.sendPacket(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
				return false;
			}
		}
		*/
		
		int shotConsumption = 1; // TODO: this should be readed from npc.sql(summons)/pets_stats.sql tables
		
		if (activePet.getActiveWeaponItem() != null)
		{
			if (type == ShotType.SOUL)
				shotConsumption = activePet.getActiveWeaponItem().getSoulShotCount();
			else
				shotConsumption = activePet.getActiveWeaponItem().getSpiritShotCount();
			
			if (shotConsumption == 0)
			{
				if (type == ShotType.SOUL)
					activeOwner.sendPacket(SystemMessageId.CANNOT_USE_SOULSHOTS);
				else
					activeOwner.sendPacket(SystemMessageId.CANNOT_USE_SPIRITSHOTS);
				
				return false;
			}
		}
		
		if (!activeOwner.destroyItemWithoutTrace("Consume", item.getObjectId(), shotConsumption, null, false))
		{
			if (type == ShotType.SOUL)
				activeOwner.sendPacket(SystemMessageId.NOT_ENOUGH_SOULSHOTS_FOR_PET);
			else
				activeOwner.sendPacket(SystemMessageId.NOT_ENOUGH_SPIRITHOTS_FOR_PET);
			
			activeOwner.getShots().removeAutoSoulShot(item.getItemId());
			return false;
		}
		
		activeOwner.sendPacket(SystemMessageId.PET_USE_THE_POWER_OF_SPIRIT);
		
		if (type == ShotType.SOUL)
			activeOwner.broadcastPacket(new MagicSkillUse(activePet, item.getItemId() == 6645 ? 2033 : 22036, 1, 0, 0));
		else if (type == ShotType.SPIRIT)
			activeOwner.broadcastPacket(new MagicSkillUse(activePet, item.getItemId() == 6646 ? 2008 : 22037, 1, 0, 0));
		else if (type == ShotType.BLESSED_SPIRIT)
			activeOwner.broadcastPacket(new MagicSkillUse(activePet, item.getItemId() == 6647 ? 2009 : 22038, 1, 0, 0));
		
		return true;
	}
}
