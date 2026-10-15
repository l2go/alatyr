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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.restriction.AvailableRestriction;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.SkillUsageRequest;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

public class RequestMagicSkillUse extends L2ClientPacket
{
	private static final String _C__REQUESTMAGICSKILLUSE = "[C] 39 RequestMagicSkillUse c[ddc]";
	
	private int _skillId;
	private boolean _ctrl;
	private boolean _shift;
	
	@Override
	protected void readImpl()
	{
		_skillId = readD(); // display ID
		_ctrl = readD() != 0;
		_shift = readC() != 0;
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		// removes spawn protection
		activeChar.onActionRequest();
		
		if (ObjectRestrictions.getInstance().checkRestriction(activeChar, AvailableRestriction.PlayerCast))
		{
			activeChar.sendMessage("You cannot cast a skill due to a restriction.");
			return;
		}
		
		// Get the level of the used skill
		int level = activeChar.getSkillLevel(_skillId);
		if (level <= 0)
		{
			sendAF();
			return;
		}
		
		// Get the L2Skill template corresponding to the skillID received from the client
		L2Skill skill = SkillTable.getInstance().getInfo(_skillId, level);
		
		// Check the validity of the skill
		if (activeChar.canUseMagic(skill))
		{
			if (skill.isToggle())
				activeChar.doSimultaneousCast(skill);
			else
				activeChar.useMagic(new SkillUsageRequest(skill, _ctrl, _shift));
		}
		else
		{
			sendAF();
		}
	}
	
	@Override
	public String getType()
	{
		return _C__REQUESTMAGICSKILLUSE;
	}
}
