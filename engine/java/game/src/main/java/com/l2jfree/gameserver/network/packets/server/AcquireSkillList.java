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

import java.util.ArrayList;
import java.util.List;

import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class AcquireSkillList extends L2ServerPacket
{
	public enum SkillType
	{
		Usual, // 0
		Fishing, // 1
		Clan, // 2
		unk3,
		unk4,
		unk5,
		Collection // 6
	}
	
	private static final String _S__90_AQUIRESKILLLIST = "[S] 90 AquireSkillList [dd (ddddd)]";
	
	private final List<Skill> _skills;
	private final SkillType _fishingSkills;
	
	private class Skill
	{
		public int id;
		public int nextLevel;
		public int maxLevel;
		public int spCost;
		public int requirements;
		
		public Skill(int pId, int pNextLevel, int pMaxLevel, int pSpCost, int pRequirements)
		{
			id = pId;
			nextLevel = pNextLevel;
			maxLevel = pMaxLevel;
			spCost = pSpCost;
			requirements = pRequirements;
		}
	}
	
	public AcquireSkillList(SkillType type)
	{
		_skills = new ArrayList<Skill>();
		_fishingSkills = type;
	}
	
	public void addSkill(int id, int nextLevel, int maxLevel, int spCost, int requirements)
	{
		_skills.add(new Skill(id, nextLevel, maxLevel, spCost, requirements));
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x90);
		writeD(_fishingSkills.ordinal()); //c4 : C5 : 0: usuall  1: fishing 2: clans
		writeD(_skills.size());
		
		for (Skill temp : _skills)
		{
			writeD(temp.id);
			writeD(temp.nextLevel);
			writeD(temp.maxLevel);
			writeD(temp.spCost);
			writeD(temp.requirements);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__90_AQUIRESKILLLIST;
	}
}
