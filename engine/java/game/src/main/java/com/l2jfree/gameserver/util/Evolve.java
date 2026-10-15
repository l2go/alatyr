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
package com.l2jfree.gameserver.util;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.SummonItemsData;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2SummonItem;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;

public final class Evolve
{
	public static final boolean doEvolve(L2Player player, L2Npc npc, int itemIdtake, int itemIdgive, int petminlvl)
	{
		if (itemIdtake == 0 || itemIdgive == 0 || petminlvl == 0)
			return false;
		
		L2Summon summon = player.getPet();
		
		if (summon == null || !(summon instanceof L2PetInstance))
			return false;
		
		L2PetInstance currentPet = (L2PetInstance)summon;
		
		if (currentPet.isAlikeDead())
		{
			Util.handleIllegalPlayerAction(player, "Player " + player.getName() + " tried to use death pet exploit!");
			return false;
		}
		
		L2ItemInstance item = null;
		String oldname = currentPet.getName();
		int oldX = currentPet.getX();
		int oldY = currentPet.getY();
		int oldZ = currentPet.getZ();
		
		L2SummonItem olditem = SummonItemsData.getInstance().getSummonItem(itemIdtake);
		
		if (olditem == null)
			return false;
		
		int oldnpcID = olditem.getNpcId();
		
		if (currentPet.getStat().getLevel() < petminlvl || currentPet.getNpcId() != oldnpcID)
			return false;
		
		L2SummonItem sitem = SummonItemsData.getInstance().getSummonItem(itemIdgive);
		
		if (sitem == null)
			return false;
		
		int npcID = sitem.getNpcId();
		
		if (npcID == 0)
			return false;
		
		L2NpcTemplate npcTemplate = NpcTable.getInstance().getTemplate(npcID);
		
		currentPet.unSummon(player);
		
		//deleting old pet item
		currentPet.destroyControlItem(player, true);
		
		item = player.getInventory().addItem("Evolve", itemIdgive, 1, player, npc);
		
		//Summoning new pet
		L2PetInstance petSummon = L2PetInstance.spawnPet(npcTemplate, player, item);
		
		if (petSummon == null)
			return false;
		
		petSummon.getStat().setExp(petSummon.getExpForThisLevel());
		petSummon.getStatus().setCurrentHp(petSummon.getMaxHp());
		petSummon.getStatus().setCurrentMp(petSummon.getMaxMp());
		petSummon.setCurrentFed(petSummon.getMaxFed());
		petSummon.setTitle(player.getName());
		petSummon.setName(oldname);
		petSummon.setRunning();
		petSummon.store();
		
		player.setPet(petSummon);
		
		player.sendPacket(new MagicSkillUse(npc, 2046, 1, 1000, 600000));
		player.sendPacket(SystemMessageId.SUMMON_A_PET);
		L2World.getInstance().storeObject(petSummon);
		petSummon.spawnMe(oldX, oldY, oldZ);
		petSummon.startFeed();
		item.setEnchantLevel(petSummon.getLevel());
		
		ThreadPoolManager.getInstance().scheduleGeneral(new EvolveFinalizer(player, petSummon), 900);
		
		if (petSummon.getCurrentFed() <= 0)
			ThreadPoolManager.getInstance().scheduleGeneral(new EvolveFeedWait(player, petSummon), 60000);
		else
			petSummon.startFeed();
		
		return true;
	}
	
	static final class EvolveFeedWait implements Runnable
	{
		private final L2Player _activeChar;
		private final L2PetInstance _petSummon;
		
		EvolveFeedWait(L2Player activeChar, L2PetInstance petSummon)
		{
			_activeChar = activeChar;
			_petSummon = petSummon;
		}
		
		@Override
		public void run()
		{
			if (_petSummon.getCurrentFed() <= 0)
				_petSummon.unSummon(_activeChar);
			else
				_petSummon.startFeed();
		}
	}
	
	static final class EvolveFinalizer implements Runnable
	{
		private final L2Player _activeChar;
		private final L2PetInstance _petSummon;
		
		EvolveFinalizer(L2Player activeChar, L2PetInstance petSummon)
		{
			_activeChar = activeChar;
			_petSummon = petSummon;
		}
		
		@Override
		public void run()
		{
			_activeChar.sendPacket(new MagicSkillLaunched(_activeChar, 2046, 1));
			_petSummon.setFollowStatus(true);
			_petSummon.setShowSummonAnimation(false);
		}
	}
}
