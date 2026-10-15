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
package com.l2jfree.gameserver.model.quest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.model.drop.L2DropData;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.ExShowQuestMark;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.PlaySound;
import com.l2jfree.gameserver.network.packets.server.QuestList;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.TutorialCloseHtml;
import com.l2jfree.gameserver.network.packets.server.TutorialEnableClientEvent;
import com.l2jfree.gameserver.network.packets.server.TutorialShowHtml;
import com.l2jfree.gameserver.network.packets.server.TutorialShowQuestionMark;
import com.l2jfree.tools.random.Rnd;

/**
 * @author Luis Arias
 */
public final class QuestState
{
	protected static Logger _log = LoggerFactory.getLogger(Quest.class);
	
	/** Quest associated to the QuestState */
	private final String _questName;
	
	/** Player who engaged the quest */
	private final L2Player _player;
	
	/** State of the quest */
	private byte _state;
	
	/** List of couples (variable for quest,value of the variable for quest) */
	private Map<String, String> _vars;
	
	/** Boolean flag letting QuestStateManager know to exit quest when cleaning up */
	private boolean _isExitQuestOnCleanUp = false;
	
	/**
	 * Constructor of the QuestState : save the quest in the list of quests of the player.<BR/><BR/>
	 * 
	 * <U><I>Actions :</U></I><BR/>
	 * <LI>Save informations in the object QuestState created (Quest, Player, Completion, State)</LI>
	 * <LI>Add the QuestState in the player's list of quests by using setQuestState()</LI>
	 * <LI>Add drops gotten by the quest</LI>
	 * <BR/>
	 * @param quest : quest associated with the QuestState
	 * @param player : L2Player pointing out the player
	 * @param state : state of the quest
	 * @param completed : boolean for completion of the quest
	 */
	QuestState(Quest quest, L2Player player, byte state)
	{
		_questName = quest.getName();
		_player = player;
		
		// Save the state of the quest for the player in the player's list of quest onwed
		getPlayer().setQuestState(this);
		
		// set the state of the quest
		_state = state;
	}
	
	public String getQuestName()
	{
		return _questName;
	}
	
	/**
	* Return the quest
	* @return Quest
	*/
	public Quest getQuest()
	{
		return QuestManager.getInstance().getQuest(_questName);
	}
	
	/**
	 * Return the L2Player
	 * @return L2Player
	 */
	public L2Player getPlayer()
	{
		return _player;
	}
	
	/**
	 * Return the state of the quest
	 * @return State
	 */
	public byte getState()
	{
		return _state;
	}
	
	/**
	 * Return true if quest completed, false otherwise
	 * @return boolean
	 */
	public boolean isCompleted()
	{
		return (getState() == State.COMPLETED);
	}
	
	/**
	 * Return true if quest started, false otherwise
	 * @return boolean
	 */
	public boolean isStarted()
	{
		return (getState() == State.STARTED);
	}
	
	/**
	 * Return state of the quest after its initialization.<BR><BR>
	 * <U><I>Actions :</I></U>
	 * <LI>Remove drops from previous state</LI>
	 * <LI>Set new state of the quest</LI>
	 * <LI>Add drop for new state</LI>
	 * <LI>Update information in database</LI>
	 * <LI>Send packet QuestList to client</LI>
	 * @param state
	 * @return object
	 */
	public Object setState(byte state)
	{
		// set new state if it is not already in that state
		if (_state != state)
		{
			_state = state;
			Quest.updateQuestInDb(this);
			getPlayer().sendPacket(new QuestList(getPlayer()));
		}
		return state;
	}
	
	/**
	 * Add parameter used in quests.
	 * @param var : String pointing out the name of the variable for quest
	 * @param val : String pointing out the value of the variable for quest
	 * @return String (equal to parameter "val")
	 */
	String setInternal(String var, String val)
	{
		if (_vars == null)
			_vars = new LinkedHashMap<String, String>();
		
		if (val == null)
			val = "";
		
		_vars.put(var, val);
		return val;
	}
	
