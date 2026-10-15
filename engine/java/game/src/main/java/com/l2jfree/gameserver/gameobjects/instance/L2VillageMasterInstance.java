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
package com.l2jfree.gameserver.gameobjects.instance;

import java.util.Iterator;
import java.util.Set;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.CharTemplateTable;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.base.ClassType;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.gameobjects.base.SubClass;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2Clan.SubPledge;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.learn.L2PledgeSkillLearn;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillDone;
import com.l2jfree.gameserver.network.packets.server.AcquireSkillList;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.PledgeReceiveSubPledgeCreated;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.FloodProtector.Protected;
import com.l2jfree.gameserver.util.StringUtil;

/**
 * This class ...
 * 
 * @version $Revision: 1.4.2.3.2.8 $ $Date: 2005/03/29 23:15:15 $
 */
public final class L2VillageMasterInstance extends L2NpcInstance
{
	/**
	 * @param template
	 */
	public L2VillageMasterInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		String[] commandStr = command.split(" ");
		String actualCommand = commandStr[0]; // Get actual command
		
		String cmdParams = "";
		String cmdParams2 = "";
		
		if (commandStr.length >= 2)
			cmdParams = commandStr[1];
		if (commandStr.length >= 3)
			cmdParams2 = commandStr[2];
		
