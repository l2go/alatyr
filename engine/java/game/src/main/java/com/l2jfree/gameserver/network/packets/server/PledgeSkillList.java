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

import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public class PledgeSkillList extends L2ServerPacket
{
	private static final String _S__PLEDGESKILLLIST = "[S] FE:3A PledgeSkillList chd0[dd]";
	private final L2Clan _clan;
	
	public PledgeSkillList(L2Clan clan)
	{
		_clan = clan;
	}
	
	@Override
	protected void writeImpl()
	{
		L2Skill[] skills = _clan.getAllSkills();
		writeC(0xfe);
		writeH(0x3a);
		writeD(skills.length);
		writeD(0x00);
		for (L2Skill sk : skills)
		{
			writeD(sk.getId());
			writeD(sk.getLevel());
		}
	}
	
	@Override
	public String getType()
	{
		return _S__PLEDGESKILLLIST;
	}
}
