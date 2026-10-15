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
package com.l2jfree.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.L2AutoInitialization;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.config.L2Properties;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.instance.L2ArtefactInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.Siege;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.util.Util;

public class SiegeManager
{
	protected static Logger _log = LoggerFactory.getLogger(SiegeManager.class);
	
	public static final SiegeManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private Map<Integer, List<SiegeSpawn>> _artefactSpawnList;
	
	private Map<Integer, List<SiegeSpawn>> _controlTowerSpawnList;
	private Map<Integer, SiegeSpawn> _flameTowerSpawnListE;
	private Map<Integer, SiegeSpawn> _flameTowerSpawnListW;
	
	private SiegeManager()
	{
		loadTowerArtefacts();
	}
	
	public final void addSiegeSkills(L2Player character)
	{
		for (L2Skill sk : SkillTable.getInstance().getSiegeSkills(character.isNoble()))
			character.addSkill(sk, false);
	}
	
	/** Return true if object is inside zone */
	public final boolean checkIfInZone(L2Object obj)
	{
		return (getSiege(obj) != null);
	}
	
	/** Return true if object is inside zone */
	public final boolean checkIfInZone(int x, int y, int z)
	{
		return (getSiege(x, y, z) != null);
	}
	
	/**
	 * Return true if character can place a flag<BR><BR>
	 * 
	 * @param player
	 *            The L2Player of the character placing the flag
	 * @param isCheckOnly
	 *            if false, it will send a notification to the player telling
	 *            him why it failed
	 */
	public static boolean checkIfOkToPlaceFlag(L2Player player, boolean isCheckOnly)
	{
		// Get siege battleground
		L2Clan clan = player.getClan();
		Siege siege = SiegeManager.getInstance().getSiege(player);
		Castle castle = (siege == null) ? null : siege.getCastle();
		
		SystemMessageId sm = null;
		
		if (siege == null || !siege.getIsInProgress())
			sm = SystemMessageId.ONLY_DURING_SIEGE;
		else if (clan == null || clan.getLeaderId() != player.getObjectId() || siege.getAttackerClan(clan) == null)
			sm = SystemMessageId.CANNOT_USE_ON_YOURSELF;
		else if (castle == null || !castle.checkIfInZoneHeadQuarters(player))
			sm = SystemMessageId.ONLY_DURING_SIEGE;
		else if (castle.getSiege().getAttackerClan(clan).getNumFlags() >= Config.SIEGE_FLAG_MAX_COUNT)
			sm = SystemMessageId.NOT_ANOTHER_HEADQUARTERS;
		else
			return true;
		
		if (!isCheckOnly)
			player.sendPacket(sm);
		return false;
	}
	
	/**
	 * Return true if character can summon<BR><BR>
	 * 
	 * @param player
	 *            The L2Player of the character can summon
	 */
	public final boolean checkIfOkToSummon(L2Player player, boolean isCheckOnly)
	{
		// Get siege battleground
		Siege siege = SiegeManager.getInstance().getSiege(player);
		
		SystemMessageId sm = null;
		
		if (siege == null)
			sm = SystemMessageId.YOU_ARE_NOT_IN_SIEGE;
		else if (!siege.getIsInProgress())
			sm = SystemMessageId.ONLY_DURING_SIEGE;
		else if (player.getClanId() != 0 && siege.getAttackerClan(player.getClanId()) == null)
			sm = SystemMessageId.CANNOT_USE_ON_YOURSELF;
		else if (SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_STRIFE) == SevenSigns.CABAL_DAWN
				&& siege.getCastle().getOwnerId() > 0)
			sm = SystemMessageId.SEAL_OF_STRIFE_FORBIDS_SUMMONING;
		else
			return true;
		