	/**
	 * Return value of parameter "val" after adding the couple (var,val) in class variable "vars".<BR><BR>
	 * <U><I>Actions :</I></U><BR>
	 * <LI>Initialize class variable "vars" if is null</LI>
	 * <LI>Initialize parameter "val" if is null</LI>
	 * <LI>Add/Update couple (var,val) in class variable Map "vars"</LI>
	 * <LI>If the key represented by "var" exists in Map "vars", the couple (var,val) is updated in the database. The key is known as
	 * existing if the preceding value of the key (given as result of function put()) is not null.<BR>
	 * If the key doesn't exist, the couple is added/created in the database</LI>
	 * @param var : String indicating the name of the variable for quest
	 * @param val : String indicating the value of the variable for quest
	 * @return String (equal to parameter "val")
	 */
	public String set(String var, String val)
	{
		if (_vars == null)
			_vars = new LinkedHashMap<String, String>();
		
		if (val == null)
			val = "";
		
		// Map.put() returns previous value associated with specified key, or null if there was no mapping for key.
		String old = _vars.put(var, val);
		
		if (old != null)
			Quest.updateQuestVarInDb(this, var, val);
		else
			Quest.createQuestVarInDb(this, var, val);
		
		if (var.equals(Quest.CONDITION))
		{
			try
			{
				int previousVal = 0;
				try
				{
					previousVal = Integer.parseInt(old);
				}
				catch (Exception ex)
				{
					previousVal = 0;
				}
				setCond(Integer.parseInt(val), previousVal);
			}
			catch (Exception e)
			{
				_log.info(getPlayer().getName() + ", " + getQuestName() + " cond [" + val
						+ "] is not an integer.  Value stored, but no packet was sent: ", e);
			}
		}
		return val;
	}
	
	public String set(String var, int val)
	{
		return set(var, String.valueOf(val));
	}
	
	/**
	 * Internally handles the progression of the quest so that it is ready for sending
	 * appropriate packets to the client<BR><BR>
	 * <U><I>Actions :</I></U><BR>
	 * <LI>Check if the new progress number resets the quest to a previous (smaller) step</LI>
	 * <LI>If not, check if quest progress steps have been skipped</LI>
	 * <LI>If skipped, prepare the variable completedStateFlags appropriately to be ready for sending to clients</LI>
	 * <LI>If no steps were skipped, flags do not need to be prepared...</LI>
	 * <LI>If the passed step resets the quest to a previous step, reset such that steps after the parameter are not
	 * considered, while skipped steps before the parameter, if any, maintain their info</LI>
	 * @param cond : int indicating the step number for the current quest progress (as will be shown to the client)
	 * @param old : int indicating the previously noted step
	 * 
	 * For more info on the variable communicating the progress steps to the client, please see
	 * @link com.l2jfree.loginserver.serverpacket.QuestList
	 */
	private void setCond(int cond, int old)
	{
		int completedStateFlags = 0; // initializing...
		
		// if there is no change since last setting, there is nothing to do here
		if (cond == old)
			return;
		
		// cond 0 and 1 do not need completedStateFlags.  Also, if cond > 1, the 1st step must
		// always exist (i.e. it can never be skipped).  So if cond is 2, we can still safely
		// assume no steps have been skipped.
		// Finally, more than 31 steps CANNOT be supported in any way with skipping.
		if (cond < 3 || cond > 31)
		{
			unset("__compltdStateFlags");
		}
		else
			completedStateFlags = getInt("__compltdStateFlags");
		
		// case 1: No steps have been skipped so far...
		if (completedStateFlags == 0)
		{
			// check if this step also doesn't skip anything.  If so, no further work is needed
			// also, in this case, no work is needed if the state is being reset to a smaller value
			// in those cases, skip forward to informing the client about the change...
			
			// ELSE, if we just now skipped for the first time...prepare the flags!!!
			if (cond > (old + 1))
			{
				// set the most significant bit to 1 (indicates that there exist skipped states)
				// also, ensure that the least significant bit is an 1 (the first step is never skipped, no matter
				// what the cond says)
				completedStateFlags = 0x80000001;
				
				// since no flag had been skipped until now, the least significant bits must all
				// be set to 1, up until "old" number of bits.
				completedStateFlags |= ((1 << old) - 1);
				
				// now, just set the bit corresponding to the passed cond to 1 (current step)
				completedStateFlags |= (1 << (cond - 1));
				set("__compltdStateFlags", String.valueOf(completedStateFlags));
			}
		}
		// case 2: There were exist previously skipped steps
		else
		{
			// if this is a push back to a previous step, clear all completion flags ahead
			if (cond < old)
			{
				completedStateFlags &= ((1 << cond) - 1); // note, this also unsets the flag indicating that there exist skips
				
				//now, check if this resulted in no steps being skipped any more
				if (completedStateFlags == ((1 << cond) - 1))
					unset("__compltdStateFlags");
				else
				{
					// set the most significant bit back to 1 again, to correctly indicate that this skips states.
					// also, ensure that the least significant bit is an 1 (the first step is never skipped, no matter
					// what the cond says)
					completedStateFlags |= 0x80000001;
					set("__compltdStateFlags", String.valueOf(completedStateFlags));
				}
			}
			// if this moves forward, it changes nothing on previously skipped steps...so just mark this
			// state and we are done
			else
			{
				completedStateFlags |= (1 << (cond - 1));
				set("__compltdStateFlags", String.valueOf(completedStateFlags));
			}
		}
		
		// send a packet to the client to inform it of the quest progress (step change)
		getPlayer().sendPacket(new QuestList(getPlayer()));
		
		int questId = getQuest().getQuestIntId();
		if (questId > 0 && questId < 19999 && cond > 0)
			getPlayer().sendPacket(new ExShowQuestMark(questId));
	}
	
