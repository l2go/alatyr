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


import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.datatables.HeroSkillTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.instance.L2CubicInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.olympiad.Olympiad.COMP_TYPE;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSummon;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.CreatureSay;
import com.l2jfree.gameserver.network.packets.server.ExOlympiadMatchEnd;
import com.l2jfree.gameserver.network.packets.server.ExOlympiadMode;
import com.l2jfree.gameserver.network.packets.server.ExOlympiadUserInfo;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * @author GodKratos
 */
public class OlympiadGame
{
	private static final Logger _log = LoggerFactory.getLogger(OlympiadGame.class);
	
	protected final COMP_TYPE _type;
	protected boolean _aborted;
	protected boolean _gamestarted;
	protected boolean _playerOneDisconnected;
	protected boolean _playerTwoDisconnected;
	protected boolean _playerOneDefaulted;
	protected boolean _playerTwoDefaulted;
	protected String _playerOneName;
	protected String _playerTwoName;
	protected int _playerOneID = 0;
	protected int _playerTwoID = 0;
	protected static final int OLY_BUFFER = 36402;
	protected static final int OLY_MANAGER = 31688;
	private static final String POINTS = "olympiad_points";
	private static final String COMP_DONE = "competitions_done";
	private static final String COMP_WON = "competitions_won";
	private static final String COMP_LOST = "competitions_lost";
	private static final String COMP_DRAWN = "competitions_drawn";
	protected static boolean _battleStarted;
	protected static boolean _gameIsStarted;
	
	public int _damageP1 = 0;
	public int _damageP2 = 0;
	
	public L2Player _playerOne;
	public L2Player _playerTwo;
	public L2Spawn _spawnOne;
	public L2Spawn _spawnTwo;
	protected List<L2Player> _players;
	private int x1, y1, z1, x2, y2, z2;
	public final int _stadiumID;
	private SystemMessage _sm;
	private SystemMessage _sm2;
	private SystemMessage _sm3;
	
	protected OlympiadGame(int id, COMP_TYPE type, List<L2Player> list)
	{
		_aborted = false;
		_gamestarted = false;
		_stadiumID = id;
		_playerOneDisconnected = false;
		_playerTwoDisconnected = false;
		_type = type;
		
		if (list != null)
		{
			_players = list;
			_playerOne = list.get(0);
			_playerTwo = list.get(1);
			
			try
			{
				_playerOneName = _playerOne.getName();
				_playerTwoName = _playerTwo.getName();
				_playerOne.setOlympiadGameId(id);
				_playerTwo.setOlympiadGameId(id);
				_playerOneID = _playerOne.getObjectId();
				_playerTwoID = _playerTwo.getObjectId();
				
				if (!Config.ALT_OLY_SAME_IP)
				{
					String _playerOneIp = _playerOne.getClient().getHostAddress();
					String _playerTwoIp = _playerTwo.getClient().getHostAddress();
					if (_playerOneIp.equals(_playerTwoIp))
					{
						String classed = "no";
						switch (_type)
						{
							case CLASSED:
								classed = "yes";
								break;
						}
						
						Olympiad.logResult(_playerOneName, _playerTwoName, 0D, 0D, 0, 0, "same ip", 0, classed);
						
						_playerOne.sendMessage("Match aborted due to same ip-address of your enemy.");
						_playerTwo.sendMessage("Match aborted due to same ip-address of your enemy.");
						
						_aborted = true;
						clearPlayers();
					}
				}
			}
			catch (Exception e)
			{
				_aborted = true;
				clearPlayers();
			}
			
			/*
			if (Config.DEBUG)
				_log.info("Olympiad System: Game - " + id + ": "
				        + _playerOne.getName() + " Vs " + _playerTwo.getName());
			*/
		}
		else
		{
			_aborted = true;
			clearPlayers();
			return;
		}
	}
	
	public boolean isAborted()
	{
		return _aborted;
	}
	
	protected void clearPlayers()
	{
		_playerOne = null;
		_playerTwo = null;
		_players = null;
		_playerOneName = "";
		_playerTwoName = "";
		_playerOneID = 0;
		_playerTwoID = 0;
	}
	
	protected void handleDisconnect(L2Player player)
	{
		if (_gamestarted)
		{
			if (player == _playerOne)
				_playerOneDisconnected = true;
			else if (player == _playerTwo)
				_playerTwoDisconnected = true;
		}
	}
	
	public L2Spawn spawnBuffer(Location loc)
	{
		try
		{
			final L2Spawn spawn = new L2Spawn(OLY_BUFFER);
			spawn.setLoc(loc);
			spawn.setAmount(1);
			spawn.setHeading(0);
			spawn.setRespawnDelay(1);
			SpawnTable.getInstance().addNewSpawn(spawn, false);
			spawn.init();
			return spawn;
		}
		catch (Exception e)
		{
			_log.warn("", e);
			return null;
		}
	}
	
