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
package com.l2jfree.gameserver.model.zone;

import org.apache.commons.lang3.ArrayUtils;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.Siege;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * @author Savormix
 * @since 2009-04-22
 */
public class L2SiegeDangerZone extends L2DamageZone
{
	private static final int SPEED_SKILL = 4625;
	private Siege _siege;
	
	@Override
	protected void checkForDamage(L2Creature character)
	{
		super.checkForDamage(character);
		
		if (getHPDamagePerSecond() > 0 && character instanceof L2Playable)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.C1_RECEIVED_DAMAGE_FROM_S2_THROUGH_FIRE_OF_MAGIC);
			sm.addCharName(character);
			sm.addNumber(getHPDamagePerSecond());
			character.getActingPlayer().sendPacket(sm);
		}
	}
	
	@Override
	protected boolean checkDynamicConditions(L2Creature character)
	{
		if (_siege == null || !_siege.getIsInProgress() || !(character instanceof L2Playable) || !isActive())
			return false;
		
		return super.checkDynamicConditions(character);
	}
	
	@Override
	protected void register() throws Exception
	{
		Castle c = CastleManager.getInstance().getCastleById(getCastleId());
		_siege = c.getSiege();
		c.loadDangerZone(this);
	}
	
	/** Activates this zone. */
	public void activate()
	{
		L2Skill s = SkillTable.getInstance().getInfo(SPEED_SKILL, 12);
		if (s != null)
		{
			if (_applyEnter == null)
				_applyEnter = new L2Skill[] { s };
			else
				_applyEnter = ArrayUtils.add(_applyEnter, s);
			_removeExit = ArrayUtils.add(_removeExit, SPEED_SKILL);
		}
		else
			_log.warn("Missing siege danger zone skill! " + SPEED_SKILL + " Lv 12");
	}
	
	/** Deactivates this zone. */
	public void deactivate()
	{
		_applyEnter = null;
		for (L2Creature c : getCharactersInside())
			removeFromZone(c);
		_removeExit = null;
	}
	
	public boolean isActive()
	{
		try
		{
			return _applyEnter[0].getLevel() > 0;
		}
		catch (NullPointerException npe)
		{
			return false;
		}
	}
}
