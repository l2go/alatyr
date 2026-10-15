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
package com.l2jfree.gameserver.handler.admincommands;

import java.util.StringTokenizer;

import com.l2jfree.Config;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.util.Util;

public class AdminTest implements IAdminCommandHandler
{
	private static final String[][] ADMIN_COMMANDS = {
			{ "admin_stats",
			
			"Shows server performance statistics.", "Usage: stats" },
			{ "admin_docast",
			
			"Test skill animation on target.", "Usage: //docast <skill id> <skill level> <skill time>", "Options:",
					"skill id - Id of skill animation that you want to test",
					"skill level - skill level of the skill you want to display",
					"skill time - the duration of the casting animation" },
			{ "admin_docastself",
			
			"Test skill animation on oneself.", "Usage: //docastself <skill id> <skill level> <skill time>",
					"Options:", "skill id - Id of skill animation that you want to test",
					"skill level - skill level of the skill you want to display",
					"skill time - the duration of the casting animation" },
			{ "admin_targets",
			
			"List skill targets (only multiple targets supported).", "Usage: targets skillID <level>", "Options:",
					"skillID - Id of skill, target list you want to see", "<level> - skill level, Default is 1", },
			{ "admin_mp",
			
			"Enable/disable client-server packets monitor.", "Usage: mp |dump", "Options:",
					"dump - dump currently cuptured packets", }, { "admin_known",
			
			"Enable/disable knownlist ingame debug messages.", "Usage: knownlist", }, { "admin_heading",
			
			"Show usefull info about target heading and angle.", "Usage: heading", } };
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#useAdminCommand(java.lang.String, com.l2jfree.gameserver.model.L2Player)
	 */
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		
		String cmd = st.nextToken(); // get command
		
		if (cmd.equals("admin_stats"))
		{
			for (String line : ThreadPoolManager.getInstance().getStats())
			{
				activeChar.sendMessage(line);
			}
		}
		else if (cmd.equals("admin_docast") || cmd.equals("admin_docastself"))
		{
			L2Object obj = activeChar.getTarget();
			L2Creature caster = null;
			if (!(obj instanceof L2Creature))
			{
				caster = activeChar;
			}
			else
			{
				caster = (L2Creature)obj;
			}
			
			int skillId = 0, skillLevel = 0, skillTime = 0;
			try
			{
				skillId = Integer.parseInt(st.nextToken());
				skillLevel = Integer.parseInt(st.nextToken());
				skillTime = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //docast <skill id> <skill level> <skill time>");
				return false;
			}
			L2Creature target = null;
			if (!(caster.getTarget() instanceof L2Creature) || cmd.equals("admin_docastself"))
			{
				target = caster;
			}
			else
				target = (L2Creature)caster.getTarget();
			
			caster.broadcastPacket(new MagicSkillUse(caster, target, skillId, skillLevel, skillTime, 0));
			activeChar.sendMessage("Did a cast for skill: " + skillId + ", level: " + skillLevel);
		}
		else if (cmd.equals("admin_mp"))
		{
			activeChar.sendMessage("Packet monitor not supported.");
		}
		else if (cmd.equals("admin_known"))
		{
			Config.TEST_KNOWNLIST = !Config.TEST_KNOWNLIST;
			activeChar.sendMessage("Knownlist debug is " + (Config.TEST_KNOWNLIST ? "enabled" : "disabled") + ".");
		}
		else if (cmd.equals("admin_targets"))
		{
			L2Skill skill;
			int skillId = 0;
			int skillLvl = 1;
			
			try
			{
				skillId = Integer.parseInt(st.nextToken());
				if (st.hasMoreTokens())
					skillLvl = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
			}
			
			if (skillId > 0)
			{
				int skillLvlMax = SkillTable.getInstance().getMaxLevel(skillId);
				
				if (skillLvl > skillLvlMax)
					skillLvl = skillLvlMax;
				
				skill = SkillTable.getInstance().getInfo(skillId, skillLvl);
				if (skill != null)
				{
					L2Object[] targetList = skill.getTargetList(activeChar);
					
					if (targetList.length > 0)
					{
						activeChar.sendMessage("Targets list fo skill " + skill.getName() + ":");
						
						for (L2Object target : targetList)
						{
							if (target instanceof L2Npc)
								activeChar.sendMessage("NPC: " + target.getName());
							if (target instanceof L2Player)
								activeChar.sendMessage("PC : " + target.getName());
							if (target instanceof L2Summon)
								activeChar.sendMessage("PET: " + target.getName());
						}
						
						activeChar.sendMessage("Total targets: " + targetList.length);
					}
					else
					{
						activeChar.sendMessage("Targets list fo skill " + skill.getName() + " is empty.");
					}
				}
				else
					activeChar.sendMessage("Skill id " + skillId + " not found.");
			}
			else
				showAdminCommandHelp(activeChar, cmd);
		}
		else if (cmd.equals("admin_heading"))
		{
			L2Creature charTarget = activeChar.getTarget(L2Creature.class);
			
			if (charTarget != null)
			{
				double angleChar, angleTarget, angleDiff;
				
				angleChar = Util.calculateAngleFrom(charTarget, activeChar);
				angleTarget = Util.convertHeadingToDegree(charTarget.getHeading());
				angleDiff = angleChar - angleTarget;
				
				activeChar.sendMessage("Target heading " + charTarget.getHeading() + ".");
				activeChar.sendMessage("Your heading " + activeChar.getHeading() + ".");
				activeChar.sendMessage("Target angle " + angleTarget + ".");
				activeChar.sendMessage("Your angle " + angleChar + ".");
				activeChar.sendMessage("Angle difference before correction " + angleDiff + ".");
				activeChar.sendMessage("Angle difference after correction "
						+ Util.getAngleDifference(activeChar, charTarget) + ".");
				activeChar.sendMessage("Is Behind ? " + activeChar.isBehind(charTarget) + ".");
			}
			else
				showAdminCommandHelp(activeChar, cmd);
		}
		return true;
	}
	
	/**
	 * Test social action or NPC animation
	 * @param activeChar
	 * @param socId
	 */
	public void adminTestSocial(L2Player activeChar, int socId)
	{
		L2Object target;
		
		if (activeChar.getTarget() instanceof L2Creature)
			target = activeChar.getTarget();
		else
			target = activeChar;
		
		SocialAction sa = new SocialAction(target.getObjectId(), socId);
		((L2Creature)target).broadcastPacket(sa);
	}
	
	/**
	 * Show tips about command usage and syntax.
	 * @param command admin command name
	 */
	private void showAdminCommandHelp(L2Player activeChar, String command)
	{
		for (String[] element : ADMIN_COMMANDS)
		{
			if (command.equals(element[0]))
			{
				for (int k = 1; k < element.length; k++)
					activeChar.sendMessage(element[k]);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.handler.IAdminCommandHandler#getAdminCommandList()
	 */
	@Override
	public String[] getAdminCommandList()
	{
		String[] _adminCommandsOnly = new String[ADMIN_COMMANDS.length];
		for (int i = 0; i < ADMIN_COMMANDS.length; i++)
		{
			_adminCommandsOnly[i] = ADMIN_COMMANDS[i][0];
		}
		
		return _adminCommandsOnly;
	}
}
