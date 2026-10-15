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
package com.l2jfree.gameserver.gameobjects.templates;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.gameserver.gameobjects.base.ClassId;
import com.l2jfree.gameserver.model.L2MinionData;
import com.l2jfree.gameserver.model.drop.L2DropCategory;
import com.l2jfree.gameserver.model.drop.L2DropData;
import com.l2jfree.gameserver.model.quest.Quest;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.Stats;
import com.l2jfree.gameserver.templates.StatsSet;

/**
 * This cl contains all generic data of a L2Spawn object.<BR><BR>
 * 
 * <B><U> Data</U> :</B><BR><BR>
 * <li>npcId, type, name, sex</li>
 * <li>rewardExp, rewardSp</li>
 * <li>aggroRange, factionId, factionRange</li>
 * <li>rhand, lhand, armor</li>
 * <li>isUndead</li>
 * <li>_drops</li>
 * <li>_minions</li>
 * <li>_teachInfo</li>
 * <li>_skills</li>
 * <li>_questsStart</li><BR><BR>
 * 
 * this template has property that will be set by setters.
 * <br/>
 * <br/>
 * <font color="red">
 * <b>Property don't change in the time, this is just a template, not the currents status
 * of characters !</b>
 * </font>
 * 
 * @version $Revision: 1.1.2.4 $ $Date: 2005/04/02 15:57:51 $
 */
public final class L2NpcTemplate extends L2CreatureTemplate
{
	/**
	 * Logger
	 */
	private final static Logger _log = LoggerFactory.getLogger(L2NpcTemplate.class);
	
	private int _npcId;
	private int _idTemplate;
	private final String _type;
	private final Class<?> _clazz;
	private String _name;
	private boolean _serverSideName;
	private String _title;
	private boolean _serverSideTitle;
	private String _sex;
	private byte _level;
	private int _rewardExp;
	private int _rewardSp;
	private int _aggroRange;
	private int _rhand;
	private int _lhand;
	private int _armor;
	private String _factionId;
	private int _factionRange;
	private int _absorbLevel;
	private AbsorbCrystalType _absorbType;
	private short _ss;
	private short _bss;
	private short _ssRate;
	private int _npcFaction;
	private String _npcFactionName;
	private String _jClass;
	private AIType _ai;
	private final boolean _isQuestMonster;
	private final boolean _dropHerbs;
	private final float _baseVitalityDivider;
	
	private Race _race;
	
	/** The table containing all Item that can be dropped by L2NpcInstance using this L2NpcTemplate*/
	private L2DropCategory[] _categories;
	
	/** The table containing all Minions that must be spawn with the L2NpcInstance using this L2NpcTemplate*/
	private L2MinionData[] _minions;
	
	/** The list of class that this NpcTemplate can Teach */
	private EnumSet<ClassId> _teachInfo;
	
	/** List of skills of this npc */
	private Map<Integer, L2Skill> _skills;
	
	/** List of resist stats for this npc*/
	private Map<Stats, Double> _vulnerabilities;
	
	/** contains a list of quests for each event type (questStart, questAttack, questKill, etc)*/
	private Quest[][] _questEvents;
	
	public static enum AbsorbCrystalType
	{
		LAST_HIT,
		FULL_PARTY,
		PARTY_ONE_RANDOM
	}
	
	public static enum AIType
	{
		FIGHTER,
		ARCHER,
		BALANCED,
		MAGE,
		HEALER,
		CORPSE
	}
	
	public static enum Race
	{
		UNDEAD,
		MAGICCREATURE,
		BEAST,
		ANIMAL,
		PLANT,
		HUMANOID,
		SPIRIT,
		ANGEL,
		DEMON,
		DRAGON,
		GIANT,
		BUG,
		FAIRIE,
		HUMAN,
		ELVE,
		DARKELVE,
		ORC,
		DWARVE,
		OTHER,
		NONLIVING,
		SIEGEWEAPON,
		DEFENDINGARMY,
		MERCENARIE,
		UNKNOWN,
		KAMAEL,
		NONE;
		
