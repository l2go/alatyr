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

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class GMViewSkillInfo extends L2ServerPacket
{
	private static final String _S__91_GMViewSkillInfo = "[S] 91 GMViewSkillInfo";
	
	private final L2Player _activeChar;
	private final L2Skill[] _skills;
	
	public GMViewSkillInfo(L2Player activeChar)
	{
		_activeChar = activeChar;
		_skills = activeChar.getSortedAllSkills(true);
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0x97);
		writeS(_activeChar.getName());
		writeD(_skills.length);
		
		for (L2Skill skill : _skills)
		{
			writeD(skill.isPassive() ? 1 : 0);
			writeD(skill.getLevel());
			writeD(skill.getDisplayId());
			writeC(0x00); //c5
		}
	}
	
	@Override
	public String getType()
	{
		return _S__91_GMViewSkillInfo;
	}
}