	protected void removals()
	{
		if (_aborted)
			return;
		
		if (_playerOne == null || _playerTwo == null)
			return;
		if (_playerOneDisconnected || _playerTwoDisconnected)
			return;
		
		for (L2Player player : _players)
		{
			try
			{
				// Remove Buffs
				player.stopAllEffectsExceptThoseThatLastThroughDeath();
				
				// Remove Clan Skills
				if (player.getClan() != null)
				{
					for (L2Skill skill : player.getClan().getAllSkills())
						player.removeSkill(skill, false);
					player.enableResidentialSkills(false);
				}
				// Abort casting if player casting
				if (player.isCastingNow())
				{
					player.abortCast();
				}
				
				// Force the character to be visible
				player.getAppearance().setVisible();
				
				// Remove Hero Skills
				if (player.isHero())
				{
					for (L2Skill skill : HeroSkillTable.getHeroSkills())
						player.removeSkill(skill, false);
				}
				
				// Heal Player fully
				player.getStatus().setCurrentCp(player.getMaxCp());
				player.getStatus().setCurrentHp(player.getMaxHp());
				player.getStatus().setCurrentMp(player.getMaxMp());
				
				// Remove Summon's Buffs
				if (player.getPet() != null)
				{
					L2Summon summon = player.getPet();
					summon.stopAllEffects();
					
					if (summon instanceof L2PetInstance)
						summon.unSummon(player);
				}
				
				// Remove invalid cubics
				if (player.getCubics() != null && Config.ALT_OLY_REMOVE_CUBICS)
				{
					List<Integer> allowedList = new ArrayList<Integer>();
					
					for (L2Skill skill : player.getAllSkills())
					{
						if (skill instanceof L2SkillSummon && ((L2SkillSummon)skill).isCubic())
						{
							int npcId = ((L2SkillSummon)skill).getNpcId();
							if (npcId != 0)
								allowedList.add(npcId);
						}
					}
					
					for (L2CubicInstance cubic : player.getCubics().values())
					{
						if (!allowedList.contains((cubic.getId())))
						{
							cubic.stopAction();
							player.delCubic(cubic.getId());
						}
					}
				}
				
				// Remove player from his party
				if (player.getParty() != null)
				{
					L2Party party = player.getParty();
					party.removePartyMember(player);
				}
				
				player.checkItemRestriction();
				
				// Remove shot automation
				for (int itemId : player.getShots().getAutoSoulShots())
					player.getShots().removeAutoSoulShot(itemId);
				
				player.clearShotCharges();
				
				if (player.getPet() != null)
					player.getPet().clearShotCharges();
				
				// enable skills with cool time <= 15 minutes
				for (L2Skill skill : player.getAllSkills())
					if (skill.getReuseDelay() <= 900000)
						player.enableSkill(skill.getId());
			}
			catch (Exception e)
			{
				_log.warn("", e);
			}
		}
	}
	
	protected boolean portPlayersToArena()
	{
		final boolean _playerOneCrash = (_playerOne == null || _playerOneDisconnected);
		final boolean _playerTwoCrash = (_playerTwo == null || _playerTwoDisconnected);
		
		if (_playerOneCrash || _playerTwoCrash || _aborted)
		{
			_playerOne = null;
			_playerTwo = null;
			_aborted = true;
			return false;
		}
		
		try
		{
			x1 = _playerOne.getX();
			y1 = _playerOne.getY();
			z1 = _playerOne.getZ();
			
			x2 = _playerTwo.getX();
			y2 = _playerTwo.getY();
			z2 = _playerTwo.getZ();
			
			if (_playerOne.isSitting())
				_playerOne.standUp();
			
			if (_playerTwo.isSitting())
				_playerTwo.standUp();
			
			_playerOne.setTarget(null);
			_playerTwo.setTarget(null);
			
			_gamestarted = true;
			
			final OlympiadStadium stadium = OlympiadManager.STADIUMS[_stadiumID];
			
			_playerOne.teleToLocation(stadium.player1Spawn, false);
			_playerTwo.teleToLocation(stadium.player2Spawn, false);
			
			_playerOne.sendPacket(ExOlympiadMode.INGAME);
			_playerTwo.sendPacket(ExOlympiadMode.INGAME);
			
			_spawnOne = spawnBuffer(stadium.buffer1Spawn);
			_spawnTwo = spawnBuffer(stadium.buffer2Spawn);
			
			_playerOne.setIsInOlympiadMode(true);
			_playerOne.setIsOlympiadStart(false);
			_playerOne.setOlympiadSide(1);
			_playerOne.olyBuff = 5;
			
			_playerTwo.setIsInOlympiadMode(true);
			_playerTwo.setIsOlympiadStart(false);
			_playerTwo.setOlympiadSide(2);
			_playerTwo.olyBuff = 5;
			
			_gameIsStarted = false;
		}
		catch (NullPointerException e)
		{
			return false;
		}
		return true;
	}
	
