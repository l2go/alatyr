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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.handler.IAdminCommandHandler;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.PledgeSkillList;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * This class handles following admin commands:
 * - show_skills
 * - remove_skills
 * - skill_list
 * - skill_index
 * - add_skill
 * - remove_skill
 * - get_skills
 * - reset_skills
 * - give_all_skills
 * - remove_all_skills
 * - add_clan_skills
 * - cast_skill
 * - give_full_skills
 * 
 * @version $Revision: 1.2.4.7 $ $Date: 2005/04/11 10:06:02 $
 */
public class AdminSkill implements IAdminCommandHandler
{
	private final static Logger _log = LoggerFactory.getLogger(AdminSkill.class);
	
	private static final String[] ADMIN_COMMANDS = { "admin_show_skills", "admin_remove_skills", "admin_skill_list",
			"admin_skill_index", "admin_add_skill", "admin_remove_skill", "admin_get_skills", "admin_reset_skills",
			"admin_give_all_skills", "admin_remove_all_skills", "admin_ench_skills", "admin_add_clan_skill",
			// L2JFREE
			"admin_cast_skill", "admin_give_full_skills" };
	
	private static L2Skill[] adminSkills;
	
	@Override
	public boolean useAdminCommand(String command, L2Player activeChar)
	{
		if (command.equals("admin_show_skills"))
		{
			showMainPage(activeChar);
		}
		else if (command.startsWith("admin_remove_skills"))
		{
			try
			{
				String val = command.substring(20);
				removeSkillsPage(activeChar, Integer.parseInt(val));
			}
			catch (StringIndexOutOfBoundsException e)
			{
			}
		}
		else if (command.startsWith("admin_skill_list"))
		{
			AdminHelpPage.showHelpPage(activeChar, "skills.htm");
		}
		else if (command.startsWith("admin_skill_index"))
		{
			try
			{
				String val = command.substring(18);
				AdminHelpPage.showHelpPage(activeChar, "skills/" + val + ".htm");
			}
			catch (StringIndexOutOfBoundsException e)
			{
			}
		}
		else if (command.startsWith("admin_add_skill"))
		{
			try
			{
				String val = command.substring(15);
				adminAddSkill(activeChar, val);
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //add_skill <skill_id> <level>");
			}
		}
		else if (command.startsWith("admin_remove_skill"))
		{
			try
			{
				String id = command.substring(19);
				int idval = Integer.parseInt(id);
				adminRemoveSkill(activeChar, idval);
			}
			catch (StringIndexOutOfBoundsException e)
			{
				activeChar.sendMessage("Usage: //remove_skill <skill_id>");
			}
		}
		else if (command.equals("admin_get_skills"))
		{
			adminGetSkills(activeChar);
		}
		else if (command.equals("admin_reset_skills"))
		{
			adminResetSkills(activeChar);
		}
		else if (command.equals("admin_give_all_skills"))
		{
			adminGiveAllSkills(activeChar);
		}
		else if (command.equals("admin_remove_all_skills"))
		{
			if (activeChar.getTarget() instanceof L2Player)
			{
				L2Player player = (L2Player)activeChar.getTarget();
				for (L2Skill skill : player.getAllSkills())
					player.removeSkill(skill);
				activeChar.sendMessage("You removed all skills from " + player.getName());
				if (activeChar != player)
					player.sendMessage("Admin removed all skills from you.");
				player.broadcastUserInfo();
			}
		}
		else if (command.equals("admin_ench_skills"))
		{
			activeChar.sendMessage("Not implemented yet.");
		}
		else if (command.startsWith("admin_add_clan_skill"))
		{
			try
			{
				String[] val = command.split(" ");
				adminAddClanSkill(activeChar, Integer.parseInt(val[1]), Integer.parseInt(val[2]));
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //add_clan_skill <skill_id> <level>");
			}
		}
		else if (command.startsWith("admin_cast_skill"))
		{
			castSkill(activeChar, command.substring(17));
		}
		else if (command.equals("admin_give_full_skills"))
		{
			adminGiveFullSkills(activeChar);
		}
		
		return true;
	}
	
	/**
	 * This function will give all the skills that the target can learn at his/her level
	 * @param activeChar: the gm char
	 */
	private void adminGiveAllSkills(L2Player activeChar)
	{
		final L2Player player = activeChar.getTarget(L2Player.class);
		
		if (player == null)
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		
		String info = SkillTreeTable.getInstance().giveAvailableSkills(player);
		
		//Notify player and admin
		if (player != activeChar)
			player.sendMessage("A GM gave you " + info + ".");
		
		activeChar.sendMessage("You gave " + info + " to " + player.getName());
		
		if (player.isGM() && !player.hasSkill(7029))
		{
			player.addSkill(7029, 4);
		}
		
		player.sendSkillList();
	}
	