		public Stats getOffensiveStat()
		{
			switch (this)
			{
				case BEAST:
					return Stats.PATK_MONSTERS;
				case ANIMAL:
					return Stats.PATK_ANIMALS;
				case PLANT:
					return Stats.PATK_PLANTS;
				case DRAGON:
					return Stats.PATK_DRAGONS;
				case BUG:
					return Stats.PATK_INSECTS;
				case GIANT:
					return Stats.PATK_GIANTS;
				case MAGICCREATURE:
					return Stats.PATK_MAGIC;
				default:
					return null;
			}
		}
		
		public Stats getDefensiveStat()
		{
			switch (this)
			{
				case BEAST:
					return Stats.PDEF_MONSTERS;
				case ANIMAL:
					return Stats.PDEF_ANIMALS;
				case PLANT:
					return Stats.PDEF_PLANTS;
				case DRAGON:
					return Stats.PDEF_DRAGONS;
				case BUG:
					return Stats.PDEF_INSECTS;
				case GIANT:
					return Stats.PDEF_GIANTS;
				case MAGICCREATURE:
					return Stats.PDEF_MAGIC;
				default:
					return null;
			}
		}
	}
	
	/**
	 * Constructor of L2Creature.<BR>
	 * 
	 * @param set The StatsSet object to transfer data to the method
	 * @throws ClassNotFoundException
	 */
	public L2NpcTemplate(StatsSet set) throws ClassNotFoundException
	{
		super(set);
		_npcId = set.getInteger("npcId");
		_idTemplate = set.getInteger("idTemplate");
		if (getNpcId() == 30995)
			_type = "L2RaceManager";
		else if (31046 <= getNpcId() && getNpcId() <= 31053)
			_type = "L2SymbolMaker";
		else
			_type = set.getString("type").intern(); // implementing class name
		_clazz = Class.forName("com.l2jfree.gameserver.gameobjects.instance." + _type + "Instance");
		_name = set.getString("name").intern();
		_serverSideName = set.getBool("serverSideName");
		_title = set.getString("title").intern();
		_isQuestMonster = _title.equalsIgnoreCase("Quest Monster");
		_serverSideTitle = set.getBool("serverSideTitle");
		_sex = set.getString("sex").intern();
		_level = set.getByte("level");
		_rewardExp = set.getInteger("rewardExp");
		_rewardSp = set.getInteger("rewardSp");
		_aggroRange = set.getInteger("aggroRange");
		_rhand = set.getInteger("rhand");
		_lhand = set.getInteger("lhand");
		_armor = set.getInteger("armor");
		setFactionId(set.getString("factionId", null));
		_factionRange = set.getInteger("factionRange");
		_absorbLevel = set.getInteger("absorb_level");
		_absorbType = AbsorbCrystalType.valueOf(set.getString("absorb_type"));
		_ss = set.getShort("ss");
		_bss = set.getShort("bss");
		_ssRate = set.getShort("ssRate");
		_npcFaction = set.getInteger("NPCFaction", 0);
		_npcFactionName = set.getString("NPCFactionName", "Devine Clan").intern();
		_jClass = set.getString("jClass").intern();
		_dropHerbs = set.getBool("drop_herbs");
		
		String ai = set.getString("AI");
		if (ai.equalsIgnoreCase("archer"))
			_ai = AIType.ARCHER;
		else if (ai.equalsIgnoreCase("balanced"))
			_ai = AIType.BALANCED;
		else if (ai.equalsIgnoreCase("mage"))
			_ai = AIType.MAGE;
		else if (ai.equalsIgnoreCase("healer"))
			_ai = AIType.HEALER;
		else if (ai.equalsIgnoreCase("corpse"))
			_ai = AIType.CORPSE;
		else
			_ai = AIType.FIGHTER;
		
		_race = null;
		_teachInfo = null;
		
		// all NPCs has 20 resistance to all attributes
		setBaseFireRes(getBaseFireRes() + 20);
		setBaseWindRes(getBaseWindRes() + 20);
		setBaseWaterRes(getBaseWaterRes() + 20);
		setBaseEarthRes(getBaseEarthRes() + 20);
		setBaseHolyRes(getBaseHolyRes() + 20);
		setBaseDarkRes(getBaseDarkRes() + 20);
		
		// can be loaded from db
		_baseVitalityDivider =
				getLevel() > 0 && getRewardExp() > 0 ? getBaseHpMax() * 9 * getLevel() * getLevel()
						/ (100 * getRewardExp()) : 0;
	}
	