	protected void cleanEffects()
	{
		if (_playerOne == null || _playerTwo == null)
			return;
		
		if (_playerOneDisconnected || _playerTwoDisconnected)
			return;
		
		for (L2Player player : _players)
		{
			if (player == null)
				continue;
			player.stopAllEffectsExceptThoseThatLastThroughDeath();
			player.clearSouls();
			player.clearCharges();
			
			L2Summon pet = player.getPet();
			if (pet != null)
				pet.stopAllEffects();
		}
	}
	
	protected void portPlayersBack()
	{
		if (_playerOne != null)
		{
			_playerOne.sendPacket(ExOlympiadMatchEnd.PACKET);
			_playerOne.teleToLocation(x1, y1, z1, true);
		}
		
		if (_playerTwo != null)
		{
			_playerTwo.sendPacket(ExOlympiadMatchEnd.PACKET);
			_playerTwo.teleToLocation(x2, y2, z2, true);
		}
	}
	
	protected void PlayersStatusBack()
	{
		for (L2Player player : _players)
		{
			try
			{
				if (player.isDead() == true)
				{
					player.setIsDead(false);
				}
				player.getStatus().startHpMpRegeneration();
				player.getStatus().setCurrentCp(player.getMaxCp());
				player.getStatus().setCurrentHp(player.getMaxHp());
				player.getStatus().setCurrentMp(player.getMaxMp());
				player.setIsInOlympiadMode(false);
				player.setIsOlympiadStart(false);
				player.setOlympiadSide(-1);
				player.setOlympiadGameId(-1);
				player.sendPacket(ExOlympiadMode.RETURN);
				
				// Add Clan Skills
				if (player.getClan() != null)
				{
					for (L2Skill skill : player.getClan().getAllSkills())
					{
						if (skill.getMinPledgeClass() <= player.getPledgeClass())
							player.addSkill(skill, false);
					}
					player.enableResidentialSkills(true);
				}
				
				// Add Hero Skills
				if (player.isHero())
				{
					for (L2Skill skill : HeroSkillTable.getHeroSkills())
						player.addSkill(skill, false);
				}
			}
			catch (Exception e)
			{
				_log.warn("", e);
			}
		}
	}
	
	protected boolean haveWinner()
	{
		if (_aborted || _playerOne == null || _playerTwo == null || _playerOneDisconnected || _playerTwoDisconnected)
		{
			return true;
		}
		
		double playerOneHp = 0;
		
		try
		{
			if (_playerOne != null && _playerOne.getOlympiadGameId() != -1)
			{
				playerOneHp = _playerOne.getStatus().getCurrentHp();
			}
		}
		catch (Exception e)
		{
			playerOneHp = 0;
		}
		
		double playerTwoHp = 0;
		try
		{
			if (_playerTwo != null && _playerTwo.getOlympiadGameId() != -1)
			{
				playerTwoHp = _playerTwo.getStatus().getCurrentHp();
			}
		}
		catch (Exception e)
		{
			playerTwoHp = 0;
		}
		
		if (playerTwoHp <= 0 || playerOneHp <= 0)
		{
			return true;
		}
		
		return false;
	}
	
