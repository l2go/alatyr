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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.sevensigns.SevenSignsFestival;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;

/**
 * L2FestivalMonsterInstance
 * This class manages all attackable festival NPCs, spawned during the Festival of Darkness.
 * 
 * @author Tempy
 */

public class L2FestivalMonsterInstance extends L2MonsterInstance
{
	private final boolean _isArcher;
	private final boolean _isChest;
	protected int _bonusMultiplier = 1;
	
	/**
	 * Constructor of L2FestivalMonsterInstance (use L2Creature and L2NpcInstance constructor).<BR><BR>
	 * 
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Call the L2Creature constructor to set the _template of the L2FestivalMonsterInstance (copy skills from template to object and link _calculators to NPC_STD_CALCULATOR) </li>
	 * <li>Set the name of the L2MonsterInstance</li>
	 * <li>Create a RandomAnimation Task that will be launched after the calculated delay if the server allow it </li><BR><BR>
	 * 
	 * @param objectId Identifier of the object to initialized
	 * @param template Template to apply to the NPC
	 */
	public L2FestivalMonsterInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		_isArcher = SevenSignsFestival.isFestivalArcher(getTemplate().getNpcId());
		_isChest = SevenSignsFestival.isFestivalChest(getTemplate().getNpcId());
	}
	
	public void setOfferingBonus(int bonusMultiplier)
	{
		_bonusMultiplier = bonusMultiplier;
	}
	
	/**
	 * Return True if the attacker is not another L2FestivalMonsterInstance.<BR><BR>
	 */
	@Override
	public boolean isAutoAttackable(L2Creature attacker)
	{
		return !(attacker instanceof L2FestivalMonsterInstance);
	}
	
	/**
	 * All mobs in the festival are aggressive, and have high aggro range.
	 */
	@Override
	public boolean isAggressive()
	{
		return true;
	}
	
	@Override
	public int getAggroRange()
	{
		if (_isArcher)
			return Config.ALT_FESTIVAL_ARCHER_AGGRO;
		
		if (_isChest)
			return Config.ALT_FESTIVAL_CHEST_AGGRO;
		
		return Config.ALT_FESTIVAL_MONSTER_AGGRO;
	}
	
	/**
	 * All mobs in the festival really don't need random animation.
	 */
	@Override
	public boolean hasRandomAnimation()
	{
		return false;
	}
	
	/**
	 * Actions:
	 * <li>Check if the killing object is a player, and then find the party they belong to.</li>
	 * <li>Add a blood offering item to the leader of the party.</li>
	 * <li>Update the party leader's inventory to show the new item addition.</li>
	 */
	@Override
	public void doItemDrop(L2Creature lastAttacker)
	{
		L2Player killingChar = null;
		
		if (!(lastAttacker instanceof L2Player))
			return;
		
		killingChar = (L2Player)lastAttacker;
		L2Party associatedParty = killingChar.getParty();
		
		if (associatedParty == null)
			return;
		
		L2Player partyLeader = associatedParty.getPartyMembers().get(0);
		L2ItemInstance addedOfferings =
				partyLeader.getInventory().addItem("Sign", SevenSignsFestival.FESTIVAL_OFFERING_ID, _bonusMultiplier,
						partyLeader, this);
		
		InventoryUpdate iu = new InventoryUpdate();
		
		if (addedOfferings.getCount() != _bonusMultiplier)
			iu.addModifiedItem(addedOfferings);
		else
			iu.addNewItem(addedOfferings);
		
		partyLeader.sendPacket(iu);
		
		super.doItemDrop(lastAttacker); // Normal drop
	}
}
