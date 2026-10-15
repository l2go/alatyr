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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.MultisellTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;

/**
 * Olympiad Npc's Instance
 * 
 * @author godson
 */
public class L2OlympiadManagerInstance extends L2Npc
{
	private static final int GATE_PASS = Config.ALT_OLY_BATTLE_REWARD_ITEM;
	
	private static final String FEWER_THAN = "Fewer than" + String.valueOf(Config.ALT_OLY_REG_DISPLAY);
	private static final String MORE_THAN = "More than" + String.valueOf(Config.ALT_OLY_REG_DISPLAY);
	
	public L2OlympiadManagerInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (command.startsWith("OlympiadDesc"))
		{
			int val = Integer.parseInt(command.substring(13, 14));
			String suffix = command.substring(14);
			showChatWindow(player, val, suffix);
		}
		else if (command.startsWith("OlympiadNoble"))
		{
			if (!player.isNoble() || player.getClassId().level() < 3)
				return;
			
			int passes;
			int val = Integer.parseInt(command.substring(14));
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			
			switch (val)
			{
				case 1:
					Olympiad.getInstance().unRegisterNoble(player);
					break;
				case 2:
					int classed = 0;
					int nonClassed = 0;
					int[] array = Olympiad.getInstance().getWaitingList();
					
					if (array != null)
					{
						classed = array[0];
						nonClassed = array[1];
					}
					html.setFile(Olympiad.OLYMPIAD_HTML_PATH + "noble_registered.htm");
					if (Config.ALT_OLY_REG_DISPLAY > 0)
					{
						html.replace("%listClassed%", classed < Config.ALT_OLY_REG_DISPLAY ? FEWER_THAN : MORE_THAN);
						html.replace("%listNonClassedTeam%", FEWER_THAN);
						html.replace("%listNonClassed%", nonClassed < Config.ALT_OLY_REG_DISPLAY ? FEWER_THAN
								: MORE_THAN);
					}
					else
					{
						html.replace("%listClassed%", String.valueOf(classed));
						html.replace("%listNonClassedTeam%", "0");
						html.replace("%listNonClassed%", String.valueOf(nonClassed));
					}
					html.replace("%objectId%", String.valueOf(getObjectId()));
					player.sendPacket(html);
					break;
				case 3:
					int points = Olympiad.getInstance().getNoblePoints(player.getObjectId());
					html.setFile(Olympiad.OLYMPIAD_HTML_PATH + "noble_points1.htm");
					html.replace("%points%", String.valueOf(points));
					html.replace("%objectId%", String.valueOf(getObjectId()));
					player.sendPacket(html);
					break;
				case 4:
					Olympiad.getInstance().registerNoble(player, false);
					break;
				case 5:
					Olympiad.getInstance().registerNoble(player, true);
					break;
				case 6:
					passes = Olympiad.getInstance().getNoblessePasses(player, false);
					if (passes > 0)
					{
						html.setFile(Olympiad.OLYMPIAD_HTML_PATH + "noble_settle.htm");
						html.replace("%objectId%", String.valueOf(getObjectId()));
						player.sendPacket(html);
					}
					else
					{
						html.setFile(Olympiad.OLYMPIAD_HTML_PATH + "noble_nopoints.htm");
						html.replace("%objectId%", String.valueOf(getObjectId()));
						player.sendPacket(html);
					}
					break;
				case 7:
					MultisellTable.getInstance().separateAndSend(102, player, getNpcId(), false, getCastle().getTaxRate());
					break;
				case 8:
					MultisellTable.getInstance().separateAndSend(103, player, getNpcId(), false, getCastle().getTaxRate());
					break;
				case 9:
					int point = Olympiad.getInstance().getLastNobleOlympiadPoints(player.getObjectId());
					html.setFile(Olympiad.OLYMPIAD_HTML_PATH + "noble_points2.htm");
					html.replace("%points%", String.valueOf(point));
					html.replace("%objectId%", String.valueOf(getObjectId()));
					player.sendPacket(html);
					break;
				case 10:
					passes = Olympiad.getInstance().getNoblessePasses(player, true);
					if (passes > 0)
					{
						L2ItemInstance item =
								player.getInventory().addItem("Olympiad", GATE_PASS, passes, player, this);
						
						InventoryUpdate iu = new InventoryUpdate();
						iu.addModifiedItem(item);
						player.sendPacket(iu);
						
						SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
						sm.addItemName(item);
						sm.addItemNumber(passes);
						player.sendPacket(sm);
					}
					break;
				default:
					_log.warn("Olympiad System: Couldnt send packet for request " + val);
					break;
			}
		}
		else if (command.startsWith("OlyBuff"))
		{
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			String[] params = command.split(" ");
			int skillId = Integer.parseInt(params[1]);
			int skillLvl;
			
			// Oly buff whitelist prevents bypass exploiters -.-
			HashMap<Integer, Integer> buffList = new HashMap<Integer, Integer>();
			buffList.put(4357, 2); // Haste Lv2
			buffList.put(4342, 2); // Wind Walk Lv2
			buffList.put(4356, 3); // Empower Lv3
			buffList.put(4355, 3); // Acumen Lv3
			buffList.put(4351, 6); // Concentration Lv6
			buffList.put(4345, 3); // Might Lv3
			buffList.put(4358, 3); // Guidance Lv3
			buffList.put(4359, 3); // Focus Lv3
			buffList.put(4360, 3); // Death Whisper Lv3
			buffList.put(4352, 2); // Berserk Spirit Lv2
			
			// Lets check on our oly buff whitelist
			if (!buffList.containsKey(skillId))
				return;
			
			// Get skilllevel from the hashmap
			skillLvl = buffList.get(skillId);
			
			L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLvl);
			