	/**
	 * Add the class id this npc can teach
	 * @param classId
	 */
	public void addTeachInfo(ClassId classId)
	{
		if (_teachInfo == null)
			_teachInfo = EnumSet.noneOf(ClassId.class);
		_teachInfo.add(classId);
	}
	
	/**
	 * @return the teach infos
	 */
	public Set<ClassId> getTeachInfo()
	{
		return _teachInfo;
	}
	
	/**
	 * Check if this npc can teach to this class
	 * @param classId
	 * @return true if this npc can teach to this class
	 */
	public boolean canTeach(ClassId classId)
	{
		if (_teachInfo == null)
			return false;
		
		// If the player is on a third class, fetch the class teacher
		// information for its parent class.
		if (classId.level() == 3)
			return _teachInfo.contains(classId.getParent());
		
		return _teachInfo.contains(classId);
	}
	
	/**
	 * add a drop to a given category.  If the category does not exist, create it.
	 * @param drop
	 * @param categoryType
	 */
	public synchronized void addDropData(L2DropData drop, int categoryType)
	{
		if (drop.isQuestDrop())
		{
			//          if (_questDrops == null)
			//              _questDrops = new ArrayList<L2DropData>(0);
			//          _questDrops.add(drop);
		}
		else
		{
			// if the category doesn't already exist, create it first
			if (_categories == null)
				_categories = new L2DropCategory[0];
			synchronized (_categories)
			{
				boolean catExists = false;
				for (L2DropCategory cat : _categories)
					// if the category exists, add the drop to this category.
					if (cat.getCategoryType() == categoryType)
					{
						cat.addDropData(drop);
						catExists = true;
						break;
					}
				// if the category doesn't exit, create it and add the drop
				if (!catExists)
				{
					L2DropCategory cat = new L2DropCategory(categoryType);
					cat.addDropData(drop);
					_categories = ArrayUtils.add(_categories, cat);
				}
			}
		}
	}
	
	public void addRaidData(L2MinionData minion)
	{
		if (_minions == null)
			_minions = new L2MinionData[] { minion };
		else
			_minions = ArrayUtils.add(_minions, minion);
	}
	
	public void addSkill(L2Skill skill)
	{
		if (_skills == null)
			_skills = new ConcurrentHashMap<Integer, L2Skill>();
		
		_skills.put(skill.getId(), skill);
	}
	
	public void clearSkills()
	{
		if (_skills != null)
			_skills.clear();
	}
	
	public void addVulnerability(Stats id, double vuln)
	{
		if (vuln == 1)
		{
			if (_vulnerabilities != null)
				_vulnerabilities.remove(id);
			return;
		}
		
		if (_vulnerabilities == null)
			_vulnerabilities = new LinkedHashMap<Stats, Double>();
		_vulnerabilities.put(id, vuln);
	}
	
	public double getVulnerability(Stats id)
	{
		if (_vulnerabilities == null)
			return 1;
		Double vuln = _vulnerabilities.get(id);
		return vuln == null ? 1 : vuln.doubleValue();
	}
	
	public double removeVulnerability(Stats id)
	{
		return _vulnerabilities.remove(id);
	}
	
	/**
	 * Return the list of all possible UNCATEGORIZED drops of this L2NpcTemplate.<BR><BR>
	 * @return the drop categories
	 */
	public L2DropCategory[] getDropData()
	{
		return _categories;
	}
	
	/**
	 * Return the list of all possible item drops of this L2NpcTemplate.<BR>
	 * (ie full drops and part drops, mats, miscellaneous & UNCATEGORIZED)<BR><BR>
	 */
	public List<L2DropData> getAllDropData()
	{
		if (_categories == null)
			return null;
		List<L2DropData> lst = new ArrayList<L2DropData>();
		for (L2DropCategory tmp : _categories)
		{
			lst.addAll(tmp.getAllDrops());
		}
		return lst;
	}
	