	/**
	 * Remove the variable of quest from the list of variables for the quest.<BR><BR>
	 * <U><I>Concept : </I></U>
	 * Remove the variable of quest represented by "var" from the class variable Map "vars" and from the database.
	 * @param var : String designating the variable for the quest to be deleted
	 * @return String pointing out the previous value associated with the variable "var"
	 */
	public String unset(String var)
	{
		if (_vars == null)
			return null;
		
		String old = _vars.remove(var);
		
		if (old != null)
			Quest.deleteQuestVarInDb(this, var);
		
		return old;
	}
	
	/**
	 * Insert (or Update) in the database variables that need to stay persistant for this player after a reboot.
	 * This function is for storage of values that do not related to a specific quest but are
	 * global for all quests.  For example, player's can get only once the adena and XP reward for
	 * the first class quests, but they can make more than one first class quest.
	 * @param var : String designating the name of the variable for the quest
	 * @param value : String designating the value of the variable for the quest
	 */
	public final void saveGlobalQuestVar(String var, String value)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			statement =
					con.prepareStatement("INSERT INTO player_quest_global_variable (player_id, variable_name, value) VALUES (?,?,?) "
							+ "ON CONFLICT (player_id, variable_name) DO UPDATE SET value = EXCLUDED.value");
			statement.setInt(1, _player.getObjectId());
			statement.setString(2, var);
			statement.setString(3, value == null ? "" : value);
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("could not insert player's global quest variable:", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Read from the database a previously saved variable for this quest.
	 * Due to performance considerations, this function should best be used only when the quest is first loaded.
	 * Subclasses of this class can define structures into which these loaded values can be saved.
	 * However, on-demand usage of this function throughout the script is not prohibited, only not recommended.
	 * Values read from this function were entered by calls to "saveGlobalQuestVar"
	 * @param var : String designating the name of the variable for the quest
	 * @return String : String representing the loaded value for the passed var, or an empty string if the var was invalid
	 */
	
	public final String getGlobalQuestVar(String var)
	{
		String result = "";
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			statement =
					con.prepareStatement("SELECT value FROM player_quest_global_variable WHERE player_id = ? AND variable_name = ?");
			statement.setInt(1, _player.getObjectId());
			statement.setString(2, var);
			ResultSet rs = statement.executeQuery();
			if (rs.next())
				result = rs.getString(1);
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("could not load player's global quest variable:", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		return result;
	}
	
	/**
	 * Permanently delete from the database one of the player's global quest variable that was previously saved.
	 * @param var : String designating the name of the variable
	 */
	public final void deleteGlobalQuestVar(String var)
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement;
			statement = con.prepareStatement("DELETE FROM player_quest_global_variable WHERE player_id = ? AND variable_name = ?");
			statement.setInt(1, _player.getObjectId());
			statement.setString(2, var);
			statement.executeUpdate();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("could not delete player's global quest variable:", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Return the value of the variable of quest represented by "var"
	 * @param var : name of the variable of quest
	 * @return Object
	 */
	public Object get(String var)
	{
		if (_vars == null)
			return null;
		
		return _vars.get(var);
	}
	
	/**
	 * Return the value of the variable of quest represented by "var"
	 * @param var : String designating the variable for the quest
	 * @return int
	 */
	public int getInt(String var)
	{
		int varint = 0;
		
		try
		{
			varint = Integer.parseInt(_vars.get(var));
		}
		catch (Exception e)
		{
			if (_log.isDebugEnabled())
				_log.info(getPlayer().getName() + ": variable " + var + " isn't an integer: " + varint, e);
			//	    if (Config.AUTODELETE_INVALID_QUEST_DATA)
			//		exitQuest(true);
		}
		
		return varint;
	}
	
	/**
	 * Add player to get notification of characters death
	 * @param character : L2Creature of the character to get notification of death
	 */
	public void addNotifyOfDeath(L2Creature character)
	{
		if (character == null || !(character instanceof L2Player))
			return;
		
		((L2Player)character).addNotifyQuestOfDeath(this);
	}
	
	/**
	 * Return the quantity of one sort of item hold by the player
	 * @param itemId : ID of the item wanted to be count
	 * @return int
	 */
	public long getQuestItemsCount(int itemId)
	{
		long count = 0;
		
		for (L2ItemInstance item : getPlayer().getInventory().getItems())
			if (item.getItemId() == itemId)
				count += item.getCount();
		
		return count;
	}
	
	/**
	 * Return the level of enchantment on the weapon of the player(Done specifically for weapon SA's)
	 * @param itemId : ID of the item to check enchantment
	 * @return int
	 */
	public int getEnchantLevel(int itemId)
	{
		L2ItemInstance enchanteditem = getPlayer().getInventory().getItemByItemId(itemId);
		
		if (enchanteditem == null)
			return 0;
		
		return enchanteditem.getEnchantLevel();
	}
	
	/**
	 * Give item to the player
	 * @param itemId
	 * @param count
	 */
	public void giveItems(int itemId, long count)
	{
		giveItems(itemId, count, 0);
	}
	
	public synchronized void giveItems(int itemId, long count, int enchantlevel)
	{
		if (count <= 0)
			return;
		
		// Add items to player's inventory
		L2ItemInstance item =
				getPlayer().getInventory().addItem(Quest.QUEST, itemId, count, getPlayer(), getPlayer().getTarget());
		
		if (item == null)
			return;
		if (enchantlevel > 0)
			item.setEnchantLevel(enchantlevel);
		
		// If item for reward is gold, send message of gold reward to client
		if (itemId == PlayerInventory.ADENA_ID)
		{
			SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S1_ADENA);
			smsg.addItemNumber(count);
			getPlayer().sendPacket(smsg);
		}
		// Otherwise, send message of object reward to client
		else
		{
			if (count > 1)
			{
				SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
				smsg.addItemName(item);
				smsg.addItemNumber(count);
				getPlayer().sendPacket(smsg);
			}
			else
			{
				SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S1);
				smsg.addItemName(item);
				getPlayer().sendPacket(smsg);
			}
		}
		getPlayer().getInventory().updateInventory(item);
	}
	
	public void giveItems(int itemId, long count, byte attributeId, int attributeLevel)
	{
		if (count <= 0)
			return;
		
		// Add items to player's inventory
		L2ItemInstance item =
				getPlayer().getInventory().addItem(Quest.QUEST, itemId, count, getPlayer(), getPlayer().getTarget());
		
		if (item == null)
			return;
		
		// set enchant level for item if that item is not adena
		if (attributeId >= 0 && attributeLevel > 0)
		{
			item.setElementAttr(attributeId, attributeLevel);
			if (item.isEquipped())
				item.updateElementAttrBonus(getPlayer());
			
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(item);
			getPlayer().sendPacket(iu);
		}
		
		// If item for reward is gold, send message of gold reward to client
		if (itemId == PlayerInventory.ADENA_ID)
		{
			SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S1_ADENA);
			smsg.addItemNumber(count);
			getPlayer().sendPacket(smsg);
		}
		// Otherwise, send message of object reward to client
		else
		{
			if (count > 1)
			{
				SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
				smsg.addItemName(item);
				smsg.addItemNumber(count);
				getPlayer().sendPacket(smsg);
			}
			else
			{
				SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S1);
				smsg.addItemName(item);
				getPlayer().sendPacket(smsg);
			}
		}
		getPlayer().getInventory().updateInventory(item);
	}
	
	/**
	 * Give reward to the player
	 * @param itemId
	 * @param count
	 */
	public void rewardItems(int itemId, long count)
	{
		rewardItems(itemId, count, 0);
	}
	
	public void rewardItems(int itemId, long count, int enchantlevel)
	{
		if (count <= 0)
			return;
		
		if (itemId == PlayerInventory.ADENA_ID)
			count = (long)(count * Config.RATE_QUESTS_REWARD_ADENA);
		else if (!Config.COMPENSATE_QUEST_ITEM_REWARDS)
			count = (long)(count * Config.RATE_QUESTS_REWARD_ITEMS);
		
		// Add items to player's inventory
		L2ItemInstance item =
				getPlayer().getInventory().addItem(Quest.QUEST, itemId, count, getPlayer(), getPlayer().getTarget());
		if (item == null)
			return;
		else if (enchantlevel > 0)
			item.setEnchantLevel(enchantlevel);
		else if (Config.COMPENSATE_QUEST_ITEM_REWARDS && !item.isStackable()
				&& item.getItem().getType2() != L2Item.TYPE2_QUEST)
		{
			long price = item.getReferencePrice();
			if (price > 0)
				getPlayer().addAdena(Quest.QUEST, (long)(price * (count * Config.RATE_QUESTS_REWARD_ITEMS - count)),
						getPlayer().getTarget(), true);
		}
		
		// If item for reward is gold, send message of gold reward to client
		if (itemId == PlayerInventory.ADENA_ID)
		{
			SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S1_ADENA);
			smsg.addItemNumber(count);
			getPlayer().sendPacket(smsg);
		}
		// Otherwise, send message of object reward to client
		else
		{
			if (count > 1)
			{
				SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
				smsg.addItemName(item);
				smsg.addItemNumber(count);
				getPlayer().sendPacket(smsg);
			}
			else
			{
				SystemMessage smsg = new SystemMessage(SystemMessageId.EARNED_S1);
				smsg.addItemName(item);
				getPlayer().sendPacket(smsg);
			}
		}
		getPlayer().getInventory().updateInventory(item);
	}
	
	/**
	 * Drop Quest item using Config.RATE_DROP_QUEST
	 * @param itemId Item Identifier of the item to be dropped
	 * @param count Quantity of items to be dropped
	 * @param neededCount Quantity of items needed for quest
	 * @param dropChance Base chance of drop, same as in droplist
	 * @param sound indicating whether to play sound
	 * @return whether player has requested number of items
	 */
	public boolean dropQuestItems(int itemId, long count, long neededCount, int dropChance, boolean sound)
	{
		return dropQuestItems(itemId, count, neededCount, dropChance, sound, true);
	}
	
	public boolean
			dropQuestItems(int itemId, long count, long neededCount, int dropChance, boolean sound, boolean party)
	{
		return dropQuestItems(itemId, count, count, neededCount, dropChance, sound, party);
	}
	
	public boolean dropQuestItems(int itemId, long minCount, long maxCount, long neededCount, int dropChance,
			boolean sound)
	{
		return dropQuestItems(itemId, minCount, maxCount, neededCount, dropChance, sound, true);
	}
	
	public boolean dropQuestItems(int itemId, long minCount, long maxCount, long neededCount, int dropChance,
			boolean sound, boolean party)
	{
		dropChance = (int) (dropChance * Config.RATE_DROP_QUEST);
		if (party)
		{
			L2Party p = getPlayer().getParty();
			if (p != null)
				dropChance /= getPlayer().getParty().getMemberCount();
		}
		
		long currentCount = getQuestItemsCount(itemId);
		if (currentCount >= neededCount)
			return true;
		
		long itemCount = 0;
		int random = Rnd.get(L2DropData.MAX_CHANCE);
		
		while (random < dropChance)
		{
			// Get the item quantity dropped
			if (minCount < maxCount)
				itemCount += Rnd.get(minCount, maxCount);
			else if (minCount == maxCount)
				itemCount += minCount;
			else
				itemCount++;
			
			// Prepare for next iteration if dropChance > L2DropData.MAX_CHANCE
			dropChance -= L2DropData.MAX_CHANCE;
		}
		
		if (itemCount > 0)
		{
			// if over neededCount, just fill the gap
			if (neededCount > 0 && currentCount + itemCount > neededCount)
				itemCount = neededCount - currentCount;
			
			// Inventory slot check
			if (!getPlayer().getInventory().validateCapacityByItemId(itemId))
				return false;
			
			// Give the item to Player
			getPlayer().addItem(Quest.QUEST, itemId, itemCount, getPlayer().getTarget(), true);
			
			if (sound)
				sendPacket((currentCount + itemCount < neededCount) ? Quest.SND_ITEM_GET : Quest.SND_MIDDLE);
		}
		
		return (neededCount > 0 && currentCount + itemCount >= neededCount);
	}
	
	//TODO: More radar functions need to be added when the radar class is complete.
	// BEGIN STUFF THAT WILL PROBABLY BE CHANGED
	public void addRadar(int x, int y, int z)
	{
		getPlayer().getRadar().addMarker(x, y, z);
	}
	
	public void removeRadar(int x, int y, int z)
	{
		getPlayer().getRadar().removeMarker(x, y, z);
	}
	
	public void clearRadar()
	{
		getPlayer().getRadar().removeAllMarkers();
	}
	
	// END STUFF THAT WILL PROBABLY BE CHANGED
	
	/**
	 * Remove items from player's inventory when talking to NPC in order to have rewards.<BR><BR>
	 * <U><I>Actions :</I></U>
	 * <LI>Destroy quantity of items wanted</LI>
	 * <LI>Send new inventory list to player</LI>
	 * @param itemId : Identifier of the item
	 * @param count : Quantity of items to destroy
	 */
	public void takeItems(int itemId, long count)
	{
		// Get object item from player's inventory list
		L2ItemInstance item = getPlayer().getInventory().getItemByItemId(itemId);
		if (item == null || count == 0)
			return;
		
		// Tests on count value in order not to have negative value
		if (count < 0 || count > item.getCount())
			count = item.getCount();
		
		// Destroy the quantity of items wanted
		if (itemId == PlayerInventory.ADENA_ID)
			getPlayer().reduceAdena(Quest.QUEST, count, getPlayer(), true);
		else
		{
			if (item.isEquipped())
			{
				L2ItemInstance[] unequiped =
						getPlayer().getInventory().unEquipItemInBodySlotAndRecord(item.getItem().getBodyPart());
				InventoryUpdate iu = new InventoryUpdate();
				for (L2ItemInstance itm : unequiped)
					iu.addModifiedItem(itm);
				getPlayer().sendPacket(iu);
				getPlayer().broadcastUserInfo();
			}
			getPlayer().destroyItemByItemId(Quest.QUEST, itemId, count, getPlayer(), true);
		}
	}
	
	/**
	 * Send a packet in order to play sound at client terminal
	 * @param sound
	 */
	public void playSound(String sound)
	{
		sendPacket(new PlaySound(0, sound));
	}
	
	/**
	 * Send a packet in order to play sound at client terminal
	 * @param sound
	 */
	public void sendPacket(L2ServerPacket packet)
	{
		getPlayer().sendPacket(packet);
	}
	
	/**
	 * Add XP and SP as quest reward
	 * @param exp
	 * @param sp
	 */
	public void addExpAndSp(int exp, int sp)
	{
		getPlayer().addExpAndSp(
				(int)getPlayer().calcStat(Stats.EXPSP_RATE, exp * Config.RATE_QUESTS_REWARD_EXPSP, null, null),
				(int)getPlayer().calcStat(Stats.EXPSP_RATE, sp * Config.RATE_QUESTS_REWARD_EXPSP, null, null));
	}
	
	/**
	 * Return random value
	 * @param max : max value for randomisation
	 * @return int
	 */
	public int getRandom(int max)
	{
		return Rnd.get(max);
	}
	
	/**
	 * Return number of ticks from GameTimeController
	 * @return int
	 */
	public int getItemEquipped(int loc)
	{
		return getPlayer().getInventory().getPaperdollItemId(loc);
	}
	
	/**
	 * Return the number of ticks from the GameTimeController
	 * @return int
	 */
	public int getGameTicks()
	{
		return GameTimeManager.getGameTicks();
	}
	
	/**
	 * Return true if quest is to exited on clean up by QuestStateManager
	 * @return boolean
	 */
	public final boolean isExitQuestOnCleanUp()
	{
		return _isExitQuestOnCleanUp;
	}
	
	/**
	 * Return the QuestTimer object with the specified name
	 * @return QuestTimer<BR> Return null if name does not exist
	 */
	public void setIsExitQuestOnCleanUp(boolean isExitQuestOnCleanUp)
	{
		_isExitQuestOnCleanUp = isExitQuestOnCleanUp;
	}
	
	/**
	 * Start a timer for quest.<BR><BR>
	 * @param name<BR> The name of the timer. Will also be the value for event of onEvent
	 * @param time<BR> The milisecond value the timer will elapse
	 */
	public void startQuestTimer(String name, long time)
	{
		getQuest().startQuestTimer(name, time, null, getPlayer(), false);
	}
	
	public void startQuestTimer(String name, long time, L2Npc npc)
	{
		getQuest().startQuestTimer(name, time, npc, getPlayer(), false);
	}
	
	public void startRepeatingQuestTimer(String name, long time)
	{
		getQuest().startQuestTimer(name, time, null, getPlayer(), true);
	}
	
	public void startRepeatingQuestTimer(String name, long time, L2Npc npc)
	{
		getQuest().startQuestTimer(name, time, npc, getPlayer(), true);
	}
	
	/**
	 * Return the QuestTimer object with the specified name
	 * @return QuestTimer<BR> Return null if name does not exist
	 */
	public final QuestTimer getQuestTimer(String name)
	{
		return getQuest().getQuestTimer(name, null, getPlayer());
	}
	
	/**
	 * Add spawn for player instance
	 * Return object id of newly spawned npc
	 */
	public L2Npc addSpawn(int npcId)
	{
		return addSpawn(npcId, getPlayer().getX(), getPlayer().getY(), getPlayer().getZ(), 0, false, 0);
	}
	
	public L2Npc addSpawn(int npcId, int despawnDelay)
	{
		return addSpawn(npcId, getPlayer().getX(), getPlayer().getY(), getPlayer().getZ(), 0, false, despawnDelay);
	}
	
	public L2Npc addSpawn(int npcId, int x, int y, int z)
	{
		return addSpawn(npcId, x, y, z, 0, false, 0);
	}
	
	/**
	 * Add spawn for player instance
	 * Will despawn after the spawn length expires
	 * Uses player's coords and heading.
	 * Adds a little randomization in the x y coords
	 * Return object id of newly spawned npc
	 */
	public L2Npc addSpawn(int npcId, L2Creature cha)
	{
		return addSpawn(npcId, cha, true, 0);
	}
	
	public L2Npc addSpawn(int npcId, L2Creature cha, int despawnDelay)
	{
		return addSpawn(npcId, cha.getX(), cha.getY(), cha.getZ(), cha.getHeading(), true, despawnDelay);
	}
	
	/**
	 * Add spawn for player instance
	 * Will despawn after the spawn length expires
	 * Return object id of newly spawned npc
	 */
	public L2Npc addSpawn(int npcId, int x, int y, int z, int despawnDelay)
	{
		return addSpawn(npcId, x, y, z, 0, false, despawnDelay);
	}
	
	/**
	 * Add spawn for player instance
	 * Inherits coords and heading from specified L2Creature instance.
	 * It could be either the player, or any killed/attacked mob
	 * Return object id of newly spawned npc
	 */
	public L2Npc addSpawn(int npcId, L2Creature cha, boolean randomOffset, int despawnDelay)
	{
		return addSpawn(npcId, cha.getX(), cha.getY(), cha.getZ(), cha.getHeading(), randomOffset, despawnDelay);
	}
	
	/**
	 * Add spawn for player instance
	 * Return object id of newly spawned npc
	 */
	public L2Npc addSpawn(int npcId, int x, int y, int z, int heading, boolean randomOffset, int despawnDelay)
	{
		return getQuest().addSpawn(npcId, x, y, z, heading, randomOffset, despawnDelay, false, 0);
	}
	
	public L2Npc addSpawn(int npcId, int x, int y, int z, int heading, boolean randomOffset, int despawnDelay,
			boolean isSummonSpawn)
	{
		return getQuest().addSpawn(npcId, x, y, z, heading, randomOffset, despawnDelay, isSummonSpawn, 0);
	}
	
	/**
	 * Add spawn for player instance
	 * Return object id of newly spawned npc
	 */
	public L2Npc addSpawn(int npcId, int x, int y, int z, int heading, boolean randomOffset, int despawnDelay,
			boolean isSummonSpawn, int instanceId)
	{
		return getQuest().addSpawn(npcId, x, y, z, heading, randomOffset, despawnDelay, isSummonSpawn, instanceId);
	}
	
	public String showHtmlFile(String fileName)
	{
		return getQuest().showHtmlFile(getPlayer(), fileName);
	}
	
	/**
	 * Destroy element used by quest when quest is exited
	 * @param repeatable
	 * @return QuestState
	 */
	public QuestState exitQuest(boolean repeatable)
	{
		// remove this quest from the notifyDeath list of this character if its on this list
		_player.removeNotifyQuestOfDeath(this);
		
		if (isCompleted())
			return this;
		
		// Say quest is completed
		setState(State.COMPLETED);
		
		// Clean registered quest items
		int[] itemIdList = getQuest().getRegisteredItemIds();
		if (itemIdList != null)
		{
			for (int finalElement : itemIdList)
				takeItems(finalElement, -1);
		}
		
		// If quest is repeatable, delete quest from list of quest of the player and from database (quest CAN be created again => repeatable)
		if (repeatable)
		{
			getPlayer().delQuestState(getQuestName());
			Quest.deleteQuestInDb(this);
			
			_vars = null;
		}
		else
		{
			// Otherwise, delete variables for quest and update database (quest CANNOT be created again => not repeatable)
			if (_vars != null)
			{
				// unset() removes from the map, so walk a copy
				for (String var : new ArrayList<String>(_vars.keySet()))
					unset(var);
			}
			
			Quest.updateQuestInDb(this);
		}
		
		return this;
	}
	
	public void showQuestionMark(int number)
	{
		getPlayer().sendPacket(new TutorialShowQuestionMark(number));
	}
	
	public void playTutorialVoice(String voice)
	{
		getPlayer().sendPacket(new PlaySound(2, voice));
	}
	
	public void showTutorialHTML(String html)
	{
		String text = HtmCache.getInstance().getHtm("data/scripts/quests/255_Tutorial/" + html);
		if (text == null)
		{
			_log.warn("Missing html page data/scripts/quests/255_Tutorial/" + html);
			text =
					"<html><body>File data/scripts/quests/255_Tutorial/" + html
							+ " not found or file is empty.</body></html>";
		}
		getPlayer().sendPacket(new TutorialShowHtml(text));
	}
	
	public void closeTutorialHtml()
	{
		getPlayer().sendPacket(new TutorialCloseHtml());
	}
	
	public void onTutorialClientEvent(int number)
	{
		getPlayer().sendPacket(new TutorialEnableClientEvent(number));
	}
	
	public void dropItem(L2MonsterInstance npc, L2Player player, int itemId, int count)
	{
		npc.dropItem(player, itemId, count);
	}
}
