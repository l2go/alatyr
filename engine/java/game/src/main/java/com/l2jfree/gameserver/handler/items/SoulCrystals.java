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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Attackable;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.handler.IItemHandler;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;

/**
 * This class ...
 * 
 * @version $Revision: 1.2.4 $ $Date: 2005/08/14 21:31:07 $
 */

public class SoulCrystals implements IItemHandler
{
	protected static Logger _log = LoggerFactory.getLogger(SoulCrystals.class);
	
	// First line is for Red Soul Crystals, second is Green and third is Blue Soul Crystals,
	// ordered by ascending level, from 0 to 16...
	private static final int[] ITEM_IDS = { 4629, 4630, 4631, 4632, 4633, 4634, 4635, 4636, 4637, 4638, 4639, 5577,
			5580, 5908, 9570, 4640, 4641, 4642, 4643, 4644, 4645, 4646, 4647, 4648, 4649, 4650, 5578, 5581, 5911, 9572,
			4651, 4652, 4653, 4654, 4655, 4656, 4657, 4658, 4659, 4660, 4661, 5579, 5582, 5914, 9570, 9571, 9572,
			10480, 10481, 10482, 13071, 13072, 13073 };
	
	// Our main method, where everything goes on
	@Override
	public void useItem(L2Playable playable, L2ItemInstance item)
	{
		if (!(playable instanceof L2Player))
			return;
		
		L2Player activeChar = (L2Player)playable;
		L2Object target = activeChar.getTarget();
		if (!(target instanceof L2MonsterInstance))
		{
			// Send a System Message to the caster
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			
			// Send a Server->Client packet ActionFailed to the L2Player
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			
			return;
		}
		
		// You can use soul crystal only when target hp goes below <50%
		if (((L2MonsterInstance)target).getStatus().getCurrentHp() > ((L2MonsterInstance)target).getMaxHp() / 2.0)
		{
			activeChar.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		int crystalId = item.getItemId();
		
		// Soul Crystal Casting section
		L2Skill skill = SkillTable.getInstance().getInfo(2096, 1);
		activeChar.useMagic(skill, false, true);
		// End Soul Crystal Casting section
		
		// Continue execution later
		CrystalFinalizer cf = new CrystalFinalizer(activeChar, target, crystalId);
		ThreadPoolManager.getInstance().scheduleEffect(cf, skill.getHitTime());
		
	}
	
	// TODO: this should be inside skill handler
	static class CrystalFinalizer implements Runnable
	{
		private final L2Player _activeChar;
		private final L2Attackable _target;
		private final int _crystalId;
		
		CrystalFinalizer(L2Player activeChar, L2Object target, int crystalId)
		{
			_activeChar = activeChar;
			_target = (L2Attackable)target;
			_crystalId = crystalId;
		}
		
		@Override
		public void run()
		{
			if (_activeChar.isDead() || _target.isDead())
				return;
			_activeChar.enableAllSkills();
			
			_target.addAbsorber(_activeChar, _crystalId);
			_activeChar.setTarget(_target);
		}
	}
	
	@Override
	public int[] getItemIds()
	{
		return ITEM_IDS;
	}
}