	/**
	 * Empty all possible drops of this L2NpcTemplate.<BR><BR>
	 */
	public synchronized void clearAllDropData()
	{
		if (_categories == null)
			return;
		
		for (L2DropCategory category : _categories)
			category.clearAllDrops();
		
		_categories = null;
	}
	
	/**
	 * Return the list of all Minions that must be spawn with the L2NpcInstance using this L2NpcTemplate.<BR><BR>
	 */
	public L2MinionData[] getMinionData()
	{
		return _minions;
	}
	
	public Map<Integer, L2Skill> getSkills()
	{
		return _skills == null ? null : Collections.unmodifiableMap(_skills);
	}
	
	public void addQuestEvent(Quest.QuestEventType EventType, Quest q)
	{
		if (_questEvents == null)
			_questEvents = new Quest[Quest.QuestEventType.values().length][];
		
		if (_questEvents[EventType.ordinal()] == null)
		{
			_questEvents[EventType.ordinal()] = new Quest[] { q };
		}
		else
		{
			Quest[] _quests = _questEvents[EventType.ordinal()];
			int len = _quests.length;
			
			// if only one registration per npc is allowed for this event type
			// then only register this NPC if not already registered for the specified event.
			// if a quest allows multiple registrations, then register regardless of count
			// In all cases, check if this new registration is replacing an older copy of the SAME quest
			if (!EventType.isMultipleRegistrationAllowed())
			{
				if (_quests[0].getName().equals(q.getName()))
					_quests[0] = q;
				else
					_log.warn("Quest event not allowed in multiple quests.  Skipped addition of Event Type \""
							+ EventType + "\" for NPC \"" + _name + "\" and quest \"" + q.getName() + "\".");
			}
			else
			{
				// be ready to add a new quest to a new copy of the list, with larger size than previously.
				Quest[] tmp = new Quest[len + 1];
				// loop through the existing quests and copy them to the new list.  While doing so, also
				// check if this new quest happens to be just a replacement for a previously loaded quest.
				// If so, just save the updated reference and do NOT use the new list. Else, add the new
				// quest to the end of the new list
				for (int i = 0; i < len; i++)
				{
					if (_quests[i].getName().equals(q.getName()))
					{
						_quests[i] = q;
						return;
					}
					tmp[i] = _quests[i];
				}
				tmp[len] = q;
				_questEvents[EventType.ordinal()] = tmp;
			}
		}
	}
	
	public Quest[] getEventQuests(Quest.QuestEventType EventType)
	{
		if (_questEvents == null)
			return null;
		return _questEvents[EventType.ordinal()];
	}
	
	public void setRace(int raceId)
	{
		switch (raceId)
		{
			case 1:
				_race = L2NpcTemplate.Race.UNDEAD;
				break;
			case 2:
				_race = L2NpcTemplate.Race.MAGICCREATURE;
				break;
			case 3:
				_race = L2NpcTemplate.Race.BEAST;
				break;
			case 4:
				_race = L2NpcTemplate.Race.ANIMAL;
				break;
			case 5:
				_race = L2NpcTemplate.Race.PLANT;
				break;
			case 6:
				_race = L2NpcTemplate.Race.HUMANOID;
				break;
			case 7:
				_race = L2NpcTemplate.Race.SPIRIT;
				break;
			case 8:
				_race = L2NpcTemplate.Race.ANGEL;
				break;
			case 9:
				_race = L2NpcTemplate.Race.DEMON;
				break;
			case 10:
				_race = L2NpcTemplate.Race.DRAGON;
				break;
			case 11:
				_race = L2NpcTemplate.Race.GIANT;
				break;
			case 12:
				_race = L2NpcTemplate.Race.BUG;
				break;
			case 13:
				_race = L2NpcTemplate.Race.FAIRIE;
				break;
			case 14:
				_race = L2NpcTemplate.Race.HUMAN;
				break;
			case 15:
				_race = L2NpcTemplate.Race.ELVE;
				break;
			case 16:
				_race = L2NpcTemplate.Race.DARKELVE;
				break;
			case 17:
				_race = L2NpcTemplate.Race.ORC;
				break;
			case 18:
				_race = L2NpcTemplate.Race.DWARVE;
				break;
			case 19:
				_race = L2NpcTemplate.Race.OTHER;
				break;
			case 20:
				_race = L2NpcTemplate.Race.NONLIVING;
				break;
			case 21:
				_race = L2NpcTemplate.Race.SIEGEWEAPON;
				break;
			case 22:
				_race = L2NpcTemplate.Race.DEFENDINGARMY;
				break;
			case 23:
				_race = L2NpcTemplate.Race.MERCENARIE;
				break;
			case 24:
				_race = L2NpcTemplate.Race.UNKNOWN;
				break;
			case 25:
				_race = L2NpcTemplate.Race.KAMAEL;
				break;
			default:
				_race = L2NpcTemplate.Race.NONE;
				break;
		}
	}
	