		if (actualCommand.equalsIgnoreCase("create_clan"))
		{
			if (cmdParams.isEmpty())
				return;
			
			ClanTable.getInstance().createClan(player, cmdParams);
		}
		else if (actualCommand.equalsIgnoreCase("create_academy"))
		{
			if (cmdParams.isEmpty())
				return;
			
			createSubPledge(player, cmdParams, null, L2Clan.SUBUNIT_ACADEMY, 5);
		}
		else if (actualCommand.equalsIgnoreCase("create_royal"))
		{
			if (cmdParams.isEmpty())
				return;
			
			createSubPledge(player, cmdParams, cmdParams2, L2Clan.SUBUNIT_ROYAL1, 6);
		}
		else if (actualCommand.equalsIgnoreCase("assign_subpl_leader"))
		{
			if (cmdParams.isEmpty())
				return;
			
			assignSubPledgeLeader(player, cmdParams, cmdParams2);
		}
		else if (actualCommand.equalsIgnoreCase("rename_royal1") || actualCommand.equalsIgnoreCase("rename_royal2")
				|| actualCommand.equalsIgnoreCase("rename_knights1")
				|| actualCommand.equalsIgnoreCase("rename_knights2")
				|| actualCommand.equalsIgnoreCase("rename_knights3")
				|| actualCommand.equalsIgnoreCase("rename_knights4"))
		{
			if (cmdParams.isEmpty())
				return;
			renameSubPledge(player, cmdParams, actualCommand);
		}
		else if (actualCommand.equalsIgnoreCase("create_knight"))
		{
			if (cmdParams.isEmpty())
				return;
			
			createSubPledge(player, cmdParams, cmdParams2, L2Clan.SUBUNIT_KNIGHT1, 7);
		}
		else if (actualCommand.equalsIgnoreCase("create_ally"))
		{
			if (cmdParams.isEmpty())
				return;
			
			if (player.getClan() == null)
				player.sendPacket(SystemMessageId.ONLY_CLAN_LEADER_CREATE_ALLIANCE);
			else
				player.getClan().createAlly(player, cmdParams);
		}
		else if (actualCommand.equalsIgnoreCase("dissolve_ally"))
		{
			player.getClan().dissolveAlly(player);
		}
		else if (actualCommand.equalsIgnoreCase("dissolve_clan"))
		{
			dissolveClan(player, player.getClanId());
		}
		else if (actualCommand.equalsIgnoreCase("change_clan_leader"))
		{
			if (cmdParams.isEmpty())
				return;
			
			changeClanLeader(player, cmdParams);
		}
		else if (actualCommand.equalsIgnoreCase("recover_clan"))
		{
			recoverClan(player, player.getClanId());
		}
		else if (actualCommand.equalsIgnoreCase("increase_clan_level"))
		{
			if (player.getClan().levelUpClan(player))
			{
				player.broadcastPacket(new MagicSkillUse(player, 5103, 1, 0, 0));
				player.broadcastPacket(new MagicSkillLaunched(player, 5103, 1));
			}
		}
		else if (actualCommand.equalsIgnoreCase("learn_clan_skills"))
		{
			showPledgeSkillList(player, false);
		}
		else if (command.startsWith("Subclass"))
		{
			// Subclasses may not be changed while a skill is in use.
			if (player.isCastingNow() || player.isAllSkillsDisabled())
			{
				player.sendPacket(SystemMessageId.SUBCLASS_NO_CHANGE_OR_CREATE_WHILE_SKILL_IN_USE);
				return;
			}
			
			if (GlobalRestrictions.isRestricted(player, null))
			{
				player.sendMessage("Sub classes may not be created or changed while you are during a restricted condition.");
				return;
			}
			
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			
			if (player.getTransformation() != null)
			{
				html.setFile("data/html/villagemaster/SubClass_NoTransformed.htm");
				player.sendPacket(html);
				return;
			}
			
			int cmdChoice = 0;
			int paramOne = 0;
			int paramTwo = 0;
			
			try
			{
				cmdChoice = Integer.parseInt(command.substring(9, 10).trim());
				
				int endIndex = command.indexOf(' ', 11);
				if (endIndex == -1)
					endIndex = command.length();
				
				paramOne = Integer.parseInt(command.substring(11, endIndex).trim());
				if (command.length() > endIndex)
					paramTwo = Integer.parseInt(command.substring(endIndex).trim());
			}
			catch (Exception NumberFormatException)
			{
			}
			
			switch (cmdChoice)
			{
				case 0: // Subclass change menu
					if (Config.ALT_GAME_SUBCLASS_EVERYWHERE)
						html.setFile("data/html/villagemaster/SubClass.htm");
					else if (getVillageMasterRace() == Race.Kamael)
					{
						if (player.getRace() == Race.Kamael)
							html.setFile("data/html/villagemaster/SubClass.htm");
						else
							html.setFile("data/html/villagemaster/SubClass_NoKamael.htm");
					}
					else
					{
						if (player.getRace() != Race.Kamael)
							html.setFile("data/html/villagemaster/SubClass.htm");
						else
							html.setFile("data/html/villagemaster/SubClass_NoOther.htm");
					}
					break;
				case 1: // Add Subclass - Initial
					// Avoid giving player an option to add a new sub class, if they have three already.
					if (player.getTotalSubClasses() >= Config.ALT_MAX_SUBCLASS)
					{
						if (player.getRace() != Race.Kamael)
							html.setFile("data/html/villagemaster/SubClass_Fail.htm");
						else
							html.setFile("data/html/villagemaster/SubClass_Fail_Kamael.htm");
						break;
					}
					
					html.setFile("data/html/villagemaster/SubClass_Add.htm");
					final StringBuilder content1 = StringUtil.startAppend(200);
					Set<ClassId> subsAvailable = getAvailableSubClasses(player);
					
					if (subsAvailable != null && !subsAvailable.isEmpty())
					{
						for (ClassId subClass : subsAvailable)
						{
							StringUtil
									.append(content1, "<a action=\"bypass -h npc_%objectId%_Subclass 4 ",
											String.valueOf(subClass.ordinal()), "\" msg=\"1268;",
											formatClassForDisplay(subClass), "\">", formatClassForDisplay(subClass),
											"</a><br>");
						}
					}
					else
					{
						// TODO: Retail message
						player.sendMessage("There are no sub classes available at this time.");
						return;
					}
					html.replace("%list%", content1.toString());
					break;
				case 2: // Change Class - Initial
					if (player.getSubClasses().isEmpty())
					{
						html.setFile("data/html/villagemaster/SubClass_ChangeNo.htm");
					}
					else
					{
						final StringBuilder content2 = StringUtil.startAppend(200);
						
						if (checkVillageMaster(player.getBaseClass()))
						{
							StringUtil.append(content2, "<a action=\"bypass -h npc_%objectId%_Subclass 5 0\">",
									CharTemplateTable.getClassNameById(player.getBaseClass()), "</a><br>");
						}
						
						for (Iterator<SubClass> subList = iterSubClasses(player); subList.hasNext();)
						{
							SubClass subClass = subList.next();
							if (checkVillageMaster(subClass.getClassDefinition()))
							{
								StringUtil.append(content2, "<a action=\"bypass -h npc_%objectId%_Subclass 5 ",
										String.valueOf(subClass.getClassIndex()), "\">",
										formatClassForDisplay(subClass.getClassDefinition()), "</a><br>");
							}
						}
						
						if (content2.length() > 0)
						{
							html.setFile("data/html/villagemaster/SubClass_Change.htm");
							html.replace("%list%", content2.toString());
						}
						else
							html.setFile("data/html/villagemaster/SubClass_ChangeNotFound.htm");
					}
					break;
				case 3: // Change/Cancel Subclass - Initial
					if (player.getSubClasses() == null || player.getSubClasses().isEmpty())
					{
						html.setFile("data/html/villagemaster/SubClass_ModifyEmpty.htm");
						break;
					}
					
					// custom value
					if (player.getTotalSubClasses() > 3)
					{
						html.setFile("data/html/villagemaster/SubClass_ModifyCustom.htm");
						final StringBuilder content3 = StringUtil.startAppend(200);
						int classIndex = 1;
						
						for (Iterator<SubClass> subList = iterSubClasses(player); subList.hasNext();)
						{
							SubClass subClass = subList.next();
							
							StringUtil.append(content3, "Sub-class ", String.valueOf(classIndex++), "<br>",
									"<a action=\"bypass -h npc_%objectId%_Subclass 6 ",
									String.valueOf(subClass.getClassIndex()), "\">",
									CharTemplateTable.getClassNameById(subClass.getClassId()), "</a><br>");
						}
						html.replace("%list%", content3.toString());
					}
					else
					{
						// retail html contain only 3 subclasses
						html.setFile("data/html/villagemaster/SubClass_Modify.htm");
						if (player.getSubClasses().containsKey(1))
							html.replace("%sub1%",
									CharTemplateTable.getClassNameById(player.getSubClasses().get(1).getClassId()));
						else
							html.replace("<a action=\"bypass -h npc_%objectId%_Subclass 6 1\">%sub1%</a><br>", "");
						
						if (player.getSubClasses().containsKey(2))
							html.replace("%sub2%",
									CharTemplateTable.getClassNameById(player.getSubClasses().get(2).getClassId()));
						else
							html.replace("<a action=\"bypass -h npc_%objectId%_Subclass 6 2\">%sub2%</a><br>", "");
						
						if (player.getSubClasses().containsKey(3))
							html.replace("%sub3%",
									CharTemplateTable.getClassNameById(player.getSubClasses().get(3).getClassId()));
						else
							html.replace("<a action=\"bypass -h npc_%objectId%_Subclass 6 3\">%sub3%</a><br>", "");
					}
					break;
				case 4: // Add Subclass - Action (Subclass 4 x[x])
					/*
					 * If the character is less than level 75 on any of their previously chosen
					 * classes then disallow them to change to their most recently added sub-class choice.
					 */
					
					if (!FloodProtector.tryPerformAction(player, Protected.SUBCLASS))
					{
						_log.warn("Player " + player.getName() + " has performed a subclass change too fast");
						return;
					}
					
					boolean allowAddition = true;
					
					if (player.getLevel() < 75)
					{
						allowAddition = false;
					}
					
					if (player.getTotalSubClasses() >= Config.ALT_MAX_SUBCLASS)
					{
						allowAddition = false;
					}
					
					if (allowAddition)
					{
						if (!player.getSubClasses().isEmpty())
						{
							for (Iterator<SubClass> subList = iterSubClasses(player); subList.hasNext();)
							{
								SubClass subClass = subList.next();
								
								if (subClass.getLevel() < 75)
								{
									allowAddition = false;
									break;
								}
							}
						}
					}
					
					/*
					 * If quest checking is enabled, verify if the character has completed the Mimir's Elixir (Path to Subclass)
					 * and Fate's Whisper (A Grade Weapon) quests by checking for instances of their unique reward items.
					 *
					 * If they both exist, remove both unique items and continue with adding the sub-class.
					 */
					if (!Config.ALT_GAME_SUBCLASS_WITHOUT_QUESTS && allowAddition)
					{
						QuestState qs = player.getQuestState("234_FatesWhisper");
						if (qs == null || !qs.isCompleted())
						{
							allowAddition = false;
						}
						
						if (player.getRace() != Race.Kamael)
						{
							qs = player.getQuestState("235_MimirsElixir");
							if (qs == null || !qs.isCompleted())
							{
								allowAddition = false;
							}
						}
						//Kamael have different quest than 235
						else
						{
							qs = player.getQuestState("236_SeedsOfChaos");
							if (qs == null || !qs.isCompleted())
							{
								allowAddition = false;
							}
						}
					}
					
					if (allowAddition && isValidNewSubClass(player, paramOne))
					{
						if (!player.addSubClass(paramOne, player.getTotalSubClasses() + 1))
							return;
						
						player.setActiveClass(player.getTotalSubClasses());
						
						html.setFile("data/html/villagemaster/SubClass_AddOk.htm");
						
						player.sendPacket(SystemMessageId.CLASS_TRANSFER); // Transfer to new class.
					}
					else
					{
						if (player.getRace() != Race.Kamael)
							html.setFile("data/html/villagemaster/SubClass_Fail.htm");
						else
							html.setFile("data/html/villagemaster/SubClass_Fail_Kamael.htm");
					}
					break;
				case 5: // Change Class - Action
					/*
					 * If the character is less than level 75 on any of their previously chosen
					 * classes then disallow them to change to their most recently added sub-class choice.
					 *
					 * Note: paramOne = classIndex
					 */
					
					if (!FloodProtector.tryPerformAction(player, Protected.SUBCLASS))
					{
						_log.warn("Player " + player.getName() + " has performed a subclass change too fast");
						return;
					}
					
					if (player.getClassIndex() == paramOne)
					{
						html.setFile("data/html/villagemaster/SubClass_Current.htm");
						break;
					}
					
					if (paramOne == 0)
					{
						if (!checkVillageMaster(player.getBaseClass()))
							return;
					}
					else
					{
						SubClass sub = player.getSubClasses().get(paramOne);
						
						if (sub == null)
							return;
						
						if (!checkVillageMaster(sub.getClassDefinition()))
							return;
					}
					
					player.setActiveClass(paramOne);
					
					player.sendPacket(SystemMessageId.SUBCLASS_TRANSFER_COMPLETED); // Transfer completed.
					return;
				case 6: // Change/Cancel Subclass - Choice
					// validity check
					if (paramOne < 1 || paramOne > Config.ALT_MAX_SUBCLASS)
						return;
					
					subsAvailable = getAvailableSubClasses(player);
					
					// another validity check
					if (subsAvailable == null || subsAvailable.isEmpty())
					{
						// TODO: Retail message
						player.sendMessage("There are no sub classes available at this time.");
						return;
					}
					
					final StringBuilder content6 = StringUtil.startAppend(200);
					
					for (ClassId subClass : subsAvailable)
					{
						StringUtil.append(content6, "<a action=\"bypass -h npc_%objectId%_Subclass 7 ",
								String.valueOf(paramOne), " ", String.valueOf(subClass.ordinal()), "\" msg=\"1445;",
								"\">", formatClassForDisplay(subClass), "</a><br>");
					}
					
					switch (paramOne)
					{
						case 1:
							html.setFile("data/html/villagemaster/SubClass_ModifyChoice1.htm");
							break;
						case 2:
							html.setFile("data/html/villagemaster/SubClass_ModifyChoice2.htm");
							break;
						case 3:
							html.setFile("data/html/villagemaster/SubClass_ModifyChoice3.htm");
							break;
						default:
							html.setFile("data/html/villagemaster/SubClass_ModifyChoice.htm");
					}
					html.replace("%list%", content6.toString());
					break;
				case 7: // Change Subclass - Action
					/*
					 * Warning: the information about this subclass will be removed from the
					 * subclass list even if false!
					 */
					
					if (!FloodProtector.tryPerformAction(player, Protected.SUBCLASS))
					{
						_log.warn("Player " + player.getName() + " has performed a subclass change too fast");
						return;
					}
					else if (!isValidNewSubClass(player, paramTwo))
					{
						return;
					}
					else if (player.modifySubClass(paramOne, paramTwo))
					{
						player.stopAllEffectsExceptThoseThatLastThroughDeath(); // all effects from old subclass stopped!
						player.setActiveClass(paramOne);
						
						html.setFile("data/html/villagemaster/SubClass_ModifyOk.htm");
						html.replace("%name%", CharTemplateTable.getClassNameById(paramTwo));
						
						player.sendPacket(SystemMessageId.ADD_NEW_SUBCLASS); // Subclass added.
						
						// check player skills
						player.checkAllowedSkills();
					}
					else
					{
						/*
						 * This isn't good! modifySubClass() removed subclass from memory
						 * we must update _classIndex! Else IndexOutOfBoundsException can turn
						 * up some place down the line along with other seemingly unrelated
						 * problems.
						 */
						player.setActiveClass(0); // Also updates _classIndex plus switching _classid to baseclass.
						
						player.sendMessage("The sub class could not be added, you have been reverted to your base class.");
						return;
					}
					break;
			}
			
			html.replace("%objectId%", String.valueOf(getObjectId()));
			player.sendPacket(html);
		}
		else if (actualCommand.equalsIgnoreCase("SubCertification"))
		{
			int cmdChoice = Integer.parseInt(command.substring(17, 19).trim());
			
			if (player.isSubClassActive())
			{
				QuestState qs = player.getQuestState("136_MoreThanMeetsTheEye");
				if (qs == null || !qs.isCompleted())
				{
					player.sendMessage("You must have completed the More than meets the eye quest for receiving certifications.");
					return;
				}
				
				if (player.getInventoryLimit() * 0.8 <= player.getInventory().getSize())
				{
					player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
					return;
				}
				
				int classIndex = player.getClassIndex();
				int subClassType = player.getSubClassType();
				int certificationLevel = player.getCertificationLevel(classIndex);
				
				if (certificationLevel == -1)
				{
					player.storeCertificationLevel(classIndex);
					certificationLevel = 0;
				}
				
				switch (cmdChoice)
				{
					case 65:
						if (player.getLevel() >= 65 && certificationLevel == 0)
						{
							player.addItem("Certificate - Emergent Ability", 10280, 1, player, true, true);
							player.getInventory().updateDatabase();
							player.updateCertificationLevel(classIndex, 1);
						}
						else
						{
							player.sendMessage("Sorry, you either already certified or did not certify previous levels or are not on the recquired level to do this.");
						}
						break;
					case 70:
						if (player.getLevel() >= 70 && certificationLevel == 1)
						{
							player.addItem("Certificate - Emergent Ability", 10280, 1, player, true, true);
							player.getInventory().updateDatabase();
							player.updateCertificationLevel(classIndex, 2);
						}
						else
						{
							player.sendMessage("Sorry, you either already certified or did not certify previous levels or are not on the recquired level to do this.");
						}
						break;
					case 75:
						if (player.getLevel() >= 75 && certificationLevel == 2)
						{
							int certifType = Integer.parseInt(command.substring(20, 21).trim());
							
							switch (certifType)
							{
								case 1:
									switch (subClassType)
									{
										case 1:
											player.addItem("Certificate - Warrior Ability", 10281, 1, player, true,
													true);
											break;
										case 2:
											player.addItem("Certificate - Rogue Ability", 10283, 1, player, true, true);
											break;
										case 3:
											player.addItem("Certificate - Knight Ability", 10282, 1, player, true, true);
											break;
										case 4:
											player.addItem("Certificate - Summoner Ability", 10286, 1, player, true,
													true);
											break;
										case 5:
											player.addItem("Certificate - Wizard Ability", 10284, 1, player, true, true);
											break;
										case 6:
											player.addItem("Certificate - Healer Ability", 10285, 1, player, true, true);
											break;
										case 7:
											player.addItem("Certificate - Enchanter Ability", 10287, 1, player, true,
													true);
											break;
									}
									break;
								case 2:
									player.addItem("Certificate - Master Ability", 10612, 1, player, true, true);
									break;
							}
							player.getInventory().updateDatabase();
							player.updateCertificationLevel(classIndex, 3);
						}
						else
						{
							player.sendMessage("Sorry, you either already certified or did not certify previous levels or are not on the recquired level to do this.");
						}
						break;
					case 80:
						if (player.getLevel() >= 80 && certificationLevel == 3)
						{
							switch (subClassType)
							{
								case 1:
									player.addItem("Transform Sealbook - Divine Warrior", 10289, 1, player, true, true);
									break;
								case 2:
									player.addItem("Transform Sealbook - Divine Rogue", 10290, 1, player, true, true);
									break;
								case 3:
									player.addItem("Transform Sealbook - Divine Knight", 10288, 1, player, true, true);
									break;
								case 4:
									player.addItem("Transform Sealbook - Divine Summoner", 10294, 1, player, true, true);
									break;
								case 5:
									player.addItem("Transform Sealbook - Divine Wizard", 10292, 1, player, true, true);
									break;
								case 6:
									player.addItem("Transform Sealbook - Divine Healer", 10291, 1, player, true, true);
									break;
								case 7:
									player.addItem("Transform Sealbook - Divine Enchanter", 10293, 1, player, true,
											true);
									break;
							}
							player.getInventory().updateDatabase();
							player.updateCertificationLevel(classIndex, 4);
						}
						else
						{
							player.sendMessage("Sorry, you either already certified or did not certify previous levels or are not on the recquired level to do this.");
						}
						break;
				}
			}
			else
			{
				showChatWindow(player, "data/html/villagemaster/SubClassCertificationFailed.htm");
				return;
			}
		}
		else
		{
			// This class dont know any other commands, let forward
			// the command to the parent class
			super.onBypassFeedback(player, command);
		}
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		
		if (val == 0)
			pom = "" + npcId;
		else
			pom = npcId + "-" + val;
		
