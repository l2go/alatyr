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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.l2jfree.Config;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.datatables.BuffTemplateTable;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.DoorTable;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.MultisellTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SpawnTable;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.instance.L2AuctioneerInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2CCHBossInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2ChestInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2ClanHallManagerInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2ControllableMobInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2DoormenInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FestivalGuideInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FishermanInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MerchantInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2TeleporterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2WarehouseInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.NpcInventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.knownlist.CreatureKnownList;
import com.l2jfree.gameserver.gameobjects.knownlist.NpcKnownList;
import com.l2jfree.gameserver.gameobjects.shot.CreatureShots;
import com.l2jfree.gameserver.gameobjects.shot.NpcShots;
import com.l2jfree.gameserver.gameobjects.stat.CreatureStat;
import com.l2jfree.gameserver.gameobjects.stat.NpcStat;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.status.NpcStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate.AIType;
import com.l2jfree.gameserver.gameobjects.view.ICreatureView;
import com.l2jfree.gameserver.gameobjects.view.NpcView;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.instancemanager.TownManager;
import com.l2jfree.gameserver.instancemanager.games.Lottery;
import com.l2jfree.gameserver.instancemanager.grandbosses.BaiumManager;
import com.l2jfree.gameserver.instancemanager.leaderboards.ArenaManager;
import com.l2jfree.gameserver.instancemanager.leaderboards.FishermanManager;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.drop.L2DropCategory;
import com.l2jfree.gameserver.model.drop.L2DropData;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.model.entity.Town;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.quest.State;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.sevensigns.SevenSignsFestival;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.templates.L2BuffTemplate;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.model.world.spawn.L2Spawn;
import com.l2jfree.gameserver.model.world.spawn.MobGroupTable;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.server.AbstractNpcInfo;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.ExShowBaseAttributeCancelWindow;
import com.l2jfree.gameserver.network.packets.server.ExShowQuestInfo;
import com.l2jfree.gameserver.network.packets.server.ExShowVariationCancelWindow;
import com.l2jfree.gameserver.network.packets.server.ExShowVariationMakeWindow;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.RadarControl;
import com.l2jfree.gameserver.network.packets.server.ServerObjectInfo;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.taskmanager.AbstractIterativePeriodicTaskManager;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;
import com.l2jfree.gameserver.util.StringUtil;
import com.l2jfree.lang.L2Math;
import com.l2jfree.tools.random.Rnd;

/**
 * This class represents a Non-Player-Character in the world. It can be a monster or a friendly character.
 * It also uses a template to fetch some static values. The templates are hardcoded in the client, so we can rely on them.<BR><BR>
 *
 * L2Creature :<BR><BR>
 * <li>L2Attackable</li>
 * <li>L2NpcInstance</li>
 *
 * @version $Revision: 1.32.2.7.2.24 $ $Date: 2005/04/11 10:06:09 $
 */
public class L2Npc extends L2Creature
{
	private static final class RandomAnimationTaskManager extends AbstractIterativePeriodicTaskManager<L2Npc>
	{
		private static final int MIN_SOCIAL_INTERVAL = 6000;
		private static final RandomAnimationTaskManager _instance = new RandomAnimationTaskManager();
		
		private static RandomAnimationTaskManager getInstance()
		{
			return _instance;
		}
		
		private RandomAnimationTaskManager()
		{
			super(1000);
			//super(MIN_SOCIAL_INTERVAL);
		}
		
		@Override
		protected void callTask(L2Npc npc)
		{
			if (!npc.tryBroadcastRandomAnimation(false, false))
				stopTask(npc);
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "broadcastRandomAnimation()";
		}
	}
	
	/** The interaction distance of the L2Npc(is used as offset in MovetoLocation method) */
	public static final int INTERACTION_DISTANCE = 150;
	
	/** The L2Spawn object that manage this L2Npc */
	private L2Spawn _spawn;
	
	private NpcInventory _inventory = null;
	
	/** The flag to specify if this L2Npc is busy */
	private boolean _isBusy = false;
	
	/** The busy message for this L2Npc */
	private String _busyMessage = "";
	
	/** True if endDecayTask has already been called */
	volatile boolean _isDecayed = false;
	
	/** True if a Dwarf has used Spoil on this L2Npc */
	private boolean _isSpoil = false;
	
	/** Castle this NPC belongs to. Remembered after the first lookup; none when this world has no castle. */
	private final NpcCastle _castle = new NpcCastle();
	
	/** The fortress index in the array of L2Fort this L2Npc belongs to */
	private int _fortIndex = -2;
	
	public String _CTF_FlagTeamName;
	public boolean _isEventMobTvT = false, _isEventMobDM = false, _isEventMobCTF = false, _isCTF_throneSpawn = false,
			_isCTF_Flag = false, _isEventVIPNPC = false, _isEventVIPNPCEnd = false;
	public boolean _isEventMobTvTi = false;
	public boolean _isEventMobSH = false;
	
	private int _isSpoiledBy = 0;
	
	private long _lastRandomAnimation;
	private int _randomAnimationDelay;
	private int _currentLHandId; // normally this shouldn't change from the template, but there exist exceptions
	private int _currentRHandId; // normally this shouldn't change from the template, but there exist exceptions
	
	private double _currentCollisionHeight; // used for npc grow effect skills
	private double _currentCollisionRadius; // used for npc grow effect skills
	
	private boolean _isKillable = true;
	private boolean _questDropable = true;
	
	// In case quests are going to use non-L2Attackables in the future
	private int _questAttackStatus;
	private L2Player _questFirstAttacker;
	
	// doesn't affect damage at all (retail)
	private int _weaponEnchant = 0;
	
	public final void broadcastRandomAnimation(boolean force)
	{
		tryBroadcastRandomAnimation(force, true);
	}
	
	public final boolean tryBroadcastRandomAnimation(boolean force, boolean init)
	{
		if (!isInActiveRegion() || !hasRandomAnimation())
			return false;
		
		if (isMob() && getAI().getIntention() != AI_INTENTION_ACTIVE)
			return false;
		
		if (_lastRandomAnimation + RandomAnimationTaskManager.MIN_SOCIAL_INTERVAL < System.currentTimeMillis()
				&& !getKnownList().getKnownPlayers().isEmpty())
		{
			if (force || _lastRandomAnimation + _randomAnimationDelay < System.currentTimeMillis())
			{
				if (!isDead() && !isStunned() && !isSleeping() && !isParalyzed())
				{
					SocialAction sa;
					if (force) // on talk/interact
						sa = new SocialAction(getObjectId(), Rnd.get(8));
					else
						// periodic
						sa = new SocialAction(getObjectId(), Rnd.get(2, 3));
					broadcastPacket(sa);
					
					int minWait = isMob() ? Config.MIN_MONSTER_ANIMATION : Config.MIN_NPC_ANIMATION;
					int maxWait = isMob() ? Config.MAX_MONSTER_ANIMATION : Config.MAX_NPC_ANIMATION;
					
					_lastRandomAnimation = System.currentTimeMillis();
					_randomAnimationDelay = Rnd.get(minWait, maxWait) * 1000;
				}
			}
		}
		
		if (init)
			RandomAnimationTaskManager.getInstance().startTask(this);
		return true;
	}
	
	public final void stopRandomAnimation()
	{
		RandomAnimationTaskManager.getInstance().stopTask(this);
	}
	
	/**
	 * Check if the server allows Random Animation.<BR><BR>
	 */
	public boolean hasRandomAnimation()
	{
		return (Config.MAX_NPC_ANIMATION > 0 && getTemplate().getAI() != AIType.CORPSE);
	}
	
	public class DestroyTemporalNPC implements Runnable
	{
		private final L2Spawn _oldSpawn;
		
		public DestroyTemporalNPC(L2Spawn spawn)
		{
			_oldSpawn = spawn;
		}
		
