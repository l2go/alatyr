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
import com.l2jfree.gameserver.gameobjects.L2Guard;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.knownlist.CreatureKnownList;
import com.l2jfree.gameserver.gameobjects.knownlist.GuardKnownList;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.taskmanager.AbstractIterativePeriodicTaskManager;
import com.l2jfree.tools.random.Rnd;

/**
 * This class manages all Guards in the world. It inherits all methods from L2Attackable and adds some more such as
 * tracking PK and aggressive L2MonsterInstance.<BR>
 * <BR>
 * 
 * @version $Revision: 1.11.2.1.2.7 $ $Date: 2005/04/06 16:13:40 $
 */
public final class L2GuardInstance extends L2Guard
{
	private static final class GuardReturnHomeManager extends AbstractIterativePeriodicTaskManager<L2GuardInstance>
	{
		private static final GuardReturnHomeManager _instance = new GuardReturnHomeManager();
		
		private static GuardReturnHomeManager getInstance()
		{
			return _instance;
		}
		
		private GuardReturnHomeManager()
		{
			super(RETURN_INTERVAL);
		}
		
		@Override
		protected void callTask(L2GuardInstance task)
		{
			if (task.getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE)
				task.returnHome();
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "returnHome()";
		}
	}
	
	public void startReturnTask()
	{
		GuardReturnHomeManager.getInstance().startTask(this);
	}
	
	public void stopReturnTask()
	{
		GuardReturnHomeManager.getInstance().stopTask(this);
	}
	
	private static final int RETURN_INTERVAL = 60000;
	
	/**
	 * Constructor of L2GuardInstance (use L2Creature and L2NpcInstance constructor).<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Call the L2Creature constructor to set the _template of the L2GuardInstance (copy skills from template to
	 * object and link _calculators to NPC_STD_CALCULATOR)</li>
	 * <li>Set the name of the L2GuardInstance</li>
	 * <li>Create a RandomAnimation Task that will be launched after the calculated delay if the server allow it</li>
	 * <BR>
	 * <BR>
	 * 
	 * @param objectId Identifier of the object to initialized
	 * @param template Template to apply to the NPC
	 */
	public L2GuardInstance(int objectId, L2NpcTemplate template)
	{
		super(objectId, template);
		getKnownList(); // Init knownlist
		
		GuardReturnHomeManager.getInstance().startTask(this);
	}
	
	@Override
	protected CreatureKnownList initKnownList()
	{
		return new GuardKnownList(this);
	}
	
	@Override
	public final GuardKnownList getKnownList()
	{
		return (GuardKnownList)_knownList;
	}
	
	/**
	 * Return True if hte attacker is a L2MonsterInstance.<BR>
	 * <BR>
	 */
	@Override
	public boolean isAutoAttackable(L2Creature attacker)
	{
		return attacker instanceof L2MonsterInstance;
	}
	
	/**
	 * Notify the L2GuardInstance to return to its home location (AI_INTENTION_MOVE_TO) and clear its _aggroList.<BR>
	 * <BR>
	 */
	public void returnHome()
	{
		if (!isInsideRadius(getSpawn().getLocx(), getSpawn().getLocy(), 150, false))
		{
			clearAggroList();
			
			getAI().setIntention(CtrlIntention.AI_INTENTION_MOVE_TO, new L2CharPosition(getSpawn()));
		}
	}
	
	/**
	 * Set the home location of its L2GuardInstance.<BR>
	 * <BR>
	 */
	@Override
	public void onSpawn()
	{
		setIsNoRndWalk(true);
		super.onSpawn();
		
		// Check the region where this mob is, do not activate the AI if region is inactive.
		L2WorldRegion region = L2World.getInstance().getRegion(getX(), getY());
		if (region != null && !region.isActive())
			getAI().stopAITask();
	}
	
	/**
	 * Return the pathfile of the selected HTML file in function of the L2GuardInstance Identifier and of the page
	 * number.<BR>
	 * <BR>
	 * <B><U> Format of the pathfile </U> :</B><BR>
	 * <BR>
	 * <li>if page number = 0 : <B>data/html/guard/12006.htm</B> (npcId-page number)</li>
	 * <li>if page number > 0 : <B>data/html/guard/12006-1.htm</B> (npcId-page number)</li>
	 * <BR>
	 * <BR>
	 * 
	 * @param npcId The Identifier of the L2NpcInstance whose text must be display
	 * @param val The number of the page to display
	 */
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		if (val == 0)
		{
			pom = "" + npcId;
		}
		else
		{
			pom = npcId + "-" + val;
		}
		return "data/html/guard/" + pom + ".htm";
	}
	
	/**
	 * Manage actions when a player click on the L2GuardInstance.<BR>
	 * <BR>
	 * <B><U> Actions on first click on the L2GuardInstance (Select it)</U> :</B><BR>
	 * <BR>
	 * <li>Set the L2GuardInstance as target of the L2Player player (if necessary)</li>
	 * <li>Send a Server->Client packet MyTargetSelected to the L2Player player (display the select window)</li>
	 * <li>Set the L2Player Intention to AI_INTENTION_IDLE</li>
	 * <li>Send a Server->Client packet ValidateLocation to correct the L2GuardInstance position and heading on the
	 * client</li>
	 * <BR>
	 * <BR>
	 * <B><U> Actions on second click on the L2GuardInstance (Attack it/Interact with it)</U> :</B><BR>
	 * <BR>
	 * <li>If L2Player is in the _aggroList of the L2GuardInstance, set the L2Player Intention to
	 * AI_INTENTION_ATTACK</li>
	 * <li>If L2Player is NOT in the _aggroList of the L2GuardInstance, set the L2Player Intention to
	 * AI_INTENTION_INTERACT (after a distance verification) and show message</li>
	 * <BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li>Client packet : Action, AttackRequest</li>
	 * <BR>
	 * <BR>
	 * 
	 * @param player The L2Player that start an action on the L2GuardInstance
	 */
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		// Check if the L2Player already target the L2GuardInstance
		if (getObjectId() != player.getTargetId())
		{
			if (_log.isDebugEnabled())
				_log.debug(player.getObjectId() + ": Targetted guard " + getObjectId());
			
			// Set the target of the L2Player player
			player.setTarget(this);
		}
		else
		{
			// Check if the L2Player is in the _aggroList of the L2GuardInstance
			if (containsTarget(player))
			{
				if (_log.isDebugEnabled())
					_log.debug(player.getObjectId() + ": Attacked guard " + getObjectId());
				
				// Set the L2Player Intention to AI_INTENTION_ATTACK
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
			}
			else
			{
				// Calculate the distance between the L2Player and the L2NpcInstance
				if (!canInteract(player))
				{
					// Set the L2Player Intention to AI_INTENTION_INTERACT
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
				}
				else
				{
					// Send a Server->Client packet SocialAction to the all L2Player on the _knownPlayer of the L2NpcInstance
					// to display a social action of the L2GuardInstance on their client
					SocialAction sa = new SocialAction(getObjectId(), Rnd.nextInt(8));
					broadcastPacket(sa);
					
					// Open a chat window on client with the text of the L2GuardInstance
					Quest[] qlsa = getTemplate().getEventQuests(Quest.QuestEventType.QUEST_START);
					if ((qlsa != null) && (qlsa.length > 0))
						player.setLastQuestNpcObject(getObjectId());
					Quest[] qlst = getTemplate().getEventQuests(Quest.QuestEventType.ON_FIRST_TALK);
					if ((qlst != null) && (qlst.length == 1))
						qlst[0].notifyFirstTalk(this, player);
					else
						showChatWindow(player, 0);
				}
			}
		}
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
}
