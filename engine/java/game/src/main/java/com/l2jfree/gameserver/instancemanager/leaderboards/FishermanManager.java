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
package com.l2jfree.gameserver.instancemanager.leaderboards;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.LeaderboardStore;
import com.l2jfree.gameserver.persistence.LeaderboardStore.Board;
import com.l2jfree.gameserver.persistence.LeaderboardStore.Entry;
import com.l2jfree.gameserver.util.Util;

/**
 *
 * @author  KID modded for the peace loving fisherman by evill33t
 */
public class FishermanManager
{
	private static final Logger _log = LoggerFactory.getLogger(FishermanManager.class);
	
	public Map<Integer, FishRank> _ranks = new LinkedHashMap<Integer, FishRank>();
	protected Future<?> _actionTask = null;
	protected int SAVETASK_DELAY = Config.FISHERMAN_INTERVAL;
	protected Long nextTimeUpdateReward = 0L;
	
	public static FishermanManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public void onCatch(int owner, String name)
	{
		FishRank ar = null;
		if (_ranks.get(owner) == null)
			ar = new FishRank();
		else
			ar = _ranks.get(owner);
		
		ar.cought();
		ar.name = name;
		_ranks.put(owner, ar);
	}
	
	public void onEscape(int owner, String name)
	{
		FishRank ar = null;
		if (_ranks.get(owner) == null)
			ar = new FishRank();
		else
			ar = _ranks.get(owner);
		
		ar.escaped();
		ar.name = name;
		_ranks.put(owner, ar);
	}
	