		@Override
		public void run()
		{
			try
			{
				_oldSpawn.getLastSpawn().deleteMe();
				_oldSpawn.stopRespawn();
				SpawnTable.getInstance().deleteSpawn(_oldSpawn, false);
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
	}
	
	public class DestroyTemporalSummon implements Runnable
	{
		L2Summon _summon;
		L2Player _player;
		
		public DestroyTemporalSummon(L2Summon summon, L2Player player)
		{
			_summon = summon;
			_player = player;
		}
		
		@Override
		public void run()
		{
			_summon.unSummon(_player);
		}
	}
	
	/**
	 * Constructor of L2Npc (use L2Creature constructor).<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Call the L2Creature constructor to set the _template of the L2Creature (copy skills from template to object and link _calculators to NPC_STD_CALCULATOR)  </li>
	 * <li>Set the name of the L2Creature</li>
	 * <li>Create a RandomAnimation Task that will be launched after the calculated delay if the server allow it </li><BR><BR>
	 *
	 * @param objectId Identifier of the object to initialized
	 * @param template The L2NpcTemplate to apply to the NPC
	 *
	 */
	public L2Npc(int objectId, L2NpcTemplate template)
	{
		// Call the L2Creature constructor to set the _template of the L2Creature, copy skills from template to object
		// and link _calculators to NPC_STD_CALCULATOR
		super(objectId, template);
		getKnownList(); // init knownlist
		getStat(); // init stats
		getStatus(); // init status
		super.initCreatureStatusUpdateValues(); // init status upadte values
		
		// Initialize the "current" equipment
		_currentLHandId = getTemplate().getLhand();
		_currentRHandId = getTemplate().getRhand();
		// initialize the "current" collisions
		_currentCollisionHeight = getTemplate().getCollisionHeight();
		_currentCollisionRadius = getTemplate().getCollisionRadius();
		
		if (template == null)
		{
			_log.error("No template for Npc. Please check your datapack is setup correctly.");
			return;
		}
		
		// Set the name and the title of the L2Creature
		setName(template.getName());
		setTitle(template.getTitle());
		
		if ((template.getSS() > 0 || template.getBSS() > 0) && template.getSSRate() > 0)
			_inventory = new NpcInventory(this);
	}
	
	@Override
	protected CreatureKnownList initKnownList()
	{
		return new NpcKnownList(this);
	}
	
	@Override
	public NpcKnownList getKnownList()
	{
		return (NpcKnownList)_knownList;
	}
	
	@Override
	protected ICreatureView initView()
	{
		return new NpcView(this);
	}
	
	@Override
	public NpcView getView()
	{
		return (NpcView)_view;
	}
	
	@Override
	protected CreatureStat initStat()
	{
		return new NpcStat(this);
	}
	
	@Override
	public NpcStat getStat()
	{
		return (NpcStat)_stat;
	}
	
	@Override
	protected CreatureStatus initStatus()
	{
		return new NpcStatus(this);
	}
	
	@Override
	public NpcStatus getStatus()
	{
		return (NpcStatus)_status;
	}
	
	/** Return the L2NpcTemplate of the L2Npc. */
	@Override
	public final L2NpcTemplate getTemplate()
	{
		return (L2NpcTemplate)super.getTemplate();
	}
	
	/**
	 * Return the generic Identifier of this L2Npc contained in the L2NpcTemplate.<BR><BR>
	 */
	public int getNpcId()
	{
		return getTemplate().getNpcId();
	}
	
	@Override
	public boolean isAttackable()
	{
		return Config.ALT_ATTACKABLE_NPCS;
	}
	
	/**
	 * Return the faction Identifier of this L2Npc contained in the L2NpcTemplate.<BR><BR>
	 *
	 * <B><U> Concept</U> :</B><BR><BR>
	 * If a NPC belows to a Faction, other NPC of the faction inside the Faction range will help it if it's attacked<BR><BR>
	 *
	 */
	public final String getFactionId()
	{
		return getTemplate().getFactionId();
	}
	
	/**
	 * Return the Level of this L2Npc contained in the L2NpcTemplate.<BR><BR>
	 */
	@Override
	public final int getLevel()
	{
		return getTemplate().getLevel();
	}
	
	/**
	 * Return True if the L2Npc is agressive (ex : L2MonsterInstance in function of aggroRange).<BR><BR>
	 */
	public boolean isAggressive()
	{
		return false;
	}
	
	/**
	 * Return the Aggro Range of this L2Npc contained in the L2NpcTemplate.<BR><BR>
	 */
	public int getAggroRange()
	{
		return getTemplate().getAggroRange();
	}
	
	/**
	 * Return the Faction Range of this L2Npc contained in the L2NpcTemplate.<BR><BR>
	 */
	public int getFactionRange()
	{
		return getTemplate().getFactionRange();
	}
	
	/**
	 * Return True if this L2Npc is undead in function of the L2NpcTemplate.<BR><BR>
	 */
	@Override
	public boolean isUndead()
	{
		return getTemplate().isUndead();
	}
	
	/**
	 * Return the Identifier of the item in the left hand of this L2Npc contained in the L2NpcTemplate.<BR><BR>
	 */
	public int getLeftHandItem()
	{
		return _currentLHandId;
	}
	
	/**
	 * Return the Identifier of the item in the right hand of this L2Npc contained in the L2NpcTemplate.<BR><BR>
	 */
	public int getRightHandItem()
	{
		return _currentRHandId;
	}
	
	/**
	 * Return True if this L2Npc has drops that can be sweeped.<BR><BR>
	 */
	public boolean isSpoil()
	{
		return _isSpoil;
	}
	
	/**
	 * Set the spoil state of this L2Npc.<BR><BR>
	 */
	public void setSpoil(boolean isSpoil)
	{
		_isSpoil = isSpoil;
	}
	
	public final int getIsSpoiledBy()
	{
		return _isSpoiledBy;
	}
	
	public final void setIsSpoiledBy(int value)
	{
		_isSpoiledBy = value;
	}
	
	/**
	 * Return the busy status of this L2Npc.<BR><BR>
	 */
	public final boolean isBusy()
	{
		return _isBusy;
	}
	
	/**
	 * Set the busy status of this L2Npc.<BR><BR>
	 */
	public void setBusy(boolean isBusy)
	{
		_isBusy = isBusy;
	}
	
	/**
	 * Return the busy message of this L2Npc.<BR><BR>
	 */
	public final String getBusyMessage()
	{
		return _busyMessage;
	}
	
	/**
	 * Set the busy message of this L2Npc.<BR><BR>
	 */
	public void setBusyMessage(String message)
	{
		_busyMessage = message;
	}
	
	/**
	 * Return true if this L2Npc instance can be warehouse manager.<BR><BR>
	 */
	public boolean isWarehouse()
	{
		return false;
	}
	
	protected boolean canTarget(L2Player player)
	{
		if (player.isOutOfControl())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (!player.canChangeLockedTarget(this))
			return false;
		
		// Restrict interactions during restart/shutdown
		if (Shutdown.isActionDisabled(DisableType.NPC_ITERACTION))
		{
			player.sendMessage("NPC interaction disabled during restart/shutdown.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		return true;
	}
	
	public boolean canInteract(L2Player player)
	{
		// TODO: NPC busy check etc...
		
		if (player.isCastingNow() || player.isCastingSimultaneouslyNow())
			return false;
		if (player.isDead() || player.isFakeDeath())
			return false;
		if (player.isSitting())
			return false;
		if (!isSameInstance(player))
			return false;
		
		if (player.getPrivateStoreType() != 0)
			return false;
		
		return isInsideRadius(player, INTERACTION_DISTANCE, true, false);
	}
	
	/**
	 * Manage actions when a player click on the L2Npc.<BR><BR>
	 *
	 * <B><U> Actions on first click on the L2Npc (Select it)</U> :</B><BR><BR>
	 * <li>Set the L2Npc as target of the L2Player player (if necessary)</li>
	 * <li>Send a Server->Client packet MyTargetSelected to the L2Player player (display the select window)</li>
	 * <li>If L2Npc is autoAttackable, send a Server->Client packet StatusUpdate to the L2Player in order to update L2Npc HP bar </li>
	 * <li>Send a Server->Client packet ValidateLocation to correct the L2Npc position and heading on the client </li><BR><BR>
	 *
	 * <B><U> Actions on second click on the L2Npc (Attack it/Intercat with it)</U> :</B><BR><BR>
	 * <li>Send a Server->Client packet MyTargetSelected to the L2Player player (display the select window)</li>
	 * <li>If L2Npc is autoAttackable, notify the L2Player AI with AI_INTENTION_ATTACK (after a height verification)</li>
	 * <li>If L2Npc is NOT autoAttackable, notify the L2Player AI with AI_INTENTION_INTERACT (after a distance verification) and show message</li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Each group of Server->Client packet must be terminated by a ActionFailed packet in order to avoid
	 * that client wait an other packet</B></FONT><BR><BR>
	 *
	 * <B><U> Example of use </U> :</B><BR><BR>
	 * <li> Client packet : Action, AttackRequest</li><BR><BR>
	 *
	 * <B><U> Overridden in </U> :</B><BR><BR>
	 * <li> L2ArtefactInstance : Manage only fisrt click to select Artefact</li><BR><BR>
	 * <li> L2GuardInstance : </li><BR><BR>
	 *
	 * @param player The L2Player that start an action on the L2Npc
	 *
	 */
	@Override
	public void onAction(L2Player player)
	{
		if (!canTarget(player))
			return;
		
		player.setLastFolkNPC(this);
		
		try
		{
			// Check if the L2Player already target the L2Npc
			if (this != player.getTarget())
			{
				if (_log.isDebugEnabled())
					_log.debug("new target selected:" + getObjectId());
				
				// Set the target of the L2Player player
				player.setTarget(this);
				
				// Check if the player is attackable (without a forced attack)
				if (isAutoAttackable(player))
				{
					// Send a Server->Client packet StatusUpdate of the L2Npc to the L2Player to update its HP bar
					StatusUpdate su = new StatusUpdate(getObjectId());
					su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp());
					su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
					player.sendPacket(su);
				}
			}
			else
			{
				// Check if the player is attackable (without a forced attack) and isn't dead
				if (isAutoAttackable(player) && !isAlikeDead())
				{
					// Check the height difference
					if (Math.abs(player.getZ() - getZ()) < 400) // this max heigth difference might need some tweaking
					{
						// Set the L2Player Intention to AI_INTENTION_ATTACK
						player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
						// player.startAttack(this);
					}
					else
					{
						// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
						player.sendPacket(ActionFailed.STATIC_PACKET);
					}
				}
				else if (!isAutoAttackable(player))
				{
					// Calculate the distance between the L2Player and the L2Npc
					if (!canInteract(player))
					{
						// Notify the L2Player AI with AI_INTENTION_INTERACT
						player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
					}
					else
					{
						// Send a Server->Client packet SocialAction to the all L2Player on the _knownPlayer of the L2Npc
						// to display a social action of the L2Npc on their client
						broadcastRandomAnimation(true);
						
						// Open a chat window on client with the text of the L2Npc
						if (GlobalRestrictions.onAction(this, player))
						{
						}
						else
						{
							Quest[] qlsa = getTemplate().getEventQuests(Quest.QuestEventType.QUEST_START);
							if (qlsa != null && qlsa.length > 0)
								player.setLastQuestNpcObject(getObjectId());
							Quest[] qlst = getTemplate().getEventQuests(Quest.QuestEventType.ON_FIRST_TALK);
							if ((qlst != null) && qlst.length == 1)
								qlst[0].notifyFirstTalk(this, player);
							else
								showChatWindow(player, 0);
						}
						player.sendPacket(ActionFailed.STATIC_PACKET);
					}
				}
				else
					player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
		catch (Exception e)
		{
			_log.error("", e);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	@Override
	public int getMyTargetSelectedColor(L2Player player)
	{
		if (isAutoAttackable(player))
			return player.getLevel() - getLevel();
		else
			return 0;
	}
	
	/**
	 * Manage and Display the GM console to modify the L2Npc (GM only).<BR><BR>
	 *
	 * <B><U> Actions (If the L2Player is a GM only)</U> :</B><BR><BR>
	 * <li>Set the L2Npc as target of the L2Player player (if necessary)</li>
	 * <li>Send a Server->Client packet MyTargetSelected to the L2Player player (display the select window)</li>
	 * <li>If L2Npc is autoAttackable, send a Server->Client packet StatusUpdate to the L2Player in order to update L2Npc HP bar </li>
	 * <li>Send a Server->Client NpcHtmlMessage() containing the GM console about this L2Npc </li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Each group of Server->Client packet must be terminated by a ActionFailed packet in order to avoid
	 * that client wait an other packet</B></FONT><BR><BR>
	 *
	 * <B><U> Example of use </U> :</B><BR><BR>
	 * <li> Client packet : Action</li><BR><BR>
	 *
	 * @param player The thread that manage the player that pessed Shift and click on the L2Npc
	 *
	 */
	@Override
	public void onActionShift(L2Player player)
	{
		// Check if the L2Player is a GM
		if (player.getAccessLevel() >= Config.GM_ACCESSLEVEL)
		{
			// Set the target of the L2Player player
			player.setTarget(this);
			
			// Check if the player is attackable (without a forced attack)
			if (isAutoAttackable(player))
			{
				// Send a Server->Client packet StatusUpdate of the L2Npc to the L2Player to update its HP bar
				StatusUpdate su = new StatusUpdate(getObjectId());
				su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp());
				su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
				player.sendPacket(su);
			}
			
			// Send a Server->Client NpcHtmlMessage() containing the GM console about this L2Npc
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			String className = getClass().getSimpleName();
			
			final StringBuilder html1 =
					StringUtil.startAppend(500,
							"<html><body><center><font color=\"LEVEL\">NPC Information</font></center>" + "<br>"
									+ "Instance Type: ", className, "<br1>Faction: ", getFactionId() != null
									? getFactionId() : "null", "<br1>");
			StringUtil.append(html1, "Coords ", String.valueOf(getX()), ",", String.valueOf(getY()), ",",
					String.valueOf(getZ()), "<br1>");
			if (getSpawn() != null)
				StringUtil.append(html1, "Spawn ", String.valueOf(getSpawn().getLocx()), ",", String.valueOf(getSpawn()
						.getLocy()), ",", String.valueOf(getSpawn().getLocz()), " Loc ID: ", String.valueOf(getSpawn()
						.getLocation()), "<br1>", "Distance from spawn 2D ", String.valueOf((int)Math
						.sqrt(getPlanDistanceSq(getSpawn().getLocx(), getSpawn().getLocy()))), " 3D ", String
						.valueOf((int)Math.sqrt(getDistanceSq(getSpawn().getLocx(), getSpawn().getLocy(), getSpawn()
								.getLocz()))), "<br1>");
			
			if (this instanceof L2ControllableMobInstance)
				html1.append("Mob Group: "
						+ MobGroupTable.getInstance().getGroupForMob((L2ControllableMobInstance)this).getGroupId()
						+ "<br>");
			else
				html1.append("Respawn Time: "
						+ (getSpawn() != null ? (getSpawn().getRespawnDelay() / 1000) + "  Seconds<br>"
								: "?  Seconds<br>"));
			
			html1.append("<table border=\"0\" width=\"100%\">");
			html1.append("<tr><td>Object ID</td><td>" + getObjectId() + "</td><td>NPC ID</td><td>"
					+ getTemplate().getNpcId() + "</td></tr>");
			Castle residence = getCastle();
			html1.append("<tr><td>Castle</td><td>" + (residence == null ? "none" : residence.getCastleId())
					+ "</td><td>AI </td><td>"
					+ (hasAI() ? getAI().getIntention() : "NULL") + "</td></tr>");
			html1.append("<tr><td>Level</td><td>" + getLevel() + "</td><td>Aggro</td><td>"
					+ ((this instanceof L2Attackable) ? getAggroRange() : 0) + "</td></tr>");
			html1.append("</table><br>");
			
			html1.append("<font color=\"LEVEL\">Combat</font>");
			html1.append("<table border=\"0\" width=\"100%\">");
			html1.append("<tr><td>Current HP</td><td>" + getStatus().getCurrentHp() + "</td><td>Current MP</td><td>"
					+ getStatus().getCurrentMp() + "</td></tr>");
			html1.append("<tr><td>Max.HP</td><td>"
					+ (int)(getMaxHp() / getStat().calcStat(Stats.MAX_HP, 1, this, null)) + "*"
					+ getStat().calcStat(Stats.MAX_HP, 1, this, null) + "</td><td>Max.MP</td><td>" + getMaxMp()
					+ "</td></tr>");
			html1.append("<tr><td>P.Atk.</td><td>" + getPAtk(null) + "</td><td>M.Atk.</td><td>" + getMAtk(null, null)
					+ "</td></tr>");
			html1.append("<tr><td>P.Def.</td><td>" + getPDef(null) + "</td><td>M.Def.</td><td>" + getMDef(null, null)
					+ "</td></tr>");
			html1.append("<tr><td>Accuracy</td><td>" + getAccuracy() + "</td><td>Evasion</td><td>" + getEvasionRate()
					+ "</td></tr>");
			html1.append("<tr><td>Critical</td><td>" + getCriticalHit() + "</td><td>Speed</td><td>" + getRunSpeed()
					+ "</td></tr>");
			html1.append("<tr><td>Atk.Speed</td><td>" + getPAtkSpd() + "</td><td>Cast.Speed</td><td>" + getMAtkSpd()
					+ "</td></tr>");
			html1.append("<tr><td>Race</td><td>" + getTemplate().getRace() + "</td><td></td><td></td></tr>");
			html1.append("</table><br>");
			
			html1.append("<font color=\"LEVEL\">Basic Stats</font>");
			html1.append("<table border=\"0\" width=\"100%\">");
			html1.append("<tr><td>STR</td><td>" + getStat().getSTR() + "</td><td>DEX</td><td>" + getStat().getDEX()
					+ "</td><td>CON</td><td>" + getStat().getCON() + "</td></tr>");
			html1.append("<tr><td>INT</td><td>" + getINT() + "</td><td>WIT</td><td>" + getStat().getWIT()
					+ "</td><td>MEN</td><td>" + getStat().getMEN() + "</td></tr>");
			html1.append("</table>");
			
			html1.append("<font color=\"LEVEL\">Quest Info</font>");
			html1.append("<table border=\"0\" width=\"100%\">");
			html1.append("<tr><td>Quest attack status:</td><td>" + getQuestAttackStatus() + "</td></tr>");
			html1.append("<tr><td>Quest attacker:</td><td>" + getQuestFirstAttacker() + "</td></tr>");
			html1.append("</table>");
			
			html1.append("<br><center><table><tr><td><button value=\"Edit NPC\" action=\"bypass -h admin_edit_npc "
					+ getTemplate().getNpcId()
					+ "\" width=100 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"><br1></td>");
			html1.append("<td><button value=\"Kill\" action=\"bypass -h admin_kill\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td><br1></tr>");
			html1.append("<tr><td><button value=\"Show DropList\" action=\"bypass -h admin_show_droplist "
					+ getTemplate().getNpcId()
					+ "\" width=100 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
			html1.append("<td><button value=\"Delete\" action=\"bypass -h admin_delete\" width=40 height=15 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td></tr>");
			// [L2J_JP ADD START]
			html1.append("<tr><td><button value=\"Show Skillist\" action=\"bypass -h admin_show_skilllist_npc "
					+ getTemplate().getNpcId()
					+ "\" width=100 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\"></td><td></td></tr>");
			// [L2J_JP ADD END]
			html1.append("</table></center><br>");
			html1.append("</body></html>");
			
			html.setHtml(html1.toString());
			player.sendPacket(html);
		}
		// Allow to see the stats of npc if option is activated and if not a box
		else if (Config.ALT_GAME_VIEWNPC && !(this instanceof L2ChestInstance))
		{
			// Set the target of the L2Player player
			player.setTarget(this);
			
			// Check if the player is attackable (without a forced attack)
			if (isAutoAttackable(player))
			{
				// Send a Server->Client packet StatusUpdate of the L2Npc to the L2Player to update its HP bar
				StatusUpdate su = new StatusUpdate(getObjectId());
				su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp());
				su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
				player.sendPacket(su);
			}
			
			NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			StringBuilder html1 = new StringBuilder("<html><body>");
			
			html1.append("<br><center><font color=\"LEVEL\">[Combat Stats]</font></center>");
			html1.append("<table border=0 width=\"100%\">");
			html1.append("<tr><td>Max.HP</td><td>"
					+ (int)(getMaxHp() / getStat().calcStat(Stats.MAX_HP, 1, this, null)) + "*"
					+ (int)getStat().calcStat(Stats.MAX_HP, 1, this, null) + "</td><td>Max.MP</td><td>" + getMaxMp()
					+ "</td></tr>");
			html1.append("<tr><td>P.Atk.</td><td>" + getPAtk(null) + "</td><td>M.Atk.</td><td>" + getMAtk(null, null)
					+ "</td></tr>");
			html1.append("<tr><td>P.Def.</td><td>" + getPDef(null) + "</td><td>M.Def.</td><td>" + getMDef(null, null)
					+ "</td></tr>");
			html1.append("<tr><td>Accuracy</td><td>" + getAccuracy() + "</td><td>Evasion</td><td>" + getEvasionRate()
					+ "</td></tr>");
			html1.append("<tr><td>Critical</td><td>" + getCriticalHit() + "</td><td>Speed</td><td>" + getRunSpeed()
					+ "</td></tr>");
			html1.append("<tr><td>Atk.Speed</td><td>" + getPAtkSpd() + "</td><td>Cast.Speed</td><td>" + getMAtkSpd()
					+ "</td></tr>");
			html1.append("<tr><td>Race</td><td>" + getTemplate().getRace() + "</td><td></td><td></td></tr>");
			html1.append("</table>");
			
			html1.append("<br><center><font color=\"LEVEL\">[Basic Stats]</font></center>");
			html1.append("<table border=0 width=\"100%\">");
			html1.append("<tr><td>STR</td><td>" + getStat().getSTR() + "</td><td>DEX</td><td>" + getStat().getDEX()
					+ "</td><td>CON</td><td>" + getStat().getCON() + "</td></tr>");
			html1.append("<tr><td>INT</td><td>" + getINT() + "</td><td>WIT</td><td>" + getStat().getWIT()
					+ "</td><td>MEN</td><td>" + getStat().getMEN() + "</td></tr>");
			html1.append("</table>");
			
			html1.append("<br><center><font color=\"LEVEL\">[Drop Info]</font></center>");
			html1.append("<br>Rates legend: <font color=\"ff0000\">50%+</font> <font color=\"00ff00\">30%+</font> <font color=\"0000ff\">less than 30%</font>");
			html1.append("<table border=0 width=\"100%\">");
			
			if (getTemplate().getDropData() != null)
			{
				for (L2DropCategory cat : getTemplate().getDropData())
				{
					for (L2DropData drop : cat.getAllDrops())
					{
						final L2Item item = ItemTable.getInstance().getTemplate(drop.getItemId());
						if (item == null)
							continue;
						
						String name = item.getName();
						
						if (drop.getChance() >= 500000)
							html1.append("<tr><td><font color=\"ff0000\">" + name + "</font></td><td>"
									+ (drop.isQuestDrop() ? "Quest" : (cat.isSweep() ? "Sweep" : "Drop"))
									+ "</td></tr>");
						else if (drop.getChance() >= 300000)
							html1.append("<tr><td><font color=\"00ff00\">" + name + "</font></td><td>"
									+ (drop.isQuestDrop() ? "Quest" : (cat.isSweep() ? "Sweep" : "Drop"))
									+ "</td></tr>");
						else
							html1.append("<tr><td><font color=\"0000ff\">" + name + "</font></td><td>"
									+ (drop.isQuestDrop() ? "Quest" : (cat.isSweep() ? "Sweep" : "Drop"))
									+ "</td></tr>");
					}
				}
			}
			
			html1.append("</table>");
			html1.append("</body></html>");
			
			html.setHtml(html1.toString());
			player.sendPacket(html);
		}
		
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/** Return the castle this NPC belongs to, or null when this world has no castle for it. */
	public final Castle getCastle()
	{
		if (!_castle.resolved())
		{
			Town town = TownManager.getInstance().getTown(this);
			boolean inTown = (town != null);
			Castle townCastle = inTown ? town.getCastle() : null;
			Castle closest = null;
			if (!inTown || townCastle == null)
				closest = CastleManager.getInstance().getClosestCastle(this);
			_castle.resolve(inTown, townCastle, closest);
		}
		
		return CastleManager.getInstance().getCastleById(_castle.index());
	}
	
	/** Return the L2Fort this L2Npc belongs to. */
	public final Fort getFort()
	{
		// Get Fort this NPC belongs to (excluding L2Attackable)
		if (_fortIndex < 0)
		{
			Fort fort = FortManager.getInstance().getFort(getX(), getY(), getZ());
			if (fort != null)
			{
				_fortIndex = FortManager.getInstance().getFortIndex(fort.getFortId());
			}
			if (_fortIndex < 0)
			{
				_fortIndex = FortManager.getInstance().findNearestFortIndex(this);
			}
		}
		if (_fortIndex < 0)
		{
			return null;
		}
		return FortManager.getInstance().getForts().get(_fortIndex);
	}
	
	public final boolean getIsInTown()
	{
		if (!_castle.resolved())
			getCastle();
		return _castle.inTown();
	}
	
	/**
	 * Open a quest or chat window on client with the text of the L2Npc in function of the command.<BR><BR>
	 *
	 * <B><U> Example of use </U> :</B><BR><BR>
	 * <li> Client packet : RequestBypassToServer</li><BR><BR>
	 *
	 * @param command The command string received from client
	 *
	 */
	public void onBypassFeedback(L2Player player, String command)
	{
		//if (canInteract(player))
		{
			if (isBusy() && getBusyMessage().length() > 0)
			{
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/npcbusy.htm");
				html.replace("%busymessage%", getBusyMessage());
				html.replace("%npcname%", getName());
				html.replace("%playername%", player.getName());
				player.sendPacket(html);
			}
			else if (Config.ALLOW_WYVERN_UPGRADER && command.startsWith("upgrade") && player.getClan() != null
					&& player.getClan().getHasCastle() != 0)
			{
				String type = command.substring(8);
				
				if (type.equalsIgnoreCase("wyvern"))
				{
					L2NpcTemplate wind = NpcTable.getInstance().getTemplate(PetDataTable.STRIDER_WIND_ID);
					L2NpcTemplate star = NpcTable.getInstance().getTemplate(PetDataTable.STRIDER_STAR_ID);
					L2NpcTemplate twilight = NpcTable.getInstance().getTemplate(PetDataTable.STRIDER_TWILIGHT_ID);
					
					L2Summon summon = player.getPet();
					L2NpcTemplate myPet = summon.getTemplate();
					
					if ((myPet.equals(wind) || myPet.equals(star) || myPet.equals(twilight))
							&& player.getAdena() >= 20000000
							&& (player.getInventory().getItemByObjectId(summon.getControlItemId()) != null))
					{
						int exchangeItem = PetDataTable.WYVERN_ID;
						if (!player.reduceAdena("PetUpdate", 20000000, this, true))
							return;
						player.getInventory().destroyItem("PetUpdate", summon.getControlItemId(), 1, player, this);
						
						L2NpcTemplate template1 = NpcTable.getInstance().getTemplate(20629);
						try
						{
							L2Spawn spawn = new L2Spawn(template1);
							
							spawn.setLocx(getX() + 20);
							spawn.setLocy(getY() + 20);
							spawn.setLocz(getZ());
							spawn.setAmount(1);
							spawn.setHeading(player.getHeading());
							spawn.setRespawnDelay(1);
							
							SpawnTable.getInstance().addNewSpawn(spawn, false);
							
							spawn.init();
							spawn.getLastSpawn().getStatus().setCurrentHp(getMaxHp());
							spawn.getLastSpawn().setName("baal");
							spawn.getLastSpawn().setTitle("hell's god");
							//spawn.getLastSpawn().isEventMob = true;
							spawn.getLastSpawn().isAggressive();
							spawn.getLastSpawn().decayMe();
							spawn.getLastSpawn().spawnMe(spawn.getLastSpawn().getX(), spawn.getLastSpawn().getY(),
									spawn.getLastSpawn().getZ());
							
							int level = summon.getLevel();
							int chance = (level - 54) * 10;
							spawn.getLastSpawn().broadcastPacket(
									new MagicSkillUse(spawn.getLastSpawn(), spawn.getLastSpawn(), 1034, 1, 1, 1));
							spawn.getLastSpawn().broadcastPacket(
									new MagicSkillUse(spawn.getLastSpawn(), summon, 1034, 1, 1, 1));
							
							if (Rnd.nextInt(100) < chance)
							{
								ThreadPoolManager.getInstance().scheduleGeneral(
										new DestroyTemporalSummon(summon, player), 6000);
								player.addItem("PetUpdate", exchangeItem, 1, player, true, true);
								
								NpcHtmlMessage adminReply = new NpcHtmlMessage(getObjectId());
								StringBuilder replyMSG = new StringBuilder("<html><body>");
								replyMSG.append("Congratulations, the evolution suceeded.");
								replyMSG.append("</body></html>");
								adminReply.setHtml(replyMSG.toString());
								player.sendPacket(adminReply);
							}
							else
							{
								summon.reduceCurrentHp(summon.getStatus().getCurrentHp(), player);
							}
							ThreadPoolManager.getInstance().scheduleGeneral(new DestroyTemporalNPC(spawn), 15000);
							
							ItemList il = new ItemList(player, true);
							player.sendPacket(il);
						}
						catch (Exception e)
						{
							_log.error(e.getMessage(), e);
						}
					}
					else
					{
						NpcHtmlMessage adminReply = new NpcHtmlMessage(getObjectId());
						StringBuilder replyMSG = new StringBuilder("<html><body>");
						
						replyMSG.append("You will need 20.000.000 and have the pet summoned for the ceremony ...");
						replyMSG.append("</body></html>");
						
						adminReply.setHtml(replyMSG.toString());
						player.sendPacket(adminReply);
					}
				}
				else if (Config.ALT_CLASS_MASTER_STRIDER_UPDATE && type.equalsIgnoreCase("strider"))
				{
					L2NpcTemplate wind = NpcTable.getInstance().getTemplate(PetDataTable.HATCHLING_WIND_ID);
					L2NpcTemplate star = NpcTable.getInstance().getTemplate(PetDataTable.HATCHLING_STAR_ID);
					L2NpcTemplate twilight = NpcTable.getInstance().getTemplate(PetDataTable.HATCHLING_TWILIGHT_ID);
					
					L2Summon summon = player.getPet();
					L2NpcTemplate myPet = summon.getTemplate();
					
					if ((myPet.equals(wind) || myPet.equals(star) || myPet.equals(twilight))
							&& player.getAdena() >= 6000000
							&& (player.getInventory().getItemByObjectId(summon.getControlItemId()) != null))
					{
						int exchangeItem = PetDataTable.STRIDER_TWILIGHT_ID;
						if (myPet.equals(wind))
							exchangeItem = PetDataTable.STRIDER_WIND_ID;
						else if (myPet.equals(star))
							exchangeItem = PetDataTable.STRIDER_STAR_ID;
						
						if (!player.reduceAdena("PetUpdate", 6000000, this, true))
							return;
						player.getInventory().destroyItem("PetUpdate", summon.getControlItemId(), 1, player, this);
						
						L2NpcTemplate template1 = NpcTable.getInstance().getTemplate(689);
						try
						{
							L2Spawn spawn = new L2Spawn(template1);
							
							spawn.setLocx(getX() + 20);
							spawn.setLocy(getY() + 20);
							spawn.setLocz(getZ());
							spawn.setAmount(1);
							spawn.setHeading(player.getHeading());
							spawn.setRespawnDelay(1);
							
							SpawnTable.getInstance().addNewSpawn(spawn, false);
							
							spawn.init();
							spawn.getLastSpawn().getStatus().setCurrentHp(getMaxHp());
							spawn.getLastSpawn().setName("mercebu");
							spawn.getLastSpawn().setTitle("baal's son");
							
							spawn.getLastSpawn().isAggressive();
							spawn.getLastSpawn().decayMe();
							spawn.getLastSpawn().spawnMe(spawn.getLastSpawn().getX(), spawn.getLastSpawn().getY(),
									spawn.getLastSpawn().getZ());
							
							spawn.getLastSpawn().broadcastPacket(
									new MagicSkillUse(spawn.getLastSpawn(), summon, 297, 1, 1, 1));
							
							int level = summon.getLevel();
							int chance = (level - 34) * 10;
							spawn.getLastSpawn().broadcastPacket(
									new MagicSkillUse(spawn.getLastSpawn(), spawn.getLastSpawn(), 1034, 1, 1, 1));
							spawn.getLastSpawn().broadcastPacket(
									new MagicSkillUse(spawn.getLastSpawn(), summon, 1034, 1, 1, 1));
							
							if (Rnd.nextInt(100) < chance)
							{
								ThreadPoolManager.getInstance().scheduleGeneral(
										new DestroyTemporalSummon(summon, player), 6000);
								player.addItem("PetUpdate", exchangeItem, 1, player, true, true);
								NpcHtmlMessage adminReply = new NpcHtmlMessage(getObjectId());
								StringBuilder replyMSG = new StringBuilder("<html><body>");
								
								replyMSG.append("Congratulations, the evolution suceeded.");
								replyMSG.append("</body></html>");
								
								adminReply.setHtml(replyMSG.toString());
								player.sendPacket(adminReply);
							}
							else
							{
								summon.reduceCurrentHp(summon.getStatus().getCurrentHp(), player);
							}
							
							ThreadPoolManager.getInstance().scheduleGeneral(new DestroyTemporalNPC(spawn), 15000);
							ItemList il = new ItemList(player, true);
							player.sendPacket(il);
						}
						catch (Exception e)
						{
							_log.error(e.getMessage(), e);
						}
					}
					else
					{
						NpcHtmlMessage adminReply = new NpcHtmlMessage(getObjectId());
						StringBuilder replyMSG = new StringBuilder("<html><body>");
						
						replyMSG.append("You will need 6.000.000 and have the pet summoned for the ceremony ...");
						replyMSG.append("</body></html>");
						
						adminReply.setHtml(replyMSG.toString());
						player.sendPacket(adminReply);
					}
				}
			}
			else if (command.equalsIgnoreCase("TerritoryStatus"))
			{
				Castle castle = getCastle();
				if (castle == null)
				{
					player.sendPacket(ActionFailed.STATIC_PACKET);
				}
				else
				{
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					{
						if (castle.getOwnerId() > 0)
						{
							html.setFile("data/html/territorystatus.htm");
							L2Clan clan = ClanTable.getInstance().getClan(castle.getOwnerId());
							html.replace("%clanname%", clan.getName());
							html.replace("%clanleadername%", clan.getLeaderName());
						}
						else
						{
							html.setFile("data/html/territorynoclan.htm");
						}
					}
					html.replace("%castlename%", castle.getName());
					html.replace("%taxpercent%", "" + castle.getTaxPercent());
					html.replace("%objectId%", String.valueOf(getObjectId()));
					{
						if (castle.getCastleId() > 6)
						{
							html.replace("%territory%", "The Kingdom of Elmore");
						}
						else
						{
							html.replace("%territory%", "The Kingdom of Aden");
						}
					}
					player.sendPacket(html);
				}
			}
			else if (command.startsWith("Quest"))
			{
				String quest = "";
				try
				{
					quest = command.substring(5).trim();
				}
				catch (IndexOutOfBoundsException ioobe)
				{
				}
				
				if (quest.length() == 0)
					showQuestWindow(player);
				else
					showQuestWindow(player, quest);
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
			else if (command.startsWith("Link"))
			{
				String path = command.substring(5).trim();
				if (path.indexOf("..") != -1)
					return;
				String filename = "data/html/" + path;
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile(filename);
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
			}
			else if (command.startsWith("NobleTeleport"))
			{
				if (!player.isNoble())
				{
					String filename = "data/html/teleporter/nobleteleporter-no.htm";
					NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
					html.setFile(filename);
					html.replace("%objectId%", String.valueOf(getObjectId()));
					html.replace("%npcname%", getName());
					player.sendPacket(html);
					return;
				}
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
			else if (command.startsWith("Loto"))
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
				if (val == 0)
				{
					// new loto ticket
					for (int i = 0; i < 5; i++)
						player.setLoto(i, 0);
				}
				showLotoWindow(player, val);
			}
			else if (command.startsWith("CPRecovery"))
			{
				makeCPRecovery(player);
			}
			else if (command.startsWith("SupportMagic"))
			{
				makeSupportMagic(player, command);
			}
			else if (command.startsWith("GiveBlessing"))
			{
				giveBlessingSupport(player);
			}
			else if (command.startsWith("multisell"))
			{
				int listId = Integer.parseInt(command.substring(9).trim());
				MultisellTable.getInstance().separateAndSend(listId, player, getNpcId(), false,
						NpcCastle.multisellRate(getCastle()));
			}
			else if (command.startsWith("exc_multisell"))
			{
				int listId = Integer.parseInt(command.substring(13).trim());
				MultisellTable.getInstance().separateAndSend(listId, player, getNpcId(), true,
						NpcCastle.multisellRate(getCastle()));
			}
			else if (command.startsWith("Augment"))
			{
				int cmdChoice = Integer.parseInt(command.substring(8, 9).trim());
				switch (cmdChoice)
				{
					case 1:
						player.sendPacket(SystemMessageId.SELECT_THE_ITEM_TO_BE_AUGMENTED);
						player.sendPacket(new ExShowVariationMakeWindow());
						break;
					case 2:
						player.sendPacket(SystemMessageId.SELECT_THE_ITEM_FROM_WHICH_YOU_WISH_TO_REMOVE_AUGMENTATION);
						player.sendPacket(new ExShowVariationCancelWindow());
						break;
				}
			}
			else if (command.startsWith("npcfind_byid"))
			{
				try
				{
					L2Spawn spawn =
							SpawnTable.getInstance().getTemplate(Integer.parseInt(command.substring(12).trim()));
					if (spawn != null)
					{
						player.sendPacket(new RadarControl(2, 2, spawn.getLocx(), spawn.getLocy(), spawn.getLocz()));
						player.sendPacket(new RadarControl(0, 1, spawn.getLocx(), spawn.getLocy(), spawn.getLocz()));
					}
					else
					{
						player.sendMessage("Boss not implemented yet");
					}
				}
				catch (NumberFormatException nfe)
				{
					player.sendMessage("Wrong command parameters");
				}
			}
			else if (command.startsWith("EnterRift"))
			{
				try
				{
					Byte b1 = Byte.parseByte(command.substring(10)); // Selected Area: Recruit, Soldier etc
					DimensionalRiftManager.getInstance().start(player, b1, this);
				}
				catch (Exception e)
				{
					_log.warn("", e);
				}
			}
			else if (command.startsWith("ChangeRiftRoom"))
			{
				if (player.isInParty() && player.getParty().isInDimensionalRift())
				{
					player.getParty().getDimensionalRift().manualTeleport(player, this);
				}
				else
				{
					DimensionalRiftManager.getInstance().handleCheat(player, this);
				}
			}
			else if (command.startsWith("ExitRift"))
			{
				if (player.isInParty() && player.getParty().isInDimensionalRift())
				{
					player.getParty().getDimensionalRift().manualExitRift(player, this);
				}
				else
				{
					DimensionalRiftManager.getInstance().handleCheat(player, this);
				}
			}
			else if (command.equals("questlist"))
			{
				player.sendPacket(ExShowQuestInfo.PACKET);
			}
			else if (command.startsWith("MakeBuffs"))
			{
				makeBuffs(player, command.substring(9).trim());
			}
			else if (command.equalsIgnoreCase("exchange"))
			{
				NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile("data/html/merchant/exchange.htm");
				html.replace("%objectId%", String.valueOf(getObjectId()));
				player.sendPacket(html);
			}
			else if (command.equals("ReleaseAttribute"))
			{
				player.sendPacket(new ExShowBaseAttributeCancelWindow(player));
			}
			// [L2J_JP ADD START]
			else if (command.startsWith("open_gate"))
			{
				final DoorTable _doorTable = DoorTable.getInstance();
				int doorId;
				
				StringTokenizer st = new StringTokenizer(command.substring(10), ", ");
				
				while (st.hasMoreTokens())
				{
					doorId = Integer.parseInt(st.nextToken());
					
					if (_doorTable.getDoor(doorId) != null)
					{
						_doorTable.getDoor(doorId).openMe();
						_doorTable.getDoor(doorId).onOpen();
					}
					else
					{
						_log.warn("Door Id does not exist.(" + doorId + ")");
					}
				}
				return;
			}
			else if (command.equalsIgnoreCase("wake_baium"))
			{
				setTarget(player);
				BaiumManager.getInstance().spawnBaium(this);
			}
			// [L2J_JP ADD END]
			else if (command.startsWith("remove_dp"))
			{
				int cmdChoice = Integer.parseInt(command.substring(10, 11).trim());
				int[] pen_clear_price = { 3600, 8640, 25200, 50400, 86400, 144000, 144000, 144000 };
				int price = pen_clear_price[player.getExpertiseIndex()] * (int)Config.RATE_DROP_ADENA;
				switch (cmdChoice)
				{
					case 1:
						String filename = "data/html/default/30981-1.htm";
						NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
						html.setFile(filename);
						html.replace("%objectId%", String.valueOf(getObjectId()));
						html.replace("%dp_price%", String.valueOf(price));
						player.sendPacket(html);
						break;
					case 2:
						NpcHtmlMessage Reply = new NpcHtmlMessage(getObjectId());
						StringBuilder replyMSG = new StringBuilder("<html><body>Black Judge:<br>");
						
						if (player.getDeathPenaltyBuffLevel() > 0)
						{
							if (player.getAdena() >= price)
							{
								if (!player.reduceAdena("DeathPenality", price, this, true))
									return;
								player.setDeathPenaltyBuffLevel(player.getDeathPenaltyBuffLevel() - 1);
								player.sendPacket(SystemMessageId.DEATH_PENALTY_LIFTED);
								player.sendEtcStatusUpdate();
								return;
							}
							
							replyMSG.append("The wound you have received from death's touch is too deep to be healed for the money you have to give me. Find more money if you wish death's mark to be fully removed from you.");
						}
						else
						{
							replyMSG.append("You have no more death wounds that require healing.<br>");
							replyMSG.append("Go forth and fight, both for this world and your own glory.");
						}
						
						replyMSG.append("</body></html>");
						Reply.setHtml(replyMSG.toString());
						player.sendPacket(Reply);
						break;
				}
			}
			else if (command.startsWith("arena_info"))
			{
				NpcHtmlMessage htm = new NpcHtmlMessage(getObjectId());
				htm.setHtml(ArenaManager.getInstance().showHtm(player.getObjectId()));
				player.sendPacket(htm);
			}
			else if (command.startsWith("fisherman_info"))
			{
				NpcHtmlMessage htm = new NpcHtmlMessage(getObjectId());
				htm.setHtml(FishermanManager.getInstance().showHtm(player.getObjectId()));
				player.sendPacket(htm);
			}
			
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Cast buffs on player, this function ignore target type
	 * only buff effects are aplied to player
	 *
	 * @param player Target player
	 * @param buffTemplate Name of buff template
	 */
	public void makeBuffs(L2Player player, String buffTemplate)
	{
		int _templateId = 0;
		
		try
		{
			_templateId = Integer.parseInt(buffTemplate);
		}
		catch (NumberFormatException e)
		{
			_templateId = BuffTemplateTable.getInstance().getTemplateIdByName(buffTemplate);
		}
		
		if (_templateId > 0)
			makeBuffs(player, _templateId, false);
	}
	
	/**
	 * Cast buffs on player/servitor, this function ignore target type
	 * only buff effects are aplied to player/servitor
	 *
	 * @param player Target player/servitor owner
	 * @param _templateId Id of buff template
	 */
	public void makeBuffs(L2Player player, int _templateId, boolean servitor)
	{
		if (player == null)
			return;
		
		List<L2BuffTemplate> _templateBuffs = BuffTemplateTable.getInstance().getBuffTemplate(_templateId).getBuffs();
		
		if (_templateBuffs.size() == 0)
			return;
		
		L2Playable receiver = (servitor ? player.getPet() : player);
		setTarget(receiver);
		
		int _priceTotal = 0;
		//TODO: add faction points support (evil33t, im waiting for you ^^ )
		//TODO: add more options for player condition, like: pk, ssq winner/looser...etc
		//TODO: add ancient adena price
		//TODO: add autobuff tasks for npc (with options range,ignorePrice,showCast)
		//TODO: add buff template striction to specified npc ids, merchants like
		for (L2BuffTemplate _buff : _templateBuffs)
		{
			if (_buff.checkPlayer(player) && _buff.checkPrice(player))
			{
				if (player.getInventory().getAdena() >= (_priceTotal + _buff.getAdenaPrice()))
				{
					_priceTotal += _buff.getAdenaPrice();
					
					if (_buff.forceCast() || receiver.getFirstEffect(_buff.getSkill()) == null)
					{
						// regeneration ^^
						getStatus().setCurrentHpMp(getMaxHp(), getMaxMp());
						
						// yes, its not for all skills right, but atleast player will know
						// for what he paid =)
						if (_templateId != 1 && !servitor)
						{
							SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FEEL_S1_EFFECT);
							sm.addSkillName(_buff.getSkill().getId());
							player.sendPacket(sm);
						}
						
						// hack for newbie summons
						if (_buff.getSkill().getSkillType() == L2SkillType.SUMMON)
						{
							player.doSimultaneousCast(_buff.getSkill());
						}
						else
						{ // Ignore skill cast time, using 100ms for NPC buffer's animation
							MagicSkillUse msu =
									new MagicSkillUse(this, receiver, _buff.getSkill().getId(), _buff.getSkill()
											.getLevel(), 100, 0);
							broadcastPacket(msu);
							_buff.getSkill().getEffects(this, receiver);
						}
					}
				}
			}
		}
		player.reduceAdena("NpcBuffer", _priceTotal, player.getLastFolkNPC(), true);
	}
	
	/**
	 * Return null (regular NPCs don't have weapons instancies).<BR><BR>
	 */
	@Override
	public L2ItemInstance getActiveWeaponInstance()
	{
		// Regular NPCs dont have weapons instancies
		return null;
	}
	
	/**
	 * Return the weapon item equiped in the right hand of the L2Npc or null.<BR><BR>
	 */
	@Override
	public L2Weapon getActiveWeaponItem()
	{
		// Get the weapon identifier equiped in the right hand of the L2Npc
		int weaponId = getTemplate().getRhand();
		
		if (weaponId < 1)
			return null;
		
		// Get the weapon item equiped in the right hand of the L2Npc
		L2Item item = ItemTable.getInstance().getTemplate(getTemplate().getRhand());
		
		if (!(item instanceof L2Weapon))
			return null;
		
		return (L2Weapon)item;
	}
	
	/**
	 * Return null (regular NPCs don't have weapons instancies).<BR><BR>
	 */
	@Override
	public L2ItemInstance getSecondaryWeaponInstance()
	{
		// Regular NPCs dont have weapons instancies
		return null;
	}
	
	/**
	 * Return the weapon item equiped in the left hand of the L2Npc or null.<BR><BR>
	 */
	@Override
	public L2Weapon getSecondaryWeaponItem()
	{
		// Get the weapon identifier equiped in the right hand of the L2Npc
		int weaponId = getTemplate().getLhand();
		
		if (weaponId < 1)
			return null;
		
		// Get the weapon item equiped in the right hand of the L2Npc
		L2Item item = ItemTable.getInstance().getTemplate(getTemplate().getLhand());
		
		if (!(item instanceof L2Weapon))
			return null;
		
		return (L2Weapon)item;
	}
	
	/**
	 * Send a Server->Client packet NpcHtmlMessage to the L2Player in order to display the message of the L2Npc.<BR><BR>
	 *
	 * @param player The L2Player who talks with the L2Npc
	 * @param content The text of the L2NpcMessage
	 *
	 */
	public void insertObjectIdAndShowChatWindow(L2Player player, String content)
	{
		// Send a Server->Client packet NpcHtmlMessage to the L2Player in order to display the message of the L2Npc
		content = content.replaceAll("%objectId%", String.valueOf(getObjectId()));
		NpcHtmlMessage npcReply = new NpcHtmlMessage(getObjectId());
		npcReply.setHtml(content);
		player.sendPacket(npcReply);
	}
	
	/**
	 * Return the pathfile of the selected HTML file in function of the npcId and of the page number.<BR><BR>
	 *
	 * <B><U> Format of the pathfile </U> :</B><BR><BR>
	 * <li> if the file exists on the server (page number = 0) : <B>data/html/default/12006.htm</B> (npcId-page number)</li>
	 * <li> if the file exists on the server (page number > 0) : <B>data/html/default/12006-1.htm</B> (npcId-page number)</li>
	 * <li> if the file doesn't exist on the server : <B>data/html/npcdefault.htm</B> (message : "I have nothing to say to you")</li><BR><BR>
	 *
	 * <B><U> Overridden in </U> :</B><BR><BR>
	 * <li> L2GuardInstance : Set the pathfile to data/html/guard/12006-1.htm (npcId-page number)</li><BR><BR>
	 *
	 * @param npcId The Identifier of the L2Npc whose text must be display
	 * @param val The number of the page to display
	 *
	 */
	public String getHtmlPath(int npcId, int val)
	{
		String pom = String.valueOf(npcId);
		
		if (val != 0)
			pom += "-" + val;
		
		String temp = "data/html/default/" + pom + ".htm";
		
		if (HtmCache.getInstance().pathExists(temp))
			return temp;
		
		// If the file is not found, the standard message "I have nothing to say to you" is returned
		return "data/html/npcdefault.htm";
	}
	
	/**
	 * Open a choose quest window on client with all quests available of the L2Npc.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a Server->Client NpcHtmlMessage containing the text of the L2Npc to the L2Player </li><BR><BR>
	 *
	 * @param player The L2Player that talk with the L2Npc
	 * @param quests The table containing quests of the L2Npc
	 *
	 */
	public void showQuestChooseWindow(L2Player player, Quest[] quests)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("<html><body>");
		for (Quest q : quests)
		{
			sb.append("<a action=\"bypass -h npc_").append(getObjectId()).append("_Quest ").append(q.getName())
					.append("\"> [").append(q.getDescr());
			
			QuestState qs = player.getQuestState(q.getScriptName());
			if (qs != null)
			{
				if (qs.getState() == State.STARTED && qs.getInt("cond") > 0)
					sb.append(" (In Progress)");
				else if (qs.getState() == State.COMPLETED)
					sb.append(" (Done)");
			}
			sb.append("]</a><br>");
		}
		
		sb.append("</body></html>");
		
		// Send a Server->Client packet NpcHtmlMessage to the L2Player in order to display the message of the L2Npc
		insertObjectIdAndShowChatWindow(player, sb.toString());
	}
	
	/**
	 * Open a quest window on client with the text of the L2Npc.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the text of the quest state in the folder data/scripts/quests/questId/stateId.htm </li>
	 * <li>Send a Server->Client NpcHtmlMessage containing the text of the L2Npc to the L2Player </li>
	 * <li>Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet </li><BR><BR>
	 *
	 * @param player The L2Player that talk with the L2Npc
	 * @param questId The Identifier of the quest to display the message
	 *
	 */
	public void showQuestWindow(L2Player player, String questId)
	{
		String content = null;
		
		Quest q = QuestManager.getInstance().getQuest(questId);
		
		// Get the state of the selected quest
		QuestState qs = player.getQuestState(questId);
		
		if (q == null)
		{
			// No quests found
			content =
					"<html><body>You are either not on a quest that involves this NPC, or you don't meet this NPC's minimum quest requirements.</body></html>";
		}
		else
		{
			if ((q.getQuestIntId() >= 1 && q.getQuestIntId() < 20000)
					&& (player.getWeightPenalty() >= 3 || player.getInventoryLimit() * 0.8 <= player.getInventory()
							.getSize()))
			{
				player.sendPacket(SystemMessageId.INVENTORY_LESS_THAN_80_PERCENT);
				return;
			}
			
			if (qs == null)
			{
				if (q.getQuestIntId() >= 1 && q.getQuestIntId() < 20000)
				{
					Quest[] questList = player.getAllActiveQuests();
					if (questList.length >= 25) // if too many ongoing quests, don't show window and send message
					{
						player.sendPacket(SystemMessageId.TOO_MANY_QUESTS);
						return;
					}
				}
				// Check for start point
				Quest[] qlst = getTemplate().getEventQuests(Quest.QuestEventType.QUEST_START);
				
				if (qlst != null && qlst.length > 0)
				{
					for (Quest temp : qlst)
					{
						if (temp == q)
						{
							qs = q.newQuestState(player);
							break;
						}
					}
				}
			}
		}
		
		if (qs != null)
		{
			// If the quest is alreday started, no need to show a window
			if (!qs.getQuest().notifyTalk(this, qs))
				return;
			
			questId = qs.getQuest().getName();
			String stateId = State.getStateName(qs.getState());
			String path = "data/scripts/quests/" + questId + "/" + stateId + ".htm";
			content = HtmCache.getInstance().getHtm(path);
			
			if (_log.isDebugEnabled())
			{
				if (content != null)
				{
					_log.debug("Showing quest window for quest " + questId + " html path: " + path);
				}
				else
				{
					_log.debug("File not exists for quest " + questId + " html path: " + path);
				}
			}
		}
		
		// Send a Server->Client packet NpcHtmlMessage to the L2Player in order to display the message of the L2Npc
		if (content != null)
			insertObjectIdAndShowChatWindow(player, content);
		
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Collect awaiting quests/start points and display a QuestChooseWindow (if several available) or QuestWindow.<BR><BR>
	 *
	 * @param player The L2Player that talk with the L2Npc
	 *
	 */
	public void showQuestWindow(L2Player player)
	{
		// Collect awaiting quests and start points
		List<Quest> options = new ArrayList<Quest>();
		
		QuestState[] awaits = player.getQuestsForTalk(getTemplate().getNpcId());
		Quest[] starts = getTemplate().getEventQuests(Quest.QuestEventType.QUEST_START);
		
		// Quests are limited between 1 and 999 because those are the quests that are supported by the client.
		// By limitting them there, we are allowed to create custom quests at higher IDs without interfering
		if (awaits != null)
		{
			for (QuestState x : awaits)
			{
				if (!options.contains(x.getQuest()))
					if ((x.getQuest().getQuestIntId() > 0) && (x.getQuest().getQuestIntId() < 20000))
						options.add(x.getQuest());
			}
		}
		
		if (starts != null)
		{
			for (Quest x : starts)
			{
				if (!options.contains(x))
					if ((x.getQuestIntId() > 0) && (x.getQuestIntId() < 20000))
						options.add(x);
			}
		}
		
		// Display a QuestChooseWindow (if several quests are available) or QuestWindow
		if (options.size() > 1)
		{
			showQuestChooseWindow(player, options.toArray(new Quest[options.size()]));
		}
		else if (options.size() == 1)
		{
			showQuestWindow(player, options.get(0).getName());
		}
		else
		{
			showQuestWindow(player, "");
		}
	}
	
	/**
	 * Open a Loto window on client with the text of the L2Npc.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the text of the selected HTML file in function of the npcId and of the page number </li>
	 * <li>Send a Server->Client NpcHtmlMessage containing the text of the L2Npc to the L2Player </li>
	 * <li>Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet </li><BR>
	 *
	 * @param player The L2Player that talk with the L2Npc
	 * @param val The number of the page of the L2Npc to display
	 *
	 */
	/**
	 * Open a Loto window on client with the text of the L2Npc.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the text of the selected HTML file in function of the npcId and of the page number </li>
	 * <li>Send a Server->Client NpcHtmlMessage containing the text of the L2Npc to the L2Player </li>
	 * <li>Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet </li><BR>
	 *
	 * @param player The L2Player that talk with the L2Npc
	 * @param val The number of the page of the L2Npc to display
	 *
	 */
	// -1 - lottery instructions
	// 0 - first buy lottery ticket window
	// 1-20 - buttons
	// 21 - second buy lottery ticket window
	// 22 - selected ticket with 5 numbers
	// 23 - current lottery jackpot
	// 24 - Previous winning numbers/Prize claim
	// >24 - check lottery ticket by item object id
	public void showLotoWindow(L2Player player, int val)
	{
		int npcId = getTemplate().getNpcId();
		String filename;
		SystemMessage sm;
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		
		if (val == 0) // 0 - first buy lottery ticket window
		{
			filename = (getHtmlPath(npcId, 1));
			html.setFile(filename);
		}
		else if (val >= 1 && val <= 21) // 1-20 - buttons, 21 - second buy lottery ticket window
		{
			if (!Lottery.getInstance().isStarted())
			{
				//tickets can't be sold
				player.sendPacket(SystemMessageId.NO_LOTTERY_TICKETS_CURRENT_SOLD);
				return;
			}
			if (!Lottery.getInstance().isSellableTickets())
			{
				//tickets can't be sold
				player.sendPacket(SystemMessageId.NO_LOTTERY_TICKETS_AVAILABLE);
				return;
			}
			
			filename = (getHtmlPath(npcId, 5));
			html.setFile(filename);
			
			int count = 0;
			int found = 0;
			// Counting buttons and unsetting button if found
			for (int i = 0; i < 5; i++)
			{
				if (player.getLoto(i) == val)
				{
					//unsetting button
					player.setLoto(i, 0);
					found = 1;
				}
				else if (player.getLoto(i) > 0)
				{
					count++;
				}
			}
			
			// If not rearched limit 5 and not unseted value
			if (count < 5 && found == 0 && val <= 20)
				for (int i = 0; i < 5; i++)
					if (player.getLoto(i) == 0)
					{
						player.setLoto(i, val);
						break;
					}
			
			// Setting pusshed buttons
			count = 0;
			for (int i = 0; i < 5; i++)
				if (player.getLoto(i) > 0)
				{
					count++;
					String button = String.valueOf(player.getLoto(i));
					if (player.getLoto(i) < 10)
						button = "0" + button;
					String search = "fore=\"L2UI.lottoNum" + button + "\" back=\"L2UI.lottoNum" + button + "a_check\"";
					String replace = "fore=\"L2UI.lottoNum" + button + "a_check\" back=\"L2UI.lottoNum" + button + "\"";
					html.replace(search, replace);
				}
			
			if (count == 5)
			{
				String search = "0\">Return";
				String replace = "22\">The winner selected the numbers above.";
				html.replace(search, replace);
			}
		}
		else if (val == 22) //22 - Selected ticket with 5 numbers
		{
			if (!Lottery.getInstance().isStarted())
			{
				// Tickets can't be sold
				player.sendPacket(SystemMessageId.NO_LOTTERY_TICKETS_CURRENT_SOLD);
				return;
			}
			if (!Lottery.getInstance().isSellableTickets())
			{
				// Tickets can't be sold
				player.sendPacket(SystemMessageId.NO_LOTTERY_TICKETS_AVAILABLE);
				return;
			}
			
			long price = Config.ALT_LOTTERY_TICKET_PRICE;
			int lotonumber = Lottery.getInstance().getId();
			int enchant = 0;
			int type2 = 0;
			
			for (int i = 0; i < 5; i++)
			{
				if (player.getLoto(i) == 0)
					return;
				
				if (player.getLoto(i) < 17)
					enchant += L2Math.pow(2, player.getLoto(i) - 1);
				else
					type2 += L2Math.pow(2, player.getLoto(i) - 17);
			}
			if (player.getAdena() < price)
			{
				player.sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
				return;
			}
			if (!player.reduceAdena("Loto", price, this, true))
				return;
			Lottery.getInstance().increasePrize(price);
			
			sm = new SystemMessage(SystemMessageId.ACQUIRED_S1_S2);
			sm.addItemNumber(lotonumber);
			sm.addItemName(4442);
			player.sendPacket(sm);
			
			L2ItemInstance item = new L2ItemInstance(IdFactory.getInstance().getNextId(), 4442);
			item.setCount(1);
			item.setCustomType1(lotonumber);
			item.setEnchantLevel(enchant);
			item.setCustomType2(type2);
			player.getInventory().addItem("Loto", item, player, this);
			
			InventoryUpdate iu = new InventoryUpdate();
			iu.addItem(item);
			L2ItemInstance adenaupdate = player.getInventory().getItemByItemId(PlayerInventory.ADENA_ID);
			iu.addModifiedItem(adenaupdate);
			player.sendPacket(iu);
			
			filename = (getHtmlPath(npcId, 3));
			html.setFile(filename);
		}
		else if (val == 23) //23 - Current lottery jackpot
		{
			filename = (getHtmlPath(npcId, 3));
			html.setFile(filename);
		}
		else if (val == 24) // 24 - Previous winning numbers/Prize claim
		{
			filename = (getHtmlPath(npcId, 4));
			html.setFile(filename);
			
			int lotonumber = Lottery.getInstance().getId();
			String message = "";
			for (L2ItemInstance item : player.getInventory().getItems())
			{
				if (item == null)
					continue;
				if (item.getItemId() == 4442 && item.getCustomType1() < lotonumber)
				{
					message =
							message + "<a action=\"bypass -h npc_%objectId%_Loto " + item.getObjectId() + "\">"
									+ item.getCustomType1() + " Event Number ";
					int[] numbers = Lottery.getInstance().decodeNumbers(item.getEnchantLevel(), item.getCustomType2());
					for (int i = 0; i < 5; i++)
					{
						message += numbers[i] + " ";
					}
					long[] check = Lottery.getInstance().checkTicket(item);
					if (check[0] > 0)
					{
						switch ((int)check[0])
						{
							case 1:
								message += "- 1st Prize";
								break;
							case 2:
								message += "- 2nd Prize";
								break;
							case 3:
								message += "- 3th Prize";
								break;
							case 4:
								message += "- 4th Prize";
								break;
						}
						message += " " + check[1] + "a.";
					}
					message += "</a><br>";
				}
			}
			if (message.isEmpty())
			{
				message += "There is no winning lottery ticket...<br>";
			}
			html.replace("%result%", message);
		}
		else if (val > 24) // >24 - Check lottery ticket by item object id
		{
			int lotonumber = Lottery.getInstance().getId();
			L2ItemInstance item = player.getInventory().getItemByObjectId(val);
			if (item == null || item.getItemId() != 4442 || item.getCustomType1() >= lotonumber)
				return;
			long[] check = Lottery.getInstance().checkTicket(item);
			
			sm = new SystemMessage(SystemMessageId.S1_DISAPPEARED);
			sm.addItemName(4442);
			player.sendPacket(sm);
			
			long adena = check[1];
			if (adena > 0)
				player.addAdena("Loto", adena, this, true);
			player.destroyItem("Loto", item, this, false);
			return;
		}
		else if (val == -1) // -1 - Lottery Instrucions
		{
			filename = (getHtmlPath(npcId, 2));
			html.setFile(filename);
		}
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%race%", "" + Lottery.getInstance().getId());
		html.replace("%adena%", "" + Lottery.getInstance().getPrize());
		html.replace("%ticket_price%", "" + Config.ALT_LOTTERY_TICKET_PRICE);
		html.replace("%prize5%", "" + (Config.ALT_LOTTERY_5_NUMBER_RATE * 100));
		html.replace("%prize4%", "" + (Config.ALT_LOTTERY_4_NUMBER_RATE * 100));
		html.replace("%prize3%", "" + (Config.ALT_LOTTERY_3_NUMBER_RATE * 100));
		html.replace("%prize2%", "" + Config.ALT_LOTTERY_2_AND_1_NUMBER_PRIZE);
		html.replace("%enddate%", "" + DateFormat.getDateInstance().format(Lottery.getInstance().getEndDate()));
		player.sendPacket(html);
		
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	public void makeCPRecovery(L2Player player)
	{
		if (getNpcId() != 31225 && getNpcId() != 31226)
			return;
		
		if (!cwCheck(player))
		{
			player.sendMessage("Go away, you're not welcome here.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		int neededmoney = 100;
		if (!player.reduceAdena("RestoreCP", neededmoney, player.getLastFolkNPC(), true))
			return;
		
		L2Skill skill = SkillTable.getInstance().getInfo(4380, 1);
		if (skill != null)
		{
			setTarget(player);
			doCast(skill);
		}
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Add Newbie helper buffs to L2Player according to its level.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the range level in wich player must be to obtain buff </li>
	 * <li>If player level is out of range, display a message and return </li>
	 * <li>According to player level cast buff </li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> Newbie Helper Buff list is define in buff templates sql table as "SupportMagic"</B></FONT><BR><BR>
	 *
	 * @param player The L2Player that talk with the L2Npc
	 *
	 */
	public void makeSupportMagic(L2Player player, String cmd)
	{
		// Prevent a cursed weapon weilder of being buffed
		if (!cwCheck(player))
			return;
		
		int _newbieBuffsId = BuffTemplateTable.getInstance().getTemplateIdByName(cmd);
		
		if (_newbieBuffsId == 0)
			return;
		
		int _lowestLevel = BuffTemplateTable.getInstance().getLowestLevel(_newbieBuffsId);
		int _highestLevel = BuffTemplateTable.getInstance().getHighestLevel(_newbieBuffsId);
		
		// If the player is too high level, display a message and return
		if (player.getLevel() > _highestLevel)
		{
			String content =
					"<html><body>Newbie Guide:<br>Only a <font color=\"LEVEL\">novice character of level "
							+ _highestLevel
							+ " or less</font> can receive my support magic.<br>Your novice character is the first one that you created and raised in this world.</body></html>";
			insertObjectIdAndShowChatWindow(player, content);
			return;
		}
		
		// If the player is too low level, display a message and return
		if (player.getLevel() < _lowestLevel)
		{
			String content =
					"<html><body>Come back here when you have reached level " + _lowestLevel
							+ ". I will give you support magic then.</body></html>";
			insertObjectIdAndShowChatWindow(player, content);
			return;
		}
		
		// If buffs are for servitor, check it's presence
		boolean servitor = cmd.endsWith("vitor");
		if (servitor)
		{
			L2Summon pet = player.getPet();
			if (pet == null || pet instanceof L2PetInstance)
			{
				String content =
						"<html><body>Only servitors can receive this Support Magic. If you do not have a servitor, you cannot access these spells.</body></html>";
				insertObjectIdAndShowChatWindow(player, content);
				return;
			}
		}
		
		makeBuffs(player, _newbieBuffsId, servitor);
	}
	
	public void giveBlessingSupport(L2Player player)
	{
		if (player == null)
			return;
		
		// Blessing of protection - author kerberos_20. Used codes from Rayan - L2Emu project.
		// Prevent a cursed weapon weilder of being buffed - I think no need of that becouse karma check > 0
		// if (player.isCursedWeaponEquiped())
		//   return;
		
		int player_level = player.getLevel();
		// Select the player
		setTarget(player);
		// If the player is too high level, display a message and return
		if (player_level > 39 || player.getClassId().level() >= 2)
		{
			String content =
					"<html><body>Newbie Guide:<br>I'm sorry, but you are not eligible to receive the protection blessing.<br1>It can only be bestowed on <font color=\"LEVEL\">characters below level 39 who have not made a seccond transfer.</font></body></html>";
			insertObjectIdAndShowChatWindow(player, content);
			return;
		}
		L2Skill skill = SkillTable.getInstance().getInfo(5182, 1);
		if (skill != null)
			doCast(skill);
	}
	
	public void showChatWindow(L2Player player)
	{
		showChatWindow(player, 0);
	}
	
	/**
	 * Returns true if html exists
	 * @param player
	 * @param type
	 * @return boolean
	 */
	private boolean showPkDenyChatWindow(L2Player player, String type)
	{
		String html = HtmCache.getInstance().getHtm("data/html/" + type + "/" + getNpcId() + "-pk.htm");
		
		if (html != null)
		{
			NpcHtmlMessage pkDenyMsg = new NpcHtmlMessage(getObjectId());
			pkDenyMsg.setHtml(html);
			player.sendPacket(pkDenyMsg);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Open a chat window on client with the text of the L2Npc.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the text of the selected HTML file in function of the npcId and of the page number </li>
	 * <li>Send a Server->Client NpcHtmlMessage containing the text of the L2Npc to the L2Player </li>
	 * <li>Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet </li><BR>
	 *
	 * @param player The L2Player that talk with the L2Npc
	 * @param val The number of the page of the L2Npc to display
	 *
	 */
	public void showChatWindow(L2Player player, int val)
	{
		if (!cwCheck(player)
				&& !(player.getTarget() instanceof L2ClanHallManagerInstance || player.getTarget() instanceof L2DoormenInstance))
		{
			player.setTarget(player);
			return;
		}
		if (player.getKarma() > 0)
		{
			if (!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && this instanceof L2MerchantInstance)
			{
				if (showPkDenyChatWindow(player, "merchant"))
					return;
			}
			else if (!Config.ALT_GAME_KARMA_PLAYER_CAN_USE_GK && this instanceof L2TeleporterInstance)
			{
				if (showPkDenyChatWindow(player, "teleporter"))
					return;
			}
			else if (!Config.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && this instanceof L2WarehouseInstance)
			{
				if (showPkDenyChatWindow(player, "warehouse"))
					return;
			}
			else if (!Config.ALT_GAME_KARMA_PLAYER_CAN_SHOP && this instanceof L2FishermanInstance)
			{
				if (showPkDenyChatWindow(player, "fisherman"))
					return;
			}
		}
		
		if (this instanceof L2AuctioneerInstance && val == 0)
			return;
		
		int npcId = getTemplate().getNpcId();
		
		/* For use with Seven Signs implementation */
		String filename = SevenSigns.SEVEN_SIGNS_HTML_PATH;
		int sealAvariceOwner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_AVARICE);
		int sealGnosisOwner = SevenSigns.getInstance().getSealOwner(SevenSigns.SEAL_GNOSIS);
		int playerCabal = SevenSigns.getInstance().getPlayerCabal(player);
		boolean isSealValidationPeriod = SevenSigns.getInstance().isSealValidationPeriod();
		int compWinner = SevenSigns.getInstance().getCabalHighestScore();
		
		switch (npcId)
		{
			case 31078:
			case 31079:
			case 31080:
			case 31081:
			case 31082: // Dawn Priests
			case 31083:
			case 31084:
			case 31168:
			case 31692:
			case 31694:
			case 31997:
				switch (playerCabal)
				{
					case SevenSigns.CABAL_DAWN:
						if (isSealValidationPeriod)
							if (compWinner == SevenSigns.CABAL_DAWN)
								if (compWinner != sealGnosisOwner)
									filename += "dawn_priest_2c.htm";
								else
									filename += "dawn_priest_2a.htm";
							else
								filename += "dawn_priest_2b.htm";
						else
							filename += "dawn_priest_1b.htm";
						break;
					case SevenSigns.CABAL_DUSK:
						if (isSealValidationPeriod)
							filename += "dawn_priest_3b.htm";
						else
							filename += "dawn_priest_3a.htm";
						break;
					default:
						if (isSealValidationPeriod)
							if (compWinner == SevenSigns.CABAL_DAWN)
								filename += "dawn_priest_4.htm";
							else
								filename += "dawn_priest_2b.htm";
						else
							filename += "dawn_priest_1a.htm";
						break;
				}
				break;
			case 31085:
			case 31086:
			case 31087:
			case 31088: // Dusk Priest
			case 31089:
			case 31090:
			case 31091:
			case 31169:
			case 31693:
			case 31695:
			case 31998:
				switch (playerCabal)
				{
					case SevenSigns.CABAL_DUSK:
						if (isSealValidationPeriod)
							if (compWinner == SevenSigns.CABAL_DUSK)
								if (compWinner != sealGnosisOwner)
									filename += "dusk_priest_2c.htm";
								else
									filename += "dusk_priest_2a.htm";
							else
								filename += "dusk_priest_2b.htm";
						else
							filename += "dusk_priest_1b.htm";
						break;
					case SevenSigns.CABAL_DAWN:
						if (isSealValidationPeriod)
							filename += "dusk_priest_3b.htm";
						else
							filename += "dusk_priest_3a.htm";
						break;
					default:
						if (isSealValidationPeriod)
							if (compWinner == SevenSigns.CABAL_DUSK)
								filename += "dusk_priest_4.htm";
							else
								filename += "dusk_priest_2b.htm";
						else
							filename += "dusk_priest_1a.htm";
						break;
				}
				break;
			case 31127: //
			case 31128: //
			case 31129: // Dawn Festival Guides
			case 31130: //
			case 31131: //
				filename += "festival/dawn_guide.htm";
				break;
			case 31137: //
			case 31138: //
			case 31139: // Dusk Festival Guides
			case 31140: //
			case 31141: //
				filename += "festival/dusk_guide.htm";
				break;
			case 31092: // Black Marketeer of Mammon
				filename += "blkmrkt_1.htm";
				break;
			case 31113: // Merchant of Mammon
				if (Config.ALT_STRICT_SEVENSIGNS)
				{
					switch (compWinner)
					{
						case SevenSigns.CABAL_DAWN:
							if (playerCabal != compWinner || playerCabal != sealAvariceOwner)
							{
								player.sendPacket(SystemMessageId.CAN_BE_USED_BY_DAWN);
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							}
							break;
						case SevenSigns.CABAL_DUSK:
							if (playerCabal != compWinner || playerCabal != sealAvariceOwner)
							{
								player.sendPacket(SystemMessageId.CAN_BE_USED_BY_DUSK);
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							}
							break;
					}
				}
				filename += "mammmerch_1.htm";
				break;
			case 31126: // Blacksmith of Mammon
				if (Config.ALT_STRICT_SEVENSIGNS)
				{
					switch (compWinner)
					{
						case SevenSigns.CABAL_DAWN:
							if (playerCabal != compWinner || playerCabal != sealGnosisOwner)
							{
								player.sendPacket(SystemMessageId.CAN_BE_USED_BY_DAWN);
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							}
							break;
						case SevenSigns.CABAL_DUSK:
							if (playerCabal != compWinner || playerCabal != sealGnosisOwner)
							{
								player.sendPacket(SystemMessageId.CAN_BE_USED_BY_DUSK);
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							}
							break;
					}
				}
				filename += "mammblack_1.htm";
				break;
			case 31132:
			case 31133:
			case 31134:
			case 31135:
			case 31136: // Festival Witches
			case 31142:
			case 31143:
			case 31144:
			case 31145:
			case 31146:
				filename += "festival/festival_witch.htm";
				break;
			case 31688:
				if (player.isNoble())
					filename = Olympiad.OLYMPIAD_HTML_PATH + "noble_main.htm";
				else
					filename = (getHtmlPath(npcId, val));
				break;
			case 31690:
			case 31769:
			case 31770:
			case 31771:
			case 31772:
				if (player.isHero())
					filename = Olympiad.OLYMPIAD_HTML_PATH + "hero_main.htm";
				else
					filename = (getHtmlPath(npcId, val));
				break;
			case 36402:
				if (player.olyBuff > 0)
					filename =
							Olympiad.OLYMPIAD_HTML_PATH
									+ (player.olyBuff == 5 ? "olympiad_buffs.htm" : "olympiad_5buffs.htm");
				else
					filename = Olympiad.OLYMPIAD_HTML_PATH + "olympiad_nobuffs.htm";
				break;
			default:
				if (npcId >= 31865 && npcId <= 31918)
				{
					if (val == 0)
						filename += "rift/GuardianOfBorder.htm";
					else
						filename += "rift/GuardianOfBorder-" + val + ".htm";
					break;
				}
				if ((npcId >= 31093 && npcId <= 31094) || (npcId >= 31172 && npcId <= 31201)
						|| (npcId >= 31239 && npcId <= 31254))
					return;
				// Get the text of the selected HTML file in function of the npcId and of the page number
				if (this instanceof L2TeleporterInstance && val == 1 && player.getLevel() < 40) // Players below level 40 have free teleport
				{
					filename = "data/html/teleporter/free/" + npcId + ".htm";
					if (!HtmCache.getInstance().pathExists(filename))
						filename = getHtmlPath(npcId, val);
				}
				else
					filename = (getHtmlPath(npcId, val));
				break;
		}
		
		// Send a Server->Client NpcHtmlMessage containing the text of the L2Npc to the L2Player
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		
		//String word = "npc-"+npcId+(val>0 ? "-"+val : "" )+"-dialog-append";
		
		if (this instanceof L2MerchantInstance)
			if (Config.LIST_PET_RENT_NPC.contains(npcId))
				html.replace("_Quest", "_RentPet\">Rent Pet</a><br><a action=\"bypass -h npc_%objectId%_Quest");
		
		html.replace("%objectId%", String.valueOf(getObjectId()));
		if (this instanceof L2FestivalGuideInstance)
		{
			html.replace("%festivalMins%", SevenSignsFestival.getInstance().getTimeToNextFestivalStr());
		}
		player.sendPacket(html);
		
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Open a chat window on client with the text specified by the given file name and path,<BR>
	 * relative to the datapack root.
	 * <BR><BR>
	 * Added by Tempy
	 * @param player The L2Player that talk with the L2Npc
	 * @param filename The filename that contains the text to send
	 *
	 */
	public void showChatWindow(L2Player player, String filename)
	{
		// Send a Server->Client NpcHtmlMessage containing the text of the L2Npc to the L2Player
		NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(filename);
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
		
		// Send a Server->Client ActionFailed to the L2Player in order to avoid that the client wait another packet
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Return the Exp Reward of this L2Npc contained in the L2NpcTemplate (modified by RATE_XP).<BR><BR>
	 */
	public int getExpReward()
	{
		double rateXp = getStat().calcStat(Stats.MAX_HP, 1, this, null);
		return (int)(getTemplate().getRewardExp() * rateXp * Config.RATE_XP);
	}
	
	/**
	 * Return the SP Reward of this L2Npc contained in the L2NpcTemplate (modified by RATE_SP).<BR><BR>
	 */
	public int getSpReward()
	{
		double rateSp = getStat().calcStat(Stats.MAX_HP, 1, this, null);
		return (int)(getTemplate().getRewardSp() * rateSp * Config.RATE_SP);
	}
	
	/**
	 * Kill the L2Npc (the corpse disappeared after 7 seconds).<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Create a DecayTask to remove the corpse of the L2Npc after 7 seconds </li>
	 * <li>Set target to null and cancel Attack or Cast </li>
	 * <li>Stop movement </li>
	 * <li>Stop HP/MP/CP Regeneration task </li>
	 * <li>Stop all active skills effects in progress on the L2Creature </li>
	 * <li>Send the Server->Client packet StatusUpdate with current HP and MP to all other L2Player to inform </li>
	 * <li>Notify L2Creature AI </li><BR><BR>
	 *
	 * <B><U> Overridden in </U> :</B><BR><BR>
	 * <li> L2Attackable </li><BR><BR>
	 *
	 * @param killer The L2Creature who killed it
	 *
	 */
	@Override
	public boolean doDie(L2Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		// Normally this wouldn't really be needed, but for those few exceptions,
		// We do need to reset the weapons back to the initial templated weapon.
		_currentLHandId = getTemplate().getLhand();
		_currentRHandId = getTemplate().getRhand();
		_currentCollisionHeight = getTemplate().getCollisionHeight();
		_currentCollisionRadius = getTemplate().getCollisionRadius();
		DecayTaskManager.getInstance().addDecayTask(this);
		return true;
	}
	
	/**
	 * Set the spawn of the L2Npc.<BR><BR>
	 *
	 * @param spawn The L2Spawn that manage the L2Npc
	 *
	 */
	public void setSpawn(L2Spawn spawn)
	{
		_spawn = spawn;
	}
	
	@Override
	public void onSpawn()
	{
		if (_inventory != null)
			_inventory.reset();
		
		super.onSpawn();
		
		setQuestFirstAttacker(null);
		setQuestAttackStatus(Quest.ATTACK_NOONE);
		if (getTemplate().getEventQuests(Quest.QuestEventType.ON_SPAWN) != null)
			for (Quest quest : getTemplate().getEventQuests(Quest.QuestEventType.ON_SPAWN))
				quest.notifySpawn(this);
	}
	
	/**
	 * Remove the L2Npc from the world and update its spawn object (for a complete removal use the deleteMe method).<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Remove the L2Npc from the world when the decay task is launched </li>
	 * <li>Decrease its spawn counter </li>
	 * <li>Manage Siege task (killFlag, killCT) </li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T REMOVE the object from _allObjects of L2World </B></FONT><BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND Server->Client packets to players</B></FONT><BR><BR>
	 *
	 * @see com.l2jfree.gameserver.gameobjects.L2Object#decayMe()
	 */
	@Override
	public void onDecay()
	{
		if (isDecayed())
			return;
		setDecayed(true);
		
		// Reset champion status if the thing is a mob
		setChampion(false);
		
		// Remove the L2Npc from the world when the decay task is launched
		super.onDecay();
		
		// Decrease its spawn counter
		if (_spawn != null)
		{
			_spawn.decreaseCount(this);
			
			// a script spawn that will not respawn would stay in the spawn table for the life of the process
			if (_spawn.isTemporary() && !_spawn.isRespawnable())
				SpawnTable.getInstance().deleteSpawn(_spawn, false);
		}
	}
	
	private boolean _champion;
	
	public final void setChampion(boolean champion)
	{
		_champion = champion;
	}
	
	@Override
	public final boolean isChampion()
	{
		return _champion;
	}
	
	/**
	 * Remove PROPERLY the L2Npc from the world.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Remove the L2Npc from the world and update its spawn object </li>
	 * <li>Remove all L2Object from _knownObjects and _knownPlayer of the L2Npc then cancel Attack or Cast and notify AI </li>
	 * <li>Remove L2Object object from _allObjects of L2World </li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND Server->Client packets to players</B></FONT><BR><BR>
	 *
	 */
	public void deleteMe()
	{
		abortCast();
		abortAttack();
		getStatus().stopHpMpRegeneration();
		getEffects().stopAllEffects(true);
		
		L2WorldRegion region = getWorldRegion();
		
		try
		{
			decayMe();
		}
		catch (Exception e)
		{
			_log.error("Failed decayMe().", e);
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
			_log.error("Failed deleteMe().", e);
		}
		
		if (region != null)
			region.removeFromZones(this);
		
		// Remove all L2Object from _knownObjects and _knownPlayer of the L2Creature then cancel Attack or Cast and notify AI
		try
		{
			getKnownList().removeAllKnownObjects();
		}
		catch (Exception e)
		{
			_log.error("Failed removing cleaning knownlist.", e);
		}
	}
	
	/**
	 * Return the L2Spawn object that manage this L2Npc.<BR><BR>
	 */
	public L2Spawn getSpawn()
	{
		return _spawn;
	}
	
	@Override
	public String toString()
	{
		return getTemplate().getName();
	}
	
	public boolean isDecayed()
	{
		return _isDecayed;
	}
	
	public void setDecayed(boolean decayed)
	{
		_isDecayed = decayed;
	}
	
	public void endDecayTask()
	{
		if (!isDecayed())
		{
			DecayTaskManager.getInstance().cancelDecayTask(this);
			onDecay();
		}
	}
	
	public boolean isMob() // Rather delete this check
	{
		return false; // This means we use MAX_NPC_ANIMATION instead of MAX_MONSTER_ANIMATION
	}
	
	// Two functions to change the appearance of the equipped weapons on the NPC
	// This is only useful for a few NPCs and is most likely going to be called from AI
	public void setLHandId(int newWeaponId)
	{
		_currentLHandId = newWeaponId;
		updateAbnormalEffect();
	}
	
	public void setRHandId(int newWeaponId)
	{
		_currentRHandId = newWeaponId;
		updateAbnormalEffect();
	}
	
	public void setLRHandId(int newLWeaponId, int newRWeaponId)
	{
		_currentRHandId = newRWeaponId;
		_currentLHandId = newLWeaponId;
		updateAbnormalEffect();
	}
	
	public void setCollisionHeight(double height)
	{
		_currentCollisionHeight = height;
	}
	
	public void setCollisionRadius(double radius)
	{
		_currentCollisionRadius = radius;
	}
	
	public double getCollisionHeight()
	{
		return _currentCollisionHeight;
	}
	
	public double getCollisionRadius()
	{
		return _currentCollisionRadius;
	}
	
	@Override
	protected final CreatureShots initShots()
	{
		return new NpcShots(this);
	}
	
	@Override
	public final NpcShots getShots()
	{
		return (NpcShots)_shots;
	}
	
	@Override
	public NpcInventory getInventory()
	{
		return _inventory;
	}
	
	private boolean cwCheck(L2Player player)
	{
		return Config.CURSED_WEAPON_NPC_INTERACT || !player.isCursedWeaponEquipped();
	}
	
	@Override
	public void sendInfo(L2Player activeChar)
	{
		if (Config.TEST_KNOWNLIST && activeChar.isGM())
			activeChar.sendMessage("Knownlist, added NPC: " + getName());
		
		activeChar.sendPacket(getRunSpeed() == 0 ? new ServerObjectInfo(this) : new AbstractNpcInfo.NpcInfo(this));
	}
	
	@Override
	public void broadcastFullInfoImpl()
	{
		broadcastPacket(getRunSpeed() == 0 ? new ServerObjectInfo(this) : new AbstractNpcInfo.NpcInfo(this));
	}
	
	public void setKillable(boolean b)
	{
		_isKillable = b;
	}
	
	public boolean isKillable()
	{
		return _isKillable;
	}
	
	public void setQuestDropable(boolean b)
	{
		_questDropable = b;
	}
	
	public boolean getQuestDropable()
	{
		return _questDropable;
	}
	
	public boolean canBeChampion()
	{
		switch (getNpcId())
		{
		// Devastated Castle
			case 35411:
			case 35412:
			case 35413:
			case 35414:
			case 35415:
			case 35416:
				// Fortress of the Dead
			case 35629:
			case 35630:
			case 35631:
			case 35632:
			case 35633:
			case 35634:
			case 35635:
			case 35636:
			case 35637:
				return false;
		}
		if (this instanceof L2CCHBossInstance)
			return false;
		return ((this instanceof L2MonsterInstance && !(this instanceof L2Boss)) || (this instanceof L2Boss && Config.CHAMPION_BOSS))
				&& Config.CHAMPION_FREQUENCY > 0
				&& !getTemplate().isQuestMonster()
				&& getLevel() >= Config.CHAMPION_MIN_LEVEL && getLevel() <= Config.CHAMPION_MAX_LEVEL;
	}
	
	public int getQuestAttackStatus()
	{
		return _questAttackStatus;
	}
	
	public void setQuestAttackStatus(int status)
	{
		_questAttackStatus = status;
	}
	
	public L2Player getQuestFirstAttacker()
	{
		return _questFirstAttacker;
	}
	
	public void setQuestFirstAttacker(L2Player attacker)
	{
		_questFirstAttacker = attacker;
	}
	
	public int getWeaponEnchantLevel()
	{
		return _weaponEnchant;
	}
	
	public void setWeaponEnchantLevel(int level)
	{
		_weaponEnchant = level;
	}
}