	protected void validateWinner()
	{
		if (_aborted)
			return;
		
		final boolean _pOneCrash = (_playerOne == null || _playerOneDisconnected);
		final boolean _pTwoCrash = (_playerTwo == null || _playerTwoDisconnected);
		
		final int _div;
		final int _gpreward;
		
		final String classed;
		switch (_type)
		{
			case NON_CLASSED:
				_div = 5;
				_gpreward = Config.ALT_OLY_NONCLASSED_RITEM_C;
				classed = "no";
				break;
			default:
				_div = 3;
				_gpreward = Config.ALT_OLY_CLASSED_RITEM_C;
				classed = "yes";
				break;
		}
		
		final StatsSet playerOneStat = Olympiad.getNobleStats(_playerOneID);
		final StatsSet playerTwoStat = Olympiad.getNobleStats(_playerTwoID);
		
		final int playerOnePlayed = playerOneStat.getInteger(COMP_DONE);
		final int playerTwoPlayed = playerTwoStat.getInteger(COMP_DONE);
		final int playerOneWon = playerOneStat.getInteger(COMP_WON);
		final int playerTwoWon = playerTwoStat.getInteger(COMP_WON);
		final int playerOneLost = playerOneStat.getInteger(COMP_LOST);
		final int playerTwoLost = playerTwoStat.getInteger(COMP_LOST);
		final int playerOneDrawn = playerOneStat.getInteger(COMP_DRAWN);
		final int playerTwoDrawn = playerTwoStat.getInteger(COMP_DRAWN);
		
		final int playerOnePoints = playerOneStat.getInteger(POINTS);
		final int playerTwoPoints = playerTwoStat.getInteger(POINTS);
		final int pointDiff = Math.min(Math.min(playerOnePoints, playerTwoPoints) / _div, Config.ALT_OLY_MAX_POINTS);
		
		// Check for if a player defaulted before battle started
		if (_playerOneDefaulted || _playerTwoDefaulted)
		{
			if (_playerOneDefaulted)
			{
				final int lostPoints = Math.min(playerOnePoints / 3, Config.ALT_OLY_MAX_POINTS);
				playerOneStat.set(POINTS, playerOnePoints - lostPoints);
				Olympiad.updateNobleStats(_playerOneID, playerOneStat);
				SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_LOST_S2_OLYMPIAD_POINTS);
				sm.addString(_playerOneName);
				sm.addNumber(lostPoints);
				broadcastMessage(sm, false);
				
				/*
				if (Config.DEBUG)
					_log.info("Olympia Result: " + _playerOneName + " lost " + lostPoints + " points for defaulting");
				*/
				
				Olympiad.logResult(_playerOneName, _playerTwoName, 0D, 0D, 0, 0, _playerOneName + " default",
						lostPoints, classed);
			}
			if (_playerTwoDefaulted)
			{
				final int lostPoints = Math.min(playerTwoPoints / 3, Config.ALT_OLY_MAX_POINTS);
				playerTwoStat.set(POINTS, playerTwoPoints - lostPoints);
				Olympiad.updateNobleStats(_playerTwoID, playerTwoStat);
				SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_LOST_S2_OLYMPIAD_POINTS);
				sm.addString(_playerTwoName);
				sm.addNumber(lostPoints);
				broadcastMessage(sm, false);
				
				/*
				if (Config.DEBUG)
					_log.info("Olympia Result: " + _playerTwoName + " lost " + lostPoints + " points for defaulting");
				*/
				
				Olympiad.logResult(_playerOneName, _playerTwoName, 0D, 0D, 0, 0, _playerTwoName + " default",
						lostPoints, classed);
			}
			return;
		}
		
		// Create results for players if a player crashed
		if (_pOneCrash || _pTwoCrash)
		{
			if (_pOneCrash && !_pTwoCrash)
			{
				try
				{
					playerOneStat.set(POINTS, playerOnePoints - pointDiff);
					playerOneStat.set(COMP_LOST, playerOneLost + 1);
					
					/*
					if (Config.DEBUG)
						_log.info("Olympia Result: " + _playerOneName + " vs " + _playerTwoName + " ... "
						        + _playerOneName + " lost " + pointDiff + " points for crash");
					*/
					
					Olympiad.logResult(_playerOneName, _playerTwoName, 0D, 0D, 0, 0, _playerOneName + " crash",
							pointDiff, classed);
					
					playerTwoStat.set(POINTS, playerTwoPoints + pointDiff);
					playerTwoStat.set(COMP_WON, playerTwoWon + 1);
					
					/*
					if (Config.DEBUG)
						_log.info("Olympia Result: " + _playerOneName + " vs " + _playerTwoName + " ... "
						        + _playerTwoName + " Win " + pointDiff + " points");
					*/
					
					_sm = new SystemMessage(SystemMessageId.C1_HAS_WON_THE_GAME);
					_sm2 = new SystemMessage(SystemMessageId.C1_HAS_GAINED_S2_OLYMPIAD_POINTS);
					_sm.addString(_playerTwoName);
					broadcastMessage(_sm, true);
					_sm2.addString(_playerTwoName);
					_sm2.addNumber(pointDiff);
					broadcastMessage(_sm2, false);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				
			}
			else if (_pTwoCrash && !_pOneCrash)
			{
				try
				{
					playerTwoStat.set(POINTS, playerTwoPoints - pointDiff);
					playerTwoStat.set(COMP_LOST, playerTwoLost + 1);
					
					/*
					if (Config.DEBUG)
						_log.info("Olympia Result: " + _playerTwoName + " vs " + _playerOneName + " ... "
								+ _playerTwoName + " lost " + pointDiff + " points for crash");
					*/
					
					Olympiad.logResult(_playerOneName, _playerTwoName, 0D, 0D, 0, 0, _playerTwoName + " crash",
							pointDiff, classed);
					
					playerOneStat.set(POINTS, playerOnePoints + pointDiff);
					playerOneStat.set(COMP_WON, playerOneWon + 1);
					
					/*
					if (Config.DEBUG)
						_log.info("Olympia Result: " + _playerTwoName + " vs " + _playerOneName + " ... "
						        + _playerOneName + " Win " + pointDiff + " points");
					*/
					
					_sm = new SystemMessage(SystemMessageId.C1_HAS_WON_THE_GAME);
					_sm2 = new SystemMessage(SystemMessageId.C1_HAS_GAINED_S2_OLYMPIAD_POINTS);
					_sm.addString(_playerOneName);
					broadcastMessage(_sm, true);
					_sm2.addString(_playerOneName);
					_sm2.addNumber(pointDiff);
					broadcastMessage(_sm2, false);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			else if (_pOneCrash && _pTwoCrash)
			{
				try
				{
					playerOneStat.set(POINTS, playerOnePoints - pointDiff);
					playerOneStat.set(COMP_LOST, playerOneLost + 1);
					
					playerTwoStat.set(POINTS, playerTwoPoints - pointDiff);
					playerTwoStat.set(COMP_LOST, playerTwoLost + 1);
					
					/*
					if (Config.DEBUG)
						_log.info("Olympia Result: " + _playerOneName + " vs " + _playerTwoName + " ... "
								+ " both lost " + pointDiff + " points for crash");
					*/
					
					Olympiad.logResult(_playerOneName, _playerTwoName, 0D, 0D, 0, 0, "both crash", pointDiff, classed);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			playerOneStat.set(COMP_DONE, playerOnePlayed + 1);
			playerTwoStat.set(COMP_DONE, playerTwoPlayed + 1);
			
			Olympiad.updateNobleStats(_playerOneID, playerOneStat);
			Olympiad.updateNobleStats(_playerTwoID, playerTwoStat);
			
			return;
		}
		
		double playerOneHp = 0;
		if (!_playerOne.isDead())
		{
			playerOneHp = _playerOne.getStatus().getCurrentHp() + _playerOne.getStatus().getCurrentCp();
		}
		
		double playerTwoHp = 0;
		if (!_playerTwo.isDead())
		{
			playerTwoHp = _playerTwo.getStatus().getCurrentHp() + _playerTwo.getStatus().getCurrentCp();
		}
		
		_sm = new SystemMessage(SystemMessageId.C1_HAS_WON_THE_GAME);
		_sm2 = new SystemMessage(SystemMessageId.C1_HAS_GAINED_S2_OLYMPIAD_POINTS);
		_sm3 = new SystemMessage(SystemMessageId.C1_HAS_LOST_S2_OLYMPIAD_POINTS);
		
		// if players crashed, search if they've relogged
		_playerOne = L2World.getInstance().getPlayer(_playerOneName);
		_players.set(0, _playerOne);
		_playerTwo = L2World.getInstance().getPlayer(_playerTwoName);
		_players.set(1, _playerTwo);
		
		String winner = "draw";
		
		if (_playerOne == null && _playerTwo == null)
		{
			playerOneStat.set(COMP_DRAWN, playerOneDrawn + 1);
			playerTwoStat.set(COMP_DRAWN, playerTwoDrawn + 1);
			_sm = SystemMessageId.THE_GAME_ENDED_IN_A_TIE.getSystemMessage();
			broadcastMessage(_sm, true);
		}
		else if (_playerTwo == null || _playerTwo.isOnline() == 0 || (playerTwoHp == 0 && playerOneHp != 0)
				|| (_damageP1 > _damageP2 && playerTwoHp != 0 && playerOneHp != 0))
		{
			playerOneStat.set(POINTS, playerOnePoints + pointDiff);
			playerTwoStat.set(POINTS, playerTwoPoints - pointDiff);
			playerOneStat.set(COMP_WON, playerOneWon + 1);
			playerTwoStat.set(COMP_LOST, playerTwoLost + 1);
			
			_sm.addString(_playerOneName);
			broadcastMessage(_sm, true);
			_sm2.addString(_playerOneName);
			_sm2.addNumber(pointDiff);
			broadcastMessage(_sm2, false);
			_sm3.addString(_playerTwoName);
			_sm3.addNumber(pointDiff);
			broadcastMessage(_sm3, false);
			winner = _playerOneName + " won";
			
			try
			{
				L2ItemInstance item =
						_playerOne.getInventory().addItem("Olympiad", Config.ALT_OLY_BATTLE_REWARD_ITEM, _gpreward,
								_playerOne, null);
				InventoryUpdate iu = new InventoryUpdate();
				iu.addModifiedItem(item);
				_playerOne.sendPacket(iu);
				
				SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
				sm.addItemName(item);
				sm.addNumber(_gpreward);
				_playerOne.sendPacket(sm);
			}
			catch (Exception e)
			{
				_log.warn("", e);
			}
		}
		else if (_playerOne == null || _playerOne.isOnline() == 0 || (playerOneHp == 0 && playerTwoHp != 0)
				|| (_damageP2 > _damageP1 && playerOneHp != 0 && playerTwoHp != 0))
		{
			playerTwoStat.set(POINTS, playerTwoPoints + pointDiff);
			playerOneStat.set(POINTS, playerOnePoints - pointDiff);
			playerTwoStat.set(COMP_WON, playerTwoWon + 1);
			playerOneStat.set(COMP_LOST, playerOneLost + 1);
			
			_sm.addString(_playerTwoName);
			broadcastMessage(_sm, true);
			_sm2.addString(_playerTwoName);
			_sm2.addNumber(pointDiff);
			broadcastMessage(_sm2, false);
			_sm3.addString(_playerOneName);
			_sm3.addNumber(pointDiff);
			broadcastMessage(_sm3, false);
			winner = _playerTwoName + " won";
			
			try
			{
				L2ItemInstance item =
						_playerTwo.getInventory().addItem("Olympiad", Config.ALT_OLY_BATTLE_REWARD_ITEM, _gpreward,
								_playerTwo, null);
				InventoryUpdate iu = new InventoryUpdate();
				iu.addModifiedItem(item);
				_playerTwo.sendPacket(iu);
				
				SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
				sm.addItemName(item);
				sm.addNumber(_gpreward);
				_playerTwo.sendPacket(sm);
			}
			catch (Exception e)
			{
				_log.warn("", e);
			}
		}
		else
		{
			_sm = SystemMessageId.THE_GAME_ENDED_IN_A_TIE.getSystemMessage();
			broadcastMessage(_sm, true);
			final int pointOneDiff = Math.min(playerOnePoints / 5, Config.ALT_OLY_MAX_POINTS);
			final int pointTwoDiff = Math.min(playerTwoPoints / 5, Config.ALT_OLY_MAX_POINTS);
			playerOneStat.set(POINTS, playerOnePoints - pointOneDiff);
			playerTwoStat.set(POINTS, playerTwoPoints - pointTwoDiff);
			playerOneStat.set(COMP_DRAWN, playerOneDrawn + 1);
			playerTwoStat.set(COMP_DRAWN, playerTwoDrawn + 1);
			_sm2 = new SystemMessage(SystemMessageId.C1_HAS_LOST_S2_OLYMPIAD_POINTS);
			_sm2.addString(_playerOneName);
			_sm2.addNumber(pointOneDiff);
			broadcastMessage(_sm2, false);
			_sm3 = new SystemMessage(SystemMessageId.C1_HAS_LOST_S2_OLYMPIAD_POINTS);
			_sm3.addString(_playerTwoName);
			_sm3.addNumber(pointTwoDiff);
			broadcastMessage(_sm3, false);
		}
		
		/*
		if (Config.DEBUG)
			_log.info("Olympia Result: " + _playerOneName + " vs " + _playerTwoName + " ... " + result);
		*/
		
		playerOneStat.set(COMP_DONE, playerOnePlayed + 1);
		playerTwoStat.set(COMP_DONE, playerTwoPlayed + 1);
		
		Olympiad.updateNobleStats(_playerOneID, playerOneStat);
		Olympiad.updateNobleStats(_playerTwoID, playerTwoStat);
		
		Olympiad.logResult(_playerOneName, _playerTwoName, playerOneHp, playerTwoHp, _damageP1, _damageP2, winner,
				pointDiff, classed);
		
		byte step = 10;
		for (int i = 40; i > 0; i -= step)
		{
			_sm = new SystemMessage(SystemMessageId.YOU_WILL_BE_MOVED_TO_TOWN_IN_S1_SECONDS);
			_sm.addNumber(i);
			broadcastMessage(_sm, false);
			switch (i)
			{
				case 10:
					step = 5;
					break;
				case 5:
					step = 1;
					break;
			}
			try
			{
				Thread.sleep(step * 1000);
			}
			catch (InterruptedException e)
			{
			}
		}
	}
	
	protected boolean makeCompetitionStart()
	{
		if (_aborted)
			return false;
		
		_sm = SystemMessageId.STARTS_THE_GAME.getSystemMessage();
		broadcastMessage(_sm, true);
		_gameIsStarted = true;
		try
		{
			for (L2Player player : _players)
			{
				player.setIsOlympiadStart(true);
			}
		}
		catch (Exception e)
		{
			_aborted = true;
			return false;
		}
		return true;
	}
	
	protected void addDamage(L2Player player, int damage)
	{
		if (_playerOne == null || _playerTwo == null)
			return;
		if (player == _playerOne)
			_damageP1 += damage;
		else if (player == _playerTwo)
			_damageP2 += damage;
	}
	
	protected String getTitle()
	{
		return _playerOneName + " / " + _playerTwoName;
	}
	
	protected L2Player[] getPlayers()
	{
		if (_players == null || _players.isEmpty())
			return null;
		
		final L2Player[] players = new L2Player[_players.size()];
		_players.toArray(players);
		
		return players;
	}
	
	protected void broadcastMessage(SystemMessage sm, boolean toAll)
	{
		for (L2Player player : _players)
		{
			if (player != null)
				player.sendPacket(sm);
		}
		
		if (toAll)
		{
			for (L2Player spec : OlympiadManager.STADIUMS[_stadiumID].getSpectators())
			{
				if (spec != null)
					spec.sendPacket(sm);
			}
		}
	}
	
	protected void announceGame()
	{
		int objId;
		String npcName;
		for (L2Spawn manager : SpawnTable.getInstance().getSpawnTable().values())
		{
			if (manager != null && manager.getNpcid() == OLY_MANAGER)
			{
				objId = manager.getLastSpawn().getObjectId();
				npcName = manager.getLastSpawn().getName();
				manager.getLastSpawn().broadcastPacket(
						new CreatureSay(objId, SystemChatChannelId.Chat_Shout, npcName,
								"Olympiad is going to begin in Arena " + (_stadiumID + 1) + " in a moment."));
			}
		}
	}
}

/**
 * @author ascharot
 */
class OlympiadGameTask implements Runnable
{
	public OlympiadGame _game = null;
	protected static final long BATTLE_PERIOD = Config.ALT_OLY_BATTLE; // 6 mins
	
	private boolean _terminated = false;
	private boolean _started = false;
	
	public boolean isTerminated()
	{
		return _terminated || _game._aborted;
	}
	
	public boolean isStarted()
	{
		return _started;
	}
	
	public OlympiadGameTask(OlympiadGame game)
	{
		_game = game;
	}
	
	protected boolean checkBattleStatus()
	{
		boolean _pOneCrash = (_game._playerOne == null || _game._playerOneDisconnected);
		boolean _pTwoCrash = (_game._playerTwo == null || _game._playerTwoDisconnected);
		if (_pOneCrash || _pTwoCrash || _game._aborted)
		{
			return false;
		}
		
		return true;
	}
	
	protected boolean checkDefaulted()
	{
		_game._playerOne = L2World.getInstance().getPlayer(_game._playerOneName);
		_game._players.set(0, _game._playerOne);
		_game._playerTwo = L2World.getInstance().getPlayer(_game._playerTwoName);
		_game._players.set(1, _game._playerTwo);
		
		for (int i = 0; i < 2; i++)
		{
			boolean defaulted = false;
			L2Player player = _game._players.get(i);
			if (player != null)
				player.setOlympiadGameId(_game._stadiumID);
			L2Player otherPlayer = _game._players.get(i ^ 1);
			SystemMessage sm = null;
			
			if (player == null)
			{
				defaulted = true;
			}
			else if (player.isDead())
			{
				sm = new SystemMessage(SystemMessageId.C1_CANNOT_PARTICIPATE_OLYMPIAD_WHILE_DEAD);
				sm.addPcName(player);
				defaulted = true;
			}
			else if (player.isSubClassActive())
			{
				sm = new SystemMessage(SystemMessageId.C1_CANNOT_PARTICIPATE_IN_OLYMPIAD_WHILE_CHANGED_TO_SUB_CLASS);
				sm.addPcName(player);
				defaulted = true;
			}
			else if (player.isCursedWeaponEquipped())
			{
				sm = new SystemMessage(SystemMessageId.C1_CANNOT_JOIN_OLYMPIAD_POSSESSING_S2);
				sm.addPcName(player);
				sm.addItemName(player.getCursedWeaponEquippedId());
				defaulted = true;
			}
			else if (player.getInventoryLimit() * 0.8 <= player.getInventory().getSize())
			{
				sm =
						new SystemMessage(
								SystemMessageId.C1_CANNOT_PARTICIPATE_IN_OLYMPIAD_INVENTORY_SLOT_EXCEEDS_80_PERCENT);
				sm.addPcName(player);
				defaulted = true;
			}
			
			if (defaulted)
			{
				if (player != null)
					player.sendPacket(sm);
				if (otherPlayer != null)
					otherPlayer.sendPacket(SystemMessageId.THE_GAME_CANCELLED_DUE_TO_OPPONENT);
				if (i == 0)
					_game._playerOneDefaulted = true;
				else
					_game._playerTwoDefaulted = true;
			}
		}
		return _game._playerOneDefaulted || _game._playerTwoDefaulted;
	}
	
	@Override
	public void run()
	{
		_started = true;
		if (_game != null)
		{
			if (_game._playerOne == null || _game._playerTwo == null)
			{
				return;
			}
			
			if (teleportCountdown())
				runGame();
			
			_terminated = true;
			_game.validateWinner();
			_game.PlayersStatusBack();
			_game.cleanEffects();
			
			if (_game._gamestarted)
			{
				_game._gamestarted = false;
				OlympiadManager.STADIUMS[_game._stadiumID].closeDoors();
				try
				{
					_game.portPlayersBack();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			
			for (L2Player spec : OlympiadManager.STADIUMS[_game._stadiumID].getSpectators())
			{
				if (spec != null)
					spec.sendPacket(ExOlympiadMatchEnd.PACKET);
			}
			
			if (_game._spawnOne != null)
			{
				_game._spawnOne.getLastSpawn().deleteMe();
				_game._spawnOne = null;
			}
			if (_game._spawnTwo != null)
			{
				_game._spawnTwo.getLastSpawn().deleteMe();
				_game._spawnTwo = null;
			}
			
			_game.clearPlayers();
			OlympiadManager.getInstance().removeGame(_game);
			_game = null;
		}
	}
	
	private boolean runGame()
	{
		// Checking for opponents and teleporting to arena
		if (checkDefaulted())
		{
			return false;
		}
		SystemMessage sm;
		OlympiadManager.STADIUMS[_game._stadiumID].closeDoors();
		_game.portPlayersToArena();
		_game.removals();
		if (Config.ALT_OLY_ANNOUNCE_GAMES)
			_game.announceGame();
		try
		{
			Thread.sleep(5000);
		}
		catch (InterruptedException e)
		{
		}
		
		synchronized (this)
		{
			if (!OlympiadGame._battleStarted)
				OlympiadGame._battleStarted = true;
		}
		
		byte step = 10;
		for (byte i = 60; i > 0; i -= step)
		{
			sm = new SystemMessage(SystemMessageId.THE_GAME_WILL_START_IN_S1_SECOND_S);
			sm.addNumber(i);
			_game.broadcastMessage(sm, true);
			
			switch (i)
			{
				case 10:
					_game._damageP1 = 0;
					_game._damageP2 = 0;
					OlympiadManager.STADIUMS[_game._stadiumID].openDoors();
					step = 5;
					break;
				case 5:
					step = 1;
					break;
			}
			try
			{
				Thread.sleep(step * 1000);
			}
			catch (InterruptedException e)
			{
			}
		}
		
		if (!checkBattleStatus())
		{
			return false;
		}
		
		_game._playerOne.sendPacket(new ExOlympiadUserInfo(_game._playerOne, 1));
		_game._playerOne.sendPacket(new ExOlympiadUserInfo(_game._playerTwo, 2));
		_game._playerTwo.sendPacket(new ExOlympiadUserInfo(_game._playerTwo, 1));
		_game._playerTwo.sendPacket(new ExOlympiadUserInfo(_game._playerOne, 2));
		
		_game._playerOne.updateEffectIcons();
		_game._playerTwo.updateEffectIcons();
		for (L2Player spec : OlympiadManager.STADIUMS[_game._stadiumID].getSpectators())
		{
			if (spec != null)
			{
				spec.sendPacket(new ExOlympiadUserInfo(_game._playerOne));
				spec.sendPacket(new ExOlympiadUserInfo(_game._playerTwo));
			}
		}
		
		_game._spawnOne.getLastSpawn().deleteMe();
		_game._spawnTwo.getLastSpawn().deleteMe();
		_game._spawnOne = null;
		_game._spawnTwo = null;
		
		if (!_game.makeCompetitionStart())
		{
			return false;
		}
		
		// Wait 3 mins (Battle)
		for (int i = 0; i < BATTLE_PERIOD; i += 10000)
		{
			try
			{
				Thread.sleep(10000);
				// If game haveWinner then stop waiting battle_period
				// and validate winner
				if (_game.haveWinner())
					break;
			}
			catch (InterruptedException e)
			{
			}
		}
		
		return checkBattleStatus();
	}
	
	private boolean teleportCountdown()
	{
		SystemMessage sm;
		// Waiting for teleport to arena
		byte step = 60;
		for (byte i = 120; i > 0; i -= step)
		{
			sm = new SystemMessage(SystemMessageId.YOU_WILL_ENTER_THE_OLYMPIAD_STADIUM_IN_S1_SECOND_S);
			sm.addNumber(i);
			_game.broadcastMessage(sm, false);
			
			switch (i)
			{
				case 60:
					step = 30;
					break;
				case 30:
					step = 15;
					break;
				case 15:
					step = 5;
					break;
				case 5:
					step = 1;
					break;
			}
			try
			{
				Thread.sleep(step * 1000);
			}
			catch (InterruptedException e)
			{
				return false;
			}
		}
		return true;
	}
}