	private void adminGiveFullSkills(L2Player activeChar)
	{
		int length = activeChar.getAllSkills().length;
		
		for (int i = 1; i < 2000; i++)
			activeChar.addSkill(SkillTable.getInstance().getInfo(i, SkillTable.getInstance().getMaxLevel(i)), true);
		
		activeChar.sendMessage((activeChar.getAllSkills().length - length) + " new skill added.");
		activeChar.sendSkillList();
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private void removeSkillsPage(L2Player activeChar, int page)
	{
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
		{
			activeChar.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
			return;
		}
		
		L2Skill[] skills = player.getAllSkills();
		
		int MaxSkillsPerPage = 10;
		int MaxPages = skills.length / MaxSkillsPerPage;
		if (skills.length > MaxSkillsPerPage * MaxPages)
			MaxPages++;
		
		if (page > MaxPages)
			page = MaxPages;
		
		int SkillsStart = MaxSkillsPerPage * page;
		int SkillsEnd = skills.length;
		if (SkillsEnd - SkillsStart > MaxSkillsPerPage)
			SkillsEnd = SkillsStart + MaxSkillsPerPage;
		
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		StringBuilder replyMSG = new StringBuilder("<html><body>");
		replyMSG.append("<table width=260><tr>");
		replyMSG.append("<td width=40><button value=\"Main\" action=\"bypass -h admin_admin\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("<td width=180><center>Character Selection Menu</center></td>");
		replyMSG.append("<td width=40><button value=\"Back\" action=\"bypass -h admin_show_skills\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td>");
		replyMSG.append("</tr></table>");
		replyMSG.append("<br><br>");
		replyMSG.append("<center>Editing <font color=\"LEVEL\">" + player.getName() + "</font></center>");
		replyMSG.append("<br><table width=270><tr><td>Lv: " + player.getLevel() + " "
				+ player.getTemplate().getClassName() + "</td></tr></table>");
		replyMSG.append("<br><table width=270><tr><td>Note: Dont forget that modifying players skills can</td></tr>");
		replyMSG.append("<tr><td>ruin the game...</td></tr></table>");
		replyMSG.append("<br><center>Click on the skill you wish to remove:</center>");
		replyMSG.append("<br>");
		String pages = "<center><table width=270><tr>";
		for (int x = 0; x < MaxPages; x++)
		{
			int pagenr = x + 1;
			pages += "<td><a action=\"bypass -h admin_remove_skills " + x + "\">Page " + pagenr + "</a></td>";
		}
		pages += "</tr></table></center>";
		replyMSG.append(pages);
		replyMSG.append("<br><table width=270>");
		replyMSG.append("<tr><td width=80>Name:</td><td width=60>Level:</td><td width=40>Id:</td></tr>");
		for (int i = SkillsStart; i < SkillsEnd; i++)
			replyMSG.append("<tr><td width=80><a action=\"bypass -h admin_remove_skill " + skills[i].getId() + "\">"
					+ skills[i].getName() + "</a></td><td width=60>" + skills[i].getLevel() + "</td><td width=40>"
					+ skills[i].getId() + "</td></tr>");
		replyMSG.append("</table>");
		replyMSG.append("<br><center><table>");
		replyMSG.append("Remove skill by ID :");
		replyMSG.append("<tr><td>Id: </td>");
		replyMSG.append("<td><edit var=\"id_to_remove\" width=110></td></tr>");
		replyMSG.append("</table></center>");
		replyMSG.append("<center><button value=\"Remove skill\" action=\"bypass -h admin_remove_skill $id_to_remove\" width=110 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></center>");
		replyMSG.append("<br><center><button value=\"Back\" action=\"bypass -h admin_current_player\" width=40 height=15></center>");
		replyMSG.append("</body></html>");
		adminReply.setHtml(replyMSG.toString());
		activeChar.sendPacket(adminReply);
	}
	
	private void showMainPage(L2Player activeChar)
	{
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		NpcHtmlMessage adminReply = new NpcHtmlMessage(5);
		adminReply.setFile("data/html/admin/charskills.htm");
		adminReply.replace("%name%", player.getName());
		adminReply.replace("%level%", String.valueOf(player.getLevel()));
		adminReply.replace("%class%", player.getTemplate().getClassName());
		activeChar.sendPacket(adminReply);
	}
	
	private void adminGetSkills(L2Player activeChar)
	{
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		if (player == activeChar)
		{
			player.sendPacket(SystemMessageId.CANNOT_USE_ON_YOURSELF);
		}
		else
		{
			L2Skill[] skills = player.getAllSkills();
			adminSkills = activeChar.getAllSkills();
			for (L2Skill element : adminSkills)
				activeChar.removeSkill(element);
			for (L2Skill element : skills)
				activeChar.addSkill(element, true);
			activeChar.sendMessage("You now have all the skills of " + player.getName() + ".");
			activeChar.sendSkillList();
		}
		showMainPage(activeChar);
	}
	
	private void adminResetSkills(L2Player activeChar)
	{
		if (adminSkills == null)
		{
			activeChar.sendMessage("You must get the skills of someone in order to do this.");
		}
		else
		{
			L2Skill[] skills = activeChar.getAllSkills();
			for (L2Skill skill : skills)
				activeChar.removeSkill(skill);
			for (L2Skill skill : adminSkills)
				activeChar.addSkill(skill, true);
			activeChar.sendMessage("You now have all your skills back.");
			adminSkills = null;
			activeChar.sendSkillList();
		}
		showMainPage(activeChar);
	}
	
	private void adminAddSkill(L2Player activeChar, String val)
	{
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		StringTokenizer st = new StringTokenizer(val);
		if (st.countTokens() != 2)
		{
			showMainPage(activeChar);
		}
		else
		{
			L2Skill skill = null;
			try
			{
				String id = st.nextToken();
				String level = st.nextToken();
				int idval = Integer.parseInt(id);
				int levelval = Integer.parseInt(level);
				skill = SkillTable.getInstance().getInfo(idval, levelval);
			}
			catch (Exception e)
			{
			}
			if (skill != null)
			{
				String name = skill.getName();
				player.sendMessage("Admin gave you the skill " + name + ".");
				player.addSkill(skill, true);
				//Admin information
				activeChar.sendMessage("You gave the skill " + name + " to " + player.getName() + ".");
				if (_log.isDebugEnabled())
					_log.debug("[GM]" + activeChar.getName() + " gave skill " + name + " to " + player.getName() + ".");
				player.sendSkillList();
			}
			else
				activeChar.sendMessage("Error: there is no such skill.");
			showMainPage(activeChar); //Back to start
		}
	}
	
	private void adminRemoveSkill(L2Player activeChar, int idval)
	{
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		L2Skill skill = SkillTable.getInstance().getInfo(idval, player.getSkillLevel(idval));
		if (skill != null)
		{
			String skillname = skill.getName();
			player.sendMessage("Admin removed the skill " + skillname + " from your skills list.");
			player.removeSkill(skill);
			//Admin information
			activeChar.sendMessage("You removed the skill " + skillname + " from " + player.getName() + ".");
			if (_log.isDebugEnabled())
				_log.debug("[GM]" + activeChar.getName() + " removed skill " + skillname + " from " + player.getName()
						+ ".");
			player.sendSkillList();
		}
		else
			activeChar.sendMessage("Error: there is no such skill.");
		removeSkillsPage(activeChar, 0); //Back to previous page
	}
	
	private void adminAddClanSkill(L2Player activeChar, int id, int level)
	{
		L2Object target = activeChar.getTarget();
		L2Player player = null;
		if (target instanceof L2Player)
			player = (L2Player)target;
		else
		{
			activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
			return;
		}
		if (!player.isClanLeader())
		{
			activeChar
					.sendPacket(new SystemMessage(SystemMessageId.S1_IS_NOT_A_CLAN_LEADER).addString(player.getName()));
			showMainPage(activeChar);
			return;
		}
		if ((id < 370) || (id > 391) || (level < 1) || (level > 3))
		{
			activeChar.sendMessage("Usage: //add_clan_skill <skill_id> <level>");
			showMainPage(activeChar);
			return;
		}
		
		L2Skill skill = SkillTable.getInstance().getInfo(id, level);
		if (skill != null)
		{
			String skillname = skill.getName();
			SystemMessage sm = new SystemMessage(SystemMessageId.CLAN_SKILL_S1_ADDED);
			sm.addSkillName(skill);
			player.sendPacket(sm);
			player.getClan().broadcastToOnlineMembers(sm);
			player.getClan().addNewSkill(skill);
			activeChar.sendMessage("You gave the Clan Skill: " + skillname + " to the clan "
					+ player.getClan().getName() + ".");
			
			activeChar.getClan().broadcastToOnlineMembers(new PledgeSkillList(activeChar.getClan()));
			for (L2Player member : activeChar.getClan().getOnlineMembers(0))
			{
				member.sendSkillList();
			}
			
			showMainPage(activeChar);
			return;
		}
		
		activeChar.sendMessage("Error: there is no such skill.");
	}
	
	public void castSkill(L2Player activeChar, String val)
	{
		int skillid = Integer.parseInt(val);
		L2Skill skill = SkillTable.getInstance().getInfo(skillid, 1);
		if (skill != null)
		{
			if (skill.getTargetType() == L2Skill.SkillTargetType.TARGET_SELF)
			{
				activeChar.setTarget(activeChar);
				MagicSkillUse msk = new MagicSkillUse(activeChar, skillid, 1, skill.getHitTime(), 0);
				activeChar.broadcastPacket(msk);
				if (_log.isDebugEnabled())
					_log.debug("showing self skill, id: " + skill.getId() + " named: " + skill.getName());
			}
			else if (skill.getTargetType() == L2Skill.SkillTargetType.TARGET_ONE)
			{
				if (_log.isDebugEnabled())
					_log.debug("showing ATTACK skill, id: " + skill.getId() + " named: " + skill.getName());
			}
		}
		else
		{
			if (_log.isDebugEnabled())
				_log.debug("no such skill id: " + skillid);
		}
	}
}
