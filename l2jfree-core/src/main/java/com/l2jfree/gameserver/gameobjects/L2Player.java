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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.ArrayUtils;

import com.l2jfree.Config;
import com.l2jfree.L2DatabaseFactory;
import com.l2jfree.gameserver.Announcements;
import com.l2jfree.gameserver.GameServer;
import com.l2jfree.gameserver.LoginLink;
import com.l2jfree.gameserver.Shutdown;
import com.l2jfree.gameserver.Shutdown.DisableType;
import com.l2jfree.gameserver.ThreadPoolManager;
import com.l2jfree.gameserver.cache.HtmCache;
import com.l2jfree.gameserver.cache.WarehouseCacheManager;
import com.l2jfree.gameserver.communitybbs.Manager.ForumsBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.RegionBBSManager;
import com.l2jfree.gameserver.communitybbs.Manager.RegionBBSManager.PlayerStateOnCommunity;
import com.l2jfree.gameserver.communitybbs.bb.Forum;
import com.l2jfree.gameserver.datatables.CharNameTable;
import com.l2jfree.gameserver.datatables.CharTemplateTable;
import com.l2jfree.gameserver.datatables.ClanTable;
import com.l2jfree.gameserver.datatables.FishTable;
import com.l2jfree.gameserver.datatables.GmListTable;
import com.l2jfree.gameserver.datatables.HennaTable;
import com.l2jfree.gameserver.datatables.HeroSkillTable;
import com.l2jfree.gameserver.datatables.ItemTable;
import com.l2jfree.gameserver.datatables.NobleSkillTable;
import com.l2jfree.gameserver.datatables.NpcTable;
import com.l2jfree.gameserver.datatables.PetDataTable;
import com.l2jfree.gameserver.datatables.RecipeTable;
import com.l2jfree.gameserver.datatables.RecordTable;
import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTreeTable;
import com.l2jfree.gameserver.gameobjects.ai.CtrlIntention;
import com.l2jfree.gameserver.gameobjects.ai.L2CreatureAI;
import com.l2jfree.gameserver.gameobjects.ai.L2PlayerAI;
import com.l2jfree.gameserver.gameobjects.ai.L2SummonAI;
import com.l2jfree.gameserver.gameobjects.appearance.PlayerAppearance;
import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.gameobjects.base.ClassLevel;
import com.l2jfree.gameserver.gameobjects.base.Experience;
import com.l2jfree.gameserver.gameobjects.base.Race;
import com.l2jfree.gameserver.gameobjects.base.SubClass;
import com.l2jfree.gameserver.gameobjects.effects.PlayerEffects;
import com.l2jfree.gameserver.gameobjects.instance.L2AirShipInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2BoatInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2ClassMasterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2CubicInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2DoorInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FestivalMonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2FortSiegeGuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2GuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2NpcInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2PetInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SiegeGuardInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2StaticObjectInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2SummonInstance;
import com.l2jfree.gameserver.gameobjects.instance.L2TamedBeastInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.ItemContainer;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PetInventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerFreight;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerInventory;
import com.l2jfree.gameserver.gameobjects.itemcontainer.PlayerWarehouse;
import com.l2jfree.gameserver.gameobjects.knownlist.CreatureKnownList;
import com.l2jfree.gameserver.gameobjects.knownlist.PlayerKnownList;
import com.l2jfree.gameserver.gameobjects.reference.ClearableReference;
import com.l2jfree.gameserver.gameobjects.reference.ImmutableReference;
import com.l2jfree.gameserver.gameobjects.shot.CreatureShots;
import com.l2jfree.gameserver.gameobjects.shot.PlayerShots;
import com.l2jfree.gameserver.gameobjects.skills.PlayerSkills;
import com.l2jfree.gameserver.gameobjects.stat.CreatureStat;
import com.l2jfree.gameserver.gameobjects.stat.PlayerStat;
import com.l2jfree.gameserver.gameobjects.status.CreatureStatus;
import com.l2jfree.gameserver.gameobjects.status.PlayerStatus;
import com.l2jfree.gameserver.gameobjects.templates.L2PlayerTemplate;
import com.l2jfree.gameserver.gameobjects.view.ICreatureView;
import com.l2jfree.gameserver.gameobjects.view.PlayerView;
import com.l2jfree.gameserver.geodata.GeoData;
import com.l2jfree.gameserver.handler.ItemHandler;
import com.l2jfree.gameserver.handler.SkillHandler;
import com.l2jfree.gameserver.handler.admincommands.AdminEditChar;
import com.l2jfree.gameserver.handler.skills.TakeCastle;
import com.l2jfree.gameserver.handler.skills.TakeFort;
import com.l2jfree.gameserver.instancemanager.CastleManager;
import com.l2jfree.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jfree.gameserver.instancemanager.DimensionalRiftManager;
import com.l2jfree.gameserver.instancemanager.DuelManager;
import com.l2jfree.gameserver.instancemanager.FortManager;
import com.l2jfree.gameserver.instancemanager.FortSiegeManager;
import com.l2jfree.gameserver.instancemanager.FourSepulchersManager;
import com.l2jfree.gameserver.instancemanager.GameTimeManager;
import com.l2jfree.gameserver.instancemanager.InstanceManager;
import com.l2jfree.gameserver.instancemanager.ItemsAutoDestroyManager;
import com.l2jfree.gameserver.instancemanager.MapRegionManager;
import com.l2jfree.gameserver.instancemanager.PartyRoomManager;
import com.l2jfree.gameserver.instancemanager.QuestManager;
import com.l2jfree.gameserver.instancemanager.RecommendationManager;
import com.l2jfree.gameserver.instancemanager.SiegeManager;
import com.l2jfree.gameserver.instancemanager.ZoneManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.AntharasManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.BaiumManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.BaylorManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.FrintezzaManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.SailrenManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.ValakasManager;
import com.l2jfree.gameserver.instancemanager.grandbosses.VanHalterManager;
import com.l2jfree.gameserver.instancemanager.lastimperialtomb.LastImperialTombManager;
import com.l2jfree.gameserver.instancemanager.leaderboards.ArenaManager;
import com.l2jfree.gameserver.model.BlockList;
import com.l2jfree.gameserver.model.CursedWeapon;
import com.l2jfree.gameserver.model.Elementals;
import com.l2jfree.gameserver.model.FishData;
import com.l2jfree.gameserver.model.L2Fishing;
import com.l2jfree.gameserver.model.L2FriendList;
import com.l2jfree.gameserver.model.L2Macro;
import com.l2jfree.gameserver.model.L2Marker;
import com.l2jfree.gameserver.model.L2PetData;
import com.l2jfree.gameserver.model.L2Request;
import com.l2jfree.gameserver.model.L2ShortCut;
import com.l2jfree.gameserver.model.L2Transformation;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.MacroList;
import com.l2jfree.gameserver.model.ShortCuts;
import com.l2jfree.gameserver.model.TradeList;
import com.l2jfree.gameserver.model.clan.L2Clan;
import com.l2jfree.gameserver.model.clan.L2ClanMember;
import com.l2jfree.gameserver.model.clan.L2SiegeClan;
import com.l2jfree.gameserver.model.entity.Castle;
import com.l2jfree.gameserver.model.entity.Duel;
import com.l2jfree.gameserver.model.entity.Fort;
import com.l2jfree.gameserver.model.entity.FortSiege;
import com.l2jfree.gameserver.model.entity.GrandBossState;
import com.l2jfree.gameserver.model.entity.Instance;
import com.l2jfree.gameserver.model.entity.Siege;
import com.l2jfree.gameserver.model.entity.events.AbstractFunEventPlayerInfo;
import com.l2jfree.gameserver.model.entity.events.AutomatedTvT;
import com.l2jfree.gameserver.model.entity.events.CTF.CTFPlayerInfo;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.manufacture.L2ManufactureList;
import com.l2jfree.gameserver.model.items.recipe.L2RecipeList;
import com.l2jfree.gameserver.model.items.templates.L2Armor;
import com.l2jfree.gameserver.model.items.templates.L2ArmorType;
import com.l2jfree.gameserver.model.items.templates.L2EtcItemType;
import com.l2jfree.gameserver.model.items.templates.L2Henna;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.items.templates.L2Weapon;
import com.l2jfree.gameserver.model.items.templates.L2WeaponType;
import com.l2jfree.gameserver.model.mapregion.TeleportWhereType;
import com.l2jfree.gameserver.model.olympiad.Olympiad;
import com.l2jfree.gameserver.model.party.L2Party;
import com.l2jfree.gameserver.model.party.L2PartyRoom;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.quest.QuestState;
import com.l2jfree.gameserver.model.quest.State;
import com.l2jfree.gameserver.model.restriction.AvailableRestriction;
import com.l2jfree.gameserver.model.restriction.ObjectRestrictions;
import com.l2jfree.gameserver.model.restriction.global.DuelRestriction;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.sevensigns.SevenSigns;
import com.l2jfree.gameserver.model.sevensigns.SevenSignsFestival;
import com.l2jfree.gameserver.model.skills.Env;
import com.l2jfree.gameserver.model.skills.Formulas;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.L2Skill.SkillTargetType;
import com.l2jfree.gameserver.model.skills.SkillUsageRequest;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.model.skills.conditions.ConditionGameTime;
import com.l2jfree.gameserver.model.skills.conditions.ConditionPlayerHp;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.model.skills.funcs.Func;
import com.l2jfree.gameserver.model.skills.l2skills.L2SkillSummon;
import com.l2jfree.gameserver.model.skills.learn.L2CertificationSkillsLearn;
import com.l2jfree.gameserver.model.skills.learn.L2SkillLearn;
import com.l2jfree.gameserver.model.skills.learn.L2TransformSkillLearn;
import com.l2jfree.gameserver.model.skills.templates.L2EffectType;
import com.l2jfree.gameserver.model.skills.templates.L2SkillType;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.model.zone.L2JailZone;
import com.l2jfree.gameserver.model.zone.L2Zone;
import com.l2jfree.gameserver.network.Disconnection;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.SystemChatChannelId;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.gameserver.network.packets.client.ConfirmDlgAnswer.AnswerHandler;
import com.l2jfree.gameserver.network.packets.server.AbstractNpcInfo;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.gameserver.network.packets.server.CameraMode;
import com.l2jfree.gameserver.network.packets.server.ChangeWaitType;
import com.l2jfree.gameserver.network.packets.server.CharInfo;
import com.l2jfree.gameserver.network.packets.server.ConfirmDlg;
import com.l2jfree.gameserver.network.packets.server.EffectInfoPacket.EffectInfoPacketList;
import com.l2jfree.gameserver.network.packets.server.EtcStatusUpdate;
import com.l2jfree.gameserver.network.packets.server.ExBasicActionList;
import com.l2jfree.gameserver.network.packets.server.ExDuelUpdateUserInfo;
import com.l2jfree.gameserver.network.packets.server.ExFishingEnd;
import com.l2jfree.gameserver.network.packets.server.ExFishingStart;
import com.l2jfree.gameserver.network.packets.server.ExGetBookMarkInfoPacket;
import com.l2jfree.gameserver.network.packets.server.ExGetOnAirShip;
import com.l2jfree.gameserver.network.packets.server.ExManagePartyRoomMember;
import com.l2jfree.gameserver.network.packets.server.ExOlympiadMode;
import com.l2jfree.gameserver.network.packets.server.ExOlympiadUserInfo;
import com.l2jfree.gameserver.network.packets.server.ExPrivateStoreSetWholeMsg;
import com.l2jfree.gameserver.network.packets.server.ExSetCompassZoneCode;
import com.l2jfree.gameserver.network.packets.server.ExSpawnEmitter;
import com.l2jfree.gameserver.network.packets.server.ExStorageMaxCount;
import com.l2jfree.gameserver.network.packets.server.ExVitalityPointInfo;
import com.l2jfree.gameserver.network.packets.server.FriendList;
import com.l2jfree.gameserver.network.packets.server.GMHide;
import com.l2jfree.gameserver.network.packets.server.GameGuardQuery;
import com.l2jfree.gameserver.network.packets.server.GetOnVehicle;
import com.l2jfree.gameserver.network.packets.server.HennaInfo;
import com.l2jfree.gameserver.network.packets.server.InventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.ItemList;
import com.l2jfree.gameserver.network.packets.server.MagicEffectIcons;
import com.l2jfree.gameserver.network.packets.server.MagicSkillUse;
import com.l2jfree.gameserver.network.packets.server.MyTargetSelected;
import com.l2jfree.gameserver.network.packets.server.NicknameChanged;
import com.l2jfree.gameserver.network.packets.server.NpcHtmlMessage;
import com.l2jfree.gameserver.network.packets.server.ObservationMode;
import com.l2jfree.gameserver.network.packets.server.ObservationReturn;
import com.l2jfree.gameserver.network.packets.server.PartySmallWindowUpdate;
import com.l2jfree.gameserver.network.packets.server.PartySpelled;
import com.l2jfree.gameserver.network.packets.server.PetInventoryUpdate;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListDelete;
import com.l2jfree.gameserver.network.packets.server.PledgeShowMemberListUpdate;
import com.l2jfree.gameserver.network.packets.server.PledgeSkillList;
import com.l2jfree.gameserver.network.packets.server.PrivateStoreListBuy;
import com.l2jfree.gameserver.network.packets.server.PrivateStoreListSell;
import com.l2jfree.gameserver.network.packets.server.PrivateStoreManageListBuy;
import com.l2jfree.gameserver.network.packets.server.PrivateStoreManageListSell;
import com.l2jfree.gameserver.network.packets.server.PrivateStoreMsgBuy;
import com.l2jfree.gameserver.network.packets.server.PrivateStoreMsgSell;
import com.l2jfree.gameserver.network.packets.server.QuestList;
import com.l2jfree.gameserver.network.packets.server.RecipeShopMsg;
import com.l2jfree.gameserver.network.packets.server.RecipeShopSellList;
import com.l2jfree.gameserver.network.packets.server.RelationChanged;
import com.l2jfree.gameserver.network.packets.server.Ride;
import com.l2jfree.gameserver.network.packets.server.SetupGauge;
import com.l2jfree.gameserver.network.packets.server.ShortBuffStatusUpdate;
import com.l2jfree.gameserver.network.packets.server.ShortCutInit;
import com.l2jfree.gameserver.network.packets.server.SkillCoolTime;
import com.l2jfree.gameserver.network.packets.server.SkillList;
import com.l2jfree.gameserver.network.packets.server.Snoop;
import com.l2jfree.gameserver.network.packets.server.SocialAction;
import com.l2jfree.gameserver.network.packets.server.SpecialCamera;
import com.l2jfree.gameserver.network.packets.server.StaticPacket;
import com.l2jfree.gameserver.network.packets.server.StatusUpdate;
import com.l2jfree.gameserver.network.packets.server.StopMove;
import com.l2jfree.gameserver.network.packets.server.SystemMessage;
import com.l2jfree.gameserver.network.packets.server.TargetSelected;
import com.l2jfree.gameserver.network.packets.server.TargetUnselected;
import com.l2jfree.gameserver.network.packets.server.TradeDone;
import com.l2jfree.gameserver.network.packets.server.TradeOtherDone;
import com.l2jfree.gameserver.network.packets.server.TradeStart;
import com.l2jfree.gameserver.network.packets.server.TutorialCloseHtml;
import com.l2jfree.gameserver.network.packets.server.TutorialShowHtml;
import com.l2jfree.gameserver.network.packets.server.UserInfo;
import com.l2jfree.gameserver.network.packets.server.ValidateLocation;
import com.l2jfree.gameserver.persistence.WorldTransaction;
import com.l2jfree.gameserver.persistence.player.PlayerRepository;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.BirthdayRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.BookmarkRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.ColorsRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.HennaRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.PlayerRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.PlayerSave;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.RecipeRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.SkillReuseRow;
import com.l2jfree.gameserver.persistence.player.PlayerRepository.SubclassRow;
import com.l2jfree.gameserver.taskmanager.AbstractIterativePeriodicTaskManager;
import com.l2jfree.gameserver.taskmanager.AttackStanceTaskManager;
import com.l2jfree.gameserver.taskmanager.LeakTaskManager;
import com.l2jfree.gameserver.taskmanager.MovementController;
import com.l2jfree.gameserver.taskmanager.PacketBroadcaster.BroadcastMode;
import com.l2jfree.gameserver.taskmanager.SQLQueue;
import com.l2jfree.gameserver.util.Broadcast;
import com.l2jfree.gameserver.util.FloodProtector;
import com.l2jfree.gameserver.util.Util;
import com.l2jfree.lang.L2Math;
import com.l2jfree.lang.L2System;
import com.l2jfree.lang.Replaceable;
import com.l2jfree.network.InvalidPacketException;
import com.l2jfree.sql.SQLQuery;
import com.l2jfree.tools.geometry.Point3D;
import com.l2jfree.tools.random.Rnd;
import com.l2jfree.tools.util.HexUtil;
import com.l2jfree.util.ArrayBunch;
import com.l2jfree.util.L2Arrays;
import com.l2jfree.util.L2Collections;
import com.l2jfree.util.LazyFastList;
import com.l2jfree.util.LazyFastMap;

/**
 * This class represents all player characters in the world.
 * There is always a client-thread connected to this (except if a player-store is activated upon logout).<BR><BR>
 *
 * @version $Revision: 1.66.2.41.2.33 $ $Date: 2005/04/11 10:06:09 $
 */
public final class L2Player extends L2Playable
{
	@SuppressWarnings("hiding")
	public static final L2Player[] EMPTY_ARRAY = new L2Player[0];
	
	public static final int REQUEST_TIMEOUT = 15;
	
	public static final int STORE_PRIVATE_NONE = 0;
	public static final int STORE_PRIVATE_SELL = 1;
	public static final int STORE_PRIVATE_BUY = 3;
	public static final int STORE_PRIVATE_MANUFACTURE = 5;
	public static final int STORE_PRIVATE_PACKAGE_SELL = 8;
	
	/** The table containing all minimum level needed for each Expertise (None, D, C, B, A, S, S80, S84)*/
	private static final int[] EXPERTISE_LEVELS = { SkillTreeTable.getInstance().getExpertiseLevel(0), // NONE
			SkillTreeTable.getInstance().getExpertiseLevel(1), // D
			SkillTreeTable.getInstance().getExpertiseLevel(2), // C
			SkillTreeTable.getInstance().getExpertiseLevel(3), // B
			SkillTreeTable.getInstance().getExpertiseLevel(4), // A
			SkillTreeTable.getInstance().getExpertiseLevel(5), // S
			SkillTreeTable.getInstance().getExpertiseLevel(6), // S80
			SkillTreeTable.getInstance().getExpertiseLevel(7) //S84
			};
	
	private static final int[] COMMON_CRAFT_LEVELS = { 5, 20, 28, 36, 43, 49, 55, 62 };
	
	public class AIAccessor extends L2Creature.AIAccessor
	{
		protected AIAccessor()
		{
		}
		
		public L2Player getPlayer()
		{
			return L2Player.this;
		}
		
		public void doPickupItem(L2Object object)
		{
			L2Player.this.doPickupItem(object);
		}
		
		public void doInteract(L2Creature target)
		{
			L2Player.this.doInteract(target);
		}
		
		@Override
		public void doAttack(L2Creature target)
		{
			super.doAttack(target);
			
			// Cancel the recent fake-death protection instantly if the player attacks or casts spells
			setRecentFakeDeath(false);
		}
		
		@Override
		public void doCast(L2Skill skill)
		{
			super.doCast(skill);
			
			// Cancel the recent fake-death protection instantly if the player attacks or casts spells
			setRecentFakeDeath(false);
			if (skill == null)
				return;
			if (!skill.isOffensive())
				return;
			
			switch (skill.getTargetType())
			{
				case TARGET_GROUND:
					return;
				default:
				{
					for (L2CubicInstance cubic : getCubics().values())
						if (cubic.getId() != L2CubicInstance.LIFE_CUBIC)
							cubic.doAction();
				}
					break;
			}
		}
	}
	
	private L2Client _client;
	
	private final PlayerAppearance _appearance;
	
	/** Sitting down and Standing up fix */
	private long _lastSitStandRequest = 0;
	
	/** The Identifier of the L2Player */
	private int _charId = 0x00030b7a;
	
	/** The Experience of the L2Player before the last Death Penalty */
	private long _expBeforeDeath;
	
	/** The Karma of the L2Player (if higher than 0, the name of the L2Player appears in red) */
	private int _karma;
	
	/** The number of player killed during a PvP (the player killed was PvP Flagged) */
	private int _pvpKills;
	
	/** The PK counter of the L2Player (= Number of non PvP Flagged player killed) */
	private int _pkKills;
	
	/** The Siege state of the L2Player */
	private byte _siegeState = SIEGE_STATE_NOT_INVOLVED;
	private boolean _isInSiege = false;
	
	private int _lastCompassZone; // The last compass zone update send to the client
	
	private boolean _isIn7sDungeon = false;
	
	public int _bookmarkslot = 0; // The Teleport Bookmark Slot
	public final List<TeleportBookmark> tpbookmark = new LazyFastList<TeleportBookmark>();
	
	private int _subPledgeType = 0;
	
	/** L2Player's pledge rank*/
	private int _pledgeRank;
	
	/** Level at which the player joined the clan as an accedemy member*/
	private int _lvlJoinedAcademy = 0;
	
	/** The random number of the L2Player */
	//private static final Random _rnd = new Random();
	private int _curWeightPenalty = 0;
	
	private long _deleteTimer;
	private PlayerInventory _inventory;
	private PlayerWarehouse _warehouse;
	private PlayerFreight _freight;
	private List<PlayerFreight> _depositedFreight;
	private final PlayerSkills _pcSkills = new PlayerSkills(this);
	
	/** True if the L2Player is sitting */
	private boolean _waitTypeSitting;
	
	/** True if the L2Player is using the relax skill */
	private boolean _relax;
	
	/** AirShip */
	private L2AirShipInstance _airShip;
	private Point3D _inAirShipPosition;
	
	public ScheduledFuture<?> _taskforfish;
	
	/** Last NPC Id talked on a quest */
	private int _questNpcObject = 0;
	
	/** Bitmask used to keep track of one-time/newbie quest rewards */
	private int _newbie;
	
	/** The table containing all Quests began by the L2Player */
	private final Map<String, QuestState> _quests = new LazyFastMap<String, QuestState>();
	
	/** The list containing all shortCuts of this L2Player */
	private ShortCuts _shortCuts;
	
	/** The list containing all macroses of this L2Player */
	private MacroList _macroses;
	
	private TradeList _activeTradeList;
	private ItemContainer _activeWarehouse;
	private L2ManufactureList _createList;
	private TradeList _sellList;
	private TradeList _buyList;
	
	private L2Player[] _snoopers = L2Player.EMPTY_ARRAY; // List of GMs snooping this player
	private L2Player[] _snoopedPlayers = L2Player.EMPTY_ARRAY; // List of players being snooped by this GM
	
	/** The Private Store type of the L2Player (STORE_PRIVATE_NONE=0, STORE_PRIVATE_SELL=1, sellmanage=2, STORE_PRIVATE_BUY=3, buymanage=4, STORE_PRIVATE_MANUFACTURE=5) */
	private int _privatestore;
	private ClassId _skillLearningClassId;
	
	// Hennas
	private final L2Henna[] _henna = new L2Henna[3];
	private int _hennaSTR;
	private int _hennaINT;
	private int _hennaDEX;
	private int _hennaMEN;
	private int _hennaWIT;
	private int _hennaCON;
	
	private boolean _isRidingStrider = false;
	private boolean _isRidingRedStrider = false;
	private boolean _isRidingHorse = false;
	private boolean _isFlyingMounted = false;
	
	/** The L2Summon of the L2Player */
	private L2Summon _summon = null;
	/** The L2Decoy of the L2Player */
	private L2Decoy _decoy = null;
	/** The L2Trap of the L2Player */
	private L2Trap _trap = null;
	/** The L2Agathion of the L2Player */
	private int _agathionId = 0;
	// Apparently, a L2Player CAN have both a summon AND a tamed beast at the same time!!
	private L2TamedBeastInstance _tamedBeast = null;
	
	// Client radar
	private L2Marker _radar;
	
	// These values are only stored temporarily
	private boolean _lookingForParty;
	private boolean _partyMatchingAllLevels;
	private int _partyMatchingRegion;
	private L2PartyRoom _partyRoom;
	private L2Party _party;
	// Clan related attributes
	
	/** The Clan Identifier of the L2Player */
	private int _clanId;
	
	/** The Clan object of the L2Player */
	private L2Clan _clan;
	
	/** Apprentice and Sponsor IDs */
	private int _apprentice = 0;
	private int _sponsor = 0;
	
	private long _clanJoinExpiryTime;
	private long _clanCreateExpiryTime;
	
	private long _onlineTime;
	private long _onlineBeginTime;
	
	// GM Stuff
	private boolean _isGm;
	private int _accessLevel;
	
	private boolean _messageRefusal = false; // Message refusal mode
	private boolean _dietMode = false; // Ignore weight penalty
	private boolean _tradeRefusal = false; // Trade refusal
	private boolean _exchangeRefusal = false; // Exchange refusal
	
	// This is needed to find the inviting player for Party response
	// There can only be one active party request at once
	private L2Player _activeRequester;
	private long _requestExpireTime = 0;
	private L2Request _request;
	private L2ItemInstance _arrowItem;
	private L2ItemInstance _boltItem;
	
	// Used for protection after teleport
	private long _protectEndTime = 0;
	
	// Protects a char from agro mobs when getting up from fake death
	private long _recentFakeDeathEndTime = 0;
	
	/** The fists L2Weapon of the L2Player (used when no weapon is equipped) */
	private L2Weapon _fistsWeaponItem;
	
	private long _uptime;
	private final String _accountName;
	
	private Map<Integer, String> _chars;
	
	/** The table containing all L2RecipeList of the L2Player */
	private final Map<Integer, L2RecipeList> _dwarvenRecipeBook = new LazyFastMap<Integer, L2RecipeList>();
	private final Map<Integer, L2RecipeList> _commonRecipeBook = new LazyFastMap<Integer, L2RecipeList>();
	
	private int _mountType;
	private int _mountNpcId;
	private int _mountLevel;
	
	/** The current higher Expertise of the L2Player (None=0, D=1, C=2, B=3, A=4, S=5, S80=6, S84=7)*/
	private int _expertiseIndex; // Index in EXPERTISE_LEVELS
	private int _weaponGradePenalty;
	private int _armorPenalty;
	
	private boolean _isEnchanting = false;
	private L2ItemInstance _activeEnchantItem = null;
	private L2ItemInstance _activeEnchantSupportItem = null;
	private L2ItemInstance _activeEnchantAttrItem = null;
	private long _activeEnchantTimestamp = 0;
	
	public static final byte ONLINE_STATE_LOADED = 0;
	public static final byte ONLINE_STATE_ONLINE = 1;
	public static final byte ONLINE_STATE_DELETED = 2;
	
	// written by the disconnect, the relogin and the shutdown threads
	private volatile byte _isOnline = ONLINE_STATE_LOADED;
	
	/** Set by the first deleteMe(): the disconnect task, a relogin and the shutdown can all call it. */
	private final AtomicBoolean _deleteStarted = new AtomicBoolean();
	
	protected boolean _inventoryDisabled = false;
	
	protected Map<Integer, L2CubicInstance> _cubics = new LazyFastMap<Integer, L2CubicInstance>().setShared();
	
	/** The L2NpcInstance corresponding to the last Folk wich one the player talked. */
	private L2Npc _lastFolkNpc = null;
	
	private int _clanPrivileges = 0;
	
	/** L2Player's pledge class (knight, Baron, etc.)*/
	private int _pledgeClass = 0;
	
	/** Location before entering Observer Mode */
	private int _obsX;
	private int _obsY;
	private int _obsZ;
	private boolean _observerMode = false;
	
	/** TvT Instanced Engine parameters */
	public int _originalNameColorTvTi, _originalKarmaTvTi, _countTvTiKills = 0, _countTvTITeamKills = 0;
	public boolean _inEventTvTi = false, _isSitForcedTvTi = false, _joiningTvTi = false;
	
	public int _telemode = 0;
	
	/** new loto ticket **/
	private final int _loto[] = new int[5];
	/** new race ticket **/
	private final int _race[] = new int[2];
	
	private BlockList _blockList;
	private L2FriendList _friendList;
	
	private boolean _fishing = false;
	private int _fishx = 0;
	private int _fishy = 0;
	private int _fishz = 0;
	
	private final List<Integer> _transformAllowedSkills = new LazyFastList<Integer>();
	
	private int _team = 0;
	private int _wantsPeace = 0;
	
	// Death Penalty Buff Level
	private int _deathPenaltyBuffLevel = 0;
	
	// Self resurrect during siege
	private boolean _charmOfCourage = false;
	
	private boolean _hero = false;
	private boolean _noble = false;
	private boolean _inOlympiadMode = false;
	private boolean _olympiadStart = false;
	private int _olympiadGameId = -1;
	private int _olympiadSide = -1;
	private int _olympiadOpponentId = 0;
	public int olyBuff = 0;
	
	/** Duel */
	private int _duelState = Duel.DUELSTATE_NODUEL;
	private boolean _isInDuel = false;
	private int _duelId = 0;
	private int _noDuelReason = 0;
	
	/** ally with ketra or varka related vars*/
	private int _alliedVarkaKetra = 0;
	
	/**
	 * IMO we don't need it, as we have FIFO packet execution.
	 */
	private final ReentrantLock _subclassLock = new ReentrantLock();
	/** The list of sub-classes this character has. */
	private Map<Integer, SubClass> _subClasses;
	protected int _baseClass;
	protected int _activeClass;
	protected int _classIndex = 0;
	
	/** data for mounted pets */
	private int _controlItemId;
	private L2PetData _data;
	private int _curFeed;
	protected Future<?> _mountFeedTask;
	private ScheduledFuture<?> _dismountTask;
	
	private long _lastAccess;
	private int _boatId;
	
	private ScheduledFuture<?> _taskRentPet;
	private ScheduledFuture<?> _taskWater;
	private L2BoatInstance _boat;
	private Point3D _inBoatPosition;
	
	/** Bypass validations */
	private List<String> _validBypass;
	private List<String> _validBypass2;
	
	private Forum _forumMail;
	private Forum _forumMemo;
	
	private L2Fishing _fishCombat;
	
	/** The number of evaluation points obtained by this player */
	private int _evalPoints;
	
	/** The number of evaluations this player can give */
	private int _evaluations;
	
	/** List of players this player already evaluated */
	private final List<Integer> _evaluated = new LazyFastList<Integer>();
	
	private boolean _inCrystallize;
	
	private boolean _inCraftMode;
	
	/** Store object used to summon the strider you are mounting **/
	private int _mountObjectID = 0;
	
	/** character VIP **/
	private boolean _charViP = false;
	
	private boolean _inJail = false;
	private long _jailTimer = 0;
	
	private boolean _maried = false;
	private int _partnerId = 0;
	private int _coupleId = 0;
	private boolean _maryrequest = false;
	private boolean _maryaccepted = false;
	
	private int _clientRevision = 0;
	
	
	/* Flag to disable equipment/skills while wearing formal wear **/
	private boolean _IsWearingFormalWear = false;
	
	private L2Transformation _transformation;
	
	private int _transformationId = 0;
	
	private L2StaticObjectInstance _throne;
	
	// Absorbed Souls
	private int _souls = 0;
	private ScheduledFuture<?> _soulTask = null;
	private int _lastSoulConsume = 0;
	
	// Force charges
	private int _charges = 0;
	private ScheduledFuture<?> _chargeTask = null;
	
	public int _fame = 0; // The Fame of this L2Player
	private ScheduledFuture<?> _fameTask;
	
	/** Vitality recovery task */
	private ScheduledFuture<?> _vitalityTask;
	
	private ScheduledFuture<?> _teleportWatchdog;
	
	// Id of the afro hair cut
	private int _afroId = 0;
	
	// Creation day
	private int _lastClaim;
	private Calendar _createdOn;
	
	private long _lastTargetChange;
	private int _lastTargetId;
	
	private boolean _illegalWaiting;
	
	private long _nextJumpTime;
	
	private class VitalityTask implements Runnable
	{
		private L2Player _player = null;
		
		public VitalityTask(L2Player player)
		{
			_player = player;
		}
		
		@Override
		public void run()
		{
			if (_player == null)
				return;
			
			if (!_player.isInsideZone(L2Zone.FLAG_PEACE))
				return;
			
			if (_player.getVitalityPoints() >= PlayerStat.MAX_VITALITY_POINTS)
				return;
			
			_player.updateVitalityPoints(Config.RATE_RECOVERY_VITALITY_PEACE_ZONE, false, false);
			_player.sendPacket(new ExVitalityPointInfo(getVitalityPoints()));
		}
	}
	
	@Override
	public final boolean isAllSkillsDisabled()
	{
		return super.isAllSkillsDisabled() || isTryingToSitOrStandup();
	}
	
	@Override
	public final boolean isAttackingDisabled()
	{
		return super.isAttackingDisabled() || isTryingToSitOrStandup();
	}
	
	@Override
	public boolean isInProtectedAction()
	{
		return super.isInProtectedAction() || isTryingToSitOrStandup();
	}
	
	/**
	 * Create a new L2Player and add it in the characters table of the database.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Create a new L2Player with an account name </li>
	 * <li>Set the name, the Hair Style, the Hair Color and  the Face type of the L2Player</li>
	 * <li>Add the player in the characters table of the database</li><BR><BR>
	 *
	 * @param objectId Identifier of the object to initialized
	 * @param template The L2PlayerTemplate to apply to the L2Player
	 * @param accountName The name of the L2Player
	 * @param name The name of the L2Player
	 * @param hairStyle The hair style Identifier of the L2Player
	 * @param hairColor The hair color Identifier of the L2Player
	 * @param face The face type Identifier of the L2Player
	 *
	 * @return The L2Player added to the database or null
	 *
	 */
	public static L2Player create(int objectId, L2PlayerTemplate template, String accountName, String name,
			byte hairStyle, byte hairColor, byte face, boolean sex)
	{
		// Create a new L2Player with an account name
		PlayerAppearance app = new PlayerAppearance(face, hairColor, hairStyle, sex);
		L2Player player = new L2Player(objectId, template, accountName, app);
		
		// Set the name of the L2Player
		player.setName(name);
		
		// Set the base class ID to that of the actual class ID.
		player.setBaseClass(player.getClassId());
		
		// Kept for backwards compabitility.
		player.setNewbie(1);
		
		// Add the player in the characters table of the database
		boolean ok = player.createDb();
		
		if (!ok)
			return null;
		
		return player;
	}
	
	public String getAccountName()
	{
		if (getClient() == null)
			return _accountName;
		return getClient().getAccountName();
	}
	
	public int getRelation(L2Player target)
	{
		int result = 0;
		
		if (getClan() != null)
			result |= RelationChanged.RELATION_CLAN_MEMBER;
		
		if (isClanLeader())
			result |= RelationChanged.RELATION_LEADER;
		
		L2Party party = getParty();
		if (party != null && party == target.getParty())
		{
			result |= RelationChanged.RELATION_HAS_PARTY;
			
			switch (party.getPartyMembers().indexOf(this))
			{
				case 0:
					result |= RelationChanged.RELATION_PARTYLEADER; // 0x10
					break;
				case 1:
					result |= RelationChanged.RELATION_PARTY4; // 0x8
					break;
				case 2:
					result |=
							RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY2
									+ RelationChanged.RELATION_PARTY1; // 0x7
					break;
				case 3:
					result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY2; // 0x6
					break;
				case 4:
					result |= RelationChanged.RELATION_PARTY3 + RelationChanged.RELATION_PARTY1; // 0x5
					break;
				case 5:
					result |= RelationChanged.RELATION_PARTY3; // 0x4
					break;
				case 6:
					result |= RelationChanged.RELATION_PARTY2 + RelationChanged.RELATION_PARTY1; // 0x3
					break;
				case 7:
					result |= RelationChanged.RELATION_PARTY2; // 0x2
					break;
				case 8:
					result |= RelationChanged.RELATION_PARTY1; // 0x1
					break;
			}
		}
		
		if (getSiegeState() != SIEGE_STATE_NOT_INVOLVED)
		{
			result |= RelationChanged.RELATION_INSIEGE;
			if (getSiegeState() != target.getSiegeState())
				result |= RelationChanged.RELATION_ENEMY;
			else
				result |= RelationChanged.RELATION_ALLY;
			if (getSiegeState() == SIEGE_STATE_ATTACKER)
				result |= RelationChanged.RELATION_ATTACKER;
		}
		
		if (getClan() != null && target.getClan() != null)
		{
			if (target.getSubPledgeType() != L2Clan.SUBUNIT_ACADEMY && getSubPledgeType() != L2Clan.SUBUNIT_ACADEMY
					&& target.getClan().isAtWarWith(getClan().getClanId()))
			{
				result |= RelationChanged.RELATION_1SIDED_WAR;
				if (getClan().isAtWarWith(target.getClan().getClanId()))
					result |= RelationChanged.RELATION_MUTUAL_WAR;
			}
		}
		return result;
	}
	
	public Map<Integer, String> getAccountChars()
	{
		if (_chars == null)
		{
			try
			{
				// Retrieve the name and ID of the other characters assigned to this account.
				Map<Integer, String> others =
						PlayerRepository.getInstance().loadOtherCharacters(getAccountName(), getObjectId());
				
				if (!others.isEmpty())
					_chars = new HashMap<Integer, String>(others);
			}
			catch (SQLException e)
			{
				_log.warn("", e);
			}
			
			if (_chars == null)
				_chars = L2Collections.emptyMap();
		}
		
		return _chars;
	}
	
	private void initPlayerStatusUpdateValues()
	{
		_cpUpdateInterval = getMaxCp() / 352.0;
		_cpUpdateIncCheck = getMaxCp();
		_cpUpdateDecCheck = getMaxCp() - _cpUpdateInterval;
		_mpUpdateInterval = getMaxMp() / 352.0;
		_mpUpdateIncCheck = getMaxMp();
		_mpUpdateDecCheck = getMaxMp() - _mpUpdateInterval;
	}
	
	/**
	 * Constructor of L2Player (use L2Creature constructor).<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Call the L2Creature constructor to create an empty _skills slot and copy basic Calculator set to this L2Player </li>
	 * <li>Set the name of the L2Player</li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method SET the level of the L2Player to 1</B></FONT><BR><BR>
	 *
	 * @param objectId Identifier of the object to initialized
	 * @param template The L2PlayerTemplate to apply to the L2Player
	 * @param accountName The name of the account including this L2Player
	 *
	 */
	private L2Player(int objectId, L2PlayerTemplate template, String accountName, PlayerAppearance app)
	{
		super(objectId, template);
		getKnownList(); // Init knownlist
		getStat(); // Init stats
		getStatus(); // Init status
		super.initCreatureStatusUpdateValues();
		initPlayerStatusUpdateValues();
		
		_accountName = accountName;
		app.setOwner(this);
		_appearance = app;
		
		// Create an AI
		getAI();
		
		// Retrieve from the database all items of this L2Player and add them to _inventory
		getInventory().restore();
		if (!Config.WAREHOUSE_CACHE)
			getWarehouse();
		getFreight();
		startVitalityTask();
	}
	
	@Override
	protected CreatureKnownList initKnownList()
	{
		return new PlayerKnownList(this);
	}
	
	@Override
	public final PlayerKnownList getKnownList()
	{
		return (PlayerKnownList)_knownList;
	}
	
	@Override
	protected ICreatureView initView()
	{
		return new PlayerView(this);
	}
	
	@Override
	public PlayerView getView()
	{
		return (PlayerView)_view;
	}
	
	@Override
	protected CreatureStat initStat()
	{
		return new PlayerStat(this);
	}
	
	@Override
	public final PlayerStat getStat()
	{
		return (PlayerStat)_stat;
	}
	
	@Override
	protected CreatureStatus initStatus()
	{
		return new PlayerStatus(this);
	}
	
	@Override
	public final PlayerStatus getStatus()
	{
		return (PlayerStatus)_status;
	}
	
	@Override
	protected PlayerEffects initEffects()
	{
		return new PlayerEffects(this);
	}
	
	@Override
	public PlayerEffects getEffects()
	{
		return (PlayerEffects)_effects;
	}
	
	public final PlayerAppearance getAppearance()
	{
		return _appearance;
	}
	
	@Override
	public void setTitle(String value)
	{
		if (value.length() > 16)
			value = value.substring(0, 15);
		
		super.setTitle(value);
	}
	
	/**
	 * Return the base L2PlayerTemplate link to the L2Player.<BR><BR>
	 */
	public final L2PlayerTemplate getBaseTemplate()
	{
		return CharTemplateTable.getInstance().getTemplate(_baseClass);
	}
	
	/** Return the L2PlayerTemplate link to the L2Player. */
	@Override
	public final L2PlayerTemplate getTemplate()
	{
		return (L2PlayerTemplate)super.getTemplate();
	}
	
	public void setTemplate(ClassId newclass)
	{
		super.setTemplate(CharTemplateTable.getInstance().getTemplate(newclass));
	}
	
	@Override
	protected L2CreatureAI initAI()
	{
		return new L2PlayerAI(new L2Player.AIAccessor());
	}
	
	/** Return the Level of the L2Player. */
	@Override
	public final int getLevel()
	{
		return getStat().getLevel();
	}
	
	/**
	 * Return the _newbie rewards state of the L2Player.<BR><BR>
	 */
	public int getNewbie()
	{
		return _newbie;
	}
	
	/**
	 * Set the _newbie rewards state of the L2Player.<BR><BR>
	 *
	 * @param newbieRewards The Identifier of the _newbie state<BR><BR>
	 *
	 */
	public void setNewbie(int newbieRewards)
	{
		_newbie = newbieRewards;
	}
	
	public void setBaseClass(int baseClass)
	{
		_baseClass = baseClass;
	}
	
	public void setBaseClass(ClassId classId)
	{
		_baseClass = classId.ordinal();
	}
	
	public boolean isInStoreMode()
	{
		return (getPrivateStoreType() > 0);
	}
	
	public boolean isInCraftMode()
	{
		return _inCraftMode;
	}
	
	public void isInCraftMode(boolean b)
	{
		_inCraftMode = b;
	}
	
	/**
	 * Return a table containing all Common L2Recipe of the L2Player.<BR><BR>
	 */
	public L2RecipeList[] getCommonRecipeBook()
	{
		return _commonRecipeBook.values().toArray(new L2RecipeList[_commonRecipeBook.values().size()]);
	}
	
	/**
	 * Return a table containing all Dwarf L2Recipe of the L2Player.<BR><BR>
	 */
	public L2RecipeList[] getDwarvenRecipeBook()
	{
		return _dwarvenRecipeBook.values().toArray(new L2RecipeList[_dwarvenRecipeBook.values().size()]);
	}
	
	/**
	 * Add a new L2Recipe to the table _commonrecipebook containing all L2Recipe of the L2Player <BR><BR>
	 *
	 * @param recipe The L2RecipeList to add to the _recipebook
	 * @param saveToDb true to save infos into the DB
	 */
	public void registerCommonRecipeList(L2RecipeList recipe, boolean saveToDb)
	{
		_commonRecipeBook.put(recipe.getId(), recipe);
		
		if (saveToDb)
			insertNewRecipeData(recipe.getId(), false);
	}
	
	/**
	 * Add a new L2Recipe to the table _recipebook containing all L2Recipe of the L2Player <BR><BR>
	 *
	 * @param recipe The L2Recipe to add to the _recipebook
	 * @param saveToDb true to save infos into the DB
	 */
	public void registerDwarvenRecipeList(L2RecipeList recipe, boolean saveToDb)
	{
		_dwarvenRecipeBook.put(recipe.getId(), recipe);
		
		if (saveToDb)
			insertNewRecipeData(recipe.getId(), true);
	}
	
	/**
	 * @param recipeId The Identifier of the L2Recipe to check in the player's recipe books
	 *
	 * @return
	 * <b>TRUE</b> if player has the recipe on Common or Dwarven Recipe book else returns <b>FALSE</b>
	 */
	public boolean hasRecipeList(int recipeId)
	{
		if (_dwarvenRecipeBook.containsKey(recipeId))
			return true;
		else
			return _commonRecipeBook.containsKey(recipeId);
	}
	
	/**
	 * Tries to remove a L2Recipe from the table _DwarvenRecipeBook or from table _CommonRecipeBook, those table contain all L2Recipe of the L2Player <BR><BR>
	 *
	 * @param recipeId The Identifier of the L2Recipe to remove from the _recipebook
	 *
	 */
	public void unregisterRecipeList(int recipeId)
	{
		if (_dwarvenRecipeBook.remove(recipeId) != null)
			deleteRecipeData(recipeId, true);
		else if (_commonRecipeBook.remove(recipeId) != null)
			deleteRecipeData(recipeId, false);
		else
			_log.warn("Attempted to remove unknown RecipeList: " + recipeId);
		
		L2ShortCut[] allShortCuts = getAllShortCuts();
		for (L2ShortCut sc : allShortCuts)
		{
			if (sc != null && sc.getId() == recipeId && sc.getType() == L2ShortCut.TYPE_RECIPE)
				deleteShortCut(sc.getSlot(), sc.getPage());
		}
	}
	
	private void insertNewRecipeData(int recipeId, boolean isDwarf)
	{
		try
		{
			PlayerRepository.getInstance().insertRecipe(getObjectId(), recipeId, isDwarf ? _classIndex : 0, isDwarf);
		}
		catch (SQLException e)
		{
			_log.error("SQL exception while inserting recipe: " + recipeId + " from character " + getObjectId(), e);
		}
	}
	
	private void deleteRecipeData(int recipeId, boolean isDwarf)
	{
		try
		{
			PlayerRepository.getInstance().deleteRecipe(getObjectId(), recipeId, isDwarf ? _classIndex : 0);
		}
		catch (SQLException e)
		{
			_log.error("SQL exception while deleting recipe: " + recipeId + " from character " + getObjectId(), e);
		}
	}
	
	/**
	 * Returns the Id for the last talked quest NPC.<BR><BR>
	 */
	public int getLastQuestNpcObject()
	{
		return _questNpcObject;
	}
	
	public void setLastQuestNpcObject(int npcId)
	{
		_questNpcObject = npcId;
	}
	
	/**
	 * Return the QuestState object corresponding to the quest name.<BR><BR>
	 *
	 * @param quest The name of the quest
	 *
	 */
	public QuestState getQuestState(String quest)
	{
		return _quests.get(quest);
	}
	
	/**
	 * Add a QuestState to the table _quest containing all quests began by the L2Player.<BR><BR>
	 *
	 * @param qs The QuestState to add to _quest
	 *
	 */
	public void setQuestState(QuestState qs)
	{
		_quests.put(qs.getQuestName(), qs);
	}
	
	/**
	 * Remove a QuestState from the table _quest containing all quests began by the L2Player.<BR><BR>
	 *
	 * @param quest The name of the quest
	 *
	 */
	public void delQuestState(String quest)
	{
		_quests.remove(quest);
	}
	
	private QuestState[] addToQuestStateArray(QuestState[] questStateArray, QuestState state)
	{
		int len = questStateArray.length;
		QuestState[] tmp = new QuestState[len + 1];
		System.arraycopy(questStateArray, 0, tmp, 0, len);
		tmp[len] = state;
		return tmp;
	}
	
	/**
	 * Return a table containing all Quest in progress from the table _quests.<BR><BR>
	 */
	public Quest[] getAllActiveQuests()
	{
		ArrayBunch<Quest> quests = new ArrayBunch<Quest>();
		
		for (QuestState qs : _quests.values())
		{
			if (qs == null)
				continue;
			
			int questId = qs.getQuest().getQuestIntId();
			if ((questId > 19999) || (questId < 1))
				continue;
			
			if (!qs.isStarted() && !Config.DEVELOPER)
				continue;
			
			quests.add(qs.getQuest());
		}
		
		return quests.moveToArray(new Quest[quests.size()]);
	}
	
	/**
	 * Return a table containing all QuestState to modify after a L2Attackable killing.<BR><BR>
	 *
	 * @param npc The Identifier of the L2Attackable attacked
	 *
	 */
	public QuestState[] getQuestsForAttacks(L2NpcInstance npc)
	{
		// Create a QuestState table that will contain all QuestState to modify
		QuestState[] states = null;
		
		// Go through the QuestState of the L2Player quests
		for (Quest quest : npc.getTemplate().getEventQuests(Quest.QuestEventType.ON_ATTACK))
		{
			// Check if the Identifier of the L2Attackable attck is needed for the current quest
			if (getQuestState(quest.getName()) != null)
			{
				// Copy the current L2Player QuestState in the QuestState table
				if (states == null)
					states = new QuestState[] { getQuestState(quest.getName()) };
				else
					states = addToQuestStateArray(states, getQuestState(quest.getName()));
			}
		}
		
		// Return a table containing all QuestState to modify
		return states;
	}
	
	/**
	 * Return a table containing all QuestState to modify after a L2Attackable killing.<BR><BR>
	 *
	 * @param npc The Identifier of the L2Attackable killed
	 *
	 */
	public QuestState[] getQuestsForKills(L2NpcInstance npc)
	{
		// Create a QuestState table that will contain all QuestState to modify
		QuestState[] states = null;
		
		// Go through the QuestState of the L2Player quests
		for (Quest quest : npc.getTemplate().getEventQuests(Quest.QuestEventType.ON_KILL))
		{
			// Check if the Identifier of the L2Attackable killed is needed for the current quest
			if (getQuestState(quest.getName()) != null)
			{
				// Copy the current L2Player QuestState in the QuestState table
				if (states == null)
					states = new QuestState[] { getQuestState(quest.getName()) };
				else
					states = addToQuestStateArray(states, getQuestState(quest.getName()));
			}
		}
		
		// Return a table containing all QuestState to modify
		return states;
	}
	
	/**
	 * Return a table containing all QuestState from the table _quests in which the L2Player must talk to the NPC.<BR><BR>
	 *
	 * @param npcId The Identifier of the NPC
	 *
	 */
	public QuestState[] getQuestsForTalk(int npcId)
	{
		// Create a QuestState table that will contain all QuestState to modify
		QuestState[] states = null;
		
		// Go through the QuestState of the L2Player quests
		Quest[] quests = NpcTable.getInstance().getTemplate(npcId).getEventQuests(Quest.QuestEventType.ON_TALK);
		if (quests != null)
		{
			for (Quest quest : quests)
			{
				// Copy the current L2Player QuestState in the QuestState table
				if (quest != null)
				{
					// Copy the current L2Player QuestState in the QuestState table
					if (getQuestState(quest.getName()) != null)
					{
						if (states == null)
							states = new QuestState[] { getQuestState(quest.getName()) };
						else
							states = addToQuestStateArray(states, getQuestState(quest.getName()));
					}
				}
			}
		}
		
		// Return a table containing all QuestState to modify
		return states;
	}
	
	public QuestState processQuestEvent(String quest, String event)
	{
		QuestState retval = null;
		if (event == null)
			event = "";
		QuestState qs = getQuestState(quest);
		if (qs == null && event.isEmpty())
			return retval;
		if (qs == null)
		{
			Quest q = QuestManager.getInstance().getQuest(quest);
			if (q == null)
				return retval;
			qs = q.newQuestState(this);
		}
		if (qs != null)
		{
			if (getLastQuestNpcObject() > 0)
			{
				L2Object object = getKnownList().getKnownObject(getLastQuestNpcObject());
				if (object instanceof L2Npc && isInsideRadius(object, L2Npc.INTERACTION_DISTANCE, false, false))
				{
					L2Npc npc = (L2Npc)object;
					QuestState[] states = getQuestsForTalk(npc.getNpcId());
					
					if (states != null)
					{
						for (QuestState state : states)
						{
							if ((state.getQuest().getQuestIntId() == qs.getQuest().getQuestIntId()))// && !qs.isCompleted())
							{
								if (qs.getQuest().notifyEvent(event, npc, this))
									showQuestWindow(quest, State.getStateName(qs.getState()));
								
								retval = qs;
							}
						}
						sendPacket(new QuestList(this));
					}
				}
			}
		}
		
		return retval;
	}
	
	/**
	 * FIXME: move this from L2Player, there is no reason to have this here
	 * @param questId
	 * @param stateId
	 */
	private void showQuestWindow(String questId, String stateId)
	{
		String path = "data/scripts/quests/" + questId + "/" + stateId + ".htm";
		String content = HtmCache.getInstance().getHtm(path);
		
		if (content != null)
		{
			if (_log.isDebugEnabled())
				_log.debug("Showing quest window for quest " + questId + " state " + stateId + " html path: " + path);
			
			NpcHtmlMessage npcReply = new NpcHtmlMessage(5);
			npcReply.setHtml(content);
			sendPacket(npcReply);
		}
		
		sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/** List of all QuestState instance that needs to be notified of this L2Player's or its pet's death */
	private final List<QuestState> _NotifyQuestOfDeathList = new LazyFastList<QuestState>();
	
	/**
	 * Add QuestState instance that is to be notified of L2Player's death.<BR>
	 * <BR>
	 *
	 * @param qs The QuestState that subscribe to this event
	 */
	public void addNotifyQuestOfDeath(QuestState qs)
	{
		if (qs == null || _NotifyQuestOfDeathList.contains(qs))
			return;
		
		_NotifyQuestOfDeathList.add(qs);
	}
	
	/**
	 * Remove QuestState instance that is to be notified of L2Player's death.<BR>
	 * <BR>
	 *
	 * @param qs The QuestState that subscribe to this event
	 */
	public void removeNotifyQuestOfDeath(QuestState qs)
	{
		if (qs == null || !_NotifyQuestOfDeathList.contains(qs))
			return;
		
		_NotifyQuestOfDeathList.remove(qs);
	}
	
	/**
	 * Return a list of QuestStates which registered for notify of death of this L2Player.<BR>
	 * <BR>
	 */
	public final List<QuestState> getNotifyQuestOfDeath()
	{
		return _NotifyQuestOfDeathList;
	}
	
	private ShortCuts getShortCuts()
	{
		if (_shortCuts == null)
			_shortCuts = new ShortCuts(this);
		
		return _shortCuts;
	}
	
	/**
	 * Return a table containing all L2ShortCut of the L2Player.<BR><BR>
	 */
	public L2ShortCut[] getAllShortCuts()
	{
		return getShortCuts().getAllShortCuts();
	}
	
	/**
	 * Add a L2shortCut to the L2Player shortCuts<BR><BR>
	 */
	public void registerShortCut(L2ShortCut shortcut)
	{
		getShortCuts().registerShortCut(shortcut);
	}
	
	/**
	 * Delete the L2ShortCut corresponding to the position (page-slot) from the L2Player shortCuts.<BR><BR>
	 */
	public void deleteShortCut(int slot, int page)
	{
		getShortCuts().deleteShortCut(slot, page);
	}
	
	/**
	 * Add a L2Macro to the L2Player macroses<BR><BR>
	 */
	public void registerMacro(L2Macro macro)
	{
		getMacroses().registerMacro(macro);
	}
	
	/**
	 * Delete the L2Macro corresponding to the Identifier from the L2Player macroses.<BR><BR>
	 */
	public void deleteMacro(int id)
	{
		getMacroses().deleteMacro(id);
	}
	
	/**
	 * Return all L2Macro of the L2Player.<BR><BR>
	 */
	public MacroList getMacroses()
	{
		if (_macroses == null)
			_macroses = new MacroList(this);
		
		return _macroses;
	}
	
	/**
	 * Set the siege state of the L2Player.<BR><BR>
	 * 1 = attacker, 2 = defender, 0 = not involved
	 */
	public void setSiegeState(byte siegeState)
	{
		_siegeState = siegeState;
		broadcastRelationChanged();
	}
	
	/**
	 * Get the siege state of the L2Player.<BR><BR>
	 * 1 = attacker, 2 = defender, 0 = not involved
	 */
	public byte getSiegeState()
	{
		return _siegeState;
	}
	
	public static final byte SIEGE_STATE_NOT_INVOLVED = 0;
	public static final byte SIEGE_STATE_ATTACKER = 1;
	public static final byte SIEGE_STATE_DEFENDER = 2;
	
	@Override
	public boolean revalidateZone(boolean force)
	{
		if (!super.revalidateZone(force))
			return false;
		
		if (Config.ALLOW_WATER)
			checkWaterState();
		
		if (isInsideZone(L2Zone.FLAG_SIEGE))
		{
			setLastCompassZone(ExSetCompassZoneCode.SIEGE_WAR);
		}
		else if (isInsideZone(L2Zone.FLAG_PVP))
		{
			setLastCompassZone(ExSetCompassZoneCode.PVP);
		}
		else if (isIn7sDungeon())
		{
			setLastCompassZone(ExSetCompassZoneCode.SEVEN_SIGNS);
		}
		else if (isInsideZone(L2Zone.FLAG_PEACE))
		{
			setLastCompassZone(ExSetCompassZoneCode.PEACEFUL);
		}
		else
		{
			if (_lastCompassZone == ExSetCompassZoneCode.SIEGE_WAR.getZoneCode())
				updatePvPStatus();
			
			setLastCompassZone(ExSetCompassZoneCode.GENERAL);
		}
		
		return true;
	}
	
	private void setLastCompassZone(ExSetCompassZoneCode packet)
	{
		if (_lastCompassZone == packet.getZoneCode())
			return;
		
		_lastCompassZone = packet.getZoneCode();
		sendPacket(packet);
	}
	
	/**
	 * Return True if the L2Player can Craft Dwarven Recipes.<BR><BR>
	 */
	public boolean hasDwarvenCraft()
	{
		return getSkillLevel(L2Skill.SKILL_CREATE_DWARVEN) >= 1;
	}
	
	public int getDwarvenCraft()
	{
		return getSkillLevel(L2Skill.SKILL_CREATE_DWARVEN);
	}
	
	/**
	 * Return True if the L2Player can Craft Dwarven Recipes.<BR><BR>
	 */
	public boolean hasCommonCraft()
	{
		return getSkillLevel(L2Skill.SKILL_CREATE_COMMON) >= 1;
	}
	
	public int getCommonCraft()
	{
		return getSkillLevel(L2Skill.SKILL_CREATE_COMMON);
	}
	
	/**
	 * Return the PK counter of the L2Player.<BR><BR>
	 */
	public int getPkKills()
	{
		return _pkKills;
	}
	
	/**
	 * Set the PK counter of the L2Player.<BR><BR>
	 */
	public void setPkKills(int pkKills)
	{
		_pkKills = pkKills;
	}
	
	/**
	 * Return the _deleteTimer of the L2Player.<BR><BR>
	 */
	public long getDeleteTimer()
	{
		return _deleteTimer;
	}
	
	/**
	 * Set the _deleteTimer of the L2Player.<BR><BR>
	 */
	public void setDeleteTimer(long deleteTimer)
	{
		_deleteTimer = deleteTimer;
	}
	
	/**
	 * Return the current weight of the L2Player.<BR><BR>
	 */
	@Override
	public int getCurrentLoad()
	{
		return getInventory().getTotalWeight();
	}
	
	/** @return the number of evaluation points obtained by player. */
	public int getEvalPoints()
	{
		return _evalPoints;
	}
	
	/**
	 * Set the number of evaluation points obtained by player.
	 * @param value Evaluation point count
	 */
	public void setEvalPoints(int value)
	{
		_evalPoints = value;
	}
	
	/** @return the number of evaluations this player can give away. */
	public int getEvaluations()
	{
		return _evaluations;
	}
	
	/**
	 * Set the number of available evaluations.
	 * @param value New available evaluation count
	 */
	public void setEvaluationCount(int value)
	{
		_evaluations = value;
	}
	
	/**
	 * Add a player that has been evaluated by this player.
	 * @param charId evaluated player's ID
	 */
	public void addEvalRestriction(int charId)
	{
		_evaluated.add(charId);
	}
	
	/**	Removes all session evaluation restrictions for this player. */
	public void cleanEvalRestrictions()
	{
		_evaluated.clear();
	}
	
	/**
	 * @param target Player being evaluated
	 * @return whether this player hasn't evaluated the given player
	 */
	public boolean canEvaluate(L2Player target)
	{
		return !_evaluated.contains(target.getObjectId());
	}
	
	/**
	 * Set the exp of the L2Player before a death
	 * @param exp
	 */
	public void setExpBeforeDeath(long exp)
	{
		_expBeforeDeath = exp;
	}
	
	public long getExpBeforeDeath()
	{
		return _expBeforeDeath;
	}
	
	/**
	 * Return the Karma of the L2Player.<BR><BR>
	 */
	public int getKarma()
	{
		return _karma;
	}
	
	/**
	 * Set the Karma of the L2Player and send a Server->Client packet StatusUpdate (broadcast).<BR><BR>
	 */
	public void setKarma(int karma)
	{
		if (karma < 0)
			karma = 0;
		if (_karma == 0 && karma > 0)
		{
			for (L2Object object : getKnownList().getKnownObjects().values())
			{
				if (!(object instanceof L2GuardInstance))
					continue;
				
				if (((L2GuardInstance)object).getAI().getIntention() == CtrlIntention.AI_INTENTION_IDLE)
					((L2GuardInstance)object).getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE, null);
			}
		}
		else if (_karma > 0 && karma == 0)
		{
			// Send a Server->Client StatusUpdate packet with Karma and PvP Flag to the L2Player and all L2Player to inform (broadcast)
			setKarmaFlag(0);
		}
		_karma = karma;
		broadcastKarma();
		RegionBBSManager.changeCommunityBoard(this, PlayerStateOnCommunity.KARMA_OWNER);
	}
	
	/**
	 * Return the max weight that the L2Player can load.<BR><BR>
	 */
	@Override
	public int getMaxLoad()
	{
		return (int)(calcStat(Stats.MAX_LOAD, 69000, this, null) * Config.ALT_WEIGHT_LIMIT);
	}
	
	public int getWeaponPenalty()
	{
		return _weaponGradePenalty;
	}
	
	private void setWeaponPenalty(int level)
	{
		_weaponGradePenalty = level;
	}
	
	public int getArmorPenalty()
	{
		return _armorPenalty;
	}
	
	private void setArmorPenalty(int level)
	{
		_armorPenalty = level;
	}
	
	public boolean getExpertisePenalty()
	{
		return getWeaponPenalty() > 0 || getArmorPenalty() > 0;
	}
	
	@Override
	public int getWeightPenalty()
	{
		return _curWeightPenalty;
	}
	
	@Override
	public void setWeightPenalty(int value)
	{
		_curWeightPenalty = value;
	}
	
	public void refreshExpertisePenalty()
	{
		if (!Config.ALT_GRADE_PENALTY)
			return;
		
		int wpnPenalty = 0;
		int armorPenalty = 0;
		boolean sendUpdate = false;
		
		for (L2ItemInstance item : getInventory().getItems())
		{
			if (!item.isEquipped() || item.getItem() == null || item.getItem().getCrystalType() <= getExpertiseIndex())
				continue;
			
			if (item.getItem().getType2() == L2Item.TYPE2_WEAPON)
				wpnPenalty = (item.getItem().getCrystalType() - getExpertiseIndex());
			else
				armorPenalty++;
		}
		
		L2Skill skill = getKnownSkill(9009);
		int skillLevel = skill == null ? 0 : skill.getLevel();
		if (wpnPenalty > 4)
			wpnPenalty = 4;
		if (getWeaponPenalty() != wpnPenalty || skillLevel != wpnPenalty)
		{
			setWeaponPenalty(wpnPenalty);
			if (wpnPenalty > 0)
				super.addSkill(9009, wpnPenalty);
			else
				super.removeSkill(skill);
			sendUpdate = true;
		}
		
		skill = getKnownSkill(9010);
		skillLevel = skill == null ? 0 : skill.getLevel();
		if (armorPenalty > 5)
			armorPenalty = 5;
		if (getArmorPenalty() != armorPenalty || skillLevel != armorPenalty)
		{
			setArmorPenalty(armorPenalty);
			if (armorPenalty > 0)
				super.addSkill(9010, armorPenalty);
			else
				super.removeSkill(skill);
			sendUpdate = true;
		}
		
		if (sendUpdate)
			sendEtcStatusUpdate();
	}
	
	/**
	 * Return the the PvP Kills of the L2Player (Number of player killed during a PvP).<BR><BR>
	 */
	public int getPvpKills()
	{
		return _pvpKills;
	}
	
	/**
	 * Set the the PvP Kills of the L2Player (Number of player killed during a PvP).<BR><BR>
	 */
	public void setPvpKills(int pvpKills)
	{
		_pvpKills = pvpKills;
	}
	
	/**
	 * Return the ClassId object of the L2Player contained in L2PlayerTemplate.<BR><BR>
	 */
	public ClassId getClassId()
	{
		return getTemplate().getClassId();
	}
	
	public void academyCheck(int Id)
	{
		if ((getSubPledgeType() == -1 || getLvlJoinedAcademy() != 0) && _clan != null
				&& ClassId.values()[Id].getLevel() == ClassLevel.Third)
		{
			if (getLvlJoinedAcademy() <= 16)
				_clan.setReputationScore(_clan.getReputationScore() + Config.JOIN_ACADEMY_MAX_REP_SCORE, true);
			else if (getLvlJoinedAcademy() >= 39)
				_clan.setReputationScore(_clan.getReputationScore() + Config.JOIN_ACADEMY_MIN_REP_SCORE, true);
			else
				_clan.setReputationScore(_clan.getReputationScore()
						+ (Config.JOIN_ACADEMY_MAX_REP_SCORE - (getLvlJoinedAcademy() - 16) * 20), true);
			setLvlJoinedAcademy(0);
			
			// Oust pledge member from the academy, cuz he has finished his 2nd class transfer
			SystemMessage msg = new SystemMessage(SystemMessageId.CLAN_MEMBER_S1_EXPELLED);
			msg.addString(getName());
			_clan.broadcastToOnlineMembers(msg);
			_clan.broadcastToOnlineMembers(new PledgeShowMemberListDelete(getName()));
			
			_clan.removeClanMember(getObjectId(), 0);
			sendPacket(SystemMessageId.ACADEMY_MEMBERSHIP_TERMINATED);
			// Receive graduation gift
			getInventory().addItem("Gift", 8181, 1, this, null); // Give academy circlet
		}
	}
	
	/**
	 * Set the template of the L2Player.<BR><BR>
	 *
	 * @param Id The Identifier of the L2PlayerTemplate to set to the L2Player
	 *
	 */
	public void setClassId(int Id)
	{
		if (!_subclassLock.tryLock())
			return;
		
		try
		{
			academyCheck(Id);
			
			if (isSubClassActive())
			{
				getSubClasses().get(_classIndex).setClassId(Id);
			}
			setClassTemplate(Id);
			
			setTarget(this);
			// Animation: Production - Clan / Transfer
			MagicSkillUse msu = new MagicSkillUse(this, this, 5103, 1, 1196, 0);
			broadcastPacket(msu);
			
			// Update class icon in party and clan
			broadcastClassIcon();
			
			rewardSkills();
		}
		finally
		{
			_subclassLock.unlock();
		}
	}
	
	public void useEquippableItem(L2ItemInstance item, boolean abortAttack)
	{
		// Equip or unEquip
		L2ItemInstance[] items = null;
		final boolean isEquiped = item.isEquipped();
		final int oldInvLimit = getInventoryLimit();
		SystemMessage sm = null;
		L2ItemInstance old = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LRHAND);
		if (old == null)
			old = getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		
		int bodyPart = item.getItem().getBodyPart();
		if (isEquiped)
		{
			if (item.getEnchantLevel() > 0)
			{
				sm = new SystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
				sm.addNumber(item.getEnchantLevel());
				sm.addItemName(item);
			}
			else
			{
				sm = new SystemMessage(SystemMessageId.S1_DISARMED);
				sm.addItemName(item);
			}
			sendPacket(sm);
			
			switch (bodyPart)
			{
				case L2Item.SLOT_L_EAR:
				case L2Item.SLOT_LR_EAR:
				case L2Item.SLOT_L_FINGER:
				case L2Item.SLOT_LR_FINGER:
					getInventory().setPaperdollItem(item.getLocationSlot(), null);
					sendPacket(new ItemList(this, false));
			}
			
			// We can't unequip talisman by body slot
			if (bodyPart == L2Item.SLOT_DECO)
				items = getInventory().unEquipItemInSlotAndRecord(item.getLocationSlot());
			else
				items = getInventory().unEquipItemInBodySlotAndRecord(bodyPart);
		}
		else
		{
			L2ItemInstance tempItem = getInventory().getPaperdollItemByL2ItemId(bodyPart);
			
			// Check if the item replaces a wear-item
			if (tempItem != null && tempItem.isWear())
			{
				// Don't allow an item to replace a wear-item
				return;
			}
			else if (bodyPart == L2Item.SLOT_LR_HAND)
			{
				// This may not remove left OR right hand equipment
				tempItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
				if (tempItem != null && tempItem.isWear())
					return;
				
				tempItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
				if (tempItem != null && tempItem.isWear())
					return;
			}
			else if (bodyPart == L2Item.SLOT_FULL_ARMOR)
			{
				// This may not remove chest or leggings
				tempItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
				if (tempItem != null && tempItem.isWear())
					return;
				
				tempItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS);
				if (tempItem != null && tempItem.isWear())
					return;
			}
			
			if (item.getEnchantLevel() > 0)
			{
				sm = new SystemMessage(SystemMessageId.S1_S2_EQUIPPED);
				sm.addNumber(item.getEnchantLevel());
				sm.addItemName(item);
			}
			else
			{
				sm = new SystemMessage(SystemMessageId.S1_EQUIPPED);
				sm.addItemName(item);
			}
			sendPacket(sm);
			
			if ((bodyPart & L2Item.SLOT_HEAD) > 0 || (bodyPart & L2Item.SLOT_NECK) > 0
					|| (bodyPart & L2Item.SLOT_L_EAR) > 0 || (bodyPart & L2Item.SLOT_R_EAR) > 0
					|| (bodyPart & L2Item.SLOT_L_FINGER) > 0 || (bodyPart & L2Item.SLOT_R_FINGER) > 0
					|| (bodyPart & L2Item.SLOT_R_BRACELET) > 0 || (bodyPart & L2Item.SLOT_L_BRACELET) > 0
					|| (bodyPart & L2Item.SLOT_DECO) > 0)
			{
				// must be sent explicitly before IU
				sendPacket(new UserInfo(this));
			}
			
			items = getInventory().equipItemAndRecord(item);
			
			// Consume mana - will start a task if required; returns if item is not a shadow item
			item.decreaseMana(false);
		}
		
		refreshExpertisePenalty();
		
		InventoryUpdate iu = new InventoryUpdate();
		iu.addEquipItems(items);
		sendPacket(iu);
		
		// must be sent explicitly after IU
		sendPacket(new UserInfo(this));
		// send 3rd time, just like retail
		broadcastUserInfo();
		
		if (abortAttack)
			abortAttack();
		
		if (getInventoryLimit() != oldInvLimit)
			sendPacket(new ExStorageMaxCount(this));
	}
	
	/** Return the Experience of the L2Player. */
	public long getExp()
	{
		return getStat().getExp();
	}
	
	public void setActiveEnchantItem(L2ItemInstance scroll)
	{
		// If we dont have a Enchant Item, we are not enchanting.
		if (scroll == null)
		{
			setActiveEnchantSupportItem(null);
			setActiveEnchantTimestamp(0);
			setIsEnchanting(false);
		}
		
		_activeEnchantItem = scroll;
	}
	
	public L2ItemInstance getActiveEnchantItem()
	{
		return _activeEnchantItem;
	}
	
	public void setActiveEnchantSupportItem(L2ItemInstance item)
	{
		_activeEnchantSupportItem = item;
	}
	
	public L2ItemInstance getActiveEnchantSupportItem()
	{
		return _activeEnchantSupportItem;
	}
	
	public long getActiveEnchantTimestamp()
	{
		return _activeEnchantTimestamp;
	}
	
	public void setActiveEnchantTimestamp(long val)
	{
		_activeEnchantTimestamp = val;
	}
	
	public void setActiveEnchantAttrItem(L2ItemInstance stone)
	{
		_activeEnchantAttrItem = stone;
	}
	
	public L2ItemInstance getActiveEnchantAttrItem()
	{
		return _activeEnchantAttrItem;
	}
	
	public void setIsEnchanting(boolean val)
	{
		_isEnchanting = val;
	}
	
	public boolean isEnchanting()
	{
		return _isEnchanting;
	}
	
	/**
	 * Set the fists weapon of the L2Player (used when no weapon is equipped).<BR><BR>
	 *
	 * @param weaponItem The fists L2Weapon to set to the L2Player
	 *
	 */
	public void setFistsWeaponItem(L2Weapon weaponItem)
	{
		_fistsWeaponItem = weaponItem;
	}
	
	/**
	 * Return the fists weapon of the L2Player (used when no weapon is equipped).<BR><BR>
	 */
	public L2Weapon getFistsWeaponItem()
	{
		return _fistsWeaponItem;
	}
	
	/**
	 * Return the fists weapon of the L2Player Class (used when no weapon is equipped).<BR><BR>
	 */
	public L2Weapon findFistsWeaponItem(int classId)
	{
		L2Weapon weaponItem = null;
		if ((classId >= 0x00) && (classId <= 0x09))
		{
			// Human fighter fists
			L2Item temp = ItemTable.getInstance().getTemplate(246);
			weaponItem = (L2Weapon)temp;
		}
		else if ((classId >= 0x0a) && (classId <= 0x11))
		{
			// Human mage fists
			L2Item temp = ItemTable.getInstance().getTemplate(251);
			weaponItem = (L2Weapon)temp;
		}
		else if ((classId >= 0x12) && (classId <= 0x18))
		{
			// Elven fighter fists
			L2Item temp = ItemTable.getInstance().getTemplate(244);
			weaponItem = (L2Weapon)temp;
		}
		else if ((classId >= 0x19) && (classId <= 0x1e))
		{
			// Elven mage fists
			L2Item temp = ItemTable.getInstance().getTemplate(249);
			weaponItem = (L2Weapon)temp;
		}
		else if ((classId >= 0x1f) && (classId <= 0x25))
		{
			// Dark elven fighter fists
			L2Item temp = ItemTable.getInstance().getTemplate(245);
			weaponItem = (L2Weapon)temp;
		}
		else if ((classId >= 0x26) && (classId <= 0x2b))
		{
			// Dark elven mage fists
			L2Item temp = ItemTable.getInstance().getTemplate(250);
			weaponItem = (L2Weapon)temp;
		}
		else if ((classId >= 0x2c) && (classId <= 0x30))
		{
			// Orc fighter fists
			L2Item temp = ItemTable.getInstance().getTemplate(248);
			weaponItem = (L2Weapon)temp;
		}
		else if ((classId >= 0x31) && (classId <= 0x34))
		{
			// Orc mage fists
			L2Item temp = ItemTable.getInstance().getTemplate(252);
			weaponItem = (L2Weapon)temp;
		}
		else if ((classId >= 0x35) && (classId <= 0x39))
		{
			// Dwarven fists
			L2Item temp = ItemTable.getInstance().getTemplate(247);
			weaponItem = (L2Weapon)temp;
		}
		
		return weaponItem;
	}
	
	/**
	 * Give Expertise skill of this level and remove beginner Lucky skill.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Get the Level of the L2Player </li>
	 * <li>If L2Player Level is 5, remove beginner Lucky skill </li>
	 * <li>Add the Expertise skill corresponding to its Expertise level</li>
	 * <li>Update the overloaded status of the L2Player</li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T give other free skills (SP needed = 0)</B></FONT><BR><BR>
	 *
	 */
	public void rewardSkills()
	{
		// Get the Level of the L2Player
		int lvl = getLevel();
		
		// Remove beginner Lucky skill
		if (lvl > 9)
		{
			L2Skill skill = SkillTable.getInstance().getInfo(194, 1);
			skill = removeSkill(skill);
			
			if (_log.isDebugEnabled() && skill != null)
				_log.debug("removed skill 'Lucky' from " + getName());
		}
		
		// Calculate the current higher Expertise of the L2Player
		for (int i = 0; i < EXPERTISE_LEVELS.length; i++)
		{
			if (lvl >= EXPERTISE_LEVELS[i])
				setExpertiseIndex(i);
		}
		
		// Add the Expertise skill corresponding to its Expertise level
		if (getExpertiseIndex() > 0)
		{
			L2Skill skill = SkillTable.getInstance().getInfo(239, getExpertiseIndex());
			addSkill(skill);
			
			if (_log.isDebugEnabled())
				_log.debug("awarded " + getName() + " with new expertise.");
			
		}
		else
		{
			if (_log.isDebugEnabled())
				_log.debug("No skills awarded at lvl: " + lvl);
		}
		
		// Active skill dwarven craft
		if (getSkillLevel(1321) < 1 && getRace() == Race.Dwarf)
		{
			L2Skill skill = SkillTable.getInstance().getInfo(1321, 1);
			addSkill(skill);
		}
		
		// Active skill common craft
		if (getSkillLevel(1322) < 1)
		{
			L2Skill skill = SkillTable.getInstance().getInfo(1322, 1);
			addSkill(skill);
		}
		
		for (int i = 0; i < COMMON_CRAFT_LEVELS.length; i++)
		{
			if (lvl >= COMMON_CRAFT_LEVELS[i] && getSkillLevel(1320) < (i + 1))
			{
				L2Skill skill = SkillTable.getInstance().getInfo(1320, (i + 1));
				addSkill(skill);
			}
		}
		
		// Auto-Learn skills if activated
		if (Config.ALT_AUTO_LEARN_SKILLS && !isCursedWeaponEquipped())
			sendMessage("You have learned " + SkillTreeTable.getInstance().giveAvailableSkills(this) + ".");
		
		if (isGM() && !hasSkill(7029))
		{
			addSkill(SkillTable.getInstance().getInfo(7029, 4));
		}
	}
	
	/** Set the Experience value of the L2Player. */
	public void setExp(long exp)
	{
		getStat().setExp(exp);
	}
	
	/**
	 * Regive all skills which aren't saved to database, like Noble, Hero, Clan Skills<BR><BR>
	 *
	 */
	public void regiveTemporarySkills()
	{
		// Do not call this on enterworld or char load
		
		// Add noble skills if noble
		if (isNoble())
			setNoble(true);
		
		// Add Hero skills if hero
		if (isHero())
			setHero(true);
		
		if (getClan() != null)
		{
			setPledgeClass(L2ClanMember.getCurrentPledgeClass(this));
			getClan().addSkillEffects(this, false);
			PledgeSkillList psl = new PledgeSkillList(getClan());
			sendPacket(psl);
			if (getClan().getLevel() >= Config.SIEGE_CLAN_MIN_LEVEL && isClanLeader())
				SiegeManager.getInstance().addSiegeSkills(this);
			enableResidentialSkills(true);
		}
		
		// Reload passive skills from armors / jewels / weapons
		getInventory().reloadEquippedItems();
		
		// Add Death Penalty Buff Level
		restoreDeathPenaltyBuffLevel();
	}
	
	public void enableResidentialSkills(boolean enable)
	{
		if (getClan() == null)
			return;
		
		if (enable)
		{
			if (getClan().getHasCastle() > 0)
				CastleManager.getInstance().getCastleByOwner(getClan()).giveResidentialSkills(this);
			
			if (getClan().getHasFort() > 0)
				FortManager.getInstance().getFortByOwner(getClan()).giveResidentialSkills(this);
		}
		else
		{
			if (getClan().getHasCastle() > 0)
				CastleManager.getInstance().getCastleByOwner(getClan()).removeResidentialSkills(this);
			if (getClan().getHasFort() > 0)
				FortManager.getInstance().getFortByOwner(getClan()).removeResidentialSkills(this);
		}
	}
	
	/**
	 * Return the Race object of the L2Player.<BR><BR>
	 */
	public Race getRace()
	{
		if (!isSubClassActive())
			return getTemplate().getRace();
		
		L2PlayerTemplate charTemp = CharTemplateTable.getInstance().getTemplate(_baseClass);
		return charTemp.getRace();
	}
	
	public L2Marker getRadar()
	{
		if (_radar == null)
			_radar = new L2Marker(this);
		
		return _radar;
	}
	
	/** Return the SP amount of the L2Player. */
	public int getSp()
	{
		return getStat().getSp();
	}
	
	/** Set the SP amount of the L2Player. */
	public void setSp(int sp)
	{
		getStat().setSp(sp);
	}
	
	/**
	 * Return true if this L2Player is a clan leader in
	 * ownership of the passed castle
	 */
	public boolean isCastleLord(int castleId)
	{
		L2Clan clan = getClan();
		// Player has clan and is the clan leader, check the castle info
		if ((clan != null) && (clan.getLeader().getPlayerInstance() == this))
		{
			// If the clan has a castle and it is actually the queried castle, return true
			Castle castle = CastleManager.getInstance().getCastleByOwner(clan);
			if ((castle != null) && (castle == CastleManager.getInstance().getCastleById(castleId)))
				return true;
		}
		return false;
	}
	
	/**
	 * Return the Clan Identifier of the L2Player.<BR><BR>
	 */
	public int getClanId()
	{
		return _clanId;
	}
	
	/**
	 * Return the Clan Crest Identifier of the L2Player or 0.<BR><BR>
	 */
	public int getClanCrestId()
	{
		if (_clan != null && _clan.hasCrest())
		{
			return _clan.getCrestId();
		}
		return 0;
	}
	
	/**
	 * @return The Clan CrestLarge Identifier or 0
	 */
	public int getClanCrestLargeId()
	{
		if (_clan != null && _clan.hasCrestLarge())
		{
			return _clan.getCrestLargeId();
		}
		return 0;
	}
	
	public long getClanJoinExpiryTime()
	{
		return _clanJoinExpiryTime;
	}
	
	public void setClanJoinExpiryTime(long time)
	{
		_clanJoinExpiryTime = time;
	}
	
	public long getClanCreateExpiryTime()
	{
		return _clanCreateExpiryTime;
	}
	
	public void setClanCreateExpiryTime(long time)
	{
		_clanCreateExpiryTime = time;
	}
	
	public void setOnlineTime(long time)
	{
		_onlineTime = time;
		_onlineBeginTime = System.currentTimeMillis();
	}
	
	/**
	 * Return the PlayerInventory Inventory of the L2Player contained in _inventory.<BR><BR>
	 */
	@Override
	public PlayerInventory getInventory()
	{
		if (_inventory == null)
			_inventory = new PlayerInventory(this);
		
		return _inventory;
	}
	
	/**
	 * Delete a ShortCut of the L2Player shortCuts.<BR><BR>
	 */
	public void removeItemFromShortCut(int objectId)
	{
		getShortCuts().deleteShortCutByObjectId(objectId);
	}
	
	/**
	 * Return True if the L2Player is sitting.<BR><BR>
	 */
	public boolean isSitting()
	{
		return _waitTypeSitting;
	}
	
	public void sitDown()
	{
		sitDown(true);
	}
	
	/**
	 * Sit down the L2Player, set the AI Intention to AI_INTENTION_REST and send a Server->Client ChangeWaitType packet (broadcast)<BR><BR>
	 */
	public void sitDown(boolean force)
	{
		if ((isCastingNow() || isCastingSimultaneouslyNow()) && !_relax)
		{
			sendMessage("Cannot sit while casting");
			return;
		}
		if (!(_waitTypeSitting || super.isAttackingDisabled() || isOutOfControl() || isImmobilized() || (!force && isTryingToSitOrStandup())))
		{
			breakAttack();
			_waitTypeSitting = true;
			getAI().setIntention(CtrlIntention.AI_INTENTION_REST);
			broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_SITTING));
			_lastSitStandRequest = System.currentTimeMillis();
		}
	}
	
	public void standUp()
	{
		standUp(true);
	}
	
	/**
	 * Stand up the L2Player, set the AI Intention to AI_INTENTION_IDLE and send a Server->Client ChangeWaitType packet (broadcast)<BR><BR>
	 */
	public void standUp(boolean force)
	{
		if (!GlobalRestrictions.canStandUp(this))
			return;
		
		if (_waitTypeSitting && !isInStoreMode() && !isAlikeDead() && (!isTryingToSitOrStandup() || force))
		{
			if (_relax)
			{
				setRelax(false);
				stopEffects(L2EffectType.RELAXING);
			}
			_waitTypeSitting = false;
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
			broadcastPacket(new ChangeWaitType(this, ChangeWaitType.WT_STANDING));
			_lastSitStandRequest = System.currentTimeMillis();
		}
	}
	
	/**
	 * Set the value of the _relax value. Must be True if using skill Relax and False if not.
	 */
	public void setRelax(boolean val)
	{
		_relax = val;
	}
	
	/**
	 * Return the PlayerWarehouse object of the L2Player.<BR><BR>
	 */
	public PlayerWarehouse getWarehouse()
	{
		if (_warehouse == null)
		{
			_warehouse = new PlayerWarehouse(this);
			_warehouse.restore();
		}
		
		if (Config.WAREHOUSE_CACHE)
			WarehouseCacheManager.getInstance().add(this);
		
		return _warehouse;
	}
	
	/**
	 * Free memory used by Warehouse
	 */
	public void clearWarehouse()
	{
		if (_warehouse != null)
			_warehouse.deleteMe();
		_warehouse = null;
	}
	
	/**
	 * Return the PcFreight object of the L2Player.<BR><BR>
	 */
	public PlayerFreight getFreight()
	{
		if (_freight == null)
		{
			_freight = new PlayerFreight(this);
			_freight.restore();
		}
		
		return _freight;
	}
	
	/**
	 * Free memory used by Freight
	 */
	public void clearFreight()
	{
		if (_freight != null)
			_freight.deleteMe();
		_freight = null;
	}
	
	/**
	 * Return deposited PcFreight object for the objectId
	 * or create new if not exist
	 */
	public PlayerFreight getDepositedFreight(int objectId)
	{
		if (_depositedFreight == null)
			_depositedFreight = new ArrayList<PlayerFreight>();
		else
		{
			for (PlayerFreight freight : _depositedFreight)
			{
				if (freight != null && freight.getOwnerId() == objectId)
					return freight;
			}
		}
		
		PlayerFreight freight = new PlayerFreight(null);
		freight.doQuickRestore(objectId);
		_depositedFreight.add(freight);
		return freight;
	}
	
	/**
	 * Clear memory used by deposited freight
	 */
	public void clearDepositedFreight()
	{
		if (_depositedFreight == null)
			return;
		
		for (PlayerFreight freight : _depositedFreight)
		{
			if (freight != null)
			{
				try
				{
					freight.deleteMe();
				}
				catch (Exception e)
				{
					_log.error("clearDepositedFreight()", e);
				}
			}
		}
		_depositedFreight.clear();
		_depositedFreight = null;
	}
	
	/**
	 * Return the Identifier of the L2Player.<BR><BR>
	 */
	public int getCharId()
	{
		return _charId;
	}
	
	/**
	 * Set the Identifier of the L2Player.<BR><BR>
	 */
	public void setCharId(int charId)
	{
		_charId = charId;
	}
	
	/**
	 * Return the Adena amount of the L2Player.<BR><BR>
	 */
	public long getAdena()
	{
		return getInventory().getAdena();
	}
	
	/**
	 * Return the Ancient Adena amount of the L2Player.<BR><BR>
	 */
	public long getAncientAdena()
	{
		return getInventory().getAncientAdena();
	}
	
	/**
	 * Add adena to Inventory of the L2Player and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param count : long Quantity of adena to be added
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 */
	public void addAdena(String process, long count, L2Object reference, boolean sendMessage)
	{
		if (count > 0)
		{
			getInventory().addAdena(process, count, this, reference);
			
			if (sendMessage)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S1_ADENA);
				sm.addItemNumber(count);
				sendPacket(sm);
			}
			
			// Send update packet
			getInventory().updateInventory(getInventory().getAdenaInstance());
		}
	}
	
	/**
	 * Reduce adena in Inventory of the L2Player and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param count : long Quantity of adena to be reduced
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean reduceAdena(String process, long count, L2Object reference, boolean sendMessage)
	{
		if (count > getAdena())
		{
			if (sendMessage)
				sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
			return false;
		}
		
		if (count > 0)
		{
			L2ItemInstance adenaItem = getInventory().getAdenaInstance();
			getInventory().reduceAdena(process, count, this, reference);
			
			// Send update packet
			getInventory().updateInventory(adenaItem);
			
			if (sendMessage)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_ADENA_DISAPPEARED);
				sm.addItemNumber(count);
				sendPacket(sm);
			}
		}
		
		return true;
	}
	
	/**
	 * Add ancient adena to Inventory of the L2Player and send a Server->Client InventoryUpdate packet to the L2Player.
	 *
	 * @param process : String Identifier of process triggering this action
	 * @param count : long Quantity of ancient adena to be added
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 */
	public void addAncientAdena(String process, long count, L2Object reference, boolean sendMessage)
	{
		if (sendMessage)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
			sm.addItemName(PlayerInventory.ANCIENT_ADENA_ID);
			sm.addItemNumber(count);
			sendPacket(sm);
		}
		
		if (count > 0)
		{
			getInventory().addAncientAdena(process, count, this, reference);
			getInventory().updateInventory(getInventory().getAncientAdenaInstance());
		}
	}
	
	/**
	 * Reduce ancient adena in Inventory of the L2Player and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param count : long Quantity of ancient adena to be reduced
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean reduceAncientAdena(String process, long count, L2Object reference, boolean sendMessage)
	{
		if (count > getAncientAdena())
		{
			if (sendMessage)
				sendPacket(SystemMessageId.YOU_NOT_ENOUGH_ADENA);
			
			return false;
		}
		
		if (count > 0)
		{
			getInventory().reduceAncientAdena(process, count, this, reference);
			getInventory().updateInventory(getInventory().getAncientAdenaInstance());
			if (sendMessage)
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
				sm.addItemName(PlayerInventory.ANCIENT_ADENA_ID);
				sm.addItemNumber(count);
				sendPacket(sm);
			}
		}
		
		return true;
	}
	
	/**
	 * Adds item to inventory and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param item : L2ItemInstance to be added
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 */
	public L2ItemInstance addItem(String process, L2ItemInstance item, L2Object reference, boolean sendMessage,
			boolean UpdateIL)
	{
		if (item.getCount() > 0)
		{
			// Sends message to client if requested
			if (sendMessage)
			{
				if (item.getCount() > 1)
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
					sm.addItemName(item);
					sm.addItemNumber(item.getCount());
					sendPacket(sm);
				}
				else if (item.getEnchantLevel() > 0)
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_A_S1_S2);
					sm.addNumber(item.getEnchantLevel());
					sm.addItemName(item);
					sendPacket(sm);
				}
				else
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1);
					sm.addItemName(item);
					sendPacket(sm);
				}
			}
			
			// Add the item to inventory
			L2ItemInstance newitem = getInventory().addItem(process, item, this, reference);
			
			// Do treatments after adding this item
			processAddItem(UpdateIL, newitem);
			return newitem;
		}
		return null;
	}
	
	/**
	 * Adds item to Inventory and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item Identifier of the item to be added
	 * @param count : long Quantity of items to be added
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 */
	public L2ItemInstance addItem(String process, int itemId, long count, L2Object reference, boolean sendMessage,
			boolean UpdateIL)
	{
		if (count > 0)
		{
			// Add the item to inventory
			L2ItemInstance newItem = getInventory().addItem(process, itemId, count, this, reference);
			
			// Sends message to client if requested
			if (sendMessage)
			{
				sendMessageForNewItem(newItem, count, process);
			}
			
			processAddItem(UpdateIL, newItem);
			return newItem;
		}
		return null;
	}
	
	/**
	 * @param UpdateIL
	 * @param newitem
	 */
	private void processAddItem(boolean UpdateIL, L2ItemInstance newitem)
	{
		// If over capacity, drop the item
		if (!isGM() && !getInventory().validateCapacity(0) && newitem.isDropable())
		{
			dropItem("InvDrop", newitem, null, true);
		}
		// Cursed Weapon
		else if (CursedWeaponsManager.getInstance().isCursed(newitem.getItemId()))
		{
			if (!CursedWeaponsManager.getInstance().activate(this, newitem))
				dropItem("CwDrop", newitem, null, true);
		}
		// Combat Flag
		else if (FortSiegeManager.getInstance().isCombat(newitem.getItemId()))
		{
			if (FortSiegeManager.getInstance().activateCombatFlag(this, newitem))
			{
				Fort fort = FortManager.getInstance().getFort(this);
				if (fort != null)
					fort.getSiege()
							.announceToPlayer(new SystemMessage(SystemMessageId.C1_ACQUIRED_THE_FLAG), getName());
			}
			//else // FIXME: i'm not sure about this
			//	dropItem("CombatFlagDrop", newitem, null, true);
		}
		
		// Auto use herbs - autoloot
		else if (newitem.getItemType() == L2EtcItemType.HERB)
		{
			ItemHandler.getInstance().useItem(newitem.getItemId(), this, newitem);
		}
		
		// Update current load as well
		if (UpdateIL)
		{
			StatusUpdate su = new StatusUpdate(getObjectId());
			su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
			sendPacket(su);
		}
		
		// Send inventory update packet
		if (!Config.FORCE_INVENTORY_UPDATE)
		{
			InventoryUpdate playerIU = new InventoryUpdate();
			playerIU.addItem(newitem);
			sendPacket(playerIU);
		}
		else
			sendPacket(new ItemList(this, false));
	}
	
	/**
	 * @param item : L2ItemInstance Item Identifier of the item to be added
	 * @param count : long Quantity of items to be added
	 * @param process : String Identifier of process triggering this action
	 */
	private void sendMessageForNewItem(L2ItemInstance item, long count, String process)
	{
		if (count > 1)
		{
			if (process.equalsIgnoreCase("sweep") || process.equalsIgnoreCase("Quest"))
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
				sm.addItemName(item);
				sm.addItemNumber(count);
				sendPacket(sm);
			}
			else
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1_S2);
				sm.addItemName(item);
				sm.addItemNumber(count);
				sendPacket(sm);
			}
		}
		else
		{
			if (process.equalsIgnoreCase("sweep") || process.equalsIgnoreCase("Quest"))
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S1);
				sm.addItemName(item);
				sendPacket(sm);
			}
			else
			{
				SystemMessage sm = new SystemMessage(SystemMessageId.YOU_PICKED_UP_S1);
				sm.addItemName(item);
				sendPacket(sm);
			}
		}
	}
	
	public void addItem(String process, L2ItemInstance item, L2Object reference, boolean sendMessage)
	{
		addItem(process, item, reference, sendMessage, true);
	}
	
	public void addItem(String process, int itemId, long count, L2Object reference, boolean sendMessage)
	{
		addItem(process, itemId, count, reference, sendMessage, true);
	}
	
	/**
	 * Destroy item from inventory and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param item : L2ItemInstance to be destroyed
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean destroyItem(String process, L2ItemInstance item, L2Object reference, boolean sendMessage)
	{
		return this.destroyItem(process, item, item.getCount(), reference, sendMessage);
	}
	
	/**
	 * Destroy item from inventory and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param item : L2ItemInstance to be destroyed
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean
			destroyItem(String process, L2ItemInstance item, long count, L2Object reference, boolean sendMessage)
	{
		item = getInventory().destroyItem(process, item, count, this, reference);
		
		if (item == null)
		{
			if (sendMessage)
			{
				sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			}
			return false;
		}
		
		// Send inventory update packet
		if (!Config.FORCE_INVENTORY_UPDATE)
		{
			InventoryUpdate playerIU = new InventoryUpdate();
			playerIU.addItem(item);
			sendPacket(playerIU);
		}
		else
		{
			sendPacket(new ItemList(this, false));
		}
		
		// Update current load as well
		StatusUpdate su = new StatusUpdate(getObjectId());
		su.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
		sendPacket(su);
		
		// Sends message to client if requested
		if (sendMessage)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
			sm.addItemName(item);
			sm.addItemNumber(count);
			sendPacket(sm);
		}
		
		return true;
	}
	
	/**
	 * Destroys item from inventory and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param objectId : int Item Instance identifier of the item to be destroyed
	 * @param count : long Quantity of items to be destroyed
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	@Override
	public boolean destroyItem(String process, int objectId, long count, L2Object reference, boolean sendMessage)
	{
		L2ItemInstance item = getInventory().getItemByObjectId(objectId);
		if (item == null)
		{
			if (sendMessage)
				sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return false;
		}
		
		return destroyItem(process, item, count, reference, sendMessage);
	}
	
	/**
	 * Destroys shots from inventory without logging and only occasional saving to database.
	 * Sends a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param objectId : int Item Instance identifier of the item to be destroyed
	 * @param count : long Quantity of items to be destroyed
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean destroyItemWithoutTrace(String process, int objectId, long count, L2Object reference,
			boolean sendMessage)
	{
		L2ItemInstance item = getInventory().getItemByObjectId(objectId);
		
		if (item == null || item.getCount() < count)
		{
			if (sendMessage)
				sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return false;
		}
		
		return destroyItem(null, item, count, reference, sendMessage);
	}
	
	/**
	 * Destroy item from inventory by using its <B>itemId</B> and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item identifier of the item to be destroyed
	 * @param count : long Quantity of items to be destroyed
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	@Override
	public boolean destroyItemByItemId(String process, int itemId, long count, L2Object reference, boolean sendMessage)
	{
		L2ItemInstance item = getInventory().getItemByItemId(itemId);
		if (item == null || item.getCount() < count
				|| getInventory().destroyItemByItemId(process, itemId, count, this, reference) == null)
		{
			if (sendMessage)
				sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			
			return false;
		}
		
		// Send inventory update packet
		getInventory().updateInventory(item);
		
		// Sends message to client if requested
		if (sendMessage)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
			sm.addItemName(item);
			sm.addItemNumber(count);
			sendPacket(sm);
		}
		return true;
	}
	
	/**
	 * Destroy all weared items from inventory and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public void destroyWearedItems(String process, L2Object reference, boolean sendMessage)
	{
		
		// Go through all Items of the inventory
		for (L2ItemInstance item : getInventory().getItems())
		{
			// Check if the item is a Try On item in order to remove it
			if (item.isWear())
			{
				if (item.isEquipped())
					getInventory().unEquipItemInSlotAndRecord(item.getLocationSlot());
				
				if (getInventory().destroyItem(process, item, this, reference) == null)
				{
					_log.warn("Player " + getName() + " can't destroy weared item: " + item.getName() + "[ "
							+ item.getObjectId() + " ]");
					continue;
				}
				
				// Send an Unequipped Message in system window of the player for each Item
				SystemMessage sm = new SystemMessage(SystemMessageId.S1_DISARMED);
				sm.addItemName(item);
				sendPacket(sm);
				
			}
		}
		
		// Send the ItemList Server->Client Packet to the player in order to refresh its Inventory
		ItemList il = new ItemList(getInventory().getItems(), true);
		sendPacket(il);
		
		// Send a Server->Client packet UserInfo to this L2Player and CharInfo to all L2Player in its _knownPlayers
		broadcastUserInfo();
		
		// Sends message to client if requested
		sendMessage("Trying-on mode has ended.");
		
	}
	
	/**
	 * Transfers item to another ItemContainer and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param objectId : int Item Identifier of the item to be transfered
	 * @param count : long Quantity of items to be transfered
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return L2ItemInstance corresponding to the new item or the updated item in inventory
	 */
	public L2ItemInstance transferItem(String process, int objectId, long count, Inventory target, L2Object reference)
	{
		L2ItemInstance oldItem = checkItemManipulation(objectId, count, "transfer");
		if (oldItem == null)
			return null;
		L2ItemInstance newItem = getInventory().transferItem(process, objectId, count, target, this, reference);
		if (newItem == null)
			return null;
		
		// Send inventory update packet
		if (!Config.FORCE_INVENTORY_UPDATE)
		{
			InventoryUpdate playerIU = new InventoryUpdate();
			
			if (oldItem.getCount() > 0 && oldItem != newItem)
				playerIU.addModifiedItem(oldItem);
			else
				playerIU.addRemovedItem(oldItem);
			
			sendPacket(playerIU);
		}
		else
			sendPacket(new ItemList(this, false));
		
		// Update current load as well
		StatusUpdate playerSU = new StatusUpdate(getObjectId());
		playerSU.addAttribute(StatusUpdate.CUR_LOAD, getCurrentLoad());
		sendPacket(playerSU);
		
		// Send target update packet
		if (target instanceof PlayerInventory)
		{
			L2Player targetPlayer = ((PlayerInventory)target).getOwner();
			
			if (!Config.FORCE_INVENTORY_UPDATE)
			{
				InventoryUpdate playerIU = new InventoryUpdate();
				
				if (newItem.getCount() > count)
					playerIU.addModifiedItem(newItem);
				else
					playerIU.addNewItem(newItem);
				
				targetPlayer.sendPacket(playerIU);
			}
			else
				targetPlayer.sendPacket(new ItemList(targetPlayer, false));
			
			// Update current load as well
			playerSU = new StatusUpdate(targetPlayer.getObjectId());
			playerSU.addAttribute(StatusUpdate.CUR_LOAD, targetPlayer.getCurrentLoad());
			targetPlayer.sendPacket(playerSU);
		}
		else if (target instanceof PetInventory)
		{
			PetInventoryUpdate petIU = new PetInventoryUpdate();
			
			if (newItem.getCount() > count)
				petIU.addModifiedItem(newItem);
			else
				petIU.addNewItem(newItem);
			
			((PetInventory)target).getOwner().getOwner().sendPacket(petIU);
		}
		
		return newItem;
	}
	
	/**
	 * Drop item from inventory and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param item : L2ItemInstance to be dropped
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return boolean informing if the action was successfull
	 */
	public boolean dropItem(String process, L2ItemInstance item, L2Object reference, boolean sendMessage)
	{
		item = getInventory().dropItem(process, item, this, reference);
		
		if (item == null)
		{
			if (sendMessage)
				sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return false;
		}
		
		item.dropMe(this, getX() + Rnd.get(50) - 25, getY() + Rnd.get(50) - 25, getZ() + 20);
		
		if (Config.DESTROY_DROPPED_PLAYER_ITEM)
		{
			ItemsAutoDestroyManager.tryAddItem(item);
			
			if (!item.isEquipable() || (item.isEquipable() && Config.DESTROY_EQUIPABLE_PLAYER_ITEM))
				item.setProtected(false);
			else
				item.setProtected(true);
		}
		else
			item.setProtected(true);
		
		// Send inventory update packet
		getInventory().updateInventory(item);
		
		// Sends message to client if requested
		if (sendMessage)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.YOU_DROPPED_S1);
			sm.addItemName(item);
			sendPacket(sm);
		}
		
		return true;
	}
	
	/**
	 * Drop item from inventory by using its <B>objectID</B> and send a Server->Client InventoryUpdate packet to the L2Player.
	 * @param process : String Identifier of process triggering this action
	 * @param objectId : int Item Instance identifier of the item to be dropped
	 * @param count : long Quantity of items to be dropped
	 * @param x : int coordinate for drop X
	 * @param y : int coordinate for drop Y
	 * @param z : int coordinate for drop Z
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @param sendMessage : boolean Specifies whether to send message to Client about this action
	 * @return L2ItemInstance corresponding to the new item or the updated item in inventory
	 */
	public L2ItemInstance dropItem(String process, int objectId, long count, int x, int y, int z, L2Object reference,
			boolean sendMessage)
	{
		L2ItemInstance olditem = getInventory().getItemByObjectId(objectId);
		L2ItemInstance item = getInventory().dropItem(process, objectId, count, this, reference);
		
		if (item == null)
		{
			if (sendMessage)
				sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			
			return null;
		}
		
		item.dropMe(this, x, y, z);
		// Destroy item droped from inventory by player when DESTROY_PLAYER_INVENTORY_DROP is set to true
		if (Config.DESTROY_PLAYER_INVENTORY_DROP)
		{
			ItemsAutoDestroyManager.tryAddItem(item);
			
			item.setProtected(false);
		}
		// Avoids it from beeing removed by the auto item destroyer
		else
			item.setDropTime(0);
		
		// Send inventory update packet
		getInventory().updateInventory(olditem);
		
		// Sends message to client if requested
		if (sendMessage)
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.YOU_DROPPED_S1);
			sm.addItemName(item);
			sendPacket(sm);
		}
		return item;
	}
	
	public L2ItemInstance checkItemManipulation(int objectId, long count, String action)
	{
		L2ItemInstance item = getInventory().getItemByObjectId(objectId);
		if (item == null)
		{
			_log.debug(getObjectId() + ": player tried to " + action + " item he is not owner of");
			return null;
		}
		
		if (count < 0 || (count > 1 && !item.isStackable()))
		{
			_log.debug(getObjectId() + ": player tried to " + action + " item with invalid count: " + count);
			return null;
		}
		
		if (count > item.getCount())
		{
			_log.debug(getObjectId() + ": player tried to " + action + " more items than he owns");
			return null;
		}
		
		// Pet is summoned and not the item that summoned the pet AND not the buggle from strider you're mounting
		if (getPet() != null && getPet().getControlItemId() == objectId || getMountObjectID() == objectId)
		{
			if (_log.isDebugEnabled())
				_log.debug(getObjectId() + ": player tried to " + action + " item controling pet");
			return null;
		}
		
		if (getActiveEnchantItem() != null && getActiveEnchantItem().getObjectId() == objectId)
		{
			if (_log.isDebugEnabled())
				_log.debug(getObjectId() + ":player tried to " + action + " an enchant scroll he was using");
			return null;
		}
		
		if (item.isWear())
		{
			// Cannot drop/trade wear-items
			return null;
		}
		
		// We cannot put a Weapon with Augmention in WH while casting (Possible Exploit)
		if (item.isAugmented() && (isCastingNow() || isCastingSimultaneouslyNow()))
			return null;
		
		return item;
	}
	
	/**
	 * Set _protectEndTime according settings.
	 */
	public void setProtection(boolean protect)
	{
		int proTime = AutomatedTvT.isPlaying(this) ? Config.AUTO_TVT_SPAWN_PROTECT : Config.PLAYER_SPAWN_PROTECTION;
		if (protect && (proTime == 0 || isInOlympiadMode()))
			return;
		
		if (_log.isDebugEnabled() && (protect || _protectEndTime > 0))
			_log.debug(getName()
					+ ": Protection "
					+ (protect ? "ON "
							+ (GameTimeManager.getGameTicks() + proTime * GameTimeManager.TICKS_PER_SECOND)
							: "OFF") + " (currently " + GameTimeManager.getGameTicks() + ")");
		
		_protectEndTime =
				protect ? GameTimeManager.getGameTicks() + proTime * GameTimeManager.TICKS_PER_SECOND : 0;
	}
	
	public long getProtection()
	{
		return _protectEndTime;
	}
	
	/**
	 * Set protection from agro mobs when getting up from fake death, according settings.
	 */
	public void setRecentFakeDeath(boolean protect)
	{
		_recentFakeDeathEndTime =
				protect ? GameTimeManager.getGameTicks() + Config.PLAYER_FAKEDEATH_UP_PROTECTION
						* GameTimeManager.TICKS_PER_SECOND : 0;
	}
	
	public boolean isRecentFakeDeath()
	{
		return _recentFakeDeathEndTime > GameTimeManager.getGameTicks();
	}
	
	/**
	 * Get the client owner of this char.<BR><BR>
	 */
	public L2Client getClient()
	{
		return _client;
	}
	
	/**
	 * Set the active connection with the client.<BR><BR>
	 */
	public void setClient(L2Client client)
	{
		_client = client;
	}
	
	public Point3D getCurrentSkillWorldPosition()
	{
		SkillUsageRequest currentSkill = getCurrentSkill();
		
		return currentSkill == null ? null : currentSkill.getSkillWorldPosition();
	}
	
	public boolean canBeTargetedByAtSiege(L2Player player)
	{
		Siege siege = SiegeManager.getInstance().getSiege(this);
		if (siege != null && siege.getIsInProgress())
		{
			L2Clan selfClan = getClan();
			L2Clan oppClan = player.getClan();
			if (selfClan != null && oppClan != null)
			{
				boolean self = false;
				for (L2SiegeClan clan : siege.getAttackerClans())
				{
					L2Clan cl = ClanTable.getInstance().getClan(clan.getClanId());
					
					if (cl == selfClan || cl.getAllyId() == getAllyId())
					{
						self = true;
						break;
					}
				}
				
				for (L2SiegeClan clan : siege.getDefenderClans())
				{
					L2Clan cl = ClanTable.getInstance().getClan(clan.getClanId());
					
					if (cl == selfClan || cl.getAllyId() == getAllyId())
					{
						self = true;
						break;
					}
				}
				
				boolean opp = false;
				for (L2SiegeClan clan : siege.getAttackerClans())
				{
					L2Clan cl = ClanTable.getInstance().getClan(clan.getClanId());
					
					if (cl == oppClan || cl.getAllyId() == player.getAllyId())
					{
						opp = true;
						break;
					}
				}
				
				for (L2SiegeClan clan : siege.getDefenderClans())
				{
					L2Clan cl = ClanTable.getInstance().getClan(clan.getClanId());
					
					if (cl == oppClan || cl.getAllyId() == player.getAllyId())
					{
						opp = true;
						break;
					}
				}
				
				return self && opp;
			}
			
			return false;
		}
		
		return true;
	}
	
	/**
	 * Manage actions when a player click on this L2Player.<BR><BR>
	 *
	 * <B><U> Actions on first click on the L2Player (Select it)</U> :</B><BR><BR>
	 * <li>Set the target of the player</li>
	 * <li>Send a Server->Client packet MyTargetSelected to the player (display the select window)</li><BR><BR>
	 *
	 * <B><U> Actions on second click on the L2Player (Follow it/Attack it/Intercat with it)</U> :</B><BR><BR>
	 * <li>Send a Server->Client packet MyTargetSelected to the player (display the select window)</li>
	 * <li>If this L2Player has a Private Store, notify the player AI with AI_INTENTION_INTERACT</li>
	 * <li>If this L2Player is autoAttackable, notify the player AI with AI_INTENTION_ATTACK</li><BR><BR>
	 * <li>If this L2Player is NOT autoAttackable, notify the player AI with AI_INTENTION_FOLLOW</li><BR><BR>
	 *
	 * <B><U> Example of use </U> :</B><BR><BR>
	 * <li> Client packet : Action, AttackRequest</li><BR><BR>
	 *
	 * @param player The player that start an action on this L2Player
	 *
	 */
	@Override
	public void onAction(L2Player player)
	{
		if (player == null)
			return;
		// Restrict interactions during restart/shutdown
		if (Shutdown.isActionDisabled(DisableType.PC_ITERACTION))
		{
			sendMessage("Player interaction disabled during restart/shutdown.");
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (!GlobalRestrictions.canTarget(player, this, true))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Check if the L2Player is confused
		if (player.isOutOfControl())
		{
			// Send a Server->Client packet ActionFailed to the player
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
		// Aggression target lock effect
		if (!player.canChangeLockedTarget(this))
			return;
		
		// Check if the player already target this L2Player
		if (player.getTarget() != this)
		{
			// Set the target of the player
			player.setTarget(this);
		}
		else
		{
			if (player != this)
				player.sendPacket(new ValidateLocation(this));
			// Check if this L2Player has a Private Store
			if (getPrivateStoreType() != 0)
			{
				player.getAI().setIntention(CtrlIntention.AI_INTENTION_INTERACT, this);
			}
			else
			{
				// Check if this L2Player is autoAttackable
				if (isAutoAttackable(player))
				{
					// Player with lvl < 21 can't attack a cursed weapon holder
					// And a cursed weapon holder  can't attack players with lvl < 21
					if ((isCursedWeaponEquipped() && player.getLevel() < 21)
							|| (player.isCursedWeaponEquipped() && getLevel() < 21))
					{
						player.sendPacket(ActionFailed.STATIC_PACKET);
					}
					else
					{
						if (GeoData.getInstance().canSeeTarget(player, this))
						{
							player.getAI().setIntention(CtrlIntention.AI_INTENTION_ATTACK, this);
							player.onActionRequest();
						}
					}
				}
				else
				{
					// This Action Failed packet avoids player getting stuck when clicking three or more times
					player.sendPacket(ActionFailed.STATIC_PACKET);
					
					if (GeoData.getInstance().canSeeTarget(player, this))
						player.getAI().setIntention(CtrlIntention.AI_INTENTION_FOLLOW, this);
				}
			}
		}
	}
	
	@Override
	public void onActionShift(L2Player gm)
	{
		gm.sendPacket(ActionFailed.STATIC_PACKET);
		if (gm.isGM())
		{
			if (this != gm.getTarget())
			{
				gm.setTarget(this);
			}
			else
			{
				AdminEditChar.gatherCharacterInfo(gm, this, "charinfo.htm");
			}
		}
	}
	
	@Override
	public boolean isInFunEvent()
	{
		final AbstractFunEventPlayerInfo info = _playerInfoForEvents;
		
		return (info != null ? info.isInFunEvent() : false) || _inEventTvTi;
	}
	
	/**
	 * Returns true if cp update should be done, false if not
	 * @return boolean
	 */
	private boolean needCpUpdate(int barPixels)
	{
		double currentCp = getStatus().getCurrentCp();
		
		if (currentCp <= 1.0 || getMaxCp() < barPixels)
			return true;
		
		if (currentCp <= _cpUpdateDecCheck || currentCp >= _cpUpdateIncCheck)
		{
			if (currentCp == getMaxCp())
			{
				_cpUpdateIncCheck = currentCp + 1;
				_cpUpdateDecCheck = currentCp - _cpUpdateInterval;
			}
			else
			{
				double doubleMulti = currentCp / _cpUpdateInterval;
				int intMulti = (int)doubleMulti;
				
				_cpUpdateDecCheck = _cpUpdateInterval * (doubleMulti < intMulti ? intMulti-- : intMulti);
				_cpUpdateIncCheck = _cpUpdateDecCheck + _cpUpdateInterval;
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Returns true if mp update should be done, false if not
	 * @return boolean
	 */
	private boolean needMpUpdate(int barPixels)
	{
		double currentMp = getStatus().getCurrentMp();
		
		if (currentMp <= 1.0 || getMaxMp() < barPixels)
			return true;
		
		if (currentMp <= _mpUpdateDecCheck || currentMp >= _mpUpdateIncCheck)
		{
			if (currentMp == getMaxMp())
			{
				_mpUpdateIncCheck = currentMp + 1;
				_mpUpdateDecCheck = currentMp - _mpUpdateInterval;
			}
			else
			{
				double doubleMulti = currentMp / _mpUpdateInterval;
				int intMulti = (int)doubleMulti;
				
				_mpUpdateDecCheck = _mpUpdateInterval * (doubleMulti < intMulti ? intMulti-- : intMulti);
				_mpUpdateIncCheck = _mpUpdateDecCheck + _mpUpdateInterval;
			}
			
			return true;
		}
		
		return false;
	}
	
	/**
	 * Send packet StatusUpdate with current HP,MP and CP to the L2Player and only current HP, MP and Level to all other L2Player of the Party.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send the Server->Client packet StatusUpdate with current HP, MP and CP to this L2Player </li><BR>
	 * <li>Send the Server->Client packet PartySmallWindowUpdate with current HP, MP and Level to all other L2Player of the Party </li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND current HP and MP to all L2Player of the _statusListener</B></FONT><BR><BR>
	 *
	 */
	@Override
	public final void broadcastStatusUpdateImpl()
	{
		StatusUpdate su = new StatusUpdate(getObjectId());
		su.addAttribute(StatusUpdate.CUR_HP, (int)getStatus().getCurrentHp());
		su.addAttribute(StatusUpdate.CUR_MP, (int)getStatus().getCurrentMp());
		su.addAttribute(StatusUpdate.CUR_CP, (int)getStatus().getCurrentCp());
		sendPacket(su);
		
		final boolean needCpUpdate = needCpUpdate(352);
		final boolean needHpUpdate = needHpUpdate(352);
		if (isInParty() && (needCpUpdate || needHpUpdate || needMpUpdate(352)))
			getParty().broadcastToPartyMembers(this, new PartySmallWindowUpdate(this));
		
		if (isInOlympiadMode() && isOlympiadStart() && (needCpUpdate || needHpUpdate))
		{
			Collection<L2Player> players = getKnownList().getKnownPlayers().values();
			if (!players.isEmpty())
			{
				ExOlympiadUserInfo olyInfo = new ExOlympiadUserInfo(this, 2);
				for (L2Player player : players)
					if (player != null && player.isInOlympiadMode() && player.getOlympiadGameId() == _olympiadGameId)
						player.sendPacket(olyInfo);
			}
			
			players = Olympiad.getInstance().getSpectators(_olympiadGameId);
			if (players != null && !players.isEmpty())
			{
				ExOlympiadUserInfo olyInfo = new ExOlympiadUserInfo(this, getOlympiadSide());
				for (L2Player spectator : players)
					if (spectator != null)
						spectator.sendPacket(olyInfo);
			}
		}
		
		if (isInDuel() && (needCpUpdate || needHpUpdate))
			DuelManager.getInstance().broadcastToOppositTeam(this, new ExDuelUpdateUserInfo(this));
	}
	
	@Override
	public void updateEffectIconsImpl()
	{
		final EffectInfoPacketList list = new EffectInfoPacketList(this);
		
		sendPacket(new MagicEffectIcons(list));
		
		if (isInParty())
			getParty().broadcastToPartyMembers(this, new PartySpelled(list));
	}
	
	/**
	 * Send a Server->Client packet UserInfo to this L2Player and CharInfo to all L2Player in its _knownPlayers.<BR><BR>
	 *
	 * <B><U> Concept</U> :</B><BR><BR>
	 * Others L2Player in the detection area of the L2Player are identified in <B>_knownPlayers</B>.
	 * In order to inform other players of this L2Player state modifications, server just need to go through _knownPlayers to send Server->Client Packet<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a Server->Client packet UserInfo to this L2Player (Public and Private Data)</li>
	 * <li>Send a Server->Client packet CharInfo to all L2Player in _knownPlayers of the L2Player (Public data only)</li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : DON'T SEND UserInfo packet to other players instead of CharInfo packet.
	 * Indeed, UserInfo packet contains PRIVATE DATA as MaxHP, STR, DEX...</B></FONT><BR><BR>
	 *
	 */
	public final void broadcastUserInfo()
	{
		broadcastFullInfo();
	}
	
	public final void broadcastTitleInfo()
	{
		// Send a Server->Client packet UserInfo to this L2Player
		sendPacket(new UserInfo(this));
		
		// Send a Server->Client packet NicknameChanged to all L2Player in _KnownPlayers of the L2Player
		if (_log.isDebugEnabled())
			_log.debug("players to notify:" + getKnownList().getKnownPlayers().size()
					+ " packet: [S] cc NicknameChanged");
		
		Broadcast.toKnownPlayers(this, new NicknameChanged(this));
	}
	
	/**
	 * Return the Alliance Identifier of the L2Player.<BR><BR>
	 */
	public int getAllyId()
	{
		return (_clan == null) ? 0 : _clan.getAllyId();
	}
	
	public int getAllyCrestId()
	{
		if (getClanId() == 0)
		{
			return 0;
		}
		if (getClan().getAllyId() == 0)
		{
			return 0;
		}
		return getClan().getAllyCrestId();
	}
	
	public void queryGameGuard()
	{
		getClient().setGameGuardOk(false);
		sendPacket(GameGuardQuery.STATIC_PACKET);
		if (Config.GAMEGUARD_ENFORCE)
		{
			ThreadPoolManager.getInstance().scheduleGeneral(new GameGuardCheck(), 30 * 1000);
		}
	}
	
	class GameGuardCheck implements Runnable
	{
		
		/**
		 * @see java.lang.Runnable#run()
		 */
		@Override
		public void run()
		{
			L2Client client = getClient();
			if (client != null && !client.isAuthedGG() && isOnline() == 1)
			{
				//GmListTable.broadcastMessageToGMs("Client "+client+" failed to reply GameGuard query and is being kicked!");
				_log.info("Client " + client + " failed to reply GameGuard query and is being kicked!");
				new Disconnection(client, L2Player.this).defaultSequence(false);
			}
		}
	}
	
	/**
	 * Send a Server->Client packet to the L2Player.<BR>
	 * <BR>
	 */
	@Override
	@SuppressWarnings("deprecation")
	public void sendPacket(L2ServerPacket packet)
	{
		final L2Client client = _client;
		if (client != null)
			client.sendPacket(packet);
	}
	
	/**
	 * Sends a SystemMessage without any parameter added. No instancing at all!
	 */
	@Override
	public void sendPacket(SystemMessageId sm)
	{
		sendPacket(sm.getSystemMessage());
	}
	
	@Override
	public void sendPacket(StaticPacket packet)
	{
		sendPacket((L2ServerPacket)packet);
	}
	
	@Override
	@SuppressWarnings("deprecation")
	public void sendMessage(String message)
	{
		sendPacket(SystemMessage.sendString(message));
	}
	
	/**
	 * Manage Interact Task with another L2Player.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>If the private store is a STORE_PRIVATE_SELL, send a Server->Client PrivateBuyListSell packet to the L2Player</li>
	 * <li>If the private store is a STORE_PRIVATE_BUY, send a Server->Client PrivateBuyListBuy packet to the L2Player</li>
	 * <li>If the private store is a STORE_PRIVATE_MANUFACTURE, send a Server->Client RecipeShopSellList packet to the L2Player</li><BR><BR>
	 *
	 * @param target The L2Creature targeted
	 *
	 */
	public void doInteract(L2Creature target)
	{
		if (target instanceof L2Player)
		{
			L2Player temp = (L2Player)target;
			sendPacket(ActionFailed.STATIC_PACKET);
			
			if (temp.getPrivateStoreType() == STORE_PRIVATE_SELL
					|| temp.getPrivateStoreType() == STORE_PRIVATE_PACKAGE_SELL)
				sendPacket(new PrivateStoreListSell(this, temp));
			else if (temp.getPrivateStoreType() == STORE_PRIVATE_BUY)
				sendPacket(new PrivateStoreListBuy(this, temp));
			else if (temp.getPrivateStoreType() == STORE_PRIVATE_MANUFACTURE)
				sendPacket(new RecipeShopSellList(this, temp));
		}
		else
		{
			// _interactTarget=null should never happen but one never knows ^^;
			if (target != null)
				target.onAction(this);
		}
	}
	
	/**
	 * Manage AutoLoot Task.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a System Message to the L2Player : YOU_PICKED_UP_S1_ADENA or YOU_PICKED_UP_S1_S2</li>
	 * <li>Add the Item to the L2Player inventory</li>
	 * <li>Send a Server->Client packet InventoryUpdate to this L2Player with NewItem (use a new slot) or ModifiedItem (increase amount)</li>
	 * <li>Send a Server->Client packet StatusUpdate to this L2Player with current weight</li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : If a Party is in progress, distribute Items between party members</B></FONT><BR><BR>
	 *
	 * @param target The L2ItemInstance dropped
	 *
	 */
	public void doAutoLoot(L2Attackable target, L2Attackable.RewardItem item)
	{
		if (!tryAutoLoot(target, item.getItemId()))
			target.dropItem(this, item);
		else if (isInParty())
			getParty().distributeItem(this, item, false, target);
		else if (item.getItemId() == PlayerInventory.ADENA_ID)
			addAdena("Loot", item.getCount(), target, true);
		else
			addItem("Loot", item.getItemId(), item.getCount(), target, true, false);
	}
	
	private boolean tryAutoLoot(L2Attackable target, int itemId)
	{
		if (target.isFlying())
			return true;
		
		if (ItemTable.isAdenaLikeItem(itemId))
			return Config.ALT_AUTO_LOOT_ADENA;
		
		if (ItemTable.getInstance().getTemplate(itemId).getItemType() == L2EtcItemType.HERB)
			return Config.ALT_AUTO_LOOT_HERBS;
		
		if (target.isRaid())
			return Config.ALT_AUTO_LOOT_RAID;
		
		return Config.ALT_AUTO_LOOT;
	}
	
	/**
	 * Manage Pickup Task.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Send a Server->Client packet StopMove to this L2Player </li>
	 * <li>Remove the L2ItemInstance from the world and send server->client GetItem packets </li>
	 * <li>Send a System Message to the L2Player : YOU_PICKED_UP_S1_ADENA or YOU_PICKED_UP_S1_S2</li>
	 * <li>Add the Item to the L2Player inventory</li>
	 * <li>Send a Server->Client packet InventoryUpdate to this L2Player with NewItem (use a new slot) or ModifiedItem (increase amount)</li>
	 * <li>Send a Server->Client packet StatusUpdate to this L2Player with current weight</li><BR><BR>
	 *
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : If a Party is in progress, distribute Items between party members</B></FONT><BR><BR>
	 *
	 * @param object The L2ItemInstance to pick up
	 *
	 */
	protected void doPickupItem(L2Object object)
	{
		if (isAlikeDead() || isFakeDeath())
			return;
		
		// Set the AI Intention to AI_INTENTION_IDLE
		getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		
		// Check if the L2Object to pick up is a L2ItemInstance
		if (!(object instanceof L2ItemInstance))
		{
			// Dont try to pickup anything that is not an item :)
			_log.warn("trying to pickup wrong target." + getTarget());
			return;
		}
		
		L2ItemInstance target = (L2ItemInstance)object;
		
		// Send a Server->Client packet ActionFailed to this L2Player
		sendPacket(ActionFailed.STATIC_PACKET);
		
		// Send a Server->Client packet StopMove to this L2Player
		StopMove sm = new StopMove(getObjectId(), getX(), getY(), getZ(), getHeading());
		if (_log.isDebugEnabled())
			_log.debug("pickup pos: " + target.getX() + " " + target.getY() + " " + target.getZ());
		sendPacket(sm);
		
		synchronized (target)
		{
			// Check if the target to pick up is visible
			if (!target.isVisible())
			{
				// Send a Server->Client packet ActionFailed to this L2Player
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
				
			}
			
			if (((isInParty() && getParty().getLootDistribution() == L2Party.ITEM_LOOTER) || !isInParty())
					&& !getInventory().validateCapacity(target))
			{
				sendPacket(SystemMessageId.SLOTS_FULL);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (isInvul() && !isGM())
			{
				SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1);
				smsg.addItemName(target);
				sendPacket(smsg);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (getActiveTradeList() != null)
			{
				sendPacket(SystemMessageId.CANNOT_PICKUP_OR_USE_ITEM_WHILE_TRADING);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (target.getOwnerId() != 0 && target.getOwnerId() != getObjectId()
					&& !isInLooterParty(target.getOwnerId()))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				
				if (target.getItemId() == PlayerInventory.ADENA_ID)
				{
					SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1_ADENA);
					smsg.addItemNumber(target.getCount());
					sendPacket(smsg);
				}
				else if (target.getCount() > 1)
				{
					SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S2_S1_S);
					smsg.addItemName(target);
					smsg.addItemNumber(target.getCount());
					sendPacket(smsg);
				}
				else
				{
					SystemMessage smsg = new SystemMessage(SystemMessageId.FAILED_TO_PICKUP_S1);
					smsg.addItemName(target);
					sendPacket(smsg);
				}
				
				return;
			}
			
			// Cursed Weapons
			if (CursedWeaponsManager.getInstance().isCursed(target.getItemId()) && isCursedWeaponEquipped())
			{
				ItemTable.getInstance().destroyItem("Pickup CW", target, this, null);
				CursedWeapon cw = CursedWeaponsManager.getInstance().getCursedWeapon(getCursedWeaponEquippedId());
				cw.increaseKills(cw.getStageKills());
				return;
			}
			
			// You can pickup only 1 combat flag
			if (FortSiegeManager.getInstance().isCombat(target.getItemId()))
			{
				if (!FortSiegeManager.getInstance().checkIfCanPickup(this))
					return;
			}
			
			if (target.getItemLootShedule() != null
					&& (target.getOwnerId() == getObjectId() || isInLooterParty(target.getOwnerId())))
				target.resetOwnerTimer();
			
			// Remove the L2ItemInstance from the world and send server->client GetItem packets
			target.pickupMe(this);
		}
		// Auto use herbs - pick up
		if (target.getItemType() == L2EtcItemType.HERB)
		{
			ItemHandler.getInstance().useItem(target.getItemId(), this, target);
		}
		// Cursed Weapons are not distributed
		else if (CursedWeaponsManager.getInstance().isCursed(target.getItemId()))
		{
			addItem("Pickup", target, null, true);
		}
		else if (FortSiegeManager.getInstance().isCombat(target.getItemId()))
		{
			addItem("Pickup", target, null, true);
		}
		else
		{
			// If item is instance of L2ArmorType or L2WeaponType broadcast an "Attention" system message
			if (target.getItemType() instanceof L2ArmorType || target.getItemType() instanceof L2WeaponType)
			{
				if (target.getEnchantLevel() > 0)
				{
					SystemMessage msg = new SystemMessage(SystemMessageId.ANNOUNCEMENT_C1_PICKED_UP_S2_S3);
					msg.addPcName(this);
					msg.addNumber(target.getEnchantLevel());
					msg.addItemName(target);
					broadcastPacket(msg, 1400);
				}
				else
				{
					SystemMessage msg = new SystemMessage(SystemMessageId.ANNOUNCEMENT_C1_PICKED_UP_S2);
					msg.addPcName(this);
					msg.addItemName(target);
					broadcastPacket(msg, 1400);
				}
			}
			
			// Check if a Party is in progress
			if (isInParty())
				getParty().distributeItem(this, target);
			// Target is adena
			else if (target.getItemId() == PlayerInventory.ADENA_ID && getInventory().getAdenaInstance() != null)
			{
				addAdena("Pickup", target.getCount(), null, true);
				ItemTable.getInstance().destroyItem("Pickup", target, this, null);
			}
			// Target is regular item
			else
				addItem("Pickup", target, null, true);
		}
	}
	
	/**
	 * Set a target.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Remove the L2Player from the _statusListener of the old target if it was a L2Creature </li>
	 * <li>Add the L2Player to the _statusListener of the new target if it's a L2Creature </li>
	 * <li>Target the new L2Object (add the target to the L2Player _target, _knownObject and L2Player to _KnownObject of the L2Object)</li><BR><BR>
	 *
	 * @param newTarget The L2Object to target
	 *
	 */
	@Override
	public void setTarget(L2Object newTarget)
	{
		if (newTarget != null)
		{
			if (this != newTarget && newTarget instanceof L2Creature)
				sendPacket(new ValidateLocation((L2Creature)newTarget));
			
			if (!isGM())
			{
				if (newTarget instanceof L2FestivalMonsterInstance && !isFestivalParticipant())
					return;
				
				if (isInParty() && getParty().isInDimensionalRift())
				{
					byte riftType = getParty().getDimensionalRift().getType();
					byte riftRoom = getParty().getDimensionalRift().getCurrentRoom();
					
					if (!DimensionalRiftManager.getInstance().getRoom(riftType, riftRoom).checkIfInZone(newTarget))
						return;
				}
			}
			
			if (!(newTarget instanceof L2Player) || !isInParty()
					|| getParty() != ((L2Player)newTarget).getParty())
			{
				if (!newTarget.isVisible())
					return;
				
				if (Math.abs(newTarget.getZ() - getZ()) > 500)
					return;
			}
		}
		
		super.setTarget(newTarget);
	}
	
	@Override
	protected void refreshTarget(L2Object newTarget)
	{
		final L2Object oldTarget = getTarget();
		
		if (oldTarget instanceof L2Creature)
			((L2Creature)oldTarget).getStatus().removeStatusListener(this);
		
		if (newTarget instanceof L2Creature)
			((L2Creature)newTarget).getStatus().addStatusListener(this);
		
		if (newTarget != null)
		{
			broadcastPacket(new TargetSelected(this, newTarget));
			
			sendPacket(new MyTargetSelected(this, newTarget));
		}
		else if (oldTarget != null)
		{
			broadcastPacket(new TargetUnselected(this));
		}
		
		super.refreshTarget(newTarget);
		
		saveLastTarget(oldTarget != null ? oldTarget.getObjectId() : 0);
	}
	
	/**
	 * Return the active weapon instance (always equipped in the right hand).<BR><BR>
	 */
	@Override
	public L2ItemInstance getActiveWeaponInstance()
	{
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
	}
	
	/**
	 * Return the active weapon item (always equipped in the right hand).<BR><BR>
	 */
	@Override
	public L2Weapon getActiveWeaponItem()
	{
		L2ItemInstance weapon = getActiveWeaponInstance();
		
		if (weapon == null)
			return getFistsWeaponItem();
		
		return (L2Weapon)weapon.getItem();
	}
	
	public L2ItemInstance getChestArmorInstance()
	{
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
	}
	
	public L2ItemInstance getLegsArmorInstance()
	{
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS);
	}
	
	public L2Armor getActiveChestArmorItem()
	{
		L2ItemInstance armor = getChestArmorInstance();
		
		if (armor == null)
			return null;
		
		return (L2Armor)armor.getItem();
	}
	
	public L2Armor getActiveLegsArmorItem()
	{
		L2ItemInstance legs = getLegsArmorInstance();
		
		if (legs == null)
			return null;
		
		return (L2Armor)legs.getItem();
	}
	
	public boolean isWearingHeavyArmor()
	{
		if ((getChestArmorInstance() != null) && getLegsArmorInstance() != null)
		{
			L2ItemInstance legs = getLegsArmorInstance();
			L2ItemInstance armor = getChestArmorInstance();
			if (legs.getItemType() == L2ArmorType.HEAVY && (armor.getItemType() == L2ArmorType.HEAVY))
				return true;
		}
		if (getChestArmorInstance() != null)
		{
			L2ItemInstance armor = getChestArmorInstance();
			
			if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST).getItem().getBodyPart() == L2Item.SLOT_FULL_ARMOR
					&& armor.getItemType() == L2ArmorType.HEAVY)
				return true;
		}
		
		return false;
	}
	
	public boolean isWearingLightArmor()
	{
		if ((getChestArmorInstance() != null) && getLegsArmorInstance() != null)
		{
			L2ItemInstance legs = getLegsArmorInstance();
			L2ItemInstance armor = getChestArmorInstance();
			if (legs.getItemType() == L2ArmorType.LIGHT && (armor.getItemType() == L2ArmorType.LIGHT))
				return true;
		}
		if (getChestArmorInstance() != null)
		{
			L2ItemInstance armor = getChestArmorInstance();
			
			if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST).getItem().getBodyPart() == L2Item.SLOT_FULL_ARMOR
					&& armor.getItemType() == L2ArmorType.LIGHT)
				return true;
		}
		
		return false;
	}
	
	public boolean isWearingMagicArmor()
	{
		if ((getChestArmorInstance() != null) && getLegsArmorInstance() != null)
		{
			L2ItemInstance legs = getLegsArmorInstance();
			L2ItemInstance armor = getChestArmorInstance();
			if (legs.getItemType() == L2ArmorType.MAGIC && (armor.getItemType() == L2ArmorType.MAGIC))
				return true;
		}
		if (getChestArmorInstance() != null)
		{
			L2ItemInstance armor = getChestArmorInstance();
			
			if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST).getItem().getBodyPart() == L2Item.SLOT_FULL_ARMOR
					&& armor.getItemType() == L2ArmorType.MAGIC)
				return true;
		}
		
		return false;
	}
	
	public boolean isWearingFormalWear()
	{
		return _IsWearingFormalWear;
	}
	
	public void setIsWearingFormalWear(boolean value)
	{
		_IsWearingFormalWear = value;
	}
	
	/**
	 * Return the secondary weapon instance (always equipped in the left hand).<BR><BR>
	 */
	@Override
	public L2ItemInstance getSecondaryWeaponInstance()
	{
		return getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
	}
	
	/**
	 * Return the secondary weapon item (always equipped in the left hand) or the fists weapon.<BR><BR>
	 */
	@Override
	public L2Weapon getSecondaryWeaponItem()
	{
		L2ItemInstance weapon = getSecondaryWeaponInstance();
		
		if (weapon == null)
			return getFistsWeaponItem();
		
		L2Item item = weapon.getItem();
		
		if (item instanceof L2Weapon)
			return (L2Weapon)item;
		
		return null;
	}
	
	/**
	 * Kill the L2Creature, Apply Death Penalty, Manage gain/loss Karma and Item Drop.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Reduce the Experience of the L2Player in function of the calculated Death Penalty </li>
	 * <li>If necessary, unsummon the Pet of the killed L2Player </li>
	 * <li>Manage Karma gain for attacker and Karma loss for the killed L2Player </li>
	 * <li>If the killed L2Player has Karma, manage Drop Item</li>
	 * <li>Kill the L2Player </li><BR><BR>
	 *
	 *
	 * @param killer The L2Creature who attacks
	 *
	 */
	@Override
	public boolean doDie(L2Creature killer)
	{
		// is the dying in duel? if so, change his duel state to dead
		if (isInDuel()) // pets can die as usual
		{
			disableAllSkills();
			getStatus().setCurrentHp(1);
			getStatus().stopHpMpRegeneration();
			killer.getAI().setIntention(CtrlIntention.AI_INTENTION_ACTIVE);
			killer.sendPacket(ActionFailed.STATIC_PACKET);
			
			// let the DuelManager know of his defeat
			DuelManager.getInstance().onPlayerDefeat(this);
			return false;
		}
		
		if (isInOlympiadMode())
		{
			getStatus().stopHpMpRegeneration();
			setIsDead(true);
			setIsPendingRevive(true);
			if (getPet() != null)
				getPet().getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE, null);
			return false;
		}
		
		/* Since L2Creature.doDie() calls stopAllEffects(), which includes
		 * setting charm of curage and other blessings as false, this stores value
		 * before calling superclass method
		 */
		boolean charmOfCourage = getCharmOfCourage();
		
		// Kill the L2Player
		if (!super.doDie(killer))
			return false;
		
		if (isMounted())
			stopFeed();
		
		synchronized (this)
		{
			if (isFakeDeath())
				stopFakeDeath(true);
		}
		
		// Clear resurrect xp calculation
		setExpBeforeDeath(0);
		
		// Issues drop of Cursed Weapon.
		if (isCursedWeaponEquipped())
		{
			CursedWeaponsManager.getInstance().drop(_cursedWeaponEquippedId, killer);
		}
		else if (isCombatFlagEquipped())
		{
			FortSiegeManager.getInstance().dropCombatFlag(this);
		}
		
		Castle castle = null;
		if (getClan() != null)
		{
			castle = CastleManager.getInstance().getCastleByOwner(getClan());
			if (castle != null)
				castle.destroyClanGate();
		}
		
		if (killer != null)
		{
			L2Player pk = killer.getActingPlayer();
			
			boolean bothWayClanWarKill = false;
			boolean clanWarKill = false;
			boolean playerKill = false;
			
			if (GlobalRestrictions.playerKilled(killer, this))
			{
			}
			
			else if (pk != null)
			{
				if (pk.getClan() != null && getClan() != null && !isAcademyMember() && !pk.isAcademyMember())
				{
					if ((_clan.isAtWarWith(pk.getClanId()) && pk.getClan().isAtWarWith(getClanId()))
							|| (isInSiege() && pk.isInSiege()))
					{
						bothWayClanWarKill = true;
						clanWarKill = true;
					}
					else if (_clan.isAtWarWith(pk.getClanId()))
						clanWarKill = true;
				}
				playerKill = true;
			}
			
			boolean srcInPvP = isInsideZone(L2Zone.FLAG_PVP) && !isInSiege();
			
			if (killer instanceof L2Player && srcInPvP && Config.ARENA_ENABLED)
			{
				ArenaManager.getInstance().onKill(killer.getObjectId(), killer.getName());
				ArenaManager.getInstance().onDeath(getObjectId(), getName());
			}
			
			if (bothWayClanWarKill && pk != null)
			{
				// When your reputation score is 0 or below, the other clan cannot acquire any reputation points
				if (getClan().getReputationScore() > 0)
					pk.getClan().setReputationScore(
							pk.getClan().getReputationScore() + Config.REPUTATION_SCORE_PER_KILL, true);
				// When the opposing sides reputation score is 0 or below, your clans reputation score does not decrease
				if (pk.getClan().getReputationScore() > 0)
					_clan.setReputationScore(_clan.getReputationScore() - Config.REPUTATION_SCORE_PER_KILL, true);
			}
			
			if (!srcInPvP)
			{
				if (pk == null || !pk.isCursedWeaponEquipped())
				{
					onDieDropItem(killer); // Check if any item should be dropped
					
					if (!srcInPvP)
					{
						if (Config.ALT_GAME_DELEVEL)
						{
							// Reduce the Experience of the L2Player in function of the calculated Death Penalty
							// NOTE: deathPenalty +- Exp will update karma
							// Penalty is lower if the player is at war with the pk (war has to be declared)
							if (getSkillLevel(L2Skill.SKILL_LUCKY) < 0 || getStat().getLevel() > 9)
								deathPenalty(clanWarKill, playerKill, charmOfCourage, killer instanceof L2SiegeGuard);
						}
						else
						{
							if (!(isInsideZone(L2Zone.FLAG_PVP) && !isInsideZone(L2Zone.FLAG_PVP)) || pk == null)
								onDieUpdateKarma(); // Update karma if delevel is not allowed
						}
					}
				}
				if (pk != null)
				{
					if (Config.ALT_ANNOUNCE_PK)
					{
						String announcetext = "";
						// Build announce text
						if (getPvpFlag() == 0)
							announcetext = pk.getName() + " has slaughtered " + getName();
						else
							announcetext = pk.getName() + " has defeated " + getName();
						
						// Announce to player
						if (Config.ALT_ANNOUNCE_PK_NORMAL_MESSAGE)
							Announcements.getInstance().announceToPlayers(announcetext);
						else
							Announcements.getInstance().announceToAll(announcetext);
					}
				}
			}
			else if (pk != null && Config.ALT_ANNOUNCE_PK)
			{
				if (Config.ALT_ANNOUNCE_PK_NORMAL_MESSAGE)
					Announcements.getInstance().announceToPlayers(pk.getName() + " has defeated " + getName());
				else
					Announcements.getInstance().announceToAll(pk.getName() + " has defeated " + getName());
			}
		}
		
		// Force Charges
		clearCharges(); // Empty charges
		
		//updatePvPFlag(0); // Clear the pvp flag
		// Pet shouldn't get unsummoned after masters death.
		// Unsummon the Pet
		//if (getPet() != null) getPet().unSummon(this);
		
		// Unsummon Cubics
		if (!_cubics.isEmpty())
		{
			for (L2CubicInstance cubic : _cubics.values())
			{
				cubic.stopAction();
				cubic.cancelDisappear();
			}
			_cubics.clear();
		}
		
		if (_fusionSkill != null)
			abortCast();
		
		for (L2Creature character : getKnownList().getKnownCharacters())
			if (character.getFusionSkill() != null && character.getFusionSkill().getTarget() == this)
				character.abortCast();
		
		if (isInParty() && getParty().isInDimensionalRift())
		{
			getParty().getDimensionalRift().memberDead(this);
		}
		
		// Calculate death penalty buff
		calculateDeathPenaltyBuffLevel(killer);
		
		// [L2J_JP ADD SANDMAN]
		// When the player has been annihilated, the player is banished from the Four Sepulcher.
		if (FourSepulchersManager.getInstance().checkIfInZone(this) && (getZ() >= -7250 && getZ() <= -6841))
			FourSepulchersManager.getInstance().checkAnnihilated(this);
		// When the player has been annihilated, the player is banished from the lair.
		else if (SailrenManager.getInstance().checkIfInZone(this))
			SailrenManager.getInstance().checkAnnihilated();
		else if (AntharasManager.getInstance().checkIfInZone(this))
			AntharasManager.getInstance().checkAnnihilated();
		else if (ValakasManager.getInstance().checkIfInZone(this))
			ValakasManager.getInstance().checkAnnihilated();
		else if (BaiumManager.getInstance().checkIfInZone(this))
			BaiumManager.getInstance().checkAnnihilated();
		else if (BaylorManager.getInstance().checkIfInZone(this))
			BaylorManager.getInstance().checkAnnihilated();
		else if (FrintezzaManager.getInstance().checkIfInZone(this))
			FrintezzaManager.getInstance().checkAnnihilated();
		else if (LastImperialTombManager.getInstance().checkIfInZone(this))
			LastImperialTombManager.getInstance().checkAnnihilated();
		
		QuestState qs = getQuestState("255_Tutorial");
		if (qs != null)
			qs.getQuest().notifyEvent("CE30", null, this);
		
		return true;
	}
	
	/** UnEnquip on skills with disarm effect **/
	public void onDisarm(L2Player target)
	{
		target.getInventory().unEquipItemInBodySlotAndRecord(14);
	}
	
	private void onDieDropItem(L2Creature killer)
	{
		if (killer == null)
			return;
		
		L2Player pk = killer.getActingPlayer();
		if (pk != null && getKarma() <= 0 && pk.getClan() != null && getClan() != null
				&& (pk.getClan().isAtWarWith(getClanId())
				//|| this.getClan().isAtWarWith(((L2Player)killer).getClanId())
				))
			return;
		
		if ((!isInsideZone(L2Zone.FLAG_PVP) || pk == null) && (!isGM() || Config.KARMA_DROP_GM))
		{
			boolean isKarmaDrop = false;
			boolean isKillerNpc = (killer instanceof L2Npc);
			int pkLimit = Config.KARMA_PK_LIMIT;
			
			int dropEquip = 0;
			int dropEquipWeapon = 0;
			int dropItem = 0;
			int dropLimit = 0;
			int dropPercent = 0;
			
			if (getKarma() > 0 && getPkKills() >= pkLimit)
			{
				isKarmaDrop = true;
				dropPercent = Config.KARMA_RATE_DROP;
				dropEquip = Config.KARMA_RATE_DROP_EQUIP;
				dropEquipWeapon = Config.KARMA_RATE_DROP_EQUIP_WEAPON;
				dropItem = Config.KARMA_RATE_DROP_ITEM;
				dropLimit = Config.KARMA_DROP_LIMIT;
			}
			else if (isKillerNpc && getLevel() > 4 && !isFestivalParticipant())
			{
				dropPercent = Config.PLAYER_RATE_DROP;
				dropEquip = Config.PLAYER_RATE_DROP_EQUIP;
				dropEquipWeapon = Config.PLAYER_RATE_DROP_EQUIP_WEAPON;
				dropItem = Config.PLAYER_RATE_DROP_ITEM;
				dropLimit = Config.PLAYER_DROP_LIMIT;
			}
			
			if (dropPercent > 0 && Rnd.get(100) < dropPercent)
			{
				int dropCount = 0;
				int itemDropPercent = 0;
				for (L2ItemInstance itemDrop : getInventory().getItems())
				{
					// Don't drop
					if (!itemDrop.isDropable() || itemDrop.getItemId() == PlayerInventory.ADENA_ID
					// Dont drop Shadow Items
							|| itemDrop.isShadowItem()
							// Dont drop Time Limited Items
							|| itemDrop.isTimeLimitedItem()
							// Quest Items
							|| itemDrop.getItem().getType2() == L2Item.TYPE2_QUEST
							// Control Item of active pet
							|| getPet() != null && getPet().getControlItemId() == itemDrop.getItemId()
							// Item listed in the non droppable item list
							|| Arrays.binarySearch(Config.KARMA_LIST_NONDROPPABLE_ITEMS, itemDrop.getItemId()) >= 0
							// Item listed in the non droppable pet item list
							|| Arrays.binarySearch(Config.KARMA_LIST_NONDROPPABLE_PET_ITEMS, itemDrop.getItemId()) >= 0)
						continue;
					
					if (itemDrop.isEquipped())
						// Set proper chance according to Item type of equipped Item
						itemDropPercent =
								itemDrop.getItem().getType2() == L2Item.TYPE2_WEAPON ? dropEquipWeapon : dropEquip;
					else
						itemDropPercent = dropItem; // Item in inventory
						
					// NOTE: Each time an item is dropped, the chance of another item being dropped gets lesser (dropCount * 2)
					if (Rnd.get(100) < itemDropPercent)
					{
						if (itemDrop.isEquipped())
						{
							getInventory().unEquipItemInSlotAndRecord(itemDrop.getLocationSlot());
							// must be sent explicitly to avoid visible garbage
							sendPacket(new UserInfo(this));
						}
						dropItem("DieDrop", itemDrop, killer, true);
						
						if (isKarmaDrop)
							_log.info(getName() + " has karma and dropped " + itemDrop);
						else
							_log.info(getName() + " dropped " + itemDrop);
						
						if (++dropCount >= dropLimit)
							break;
					}
				}
			}
			// Player can drop adena against other player
			if (Config.ALT_PLAYER_CAN_DROP_ADENA && !isKillerNpc && Config.PLAYER_RATE_DROP_ADENA > 0
					&& 100 >= Config.PLAYER_RATE_DROP_ADENA
					&& !(killer instanceof L2Player && ((L2Player)killer).isGM()))
			{
				L2ItemInstance itemDrop = getInventory().getAdenaInstance();
				long iCount = getInventory().getAdena();
				// Adena count depends on config
				iCount = iCount * Config.PLAYER_RATE_DROP_ADENA / 100;
				// Drop only adena this time
				if (itemDrop != null && itemDrop.getItemId() == PlayerInventory.ADENA_ID) // Adena
				{
					dropItem("DieDrop", itemDrop.getObjectId(), iCount, getPosition().getX() + Rnd.get(50) - 25,
							getPosition().getY() + Rnd.get(50) - 25, getPosition().getZ() + 20, killer, true);
				}
			}
		}
	}
	
	private void onDieUpdateKarma()
	{
		// Karma lose for server that does not allow delevel
		if (getKarma() > 0)
		{
			// This formula seems to work relatively well:
			// baseKarma * thisLVL * (thisLVL/100)
			// Calculate the new Karma of the attacker : newKarma = baseKarma*pkCountMulti*lvlDiffMulti
			double karmaLost = Config.KARMA_LOST_BASE;
			karmaLost *= getLevel(); // Multiply by char lvl
			karmaLost *= (getLevel() / 100.0); // Divide by 0.charLVL
			karmaLost = Math.round(karmaLost);
			if (karmaLost < 0)
				karmaLost = 1;
			
			// Decrease Karma of the L2Player and Send it a Server->Client StatusUpdate packet with Karma and PvP Flag if necessary
			setKarma(getKarma() - (int)karmaLost);
		}
	}
	
	public void onKillUpdatePvPKarma(L2Creature target)
	{
		if (target == null)
			return;
		if (!(target instanceof L2Playable))
			return;
		
		L2Player targetPlayer = target.getActingPlayer();
		
		if (targetPlayer == null)
			return; // Target player is null
		if (targetPlayer == this)
			return; // Target player is self
			
		if (isCursedWeaponEquipped())
		{
			CursedWeaponsManager.getInstance().increaseKills(_cursedWeaponEquippedId);
			// Custom message for time left
			// CursedWeapon cw = CursedWeaponsManager.getInstance().getCursedWeapon(_cursedWeaponEquippedId);
			// SystemMessage msg = new SystemMessage(SystemMessageId.THERE_IS_S1_HOUR_AND_S2_MINUTE_LEFT_OF_THE_FIXED_USAGE_TIME);
			// int timeLeftInHours = (int)(((cw.getTimeLeft()/60000)/60));
			// msg.addItemName(_cursedWeaponEquippedId);
			// msg.addNumber(timeLeftInHours);
			// sendPacket(msg);
			return;
		}
		
		// If in duel and you kill (only can kill l2summon), do nothing
		if (isInDuel() && targetPlayer.isInDuel())
			return;
		
		// If in Arena, do nothing
		if (isInsideZone(L2Zone.FLAG_PVP))
			return;
		
		// Check if it's pvp
		if ((checkIfPvP(target) && //  Can pvp and
				targetPlayer.getPvpFlag() != 0 // Target player has pvp flag set
				)
				|| // or
				(isInsideZone(L2Zone.FLAG_PVP) && // Player is inside pvp zone and
				targetPlayer.isInsideZone(L2Zone.FLAG_PVP) // Target player is inside pvp zone
				))
		{
			if (target instanceof L2Player)
				increasePvpKills();
		}
		else
		// Target player doesn't have pvp flag set
		{
			// Check about wars
			boolean clanWarKill =
					(targetPlayer.getClan() != null && getClan() != null && !isAcademyMember()
							&& !(targetPlayer.isAcademyMember()) && _clan.isAtWarWith(targetPlayer.getClanId()) && targetPlayer
							.getClan().isAtWarWith(_clan.getClanId()));
			if (clanWarKill)
			{
				// 'Both way war' -> 'PvP Kill'
				if (target instanceof L2Player)
					increasePvpKills();
				return;
			}
			
			// 'No war' or 'One way war' -> 'Normal PK'
			if (targetPlayer.getKarma() > 0) // Target player has karma
			{
				if (Config.KARMA_AWARD_PK_KILL)
				{
					if (target instanceof L2Player)
						increasePvpKills();
				}
			}
			else if (targetPlayer.getPvpFlag() == 0) // Target player doesn't have karma
			{
				increasePkKillsAndKarma(targetPlayer.getLevel(), target instanceof L2Player);
				// Unequip adventurer items
				if (getInventory().getPaperdollItemId(7) >= 7816 && getInventory().getPaperdollItemId(7) <= 7831)
				{
					L2ItemInstance invItem = getInventory().getItemByItemId(getInventory().getPaperdollItemId(7));
					if (invItem.isEquipped())
					{
						L2ItemInstance[] unequiped =
								getInventory().unEquipItemInSlotAndRecord(invItem.getLocationSlot());
						InventoryUpdate iu = new InventoryUpdate();
						for (L2ItemInstance itm : unequiped)
							iu.addModifiedItem(itm);
						sendPacket(iu);
					}
					refreshExpertisePenalty();
					sendPacket(SystemMessageId.CANT_EQUIP_WITH_PK_POINTS);
				}
			}
		}
	}
	
	/**
	 * Increase the pvp kills count and send the info to the player
	 *
	 */
	private void increasePvpKills()
	{
		// Add karma to attacker and increase its PK counter
		setPvpKills(getPvpKills() + 1);
		
		// Send a Server->Client UserInfo packet to attacker with its Karma and PK Counter
		sendPacket(new UserInfo(this));
	}
	
	/**
	 * Increase pk count, karma and send the info to the player
	 *
	 * @param targLVL : level of the killed player
	 * @param increasePk : true if PK counter should be increased too
	 */
	private void increasePkKillsAndKarma(int targLVL, boolean increasePk)
	{
		int baseKarma = (int)(Config.KARMA_MIN_KARMA * Config.KARMA_RATE);
		int newKarma = baseKarma;
		int karmaLimit = (int)(Config.KARMA_MAX_KARMA * Config.KARMA_RATE);
		
		int pkLVL = getLevel();
		int pkPKCount = getPkKills();
		
		int lvlDiffMulti = 0;
		int pkCountMulti = 0;
		
		// Check if the attacker has a PK counter greater than 0
		if (pkPKCount > 0)
			pkCountMulti = pkPKCount / 2;
		else
			pkCountMulti = 1;
		if (pkCountMulti < 1)
			pkCountMulti = 1;
		
		// Calculate the level difference Multiplier between attacker and killed L2Player
		if (pkLVL > targLVL)
			lvlDiffMulti = pkLVL / targLVL;
		else
			lvlDiffMulti = 1;
		if (lvlDiffMulti < 1)
			lvlDiffMulti = 1;
		
		// Calculate the new Karma of the attacker : newKarma = baseKarma*pkCountMulti*lvlDiffMulti
		newKarma = (int)(newKarma * pkCountMulti * lvlDiffMulti * Config.KARMA_RATE);
		
		// Make sure newKarma is less than karmaLimit and higher than baseKarma
		if (newKarma < baseKarma)
			newKarma = baseKarma;
		if (newKarma > karmaLimit)
			newKarma = karmaLimit;
		
		// Fix to prevent overflow (=> karma has a  max value of 2 147 483 647)
		if (getKarma() > (Integer.MAX_VALUE - newKarma))
			newKarma = Integer.MAX_VALUE - getKarma();
		
		// Add karma to attacker and increase its PK counter
		if (increasePk)
			setPkKills(getPkKills() + 1);
		setKarma(getKarma() + newKarma);
		
		// Send a Server->Client UserInfo packet to attacker with its Karma and PK Counter
		sendPacket(new UserInfo(this));
	}
	
	public int calculateKarmaLost(long exp)
	{
		// KARMA LOSS
		// When a Player Killer gets killed by another player or a L2MonsterInstance, it loses a certain amount of Karma based on their level.
		// this (with defaults) results in a level 1 losing about ~2 karma per death, and a lvl 70 loses about 11760 karma per death...
		// You lose karma as long as you were not in a pvp zone and you did not kill urself.
		// NOTE: exp for death (if delevel is allowed) is based on the players level
		
		long expGained = Math.abs(exp);
		expGained /= Config.KARMA_XP_DIVIDER;
		
		int karmaLost = 0;
		if (expGained > Integer.MAX_VALUE)
			karmaLost = Integer.MAX_VALUE;
		else
			karmaLost = (int)expGained;
		
		if (karmaLost < Config.KARMA_LOST_BASE)
			karmaLost = Config.KARMA_LOST_BASE;
		if (karmaLost > getKarma())
			karmaLost = getKarma();
		
		return karmaLost;
	}
	
	private static final class PvPFlagManager extends AbstractIterativePeriodicTaskManager<L2Player>
	{
		private static final PvPFlagManager _instance = new PvPFlagManager();
		
		private static PvPFlagManager getInstance()
		{
			return _instance;
		}
		
		private PvPFlagManager()
		{
			super(1000);
		}
		
		@Override
		protected void callTask(L2Player task)
		{
			if (System.currentTimeMillis() > task.getPvpFlagLasts())
			{
				task.stopPvPFlag();
			}
			else if (System.currentTimeMillis() > (task.getPvpFlagLasts() - 20000))
			{
				task.updatePvPFlag(2);
			}
			else
			{
				task.updatePvPFlag(1);
			}
		}
		
		@Override
		protected String getCalledMethodName()
		{
			return "updatePvPFlag()";
		}
	}
	
	/** The PvP Flag state of the L2Player (0=White, 1=Purple) */
	private byte _pvpFlag;
	private long _pvpFlagLasts;
	
	/**
	 * Set the PvP Flag of the L2Player.<BR>
	 * <BR>
	 */
	private void setPvpFlag(int pvpFlag)
	{
		_pvpFlag = (byte)pvpFlag;
	}
	
	public byte getPvpFlag()
	{
		return _pvpFlag;
	}
	
	public void updatePvPFlag(int value)
	{
		if (getPvpFlag() == value)
			return;
		
		setPvpFlag((byte)value);
		
		if (getPvpFlag() == 0)
			PvPFlagManager.getInstance().stopTask(this);
		else
			PvPFlagManager.getInstance().startTask(this);
		
		sendPacket(new UserInfo(this));
		broadcastRelationChanged();
	}
	
	private void setPvpFlagLasts(long time)
	{
		_pvpFlagLasts = time;
	}
	
	private long getPvpFlagLasts()
	{
		return _pvpFlagLasts;
	}
	
	private void startPvPFlag()
	{
		updatePvPFlag(1);
	}
	
	private void stopPvPFlag()
	{
		updatePvPFlag(0);
	}
	
	public void updatePvPStatus()
	{
		if (isInsideZone(L2Zone.FLAG_PVP))
			return;
		
		setPvpFlagLasts(System.currentTimeMillis() + Config.PVP_NORMAL_TIME);
		
		if (getPvpFlag() == 0)
			startPvPFlag();
	}
	
	public void updatePvPStatus(L2Creature target)
	{
		L2Player player_target = target.getActingPlayer();
		
		if (player_target == null)
			return;
		
		if ((isInDuel() && player_target.getDuelId() == getDuelId()))
			return;
		if ((!isInsideZone(L2Zone.FLAG_PVP) || !player_target.isInsideZone(L2Zone.FLAG_PVP))
				&& player_target.getKarma() == 0)
		{
			if (checkIfPvP(player_target))
				setPvpFlagLasts(System.currentTimeMillis() + Config.PVP_PVP_TIME);
			else
				setPvpFlagLasts(System.currentTimeMillis() + Config.PVP_NORMAL_TIME);
			if (getPvpFlag() == 0)
				startPvPFlag();
		}
	}
	
	/**
	 * Restore the specified % of experience this L2Player has
	 * lost and sends a Server->Client StatusUpdate packet.<BR><BR>
	 */
	public void restoreExp(double restorePercent)
	{
		if (getExpBeforeDeath() > 0)
		{
			// Restore the specified % of lost experience.
			getStat().addExp((int)Math.round((getExpBeforeDeath() - getExp()) * restorePercent / 100));
			setExpBeforeDeath(0);
		}
	}
	
	/**
	 * Reduce the Experience (and level if necessary) of the L2Player in function of the calculated Death Penalty.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Calculate the Experience loss </li>
	 * <li>Set the value of _expBeforeDeath </li>
	 * <li>Set the new Experience value of the L2Player and Decrease its level if necessary </li>
	 * <li>Send a Server->Client StatusUpdate packet with its new Experience </li><BR><BR>
	 *
	 */
	public void deathPenalty(boolean atwar, boolean killed_by_pc, boolean charmOfCourage, boolean killed_by_siege_npc)
	{
		if (charmOfCourage && isInSiege())
			return;
		
		if ((killed_by_pc || killed_by_siege_npc) && ((isInsideZone(L2Zone.FLAG_PVP) && !isInSiege()) || isInSiege()))
			return;
		
		// FIXME: Need Correct Penalty
		
		// Get the level of the L2Player
		final int lvl = getLevel();
		
		byte level = (byte)getLevel();
		
		int clan_luck = getSkillLevel(L2Skill.SKILL_CLAN_LUCK);
		
		double clan_luck_modificator = 1.0;
		
		if (!killed_by_pc)
		{
			switch (clan_luck)
			{
				case 3:
					clan_luck_modificator = 0.8;
					break;
				case 2:
					clan_luck_modificator = 0.8;
					break;
				case 1:
					clan_luck_modificator = 0.88;
					break;
				default:
					clan_luck_modificator = 1.0;
					break;
			}
		}
		else
		{
			switch (clan_luck)
			{
				case 3:
					clan_luck_modificator = 0.5;
					break;
				case 2:
					clan_luck_modificator = 0.5;
					break;
				case 1:
					clan_luck_modificator = 0.5;
					break;
				default:
					clan_luck_modificator = 1.0;
					break;
			}
		}
		
		// The death steal you some Exp
		double percentLost = Config.PLAYER_XP_PERCENT_LOST[getLevel()] * clan_luck_modificator;
		
		switch (level)
		{
			case 78:
				percentLost = (1.5 * clan_luck_modificator);
				break;
			case 77:
				percentLost = (2.0 * clan_luck_modificator);
				break;
			case 76:
				percentLost = (2.5 * clan_luck_modificator);
				break;
			default:
				if (level < 40)
					percentLost = (7.0 * clan_luck_modificator);
				else if (level >= 40 && level <= 75)
					percentLost = (4.0 * clan_luck_modificator);
				break;
		}
		
		if (getKarma() > 0)
			percentLost *= Config.RATE_KARMA_EXP_LOST;
		
		if (isFestivalParticipant() || atwar)
			percentLost /= 4.0;
		
		// Calculate the Experience loss
		final long lostExp;
		
		if (lvl < Experience.MAX_LEVEL)
			lostExp =
					Math.round((getStat().getExpForLevel(lvl + 1) - getStat().getExpForLevel(lvl)) * percentLost / 100);
		else
			lostExp =
					Math.round((getStat().getExpForLevel(Experience.MAX_LEVEL) - getStat().getExpForLevel(
							Experience.MAX_LEVEL - 1))
							* percentLost / 100);
		
		if (_log.isDebugEnabled())
			_log.debug(getName() + " died and lost " + lostExp + " experience.");
		
		// Get the Experience before applying penalty
		setExpBeforeDeath(getExp());
		
		// Set the new Experience value of the L2Player
		getStat().addExp(-lostExp);
	}
	
	public void deathPenalty(boolean atwar, boolean killed_by_pc, boolean killed_by_siege_npc)
	{
		deathPenalty(atwar, killed_by_pc, getCharmOfCourage(), killed_by_siege_npc);
	}
	
	public boolean isLookingForParty()
	{
		return _lookingForParty;
	}
	
	public boolean getPartyMatchingLevelRestriction()
	{
		return !_partyMatchingAllLevels;
	}
	
	public int getPartyMatchingRegion()
	{
		return _partyMatchingRegion;
	}
	
	public void setLookingForParty(boolean matching)
	{
		_lookingForParty = matching;
	}
	
	public void setPartyMatchingLevelRestriction(boolean off)
	{
		_partyMatchingAllLevels = off;
	}
	
	public void setPartyMatchingRegion(int region)
	{
		_partyMatchingRegion = region;
	}
	
	public L2PartyRoom getPartyRoom()
	{
		return _partyRoom;
	}
	
	/**
	 * Set the _partyRoom object of the L2Player (without joining it).
	 * @param room new party room
	 */
	public void setPartyRoom(L2PartyRoom room)
	{
		_partyRoom = room;
	}
	
	/**
	 * Stop the HP/MP/CP Regeneration task.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Set the RegenActive flag to False </li>
	 * <li>Stop the HP/MP/CP Regeneration task </li><BR><BR>
	 *
	 */
	public void stopAllTimers()
	{
		getStatus().stopHpMpRegeneration();
		stopWarnUserTakeBreak();
		stopAutoSaveTask();
		stopWaterTask();
		
		stopFeed();
		clearPetData();
		storePetFood(_mountNpcId);
		
		stopSoulTask();
		stopChargeTask();
		stopFameTask();
		stopVitalityTask();
		
		stopPvPFlag();
		stopJailTask(true);
	}
	
	/**
	 * Return the L2Summon of the L2Player or null.<BR><BR>
	 */
	@Override
	public L2Summon getPet()
	{
		return _summon;
	}
	
	/**
	 * Return the L2Decoy of the L2Player or null.<BR><BR>
	 */
	public L2Decoy getDecoy()
	{
		return _decoy;
	}
	
	/**
	 * Return the L2Trap of the L2Player or null.<BR><BR>
	 */
	public L2Trap getTrap()
	{
		return _trap;
	}
	
	/**
	 * Set the L2Summon of the L2Player.<BR><BR>
	 */
	public void setPet(L2Summon summon)
	{
		_summon = summon;
		// update attack element value display
		if ((_summon == null || _summon instanceof L2SummonInstance) && getClassId().isSummoner()
				&& getAttackElement() != Elementals.NONE)
			sendPacket(new UserInfo(this));
	}
	
	/**
	 * Set the L2Decoy of the L2Player.<BR><BR>
	 */
	public void setDecoy(L2Decoy decoy)
	{
		_decoy = decoy;
	}
	
	/**
	 * Set the L2Trap of this L2Player<BR><BR>
	 * @param trap
	 */
	public void setTrap(L2Trap trap)
	{
		_trap = trap;
	}
	
	/**
	 * Return the L2Summon of the L2Player or null.<BR><BR>
	 */
	public L2TamedBeastInstance getTrainedBeast()
	{
		return _tamedBeast;
	}
	
	/**
	 * Set the L2Summon of the L2Player.<BR><BR>
	 */
	public void setTrainedBeast(L2TamedBeastInstance tamedBeast)
	{
		_tamedBeast = tamedBeast;
	}
	
	/**
	 * Return the L2Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).<BR><BR>
	 */
	public L2Request getRequest()
	{
		if (_request == null)
			_request = new L2Request(this);
		
		return _request;
	}
	
	/**
	 * Set the L2Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).<BR><BR>
	 */
	public synchronized void setActiveRequester(L2Player requester)
	{
		_activeRequester = requester;
	}
	
	/**
	 * Return true if last request is expired.
	 * @return
	 */
	public boolean isRequestExpired()
	{
		return !(_requestExpireTime > GameTimeManager.getGameTicks());
	}
	
	/**
	 * Return the L2Player requester of a transaction (ex : FriendInvite, JoinAlly, JoinParty...).<BR><BR>
	 */
	public L2Player getActiveRequester()
	{
		return _activeRequester;
	}
	
	/**
	 * Return True if a transaction is in progress.<BR><BR>
	 */
	public boolean isProcessingRequest()
	{
		return _activeRequester != null || _requestExpireTime > GameTimeManager.getGameTicks();
	}
	
	/**
	 * Return True if a transaction is in progress.<BR><BR>
	 */
	public boolean isProcessingTransaction()
	{
		return _activeRequester != null || _activeTradeList != null
				|| _requestExpireTime > GameTimeManager.getGameTicks();
	}
	
	/**
	 * Select the Warehouse to be used in next activity.<BR><BR>
	 */
	public void onTransactionRequest(L2Player partner)
	{
		_requestExpireTime = GameTimeManager.getGameTicks() + REQUEST_TIMEOUT * GameTimeManager.TICKS_PER_SECOND;
		partner.setActiveRequester(this);
	}
	
	/**
	 * Select the Warehouse to be used in next activity.<BR><BR>
	 */
	public void onTransactionResponse()
	{
		_requestExpireTime = 0;
	}
	
	/**
	 * Select the Warehouse to be used in next activity.<BR><BR>
	 */
	public void setActiveWarehouse(ItemContainer warehouse)
	{
		_activeWarehouse = warehouse;
	}
	
	/**
	 * Return active Warehouse.<BR><BR>
	 */
	public ItemContainer getActiveWarehouse()
	{
		return _activeWarehouse;
	}
	
	/**
	 * Select the TradeList to be used in next activity.<BR><BR>
	 */
	public void setActiveTradeList(TradeList tradeList)
	{
		_activeTradeList = tradeList;
	}
	
	/**
	 * Return active TradeList.<BR><BR>
	 */
	public TradeList getActiveTradeList()
	{
		return _activeTradeList;
	}
	
	public void onTradeStart(L2Player partner)
	{
		_activeTradeList = new TradeList(this);
		_activeTradeList.setPartner(partner);
		
		SystemMessage msg = new SystemMessage(SystemMessageId.BEGIN_TRADE_WITH_C1);
		msg.addPcName(partner);
		sendPacket(msg);
		sendPacket(new TradeStart(this));
	}
	
	public void onTradeConfirm(L2Player partner)
	{
		SystemMessage msg = new SystemMessage(SystemMessageId.C1_CONFIRMED_TRADE);
		msg.addPcName(partner);
		sendPacket(msg);
		sendPacket(TradeOtherDone.STATIC_PACKET);
	}
	
	public void onTradeCancel(L2Player partner)
	{
		if (_activeTradeList == null)
			return;
		
		_activeTradeList.lock();
		_activeTradeList = null;
		sendPacket(TradeDone.CANCELLED);
		SystemMessage msg = new SystemMessage(SystemMessageId.C1_CANCELED_TRADE);
		msg.addPcName(partner);
		sendPacket(msg);
	}
	
	public void onTradeFinish(boolean successfull)
	{
		_activeTradeList = null;
		sendPacket(TradeDone.COMPLETED);
		if (successfull)
			sendPacket(SystemMessageId.TRADE_SUCCESSFUL);
	}
	
	public void startTrade(L2Player partner)
	{
		onTradeStart(partner);
		partner.onTradeStart(this);
	}
	
	public void cancelActiveTrade()
	{
		if (_activeTradeList == null)
			return;
		
		L2Player partner = _activeTradeList.getPartner();
		if (partner != null)
			partner.onTradeCancel(this);
		onTradeCancel(this);
	}
	
	/**
	 * Return the _createList object of the L2Player.<BR><BR>
	 */
	public L2ManufactureList getCreateList()
	{
		return _createList;
	}
	
	/**
	 * Set the _createList object of the L2Player.<BR><BR>
	 */
	public void setCreateList(L2ManufactureList x)
	{
		_createList = x;
	}
	
	/**
	 * Return the _buyList object of the L2Player.<BR><BR>
	 */
	public TradeList getSellList()
	{
		if (_sellList == null)
			_sellList = new TradeList(this);
		return _sellList;
	}
	
	/**
	 * Return the _buyList object of the L2Player.<BR><BR>
	 */
	public TradeList getBuyList()
	{
		if (_buyList == null)
			_buyList = new TradeList(this);
		return _buyList;
	}
	
	/**
	 * Set the Private Store type of the L2Player.<BR><BR>
	 *
	 * <B><U> Values </U> :</B><BR><BR>
	 * <li>0 : STORE_PRIVATE_NONE</li>
	 * <li>1 : STORE_PRIVATE_SELL</li>
	 * <li>2 : sellmanage</li><BR>
	 * <li>3 : STORE_PRIVATE_BUY</li><BR>
	 * <li>4 : buymanage</li><BR>
	 * <li>5 : STORE_PRIVATE_MANUFACTURE</li><BR>
	 *
	 */
	public void setPrivateStoreType(int type)
	{
		_privatestore = type;
	}
	
	/**
	 * Return the Private Store type of the L2Player.<BR><BR>
	 *
	 * <B><U> Values </U> :</B><BR><BR>
	 * <li>0 : STORE_PRIVATE_NONE</li>
	 * <li>1 : STORE_PRIVATE_SELL</li>
	 * <li>2 : sellmanage</li><BR>
	 * <li>3 : STORE_PRIVATE_BUY</li><BR>
	 * <li>4 : buymanage</li><BR>
	 * <li>5 : STORE_PRIVATE_MANUFACTURE</li><BR>
	 *
	 */
	public int getPrivateStoreType()
	{
		return _privatestore;
	}
	
	/**
	 * Set the _skillLearningClassId object of the L2Player.<BR><BR>
	 */
	public void setSkillLearningClassId(ClassId classId)
	{
		_skillLearningClassId = classId;
	}
	
	/**
	 * Return the _skillLearningClassId object of the L2Player.<BR><BR>
	 */
	public ClassId getSkillLearningClassId()
	{
		return _skillLearningClassId;
	}
	
	/**
	 * Set the _clan object, _clanId, _clanLeader Flag and title of the L2Player.<BR><BR>
	 */
	public void setClan(L2Clan clan)
	{
		_clan = clan;
		setTitle("");
		
		if (clan == null)
		{
			_clanId = 0;
			_clanPrivileges = 0;
			_subPledgeType = 0;
			_pledgeRank = 0;
			_lvlJoinedAcademy = 0;
			_apprentice = 0;
			_sponsor = 0;
			return;
		}
		
		if (!clan.isMember(getObjectId()))
		{
			// Char has been kicked from clan
			setClan(null);
			return;
		}
		
		_clanId = clan.getClanId();
	}
	
	/**
	 * Return the _clan object of the L2Player.<BR><BR>
	 */
	public L2Clan getClan()
	{
		return _clan;
	}
	
	/**
	 * Return True if the L2Player is the leader of its clan.<BR><BR>
	 */
	public boolean isClanLeader()
	{
		return (getClan() != null) && getObjectId() == getClan().getLeaderId();
	}
	
	/**
	 * Disarm the player's weapon and shield.<BR><BR>
	 */
	public boolean disarmWeapons(boolean shield)
	{
		// Don't allow disarming a cursed weapon
		if (isCursedWeaponEquipped())
			return false;
		
		// Unequip the weapon
		L2ItemInstance wpn = getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
		if (wpn == null)
			wpn = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		if (wpn != null)
		{
			if (wpn.isWear())
				return false;
			
			L2ItemInstance[] unequipped = getInventory().unEquipItemInBodySlotAndRecord(wpn.getItem().getBodyPart());
			InventoryUpdate iu = new InventoryUpdate();
			for (L2ItemInstance element : unequipped)
				iu.addModifiedItem(element);
			sendPacket(iu);
			
			abortAttack();
			refreshExpertisePenalty();
			
			// This can be 0 if the user pressed the right mousebutton twice very fast
			if (unequipped.length > 0)
			{
				SystemMessage sm = null;
				if (unequipped[0].getEnchantLevel() > 0)
				{
					sm = new SystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
					sm.addNumber(unequipped[0].getEnchantLevel());
					sm.addItemName(unequipped[0]);
				}
				else
				{
					sm = new SystemMessage(SystemMessageId.S1_DISARMED);
					sm.addItemName(unequipped[0]);
				}
				sendPacket(sm);
			}
			broadcastFullInfoImpl();
		}
		
		if (!shield)
			return true;
		
		// Unequip the shield
		L2ItemInstance sld = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		if (sld != null)
		{
			if (sld.isWear())
				return false;
			
			L2ItemInstance[] unequipped = getInventory().unEquipItemInBodySlotAndRecord(sld.getItem().getBodyPart());
			InventoryUpdate iu = new InventoryUpdate();
			for (L2ItemInstance element : unequipped)
				iu.addModifiedItem(element);
			sendPacket(iu);
			
			abortAttack();
			refreshExpertisePenalty();
			
			// This can be 0 if the user pressed the right mousebutton twice very fast
			if (unequipped.length > 0)
			{
				SystemMessage sm = null;
				if (unequipped[0].getEnchantLevel() > 0)
				{
					sm = new SystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
					sm.addNumber(unequipped[0].getEnchantLevel());
					sm.addItemName(unequipped[0]);
				}
				else
				{
					sm = new SystemMessage(SystemMessageId.S1_DISARMED);
					sm.addItemName(unequipped[0]);
				}
				sendPacket(sm);
			}
			broadcastFullInfoImpl();
		}
		
		return true;
	}
	
	/**
	 * Reduce the number of arrows/bolts owned by the L2Player and send it Server->Client Packet InventoryUpdate or ItemList (to unequip if the last arrow was consummed).<BR><BR>
	 */
	@Override
	protected void reduceArrowCount(boolean bolts)
	{
		L2ItemInstance arrows = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		
		if (arrows == null)
		{
			getInventory().unEquipItemInSlot(Inventory.PAPERDOLL_LHAND);
			if (bolts)
				_boltItem = null;
			else
				_arrowItem = null;
			sendPacket(new ItemList(this, false));
			return;
		}
		
		// Adjust item quantity
		if (arrows.getCount() > 1)
		{
			synchronized (arrows)
			{
				arrows.changeCountWithoutTrace(-1, this, null);
				arrows.setLastChange(L2ItemInstance.MODIFIED);
				
				// Could do also without saving, but let's save approx 1 of 10
				if (GameTimeManager.getGameTicks() % 10 == 0)
					arrows.updateDatabase();
				getInventory().refreshWeight();
			}
		}
		else
		{
			// Destroy entire item and save to database
			getInventory().destroyItem("Consume", arrows, this, null);
			
			getInventory().unEquipItemInSlot(Inventory.PAPERDOLL_LHAND);
			if (bolts)
				_boltItem = null;
			else
				_arrowItem = null;
			
			if (_log.isDebugEnabled())
				_log.debug("removed arrows count");
			sendPacket(new ItemList(this, false));
			return;
		}
		
		if (!Config.FORCE_INVENTORY_UPDATE)
		{
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(arrows);
			sendPacket(iu);
		}
		else
			sendPacket(new ItemList(this, false));
	}
	
	/**
	 * Equip arrows needed in left hand and send a Server->Client packet ItemList to the L2Player then return True.<BR><BR>
	 */
	@Override
	protected boolean checkAndEquipArrows()
	{
		// Check if nothing is equipped in left hand
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null)
		{
			// Get the L2ItemInstance of the arrows needed for this bow
			_arrowItem = getInventory().findArrowForBow(getActiveWeaponItem());
			
			if (_arrowItem != null)
			{
				// Equip arrows needed in left hand
				getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, _arrowItem);
				// Send inventory update packet
				getInventory().updateInventory(_arrowItem);
			}
		}
		else
		{
			// Get the L2ItemInstance of arrows equipped in left hand
			_arrowItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		}
		
		return _arrowItem != null;
	}
	
	/**
	 * Equip bolts needed in left hand and send a Server->Client packet ItemList to the L2Player then return True.<BR><BR>
	 */
	@Override
	protected boolean checkAndEquipBolts()
	{
		// Check if nothing is equipped in left hand
		if (getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND) == null)
		{
			// Get the L2ItemInstance of the arrows needed for this bow
			_boltItem = getInventory().findBoltForCrossBow(getActiveWeaponItem());
			
			if (_boltItem != null)
			{
				// Equip arrows needed in left hand
				getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, _boltItem);
				
				// Send a Server->Client packet ItemList to this L2Player to update left hand equipement
				ItemList il = new ItemList(this, false);
				sendPacket(il);
			}
		}
		else
		{
			// Get the L2ItemInstance of arrows equipped in left hand
			_boltItem = getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		}
		
		return _boltItem != null;
	}
	
	public boolean mount(L2Summon pet)
	{
		// TODO: all checks from usercommandhandler mount and requestactionuse should be handled in ONE place, and this is not L2Player
		// so this is temporary
		if (!isInsideRadius(pet, 80, true, false))
		{
			sendPacket(SystemMessageId.TOO_FAR_AWAY_FROM_FENRIR_TO_MOUNT);
			return false;
		}
		else if (!GeoData.getInstance().canSeeTarget(this, pet))
		{
			sendPacket(SystemMessageId.CANT_SEE_TARGET);
			return false;
		}
		
		if (!disarmWeapons(true))
			return false;
		if (isTransformed())
			return false;
		
		for (L2Effect e : getAllEffects())
		{
			if (e != null && e.getSkill().isToggle())
				e.exit();
		}
		
		Ride mount = new Ride(this, true, pet.getTemplate().getNpcId());
		setMount(pet.getNpcId(), pet.getLevel(), mount.getMountType());
		setMountObjectID(pet.getControlItemId());
		clearPetData();
		startFeed(pet.getNpcId());
		broadcastPacket(mount);
		
		// Notify self and others about speed change
		broadcastUserInfo();
		
		pet.unSummon(this);
		
		return true;
	}
	
	public boolean remount(L2Player player)
	{
		Ride dismount = new Ride(this, false, 0);
		Ride mount = new Ride(this, true, getMountNpcId());
		
		player.sendPacket(dismount);
		player.sendPacket(mount);
		return true;
	}
	
	public boolean mount(int npcId, int controlItemObjId, boolean useFood)
	{
		if (!disarmWeapons(true))
			return false;
		if (isTransformed())
			return false;
		
		for (L2Effect e : getAllEffects())
		{
			if (e != null && e.getSkill().isToggle())
				e.exit();
		}
		
		Ride mount = new Ride(this, true, npcId);
		if (setMount(npcId, getLevel(), mount.getMountType()))
		{
			clearPetData();
			setMountObjectID(controlItemObjId);
			broadcastPacket(mount);
			// Notify self and others about speed change
			broadcastFullInfoImpl();
			if (useFood)
				startFeed(npcId);
			return true;
		}
		return false;
	}
	
	public boolean mountPlayer(L2Summon pet)
	{
		if (pet != null && pet.isMountable() && !isMounted() && !isBetrayed() && !pet.isOutOfControl())
		{
			if (pet.getNpcId() == 16030 && pet.getLevel() < Config.GREAT_WOLF_MOUNT_LEVEL)
			{
				sendMessage("Your Wolf needs minimum level " + Config.GREAT_WOLF_MOUNT_LEVEL);
				return false;
			}
			else if (isInEvent(CTFPlayerInfo.class) && as(CTFPlayerInfo.class)._haveFlagCTF)
			{
				// You cannot mount a steed while holding a flag.
				sendPacket(SystemMessageId.YOU_CANNOT_MOUNT_A_STEED_WHILE_HOLDING_A_FLAG);
				return false;
			}
			else if (isParalyzed())
			{
				// You cannot mount a steed while petrified.
				sendPacket(SystemMessageId.YOU_CANNOT_MOUNT_A_STEED_WHILE_PETRIFIED);
				return false;
			}
			else if (isDead())
			{
				//A strider cannot be ridden when dead
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.STRIDER_CANT_BE_RIDDEN_WHILE_DEAD);
				return false;
			}
			else if (pet.isDead())
			{
				//A dead strider cannot be ridden.
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.DEAD_STRIDER_CANT_BE_RIDDEN);
				return false;
			}
			else if (pet.isInCombat() || pet.isRooted() || pet.isParalyzed())
			{
				//A strider in battle cannot be ridden
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.STRIDER_IN_BATLLE_CANT_BE_RIDDEN);
				return false;
				
			}
			else if (isInCombat())
			{
				//A strider cannot be ridden while in battle
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.STRIDER_CANT_BE_RIDDEN_WHILE_IN_BATTLE);
				return false;
			}
			else if (isSitting() || isInsideZone(L2Zone.FLAG_WATER))
			{
				//A strider can be ridden only when standing
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.STRIDER_CAN_BE_RIDDEN_ONLY_WHILE_STANDING);
				return false;
			}
			else if (isFishing())
			{
				//You can't mount, dismount, break and drop items while fishing
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.CANNOT_DO_WHILE_FISHING_2);
				return false;
			}
			else if (isInDuel())
			{
				// You cannot mount a steed while in a duel.
				sendPacket(SystemMessageId.YOU_CANNOT_MOUNT_A_STEED_WHILE_IN_A_DUEL);
				return false;
			}
			else if (isTransformed() || isCursedWeaponEquipped())
			{
				// no message needed, player while transformed doesn't have mount action
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			else if (getInventory().getItemByItemId(9819) != null)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.YOU_CANNOT_MOUNT_A_STEED_WHILE_HOLDING_A_FLAG); // TODO: confirm this message
				return false;
			}
			else if (isCastingNow())
			{
				// You cannot mount a steed while skill casting.
				sendPacket(SystemMessageId.YOU_CANNOT_MOUNT_A_STEED_WHILE_SKILL_CASTING);
				return false;
			}
			else if (pet.isHungry())
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.HUNGRY_STRIDER_NOT_MOUNT);
				return false;
			}
			else if (!Util.checkIfInRange(200, this, pet, true))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.TOO_FAR_AWAY_FROM_FENRIR_TO_MOUNT);
				return false;
			}
			else if (!pet.isDead() && !isMounted())
			{
				mount(pet);
			}
		}
		else if (isRentedPet())
		{
			stopRentPet();
		}
		else if (isMounted())
		{
			if (getMountType() == 2 && isInsideZone(L2Zone.FLAG_NOWYVERN))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.NO_DISMOUNT_HERE);
				return false;
			}
			else if (isHungry())
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessageId.HUNGRY_STRIDER_NOT_MOUNT);
				return false;
			}
			else if (ObjectRestrictions.getInstance().checkRestriction(this, AvailableRestriction.PlayerUnmount))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				sendMessage("You cannot dismount due to a restriction.");
				return false;
			}
			else
				dismount();
		}
		return true;
	}
	
	public boolean dismount()
	{
		boolean wasFlying = isFlying();
		
		sendPacket(new SetupGauge(3, 0, 0));
		int petId = _mountNpcId;
		if (setMount(0, 0, 0))
		{
			stopFeed();
			clearPetData();
			
			if (wasFlying)
				removeSkill(SkillTable.getInstance().getInfo(4289, 1));
			Ride dismount = new Ride(this, false, 0);
			broadcastPacket(dismount);
			setMountObjectID(0);
			storePetFood(petId);
			
			// Notify self and others about speed change
			broadcastUserInfo();
			return true;
		}
		return false;
	}
	
	/**
	 * Return True if the L2Player use a dual weapon.<BR><BR>
	 */
	@Override
	public boolean isUsingDualWeapon()
	{
		L2Weapon weaponItem = getActiveWeaponItem();
		
		if (weaponItem == null)
			return false;
		if (weaponItem.getItemType() == L2WeaponType.DUAL)
		{
			return true;
		}
		else if (weaponItem.getItemType() == L2WeaponType.DUAL_DAGGER)
		{
			return true;
		}
		else if (weaponItem.getItemType() == L2WeaponType.DUALFIST)
		{
			return true;
		}
		else if (weaponItem.getItemId() == 248) // Orc fighter fists
		{
			return true;
		}
		else
			return weaponItem.getItemId() == 252;
	}
	
	public void setUptime(long time)
	{
		_uptime = time;
	}
	
	public long getUptime()
	{
		return System.currentTimeMillis() - _uptime;
	}
	
	public long getOnlineTime()
	{
		long totalOnlineTime = _onlineTime;
		
		if (_onlineBeginTime > 0)
			totalOnlineTime += (System.currentTimeMillis() - _onlineBeginTime) / 1000;
		
		return totalOnlineTime;
	}
	
	/**
	 * Return True if the L2Player is invulnerable.<BR><BR>
	 */
	@Override
	public boolean isInvul()
	{
		return super.isInvul() || _protectEndTime > GameTimeManager.getGameTicks();
	}
	
	/**
	 * Return True if the L2Player has a Party in progress.<BR><BR>
	 */
	@Override
	public boolean isInParty()
	{
		return _party != null;
	}
	
	/**
	 * Set the _party object of the L2Player (without joining it).<BR><BR>
	 */
	public void setParty(L2Party party)
	{
		_party = party;
	}
	
	/**
	 * Set the _party object of the L2Player AND join it.<BR><BR>
	 */
	public void joinParty(L2Party party)
	{
		if (party != null)
		{
			// First set the party otherwise this wouldn't be considered
			// as in a party into the L2Creature.updateEffectIcons() call.
			setParty(party);
			if (!party.addPartyMember(this))
				setParty(null);
		}
	}
	
	/**
	 * Manage the Leave Party task of the L2Player.<BR><BR>
	 */
	public void leaveParty()
	{
		if (isInParty())
		{
			_party.removePartyMember(this, false);
			_party = null;
		}
	}
	
	/**
	 * Return the _party object of the L2Player.<BR><BR>
	 */
	@Override
	public L2Party getParty()
	{
		return _party;
	}
	
	/**
	 * Set the _isGm Flag of the L2Player.<BR><BR>
	 */
	public void setIsGM(boolean status)
	{
		_isGm = status;
	}
	
	/**
	 * Return True if the L2Player is a GM.<BR><BR>
	 */
	public boolean isGM()
	{
		return _isGm;
	}
	
	/**
	 * Set the _accessLevel of the L2Player.<BR><BR>
	 */
	public void setAccessLevel(int level)
	{
		_accessLevel = level;
		if (_accessLevel >= Config.GM_MIN)
			setIsGM(true);
	}
	
	public void setAccountAccesslevel(int level)
	{
		LoginLink.getInstance().sendAccessLevel(getAccountName(), level);
	}
	
	/**
	 * Return the _accessLevel of the L2Player.<BR><BR>
	 */
	public int getAccessLevel()
	{
		return _accessLevel;
	}
	
	@Override
	public double getLevelMod()
	{
		return (89 + getLevel()) / 100.0;
	}
	
	/**
	 * Send a Server->Client StatusUpdate packet with Karma and PvP Flag to the L2Player and all L2Player to inform (broadcast).<BR><BR>
	 * @param flag
	 */
	public void setKarmaFlag(int flag)
	{
		sendPacket(new UserInfo(this));
		broadcastRelationChanged();
	}
	
	/**
	 * Send a Server->Client StatusUpdate packet with Karma to the L2Player and all L2Player to inform (broadcast).<BR><BR>
	 */
	public void broadcastKarma()
	{
		StatusUpdate su = new StatusUpdate(getObjectId());
		su.addAttribute(StatusUpdate.KARMA, getKarma());
		sendPacket(su);
		broadcastRelationChanged();
	}
	
	/**
	 * Set the online Flag to True or False and update the characters table of the database with online status and lastAccess (called when login and logout).<BR><BR>
	 */
	public void setOnlineStatus(boolean isOnline)
	{
		final byte value = isOnline ? ONLINE_STATE_ONLINE : ONLINE_STATE_DELETED;
		
		if (_isOnline != value)
		{
			_isOnline = value;
			
			// Update the characters table of the database with online status and lastAccess (called when login and logout)
			updateOnlineStatusInDb();
		}
	}
	
	public void setIsIn7sDungeon(boolean isIn7sDungeon)
	{
		_isIn7sDungeon = isIn7sDungeon;
	}
	
	/**
	 * Update the characters table of the database with online status and lastAccess of this L2Player (called when
	 * login and logout).<BR>
	 * <BR>
	 */
	private void updateOnlineStatusInDb()
	{
		RecordTable.getInstance().update();
		
		SQLQueue.getInstance().add(new SQLQuery() {
			@Override
			public void execute(Connection con)
			{
				try
				{
					PlayerRepository.getInstance().saveOnlineStatus(getObjectId(), isOnline() != 0,
							System.currentTimeMillis());
				}
				catch (Exception e)
				{
					_log.error("Failed updating character online status.", e);
				}
			}
		});
	}
	
	/**
	 * Create a new player in the characters table of the database.<BR><BR>
	 */
	private boolean createDb()
	{
		try
		{
			PlayerRepository.getInstance().insertPlayer(buildPlayerRow());
		}
		catch (Exception e)
		{
			_log.error("Could not insert char data: ", e);
			return false;
		}
		
		return true;
	}
	
	/** HP, CP and MP are stored as whole points that are never negative. */
	private static int toStoredPoints(double points)
	{
		return (int)Math.max(0, Math.round(points));
	}
	
	/** @return the values of this player that the player row stores; level, exp and sp are those of the base class */
	private PlayerRow buildPlayerRow()
	{
		// The exp, level, and sp of the base class go to the player row, whichever class is active. They are read
		// as such: the class index of a live player is not changed for the time of a save.
		long exp = getStat().getBaseClassExp();
		int level = getStat().getBaseClassLevel();
		int sp = getStat().getBaseClassSp();
		
		PlayerRow row = new PlayerRow();
		row.id = getObjectId();
		row.accountName = _accountName;
		row.name = getName();
		row.title = getTitle();
		row.raceId = getRace().ordinal();
		row.activeClassId = getClassId().getId();
		row.baseClassId = getBaseClass();
		row.female = getAppearance().getSex();
		row.face = getAppearance().getFace();
		row.hairStyle = getAppearance().getHairStyle();
		row.hairColor = getAppearance().getHairColor();
		row.level = level;
		row.exp = exp;
		row.expBeforeDeath = Math.max(0L, getExpBeforeDeath());
		row.sp = sp;
		row.maxHp = getMaxHp();
		row.currentHp = toStoredPoints(getStatus().getCurrentHp());
		row.maxCp = getMaxCp();
		row.currentCp = toStoredPoints(getStatus().getCurrentCp());
		row.maxMp = getMaxMp();
		row.currentMp = toStoredPoints(getStatus().getCurrentMp());
		row.x = _observerMode ? _obsX : getX();
		row.y = _observerMode ? _obsY : getY();
		row.z = _observerMode ? _obsZ : getZ();
		row.heading = getHeading();
		row.karma = getKarma();
		row.fame = getFame();
		row.pvpKills = getPvpKills();
		row.pkKills = getPkKills();
		row.accessLevel = getAccessLevel();
		row.online = isOnline() != 0;
		row.onlineTimeSeconds = getOnlineTime();
		row.deleteAt = getDeleteTimer();
		row.noble = isNoble();
		row.inSevenSignsDungeon = isIn7sDungeon();
		row.inJail = isInJail();
		row.jailRemainingMillis = Math.max(0L, getJailTimer());
		row.newbieRewardMask = getNewbie();
		row.clanId = getClanId();
		row.pledgeType = getSubPledgeType();
		row.pledgeRank = Math.max(0, getPledgeRank());
		row.wantsPeace = getWantsPeace() != 0;
		row.academyJoinLevel = getLvlJoinedAcademy();
		row.apprenticeId = getApprentice();
		row.sponsorId = getSponsor();
		row.clanJoinAllowedAt = getClanJoinExpiryTime();
		row.clanCreateAllowedAt = getClanCreateExpiryTime();
		row.varkaKetraAlliance = getAllianceWithVarkaKetra();
		row.deathPenaltyLevel = getDeathPenaltyBuffLevel();
		row.vitalityPoints = getVitalityPoints();
		row.bookmarkSlots = getBookMarkSlot();
		return row;
	}
	
	public static void disconnectIfOnline(int objectId)
	{
		L2Player onlinePlayer = L2World.getInstance().findPlayer(objectId);
		
		if (onlinePlayer == null)
			onlinePlayer = L2World.getInstance().getPlayer(CharNameTable.getInstance().getByObjectId(objectId));
		
		if (onlinePlayer == null)
			return;
		
		if (!onlinePlayer.isInOfflineMode())
			_log.warn("Avoiding duplicate character! Disconnecting online character (" + onlinePlayer.getName() + ")");
		
		new Disconnection(onlinePlayer).defaultSequence(true);
	}
	
	public static void disconnectIfOnline(String accountName)
	{
		for (int objectId : CharNameTable.getInstance().getObjectIdsForAccount(accountName))
			disconnectIfOnline(objectId);
	}
	
	/**
	 * Retrieve a L2Player from the characters table of the database and add it in _allObjects of the L2world.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Retrieve the L2Player from the characters table of the database </li>
	 * <li>Add the L2Player object in _allObjects </li>
	 * <li>Set the x,y,z position of the L2Player and make it invisible</li>
	 * <li>Update the overloaded status of the L2Player</li><BR><BR>
	 *
	 * @param objectId Identifier of the object to initialized
	 *
	 * @return The L2Player loaded from the database
	 *
	 */
	public static L2Player load(int objectId)
	{
		disconnectIfOnline(objectId);
		
		L2Player player = null;
		
		try
		{
			// Retrieve the L2Player from the player table of the database
			PlayerRow row = PlayerRepository.getInstance().loadPlayer(objectId);
			
			double currentHp = 1, currentMp = 1, currentCp = 1;
			if (row != null)
			{
				final int activeClassId = row.activeClassId;
				final boolean female = row.female;
				final L2PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(activeClassId);
				PlayerAppearance app =
						new PlayerAppearance((byte)row.face, (byte)row.hairColor, (byte)row.hairStyle, female);
				
				player = new L2Player(objectId, template, row.accountName, app);
				player.setName(row.name);
				player._lastAccess = row.lastAccess;
				
				player.getStat().setExp(row.exp);
				player.setExpBeforeDeath(row.expBeforeDeath);
				player.getStat().setLevel((byte)row.level);
				player.getStat().setSp(row.sp);
				
				player.setWantsPeace(row.wantsPeace ? 1 : 0);
				
				player.setHeading(row.heading);
				
				player.setKarma(row.karma);
				player.setFame(row.fame);
				player.setPvpKills(row.pvpKills);
				player.setPkKills(row.pkKills);
				
				player.setClanJoinExpiryTime(row.clanJoinAllowedAt);
				if (player.getClanJoinExpiryTime() < System.currentTimeMillis())
				{
					player.setClanJoinExpiryTime(0);
				}
				player.setClanCreateExpiryTime(row.clanCreateAllowedAt);
				if (player.getClanCreateExpiryTime() < System.currentTimeMillis())
				{
					player.setClanCreateExpiryTime(0);
				}
				
				int clanId = row.clanId;
				
				if (clanId > 0)
				{
					player.setClan(ClanTable.getInstance().getClan(clanId));
				}
				
				player.setDeleteTimer(row.deleteAt);
				player.setOnlineTime(row.onlineTimeSeconds);
				player.setNewbie(row.newbieRewardMask);
				player.setNoble(row.noble);
				
				player.setTitle(row.title);
				player.setAccessLevel(row.accessLevel);
				player.setFistsWeaponItem(player.findFistsWeaponItem(activeClassId));
				player.setUptime(System.currentTimeMillis());
				
				// Only 1 line needed for each and their values only have to be set once as long as you don't die before it's set.
				currentHp = row.currentHp;
				currentMp = row.currentMp;
				currentCp = row.currentCp;
				
				player._classIndex = 0;
				try
				{
					player.setBaseClass(row.baseClassId);
				}
				catch (Exception e)
				{
					player.setBaseClass(activeClassId);
				}
				
				// Restore Subclass Data (cannot be done earlier in function)
				if (restoreSubClassData(player))
				{
					if (activeClassId != player.getBaseClass())
					{
						for (SubClass subClass : player.getSubClasses().values())
							if (subClass.getClassId() == activeClassId)
								player._classIndex = subClass.getClassIndex();
					}
				}
				if (player.getClassIndex() == 0 && activeClassId != player.getBaseClass())
				{
					// Subclass in use but doesn't exist in DB -
					// a possible restart-while-modifysubclass cheat has been attempted.
					// Switching to use base class
					player.setClassId(player.getBaseClass());
					_log.warn("Player " + player.getName()
							+ " reverted to base class. Possibly has tried a relogin exploit while subclassing.");
				}
				else
					player._activeClass = activeClassId;
				
				player.setIsIn7sDungeon(row.inSevenSignsDungeon);
				player.setInJail(row.inJail);
				player.setJailTimer(row.jailRemainingMillis);
				if (player.isInJail())
					player.setJailTimer(row.jailRemainingMillis);
				else
					player.setJailTimer(0);
				
				CursedWeaponsManager.getInstance().onEnter(player);
				
				player.setNoble(row.noble);
				player.setCharViP(row.vip);
				player.setSubPledgeType(row.pledgeType);
				player.setPledgeRank(row.pledgeRank);
				player.setApprentice(row.apprenticeId);
				player.setSponsor(row.sponsorId);
				if (player.getClan() != null)
				{
					if (player.getClan().getLeaderId() != player.getObjectId())
					{
						if (player.getPledgeRank() == 0)
						{
							player.setPledgeRank(5);
						}
						player.setClanPrivileges(player.getClan().getRankPrivs(player.getPledgeRank()));
					}
					else
					{
						player.setClanPrivileges(L2Clan.CP_ALL);
						player.setPledgeRank(1);
					}
				}
				else
				{
					player.setClanPrivileges(L2Clan.CP_NOTHING);
				}
				player.setLvlJoinedAcademy(row.academyJoinLevel);
				player.setAllianceWithVarkaKetra(row.varkaKetraAlliance);
				player.setDeathPenaltyBuffLevel(row.deathPenaltyLevel);
				player.setVitalityPoints(row.vitalityPoints, true);
				
				// Add the L2Player object in _allObjects
				// L2World.getInstance().storeObject(player);
				
				// Set the x,y,z position of the L2Player and make it invisible
				player.getPosition().setXYZInvisible(row.x, row.y, row.z);
				
				// Set Teleport Bookmark Slot
				player.setBookMarkSlot(row.bookmarkSlots);
			}
			
			if (player == null)
				return null;
			
			// Retrieve from the database all secondary data of this L2Player
			// and reward expertise/lucky skills if necessary.
			player.restoreCharData();
			player.rewardSkills();
			
			// Buff and status icons
			player.getEffects().restoreEffects();
			player.restoreSkillReuses();
			
			player.stopEffects(L2EffectType.HEAL_OVER_TIME);
			player.stopEffects(L2EffectType.COMBAT_POINT_HEAL_OVER_TIME);
			
			// Restore current Cp, HP and MP values
			player.getStatus().setCurrentCp(currentCp);
			player.getStatus().setCurrentHp(currentHp);
			player.getStatus().setCurrentMp(currentMp);
			
			if (currentHp < 0.5)
			{
				player.setIsDead(true);
				player.getStatus().stopHpMpRegeneration();
			}
			
			// Restore pet if exists in the world
			player.setPet(L2World.getInstance().getPet(player.getObjectId()));
			if (player.getPet() != null)
				player.getPet().setOwner(player);
			
			// refresh overloaded already done when loading inventory
			// Update the expertise status of the L2Player
			player.refreshExpertisePenalty();
		}
		catch (Exception e)
		{
			_log.error("Failed loading character.", e);
		}
		
		return player;
	}
	
	/**
	 * @return
	 */
	public Forum getMail()
	{
		if (_forumMail == null)
		{
			setMail(ForumsBBSManager.getInstance().getForumByName("MailRoot").getChildByName(getName()));
			
			if (_forumMail == null)
			{
				ForumsBBSManager.getInstance().createNewForum(getName(),
						ForumsBBSManager.getInstance().getForumByName("MailRoot"), Forum.MAIL, Forum.OWNERONLY,
						getObjectId());
				setMail(ForumsBBSManager.getInstance().getForumByName("MailRoot").getChildByName(getName()));
			}
		}
		
		return _forumMail;
	}
	
	/**
	 * @param forum
	 */
	public void setMail(Forum forum)
	{
		_forumMail = forum;
	}
	
	/**
	 * @return
	 */
	public Forum getMemo()
	{
		if (_forumMemo == null)
		{
			setMemo(ForumsBBSManager.getInstance().getForumByName("MemoRoot").getChildByName(_accountName));
			
			if (_forumMemo == null)
			{
				ForumsBBSManager.getInstance().createNewForum(_accountName,
						ForumsBBSManager.getInstance().getForumByName("MemoRoot"), Forum.MEMO, Forum.OWNERONLY,
						getObjectId());
				setMemo(ForumsBBSManager.getInstance().getForumByName("MemoRoot").getChildByName(_accountName));
			}
		}
		
		return _forumMemo;
	}
	
	/**
	 * @param forum
	 */
	public void setMemo(Forum forum)
	{
		_forumMemo = forum;
	}
	
	/**
	 * Restores sub-class data for the L2Player, used to check the current
	 * class index for the character.
	 */
	private static boolean restoreSubClassData(L2Player player)
	{
		try
		{
			for (SubclassRow row : PlayerRepository.getInstance().loadSubclasses(player.getObjectId()))
			{
				SubClass subClass = new SubClass();
				subClass.setClassId(row.classId());
				subClass.setLevel((byte)row.level());
				subClass.setExp(row.exp());
				subClass.setSp(row.sp());
				subClass.setClassIndex(row.classIndex());
				
				// Enforce the correct indexing of _subClasses against their class indexes.
				player.getSubClasses().put(subClass.getClassIndex(), subClass);
			}
		}
		catch (Exception e)
		{
			_log.error("Could not restore classes for " + player.getName() + ": ", e);
		}
		
		return true;
	}
	
	/**
	 * Restores secondary data for the L2Player, based on the current class index.
	 */
	private void restoreCharData()
	{
		// Retrieve from the database all skills of this L2Player and add them to _skills.
		restoreSkills();
		
		// Retrieve from the database all macroses of this L2Player and add them to macroses.
		getMacroses().restore();
		
		// Retrieve from the database all shortCuts of this L2Player and add them to shortCuts.
		getShortCuts().restore();
		
		// Retrieve from the database all henna of this L2Player and add them to _henna.
		restoreHenna();
		
		// Retrieve from the database all teleport bookmark of this L2Player and add them to _tpbookmark.
		restoreTeleportBookmark();
		
		// Retrieve from the database all recom data of this L2Player and add to _recomChars.
		RecommendationManager.getInstance().onJoin(this);
		
		// Retrieve from the database the recipe book of this L2Player.
		restoreRecipeBook(true);
		
		restoreCreationDate();
		
		restoreNameTitleColors();
	}
	
	/**
	 * Restore recipe book data for this L2Player.
	 */
	private void restoreRecipeBook(boolean loadCommon)
	{
		try
		{
			_dwarvenRecipeBook.clear();
			
			if (loadCommon)
			{
				for (RecipeRow row : PlayerRepository.getInstance().loadRecipes(getObjectId()))
				{
					L2RecipeList recipe = RecipeTable.getInstance().getRecipeList(row.recipeId());
					
					if (row.dwarven())
					{
						if (row.classIndex() == _classIndex)
							registerDwarvenRecipeList(recipe, false);
					}
					else
						registerCommonRecipeList(recipe, false);
				}
			}
			else
			{
				for (int recipeId : PlayerRepository.getInstance().loadDwarvenRecipes(getObjectId(), _classIndex))
					registerDwarvenRecipeList(RecipeTable.getInstance().getRecipeList(recipeId), false);
			}
		}
		catch (Exception e)
		{
			_log.error("Could not restore recipe book data:", e);
		}
	}
	
	/** player coords from client */
	private int _clientX;
	private int _clientY;
	private int _clientZ;
	private int _clientHeading;
	
	@Override
	public int getClientX()
	{
		return _clientX;
	}
	
	@Override
	public int getClientY()
	{
		return _clientY;
	}
	
	@Override
	public int getClientZ()
	{
		return _clientZ;
	}
	
	@Override
	public int getClientHeading()
	{
		return _clientHeading;
	}
	
	public void setClientX(int val)
	{
		_clientX = val;
	}
	
	public void setClientY(int val)
	{
		_clientY = val;
	}
	
	public void setClientZ(int val)
	{
		_clientZ = val;
	}
	
	public void setClientHeading(int val)
	{
		_clientHeading = val;
	}
	
	/**
	 * Update L2Player stats in the characters table of the database.<BR><BR>
	 */
	private long _lastStore;
	
	public void store()
	{
		store(false, true);
	}
	
	public void store(boolean storeActiveEffects)
	{
		store(false, storeActiveEffects);
	}
	
	/**
	 * @return false if the save was rolled back and nothing of it reached the database
	 */
	public synchronized boolean store(boolean items, boolean storeActiveEffects)
	{
		_lastStore = System.currentTimeMillis();
		
		if (getOnlineState() == ONLINE_STATE_DELETED)
			return true;
		
		// Update client coords, if these look like true
		// if (isInsideRadius(getClientX(), getClientY(), 1000, true))
		//	getPosition().setXYZ(getClientX(), getClientY(), getClientZ());
		
		// The player row, the pet, the effects, and the items reach the database together or not at all.
		// The control item is written before the pet row, which references it.
		return WorldTransaction.run("Saving player " + getName(), () -> {
			if (Config.UPDATE_ITEMS_ON_CHAR_STORE || items)
				getInventory().updateDatabase();
			L2Summon pet = getPet();
			if (pet != null && pet.getInventory() != null)
				pet.getInventory().updateDatabase();
			storePet();
			storePlayerData();
			getEffects().storeEffects(storeActiveEffects);
		});
	}
	
	/**
	 * Saves the player row, the subclasses, the skill cooldowns, the transformation, and the name and title colors.
	 * Meant to run inside the transaction of {@link #store(boolean, boolean)}.
	 */
	private void storePlayerData()
	{
		PlayerSave save = new PlayerSave();
		save.player = buildPlayerRow();
		
		for (SubClass subClass : getSubClasses().values())
		{
			save.subclasses.add(new SubclassRow(subClass.getClassIndex(), subClass.getClassId(), subClass.getLevel(),
					subClass.getExp(), subClass.getSp()));
		}
		
		for (TimeStamp t : getReuseTimeStamps().values())
		{
			if (t.getRemaining() > 10000) // store only over 10s
				save.skillReuses.add(new SkillReuseRow(t.getSkillId(), Math.max(0, t.getReuseDelay()), t.getExpiration()));
		}
		
		if (getTransformationId() != L2Transformation.TRANSFORM_AKAMANAH
				&& getTransformationId() != L2Transformation.TRANSFORM_ZARICHE)
		{
			_transformationId = getTransformationId();
			save.transformationId = _transformationId;
		}
		
		save.colors =
				new ColorsRow(HexUtil.fillHex(Util.reverseRGBChanels(getAppearance().getNameColor()), 6), HexUtil.fillHex(
						Util.reverseRGBChanels(getAppearance().getTitleColor()), 6));
		
		PlayerRepository.getInstance().savePlayer(save);
	}
	
	private void storePet()
	{
		L2Summon pet = getPet();
		if (pet != null)
			pet.store();
	}
	
	/**
	 * Return True if the L2Player is on line.<BR><BR>
	 */
	public int isOnline()
	{
		return (getOnlineState() == ONLINE_STATE_ONLINE) ? 1 : 0;
	}
	
	public byte getOnlineState()
	{
		return _isOnline;
	}
	
	public boolean isIn7sDungeon()
	{
		return _isIn7sDungeon;
	}
	
	/**
	 * Add a skill to the L2Player _skills and its Func objects to the calculator set of the L2Player and save update in the character_skills table of the database.<BR><BR>
	 *
	 * <B><U> Concept</U> :</B><BR><BR>
	 * All skills own by a L2Player are identified in <B>_skills</B><BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Replace oldSkill by newSkill or Add the newSkill </li>
	 * <li>If an old skill has been replaced, remove all its Func objects of L2Creature calculator set</li>
	 * <li>Add Func objects of newSkill to the calculator set of the L2Creature </li><BR><BR>
	 *
	 * @param newSkill The L2Skill to add to the L2Creature
	 *
	 * @return The L2Skill replaced or null if just added a new L2Skill
	 *
	 */
	public L2Skill addSkill(L2Skill newSkill, boolean save)
	{
		// Add a skill to the L2Player _skills and its Func objects to the calculator set of the L2Player
		final L2Skill oldSkill = addSkill(newSkill);
		
		// Add or update a L2Player skill in the character_skills table of the database
		if (save)
			_pcSkills.storeSkill(newSkill, getClassIndex());
		
		return oldSkill;
	}
	
	@Override
	protected void skillChanged(L2Skill removed, L2Skill added)
	{
		super.skillChanged(removed, added);
		
		if (!L2System.equals(removed, added))
			sendSkillList();
	}
	
	public L2Skill removeSkill(L2Skill skill, boolean store)
	{
		return store ? removeSkill(skill) : super.removeSkill(skill);
	}
	
	/**
	 * Remove a skill from the L2Creature and its Func objects from calculator set of the L2Creature and save update in the character_skills table of the database.<BR><BR>
	 *
	 * <B><U> Concept</U> :</B><BR><BR>
	 * All skills own by a L2Creature are identified in <B>_skills</B><BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Remove the skill from the L2Creature _skills </li>
	 * <li>Remove all its Func objects from the L2Creature calculator set</li><BR><BR>
	 *
	 * <B><U> Overridden in </U> :</B><BR><BR>
	 * <li> L2Player : Save update in the character_skills table of the database</li><BR><BR>
	 *
	 * @param skill The L2Skill to remove from the L2Creature
	 *
	 * @return The L2Skill removed
	 *
	 */
	@Override
	public L2Skill removeSkill(L2Skill skill)
	{
		if (skill == null)
			return null;
		
		// Remove a skill from the L2Creature and its Func objects from calculator set of the L2Creature
		L2Skill oldSkill = super.removeSkill(skill);
		
		_pcSkills.deleteSkill(skill);
		
		if (transformId() > 0 || isCursedWeaponEquipped())
			return oldSkill;
		
		for (L2ShortCut sc : getAllShortCuts())
		{
			if (sc != null && sc.getId() == skill.getId() && sc.getType() == L2ShortCut.TYPE_SKILL
					&& !skill.isItemSkill())
			{
				deleteShortCut(sc.getSlot(), sc.getPage());
			}
		}
		
		return oldSkill;
	}
	
	/**
	 * check player skills and remove unlegit ones (excludes hero, noblesse and cursed weapon skills)
	 */
	public void checkAllowedSkills()
	{
		if (isGM() || !Config.CHECK_SKILLS_ON_ENTER || Config.ALT_GAME_SKILL_LEARN)
			return;
		
		Set<Integer> skillTreeUIDs = SkillTreeTable.getInstance().getAllowedSkillUIDs(getClassId());
		
		skill_loop: for (L2Skill skill : getAllSkills())
		{
			int skillid = skill.getId();
			
			if (isStoredSkill(skill, skillTreeUIDs))
				continue;
			
			if (isTemporarySkill(skill))
				continue;
			
			// Exclude Skills from AllowedSkills in options.properties
			if (Config.ALLOWED_SKILLS_LIST.contains(skillid))
				continue skill_loop;
			// Exclude VIP character
			if (isCharViP() && Config.CHAR_VIP_SKIP_SKILLS_CHECK)
				continue skill_loop;
			
			// Remove skill from ingame, but not from the database to avoid accidentally removal of skills
			// if something failed loading and do a lil log message
			removeSkill(skill, false);
			sendMessage("Skill " + skill.getName() + " removed and GM informed!");
			_log.error("Cheater?! " + skill + " removed from " + getName() + " (" + getAccountName() + ")");
		}
	}
	
	public boolean isStoredSkill(L2Skill skill)
	{
		return isStoredSkill(skill, SkillTreeTable.getInstance().getAllowedSkillUIDs(getClassId()));
	}
	
	private boolean isStoredSkill(L2Skill skill, Set<Integer> skillTreeUIDs)
	{
		int skillid = skill.getId();
		
		// Loop through all skills in players skilltree
		if (skillTreeUIDs.contains(SkillTable.getSkillUID(skillid, SkillTable.getInstance().getNormalLevel(skill))))
			return true;
		
		// skills learned by L2SkillType.LEARN_SKILL
		if (SkillTable.getInstance().isLearnedSkill(skill))
			return true;
		
		// Exclude fishing skills and common skills + dwarfen craft
		if (skillid >= 1312 && skillid <= 1322)
			return true;
		if (skillid >= 1368 && skillid <= 1373)
			return true;
		
		if (L2CertificationSkillsLearn.isCertificationSkill(skillid))
			return true;
		if (L2TransformSkillLearn.isTransformSkill(skillid))
			return true;
		if (L2SkillLearn.isSpecialSkill(skillid))
			return true;
		
		return false;
	}
	
	public boolean isTemporarySkill(L2Skill skill)
	{
		int skillid = skill.getId();
		
		if (getTransformation() != null && containsAllowedTransformSkill(skillid))
			return true;
		// Exclude noble skills
		if (isNoble() && NobleSkillTable.isNobleSkill(skillid))
			return true;
		// Exclude hero skills
		if (isHero() && HeroSkillTable.isHeroSkill(skillid))
			return true;
		// Exclude cursed weapon skills
		if (isCursedWeaponEquipped()
				&& skillid == CursedWeaponsManager.getInstance().getCursedWeapon(_cursedWeaponEquippedId).getSkillId())
			return true;
		// Exclude clan skills
		if (getClan() != null && (skillid >= 370 && skillid <= 391))
			return true;
		// Exclude residential skills
		if (getClan() != null && (getClan().getHasCastle() > 0 || getClan().getHasFort() > 0))
			if (590 <= skillid && skillid <= 610)
				return true;
		// Exclude seal of ruler / build siege hq
		if (getClan() != null && getClan().getLeaderId() == getObjectId() && (skillid == 246 || skillid == 247))
			return true;
		// Exclude sa / enchant bonus / penality etc. skills
		if (skillid >= 3000 && skillid < 7000)
			return true;
		// Exclude Armor Set skills
		if (skillid >= 8100 && skillid < 8400)
			return true;
		
		return false;
	}
	
	/**
	 * Retrieve from the database all skills of this L2Player and add them to _skills.<BR><BR>
	 */
	private void restoreSkills()
	{
		_pcSkills.restoreSkills();
		
		// Restore clan skills
		if (_clan != null)
			_clan.addSkillEffects(this, false);
	}
	
	private void restoreSkillReuses()
	{
		try
		{
			for (SkillReuseRow row : PlayerRepository.getInstance().loadSkillReuses(getObjectId()))
			{
				final int skillId = row.skillId();
				final int reuseDelay = row.reuseDelayMillis();
				final long expiration = row.expiresAt();
				
				final int remaining = L2Math.limit(0, expiration - System.currentTimeMillis(), Integer.MAX_VALUE);
				
				disableSkill(skillId, reuseDelay, remaining);
			}
		}
		catch (Exception e)
		{
			_log.error("", e);
		}
	}
	
	/**
	 * Retrieve from the database all Henna of this L2Player, add them to _henna and calculate stats of the L2Player.<BR><BR>
	 */
	private void restoreHenna()
	{
		try
		{
			List<HennaRow> rows = PlayerRepository.getInstance().loadHennas(getObjectId(), getClassIndex());
			
			for (int i = 0; i < 3; i++)
				_henna[i] = null;
			
			for (HennaRow row : rows)
			{
				int slot = row.slot();
				
				if (slot < 1 || slot > 3)
					continue;
				
				_henna[slot - 1] = HennaTable.getInstance().getTemplate(row.hennaId());
			}
		}
		catch (Exception e)
		{
			_log.error("Failed restoing character hennas.", e);
		}
		
		// Calculate Henna modifiers of this L2Player
		recalcHennaStats();
	}
	
	/**
	 * Return the number of Henna empty slot of the L2Player.<BR><BR>
	 */
	public int getHennaEmptySlots()
	{
		int totalSlots;
		if (getClassId().level() == 1)
			totalSlots = 2;
		else
			totalSlots = 3;
		
		for (int i = 0; i < 3; i++)
			if (_henna[i] != null)
				totalSlots--;
		
		if (totalSlots <= 0)
			return 0;
		
		return totalSlots;
	}
	
	/**
	 * Remove a Henna of the L2Player, save update in the character_hennas table of the database and send Server->Client HennaInfo/UserInfo packet to this L2Player.<BR><BR>
	 */
	public boolean removeHenna(int slot)
	{
		if (slot < 1 || slot > 3)
			return false;
		
		slot--;
		
		if (_henna[slot] == null)
			return false;
		
		L2Henna henna = _henna[slot];
		_henna[slot] = null;
		
		try
		{
			PlayerRepository.getInstance().deleteHenna(getObjectId(), getClassIndex(), slot + 1);
		}
		catch (Exception e)
		{
			_log.error("Failed removing character henna.", e);
		}
		
		// Calculate Henna modifiers of this L2Player
		recalcHennaStats();
		
		// Send Server->Client HennaInfo packet to this L2Player
		sendPacket(new HennaInfo(this));
		
		// Send Server->Client UserInfo packet to this L2Player
		sendPacket(new UserInfo(this));
		
		// Add the recovered dyes to the player's inventory and notify them.
		L2ItemInstance dye = getInventory().addItem("Henna", henna.getItemId(), henna.getAmount() / 2, this, null);
		getInventory().updateInventory(dye);
		
		reduceAdena("Henna", henna.getPrice() / 5, this, false);
		
		SystemMessage sm = new SystemMessage(SystemMessageId.EARNED_S2_S1_S);
		sm.addItemName(henna.getItemId());
		sm.addItemNumber(henna.getAmount() / 2);
		sendPacket(sm);
		
		sendPacket(SystemMessageId.SYMBOL_DELETED);
		
		return true;
	}
	
	/**
	 * Add a Henna to the L2Player, save update in the character_hennas table of the database and send Server->Client HennaInfo/UserInfo packet to this L2Player.<BR><BR>
	 * <B>Does not do <U>any</U> validation!</B>
	 */
	public void addHenna(L2Henna henna)
	{
		for (int i = 0; i < 3; i++)
		{
			if (_henna[i] == null)
			{
				_henna[i] = henna;
				
				// Calculate Henna modifiers of this L2Player
				recalcHennaStats();
				
				try
				{
					PlayerRepository.getInstance().saveHenna(getObjectId(), getClassIndex(), i + 1, henna.getSymbolId());
				}
				catch (Exception e)
				{
					_log.error("Failed saving character henna.", e);
				}
				
				// Send Server->Client HennaInfo packet to this L2Player
				sendPacket(new HennaInfo(this));
				// Send Server->Client UserInfo packet to this L2Player
				sendPacket(new UserInfo(this));
				return;
			}
		}
	}
	
	/**
	 * Calculate Henna modifiers of this L2Player.<BR><BR>
	 */
	private void recalcHennaStats()
	{
		_hennaINT = 0;
		_hennaSTR = 0;
		_hennaCON = 0;
		_hennaMEN = 0;
		_hennaWIT = 0;
		_hennaDEX = 0;
		
		for (int i = 0; i < 3; i++)
		{
			if (_henna[i] == null)
				continue;
			_hennaINT += _henna[i].getStatINT();
			_hennaSTR += _henna[i].getStatSTR();
			_hennaMEN += _henna[i].getStatMEM();
			_hennaCON += _henna[i].getStatCON();
			_hennaWIT += _henna[i].getStatWIT();
			_hennaDEX += _henna[i].getStatDEX();
		}
		
		if (_hennaINT > 5)
			_hennaINT = 5;
		if (_hennaSTR > 5)
			_hennaSTR = 5;
		if (_hennaMEN > 5)
			_hennaMEN = 5;
		if (_hennaCON > 5)
			_hennaCON = 5;
		if (_hennaWIT > 5)
			_hennaWIT = 5;
		if (_hennaDEX > 5)
			_hennaDEX = 5;
	}
	
	/**
	 * Return the Henna of this L2Player corresponding to the selected slot.<BR><BR>
	 */
	public L2Henna getHenna(int slot)
	{
		if (slot < 1 || slot > 3)
			return null;
		
		return _henna[slot - 1];
	}
	
	/**
	 * Return the INT Henna modifier of this L2Player.<BR><BR>
	 */
	public int getHennaStatINT()
	{
		return _hennaINT;
	}
	
	/**
	 * Return the STR Henna modifier of this L2Player.<BR><BR>
	 */
	public int getHennaStatSTR()
	{
		return _hennaSTR;
	}
	
	/**
	 * Return the CON Henna modifier of this L2Player.<BR><BR>
	 */
	public int getHennaStatCON()
	{
		return _hennaCON;
	}
	
	/**
	 * Return the MEN Henna modifier of this L2Player.<BR><BR>
	 */
	public int getHennaStatMEN()
	{
		return _hennaMEN;
	}
	
	/**
	 * Return the WIT Henna modifier of this L2Player.<BR><BR>
	 */
	public int getHennaStatWIT()
	{
		return _hennaWIT;
	}
	
	/**
	 * Return the DEX Henna modifier of this L2Player.<BR><BR>
	 */
	public int getHennaStatDEX()
	{
		return _hennaDEX;
	}
	
	/**
	 * Return True if the L2Player is autoAttackable.<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Check if the attacker isn't the L2Player Pet </li>
	 * <li>Check if the attacker is L2MonsterInstance</li>
	 * <li>If the attacker is a L2Player, check if it is not in the same party </li>
	 * <li>Check if the L2Player has Karma </li>
	 * <li>If the attacker is a L2Player, check if it is not in the same siege clan (Attacker, Defender) </li><BR><BR>
	 *
	 */
	@Override
	public boolean isAutoAttackable(L2Creature attacker)
	{
		// Check if the attacker isn't the L2Player Pet
		if (attacker == this || attacker == getPet())
			return false;
		
		// Check if the attacker is a L2MonsterInstance
		if (attacker instanceof L2MonsterInstance)
			return true;
		
		switch (GlobalRestrictions.getCombatState(L2Object.getActingPlayer(attacker), this))
		{
			case ENEMY:
				return true;
			case FRIEND:
				return false;
		}
		
		// Check if the attacker is not in the same party
		if (getParty() != null && getParty().getPartyMembers().contains(attacker))
			return false;
		
		if (isCursedWeaponEquipped())
			return true;
		
		// Check if the attacker is in olympia and olympia start
		if (attacker instanceof L2Player && ((L2Player)attacker).isInOlympiadMode())
		{
			return isInOlympiadMode() && isOlympiadStart()
					&& ((L2Player)attacker).getOlympiadGameId() == getOlympiadGameId();
		}
		
		// Check if the attacker is not in the same clan
		if (getClan() != null && attacker != null && getClan().isMember(attacker.getObjectId()))
			return false;
		
		if (attacker instanceof L2Playable && isInsideZone(L2Zone.FLAG_PEACE))
			return false;
		
		// Check if the L2Player has Karma
		if (getKarma() > 0 || getPvpFlag() > 0)
			return true;
		
		// Check if the attacker is a L2Playable
		if (attacker instanceof L2Playable)
		{
			// Is AutoAttackable if both playables are in the same duel and the duel is still going on
			if (getDuelState() == Duel.DUELSTATE_DUELLING && getDuelId() == attacker.getActingPlayer().getDuelId())
				return true;
		}
		
		// Check if the attacker is a L2Player
		if (attacker instanceof L2Player)
		{
			// Check if the L2Player is in an arena or a siege area
			if (isInsideZone(L2Zone.FLAG_PVP) && attacker.isInsideZone(L2Zone.FLAG_PVP))
				return true;
			
			// Check if the L2Player holds a cursed weapon
			if (((L2Player)attacker).isCursedWeaponEquipped())
				return true;
			
			if (getClan() != null)
			{
				Siege siege = SiegeManager.getInstance().getSiege(getX(), getY(), getZ());
				if (siege != null)
				{
					// Check if a siege is in progress and if attacker and the L2Player aren't in the Defender clan
					if (siege.checkIsDefender(((L2Player)attacker).getClan()) && siege.checkIsDefender(getClan()))
						return false;
					
					// Check if a siege is in progress and if attacker and the L2Player aren't in the Attacker clan
					if (siege.checkIsAttacker(((L2Player)attacker).getClan()) && siege.checkIsAttacker(getClan()))
						return false;
				}
				
				// Check if clan is at war
				if (getClan() != null
						&& ((L2Player)attacker).getClan() != null
						&& (getClan().isAtWarWith(((L2Player)attacker).getClanId())
								&& ((L2Player)attacker).getClan().isAtWarWith(getClanId()) && getWantsPeace() == 0
								&& ((L2Player)attacker).getWantsPeace() == 0 && !isAcademyMember()))
					return true;
			}
		}
		else if (attacker instanceof L2SiegeGuardInstance)
		{
			if (getClan() != null)
			{
				Siege siege = SiegeManager.getInstance().getSiege(this);
				return (siege != null && siege.checkIsAttacker(getClan()));
			}
		}
		else if (attacker instanceof L2FortSiegeGuardInstance)
		{
			if (getClan() != null)
			{
				FortSiege siege = FortSiegeManager.getInstance().getSiege(this);
				return (siege != null && siege.checkIsAttacker(getClan()));
			}
		}
		
		return false;
	}
	
	/**
	 * Checks if the client was allowed to call that skill at all, or not.
	 */
	public boolean canUseMagic(L2Skill skill)
	{
		if (skill == null || skill.getSkillType() == L2SkillType.NOTDONE)
			return false;
		
		// players mounted on pets cannot use any toggle skills
		if (skill.isToggle() && isMounted())
			return false;
		
		// Check if the skill is active
		if (skill.isPassive())
			return false;
		
		if (isTransformationDisabledSkill(skill) && !skill.isPotion())
			return false;
		
		// Failfast as in retail
		if (isSkillDisabled(skill.getId()))
		{
			sendReuseMessage(skill);
			return false;
		}
		
		return true;
	}
	
	private boolean isTransformationDisabledSkill(L2Skill skill)
	{
		if (_transformation != null && !containsAllowedTransformSkill(skill.getId()) && !skill.allowOnTransform())
			return true;
		
		return false;
	}
	
	@Override
	public void doCast(L2Skill skill)
	{
		if (!canUseMagic(skill))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		super.doCast(skill);
	}
	
	@Override
	public void doSimultaneousCast(L2Skill skill)
	{
		if (!canUseMagic(skill))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		super.doSimultaneousCast(skill);
	}
	
	public void sendReuseMessage(L2Skill skill)
	{
		SystemMessage sm = null;
		
		TimeStamp timeStamp = getReuseTimeStamps().get(skill.getId());
		int remainingTime = (timeStamp == null ? 0 : timeStamp.getRemaining() / 1000);
		int hours = remainingTime / 3600;
		int minutes = (remainingTime % 3600) / 60;
		int seconds = (remainingTime % 60);
		if (hours > 0)
		{
			sm = new SystemMessage(SystemMessageId.S2_HOURS_S3_MINUTES_S4_SECONDS_REMAINING_FOR_REUSE_S1);
			sm.addSkillName(skill);
			sm.addNumber(hours);
			sm.addNumber(minutes);
			sm.addNumber(seconds);
		}
		else if (minutes > 0)
		{
			sm = new SystemMessage(SystemMessageId.S2_MINUTES_S3_SECONDS_REMAINING_FOR_REUSE_S1);
			sm.addSkillName(skill);
			sm.addNumber(minutes);
			sm.addNumber(seconds);
		}
		else if (seconds > 0)
		{
			sm = new SystemMessage(SystemMessageId.S2_SECONDS_REMAINING_FOR_REUSE_S1);
			sm.addSkillName(skill);
			sm.addNumber(seconds);
		}
		else
		{
			sm = new SystemMessage(SystemMessageId.S1_PREPARED_FOR_REUSE);
			sm.addSkillName(skill);
		}
		sendPacket(sm);
	}
	
	@Override
	protected boolean checkUseMagicConditions(L2Skill skill, boolean forceUse)
	{
		L2SkillType sklType = skill.getSkillType();
		
		//************************************* Check Player State *******************************************
		
		// Abnormal effects(ex : Stun, Sleep...) are checked in L2Creature useMagic()
		
		if (!SkillHandler.getInstance().checkConditions(this, skill))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (isOutOfControl())
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the player is dead
		if (isDead())
		{
			abortCast();
			// Send a Server->Client packet ActionFailed to the L2Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		if (isFishing()
				&& (sklType != L2SkillType.PUMPING && sklType != L2SkillType.REELING && sklType != L2SkillType.FISHING))
		{
			// Only fishing skills are available
			sendPacket(SystemMessageId.ONLY_FISHING_SKILLS_NOW);
			return false;
		}
		
		if (inObserverMode())
		{
			sendPacket(SystemMessageId.OBSERVERS_CANNOT_PARTICIPATE);
			abortCast();
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the caster is sitting
		if (isSitting() && !skill.isPotion())
		{
			// Send a System Message to the caster
			sendPacket(SystemMessageId.CANT_MOVE_SITTING);
			
			// Send a Server->Client packet ActionFailed to the L2Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if the skill type is TOGGLE
		if (skill.isToggle())
		{
			// Get effects of the skill
			L2Effect effect = getFirstEffect(skill);
			
			if (effect != null)
			{
				effect.exit();
				
				// Send a Server->Client packet ActionFailed to the L2Player
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
		}
		
		// Check if the player uses "Fake Death" skill
		// Note: do not check this before TOGGLE reset
		if (isFakeDeath())
		{
			// Send a Server->Client packet ActionFailed to the L2Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if it's ok to summon
		// Siege Golem (13), Wild Hog Cannon (299), Swoop Cannon (448)
		switch (skill.getId())
		{
			case 13:
			case 299:
			case 448:
				if ((!SiegeManager.getInstance().checkIfOkToSummon(this, false) && !FortSiegeManager.getInstance()
						.checkIfOkToSummon(this, false)) || SevenSigns.getInstance().checkSummonConditions(this))
				{
					return false;
				}
		}
		
		//************************************* Check Target *******************************************
		
		// Create and set a L2Object containing the target of the skill
		
		SkillTargetType sklTargetType = skill.getTargetType();
		Point3D worldPosition = getCurrentSkillWorldPosition();
		
		if (sklTargetType == SkillTargetType.TARGET_GROUND && worldPosition == null)
		{
			_log.info("WorldPosition is null for skill: " + skill.getName() + ", player: " + getName() + ".");
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		final L2Creature target = skill.getFirstOfTargetList(this);
		
		// Check the validity of the target
		if (target == null)
		{
			//sendPacket(SystemMessageId.TARGET_CANT_FOUND);
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Skills can be used on walls and doors only during siege
		if (target instanceof L2DoorInstance)
		{
			L2DoorInstance door = (L2DoorInstance)target;
			boolean isCastleDoor = (door.getCastle() != null && door.getCastle().getSiege().getIsInProgress());
			boolean isFortDoor =
					(door.getFort() != null && door.getFort().getSiege().getIsInProgress() && !door.isCommanderDoor());
			if (!isCastleDoor && !isFortDoor && (door.isUnlockable() && skill.getSkillType() != L2SkillType.UNLOCK))
				return false;
		}
		
		// Are the target and the player in the same duel?
		if (isInDuel())
		{
			if (!(target instanceof L2Playable && target.getActingPlayer().getDuelId() == getDuelId()))
			{
				sendMessage("You cannot do this while duelling.");
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
		}
		
		//************************************* Check skill availability *******************************************
		
		// Check if this skill is enabled (ex : reuse time)
		if (isSkillDisabled(skill.getId()))
		{
			sendReuseMessage(skill);
			return false;
		}
		
		//************************************* Check Consumables *******************************************
		
		if (skill.getTransformId() > 0)
		{
			if (getPet() != null)
				getPet().unSummon(this); // Unsummon pets
				
			if (getEffects().hasEffect(L2EffectType.TRANSFORMATION) || getPet() != null || isMounted() || isFlying())
			{
				sendPacket(new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED).addSkillName(skill));
				return false;
			}
		}
		
		//************************************* Check Consumables *******************************************
		
		// Check if spell consumes a Soul
		// Most kamael skills have only optional soul consumption to empower skills!
		if (getSouls() < skill.getSoulConsumeCount())
		{
			sendPacket(SystemMessageId.THERE_IS_NOT_ENOUGH_SOUL);
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if spell consumes charges
		if (_charges < skill.getNeededCharges())
		{
			SystemMessage sm = new SystemMessage(SystemMessageId.S1_CANNOT_BE_USED);
			sm.addSkillName(skill);
			sendPacket(sm);
			return false;
		}
		
		// Check if spell adds charges
		if (!skill.isOffensive() && skill.getGiveCharges() > 0 && _charges >= skill.getMaxCharges())
		{
			sendPacket(SystemMessageId.FORCE_MAXLEVEL_REACHED);
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		//************************************* Check Casting Conditions *******************************************
		
		// Check if all casting conditions are completed
		if (!skill.checkCondition(this, target))
		{
			// Send a Server->Client packet ActionFailed to the L2Player
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		//************************************* Check Skill Type *******************************************
		
		// Check if this is offensive magic skill
		if (skill.isOffensive())
		{
			if (L2Creature.isInsidePeaceZone(this, target))
			{
				// If L2Creature or target is in a peace zone, send a system message TARGET_IN_PEACEZONE a Server->Client packet ActionFailed
				sendPacket(SystemMessageId.TARGET_IN_PEACEZONE);
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			if (isInOlympiadMode() && !isOlympiadStart())
			{
				// If the L2Player is in Olympia and the match isn't already start, send a Server->Client packet ActionFailed
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			// Check if the target is attackable
			if (!target.isAttackable() && (getAccessLevel() < Config.GM_PEACEATTACK))
			{
				// If target is not attackable, send a Server->Client packet ActionFailed
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
			
			// Check if a Forced ATTACK is in progress on non-attackable target
			if (!target.isAutoAttackable(this) && !forceUse)
			{
				switch (sklTargetType)
				{
					case TARGET_AURA:
					case TARGET_FRONT_AURA:
					case TARGET_BEHIND_AURA:
					case TARGET_SERVITOR_AURA:
					case TARGET_CLAN:
					case TARGET_PARTY_CLAN:
					case TARGET_ALLY:
					case TARGET_PARTY:
					case TARGET_SELF:
					case TARGET_GROUND:
						// Everything okay
						break;
					default:
						// Send a Server->Client packet ActionFailed to the L2Player
						sendPacket(ActionFailed.STATIC_PACKET);
						return false;
				}
			}
		}
		
		// Check if the skill is defensive
		if (skill.isPositive() && target instanceof L2MonsterInstance && !forceUse)
		{
			switch (sklTargetType)
			{
				case TARGET_PET:
				case TARGET_SUMMON:
				case TARGET_AURA:
				case TARGET_FRONT_AURA:
				case TARGET_BEHIND_AURA:
				case TARGET_SERVITOR_AURA:
				case TARGET_CLAN:
				case TARGET_PARTY_CLAN:
				case TARGET_SELF:
				case TARGET_PARTY:
				case TARGET_ALLY:
				case TARGET_CORPSE_MOB:
				case TARGET_AREA_CORPSE_MOB:
				case TARGET_GROUND:
					// Everything okay
					break;
				default:
					switch (sklType)
					{
						case BEAST_FEED:
						case DELUXE_KEY_UNLOCK:
						case UNLOCK:
						case MAKE_KILLABLE:
							// Everything okay
							break;
						default:
							// send the action failed so that the skill doens't go off.
							sendPacket(ActionFailed.STATIC_PACKET);
							return false;
					}
			}
		}
		
		if (!SkillHandler.getInstance().checkConditions(this, skill, L2Object.getActingCharacter(target)))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return false;
		}
		
		// Check if this is a Pvp skill and target isn't a non-flagged/non-karma player
		switch (sklTargetType)
		{
			case TARGET_PARTY:
			case TARGET_ALLY: // For such skills, checkPvpSkill() is called from L2Skill.getTargetList()
			case TARGET_CLAN: // For such skills, checkPvpSkill() is called from L2Skill.getTargetList()
			case TARGET_PARTY_CLAN:// For such skills, checkPvpSkill() is called from L2Skill.getTargetList()
			case TARGET_AURA:
			case TARGET_FRONT_AURA:
			case TARGET_BEHIND_AURA:
			case TARGET_SERVITOR_AURA:
			case TARGET_GROUND:
			case TARGET_SELF:
				break;
			default:
				if (!checkPvpSkill(target, skill) && (getAccessLevel() < Config.GM_PEACEATTACK))
				{
					// Send a System Message to the L2Player
					sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
					
					// Send a Server->Client packet ActionFailed to the L2Player
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
		}
		
		if ((sklTargetType == SkillTargetType.TARGET_HOLY && (!TakeCastle.checkIfOkToCastSealOfRule(this)))
				|| (sklTargetType == SkillTargetType.TARGET_FLAGPOLE && !TakeFort.checkIfOkToCastFlagDisplay(this,
						false, skill, getTarget())))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			abortCast();
			return false;
		}
		
		// GeoData Los Check here
		if (skill.getCastRange() > 0)
		{
			if (sklTargetType == SkillTargetType.TARGET_GROUND)
			{
				if (!GeoData.getInstance().canSeeTarget(this, worldPosition))
				{
					sendPacket(SystemMessageId.CANT_SEE_TARGET);
					sendPacket(ActionFailed.STATIC_PACKET);
					return false;
				}
			}
			else if (!GeoData.getInstance().canSeeTarget(this, target))
			{
				sendPacket(SystemMessageId.CANT_SEE_TARGET);
				sendPacket(ActionFailed.STATIC_PACKET);
				return false;
			}
		}
		
		// Finally, after passing all conditions
		return true;
	}
	
	public boolean isInLooterParty(int LooterId)
	{
		L2Player looter = (L2Player)L2World.getInstance().findObject(LooterId);
		
		// If L2Player is in a CommandChannel
		if (isInParty() && getParty().isInCommandChannel() && looter != null)
			return getParty().getCommandChannel().getMembers().contains(looter);
		
		if (isInParty() && looter != null)
			return getParty().getPartyMembers().contains(looter);
		
		return false;
	}
	
	/**
	 * Check if the requested casting is a Pc->Pc skill cast and if it's a valid pvp condition
	 * @param obj L2Object instance containing the target
	 * @param skill L2Skill instance with the skill being casted
	 * @return False if the skill is a pvpSkill and target is not a valid pvp target
	 */
	public boolean checkPvpSkill(L2Object obj, L2Skill skill)
	{
		return checkPvpSkill(obj, skill, false);
	}
	
	/**
	 * Check if the requested casting is a Pc->Pc skill cast and if it's a valid pvp condition
	 * @param obj L2Object instance containing the target
	 * @param skill L2Skill instance with the skill being casted
	 * @param srcIsSummon is L2Summon - caster?
	 * @return False if the skill is a pvpSkill and target is not a valid pvp target
	 */
	public boolean checkPvpSkill(L2Object obj, L2Skill skill, boolean srcIsSummon)
	{
		if (obj instanceof L2Creature)
			if (GlobalRestrictions.isProtected(this, (L2Creature)obj, skill, false))
				return false;
		
		// Check for PC->PC Pvp status
		if (obj != this && // Target is not self and
				obj instanceof L2Player && // Target is L2Player and
				!(isInDuel() && ((L2Player)obj).getDuelId() == getDuelId()) && // Self is not in a duel and attacking opponent
				!isInsideZone(L2Zone.FLAG_PVP) && // Pc is not in PvP zone
				!((L2Player)obj).isInsideZone(L2Zone.FLAG_PVP) // Target is not in PvP zone
		)
		{
			L2Player target = (L2Player)obj;
			
			if (skill.isPvpSkill()) // Pvp skill
			{
				if (getClan() != null && target.getClan() != null)
				{
					if (getClan().isAtWarWith(target.getClan().getClanId())
							&& target.getClan().isAtWarWith(getClan().getClanId()))
						return true; // In clan war player can attack whites even with sleep etc.
				}
				if (target.getPvpFlag() == 0 && // Target's pvp flag is not set and
						target.getKarma() == 0 // Target has no karma
				)
					return false;
			}
			else if ((getCurrentSkill() != null && !getCurrentSkill().isCtrlPressed() && skill.isOffensive() && !srcIsSummon)
					|| (getCurrentPetSkill() != null && !getCurrentPetSkill().isCtrlPressed() && skill.isOffensive() && srcIsSummon))
			{
				if (getClan() != null && target.getClan() != null)
				{
					if (getClan().isAtWarWith(target.getClan().getClanId())
							&& target.getClan().isAtWarWith(getClan().getClanId()))
						return true; // In clan war player can attack whites even without ctrl
				}
				if (target.getPvpFlag() == 0 && // Target's pvp flag is not set and
						target.getKarma() == 0 // Target has no karma
				)
					return false;
			}
		}
		return true;
	}
	
	/**
	 * Return True if the L2Player is a Mage.<BR><BR>
	 */
	public boolean isMageClass()
	{
		return getClassId().isMage();
	}
	
	public boolean isMounted()
	{
		return _mountType > 0;
	}
	
	public boolean checkCanLand()
	{
		// Check if char is in a no landing zone
		if (isInsideZone(L2Zone.FLAG_NOWYVERN))
			return false;
		
		// If this is a castle that is currently being sieged, and the rider is NOT a castle owner
		// he cannot land.
		// Castle owner is the leader of the clan that owns the castle where the pc is
		return !(SiegeManager.getInstance().checkIfInZone(this) && !(getClan() != null
				&& CastleManager.getInstance().getCastle(this) == CastleManager.getInstance().getCastleByOwner(
						getClan()) && this == getClan().getLeader().getPlayerInstance()));
	}
	
	/**
	 * Set the type of Pet mounted (0 : none, 1 : Stridder, 2 : Wyvern) and send a Server->Client packet InventoryUpdate to the L2Player.<BR><BR>
	 * @return false if the change of mount type false
	 */
	public boolean setMount(int npcId, int npcLevel, int mountType)
	{
		switch (mountType)
		{
			case 0:
				setIsRidingStrider(false);
				setIsRidingRedStrider(false);
				setIsRidingHorse(false);
				setIsFlying(false);
				isFalling(false, 0); // Initialize the fall just incase dismount was made while in-air
				break; // Dismounted
			case 1:
				if (npcId >= 12526 && npcId <= 12528)
				{
					setIsRidingStrider(true);
				}
				else if (npcId >= 16038 && npcId <= 16040)
				{
					setIsRidingRedStrider(true);
				}
				if (isNoble())
				{
					L2Skill striderAssaultSkill = SkillTable.getInstance().getInfo(325, 1);
					addSkill(striderAssaultSkill); // Not saved to DB
				}
				break;
			case 2:
				setIsFlying(true);
				break; // Flying Wyvern
			case 4:
				setIsRidingHorse(true);
				break;
		}
		
		_mountType = mountType;
		_mountNpcId = npcId;
		_mountLevel = npcLevel;
		
		return true;
	}
	
	/**
	 * @return the type of Pet mounted (0 : none, 1 : Strider, 2 : Wyvern, 3: Wolf).
	 */
	public int getMountType()
	{
		return _mountType;
	}
	
	/**
	 * Disable the Inventory and create a new task to enable it after 1.5s.<BR><BR>
	 */
	public void tempInventoryDisable()
	{
		_inventoryDisabled = true;
		
		ThreadPoolManager.getInstance().scheduleGeneral(new InventoryEnable(), 1500);
	}
	
	/**
	 * Return True if the Inventory is disabled.<BR><BR>
	 */
	public boolean isInventoryDisabled()
	{
		return _inventoryDisabled;
	}
	
	class InventoryEnable implements Runnable
	{
		@Override
		public void run()
		{
			_inventoryDisabled = false;
		}
	}
	
	public Map<Integer, L2CubicInstance> getCubics()
	{
		return _cubics;
	}
	
	/**
	 * Add a L2CubicInstance to the L2Player _cubics.<BR><BR>
	 */
	public void addCubic(int id, int level, double matk, int activationtime, int activationchance, int totalLifeTime)
	{
		if (_log.isDebugEnabled())
			_log.info("L2Player(" + getName() + "): addCubic(" + id + "|" + level + "|" + matk + ")");
		L2CubicInstance cubic =
				new L2CubicInstance(this, id, level, (int)matk, activationtime, activationchance, totalLifeTime);
		_cubics.put(id, cubic);
	}
	
	public void addCubic(L2SkillSummon skill)
	{
		addCubic(skill.getNpcId(), skill.getLevel(), skill.getPower(), skill.getActivationTime(),
				skill.getActivationChance(), skill.getTotalLifeTime());
	}
	
	/**
	 * Remove a L2CubicInstance from the L2Player _cubics.<BR><BR>
	 */
	public void delCubic(int id)
	{
		_cubics.remove(id);
	}
	
	/**
	 * Return the L2CubicInstance corresponding to the Identifier of the L2Player _cubics.<BR><BR>
	 */
	public L2CubicInstance getCubic(int id)
	{
		return _cubics.get(id);
	}
	
	@Override
	public String toString()
	{
		return "player " + getName();
	}
	
	/**
	 * Return the modifier corresponding to the Enchant Effect of the Active Weapon (Min : 127).<BR><BR>
	 */
	public int getEnchantEffect()
	{
		L2ItemInstance wpn = getActiveWeaponInstance();
		
		if (wpn == null)
			return 0;
		
		return Math.min(127, wpn.getEnchantLevel());
	}
	
	/**
	 * Set the _lastFolkNpc of the L2Player corresponding to the last Folk wich one the player talked.<BR><BR>
	 */
	public void setLastFolkNPC(L2Npc folkNpc)
	{
		_lastFolkNpc = folkNpc;
	}
	
	/**
	 * Return the _lastFolkNpc of the L2Player corresponding to the last Folk wich one the player talked.<BR><BR>
	 */
	public L2Npc getLastFolkNPC()
	{
		return _lastFolkNpc;
	}
	
	/**
	 * Return True if L2Player is a participant in the Festival of Darkness.<BR><BR>
	 */
	public boolean isFestivalParticipant()
	{
		return SevenSignsFestival.getInstance().isParticipant(this);
	}
	
	private ScheduledFuture<?> _taskWarnUserTakeBreak;
	
	class WarnUserTakeBreak implements Runnable
	{
		@Override
		public void run()
		{
			sendPacket(SystemMessageId.PLAYING_FOR_LONG_TIME);
		}
	}
	
	class RentPetTask implements Runnable
	{
		@Override
		public void run()
		{
			stopRentPet();
		}
	}
	
	class WaterTask implements Runnable
	{
		@Override
		@SuppressWarnings("deprecation")
		public void run()
		{
			double reduceHp = getMaxHp() / 100.0;
			
			if (reduceHp < 1)
				reduceHp = 1;
			
			reduceCurrentHp(reduceHp, L2Player.this, false);
			// Reduced hp, because not rest
			SystemMessage sm = new SystemMessage(SystemMessageId.DROWN_DAMAGE_S1);
			sm.addNumber((int)reduceHp);
			sendPacket(sm);
			
		}
	}
	
	class LookingForFishTask implements Runnable
	{
		boolean _isNoob, _isUpperGrade;
		int _fishType, _fishGutsCheck, _gutsCheckTime;
		long _endTaskTime;
		
		protected LookingForFishTask(int fishWaitTime, int fishGutsCheck, int fishType, boolean isNoob,
				boolean isUpperGrade)
		{
			_fishGutsCheck = fishGutsCheck;
			_endTaskTime = System.currentTimeMillis() + fishWaitTime + 10000;
			_fishType = fishType;
			_isNoob = isNoob;
			_isUpperGrade = isUpperGrade;
		}
		
		@Override
		public void run()
		{
			if (System.currentTimeMillis() >= _endTaskTime)
			{
				endFishing(false);
				return;
			}
			
			if (!GameTimeManager.getInstance().isNowNight() && _lure.isNightLure())
				return;
			
			int check = Rnd.get(1000);
			if (_fishGutsCheck > check)
			{
				stopLookingForFishTask();
				startFishCombat(_isNoob, _isUpperGrade);
			}
		}
	}
	
	public int getClanPrivileges()
	{
		return _clanPrivileges;
	}
	
	public void setClanPrivileges(int n)
	{
		_clanPrivileges = n;
	}
	
	public int getBookMarkSlot()
	{
		return _bookmarkslot;
	}
	
	public void setBookMarkSlot(int slot)
	{
		_bookmarkslot = slot;
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public void enterObserverMode(int x, int y, int z)
	{
		_obsX = getX();
		_obsY = getY();
		_obsZ = getZ();
		
		setTarget(null);
		stopMove(null);
		startParalyze();
		setIsInvul(true);
		getAppearance().setInvisible();
		sendPacket(GMHide.ENABLE);
		sendPacket(new ObservationMode(x, y, z));
		getPosition().setXYZ(x, y, z);
		
		_observerMode = true;
		
		updateInvisibilityStatus();
	}
	
	public void enterOlympiadObserverMode(Location loc, int id, boolean storeCoords)
	{
		if (getPet() != null)
			getPet().unSummon(this);
		
		if (!getCubics().isEmpty())
		{
			for (L2CubicInstance cubic : getCubics().values())
			{
				cubic.stopAction();
				cubic.cancelDisappear();
			}
			
			getCubics().clear();
		}
		
		if (getParty() != null)
			getParty().removePartyMember(this);
		
		_olympiadGameId = id;
		if (isSitting())
			standUp();
		if (storeCoords)
		{
			_obsX = getX();
			_obsY = getY();
			_obsZ = getZ();
		}
		setTarget(null);
		setIsInvul(true);
		getAppearance().setInvisible();
		teleToLocation(loc, false);
		sendPacket(GMHide.ENABLE);
		sendPacket(ExOlympiadMode.SPECTATE);
		_observerMode = true;
		
		updateInvisibilityStatus();
	}
	
	// [L2J_JP ADD SANDMAN]
	public void enterMovieMode()
	{
		setTarget(null);
		stopMove(null);
		setIsInvul(true);
		setIsImmobilized(true);
		sendPacket(CameraMode.FIRST_PERSON);
	}
	
	public void leaveMovieMode()
	{
		if (!isGM())
			setIsInvul(false);
		setIsImmobilized(false);
		sendPacket(CameraMode.THIRD_PERSON);
	}
	
	/**
	 * yaw:North=90, south=270, east=0, west=180<BR>
	 * pitch > 0:looks up,pitch < 0:looks down<BR>
	 * time:faster that small value is.<BR>
	 */
	public void specialCamera(L2Object target, int dist, int yaw, int pitch, int time, int duration)
	{
		sendPacket(new SpecialCamera(target.getObjectId(), dist, yaw, pitch, time, duration));
	}
	
	// L2JJP END
	
	public void leaveObserverMode()
	{
		setTarget(null);
		getPosition().setXYZ(_obsX, _obsY, _obsZ);
		sendPacket(GMHide.DISABLE);
		stopParalyze(false);
		
		if (!isGM())
		{
			getAppearance().setVisible();
			setIsInvul(false);
		}
		
		if (getAI() != null)
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		
		teleToLocation(_obsX, _obsY, _obsZ);
		sendPacket(new ObservationReturn(this));
		_observerMode = false;
		broadcastUserInfo();
	}
	
	public void leaveOlympiadObserverMode()
	{
		setTarget(null);
		sendPacket(ExOlympiadMode.RETURN);
		teleToLocation(_obsX, _obsY, _obsZ);
		sendPacket(GMHide.DISABLE);
		if (!isGM())
		{
			getAppearance().setVisible();
			setIsInvul(false);
		}
		if (getAI() != null)
		{
			getAI().setIntention(CtrlIntention.AI_INTENTION_IDLE);
		}
		Olympiad.removeSpectator(_olympiadGameId, this);
		_olympiadGameId = -1;
		_observerMode = false;
		broadcastUserInfo();
	}
	
	public void updateNameTitleColor()
	{
		int nameColor = getAppearance().getNameColor();
		int titleColor = getAppearance().getTitleColor();
		
		if (isClanLeader() && Config.CLAN_LEADER_COLOR_ENABLED
				&& getClan().getLevel() >= Config.CLAN_LEADER_COLOR_CLAN_LEVEL)
		{
			if (Config.CLAN_LEADER_COLORED == Config.ClanLeaderColored.name)
				nameColor = Config.CLAN_LEADER_COLOR;
			else
				titleColor = Config.CLAN_LEADER_COLOR;
		}
		
		if (Config.CHAR_VIP_COLOR_ENABLED)
		{
			if (isCharViP())
				nameColor = Config.CHAR_VIP_COLOR;
		}
		
		if (Config.ALLOW_OFFLINE_TRADE_COLOR_NAME)
		{
			if (isInOfflineMode())
				nameColor = Config.OFFLINE_TRADE_COLOR_NAME;
		}
		
		if (isGM())
		{
			if (Config.GM_NAME_COLOR_ENABLED)
			{
				if (getAccessLevel() >= 100)
					nameColor = Config.ADMIN_NAME_COLOR;
				else if (getAccessLevel() >= 75)
					nameColor = Config.GM_NAME_COLOR;
			}
			
			if (Config.GM_TITLE_COLOR_ENABLED)
			{
				if (getAccessLevel() >= 100)
					titleColor = Config.ADMIN_TITLE_COLOR;
				else if (getAccessLevel() >= 75)
					titleColor = Config.GM_TITLE_COLOR;
			}
		}
		
		getAppearance().setTitleColor(titleColor);
		getAppearance().setNameColor(nameColor);
		
		broadcastUserInfo();
	}
	
	public void setOlympiadSide(int i)
	{
		_olympiadSide = i;
	}
	
	public int getOlympiadSide()
	{
		return _olympiadSide;
	}
	
	public void setOlympiadGameId(int id)
	{
		_olympiadGameId = id;
	}
	
	public int getOlympiadGameId()
	{
		return _olympiadGameId;
	}
	
	public int getObsX()
	{
		return _obsX;
	}
	
	public int getObsY()
	{
		return _obsY;
	}
	
	public int getObsZ()
	{
		return _obsZ;
	}
	
	public boolean inObserverMode()
	{
		return _observerMode;
	}
	
	public int getTeleMode()
	{
		return _telemode;
	}
	
	public void setTeleMode(int mode)
	{
		_telemode = mode;
	}
	
	public void setLoto(int i, int val)
	{
		_loto[i] = val;
	}
	
	public int getLoto(int i)
	{
		return _loto[i];
	}
	
	public void setRace(int i, int val)
	{
		_race[i] = val;
	}
	
	public int getRace(int i)
	{
		return _race[i];
	}
	
	public boolean isChatBanned()
	{
		return ObjectRestrictions.getInstance().checkRestriction(this, AvailableRestriction.PlayerChat);
	}
	
	public boolean getMessageRefusal()
	{
		return _messageRefusal;
	}
	
	public void setMessageRefusal(boolean mode)
	{
		_messageRefusal = mode;
		sendEtcStatusUpdate();
	}
	
	public void setDietMode(boolean mode)
	{
		_dietMode = mode;
	}
	
	public boolean getDietMode()
	{
		return _dietMode;
	}
	
	public void setTradeRefusal(boolean mode)
	{
		_tradeRefusal = mode;
	}
	
	public boolean getTradeRefusal()
	{
		return _tradeRefusal;
	}
	
	public void setExchangeRefusal(boolean mode)
	{
		_exchangeRefusal = mode;
	}
	
	public boolean getExchangeRefusal()
	{
		return _exchangeRefusal;
	}
	
	public BlockList getBlockList()
	{
		if (_blockList == null)
			_blockList = new BlockList(this);
		
		return _blockList;
	}
	
	public L2FriendList getFriendList()
	{
		if (_friendList == null)
			_friendList = new L2FriendList(this);
		
		return _friendList;
	}
	
	public void setHero(boolean hero)
	{
		if (hero && _baseClass == _activeClass)
			for (L2Skill s : HeroSkillTable.getHeroSkills())
				addSkill(s, false); // Dont Save Hero skills to Sql
		else
			for (L2Skill s : HeroSkillTable.getHeroSkills())
				super.removeSkill(s); // Just Remove skills without deleting from Sql
		_hero = hero;
	}
	
	public void setIsInOlympiadMode(boolean b)
	{
		_inOlympiadMode = b;
	}
	
	public void setIsOlympiadStart(boolean b)
	{
		_olympiadStart = b;
	}
	
	public boolean isOlympiadStart()
	{
		return _olympiadStart;
	}
	
	public boolean isHero()
	{
		return _hero;
	}
	
	public boolean isInOlympiadMode()
	{
		return _inOlympiadMode;
	}
	
	public void setNoble(boolean val)
	{
		if (val)
			for (L2Skill s : NobleSkillTable.getNobleSkills())
				addSkill(s, false); // Dont Save Noble skills to Sql
		else
			for (L2Skill s : NobleSkillTable.getNobleSkills())
				super.removeSkill(s); // Just Remove skills without deleting from Sql
		_noble = val;
	}
	
	public boolean isInDuel()
	{
		return _isInDuel;
	}
	
	public int getDuelId()
	{
		return _duelId;
	}
	
	public void setDuelState(int mode)
	{
		_duelState = mode;
	}
	
	public int getDuelState()
	{
		return _duelState;
	}
	
	/**
	 * Sets up the duel state using a non 0 duelId.
	 * @param duelId 0=not in a duel
	 */
	public void setIsInDuel(int duelId)
	{
		if (duelId > 0)
		{
			_isInDuel = true;
			_duelState = Duel.DUELSTATE_DUELLING;
			_duelId = duelId;
		}
		else
		{
			if (_duelState == Duel.DUELSTATE_DEAD)
			{
				enableAllSkills();
				getStatus().startHpMpRegeneration();
			}
			_isInDuel = false;
			_duelState = Duel.DUELSTATE_NODUEL;
			_duelId = 0;
		}
	}
	
	/**
	 * This returns a SystemMessage stating why
	 * the player is not available for duelling.
	 * @return S1_CANNOT_DUEL... message
	 */
	public SystemMessage getNoDuelReason()
	{
		// This is somewhat hacky - but that case should never happen anyway...
		if (_noDuelReason == 0)
			_noDuelReason = SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL.getId();
		
		SystemMessage sm = new SystemMessage(SystemMessageId.getSystemMessageId(_noDuelReason));
		sm.addPcName(this);
		_noDuelReason = 0;
		return sm;
	}
	
	/**
	 * Checks if this player might join / start a duel.
	 * To get the reason use getNoDuelReason() after calling this function.
	 * @return true if the player might join/start a duel.
	 */
	public boolean canDuel()
	{
		if (isInCombat() || isInJail())
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_ENGAGED_IN_BATTLE.getId();
			return false;
		}
		if (isDead()
				|| isAlikeDead()
				|| (getStatus().getCurrentHp() < getStat().getMaxHp() / 2 || getStatus().getCurrentMp() < getStat()
						.getMaxMp() / 2))
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_HP_OR_MP_IS_BELOW_50_PERCENT.getId();
			return false;
		}
		if (isInDuel())
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_ALREADY_ENGAGED_IN_A_DUEL.getId();
			return false;
		}
		if (isInOlympiadMode())
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_PARTICIPATING_IN_THE_OLYMPIAD.getId();
			return false;
		}
		if (isCursedWeaponEquipped())
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_IN_A_CHAOTIC_STATE.getId();
			return false;
		}
		if (getPrivateStoreType() != STORE_PRIVATE_NONE)
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_C1_IS_IN_A_PRIVATE_STORE_OR_MANUFACTURE.getId();
			return false;
		}
		if (isMounted() || isInBoat())
		{
			_noDuelReason =
					SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_RIDING_A_BOAT_WYVERN_OR_STRIDER.getId();
			return false;
		}
		if (isFishing())
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_FISHING.getId();
			return false;
		}
		if (isInsideZone(L2Zone.FLAG_PVP) || isInsideZone(L2Zone.FLAG_PEACE)
				|| SiegeManager.getInstance().checkIfInZone(this))
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_IN_A_DUEL_PROHIBITED_AREA.getId();
			return false;
		}
		if (GlobalRestrictions.isRestricted(this, DuelRestriction.class))
		{
			_noDuelReason = SystemMessageId.C1_CANNOT_DUEL_BECAUSE_C1_IS_CURRENTLY_ENGAGED_IN_BATTLE.getId(); // TODO
			return false;
		}
		return true;
	}
	
	public boolean isNoble()
	{
		/**_noble = false;

		 if (isSubClassActive())
		 {
		 if (getTotalSubClasses() > 1)
		 {
		 _noble = true;
		 return _noble;
		 }
		 if (getLevel() >= 75)
		 {
		 _noble = true;
		 return _noble;
		 }
		 }
		 else
		 {
		 if (getSubClasses() == null)
		 return _noble;

		 if (getLevel() >= 76 && getClassId().getId() >= 88)
		 {
		 for (SubClass sub : getSubClasses().values())
		 {
		 if (sub.getLevel() >= 75)
		 {
		 _noble = true;
		 return _noble;
		 }
		 }
		 }
		 }*/
		
		return _noble;
	}
	
	public int getSubLevel()
	{
		if (isSubClassActive())
		{
			int lvl = getLevel();
			return lvl;
		}
		return 0;
	}
	
	// Baron, Wise Man etc, calculated on EnterWorld and when rank is changing
	public void setPledgeClass(int classId)
	{
		_pledgeClass = classId;
	}
	
	public int getPledgeClass()
	{
		return _pledgeClass;
	}
	
	public void setSubPledgeType(int typeId)
	{
		_subPledgeType = typeId;
	}
	
	public int getSubPledgeType()
	{
		return _subPledgeType;
	}
	
	public int getPledgeRank()
	{
		return _pledgeRank;
	}
	
	public void setPledgeRank(int rank)
	{
		_pledgeRank = rank;
	}
	
	public int getApprentice()
	{
		return _apprentice;
	}
	
	public void setApprentice(int apprentice_id)
	{
		_apprentice = apprentice_id;
	}
	
	public int getSponsor()
	{
		return _sponsor;
	}
	
	public void setSponsor(int sponsor_id)
	{
		_sponsor = sponsor_id;
	}
	
	public void setLvlJoinedAcademy(int lvl)
	{
		_lvlJoinedAcademy = lvl;
	}
	
	public int getLvlJoinedAcademy()
	{
		return _lvlJoinedAcademy;
	}
	
	public boolean isAcademyMember()
	{
		return _lvlJoinedAcademy > 0;
	}
	
	public void setTeam(int team)
	{
		_team = team;
		if (getPet() != null)
			getPet().broadcastStatusUpdate();
	}
	
	public int getTeam()
	{
		return _team;
	}
	
	public void setWantsPeace(int wantsPeace)
	{
		_wantsPeace = wantsPeace;
	}
	
	public int getWantsPeace()
	{
		return _wantsPeace;
	}
	
	public boolean isFishing()
	{
		return _fishing;
	}
	
	public void setFishing(boolean fishing)
	{
		_fishing = fishing;
	}
	
	public void setAllianceWithVarkaKetra(int sideAndLvlOfAlliance)
	{
		// [-5,-1] varka, 0 neutral, [1,5] ketra
		_alliedVarkaKetra = sideAndLvlOfAlliance;
	}
	
	public int getAllianceWithVarkaKetra()
	{
		return _alliedVarkaKetra;
	}
	
	public boolean isAlliedWithVarka()
	{
		return (_alliedVarkaKetra < 0);
	}
	
	public boolean isAlliedWithKetra()
	{
		return (_alliedVarkaKetra > 0);
	}
	
	public final Comparator<L2Skill> SKILL_LIST_COMPARATOR = new Comparator<L2Skill>() {
		@Override
		public int compare(L2Skill s1, L2Skill s2)
		{
			int o1 = getOrder(s1);
			int o2 = getOrder(s2);
			
			if (o1 != o2)
				return (o1 < o2) ? -1 : 1;
			
			int so1 = getSubOrder(s1);
			int so2 = getSubOrder(s2);
			
			if (so1 != so2)
				return (so1 < so2) ? -1 : 1;
			
			return s1.getId().compareTo(s2.getId());
		}
		
		private int getOrder(L2Skill s)
		{
			if (s.getSkillType() == L2SkillType.NOTDONE)
				return 10;
			
			if (isTransformationDisabledSkill(s))
				return 9;
			
			// TODO: add other ordering conditions, if there is any other useful :)
			
			return 0;
		}
		
		private int getSubOrder(L2Skill s)
		{
			if (s.isPositive())
				return 1;
			else if (s.isNeutral())
				return 0;
			else
				// s.isOffensive()
				return -1;
		}
	};
	
	public L2Skill[] getSortedAllSkills(boolean isGM)
	{
		L2Skill[] array = getAllSkills();
		
		for (int i = 0; i < array.length; i++)
		{
			L2Skill s = array[i];
			
			if (s == null)
				continue;
			
			if (!isGM)
			{
				if (!s.canSendToClient())
				{
					array[i] = null;
					continue;
				}
				
				// Hide skills when transformed if they are not passive
				if (isTransformationDisabledSkill(s))
				{
					array[i] = null;
					continue;
				}
			}
			
			if (s.getSkillType() == L2SkillType.NOTDONE)
			{
				switch (Config.SEND_NOTDONE_SKILLS)
				{
					case 1:
					{
						array[i] = null;
						continue;
					}
					case 2:
					{
						if (!isGM)
						{
							array[i] = null;
							continue;
						}
					}
				}
			}
		}
		
		array = L2Arrays.compact(array);
		
		Arrays.sort(array, SKILL_LIST_COMPARATOR);
		
		return array;
	}
	
	public void sendSkillList()
	{
		addPacketBroadcastMask(BroadcastMode.SEND_SKILL_LIST);
	}
	
	public void sendSkillListImpl()
	{
		sendPacket(new SkillList(this));
	}
	
	public void setTransformAllowedSkills(int[] ids)
	{
		_transformAllowedSkills.clear();
		for (int id : ids)
			addTransformAllowedSkill(id);
	}
	
	public void addTransformAllowedSkill(int[] ids)
	{
		for (int id : ids)
			addTransformAllowedSkill(id);
	}
	
	public void addTransformAllowedSkill(int id)
	{
		_transformAllowedSkills.add(id);
	}
	
	public boolean containsAllowedTransformSkill(int id)
	{
		return _transformAllowedSkills.contains(id);
	}
	
	/** Section for mounted pets */
	class FeedTask implements Runnable
	{
		@Override
		public void run()
		{
			try
			{
				if (!isMounted())
				{
					stopFeed();
					return;
				}
				
				if (getCurrentFeed() > getFeedConsume())
				{
					// eat
					setCurrentFeed(getCurrentFeed() - getFeedConsume());
				}
				else
				{
					// go back to pet control item, or simply said, unsummon it
					setCurrentFeed(0);
					stopFeed();
					dismount();
					sendPacket(SystemMessageId.OUT_OF_FEED_MOUNT_CANCELED);
				}
				
				int[] foodIds = PetDataTable.getFoodItemId(getMountNpcId());
				if (foodIds[0] == 0)
					return;
				L2ItemInstance food = null;
				food = getInventory().getItemByItemId(foodIds[0]);
				
				// use better strider food if exists
				if (PetDataTable.isStrider(getMountNpcId()))
				{
					if (getInventory().getItemByItemId(foodIds[1]) != null)
						food = getInventory().getItemByItemId(foodIds[1]);
				}
				if (food != null && isHungry())
				{
					if (ItemHandler.getInstance().useItem(food.getItemId(), L2Player.this, food))
					{
						SystemMessage sm = new SystemMessage(SystemMessageId.PET_TOOK_S1_BECAUSE_HE_WAS_HUNGRY);
						sm.addItemName(food.getItemId());
						sendPacket(sm);
					}
				}
			}
			catch (Exception e)
			{
				_log.error("Mounted Pet [NpcId: " + getMountNpcId() + "] a feed task error has occurred", e);
			}
		}
	}
	
	protected synchronized void startFeed(int npcId)
	{
		_canFeed = npcId > 0;
		if (!isMounted())
			return;
		if (getPet() != null)
		{
			setCurrentFeed(((L2PetInstance)getPet()).getCurrentFed());
			_controlItemId = getPet().getControlItemId();
			sendPacket(new SetupGauge(3, getCurrentFeed() * 10000 / getFeedConsume(), getMaxFeed() * 10000
					/ getFeedConsume()));
			if (!isDead())
			{
				_mountFeedTask =
						ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new FeedTask(), 10000, 10000);
			}
		}
		else if (_canFeed)
		{
			setCurrentFeed(getMaxFeed());
			SetupGauge sg =
					new SetupGauge(3, getCurrentFeed() * 10000 / getFeedConsume(), getMaxFeed() * 10000
							/ getFeedConsume());
			sendPacket(sg);
			if (!isDead())
			{
				_mountFeedTask =
						ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new FeedTask(), 10000, 10000);
			}
		}
	}
	
	protected synchronized void stopFeed()
	{
		if (_mountFeedTask != null)
		{
			_mountFeedTask.cancel(false);
			_mountFeedTask = null;
			if (_log.isDebugEnabled())
				_log.info("Pet [#" + _mountNpcId + "] feed task stop");
		}
	}
	
	protected final void clearPetData()
	{
		_data = null;
	}
	
	protected final L2PetData getPetData(int npcId)
	{
		if (_data == null && getPet() != null)
		{
			_data = PetDataTable.getInstance().getPetData(getPet().getNpcId(), getPet().getLevel());
		}
		else if (_data == null && npcId > 0)
		{
			_data = PetDataTable.getInstance().getPetData(npcId, getLevel());
		}
		
		return _data;
	}
	
	public int getCurrentFeed()
	{
		return _curFeed;
	}
	
	protected int getFeedConsume()
	{
		// if pet is attacking
		if (isAttackingNow())
			return getPetData(_mountNpcId).getPetFeedBattle();
		else
			return getPetData(_mountNpcId).getPetFeedNormal();
	}
	
	public void setCurrentFeed(int num)
	{
		_curFeed = num > getMaxFeed() ? getMaxFeed() : num;
		SetupGauge sg =
				new SetupGauge(3, getCurrentFeed() * 10000 / getFeedConsume(), getMaxFeed() * 10000 / getFeedConsume());
		sendPacket(sg);
	}
	
	protected int getMaxFeed()
	{
		return getPetData(_mountNpcId).getPetMaxFeed();
	}
	
	protected boolean isHungry()
	{
		return _canFeed ? (getCurrentFeed() < (0.55 * getPetData(getMountNpcId()).getPetMaxFeed())) : false;
	}
	
	public class dismount implements Runnable
	{
		@Override
		public void run()
		{
			dismount();
		}
	}
	
	public void enteredNoWyvernZone()
	{
		sendPacket(SystemMessageId.AREA_CANNOT_BE_ENTERED_WHILE_MOUNTED_WYVERN);
		
		_dismountTask = ThreadPoolManager.getInstance().scheduleGeneral(new L2Player.dismount(), 5000);
	}
	
	public void exitedNoWyvernZone()
	{
		if (_dismountTask != null)
		{
			_dismountTask.cancel(false);
			_dismountTask = null;
		}
	}
	
	public void storePetFood(int petId)
	{
		if (_controlItemId != 0 && petId != 0)
		{
			String req = "UPDATE pet SET current_feed = ? WHERE item_id = ?";
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = con.prepareStatement(req);
				statement.setInt(1, getCurrentFeed());
				statement.setInt(2, _controlItemId);
				statement.executeUpdate();
				statement.close();
				_controlItemId = 0;
			}
			catch (Exception e)
			{
				_log.error("Failed to store Pet [NpcId: " + petId + "] data", e);
			}
			finally
			{
				L2DatabaseFactory.close(con);
			}
		}
	}
	
	/** End of section for mounted pets */
	
	/**
	 * 1. Add the specified class ID as a subclass (up to the maximum number of <b>three</b>)
	 * for this character.<BR>
	 * 2. This method no longer changes the active _classIndex of the player. This is only
	 * done by the calling of setActiveClass() method as that should be the only way to do so.
	 *
	 * @param classId
	 * @param classIndex
	 * @return subclassAdded
	 */
	public boolean addSubClass(int classId, int classIndex)
	{
		if (!_subclassLock.tryLock())
			return false;
		
		try
		{
			if (getTotalSubClasses() == Config.ALT_MAX_SUBCLASS || classIndex == 0)
				return false;
			
			if (getSubClasses().containsKey(classIndex))
				return false;
			
			// Note: Never change _classIndex in any method other than setActiveClass().
			
			SubClass newClass = new SubClass();
			newClass.setClassId(classId);
			newClass.setClassIndex(classIndex);
			
			try
			{
				// Store the basic info about this new sub-class.
				PlayerRepository.getInstance().insertSubclass(getObjectId(),
						new SubclassRow(newClass.getClassIndex(), newClass.getClassId(), newClass.getLevel(),
								newClass.getExp(), newClass.getSp()));
			}
			catch (Exception e)
			{
				_log.warn("Could not add character sub class for " + getName() + ": ", e);
				return false;
			}
			
			// Commit after database INSERT incase exception is thrown.
			getSubClasses().put(newClass.getClassIndex(), newClass);
			
			if (_log.isDebugEnabled())
				_log.info(getName() + " added class ID " + classId + " as a sub class at index " + classIndex + ".");
			
			ClassId subTemplate = ClassId.values()[classId];
			Iterable<L2SkillLearn> skillTree = SkillTreeTable.getInstance().getAllowedSkills(subTemplate);
			
			if (skillTree == null)
				return true;
			
			final Map<Integer, L2Skill> skills = new LinkedHashMap<Integer, L2Skill>();
			
			for (L2SkillLearn skillInfo : skillTree)
			{
				if (skillInfo.getMinLevel() <= 40)
				{
					final L2Skill prevSkill = skills.get(skillInfo.getId());
					final L2Skill newSkill = SkillTable.getInstance().getInfo(skillInfo.getId(), skillInfo.getLevel());
					
					if (prevSkill != null && prevSkill.getLevel() >= newSkill.getLevel())
						continue;
					
					skills.put(newSkill.getId(), newSkill);
				}
			}
			
			for (L2Skill skill : skills.values())
				_pcSkills.storeSkill(skill, classIndex);
			
			if (_log.isDebugEnabled())
				_log.info(getName() + " was given " + getAllSkills().length + " skills for their new sub class.");
			
			return true;
		}
		finally
		{
			_subclassLock.unlock();
		}
	}
	
	/**
	 * 1. Completely erase all existance of the subClass linked to the classIndex.<BR>
	 * 2. Send over the newClassId to addSubClass()to create a new instance on this classIndex.<BR>
	 * 3. Upon Exception, revert the player to their BaseClass to avoid further problems.<BR>
	 * 
	 * @param classIndex
	 * @param newClassId
	 * @return subclassAdded
	 */
	public boolean modifySubClass(int classIndex, int newClassId)
	{
		if (!_subclassLock.tryLock())
			return false;
		
		try
		{
			int oldClassId = getSubClasses().get(classIndex).getClassId();
			
			if (_log.isDebugEnabled())
				_log.info(getName() + " has requested to modify sub class index " + classIndex + " from class ID "
						+ oldClassId + " to " + newClassId + ".");
			
			// Everything stored for this sub-class goes together or stays.
			boolean removed = WorldTransaction.run("Removing sub class " + classIndex + " of " + getName(), () -> {
				try
				{
					// Remove all henna info stored for this sub-class.
					PlayerRepository.getInstance().deleteHennas(getObjectId(), classIndex);
					
					// Remove all shortcuts info stored for this sub-class.
					PlayerRepository.getInstance().deleteShortcuts(getObjectId(), classIndex);
					
					// Remove all effects info stored for this sub-class.
					getEffects().deleteEffects(classIndex);
					
					// Remove all skill info stored for this sub-class.
					_pcSkills.deleteSkills(classIndex);
					
					// Remove all basic info stored about this sub-class.
					PlayerRepository.getInstance().deleteSubclass(getObjectId(), classIndex);
				}
				catch (SQLException e)
				{
					throw new IllegalStateException(e);
				}
			});
			
			if (!removed)
			{
				_log.warn("Could not modify sub class for " + getName() + " to class index " + classIndex + ".");
				// This must be done in order to maintain data consistency.
				getSubClasses().remove(classIndex);
				return false;
			}
			
			getSubClasses().remove(classIndex);
		}
		finally
		{
			_subclassLock.unlock();
		}
		
		return addSubClass(newClassId, classIndex);
	}
	
	public boolean isSubClassActive()
	{
		return _classIndex > 0;
	}
	
	public Map<Integer, SubClass> getSubClasses()
	{
		if (_subClasses == null)
			_subClasses = new LazyFastMap<Integer, SubClass>();
		
		return _subClasses;
	}
	
	public int getTotalSubClasses()
	{
		return getSubClasses().size();
	}
	
	public int getBaseClass()
	{
		return _baseClass;
	}
	
	public int getActiveClass()
	{
		return _activeClass;
	}
	
	public int getClassIndex()
	{
		return _classIndex;
	}
	
	private void setClassTemplate(int classId)
	{
		_activeClass = classId;
		
		L2PlayerTemplate t = CharTemplateTable.getInstance().getTemplate(classId);
		
		if (t == null)
		{
			_log.error("Missing template for classId: " + classId);
			throw new Error();
		}
		
		// Set the template of the L2Player
		setTemplate(t);
		
		L2PartyRoom room = getPartyRoom();
		if (room != null)
			room.broadcastPacket(new ExManagePartyRoomMember(ExManagePartyRoomMember.MODIFIED, this));
	}
	
	/**
	 * Changes the character's class based on the given class index.
	 * <BR><BR>
	 * An index of zero specifies the character's original (base) class,
	 * while indexes 1-3 specifies the character's sub-classes respectively.
	 * <br><br>
	 * <font color="00FF00"/>WARNING: Use only on subclass change</font>
	 *
	 * @param classIndex
	 */
	public boolean setActiveClass(int classIndex)
	{
		if (!_subclassLock.tryLock())
			return false;
		
		try
		{
			//  Cannot switch or change subclasses while transformed
			if (_transformation != null)
				return false;
			
			// Remove active item skills before saving char to database
			// because next time when choosing this class, weared items can be different
			
			for (L2ItemInstance temp : getInventory().getAugmentedItems())
				if (temp != null && temp.isEquipped())
					temp.getAugmentation().removeBonus(this);
			
			// Remove class circlets (can't equip circlets while being in subclass)
			L2ItemInstance circlet = getInventory().getPaperdollItem(Inventory.PAPERDOLL_HAIRALL);
			if (circlet != null)
			{
				if (((circlet.getItemId() >= 9397 && circlet.getItemId() <= 9408) || circlet.getItemId() == 10169)
						&& circlet.isEquipped())
				{
					L2ItemInstance[] unequipped =
							getInventory().unEquipItemInBodySlotAndRecord(circlet.getItem().getBodyPart());
					InventoryUpdate iu = new InventoryUpdate();
					for (L2ItemInstance element : unequipped)
						iu.addModifiedItem(element);
					sendPacket(iu);
				}
			}
			
			// Delete a force buff upon class change.
			if (_fusionSkill != null)
				abortCast();
			
			// Stop casting for any player that may be casting a force buff on this L2Player.
			for (L2Creature character : getKnownList().getKnownCharacters())
			{
				if (character.getFusionSkill() != null && character.getFusionSkill().getTarget() == this)
					character.abortCast();
			}
			
			/*
			 * 1. Call store() before modifying _classIndex to avoid skill effects rollover.
			 * 2. Register the correct _classId against applied 'classIndex'.
			 */
			
			store(Config.STORE_EFFECTS_ON_SUBCLASS_CHANGE);
			
			// clear charges
			clearCharges();
			
			if (classIndex == 0)
			{
				setClassTemplate(getBaseClass());
			}
			else
			{
				try
				{
					setClassTemplate(getSubClasses().get(classIndex).getClassId());
				}
				catch (Exception e)
				{
					_log.info("Could not switch " + getName() + "'s sub class to class index " + classIndex + ": ", e);
					return false;
				}
			}
			_classIndex = classIndex;
			
			if (isInParty())
			{
				if (Config.MAX_PARTY_LEVEL_DIFFERENCE > 0)
				{
					for (L2Player p : getParty().getPartyMembers())
					{
						if (Math.abs(p.getLevel() - getLevel()) > Config.MAX_PARTY_LEVEL_DIFFERENCE)
						{
							getParty().removePartyMember(this);
							sendMessage("You have been removed from your party, because the level difference is too big.");
							break;
						}
					}
				}
				else
					getParty().recalculatePartyLevel();
			}
			
			/*
			 * Update the character's change in class status.
			 *
			 * 1. Remove any active cubics from the player.
			 * 2. Renovate the characters table in the database with the new class info, storing also buff/effect data.
			 * 3. Remove all existing skills.
			 * 4. Restore all the learned skills for the current class from the database.
			 * 5. Restore effect/buff data for the new class.
			 * 6. Restore henna data for the class, applying the new stat modifiers while removing existing ones.
			 * 7. Reset HP/MP/CP stats and send Server->Client character status packet to reflect changes.
			 * 8. Restore shortcut data related to this class.
			 * 9. Resend a class change animation effect to broadcast to all nearby players.
			 * 10.Unsummon any active servitor from the player.
			 */
			
			if (getPet() instanceof L2SummonInstance)
				getPet().unSummon(this);
			
			if (!getCubics().isEmpty())
			{
				for (L2CubicInstance cubic : getCubics().values())
				{
					cubic.stopAction();
					cubic.cancelDisappear();
				}
				
				getCubics().clear();
			}
			
			abortCast();
			
			for (L2Skill oldSkill : getAllSkills())
				super.removeSkill(oldSkill);
			
			stopAllEffectsExceptThoseThatLastThroughDeath();
			
			restoreRecipeBook(false);
			
			// Restore any Death Penalty Buff
			restoreDeathPenaltyBuffLevel();
			
			restoreSkills();
			regiveTemporarySkills();
			rewardSkills();
			
			getEffects().restoreEffects();
			updateEffectIcons();
			
			// If player has quest "422: Repent Your Sins", remove it
			QuestState st = getQuestState("422_RepentYourSins");
			if (st != null)
			{
				st.exitQuest(true);
			}
			
			for (int i = 0; i < 3; i++)
				_henna[i] = null;
			
			restoreHenna();
			sendPacket(new HennaInfo(this));
			
			checkItemRestriction();
			
			if (getCurrentHp() > getMaxHp())
				getStatus().setCurrentHp(getMaxHp());
			if (getCurrentMp() > getMaxMp())
				getStatus().setCurrentMp(getMaxMp());
			if (getCurrentCp() > getMaxCp())
				getStatus().setCurrentCp(getMaxCp());
			
			getInventory().restoreEquipedItemsPassiveSkill();
			getInventory().restoreArmorSetPassiveSkill();
			
			refreshOverloaded();
			refreshExpertisePenalty();
			broadcastUserInfo();
			
			// Clear resurrect xp calculation
			setExpBeforeDeath(0);
			
			//getMacroses().restore();
			//getMacroses().sendUpdate();
			
			getShortCuts().restore();
			sendPacket(new ShortCutInit(this));
			
			broadcastPacket(new SocialAction(getObjectId(), SocialAction.LEVEL_UP));
			sendSkillCoolTime();
			sendPacket(new ExStorageMaxCount(this));
			
			broadcastClassIcon();
			
			return true;
		}
		finally
		{
			_subclassLock.unlock();
		}
	}
	
	public boolean isLocked()
	{
		return _subclassLock.isLocked();
	}
	
	public void broadcastClassIcon()
	{
		// Update class icon in party and clan
		if (isInParty())
			getParty().broadcastToPartyMembers(new PartySmallWindowUpdate(this));
		
		if (getClan() != null)
			getClan().broadcastToOnlineMembers(new PledgeShowMemberListUpdate(this));
	}
	
	public void stopWarnUserTakeBreak()
	{
		if (_taskWarnUserTakeBreak != null)
		{
			_taskWarnUserTakeBreak.cancel(false);
			_taskWarnUserTakeBreak = null;
		}
	}
	
	public void startWarnUserTakeBreak()
	{
		if (_taskWarnUserTakeBreak == null)
			_taskWarnUserTakeBreak =
					ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new WarnUserTakeBreak(), 7200000,
							7200000);
	}
	
	public void stopRentPet()
	{
		if (_taskRentPet != null)
		{
			// If the rent of a wyvern expires while over a flying zone, tp to down before unmounting
			if (getMountType() == 2 && !checkCanLand())
				teleToLocation(TeleportWhereType.Town);
			
			if (dismount()) // This should always be true now, since we teleported already
			{
				_taskRentPet.cancel(true);
				_taskRentPet = null;
			}
		}
	}
	
	public void startRentPet(int seconds)
	{
		if (_taskRentPet == null)
			_taskRentPet =
					ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new RentPetTask(), seconds * 1000L,
							seconds * 1000);
	}
	
	public boolean isRentedPet()
	{
		return _taskRentPet != null;
	}
	
	public void stopWaterTask()
	{
		if (_taskWater != null)
		{
			_taskWater.cancel(false);
			
			_taskWater = null;
			sendPacket(new SetupGauge(SetupGauge.CYAN, 0));
			// Added to sync fall when swimming stops:
			// (e.g. catacombs players swim down and then they fell when they got out of the water).
			isFalling(false, 0);
		}
		
		broadcastUserInfo();
	}
	
	public void startWaterTask()
	{
		// Temp fix here
		if (isMounted())
			dismount();
		
		if (isTransformed() && !isCursedWeaponEquipped())
		{
			untransform();
		}
		// TODO: update to only send speed status when that packet is known
		else
			broadcastUserInfo();
		
		if (!isDead() && _taskWater == null)
		{
			int timeinwater = (int)calcStat(Stats.BREATH, 60000, this, null);
			
			sendPacket(new SetupGauge(2, timeinwater));
			_taskWater = ThreadPoolManager.getInstance().scheduleEffectAtFixedRate(new WaterTask(), timeinwater, 1000);
		}
	}
	
	public boolean isInWater()
	{
		return _taskWater != null;
	}
	
	public void onPlayerEnter()
	{
		startWarnUserTakeBreak();
		startAutoSaveTask();
		
		if (SevenSigns.getInstance().isSealValidationPeriod() || SevenSigns.getInstance().isCompResultsPeriod())
		{
			if (!isGM() && isIn7sDungeon() && Config.ALT_STRICT_SEVENSIGNS
					&& SevenSigns.getInstance().getPlayerCabal(this) != SevenSigns.getInstance().getCabalHighestScore())
			{
				teleToLocation(TeleportWhereType.Town);
				setIsIn7sDungeon(false);
				sendPacket(SystemMessage
						.sendString("You have been teleported to the nearest town due to the beginning of the Seal Validation period."));
			}
		}
		else
		{
			if (!isGM() && isIn7sDungeon() && Config.ALT_STRICT_SEVENSIGNS
					&& SevenSigns.getInstance().getPlayerCabal(this) == SevenSigns.CABAL_NULL)
			{
				teleToLocation(TeleportWhereType.Town);
				setIsIn7sDungeon(false);
				sendPacket(SystemMessage
						.sendString("You have been teleported to the nearest town because you have not signed for any cabal."));
			}
		}
		
		// Jail task
		updateJailState();
		
		if (_isInvul) // isInvul() is always true on login if login protection is activated...
			sendPacket(SystemMessage.sendString("Entering world in Invulnerable mode."));
		if (getAppearance().isInvisible())
			sendPacket(SystemMessage.sendString("Entering world in Invisible mode."));
		if (getMessageRefusal())
			sendPacket(SystemMessage.sendString("Entering world in Message Refusal mode."));
		
		revalidateZone(true);
		
		// [L2J_JP ADD SANDMAN] Check of a restart prohibition area.
		if (!isGM())
		{
			// Four-Sepulcher,It is less than 5 minutes.
			if (FourSepulchersManager.getInstance().checkIfInZone(this)
					&& (System.currentTimeMillis() - getLastAccess() >= 300000))
			{
				int driftX = Rnd.get(-80, 80);
				int driftY = Rnd.get(-80, 80);
				teleToLocation(178293 + driftX, -84607 + driftY, -7216);
			}
			
			// It is less than a time limit from player restarting.
			// TODO write code for restart fight against bosses.
			// Lair of bosses,It is less than 30 minutes from server starting.
			// It is only for Antharas and Valakas that I know it now.
			// Thanks a lot Serafiel of L2J_JP.
			
			// 10 minutes
			// Antharas
			else if (AntharasManager.getInstance().checkIfInZone(this))
			{
				// Lair of bosses,It is less than 30 minutes from server starting.
				// Player can restart inside lair, but Antharas do not respawn.
				if (System.currentTimeMillis() - GameServer.getStartedTime().getTimeInMillis() <= Config.ALT_TIMELIMITOFINVADE)
				{
					if (getQuestState("antharas") != null)
						getQuestState("antharas").exitQuest(true);
				}
				else if (System.currentTimeMillis() - getLastAccess() >= 600000)
				{
					if (getQuestState("antharas") != null)
						getQuestState("antharas").exitQuest(true);
					teleToLocation(TeleportWhereType.Town);
				}
			}
			
			// Baium
			else if (BaiumManager.getInstance().checkIfInZone(this))
			{
				if (System.currentTimeMillis() - getLastAccess() >= 600000)
				{
					if (getQuestState("baium") != null)
						getQuestState("baium").exitQuest(true);
					teleToLocation(TeleportWhereType.Town);
				}
				else
				{
					// Player can restart inside lair, but can not awake Baium.
					if (getQuestState("baium") != null)
						getQuestState("baium").exitQuest(true);
				}
			}
			
			// 10 minutes
			// Last Imperial Tomb (includes Frintezza's room)
			else if (LastImperialTombManager.getInstance().checkIfInZone(this))
			{
				// Lair of bosses,It is less than 30 minutes from server starting.
				// Player can restart inside lair, but Antharas do not respawn.
				if (System.currentTimeMillis() - GameServer.getStartedTime().getTimeInMillis() <= Config.ALT_TIMELIMITOFINVADE)
				{
					if (getQuestState("lastimperialtomb") != null)
						getQuestState("lastimperialtomb").exitQuest(true);
				}
				else if (System.currentTimeMillis() - getLastAccess() >= 600000)
				{
					if (getQuestState("lastimperialtomb") != null)
						getQuestState("lastimperialtomb").exitQuest(true);
					teleToLocation(TeleportWhereType.Town);
				}
			}
			
			// 10 minutes
			// Not sure if Imperial tomb includes Frintezza's room
			// So just incase lets clear that room from intruders till all FrintezzaManager will work like retail
			else if (FrintezzaManager.getInstance().checkIfInZone(this))
			{
				if (System.currentTimeMillis() - getLastAccess() >= 600000
						&& !FrintezzaManager.getInstance().getState().equals(GrandBossState.StateEnum.ALIVE))
					teleToLocation(TeleportWhereType.Town);
			}
			
			// Lilith
			/*else if (LilithManager.getInstance().checkIfInZone(this))
			{
				if (System.currentTimeMillis() - getLastAccess() >= 600000)
					teleToLocation(TeleportWhereType.Town);
			}

			// Anakim
			else if (AnakimManager.getInstance().checkIfInZone(this))
			{
				if (System.currentTimeMillis() - getLastAccess() >= 600000)
					teleToLocation(TeleportWhereType.Town);
			}

			// Zaken
			else if (ZakenManager.getInstance().checkIfInZone(this))
			{
				if (System.currentTimeMillis() - getLastAccess() >= 600000)
					teleToLocation(TeleportWhereType.Town);
			}*/
			
			// High Priestess van Halter
			else if (VanHalterManager.getInstance().checkIfInZone(this))
			{
				if (System.currentTimeMillis() - getLastAccess() >= 600000)
					teleToLocation(TeleportWhereType.Town);
				else
					VanHalterManager.getInstance().intruderDetection(this);
			}
			
			// 30 minutes
			// Valakas
			else if (ValakasManager.getInstance().checkIfInZone(this))
			{
				// Lair of bosses,It is less than 30 minutes from server starting.
				// Player can restart inside lair, and begin fight against Valakas 30min later.
				if (System.currentTimeMillis() - GameServer.getStartedTime().getTimeInMillis() <= Config.ALT_TIMELIMITOFINVADE
						&& ValakasManager.getInstance().getState() == GrandBossState.StateEnum.ALIVE)
				{
				}
				else
				{
					if (getQuestState("valakas") != null)
						getQuestState("valakas").exitQuest(true);
					teleToLocation(TeleportWhereType.Town);
				}
			}
			// Baylor
			else if (BaylorManager.getInstance().checkIfInZone(this))
			{
				if (getQuestState("baylor") != null)
					getQuestState("baylor").exitQuest(true);
				teleToLocation(TeleportWhereType.Town);
			}
			// Sailren
			else if (SailrenManager.getInstance().checkIfInZone(this))
			{
				if (getQuestState("sailren") != null)
					getQuestState("sailren").exitQuest(true);
				teleToLocation(TeleportWhereType.Town);
			}
		}
	}
	
	public void checkWaterState()
	{
		if (isInsideZone(L2Zone.FLAG_WATER))
		{
			startWaterTask();
		}
		else
		{
			stopWaterTask();
		}
	}
	
	public long getLastAccess()
	{
		return _lastAccess;
	}
	
	public int getBoatId()
	{
		return _boatId;
	}
	
	public void setBoatId(int boatId)
	{
		_boatId = boatId;
	}
	
	@Override
	public void doRevive()
	{
		super.doRevive();
		stopEffects(L2EffectType.CHARMOFCOURAGE);
		updateEffectIcons();
		_reviveRequested = false;
		
		if (isMounted())
			startFeed(_mountNpcId);
		
		if (isInParty() && getParty().isInDimensionalRift())
		{
			if (!DimensionalRiftManager.getInstance().checkIfInPeaceZone(getX(), getY(), getZ()))
				getParty().getDimensionalRift().memberRessurected(this);
		}
		
		GlobalRestrictions.playerRevived(this);
	}
	
	@Override
	public void doRevive(double revivePower)
	{
		// Restore the player's lost experience,
		// depending on the % return of the skill used (based on its power).
		restoreExp(revivePower);
		doRevive();
	}
	
	public void reviveRequest(L2Player reviver, L2Skill skill)
	{
		if (_reviveRequested || _revivePetRequested)
		{
			reviver.sendPacket(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED); // Resurrection is already been proposed.
			return;
		}
		if (isDead())
		{
			_reviveRequested = true;
			
			final double revivePower;
			if (isPhoenixBlessed())
				revivePower = 100;
			else if (skill != null)
				revivePower = Formulas.calculateSkillResurrectRestorePercent(skill, reviver);
			else
				revivePower = 0;
			
			int restoreExp = (int)Math.round((getExpBeforeDeath() - getExp()) * revivePower / 100);
			
			if (getCharmOfCourage())
			{
				ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.RESURRECT_USING_CHARM_OF_COURAGE);
				dlg.addTime(60000);
				dlg.addAnswerHandler(new AnswerHandler() {
					@Override
					public void handle(boolean answer)
					{
						reviveAnswer(answer, revivePower);
					}
				});
				sendPacket(dlg);
				return;
			}
			
			ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.RESSURECTION_REQUEST_BY_C1_FOR_S2_XP);
			dlg.addPcName(reviver);
			dlg.addString(String.valueOf(restoreExp));
			dlg.addAnswerHandler(new AnswerHandler() {
				@Override
				public void handle(boolean answer)
				{
					reviveAnswer(answer, revivePower);
				}
			});
			sendPacket(dlg);
		}
	}
	
	public void revivePetRequest(L2Player reviver, L2Skill skill)
	{
		if (_reviveRequested || _revivePetRequested)
		{
			reviver.sendPacket(SystemMessageId.RES_HAS_ALREADY_BEEN_PROPOSED); // Resurrection is already been proposed.
			return;
		}
		
		if (getPet().isDead() && getPet() instanceof L2PetInstance)
		{
			_revivePetRequested = true;
			
			final double revivePower;
			if (skill != null)
				revivePower = Formulas.calculateSkillResurrectRestorePercent(skill, reviver);
			else
				revivePower = 0;
			
			int restoreExp =
					(int)Math.round((((L2PetInstance)getPet()).getExpBeforeDeath() - getPet().getStat().getExp())
							* revivePower / 100);
			
			ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.RESSURECTION_REQUEST_BY_C1_FOR_S2_XP);
			dlg.addAnswerHandler(new AnswerHandler() {
				@Override
				public void handle(boolean answer)
				{
					reviveAnswer(answer, revivePower);
				}
			});
			sendPacket(dlg.addPcName(reviver).addString("" + restoreExp));
		}
	}
	
	public void reviveAnswer(boolean answer, double revivePower)
	{
		if (!(_reviveRequested && isDead() || _revivePetRequested && getPet() != null && getPet().isDead()))
			return;
		// If character refuses a PhoenixBless autoress, cancel all buffs he had
		if (!answer && isPhoenixBlessed() && isDead() && _reviveRequested)
		{
			stopPhoenixBlessing(true);
			stopAllEffectsExceptThoseThatLastThroughDeath();
		}
		
		if (answer)
		{
			if (_reviveRequested)
			{
				if (revivePower != 0)
					doRevive(revivePower);
				else
					doRevive();
			}
			else if (_revivePetRequested && getPet() != null)
			{
				if (revivePower != 0)
					getPet().doRevive(revivePower);
				else
					getPet().doRevive();
			}
		}
		_reviveRequested = false;
		_revivePetRequested = false;
	}
	
	public boolean isReviveRequested()
	{
		return _reviveRequested;
	}
	
	public boolean isPetReviveRequested()
	{
		return _revivePetRequested;
	}
	
	public void removeReviving()
	{
		_reviveRequested = false;
	}
	
	public void removePetReviving()
	{
		_revivePetRequested = false;
	}
	
	public void onActionRequest()
	{
		setProtection(false);
	}
	
	/**
	 * @param expertiseIndex The expertiseIndex to set.
	 */
	public void setExpertiseIndex(int expertiseIndex)
	{
		_expertiseIndex = expertiseIndex;
	}
	
	/**
	 * @return Returns the expertiseIndex.
	 */
	public int getExpertiseIndex()
	{
		return _expertiseIndex;
	}
	
	@Override
	public boolean onTeleported()
	{
		if (!super.onTeleported())
			return false;
		
		getKnownList().updateKnownObjects();
		
		setProtection(true);
		
		// Trained beast is after teleport lost
		if (getTrainedBeast() != null)
		{
			getTrainedBeast().decayMe();
			setTrainedBeast(null);
		}
		// Modify the position of the pet if necessary
		L2Summon pet = getPet();
		if (pet != null)
		{
			pet.setFollowStatus(false);
			getPet().teleToLocation(getPosition().getX(), getPosition().getY(), getPosition().getZ(), false);
			((L2SummonAI)getPet().getAI()).setStartFollowController(true);
			pet.setFollowStatus(true);
			getPet().broadcastFullInfoImpl(0);
		}
		
		sendPacket(new UserInfo(this));
		return true;
	}
	
	@Override
	public void setIsTeleporting(boolean teleport)
	{
		super.setIsTeleporting(teleport);
		if (teleport)
		{
			if (Config.TELEPORT_WATCHDOG_TIMEOUT > 0 && _teleportWatchdog == null)
				_teleportWatchdog =
						ThreadPoolManager.getInstance().scheduleGeneral(new TeleportWatchdog(),
								Config.TELEPORT_WATCHDOG_TIMEOUT * 1000);
		}
		else if (_teleportWatchdog != null)
		{
			_teleportWatchdog.cancel(false);
			_teleportWatchdog = null;
		}
	}
	
	private class TeleportWatchdog implements Runnable
	{
		@Override
		public void run()
		{
			if (!isTeleporting() || getOnlineState() == L2Player.ONLINE_STATE_DELETED)
				return;
			
			if (_log.isDebugEnabled())
				_log.debug("Player " + getName() + " teleport timeout expired");
			onTeleported();
		}
	}
	
	public void teleToLocation(TeleportWhereType teleportWhere)
	{
		teleToLocation(MapRegionManager.getInstance().getTeleToLocation(this, teleportWhere), true);
	}
	
	@Override
	public void addExpAndSp(long addToExp, int addToSp)
	{
		getStat().addExpAndSp(addToExp, addToSp, false);
	}
	
	public void addExpAndSp(long addToExp, int addToSp, boolean useVitality)
	{
		getStat().addExpAndSp(addToExp, addToSp, useVitality);
	}
	
	public void removeExpAndSp(long removeExp, int removeSp)
	{
		getStat().removeExpAndSp(removeExp, removeSp);
	}
	
	public void removeExpAndSp(long removeExp, int removeSp, boolean sendMessage)
	{
		getStat().removeExpAndSp(removeExp, removeSp, sendMessage);
	}
	
	/**
	 * Function is used in the PLAYER, calls snoop for all GMs listening to this player speak.
	 *
	 * @param channel - msg channel of the snooped player
	 * @param name - name of snooped player
	 * @param text - the msg the snooped player sent/received
	 */
	public void broadcastSnoop(SystemChatChannelId channel, String name, String text)
	{
		if (_snoopers.length == 0)
			return;
		
		final Snoop sn = new Snoop(this, channel, name, text);
		
		for (L2Player snooper : _snoopers)
			snooper.sendPacket(sn);
	}
	
	/**
	 * Adds a spy ^^ GM to the player listener.
	 *
	 * @param pci - GM char that listens to the conversation
	 */
	public void addSnooper(L2Player snooper)
	{
		if (!ArrayUtils.contains(_snoopers, snooper))
			_snoopers = ArrayUtils.add(_snoopers, snooper);
	}
	
	public void removeSnooper(L2Player snooper)
	{
		_snoopers = ArrayUtils.removeElement(_snoopers, snooper);
	}
	
	public void removeSnooped(L2Player snooped)
	{
		_snoopedPlayers = ArrayUtils.removeElement(_snoopedPlayers, snooped);
	}
	
	/**
	 * Adds a player to the GM queue for being listened.
	 * @param pci - player we listen to
	 */
	public void addSnooped(L2Player snooped)
	{
		if (!ArrayUtils.contains(_snoopedPlayers, snooped))
		{
			_snoopedPlayers = ArrayUtils.add(_snoopedPlayers, snooped);
			
			sendPacket(new Snoop(snooped, SystemChatChannelId.Chat_Normal, "", "*** Starting snooping of player "
					+ snooped.getName() + " ***"));
		}
	}
	
	public synchronized void buildBypassCache(final Replaceable replaceable)
	{
		if (_validBypass != null)
			_validBypass.clear();
		
		if (_validBypass2 != null)
			_validBypass2.clear();
		
		for (int i = 0; i < replaceable.length(); i++)
		{
			int start = replaceable.indexOf("bypass -h", i);
			int finish = replaceable.indexOf("\"", start);
			
			if (start < 0 || finish < 0)
				break;
			
			start += 10;
			i = start;
			int finish2 = replaceable.indexOf("$", start);
			
			if (0 < finish2 && finish2 < finish)
			{
				if (_validBypass2 == null)
					_validBypass2 = new ArrayList<String>();
				
				_validBypass2.add(replaceable.substring(start, finish2).trim());
			}
			else
			{
				if (_validBypass == null)
					_validBypass = new ArrayList<String>();
				
				_validBypass.add(replaceable.substring(start, finish).trim());
			}
		}
	}
	
	public synchronized void validateBypass(String cmd) throws InvalidPacketException
	{
		if (_validBypass != null)
			for (String bp : _validBypass)
				if (bp != null && cmd.equals(bp))
					return;
		
		if (_validBypass2 != null)
			for (String bp : _validBypass2)
				if (bp != null && cmd.startsWith(bp))
					return;
		
		throw new InvalidPacketException("[" + this + "] sent invalid bypass '" + cmd + "'!");
	}
	
	/**
	 * Performs following tests:<br>
	 * <li> Inventory contains item
	 * <li> Item owner id == this.owner id
	 * <li> It isnt pet control item while mounting pet or pet summoned
	 * <li> It isnt active enchant item
	 * <li> It isnt cursed weapon/item
	 * <li> It isnt wear item
	 * <br>
	 * 
	 * @param objectId item object id
	 * @param action just for logging
	 * @return
	 */
	public boolean validateItemManipulation(int objectId, String action)
	{
		L2ItemInstance item = getInventory().getItemByObjectId(objectId);
		
		if (item == null || item.getOwnerId() != getObjectId())
		{
			_log.debug(getObjectId() + ": player tried to " + action + " item he is not owner of");
			return false;
		}
		
		// Pet is summoned and not the item that summoned the pet AND not the buggle from strider you're mounting
		if (getPet() != null && getPet().getControlItemId() == objectId || getMountObjectID() == objectId)
		{
			if (_log.isDebugEnabled())
				_log.debug(getObjectId() + ": player tried to " + action + " item controling pet");
			
			return false;
		}
		
		if (getActiveEnchantItem() != null && getActiveEnchantItem().getObjectId() == objectId)
		{
			if (_log.isDebugEnabled())
				_log.debug(getObjectId() + ":player tried to " + action + " an enchant scroll he was using");
			
			return false;
		}
		
		if (CursedWeaponsManager.getInstance().isCursed(item.getItemId()))
		{
			// Cannot trade a cursed weapon
			return false;
		}
		
		return !item.isWear();
	}
	
	/**
	 * @return Returns the inBoat.
	 */
	public boolean isInBoat()
	{
		return getBoat() != null;
	}
	
	/**
	 * @return
	 */
	public L2BoatInstance getBoat()
	{
		return _boat;
	}
	
	/**
	 * @param boat
	 */
	public void setBoat(L2BoatInstance boat)
	{
		_boat = boat;
	}
	
	/**
	 * @return Returns the inAirShip.
	 */
	public boolean isInAirShip()
	{
		return getAirShip() != null;
	}
	
	/**
	 * @return
	 */
	public L2AirShipInstance getAirShip()
	{
		return _airShip;
	}
	
	/**
	 * @param airShip
	 */
	public void setAirShip(L2AirShipInstance airShip)
	{
		_airShip = airShip;
	}
	
	public void setInCrystallize(boolean inCrystallize)
	{
		_inCrystallize = inCrystallize;
	}
	
	public boolean isInCrystallize()
	{
		return _inCrystallize;
	}
	
	/**
	 * @return
	 */
	public Point3D getInBoatPosition()
	{
		return _inBoatPosition;
	}
	
	public void setInBoatPosition(Point3D pt)
	{
		_inBoatPosition = pt;
	}
	
	/**
	 * @return
	 */
	public Point3D getInAirShipPosition()
	{
		return _inAirShipPosition;
	}
	
	public void setInAirShipPosition(Point3D pt)
	{
		_inAirShipPosition = pt;
	}
	
	/**
	 * Manage the delete task of a L2Player (Leave Party, Unsummon pet, Save its inventory in the database, Remove it from the world...).<BR><BR>
	 *
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>If the L2Player is in observer mode, set its position to its position before entering in observer mode </li>
	 * <li>Set the online Flag to True or False and update the characters table of the database with online status and lastAccess </li>
	 * <li>Stop the HP/MP/CP Regeneration task </li>
	 * <li>Cancel Crafting, Attak or Cast </li>
	 * <li>Remove the L2Player from the world </li>
	 * <li>Stop Party and Unsummon Pet </li>
	 * <li>Update database with items in its inventory and remove them from the world </li>
	 * <li>Remove all L2Object from _knownObjects and _knownPlayer of the L2Creature then cancel Attak or Cast and notify AI </li>
	 * <li>Close the connection with the client </li><BR><BR>
	 *
	 */
	public void deleteMe()
	{
		final HashSet<L2Zone> before = getZonesPlayerIn();
		
		if (getOnlineState() == ONLINE_STATE_DELETED || !_deleteStarted.compareAndSet(false, true))
			return;
		
		// Publish the flag before the rest of the work, so a second deleteMe on this instance returns.
		try
		{
			setOnlineStatus(false);
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		// Pause restrictions
		ObjectRestrictions.getInstance().pauseTasks(getObjectId());
		
		abortCast();
		abortAttack();
		
		try
		{
			if (isFlying())
				removeSkill(SkillTable.getInstance().getInfo(4289, 1));
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		try
		{
			L2ItemInstance flag = getInventory().getItemByItemId(9819);
			if (flag != null)
			{
				Fort fort = FortManager.getInstance().getFort(this);
				if (fort != null)
					FortSiegeManager.getInstance().dropCombatFlag(this);
				else
				{
					int slot = flag.getItem().getBodyPart();
					getInventory().unEquipItemInBodySlotAndRecord(slot);
					destroyItem("CombatFlag", flag, null, true);
				}
			}
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		// If the L2Player has Pet, unsummon it
		if (getPet() != null)
		{
			try
			{
				getPet().unSummon(this);
				// dead pet wasnt unsummoned, broadcast npcinfo changes (pet will be without owner name - means owner offline)
				if (getPet() != null)
					getPet().broadcastFullInfoImpl(0);
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}// Return pet to the control item
		}
		
		// Cancel trade
		if (getActiveRequester() != null)
		{
			getActiveRequester().onTradeCancel(this);
			onTradeCancel(getActiveRequester());
			
			cancelActiveTrade();
			
			setActiveRequester(null);
		}
		
		// Check if the L2Player is in observer mode to set its position to its position before entering in observer mode
		if (inObserverMode())
			getPosition().setXYZ(_obsX, _obsY, _obsZ);
		else if (isInAirShip())
			getAirShip().oustPlayer(this);
		
		Castle castle = null;
		if (getClan() != null)
		{
			castle = CastleManager.getInstance().getCastleByOwner(getClan());
			if (castle != null)
				castle.destroyClanGate();
		}
		
		// Stop the HP/MP/CP Regeneration task (scheduled tasks)
		try
		{
			stopAllTimers();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		GlobalRestrictions.playerDisconnected(this);
		
		try
		{
			setIsTeleporting(false);
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		// Stop crafting, if in progress
		try
		{
			RecipeTable.getInstance().requestMakeItemAbort(this);
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		try
		{
			setTarget(null);
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		if (_throne != null)
			_throne.setOccupier(null);
		_throne = null;
		
		try
		{
			if (_fusionSkill != null)
			{
				abortCast();
			}
			for (L2Creature character : getKnownList().getKnownCharacters())
				if (character.getFusionSkill() != null && character.getFusionSkill().getTarget() == this)
					character.abortCast();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		getEffects().stopAllEffects(true);
		
		// Remove from world regions zones
		L2WorldRegion oldRegion = getWorldRegion();
		
		// Remove the L2Player from the world
		if (isVisible())
		{
			try
			{
				decayMe();
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
		
		if (oldRegion != null)
			oldRegion.removeFromZones(this);
		
		// If a Party is in progress, leave it (and festival party)
		if (isInParty())
		{
			try
			{
				// If player is festival participant and it is in progress
				// notify party members that the player is not longer a participant.
				if (isFestivalParticipant() && SevenSignsFestival.getInstance().isFestivalInitialized())
				{
					getParty().broadcastToPartyMembers(
							SystemMessage.sendString(getName() + " has been removed from the upcoming festival."));
				}
				
				leaveParty();
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
		else
		// if in party, already taken care of
		{
			L2PartyRoom room = getPartyRoom();
			if (room != null)
				room.removeMember(this, false);
		}
		PartyRoomManager.getInstance().removeFromWaitingList(this);
		
		if (getClanId() != 0 && getClan() != null)
		{
			// Set the status for pledge member list to OFFLINE
			try
			{
				L2ClanMember clanMember = getClan().getClanMember(getName());
				if (clanMember != null)
					clanMember.setPlayerInstance(null);
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
		
		if (getActiveRequester() != null)
		{
			// Deals with sudden exit in the middle of transaction
			setActiveRequester(null);
		}
		
		// If the L2Player is a GM, remove it from the GM List
		if (isGM())
		{
			try
			{
				GmListTable.deleteGm(this);
			}
			catch (Exception e)
			{
				_log.error(e.getMessage(), e);
			}
		}
		
		// remove player from instance and set spawn location if any
		try
		{
			if (isInInstance())
			{
				final Instance inst = InstanceManager.getInstance().getInstance(getInstanceId());
				if (inst != null)
				{
					inst.removePlayer(getObjectId());
					final Location spawn = inst.getSpawnLoc();
					if (spawn != null)
					{
						final int x = spawn.getX() + Rnd.get(-30, 30);
						final int y = spawn.getY() + Rnd.get(-30, 30);
						getPosition().setXYZ(x, y, spawn.getZ());
						if (getPet() != null) // dead pet
						{
							getPet().teleToLocation(x, y, spawn.getZ());
							// ??? unset pet's instance id, but not players
							getPet().decayMe();
							getPet().spawnMe();
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		// Update database with items in its inventory and remove them from the world
		try
		{
			getInventory().deleteMe();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		// Update database with items in its warehouse and remove them from the world
		try
		{
			clearWarehouse();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		if (Config.WAREHOUSE_CACHE)
			WarehouseCacheManager.getInstance().remove(this);
		
		// Update database with items in its freight and remove them from the world
		try
		{
			clearFreight();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		try
		{
			clearDepositedFreight();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		// Remove all L2Object from _knownObjects and _knownPlayer of the L2Creature then cancel Attak or Cast and notify AI
		try
		{
			getKnownList().removeAllKnownObjects();
		}
		catch (Exception e)
		{
			_log.error(e.getMessage(), e);
		}
		
		untransform();
		
		if (getClanId() > 0)
			getClan().broadcastToOtherOnlineMembers(new PledgeShowMemberListUpdate(this), this);
		
		if (_snoopedPlayers.length > 0)
		{
			for (L2Player snooped : _snoopedPlayers)
				snooped.removeSnooper(this);
			_snoopedPlayers = L2Player.EMPTY_ARRAY;
		}
		
		if (_snoopers.length > 0)
		{
			broadcastSnoop(SystemChatChannelId.Chat_Normal, "", "*** Player " + getName() + " logged off ***");
			for (L2Player snooper : _snoopers)
				snooper.removeSnooped(this);
			_snoopers = L2Player.EMPTY_ARRAY;
		}
		
		for (Integer objId : getFriendList().getFriendIds())
		{
			L2Player friend = L2World.getInstance().findPlayer(objId);
			if (friend != null)
			{
				friend.sendPacket(new FriendList(friend));
				friend.sendMessage("Friend: " + getName() + " has logged off.");
			}
		}
		
		MovementController.getInstance().remove(this);
		
		// Remove L2Object object from _allObjects of L2World, if still in it
		L2World.getInstance().removeObject(this);
		
		try
		{
			// To delete the player from L2World on crit during teleport ;)
			setIsTeleporting(false);
			
			L2World.getInstance().removeOnlinePlayer(this);
		}
		catch (RuntimeException e)
		{
			_log.error("deleteMe()", e);
		}
		
		RegionBBSManager.changeCommunityBoard(this, PlayerStateOnCommunity.NONE);
		
		//getClearableReference().clear();
		LeakTaskManager.getInstance().add(this);
		
		SQLQueue.getInstance().run();
		
		final HashSet<L2Zone> after = getZonesPlayerIn();
		
		if (!after.isEmpty())
		{
			_log.warn("Leaking zones before L2Player.deleteMe(): " + before.toString());
			_log.warn("Leaking zones after L2Player.deleteMe(): " + after.toString());
		}
	}
	
	private HashSet<L2Zone> getZonesPlayerIn()
	{
		final HashSet<L2Zone> set = new HashSet<L2Zone>();
		
		for (L2Zone[] zones : ZoneManager.getInstance().getZones())
			if (zones != null)
				for (L2Zone zone : zones)
					if (zone.getCharactersInside().contains(this))
						set.add(zone);
		
		return set;
	}
	
	public boolean canLogout()
	{
		return canLogout(false);
	}
	
	public boolean canLogout(boolean restart)
	{
		if (!isGM() || !Config.GM_RESTART_FIGHTING)
		{
			if (AttackStanceTaskManager.getInstance().getAttackStanceTask(this))
			{
				if (restart)
					sendPacket(SystemMessageId.CANT_RESTART_WHILE_FIGHTING);
				else
					sendPacket(SystemMessageId.CANT_LOGOUT_WHILE_FIGHTING);
				return false;
			}
		}
		
		if (isFlying())
		{
			sendPacket(SystemMessageId.CANNOT_DO_WHILE_MOUNTED);
			return false;
		}
		
		L2Summon summon = getPet();
		
		if (summon != null && summon instanceof L2PetInstance && !summon.isBetrayed() && summon.isAttackingNow())
		{
			sendPacket(SystemMessageId.PET_CANNOT_SENT_BACK_DURING_BATTLE);
			return false;
		}
		
		if (isInFunEvent())
		{
			sendMessage("A superior power doesn't allow you to leave the event.");
			return false;
		}
		
		// Prevent player from restarting if they are a festival participant
		if (isFestivalParticipant() /* && SevenSignsFestival.getInstance().isFestivalInitialized()*/)
		{
			sendMessage("You can't logout while you are a participant in a festival.");
			return false;
		}
		
		if (getPrivateStoreType() != 0)
		{
			sendMessage("You can't logout while trading.");
			return false;
		}
		
		if (getActiveEnchantItem() != null || getActiveEnchantAttrItem() != null)
			return false;
		
		if (isLocked())
		{
			sendMessage("You can't logout while changing class.");
			return false;
		}
		
		if (!isGM())
		{
			if (isInsideZone(L2Zone.FLAG_NOESCAPE))
			{
				sendPacket(SystemMessageId.NO_LOGOUT_HERE);
				teleToLocation(TeleportWhereType.Town);
			}
		}
		
		return true;
	}
	
	private FishData _fish;
	
	public void startFishing(int x, int y, int z)
	{
		_fishx = x;
		_fishy = y;
		_fishz = z;
		
		stopMove(null);
		setIsImmobilized(true);
		_fishing = true;
		broadcastUserInfo();
		// Start Fishing
		int lvl = getRandomFishLvl();
		int group = getRandomGroup();
		int type = getRandomFishType(group);
		List<FishData> fishs = FishTable.getInstance().getFish(lvl, type, group);
		if (fishs == null || fishs.size() == 0)
		{
			endFishing(false);
			return;
		}
		int check = Rnd.get(fishs.size());
		_fish = fishs.get(check);
		fishs.clear();
		fishs = null;
		sendPacket(SystemMessageId.CAST_LINE_AND_START_FISHING);
		ExFishingStart efs = null;
		efs = new ExFishingStart(this, _fish.getType(), x, y, z, _lure.isNightLure());
		broadcastPacket(efs);
		startLookingForFishTask();
	}
	
	public void stopLookingForFishTask()
	{
		if (_taskforfish != null)
		{
			_taskforfish.cancel(false);
			_taskforfish = null;
		}
	}
	
	public void startLookingForFishTask()
	{
		if (!isDead() && _taskforfish == null)
		{
			int checkDelay = 0;
			boolean isNoob = false;
			boolean isUpperGrade = false;
			
			if (_lure != null)
			{
				int lureid = _lure.getItemId();
				isNoob = _fish.getGroup() == 0;
				isUpperGrade = _fish.getGroup() == 2;
				if (lureid == 6519 || lureid == 6522 || lureid == 6525 || lureid == 8505 || lureid == 8508
						|| lureid == 8511) // Low Grade
					checkDelay = Math.round((float)(_fish.getGutsCheckTime() * (1.33)));
				else if (lureid == 6520 || lureid == 6523 || lureid == 6526 || (lureid >= 8505 && lureid <= 8513)
						|| (lureid >= 7610 && lureid <= 7613) || (lureid >= 7807 && lureid <= 7809)
						|| (lureid >= 8484 && lureid <= 8486)) // Medium Grade, Beginner, Prize-Winning & Quest Special Bait
					checkDelay = Math.round((float)(_fish.getGutsCheckTime() * (1.00)));
				else if (lureid == 6521 || lureid == 6524 || lureid == 6527 || lureid == 8507 || lureid == 8510
						|| lureid == 8513) // High grade
					checkDelay = Math.round((float)(_fish.getGutsCheckTime() * (0.66)));
			}
			_taskforfish =
					ThreadPoolManager.getInstance().scheduleEffectAtFixedRate(
							new LookingForFishTask(_fish.getWaitTime(), _fish.getFishGuts(), _fish.getType(), isNoob,
									isUpperGrade), 10000, checkDelay);
		}
	}
	
	private int getRandomGroup()
	{
		switch (_lure.getItemId())
		{
			case 7807: // Green for Beginners
			case 7808: // Purple for Beginners
			case 7809: // Yellow for Beginners
			case 8486: // Prize-Winning for Beginners
				return 0;
			case 8485: // Prize-Winning Luminous
			case 8506: // Green Luminous
			case 8509: // Purple Luminous
			case 8512: // Yellow Luminous
				return 2;
			default:
				return 1;
		}
	}
	
	private int getRandomFishType(int group)
	{
		int check = Rnd.get(100);
		int type = 1;
		switch (group)
		{
			case 0: // Fish for Novices
				switch (_lure.getItemId())
				{
					case 7807: // Green Lure, preferred by fast-moving (nimble) fish (type 5)
						if (check <= 54)
							type = 5;
						else if (check <= 77)
							type = 4;
						else
							type = 6;
						break;
					case 7808: // Purple Lure, preferred by fat fish (type 4)
						if (check <= 54)
							type = 4;
						else if (check <= 77)
							type = 6;
						else
							type = 5;
						break;
					case 7809: // Yellow Lure, preferred by ugly fish (type 6)
						if (check <= 54)
							type = 6;
						else if (check <= 77)
							type = 5;
						else
							type = 4;
						break;
					case 8486: // Prize-Winning Fishing Lure for Beginners
						if (check <= 33)
							type = 4;
						else if (check <= 66)
							type = 5;
						else
							type = 6;
						break;
				}
				break;
			case 1: // Normal Fish
				switch (_lure.getItemId())
				{
					case 7610:
					case 7611:
					case 7612:
					case 7613:
						type = 3;
						break;
					case 6519: // All theese lures (green) are prefered by fast-moving (nimble) fish (type 1)
					case 8505:
					case 6520:
					case 6521:
					case 8507:
						if (check <= 54)
							type = 1;
						else if (check <= 74)
							type = 0;
						else if (check <= 94)
							type = 2;
						else
							type = 3;
						break;
					case 6522: // All theese lures (purple) are prefered by fat fish (type 0)
					case 8508:
					case 6523:
					case 6524:
					case 8510:
						if (check <= 54)
							type = 0;
						else if (check <= 74)
							type = 1;
						else if (check <= 94)
							type = 2;
						else
							type = 3;
						break;
					case 6525: // All theese lures (yellow) are prefered by ugly fish (type 2)
					case 8511:
					case 6526:
					case 6527:
					case 8513:
						if (check <= 55)
							type = 2;
						else if (check <= 74)
							type = 1;
						else if (check <= 94)
							type = 0;
						else
							type = 3;
						break;
					case 8484: // Prize-Winning Fishing Lure
						if (check <= 33)
							type = 0;
						else if (check <= 66)
							type = 1;
						else
							type = 2;
						break;
				}
				break;
			case 2: // Upper Grade Fish, Luminous Lure
				switch (_lure.getItemId())
				{
					case 8506: // Green Lure, preferred by fast-moving (nimble) fish (type 8)
						if (check <= 54)
							type = 8;
						else if (check <= 77)
							type = 7;
						else
							type = 9;
						break;
					case 8509: // Purple Lure, preferred by fat fish (type 7)
						if (check <= 54)
							type = 7;
						else if (check <= 77)
							type = 9;
						else
							type = 8;
						break;
					case 8512: // Yellow Lure, preferred by ugly fish (type 9)
						if (check <= 54)
							type = 9;
						else if (check <= 77)
							type = 8;
						else
							type = 7;
						break;
					case 8485: // Prize-Winning Fishing Lure
						if (check <= 33)
							type = 7;
						else if (check <= 66)
							type = 8;
						else
							type = 9;
						break;
				}
		}
		return type;
	}
	
	private int getRandomFishLvl()
	{
		L2Effect[] effects = getAllEffects();
		int skilllvl = getSkillLevel(1315);
		for (L2Effect e : effects)
		{
			if (e.getSkill().getId() == 2274)
				skilllvl = (int)e.getSkill().getPower();
		}
		if (skilllvl <= 0)
			return 1;
		int randomlvl;
		int check = Rnd.get(100);
		
		if (check <= 50)
		{
			randomlvl = skilllvl;
		}
		else if (check <= 85)
		{
			randomlvl = skilllvl - 1;
			if (randomlvl <= 0)
			{
				randomlvl = 1;
			}
		}
		else
		{
			randomlvl = skilllvl + 1;
			if (randomlvl > 27)
				randomlvl = 27;
		}
		
		return randomlvl;
	}
	
	public void startFishCombat(boolean isNoob, boolean isUpperGrade)
	{
		_fishCombat = new L2Fishing(this, _fish, isNoob, isUpperGrade);
	}
	
	public void endFishing(boolean win)
	{
		ExFishingEnd efe = new ExFishingEnd(win, this);
		broadcastPacket(efe);
		_fishing = false;
		_fishx = 0;
		_fishy = 0;
		_fishz = 0;
		broadcastUserInfo();
		if (_fishCombat == null)
			sendPacket(SystemMessageId.BAIT_LOST_FISH_GOT_AWAY);
		_fishCombat = null;
		
		_lure = null;
		// End Fishing
		sendPacket(SystemMessageId.REEL_LINE_AND_STOP_FISHING);
		setIsImmobilized(false);
		stopLookingForFishTask();
	}
	
	public L2Fishing getFishCombat()
	{
		return _fishCombat;
	}
	
	public int getFishx()
	{
		return _fishx;
	}
	
	public int getFishy()
	{
		return _fishy;
	}
	
	public int getFishz()
	{
		return _fishz;
	}
	
	public void setLure(L2ItemInstance lure)
	{
		_lure = lure;
	}
	
	public L2ItemInstance getLure()
	{
		return _lure;
	}
	
	public int getInventoryLimit()
	{
		int ivlim;
		if (isGM())
		{
			ivlim = Config.INVENTORY_MAXIMUM_GM;
		}
		else if (getRace() == Race.Dwarf)
		{
			ivlim = Config.INVENTORY_MAXIMUM_DWARF;
		}
		else
		{
			ivlim = Config.INVENTORY_MAXIMUM_NO_DWARF;
		}
		ivlim += (int)getStat().calcStat(Stats.INV_LIM, 0, null, null);
		
		return ivlim;
	}
	
	public int getWareHouseLimit()
	{
		int whlim;
		if (getRace() == Race.Dwarf)
		{
			whlim = Config.WAREHOUSE_SLOTS_DWARF;
		}
		else
		{
			whlim = Config.WAREHOUSE_SLOTS_NO_DWARF;
		}
		whlim += (int)getStat().calcStat(Stats.WH_LIM, 0, null, null);
		
		return whlim;
	}
	
	public int getPrivateSellStoreLimit()
	{
		int pslim;
		if (getRace() == Race.Dwarf)
		{
			pslim = Config.MAX_PVTSTORESELL_SLOTS_DWARF;
		}
		else
		{
			pslim = Config.MAX_PVTSTORESELL_SLOTS_OTHER;
		}
		pslim += (int)getStat().calcStat(Stats.P_SELL_LIM, 0, null, null);
		
		return pslim;
	}
	
	public int getPrivateBuyStoreLimit()
	{
		int pblim;
		if (getRace() == Race.Dwarf)
		{
			pblim = Config.MAX_PVTSTOREBUY_SLOTS_DWARF;
		}
		else
		{
			pblim = Config.MAX_PVTSTOREBUY_SLOTS_OTHER;
		}
		pblim += (int)getStat().calcStat(Stats.P_BUY_LIM, 0, null, null);
		
		return pblim;
	}
	
	public int getFreightLimit()
	{
		return Config.FREIGHT_SLOTS + (int)getStat().calcStat(Stats.FREIGHT_LIM, 0, null, null);
	}
	
	public int getDwarfRecipeLimit()
	{
		int recdlim = Config.ALT_DWARF_RECIPE_LIMIT;
		recdlim += (int)getStat().calcStat(Stats.REC_D_LIM, 0, null, null);
		return recdlim;
	}
	
	public int getCommonRecipeLimit()
	{
		int recclim = Config.ALT_COMMON_RECIPE_LIMIT;
		recclim += (int)getStat().calcStat(Stats.REC_C_LIM, 0, null, null);
		return recclim;
	}
	
	/**
	 * @return Returns the mountNpcId.
	 */
	public int getMountNpcId()
	{
		return _mountNpcId;
	}
	
	/**
	 * @return Returns the mountLevel.
	 */
	public int getMountLevel()
	{
		return _mountLevel;
	}
	
	public void setMountObjectID(int newID)
	{
		_mountObjectID = newID;
	}
	
	public int getMountObjectID()
	{
		return _mountObjectID;
	}
	
	private L2ItemInstance _lure = null;
	
	public SkillUsageRequest getCurrentPetSkill()
	{
		final L2Summon pet = getPet();
		
		return pet == null ? null : pet.getCurrentSkill();
	}
	
	public boolean isMaried()
	{
		return _maried;
	}
	
	public void setMaried(boolean state)
	{
		_maried = state;
	}
	
	public void setMaryRequest(boolean state)
	{
		_maryrequest = state;
	}
	
	public boolean isMary()
	{
		return _maryrequest;
	}
	
	public void setMaryAccepted(boolean state)
	{
		_maryaccepted = state;
	}
	
	public boolean isMaryAccepted()
	{
		return _maryaccepted;
	}
	
	public int getPartnerId()
	{
		return _partnerId;
	}
	
	public void setPartnerId(int partnerid)
	{
		_partnerId = partnerid;
	}
	
	public int getCoupleId()
	{
		return _coupleId;
	}
	
	public void setCoupleId(int coupleId)
	{
		_coupleId = coupleId;
	}
	
	public void setClientRevision(int clientrev)
	{
		_clientRevision = clientrev;
	}
	
	public int getClientRevision()
	{
		return _clientRevision;
	}
	
	public boolean isInJail()
	{
		return _inJail;
	}
	
	public void setInJail(boolean state)
	{
		//setInJail(state, 0);
		_inJail = state;
	}
	
	public void setInJail(boolean state, int delayInMinutes)
	{
		_inJail = state;
		// Remove the task if any
		stopJailTask(false);
		
		if (_inJail)
		{
			if (delayInMinutes > 0)
			{
				_jailTimer = delayInMinutes * 60000L; // In millisec
				
				// Start the countdown
				_jailTask = ThreadPoolManager.getInstance().scheduleGeneral(new JailTask(), _jailTimer);
				sendMessage("You are in jail for " + delayInMinutes + " minutes.");
			}
			
			// Open a Html message to inform the player
			NpcHtmlMessage htmlMsg = new NpcHtmlMessage(0);
			String jailInfos = HtmCache.getInstance().getHtm("data/html/jail_in.htm");
			if (jailInfos != null)
				htmlMsg.setHtml(jailInfos);
			else
				htmlMsg.setHtml("<html><body>You have been put in jail by an admin.</body></html>");
			sendPacket(htmlMsg);
			
			if (isFlyingMounted())
				untransform();
			setInstanceId(0);
			setIsIn7sDungeon(false);
			
			teleToLocation(L2JailZone.JAIL_LOCATION, false); // Jail
		}
		else
		{
			// Open a Html message to inform the player
			NpcHtmlMessage htmlMsg = new NpcHtmlMessage(0);
			String jailInfos = HtmCache.getInstance().getHtm("data/html/jail_out.htm");
			if (jailInfos != null)
				htmlMsg.setHtml(jailInfos);
			else
				htmlMsg.setHtml("<html><body>You are free for now, respect server rules!</body></html>");
			sendPacket(htmlMsg);
			
			teleToLocation(17836, 170178, -3507); // Floran
		}
		
		// Store in database
		storePlayerData();
		
		RegionBBSManager.changeCommunityBoard(this, PlayerStateOnCommunity.IN_JAIL);
	}
	
	public long getJailTimer()
	{
		if (_jailTask != null)
			return _jailTask.getDelay(TimeUnit.MILLISECONDS);
		
		return _jailTimer;
	}
	
	public void setJailTimer(long time)
	{
		_jailTimer = time;
	}
	
	private void updateJailState()
	{
		if (isInJail())
		{
			// If jail time is elapsed, free the player
			if (_jailTimer > 0)
			{
				// Restart the countdown
				_jailTask = ThreadPoolManager.getInstance().scheduleGeneral(new JailTask(), _jailTimer);
				sendMessage("You are still in jail for " + Math.round(_jailTimer / 60000) + " minutes.");
			}
			
			// If player escaped, put him back in jail
			if (!isInsideZone(L2Zone.FLAG_JAIL))
				teleToLocation(L2JailZone.JAIL_LOCATION, false);
		}
	}
	
	public void stopJailTask(boolean save)
	{
		if (_jailTask != null)
		{
			if (save)
			{
				long delay = _jailTask.getDelay(TimeUnit.MILLISECONDS);
				if (delay < 0)
					delay = 0;
				setJailTimer(delay);
			}
			_jailTask.cancel(false);
			_jailTask = null;
		}
	}
	
	/**
	 * Return True if the L2Player is a ViP.<BR><BR>
	 */
	public boolean isCharViP()
	{
		return _charViP;
	}
	
	/**
	 * Set the _charViP Flag of the L2Player.<BR><BR>
	 */
	public void setCharViP(boolean status)
	{
		_charViP = status;
	}
	
	private ScheduledFuture<?> _jailTask;
	private int _cursedWeaponEquippedId = 0;
	private boolean _combatFlagEquipped = false;
	
	private boolean _reviveRequested = false;
	private boolean _revivePetRequested = false;
	
	private double _cpUpdateIncCheck = .0;
	private double _cpUpdateDecCheck = .0;
	private double _cpUpdateInterval = .0;
	private double _mpUpdateIncCheck = .0;
	private double _mpUpdateDecCheck = .0;
	private double _mpUpdateInterval = .0;
	
	private class JailTask implements Runnable
	{
		@Override
		public void run()
		{
			setInJail(false, 0);
		}
	}
	
	public void restoreHPMP()
	{
		getStatus().setCurrentHpMp(getMaxHp(), getMaxMp());
	}
	
	public boolean isCursedWeaponEquipped()
	{
		return _cursedWeaponEquippedId != 0;
	}
	
	public void setCursedWeaponEquippedId(int value)
	{
		_cursedWeaponEquippedId = value;
		
		RegionBBSManager.changeCommunityBoard(this, PlayerStateOnCommunity.CURSED_WEAPON_OWNER);
	}
	
	public int getCursedWeaponEquippedId()
	{
		return _cursedWeaponEquippedId;
	}
	
	public boolean isCombatFlagEquipped()
	{
		return _combatFlagEquipped;
	}
	
	public void setCombatFlagEquipped(boolean value)
	{
		_combatFlagEquipped = value;
	}
	
	public boolean getCharmOfCourage()
	{
		return _charmOfCourage;
	}
	
	public void setCharmOfCourage(boolean val)
	{
		_charmOfCourage = val;
		sendEtcStatusUpdate();
	}
	
	/** Return True if the L2Creature is riding. */
	public final boolean isRidingStrider()
	{
		return _isRidingStrider;
	}
	
	public final boolean isRidingRedStrider()
	{
		return _isRidingRedStrider;
	}
	
	public final boolean isRidingHorse()
	{
		return _isRidingHorse;
	}
	
	/** Set the L2Creature riding mode to True. */
	public final void setIsRidingStrider(boolean mode)
	{
		_isRidingStrider = mode;
	}
	
	public final void setIsRidingRedStrider(boolean mode)
	{
		_isRidingRedStrider = mode;
	}
	
	public final void setIsRidingHorse(boolean mode)
	{
		_isRidingHorse = mode;
	}
	
	public int getCharges()
	{
		return _charges;
	}
	
	private static final int[] CHARGE_SKILLS = { 570, 8, 50 }; // Transformation skill is checked first
	
	public L2Skill getChargeSkill()
	{
		for (int id : L2Player.CHARGE_SKILLS)
		{
			L2Skill skill = getKnownSkill(id);
			if (skill != null && skill.getMaxCharges() > 0)
			{
				return skill;
			}
		}
		return null;
	}
	
	public void increaseCharges(int count, int max)
	{
		if (count <= 0) // Wrong usage
			return;
		
		// Checking charges maximum
		if (_charges >= max)
		{
			sendPacket(SystemMessageId.FORCE_MAXLEVEL_REACHED);
			return;
		}
		
		// Increase charges
		setCharges(Math.min(_charges + count, max));
		
		SystemMessage sm = new SystemMessage(SystemMessageId.FORCE_INCREASED_TO_S1);
		sm.addNumber(_charges);
		sendPacket(sm);
	}
	
	public void increaseChargesBySkill(L2Skill skill)
	{
		if (skill.getGiveCharges() > 0)
			increaseCharges(skill.getGiveCharges(), skill.getMaxCharges());
	}
	
	private void setCharges(int charges)
	{
		_charges = Math.max(0, charges);
		
		sendEtcStatusUpdate();
		if (_charges == 0)
			stopChargeTask();
		else
			restartChargeTask();
	}
	
	public void decreaseCharges(int count)
	{
		if (count < 0) // Wrong usage
			return;
		setCharges(_charges - count);
	}
	
	public class ChargeTask implements Runnable
	{
		@Override
		public void run()
		{
			clearCharges();
		}
	}
	
	/**
	 * Clear out all charges from this L2Player
	 */
	public void clearCharges()
	{
		setCharges(0);
	}
	
	/**
	 * Starts/Restarts the SoulTask to Clear Charges after 10 Mins.
	 */
	private void restartChargeTask()
	{
		if (_chargeTask != null)
		{
			_chargeTask.cancel(false);
			_chargeTask = null;
		}
		_chargeTask = ThreadPoolManager.getInstance().scheduleGeneral(new ChargeTask(), 600000);
	}
	
	/**
	 * Stops the Clearing Task.
	 */
	public void stopChargeTask()
	{
		if (_chargeTask != null)
		{
			_chargeTask.cancel(false);
			_chargeTask = null;
		}
	}
	
	/**
	 * Returns the Number of Souls this L2Player got.
	 *
	 * @return
	 */
	public int getSouls()
	{
		return _souls;
	}
	
	public int getLastSoulConsume()
	{
		return _lastSoulConsume;
	}
	
	public void resetLastSoulConsume()
	{
		_lastSoulConsume = 0;
	}
	
	/**
	 * Absorbs a Soul from a Npc.
	 *
	 * @param skill
	 * @param target
	 */
	public void absorbSoulFromNpc(L2Skill soulMastery, L2Creature target)
	{
		if (_souls >= soulMastery.getNumSouls())
		{
			sendPacket(SystemMessageId.SOUL_CANNOT_BE_INCREASED_ANYMORE);
			return;
		}
		
		increaseSouls(1);
		
		// Npc -> Player absorb animation
		if (target != null)
			broadcastPacket(new ExSpawnEmitter(getObjectId(), target.getObjectId()), 500);
	}
	
	/**
	 * Increase Souls
	 *
	 * @param count
	 */
	private void increaseSouls(int count) // By skill or mob kill
	{
		setSouls(_souls + count);
		
		SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_SOUL_HAS_INCREASED_BY_S1_SO_IT_IS_NOW_AT_S2);
		sm.addNumber(count);
		sm.addNumber(_souls);
		sendPacket(sm);
	}
	
	public void increaseSoulsBySkill(L2Skill skill)
	{
		if (skill.getNumSouls() == 0)
			return;
		
		final L2Skill soulmastery = getKnownSkill(L2Skill.SKILL_SOUL_MASTERY);
		if (soulmastery == null)
			return;
		
		if (_souls >= soulmastery.getNumSouls())
		{
			sendPacket(SystemMessageId.SOUL_CANNOT_BE_INCREASED_ANYMORE);
			return;
		}
		
		increaseSouls(Math.min(skill.getNumSouls(), soulmastery.getNumSouls() - getSouls()));
	}
	
	/**
	 * Decreases existing Souls.
	 *
	 * @param skill
	 */
	public void decreaseSouls(L2Skill skill)
	{
		if (_souls == 0)
			return;
		
		// Calculation part
		int souls = _souls;
		if (skill.getSoulConsumeCount() > 0)
		{
			souls -= skill.getSoulConsumeCount();
		}
		else if (skill.getMaxSoulConsumeCount() > 0)
		{
			int consume = Math.min(_souls, skill.getMaxSoulConsumeCount());
			souls -= consume;
			_lastSoulConsume = consume; // Store for PDAM/MDAM boosting
		}
		
		setSouls(souls);
	}
	
	public void setSouls(int count)
	{
		_souls = L2Math.limit(0, count, 45); // Client can't display more
		
		if (_souls > 0)
			restartSoulTask();
		else
			stopSoulTask();
		
		sendEtcStatusUpdate();
	}
	
	private class SoulTask implements Runnable
	{
		@Override
		public void run()
		{
			clearSouls();
		}
	}
	
	/**
	 * Clear out all Souls from this L2Player
	 */
	public void clearSouls()
	{
		setSouls(0);
	}
	
	/**
	 * Starts/Restarts the SoulTask to Clear Souls after 10 Mins.
	 */
	private void restartSoulTask()
	{
		if (_soulTask != null)
		{
			_soulTask.cancel(false);
			_soulTask = null;
		}
		_soulTask = ThreadPoolManager.getInstance().scheduleGeneral(new SoulTask(), 600000);
	}
	
	/**
	 * Stops the Clearing Task.
	 */
	public void stopSoulTask()
	{
		if (_soulTask != null)
		{
			_soulTask.cancel(false);
			_soulTask = null;
		}
	}
	
	public void startFameTask(long delay, int fameFixRate)
	{
		if (getLevel() < 40 || getClassId().level() < 2)
			return;
		if (_fameTask == null)
			_fameTask =
					ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new FameTask(fameFixRate), delay, delay);
	}
	
	public void stopFameTask()
	{
		if (_fameTask != null)
		{
			_fameTask.cancel(false);
			_fameTask = null;
		}
	}
	
	public class FameTask implements Runnable
	{
		protected int _value;
		
		protected FameTask(int value)
		{
			_value = value;
		}
		
		@Override
		public void run()
		{
			if (isDead() && !Config.ALT_FAME_FOR_DEAD_PLAYERS)
				return;
			
			setFame(getFame() + _value);
			SystemMessage sm = new SystemMessage(SystemMessageId.ACQUIRED_S1_REPUTATION_SCORE);
			sm.addNumber(_value);
			sendPacket(sm);
			sendPacket(new UserInfo(L2Player.this));
		}
	}
	
	private L2Effect _shortBuff;
	
	public void startShortBuffStatusUpdate(L2Effect effect)
	{
		if (ShortBuffStatusUpdate.getPriority(_shortBuff) > ShortBuffStatusUpdate.getPriority(effect))
			return;
		
		_shortBuff = effect;
		
		sendPacket(new ShortBuffStatusUpdate(effect.getId(), effect.getLevel(), (int)effect.getRemainingTaskTime()));
	}
	
	public void stopShortBuffStatusUpdate(L2Effect effect)
	{
		if (_shortBuff != effect)
			return;
		
		_shortBuff = null;
		
		sendPacket(new ShortBuffStatusUpdate(0, 0, 0));
	}
	
	public int getDeathPenaltyBuffLevel()
	{
		return _deathPenaltyBuffLevel;
	}
	
	public void setDeathPenaltyBuffLevel(int level)
	{
		_deathPenaltyBuffLevel = level;
	}
	
	public void calculateDeathPenaltyBuffLevel(L2Creature killer)
	{
		if (Config.DEATH_PENALTY_CHANCE < 1)
			return;
		
		if (!(killer instanceof L2Playable) && !isGM() && !(getCharmOfLuck() && killer.isRaid()) && !isPhoenixBlessed()
				&& !isInFunEvent() && (getKarma() > 0 || Rnd.get(100) < Config.DEATH_PENALTY_CHANCE))
			increaseDeathPenaltyBuffLevel();
	}
	
	public void increaseDeathPenaltyBuffLevel()
	{
		if (getDeathPenaltyBuffLevel() >= 15) // Maximum level reached
			return;
		
		if (getDeathPenaltyBuffLevel() != 0)
		{
			L2Skill skill = SkillTable.getInstance().getInfo(5076, getDeathPenaltyBuffLevel());
			
			if (skill != null)
				removeSkill(skill, true);
		}
		
		_deathPenaltyBuffLevel++;
		
		addSkill(SkillTable.getInstance().getInfo(5076, getDeathPenaltyBuffLevel()), false);
		sendEtcStatusUpdate();
		SystemMessage sm = new SystemMessage(SystemMessageId.DEATH_PENALTY_LEVEL_S1_ADDED);
		sm.addNumber(getDeathPenaltyBuffLevel());
		sendPacket(sm);
	}
	
	public void reduceDeathPenaltyBuffLevel()
	{
		if (getDeathPenaltyBuffLevel() <= 0)
			return;
		
		L2Skill skill = SkillTable.getInstance().getInfo(5076, getDeathPenaltyBuffLevel());
		
		if (skill != null)
			removeSkill(skill, true);
		
		_deathPenaltyBuffLevel--;
		
		if (getDeathPenaltyBuffLevel() > 0)
		{
			addSkill(SkillTable.getInstance().getInfo(5076, getDeathPenaltyBuffLevel()), false);
			SystemMessage sm = new SystemMessage(SystemMessageId.DEATH_PENALTY_LEVEL_S1_ADDED);
			sm.addNumber(getDeathPenaltyBuffLevel());
			sendPacket(sm);
		}
		else
		{
			sendPacket(SystemMessageId.DEATH_PENALTY_LIFTED);
		}
		
		sendEtcStatusUpdate();
	}
	
	public void restoreDeathPenaltyBuffLevel()
	{
		L2Skill skill = SkillTable.getInstance().getInfo(5076, getDeathPenaltyBuffLevel());
		
		if (skill != null)
			removeSkill(skill, true);
		
		if (getDeathPenaltyBuffLevel() > 0)
		{
			addSkill(SkillTable.getInstance().getInfo(5076, getDeathPenaltyBuffLevel()), false);
		}
	}
	
	private boolean _canFeed;
	
	private final Map<Integer, TimeStamp> _reuseTimeStamps = new ConcurrentHashMap<Integer, TimeStamp>();
	
	public Map<Integer, TimeStamp> getReuseTimeStamps()
	{
		return _reuseTimeStamps;
	}
	
	public void sendSkillCoolTime()
	{
		addPacketBroadcastMask(BroadcastMode.SEND_SKILL_COOL_TIME);
	}
	
	public void sendSkillCoolTimeImpl()
	{
		sendPacket(SkillCoolTime.STATIC_PACKET);
	}
	
	/**
	 * Simple class containing all neccessary information to maintain
	 * valid timestamps and reuse for skills upon relog. Filter this
	 * carefully as it becomes redundant to store reuse for small delays.
	 * @author  Yesod
	 */
	public static final class TimeStamp
	{
		private final int _skillId;
		private final int _reuseDelay;
		private final long _expiration;
		
		public TimeStamp(int skillId, int reuseDelay, int remaining)
		{
			_skillId = skillId;
			_reuseDelay = reuseDelay;
			_expiration = System.currentTimeMillis() + remaining;
		}
		
		public long getExpiration()
		{
			return _expiration;
		}
		
		public int getSkillId()
		{
			return _skillId;
		}
		
		public int getReuseDelay()
		{
			return _reuseDelay;
		}
		
		public int getRemaining()
		{
			return L2Math.limit(0, _expiration - System.currentTimeMillis(), Integer.MAX_VALUE);
		}
	}
	
	public void disableSkill(TimeStamp ts)
	{
		disableSkill(ts.getSkillId(), ts.getReuseDelay(), ts.getRemaining());
	}
	
	public boolean disableSkill(int skillId, int delay, int remaining)
	{
		if (!super.disableSkill(skillId, remaining))
			return false;
		
		//if (remaining < 10000)
		//	return true;
		
		final TimeStamp ts = getReuseTimeStamps().put(skillId, new TimeStamp(skillId, delay, remaining));
		final SkillUsageRequest request = getCurrentSkill();
		
		if (ts == null || Math.abs(ts.getReuseDelay() - delay) > 500 || Math.abs(ts.getRemaining() - remaining) > 500)
			if (request == null || request.getSkillId() != skillId)
				sendSkillCoolTime();
		return true;
	}
	
	@Override
	public boolean disableSkill(int skillId, int delay)
	{
		return disableSkill(skillId, delay, delay);
	}
	
	@Override
	public void enableSkill(int skillId)
	{
		super.enableSkill(skillId);
		
		final TimeStamp ts = getReuseTimeStamps().remove(skillId);
		
		if (ts != null && ts.getRemaining() > 500)
			sendSkillCoolTime();
	}
	
	public boolean isKamaelic()
	{
		return getRace() == Race.Kamael;
	}
	
	public boolean canOpenPrivateStore()
	{
		return !isAlikeDead() && !isInOlympiadMode() && !isMounted();
	}
	
	public void tryOpenPrivateBuyStore()
	{
		// Player shouldn't be able to set stores if he/she is alike dead (dead or fake death)
		if (canOpenPrivateStore())
		{
			if (getPrivateStoreType() == L2Player.STORE_PRIVATE_BUY
					|| getPrivateStoreType() == L2Player.STORE_PRIVATE_BUY + 1)
			{
				setPrivateStoreType(L2Player.STORE_PRIVATE_NONE);
			}
			else if (isInsideZone(L2Zone.FLAG_NOSTORE))
			{
				sendPacket(SystemMessageId.NO_PRIVATE_STORE_HERE);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			if (getPrivateStoreType() == L2Player.STORE_PRIVATE_NONE)
			{
				if (isSitting())
				{
					this.standUp();
				}
				setPrivateStoreType(L2Player.STORE_PRIVATE_BUY + 1);
				this.sendPacket(new PrivateStoreManageListBuy(this));
			}
		}
		else
		{
			sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	public void tryOpenPrivateSellStore(boolean isPackageSale)
	{
		// Player shouldn't be able to set stores if he/she is alike dead (dead or fake death)
		if (canOpenPrivateStore())
		{
			if (getPrivateStoreType() == L2Player.STORE_PRIVATE_SELL
					|| getPrivateStoreType() == L2Player.STORE_PRIVATE_SELL + 1
					|| getPrivateStoreType() == L2Player.STORE_PRIVATE_PACKAGE_SELL)
			{
				setPrivateStoreType(L2Player.STORE_PRIVATE_NONE);
			}
			else if (isInsideZone(L2Zone.FLAG_NOSTORE))
			{
				sendPacket(SystemMessageId.NO_PRIVATE_STORE_HERE);
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (getPrivateStoreType() == L2Player.STORE_PRIVATE_NONE)
			{
				if (isSitting())
				{
					this.standUp();
				}
				setPrivateStoreType(L2Player.STORE_PRIVATE_SELL + 1);
				this.sendPacket(new PrivateStoreManageListSell(this, isPackageSale));
			}
		}
		else
		{
			sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	public boolean isTransformed()
	{
		return _transformation != null && !_transformation.isStance();
	}
	
	public boolean isInStance()
	{
		return _transformation != null && _transformation.isStance();
	}
	
	public void transform(L2Transformation transformation)
	{
		if (_transformation != null)
		{
			// You already polymorphed and cannot polymorph again.
			sendPacket(SystemMessageId.YOU_ALREADY_POLYMORPHED_AND_CANNOT_POLYMORPH_AGAIN);
			return;
		}
		if (isMounted())
		{
			// Get off the strider or something else if character is mounted
			dismount();
		}
		if (getPet() != null)
		{
			// Unsummon pets
			getPet().unSummon(this);
		}
		_transformation = transformation;
		for (L2Effect e : getAllEffects())
		{
			if (e != null && e.getSkill().isToggle())
				e.exit();
		}
		transformation.onTransform(this);
		sendSkillList();
		sendSkillCoolTime();
		ExBasicActionList.sendTo(this);
		broadcastUserInfo();
	}
	
	public void untransform()
	{
		if (_transformation != null)
		{
			_transformAllowedSkills.clear();
			_transformation.onUntransform(this);
			_transformation = null;
			stopEffects(L2EffectType.TRANSFORMATION);
			broadcastUserInfo();
			ExBasicActionList.sendTo(this);
			sendSkillList();
			sendSkillCoolTime();
		}
	}
	
	public L2Transformation getTransformation()
	{
		return _transformation;
	}
	
	/**
	 * This returns the transformation Id of the current transformation.
	 * For example, if a player is transformed as a Buffalo, and then picks up the Zariche,
	 * the transform Id returned will be that of the Zariche, and NOT the Buffalo.
	 * @return Transformation Id
	 */
	public int getTransformationId()
	{
		L2Transformation transformation = getTransformation();
		if (transformation == null)
			return 0;
		return transformation.getId();
	}
	
	/**
	 * This returns the transformation Id stored inside the character table, selected by the method: transformSelectInfo()
	 * For example, if a player is transformed as a Buffalo, and then picks up the Zariche,
	 * the transform Id returned will be that of the Buffalo, and NOT the Zariche.
	 * @return Transformation Id
	 */
	public int transformId()
	{
		return _transformationId;
	}
	
	/**
	 * This is a simple query that inserts the transform Id into the character table for future reference.
	 */
	public void transformInsertInfo()
	{
		
		if (getTransformationId() == L2Transformation.TRANSFORM_AKAMANAH
				|| getTransformationId() == L2Transformation.TRANSFORM_ZARICHE)
			return;
		
		_transformationId = getTransformationId();
		
		try
		{
			PlayerRepository.getInstance().saveTransformation(getObjectId(), _transformationId);
		}
		catch (Exception e)
		{
			_log.error("Transformation insert info: " + e, e);
		}
	}
	
	/**
	 * This selects the current
	 * @return transformation Id
	 */
	public int transformSelectInfo()
	{
		try
		{
			_transformationId = PlayerRepository.getInstance().loadTransformation(getObjectId());
		}
		catch (Exception e)
		{
			_log.error("Transformation select info error:" + e.getMessage(), e);
		}
		
		return _transformationId;
	}
	
	/**
	 *
	 * @param npcId
	 */
	public void setAgathionId(int npcId)
	{
		_agathionId = npcId;
	}
	
	/**
	 *
	 * @return
	 */
	public int getAgathionId()
	{
		return _agathionId;
	}
	
	private void startVitalityTask()
	{
		if (Config.ENABLE_VITALITY && _vitalityTask == null)
		{
			_vitalityTask =
					ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new VitalityTask(this), 1000, 60000);
		}
	}
	
	private void stopVitalityTask()
	{
		if (_vitalityTask != null)
		{
			_vitalityTask.cancel(true);
			_vitalityTask = null;
		}
	}
	
	public int getVitalityPoints()
	{
		return getStat().getVitalityPoints();
	}
	
	public void setVitalityPoints(int points, boolean quiet)
	{
		getStat().setVitalityPoints(points, quiet);
	}
	
	public synchronized void updateVitalityPoints(float points, boolean useRates, boolean quiet)
	{
		getStat().updateVitalityPoints(points, useRates, quiet);
	}
	
	public L2StaticObjectInstance getObjectSittingOn()
	{
		return _throne;
	}
	
	public void setObjectSittingOn(L2StaticObjectInstance throne)
	{
		//prevent misuse
		if (throne == null)
			resetThrone();
		else
			_throne = throne;
	}
	
	public void resetThrone()
	{
		if (_throne == null)
			return;
		_throne.setOccupier(null);
		_throne = null;
	}
	
	public int getOlympiadOpponentId()
	{
		return _olympiadOpponentId;
	}
	
	public void setOlympiadOpponentId(int value)
	{
		_olympiadOpponentId = value;
	}
	
	private ImmutableReference<L2Player> _immutableReference;
	private ClearableReference<L2Player> _clearableReference;
	
	public ImmutableReference<L2Player> getImmutableReference()
	{
		if (_immutableReference == null)
			_immutableReference = new ImmutableReference<L2Player>(this);
		
		return _immutableReference;
	}
	
	public ClearableReference<L2Player> getClearableReference()
	{
		if (_clearableReference == null)
			_clearableReference = new ClearableReference<L2Player>(this);
		
		return _clearableReference;
	}
	
	@Override
	public final L2Player getActingPlayer()
	{
		return this;
	}
	
	@Override
	public final L2Summon getActingSummon()
	{
		return getPet();
	}
	
	/**
	 * Set the Fame of this L2PcInstane <BR><BR>
	 * @param fame
	 */
	public synchronized void setFame(int fame)
	{
		if (fame > Config.MAX_PERSONAL_FAME_POINTS)
			_fame = Config.MAX_PERSONAL_FAME_POINTS;
		else
			_fame = fame;
	}
	
	/**
	 * Return the Fame of this L2Player <BR><BR>
	 * @return
	 */
	public synchronized int getFame()
	{
		return _fame;
	}
	
	private ScheduledFuture<?> _autoSaveTask;
	
	private void startAutoSaveTask()
	{
		if (_autoSaveTask == null && Config.CHAR_STORE_INTERVAL > 0)
			_autoSaveTask = ThreadPoolManager.getInstance().schedule(new AutoSave(), 300000);
	}
	
	private void stopAutoSaveTask()
	{
		if (_autoSaveTask != null)
		{
			_autoSaveTask.cancel(false);
			_autoSaveTask = null;
		}
	}
	
	private final class AutoSave implements Runnable
	{
		@Override
		public void run()
		{
			long period = Config.CHAR_STORE_INTERVAL * 60000L;
			long delay = _lastStore + period - System.currentTimeMillis();
			
			if (delay <= 0)
			{
				try
				{
					store();
				}
				catch (RuntimeException e)
				{
					_log.error("", e);
				}
				
				delay = period;
			}
			
			if (Config.CHAR_STORE_INTERVAL > 0)
				_autoSaveTask = ThreadPoolManager.getInstance().schedule(this, delay);
		}
	}
	
	public void checkItemRestriction()
	{
		for (int i = 0; i < Inventory.PAPERDOLL_TOTALSLOTS; i++)
		{
			L2ItemInstance equippedItem = getInventory().getPaperdollItem(i);
			if (equippedItem != null && !equippedItem.getItem().checkCondition(this, false))
			{
				getInventory().unEquipItemInSlotAndRecord(i);
				if (equippedItem.isWear())
					continue;
				
				SystemMessage sm = null;
				if (equippedItem.getItem().getBodyPart() == L2Item.SLOT_BACK)
					sm = SystemMessageId.CLOAK_REMOVED_BECAUSE_ARMOR_SET_REMOVED.getSystemMessage();
				else if (equippedItem.getEnchantLevel() > 0)
				{
					sm = new SystemMessage(SystemMessageId.EQUIPMENT_S1_S2_REMOVED);
					sm.addNumber(equippedItem.getEnchantLevel());
					sm.addItemName(equippedItem);
				}
				else
				{
					sm = new SystemMessage(SystemMessageId.S1_DISARMED);
					sm.addItemName(equippedItem);
				}
				sendPacket(sm);
			}
		}
	}
	
	@Override
	public void setName(String name)
	{
		super.setName(name);
		
		CharNameTable.getInstance().update(getObjectId(), getAccountName(), getName());
	}
	
	// =========================================================================================
	// Condition listeners
	// TODO: wrapper conditions - ConditionLogicAnd, ConditionLogicOr, etc
	// TODO: make it more similar to conventional java listeners
	// TODO: add every listener which makes sense - currently listens for hp percent and game time only
	
	public enum ConditionListenerDependency
	{
		PLAYER_HP,
		GAME_TIME, ;
	}
	
	private abstract class ConditionListener
	{
		private final Map<Func, Boolean> _values = new LazyFastMap<Func, Boolean>().setShared();
		private final Env _env;
		
		protected ConditionListener()
		{
			_env = new Env();
			_env.player = L2Player.this;
		}
		
		protected void refresh(ConditionListenerDependency dependency)
		{
			for (Entry<Func, Boolean> entry : _values.entrySet())
			{
				boolean newValue = entry.getKey().isAllowed(_env);
				boolean oldValue = entry.setValue(newValue);
				
				if (newValue != oldValue)
					onChange(entry.getKey(), newValue);
			}
		}
		
		protected void onChange(Func f, boolean newValue)
		{
			sendMessage(f.funcOwner.getFuncOwnerName() + (newValue ? " activated." : " deactivated."));
		}
		
		protected void onFuncAddition(Func f)
		{
			final boolean newValue = f.isAllowed(_env);
			
			_values.put(f, newValue);
			
			if (newValue)
				onChange(f, true);
		}
		
		protected void onFuncRemoval(Func f)
		{
			_values.remove(f);
		}
	}
	
	private final class ConditionPlayerHpListener extends ConditionListener
	{
		@Override
		protected void onFuncAddition(Func f)
		{
			if (f.condition instanceof ConditionPlayerHp)
				super.onFuncAddition(f);
		}
		
		@Override
		protected void refresh(ConditionListenerDependency dependency)
		{
			if (dependency == ConditionListenerDependency.PLAYER_HP)
				super.refresh(dependency);
		}
		
		@Override
		protected void onChange(Func f, boolean newValue)
		{
			final SystemMessage sm;
			
			if (newValue)
				sm = new SystemMessage(SystemMessageId.S1_HP_DECREASED_EFFECT_APPLIES);
			else
				sm = new SystemMessage(SystemMessageId.S1_HP_DECREASED_EFFECT_DISAPPEARS);
			
			if (f.funcOwner.getFuncOwnerSkill() != null)
				sm.addSkillName(f.funcOwner.getFuncOwnerSkill());
			else
				sm.addString(f.funcOwner.getFuncOwnerName());
			
			sendPacket(sm);
			
			broadcastUserInfo();
		}
	}
	
	private final class ConditionGameTimeListener extends ConditionListener
	{
		@Override
		protected void onFuncAddition(Func f)
		{
			if (f.condition instanceof ConditionGameTime)
				super.onFuncAddition(f);
		}
		
		@Override
		protected void refresh(ConditionListenerDependency dependency)
		{
			if (dependency == ConditionListenerDependency.GAME_TIME)
				super.refresh(dependency);
		}
		
		@Override
		protected void onChange(Func f, boolean newValue)
		{
			final SystemMessage sm;
			
			if (newValue)
				sm = new SystemMessage(SystemMessageId.S1_NIGHT_EFFECT_APPLIES);
			else
				sm = new SystemMessage(SystemMessageId.S1_NIGHT_EFFECT_DISAPPEARS);
			
			if (f.funcOwner.getFuncOwnerSkill() != null)
				sm.addSkillName(f.funcOwner.getFuncOwnerSkill());
			else
				sm.addString(f.funcOwner.getFuncOwnerName());
			
			sendPacket(sm);
			
			broadcastUserInfo();
		}
	}
	
	private ConditionListener[] _conditionListeners;
	
	private ConditionListener[] getConditionListeners()
	{
		if (_conditionListeners == null)
			_conditionListeners =
					new ConditionListener[] { new ConditionPlayerHpListener(), new ConditionGameTimeListener() };
		
		return _conditionListeners;
	}
	
	public void onFuncAddition(Func f)
	{
		for (ConditionListener listener : getConditionListeners())
			listener.onFuncAddition(f);
	}
	
	public void onFuncRemoval(Func f)
	{
		for (ConditionListener listener : getConditionListeners())
			listener.onFuncRemoval(f);
	}
	
	public void refreshConditionListeners(ConditionListenerDependency dependency)
	{
		for (ConditionListener listener : getConditionListeners())
			listener.refresh(dependency);
	}
	
	// =========================================================================================
	
	public void sendEtcStatusUpdate()
	{
		addPacketBroadcastMask(BroadcastMode.SEND_ETC_STATUS_UPDATE);
	}
	
	public void sendEtcStatusUpdateImpl()
	{
		sendPacket(EtcStatusUpdate.STATIC_PACKET);
	}
	
	public void broadcastRelationChanged()
	{
		addPacketBroadcastMask(BroadcastMode.BROADCAST_RELATION_CHANGED);
	}
	
	public void broadcastRelationChangedImpl()
	{
		if (!getKnownList().getKnownPlayers().isEmpty())
			for (L2Player player : getKnownList().getKnownPlayers().values())
				RelationChanged.sendRelationChanged(this, player);
	}
	
	@Override
	public void sendInfo(L2Player activeChar)
	{
		if (isInBoat())
			getPosition().setWorldPosition(getBoat().getPosition());
		else if (isInAirShip())
			getPosition().setWorldPosition(getAirShip().getPosition());
		
		if (getPoly().isMorphed())
		{
			activeChar.sendPacket(new AbstractNpcInfo.PlayerMorphInfo(this, getPoly().getNpcTemplate()));
		}
		else
		{
			activeChar.sendPacket(new CharInfo(this));
			//activeChar.sendPacket(new ExBrExtraUserInfo(this));
		}
		
		if (isInBoat())
			activeChar.sendPacket(new GetOnVehicle(this, getBoat(), getInBoatPosition().getX(), getInBoatPosition()
					.getY(), getInBoatPosition().getZ()));
		else if (isInAirShip())
			activeChar.sendPacket(new ExGetOnAirShip(this, getAirShip()));
		
		if (getMountType() == 4)
		{
			// TODO: Remove when horse mounts fixed
			activeChar.sendPacket(new Ride(this, false, 0));
			activeChar.sendPacket(new Ride(this, true, getMountNpcId()));
		}
		switch (getPrivateStoreType())
		{
			case L2Player.STORE_PRIVATE_SELL:
			{
				activeChar.sendPacket(new PrivateStoreMsgSell(this));
				break;
			}
			case L2Player.STORE_PRIVATE_PACKAGE_SELL:
			{
				activeChar.sendPacket(new ExPrivateStoreSetWholeMsg(this));
				break;
			}
			case L2Player.STORE_PRIVATE_BUY:
			{
				activeChar.sendPacket(new PrivateStoreMsgBuy(this));
				break;
			}
			case L2Player.STORE_PRIVATE_MANUFACTURE:
			{
				activeChar.sendPacket(new RecipeShopMsg(this));
				break;
			}
		}
	}
	
	@Override
	public void broadcastFullInfoImpl()
	{
		sendPacket(new UserInfo(this));
		if (getPoly().isMorphed())
		{
			Broadcast.toKnownPlayers(this, new AbstractNpcInfo.PlayerMorphInfo(this, getPoly().getNpcTemplate()));
		}
		else
		{
			Broadcast.toKnownPlayers(this, new CharInfo(this));
		}
	}
	
	@Override
	protected boolean shouldAddPacketBroadcastMask()
	{
		return getOnlineState() != L2Player.ONLINE_STATE_LOADED;
	}
	
	/**
	 * @return afro haircut id
	 */
	public int getAfroHaircutId()
	{
		return _afroId;
	}
	
	public void setAfroHaircutId(int id)
	{
		_afroId = id;
		broadcastUserInfo();
	}
	
	public void setIsInSiege(boolean b)
	{
		_isInSiege = b;
	}
	
	public boolean isInSiege()
	{
		return _isInSiege;
	}
	
	@Override
	protected CreatureShots initShots()
	{
		return new PlayerShots(this);
	}
	
	@Override
	public PlayerShots getShots()
	{
		return (PlayerShots)_shots;
	}
	
	public boolean isTryingToSitOrStandup()
	{
		return ((_lastSitStandRequest + 2333) > System.currentTimeMillis());
	}
	
	public static enum TeleportMode
	{
		SCROLL_OF_ESCAPE,
		RECALL,
		UNSTUCK,
		GOTOLOVE,
	}
	
	public boolean canTeleport(TeleportMode mode)
	{
		return canTeleport(mode, false);
	}
	
	public boolean canTeleport(TeleportMode mode, boolean bySkill)
	{
		if (mode != TeleportMode.RECALL && !bySkill)
		{
			if (isCastingNow() || isMuted() || isAlikeDead() || isMovementDisabled() || isAllSkillsDisabled())
				return false;
		}
		
		if (mode == TeleportMode.SCROLL_OF_ESCAPE)
		{
			if (isSitting())
			{
				sendPacket(SystemMessageId.CANT_MOVE_SITTING);
				return false;
			}
		}
		
		if (!GlobalRestrictions.canTeleport(this))
			return false;
		
		// [L2J_JP ADD]
		if (isInsideZone(L2Zone.FLAG_NOESCAPE))
		{
			sendMessage("You can't teleport from here.");
			return false;
		}
		
		if (isInOlympiadMode())
		{
			if (mode == TeleportMode.SCROLL_OF_ESCAPE)
				sendPacket(SystemMessageId.THIS_ITEM_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
			else if (mode == TeleportMode.RECALL)
				sendPacket(SystemMessageId.THIS_SKILL_IS_NOT_AVAILABLE_FOR_THE_OLYMPIAD_EVENT);
			else
				sendMessage("You can't teleport during Olympiad.");
			return false;
		}
		
		// Check to see if the player is in a festival.
		if (isFestivalParticipant())
		{
			sendMessage("You can't teleport in a festival.");
			return false;
		}
		
		if (inObserverMode())
		{
			sendMessage("You can't teleport during Observation Mode.");
			return false;
		}
		
		if (isAfraid())
			return false;
		
		return true;
	}
	
	public boolean canSee(L2Creature cha)
	{
		if (isGM())
			return true;
		
		final L2Player player = cha.getActingPlayer();
		
		if (player == this)
			return true;
		
		if (player != null)
		{
			if (player.getAppearance().isInvisible())
				return false;
			
			if (player.inObserverMode())
				return false;
		}
		
		// Are traps invisible for other chars than owner?
		if (cha instanceof L2Trap)
		{
			final L2Trap trap = (L2Trap)cha;
			
			if (!trap.isDetected())
				return false;
		}
		
		return true;
	}
	
	public static class TeleportBookmark
	{
		public int _id, _x, _y, _z, _icon;
		public String _name, _tag;
		
		public TeleportBookmark(int id, int x, int y, int z, int icon, String tag, String name)
		{
			_id = id;
			_x = x;
			_y = y;
			_z = z;
			_icon = icon;
			_name = name;
			_tag = tag;
		}
	}
	
	public void teleportBookmarkModify(int Id, int icon, String tag, String name)
	{
		int count = 0;
		int size = tpbookmark.size();
		while (size > count)
		{
			if (tpbookmark.get(count)._id == Id)
			{
				tpbookmark.get(count)._icon = icon;
				tpbookmark.get(count)._tag = tag;
				tpbookmark.get(count)._name = name;
				
				try
				{
					PlayerRepository.getInstance().updateBookmark(getObjectId(), Id, icon, tag, name);
				}
				catch (Exception e)
				{
					_log.error("Could not update character teleport bookmark data.", e);
				}
			}
			count++;
		}
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public void teleportBookmarkDelete(int Id)
	{
		try
		{
			PlayerRepository.getInstance().deleteBookmark(getObjectId(), Id);
		}
		catch (Exception e)
		{
			_log.error("Could not delete character teleport bookmark data.", e);
		}
		
		int count = 0;
		int size = tpbookmark.size();
		
		while (size > count)
		{
			if (tpbookmark.get(count)._id == Id)
			{
				tpbookmark.remove(count);
				break;
			}
			count++;
		}
		
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public void teleportBookmarkGo(int Id)
	{
		if (!teleportBookmarkCondition(0))
			return;
		if (getInventory().getInventoryItemCount(20025, 0) == 0)
		{
			sendPacket(SystemMessageId.CANNOT_TELEPORT_WITHOUT_TELEPORT_ITEM);
			return;
		}
		SystemMessage sm = new SystemMessage(SystemMessageId.S1_DISAPPEARED);
		sm.addItemName(20025);
		sendPacket(sm);
		
		int count = 0;
		int size = tpbookmark.size();
		while (size > count)
		{
			if (tpbookmark.get(count)._id == Id)
			{
				destroyItem("Consume", getInventory().getItemByItemId(20025).getObjectId(), 1, null, false);
				teleToLocation(tpbookmark.get(count)._x, tpbookmark.get(count)._y, tpbookmark.get(count)._z);
				break;
			}
			count++;
		}
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public boolean teleportBookmarkCondition(int type)
	{
		if (isInCombat())
		{
			sendPacket(SystemMessageId.UNNAMED_2348);
			return false;
		}
		else if (isInSiege())
		{
			sendPacket(SystemMessageId.UNNAMED_2349);
			return false;
		}
		else if (isInDuel())
		{
			sendPacket(SystemMessageId.UNNAMED_2350);
			return false;
		}
		else if (isFlying())
		{
			sendPacket(SystemMessageId.UNNAMED_2351);
			return false;
		}
		else if (isInOlympiadMode())
		{
			sendPacket(SystemMessageId.UNNAMED_2352);
			return false;
		}
		else if (isParalyzed())
		{
			sendPacket(SystemMessageId.UNNAMED_2353);
			return false;
		}
		else if (isDead())
		{
			sendPacket(SystemMessageId.UNNAMED_2354);
			return false;
		}
		else if (isInBoat() || isInAirShip() || isInJail() || isInsideZone(L2Zone.FLAG_NOSUMMON))
		{
			if (type == 0)
				sendPacket(SystemMessageId.UNNAMED_2355);
			else if (type == 1)
				sendPacket(SystemMessageId.UNNAMED_2410);
			return false;
		}
		else if (isInWater())
		{
			sendPacket(SystemMessageId.UNNAMED_2356);
			return false;
		}
		/* TODO: Instant Zone still not implement
		else if (this.isInsideZone(ZONE_INSTANT))
		{
			sendPacket(SystemMessageId.UNNAMED_2357);
			return;
		}
		 */
		else
			return true;
	}
	
	public void teleportBookmarkAdd(int x, int y, int z, int icon, String tag, String name)
	{
		if (!teleportBookmarkCondition(1))
			return;
		
		if (tpbookmark.size() >= _bookmarkslot)
		{
			sendPacket(SystemMessageId.UNNAMED_2358);
			return;
		}
		
		if (getInventory().getInventoryItemCount(20033, 0) == 0)
		{
			sendPacket(SystemMessageId.YOU_CANNOT_BOOKMARK_THIS_LOCATION_BECAUSE_YOU_DO_NOT_HAVE_A_MY_TELEPORT_FLAG);
			return;
		}
		
		int count = 0;
		int id = 1;
		List<Integer> idlist = new ArrayList<Integer>();
		
		int size = tpbookmark.size();
		while (size > count)
		{
			idlist.add(tpbookmark.get(count)._id);
			count++;
		}
		
		for (int i = 1; i < 10; i++)
		{
			if (!idlist.contains(i))
			{
				id = i;
				break;
			}
		}
		
		TeleportBookmark tpadd = new TeleportBookmark(id, x, y, z, icon, tag, name);
		tpbookmark.add(tpadd);
		
		destroyItem("Consume", getInventory().getItemByItemId(20033).getObjectId(), 1, null, false);
		
		SystemMessage sm = new SystemMessage(SystemMessageId.S1_DISAPPEARED);
		sm.addItemName(20033);
		sendPacket(sm);
		
		try
		{
			PlayerRepository.getInstance().saveBookmark(getObjectId(), new BookmarkRow(id, x, y, z, icon, tag, name));
		}
		catch (Exception e)
		{
			_log.error("Could not insert character teleport bookmark data.", e);
		}
		
		sendPacket(new ExGetBookMarkInfoPacket(this));
	}
	
	public void restoreTeleportBookmark()
	{
		try
		{
			for (BookmarkRow row : PlayerRepository.getInstance().loadBookmarks(getObjectId()))
			{
				tpbookmark.add(new TeleportBookmark(row.number(), row.x(), row.y(), row.z(), row.icon(), row.tag(), row
						.name()));
			}
		}
		catch (Exception e)
		{
			_log.error("Failed restoring character teleport bookmark.", e);
		}
	}
	
	public boolean isFlyingMounted()
	{
		return _isFlyingMounted;
	}
	
	public void setIsFlyingMounted(boolean val)
	{
		_isFlyingMounted = val;
		setIsFlying(val);
	}
	
	public synchronized boolean enterOfflineMode()
	{
		if (isInOfflineMode())
			return false;
		
		leaveParty();
		
		if (getPet() != null)
			getPet().unSummon(this);
		
		// set name color if enabled
		updateNameTitleColor();
		
		new Disconnection(this).store().close(false);
		
		RegionBBSManager.changeCommunityBoard(this, PlayerStateOnCommunity.OFFLINE);
		return true;
	}
	
	public boolean isInOfflineMode()
	{
		return getOnlineState() == ONLINE_STATE_ONLINE && getClient() == null;
	}
	
	public boolean isChaotic()
	{
		return getKarma() > 0 || isCursedWeaponEquipped();
	}
	
	public int getCertificationLevel(int classIndex)
	{
		int certificationLevel = -1;
		
		try
		{
			certificationLevel = PlayerRepository.getInstance().loadCertificationLevel(getObjectId(), classIndex);
		}
		catch (Exception e)
		{
			_log.error("Could not get subclass certification level: ", e);
		}
		return certificationLevel;
	}
	
	public void storeCertificationLevel(int classIndex)
	{
		try
		{
			PlayerRepository.getInstance().insertCertification(getObjectId(), classIndex);
		}
		catch (Exception e)
		{
			_log.error("Could not store character subclass certification: ", e);
		}
	}
	
	public void updateCertificationLevel(int classIndex, int level)
	{
		try
		{
			PlayerRepository.getInstance().updateCertificationLevel(getObjectId(), classIndex, level);
		}
		catch (Exception e)
		{
			_log.error("Could not update character subclass certification: ", e);
		}
	}
	
	public void deleteSubclassCertifications()
	{
		try
		{
			PlayerRepository.getInstance().deleteCertifications(getObjectId());
		}
		catch (Exception e)
		{
			_log.error("Could not delete character subclass certifications: ", e);
		}
	}
	
	public int getSubClassType()
	{
		/*
		 * 1: Warrior
		 * 2: Rogue
		 * 3: Knight
		 * 4: Summoner
		 * 5: Wizard
		 * 6: Healer
		 * 7: Enchanter
		 */
		
		int classId = getActiveClass();
		int subClassType = 0;
		
		if (classId == 3 || classId == 2 || classId == 46 || classId == 48 || classId == 55 || classId == 57
				|| classId == 127 || classId == 128 || classId == 129 || classId == 89 || classId == 88
				|| classId == 113 || classId == 114 || classId == 117 || classId == 118 || classId == 131
				|| classId == 132 || classId == 133)
			subClassType = 1;
		else if (classId == 9 || classId == 24 || classId == 37 || classId == 130 || classId == 8 || classId == 23
				|| classId == 36 || classId == 92 || classId == 102 || classId == 109 || classId == 134
				|| classId == 93 || classId == 101 || classId == 108)
			subClassType = 2;
		else if (classId == 5 || classId == 6 || classId == 20 || classId == 33 || classId == 90 || classId == 91
				|| classId == 99 || classId == 106)
			subClassType = 3;
		else if (classId == 14 || classId == 28 || classId == 41 || classId == 96 || classId == 104 || classId == 111)
			subClassType = 4;
		else if (classId == 12 || classId == 13 || classId == 27 || classId == 40 || classId == 94 || classId == 95
				|| classId == 103 || classId == 110)
			subClassType = 5;
		else if (classId == 16 || classId == 30 || classId == 43 || classId == 97 || classId == 105 || classId == 112)
			subClassType = 6;
		else if (classId == 17 || classId == 21 || classId == 34 || classId == 52 || classId == 51 || classId == 135
				|| classId == 98 || classId == 100 || classId == 107 || classId == 116 || classId == 115
				|| classId == 136)
			subClassType = 7;
		
		return subClassType;
	}
	
	private final long[] _floodProtectors = new long[FloodProtector.Protected.VALUES_LENGTH];
	
	public long[] getFloodProtectors()
	{
		return _floodProtectors;
	}
	
	private AbstractFunEventPlayerInfo _playerInfoForEvents;
	
	public <T extends AbstractFunEventPlayerInfo> void setPlayerInfo(T info)
	{
		if (_playerInfoForEvents != null && info != null)
			throw new IllegalStateException();
		
		_playerInfoForEvents = info;
	}
	
	public <T extends AbstractFunEventPlayerInfo> T getPlayerInfo(Class<T> clazz)
	{
		final AbstractFunEventPlayerInfo info = _playerInfoForEvents;
		
		if (clazz.isInstance(info))
			return clazz.cast(info);
		else
			return null;
	}
	
	public <T extends AbstractFunEventPlayerInfo> boolean isInEvent(Class<T> clazz)
	{
		return clazz.isInstance(_playerInfoForEvents);
	}
	
	public <T extends AbstractFunEventPlayerInfo> T as(Class<T> clazz)
	{
		final AbstractFunEventPlayerInfo info = _playerInfoForEvents;
		
		if (clazz.isInstance(info))
			return clazz.cast(info);
		else
			throw new IllegalStateException();
	}
	
	private final void restoreNameTitleColors()
	{
		try
		{
			ColorsRow colors = PlayerRepository.getInstance().loadColors(getObjectId());
			if (colors != null)
			{
				getAppearance().setNameColor(Util.reverseRGBChanels(Integer.decode("0x" + colors.nameColor())));
				getAppearance().setTitleColor(Util.reverseRGBChanels(Integer.decode("0x" + colors.titleColor())));
			}
			else
			{
				getAppearance().setNameColor(PlayerAppearance.DEFAULT_NAME_COLOR);
				getAppearance().setTitleColor(PlayerAppearance.DEFAULT_TITLE_COLOR);
			}
		}
		catch (Exception e)
		{
			getAppearance().setNameColor(PlayerAppearance.DEFAULT_NAME_COLOR);
			getAppearance().setTitleColor(PlayerAppearance.DEFAULT_TITLE_COLOR);
			
			_log.error("Could not load character name/title colors!", e);
		}
		updateNameTitleColor();
	}
	
	private final void restoreCreationDate()
	{
		try
		{
			BirthdayRow birthday = PlayerRepository.getInstance().loadBirthday(getObjectId());
			_lastClaim = birthday.giftClaimedYear();
			_createdOn = Calendar.getInstance();
			_createdOn.setTimeInMillis(birthday.createdOn().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
		}
		catch (Exception e)
		{
			_log.error("Could not load character creation date!", e);
			_lastClaim = Calendar.getInstance().get(Calendar.YEAR);
			_createdOn = Calendar.getInstance();
		}
	}
	
	/**
	 * Update the database indicating that the player received this year's
	 * birthday gift. Should be called before giving items.
	 * @return whether updating gift receival status succeeds
	 */
	public final boolean claimCreationPrize()
	{
		_lastClaim = Calendar.getInstance().get(Calendar.YEAR);
		try
		{
			PlayerRepository.getInstance().claimBirthdayGift(getObjectId(), _lastClaim);
			return true;
		}
		catch (Exception e)
		{
			_log.error(this + " could not claim creation day prize!", e);
			return false;
		}
	}
	
	/** @return Calendar object representing the exact creation date. */
	public final Calendar getCreationDate()
	{
		if (_createdOn == null) // this cannot happen, but just in-case
		{
			_log.error("Illegal method call: before load() -ing the actual player instance!", new Exception());
			restoreCreationDate();
		}
		return _createdOn;
	}
	
	/** @return true if birthday is on Feb 29th, false otherwise */
	public final boolean isBirthdayIllegal()
	{
		return (getCreationDate().get(Calendar.MONTH) == Calendar.FEBRUARY && getCreationDate().get(
				Calendar.DAY_OF_MONTH) == 29);
	}
	
	private final int getDaysUntilAnniversary()
	{
		Calendar now = Calendar.getInstance();
		Calendar birth = getCreationDate();
		int day = birth.get(Calendar.DAY_OF_MONTH);
		if (isBirthdayIllegal())
			day = 28;
		if (birth.get(Calendar.MONTH) == now.get(Calendar.MONTH) && day == now.get(Calendar.DAY_OF_MONTH))
		{
			if (now.get(Calendar.YEAR) > _lastClaim)
				return 0;
			else
				return 365;
		}
		
		Calendar anno = Calendar.getInstance();
		anno.setTimeInMillis(birth.getTimeInMillis());
		anno.set(Calendar.DAY_OF_MONTH, day);
		anno.set(Calendar.YEAR, now.get(Calendar.YEAR));
		
		if (anno.compareTo(now) < 0)
			anno.add(Calendar.YEAR, 1);
		long diff = (anno.getTimeInMillis() - now.getTimeInMillis());
		return (int)(diff / 86400000) + 1;
	}
	
	/**
	 * Returns -1 if this day is the anniversary day, but the player
	 * has already received the gift. Otherwise returns how many days
	 * are left until the birthday.<BR>
	 * 0 indicates that player can receive the gift.
	 * @return days until anniversary or -1
	 */
	public final int canReceiveAnnualPresent()
	{
		if (_lastClaim < Calendar.getInstance().get(Calendar.YEAR))
			return getDaysUntilAnniversary();
		else
			return -1;
	}
	
	public final void saveLastTarget(int objectId)
	{
		_lastTargetId = objectId;
		_lastTargetChange = System.currentTimeMillis();
	}
	
	public final int getLastTargetId()
	{
		return _lastTargetId;
	}
	
	public final long getLastTargetTime()
	{
		return _lastTargetChange;
	}
	
	public final boolean isIllegalWaiting()
	{
		return _illegalWaiting;
	}
	
	public final void setIllegalWaiting(boolean iw)
	{
		_illegalWaiting = iw;
	}
	
	@Override
	public void sendResistedMyEffectMessage(L2Creature target, L2Skill skill)
	{
		SystemMessage sm = new SystemMessage(SystemMessageId.C1_RESISTED_YOUR_S2_EFFECT);
		sm.addCharName(target);
		sm.addSkillName(skill);
		sendPacket(sm);
	}
	
	public boolean tryJump()
	{
		long time = System.currentTimeMillis();
		if (_nextJumpTime < time)
		{
			_nextJumpTime = time + 3000;
			return true;
		}
		else
			return false;
	}
	
	public boolean destroyItems(String process, int[] itemId, long[] count, L2Object ref, boolean sendMessage)
	{
		if (itemId == null || itemId.length == 0 || count == null || count.length == 0 || itemId.length != count.length)
		{
			_log.error("Invalid mass item destruction call!", new IllegalArgumentException());
			return false;
		}
		
		int fail = -1;
		L2ItemInstance[] items = new L2ItemInstance[itemId.length];
		for (int i = 0; i < itemId.length; i++)
		{
			L2ItemInstance item = getInventory().getItemByItemId(itemId[i]);
			items[i] = item;
			if (item == null || item.getCount() < count[i]
					|| getInventory().destroyItem(process, item, count[i], this, ref) == null)
			{
				fail = i;
				break;
			}
		}
		
		if (fail > -1)
		{
			if (fail > 0)
				for (int i = 0; i < fail; i++)
					getInventory().addItem(process, itemId[i], count[i], this, ref);
			if (sendMessage)
				sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			return false;
		}
		else
		{
			for (int i = 0; i < itemId.length; i++)
			{
				getInventory().updateInventory(items[i]);
				if (sendMessage)
				{
					SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1_DISAPPEARED);
					sm.addItemName(itemId[i]);
					sm.addItemNumber(count[i]);
					sendPacket(sm);
				}
			}
			return true;
		}
	}
	
	public void onTutorialQuestionMark(int number)
	{
		showTutorialHtml(number);
	}
	
	public void onTutorialLink(String request)
	{
		if (request.equals("close"))
			sendPacket(new TutorialCloseHtml());
	}
	
	private void showTutorialHtml(int number)
	{
		String msg = "";
		
		switch (number)
		{
			case 1001:
				L2ClassMasterInstance.onTutorialQuestionMark(this, number);
				break;
			case 1002:
				msg = HtmCache.getInstance().getHtm("data/html/level28.htm");
				sendPacket(new TutorialShowHtml(msg));
				break;
		}
	}
	
	private AnswerHandler _answerHandler;
	
	public AnswerHandler setAnswerHandler(AnswerHandler nextHandler)
	{
		final AnswerHandler previousHandler = _answerHandler;
		
		_answerHandler = nextHandler;
		
		return previousHandler;
	}
	
	public boolean hasActiveConfirmDlg()
	{
		return _answerHandler != null;
	}
}
