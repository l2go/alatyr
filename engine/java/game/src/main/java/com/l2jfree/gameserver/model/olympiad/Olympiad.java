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
package com.l2jfree.gameserver.model.olympiad;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.entity.Hero;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.restriction.global.OlympiadRestriction;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * @author godson
 */
public final class Olympiad
{
	private static final Logger _log = LoggerFactory.getLogger(Olympiad.class);
	
	private static Map<Integer, StatsSet> _nobles;
	protected static List<StatsSet> _heroesToBe;
	private static List<L2Player> _nonClassBasedRegisters;
	private static Map<Integer, List<L2Player>> _classBasedRegisters;
	private static Map<Integer, Integer> _noblesRank;
	
	public static final String OLYMPIAD_HTML_PATH = "data/html/olympiad/";
	private static final String OLYMPIAD_LOAD_DATA = "SELECT current_cycle, period, competition_end_at, validation_end_at, "
			+ "next_weekly_change_at FROM olympiad_state WHERE id = 0";
	private static final String OLYMPIAD_SAVE_DATA = "INSERT INTO olympiad_state (id, current_cycle, "
			+ "period, competition_end_at, validation_end_at, next_weekly_change_at) VALUES (0,?,?,?,?,?) "
			+ "ON CONFLICT (id) DO UPDATE SET current_cycle = EXCLUDED.current_cycle, period = EXCLUDED.period, "
			+ "competition_end_at = EXCLUDED.competition_end_at, validation_end_at = EXCLUDED.validation_end_at, "
			+ "next_weekly_change_at = EXCLUDED.next_weekly_change_at";
	private static final String OLYMPIAD_LOAD_NOBLES =
			"SELECT olympiad_noble.player_id, olympiad_noble.class_id, "
					+ "player.name, olympiad_noble.olympiad_points, olympiad_noble.competitions_done, "
					+ "olympiad_noble.competitions_won, olympiad_noble.competitions_lost, olympiad_noble.competitions_drawn "
					+ "FROM olympiad_noble JOIN player ON player.id = olympiad_noble.player_id";
	private static final String OLYMPIAD_SAVE_NOBLES = "INSERT INTO olympiad_noble "
			+ "(player_id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost, "
			+ "competitions_drawn) VALUES (?,?,?,?,?,?,?)";
	private static final String OLYMPIAD_UPDATE_NOBLES =
			"UPDATE olympiad_noble SET "
					+ "olympiad_points = ?, competitions_done = ?, competitions_won = ?, competitions_lost = ?, competitions_drawn = ? WHERE player_id = ?";
	private static final String OLYMPIAD_GET_HEROS = "SELECT olympiad_noble.player_id, player.name "
			+ "FROM olympiad_noble JOIN player ON player.id = olympiad_noble.player_id "
			+ "WHERE olympiad_noble.class_id = ? AND olympiad_noble.competitions_done >= 9 "
			+ "ORDER BY olympiad_noble.olympiad_points DESC, olympiad_noble.competitions_done DESC";
	private static final String GET_ALL_CLASSIFIED_NOBLESS = "SELECT player_id FROM olympiad_noble_month_end "
			+ "WHERE competitions_done >= 9 ORDER BY olympiad_points DESC, competitions_done DESC";
	private static final String GET_EACH_CLASS_LEADER = "SELECT player.name FROM olympiad_noble_month_end "
			+ "JOIN player ON player.id = olympiad_noble_month_end.player_id "
			+ "WHERE olympiad_noble_month_end.class_id = ? AND olympiad_noble_month_end.competitions_done >= 9 "
			+ "ORDER BY olympiad_noble_month_end.olympiad_points DESC, olympiad_noble_month_end.competitions_done DESC LIMIT 10";
	private static final String GET_EACH_CLASS_LEADER_CURRENT = "SELECT player.name FROM olympiad_noble "
			+ "JOIN player ON player.id = olympiad_noble.player_id "
			+ "WHERE olympiad_noble.class_id = ? AND olympiad_noble.competitions_done >= 9 "
			+ "ORDER BY olympiad_noble.olympiad_points DESC, olympiad_noble.competitions_done DESC LIMIT 10";
	private static final String GET_EACH_CLASS_LEADER_SOULHOUND = "SELECT player.name FROM olympiad_noble_month_end "
			+ "JOIN player ON player.id = olympiad_noble_month_end.player_id "
			+ "WHERE (olympiad_noble_month_end.class_id = 132 OR olympiad_noble_month_end.class_id = 133) "
			+ "AND olympiad_noble_month_end.competitions_done >= 9 "
			+ "ORDER BY olympiad_noble_month_end.olympiad_points DESC, olympiad_noble_month_end.competitions_done DESC LIMIT 10";
	private static final String GET_EACH_CLASS_LEADER_SOULHOUND_CURRENT = "SELECT player.name FROM olympiad_noble "
			+ "JOIN player ON player.id = olympiad_noble.player_id "
			+ "WHERE (olympiad_noble.class_id = 132 OR olympiad_noble.class_id = 133) "
			+ "AND olympiad_noble.competitions_done >= 9 "
			+ "ORDER BY olympiad_noble.olympiad_points DESC, olympiad_noble.competitions_done DESC LIMIT 10";
	
	private static final String OLYMPIAD_DELETE_ALL = "DELETE FROM olympiad_noble";
	private static final String OLYMPIAD_MONTH_CLEAR = "DELETE FROM olympiad_noble_month_end";
	private static final String OLYMPIAD_MONTH_CREATE = "INSERT INTO olympiad_noble_month_end "
			+ "(player_id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost, competitions_drawn) "
			+ "SELECT player_id, class_id, olympiad_points, competitions_done, competitions_won, competitions_lost, competitions_drawn "
			+ "FROM olympiad_noble";
	
	private static final int[] HERO_IDS = { 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104,
			105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 131, 132, 133, 134 };
	
	private static final int COMP_START = Config.ALT_OLY_START_TIME; // 6PM
	private static final int COMP_MIN = Config.ALT_OLY_MIN; // 00 mins
	private static final long COMP_PERIOD = Config.ALT_OLY_CPERIOD; // 6 hours
	protected static final long WEEKLY_PERIOD = Config.ALT_OLY_WPERIOD; // 1 week
	protected static final long VALIDATION_PERIOD = Config.ALT_OLY_VPERIOD; // 24 hours
	
