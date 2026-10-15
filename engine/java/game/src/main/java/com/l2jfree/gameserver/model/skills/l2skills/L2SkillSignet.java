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
package com.l2jfree.gameserver.model.skills.l2skills;

import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2EffectPointInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.templates.StatsSet;
import com.l2jfree.tools.geometry.Point3D;

/**
 * @author Forsaiken
 */
public final class L2SkillSignet extends L2Skill
{
	private final int _effectNpcId;
	private final int _effectId;
	
	public L2SkillSignet(StatsSet set)
	{
		super(set);
		_effectNpcId = set.getInteger("effectNpcId", -1);
		_effectId = set.getInteger("effectId", -1);
	}
	
	public int getSignetEffectId()
	{
		return _effectId;
	}
	
	@Override
	public void useSkill(L2Creature caster, L2Creature... targets)
	{
		if (caster.isAlikeDead())
			return;
		
		L2NpcTemplate template = NpcTable.getInstance().getTemplate(_effectNpcId);
		L2EffectPointInstance effectPoint =
				new L2EffectPointInstance(IdFactory.getInstance().getNextId(), template, caster);
		effectPoint.getStatus().setCurrentHp(effectPoint.getMaxHp());
		effectPoint.getStatus().setCurrentMp(effectPoint.getMaxMp());
		L2World.getInstance().storeObject(effectPoint);
		
		int x = caster.getX();
		int y = caster.getY();
		int z = caster.getZ();
		
		if (caster instanceof L2Player && getTargetType() == L2Skill.SkillTargetType.TARGET_GROUND)
		{
			Point3D wordPosition = ((L2Player)caster).getCurrentSkillWorldPosition();
			
			if (wordPosition != null)
			{
				x = wordPosition.getX();
				y = wordPosition.getY();
				z = wordPosition.getZ();
			}
		}
		getEffects(caster, effectPoint);
		
		effectPoint.setIsInvul(true);
		effectPoint.setInstanceId(caster.getInstanceId());
		effectPoint.spawnMe(x, y, z);
	}
}