	public void startSaveTask()
	{
		if (_actionTask == null)
			_actionTask =
					ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new saveTask(), 1000,
							SAVETASK_DELAY * 60000);
	}
	
	public void stopSaveTask()
	{
		if (_actionTask != null)
			_actionTask.cancel(true);
		
		_actionTask = null;
	}
	
	public class saveTask implements Runnable
	{
		@Override
		public void run()
		{
			_log.info("FishManager: Autotask init.");
			formRank();
			saveData();
			nextTimeUpdateReward = System.currentTimeMillis() + SAVETASK_DELAY * 60000;
		}
	}
	
	public void startTask()
	{
		if (_actionTask == null)
			_actionTask =
					ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new saveTask(), 1000,
							SAVETASK_DELAY * 60000);
	}
	
	public void formRank()
	{
		Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();
		for (int obj : _ranks.keySet())
		{
			FishRank ar = _ranks.get(obj);
			scores.put(obj, ar.cought - ar.escaped);
		}
		
		scores = Util.sortMap(scores, false);
		FishRank arTop = null;
		int idTop = 0;
		for (int id : scores.keySet())
		{
			arTop = _ranks.get(id);
			idTop = id;
			break;
		}
		
		if (arTop == null)
		{
			Announcements.getInstance().announceToAll("Fisherman: No winners at this time!");
			return;
		}
		
		L2Player winner = L2World.getInstance().findPlayer(idTop);
		
		Announcements.getInstance().announceToAll(
				"Attention Fishermans: " + arTop.name + " is the winner for this time with " + arTop.cought + "/"
						+ arTop.escaped + ". Next calculation in " + Config.FISHERMAN_INTERVAL + " min(s).");
		if (winner != null && Config.FISHERMAN_REWARD_ID > 0)
		{
			winner.getInventory().addItem("FishManager", Config.FISHERMAN_REWARD_ID, Config.FISHERMAN_REWARD_COUNT,
					winner, null);
			if (Config.FISHERMAN_REWARD_COUNT > 1) //You have earned $s1.
				winner.sendPacket(new SystemMessage(SystemMessageId.EARNED_S2_S1_S).addItemName(
						Config.FISHERMAN_REWARD_ID).addNumber(Config.FISHERMAN_REWARD_COUNT));
			else
				winner.sendPacket(new SystemMessage(SystemMessageId.EARNED_S1).addItemName(Config.FISHERMAN_REWARD_ID));
			winner.sendPacket(new ItemList(winner, false));
		}
		_ranks.clear();
	}
	
	public String showHtm(int owner)
	{
		Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();
		for (int obj : _ranks.keySet())
		{
			FishRank ar = _ranks.get(obj);
			scores.put(obj, ar.cought - ar.escaped);
		}
		
		scores = Util.sortMap(scores, false);
		
		int counter = 0, max = 20;
		String pt = "<html><body><center>" + "<font color=\"cc00ad\">TOP " + max + " Fisherman</font><br>";
		
		pt += "<table width=260 border=0 cellspacing=0 cellpadding=0 bgcolor=333333>";
		pt +=
				"<tr> <td align=center>No.</td> <td align=center>Name</td> <td align=center>Cought</td> <td align=center>Escaped</td> </tr>";
		pt +=
				"<tr> <td align=center>&nbsp;</td> <td align=center>&nbsp;</td> <td align=center></td> <td align=center></td> </tr>";
		boolean inTop = false;
		for (int id : scores.keySet())
		{
			if (counter < max)
			{
				FishRank ar = _ranks.get(id);
				pt += tx(counter, ar.name, ar.cought, ar.escaped, id == owner);
				if (id == owner)
				{
					inTop = true;
				}
				counter++;
			}
			else
				break;
		}
		
		if (!inTop)
		{
			FishRank arMe = _ranks.get(owner);
			if (arMe != null)
			{
				pt +=
						"<tr> <td align=center>...</td> <td align=center>...</td> <td align=center>...</td> <td align=center>...</td> </tr>";
				int placeMe = 0;
				for (int idMe : scores.keySet())
				{
					placeMe++;
					if (idMe == owner)
						break;
				}
				pt += tx(placeMe, arMe.name, arMe.cought, arMe.escaped, true);
			}
		}
		
		pt += "</table>";
		pt += "<br><br>";
		if (Config.FISHERMAN_REWARD_ID > 0)
		{
			pt += "Next Reward Time in <font color=\"LEVEL\">" + calcMinTo() + " min(s)</font><br1>";
			pt +=
					"<font color=\"aadd77\">" + Config.FISHERMAN_REWARD_COUNT + " &#" + Config.FISHERMAN_REWARD_ID
							+ ";</font>";
		}
		
		pt += "</center></body></html>";
		
		return pt;
	}
	
	private int calcMinTo()
	{
		return ((int)(nextTimeUpdateReward - System.currentTimeMillis())) / 60000;
	}
	
	private String tx(int counter, String name, int kills, int deaths, boolean mi)
	{
		String t = "";
		
		t +=
				"	<tr>" + "<td align=center>" + (mi ? "<font color=\"LEVEL\">" : "") + (counter + 1) + ".</td>"
						+ "<td align=center>" + name + "</td>" + "<td align=center>" + kills + "</td>"
						+ "<td align=center>" + deaths + "" + (mi ? "</font>" : "") + " </td>" + "</tr>";
		
		return t;
	}
	
	public void engineInit()
	{
		_ranks = new LinkedHashMap<Integer, FishRank>();
		for (Entry entry : LeaderboardStore.load(Board.FISHING))
		{
			FishRank rank = new FishRank();
			rank.cought = entry.wins();
			rank.escaped = entry.losses();
			rank.name = entry.playerName();
			_ranks.put(entry.playerId(), rank);
		}
		startSaveTask();
		_log.info("FishManager: Loaded " + _ranks.size() + " player(s).");
	}
	
	public void saveData()
	{
		List<Entry> entries = new ArrayList<Entry>();
		for (Map.Entry<Integer, FishRank> rank : _ranks.entrySet())
			entries.add(new Entry(rank.getKey(), rank.getValue().name, rank.getValue().cought, rank.getValue().escaped));
		LeaderboardStore.replace(Board.FISHING, entries);
	}
	
	public class FishRank
	{
		public int cought, escaped;
		public String name;
		
		public FishRank()
		{
			cought = 0;
			escaped = 0;
		}
		
		public void cought()
		{
			cought++;
		}
		
		public void escaped()
		{
			escaped++;
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final FishermanManager _instance = new FishermanManager();
	}
}
