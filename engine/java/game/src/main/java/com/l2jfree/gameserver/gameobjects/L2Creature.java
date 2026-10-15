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
package com.l2jfree.gameserver.gameobjects;

import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_ACTIVE;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_FOLLOW;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_IDLE;
import static com.l2jfree.gameserver.gameobjects.ai.CtrlIntention.AI_INTENTION_MOVE_TO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.gameobjects.ai.CtrlEvent;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.gameobjects.effects.CreatureEffects;
import com.l2jfree.gameserver.gameobjects.instance.L2AirShipInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2BoatInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MinionInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcWalkerInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2RiftInvaderInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.knownlist.CreatureKnownList;
import com.l2jfree.gameserver.gameobjects.shot.CreatureShots;
import com.l2jfree.gameserver.gameobjects.stat.CreatureStat;
import com.l2jfree.gameserver.gameobjects.skills.SkillMaps;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2CreatureTemplate;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.gameobjects.view.ICreatureView;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.geodata.pathfinding.Node;
import com.l2jfree.gameserver.geodata.pathfinding.PathFinding;
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.instancemanager.InstanceManager;
import com.l2jfree.gameserver.model.L2CharPosition;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.quest.Quest.QuestEventType;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.skills.Calculator;
import com.l2jfree.gameserver.model.skills.ChanceSkillList;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.FusionSkill;
import com.l2jfree.gameserver.model.skills.IChanceSkillTrigger;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.L2Skill.SkillTargetType;
import com.l2jfree.gameserver.model.skills.SkillUsageRequest;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.effects.AbnormalEffect;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.effects.SpecialEffect;
import com.l2jfree.gameserver.model.skills.funcs.Func;
import com.l2jfree.gameserver.model.skills.funcs.FuncOwner;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillAgathion;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillFusion;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillMount;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSummon;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.Attack;
import com.l2jfree.gameserver.network.packets.server.ChangeMoveType;
import com.l2jfree.gameserver.network.packets.server.ChangeWaitType;
import com.l2jfree.gameserver.network.packets.server.DeleteObject;
import com.l2jfree.gameserver.network.packets.server.FlyToLocation;
import com.l2jfree.gameserver.network.packets.server.MagicSkillCanceled;
import com.l2jfree.gameserver.network.packets.server.MagicSkillLaunched;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.MoveToLocation;
import com.l2jfree.gameserver.network.packets.server.Revive;
import com.l2jfree.gameserver.network.packets.server.SetupGauge;
import com.l2jfree.gameserver.network.packets.server.StaticPacket;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.TeleportToLocation;
import com.l2jfree.gameserver.network.packets.server.ValidateLocation;
import com.l2jfree.gameserver.taskmanager.CoordRevalidator;
import com.l2jfree.gameserver.taskmanager.MovementController;
import com.l2jfree.gameserver.taskmanager.PacketBroadcaster;
import com.l2jfree.gameserver.taskmanager.PacketBroadcaster.BroadcastMode;
import com.l2jfree.gameserver.threadmanager.ExclusiveTask;
import com.l2jfree.gameserver.util.Broadcast;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.gameserver.util.Util.Direction;
import com.l2jfree.lang.L2System;
import com.l2jfree.tools.geometry.Point3D;
import com.l2jfree.tools.random.Rnd;
import com.l2jfree.util.L2Arrays;
import com.l2jfree.util.LazyFastSet;

/**
 * Mother class of all character objects of the world (PC, NPC...)<BR>
 * <BR>
 * L2Creature :<BR>
 * <BR>
 * <li>L2CastleGuardInstance</li>
 * <li>L2DoorInstance</li>
 * <li>L2Npc</li>
 * <li>L2Playable </li>
 * <BR>
 * <BR>
 * <B><U> Concept of L2CreatureTemplate</U> :</B><BR>
 * <BR>
 * Each L2Creature owns generic and static properties (ex : all Keltir have the same number of HP...). All of those properties are stored in a different
 * template for each type of L2Creature. Each template is loaded once in the server cache memory (reduce memory use). When a new instance of L2Creature is
 * spawned, server just create a link between the instance and the template. This link is stored in <B>_template</B><BR>
 * <BR>
 *
 * @version $Revision: 1.53.2.45.2.34 $ $Date: 2005/04/11 10:06:08 $
 */
public abstract class L2Creature extends L2Object
{
	public final static Logger _log = LoggerFactory.getLogger(L2Creature.class);
	
	// =========================================================
	// Data Field
	private Set<L2Creature> _attackByList;
	//private L2Creature				_attackingChar;
	private volatile boolean _isCastingNow = false;
	private volatile boolean _isCastingSimultaneouslyNow = false;
	private L2Skill _lastSimultaneousSkillCast;
	private boolean _block_buffs = false;
	private boolean _isAfraid = false; // Flee in a random direction
	private boolean _isConfused = false; // Attack anyone randomly
	private boolean _isFakeDeath = false; // Fake death
	private boolean _isFallsdown = false; // Falls down [L2J_JP_ADD]
	private boolean _isMuted = false; // Cannot use magic
	private boolean _isPhysicalMuted = false; // Cannot use physical attack
	private boolean _isPhysicalAttackMuted = false; // Cannot use attack
	private volatile boolean _isDead = false;
	private byte _isDying = DEATH_ANIMATION_NONE;
	private boolean _isImmobilized = false;
	private boolean _isParalyzed = false; // cannot do anything
	
	private boolean _isPendingRevive = false;
	private boolean _isRooted = false; // Cannot move until root timed out
	private boolean _isRunning = true;
	private boolean _isNoRndWalk = false; // Is no random walk
	private boolean _isImmobileUntilAttacked = false;
	private boolean _isSleeping = false; // Cannot move/attack until sleep
	// timed out or monster is attacked
	private boolean _isBlessedByNoblesse = false;
	private boolean _isLuckByNoblesse = false;
	private boolean _isBetrayed = false;
	private boolean _isStunned = false; // Cannot move/attack until stun
	// timed out
	protected boolean _isTeleporting = false;
	protected boolean _isInvul = false;
	protected L2Effect _invulEffect = null;
	protected boolean _isDisarmed = false;
	protected boolean _isMarked = false;
	protected boolean _isEradicated = false;
	private final int[] lastPosition = { 0, 0, 0 };
	protected final ICreatureView _view;
	protected final CreatureStat _stat;
	protected final CreatureStatus _status;
	private L2CreatureTemplate _template; // The link on the L2CreatureTemplate
	protected boolean _showSummonAnimation = false;
	// object containing generic and
	// static properties of this
	// L2Creature type (ex : Max HP,
	// Speed...)
	private String _title;
	private double _hpUpdateIncCheck = .0;
	private double _hpUpdateDecCheck = .0;
	private double _hpUpdateInterval = .0;
	
	/** Table of Calculators containing all used calculator */
	private Calculator[] _calculators;
	
	/** Map(Integer, L2Skill) containing all skills of the L2Creature */
	private Map<Integer, L2Skill> _skills;
	private ChanceSkillList _chanceSkills;
	/** Current force buff this caster is casting to a target */
	protected FusionSkill _fusionSkill;
	
	private boolean _isRaid = false;
	private boolean _isFlying;
	
	/**
	 * Objects known by this object
	 */
	protected final CreatureKnownList _knownList;
	
	// =========================================================
	// Constructor
	/**
	 * Constructor of L2Creature.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * Each L2Creature owns generic and static properties (ex : all Keltir have the same number of HP...). All of those properties are stored in a different
	 * template for each type of L2Creature. Each template is loaded once in the server cache memory (reduce memory use). When a new instance of L2Creature is
	 * spawned, server just create a link between the instance and the template This link is stored in <B>_template</B><BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Set the _template of the L2Creature </li>
	 * <li>Set _overloaded to false (the charcater can take more items)</li>
	 * <BR>
	 * <BR>
	 * <li>If L2Creature is a L2Npc, copy skills from template to object</li>
	 * <li>If L2Creature is a L2Npc, link _calculators to NPC_STD_CALCULATOR</li>
	 * <BR>
	 * <BR>
	 * <li>If L2Creature is NOT a L2Npc, create an empty _skills slot</li>
	 * <li>If L2Creature is a L2Player or L2Summon, copy basic Calculator set to object</li>
	 * <BR>
	 * <BR>
	 *
	 * @param objectId
	 *            Identifier of the object to initialized
	 * @param template
	 *            The L2CreatureTemplate to apply to the object
	 */
	public L2Creature(int objectId, L2CreatureTemplate template)
	{
		super(objectId);
		
		// Set its template to the new L2Creature
		_template = template;
		
		_view = initView();
		_stat = initStat();
		_status = initStatus();
		_knownList = initKnownList();
		_effects = initEffects();
		_shots = initShots();
		
		if (this instanceof L2DoorInstance)
			_calculators = Formulas.getStdDoorCalculators();
		else if (this instanceof L2Npc)
			_calculators = NPC_STD_CALCULATOR;
		else
			_calculators = new Calculator[Stats.NUM_STATS];
		
		Formulas.addFuncsToNewCharacter(this);
		
		if (_template instanceof L2NpcTemplate)
		{
			_skills = ((L2NpcTemplate)_template).getSkills();
			
			if (_skills != null)
				for (L2Skill skill : _skills.values())
					skillChanged(null, skill);
		}
		else
			_skills = new ConcurrentHashMap<Integer, L2Skill>();
		
		setIsInvul(true);
	}
	
	private final byte[] _currentZones = new byte[L2Zone.FLAG_SIZE];
	
	public final boolean isInsideZone(byte zone)
	{
		switch (zone)
		{
			case L2Zone.FLAG_PVP:
			{
				if (InstanceManager.getInstance().getInstance(getInstanceId()).isPvPInstance())
					return true;
				if (isInsideZone(L2Zone.FLAG_PEACE))
					return false;
				break;
			}
			case L2Zone.FLAG_PEACE:
			{
				if (InstanceManager.getInstance().getInstance(getInstanceId()).isPvPInstance())
					return false;
			}
		}
		
		final Boolean value = GlobalRestrictions.isInsideZone(this, zone);
		
		if (value != null)
			return value.booleanValue();
		
		return _currentZones[zone] > 0;
	}
	
	public final void setInsideZone(byte zone, boolean state)
	{
		final boolean oldState = isInsideZone(zone);
		
		_currentZones[zone] = (byte)Math.max(0, _currentZones[zone] + (state ? 1 : -1));
		
		final boolean newState = isInsideZone(zone);
		
		if (oldState != newState)
			GlobalRestrictions.isInsideZoneStateChanged(this, zone, newState);
	}
	
	/**
	 * Returns character inventory, default null, overridden in L2Playable types and in L2Npc
	 */
	public Inventory getInventory()
	{
		return null;
	}
	
	/**
	 * @param process
	 * @param itemId
	 * @param count
	 * @param reference
	 * @param sendMessage
	 */
	public boolean destroyItemByItemId(String process, int itemId, long count, L2Object reference, boolean sendMessage)
	{
		// Default: NPCs consume virtual items for their skills
		if (_log.isDebugEnabled())
			_log.warn("destroyItem called for L2Creature!", new IllegalStateException());
		
		return true;
	}
	
	/**
	 * @param process
	 * @param objectId
	 * @param count
	 * @param reference
	 * @param sendMessage
	 */
	public boolean destroyItem(String process, int objectId, long count, L2Object reference, boolean sendMessage)
	{
		// Default: NPCs consume virtual items for their skills
		if (_log.isDebugEnabled())
			_log.warn("destroyItem called for L2Creature!", new IllegalStateException());
		
		return true;
	}
	
	protected void initCreatureStatusUpdateValues()
	{
		_hpUpdateInterval = getMaxHp() / 352.0; // MAX_HP div MAX_HP_BAR_PX
		_hpUpdateIncCheck = getMaxHp();
		_hpUpdateDecCheck = getMaxHp() - _hpUpdateInterval;
	}
	
	// =========================================================
	// Event - Public
	/**
	 * Remove the L2Creature from the world when the decay task is launched.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T REMOVE the object from _allObjects of L2World </B></FONT><BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND Server->Client packets to players</B></FONT><BR>
	 * <BR>
	 */
	public void onDecay()
	{
		L2WorldRegion reg = getWorldRegion();
		decayMe();
		if (reg != null)
			reg.removeFromZones(this);
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		// Force a revalidation
		revalidateZone(true);
	}
	
	public boolean onTeleported()
	{
		if (!isTeleporting())
			return false;
		
		if (this instanceof L2Summon)
		{
			((L2Summon)this).getOwner().sendPacket(
					new TeleportToLocation(this, getPosition().getX(), getPosition().getY(), getPosition().getZ(),
							getPosition().getHeading()));
		}
		
		setIsTeleporting(false);
		spawnMe();
		if (_isPendingRevive)
			doRevive();
		return true;
	}
	
	// =========================================================
	// Method - Public
	/**
	 * Add L2Creature instance that is attacking to the attacker list.<BR>
	 * <BR>
	 *
	 * @param player
	 *            The L2Creature that attacks this one
	 */
	public void addAttackerToAttackByList(L2Creature player)
	{
		if (player == null || player == this)
			return;
		getAttackByList().add(player);
	}
	
	/**
	 * Send a packet to the L2Creature AND to all L2Player in the _knownPlayers of the L2Creature.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * L2Player in the detection area of the L2Creature are identified in <B>_knownPlayers</B>. In order to inform other players of state modification on
	 * the L2Creature, server just need to go through _knownPlayers to send Server->Client Packet<BR>
	 * <BR>
	 */
	public final void broadcastPacket(L2ServerPacket mov)
	{
		Broadcast.toSelfAndKnownPlayers(this, mov);
	}
	
	/**
	 * Send a packet to the L2Creature AND to all L2Player in the radius (max knownlist radius) from the L2Creature.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * L2Player in the detection area of the L2Creature are identified in <B>_knownPlayers</B>. In order to inform other players of state modification on
	 * the L2Creature, server just need to go through _knownPlayers to send Server->Client Packet<BR>
	 * <BR>
	 */
	public final void broadcastPacket(L2ServerPacket mov, int radiusInKnownlist)
	{
		Broadcast.toSelfAndKnownPlayersInRadius(this, mov, radiusInKnownlist);
	}
	
	/**
	 * Returns true if hp update should be done, false if not
	 *
	 * @return boolean
	 */
	protected boolean needHpUpdate(int barPixels)
	{
		double currentHp = getStatus().getCurrentHp();
		
		if (currentHp <= 1.0 || getMaxHp() < barPixels)
			return true;
		
		if (currentHp <= _hpUpdateDecCheck || currentHp >= _hpUpdateIncCheck)
		{
			if (currentHp == getMaxHp())
			{
				_hpUpdateIncCheck = currentHp + 1;
				_hpUpdateDecCheck = currentHp - _hpUpdateInterval;
			}
			else
			{
				double doubleMulti = currentHp / _hpUpdateInterval;
				int intMulti = (int)doubleMulti;
				
				_hpUpdateDecCheck = _hpUpdateInterval * (doubleMulti < intMulti ? intMulti-- : intMulti);
				_hpUpdateIncCheck = _hpUpdateDecCheck + _hpUpdateInterval;
			}
			return true;
		}
		
		return false;
	}
	
	/**
	 * Send the Server->Client packet StatusUpdate with current HP and MP to all other L2Player to inform.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Create the Server->Client packet StatusUpdate with current HP and MP </li>
	 * <li>Send the Server->Client packet StatusUpdate with current HP and MP to all L2Creature called _statusListener that must be informed of HP/MP updates
	 * of this L2Creature </li>
	 * <BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND CP information</B></FONT><BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player : Send current HP,MP and CP to the L2Player and only current HP, MP and Level to all other L2Player of the Party</li>
	 * <BR>
	 * <BR>
	 */
	public final void broadcastStatusUpdate()
	{
		addPacketBroadcastMask(BroadcastMode.BROADCAST_STATUS_UPDATE);
	}
	
	public void broadcastStatusUpdateImpl()
	{
		if (getStatus().getStatusListeners().isEmpty() || !needHpUpdate(352))
			return;
		
		StatusUpdate su = new StatusUpdate(getObjectId());
		su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp());
		su.addAttribute(StatusUpdate.CUR_MP, (int)getStatus().getCurrentMp());
		