			setTarget(player);
			
			if (player.olyBuff > 0)
			{
				broadcastPacket(new MagicSkillUse(this, player, skill, 0, 0));
				skill.getEffects(player, player);
				player.olyBuff--;
			}
			
			if (player.olyBuff > 0)
			{
				html.setFile(Olympiad.OLYMPIAD_HTML_PATH
						+ (player.olyBuff == 5 ? "olympiad_buffs.htm" : "olympiad_5buffs.htm"));
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
			}
			else
			{
				html.setFile(Olympiad.OLYMPIAD_HTML_PATH + "olympiad_nobuffs.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
				deleteMe();
			}
		}
		else if (command.startsWith("Olympiad"))
		{
			int val = Integer.parseInt(command.substring(9, 10));
			
			NpcHtmlMessage reply = new NpcHtmlMessage(getObjectId());
			
			switch (val)
			{
				case 1:
					Map<Integer, String> matches = Olympiad.getInstance().getMatchList();
					reply.setFile(Olympiad.OLYMPIAD_HTML_PATH + "olympiad_observe1.htm");
					
					for (int i = 0; i < Olympiad.getStadiumCount(); i++)
					{
						int arenaID = i + 1;
						
						// &$906; -> \\&\\$906;
						reply.replace("%title" + arenaID + "%", matches.containsKey(i) ? matches.get(i) : "\\&\\$906;");
					}
					reply.replace("%objectId%", String.valueOf(getObjectId()));
					player.sendPacket(reply);
					break;
				case 2:
					// for example >> Olympiad 1_88
					int classId = Integer.parseInt(command.substring(11));
					if ((classId >= 88 && classId <= 118) || (classId >= 131 && classId <= 134) || classId == 136)
					{
						List<String> names = Olympiad.getInstance().getClassLeaderBoard(classId);
						reply.setFile(Olympiad.OLYMPIAD_HTML_PATH + "olympiad_ranking.htm");
						
						int index = 1;
						for (String name : names)
						{
							reply.replace("%place" + index + "%", String.valueOf(index));
							reply.replace("%rank" + index + "%", name);
							index++;
							if (index > 10)
								break;
						}
						for (; index <= 10; index++)
						{
							reply.replace("%place" + index + "%", "");
							reply.replace("%rank" + index + "%", "");
						}
						
						reply.replace("%objectId%", String.valueOf(getObjectId()));
						player.sendPacket(reply);
					}
					break;
				case 3:
					int id = Integer.parseInt(command.substring(11));
					Olympiad.addSpectator(id, player, true);
					break;
				default:
					_log.warn("Olympiad System: Couldnt send packet for request " + val);
					break;
			}
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	private void showChatWindow(L2Player player, int val, String suffix)
	{
		String filename = Olympiad.OLYMPIAD_HTML_PATH;
		
		filename += "noble_desc" + val;
		filename += (suffix != null) ? suffix + ".htm" : ".htm";
		
		if (filename.equals(Olympiad.OLYMPIAD_HTML_PATH + "noble_desc0.htm"))
			filename = Olympiad.OLYMPIAD_HTML_PATH + "noble_main.htm";
		
		showChatWindow(player, filename);
	}
}
