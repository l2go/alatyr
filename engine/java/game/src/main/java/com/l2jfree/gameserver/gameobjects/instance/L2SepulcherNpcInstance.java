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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;

import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.instancemanager.FourSepulchersManager;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.tools.random.Rnd;

/**
 * 
 * @author sandman
 */
public class L2SepulcherNpcInstance extends L2Npc
{
	protected static Map<Integer, Integer> _hallGateKeepers = new LinkedHashMap<Integer, Integer>();
	
	protected Future<?> _closeTask = null;
	protected Future<?> _spawnNextMysteriousBoxTask = null;
	protected Future<?> _spawnMonsterTask = null;
	
	private final static String HTML_FILE_PATH = "data/html/SepulcherNpc/";
	
	private final static int HALLS_KEY = 7260;
	
	public L2SepulcherNpcInstance(int objectID, L2NpcTemplate template)
	{
		super(objectID, template);
		
		if (_closeTask != null)
			_closeTask.cancel(true);
		if (_spawnNextMysteriousBoxTask != null)
			_spawnNextMysteriousBoxTask.cancel(true);
		if (_spawnMonsterTask != null)
			_spawnMonsterTask.cancel(true);
		_closeTask = null;
		_spawnNextMysteriousBoxTask = null;
		_spawnMonsterTask = null;
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		setShowSummonAnimation(false);
	}
	
