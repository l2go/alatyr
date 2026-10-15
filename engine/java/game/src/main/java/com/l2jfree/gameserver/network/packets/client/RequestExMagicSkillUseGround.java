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
import com.l2jfree.tools.geometry.Point3D;

/**
 * @author -Wooden-
 */
public final class RequestExMagicSkillUseGround extends L2ClientPacket
{
	private static final String _C__D0_2F_REQUESTEXMAGICSKILLUSEGROUND = "[C] D0:2F RequestExMagicSkillUseGround";
	
	private int _x;
	private int _y;
	private int _z;
	private int _skillId;
	private boolean _ctrlPressed;
	private boolean _shiftPressed;
	
	@Override
	protected void readImpl()
	{
		_x = readD();
		_y = readD();
		_z = readD();
		_skillId = readD();
		_ctrlPressed = readD() != 0;
		_shiftPressed = readC() != 0;
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getClient().getActiveChar();
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
			activeChar.useMagic(new SkillUsageRequest(skill, _ctrlPressed, _shiftPressed, new Point3D(_x, _y, _z)));
		}
		else
		{
			sendAF();
		}
	}
	
	@Override
	public String getType()
	{
		return _C__D0_2F_REQUESTEXMAGICSKILLUSEGROUND;
	}
}