		return "data/html/villagemaster/" + pom + ".htm";
	}
	
	//Private stuff
	/**
	 * @param player
	 * @param clanId
	 */
	private static final void dissolveClan(L2Player player, int clanId)
	{
		if (!player.isClanLeader())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		final L2Clan clan = player.getClan();
		if (clan.getAllyId() != 0)
		{
			player.sendPacket(SystemMessageId.CANNOT_DISPERSE_THE_CLANS_IN_ALLY);
			return;
		}
		if (clan.isAtWar())
		{
			player.sendPacket(SystemMessageId.CANNOT_DISSOLVE_WHILE_IN_WAR);
			return;
		}
		if (clan.getHasCastle() != 0 || clan.getHasHideout() != 0 || clan.getHasFort() != 0)
		{
			player.sendPacket(SystemMessageId.CANNOT_DISSOLVE_WHILE_OWNING_CLAN_HALL_OR_CASTLE);
			return;
		}
		for (Castle castle : CastleManager.getInstance().getCastles().values())
		{
			if (SiegeManager.getInstance().checkIsRegistered(clan, castle.getCastleId()))
			{
				player.sendPacket(SystemMessageId.CANNOT_DISSOLVE_WHILE_IN_SIEGE);
				return;
			}
		}
		for (Fort fort : FortManager.getInstance().getForts())
		{
			if (FortSiegeManager.getInstance().checkIsRegistered(clan, fort.getFortId()))
			{
				player.sendPacket(SystemMessageId.CANNOT_DISSOLVE_WHILE_IN_SIEGE);
				return;
			}
		}
		if (SiegeManager.getInstance().checkIfInZone(player))
		{
			player.sendPacket(SystemMessageId.CANNOT_DISSOLVE_WHILE_IN_SIEGE);
			return;
		}
		if (clan.getDissolvingExpiryTime() > System.currentTimeMillis())
		{
			player.sendPacket(SystemMessageId.DISSOLUTION_IN_PROGRESS);
			return;
		}
		
		clan.setDissolvingExpiryTime(System.currentTimeMillis() + Config.ALT_CLAN_DISSOLVE_DAYS * 86400000L); //24*60*60*1000 = 86400000
		clan.updateClanInDB();
		
		ClanTable.getInstance().scheduleRemoveClan(clan.getClanId());
		
		// The clan leader should take the XP penalty of a full death.
		player.deathPenalty(false, false, false);
	}
	
	/**
	 * @param player
	 * @param clanId
	 */
	private static final void recoverClan(L2Player player, int clanId)
	{
		if (!player.isClanLeader())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		final L2Clan clan = player.getClan();
		
		clan.setDissolvingExpiryTime(0);
		clan.updateClanInDB();
	}
	
	private static final void changeClanLeader(L2Player player, String target)
	{
		if (!player.isClanLeader())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		if (player.getName().equalsIgnoreCase(target))
			return;
		
		/*
		 * Until proper clan leader change support is done, this is a little
		 * exploit fix (leader, while fliying wyvern changes clan leader and the new leader
		 * can ride the wyvern too)
		 * DrHouse
		 */
		if (player.isFlying())
		{
			player.sendMessage("Please, stop flying");
			return;
		}
		
		final L2Clan clan = player.getClan();
		
		Castle c = CastleManager.getInstance().getCastleByOwner(clan);
		if (c != null && c.isGateOpen())
		{
			player.sendMessage("Please close the clan gate.");
			return;
		}
		
		final L2ClanMember member = clan.getClanMember(target);
		if (member == null)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_DOES_NOT_EXIST);
			sm.addString(target);
			player.sendPacket(sm);
			sm = null;
			return;
		}
		if (!member.isOnline())
		{
			player.sendPacket(SystemMessageId.INVITED_USER_NOT_ONLINE);
			return;
		}
		clan.setNewLeader(member);
	}
	
	private static final void createSubPledge(L2Player player, String clanName, String leaderName, int pledgeType,
			int minClanLvl)
	{
		if (!player.isClanLeader())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		final L2Clan clan = player.getClan();
		if (clan.getLevel() < minClanLvl)
		{
			if (pledgeType == L2Clan.SUBUNIT_ACADEMY)
				player.sendPacket(SystemMessageId.YOU_DO_NOT_MEET_CRITERIA_IN_ORDER_TO_CREATE_A_CLAN_ACADEMY);
			else
				player.sendPacket(SystemMessageId.YOU_DO_NOT_MEET_CRITERIA_IN_ORDER_TO_CREATE_A_MILITARY_UNIT);
			return;
		}
		
		if (!Config.CLAN_ALLY_NAME_PATTERN.matcher(clanName).matches())
		{
			player.sendPacket(SystemMessageId.CLAN_NAME_INCORRECT);
			return;
		}
		
		for (L2Clan tempClan : ClanTable.getInstance().getClans())
		{
			if (tempClan.getSubPledge(clanName) != null)
			{
				if (pledgeType == L2Clan.SUBUNIT_ACADEMY)
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.S1_ALREADY_EXISTS);
					sm.addString(clanName);
					player.sendPacket(sm);
				}
				else
					player.sendPacket(SystemMessageId.ANOTHER_MILITARY_UNIT_IS_ALREADY_USING_THAT_NAME);
				
				return;
			}
		}
		
		if (pledgeType != L2Clan.SUBUNIT_ACADEMY)
		{
			if (clan.getClanMember(leaderName) == null || clan.getClanMember(leaderName).getSubPledgeType() != 0)
			{
				if (pledgeType >= L2Clan.SUBUNIT_KNIGHT1)
					player.sendPacket(SystemMessageId.CAPTAIN_OF_ORDER_OF_KNIGHTS_CANNOT_BE_APPOINTED);
				else if (pledgeType >= L2Clan.SUBUNIT_ROYAL1)
					player.sendPacket(SystemMessageId.CAPTAIN_OF_ROYAL_GUARD_CANNOT_BE_APPOINTED);
				
				return;
			}
		}
		
		final int leaderId = pledgeType != L2Clan.SUBUNIT_ACADEMY ? clan.getClanMember(leaderName).getObjectId() : 0;
		if (leaderId != 0 && clan.getLeaderSubPledge(leaderId) != 0)
		{
			player.sendMessage(leaderName + " is already a sub unit leader.");
			return;
		}
		
		if (clan.createSubPledge(player, pledgeType, leaderId, clanName) == null)
			return;
		
		SystemMessage sm;
		if (pledgeType == L2Clan.SUBUNIT_ACADEMY)
		{
			sm = new SystemMessage(SystemMessageId.THE_S1S_CLAN_ACADEMY_HAS_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		}
		else if (pledgeType >= L2Clan.SUBUNIT_KNIGHT1)
		{
			sm = new SystemMessage(SystemMessageId.THE_KNIGHTS_OF_S1_HAVE_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		}
		else if (pledgeType >= L2Clan.SUBUNIT_ROYAL1)
		{
			sm = new SystemMessage(SystemMessageId.THE_ROYAL_GUARD_OF_S1_HAVE_BEEN_CREATED);
			sm.addString(player.getClan().getName());
		}
		else
			sm = SystemMessageId.CLAN_CREATED.getSystemMessage();
		
		player.sendPacket(sm);
		
		if (pledgeType != L2Clan.SUBUNIT_ACADEMY)
		{
			L2ClanMember leaderSubPledge = clan.getClanMember(leaderName);
			L2Player subLeader = leaderSubPledge.getPlayerInstance();
			if (subLeader == null)
				return;
			subLeader.setPledgeClass(L2ClanMember.getCurrentPledgeClass(subLeader));
			subLeader.sendPacket(new UserInfo(subLeader));
			try
			{
				clan.getClanMember(leaderName).updateSubPledgeType();
				for (L2Skill skill : leaderSubPledge.getPlayerInstance().getAllSkills())
					leaderSubPledge.getPlayerInstance().removeSkill(skill, false);
				clan.getClanMember(leaderName).getPlayerInstance().setActiveClass(0);
			}
			catch (RuntimeException e)
			{
				_log.warn("", e);
			}
			
			for (L2ClanMember member : clan.getMembers())
			{
				if (member == null || member.getPlayerInstance() == null || member.getPlayerInstance().isOnline() == 0)
					continue;
				SubPledge[] subPledge = clan.getAllSubPledges();
				for (SubPledge element : subPledge)
				{
					member.getPlayerInstance().sendPacket(new PledgeReceiveSubPledgeCreated(element, clan));
				}
			}
		}
	}
	
	public void renameSubPledge(L2Player player, String newName, String command)
	{
		if (player == null || player.getClan() == null || !player.isClanLeader())
		{
			if (player != null)
				player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		L2Clan clan = player.getClan();
		SubPledge[] subPledge = clan.getAllSubPledges();
		for (SubPledge element : subPledge)
		{
			switch (element.getId())
			{
				case L2Clan.SUBUNIT_ROYAL1: // 1st Royal Guard
					if (command.equalsIgnoreCase("rename_royal1"))
					{
						changeSubPledge(clan, element, newName);
						return;
					}
					break;
				case L2Clan.SUBUNIT_ROYAL2: // 2nd Royal Guard
					if (command.equalsIgnoreCase("rename_royal2"))
					{
						changeSubPledge(clan, element, newName);
						return;
					}
					break;
				case L2Clan.SUBUNIT_KNIGHT1: // 1st Order of Knights
					if (command.equalsIgnoreCase("rename_knights1"))
					{
						changeSubPledge(clan, element, newName);
						return;
					}
					break;
				case L2Clan.SUBUNIT_KNIGHT2: // 2nd Order of Knights
					if (command.equalsIgnoreCase("rename_knights2"))
					{
						changeSubPledge(clan, element, newName);
						return;
					}
					break;
				case L2Clan.SUBUNIT_KNIGHT3: // 3rd Order of Knights
					if (command.equalsIgnoreCase("rename_knights3"))
					{
						changeSubPledge(clan, element, newName);
						return;
					}
					break;
				case L2Clan.SUBUNIT_KNIGHT4: // 4th Order of Knights
					if (command.equalsIgnoreCase("rename_knights4"))
					{
						changeSubPledge(clan, element, newName);
						return;
					}
					break;
			}
		}
		player.sendMessage("Sub unit not found.");
	}
	
	public void changeSubPledge(L2Clan clan, SubPledge element, String newName)
	{
		if (newName.length() > 16 || newName.length() < 3)
		{
			clan.getLeader().getPlayerInstance().sendPacket(SystemMessageId.CLAN_NAME_TOO_LONG);
			return;
		}
		String oldName = element.getName();
		element.setName(newName);
		clan.updateSubPledgeInDB(element.getId());
		for (L2ClanMember member : clan.getMembers())
		{
			if (member == null || member.getPlayerInstance() == null || member.getPlayerInstance().isOnline() == 0)
				continue;
			SubPledge[] subPledge = clan.getAllSubPledges();
			for (SubPledge sp : subPledge)
			{
				member.getPlayerInstance().sendPacket(new PledgeReceiveSubPledgeCreated(sp, clan));
			}
			if (member.getPlayerInstance() != null)
				member.getPlayerInstance().sendMessage(
						"Clan sub unit " + oldName + "'s name has been changed into " + newName + ".");
		}
	}
	
	private static final void assignSubPledgeLeader(L2Player player, String clanName, String leaderName)
	{
		final L2Clan clan = player.getClan();
		if (clan == null || !player.isClanLeader())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return;
		}
		
		if (leaderName.length() > 16)
		{
			player.sendPacket(SystemMessageId.INCORRECT_CHARACTER_NAME_TRY_AGAIN);
			return;
		}
		
		if (player.getName().equals(leaderName))
		{
			player.sendPacket(SystemMessageId.CAPTAIN_OF_ROYAL_GUARD_CANNOT_BE_APPOINTED);
			return;
		}
		
		final SubPledge subPledge = player.getClan().getSubPledge(clanName);
		if (subPledge == null || subPledge.getId() == L2Clan.SUBUNIT_ACADEMY)
		{
			player.sendPacket(SystemMessageId.CLAN_NAME_INCORRECT);
			return;
		}
		
		L2Player newLeader = L2World.getInstance().getPlayer(leaderName);
		if (newLeader == null || newLeader.getClan() == null || newLeader.getClan() != clan)
		{
			player.sendMessage(leaderName + " is not in your clan!");
			return;
		}
		
		if (clan.getClanMember(leaderName) == null || (clan.getClanMember(leaderName).getSubPledgeType() != 0))
		{
			if (subPledge.getId() >= L2Clan.SUBUNIT_KNIGHT1)
				player.sendPacket(SystemMessageId.CAPTAIN_OF_ORDER_OF_KNIGHTS_CANNOT_BE_APPOINTED);
			else if (subPledge.getId() >= L2Clan.SUBUNIT_ROYAL1)
				player.sendPacket(SystemMessageId.CAPTAIN_OF_ROYAL_GUARD_CANNOT_BE_APPOINTED);
			
			return;
		}
		
		try
		{
			L2ClanMember oldLeader = clan.getClanMember(subPledge.getLeaderId());
			String oldLeaderName = oldLeader == null ? "" : oldLeader.getName();
			clan.getClanMember(oldLeaderName).setSubPledgeType(0);
			clan.getClanMember(oldLeaderName).updateSubPledgeType();
			clan.getClanMember(oldLeaderName)
					.getPlayerInstance()
					.setPledgeClass(
							L2ClanMember.getCurrentPledgeClass(clan.getClanMember(oldLeaderName).getPlayerInstance()));
			clan.getClanMember(oldLeaderName).getPlayerInstance().setActiveClass(0);
		}
		catch (RuntimeException e)
		{
			_log.warn("", e);
		}
		
		int leaderId = clan.getClanMember(leaderName).getObjectId();
		
		subPledge.setLeaderId(leaderId);
		clan.updateSubPledgeInDB(subPledge.getId());
		L2ClanMember leaderSubPledge = clan.getClanMember(leaderName);
		L2Player subLeader = leaderSubPledge.getPlayerInstance();
		if (subLeader != null)
		{
			subLeader.setPledgeClass(L2ClanMember.getCurrentPledgeClass(subLeader));
			subLeader.sendPacket(new UserInfo(subLeader));
		}
		clan.broadcastClanStatus();
		SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_BEEN_SELECTED_AS_CAPTAIN_OF_S2);
		sm.addString(leaderName);
		sm.addString(clanName);
		clan.broadcastToOnlineMembers(sm);
	}
	
	/*
	 * Check new subclass classId for validity
	 * (villagemaster race/type, is not contains in previous subclasses,
	 * is contains in allowed subclasses)
	 * Base class not added into allowed subclasses.
	 */
	private final boolean isValidNewSubClass(L2Player player, int classId)
	{
		if (!checkVillageMaster(classId))
			return false;
		
		final ClassId cid = ClassId.values()[classId];
		for (Iterator<SubClass> subList = iterSubClasses(player); subList.hasNext();)
		{
			SubClass sub = subList.next();
			ClassId subClassId = sub.getClassDefinition();
			
			if (subClassId.equalsOrChildOf(cid))
				return false;
		}
		
		// get player base class
		final int currentBaseId = player.getBaseClass();
		final ClassId baseCID = ClassId.values()[currentBaseId];
		
		// we need 2nd occupation ID
		final int baseClassId;
		if (baseCID.level() > 2)
			baseClassId = baseCID.getParent().ordinal();
		else
			baseClassId = currentBaseId;
		
		Set<ClassId> availSubs = ClassId.values()[baseClassId].getAvailableSubclasses(player);
		if (availSubs == null || availSubs.isEmpty())
			return false;
		
		for (ClassId pclass : availSubs)
		{
			if (pclass.ordinal() == classId)
			{
				return true;
			}
		}
		
		return false;
	}
	
	/*
	 * Returns true if this classId allowed for master
	 */
	private final boolean checkVillageMaster(int classId)
	{
		return checkVillageMaster(ClassId.values()[classId]);
	}
	
	/*
	 * Returns true if this PlayerClass is allowed for master
	 */
	private final boolean checkVillageMaster(ClassId pclass)
	{
		if (Config.ALT_GAME_SUBCLASS_EVERYWHERE)
			return true;
		
		final Race npcRace = getVillageMasterRace();
		
		switch (npcRace)
		{
		// If the master is human or light elf, ensure that fighter-type
		// masters only teach fighter classes, and priest-type masters
		// only teach priest classes etc.
			case Human:
			case Elf:
				final ClassType npcTeachType = getVillageMasterTeachType();
				if (!pclass.isOfType(npcTeachType))
					return false;
				// Remove any non-human or light elf classes.
				else if (!pclass.isOfRace(Race.Human) && !pclass.isOfRace(Race.Elf))
					return false;
				break;
			// If the master is not human and not light elf,
			// then remove any classes not of the same race as the master.
			default:
				if (!pclass.isOfRace(npcRace))
					return false;
		}
		return true;
	}
	
	private static final String formatClassForDisplay(ClassId classId)
	{
		return CharTemplateTable.getClassNameById(classId.getId());
	}
	
	/*
	 * Returns list of available subclasses
	 * Base class and already used subclasses removed
	 */
	private final Set<ClassId> getAvailableSubClasses(L2Player player)
	{
		// get player base class
		final int currentBaseId = player.getBaseClass();
		final ClassId baseCID = ClassId.values()[currentBaseId];
		
		// we need 2nd occupation ID
		final int baseClassId;
		if (baseCID.level() > 2)
			baseClassId = baseCID.getParent().ordinal();
		else
			baseClassId = currentBaseId;
		
		/**
		 * If the race of your main class is Elf or Dark Elf,
		 * you may not select each class as a subclass to the other class.
		 *
		 * If the race of your main class is Kamael, you may not subclass any other race
		 * If the race of your main class is NOT Kamael, you may not subclass any Kamael class
		 *
		 * You may not select Overlord and Warsmith class as a subclass.
		 *
		 * You may not select a similar class as the subclass.
		 * The occupations classified as similar classes are as follows:
		 *
		 * Treasure Hunter, Plainswalker and Abyss Walker
		 * Hawkeye, Silver Ranger and Phantom Ranger
		 * Paladin, Dark Avenger, Temple Knight and Shillien Knight
		 * Warlocks, Elemental Summoner and Phantom Summoner
		 * Elder and Shillien Elder
		 * Swordsinger and Bladedancer
		 * Sorcerer, Spellsinger and Spellhowler
		 *
		 * Also, Kamael have a special, hidden 4 subclass, the inspector, which can
		 * only be taken if you have already completed the other two Kamael subclasses
		 *
		 */
		Set<ClassId> availSubs = ClassId.values()[baseClassId].getAvailableSubclasses(player);
		
		if (availSubs != null && !availSubs.isEmpty())
		{
			for (Iterator<ClassId> availSub = availSubs.iterator(); availSub.hasNext();)
			{
				ClassId pclass = availSub.next();
				
				// check for the village master
				if (!checkVillageMaster(pclass))
				{
					availSub.remove();
					continue;
				}
				
				// scan for already used subclasses
				for (Iterator<SubClass> subList = iterSubClasses(player); subList.hasNext();)
				{
					SubClass prevSubClass = subList.next();
					ClassId subClassId = prevSubClass.getClassDefinition();
					
					if (subClassId.equalsOrChildOf(pclass))
					{
						availSub.remove();
						break;
					}
				}
			}
		}
		return availSubs;
	}
	
	/**
	 * this displays PledgeSkillList to the player.
	 * @param player
	 */
	public static final void showPledgeSkillList(L2Player player, boolean closable)
	{
		if (player.getClan() == null || !player.isClanLeader())
		{
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			html.setFile("data/html/villagemaster/NotClanLeader.htm");
			player.sendPacket(html);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		L2PledgeSkillLearn[] skills = SkillTreeTable.getInstance().getAvailablePledgeSkills(player);
		AcquireSkillList asl = new AcquireSkillList(AcquireSkillList.SkillType.Clan);
		int counts = 0;
		
		for (L2PledgeSkillLearn s : skills)
		{
			asl.addSkill(s.getId(), s.getLevel(), s.getLevel(), s.getRepCost(), (int)s.getItemCount());
			counts++;
		}
		
		if (counts == 0)
		{
			if (player.getClan().getLevel() < 8)
			{
				SystemMessage sm =
						new SystemMessage(SystemMessageId.DO_NOT_HAVE_FURTHER_SKILLS_TO_LEARN_COME_BACK_WHEN_REACHED_S1);
				if (player.getClan().getLevel() < 5)
					sm.addNumber(5);
				else
					sm.addNumber(player.getClan().getLevel() + 1);
				player.sendPacket(sm);
			}
			else
			{
				NpcHtmlMessage html = new NpcHtmlMessage(1);
				html.setFile("data/html/villagemaster/NoMoreSkills.htm");
				player.sendPacket(html);
			}
			if (closable)
				player.sendPacket(AcquireSkillDone.PACKET);
		}
		else
			player.sendPacket(asl);
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	public final Race getVillageMasterRace()
	{
		String npcClass = getTemplate().getJClass().toLowerCase();
		
		if (npcClass.indexOf("human") > -1)
			return Race.Human;
		
		if (npcClass.indexOf("darkelf") > -1)
			return Race.Darkelf;
		
		if (npcClass.indexOf("elf") > -1)
			return Race.Elf;
		
		if (npcClass.indexOf("orc") > -1)
			return Race.Orc;
		
		if (npcClass.indexOf("dwarf") > -1)
			return Race.Dwarf;
		
		return Race.Kamael;
	}
	
	public final ClassType getVillageMasterTeachType()
	{
		String npcClass = getTemplate().getJClass().toLowerCase();
		
		if (npcClass.indexOf("sanctuary") > -1 || npcClass.indexOf("clergyman") > -1 || npcClass.indexOf("temple") > -1)
			return ClassType.Priest;
		
		if (npcClass.indexOf("mageguild") > -1 || npcClass.indexOf("patriarch") > -1)
			return ClassType.Mystic;
		
		return ClassType.Fighter;
	}
	
	private static final Iterator<SubClass> iterSubClasses(L2Player player)
	{
		return player.getSubClasses().values().iterator();
	}
}