	private static final int DEFAULT_POINTS = 18;
	protected static final int WEEKLY_POINTS = 3;
	
	public static final String CHAR_ID = "charId";
	public static final String CLASS_ID = "class_id";
	public static final String CHAR_NAME = "char_name";
	public static final String POINTS = "olympiad_points";
	public static final String COMP_DONE = "competitions_done";
	public static final String COMP_WON = "competitions_won";
	public static final String COMP_LOST = "competitions_lost";
	public static final String COMP_DRAWN = "competitions_drawn";
	
	protected long _olympiadEnd;
	protected long _validationEnd;
	
	/**
	 * The current period of the olympiad.<br>
	 * <b>0 -</b> Competition period<br>
	 * <b>1 -</b> Validation Period
	 */
	protected int _period;
	protected long _nextWeeklyChange;
	protected int _currentCycle;
	private long _compEnd;
	private Calendar _compStart;
	protected static boolean _inCompPeriod;
	protected static boolean _compStarted = false;
	protected ScheduledFuture<?> _scheduledCompStart;
	protected ScheduledFuture<?> _scheduledCompEnd;
	protected ScheduledFuture<?> _scheduledOlympiadEnd;
	protected ScheduledFuture<?> _scheduledWeeklyTask;
	protected ScheduledFuture<?> _scheduledValdationTask;
	
	protected static enum COMP_TYPE
	{
		CLASSED,
		NON_CLASSED
	}
	
	public static Olympiad getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public Olympiad()
	{
		load();
		
		if (_period == 0)
			init();
	}
	
	/** @return the moment for the column, or null when the epoch milliseconds are 0 ("not set") */
	private static Timestamp toMoment(long millis)
	{
		return millis == 0 ? null : new Timestamp(millis);
	}
	
	/** @return the epoch milliseconds of the moment, or 0 when it is NULL ("not set") */
	private static long fromMoment(Timestamp moment)
	{
		return moment == null ? 0 : moment.getTime();
	}
	
	public static Integer getStadiumCount()
	{
		return OlympiadManager.STADIUMS.length;
	}
	
	private void load()
	{
		// read by the packet threads, the manager thread and the save, written by the registration
		_nobles = new ConcurrentHashMap<Integer, StatsSet>();
		
		Connection con = null;
		
		boolean loaded = false;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_LOAD_DATA);
			ResultSet rset = statement.executeQuery();
			while (rset.next())
			{
				_currentCycle = rset.getInt("current_cycle");
				_period = rset.getInt("period");
				_olympiadEnd = fromMoment(rset.getTimestamp("competition_end_at"));
				_validationEnd = fromMoment(rset.getTimestamp("validation_end_at"));
				_nextWeeklyChange = fromMoment(rset.getTimestamp("next_weekly_change_at"));
				loaded = true;
			}
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Olympiad System: Error loading olympiad data from database: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		if (!loaded)
		{
			// the first start of a world: the first cycle, no dates yet
			_currentCycle = 1;
			_period = 0;
			_olympiadEnd = 0;
			_validationEnd = 0;
			_nextWeeklyChange = 0;
		}
		
		switch (_period)
		{
			case 0:
				if (_olympiadEnd == 0 || _olympiadEnd < System.currentTimeMillis())
					setNewOlympiadEnd();
				else
					scheduleWeeklyChange();
				break;
			case 1:
				if (_validationEnd > System.currentTimeMillis())
				{
					loadNoblesRank();
					_scheduledValdationTask =
							ThreadPoolManager.getInstance().scheduleGeneral(new ValidationEndTask(),
									getMillisToValidationEnd());
				}
				else
				{
					_currentCycle++;
					_period = 0;
					deleteNobles();
					setNewOlympiadEnd();
				}
				break;
			default:
				_log.warn("Olympiad System: Omg something went wrong in loading!! Period = " + _period);
				return;
		}
		