		synchronized (getStatus().getStatusListeners())
		{
			for (L2Player player : getStatus().getStatusListeners())
				player.sendPacket(su);
		}
	}
	
	/**
	 * Not Implemented.<BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player</li>
	 * <BR>
	 * <BR>
	 * @param gsp
	 */
	@Deprecated
	public void sendPacket(L2ServerPacket gsp)
	{
	}
	
	/**
	 * @param sm
	 */
	public void sendPacket(SystemMessageId sm)
	{
	}
	
	public void sendPacket(StaticPacket packet)
	{
	}
	
	@Deprecated
	public void sendMessage(String message)
	{
	}
	
	/**
	 * Teleport a L2Creature and its pet if necessary.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Stop the movement of the L2Creature</li>
	 * <li>Set the x,y,z position of the L2Object and if necessary modify its _worldRegion</li>
	 * <li>Send a Server->Client packet TeleportToLocationt to the L2Creature AND to all L2Player in its _knownPlayers</li>
	 * <li>Modify the position of the pet if necessary</li>
	 * <BR>
	 * <BR>
	 */
	public final void teleToLocation(int x, int y, int z, boolean allowRandomOffset)
	{
		teleToLocation(x, y, z, getHeading(), allowRandomOffset);
	}
	
	public final void teleToLocation(int x, int y, int z, int heading, boolean allowRandomOffset)
	{
		// Restrict teleport during restart/shutdown
		if (Shutdown.isActionDisabled(DisableType.TELEPORT))
		{
			sendMessage("Teleport is not allowed during restart/shutdown.");
			return;
		}
		
		// Stop movement
		setTarget(this);
		abortAttack();
		abortCast();
		isFalling(false, 0);
		getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
		setIsTeleporting(true);
		
		if (Config.RESPAWN_RANDOM_ENABLED && allowRandomOffset)
		{
			x += Rnd.get(-Config.RESPAWN_RANDOM_MAX_OFFSET, Config.RESPAWN_RANDOM_MAX_OFFSET);
			y += Rnd.get(-Config.RESPAWN_RANDOM_MAX_OFFSET, Config.RESPAWN_RANDOM_MAX_OFFSET);
		}
		
		z += 5;
		
		if (_log.isDebugEnabled())
			_log.debug("Teleporting to: " + x + ", " + y + ", " + z);
		
		// remove the object from its old location
		decayMe();
		
		// Send a Server->Client packet TeleportToLocationt to the L2Creature AND to all L2Player in the _knownPlayers of the L2Creature
		broadcastPacket(new TeleportToLocation(this, x, y, z, heading));
		
		// Set the x, y, z coords of the object, but do not update it's world region yet - onTeleported() will do it
		getPosition().setWorldPosition(x, y, z);
		// temporary fix for heading on teleports
		if (heading != 0)
			getPosition().setHeading(heading);
		
		isFalling(false, 0);
		
		if (this instanceof L2Player && !((L2Player)this).isInOfflineMode())
		{
		}
		else
			onTeleported();
		
		revalidateZone(true);
	}
	
	public final void teleToLocation(int x, int y, int z)
	{
		teleToLocation(x, y, z, true);
	}
	
	public final void teleToLocation(Location loc)
	{
		teleToLocation(loc, true);
	}
	
	public final void teleToLocation(Location loc, boolean allowRandomOffset)
	{
		int x = loc.getX();
		int y = loc.getY();
		int z = loc.getZ();
		int heading = loc.getHeading();
		
		if (heading == 0)
			teleToLocation(x, y, z, allowRandomOffset);
		else
			teleToLocation(x, y, z, heading, allowRandomOffset);
	}
	
	/** ************************************-+ Fall Damage +-************************************** */
	
	/**
	 * @author Darki699 Calculates if a L2Creature is falling or not. If the character falls, it returns the fall height.
	 * @param  falling: if false no checks are made, but last position is set to the current one
	 * @param  fallHeight: an integer value of the fall already calculated before.
	 * @return A positive integer of the fall height, if not falling returns -1
	 */
	public int isFalling(boolean falling, int fallHeight)
	{
		
		if (isFallsdown() && fallHeight == 0) // Avoid double checks -> let him fall only 1 time =P
			return -1;
		
		// If the boolean falling is set to false, just initialize this fall
		if (!falling || (lastPosition[0] == 0 && lastPosition[1] == 0 && lastPosition[2] == 0))
		{
			lastPosition[0] = getPosition().getX();
			lastPosition[1] = getPosition().getY();
			lastPosition[2] = getPosition().getZ();
			setIsFallsdown(false);
			return -1;
		}
		
		int moveChangeX = Math.abs(lastPosition[0] - getPosition().getX()), moveChangeY =
				Math.abs(lastPosition[1] - getPosition().getY()),
		// Z has a Positive value ONLY if the L2Creature is moving down!
		moveChangeZ = Math.max(lastPosition[2] - getPosition().getZ(), lastPosition[2] - getZ());
		
		// Add acumulated damage to this fall, calling this function at a short delay while the fall is in progress
		if (moveChangeZ > fallSafeHeight() && moveChangeY < moveChangeZ && moveChangeX < moveChangeZ && !isFlying())
		{
			
			setIsFallsdown(true);
			// Calculate the acumulated fall height for a total fall calculation
			fallHeight += moveChangeZ;
			
			// set the last position to the current one for the next future calculation
			lastPosition[0] = getPosition().getX();
			lastPosition[1] = getPosition().getY();
			lastPosition[2] = getPosition().getZ();
			getPosition().setXYZ(lastPosition[0], lastPosition[1], lastPosition[2]);
			
			// Call this function for further checks in the short future (next time we either keep falling, or finalize the fall)
			// This "next time" check is a rough estimate on how much time is needed to calculate the next check, and it is based on the current fall height.
			ThreadPoolManager.getInstance().scheduleGeneral(new CheckFalling(fallHeight), Math.min(1200, moveChangeZ));
			
			// Value returned but not currently used. Maybe useful for future features.
			return fallHeight;
		}
		
		// Stopped falling or is not falling at all.
		lastPosition[0] = getPosition().getX();
		lastPosition[1] = getPosition().getY();
		lastPosition[2] = getPosition().getZ();
		getPosition().setXYZ(lastPosition[0], lastPosition[1], lastPosition[2]);
		
		if (fallHeight > fallSafeHeight())
		{
			doFallDamage(fallHeight);
			return fallHeight;
		}
		
		return -1;
	}
	
	/**
	 * <font color="ff0000"><b>Needs to be completed!</b></font> Add to safeFallHeight the buff resist values which increase the fall resistance.
	 *
	 * @author Darki699
	 * @return integer safeFallHeight is the value from which above it this L2Creature suffers a fall damage.
	 */
	private int fallSafeHeight()
	{
		
		int safeFallHeight = Config.ALT_MINIMUM_FALL_HEIGHT;
		
		try
		{
			if (this instanceof L2Player)
			{
				safeFallHeight =
						((L2Player)this).getTemplate().getBaseFallSafeHeight(
								((L2Player)this).getAppearance().getSex());
			}
		}
		
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		return safeFallHeight;
	}
	
	private int getFallDamage(int fallHeight)
	{
		int damage = (fallHeight - fallSafeHeight()) * 2; // Needs verification for actual damage
		damage = (int)(damage / getStat().calcStat(Stats.FALL_VULN, 1, this, null));
		
		if (damage >= getStatus().getCurrentHp())
		{
			damage = (int)(getStatus().getCurrentHp() - 1);
		}
		
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_START_FAKEDEATH));
		disableAllSkills();
		
		ThreadPoolManager.getInstance().scheduleGeneral(new Runnable() {
			@Override
			public void run()
			{
				L2Creature.this.enableAllSkills();
				broadcastPacket(new ChangeWaitType(L2Creature.this, ChangeWaitType.WT_STOP_FAKEDEATH));
				setIsFallsdown(false);
				
				// For some reason this is needed since the client side changes back to last airborn position after 1 second
				lastPosition[0] = getPosition().getX();
				lastPosition[1] = getPosition().getY();
				lastPosition[2] = getPosition().getZ();
			}
		}, 1100);
		
		return damage;
	}
	
	/**
	 * Receives a integer fallHeight and finalizes the damage effect from the fall.
	 *
	 * @author Darki699
	 */
	private void doFallDamage(int fallHeight)
	{
		isFalling(false, 0);
		
		if (isInvul())
		{
			setIsFallsdown(false);
			return;
		}
		
		int damage = getFallDamage(fallHeight);
		
		if (damage < 1)
			return;
		
		if (this instanceof L2Player)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.FALL_DAMAGE_S1);
			sm.addNumber(damage);
			sendPacket(sm);
		}
		
		getStatus().reduceHp(damage, this);
		getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, this);
	}
	
	/**
	 * @author Darki699 Once a character is falling, we call this to run in order to see when he is not falling down any more. Constructor receives the int
	 *         fallHeight already calculated, and function isFalling(boolean,int) will be called again to terminate the fall and calculate the damage.
	 */
	public class CheckFalling implements Runnable
	{
		private final int _fallHeight;
		
		public CheckFalling(int fallHeight)
		{
			_fallHeight = fallHeight;
		}
		
		@Override
		public void run()
		{
			isFalling(true, _fallHeight);
		}
	}
	
	// =========================================================
	// Method - Private
	/**
	 * Launch a physical attack against a target (Simple, Bow, Pole or Dual).<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Get the active weapon (always equipped in the right hand) </li>
	 * <BR>
	 * <BR>
	 * <li>If weapon is a bow, check for arrows, MP and bow re-use delay (if necessary, equip the L2Player with arrows in left hand)</li>
	 * <li>If weapon is a bow, consume MP and set the new period of bow non re-use </li>
	 * <BR>
	 * <BR>
	 * <li>Get the Attack Speed of the L2Creature (delay (in milliseconds) before next attack) </li>
	 * <li>Select the type of attack to start (Simple, Bow, Pole or Dual) and verify if SoulShot are charged then start calculation</li>
	 * <li>If the Server->Client packet Attack contains at least 1 hit, send the Server->Client packet Attack to the L2Creature AND to all L2Player in the
	 * _knownPlayers of the L2Creature</li>
	 * <li>Notify AI with EVT_READY_TO_ACT</li>
	 * <BR>
	 * <BR>
	 *
	 * @param target
	 *            The L2Creature targeted
	 */
	protected void doAttack(final L2Creature target)
	{
		if (_log.isDebugEnabled())
			_log.debug(getName() + " doAttack: target=" + target);
		
		if (isAlikeDead() || target == null || (this instanceof L2Npc && target.isAlikeDead())
				|| (this instanceof L2Player && target.isDead() && !target.isFakeDeath()))
		{
			// If L2Player is dead or the target is dead, the action is stoped
			getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (isAttackingDisabled())
			return;
		
		// GeoData Los Check here (or dz > 1000)
		if (!(target instanceof L2DoorInstance) && !GeoData.getInstance().canSeeTarget(this, target))
		{
			sendPacket(SystemMessageId.CANT_SEE_TARGET);
			getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		boolean transformed = false;
		
		if (this instanceof L2Player)
		{
			L2Player actor = (L2Player)this;
			if ((actor.isMounted() && actor.getMountNpcId() == 12621)
					|| (actor.isTransformed() && !actor.getTransformation().canDoMeleeAttack()))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			transformed = ((L2Player)this).isTransformed();
		}
		
		if (GlobalRestrictions.isProtected(this, target, null, true))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Get the active weapon instance (always equipped in the right hand)
		L2ItemInstance weaponInst = getActiveWeaponInstance();
		
		// TODO: unhardcode this to support boolean if with that weapon u can attack or not (for ex transform weapons)
		if (weaponInst != null && weaponInst.getItemId() == 9819)
		{
			sendPacket(SystemMessageId.THAT_WEAPON_CANT_ATTACK);
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Get the active weapon item corresponding to the active weapon instance (always equipped in the right hand)
		L2Weapon weaponItem = getActiveWeaponItem();
		
		if ((weaponItem != null && weaponItem.getItemType() == L2WeaponType.ROD))
		{
			// You can't make an attack with a fishing pole.
			sendPacket(SystemMessageId.CANNOT_ATTACK_WITH_FISHING_POLE);
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// BOW and CROSSBOW checks
		if (weaponItem != null && !transformed && this instanceof L2Player)
		{
			// Check for arrows and MP
			if (weaponItem.getItemType() == L2WeaponType.BOW)
			{
				// Verify if the bow can be used
				if (getBowReuseEndEvtReadyToAct().isScheduled())
				{
					// Cancel the action because the bow can't be re-used at this moment
					sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
				
				// Equip arrows needed in left hand and send a Server->Client packet ItemList to the L2Player then return True
				if (!checkAndEquipArrows())
				{
					// Cancel the action because the L2Player have no arrow
					getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
					sendPacket(ActionFailed.STATIC_PACKET);
					sendPacket(SystemMessageId.NOT_ENOUGH_ARROWS);
					return;
				}
				
				// Verify if L2Player owns enough MP
				int saMpConsume = (int)getStat().calcStat(Stats.MP_CONSUME, 0, null, null);
				int mpConsume = saMpConsume == 0 ? weaponItem.getMpConsume() : saMpConsume;
				mpConsume = (int)calcStat(Stats.BOW_MP_CONSUME_RATE, mpConsume, null, null);
				
				if (getStatus().getCurrentMp() < mpConsume)
				{
					// If L2Player doesn't have enough MP, stop the attack
					getBowReuseEndEvtReadyToAct().schedule(1000);
					sendPacket(ActionFailed.STATIC_PACKET);
					sendPacket(SystemMessageId.NOT_ENOUGH_MP);
					return;
				}
				
				// If L2Player have enough MP, the bow consumes it
				if (mpConsume > 0)
					getStatus().reduceMp(mpConsume);
			}
			// Check for bolts
			else if (weaponItem.getItemType() == L2WeaponType.CROSSBOW)
			{
				// Verify if the crossbow can be used
				if (getBowReuseEndEvtReadyToAct().isScheduled())
				{
					// Cancel the action because the crossbow can't be re-used at this moment
					sendPacket(ActionFailed.STATIC_PACKET);
					return;
				}
				
				// Equip bolts needed in left hand and send a Server->Client packet ItemList to the L2Player then return True
				if (!checkAndEquipBolts())
				{
					// Cancel the action because the L2Player have no arrow
					getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
					sendPacket(ActionFailed.STATIC_PACKET);
					sendPacket(SystemMessageId.NOT_ENOUGH_BOLTS);
					return;
				}
			}
		}
		
		// Add the L2Player to _knownObjects and _knownPlayer of the target
		target.getKnownList().addKnownObject(this);
		
		// Reduce the current CP if TIREDNESS configuration is activated
		if (Config.ALT_GAME_TIREDNESS)
			getStatus().setCurrentCp(getStatus().getCurrentCp() - 10);
		
		rechargeShot();
		
		// Verify if soulshots are charged.
		final boolean wasSSCharged = isSoulshotCharged();
		
		// Get the Attack Speed of the L2Creature (delay (in milliseconds) before next attack)
		int timeAtk = calculateTimeBetweenAttacks(target, weaponItem);
		// the hit is calculated to happen halfway to the animation - might need further tuning e.g. in bow, dual case
		int timeToHit = timeAtk / 2;
		// Get the Attack Reuse Delay of the L2Weapon
		int reuse = calculateReuseTime(target, weaponItem);
		
		// Notify AI with EVT_READY_TO_ACT
		getAttackEndEvtReadyToAct().schedule(timeAtk); // normal attacks and bow attacks with queued intentions
		if (reuse > 0)
			getBowReuseEndEvtReadyToAct().schedule(timeAtk + reuse); // bow attack without queued intentions
			
		int ssGrade = 0;
		
		if (weaponItem != null)
			ssGrade = weaponItem.getItemGradeSPlus();
		
		// Create a Server->Client packet Attack
		final Attack attack = new Attack(this, target, wasSSCharged, ssGrade);
		
		// Set the Attacking Body part to CHEST
		setAttackingBodypart();
		// Make sure that char is facing selected target
		// also works: setHeading(Util.convertDegreeToClientHeading(Util.calculateAngleFrom(this, target)));
		setHeading(Util.calculateHeadingFrom(this, target));
		
		boolean hitted;
		// Select the type of attack to start
		if (weaponItem == null || transformed)
			hitted = doAttackHitSimple(attack, target, timeToHit);
		
		else if (weaponItem.getItemType() == L2WeaponType.BOW)
			hitted = doAttackHitByBow(attack, target, timeAtk, reuse);
		
		else if (weaponItem.getItemType() == L2WeaponType.CROSSBOW)
			hitted = doAttackHitByCrossBow(attack, target, timeAtk, reuse);
		
		else if (weaponItem.getItemType() == L2WeaponType.POLE)
			hitted = doAttackHitByPole(attack, target, timeToHit);
		
		else if (isUsingDualWeapon())
			hitted = doAttackHitByDual(attack, target, timeToHit);
		
		else
			hitted = doAttackHitSimple(attack, target, timeToHit);
		
		// Flag the attacker if it's a L2Player outside a PvP area
		L2Player player = getActingPlayer();
		
		if (player != null && player.getPet() != target)
		{
			player.updatePvPStatus(target);
		}
		
		// Check if hit isn't missed
		if (!hitted)
			// Abort the attack of the L2Creature and send Server->Client ActionFailed packet
			abortAttack();
		else
		{
			/*
			 * ADDED BY nexus - 2006-08-17
			 *
			 * As soon as we know that our hit landed, we must discharge any active soulshots. This must be done so to avoid unwanted soulshot consumption.
			 */
			
			// If we didn't miss the hit, discharge the shoulshots, if any
			if (wasSSCharged)
			{
				useSoulshotCharge();
				
				double shotTime = 0.4 * timeAtk;
				
				if (weaponItem != null && weaponItem.getItemType() == L2WeaponType.BOW)
					shotTime = 0.7 * timeAtk;
				else if (isUsingDualWeapon())
					shotTime = 0.5 * timeAtk;
				
				scheduleShotRecharge((int)shotTime);
			}
			
			if (player != null)
			{
				if (player.isCursedWeaponEquipped())
				{
					if (!target.isInvul())
						target.getStatus().setCurrentCp(0);
				}
				else if (player.isHero())
				{
					if (target instanceof L2Player && ((L2Player)target).isCursedWeaponEquipped())
						target.getStatus().setCurrentCp(0); // If Zariche is hitted by a Hero, Cp is reduced to 0
				}
			}
		}
		
		// If the Server->Client packet Attack contains at least 1 hit, send the Server->Client packet Attack
		// to the L2Creature AND to all L2Player in the _knownPlayers of the L2Creature
		if (attack.hasHits())
			broadcastPacket(attack);
	}
	
	private EvtReadyToAct _attackEndEvtReadyToAct;
	private EvtReadyToAct _bowReuseEndEvtReadyToAct;
	
	private EvtReadyToAct getAttackEndEvtReadyToAct()
	{
		if (_attackEndEvtReadyToAct == null)
			_attackEndEvtReadyToAct = new EvtReadyToAct();
		
		return _attackEndEvtReadyToAct;
	}
	
	private EvtReadyToAct getBowReuseEndEvtReadyToAct()
	{
		if (_bowReuseEndEvtReadyToAct == null)
			_bowReuseEndEvtReadyToAct = new EvtReadyToAct();
		
		return _bowReuseEndEvtReadyToAct;
	}
	
	private final class EvtReadyToAct extends ExclusiveTask
	{
		@Override
		protected void onElapsed()
		{
			cancel();
			
			getAI().notifyEvent(CtrlEvent.EVT_READY_TO_ACT);
		}
	}
	
	/**
	 * Launch a Bow attack.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Calculate if hit is missed or not </li>
	 * <li>Consume arrows </li>
	 * <li>If hit isn't missed, calculate if shield defense is efficient </li>
	 * <li>If hit isn't missed, calculate if hit is critical </li>
	 * <li>If hit isn't missed, calculate physical damages </li>
	 * <li>If the L2Creature is a L2Player, Send a Server->Client packet SetupGauge </li>
	 * <li>Create a new hit task with Medium priority</li>
	 * <li>Calculate and set the disable delay of the bow in function of the Attack Speed</li>
	 * <li>Add this hit to the Server-Client packet Attack </li>
	 * <BR>
	 * <BR>
	 *
	 * @param attack
	 *            Server->Client packet Attack in which the hit will be added
	 * @param target
	 *            The L2Creature targeted
	 * @param sAtk
	 *            The Attack Speed of the attacker
	 * @return True if the hit isn't missed
	 */
	private boolean doAttackHitByBow(Attack attack, L2Creature target, int sAtk, int reuse)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		
		// Calculate if hit is missed or not
		boolean miss1 = Formulas.calcHitMiss(this, target);
		
		// Consume arrows
		reduceArrowCount(false);
		
		_move = null;
		
		// Check if hit isn't missed
		if (!miss1)
		{
			// Calculate if shield defense is efficient
			shld1 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit is critical
			crit1 = Formulas.calcCriticalHit(this, target);
			
			// Calculate physical damages
			damage1 = (int)Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.soulshot);
		}
		
		// Check if the L2Creature is a L2Player
		if (this instanceof L2Player)
		{
			// Send a system message
			sendPacket(SystemMessageId.GETTING_READY_TO_SHOOT_AN_ARROW);
			
			// Send a Server->Client packet SetupGauge
			SetupGauge sg = new SetupGauge(SetupGauge.RED, sAtk + reuse);
			sendPacket(sg);
		}
		
		// Create a new hit task with Medium priority
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(target, damage1, crit1, miss1), sAtk);
		
		// Add this hit to the Server-Client packet Attack
		attack.hit(attack.createHit(target, damage1, miss1, crit1, shld1));
		
		// Return true if hit isn't missed
		return !miss1;
	}
	
	/**
	* Launch a CrossBow attack.<BR><BR>
	*
	* <B><U> Actions</U> :</B><BR><BR>
	* <li>Calculate if hit is missed or not </li>
	* <li>Consume bolts </li>
	* <li>If hit isn't missed, calculate if shield defense is efficient </li>
	* <li>If hit isn't missed, calculate if hit is critical </li>
	* <li>If hit isn't missed, calculate physical damages </li>
	* <li>If the L2Creature is a L2Player, Send a Server->Client packet SetupGauge </li>
	* <li>Create a new hit task with Medium priority</li>
	* <li>Calculate and set the disable delay of the crossbow in function of the Attack Speed</li>
	* <li>Add this hit to the Server-Client packet Attack </li><BR><BR>
	*
	* @param attack Server->Client packet Attack in which the hit will be added
	* @param target The L2Creature targeted
	* @param sAtk The Attack Speed of the attacker
	*
	* @return True if the hit isn't missed
	*
	*/
	private boolean doAttackHitByCrossBow(Attack attack, L2Creature target, int sAtk, int reuse)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		
		// Calculate if hit is missed or not
		boolean miss1 = Formulas.calcHitMiss(this, target);
		
		// Consume bows
		reduceArrowCount(true);
		
		_move = null;
		
		// Check if hit isn't missed
		if (!miss1)
		{
			// Calculate if shield defense is efficient
			shld1 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit is critical
			crit1 = Formulas.calcCriticalHit(this, target);
			
			// Calculate physical damages
			damage1 = (int)Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.soulshot);
		}
		
		// Check if the L2Creature is a L2Player
		if (this instanceof L2Player)
		{
			// Send a system message
			sendPacket(SystemMessageId.CROSSBOW_PREPARING_TO_FIRE);
			
			// Send a Server->Client packet SetupGauge
			SetupGauge sg = new SetupGauge(SetupGauge.RED, sAtk + reuse);
			sendPacket(sg);
		}
		
		// Create a new hit task with Medium priority
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(target, damage1, crit1, miss1), sAtk);
		
		// Add this hit to the Server-Client packet Attack
		attack.hit(attack.createHit(target, damage1, miss1, crit1, shld1));
		
		// Return true if hit isn't missed
		return !miss1;
	}
	
	/**
	 * Launch a Dual attack.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Calculate if hits are missed or not </li>
	 * <li>If hits aren't missed, calculate if shield defense is efficient </li>
	 * <li>If hits aren't missed, calculate if hit is critical </li>
	 * <li>If hits aren't missed, calculate physical damages </li>
	 * <li>Create 2 new hit tasks with Medium priority</li>
	 * <li>Add those hits to the Server-Client packet Attack </li>
	 * <BR>
	 * <BR>
	 *
	 * @param attack
	 *            Server->Client packet Attack in which the hit will be added
	 * @param target
	 *            The L2Creature targeted
	 * @return True if hit 1 or hit 2 isn't missed
	 */
	private boolean doAttackHitByDual(Attack attack, L2Creature target, int sAtk)
	{
		int damage1 = 0;
		int damage2 = 0;
		byte shld1 = 0;
		byte shld2 = 0;
		boolean crit1 = false;
		boolean crit2 = false;
		
		// Calculate if hits are missed or not
		boolean miss1 = Formulas.calcHitMiss(this, target);
		boolean miss2 = Formulas.calcHitMiss(this, target);
		
		// Check if hit 1 isn't missed
		if (!miss1)
		{
			// Calculate if shield defense is efficient against hit 1
			shld1 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit 1 is critical
			crit1 = Formulas.calcCriticalHit(this, target);
			
			// Calculate physical damages of hit 1
			damage1 = (int)Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.soulshot);
			damage1 /= 2;
		}
		
		// Check if hit 2 isn't missed
		if (!miss2)
		{
			// Calculate if shield defense is efficient against hit 2
			shld2 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit 2 is critical
			crit2 = Formulas.calcCriticalHit(this, target);
			
			// Calculate physical damages of hit 2
			damage2 = (int)Formulas.calcPhysDam(this, target, null, shld2, crit2, attack.soulshot);
			damage2 /= 2;
		}
		
		// Create a new hit task with Medium priority for hit 1
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(target, damage1, crit1, miss1), sAtk / 2);
		
		// Create a new hit task with Medium priority for hit 2 with a higher delay
		ThreadPoolManager.getInstance().scheduleAi(new HitTask(target, damage2, crit2, miss2), sAtk);
		
		// Add those hits to the Server-Client packet Attack
		attack.hit(attack.createHit(target, damage1, miss1, crit1, shld1),
				attack.createHit(target, damage2, miss2, crit2, shld2));
		
		// Return true if hit 1 or hit 2 isn't missed
		return (!miss1 || !miss2);
	}
	
	/**
	 * Launch a Pole attack.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Get all visible objects in a spherical area near the L2Creature to obtain possible targets </li>
	 * <li>If possible target is the L2Creature targeted, launch a simple attack against it </li>
	 * <li>If possible target isn't the L2Creature targeted but is attackable, launch a simple attack against it </li>
	 * <BR>
	 * <BR>
	 *
	 * @param attack
	 *            Server->Client packet Attack in which the hit will be added
	 * @return True if one hit isn't missed
	 */
	private boolean doAttackHitByPole(Attack attack, L2Creature target, int sAtk)
	{
		final int maxRadius = getPhysicalAttackRange();
		final int maxAngleDiff = (int)getStat().calcStat(Stats.POWER_ATTACK_ANGLE, 120, null, null) / 2;
		final int attackCountMax = (int)getStat().calcStat(Stats.ATTACK_COUNT_MAX, 1, null, null);
		
		// ===========================================================
		
		final ArrayList<HitTask> hitTasks = new ArrayList<HitTask>();
		double attackMulti = 1.0;
		
		hitTasks.add(doAttackHitSimple(attack, target, attackMulti));
		attackMulti *= Config.ALT_POLEARM_DAMAGE_MULTI;
		
		for (L2Object obj : getKnownList().getKnownObjects().values())
		{
			if (hitTasks.size() >= attackCountMax)
				break;
			
			if (obj == target)
				continue; // do not hit twice
				
			// Check if the L2Object is a L2Creature
			if (!(obj instanceof L2Creature))
				continue;
			
			if (obj instanceof L2Summon && ((L2Summon)obj).getOwner() == this)
				continue;
			
			if (!Util.checkIfInRange(maxRadius, this, obj, false))
				continue;
			if (!GeoData.getInstance().canSeeTarget(this, obj))
				continue;
			
			// otherwise hit too high/low. 650 because mob z coord sometimes wrong on hills
			if (Math.abs(obj.getZ() - getZ()) > 650)
				continue;
			if (!target.isInFrontOf(this, maxAngleDiff))
				continue;
			
			L2Creature cha = (L2Creature)obj;
			
			// Launch a simple attack against the L2Creature targeted
			if (cha.isAlikeDead())
				continue;
			
			if (cha != getAI().getAttackTarget() && !cha.isAutoAttackable(this))
				continue;
			
			if (GlobalRestrictions.isProtected(this, cha, null, false))
				continue;
			
			hitTasks.add(doAttackHitSimple(attack, cha, attackMulti));
			attackMulti *= Config.ALT_POLEARM_DAMAGE_MULTI;
		}
		
		// Create a new hit task with Medium priority
		ThreadPoolManager.getInstance().schedule(new PolearmHitTask(hitTasks), sAtk);
		
		// Return true if one hit isn't missed
		for (int i = 0; i < hitTasks.size(); i++)
			if (!hitTasks.get(i)._miss)
				return true;
		
		return false;
	}
	
	/**
	 * Launch a simple attack.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Calculate if hit is missed or not </li>
	 * <li>If hit isn't missed, calculate if shield defense is efficient </li>
	 * <li>If hit isn't missed, calculate if hit is critical </li>
	 * <li>If hit isn't missed, calculate physical damages </li>
	 * <li>Create a new hit task with Medium priority</li>
	 * <li>Add this hit to the Server-Client packet Attack </li>
	 * <BR>
	 * <BR>
	 *
	 * @param attack
	 *            Server->Client packet Attack in which the hit will be added
	 * @param target
	 *            The L2Creature targeted
	 * @return True if the hit isn't missed
	 */
	private boolean doAttackHitSimple(Attack attack, L2Creature target, int sAtk)
	{
		HitTask hitTask = doAttackHitSimple(attack, target, 1.0);
		
		// Create a new hit task with Medium priority
		ThreadPoolManager.getInstance().scheduleAi(hitTask, sAtk);
		
		// Return true if hit isn't missed
		return !hitTask._miss;
	}
	
	private HitTask doAttackHitSimple(Attack attack, L2Creature target, double attackMulti)
	{
		int damage1 = 0;
		byte shld1 = 0;
		boolean crit1 = false;
		
		// Calculate if hit is missed or not
		boolean miss1 = Formulas.calcHitMiss(this, target);
		
		// Check if hit isn't missed
		if (!miss1)
		{
			// Calculate if shield defense is efficient
			shld1 = Formulas.calcShldUse(this, target);
			
			// Calculate if hit is critical
			crit1 = Formulas.calcCriticalHit(this, target);
			
			// Calculate physical damages
			damage1 = (int)Formulas.calcPhysDam(this, target, null, shld1, crit1, attack.soulshot);
			damage1 *= attackMulti;
		}
		
		// Add this hit to the Server-Client packet Attack
		attack.hit(attack.createHit(target, damage1, miss1, crit1, shld1));
		
		// Create a new hit task
		return new HitTask(target, damage1, crit1, miss1);
	}
	
	/**
	 * Manage the casting task (casting and interrupt time, re-use delay...) and display the casting bar and animation on client.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Verify the possibilty of the the cast : skill is a spell, caster isn't muted... </li>
	 * <li>Get the list of all targets (ex : area effects) and define the L2Charcater targeted (its stats will be used in calculation)</li>
	 * <li>Calculate the casting time (base + modifier of MAtkSpd), interrupt time and re-use delay</li>
	 * <li>Send a Server->Client packet MagicSkillUse (to diplay casting animation), a packet SetupGauge (to display casting bar) and a system message </li>
	 * <li>Disable all skills during the casting time (create a task EnableAllSkills)</li>
	 * <li>Disable the skill during the re-use delay (create a task EnableSkill)</li>
	 * <li>Create a task MagicUseTask (that will call method onMagicUseTimer) to launch the Magic Skill at the end of the casting time</li>
	 * <BR>
	 * <BR>
	 *
	 * @param skill
	 *            The L2Skill to use
	 */
	public void doCast(L2Skill skill)
	{
		beginCast(skill, false);
	}
	
	public void doSimultaneousCast(L2Skill skill)
	{
		// queue herbs and potions
		if (isCastingSimultaneouslyNow())
		{
			ThreadPoolManager.getInstance().scheduleAi(new UsePotionTask(skill), 100);
			return;
		}
		
		beginCast(skill, true);
	}
	
	private void beginCast(L2Skill skill, boolean simultaneously)
	{
		if (!checkDoCastConditions(skill))
		{
			if (this instanceof L2Player)
				getAI().setIntention(AI_INTENTION_ACTIVE);
			return;
		}
		
		// Get all possible targets of the skill in a table in function of the skill target type
		final L2Creature[] targets = skill.getTargetList(this);
		
		// Set the target of the skill in function of Skill Type and Target Type
		final L2Creature target = skill.getFirstOfTargetList(this, targets);
		
		if (target == null || GlobalRestrictions.isProtected(this, target, skill, true))
		{
			if (this instanceof L2Player)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				getAI().setIntention(AI_INTENTION_ACTIVE);
			}
			
			return;
		}
		
		//setAttackingChar(this);
		// setLastSkillCast(skill);
		
		// Get the casting time of the skill (base)
		int hitTime = skill.getHitTime();
		int coolTime = skill.getCoolTime();
		int skillInterruptTime = skill.getSkillInterruptTime();
		// Seems this is the indicator in retail
		if (!skill.isMagic())
			skillInterruptTime = 0;
		
		final boolean effectWhileCasting = skill.hasEffectWhileCasting();
		
		// Calculate the casting time of the skill (base + modifier of MAtkSpd)
		// Don't modify the skill time for FORCE_BUFF skills. The skill time for those skills represent the buff time.
		if (!effectWhileCasting && !skill.isStaticHitTime())
		{
			double multi = Formulas.calcCastingRelatedTimeMulti(this, skill);
			
			rechargeShot();
			
			// Calculate altered Cast Speed due to BSpS/SpS
			if (skill.useSpiritShot() && isAnySpiritshotCharged())
				multi *= 0.7;
			
			// if basic hitTime is higher or equal to 500 than the min hitTime is 500
			if (hitTime >= 500)
				multi = Math.max(multi, 500.0 / hitTime);
			
			hitTime *= multi;
			coolTime *= multi;
			skillInterruptTime *= multi;
		}
		
		// Set the _castInterruptTime and casting status (L2Player already has this true)
		if (simultaneously)
		{
			setIsCastingSimultaneouslyNow(true);
			setLastSimultaneousSkillCast(skill);
		}
		else
		{
			// Note: _castEndTime = GameTimeController.getGameTicks() + (coolTime + hitTime) / GameTimeController.MILLIS_IN_TICK;
			setIsCastingNow(true, skillInterruptTime);
		}
		
		// Init the reuse time of the skill
		int reuseDelay = skill.getReuseDelay();
		
		if (Formulas.calcSkillMastery(this, skill))
		{
			reuseDelay = 0;
			sendPacket(SystemMessageId.SKILL_READY_TO_USE_AGAIN);
		}
		else if (!skill.isStaticReuse())
		{
			reuseDelay *= skill.isMagic() ? getStat().getMReuseRate(skill) : getStat().getPReuseRate(skill);
		}
		
		// Check if this skill consume mp on start casting
		int initmpcons = getStat().getMpInitialConsume(skill);
		if (initmpcons > 0)
			getStatus().reduceMp(initmpcons);
		
		// Disable the skill during the re-use delay and create a task EnableSkill with Medium priority to enable it at the end of the re-use delay
		disableSkill(skill.getId(), reuseDelay);
		
		// Make sure that char is facing selected target
		if (target != this)
			setHeading(Util.calculateHeadingFrom(this, target));
		
		// For force buff skills, start the effect as long as the player is casting.
		if (effectWhileCasting)
		{
			// Consume Items if necessary and Send the Server->Client packet InventoryUpdate with Item modification to all the L2Creature
			if (skill.getItemConsume() > 0)
			{
				if (!destroyItemByItemId("Consume", skill.getItemConsumeId(), skill.getItemConsume(), null, false))
				{
					sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					
					if (simultaneously)
						setIsCastingSimultaneouslyNow(false);
					else
						setIsCastingNow(false);
					
					if (this instanceof L2Player)
						getAI().setIntention(AI_INTENTION_ACTIVE);
					return;
				}
			}
			
			if (this instanceof L2Player)
			{
				L2Player player = (L2Player)this;
				// Reset soul bonus for skills
				player.resetLastSoulConsume();
				
				// Consume Souls if necessary
				if (skill.getSoulConsumeCount() > 0 || skill.getMaxSoulConsumeCount() > 0)
				{
					player.decreaseSouls(skill);
				}
				
				// Consume Charges if necessary ... L2SkillChargeDmg does the consume by itself.
				if (skill.getNeededCharges() > 0 && skill.getSkillType() != L2SkillType.CHARGEDAM)
				{
					player.decreaseCharges(skill.getNeededCharges());
				}
			}
			
			if (skill.getSkillType() == L2SkillType.FUSION)
				startFusionSkill(target, skill);
			else
				callSkill(skill, targets);
		}
		
		if (!skill.isToggle()) // otherwise stops movement client side
		{
			broadcastPacket(new MagicSkillUse(this, target, skill, hitTime, reuseDelay));
			// The correct time to send this packet?!!
			broadcastPacket(new MagicSkillLaunched(this, skill, targets));
		}
		
		if (this instanceof L2Playable)
		{
			long protTime = hitTime + coolTime;
			
			if (reuseDelay < protTime)
				protTime /= 2;
			
			((L2Playable)this).setSkillQueueProtectionTime(System.currentTimeMillis() + protTime);
		}
		
		// Send a system message USE_S1 to the L2Creature
		if (this instanceof L2Player && skill.getId() != 1312)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.USE_S1);
			sm.addSkillName(skill);
			getActingPlayer().sendPacket(sm);
		}
		
		//		switch (skill.getTargetType())
		//		{
		//			case TARGET_AURA:
		//			case TARGET_FRONT_AURA:
		//			case TARGET_BEHIND_AURA:
		//			case TARGET_GROUND:
		//			{
		//				if (targets.length == 0)
		//				{
		//					// now cancels both, simultaneous and normal
		//					getAI().notifyEvent(CtrlEvent.EVT_CANCEL);
		//					return;
		//				}
		//				break;
		//			}
		//			default:
		//				break;
		//		}
		
		// Before start AI Cast Broadcast Fly Effect is Need
		if (skill.getFlyType() != null && this instanceof L2Player)
			ThreadPoolManager.getInstance().schedule(new FlyToLocationTask(target, skill), 50);
		
		final MagicEnv magicEnv = new MagicEnv(skill, targets, getTarget(), target, coolTime, simultaneously);
		
		// launch the magic in hitTime milliseconds
		if (hitTime < 10)
		{
			if (effectWhileCasting)
				onMagicHitTimer(magicEnv);
			else
				onMagicLaunchedTimer(magicEnv);
		}
		else
		{
			if (this instanceof L2Player && !effectWhileCasting)
				getActingPlayer().sendPacket(new SetupGauge(SetupGauge.BLUE, hitTime));
			
			// Create a task MagicUseTask to launch the MagicSkill at the end of the casting time (hitTime)
			if (simultaneously)
			{
				if (_skillCast2 != null)
				{
					_skillCast2.cancel(true);
					_skillCast2 = null;
				}
				
				if (effectWhileCasting)
					_skillCast2 = ThreadPoolManager.getInstance().schedule(new MagicHitTimer(magicEnv), hitTime);
				else
					_skillCast2 = ThreadPoolManager.getInstance().schedule(new MagicLaunchedTimer(magicEnv), hitTime);
			}
			else
			{
				if (_skillCast != null)
				{
					_skillCast.cancel(true);
					_skillCast = null;
				}
				
				if (effectWhileCasting)
					_skillCast = ThreadPoolManager.getInstance().schedule(new MagicHitTimer(magicEnv), hitTime);
				else
					_skillCast = ThreadPoolManager.getInstance().schedule(new MagicLaunchedTimer(magicEnv), hitTime);
			}
		}
	}
	
	private boolean checkDoCastConditions(L2Skill skill)
	{
		if (skill == null || isSkillDisabled(skill.getId()) || skill.getSkillType() == L2SkillType.NOTDONE
				|| (skill.getFlyType() != null && isRooted()))
		{
			// Send a Server->Client packet ActionFailed to the L2Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the caster has enough MP
		if (getStatus().getCurrentMp() < getStat().getMpConsume(skill) + getStat().getMpInitialConsume(skill))
		{
			if (this instanceof L2Player)
			{
				// Send a System Message to the caster
				sendPacket(SystemMessageId.NOT_ENOUGH_MP);
				
				// Send a Server->Client packet ActionFailed to the L2Player
				sendPacket(ActionFailed.STATIC_PACKET);
			}
			return false;
		}
		
		// Check if the caster has enough HP
		if (getStatus().getCurrentHp() <= skill.getHpConsume())
		{
			if (this instanceof L2Player)
			{
				// Send a System Message to the caster
				sendPacket(SystemMessageId.NOT_ENOUGH_HP);
				
				// Send a Server->Client packet ActionFailed to the L2Player
				sendPacket(ActionFailed.STATIC_PACKET);
			}
			return false;
		}
		
		switch (skill.getSkillType())
		{
			case SUMMON_TRAP:
			{
				if (isInsideZone(L2Zone.FLAG_PEACE))
				{
					if (this instanceof L2Player)
						sendPacket(SystemMessageId.A_MALICIOUS_SKILL_CANNOT_BE_USED_IN_PEACE_ZONE);
					return false;
				}
				if (this instanceof L2Player && ((L2Player)this).getTrap() != null)
				{
					// Send a Server->Client packet ActionFailed to the L2Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
				
				break;
			}
			case SUMMON:
			{
				if (!((L2SkillSummon)skill).isCubic() && this instanceof L2Player
						&& (getPet() != null || ((L2Player)this).isMounted()))
				{
					if (_log.isDebugEnabled())
						_log.info("player has a pet already. ignore summon skill");
					
					sendPacket(SystemMessageId.YOU_ALREADY_HAVE_A_PET);
					return false;
				}
				break;
			}
			case HEAL:
			{
				if (isInsideZone(L2Zone.FLAG_NOHEAL))
				{
					sendPacket(new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addSkillName(skill));
					return false;
				}
			}
		}
		
		if (!skill.isPotion())
		{
			// Check if the skill is a magic spell and if the L2Creature is not muted
			if (skill.isMagic())
			{
				if (isMuted())
				{
					// Send a Server->Client packet ActionFailed to the L2Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
			else
			{
				// Check if the skill is physical and if the L2Creature is not physical_muted
				if (isPhysicalMuted())
				{
					// Send a Server->Client packet ActionFailed to the L2Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
				else if (isPhysicalAttackMuted()) // Prevent use attack
				{
					// Send a Server->Client packet ActionFailed to the L2Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
		}
		
		// prevent casting signets to peace zone
		if (skill.getSkillType() == L2SkillType.SIGNET || skill.getSkillType() == L2SkillType.SIGNET_CASTTIME)
		{
			L2WorldRegion region = getWorldRegion();
			if (region == null)
				return false;
			boolean canCast = true;
			if (skill.getTargetType() == SkillTargetType.TARGET_GROUND && this instanceof L2Player)
			{
				Point3D wp = ((L2Player)this).getCurrentSkillWorldPosition();
				if (!region.checkEffectRangeInsidePeaceZone(skill, wp.getX(), wp.getY(), wp.getZ()))
					canCast = false;
			}
			else if (!region.checkEffectRangeInsidePeaceZone(skill, getX(), getY(), getZ()))
				canCast = false;
			if (!canCast)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
				sm.addSkillName(skill);
				sendPacket(sm);
				return false;
			}
		}
		
		// Check if the caster owns the weapon needed
		if (!skill.getWeaponDependancy(this, true))
		{
			// Send a Server->Client packet ActionFailed to the L2Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the spell consumes an Item
		// TODO: combine check and consume
		if (skill.getItemConsume() > 0 && getInventory() != null)
		{
			// Get the L2ItemInstance consumed by the spell
			L2ItemInstance requiredItems = getInventory().getItemByItemId(skill.getItemConsumeId());
			
			// Check if the caster owns enough consumed Item to cast
			if (requiredItems == null || requiredItems.getCount() < skill.getItemConsume())
			{
				// Checked: when a summon skill failed, server show required consume item count
				if (skill.getSkillType() == L2SkillType.SUMMON)
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.SUMMONING_SERVITOR_COSTS_S2_S1);
					sm.addItemName(skill.getItemConsumeId());
					sm.addNumber(skill.getItemConsume());
					sendPacket(sm);
					return false;
				}
				
				// Send a System Message to the caster
				sendPacket(SystemMessageId.THERE_ARE_NOT_ENOUGH_NECESSARY_ITEMS_TO_USE_THE_SKILL);
				return false;
			}
		}
		return true;
	}
	
	public void startFusionSkill(L2Creature target, L2Skill skill)
	{
		if (skill.getSkillType() != L2SkillType.FUSION)
			return;
		
		if (_fusionSkill == null)
			_fusionSkill = new FusionSkill(this, target, (L2SkillFusion)skill);
	}
	
	/**
	 * Kill the L2Creature.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Set target to null and cancel Attack or Cast </li>
	 * <li>Stop movement </li>
	 * <li>Stop HP/MP/CP Regeneration task </li>
	 * <li>Stop all active skills effects in progress on the L2Creature </li>
	 * <li>Send the Server->Client packet StatusUpdate with current HP and MP to all other L2Player to inform </li>
	 * <li>Notify L2Creature AI </li>
	 * <BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Npc : Create a DecayTask to remove the corpse of the L2Npc after 7 seconds </li>
	 * <li> L2Attackable : Distribute rewards (EXP, SP, Drops...) and notify Quest Engine </li>
	 * <li> L2Player : Apply Death Penalty, Manage gain/loss Karma and Item Drop </li>
	 * <BR>
	 * <BR>
	 *
	 * @param killer
	 *            The L2Creature who killed it
	 */
	public boolean doDie(L2Creature killer)
	{
		// starts the protection to make sure that the death animation finishes normally
		startDying();
		
		// killing is only possible one time
		synchronized (this)
		{
			if (isDead())
				return false;
			// now reset currentHp to zero
			getStatus().setCurrentHp(0);
			if (isFakeDeath())
				stopFakeDeath(true);
			setIsDead(true);
		}
		// Set target to null and cancel Attack or Cast
		setTarget(null);
		
		// Stop movement
		stopMove(null);
		
		// Stop HP/MP/CP Regeneration task
		getStatus().stopHpMpRegeneration();
		
		// Stop all active skills effects in progress on the L2Creature,
		// if the Character isn't affected by Soul of The Phoenix or Salvation
		if (this instanceof L2Playable)
		{
			final L2Playable pl = (L2Playable)this;
			
			if (pl.isPhoenixBlessed())
			{
				if (pl.getCharmOfLuck()) //remove Lucky Charm if player has SoulOfThePhoenix/Salvation buff
					pl.stopCharmOfLuck(true);
				if (pl.isNoblesseBlessed())
					pl.stopNoblesseBlessing(true);
			}
			// Same thing if the Character isn't a Noblesse Blessed L2Playable
			else if (pl.isNoblesseBlessed())
			{
				pl.stopNoblesseBlessing(true);
				if (pl.getCharmOfLuck()) // remove Lucky Charm if player have Nobless blessing buff
					pl.stopCharmOfLuck(true);
			}
			else
				stopAllEffectsExceptThoseThatLastThroughDeath();
		}
		else
			stopAllEffectsExceptThoseThatLastThroughDeath();
		
		if (this instanceof L2Player && ((L2Player)this).getAgathionId() != 0)
			((L2Player)this).setAgathionId(0);
		
		calculateRewards(killer);
		
		// Send the Server->Client packet StatusUpdate with current HP and MP to all other L2Player to inform
		broadcastStatusUpdate();
		
		if (getWorldRegion() != null)
			getWorldRegion().onDie(this);
		
		// Notify Quest of L2Playable's death
		L2Player actingPlayer = getActingPlayer();
		if (actingPlayer != null)
		{
			// a quest leaves the list in notifyDeath() (exitQuest), so walk a copy
			for (QuestState qs : new ArrayList<QuestState>(actingPlayer.getNotifyQuestOfDeath()))
			{
				qs.getQuest().notifyDeath((killer == null ? this : killer), this, qs);
			}
		}
		
		// Notify L2Creature AI
		getAI().notifyEvent(CtrlEvent.EVT_DEAD, null);
		
		// If character is PhoenixBlessed
		// or has charm of courage inside siege battlefield (exact operation to be confirmed)
		// a resurrection popup will show up
		if (this instanceof L2Summon)
		{
			if (((L2Summon)this).isPhoenixBlessed() && ((L2Summon)this).getOwner() != null)
				((L2Summon)this).getOwner().revivePetRequest(((L2Summon)this).getOwner(), null);
		}
		else if (this instanceof L2Player)
		{
			if (((L2Playable)this).isPhoenixBlessed())
				((L2Player)this).reviveRequest(((L2Player)this), null);
			else if (((L2Player)this).getCharmOfCourage() && ((L2Player)this).isInSiege())
			{
				((L2Player)this).reviveRequest(((L2Player)this), null);
			}
		}
		
		try
		{
			if (_fusionSkill != null)
				abortCast();
			
			for (L2Creature character : getKnownList().getKnownCharacters())
				if (character.getFusionSkill() != null && character.getFusionSkill().getTarget() == this)
					character.abortCast();
		}
		catch (Exception e)
		{
			_log.error("", e);
		}
		
		getAttackByList().clear();
		return true;
	}
	
	/**
	 * @param killer
	 */
	protected void calculateRewards(L2Creature killer)
	{
	}
	
	/** Sets HP, MP and CP and revives the L2Creature. */
	public void doRevive()
	{
		if (!isDead())
			return;
		if (!isTeleporting())
		{
			setIsPendingRevive(false);
			setIsDead(false);
			
			boolean restorefull = false;
			
			if (this instanceof L2Playable && ((L2Playable)this).isPhoenixBlessed())
			{
				restorefull = true;
				((L2Playable)this).stopPhoenixBlessing(true);
			}
			
			if (restorefull)
			{
				//_status.setCurrentCp(getMaxCp()); //this is not confirmed...
				_status.setCurrentHp(getMaxHp()); //confirmed
				_status.setCurrentMp(getMaxMp()); //and also confirmed
			}
			else
			{
				_status.setCurrentHp(getMaxHp() * Config.RESPAWN_RESTORE_HP);
				//_status.setCurrentCp(getMaxCp() * Config.RESPAWN_RESTORE_CP);
				//_status.setCurrentMp(getMaxMp() * Config.RESPAWN_RESTORE_MP);
			}
			
			// Start broadcast status
			broadcastPacket(new Revive(this));
			
			if (getWorldRegion() != null)
				getWorldRegion().onRevive(this);
		}
		else
			setIsPendingRevive(true);
	}
	
	/** Revives the L2Creature using skill.
	 * @param revivePower */
	public void doRevive(double revivePower)
	{
		doRevive();
	}
	
	// =========================================================
	// Property - Public
	/**
	 * Return the L2CreatureAI of the L2Creature and if its null create a new one.
	 */
	public L2CreatureAI getAI()
	{
		if (_ai == null)
		{
			synchronized (this)
			{
				if (_ai == null)
					_ai = initAI();
			}
		}
		
		return _ai;
	}
	
	protected L2CreatureAI initAI()
	{
		return new L2CreatureAI(new AIAccessor());
	}
	
	public final void setAI(L2CreatureAI newAI)
	{
		if (!canReplaceAI())
			return;
		
		final L2CreatureAI oldAI = _ai;
		
		if (oldAI == newAI)
			return;
		
		if (oldAI != null)
			oldAI.stopAITask();
		
		_ai = newAI;
	}
	
	/** Return True if the L2Creature has a L2CreatureAI. */
	public boolean hasAI()
	{
		return _ai != null;
	}
	
	protected boolean canReplaceAI()
	{
		return true;
	}
	
	/** Return True if the L2Creature is RaidBoss or his minion. */
	public boolean isRaid()
	{
		return _isRaid;
	}
	
	/**
	 * Set this Npc as a Raid instance.<BR><BR>
	 * @param isRaid
	 */
	public void setIsRaid(boolean isRaid)
	{
		_isRaid = isRaid;
	}
	
	/** Return a list of L2Creature that attacked. */
	public final Set<L2Creature> getAttackByList()
	{
		if (_attackByList == null)
			_attackByList = new LazyFastSet<L2Creature>();
		
		return _attackByList;
	}
	
	/*
	public final L2Creature getAttackingChar()
	{
		return _attackingChar;
	}
	*/
	
	/**
	 * Set _attackingChar to the L2Creature that attacks this one.<BR>
	 * <BR>
	 *
	 * @param player
	 *            The L2Creature that attcks this one
	 */
	/*
	public final void setAttackingChar(L2Creature player)
	{
		if (player == null || player == this)
			return;
		_attackingChar = player;
		addAttackerToAttackByList(player);
	}
	*/
	
	public final L2Skill getLastSimultaneousSkillCast()
	{
		return _lastSimultaneousSkillCast;
	}
	
	public void setLastSimultaneousSkillCast(L2Skill skill)
	{
		_lastSimultaneousSkillCast = skill;
	}
	
	public final boolean isNoRndWalk()
	{
		return _isNoRndWalk;
	}
	
	public final void setIsNoRndWalk(boolean value)
	{
		_isNoRndWalk = value;
	}
	
	public final boolean isAfraid()
	{
		return _isAfraid;
	}
	
	public final void setIsAfraid(boolean value)
	{
		_isAfraid = value;
		updateAbnormalEffect();
	}
	
	/** Return True if the L2Creature can't use its skills (ex : stun, sleep...). */
	public boolean isAllSkillsDisabled()
	{
		return _allSkillsDisabled || isStunned() || isSleeping() || isImmobileUntilAttacked() || isParalyzed();
	}
	
	/** Return True if the L2Creature can't attack (stun, sleep, attackEndTime, fakeDeath, paralyse). */
	public boolean isAttackingDisabled()
	{
		return isStunned() || isSleeping() || isImmobileUntilAttacked() || isAttackingNow() || isAlikeDead()
				|| isParalyzed() || isFallsdown() || isPhysicalAttackMuted() || isCoreAIDisabled() || isFlying();
	}
	
	public boolean isInProtectedAction()
	{
		return isAllSkillsDisabled() || isCastingNow() || isAttackingNow();
	}
	
	public final Calculator[] getCalculators()
	{
		return _calculators;
	}
	
	public final boolean isConfused()
	{
		return _isConfused;
	}
	
	public final void setIsConfused(boolean value)
	{
		_isConfused = value;
		updateAbnormalEffect();
	}
	
	private static final byte DEATH_ANIMATION_NONE = 0;
	private static final byte DEATH_ANIMATION_RUNNING = 1;
	private static final byte DEATH_ANIMATION_RUNNING_AND_BROADCAST_NEEDED = 2;
	
	/**
	 * Starts the protection to make sure that the death animation finishes normally.
	 */
	public final void startDying()
	{
		_isDying = DEATH_ANIMATION_RUNNING;
		
		// broadcast status now just to be sure
		broadcastFullInfoImpl();
		
		ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run()
			{
				final byte isDying = _isDying;
				
				_isDying = DEATH_ANIMATION_NONE;
				
				if (isDying == DEATH_ANIMATION_RUNNING_AND_BROADCAST_NEEDED)
					broadcastFullInfo();
			}
		}, 2000);
	}
	
	/**
	 * @return indicates that the {@link PacketBroadcaster} should broadcast full info about the character, or not
	 *         because of the death animation
	 */
	public final boolean isDying()
	{
		// normal state
		if (_isDying == DEATH_ANIMATION_NONE)
			return false;
		
		// death animation is in progress
		_isDying = DEATH_ANIMATION_RUNNING_AND_BROADCAST_NEEDED;
		return true;
	}
	
	public final boolean isDead()
	{
		return _isDead;
	}
	
	public final void setIsDead(boolean value)
	{
		_isDead = value;
	}
	
	/** Return True if the L2Creature is dead or use fake death.  */
	public final boolean isAlikeDead()
	{
		return isFakeDeath() || _isDead;
	}
	
	public final boolean isFakeDeath()
	{
		return _isFakeDeath;
	}
	
	public final void setIsFakeDeath(boolean value)
	{
		_isFakeDeath = value;
	}
	
	// [L2J_JP_ADD START]
	public final boolean isFallsdown()
	{
		return _isFallsdown;
	}
	
	public final void setIsFallsdown(boolean value)
	{
		_isFallsdown = value;
	}
	
	public boolean isImmobilized()
	{
		return _isImmobilized;
	}
	
	public void setIsImmobilized(boolean value)
	{
		_isImmobilized = value;
	}
	
	public final boolean isMuted()
	{
		return _isMuted;
	}
	
	public final void setIsMuted(boolean value)
	{
		_isMuted = value;
		updateAbnormalEffect();
	}
	
	public final boolean isPhysicalMuted()
	{
		return _isPhysicalMuted;
	}
	
	public final void setIsPhysicalMuted(boolean value)
	{
		_isPhysicalMuted = value;
		updateAbnormalEffect();
	}
	
	public final boolean isPhysicalAttackMuted()
	{
		return _isPhysicalAttackMuted;
	}
	
	public final void setIsPhysicalAttackMuted(boolean value)
	{
		_isPhysicalAttackMuted = value;
	}
	
	public void disableCoreAI(boolean val)
	{
		_AIdisabled = val;
	}
	
	public boolean isCoreAIDisabled()
	{
		return _AIdisabled;
	}
	
	/** Return True if the L2Creature can't move (stun, root, sleep, overload, paralyzed). */
	public boolean isMovementDisabled()
	{
		// check for isTeleporting to prevent teleport cheating (if appear packet not received)
		return isStunned() || isRooted() || isSleeping() || isTeleporting() || isImmobileUntilAttacked()
				|| isOverloaded() || isParalyzed() || isImmobilized() || isFakeDeath() || isFallsdown();
	}
	
	/** Return True if the L2Creature can not be controlled by the player (confused, afraid). */
	public boolean isOutOfControl()
	{
		return isConfused() || isAfraid();
	}
	
	public boolean isOverloaded()
	{
		return false;
	}
	
	public final boolean isParalyzed()
	{
		return _isParalyzed;
	}
	
	public final void setIsParalyzed(boolean value)
	{
		_isParalyzed = value;
		updateAbnormalEffect();
	}
	
	public final boolean isPendingRevive()
	{
		return isDead() && _isPendingRevive;
	}
	
	public final void setIsPendingRevive(boolean value)
	{
		_isPendingRevive = value;
	}
	
	public final boolean isDisarmed()
	{
		return _isDisarmed;
	}
	
	public final void setIsDisarmed(boolean value)
	{
		_isDisarmed = value;
	}
	
	public final boolean isEradicated()
	{
		return _isEradicated;
	}
	
	public final void setIsEradicated(boolean value)
	{
		_isEradicated = value;
	}
	
	/**
	 * Return the L2Summon of the L2Creature.<BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player</li>
	 * <BR>
	 * <BR>
	 */
	public L2Summon getPet()
	{
		return null;
	}
	
	public final boolean isRooted()
	{
		return _isRooted;
	}
	
	public final void setIsRooted(boolean value)
	{
		_isRooted = value;
		updateAbnormalEffect();
	}
	
	/** Return True if the L2Creature is running. */
	public boolean isRunning()
	{
		return _isRunning;
	}
	
	public final void setIsRunning(boolean value)
	{
		_isRunning = value;
		if (getRunSpeed() != 0)
			broadcastPacket(new ChangeMoveType(this));
		
		broadcastFullInfo();
	}
	
	/** Set the L2Creature movement type to run and send Server->Client packet ChangeMoveType to all others L2Player. */
	public final void setRunning()
	{
		if (!isRunning())
			setIsRunning(true);
	}
	
	public final boolean isSleeping()
	{
		return _isSleeping;
	}
	
	public final void setIsImmobileUntilAttacked(boolean value)
	{
		_isImmobileUntilAttacked = value;
		updateAbnormalEffect();
	}
	
	public final boolean isImmobileUntilAttacked()
	{
		return _isImmobileUntilAttacked;
	}
	
	public final void setIsSleeping(boolean value)
	{
		_isSleeping = value;
		updateAbnormalEffect();
	}
	
	public final boolean isBlessedByNoblesse()
	{
		return _isBlessedByNoblesse;
	}
	
	public final void setIsBlessedByNoblesse(boolean value)
	{
		_isBlessedByNoblesse = value;
	}
	
	public final boolean isLuckByNoblesse()
	{
		return _isLuckByNoblesse;
	}
	
	public final void setIsLuckByNoblesse(boolean value)
	{
		_isLuckByNoblesse = value;
	}
	
	public final boolean isStunned()
	{
		return _isStunned;
	}
	
	public final void setIsStunned(boolean value)
	{
		_isStunned = value;
		updateAbnormalEffect();
	}
	
	public final void setIsPetrified(boolean value)
	{
		if (value)
		{
			startParalyze();
			setIsInvul(true);
		}
		else
		{
			stopParalyze(false);
			setIsInvul(false);
		}
	}
	
	public final boolean isBetrayed()
	{
		return _isBetrayed;
	}
	
	public final void setIsBetrayed(boolean value)
	{
		_isBetrayed = value;
	}
	
	public final boolean isTeleporting()
	{
		return _isTeleporting;
	}
	
	public void setIsTeleporting(boolean value)
	{
		_isTeleporting = value;
	}
	
	public void setIsInvul(boolean b)
	{
		_isInvul = b;
	}
	
	public void setIsInvulByEffect(L2Effect effect)
	{
		_invulEffect = effect;
	}
	
	public boolean isInvul()
	{
		return _isInvul || _invulEffect != null || _isTeleporting
				|| GlobalRestrictions.isInvul(null, this, null, false);
	}
	
	public L2Effect getInvulEffect()
	{
		return _invulEffect;
	}
	
	public boolean isUndead()
	{
		return _template.isUndead();
	}
	
	public final boolean isFlying()
	{
		return _isFlying;
	}
	
	public void setIsFlying(boolean mode)
	{
		_isFlying = mode;
	}
	
	protected CreatureKnownList initKnownList()
	{
		return new CreatureKnownList(this);
	}
	
	@Override
	public CreatureKnownList getKnownList()
	{
		return _knownList;
	}
	
	protected ICreatureView initView()
	{
		return null;
	}
	
	public ICreatureView getView()
	{
		return _view;
	}
	
	protected CreatureStat initStat()
	{
		return new CreatureStat(this);
	}
	
	public CreatureStat getStat()
	{
		return _stat;
	}
	
	protected CreatureStatus initStatus()
	{
		return new CreatureStatus(this);
	}
	
	public CreatureStatus getStatus()
	{
		return _status;
	}
	
	public L2CreatureTemplate getTemplate()
	{
		return _template;
	}
	
	/**
	 * Set the template of the L2Creature.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * Each L2Creature owns generic and static properties (ex : all Keltir have the same number of HP...). All of those properties are stored in a different
	 * template for each type of L2Creature. Each template is loaded once in the server cache memory (reduce memory use). When a new instance of L2Creature is
	 * spawned, server just create a link between the instance and the template This link is stored in <B>_template</B><BR>
	 * <BR>
	 * <B><U> Assert </U> :</B><BR>
	 * <BR>
	 * <li> this instanceof L2Creature</li>
	 * <BR>
	 * <BR
	 */
	protected final void setTemplate(L2CreatureTemplate template)
	{
		_template = template;
	}
	
	/** Return the Title of the L2Creature. */
	public final String getTitle()
	{
		return _title;
	}
	
	/** Set the Title of the L2Creature. */
	public void setTitle(String value)
	{
		_title = value;
	}
	
	/** Set the L2Creature movement type to walk and send Server->Client packet ChangeMoveType to all others L2Player. */
	public final void setWalking()
	{
		if (isRunning())
			setIsRunning(false);
	}
	
	/**
	 * Task launching the function onHitTimer().<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>If the attacker/target is dead or use fake death, notify the AI with EVT_CANCEL and send a Server->Client packet ActionFailed (if attacker is a
	 * L2Player)</li>
	 * <li>If attack isn't aborted, send a message system (critical hit, missed...) to attacker/target if they are L2Player </li>
	 * <li>If attack isn't aborted and hit isn't missed, reduce HP of the target and calculate reflection damage to reduce HP of attacker if necessary </li>
	 * <li>if attack isn't aborted and hit isn't missed, manage attack or cast break of the target (calculating rate, sending message...) </li>
	 * <BR>
	 * <BR>
	 */
	private final class HitTask implements Runnable
	{
		private final L2Creature _hitTarget;
		private final int _damage;
		private final boolean _crit;
		private final boolean _miss;
		
		public HitTask(L2Creature target, int damage, boolean crit, boolean miss)
		{
			_hitTarget = target;
			_damage = damage;
			_crit = crit;
			_miss = miss;
		}
		
		@Override
		public void run()
		{
			onHitTimer(_hitTarget, _damage, _crit, _miss, 1.0);
		}
		
		public void hitByPole(double vampiricMulti)
		{
			onHitTimer(_hitTarget, _damage, _crit, _miss, vampiricMulti);
		}
	}
	
	private static final class PolearmHitTask implements Runnable
	{
		private final ArrayList<HitTask> _hitTasks;
		
		public PolearmHitTask(ArrayList<HitTask> hitTasks)
		{
			_hitTasks = hitTasks;
		}
		
		@Override
		public void run()
		{
			double vampiricMulti = 1.0;
			
			for (int i = 0; i < _hitTasks.size(); i++)
			{
				final HitTask hitTask = _hitTasks.get(i);
				
				hitTask.hitByPole(vampiricMulti);
				
				vampiricMulti *= Config.ALT_POLEARM_VAMPIRIC_MULTI;
			}
		}
	}
	
	private static final class MagicEnv
	{
		private final L2Skill _skill;
		private final List<L2Creature> _targets;
		private final L2Creature _originalTarget;
		private final L2Creature _originalSkillTarget;
		private final int _coolTime;
		private final boolean _simultaneously;
		
		private MagicEnv(L2Skill skill, L2Creature[] targets, L2Object originalTarget,
				L2Creature originalSkillTarget, int coolTime, boolean simultaneously)
		{
			if (skill == null)
				throw new NullPointerException();
			
			_skill = skill;
			_targets = L2Arrays.asForeachSafeList(targets);
			_originalTarget = L2Object.getActingCharacter(originalTarget);
			_originalSkillTarget = originalSkillTarget;
			_coolTime = coolTime;
			_simultaneously = simultaneously;
		}
	}
	
	private final class MagicLaunchedTimer implements Runnable
	{
		private final MagicEnv _magicEnv;
		
		private MagicLaunchedTimer(MagicEnv magicEnv)
		{
			_magicEnv = magicEnv;
		}
		
		@Override
		public void run()
		{
			try
			{
				onMagicLaunchedTimer(_magicEnv);
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
				
				if (_magicEnv._simultaneously)
					setIsCastingSimultaneouslyNow(false);
				else
					setIsCastingNow(false);
			}
		}
	}
	
	private final class MagicHitTimer implements Runnable
	{
		private final MagicEnv _magicEnv;
		
		private MagicHitTimer(MagicEnv magicEnv)
		{
			_magicEnv = magicEnv;
		}
		
		@Override
		public void run()
		{
			try
			{
				onMagicHitTimer(_magicEnv);
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
				
				if (_magicEnv._simultaneously)
					setIsCastingSimultaneouslyNow(false);
				else
					setIsCastingNow(false);
			}
		}
	}
	
	private final class MagicFinalizer implements Runnable
	{
		private final MagicEnv _magicEnv;
		
		private MagicFinalizer(MagicEnv magicEnv)
		{
			_magicEnv = magicEnv;
		}
		
		@Override
		public void run()
		{
			try
			{
				onMagicFinalizer(_magicEnv);
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
				
				if (_magicEnv._simultaneously)
					setIsCastingSimultaneouslyNow(false);
				else
					setIsCastingNow(false);
			}
		}
	}
	
	/** Task of AI notification */
	public class NotifyAITask implements Runnable
	{
		private final CtrlEvent _evt;
		
		NotifyAITask(CtrlEvent evt)
		{
			_evt = evt;
		}
		
		@Override
		public void run()
		{
			getAI().notifyEvent(_evt, null);
		}
	}
	
	// =========================================================
	
	// =========================================================
	// Abnormal Effect - NEED TO REMOVE ONCE L2CHARABNORMALEFFECT IS COMPLETE
	// Data Field
	/** Map 32 bits (0x0000) containing all abnormal effect in progress */
	private int _AbnormalEffects;
	
	protected final CreatureEffects _effects;
	
	protected CreatureEffects initEffects()
	{
		return new CreatureEffects(this);
	}
	
	public CreatureEffects getEffects()
	{
		return _effects;
	}
	
	private int _SpecialEffects;
	
	// Method - Public
	/**
	 * Active abnormal effects flags in the binary mask and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startAbnormalEffect(AbnormalEffect mask)
	{
		startAbnormalEffect(mask.getMask());
	}
	
	/**
	 * Active special effects flags in the binary mask and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startSpecialEffect(SpecialEffect mask)
	{
		startSpecialEffect(mask.getMask());
	}
	
	public final void startAbnormalEffect(int mask)
	{
		if (_AbnormalEffects != (_AbnormalEffects |= mask))
			updateAbnormalEffect();
	}
	
	public final void startSpecialEffect(int mask)
	{
		if (_SpecialEffects != (_SpecialEffects |= mask))
			updateAbnormalEffect();
	}
	
	/**
	 * Active the abnormal effect Confused flag, notify the L2Creature AI and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startConfused()
	{
		setIsConfused(true);
		getAI().notifyEvent(CtrlEvent.EVT_CONFUSED);
	}
	
	/**
	 * Active the abnormal effect Fake Death flag, notify the L2Creature AI and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startFakeDeath()
	{
		// [L2J_JP ADD START]
		setIsFallsdown(true);
		
		/*
		if (Config.ALT_FAIL_FAKEDEATH)
		{
			// It fails in Fake Death at the probability
			setIsFakeDeath(true);
			if (_attackingChar != null)
			{
				int _diff;
				_diff = _attackingChar.getLevel() - getLevel();
				switch (_diff)
				{
				case 1:
				case 2:
				case 3:
				case 4:
				case 5:
					if (Rnd.nextInt(100) >= 95) // fails at 5%.
						setIsFakeDeath(false);
					break;
				case 6:
					if (Rnd.nextInt(100) >= 90) // fails at 10%.
						setIsFakeDeath(false);
					break;
				case 7:
					if (Rnd.nextInt(100) >= 85) // fails at 15%.
						setIsFakeDeath(false);
					break;
				case 8:
					if (Rnd.nextInt(100) >= 80) // fails at 20%.
						setIsFakeDeath(false);
					break;
				case 9:
					if (Rnd.nextInt(100) >= 75) // fails at 25%.
						setIsFakeDeath(false);
					break;
				default:
					if (_diff > 9)
					{
						if (Rnd.nextInt(100) >= 50) // fails at 50%.
							setIsFakeDeath(false);
					}
					else
					{
						setIsFakeDeath(true);
					}
				}
				// If _attackingChar is L2RaidBoss, Fake Death will have failed.
				if (_attackingChar.isRaid())
				{
					setIsFakeDeath(false);
				}
			}
			else
			// attacked from aggressive monster
			{
				if (Rnd.nextInt(100) >= 75) // fails at 25%.
					setIsFakeDeath(false);
			}
		}
		else
		{
			setIsFakeDeath(true);
		}
		// [L2J_JP ADD END]
		*/
		
		setIsFakeDeath(true);
		/* Aborts any attacks/casts if fake dead */
		abortAttack();
		abortCast();
		stopMove(null);
		sendPacket(ActionFailed.STATIC_PACKET);
		getAI().notifyEvent(CtrlEvent.EVT_FAKE_DEATH, null);
		broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_START_FAKEDEATH));
	}
	
	/**
	 * Active the abnormal effect Fear flag, notify the L2Creature AI and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startFear()
	{
		setIsAfraid(true);
		getAI().notifyEvent(CtrlEvent.EVT_AFRAID);
	}
	
	/**
	 * Active the abnormal effect Muted flag, notify the L2Creature AI and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startMuted()
	{
		setIsMuted(true);
		/* Aborts any casts if muted */
		abortCast();
		getAI().notifyEvent(CtrlEvent.EVT_MUTED);
	}
	
	/**
	 * Active the abnormal effect Physical_Muted flag, notify the L2Creature AI and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startPhysicalMuted()
	{
		setIsPhysicalMuted(true);
		getAI().notifyEvent(CtrlEvent.EVT_MUTED);
	}
	
	/**
	 * Active the abnormal effect Root flag, notify the L2Creature AI and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startRooted()
	{
		setIsRooted(true);
		stopMove(null);
		getAI().notifyEvent(CtrlEvent.EVT_ROOTED, null);
	}
	
	/**
	 * Active the abnormal effect Sleep flag, notify the L2Creature AI and send Server->Client UserInfo/CharInfo packet.<BR>
	 * <BR>
	 */
	public final void startSleeping()
	{
		setIsSleeping(true);
		/* Aborts any attacks/casts if sleeped */
		abortAttack();
		abortCast();
		stopMove(null);
		getAI().notifyEvent(CtrlEvent.EVT_SLEEPING, null);
	}
	
	public final void startImmobileUntilAttacked()
	{
		setIsImmobileUntilAttacked(true);
		abortAttack();
		abortCast();
		stopMove(null);
		getAI().notifyEvent(CtrlEvent.EVT_SLEEPING, null);
	}
	
	public final void startLuckNoblesse()
	{
		setIsBlessedByNoblesse(true);
		getAI().notifyEvent(CtrlEvent.EVT_LUCKNOBLESSE, null);
	}
	
	public final void stopLuckNoblesse()
	{
		setIsBlessedByNoblesse(false);
		getAI().notifyEvent(CtrlEvent.EVT_LUCKNOBLESSE, null);
	}
	
	/**
	 * Launch a Stun Abnormal Effect on the L2Creature.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Calculate the success rate of the Stun Abnormal Effect on this L2Creature</li>
	 * <li>If Stun succeed, active the abnormal effect Stun flag, notify the L2Creature AI and send Server->Client UserInfo/CharInfo packet</li>
	 * <li>If Stun NOT succeed, send a system message Failed to the L2Player attacker</li>
	 * <BR>
	 * <BR>
	 */
	public final void startStunning()
	{
		setIsStunned(true);
		/* Aborts any attacks/casts if stunned */
		abortAttack();
		abortCast();
		stopMove(null);
		getAI().notifyEvent(CtrlEvent.EVT_STUNNED, null);
	}
	
	public final void startParalyze()
	{
		setIsParalyzed(true);
		/* Aborts any attacks/casts if paralyzed */
		abortAttack();
		abortCast();
		stopMove(null);
		getAI().notifyEvent(CtrlEvent.EVT_PARALYZED, null);
	}
	
	public final void startBetray()
	{
		setIsBetrayed(true);
		getAI().notifyEvent(CtrlEvent.EVT_BETRAYED, null);
		updateAbnormalEffect();
	}
	
	public final void stopBetray()
	{
		stopEffects(L2EffectType.BETRAY);
		setIsBetrayed(false);
		updateAbnormalEffect();
	}
	
	/**
	 * Modify the abnormal effect map according to the mask.<BR>
	 * <BR>
	 */
	public final void stopAbnormalEffect(AbnormalEffect mask)
	{
		stopAbnormalEffect(mask.getMask());
	}
	
	/**
	 * Modify the special effect map according to the mask.<BR>
	 * <BR>
	 */
	public final void stopSpecialEffect(SpecialEffect mask)
	{
		stopSpecialEffect(mask.getMask());
	}
	
	public final void stopAbnormalEffect(int mask)
	{
		if (_AbnormalEffects != (_AbnormalEffects &= ~mask))
			updateAbnormalEffect();
	}
	
	public final void stopSpecialEffect(int mask)
	{
		if (_SpecialEffects != (_SpecialEffects &= ~mask))
			updateAbnormalEffect();
	}
	
	/**
	 * Stop all active skills effects in progress on the L2Creature.<BR>
	 * <BR>
	 */
	public final void stopAllEffects()
	{
		getEffects().stopAllEffects();
		
		broadcastFullInfo();
	}
	
	public final void stopAllEffects(boolean stopEffectsThatLastThroughDeathToo)
	{
		getEffects().stopAllEffects(stopEffectsThatLastThroughDeathToo);
		
		broadcastFullInfo();
	}
	
	public final void stopAllEffectsExceptThoseThatLastThroughDeath()
	{
		getEffects().stopAllEffectsExceptThoseThatLastThroughDeath();
		
		broadcastFullInfo();
	}
	
	/**
	 * Stop a specified/all Confused abnormal L2Effect.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Delete a specified/all (if effect=null) Confused abnormal L2Effect from L2Creature and update client magic icon </li>
	 * <li>Set the abnormal effect flag _confused to False </li>
	 * <li>Notify the L2Creature AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * <BR>
	 * <BR>
	 */
	public final void stopConfused(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.CONFUSION);
		
		setIsConfused(false);
		getAI().notifyEvent(CtrlEvent.EVT_THINK, null);
	}
	
	public final void startPhysicalAttackMuted()
	{
		setIsPhysicalAttackMuted(true);
		abortAttack();
	}
	
	public final void stopPhysicalAttackMuted(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.PHYSICAL_ATTACK_MUTE);
		
		setIsPhysicalAttackMuted(false);
	}
	
	/**
	 * Stop and remove the L2Effects corresponding to the L2Skill Identifier and update client magic icon.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All active skills effects in progress on the L2Creature are identified in ConcurrentHashMap(Integer,L2Effect) <B>_effects</B>. The Integer key of
	 * _effects is the L2Skill Identifier that has created the L2Effect.<BR>
	 * <BR>
	 *
	 * @param skillId
	 *            The L2Skill Identifier of the L2Effect to remove from _effects
	 */
	public final void stopSkillEffects(int skillId)
	{
		getEffects().stopEffects(skillId);
	}
	
	/**
	 * Stop and remove all L2Effect of the selected type (ex : BUFF, DMG_OVER_TIME...) from the L2Creature and update client magic icon.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All active skills effects in progress on the L2Creature are identified in ConcurrentHashMap(Integer,L2Effect) <B>_effects</B>. The Integer key of
	 * _effects is the L2Skill Identifier that has created the L2Effect.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Remove Func added by this effect from the L2Creature Calculator (Stop L2Effect)</li>
	 * <li>Remove the L2Effect from _effects of the L2Creature</li>
	 * <li>Update active skills in progress icons on player client</li>
	 * <BR>
	 * <BR>
	 *
	 * @param type
	 *            The type of effect to stop ((ex : BUFF, DMG_OVER_TIME...)
	 */
	public final void stopEffects(L2EffectType type)
	{
		getEffects().stopEffects(type);
	}
	
	/**
	 * Stop a specified/all Fake Death abnormal L2Effect.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Delete a specified/all (if effect=null) Fake Death abnormal L2Effect from L2Creature and update client magic icon </li>
	 * <li>Set the abnormal effect flag _fake_death to False </li>
	 * <li>Notify the L2Creature AI</li>
	 * <BR>
	 * <BR>
	 */
	public final void stopFakeDeath(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.FAKE_DEATH);
		
		setIsFakeDeath(false);
		setIsFallsdown(false); // [L2J_JP_ADD]
		// if this is a player instance, start the grace period for this character (grace from mobs only)!
		if (this instanceof L2Player)
		{
			((L2Player)this).setRecentFakeDeath(true);
		}
		ChangeWaitType revive = new ChangeWaitType(this, ChangeWaitType.WT_STOP_FAKEDEATH);
		broadcastPacket(revive);
		//TODO: Temp hack: players see FD on ppl that are moving: Teleport to someone who uses FD - if he gets up he will fall down again for that client -
		// even tho he is actually standing... Probably bad info in CharInfo packet?
		broadcastPacket(new Revive(this));
		getAI().notifyEvent(CtrlEvent.EVT_THINK, null);
	}
	
	/**
	 * Stop a specified/all Fear abnormal L2Effect.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Delete a specified/all (if effect=null) Fear abnormal L2Effect from L2Creature and update client magic icon </li>
	 * <li>Set the abnormal effect flag _afraid to False </li>
	 * <li>Notify the L2Creature AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * <BR>
	 * <BR>
	 */
	public final void stopFear(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.FEAR);
		
		setIsAfraid(false);
	}
	
	/**
	 * Stop a specified/all Muted abnormal L2Effect.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Delete a specified/all (if effect=null) Muted abnormal L2Effect from L2Creature and update client magic icon </li>
	 * <li>Set the abnormal effect flag _muted to False </li>
	 * <li>Notify the L2Creature AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * <BR>
	 * <BR>
	 */
	public final void stopMuted(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.MUTE);
		
		setIsMuted(false);
	}
	
	public final void stopPhysicalMuted(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.PHYSICAL_MUTE);
		
		setIsPhysicalMuted(false);
	}
	
	/**
	 * Stop a specified/all Root abnormal L2Effect.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Delete a specified/all (if effect=null) Root abnormal L2Effect from L2Creature and update client magic icon </li>
	 * <li>Set the abnormal effect flag _rooted to False </li>
	 * <li>Notify the L2Creature AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * <BR>
	 * <BR>
	 */
	public final void stopRooting(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.ROOT);
		
		setIsRooted(false);
		getAI().notifyEvent(CtrlEvent.EVT_THINK, null);
	}
	
	/**
	 * Stop a specified/all Sleep abnormal L2Effect.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Delete a specified/all (if effect=null) Sleep abnormal L2Effect from L2Creature and update client magic icon </li>
	 * <li>Set the abnormal effect flag _sleeping to False </li>
	 * <li>Notify the L2Creature AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * <BR>
	 * <BR>
	 */
	public final void stopSleeping(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.SLEEP);
		
		setIsSleeping(false);
		getAI().notifyEvent(CtrlEvent.EVT_THINK, null);
	}
	
	public final void stopImmobileUntilAttacked(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.IMMOBILEUNTILATTACKED);
		
		setIsImmobileUntilAttacked(false);
		getAI().notifyEvent(CtrlEvent.EVT_THINK, null);
	}
	
	public final void stopNoblesse()
	{
		stopEffects(L2EffectType.NOBLESSE_BLESSING);
		stopEffects(L2EffectType.LUCKNOBLESSE);
		setIsBlessedByNoblesse(false);
		setIsLuckByNoblesse(false);
		getAI().notifyEvent(CtrlEvent.EVT_LUCKNOBLESSE, null);
	}
	
	/**
	 * Stop a specified/all Stun abnormal L2Effect.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Delete a specified/all (if effect=null) Stun abnormal L2Effect from L2Creature and update client magic icon </li>
	 * <li>Set the abnormal effect flag _stuned to False </li>
	 * <li>Notify the L2Creature AI</li>
	 * <li>Send Server->Client UserInfo/CharInfo packet</li>
	 * <BR>
	 * <BR>
	 */
	public final void stopStunning(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.STUN);
		
		setIsStunned(false);
		getAI().notifyEvent(CtrlEvent.EVT_THINK, null);
	}
	
	public final void stopParalyze(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.PARALYZE);
		
		setIsParalyzed(false);
		getAI().notifyEvent(CtrlEvent.EVT_THINK, null);
	}
	
	/**
	* Stop L2Effect: Transformation<BR><BR>
	*
	* <B><U> Actions</U> :</B><BR><BR>
	* <li>Remove Transformation Effect</li>
	* <li>Notify the L2Creature AI</li>
	* <li>Send Server->Client UserInfo/CharInfo packet</li><BR><BR>
	*
	*/
	public final void stopTransformation(boolean all)
	{
		if (all)
			stopEffects(L2EffectType.TRANSFORMATION);
		
		// if this is a player instance, then untransform, also set the transform_id column equal to 0 if not cursed.
		if (this instanceof L2Player)
		{
			if (((L2Player)this).getTransformation() != null)
			{
				((L2Player)this).untransform();
			}
		}
		
		getAI().notifyEvent(CtrlEvent.EVT_THINK, null);
		updateAbnormalEffect();
	}
	
	/**
	 * Not Implemented.<BR>
	 * <BR>
	 * <B><U> Overridden in</U> :</B><BR>
	 * <BR>
	 * <li>L2Npc</li>
	 * <li>L2Player</li>
	 * <li>L2Summon</li>
	 * <li>L2DoorInstance</li>
	 * <BR>
	 * <BR>
	 */
	public final void updateAbnormalEffect()
	{
		broadcastFullInfo();
	}
	
	// Property - Public
	/**
	 * Return a map of 16 bits (0x0000) containing all abnormal effect in progress for this L2Creature.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * In Server->Client packet, each effect is represented by 1 bit of the map (ex : BLEEDING = 0x0001 (bit 1), SLEEP = 0x0080 (bit 8)...). The map is
	 * calculated by applying a BINARY OR operation on each effect.<BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Server Packet : CharInfo, NpcInfo, NpcInfoPoly, UserInfo...</li>
	 * <BR>
	 * <BR>
	 */
	public int getAbnormalEffect()
	{
		int ae = _AbnormalEffects;
		if (!isFlying() && isStunned())
			ae |= AbnormalEffect.STUN.getMask();
		if (!isFlying() && isRooted())
			ae |= AbnormalEffect.ROOT.getMask();
		if (isSleeping())
			ae |= AbnormalEffect.SLEEP.getMask();
		if (isConfused())
			ae |= AbnormalEffect.CONFUSED.getMask();
		if (isMuted())
			ae |= AbnormalEffect.MUTED.getMask();
		if (isPhysicalMuted())
			ae |= AbnormalEffect.MUTED.getMask();
		return ae;
	}
	
	/**
	* Return a map of 32 bits (0x00000000) containing all special effect in progress for this L2Creature.<BR><BR>
	*
	* <B><U> Concept</U> :</B><BR><BR>
	* In Server->Client packet, each effect is represented by 1 bit of the map (ex : INVULNERABLE = 0x0001 (bit 1), PINK_AFFRO = 0x0020 (bit 6)...).
	* The map is calculated by applying a BINARY OR operation on each effect.<BR><BR>
	*
	* <B><U> Example of use </U> :</B><BR><BR>
	* <li> Server Packet : CharInfo, UserInfo...</li><BR><BR>
	*/
	public int getSpecialEffect()
	{
		int se = _SpecialEffects;
		if (isFlying() && isStunned())
			se |= SpecialEffect.S_AIR_STUN.getMask();
		if (isFlying() && isRooted())
			se |= SpecialEffect.S_AIR_ROOT.getMask();
		return se;
	}
	
	/**
	 * Return all active skills effects in progress on the L2Creature.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All active skills effects in progress on the L2Creature are identified in <B>_effects</B>. The Integer key of _effects is the L2Skill Identifier that
	 * has created the effect.<BR>
	 * <BR>
	 *
	 * @return A table containing all active skills effect in progress on the L2Creature
	 */
	public final L2Effect[] getAllEffects()
	{
		return getEffects().getAllEffects();
	}
	
	/**
	 * Return L2Effect in progress on the L2Creature corresponding to the L2Skill Identifier.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All active skills effects in progress on the L2Creature are identified in <B>_effects</B>.
	 *
	 * @param index
	 *            The L2Skill Identifier of the L2Effect to return from the _effects
	 * @return The L2Effect corresponding to the L2Skill Identifier
	 */
	public final L2Effect getFirstEffect(int skillId)
	{
		return getEffects().getFirstEffect(skillId);
	}
	
	/**
	 * Return the first L2Effect in progress on the L2Creature created by the L2Skill.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All active skills effects in progress on the L2Creature are identified in <B>_effects</B>.
	 *
	 * @param skill
	 *            The L2Skill whose effect must be returned
	 * @return The first L2Effect created by the L2Skill
	 */
	public final L2Effect getFirstEffect(L2Skill skill)
	{
		return getEffects().getFirstEffect(skill);
	}
	
	/**
	 * Return the first L2Effect in progress on the L2Creature corresponding to the Effect Type (ex : BUFF, STUN, ROOT...).<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All active skills effects in progress on the L2Creature are identified in <B>_effects</B>.
	 * <BR>
	 *
	 * @param tp
	 *            The Effect Type of skills whose effect must be returned
	 * @return The first L2Effect corresponding to the Effect Type
	 */
	public final L2Effect getFirstEffect(L2EffectType tp)
	{
		return getEffects().getFirstEffect(tp);
	}
	
	// =========================================================
	
	// =========================================================
	// NEED TO ORGANIZE AND MOVE TO PROPER PLACE
	/** This class permit to the L2Creature AI to obtain informations and uses L2Creature method */
	public class AIAccessor
	{
		public AIAccessor()
		{
		}
		
		/**
		 * Return the L2Creature managed by this Accessor AI.<BR>
		 * <BR>
		 */
		public L2Creature getActor()
		{
			return L2Creature.this;
		}
		
		/**
		 * Accessor to L2Creature moveToLocation() method with an interaction area.<BR>
		 * <BR>
		 */
		public void moveTo(int x, int y, int z, int offset)
		{
			moveToLocation(x, y, z, offset);
		}
		
		/**
		 * Accessor to L2Creature moveToLocation() method without interaction area.<BR>
		 * <BR>
		 */
		public void moveTo(int x, int y, int z)
		{
			moveToLocation(x, y, z, 0);
		}
		
		/**
		 * Accessor to L2Creature stopMove() method.<BR>
		 * <BR>
		 */
		public void stopMove(L2CharPosition pos)
		{
			L2Creature.this.stopMove(pos);
		}
		
		/**
		 * Accessor to L2Creature doAttack() method.<BR>
		 * <BR>
		 */
		public void doAttack(L2Creature target)
		{
			getEffects().dispelOnAction();
			getEffects().dispelOnAttack();
			
			if (L2Creature.this != target)
				L2Creature.this.doAttack(target);
		}
		
		/**
		 * Accessor to L2Creature doCast() method.<BR>
		 * <BR>
		 */
		public void doCast(L2Skill skill)
		{
			getEffects().dispelOnAction();
			getEffects().dispelOnAttack();
			
			L2Creature.this.doCast(skill);
		}
		
		/**
		 * Cancel the AI.<BR>
		 * <BR>
		 */
		public final void detachAI()
		{
			if (!canReplaceAI())
				return;
			
			_ai = null;
		}
	}
	
	/**
	 * This class group all mouvement data.<BR>
	 * <BR>
	 * <B><U> Data</U> :</B><BR>
	 * <BR>
	 * <li>_moveTimestamp : Last time position update</li>
	 * <li>_xDestination, _yDestination, _zDestination : Position of the destination</li>
	 * <li>_xMoveFrom, _yMoveFrom, _zMoveFrom : Position of the origin</li>
	 * <li>_moveStartTime : Start time of the movement</li>
	 * <li>_ticksToMove : Nb of ticks between the start and the destination</li>
	 * <li>_xSpeedTicks, _ySpeedTicks : Speed in unit/ticks</li>
	 * <BR>
	 * <BR>
	 */
	public static class MoveData
	{
		// when we retrieve x/y/z we use GameTimeControl.getGameTicks()
		// if we are moving, but move timestamp==gameticks, we don't need
		// to recalculate position
		public int _moveStartTime;
		public int _moveTimestamp; // last update
		public int _xDestination;
		public int _yDestination;
		public int _zDestination;
		public double _xAccurate; // otherwise there would be rounding errors
		public double _yAccurate;
		public double _zAccurate;
		public int _yMoveFrom;
		public int _zMoveFrom;
		public int _heading;
		
		public boolean disregardingGeodata;
		public int onGeodataPathIndex;
		public Node[] geoPath;
		public int geoPathAccurateTx;
		public int geoPathAccurateTy;
		public int geoPathGtx;
		public int geoPathGty;
	}
	
	/** Table containing all skillId that are disabled */
	private Map<Integer, ScheduledFuture<?>> _disabledSkills;
	private boolean _allSkillsDisabled;
	
	// private int _flyingRunSpeed;
	// private int _floatingWalkSpeed;
	// private int _flyingWalkSpeed;
	// private int _floatingRunSpeed;
	
	/** Movement data of this L2Creature */
	protected MoveData _move;
	
	/** L2Charcater targeted by the L2Creature */
	private L2Object _target = null;
	
	// set by the start of attack, in game ticks
	private int _attacking;
	
	private long _castInterruptTime;
	
	/** Table of calculators containing all standard NPC calculator (ex : ACCURACY_COMBAT, EVASION_RATE */
	private static final Calculator[] NPC_STD_CALCULATOR;
	static
	{
		NPC_STD_CALCULATOR = Formulas.getStdNPCCalculators();
	}
	
	private L2CreatureAI _ai;
	
	/** Future Skill Cast */
	protected Future<?> _skillCast;
	protected Future<?> _skillCast2;
	
	/**
	 * Return True if the L2Creature is avoiding a geodata obstacle.<BR>
	 * <BR>
	 */
	public final boolean isOnGeodataPath()
	{
		MoveData m = _move;
		if (m == null)
			return false;
		
		if (m.onGeodataPathIndex == -1)
			return false;
		
		return m.onGeodataPathIndex != m.geoPath.length - 1;
	}
	
	public final void addStatFunc(Func f)
	{
		if (f == null)
			return;
		
		synchronized (_calculators)
		{
			// Check if Calculator set is linked to the standard Calculator set of NPC
			if (_calculators == NPC_STD_CALCULATOR)
			{
				// Create a copy of the standard NPC Calculator set
				_calculators = new Calculator[Stats.NUM_STATS];
				
				for (int i = 0; i < Stats.NUM_STATS; i++)
				{
					if (NPC_STD_CALCULATOR[i] != null)
						_calculators[i] = new Calculator(NPC_STD_CALCULATOR[i]);
				}
			}
			
			// Select the Calculator of the affected state in the Calculator set
			int stat = f.stat.ordinal();
			
			if (_calculators[stat] == null)
				_calculators[stat] = new Calculator();
			
			// Add the Func to the calculator corresponding to the state
			_calculators[stat].addFunc(f);
			
			if (this instanceof L2Player)
				((L2Player)this).onFuncAddition(f);
		}
		
		broadcastFullInfo();
	}
	
	public final void addStatFuncs(Func[] funcs)
	{
		for (Func f : funcs)
			addStatFunc(f);
	}
	
	public final void removeStatsOwner(FuncOwner owner)
	{
		// Go through the Calculator set
		synchronized (_calculators)
		{
			for (int i = 0; i < _calculators.length; i++)
			{
				if (_calculators[i] != null)
				{
					// Delete all Func objects of the selected owner
					_calculators[i].removeOwner(owner, this);
					
					if (_calculators[i].size() == 0)
						_calculators[i] = null;
				}
			}
			
			// If possible, free the memory and just create a link on NPC_STD_CALCULATOR
			if (this instanceof L2Npc)
			{
				int i = 0;
				for (; i < Stats.NUM_STATS; i++)
				{
					if (!Calculator.equalsCals(_calculators[i], NPC_STD_CALCULATOR[i]))
						break;
				}
				
				if (i >= Stats.NUM_STATS)
					_calculators = NPC_STD_CALCULATOR;
			}
		}
		
		broadcastFullInfo();
	}
	
	public int getClientX()
	{
		return getX();
	}
	
	public int getClientY()
	{
		return getY();
	}
	
	public int getClientZ()
	{
		return getZ();
	}
	
	public int getClientHeading()
	{
		return getHeading();
	}
	
	public final int getXdestination()
	{
		MoveData m = _move;
		
		if (m != null)
			return m._xDestination;
		
		return getX();
	}
	
	/**
	 * Return the Y destination of the L2Creature or the Y position if not in movement.<BR>
	 * <BR>
	 */
	public final int getYdestination()
	{
		MoveData m = _move;
		
		if (m != null)
			return m._yDestination;
		
		return getY();
	}
	
	/**
	 * Return the Z destination of the L2Creature or the Z position if not in movement.<BR>
	 * <BR>
	 */
	public final int getZdestination()
	{
		MoveData m = _move;
		
		if (m != null)
			return m._zDestination;
		
		return getZ();
	}
	
	/**
	 * Return True if the L2Creature is in combat.<BR>
	 * <BR>
	 */
	public boolean isInCombat()
	{
		return (getAI().getAttackTarget() != null || getAI().isAutoAttacking());
	}
	
	/**
	 * Return True if the L2Creature is moving.<BR>
	 * <BR>
	 */
	@Override
	public final boolean isMoving()
	{
		return _move != null;
	}
	
	/**
	 * Return True if the L2Creature is casting.<BR>
	 * <BR>
	 */
	public final boolean isCastingNow()
	{
		return _isCastingNow;
	}
	
	public final void setIsCastingNow(boolean value)
	{
		setIsCastingNow(value, 0);
	}
	
	public final void setIsCastingNow(boolean value, int interruptTime)
	{
		_isCastingNow = value;
		
		if (!value)
		{
			// safeguard for cannot be interrupt any more
			_castInterruptTime = 0;
			
			if (_skillCast != null)
			{
				_skillCast.cancel(false);
				_skillCast = null;
			}
		}
		else
		{
			_castInterruptTime = L2System.milliTime() + interruptTime;
		}
	}
	
	public final boolean isCastingSimultaneouslyNow()
	{
		return _isCastingSimultaneouslyNow;
	}
	
	public final void setIsCastingSimultaneouslyNow(boolean value)
	{
		_isCastingSimultaneouslyNow = value;
		
		if (!value)
		{
			if (_skillCast2 != null)
			{
				_skillCast2.cancel(false);
				_skillCast2 = null;
			}
		}
	}
	
	/**
	 * Return True if the cast of the L2Creature can be aborted.<BR>
	 * <BR>
	 */
	public final boolean canAbortCast()
	{
		return _castInterruptTime > L2System.milliTime();
	}
	
	/**
	 * Return True if the L2Creature is attacking.<BR>
	 * <BR>
	 */
	public boolean isAttackingNow()
	{
		return getAttackEndEvtReadyToAct().isScheduled();
	}
	
	/**
	 * Return True if the L2Creature has aborted its attack.<BR>
	 * <BR>
	 */
	public final boolean isAttackAborted()
	{
		return _attacking <= 0;
	}
	
	/**
	 * Abort the attack of the L2Creature and send Server->Client ActionFailed packet.<BR>
	 * <BR>
	 */
	public final void abortAttack()
	{
		if (isAttackingNow())
		{
			_attacking = 0;
			sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	/**
	 * Returns body part (paperdoll slot) we are targeting right now
	 */
	public final int getAttackingBodyPart()
	{
		return _attacking;
	}
	
	/**
	 * Abort the cast of the L2Creature and send Server->Client MagicSkillCanceld/ActionFailed packet.<BR>
	 * <BR>
	 */
	public final void abortCast()
	{
		if (isCastingNow() || isCastingSimultaneouslyNow())
		{
			if (getFusionSkill() != null)
				getFusionSkill().onCastAbort();
			
			stopEffects(L2EffectType.SIGNET_GROUND);
			
			if (_allSkillsDisabled)
				enableAllSkills(); // this remains for forced skill use, e.g. scroll of escape
			setIsCastingNow(false);
			setIsCastingSimultaneouslyNow(false);
			//if (this instanceof L2Player)
			getAI().notifyEvent(CtrlEvent.EVT_FINISH_CASTING); // setting back previous intention
			broadcastPacket(new MagicSkillCanceled(getObjectId())); // broadcast packet to stop animations client-side
			sendPacket(ActionFailed.STATIC_PACKET); // send an "action failed" packet to the caster
		}
	}
	
	/**
	 * Update the position of the L2Creature during a movement and return True if the movement is finished.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * At the beginning of the move action, all properties of the movement are stored in the MoveData object called <B>_move</B> of the L2Creature. The
	 * position of the start point and of the destination permit to estimated in function of the movement speed the time to achieve the destination.<BR>
	 * <BR>
	 * When the movement is started (ex : by MovetoLocation), this method will be called each 0.1 sec to estimate and update the L2Creature position on the
	 * server. Note, that the current server position can differe from the current client position even if each movement is straight foward. That's why, client
	 * send regularly a Client->Server ValidatePosition packet to eventually correct the gap on the server. But, it's always the server position that is used in
	 * range calculation.<BR>
	 * <BR>
	 * At the end of the estimated movement time, the L2Creature position is automatically set to the destination position even if the movement is not
	 * finished.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : The current Z position is obtained FROM THE CLIENT by the Client->Server ValidatePosition Packet. But x and y
	 * positions must be calculated to avoid that players try to modify their movement speed.</B></FONT><BR>
	 * <BR>
	 *
	 * @param gameTicks
	 *            Nb of ticks since the server start
	 * @return True if the movement is finished
	 */
	public boolean updatePosition(int gameTicks)
	{
		// Get movement data
		MoveData m = _move;
		
		if (m == null)
			return true;
		
		if (!isVisible())
		{
			_move = null;
			return true;
		}
		
		// Check if this is the first update
		if (m._moveTimestamp == 0)
		{
			m._moveTimestamp = m._moveStartTime;
			m._xAccurate = getX();
			m._yAccurate = getY();
		}
		
		// Check if the position has already been calculated
		if (m._moveTimestamp == gameTicks)
			return false;
		
		int xPrev = getX();
		int yPrev = getY();
		int zPrev = getZ(); // the z coordinate may be modified by coordinate synchronizations
		
		double dx, dy, dz, distFraction;
		if (Config.COORD_SYNCHRONIZE == 1)
		// the only method that can modify x,y while moving (otherwise _move would/should be set null)
		{
			dx = m._xDestination - xPrev;
			dy = m._yDestination - yPrev;
		}
		else
		// otherwise we need saved temporary values to avoid rounding errors
		{
			dx = m._xDestination - m._xAccurate;
			dy = m._yDestination - m._yAccurate;
		}
		// Z coordinate will follow geodata or client values
		if (Config.GEODATA > 0 && Config.COORD_SYNCHRONIZE == 2 && !isFlying() && !isInsideZone(L2Zone.FLAG_WATER)
				&& !m.disregardingGeodata && GameTimeManager.getGameTicks() % 10 == 0
				&& !(this instanceof L2BoatInstance) // once a second to reduce possible cpu load
				&& !(this instanceof L2AirShipInstance))
		{
			short geoHeight = GeoData.getInstance().getSpawnHeight(xPrev, yPrev, zPrev - 30, zPrev + 30, getObjectId());
			dz = m._zDestination - geoHeight;
			// quite a big difference, compare to validatePosition packet
			if (this instanceof L2Player && Math.abs(getClientZ() - geoHeight) > 200
					&& Math.abs(getClientZ() - geoHeight) < 1500)
			{
				dz = m._zDestination - zPrev; // allow diff
			}
			else if (isInCombat() && Math.abs(dz) > 200 && (dx * dx + dy * dy) < 40000) // allow mob to climb up to pcinstance
			{
				dz = m._zDestination - zPrev; // climbing
			}
			else
			{
				zPrev = geoHeight;
			}
		}
		else
			dz = m._zDestination - zPrev;
		
		double distPassed =
				getStat().getMoveSpeed() * (gameTicks - m._moveTimestamp) / GameTimeManager.TICKS_PER_SECOND;
		if ((dx * dx + dy * dy) < 10000 && (dz * dz > 2500)) // close enough, allows error between client and server geodata if it cannot be avoided
		{
			distFraction = distPassed / Math.sqrt(dx * dx + dy * dy);
		}
		else
			distFraction = distPassed / Math.sqrt(dx * dx + dy * dy + dz * dz);
		
		if (distFraction > 1) // already there
		{
			// Set the position of the L2Creature to the destination
			super.getPosition().setXYZ(m._xDestination, m._yDestination, m._zDestination);
		}
		else
		{
			m._xAccurate += dx * distFraction;
			m._yAccurate += dy * distFraction;
			
			// Set the position of the L2Creature to estimated after parcial move
			super.getPosition()
					.setXYZ((int)(m._xAccurate), (int)(m._yAccurate), zPrev + (int)(dz * distFraction + 0.5));
		}
		
		// Set the timer of last position update to now
		m._moveTimestamp = gameTicks;
		
		return (distFraction > 1);
	}
	
	public boolean revalidateZone(boolean force)
	{
		if (!force)
		{
			CoordRevalidator.getInstance().add(this);
			return false;
		}
		
		final L2WorldRegion region = getWorldRegion();
		
		if (region == null)
			return false;
		
		region.revalidateZones(this);
		return true;
	}
	
	/**
	 * Stop movement of the L2Creature (Called by AI Accessor only).<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Delete movement data of the L2Creature </li>
	 * <li>Set the current position (x,y,z), its current L2WorldRegion if necessary and its heading </li>
	 * <li>Remove the L2Object object from _gmList** of GmListTable </li>
	 * <li>Remove object from _knownObjects and _knownPlayer* of all surrounding L2WorldRegion L2Creatures </li>
	 * <BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T send Server->Client packet StopMove/StopRotation </B></FONT><BR>
	 * <BR>
	 */
	public void stopMove(L2CharPosition pos)
	{
		stopMove(pos, false);
	}
	
	public void stopMove(L2CharPosition pos, boolean updateKnownObjects)
	{
		// Delete movement data of the L2Creature
		_move = null;
		
		// if (getAI() != null)
		// getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		
		// Set the current position (x,y,z), its current L2WorldRegion if necessary and its heading
		// All data are contained in a L2CharPosition object
		if (pos != null)
		{
			getPosition().setXYZ(pos.x, pos.y, pos.z);
			setHeading(pos.heading);
			revalidateZone(true);
		}
		broadcastPacket(new StopMove(this));
		if (updateKnownObjects)
			getKnownList().updateKnownObjects();
	}
	
	/**
	 * @return Returns the showSummonAnimation.
	 */
	public boolean isShowSummonAnimation()
	{
		return _showSummonAnimation;
	}
	
	/**
	 * @param showSummonAnimation The showSummonAnimation to set.
	 */
	public void setShowSummonAnimation(boolean showSummonAnimation)
	{
		_showSummonAnimation = showSummonAnimation;
	}
	
	/**
	 * Target a L2Object (add the target to the L2Creature _target, _knownObject and L2Creature to _KnownObject of the L2Object).<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * The L2Object (including L2Creature) targeted is identified in <B>_target</B> of the L2Creature<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Set the _target of L2Creature to L2Object </li>
	 * <li>If necessary, add L2Object to _knownObject of the L2Creature </li>
	 * <li>If necessary, add L2Creature to _KnownObject of the L2Object </li>
	 * <li>If object==null, cancel Attak or Cast </li>
	 * <BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player : Remove the L2Player from the old target _statusListener and add it to the new target if it was a L2Creature</li>
	 * <BR>
	 * <BR>
	 *
	 * @param object
	 *            L2object to target
	 */
	public void setTarget(L2Object newTarget)
	{
		if (newTarget == getTarget())
			return;
		
		if (newTarget != null && !(this instanceof L2Player))
		{
			if (!newTarget.isVisible())
				return;
			
			if (Math.abs(newTarget.getZ() - getZ()) > 500)
				return;
		}
		
		refreshTarget(newTarget);
	}
	
	protected void refreshTarget(L2Object newTarget)
	{
		if (newTarget != null)
		{
			getKnownList().addKnownObject(newTarget);
			newTarget.getKnownList().addKnownObject(this);
		}
		
		_target = newTarget;
	}
	
	/**
	 * Return the identifier of the L2Object targeted or -1.<BR>
	 * <BR>
	 */
	public final int getTargetId()
	{
		if (_target != null)
		{
			return _target.getObjectId();
		}
		
		return -1;
	}
	
	/**
	 * Return the L2Object targeted or null.<BR>
	 * <BR>
	 */
	public final L2Object getTarget()
	{
		return _target;
	}
	
	public final <T> T getTarget(Class<T> clazz)
	{
		L2Object target = getTarget();
		
		if (clazz.isInstance(target))
			return clazz.cast(target);
		else
			return null;
	}
	
	public final <T> T getTarget(Class<T> clazz, int objectId)
	{
		L2Object target = getTarget();
		
		if (target == null)
			target = L2World.getInstance().findObject(objectId);
		
		if (clazz.isInstance(target) && target.getObjectId() == objectId)
			return clazz.cast(target);
		else
			return null;
	}
	
	// called from AIAccessor only
	/**
	 * Calculate movement data for a move to location action and add the L2Creature to movingObjects of GameTimeController (only called by AI Accessor).<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * At the beginning of the move action, all properties of the movement are stored in the MoveData object called <B>_move</B> of the L2Creature. The
	 * position of the start point and of the destination permit to estimated in function of the movement speed the time to achieve the destination.<BR>
	 * <BR>
	 * All L2Creature in movement are identified in <B>movingObjects</B> of GameTimeController that will call the updatePosition method of those L2Creature
	 * each 0.1s.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Get current position of the L2Creature </li>
	 * <li>Calculate distance (dx,dy) between current position and destination including offset </li>
	 * <li>Create and Init a MoveData object </li>
	 * <li>Set the L2Creature _move object to MoveData object </li>
	 * <li>Add the L2Creature to movingObjects of the GameTimeController </li>
	 * <li>Create a task to notify the AI that L2Creature arrives at a check point of the movement </li>
	 * <BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T send Server->Client packet MoveToPawn/MoveToLocation </B></FONT><BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> AI : onIntentionMoveTo(L2CharPosition), onIntentionPickUp(L2Object), onIntentionInteract(L2Object) </li>
	 * <li> FollowTask </li>
	 * <BR>
	 * <BR>
	 *
	 * @param x
	 *            The X position of the destination
	 * @param y
	 *            The Y position of the destination
	 * @param z
	 *            The Y position of the destination
	 * @param offset
	 *            The size of the interaction area of the L2Creature targeted
	 */
	protected void moveToLocation(int x, int y, int z, int offset)
	{
		// Get the Move Speed of the L2Charcater
		float speed = getStat().getMoveSpeed();
		if (speed <= 0 || isMovementDisabled())
			return;
		
		// Get current position of the L2Creature
		final int curX = super.getX();
		final int curY = super.getY();
		final int curZ = super.getZ();
		
		// Calculate distance (dx,dy) between current position and destination
		// TODO: improve Z axis move/follow support when dx,dy are small compared to dz
		double dx = (x - curX);
		double dy = (y - curY);
		double dz = (z - curZ);
		double distance = Math.sqrt(dx * dx + dy * dy);
		
		// make water move short and use no geodata checks for swimming chars
		// distance in a click can easily be over 3000
		if (Config.GEODATA > 0 && isInsideZone(L2Zone.FLAG_WATER) && distance > 700)
		{
			double divider = 700 / distance;
			x = curX + (int)(divider * dx);
			y = curY + (int)(divider * dy);
			z = curZ + (int)(divider * dz);
			dx = (x - curX);
			dy = (y - curY);
			dz = (z - curZ);
			distance = Math.sqrt(dx * dx + dy * dy);
		}
		
		if (_log.isDebugEnabled())
			_log.info("distance to target:" + distance);
		
		// Define movement angles needed
		// ^
		// |     X (x,y)
		// |   /
		// |  /distance
		// | /
		// |/ angle
		// X ---------->
		// (curx,cury)
		
		double cos;
		double sin;
		
		// Check if a movement offset is defined or no distance to go through
		if (offset != 0 || distance < 1)
		{
			// approximation for moving closer when z coordinates are different
			// TODO: handle Z axis movement better
			offset -= Math.abs(dz);
			//if (offset < 5) offset = 5;
			
			// If no distance to go through, the movement is canceled
			if (distance < 1 || distance - offset <= 0)
			{
				if (_log.isDebugEnabled())
					_log.info("already in range, no movement needed.");
				
				// Notify the AI that the L2Creature is arrived at destination
				getAI().notifyEvent(CtrlEvent.EVT_ARRIVED);
				
				return;
			}
			// Calculate movement angles needed
			sin = dy / distance;
			cos = dx / distance;
			
			distance -= (offset - 5); // due to rounding error, we have to move a bit closer to be in range
			
			// Calculate the new destination with offset included
			x = curX + (int)(distance * cos);
			y = curY + (int)(distance * sin);
			
		}
		else
		{
			// Calculate movement angles needed
			sin = dy / distance;
			cos = dx / distance;
		}
		
		// Create and Init a MoveData object
		MoveData m = new MoveData();
		
		// GEODATA MOVEMENT CHECKS AND PATHFINDING
		m.onGeodataPathIndex = -1; // Initialize not on geodata path
		m.disregardingGeodata = false;
		
		if (Config.GEODATA > 0 && !isFlying() // flying chars not checked - even canSeeTarget doesn't work yet
				&& (!isInsideZone(L2Zone.FLAG_WATER) || isInsideZone(L2Zone.FLAG_SIEGE)) // swimming also not checked unless in siege zone - but distance is limited
				&& !(this instanceof L2NpcWalkerInstance)) // npc walkers not checked
		{
			double originalDistance = distance;
			int originalX = x;
			int originalY = y;
			int originalZ = z;
			int gtx = (originalX - L2World.MAP_MIN_X) >> 4;
			int gty = (originalY - L2World.MAP_MIN_Y) >> 4;
			
			// Movement checks:
			// when geodata == 2, for all characters except mobs returning home (could be changed later to teleport if pathfinding fails)
			// when geodata == 1, for L2Playable and l2riftinstance only
			if ((Config.GEODATA == 2 && !(this instanceof L2Attackable && ((L2Attackable)this)
					.isReturningToSpawnPoint()))
					|| this instanceof L2Player
					|| (this instanceof L2Summon && !(getAI().getIntention() == AI_INTENTION_FOLLOW)) // assuming intention_follow only when following owner
					|| isAfraid() || this instanceof L2RiftInvaderInstance)
			{
				if (isOnGeodataPath())
				{
					try
					{
						if (gtx == _move.geoPathGtx && gty == _move.geoPathGty)
							return;
						else
							_move.onGeodataPathIndex = -1; // Set not on geodata path
					}
					catch (NullPointerException e)
					{
						// nothing
					}
				}
				
				if (curX < L2World.MAP_MIN_X || curX > L2World.MAP_MAX_X || curY < L2World.MAP_MIN_Y
						|| curY > L2World.MAP_MAX_Y)
				{
					// Temporary fix for character outside world region errors
					_log.warn("Character " + getName() + " outside world area, in coordinates x:" + curX + " y:" + curY);
					getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
					if (this instanceof L2Player)
						new Disconnection((L2Player)this).defaultSequence(true);
					else if (!(this instanceof L2Summon))
						onDecay(); // preventation when summon get out of world coords, player will not loose it, unsummon handled from pcinstance
					return;
				}
				Location destiny = GeoData.getInstance().moveCheck(curX, curY, curZ, x, y, z, getInstanceId());
				// location different if destination wasn't reached (or just z coord is different)
				x = destiny.getX();
				y = destiny.getY();
				z = destiny.getZ();
				distance = Math.sqrt((x - curX) * (x - curX) + (y - curY) * (y - curY));
				
			}
			// Pathfinding checks. Only when geodata setting is 2, the LoS check gives shorter result
			// than the original movement was and the LoS gives a shorter distance than 2000
			// This way of detecting need for pathfinding could be changed.
			if (Config.GEODATA == 2 && originalDistance - distance > 100 && distance < 2000 && !isAfraid())
			{
				// Path calculation
				// Overrides previous movement check
				if (this instanceof L2Playable || isInCombat() || this instanceof L2MinionInstance)
				{
					
					m.geoPath =
							PathFinding.getInstance().findPath(curX, curY, curZ, originalX, originalY, originalZ,
									getInstanceId());
					if (m.geoPath == null || m.geoPath.length < 2) // No path found
					{
						// * Even though there's no path found (remember geonodes aren't perfect),
						// the mob is attacking and right now we set it so that the mob will go
						// after target anyway, is dz is small enough.
						// * With cellpathfinding this approach could be changed but would require taking
						// off the geonodes and some more checks.
						// * Summons will follow their masters no matter what.
						// * Currently minions also must move freely since L2AttackableAI commands
						// them to move along with their leader
						if (this instanceof L2Player
								|| (!(this instanceof L2Playable) && !(this instanceof L2MinionInstance) && Math.abs(z
										- curZ) > 140)
								|| (this instanceof L2Summon && !((L2Summon)this).getFollowStatus()))
						{
							getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
							return;
						}
						
						m.disregardingGeodata = true;
						x = originalX;
						y = originalY;
						z = originalZ;
						distance = originalDistance;
					}
					else
					{
						m.onGeodataPathIndex = 0; // on first segment
						m.geoPathGtx = gtx;
						m.geoPathGty = gty;
						m.geoPathAccurateTx = originalX;
						m.geoPathAccurateTy = originalY;
						
						x = m.geoPath[m.onGeodataPathIndex].getX();
						y = m.geoPath[m.onGeodataPathIndex].getY();
						z = m.geoPath[m.onGeodataPathIndex].getZ();
						
						dx = (x - curX);
						dy = (y - curY);
						distance = Math.sqrt(dx * dx + dy * dy);
						sin = dy / distance;
						cos = dx / distance;
					}
				}
			}
			// If no distance to go through, the movement is canceled
			if (distance < 1
					&& (Config.GEODATA == 2 || this instanceof L2Playable || isAfraid() || this instanceof L2RiftInvaderInstance))
			{
				if (this instanceof L2Summon)
					((L2Summon)this).setFollowStatus(false);
				getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
				return;
			}
		}
		
		// Caclulate the Nb of ticks between the current position and the destination
		// One tick added for rounding reasons
		int ticksToMove = 1 + (int)(GameTimeManager.TICKS_PER_SECOND * distance / speed);
		m._xDestination = x;
		m._yDestination = y;
		m._zDestination = z; // this is what was requested from client
		
		// Calculate and set the heading of the L2Creature
		m._heading = 0; // initial value for coordinate sync
		setHeading(Util.calculateHeadingFrom(cos, sin));
		
		if (_log.isDebugEnabled())
			_log.info("dist:" + distance + "speed:" + speed + " ttt:" + ticksToMove + " heading:" + getHeading());
		
		m._moveStartTime = GameTimeManager.getGameTicks();
		
		// Set the L2Creature _move object to MoveData object
		_move = m;
		
		MovementController.getInstance().add(this, ticksToMove);
	}
	
	public boolean moveToNextRoutePoint()
	{
		if (!isOnGeodataPath())
		{
			// Cancel the move action
			_move = null;
			return false;
		}
		
		// Get the Move Speed of the L2Charcater
		float speed = getStat().getMoveSpeed();
		if (speed <= 0 || isMovementDisabled())
		{
			// Cancel the move action
			_move = null;
			return false;
		}
		
		// Create and Init a MoveData object
		MoveData m = new MoveData();
		MoveData md = _move;
		if (md == null)
			return false;
		
		// Update MoveData object
		m.onGeodataPathIndex = md.onGeodataPathIndex + 1; // next segment
		m.geoPath = md.geoPath;
		m.geoPathGtx = md.geoPathGtx;
		m.geoPathGty = md.geoPathGty;
		m.geoPathAccurateTx = md.geoPathAccurateTx;
		m.geoPathAccurateTy = md.geoPathAccurateTy;
		
		if (md.onGeodataPathIndex == md.geoPath.length - 2)
		{
			m._xDestination = md.geoPathAccurateTx;
			m._yDestination = md.geoPathAccurateTy;
			m._zDestination = md.geoPath[m.onGeodataPathIndex].getZ();
		}
		else
		{
			m._xDestination = md.geoPath[m.onGeodataPathIndex].getX();
			m._yDestination = md.geoPath[m.onGeodataPathIndex].getY();
			m._zDestination = md.geoPath[m.onGeodataPathIndex].getZ();
		}
		double dx = (m._xDestination - super.getX());
		double dy = (m._yDestination - super.getY());
		
		double distance = Math.sqrt(dx * dx + dy * dy);
		double sin = dy / distance;
		double cos = dx / distance;
		
		// Caclulate the Nb of ticks between the current position and the destination
		// One tick added for rounding reasons
		int ticksToMove = 1 + (int)(GameTimeManager.TICKS_PER_SECOND * distance / speed);
		
		// Calculate and set the heading of the L2Creature
		int heading = (int)(Math.atan2(-sin, -cos) * 10430.378);
		heading += 32768;
		setHeading(heading);
		m._heading = 0; // initial value for coordinate sync
		
		m._moveStartTime = GameTimeManager.getGameTicks();
		
		if (_log.isDebugEnabled())
			_log.info("time to target:" + ticksToMove);
		
		// Set the L2Creature _move object to MoveData object
		_move = m;
		
		MovementController.getInstance().add(this, ticksToMove);
		
		// Send a Server->Client packet MoveToLocation to the actor and all L2Player in its _knownPlayers
		MoveToLocation msg = new MoveToLocation(this);
		broadcastPacket(msg);
		
		return true;
	}
	
	public boolean validateMovementHeading(int heading)
	{
		MoveData md = _move;
		if (md == null)
			return true;
		
		boolean result = true;
		// if (_move._heading < heading - 5 || _move._heading > heading 5)
		if (md._heading != heading)
		{
			result = (md._heading == 0);
			md._heading = heading;
		}
		
		return result;
	}
	
	/**
	 * Return the distance between the current position of the L2Creature and the target (x,y).<BR>
	 * <BR>
	 *
	 * @param x
	 *            X position of the target
	 * @param y
	 *            Y position of the target
	 * @return the plan distance
	 * @deprecated use getPlanDistanceSq(int x, int y, int z)
	 */
	@Deprecated
	public final double getDistance(int x, int y)
	{
		double dx = x - getX();
		double dy = y - getY();
		
		return Math.sqrt(dx * dx + dy * dy);
	}
	
	/**
	 * Return the distance between the current position of the L2Creature and the target (x,y).<BR>
	 * <BR>
	 *
	 * @param x
	 *            X position of the target
	 * @param y
	 *            Y position of the target
	 * @return the plan distance
	 * @deprecated use getPlanDistanceSq(int x, int y, int z)
	 */
	@Deprecated
	public final double getDistance(int x, int y, int z)
	{
		double dx = x - getX();
		double dy = y - getY();
		double dz = z - getZ();
		
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	
	/**
	 * Return the squared distance between the current position of the L2Creature and the given object.<BR>
	 * <BR>
	 *
	 * @param object
	 *            L2Object
	 * @return the squared distance
	 */
	public final double getDistanceSq(L2Object object)
	{
		return getDistanceSq(object.getX(), object.getY(), object.getZ());
	}
	
	/**
	 * Return the squared distance between the current position of the L2Creature and the given x, y, z.<BR>
	 * <BR>
	 *
	 * @param x
	 *            X position of the target
	 * @param y
	 *            Y position of the target
	 * @param z
	 *            Z position of the target
	 * @return the squared distance
	 */
	public final double getDistanceSq(int x, int y, int z)
	{
		double dx = x - getX();
		double dy = y - getY();
		double dz = z - getZ();
		
		return (dx * dx + dy * dy + dz * dz);
	}
	
	/**
	 * Return the squared plan distance between the current position of the L2Creature and the given object.<BR>
	 * (check only x and y, not z)<BR>
	 * <BR>
	 *
	 * @param object
	 *            L2Object
	 * @return the squared plan distance
	 */
	public final double getPlanDistanceSq(L2Object object)
	{
		return getPlanDistanceSq(object.getX(), object.getY());
	}
	
	/**
	 * Return the squared plan distance between the current position of the L2Creature and the given x, y, z.<BR>
	 * (check only x and y, not z)<BR>
	 * <BR>
	 *
	 * @param x
	 *            X position of the target
	 * @param y
	 *            Y position of the target
	 * @return the squared plan distance
	 */
	public final double getPlanDistanceSq(int x, int y)
	{
		double dx = x - getX();
		double dy = y - getY();
		
		return (dx * dx + dy * dy);
	}
	
	/**
	 * Check if this object is inside the given radius around the given object. Warning: doesn't cover collision radius!<BR>
	 * <BR>
	 * If the target is null, we consider that this object is not inside radius
	 *
	 * @param object
	 *            the target
	 * @param radius
	 *            the radius around the target
	 * @param checkZ
	 *            should we check Z axis also
	 * @param strictCheck
	 *            true if (distance < radius), false if (distance <= radius)
	 * @return true is the L2Creature is inside the radius.
	 */
	public final boolean isInsideRadius(L2Object object, int radius, boolean checkZ, boolean strictCheck)
	{
		if (object == null)
			return false;
		
		return isInsideRadius(object.getX(), object.getY(), object.getZ(), radius, checkZ, strictCheck);
	}
	
	/**
	 * Check if this object is inside the given plan radius around the given point. Warning: doesn't cover collision radius!<BR>
	 * <BR>
	 *
	 * @param x
	 *            X position of the target
	 * @param y
	 *            Y position of the target
	 * @param radius
	 *            the radius around the target
	 * @param strictCheck
	 *            true if (distance < radius), false if (distance <= radius)
	 * @return true is the L2Creature is inside the radius.
	 */
	public final boolean isInsideRadius(int x, int y, int radius, boolean strictCheck)
	{
		return isInsideRadius(x, y, 0, radius, false, strictCheck);
	}
	
	/**
	 * Check if this object is inside the given radius around the given point.<BR>
	 * <BR>
	 *
	 * @param x
	 *            X position of the target
	 * @param y
	 *            Y position of the target
	 * @param z
	 *            Z position of the target
	 * @param radius
	 *            the radius around the target
	 * @param checkZ
	 *            should we check Z axis also
	 * @param strictCheck
	 *            true if (distance < radius), false if (distance <= radius)
	 * @return true is the L2Creature is inside the radius.
	 */
	public final boolean isInsideRadius(int x, int y, int z, int radius, boolean checkZ, boolean strictCheck)
	{
		double dx = x - getX();
		double dy = y - getY();
		double dz = z - getZ();
		
		if (strictCheck)
		{
			if (checkZ)
				return (dx * dx + dy * dy + dz * dz) < radius * radius;
			
			return (dx * dx + dy * dy) < radius * radius;
		}
		
		if (checkZ)
			return (dx * dx + dy * dy + dz * dz) <= radius * radius;
		
		return (dx * dx + dy * dy) <= radius * radius;
	}
	
	public final boolean isOutsideRadius(int x, int y, int radius)
	{
		double dx = x - getX();
		double dy = y - getY();
		
		return (dx * dx + dy * dy) >= radius * radius;
	}
	
	/**
	 * Return the Weapon Expertise Penalty of the L2Creature.<BR>
	 * <BR>
	 */
	@Deprecated
	public final float getWeaponExpertisePenalty()
	{
		return 1.f;
	}
	
	/**
	 * Return the Armour Expertise Penalty of the L2Creature.<BR>
	 * <BR>
	 */
	@Deprecated
	public final float getArmourExpertisePenalty()
	{
		return 1.f;
	}
	
	/**
	 * Set _attacking corresponding to Attacking Body part to CHEST.<BR>
	 * <BR>
	 */
	public void setAttackingBodypart()
	{
		_attacking = Inventory.PAPERDOLL_CHEST;
	}
	
	/**
	 * Retun True if arrows are available.<BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player</li>
	 * <BR>
	 * <BR>
	 */
	protected boolean checkAndEquipArrows()
	{
		return true;
	}
	
	/**
	* Retun True if bolts are available.<BR><BR>
	*
	* <B><U> Overridden in </U> :</B><BR><BR>
	* <li> L2Player</li><BR><BR>
	*
	*/
	protected boolean checkAndEquipBolts()
	{
		return true;
	}
	
	/**
	 * Add Exp and Sp to the L2Creature.<BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player</li>
	 * <li> L2PetInstance</li>
	 * <BR>
	 * <BR>
	 */
	public void addExpAndSp(long addToExp, int addToSp)
	{
		// Dummy method (overridden by players and pets)
	}
	
	/**
	 * Return the active weapon instance (always equipped in the right hand).<BR>
	 */
	public L2ItemInstance getActiveWeaponInstance()
	{
		return null;
	}
	
	/**
	 * Return the active weapon item (always equipped in the right hand).<BR>
	 */
	public L2Weapon getActiveWeaponItem()
	{
		return null;
	}
	
	/**
	 * Return the secondary weapon instance (always equipped in the left hand).<BR>
	 */
	public L2ItemInstance getSecondaryWeaponInstance()
	{
		return null;
	}
	
	/**
	 * Return the secondary weapon item (always equipped in the left hand).<BR>
	 */
	public L2Weapon getSecondaryWeaponItem()
	{
		return null;
	}
	
	/**
	 * Manage hit process (called by Hit Task).<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>If the attacker/target is dead or use fake death, notify the AI with EVT_CANCEL and send a Server->Client packet ActionFailed (if attacker is a
	 * L2Player)</li>
	 * <li>If attack isn't aborted, send a message system (critical hit, missed...) to attacker/target if they are L2Player </li>
	 * <li>If attack isn't aborted and hit isn't missed, reduce HP of the target and calculate reflection damage to reduce HP of attacker if necessary </li>
	 * <li>if attack isn't aborted and hit isn't missed, manage attack or cast break of the target (calculating rate, sending message...) </li>
	 * <BR>
	 * <BR>
	 *
	 * @param target
	 *            The L2Creature targeted
	 * @param damage
	 *            Nb of HP to reduce
	 * @param crit
	 *            True if hit is critical
	 * @param miss
	 *            True if hit is missed
	 * @param soulshot
	 *            True if SoulShot are charged
	 * @param shld
	 *            True if shield is efficient
	 */
	protected void onHitTimer(L2Creature target, int damage, boolean crit, boolean miss, double vampiricMulti)
	{
		// If the attacker/target is dead or use fake death, notify the AI with EVT_CANCEL
		// and send a Server->Client packet ActionFailed (if attacker is a L2Player)
		if (target == null || isAlikeDead())
		{
			getAI().notifyEvent(CtrlEvent.EVT_CANCEL);
			return;
		}
		
		if ((this instanceof L2Npc && target.isAlikeDead()) || target.isDead()
				|| (!getKnownList().knowsObject(target) && !(this instanceof L2DoorInstance)))
		{
			// getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);
			getAI().notifyEvent(CtrlEvent.EVT_CANCEL);
			
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (miss)
		{
			// ON_EVADED_HIT
			if (target.getChanceSkills() != null)
				target.getChanceSkills().onEvadedHit(this);
		}
		
		sendDamageMessage(target, damage, false, crit, miss);
		
		// If attack isn't aborted, send a message system (critical hit, missed...) to attacker/target if they are L2Player
		if (!isAttackAborted())
		{
			// Check Raidboss attack
			// Character will be petrified if attacking a raid that's more
			// than 8 levels lower
			if (target.isRaid() && !Config.ALT_DISABLE_RAIDBOSS_PETRIFICATION
					&& getSkillLevel(L2Boss.BOSS_PENALTY_RESISTANCE) == -1)
			{
				int level = 0;
				if (this instanceof L2Player)
					level = getLevel();
				else if (this instanceof L2Summon)
					level = ((L2Summon)this).getOwner().getLevel();
				
				if (level > target.getLevel() + 8)
				{
					L2Skill skill = SkillTable.getInstance().getInfo(L2Boss.BOSS_PENALTY_PETRIFICATION, 1);
					
					if (skill != null)
						skill.getEffects(target, this);
					else
						_log.warn("Skill " + L2Boss.BOSS_PENALTY_PETRIFICATION + " at level 1 is missing in DP.");
					
					damage = 0; // prevents messing up drop calculation
				}
			}
			
			// If L2Creature target is a L2Player, send a system message
			if (target instanceof L2Player)
			{
				L2Player enemy = (L2Player)target;
				enemy.getAI().clientStartAutoAttack();
				
				// Check if shield is efficient
				/*if (shld && 100 - Config.ALT_PERFECT_SHLD_BLOCK < Rnd.get(100))
					enemy.sendPacket(SystemMessageId.SHIELD_DEFENCE_SUCCESSFULL);
				// else if (!miss && damage < 1)
				// enemy.sendMessage("You hit the target's armor.");*/
			}
			else if (target instanceof L2Summon)
			{
				((L2Summon)target).getOwner().getAI().clientStartAutoAttack();
			}
			
			if (!miss && damage > 0)
			{
				L2Weapon weapon = getActiveWeaponItem();
				boolean isRangeWeapon =
						(weapon != null && (weapon.getItemType() == L2WeaponType.BOW || weapon.getItemType() == L2WeaponType.CROSSBOW));
				
				int reflectedDamage = 0;
				if (!isRangeWeapon && !target.isInvul()) // Do not reflect if weapon is of type bow/crossbow or target is invulnerable
				{
					// quick fix for no drop from raid if boss attack high-level char with damage reflection
					if (!isRaid() || target.getActingPlayer() == null
							|| target.getActingPlayer().getLevel() <= getLevel() + 8)
					{
						// Reduce HP of the target and calculate reflection damage to reduce HP of attacker if necessary
						double reflectPercent = target.getStat().calcStat(Stats.REFLECT_DAMAGE_PERCENT, 0, null, null);
						
						if (reflectPercent > 0)
						{
							reflectedDamage = (int)(reflectPercent / 100. * damage);
							damage -= reflectedDamage;
							
							if (reflectedDamage > target.getMaxHp()) // to prevent extreme damage when hitting a low lvl char...
								reflectedDamage = target.getMaxHp();
						}
					}
				}
				
				// Reduce targets HP
				target.reduceCurrentHp(damage, this);
				
				if (reflectedDamage > 0)
				{
					reduceCurrentHp(reflectedDamage, target);
					
					// Custom messages - nice but also more network load
					/*
					 * if (target instanceof L2Player) ((L2Player)target).sendMessage("You reflected " + reflectedDamage + " damage."); else if
					 * (target instanceof L2Summon) ((L2Summon)target).getOwner().sendMessage("Summon reflected " + reflectedDamage + " damage.");
					 *
					 * if (this instanceof L2Player) ((L2Player)this).sendMessage("Target reflected to you " + reflectedDamage + " damage."); else
					 * if (this instanceof L2Summon) ((L2Summon)this).getOwner().sendMessage("Target reflected to your summon " + reflectedDamage + "
					 * damage.");
					 */
				}
				
				if (!isRangeWeapon) // Do not absorb if weapon is of type bow
				{
					// Absorb HP from the damage inflicted
					double absorbPercent =
							getStat().calcStat(Stats.ABSORB_DAMAGE_PERCENT, 0, null, null) * vampiricMulti;
					
					if (absorbPercent > 0)
					{
						int maxCanAbsorb = (int)(getMaxHp() - getStatus().getCurrentHp());
						int absorbDamage = (int)(absorbPercent / 100. * damage);
						
						if (absorbDamage > maxCanAbsorb)
							absorbDamage = maxCanAbsorb; // Can't absorb more than max hp
							
						if (absorbDamage > 0)
						{
							getStatus().increaseHp(absorbDamage);
						}
					}
					
					// Absorb MP from the damage inflicted
					absorbPercent = getStat().calcStat(Stats.ABSORB_MANA_DAMAGE_PERCENT, 0, null, null) * vampiricMulti;
					if (absorbPercent > 0)
					{
						int maxCanAbsorb = (int)(getMaxMp() - getCurrentMp());
						int absorbDamage = (int)(absorbPercent / 100. * damage);
						
						if (absorbDamage > maxCanAbsorb)
							absorbDamage = maxCanAbsorb; // Can't absord more than max hp
							
						if (absorbDamage > 0)
							getStatus().increaseMp(absorbDamage);
					}
					
					/*
					// Absorb CP from the damage inflicted
					double absorbCPPercent = getStat().calcStat(Stats.ABSORB_CP_PERCENT, 0, null, null) * vampiricMulti;

					if (absorbCPPercent > 0)
					{
						int maxCanAbsorb = (int) (getMaxCp() - getStatus().getCurrentCp());
						int absorbDamage = (int) (absorbCPPercent / 100. * damage);

						if (absorbDamage > maxCanAbsorb)
							absorbDamage = maxCanAbsorb; // Can't absorb more than max cp

						getStatus().setCurrentCp(getStatus().getCurrentCp() + absorbDamage);
					}
					*/
				}
				
				// Notify AI with EVT_ATTACKED
				if (target.hasAI())
					target.getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, this);
				getAI().clientStartAutoAttack();
				if (this instanceof L2Summon)
				{
					L2Player owner = ((L2Summon)this).getOwner();
					if (owner != null)
					{
						owner.getAI().clientStartAutoAttack();
					}
				}
				
				// Manage attack or cast break of the target (calculating rate, sending message...)
				if (Formulas.calcAtkBreak(target, damage))
				{
					target.breakAttack();
					target.breakCast();
				}
				
				// Maybe launch chance skills on us
				if (_chanceSkills != null)
					_chanceSkills.onHit(target, false, crit, isRangeWeapon);
				
				// Maybe launch chance skills on target
				if (target.getChanceSkills() != null)
					target.getChanceSkills().onHit(this, true, crit, isRangeWeapon);
				
				// Launch weapon Special ability effect if available
				L2Weapon activeWeapon = getActiveWeaponItem();
				
				if (activeWeapon != null && crit)
					activeWeapon.getSkillEffectsByCrit(this, target);
			}
			return;
		}
		
		if (!isCastingNow() && !isCastingSimultaneouslyNow())
			getAI().notifyEvent(CtrlEvent.EVT_CANCEL);
	}
	
	/**
	 * Break an attack and send Server->Client ActionFailed packet and a System Message to the L2Creature.<BR>
	 * <BR>
	 */
	public void breakAttack()
	{
		if (isAttackingNow())
		{
			// Abort the attack of the L2Creature and send Server->Client ActionFailed packet
			abortAttack();
			
			if (this instanceof L2Player)
			{
				// not retail, so no system message.
				sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	/**
	 * Break a cast and send Server->Client ActionFailed packet and a System Message to the L2Creature.<BR>
	 * <BR>
	 */
	public void breakCast()
	{
		// damage can only cancel magical skills
		if (isCastingNow() && canAbortCast())
		{
			// Abort the cast of the L2Creature and send Server->Client MagicSkillCanceld/ActionFailed packet.
			abortCast();
			
			if (this instanceof L2Player)
			{
				// Send a system message
				SystemMessage sm = new SystemMessage(SystemMessageId.C1_CASTING_INTERRUPTED);
				sm.addCharName(this);
				getActingPlayer().sendPacket(sm);
			}
		}
	}
	
	/**
	 * Reduce the arrow number of the L2Creature.<BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player</li>
	 * <BR>
	 * <BR>
	 * @param bolts
	 */
	protected void reduceArrowCount(boolean bolts)
	{
		// default is to do nothin
	}
	
	/**
	 * Manage Forced attack (shift + select target).<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>If L2Creature or target is in a town area, send a system message TARGET_IN_PEACEZONE a Server->Client packet ActionFailed </li>
	 * <li>If target is confused, send a Server->Client packet ActionFailed </li>
	 * <li>If L2Creature is a L2ArtefactInstance, send a Server->Client packet ActionFailed </li>
	 * <li>Send a Server->Client packet MyTargetSelected to start attack and Notify AI with AI_INTENTION_ATTACK </li>
	 * <BR>
	 * <BR>
	 *
	 * @param player
	 *            The L2Player to attack
	 */
	@Override
	public void onForcedAttack(L2Player player)
	{
		if (GlobalRestrictions.isProtected(player, this, null, true))
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (!isAttackable() && player.getAccessLevel() < Config.GM_PEACEATTACK)
		{
			// If target is not attackable, send a Server->Client packet ActionFailed
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.isConfused())
		{
			// If target is confused, send a Server->Client packet ActionFailed
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// GeoData Los Check or dz > 1000
		if (!GeoData.getInstance().canSeeTarget(player, this))
		{
			player.sendPacket(SystemMessageId.CANT_SEE_TARGET);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Notify AI with AI_INTENTION_ATTACK
		player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
	}
	
	public static final boolean isInsidePeaceZone(L2Creature attacker, L2Object objTarget)
	{
		final L2Creature target = L2Object.getActingCharacter(objTarget);
		final L2Player attackerPlayer = L2Object.getActingPlayer(attacker);
		final L2Player targetPlayer = L2Object.getActingPlayer(target);
		
		if (attackerPlayer == null || targetPlayer == null)
			return false;
		
		if (attackerPlayer.getAccessLevel() >= Config.GM_PEACEATTACK)
			return false;
		
		if (InstanceManager.getInstance().getInstance(attacker.getInstanceId()).isPvPInstance())
			return false;
		
		if (Config.ALT_GAME_KARMA_PLAYER_CAN_BE_KILLED_IN_PEACEZONE)
		{
			// allows red to be attacked and red to attack flagged players
			if (targetPlayer.getKarma() > 0)
				return false;
			
			if (attackerPlayer.getKarma() > 0 && targetPlayer.getPvpFlag() > 0)
				return false;
		}
		
		return attacker.isInsideZone(L2Zone.FLAG_PEACE) || target.isInsideZone(L2Zone.FLAG_PEACE);
	}
	
	/**
	 * return true if this character is inside an active grid.
	 */
	public boolean isInActiveRegion()
	{
		L2WorldRegion region = getWorldRegion();
		return ((region != null) && (region.isActive()));
	}
	
	/**
	 * Return True if the L2Creature has a Party in progress.<BR>
	 * <BR>
	 */
	public boolean isInParty()
	{
		return false;
	}
	
	/**
	 * Return the L2Party object of the L2Creature.<BR>
	 * <BR>
	 */
	public L2Party getParty()
	{
		return null;
	}
	
	/**
	 * Return the Attack Speed of the L2Creature (delay (in milliseconds) before next attack).<BR>
	 * <BR>
	 * @param target
	 * @param weapon
	 */
	// FIXME
	public int calculateTimeBetweenAttacks(L2Creature target, L2Weapon weapon)
	{
		double base = 500000/*468000*/; //1500 * 333.3/*1500 * 312*/
		
		//if (weapon != null && !(this instanceof L2Player && ((L2Player)this).isTransformed()))
		//{
		//	switch (weapon.getItemType())
		//	{
		//		case BOW:
		//			base = 517500; //1500 * 345
		//		case CROSSBOW:
		//			base = 414000; //1200 * 345
		//	}
		//}
		
		return Formulas.calcPAtkSpd(this, target, getPAtkSpd(), base);
	}
	
	public int calculateReuseTime(L2Creature target, L2Weapon weapon)
	{
		// Source L2P
		// Standing still and with no SA, normal bows and yumi bows shoot the exact same number of shots per second.
		// Normal Bows allow faster use of skills and more kiteability due to having a higher Atk. Spd. while Yumi Bows have significant P.Atk.
		// The SA "Quick Recovery" reduces the red bar Weapon Delay on a bow to the following:
		// Reuse goes from 639 EB QR, to 1500 Normal...
		
		if (weapon == null || (this instanceof L2Player && ((L2Player)this).isTransformed()))
			return 0;
		
		double reuse = weapon.getAttackReuseDelay();
		
		// only bows should continue for now
		if (reuse == 0)
			return 0;
		
		reuse = calcStat(Stats.BOW_REUSE, reuse, target, null);
		
		reuse *= 333.3/*345/312*/;
		
		return Formulas.calcPAtkSpd(this, target, getPAtkSpd(), reuse);
	}
	
	/**
	 * Return True if the L2Creature use a dual weapon.<BR><BR>
	 */
	public boolean isUsingDualWeapon()
	{
		return false;
	}
	
	/**
	 * Add a skill to the L2Creature _skills and its Func objects to the calculator set of the L2Creature.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All skills own by a L2Creature are identified in <B>_skills</B><BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Replace oldSkill by newSkill or Add the newSkill </li>
	 * <li>If an old skill has been replaced, remove all its Func objects of L2Creature calculator set</li>
	 * <li>Add Func objects of newSkill to the calculator set of the L2Creature </li>
	 * <BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player : Save update in the character_skills table of the database</li>
	 * <BR>
	 * <BR>
	 *
	 * @param newSkill
	 *            The L2Skill to add to the L2Creature
	 * @return The L2Skill replaced or null if just added a new L2Skill
	 */
	@Deprecated
	public L2Skill addSkill(L2Skill newSkill)
	{
		if (newSkill == null)
			return null;
		
		// the map of the template of a non-player character is unmodifiable
		_skills = SkillMaps.modifiable(_skills);
		
		// Replace oldSkill by newSkill or Add the newSkill
		final L2Skill oldSkill = _skills.put(newSkill.getId(), newSkill);
		
		// If an old skill has been replaced, remove all its Func objects
		skillChanged(oldSkill, newSkill);
		
		return oldSkill;
	}
	
	/**
	 * Remove a skill from the L2Creature and its Func objects from calculator set of the L2Creature.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All skills own by a L2Creature are identified in <B>_skills</B><BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Remove the skill from the L2Creature _skills </li>
	 * <li>Remove all its Func objects from the L2Creature calculator set</li>
	 * <BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player : Save update in the character_skills table of the database</li>
	 * <BR>
	 * <BR>
	 *
	 * @param skill
	 *            The L2Skill to remove from the L2Creature
	 * @return The L2Skill removed
	 */
	@Deprecated
	public L2Skill removeSkill(L2Skill skill)
	{
		if (skill == null || _skills == null)
			return null;
		
		// Remove the skill from the L2Creature _skills
		final L2Skill oldSkill = _skills.remove(skill.getId());
		
		// Remove all its Func objects from the L2Creature calculator set
		if (oldSkill != null)
		{
			skillChanged(oldSkill, null);
			
			// does not abort casting of the transformation dispell
			if (oldSkill.getSkillType() != L2SkillType.TRANSFORMDISPEL)
			{
				// Stop casting if this skill is used right now
				if (isCastingNow() && this instanceof L2Player)
				{
					SkillUsageRequest currentSkill = ((L2Player)this).getCurrentSkill();
					if (currentSkill != null && currentSkill.getSkillId() == oldSkill.getId())
						abortCast();
				}
				
				if (isCastingSimultaneouslyNow())
				{
					L2Skill lastSimultaneousSkillCast = getLastSimultaneousSkillCast();
					if (lastSimultaneousSkillCast != null && lastSimultaneousSkillCast.getId() == oldSkill.getId())
						abortCast();
				}
			}
			
			// for now, to support transformations, we have to let their
			// effects stay when skill is removed
			for (L2Effect e : getAllEffects())
			{
				if (e == null || e.getSkill().getId() != oldSkill.getId())
					continue; // remove only effects with the same id
					
				if (e.getEffectType() == L2EffectType.TRANSFORMATION)
					continue; // remove only non-transformation effects
					
				if (e.getSkill().isChance())
					continue; // don't remove triggered effects
					
				if (Config.ALT_KEEP_ITEM_BUFFS && e.getSkill().isItemSkill() && e.getSkill().isActive())
					continue; // skip item/augmentation active/self buffs
					
				if (e.getSkill().getTargetType() == SkillTargetType.TARGET_SELF)
					e.exit(); // remove self skills only - there is no reason to remove normal buffs
			}
			
			if (this instanceof L2Player)
			{
				L2Player player = (L2Player)this;
				
				if (oldSkill instanceof L2SkillAgathion && player.getAgathionId() > 0)
				{
					player.setAgathionId(0);
					player.broadcastUserInfo();
				}
				
				if (oldSkill instanceof L2SkillMount && player.isMounted())
				{
					player.dismount();
				}
				
				if (oldSkill instanceof L2SkillSummon && oldSkill.getId() == 710)
				{
					L2Summon summon = player.getPet();
					
					if (summon != null && summon.getNpcId() == 14870)
						summon.unSummon();
				}
			}
		}
		
		return oldSkill;
	}
	
	protected void skillChanged(L2Skill removed, L2Skill added)
	{
		if (removed != null)
		{
			removeStatsOwner(removed);
			
			if (removed.isChance())
				removeChanceSkillTrigger(removed);
		}
		
		if (added != null)
		{
			if (added.getSkillType() != L2SkillType.NOTDONE)
				addStatFuncs(added.getStatFuncs(this));
			
			if (added.isChance())
				addChanceSkillTrigger(added);
			
			if (added.isActive())
				disableSkill(added.getId(), added.getEquipDelay());
		}
	}
	
	public synchronized void addChanceSkillTrigger(IChanceSkillTrigger trigger)
	{
		if (_chanceSkills == null)
			_chanceSkills = new ChanceSkillList(this);
		
		_chanceSkills.add(trigger);
	}
	
	public synchronized void removeChanceSkillTrigger(IChanceSkillTrigger trigger)
	{
		if (_chanceSkills == null)
			return;
		
		_chanceSkills.remove(trigger);
	}
	
	/**
	 * Return all skills own by the L2Creature in a table of L2Skill.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All skills own by a L2Creature are identified in <B>_skills</B> the L2Creature <BR>
	 * <BR>
	 */
	public final L2Skill[] getAllSkills()
	{
		if (_skills == null)
			return new L2Skill[0];
		
		return _skills.values().toArray(new L2Skill[_skills.size()]);
	}
	
	/**
	 * Return the level of a skill owned by the L2Creature.<BR>
	 * <BR>
	 *
	 * @param skillId
	 *            The identifier of the L2Skill whose level must be returned
	 * @return The level of the L2Skill identified by skillId
	 */
	public int getSkillLevel(int skillId)
	{
		if (_skills == null)
			return -1;
		
		L2Skill skill = _skills.get(skillId);
		
		if (skill == null)
			return -1;
		return skill.getLevel();
	}
	
	/**
	 * Return True if the skill is known by the L2Creature.<BR>
	 * <BR>
	 *
	 * @param skillId
	 *            The identifier of the L2Skill to check the knowledge
	 */
	public final L2Skill getKnownSkill(int skillId)
	{
		if (_skills == null)
			return null;
		
		return _skills.get(skillId);
	}
	
	public final boolean hasSkill(int skillId)
	{
		return getKnownSkill(skillId) != null;
	}
	
	/**
	 * Return the number of buffs affecting this L2Creature.<BR><BR>
	 *
	 * @return The number of Buffs affecting this L2Creature
	 */
	public final int getBuffCount()
	{
		return getEffects().getBuffCount();
	}
	
	public final int getDanceCount(boolean dances, boolean songs)
	{
		return getEffects().getDanceCount(dances, songs);
	}
	
	/**
	 * Manage the magic skill launching task (MP, HP, Item consummation...) and display the magic skill animation on client.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Send a Server->Client packet MagicSkillLaunched (to display magic skill animation) to all L2Player of L2Charcater _knownPlayers</li>
	 * <li>Consume MP, HP and Item if necessary</li>
	 * <li>Send a Server->Client packet StatusUpdate with MP modification to the L2Player</li>
	 * <li>Launch the magic skill in order to calculate its effects</li>
	 * <li>If the skill type is PDAM, notify the AI of the target with AI_INTENTION_ATTACK</li>
	 * <li>Notify the AI of the L2Creature with EVT_FINISH_CASTING</li>
	 * <BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : A magic skill casting MUST BE in progress</B></FONT><BR>
	 * <BR>
	 *
	 * @param skill
	 *            The L2Skill to use
	 */
	private final void onMagicLaunchedTimer(MagicEnv magicEnv)
	{
		final L2Skill skill = magicEnv._skill;
		final List<L2Creature> targets = magicEnv._targets;
		final boolean simultaneously = magicEnv._simultaneously;
		
		//		if (targets.isEmpty())
		//		{
		//			abortCast();
		//			setAttackingChar(null);
		//			return;
		//		}
		
		// Escaping from under skill's radius and peace zone check. First version, not perfect in AoE skills.
		int escapeRange = 0;
		if (skill.getEffectRange() > 0)
			escapeRange = skill.getEffectRange();
		else if (skill.getCastRange() < 0 && skill.getSkillRadius() > 80)
			escapeRange = skill.getSkillRadius();
		
		if (escapeRange > 0)
		{
			// out of range or protected targets are removed from the list, so walk a copy
			for (L2Creature target : new ArrayList<L2Creature>(targets))
			{
				if ((!Util.checkIfInRange(escapeRange, this, target, true) || !GeoData.getInstance().canSeeTarget(this,
						target)))
				{
					targets.remove(target);
					continue;
				}
				if (skill.isOffensive())
				{
					if (L2Creature.isInsidePeaceZone(this, target))
					{
						targets.remove(target);
						continue;
					}
				}
			}
			
			//			if (targets.isEmpty())
			//			{
			//				abortCast();
			//				return;
			//			}
		}
		
		if (simultaneously && !isCastingSimultaneouslyNow() // Ensure that a cast is in progress
				|| !simultaneously && !isCastingNow() // Ensure that a cast is in progress
				|| isAlikeDead() && !skill.isPotion()) // Check if player is using fake death, potions still can be used.
		{
			// now cancels both, simultaneous and normal
			//setAttackingChar(null);
			getAI().notifyEvent(CtrlEvent.EVT_CANCEL);
			return;
		}
		
		// If this packet is sent here, client may choose not to accept it
		//if (!skill.isPotion())
		//	broadcastPacket(new MagicSkillLaunched(this, skill, targets.toArray(new L2Creature[targets.size()])));
		
		onMagicHitTimer(magicEnv);
	}
	
	/**
	 * Runs in the end of skill casting
	 * @param magicEnv
	 */
	private final void onMagicHitTimer(MagicEnv magicEnv)
	{
		final L2Skill skill = magicEnv._skill;
		final List<L2Creature> targets = magicEnv._targets;
		final boolean simultaneously = magicEnv._simultaneously;
		
		//		if (targets.isEmpty())
		//		{
		//			abortCast();
		//			setAttackingChar(null);
		//			return;
		//		}
		
		if (getFusionSkill() != null)
		{
			if (simultaneously)
			{
				setIsCastingSimultaneouslyNow(false);
			}
			else
			{
				setIsCastingNow(false);
			}
			notifyQuestEventSkillFinished(magicEnv);
			getFusionSkill().onCastAbort();
			return;
		}
		
		L2Effect mog = getFirstEffect(L2EffectType.SIGNET_GROUND);
		if (mog != null)
		{
			if (simultaneously)
			{
				setIsCastingSimultaneouslyNow(false);
			}
			else
			{
				setIsCastingNow(false);
			}
			mog.exit();
			notifyQuestEventSkillFinished(magicEnv);
			return;
		}
		
		try
		{
			for (L2Creature target : targets)
			{
				if (target instanceof L2Playable)
				{
					if (skill.getSkillType() == L2SkillType.BUFF)
					{
						SystemMessage smsg = new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
						smsg.addSkillName(skill);
						target.sendPacket(smsg);
					}
					
					if (this instanceof L2Player && target instanceof L2Summon)
					{
						((L2Summon)target).broadcastFullInfo();
					}
				}
			}
			
			// Consume MP of the L2Creature and Send the Server->Client packet StatusUpdate with current HP and MP to all other L2Player to inform
			double mpConsume = getStat().getMpConsume(skill);
			if (mpConsume > 0)
				getStatus().reduceMp(mpConsume);
			
			// Consume HP if necessary and Send the Server->Client packet StatusUpdate with current HP and MP to all other L2Player to inform
			if (skill.getHpConsume() > 0)
			{
				double consumeHp;
				
				consumeHp = calcStat(Stats.HP_CONSUME_RATE, skill.getHpConsume(), null, null);
				if (consumeHp + 1 >= getStatus().getCurrentHp())
					consumeHp = getStatus().getCurrentHp() - 1.0;
				
				reduceCurrentHpByConsume(consumeHp);
			}
			
			// Consume CP if necessary and Send the Server->Client packet StatusUpdate with current CP/HP and MP to all other L2Player to inform
			if (skill.getCpConsume() > 0)
			{
				double consumeCp;
				
				consumeCp = skill.getCpConsume();
				if (consumeCp + 1 >= getStatus().getCurrentHp())
					consumeCp = getStatus().getCurrentHp() - 1.0;
				
				getStatus().reduceCp((int)consumeCp);
			}
			
			// Consume Items if necessary and Send the Server->Client packet InventoryUpdate with Item modification to all the L2Creature
			if (skill.getItemConsume() > 0)
			{
				if (!destroyItemByItemId("Consume", skill.getItemConsumeId(), skill.getItemConsume(), null, false))
				{
					sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
					abortCast();
					return;
				}
			}
			
			if (this instanceof L2Player)
			{
				L2Player player = (L2Player)this;
				// Reset soul bonus for skills
				player.resetLastSoulConsume();
				
				// Consume Souls if necessary
				if (skill.getSoulConsumeCount() > 0 || skill.getMaxSoulConsumeCount() > 0)
				{
					player.decreaseSouls(skill);
				}
				
				// Consume Charges if necessary ... L2SkillChargeDmg does the consume by itself.
				if (skill.getNeededCharges() > 0 && skill.getSkillType() != L2SkillType.CHARGEDAM)
				{
					player.decreaseCharges(skill.getNeededCharges());
				}
			}
			
			// Launch the magic skill in order to calculate its effects
			callSkill(skill, targets.toArray(new L2Creature[targets.size()]));
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		final int coolTime = magicEnv._coolTime;
		
		if (coolTime < 10)
			onMagicFinalizer(magicEnv);
		else
		{
			if (simultaneously)
				_skillCast2 = ThreadPoolManager.getInstance().scheduleEffect(new MagicFinalizer(magicEnv), coolTime);
			else
				_skillCast = ThreadPoolManager.getInstance().scheduleEffect(new MagicFinalizer(magicEnv), coolTime);
		}
	}
	
	/**
	 * Runs after skill hitTime+coolTime
	 * @param magicEnv
	 */
	private final void onMagicFinalizer(MagicEnv magicEnv)
	{
		final L2Skill skill = magicEnv._skill;
		final boolean simultaneously = magicEnv._simultaneously;
		
		if (simultaneously)
		{
			setIsCastingSimultaneouslyNow(false);
			return;
		}
		else
		{
			setIsCastingNow(false);
		}
		
		// if the skill has changed the character's state to something other than STATE_CASTING
		// then just leave it that way, otherwise switch back to STATE_IDLE.
		// if(isCastingNow())
		// getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);
		
		switch (skill.getSkillType())
		{
			case PDAM:
			case BLOW:
			case CHARGEDAM:
			case SPOIL:
			case STUN:
			{
				final L2Creature originalTarget = magicEnv._originalTarget;
				final L2Creature originalSkillTarget = magicEnv._originalSkillTarget;
				final L2Object currentTarget = L2Object.getActingCharacter(getTarget());
				
				L2Object newTarget = null;
				
				if (originalSkillTarget != null && originalSkillTarget != this && originalSkillTarget == currentTarget)
					newTarget = originalSkillTarget;
				else if (originalTarget != null && originalTarget != this && originalTarget == currentTarget)
					newTarget = originalTarget;
				
				if (// As far as I remember, you can move away after launching a skill without hitting
				getAI().getIntention() != AI_INTENTION_MOVE_TO && (getAI().getNextCtrlIntention() == null
				// I see no problems with this...
						|| getAI().getNextCtrlIntention() == AI_INTENTION_IDLE)
						// And you will not auto-attack a non-flagged player after launching a skill
						&& newTarget != null && newTarget.isAutoAttackable(this))
				{
					double distance = Util.calculateDistance(this, newTarget, false);
					
					// if the skill is melee, or almost in the range of a normal attack
					if (getMagicalAttackRange(skill) < 200 || getPhysicalAttackRange() + 200 > distance)
						getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, newTarget);
				}
			}
		}
		
		switch (skill.getSkillType())
		{
			case UNLOCK:
			case DELUXE_KEY_UNLOCK:
			case MAKE_KILLABLE:
			case MAKE_QUEST_DROPABLE:
				break;
			default:
			{
				if (skill.isOffensive())
					getAI().clientStartAutoAttack();
			}
		}
		
		// Notify the AI of the L2Creature with EVT_FINISH_CASTING
		getAI().notifyEvent(CtrlEvent.EVT_FINISH_CASTING);
		
		notifyQuestEventSkillFinished(magicEnv);
	}
	
	// Quest event ON_SPELL_FNISHED
	private void notifyQuestEventSkillFinished(MagicEnv magicEnv)
	{
		if (this instanceof L2Npc)
		{
			for (L2Creature target : magicEnv._targets)
			{
				try
				{
					final Quest[] quests =
							((L2NpcTemplate)getTemplate()).getEventQuests(QuestEventType.ON_SPELL_FINISHED);
					
					if (quests != null)
					{
						final L2Player player = target.getActingPlayer();
						
						for (Quest quest : quests)
							quest.notifySpellFinished(((L2Npc)this), player, magicEnv._skill);
					}
				}
				catch (Exception e)
				{
					_log.error(e.getMessage(), e);
				}
			}
		}
	}
	
	/**
	 * Enable a skill (remove it from _disabledSkills of the L2Creature).<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All skills disabled are identified by their skillId in <B>_disabledSkills</B> of the L2Creature <BR>
	 * <BR>
	 * 
	 * @param skillId The identifier of the L2Skill to enable
	 */
	public void enableSkill(int skillId)
	{
		if (_disabledSkills == null)
			return;
		
		final ScheduledFuture<?> task = _disabledSkills.remove(skillId);
		
		if (task != null)
			task.cancel(false);
	}
	
	/**
	 * Disable a skill (add it to _disabledSkills of the L2Creature).<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All skills disabled are identified by their skillId in <B>_disabledSkills</B> of the L2Creature <BR>
	 * <BR>
	 * 
	 * @param skillId The identifier of the L2Skill to disable
	 * @deprecated
	 */
	@Deprecated
	public final void disableSkill(int skillId)
	{
		disableSkill(skillId, Integer.MAX_VALUE);
	}
	
	/**
	 * Disable this skill id for the duration of the delay in milliseconds.
	 * 
	 * @param skillId
	 * @param delay in milliseconds
	 * @return modified
	 */
	public boolean disableSkill(int skillId, int delay)
	{
		if (delay < 100)
			return false;
		
		if (_disabledSkills == null)
			_disabledSkills = new LinkedHashMap<Integer, ScheduledFuture<?>>();
		
		final ScheduledFuture<?> oldTask = _disabledSkills.get(skillId);
		
		if (oldTask != null)
		{
			if (oldTask.getDelay(TimeUnit.MILLISECONDS) + 50 >= delay)
				return false;
			
			oldTask.cancel(false);
		}
		
		_disabledSkills.put(skillId, ThreadPoolManager.getInstance().schedule(new EnableSkill(skillId), delay));
		return true;
	}
	
	/** Task launching the function enableSkill() */
	private final class EnableSkill implements Runnable
	{
		private final int _skillId;
		
		public EnableSkill(int skillId)
		{
			_skillId = skillId;
		}
		
		@Override
		public void run()
		{
			enableSkill(_skillId);
		}
	}
	
	/**
	 * Check if a skill is disabled.<BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * All skills disabled are identified by their skillId in <B>_disabledSkills</B> of the L2Creature <BR>
	 * <BR>
	 * 
	 * @param skillId The identifier of the L2Skill to disable
	 */
	public final boolean isSkillDisabled(int skillId)
	{
		if (isAllSkillsDisabled())
			return true;
		
		if (_disabledSkills == null)
			return false;
		
		return _disabledSkills.containsKey(skillId);
	}
	
	/**
	 * Disable all skills (set _allSkillsDisabled to True).<BR>
	 * <BR>
	 */
	public final void disableAllSkills()
	{
		if (_log.isDebugEnabled())
			_log.debug("all skills disabled");
		_allSkillsDisabled = true;
	}
	
	/**
	 * Enable all skills (set _allSkillsDisabled to False).<BR>
	 * <BR>
	 */
	public final void enableAllSkills()
	{
		if (_log.isDebugEnabled())
			_log.debug("all skills enabled");
		_allSkillsDisabled = false;
	}
	
	/**
	 * Launch the magic skill and calculate its effects on each target contained in the targets table.<BR>
	 * <BR>
	 *
	 * @param skill
	 *            The L2Skill to use
	 * @param targets
	 *            The table of L2Object targets
	 */
	public void callSkill(L2Skill skill, L2Creature... targets)
	{
		L2Weapon activeWeapon = getActiveWeaponItem();
		
		L2Player player = getActingPlayer();
		
		for (L2Object trg : targets)
		{
			if (player != null && trg instanceof L2Player && Config.SIEGE_ONLY_REGISTERED)
			{
				if (!((L2Player)trg).canBeTargetedByAtSiege(player))
				{
					//quick fix should be just removed from targetlist
					return;
				}
			}
			
			if (trg instanceof L2Creature)
			{
				// Set some values inside target's instance for later use
				L2Creature target = (L2Creature)trg;
				
				// Check Raidboss attack and
				// check buffing chars who attack raidboss. Results in mute.
				L2Creature targetsAttackTarget = null;
				L2Creature targetsCastTarget = null;
				if (target.hasAI())
				{
					targetsAttackTarget = target.getAI().getAttackTarget();
					targetsCastTarget = target.getAI().getCastTarget();
				}
				
				if (!Config.ALT_DISABLE_RAIDBOSS_PETRIFICATION
						&& getSkillLevel(L2Boss.BOSS_PENALTY_RESISTANCE) == -1
						&& ((target.isRaid() && getLevel() > target.getLevel() + 8)
								|| (!skill.isOffensive() && targetsAttackTarget != null && targetsAttackTarget.isRaid()
										&& targetsAttackTarget.getAttackByList().contains(target) // has attacked raid
								&& getLevel() > targetsAttackTarget.getLevel() + 8) || (!skill.isOffensive()
								&& targetsCastTarget != null && targetsCastTarget.isRaid()
								&& targetsCastTarget.getAttackByList().contains(target) // has attacked raid
						&& getLevel() > targetsCastTarget.getLevel() + 8)))
				{
					if (skill.isMagic())
					{
						L2Skill tempSkill = SkillTable.getInstance().getInfo(L2Boss.BOSS_PENALTY_SILENCE, 1);
						if (tempSkill != null)
							tempSkill.getEffects(target, this);
						else
							_log.warn("Skill " + L2Boss.BOSS_PENALTY_SILENCE + " at level 1 is missing in DP.");
					}
					else
					{
						L2Skill tempSkill = SkillTable.getInstance().getInfo(L2Boss.BOSS_PENALTY_PETRIFICATION, 1);
						if (tempSkill != null)
							tempSkill.getEffects(target, this);
						else
							_log.warn("Skill " + L2Boss.BOSS_PENALTY_PETRIFICATION + " at level 1 is missing in DP.");
					}
					return;
				}
				
				// Check if over-hit is possible
				if (skill.isOverhit())
				{
					if (target instanceof L2Attackable)
						((L2Attackable)target).overhitEnabled(true);
				}
				
				if (ChanceSkillList.canTriggerByCast(this, target, skill))
				{
					// Launch weapon Special ability skill effect if available
					if (activeWeapon != null)
						activeWeapon.getSkillEffectsByCast(this, target, skill);
					
					// Maybe launch chance skills on us
					if (_chanceSkills != null)
						_chanceSkills.onSkillHit(target, false, skill);
					
					// Maybe launch chance skills on target
					if (target.getChanceSkills() != null)
						target.getChanceSkills().onSkillHit(this, true, skill);
				}
			}
		}
		
		// Launch the magic skill and calculate its effects
		SkillHandler.getInstance().useSkill(this, skill, targets);
		
		if (skill.useSpiritShot() && !skill.hasEffectWhileCasting())
			useSpiritshotCharge();
		
		rechargeShot();
		
		if (player != null)
		{
			final boolean isAggroReducingSkill =
					skill.getSkillType() == L2SkillType.AGGREMOVE || skill.getSkillType() == L2SkillType.AGGREDUCE
							|| skill.getSkillType() == L2SkillType.AGGREDUCE_CHAR;
			
			for (L2Object target : targets)
			{
				// EVT_ATTACKED and PvPStatus
				if (target instanceof L2Creature)
				{
					if (skill.isNeutral())
					{
						// no flags
					}
					else if (skill.isOffensive())
					{
						if (target instanceof L2Player || target instanceof L2Summon || target instanceof L2Trap)
						{
							if (skill.getSkillType() != L2SkillType.SIGNET
									&& skill.getSkillType() != L2SkillType.SIGNET_CASTTIME)
							{
								if (!isAggroReducingSkill)
								{
									// notify target AI about the attack
									((L2Creature)target).getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, player);
								}
								
								if (!(target instanceof L2Summon) || player.getPet() != target)
									player.updatePvPStatus(target.getActingPlayer());
							}
						}
						else if (target instanceof L2Attackable)
						{
							if (!isAggroReducingSkill)
							{
								switch (skill.getId())
								{
									case 51:
									case 511:
										break;
									default:
										// add attacker into list
										((L2Creature)target).addAttackerToAttackByList(this);
								}
								// notify target AI about the attack
								((L2Creature)target).getAI().notifyEvent(CtrlEvent.EVT_ATTACKED, this);
							}
						}
					}
					else
					{
						if (target instanceof L2Player)
						{
							// Casting non offensive skill on player with pvp flag set or with karma
							if (target != this
									&& target != player
									&& (((L2Player)target).getPvpFlag() > 0 || ((L2Player)target).getKarma() > 0))
								player.updatePvPStatus();
						}
						else if (target instanceof L2Attackable && !(skill.getSkillType() == L2SkillType.SUMMON)
								&& !(skill.getSkillType() == L2SkillType.BEAST_FEED)
								&& !(skill.getSkillType() == L2SkillType.UNLOCK)
								&& !(skill.getSkillType() == L2SkillType.DELUXE_KEY_UNLOCK)
								&& !(skill.getSkillType() == L2SkillType.HEAL_MOB)
								&& !(skill.getSkillType() == L2SkillType.MAKE_KILLABLE)
								&& !(skill.getSkillType() == L2SkillType.MAKE_QUEST_DROPABLE)
								&& (!(target instanceof L2Summon) || player.getPet() != target))
							player.updatePvPStatus();
					}
				}
			}
			
			notifyMobsAboutSkillCast(skill, targets);
		}
	}
	
	public void notifyMobsAboutSkillCast(L2Skill skill, L2Creature... targets)
	{
		L2Player player = getActingPlayer();
		
		if (player == null)
			return;
		
		// Mobs in range 1000 see spell
		for (L2Object obj : player.getKnownList().getKnownObjects().values())
		{
			if (!(obj instanceof L2Npc))
				continue;
			
			final L2Npc npc = (L2Npc)obj;
			
			if (!npc.isInsideRadius(player, 1000, true, true))
				continue;
			
			final Quest[] quests = npc.getTemplate().getEventQuests(QuestEventType.ON_SKILL_SEE);
			if (quests != null)
				for (Quest quest : quests)
					quest.notifySkillSee(npc, player, skill, targets, this instanceof L2Summon);
		}
	}
	
	public boolean isBehind(L2Object src)
	{
		return Direction.getDirection(this, src) == Direction.BACK;
	}
	
	/**
	 * @deprecated should be called with proper target
	 */
	@Deprecated
	public boolean isBehindTarget()
	{
		return isBehind(getTarget());
	}
	
	public boolean isInFrontOf(L2Object src, double degree)
	{
		return Util.isInAngle(this, src, degree);
	}
	
	public boolean isInFrontOf(L2Object src)
	{
		return Direction.getDirection(this, src) == Direction.FRONT;
	}
	
	/**
	 * @deprecated should be called with proper target
	 */
	@Deprecated
	public boolean isInFrontOfTarget()
	{
		return isInFrontOf(getTarget());
	}
	
	/**
	 * Return 1.<BR>
	 * <BR>
	 */
	public double getLevelMod()
	{
		return 1;
	}
	
	public final void setTeleportSkillCast(final Runnable runnable, int delay)
	{
		getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		
		// SoE Animation section
		setTarget(this);
		
		broadcastPacket(new MagicSkillUse(this, 1050, 1, delay, 0));
		// End SoE Animation section
		
		setSkillCast(new Runnable() {
			@Override
			public void run()
			{
				if (isDead())
					return;
				
				runnable.run();
			}
		}, delay);
	}
	
	public final void setSkillCast(final Runnable runnable, int delay)
	{
		if (this instanceof L2Player)
			getActingPlayer().sendPacket(new SetupGauge(SetupGauge.BLUE, delay));
		
		setIsCastingNow(true, delay);
		
		_skillCast = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run()
			{
				setIsCastingNow(false);
				
				runnable.run();
			}
		}, delay);
	}
	
	private boolean _AIdisabled = false;
	
	private boolean _isMinion = false;
	
	/**
	 * Return a Random Damage in function of the weapon.<BR>
	 * <BR>
	 */
	public final int getRandomDamage(L2Creature target)
	{
		L2Weapon weaponItem = getActiveWeaponItem();
		
		if (weaponItem == null)
			return 5 + (int)Math.sqrt(getLevel());
		
		return weaponItem.getRandomDamage();
	}
	
	@Override
	public String toString()
	{
		return "mob " + getObjectId();
	}
	
	/**
	 * Not Implemented.<BR>
	 * <BR>
	 */
	public abstract int getLevel();
	
	// =========================================================
	
	// =========================================================
	// Stat - NEED TO REMOVE ONCE CREATURESTAT IS COMPLETE
	// Property - Public
	public final double calcStat(Stats stat, double init, L2Creature target, L2Skill skill)
	{
		return getStat().calcStat(stat, init, target, skill);
	}
	
	// Property - Public
	public final int getAccuracy()
	{
		return getStat().getAccuracy();
	}
	
	public final int getCriticalHit()
	{
		return getStat().getCriticalHit(null);
	}
	
	public final int getEvasionRate()
	{
		return getStat().getEvasionRate(null);
	}
	
	public final int getINT()
	{
		return getStat().getINT();
	}
	
	public final int getMagicalAttackRange(L2Skill skill)
	{
		return getStat().getMagicalAttackRange(skill);
	}
	
	public final int getMaxCp()
	{
		return getStat().getMaxCp();
	}
	
	public final int getMAtk(L2Creature target, L2Skill skill)
	{
		return getStat().getMAtk(target, skill);
	}
	
	public final int getMAtkSpd()
	{
		return getStat().getMAtkSpd();
	}
	
	public final int getMaxMp()
	{
		return getStat().getMaxMp();
	}
	
	public final int getMaxHp()
	{
		return getStat().getMaxHp();
	}
	
	public final int getMCriticalHit(L2Creature target, L2Skill skill)
	{
		return getStat().getMCriticalHit(target, skill);
	}
	
	public final int getMDef(L2Creature target, L2Skill skill)
	{
		return getStat().getMDef(target, skill);
	}
	
	public final int getPAtk(L2Creature target)
	{
		return getStat().getPAtk(target);
	}
	
	public final int getPAtkSpd()
	{
		return getStat().getPAtkSpd();
	}
	
	public final int getPDef(L2Creature target)
	{
		return getStat().getPDef(target);
	}
	
	public final int getShldDef()
	{
		return getStat().getShldDef();
	}
	
	public final int getPhysicalAttackRange()
	{
		return getStat().getPhysicalAttackRange();
	}
	
	public final int getRunSpeed()
	{
		return getStat().getRunSpeed();
	}
	
	// =========================================================
	
	// =========================================================
	// Status - NEED TO REMOVE ONCE L2CHARTATUS IS COMPLETE
	// Method - Public
	// WRAPPERS ONLY! DO NOT OVERRIDE IT!
	public final void reduceCurrentHp(double i, L2Creature attacker)
	{
		getStatus().reduceHp(i, attacker);
	}
	
	public final void reduceCurrentHp(double i, L2Creature attacker, L2Skill skill)
	{
		getStatus().reduceHp(i, attacker);
	}
	
	@Deprecated
	@SuppressWarnings("deprecation")
	public final void reduceCurrentHp(double i, L2Creature attacker, boolean awake)
	{
		getStatus().reduceHp(i, attacker, awake);
	}
	
	@Deprecated
	@SuppressWarnings("deprecation")
	public final void reduceCurrentHp(double i, L2Creature attacker, boolean awake, L2Skill skill)
	{
		getStatus().reduceHp(i, attacker, awake);
	}
	
	public final void reduceCurrentHp(double i, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume)
	{
		getStatus().reduceHp(i, attacker, awake, isDOT, isConsume);
	}
	
	public final void reduceCurrentHp(double i, L2Creature attacker, boolean awake, boolean isDOT, boolean isConsume,
			L2Skill skill)
	{
		getStatus().reduceHp(i, attacker, awake, isDOT, isConsume);
	}
	
	public final void reduceCurrentHpByDOT(double i, L2Creature attacker, L2Skill skill)
	{
		getStatus().reduceHpByDOT(i, attacker, skill);
	}
	
	public final void reduceCurrentHpByConsume(double i)
	{
		getStatus().reduceHpByConsume(i);
	}
	
	public final void reduceCurrentMp(double i)
	{
		getStatus().reduceMp(i);
	}
	
	// =========================================================
	public boolean isChampion()
	{
		return false;
	}
	
	/**
	 * Check player max buff count
	 * @return max buff count
	 */
	public int getMaxBuffCount()
	{
		return Config.ALT_BUFFS_MAX_AMOUNT + Math.max(0, getSkillLevel(L2Skill.SKILL_DIVINE_INSPIRATION));
	}
	
	/**
	 * Send system message about damage.<BR>
	 * <BR>
	 * <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2Player
	 * <li> L2SummonInstance
	 * <li> L2PetInstance</li>
	 * <BR>
	 * <BR>
	 * @param target
	 * @param damage
	 * @param mcrit
	 * @param pcrit
	 * @param miss
	 */
	public void sendDamageMessage(L2Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss)
	{
		if (miss)
			target.sendAvoidMessage(this);
	}
	
	public void sendAvoidMessage(L2Creature attacker)
	{
	}
	
	public final void sendMissedDamageMessage(L2Creature target)
	{
		sendDamageMessage(target, -1, false, false, true);
	}
	
	public FusionSkill getFusionSkill()
	{
		return _fusionSkill;
	}
	
	public void setFusionSkill(FusionSkill fs)
	{
		_fusionSkill = fs;
	}
	
	public ChanceSkillList getChanceSkills()
	{
		return _chanceSkills;
	}
	
	public final byte getAttackElement()
	{
		return getStat().getAttackElement();
	}
	
	public final int getDefenseElementValue(byte defenseAttribute)
	{
		return getStat().getDefenseElementValue(defenseAttribute);
	}
	
	// Wrapper
	public final double getCurrentHp()
	{
		return getStatus().getCurrentHp();
	}
	
	// Wrapper
	public final double getCurrentMp()
	{
		return getStatus().getCurrentMp();
	}
	
	// Wrapper
	public final double getCurrentCp()
	{
		return getStatus().getCurrentCp();
	}
	
	public final int getAttackElementValue(byte attackAttribute)
	{
		return getStat().getAttackElementValue(attackAttribute);
	}
	
	public boolean mustFallDownOnDeath()
	{
		return isDead();
	}
	
	public void setPreventedFromReceivingBuffs(boolean value)
	{
		_block_buffs = value;
	}
	
	public boolean isPreventedFromReceivingBuffs()
	{
		return _block_buffs;
	}
	
	private final class FlyToLocationTask implements Runnable
	{
		private final L2Creature _flyTarget;
		private final L2Skill _skill;
		
		public FlyToLocationTask(L2Creature target, L2Skill skill)
		{
			_flyTarget = target;
			_skill = skill;
		}
		
		@Override
		public void run()
		{
			broadcastPacket(new FlyToLocation(L2Creature.this, _flyTarget, _skill.getFlyType()));
			getPosition().setXYZ(_flyTarget.getX(), _flyTarget.getY(), _flyTarget.getZ());
			broadcastPacket(new ValidateLocation(L2Creature.this));
		}
	}
	
	/** Task for potion and herb queue */
	private final class UsePotionTask implements Runnable
	{
		private final L2Skill _skill;
		
		private UsePotionTask(L2Skill skill)
		{
			_skill = skill;
		}
		
		@Override
		public void run()
		{
			doSimultaneousCast(_skill);
		}
	}
	
	public boolean isRaidMinion()
	{
		return _isMinion;
	}
	
	public boolean isRaidBoss()
	{
		return _isRaid && !_isMinion;
	}
	
	/**
	 * Set this Npc as a Minion instance.<BR><BR>
	 * @param val
	 */
	public void setIsRaidMinion(boolean val)
	{
		_isRaid = val;
		_isMinion = val;
	}
	
	private volatile byte _packetBroadcastMask;
	
	protected boolean shouldAddPacketBroadcastMask()
	{
		return !getKnownList().getKnownPlayers().isEmpty();
	}
	
	public final void addPacketBroadcastMask(BroadcastMode mode)
	{
		if (!shouldAddPacketBroadcastMask())
			return;
		
		_packetBroadcastMask |= mode.mask();
		
		PacketBroadcaster.getInstance().add(this);
	}
	
	public final void removePacketBroadcastMask(BroadcastMode mode)
	{
		_packetBroadcastMask &= ~mode.mask();
	}
	
	public final byte getPacketBroadcastMask()
	{
		return _packetBroadcastMask;
	}
	
	public final void broadcastFullInfo()
	{
		addPacketBroadcastMask(BroadcastMode.BROADCAST_FULL_INFO);
	}
	
	public abstract void broadcastFullInfoImpl();
	
	@Override
	public final L2Creature getActingCharacter()
	{
		return this;
	}
	
	protected final CreatureShots _shots;
	
	protected CreatureShots initShots()
	{
		return CreatureShots.getEmptyInstance();
	}
	
	public CreatureShots getShots()
	{
		return _shots;
	}
	
	public final void rechargeShot()
	{
		getShots().rechargeShots();
	}
	
	public final void scheduleShotRecharge(int delay)
	{
		getShots().scheduleShotRecharge(delay);
	}
	
	public final boolean isSoulshotCharged()
	{
		return getShots().isSoulshotCharged();
	}
	
	public final boolean isSpiritshotCharged()
	{
		return getShots().isSpiritshotCharged();
	}
	
	public final boolean isBlessedSpiritshotCharged()
	{
		return getShots().isBlessedSpiritshotCharged();
	}
	
	public final boolean isAnySpiritshotCharged()
	{
		return getShots().isAnySpiritshotCharged();
	}
	
	public final boolean isFishshotCharged()
	{
		return getShots().isFishshotCharged();
	}
	
	public final void useSoulshotCharge()
	{
		getShots().useSoulshotCharge();
	}
	
	public final void useSpiritshotCharge()
	{
		getShots().useSpiritshotCharge();
	}
	
	public final void useBlessedSpiritshotCharge()
	{
		getShots().useBlessedSpiritshotCharge();
	}
	
	public final void useFishshotCharge()
	{
		getShots().useFishshotCharge();
	}
	
	public final void clearShotCharges()
	{
		getShots().clearShotCharges();
	}
	
	public void updateInvisibilityStatus()
	{
		// Since knownlist objects are always mutual linked, DeleteObject is used for invisibility.
		// In this case, sending the specific *Info packet is prevented until the object is visible again.
		DeleteObject de = new DeleteObject(this);
		for (L2Player player : getKnownList().getKnownPlayers().values())
		{
			if (!player.canSee(this))
			{
				if (player.getTarget() == this)
				{
					player.setTarget(null);
					player.abortAttack();
				}
				player.sendPacket(de);
			}
		}
		broadcastFullInfo();
	}
	
	public void sendResistedMyEffectMessage(L2Creature target, L2Skill skill)
	{
	}
	
	public void sendResistedMyMagicMessage(L2Creature target)
	{
		target.sendResistedAgainstMagicMessage(this);
	}
	
	public void sendResistedMyMagicSlightlyMessage(L2Creature target)
	{
		target.sendResistedAgainstMagicWeaklyMessage(this);
	}
	
	public void sendResistedAgainstMagicMessage(L2Creature attacker)
	{
	}
	
	public void sendResistedAgainstMagicWeaklyMessage(L2Creature attacker)
	{
	}
}
