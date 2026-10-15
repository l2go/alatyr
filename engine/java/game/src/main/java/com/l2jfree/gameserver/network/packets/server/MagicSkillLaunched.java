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
package com.l2jfree.gameserver.network.packets.server;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class MagicSkillLaunched extends L2ServerPacket
{
	private static final String _S__MAGICSKILLLAUNCHED = "[S] 54 MagicSkillLaunched c[ddd (d)]";
	
	private final int _charObjId;
	private final int _skillId;
	private final int _skillLevel;
	private final L2Object[] _targets;
	
	public MagicSkillLaunched(L2Creature cha, int skillId, int skillLevel, L2Object... targets)
	{
		_charObjId = cha.getObjectId();
		_skillId = skillId;
		_skillLevel = skillLevel;
		if (targets == null) // how??!
		{
			_targets = new L2Object[0];
			_log.info("Invalid MSL construction!", new IllegalArgumentException());
		}
		else
			_targets = targets;
	}
	
	public MagicSkillLaunched(L2Creature cha, L2Skill skill, L2Object... targets)
	{
		this(cha, skill.getDisplayId(), skill.getLevel(), targets);
	}
	
	public MagicSkillLaunched(L2Creature cha, int skillId, int skillLevel)
	{
		this(cha, skillId, skillLevel, cha.getTarget());
	}
	
	public MagicSkillLaunched(L2Creature cha, L2Skill skill)
	{
		this(cha, skill.getDisplayId(), skill.getLevel(), cha.getTarget());
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0x54);
		writeD(_charObjId);
		writeD(_skillId);
		writeD(_skillLevel);
		writeD(_targets.length);
		
		if (_targets.length == 0)
			writeD(0);
		else
			for (L2Object target : _targets)
				writeD(target == null ? 0 : target.getObjectId());
	}
	
	@Override
	public String getType()
	{
		return _S__MAGICSKILLLAUNCHED;
	}
}