	public L2NpcTemplate.Race getRace()
	{
		if (_race == null)
			_race = L2NpcTemplate.Race.NONE;
		
		return _race;
	}
	
	public int getNpcFaction()
	{
		return _npcFaction;
	}
	
	public void setNpcFaction(int npcFaction)
	{
		_npcFaction = npcFaction;
	}
	
	public String getNpcFactionName()
	{
		return _npcFactionName;
	}
	
	/**
	 * @return the absorb_level
	 */
	public int getAbsorbLevel()
	{
		return _absorbLevel;
	}
	
	/**
	 * @param absorb_level the absorb_level to set
	 */
	public void setAbsorbLevel(int absorb_level)
	{
		_absorbLevel = absorb_level;
	}
	
	/**
	 * @return the absorb_type
	 */
	public AbsorbCrystalType getAbsorbType()
	{
		return _absorbType;
	}
	
	/**
	 * @param absorb_type the absorb type to set
	 */
	public void setAbsorbType(AbsorbCrystalType absorb_type)
	{
		_absorbType = absorb_type;
	}
	
	/**
	 * @return the aggroRange
	 */
	public int getAggroRange()
	{
		return _aggroRange;
	}
	
	/**
	 * @param aggroRange the aggroRange to set
	 */
	public void setAggroRange(int aggroRange)
	{
		_aggroRange = aggroRange;
	}
	
	/**
	 * @return the armor
	 */
	public int getArmor()
	{
		return _armor;
	}
	
	/**
	 * @param armor the armor to set
	 */
	public void setArmor(int armor)
	{
		_armor = armor;
	}
	
	/**
	 * @return the factionId
	 */
	public String getFactionId()
	{
		return _factionId;
	}
	
	/**
	 * @param factionId the factionId to set
	 */
	public void setFactionId(String factionId)
	{
		if (factionId == null)
		{
			_factionId = null;
			return;
		}
		_factionId = factionId.intern();
	}
	
	/**
	 * @return the factionRange
	 */
	public int getFactionRange()
	{
		return _factionRange;
	}
	
	/**
	 * @param factionRange the factionRange to set
	 */
	public void setFactionRange(int factionRange)
	{
		_factionRange = factionRange;
	}
	
	/**
	 * @return the idTemplate
	 */
	public int getIdTemplate()
	{
		return _idTemplate;
	}
	
	/**
	 * @param idTemplate the idTemplate to set
	 */
	public void setIdTemplate(int idTemplate)
	{
		_idTemplate = idTemplate;
	}
	
	/**
	 * @return the level
	 */
	public byte getLevel()
	{
		return _level;
	}
	
	/**
	 * @param level the level to set
	 */
	public void setLevel(byte level)
	{
		_level = level;
	}
	
	/**
	 * @return the lhand
	 */
	public int getLhand()
	{
		return _lhand;
	}
	
	/**
	 * @param lhand the lhand to set
	 */
	public void setLhand(int lhand)
	{
		_lhand = lhand;
	}
	
	/**
	 * @return the name
	 */
	public String getName()
	{
		return _name;
	}
	