		con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_LOAD_NOBLES);
			ResultSet rset = statement.executeQuery();
			
			while (rset.next())
			{
				StatsSet statData = new StatsSet();
				int charId = rset.getInt("player_id");
				statData.set(CLASS_ID, rset.getInt("class_id"));
				statData.set(CHAR_NAME, rset.getString("name"));
				statData.set(POINTS, rset.getInt(POINTS));
				statData.set(COMP_DONE, rset.getInt(COMP_DONE));
				statData.set(COMP_WON, rset.getInt(COMP_WON));
				statData.set(COMP_LOST, rset.getInt(COMP_LOST));
				statData.set(COMP_DRAWN, rset.getInt(COMP_DRAWN));
				statData.set("to_save", false);
				
				_nobles.put(charId, statData);
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.warn("Olympiad System: Error loading noblesse data from database: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		synchronized (this)
		{
			_log.info("Olympiad System: Loading Olympiad System....");
			if (_period == 0)
				_log.info("Olympiad System: Currently in Olympiad Period");
			else
				_log.info("Olympiad System: Currently in Validation Period");
			
			long milliToEnd;
			if (_period == 0)
				milliToEnd = getMillisToOlympiadEnd();
			else
				milliToEnd = getMillisToValidationEnd();
			
			_log.info("Olympiad System: " + Math.round(milliToEnd / 60000) + " minutes until period ends");
			
			if (_period == 0)
			{
				milliToEnd = getMillisToWeekChange();
				
				_log.info("Olympiad System: Next weekly change is in " + Math.round(milliToEnd / 60000) + " minutes");
			}
		}
		
		_log.info("Olympiad System: Loaded " + _nobles.size() + " Nobles");
		
	}
	
	public void loadNoblesRank()
	{
		_noblesRank = new LinkedHashMap<Integer, Integer>();
		Map<Integer, Integer> tmpPlace = new LinkedHashMap<Integer, Integer>();
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(GET_ALL_CLASSIFIED_NOBLESS);
			ResultSet rset = statement.executeQuery();
			
			int place = 1;
			while (rset.next())
			{
				tmpPlace.put(rset.getInt("player_id"), place++);
			}
			
			rset.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Olympiad System: Error loading noblesse data from database for Ranking: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		int rank1 = (int)Math.round(tmpPlace.size() * 0.01);
		int rank2 = (int)Math.round(tmpPlace.size() * 0.10);
		int rank3 = (int)Math.round(tmpPlace.size() * 0.25);
		int rank4 = (int)Math.round(tmpPlace.size() * 0.50);
		if (rank1 == 0)
		{
			rank1 = 1;
			rank2++;
			rank3++;
			rank4++;
		}
		for (int charId : tmpPlace.keySet())
		{
			if (tmpPlace.get(charId) <= rank1)
				_noblesRank.put(charId, 1);
			else if (tmpPlace.get(charId) <= rank2)
				_noblesRank.put(charId, 2);
			else if (tmpPlace.get(charId) <= rank3)
				_noblesRank.put(charId, 3);
			else if (tmpPlace.get(charId) <= rank4)
				_noblesRank.put(charId, 4);
			else
				_noblesRank.put(charId, 5);
		}
	}
	
	protected void init()
	{
		if (_period == 1)
			return;
		
		_nonClassBasedRegisters = new CopyOnWriteArrayList<L2Player>();
		_classBasedRegisters = new ConcurrentHashMap<Integer, List<L2Player>>();
		
		_compStart = Calendar.getInstance();
		_compStart.set(Calendar.HOUR_OF_DAY, COMP_START);
		_compStart.set(Calendar.MINUTE, COMP_MIN);
		_compEnd = _compStart.getTimeInMillis() + COMP_PERIOD;
		
		if (_scheduledOlympiadEnd != null)
			_scheduledOlympiadEnd.cancel(true);
		
		_scheduledOlympiadEnd =
				ThreadPoolManager.getInstance().scheduleGeneral(new OlympiadEndTask(), getMillisToOlympiadEnd());
		
		updateCompStatus();
	}
	
	protected class OlympiadEndTask implements Runnable
	{
		@Override
		public void run()
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.OLYMPIAD_PERIOD_S1_HAS_ENDED);
			sm.addNumber(_currentCycle);
			
			Announcements.getInstance().announceToAll(sm);
			Announcements.getInstance().announceToAll("Olympiad Validation Period has began");
			
			if (_scheduledWeeklyTask != null)
				_scheduledWeeklyTask.cancel(true);
			
			// The noble data, the heroes, the period, and the monthly snapshot change together.
			WorldTransaction.run("Olympiad period change", () -> {
				saveNobleData();
				
				_period = 1;
				sortHerosToBe();
				Hero.getInstance().computeNewHeroes(_heroesToBe);
				
				saveOlympiadStatus();
				updateMonthlyData();
			});
			
			Calendar validationEnd = Calendar.getInstance();
			_validationEnd = validationEnd.getTimeInMillis() + VALIDATION_PERIOD;
			
			loadNoblesRank();
			_scheduledValdationTask =
					ThreadPoolManager.getInstance()
							.scheduleGeneral(new ValidationEndTask(), getMillisToValidationEnd());
		}
	}
	
	protected class ValidationEndTask implements Runnable
	{
		@Override
		public void run()
		{
			Announcements.getInstance().announceToAll("Olympiad Validation Period has ended");
			_period = 0;
			_currentCycle++;
			deleteNobles();
			setNewOlympiadEnd();
			init();
		}
	}
	
	/** The registers are checked and changed in one step: two clicks must not register a noble twice. */
	protected static final Object REGISTRATION_LOCK = new Object();
	
	public boolean registerNoble(L2Player noble, boolean classBased)
	{
		synchronized (REGISTRATION_LOCK)
		{
			return registerNobleLocked(noble, classBased);
		}
	}
	
	private boolean registerNobleLocked(L2Player noble, boolean classBased)
	{
		if (GlobalRestrictions.isRestricted(noble, OlympiadRestriction.class))
		{
			// TODO: msg
			return false;
		}
		
		SystemMessage sm;
		
		/*
		 * if (_compStarted) {
		 * noble.sendMessage("Cant Register whilst competition is under way");
		 * return false; }
		 */
		
		if (!_inCompPeriod)
		{
			noble.sendPacket(SystemMessageId.THE_OLYMPIAD_GAME_IS_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}
		
		if (!noble.isNoble())
		{
			sm =
					new SystemMessage(
							SystemMessageId.C1_DOES_NOT_MEET_REQUIREMENTS_ONLY_NOBLESS_CAN_PARTICIPATE_IN_THE_OLYMPIAD);
			sm.addPcName(noble);
			noble.sendPacket(sm);
			return false;
		}
		
		/** Begin Olympiad Restrictions */
		if (noble.getBaseClass() != noble.getClassId().getId())
		{
			sm = new SystemMessage(SystemMessageId.C1_CANT_JOIN_THE_OLYMPIAD_WITH_A_SUB_CLASS_CHARACTER);
			sm.addPcName(noble);
			noble.sendPacket(sm);
			return false;
		}
		if (noble.isCursedWeaponEquipped())
		{
			sm = new SystemMessage(SystemMessageId.C1_CANNOT_JOIN_OLYMPIAD_POSSESSING_S2);
			sm.addPcName(noble);
			sm.addItemName(noble.getCursedWeaponEquippedId());
			noble.sendPacket(sm);
			return false;
		}
		if (noble.getInventoryLimit() * 0.8 <= noble.getInventory().getSize())
		{
			sm = new SystemMessage(SystemMessageId.C1_CANNOT_PARTICIPATE_IN_OLYMPIAD_INVENTORY_SLOT_EXCEEDS_80_PERCENT);
			sm.addPcName(noble);
			noble.sendPacket(sm);
			return false;
		}
		if (getMillisToCompEnd() < 600000)
		{
			noble.sendPacket(SystemMessageId.GAME_REQUEST_CANNOT_BE_MADE);
			return false;
		}
		/** End Olympiad Restrictions */
		
		if (_classBasedRegisters.containsKey(noble.getClassId().getId()))
		{
			List<L2Player> classed = _classBasedRegisters.get(noble.getClassId().getId());
			for (L2Player participant : classed)
			{
				if (participant.getObjectId() == noble.getObjectId())
				{
					sm = new SystemMessage(SystemMessageId.C1_IS_ALREADY_REGISTERED_ON_THE_CLASS_MATCH_WAITING_LIST);
					sm.addPcName(noble);
					noble.sendPacket(sm);
					return false;
				}
			}
		}
		
		if (isRegisteredInComp(noble))
		{
			sm =
					new SystemMessage(
							SystemMessageId.C1_IS_ALREADY_REGISTERED_ON_THE_NON_CLASS_LIMITED_MATCH_WAITING_LIST);
			sm.addPcName(noble);
			noble.sendPacket(sm);
			return false;
		}
		
		if (!_nobles.containsKey(noble.getObjectId()))
		{
			StatsSet statDat = new StatsSet();
			statDat.set(CLASS_ID, noble.getClassId().getId());
			statDat.set(CHAR_NAME, noble.getName());
			statDat.set(POINTS, DEFAULT_POINTS);
			statDat.set(COMP_DONE, 0);
			statDat.set(COMP_WON, 0);
			statDat.set(COMP_LOST, 0);
			statDat.set(COMP_DRAWN, 0);
			statDat.set("to_save", true);
			
			_nobles.put(noble.getObjectId(), statDat);
		}
		
		if (classBased && getNoblePoints(noble.getObjectId()) < 3)
		{
			noble.sendMessage("Cant register when you have less than 3 points");
			return false;
		}
		if (!classBased && getNoblePoints(noble.getObjectId()) < 5)
		{
			noble.sendMessage("Cant register when you have less than 5 points");
			return false;
		}
		
		if (classBased)
		{
			if (_classBasedRegisters.containsKey(noble.getClassId().getId()))
			{
				List<L2Player> classed = _classBasedRegisters.get(noble.getClassId().getId());
				classed.add(noble);
				
				_classBasedRegisters.put(noble.getClassId().getId(), classed);
			}
			else
			{
				List<L2Player> classed = new CopyOnWriteArrayList<L2Player>();
				classed.add(noble);
				
				_classBasedRegisters.put(noble.getClassId().getId(), classed);
			}
			noble.sendPacket(SystemMessageId.YOU_HAVE_BEEN_REGISTERED_IN_A_WAITING_LIST_OF_CLASSIFIED_GAMES);
		}
		else
		{
			_nonClassBasedRegisters.add(noble);
			noble.sendPacket(SystemMessageId.YOU_HAVE_BEEN_REGISTERED_IN_A_WAITING_LIST_OF_NO_CLASS_GAMES);
		}
		
		return true;
	}
	
	protected static int getNobleCount()
	{
		return _nobles.size();
	}
	
	protected static StatsSet getNobleStats(int playerId)
	{
		return _nobles.get(playerId);
	}
	
	protected static synchronized void updateNobleStats(int playerId, StatsSet stats)
	{
		_nobles.put(playerId, stats);
	}
	
	protected static List<L2Player> getRegisteredNonClassBased()
	{
		return _nonClassBasedRegisters;
	}
	
	protected static Map<Integer, List<L2Player>> getRegisteredClassBased()
	{
		return _classBasedRegisters;
	}
	
	protected static List<Integer> hasEnoughRegisteredClassed()
	{
		List<Integer> result = new ArrayList<Integer>();
		
		for (Integer classList : getRegisteredClassBased().keySet())
		{
			if (getRegisteredClassBased().get(classList).size() >= Config.ALT_OLY_CLASSED)
				result.add(classList);
		}
		
		if (!result.isEmpty())
		{
			return result;
		}
		return null;
	}
	
	protected static boolean hasEnoughRegisteredNonClassed()
	{
		return Olympiad.getRegisteredNonClassBased().size() >= Config.ALT_OLY_NONCLASSED;
	}
	
	protected static void clearRegistered()
	{
		_nonClassBasedRegisters.clear();
		_classBasedRegisters.clear();
	}
	
	public boolean isRegistered(L2Player noble)
	{
		boolean result = false;
		
		if (_nonClassBasedRegisters != null && _nonClassBasedRegisters.contains(noble))
			result = true;
		
		else if (_classBasedRegisters != null && _classBasedRegisters.containsKey(noble.getClassId().getId()))
		{
			List<L2Player> classed = _classBasedRegisters.get(noble.getClassId().getId());
			if (classed != null && classed.contains(noble))
				result = true;
		}
		
		return result;
	}
	
	public boolean unRegisterNoble(L2Player noble)
	{
		synchronized (REGISTRATION_LOCK)
		{
			return unRegisterNobleLocked(noble);
		}
	}
	
	private boolean unRegisterNobleLocked(L2Player noble)
	{
		SystemMessage sm;
		/*
		 * if (_compStarted) {
		 * noble.sendMessage("Cant Unregister whilst competition is under way");
		 * return false; }
		 */
		
		if (!_inCompPeriod)
		{
			noble.sendPacket(SystemMessageId.THE_OLYMPIAD_GAME_IS_NOT_CURRENTLY_IN_PROGRESS);
			return false;
		}
		
		if (!noble.isNoble())
		{
			sm =
					new SystemMessage(
							SystemMessageId.C1_DOES_NOT_MEET_REQUIREMENTS_ONLY_NOBLESS_CAN_PARTICIPATE_IN_THE_OLYMPIAD);
			sm.addPcName(noble);
			noble.sendPacket(sm);
			return false;
		}
		
		if (!isRegistered(noble))
		{
			noble.sendPacket(SystemMessageId.YOU_HAVE_NOT_BEEN_REGISTERED_IN_A_WAITING_LIST_OF_A_GAME);
			return false;
		}
		
		for (OlympiadGame game : OlympiadManager.getInstance().getOlympiadGames().values())
		{
			if (game == null)
				continue;
			
			if (game._playerOneID == noble.getObjectId() || game._playerTwoID == noble.getObjectId())
			{
				noble.sendMessage("Can't deregister whilst you are already selected for a game");
				return false;
			}
		}
		
		if (_nonClassBasedRegisters.contains(noble))
			_nonClassBasedRegisters.remove(noble);
		else
		{
			List<L2Player> classed = _classBasedRegisters.get(noble.getClassId().getId());
			classed.remove(noble);
			
			_classBasedRegisters.put(noble.getClassId().getId(), classed);
		}
		
		noble.sendPacket(SystemMessageId.YOU_HAVE_BEEN_DELETED_FROM_THE_WAITING_LIST_OF_A_GAME);
		
		return true;
	}
	
	public void removeDisconnectedCompetitor(L2Player player)
	{
		if (OlympiadManager.getInstance().getOlympiadGame(player.getOlympiadGameId()) != null)
			OlympiadManager.getInstance().getOlympiadGame(player.getOlympiadGameId()).handleDisconnect(player);
		
		synchronized (REGISTRATION_LOCK)
		{
			List<L2Player> classed = _classBasedRegisters.get(player.getClassId().getId());
			
			if (_nonClassBasedRegisters.contains(player))
				_nonClassBasedRegisters.remove(player);
			else if (classed != null && classed.contains(player))
			{
				classed.remove(player);
				
				_classBasedRegisters.put(player.getClassId().getId(), classed);
			}
		}
	}
	
	public void notifyCompetitorDamage(L2Player player, int damage, int gameId)
	{
		if (OlympiadManager.getInstance().getOlympiadGames().get(gameId) != null)
			OlympiadManager.getInstance().getOlympiadGames().get(gameId).addDamage(player, damage);
	}
	
	private void updateCompStatus()
	{
		// _compStarted = false;
		
		synchronized (this)
		{
			long milliToStart = getMillisToCompBegin();
			
			double numSecs = (milliToStart / 1000) % 60;
			double countDown = ((milliToStart / 1000) - numSecs) / 60;
			int numMins = (int)Math.floor(countDown % 60);
			countDown = (countDown - numMins) / 60;
			int numHours = (int)Math.floor(countDown % 24);
			int numDays = (int)Math.floor((countDown - numHours) / 24);
			
			_log.info("Olympiad System: Competition Period Starts in " + numDays + " days, " + numHours + " hours and "
					+ numMins + " mins.");
			_log.info("Olympiad System: Event starts/started : " + _compStart.getTime());
		}
		
		_scheduledCompStart = ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				if (isOlympiadEnd())
					return;
				
				_inCompPeriod = true;
				
				Announcements.getInstance().announceToAll(SystemMessageId.THE_OLYMPIAD_GAME_HAS_STARTED);
				_log.info("Olympiad System: Olympiad Game Started");
				
				Thread olyCycle = new Thread(OlympiadManager.getInstance());
				olyCycle.start();
				
				long regEnd = getMillisToCompEnd() - 600000;
				if (regEnd > 0)
				{
					ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
						@Override
						public void run()
						{
							Announcements.getInstance().announceToAll(
									SystemMessageId.OLYMPIAD_REGISTRATION_PERIOD_ENDED);
						}
					}, regEnd);
				}
				
				_scheduledCompEnd = ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
					@Override
					public void run()
					{
						if (isOlympiadEnd())
							return;
						_inCompPeriod = false;
						Announcements.getInstance().announceToAll(SystemMessageId.THE_OLYMPIAD_GAME_HAS_ENDED);
						_log.info("Olympiad System: Olympiad Game Ended");
						
						ThreadPoolManager.getInstance().schedule(new Runnable() {
							@Override
							public void run()
							{
								if (OlympiadGame._battleStarted)
								{
									ThreadPoolManager.getInstance().schedule(this, 60000);
								}
								else
								{
									saveOlympiadStatus();
									init();
								}
							}
							
						}, 0);
					}
				}, getMillisToCompEnd());
			}
		}, getMillisToCompBegin());
	}
	
	private long getMillisToOlympiadEnd()
	{
		// if (_olympiadEnd > System.currentTimeMillis())
		return (_olympiadEnd - System.currentTimeMillis());
		// return 10L;
	}
	
	public void manualSelectHeroes()
	{
		if (_scheduledOlympiadEnd != null)
			_scheduledOlympiadEnd.cancel(true);
		
		_scheduledOlympiadEnd = ThreadPoolManager.getInstance().scheduleGeneral(new OlympiadEndTask(), 0);
	}
	
	protected long getMillisToValidationEnd()
	{
		if (_validationEnd > System.currentTimeMillis())
			return (_validationEnd - System.currentTimeMillis());
		return 10L;
	}
	
	public boolean isOlympiadEnd()
	{
		return (_period != 0);
	}
	
	protected void setNewOlympiadEnd()
	{
		SystemMessage sm = new SystemMessage(SystemMessageId.OLYMPIAD_PERIOD_S1_HAS_STARTED);
		sm.addNumber(_currentCycle);
		
		Announcements.getInstance().announceToAll(sm);
		
		Calendar currentTime = Calendar.getInstance();
		currentTime.add(Calendar.MONTH, 1);
		currentTime.set(Calendar.DAY_OF_MONTH, 1);
		currentTime.set(Calendar.AM_PM, Calendar.AM);
		currentTime.set(Calendar.HOUR, 12);
		currentTime.set(Calendar.MINUTE, 0);
		currentTime.set(Calendar.SECOND, 0);
		_olympiadEnd = currentTime.getTimeInMillis();
		
		Calendar nextChange = Calendar.getInstance();
		_nextWeeklyChange = nextChange.getTimeInMillis() + WEEKLY_PERIOD;
		scheduleWeeklyChange();
	}
	
	public boolean inCompPeriod()
	{
		return _inCompPeriod;
	}
	
	private long getMillisToCompBegin()
	{
		if (_compStart.getTimeInMillis() < System.currentTimeMillis() && _compEnd > System.currentTimeMillis())
			return 10L;
		
		if (_compStart.getTimeInMillis() > System.currentTimeMillis())
			return (_compStart.getTimeInMillis() - System.currentTimeMillis());
		
		return setNewCompBegin();
	}
	
	private long setNewCompBegin()
	{
		_compStart = Calendar.getInstance();
		_compStart.set(Calendar.HOUR_OF_DAY, COMP_START);
		_compStart.set(Calendar.MINUTE, COMP_MIN);
		_compStart.add(Calendar.HOUR_OF_DAY, 24);
		_compEnd = _compStart.getTimeInMillis() + COMP_PERIOD;
		
		_log.info("Olympiad System: New Schedule @ " + _compStart.getTime());
		
		return (_compStart.getTimeInMillis() - System.currentTimeMillis());
	}
	
	protected long getMillisToCompEnd()
	{
		// if (_compEnd > System.currentTimeMillis())
		return (_compEnd - System.currentTimeMillis());
		// return 10L;
	}
	
	private long getMillisToWeekChange()
	{
		if (_nextWeeklyChange > System.currentTimeMillis())
			return (_nextWeeklyChange - System.currentTimeMillis());
		return 10L;
	}
	
	private void scheduleWeeklyChange()
	{
		_scheduledWeeklyTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new Runnable() {
			@Override
			public void run()
			{
				addWeeklyPoints();
				_log.info("Olympiad System: Added weekly points to nobles");
				
				Calendar nextChange = Calendar.getInstance();
				_nextWeeklyChange = nextChange.getTimeInMillis() + WEEKLY_PERIOD;
			}
		}, getMillisToWeekChange(), WEEKLY_PERIOD);
	}
	
	protected synchronized void addWeeklyPoints()
	{
		if (_period == 1)
			return;
		
		for (Integer nobleId : _nobles.keySet())
		{
			StatsSet nobleInfo = _nobles.get(nobleId);
			int currentPoints = nobleInfo.getInteger(POINTS);
			currentPoints += WEEKLY_POINTS;
			nobleInfo.set(POINTS, currentPoints);
			
			updateNobleStats(nobleId, nobleInfo);
		}
	}
	
	public Map<Integer, String> getMatchList()
	{
		return OlympiadManager.getInstance().getAllTitles();
	}
	
	// returns the players for the given olympiad game Id
	public L2Player[] getPlayers(int Id)
	{
		if (OlympiadManager.getInstance().getOlympiadGame(Id) == null)
			return null;
		else
			return OlympiadManager.getInstance().getOlympiadGame(Id).getPlayers();
	}
	
	public int getCurrentCycle()
	{
		return _currentCycle;
	}
	
	public static void addSpectator(int id, L2Player spectator, boolean storeCoords)
	{
		if (getInstance().isRegisteredInComp(spectator))
		{
			spectator.sendPacket(new SystemMessage(
					SystemMessageId.WHILE_YOU_ARE_ON_THE_WAITING_LIST_YOU_ARE_NOT_ALLOWED_TO_WATCH_THE_GAME));
			return;
		}
		
		if (GlobalRestrictions.isRestricted(spectator, OlympiadRestriction.class))
		{
			spectator.sendMessage("You can not observe games while registered for event.");
			return;
		}
		
		OlympiadManager.STADIUMS[id].addSpectator(id, spectator, storeCoords);
	}
	
	public static int getSpectatorArena(L2Player player)
	{
		for (int i = 0; i < OlympiadManager.STADIUMS.length; i++)
		{
			if (OlympiadManager.STADIUMS[i].getSpectators().contains(player))
				return i;
		}
		return -1;
	}
	
	public static void removeSpectator(int id, L2Player spectator)
	{
		try
		{
			OlympiadManager.STADIUMS[id].removeSpectator(spectator);
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
		}
	}
	
	public Set<L2Player> getSpectators(int id)
	{
		try
		{
			if (OlympiadManager.getInstance().getOlympiadGame(id) == null)
				return null;
			return OlympiadManager.STADIUMS[id].getSpectators();
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			return null;
		}
	}
	
	public Map<Integer, OlympiadGame> getOlympiadGames()
	{
		return OlympiadManager.getInstance().getOlympiadGames();
	}
	
	public boolean playerInStadia(L2Player player)
	{
		return player.isInsideZone(L2Zone.FLAG_STADIUM);
	}
	
	public int[] getWaitingList()
	{
		int[] array = new int[2];
		
		if (!inCompPeriod())
			return null;
		
		int classCount = 0;
		
		if (!_classBasedRegisters.isEmpty())
		{
			for (List<L2Player> classed : _classBasedRegisters.values())
				classCount += classed.size();
		}
		
		array[0] = classCount;
		array[1] = _nonClassBasedRegisters.size();
		
		return array;
	}
	
	/**
	 * Save noblesse data to database
	 */
	protected synchronized void saveNobleData()
	{
		if (_nobles == null || _nobles.isEmpty())
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			
			for (Integer nobleId : _nobles.keySet())
			{
				StatsSet nobleInfo = _nobles.get(nobleId);
				
				if (nobleInfo == null)
					continue;
				
				int charId = nobleId;
				int classId = nobleInfo.getInteger(CLASS_ID);
				int points = nobleInfo.getInteger(POINTS);
				int compDone = nobleInfo.getInteger(COMP_DONE);
				int compWon = nobleInfo.getInteger(COMP_WON);
				int compLost = nobleInfo.getInteger(COMP_LOST);
				int compDrawn = nobleInfo.getInteger(COMP_DRAWN);
				boolean toSave = nobleInfo.getBool("to_save");
				
				if (toSave)
				{
					statement = con.prepareStatement(OLYMPIAD_SAVE_NOBLES);
					statement.setInt(1, charId);
					statement.setInt(2, classId);
					statement.setInt(3, points);
					statement.setInt(4, compDone);
					statement.setInt(5, compWon);
					statement.setInt(6, compLost);
					statement.setInt(7, compDrawn);
					
					nobleInfo.set("to_save", false);
					
					updateNobleStats(nobleId, nobleInfo);
				}
				else
				{
					statement = con.prepareStatement(OLYMPIAD_UPDATE_NOBLES);
					statement.setInt(1, points);
					statement.setInt(2, compDone);
					statement.setInt(3, compWon);
					statement.setInt(4, compLost);
					statement.setInt(5, compDrawn);
					statement.setInt(6, charId);
				}
				statement.execute();
				statement.close();
			}
		}
		catch (SQLException e)
		{
			_log.error("Olympiad System: Failed to save noblesse data to database: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	/**
	 * Saves the noble statistics and the Olympiad state (olympiad_state) to the database
	 */
	public void saveOlympiadStatus()
	{
		WorldTransaction.run("Olympiad status save", () -> {
			saveNobleData();
			saveStatusData();
		});
	}
	
	private void saveStatusData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_SAVE_DATA);
			
			statement.setInt(1, _currentCycle);
			statement.setInt(2, _period);
			statement.setTimestamp(3, toMoment(_olympiadEnd));
			statement.setTimestamp(4, toMoment(_validationEnd));
			statement.setTimestamp(5, toMoment(_nextWeeklyChange));
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Olympiad System: Failed to save olympiad data to database: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void updateMonthlyData()
	{
		WorldTransaction.run("Olympiad monthly snapshot", this::saveMonthlyData);
	}
	
	private void saveMonthlyData()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			
			statement = con.prepareStatement(OLYMPIAD_MONTH_CLEAR);
			statement.execute();
			statement.close();
			statement = con.prepareStatement(OLYMPIAD_MONTH_CREATE);
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.error("Olympiad System: Failed to update monthly noblese data: ", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	protected void sortHerosToBe()
	{
		if (_period != 1)
			return;
		
		if (_nobles != null)
		{
			for (Integer nobleId : _nobles.keySet())
			{
				StatsSet nobleInfo = _nobles.get(nobleId);
				
				if (nobleInfo == null)
					continue;
				
				int charId = nobleId;
				int classId = nobleInfo.getInteger(CLASS_ID);
				String charName = nobleInfo.getString(CHAR_NAME);
				int points = nobleInfo.getInteger(POINTS);
				int compDone = nobleInfo.getInteger(COMP_DONE);
				
				logResult(charName, "", Double.valueOf(charId), Double.valueOf(classId), compDone, points,
						"noble-charId-classId-compdone-points", 0, "");
			}
		}
		
		_heroesToBe = new ArrayList<StatsSet>();
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			ResultSet rset;
			StatsSet hero;
			List<StatsSet> soulHounds = new ArrayList<StatsSet>();
			for (int finalElement : HERO_IDS)
			{
				statement = con.prepareStatement(OLYMPIAD_GET_HEROS);
				statement.setInt(1, finalElement);
				rset = statement.executeQuery();
				
				if (rset.next())
				{
					hero = new StatsSet();
					hero.set(CLASS_ID, finalElement);
					hero.set(CHAR_ID, rset.getInt("player_id"));
					hero.set(CHAR_NAME, rset.getString("name"));
					
					if (finalElement == 132 || finalElement == 133) // Male & Female Soulhounds rank as one hero class
					{
						hero = _nobles.get(hero.getInteger(CHAR_ID));
						hero.set(CHAR_ID, rset.getInt("player_id"));
						soulHounds.add(hero);
					}
					else
					{
						logResult(hero.getString(CHAR_NAME), "", hero.getDouble(CHAR_ID), hero.getDouble(CLASS_ID), 0,
								0, "awarded hero", 0, "");
						_heroesToBe.add(hero);
					}
				}
				
				statement.close();
				rset.close();
			}
			switch (soulHounds.size())
			{
				case 0:
				{
					break;
				}
				case 1:
				{
					hero = new StatsSet();
					StatsSet winner = soulHounds.get(0);
					hero.set(CLASS_ID, winner.getInteger(CLASS_ID));
					hero.set(CHAR_ID, winner.getInteger(CHAR_ID));
					hero.set(CHAR_NAME, winner.getString(CHAR_NAME));
					logResult(hero.getString(CHAR_NAME), "", hero.getDouble(CHAR_ID), hero.getDouble(CLASS_ID), 0, 0,
							"awarded hero", 0, "");
					_heroesToBe.add(hero);
					break;
				}
				case 2:
				{
					hero = new StatsSet();
					StatsSet winner;
					StatsSet hero1 = soulHounds.get(0);
					StatsSet hero2 = soulHounds.get(1);
					int hero1Points = hero1.getInteger(POINTS);
					int hero2Points = hero2.getInteger(POINTS);
					int hero1Comps = hero1.getInteger(COMP_DONE);
					int hero2Comps = hero2.getInteger(COMP_DONE);
					
					if (hero1Points > hero2Points)
						winner = hero1;
					else if (hero2Points > hero1Points)
						winner = hero2;
					else
					{
						if (hero1Comps > hero2Comps)
							winner = hero1;
						else
							winner = hero2;
					}
					
					hero.set(CLASS_ID, winner.getInteger(CLASS_ID));
					hero.set(CHAR_ID, winner.getInteger(CHAR_ID));
					hero.set(CHAR_NAME, winner.getString(CHAR_NAME));
					logResult(hero.getString(CHAR_NAME), "", hero.getDouble(CHAR_ID), hero.getDouble(CLASS_ID), 0, 0,
							"awarded hero", 0, "");
					_heroesToBe.add(hero);
					break;
				}
			}
		}
		catch (SQLException e)
		{
			_log.warn("Olympiad System: Couldnt load heros from DB", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
	}
	
	public List<String> getClassLeaderBoard(int classId)
	{
		// if (_period != 1) return;
		
		List<String> names = new ArrayList<String>();
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			ResultSet rset;
			if (classId == 132 || classId == 133)
			{
				if (Config.ALT_OLY_SHOW_MONTHLY_WINNERS)
					statement = con.prepareStatement(GET_EACH_CLASS_LEADER_SOULHOUND);
				else
					statement = con.prepareStatement(GET_EACH_CLASS_LEADER_SOULHOUND_CURRENT);
			}
			else
			{
				if (Config.ALT_OLY_SHOW_MONTHLY_WINNERS)
					statement = con.prepareStatement(GET_EACH_CLASS_LEADER);
				else
					statement = con.prepareStatement(GET_EACH_CLASS_LEADER_CURRENT);
				statement.setInt(1, classId);
			}
			rset = statement.executeQuery();
			
			while (rset.next())
			{
				names.add(rset.getString("name"));
			}
			
			statement.close();
			rset.close();
			
			return names;
		}
		catch (SQLException e)
		{
			_log.warn("Olympiad System: Couldnt load olympiad leaders from DB", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return names;
		
	}
	
	public int getNoblessePasses(L2Player player, boolean clear)
	{
		if (_period != 1 || _noblesRank.isEmpty())
			return 0;
		
		int objId = player.getObjectId();
		if (!_noblesRank.containsKey(objId))
			return 0;
		
		StatsSet noble = _nobles.get(objId);
		if (noble.getInteger(POINTS) == 0)
			return 0;
		
		int rank = _noblesRank.get(objId);
		int points = (player.isHero() ? Config.ALT_OLY_HERO_POINTS : 0);
		switch (rank)
		{
			case 1:
				points += Config.ALT_OLY_RANK1_POINTS;
				break;
			case 2:
				points += Config.ALT_OLY_RANK2_POINTS;
				break;
			case 3:
				points += Config.ALT_OLY_RANK3_POINTS;
				break;
			case 4:
				points += Config.ALT_OLY_RANK4_POINTS;
				break;
			default:
				points += Config.ALT_OLY_RANK5_POINTS;
		}
		
		if (clear)
		{
			noble.set(POINTS, 0);
			updateNobleStats(objId, noble);
		}
		
		points *= Config.ALT_OLY_GP_PER_POINT;
		
		return points;
	}
	
	public boolean isRegisteredInComp(L2Player player)
	{
		boolean result = isRegistered(player);
		
		if (_inCompPeriod)
		{
			for (OlympiadGame game : OlympiadManager.getInstance().getOlympiadGames().values())
			{
				if ((game._playerOneID == player.getObjectId()) || (game._playerTwoID == player.getObjectId()))
				{
					result = true;
					break;
				}
			}
		}
		
		return result;
	}
	
	public int getNoblePoints(int objId)
	{
		if (_nobles.isEmpty())
			return 0;
		
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		int points = noble.getInteger(POINTS);
		
		return points;
	}
	
	public int getLastNobleOlympiadPoints(int objId)
	{
		int result = 0;
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("SELECT olympiad_points FROM olympiad_noble_month_end WHERE player_id = ?");
			statement.setInt(1, objId);
			ResultSet rs = statement.executeQuery();
			if (rs.next())
				result = rs.getInt(1);
			rs.close();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("Could not load last olympiad points:", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return result;
	}
	
	public int getCompetitionDone(int objId)
	{
		if (_nobles.isEmpty())
			return 0;
		
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		int points = noble.getInteger(COMP_DONE);
		
		return points;
	}
	
	public int getCompetitionWon(int objId)
	{
		if (_nobles.size() == 0)
			return 0;
		
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		int points = noble.getInteger(COMP_WON);
		
		return points;
	}
	
	public int getCompetitionLost(int objId)
	{
		if (_nobles.size() == 0)
			return 0;
		
		StatsSet noble = _nobles.get(objId);
		if (noble == null)
			return 0;
		int points = noble.getInteger(COMP_LOST);
		
		return points;
	}
	
	protected void deleteNobles()
	{
		Connection con = null;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_DELETE_ALL);
			statement.execute();
			statement.close();
		}
		catch (SQLException e)
		{
			_log.warn("Olympiad System: Couldnt delete nobles from DB", e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		_nobles.clear();
	}
	
	/**
	 * Logs result of Olympiad to a csv file.
	 * 
	 * @param playerOne
	 * @param playerTwo
	 * @param p1hp
	 * @param p2hp
	 * @param p1dmg
	 * @param p2dmg
	 * @param result
	 * @param points
	 */
	public static synchronized void logResult(String playerOne, String playerTwo, Double p1hp, Double p2hp, int p1dmg,
			int p2dmg, String result, int points, String classed)
	{
		if (!Config.ALT_OLY_LOG_FIGHTS)
			return;
		
		SimpleDateFormat formatter;
		formatter = new SimpleDateFormat("dd/MM/yyyy H:mm:ss");
		String date = formatter.format(new Date());
		FileWriter save = null;
		try
		{
			File file = new File("log/olympiad.csv");
			
			boolean writeHead = !file.exists();
			
			save = new FileWriter(file, true);
			
			if (writeHead)
			{
				String header =
						"Date,Player1,Player2,Player1 HP,Player2 HP,Player1 Damage,Player2 Damage,Result,Points,Classed\r\n";
				save.write(header);
			}
			
			String out =
					date + "," + playerOne + "," + playerTwo + "," + p1hp + "," + p2hp + "," + p1dmg + "," + p2dmg
							+ "," + result + "," + points + "," + classed + "\r\n";
			save.write(out);
		}
		catch (IOException e)
		{
			_log.warn("Olympiad System: Olympiad log could not be saved: ", e);
		}
		finally
		{
			try
			{
				if (save != null)
					save.close();
			}
			catch (Exception e)
			{
			}
		}
	}
	
	public static void sendMatchList(L2Player player)
	{
		NpcHtmlMessage message = new NpcHtmlMessage(0);
		message.setFile(Olympiad.OLYMPIAD_HTML_PATH + "olympiad_observe2.htm");
		
		Map<Integer, String> matches = getInstance().getMatchList();
		for (int i = 0; i < Olympiad.getStadiumCount(); i++)
		{
			int arenaId = i + 1;
			String state = "Initial State";
			String players = "&nbsp;";
			if (matches.containsKey(i))
			{
				if (OlympiadGame._gameIsStarted)
					state = "Playing";
				else
					state = "Standby";
				players = matches.get(i);
			}
			message.replace("%state" + arenaId + "%", state);
			message.replace("%players" + arenaId + "%", players);
		}
		
		player.sendPacket(message);
	}
	
	public static void bypassChangeArena(String command, L2Player player)
	{
		if (!player.inObserverMode())
			return;
		
		String[] commands = command.split(" ");
		int id = Integer.parseInt(commands[1]);
		int arena = getSpectatorArena(player);
		if (arena >= 0)
			Olympiad.removeSpectator(arena, player);
		else
			return;
		Olympiad.addSpectator(id, player, false);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final Olympiad _instance = new Olympiad();
	}
}
