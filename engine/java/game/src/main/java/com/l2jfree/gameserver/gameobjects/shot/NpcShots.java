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

import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.tools.random.Rnd;

/**
 * @author NB4L1
 */
public final class NpcShots extends CreatureShots
{
	private ShotState _shotState;
	
	private boolean _shouldRecalculateSoulshot;
	private boolean _shouldRecalculateBlessedSpiritshot;
	
	private byte _soulshotRandom = 0;
	private byte _blessedSpiritshotRandom = 0;
	
	public NpcShots(L2Npc activeChar)
	{
		super(activeChar);
	}
	
	@Override
	protected L2Npc getActiveChar()
	{
		return (L2Npc)_activeChar;
	}
	
	@Override
	protected ShotState getShotState()
	{
		if (getActiveChar().getTemplate().getSSRate() == 0 || getActiveChar().getActiveWeaponItem() == null)
			return ShotState.getEmptyInstance();
		
		if (_shotState == null)
			_shotState = new ShotState();
		
		return _shotState;
	}
	
	@Override
	public void rechargeShots()
	{
		chargeSoulshot(null);
		chargeBlessedSpiritshot(null);
	}
	
	@Override
	protected boolean canChargeSoulshot(L2ItemInstance consume)
	{
		if (getActiveChar().getTemplate().getSSRate() <= _soulshotRandom)
			return false;
		
		L2Weapon weapon = getActiveChar().getActiveWeaponItem();
		
		if (getActiveChar().getInventory().destroyItemByItemId("Consume", 1835, weapon.getSoulShotCount(), null, null) == null)
			return false;
		
		getActiveChar().broadcastPacket(new MagicSkillUse(getActiveChar(), 2039, 1, 0, 0)); // No Grade
		return true;
	}
	
	@Override
	protected boolean canChargeBlessedSpiritshot(L2ItemInstance consume)
	{
		if (getActiveChar().getTemplate().getSSRate() <= _blessedSpiritshotRandom)
			return false;
		
		L2Weapon weapon = getActiveChar().getActiveWeaponItem();
		
		if (getActiveChar().getInventory()
				.destroyItemByItemId("Consume", 3947, weapon.getSpiritShotCount(), null, null) == null)
			return false;
		
		getActiveChar().broadcastPacket(new MagicSkillUse(getActiveChar(), 2061, 1, 0, 0)); // No Grade
		return true;
	}
	
	@Override
	public final void chargeSoulshot(L2ItemInstance consume)
	{
		if (_shouldRecalculateSoulshot)
		{
			_shouldRecalculateSoulshot = false;
			_soulshotRandom = (byte)Rnd.get(100);
		}
		
		super.chargeSoulshot(consume);
	}
	
	@Override
	public final void chargeBlessedSpiritshot(L2ItemInstance consume)
	{
		if (_shouldRecalculateBlessedSpiritshot)
		{
			_shouldRecalculateBlessedSpiritshot = false;
			_blessedSpiritshotRandom = (byte)Rnd.get(100);
		}
		
		super.chargeBlessedSpiritshot(consume);
	}
	
	@Override
	public void useSoulshotCharge()
	{
		super.useSoulshotCharge();
		
		_shouldRecalculateSoulshot = true;
	}
	
	@Override
	public void useBlessedSpiritshotCharge()
	{
		super.useBlessedSpiritshotCharge();
		
		_shouldRecalculateBlessedSpiritshot = true;
	}
}
