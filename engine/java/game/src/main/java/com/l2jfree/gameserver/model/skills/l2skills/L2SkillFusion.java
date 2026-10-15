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

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * @author NB4L1
 */
public final class L2SkillFusion extends L2Skill
{
	private final int _fusionTriggeredId;
	private final int _fusionTriggeredLvl;
	
	public L2SkillFusion(StatsSet set)
	{
		super(set);
		
		_fusionTriggeredId = set.getInteger("fusionTriggeredId");
		_fusionTriggeredLvl = set.getInteger("fusionTriggeredLevel");
	}
	
	@Override
	public void validate() throws Exception
	{
		super.validate();
		
		// must have fusion triggered skill
		if (getFusionTriggeredSkill() == null)
			throw new IllegalStateException(toString());
		
		// can't have triggered skill
		if (getTriggeredSkill() != null)
			throw new IllegalStateException(toString());
		
		// can't have effects
		if (getEffectTemplates() != null)
			throw new IllegalStateException(toString());
	}
	
	public L2Skill getFusionTriggeredSkill()
	{
		return SkillTable.getInstance().getInfo(_fusionTriggeredId, _fusionTriggeredLvl);
	}
}
