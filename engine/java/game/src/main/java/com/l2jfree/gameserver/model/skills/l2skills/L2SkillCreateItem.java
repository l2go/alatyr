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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.templates.StatsSet;
import com.l2jfree.tools.random.Rnd;

/**
 * @author Nemesiss
 */
public class L2SkillCreateItem extends L2Skill
{
	private final int[] _createItemId;
	private final int _createItemCount;
	private final int _randomCount;
	
	public L2SkillCreateItem(StatsSet set)
	{
		super(set);
		_createItemId = set.getIntegerArray("create_item_id");
		_createItemCount = set.getInteger("create_item_count");
		_randomCount = set.getInteger("random_count", 0);
	}
	
	@Override
	public void useSkill(L2Creature activeChar, L2Creature... targets)
	{
		if (activeChar.isAlikeDead())
			return;
		
		if (activeChar instanceof L2Player)
		{
			int count = _createItemCount + Rnd.nextInt(_randomCount);
			int rndid = Rnd.nextInt(_createItemId.length);
			((L2Player)activeChar).addItem("Skill", _createItemId[rndid], count, activeChar, true);
		}
	}
}