		if (!isCheckOnly)
			player.sendPacket(sm);
		return false;
	}
	
	/**
	 * Return true if character can use Strider Siege Assault skill <BR><BR>
	 * 
	 * @param player
	 *            The L2Creature of the character placing the flag
	 * @param isCheckOnly
	 *            if false, it will send a notification to the player telling
	 *            him why it failed
	 */
	public static boolean checkIfOkToUseStriderSiegeAssault(L2Player player, boolean isCheckOnly)
	{
		// Get siege battleground
		Siege siege = SiegeManager.getInstance().getSiege(player);
		
		SystemMessageId sm = null;
		
		if (siege == null)
			sm = SystemMessageId.YOU_ARE_NOT_IN_SIEGE;
		else if (!siege.getIsInProgress())
			sm = SystemMessageId.ONLY_DURING_SIEGE;
		else if (!(player.getTarget() instanceof L2DoorInstance))
			sm = SystemMessageId.TARGET_IS_INCORRECT;
		else if (!player.isRidingStrider() && !player.isRidingRedStrider())
			sm = SystemMessageId.CANNOT_USE_ON_YOURSELF;
		else
			return true;
		
		if (!isCheckOnly)
			player.sendPacket(sm);
		return false;
	}
	
	public boolean checkIfOkToCastSealOfRule(L2Creature activeChar, Castle castle)
	{
		if (activeChar == null || !(activeChar instanceof L2Player))
			return false;
		
		SystemMessageId sm = null;
		L2Player player = (L2Player)activeChar;
		
		if (castle == null || castle.getCastleId() <= 0 || castle.getSiege().getAttackerClan(player.getClan()) == null)
			sm = SystemMessageId.YOU_ARE_NOT_IN_SIEGE;
		else if (player.getTarget() == null && !(player.getTarget() instanceof L2ArtefactInstance))
			sm = SystemMessageId.TARGET_IS_INCORRECT;
		else if (!castle.getSiege().getIsInProgress())
			sm = SystemMessageId.ONLY_DURING_SIEGE;
		else if (!Util.checkIfInRange(200, player, player.getTarget(), true))
			sm = SystemMessageId.TARGET_TOO_FAR;
		else
		{
			// DO NOT OVERSYNC THE LINE BELOW!
			castle.getSiege().announceToOpponent(SystemMessageId.OPPONENT_STARTED_ENGRAVING.getSystemMessage(), false);
			return true;
		}
		
		player.sendPacket(sm);
		return false;
	}
	
	/**
	 * Return true if the clan is registered or owner of a castle<BR>
	 * <BR>
	 * 
	 * @param clan
	 *            The L2Clan of the player
	 */
	public final boolean checkIsRegistered(L2Clan clan, int castleid)
	{
		if (clan == null)
			return false;
		
		if (clan.getHasCastle() > 0)
			return true;
		
		Connection con = null;
		boolean register = false;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(con);
			PreparedStatement statement =
					con.prepareStatement("SELECT clan_id FROM castle_siege_clan WHERE clan_id=? AND castle_id=?");
			statement.setInt(1, clan.getClanId());
			statement.setInt(2, castleid);
			ResultSet rs = statement.executeQuery();
			
			if (rs.next())
			{
				register = true;
			}
			
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.error("Exception: checkIsRegistered(): " + e.getMessage(), e);
		}
		finally
		{
			L2DatabaseFactory.close(con);
		}
		
		return register;
	}
	
	public final void removeSiegeSkills(L2Player character)
	{
		for (L2Skill sk : SkillTable.getInstance().getSiegeSkills(character.isNoble()))
			character.removeSkill(sk);
	}
	
	// =========================================================
	// Method - Private
	private final void loadTowerArtefacts()
	{
		try
		{
			L2Properties siegeSettings = new L2Properties(Config.SIEGE_CONFIGURATION_FILE).setLog(false);
			
			// Siege spawns settings
			_controlTowerSpawnList = new LinkedHashMap<Integer, List<SiegeSpawn>>();
			_artefactSpawnList = new LinkedHashMap<Integer, List<SiegeSpawn>>();
			_flameTowerSpawnListE = new LinkedHashMap<Integer, SiegeSpawn>();
			_flameTowerSpawnListW = new LinkedHashMap<Integer, SiegeSpawn>();
			
			for (Castle castle : CastleManager.getInstance().getCastles().values())
			{
				String _spawnParams = siegeSettings.getProperty(castle.getName() + "FlameTower1", "");
				StringTokenizer st = new StringTokenizer(_spawnParams.trim(), ",");
				try
				{
					int x = Integer.parseInt(st.nextToken());
					int y = Integer.parseInt(st.nextToken());
					int z = Integer.parseInt(st.nextToken());
					int npc_id = Integer.parseInt(st.nextToken());
					int hp = Integer.parseInt(st.nextToken());
					
					_flameTowerSpawnListE.put(castle.getCastleId(), new SiegeSpawn(castle.getCastleId(), x, y, z, 0,
							npc_id, hp));
				}
				catch (Exception e)
				{
					_log.error("Error while loading flame control tower 1 for " + castle.getName() + " castle.", e);
				}
				_spawnParams = siegeSettings.getProperty(castle.getName() + "FlameTower2", "");
				st = new StringTokenizer(_spawnParams.trim(), ",");
				try
				{
					int x = Integer.parseInt(st.nextToken());
					int y = Integer.parseInt(st.nextToken());
					int z = Integer.parseInt(st.nextToken());
					int npc_id = Integer.parseInt(st.nextToken());
					int hp = Integer.parseInt(st.nextToken());
					
					_flameTowerSpawnListW.put(castle.getCastleId(), new SiegeSpawn(castle.getCastleId(), x, y, z, 0,
							npc_id, hp));
				}
				catch (Exception e)
				{
					_log.error("Error while loading flame control tower 2 for " + castle.getName() + " castle.", e);
				}
				
				List<SiegeSpawn> _controlTowersSpawns = new ArrayList<SiegeSpawn>();
				
				for (int i = 1; i < 0xFF; i++)
				{
					_spawnParams =
							siegeSettings.getProperty(castle.getName() + "ControlTower" + Integer.toString(i), "");
					
					if (_spawnParams.length() == 0)
						break;
					
					st = new StringTokenizer(_spawnParams.trim(), ",");
					try
					{
						int x = Integer.parseInt(st.nextToken());
						int y = Integer.parseInt(st.nextToken());
						int z = Integer.parseInt(st.nextToken());
						int npc_id = Integer.parseInt(st.nextToken());
						int hp = Integer.parseInt(st.nextToken());
						
						_controlTowersSpawns.add(new SiegeSpawn(castle.getCastleId(), x, y, z, 0, npc_id, hp));
					}
					catch (Exception e)
					{
						_log.error("Error while loading control tower(s) for " + castle.getName() + " castle.", e);
					}
				}
				
				List<SiegeSpawn> _artefactSpawns = new ArrayList<SiegeSpawn>();
				
				for (int i = 1; i < 0xFF; i++)
				{
					_spawnParams = siegeSettings.getProperty(castle.getName() + "Artefact" + Integer.toString(i), "");
					
					if (_spawnParams.length() == 0)
						break;
					
					st = new StringTokenizer(_spawnParams.trim(), ",");
					try
					{
						int x = Integer.parseInt(st.nextToken());
						int y = Integer.parseInt(st.nextToken());
						int z = Integer.parseInt(st.nextToken());
						int heading = Integer.parseInt(st.nextToken());
						int npc_id = Integer.parseInt(st.nextToken());
						
						_artefactSpawns.add(new SiegeSpawn(castle.getCastleId(), x, y, z, heading, npc_id));
					}
					catch (Exception e)
					{
						_log.error("Error while loading artefact(s) for " + castle.getName() + " castle.", e);
					}
				}
				
				_controlTowerSpawnList.put(castle.getCastleId(), _controlTowersSpawns);
				_artefactSpawnList.put(castle.getCastleId(), _artefactSpawns);
				
				_log.info("SiegeManager: loaded controltowers[" + Integer.toString(_controlTowersSpawns.size())
						+ "] artifacts[" + Integer.toString(_artefactSpawns.size()) + "] castle[" + castle.getName()
						+ "]");
			}
			_log.info("SiegeManager: loaded "
					+ Integer.toString(_flameTowerSpawnListE.size() + _flameTowerSpawnListW.size())
					+ " flame control towers. [2 per castle]");
		}
		catch (Exception e)
		{
			// _initialized = false;
			_log.error("Error while loading siege data.", e);
		}
	}
	
	public final void reload()
	{
		_artefactSpawnList.clear();
		_controlTowerSpawnList.clear();
		try
		{
			L2AutoInitialization.loadConfig("siege");
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
		loadTowerArtefacts();
	}
	
	public final List<SiegeSpawn> getArtefactSpawnList(int _castleId)
	{
		if (_artefactSpawnList.containsKey(_castleId))
			return _artefactSpawnList.get(_castleId);
		return null;
	}
	
	public final List<SiegeSpawn> getControlTowerSpawnList(int _castleId)
	{
		if (_controlTowerSpawnList.containsKey(_castleId))
			return _controlTowerSpawnList.get(_castleId);
		return null;
	}
	
	public final SiegeSpawn getFlameControlTowerSpawn(int castleId, boolean east)
	{
		if (east)
			return _flameTowerSpawnListE.get(castleId);
		else
			return _flameTowerSpawnListW.get(castleId);
	}
	
	public final Siege getSiege(L2Object activeObject)
	{
		return getSiege(activeObject.getX(), activeObject.getY(), activeObject.getZ());
	}
	
	/** * get active siege for clan ** */
	public final Siege getSiege(L2Clan clan)
	{
		if (clan == null)
			return null;
		for (Castle castle : CastleManager.getInstance().getCastles().values())
		{
			Siege siege = castle.getSiege();
			if (siege.getIsInProgress() && (siege.checkIsAttacker(clan) || siege.checkIsDefender(clan)))
				return siege;
		}
		return null;
	}
	
	public final Siege getSiege(int x, int y, int z)
	{
		for (Castle castle : CastleManager.getInstance().getCastles().values())
			if (castle.getSiege().checkIfInZone(x, y, z))
				return castle.getSiege();
		return null;
	}
	
	public final List<Siege> getSieges()
	{
		List<Siege> sieges = new ArrayList<Siege>();
		for (Castle castle : CastleManager.getInstance().getCastles().values())
			sieges.add(castle.getSiege());
		return sieges;
	}
	
	public class SiegeSpawn
	{
		Location _location;
		private final int _npcId;
		private final int _heading;
		private final int _castleId;
		private int _hp;
		
		public SiegeSpawn(int castle_id, int x, int y, int z, int heading, int npc_id)
		{
			_castleId = castle_id;
			_location = new Location(x, y, z, heading);
			_heading = heading;
			_npcId = npc_id;
		}
		
		public SiegeSpawn(int castle_id, int x, int y, int z, int heading, int npc_id, int hp)
		{
			_castleId = castle_id;
			_location = new Location(x, y, z, heading);
			_heading = heading;
			_npcId = npc_id;
			_hp = hp;
		}
		
		public int getCastleId()
		{
			return _castleId;
		}
		
		public int getNpcId()
		{
			return _npcId;
		}
		
		public int getHeading()
		{
			return _heading;
		}
		
		public int getHp()
		{
			return _hp;
		}
		
		public Location getLocation()
		{
			return _location;
		}
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final SiegeManager _instance = new SiegeManager();
	}
}
