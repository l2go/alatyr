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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.regex.Pattern;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.datatables.CharTemplateTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.stat.PlayerStat;
import com.l2jfree.gameserver.gameobjects.templates.L2PlayerTemplate;
import com.l2jfree.gameserver.gameobjects.templates.L2PlayerTemplate.PlayerTemplateItem;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.instancemanager.RecommendationManager;
import com.l2jfree.gameserver.model.L2ShortCut;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;
import com.l2jfree.gameserver.network.packets.server.CharSelectionInfo;
import com.l2jfree.gameserver.network.packets.server.CharacterCreateFail;
import com.l2jfree.gameserver.network.packets.server.CharacterCreateSuccess;
import com.l2jfree.gameserver.taskmanager.SQLQueue;

/**
 * This class represents a packet sent by the client when a new character is being created.
 * 
 * @version $Revision: 1.9.2.3.2.8 $ $Date: 2005/03/27 15:29:30 $
 */
public class NewCharacter extends L2ClientPacket
{
	private static final String _C__NEWCHARACTER = "[C] 0C NewCharacter c[sdddddddddddd]";
	private static final Object CREATION_LOCK = new Object();
	
	private String _name;
	//private int				_race;
	private byte _sex;
	private int _classId;
	/*
	private int _int;
	private int _str;
	private int _con;
	private int _men;
	private int _dex;
	private int _wit;
	*/
	private byte _hairStyle;
	private byte _hairColor;
	private byte _face;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
		/*_race  = */readD();
		_sex = (byte)readD();
		_classId = readD();
		/*_int   = */readD();
		/*_str   = */readD();
		/*_con   = */readD();
		/*_men   = */readD();
		/*_dex   = */readD();
		/*_wit   = */readD();
		_hairStyle = (byte)readD();
		_hairColor = (byte)readD();
		_face = (byte)readD();
	}
	
	@Override
	protected void runImpl()
	{
		// Only 1 packet may be executed at a time (prevent multiple names)
		synchronized (CREATION_LOCK)
		{
			int reason = -1;
			if (CharNameTable.getInstance().doesCharNameExist(_name))
			{
				if (_log.isDebugEnabled())
					_log.debug("charname: " + _name + " already exists. creation failed.");
				reason = CharacterCreateFail.REASON_NAME_ALREADY_EXISTS;
			}
			else if (CharNameTable.getInstance().accountCharNumber(getClient().getAccountName()) >= Config.MAX_CHARACTERS_NUMBER_PER_ACCOUNT
					&& Config.MAX_CHARACTERS_NUMBER_PER_ACCOUNT != 0)
			{
				if (_log.isDebugEnabled())
					_log.debug("Max number of characters reached. Creation failed.");
				reason = CharacterCreateFail.REASON_TOO_MANY_CHARACTERS;
			}
			else if (!Config.CNAME_PATTERN.matcher(_name).matches())
			{
				if (_log.isDebugEnabled())
					_log.debug("charname: " + _name + " is invalid. creation failed.");
				reason = CharacterCreateFail.REASON_16_ENG_CHARS;
			}
			else if (NpcTable.getInstance().getTemplateByName(_name) != null || obsceneCheck(_name))
			{
				if (_log.isDebugEnabled())
					_log.debug("charname: " + _name + " overlaps with a NPC. creation failed.");
				reason = CharacterCreateFail.REASON_INCORRECT_NAME;
			}
			
			if (_log.isDebugEnabled())
				_log.debug("charname: " + _name + " classId: " + _classId);
			
			L2PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(_classId);
			if (template == null || template.getClassBaseLevel() > 1)
			{
				sendPacket(new CharacterCreateFail(CharacterCreateFail.REASON_CREATION_FAILED));
				return;
			}
			else if (reason != -1)
			{
				sendPacket(new CharacterCreateFail(reason));
				return;
			}
			
			int objectId = IdFactory.getInstance().getNextId();
			L2Player newChar =
					L2Player.create(objectId, template, getClient().getAccountName(), _name, _hairStyle,
							_hairColor, _face, _sex != 0);
			if (newChar == null)
			{
				// create() reserves the name before the insert. The row was not written, so the
				// name and the account slot have to be released before the id is reused.
				CharNameTable.getInstance().remove(objectId);
				IdFactory.getInstance().releaseId(objectId);
				sendPacket(new CharacterCreateFail(CharacterCreateFail.REASON_CREATION_FAILED));
				return;
			}
			newChar.getStatus().setCurrentHp(template.getBaseHpMax());
			newChar.getStatus().setCurrentCp(template.getBaseCpMax());
			newChar.getStatus().setCurrentMp(template.getBaseMpMax());
			//newChar.setMaxLoad(template.baseLoad);
			
			// send acknowledgement
			sendPacket(CharacterCreateSuccess.PACKET);
			
			initNewChar(newChar);
			sendAF();
		}
	}
	
	private void initNewChar(L2Player newChar)
	{
		if (_log.isDebugEnabled())
			_log.debug("Character init start");
		
		storeCreationDate(newChar);
		
		L2World.getInstance().storeObject(newChar);
		
		L2PlayerTemplate template = newChar.getTemplate();
		
		newChar.addAdena("Init", Config.STARTING_ADENA, null, false);
		
		Location startPos = template.getStartingPosition();
		newChar.getPosition().setXYZInvisible(startPos.getX(), startPos.getY(), startPos.getZ());
		newChar.setTitle("");
		
		newChar.setVitalityPoints(PlayerStat.MAX_VITALITY_POINTS, true);
		
		if (Config.STARTING_LEVEL > 1)
			newChar.getStat().addLevel((byte)(Config.STARTING_LEVEL - 1));
		
		if (Config.STARTING_SP > 0)
			newChar.getStat().addSp(Config.STARTING_SP);
		
		L2ShortCut shortcut;
		//add attack shortcut
		shortcut = new L2ShortCut(0, 0, 3, 2, 0, 1);
		newChar.registerShortCut(shortcut);
		//add take shortcut
		shortcut = new L2ShortCut(3, 0, 3, 5, 0, 1);
		newChar.registerShortCut(shortcut);
		//add sit shortcut
		shortcut = new L2ShortCut(10, 0, 3, 0, 0, 1);
		newChar.registerShortCut(shortcut);
		
		for (PlayerTemplateItem ia : template.getItems())
		{
			L2ItemInstance item = newChar.getInventory().addItem("Init", ia.getItemId(), ia.getAmount(), newChar, null);
			
			// add tutorial guide shortcut
			if (item.getItemId() == 5588)
			{
				shortcut = new L2ShortCut(11, 0, 1, item.getObjectId(), 0, 1);
				newChar.registerShortCut(shortcut);
			}
			
			if (item.isEquipable() && ia.isEquipped())
				newChar.getInventory().equipItemAndRecord(item);
		}
		
		SQLQueue.getInstance().run();
		
		for (L2SkillLearn skill : SkillTreeTable.getInstance().getAvailableSkills(newChar, newChar.getClassId()))
		{
			newChar.addSkill(SkillTable.getInstance().getInfo(skill.getId(), skill.getLevel()), true);
			if (skill.getId() == 1001 || skill.getId() == 1177)
			{
				shortcut = new L2ShortCut(1, 0, 2, skill.getId(), skill.getLevel(), 1);
				newChar.registerShortCut(shortcut);
			}
			if (skill.getId() == 1216)
			{
				shortcut = new L2ShortCut(10, 0, 2, skill.getId(), skill.getLevel(), 1);
				newChar.registerShortCut(shortcut);
			}
			if (_log.isDebugEnabled())
				_log.debug("adding starter skill:" + skill.getId() + " / " + skill.getLevel());
		}
		startTutorialQuest(newChar);
		RecommendationManager.getInstance().onCreate(newChar);
		new Disconnection(getClient(), newChar).store().deleteMe();
		
		// send char list
		sendPacket(new CharSelectionInfo(getClient()));
		if (_log.isDebugEnabled())
			_log.debug("Character init end");
	}
	
	public void startTutorialQuest(L2Player player)
	{
		QuestState qs = player.getQuestState("255_Tutorial");
		Quest q = null;
		if (qs == null)
			q = QuestManager.getInstance().getQuest("255_Tutorial");
		if (q != null)
			q.newQuestState(player);
	}
	
	private final void storeCreationDate(L2Player player)
	{
		Connection con = null;
		try
		{
			Calendar now = Calendar.getInstance();
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO player_birthday (player_id, gift_claimed_year, created_on) VALUES (?,?,?)");
			ps.setInt(1, player.getObjectId());
			ps.setInt(2, now.get(Calendar.YEAR));
			ps.setObject(3, LocalDate.now());
			ps.executeUpdate();
			ps.close();
		}
		catch (Exception e)
		{
			_log.error("Could not store " + player + "'s creation day!", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	private final boolean obsceneCheck(String name)
	{
		for (Pattern pattern : Config.FILTER_LIST)
			if (pattern.matcher(name).find())
				return true;
		return false;
	}
	
	@Override
	public String getType()
	{
		return _C__NEWCHARACTER;
	}
}
