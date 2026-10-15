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

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.gameobjects.ai.L2NpcWalkerAI;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.status.NpcWalkerStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;

/**
 * This class manages some npcs can walk in the city. <br>
 * It inherits all methods from L2NpcInstance. <br><br>
 *
 * @original author Rayan RPG for L2Emu Project
 */
public class L2NpcWalkerInstance extends L2Npc
{
	/**
	 * Constructor of L2NpcWalkerInstance (use L2Creature and L2NpcInstance constructor).<BR><BR>
	 * @param objectId given object id
	 * @param template L2NpcTemplateForThisAi
	 */
	public L2NpcWalkerInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		getAI();
	}
	
	@Override
	protected L2CreatureAI initAI()
	{
		return new L2NpcWalkerAI(new L2NpcWalkerAIAccessor());
	}
	
	/**
	 * AI can't be deattached, npc must move always with the same AI instance.
	 * @param newAI AI to set for this L2NpcWalkerInstance
	 */
	@Override
	protected boolean canReplaceAI()
	{
		return false;
	}
	
	@Override
	public void onSpawn()
	{
		/*L2NpcWalkerAI ai = (L2NpcWalkerAI)getAI();
		
		ai.setHomeX(getX());
		ai.setHomeY(getY());
		ai.setHomeZ(getZ());*/
	}
	
	/**
	 * Sends a chat to all _knowObjects
	 * @param chat message to say
	 */
	public void broadcastChat(String chat)
	{
		if (!getKnownList().getKnownPlayers().isEmpty())
		{
			broadcastPacket(new CreatureSay(getObjectId(), SystemChatChannelId.Chat_Normal, getName(), chat));
		}
	}
	
	/**
	 * NPCs are immortal
	 * @param killer ignore it
	 * @return false
	 */
	@Override
	public boolean doDie(L2Creature killer)
	{
		return false;
	}
	
	protected class L2NpcWalkerAIAccessor extends L2Creature.AIAccessor
	{
		/**
		 * AI can't be deattached.
		 */
		/*@Override
		public void detachAI()
		{
		}*/
	}
	
	@Override
	protected CreatureStatus initStatus()
	{
		return new NpcWalkerStatus(this);
	}
	
	@Override
	public final NpcWalkerStatus getStatus()
	{
		return (NpcWalkerStatus)_status;
	}
}