	@Override
	public void deleteMe()
	{
		if (_closeTask != null)
		{
			_closeTask.cancel(true);
			_closeTask = null;
		}
		if (_spawnNextMysteriousBoxTask != null)
		{
			_spawnNextMysteriousBoxTask.cancel(true);
			_spawnNextMysteriousBoxTask = null;
		}
		if (_spawnMonsterTask != null)
		{
			_spawnMonsterTask.cancel(true);
			_spawnMonsterTask = null;
		}
		super.deleteMe();
	}
	
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		// Check if the L2Player already target the L2NpcInstance
		if (this != player.getTarget())
		{
			if (_log.isDebugEnabled())
				_log.info("new target selected:" + getObjectId());
			
			// Set the target of the L2Player player
			player.setTarget(this);
			
			// Check if the player is attackable (without a forced attack)
			if (isAutoAttackable(player))
			{
				// Send a Server->Client packet StatusUpdate of the
				// L2NpcInstance to the L2Player to update its HP bar
				StatusUpdate su = new StatusUpdate(getObjectId());
				su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp());
				su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
				player.sendPacket(su);
			}
		}
		else
		{
			// Check if the player is attackable (without a forced attack) and
			// isn't dead
			if (isAutoAttackable(player) && !isAlikeDead())
			{
				// Check the height difference
				if (Math.abs(player.getZ() - getZ()) < 400) // this max heigth difference might need some tweaking
				{
					// Set the L2Player Intention to AI_INTENTION_ATTACK
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
				}
				else
				{
					// Send a Server->Client packet ActionFailed (target is out
					// of attack range) to the L2Player player
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
			}
			
			if (!isAutoAttackable(player))
			{
				// Calculate the distance between the L2Player and the
				// L2NpcInstance
				if (!canInteract(player))
				{
					// Notify the L2Player AI with AI_INTENTION_INTERACT
					player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
				}
				else
				{
					// Send a Server->Client packet SocialAction to the all
					// L2Player on the _knownPlayer of the L2NpcInstance
					// to display a social action of the L2NpcInstance on their
					// client
					SocialAction sa = new SocialAction(getObjectId(), Rnd.get(8));
					broadcastPacket(sa);
					
					doAction(player);
				}
			}
			// Send a Server->Client ActionFailed to the L2Player in order
			// to avoid that the client wait another packet
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	private void doAction(L2Player player)
	{
		if (isDead())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		switch (getNpcId())
		{
			case 31468:
			case 31469:
			case 31470:
			case 31471:
			case 31472:
			case 31473:
			case 31474:
			case 31475:
			case 31476:
			case 31477:
			case 31478:
			case 31479:
			case 31480:
			case 31481:
			case 31482:
			case 31483:
			case 31484:
			case 31485:
			case 31486:
			case 31487:
				setIsInvul(false);
				reduceCurrentHp(getMaxHp() + 1, player);
				if (_spawnMonsterTask != null)
					_spawnMonsterTask.cancel(true);
				_spawnMonsterTask = ThreadPoolManager.getInstance().scheduleEffect(new SpawnMonster(getNpcId()), 3500);
				break;
			
			case 31455:
			case 31456:
			case 31457:
			case 31458:
			case 31459:
			case 31460:
			case 31461:
			case 31462:
			case 31463:
			case 31464:
			case 31465:
			case 31466:
			case 31467:
				setIsInvul(false);
				reduceCurrentHp(getMaxHp() + 1, player);
				if (player.getParty() != null && !player.getParty().isLeader(player))
					player = player.getParty().getLeader();
				player.addItem("Quest", HALLS_KEY, 1, player, true);
				break;
			
			default:
			{
				Quest[] qlsa = getTemplate().getEventQuests(Quest.QuestEventType.QUEST_START);
				if ((qlsa != null) && qlsa.length > 0)
					player.setLastQuestNpcObject(getObjectId());
				Quest[] qlst = getTemplate().getEventQuests(Quest.QuestEventType.ON_FIRST_TALK);
				if ((qlst != null) && qlst.length == 1)
					qlst[0].notifyFirstTalk(this, player);
				else
					showChatWindow(player, 0);
			}
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
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
		
		return HTML_FILE_PATH + pom + ".htm";
	}
	
	@Override
	public void showChatWindow(L2Player player, int val)
	{
		String filename = getHtmlPath(getNpcId(), val);
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public void onBypassFeedback(L2Player player, String command)
	{
		if (isBusy())
		{
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setFile("data/html/npcbusy.htm");
			html.replace("%busymessage%", getBusyMessage());
			html.replace("%npcname%", getName());
			html.replace("%playername%", player.getName());
			player.sendPacket(html);
		}
		else if (command.startsWith("Chat"))
		{
			int val = 0;
			try
			{
				val = Integer.parseInt(command.substring(5));
			}
			catch (IndexOutOfBoundsException ioobe)
			{
			}
			catch (NumberFormatException nfe)
			{
			}
			showChatWindow(player, val);
		}
		else if (command.startsWith("open_gate"))
		{
			L2ItemInstance hallsKey = player.getInventory().getItemByItemId(HALLS_KEY);
			if (hallsKey == null)
			{
				showHtmlFile(player, "Gatekeeper-no.htm");
			}
			else if (FourSepulchersManager.getInstance().isAttackTime())
			{
				switch (getNpcId())
				{
					case 31929:
					case 31934:
					case 31939:
					case 31944:
						FourSepulchersManager.getInstance().spawnShadow(getNpcId());
				}
				
				//Moved here from switch-default
				openNextDoor(getNpcId());
				if (player.getParty() != null)
				{
					for (L2Player mem : player.getParty().getPartyMembers())
					{
						if (mem.getInventory().getItemByItemId(HALLS_KEY) != null)
							mem.destroyItemByItemId("Quest", HALLS_KEY, mem.getInventory().getItemByItemId(HALLS_KEY)
									.getCount(), mem, true);
					}
				}
				else
					player.destroyItemByItemId("Quest", HALLS_KEY, hallsKey.getCount(), player, true);
			}
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	public void openNextDoor(int npcId)
	{
		int doorId = FourSepulchersManager.getInstance().getHallGateKeepers().get(npcId);
		DoorTable _doorTable = DoorTable.getInstance();
		_doorTable.getDoor(doorId).openMe();
		
		if (_closeTask != null)
			_closeTask.cancel(true);
		_closeTask = ThreadPoolManager.getInstance().scheduleEffect(new CloseNextDoor(npcId, doorId), 10000);
	}
	
	private class CloseNextDoor implements Runnable
	{
		final DoorTable _DoorTable = DoorTable.getInstance();
		private final int _NpcId;
		private final int _DoorId;
		
		public CloseNextDoor(int npcId, int doorId)
		{
			_NpcId = npcId;
			_DoorId = doorId;
		}
		
		@Override
		public void run()
		{
			try
			{
				_DoorTable.getDoor(_DoorId).closeMe();
			}
			catch (Exception e)
			{
				_log.warn(e.getMessage(), e);
			}
			
			if (_spawnNextMysteriousBoxTask != null)
				_spawnNextMysteriousBoxTask.cancel(true);
			_spawnNextMysteriousBoxTask =
					ThreadPoolManager.getInstance().scheduleEffect(new SpawnNextMysteriousBox(_NpcId), 10000);
		}
	}
	
	private class SpawnNextMysteriousBox implements Runnable
	{
		private final int _NpcId;
		
		public SpawnNextMysteriousBox(int npcId)
		{
			_NpcId = npcId;
		}
		
		@Override
		public void run()
		{
			FourSepulchersManager.getInstance().spawnMysteriousBox(_NpcId);
		}
	}
	
	private class SpawnMonster implements Runnable
	{
		private final int _NpcId;
		
		public SpawnMonster(int npcId)
		{
			_NpcId = npcId;
		}
		
		@Override
		public void run()
		{
			FourSepulchersManager.getInstance().spawnMonster(_NpcId);
		}
	}
	
	public void sayInShout(String msg)
	{
		if (msg == null || msg.isEmpty())
			return; //wrong usage
			
		Collection<L2Player> knownPlayers = L2World.getInstance().getAllPlayers();
		if (knownPlayers == null || knownPlayers.isEmpty())
			return;
		CreatureSay sm = new CreatureSay(0, SystemChatChannelId.Chat_Shout, getName(), msg);
		for (L2Player player : knownPlayers)
		{
			if (player == null)
				continue;
			if (Util.checkIfInRange(15000, player, this, true))
				player.sendPacket(sm);
		}
	}
	
	public void showHtmlFile(L2Player player, String file)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/SepulcherNpc/" + file);
		html.replace("%npcname%", getName());
		player.sendPacket(html);
	}
}
