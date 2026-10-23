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

import com.l2jfree.gameserver.network.bridge.BridgeFact;
import com.l2jfree.gameserver.network.bridge.BridgeField;
import com.l2jfree.gameserver.network.bridge.FactSink;
import com.l2jfree.gameserver.network.bridge.SemanticPacket;

import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.L2Skill.SkillTargetType;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public final class SkillList extends L2ServerPacket implements SemanticPacket
{
	@Override
	public void describe(FactSink sink, com.l2jfree.gameserver.gameobjects.L2Player receiver)
	{
		var skills = new java.util.ArrayList<com.l2jfree.gameserver.network.bridge.BridgeValue>();
		for (L2Skill skill : _skills)
			skills.add(BridgeField.object(1, BridgeField.number(1, skill.getDisplayId()),
					BridgeField.number(2, skill.getLevel()), BridgeField.flag(3, skill.isPassive()),
					BridgeField.flag(4, skill.getSkillType() == L2SkillType.NOTDONE
							|| skill.getTargetType() == SkillTargetType.TARGET_NONE)).value());
		sink.emit(BridgeFact.of(23, BridgeField.list(30, skills), BridgeField.flag(31, true)));
	}

	private static final String _S__6D_SKILLLIST = "[S] 58 SkillList";
	
	private final L2Skill[] _skills;
	
	public SkillList(L2Player activeChar)
	{
		_skills = activeChar.getSortedAllSkills(activeChar.isGM() && !activeChar.isSubClassActive());
	}
	
	@Override
	public void packetSent(L2Client client, L2Player activeChar)
	{
		if (activeChar != null)
			activeChar.sendSkillCoolTime();
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0x5f);
		writeD(_skills.length);
		
		for (L2Skill s : _skills)
		{
			writeD(s.isPassive() ? 1 : 0);
			writeD(s.getLevel());
			writeD(s.getDisplayId());
			// as currently this isn't used in retail?
			// it will serve us better like this
			writeC(s.getSkillType() == L2SkillType.NOTDONE || s.getTargetType() == SkillTargetType.TARGET_NONE);
		}
	}
	
	@Override
	public String getType()
	{
		return _S__6D_SKILLLIST;
	}
}