	/**
	 * @param name the name to set
	 */
	public void setName(String name)
	{
		_name = name;
	}
	
	/**
	 * @return the npcId
	 */
	public int getNpcId()
	{
		return _npcId;
	}
	
	/**
	 * @param npcId the npcId to set
	 */
	public void setNpcId(int npcId)
	{
		_npcId = npcId;
	}
	
	/**
	 * @return the rewardExp
	 */
	public int getRewardExp()
	{
		return _rewardExp;
	}
	
	/**
	 * @param rewardExp the rewardExp to set
	 */
	public void setRewardExp(int rewardExp)
	{
		_rewardExp = rewardExp;
	}
	
	/**
	 * @return the rewardSp
	 */
	public int getRewardSp()
	{
		return _rewardSp;
	}
	
	/**
	 * @param rewardSp the rewardSp to set
	 */
	public void setRewardSp(int rewardSp)
	{
		_rewardSp = rewardSp;
	}
	
	/**
	 * @return the rhand
	 */
	public int getRhand()
	{
		return _rhand;
	}
	
	/**
	 * @param rhand the rhand to set
	 */
	public void setRhand(int rhand)
	{
		_rhand = rhand;
	}
	
	/**
	 * @return the serverSideName
	 */
	public boolean isServerSideName()
	{
		return _serverSideName;
	}
	
	/**
	 * @param serverSideName the serverSideName to set
	 */
	public void setServerSideName(boolean serverSideName)
	{
		_serverSideName = serverSideName;
	}
	
	/**
	 * @return the serverSideTitle
	 */
	public boolean isServerSideTitle()
	{
		return _serverSideTitle;
	}
	
	/**
	 * @param serverSideTitle the serverSideTitle to set
	 */
	public void setServerSideTitle(boolean serverSideTitle)
	{
		_serverSideTitle = serverSideTitle;
	}
	
	/**
	 * @return the sex
	 */
	public String getSex()
	{
		return _sex;
	}
	
	/**
	 * @param sex the sex to set
	 */
	public void setSex(String sex)
	{
		_sex = sex;
	}
	
	/**
	 * @return the title
	 */
	public String getTitle()
	{
		return _title;
	}
	
	/**
	 * @param title the title to set
	 */
	public void setTitle(String title)
	{
		_title = title;
	}
	
	/**
	 * @return the type
	 */
	public String getType()
	{
		return _type;
	}
	
	public boolean isAssignableTo(Class<?> clazz)
	{
		return clazz.isAssignableFrom(_clazz);
	}
	
	public Constructor<?> getDefaultConstructor() throws NoSuchMethodException, SecurityException
	{
		return _clazz.getConstructor(Integer.TYPE, L2NpcTemplate.class);
	}
	
	/**
	 * @param factionName the nPCFactionName to set
	 */
	public void setNPCFactionName(String factionName)
	{
		_npcFactionName = (factionName == null ? "Devine Clan" : factionName);
	}
	
	/**
	 * @return the jClass
	 */
	public String getJClass()
	{
		return _jClass;
	}
	
	/**
	 * @param class1 the jClass to set
	 */
	public void setJClass(String class1)
	{
		_jClass = class1;
	}
	
	public short getSS()
	{
		return _ss;
	}
	
	public short getBSS()
	{
		return _bss;
	}
	
	public short getSSRate()
	{
		return _ssRate;
	}
	
	public AIType getAI()
	{
		return _ai;
	}
	
	public void setSS(short ss)
	{
		_ss = ss;
	}
	
	public void setBSS(short bss)
	{
		_bss = bss;
	}
	
	public void setSSRate(short ssrate)
	{
		_ssRate = ssrate;
	}
	
	public void setAI(AIType type)
	{
		_ai = type;
	}
	
	public boolean isQuestMonster()
	{
		return _isQuestMonster;
	}
	
	public boolean dropHerbs()
	{
		return _dropHerbs;
	}
	
	public boolean isCustom()
	{
		return _npcId != _idTemplate;
	}
	
	public float getBaseVitalityDivider()
	{
		return _baseVitalityDivider;
	}
}
