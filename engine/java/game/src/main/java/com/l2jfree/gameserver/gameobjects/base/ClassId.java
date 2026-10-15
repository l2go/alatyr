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
package com.l2jfree.gameserver.gameobjects.base;

import java.util.EnumSet;

import com.l2jfree.gameserver.gameobjects.L2Player;

public enum ClassId
{
	HumanFighter/*					*/(0, Race.Human, ClassType.Fighter, ClassLevel.First, null),
	/*	*/Warrior/*					*/(1, Race.Human, ClassType.Fighter, ClassLevel.Second, HumanFighter),
	/*		*/Gladiator/*			*/(2, Race.Human, ClassType.Fighter, ClassLevel.Third, Warrior),
	/*		*/Warlord/*				*/(3, Race.Human, ClassType.Fighter, ClassLevel.Third, Warrior),
	/*	*/HumanKnight/*				*/(4, Race.Human, ClassType.Fighter, ClassLevel.Second, HumanFighter),
	/*		*/Paladin/*				*/(5, Race.Human, ClassType.Fighter, ClassLevel.Third, HumanKnight),
	/*		*/DarkAvenger/*			*/(6, Race.Human, ClassType.Fighter, ClassLevel.Third, HumanKnight),
	/*	*/Rogue/*					*/(7, Race.Human, ClassType.Fighter, ClassLevel.Second, HumanFighter),
	/*		*/TreasureHunter/*		*/(8, Race.Human, ClassType.Fighter, ClassLevel.Third, Rogue),
	/*		*/Hawkeye/*				*/(9, Race.Human, ClassType.Fighter, ClassLevel.Third, Rogue),
	
	HumanMystic/*					*/(10, Race.Human, ClassType.Mystic, ClassLevel.First, null),
	/*	*/HumanWizard/*				*/(11, Race.Human, ClassType.Mystic, ClassLevel.Second, HumanMystic),
	/*		*/Sorceror/*			*/(12, Race.Human, ClassType.Mystic, ClassLevel.Third, HumanWizard),
	/*		*/Necromancer/*			*/(13, Race.Human, ClassType.Mystic, ClassLevel.Third, HumanWizard),
	/*		*/Warlock/*				*/(14, Race.Human, ClassType.Summoner, ClassLevel.Third, HumanWizard),
	/*	*/Cleric/*					*/(15, Race.Human, ClassType.Priest, ClassLevel.Second, HumanMystic),
	/*		*/Bishop/*				*/(16, Race.Human, ClassType.Priest, ClassLevel.Third, Cleric),
	/*		*/Prophet/*				*/(17, Race.Human, ClassType.Priest, ClassLevel.Third, Cleric),
	
	ElvenFighter/*					*/(18, Race.Elf, ClassType.Fighter, ClassLevel.First, null),
	/*	*/ElvenKnight/*				*/(19, Race.Elf, ClassType.Fighter, ClassLevel.Second, ElvenFighter),
	/*		*/TempleKnight/*		*/(20, Race.Elf, ClassType.Fighter, ClassLevel.Third, ElvenKnight),
	/*		*/Swordsinger/*			*/(21, Race.Elf, ClassType.Fighter, ClassLevel.Third, ElvenKnight),
	/*	*/ElvenScout/*				*/(22, Race.Elf, ClassType.Fighter, ClassLevel.Second, ElvenFighter),
	/*		*/Plainswalker/*		*/(23, Race.Elf, ClassType.Fighter, ClassLevel.Third, ElvenScout),
	/*		*/SilverRanger/*		*/(24, Race.Elf, ClassType.Fighter, ClassLevel.Third, ElvenScout),
	
	ElvenMystic/*					*/(25, Race.Elf, ClassType.Mystic, ClassLevel.First, null),
	/*	*/ElvenWizard/*				*/(26, Race.Elf, ClassType.Mystic, ClassLevel.Second, ElvenMystic),
	/*		*/Spellsinger/*			*/(27, Race.Elf, ClassType.Mystic, ClassLevel.Third, ElvenWizard),
	/*		*/ElementalSummoner/*	*/(28, Race.Elf, ClassType.Summoner, ClassLevel.Third, ElvenWizard),
	/*	*/ElvenOracle/*				*/(29, Race.Elf, ClassType.Priest, ClassLevel.Second, ElvenMystic),
	/*		*/ElvenElder/*			*/(30, Race.Elf, ClassType.Priest, ClassLevel.Third, ElvenOracle),
	
	DarkFighter/*					*/(31, Race.Darkelf, ClassType.Fighter, ClassLevel.First, null),
	/*	*/PalusKnight/*				*/(32, Race.Darkelf, ClassType.Fighter, ClassLevel.Second, DarkFighter),
	/*		*/ShillienKnight/*		*/(33, Race.Darkelf, ClassType.Fighter, ClassLevel.Third, PalusKnight),
	/*		*/Bladedancer/*			*/(34, Race.Darkelf, ClassType.Fighter, ClassLevel.Third, PalusKnight),
	/*	*/Assassin/*				*/(35, Race.Darkelf, ClassType.Fighter, ClassLevel.Second, DarkFighter),
	/*		*/AbyssWalker/*			*/(36, Race.Darkelf, ClassType.Fighter, ClassLevel.Third, Assassin),
	/*		*/PhantomRanger/*		*/(37, Race.Darkelf, ClassType.Fighter, ClassLevel.Third, Assassin),
	
	DarkMystic/*					*/(38, Race.Darkelf, ClassType.Mystic, ClassLevel.First, null),
	/*	*/DarkWizard/*				*/(39, Race.Darkelf, ClassType.Mystic, ClassLevel.Second, DarkMystic),
	/*		*/Spellhowler/*			*/(40, Race.Darkelf, ClassType.Mystic, ClassLevel.Third, DarkWizard),
	/*		*/PhantomSummoner/*		*/(41, Race.Darkelf, ClassType.Summoner, ClassLevel.Third, DarkWizard),
	/*	*/ShillienOracle/*			*/(42, Race.Darkelf, ClassType.Priest, ClassLevel.Second, DarkMystic),
	/*		*/ShillienElder/*		*/(43, Race.Darkelf, ClassType.Priest, ClassLevel.Third, ShillienOracle),
	
	OrcFighter/*					*/(44, Race.Orc, ClassType.Fighter, ClassLevel.First, null),
	/*	*/OrcRaider/*				*/(45, Race.Orc, ClassType.Fighter, ClassLevel.Second, OrcFighter),
	/*		*/Destroyer/*			*/(46, Race.Orc, ClassType.Fighter, ClassLevel.Third, OrcRaider),
	/*	*/Monk/*					*/(47, Race.Orc, ClassType.Fighter, ClassLevel.Second, OrcFighter),
	/*		*/Tyrant/*				*/(48, Race.Orc, ClassType.Fighter, ClassLevel.Third, Monk),
	
	OrcMystic/*						*/(49, Race.Orc, ClassType.Mystic, ClassLevel.First, null),
	/*	*/OrcShaman/*				*/(50, Race.Orc, ClassType.Mystic, ClassLevel.Second, OrcMystic),
	/*		*/Overlord/*			*/(51, Race.Orc, ClassType.Mystic, ClassLevel.Third, OrcShaman),
	/*		*/Warcryer/*			*/(52, Race.Orc, ClassType.Mystic, ClassLevel.Third, OrcShaman),
	
	DwarvenFighter/*				*/(53, Race.Dwarf, ClassType.Fighter, ClassLevel.First, null),
	/*	*/Scavenger/*				*/(54, Race.Dwarf, ClassType.Fighter, ClassLevel.Second, DwarvenFighter),
	/*		*/BountyHunter/*		*/(55, Race.Dwarf, ClassType.Fighter, ClassLevel.Third, Scavenger),
	/*	*/Artisan/*					*/(56, Race.Dwarf, ClassType.Fighter, ClassLevel.Second, DwarvenFighter),
	/*		*/Warsmith/*			*/(57, Race.Dwarf, ClassType.Fighter, ClassLevel.Third, Artisan),
	
	dummyEntry1(58),
	dummyEntry2(59),
	dummyEntry3(60),
	dummyEntry4(61),
	dummyEntry5(62),
	dummyEntry6(63),
	dummyEntry7(64),
	dummyEntry8(65),
	dummyEntry9(66),
	dummyEntry10(67),
	dummyEntry11(68),
	dummyEntry12(69),
	dummyEntry13(70),
	dummyEntry14(71),
	dummyEntry15(72),
	dummyEntry16(73),
	dummyEntry17(74),
	dummyEntry18(75),
	dummyEntry19(76),
	dummyEntry20(77),
	dummyEntry21(78),
	dummyEntry22(79),
	dummyEntry23(80),
	dummyEntry24(81),
	dummyEntry25(82),
	dummyEntry26(83),
	dummyEntry27(84),
	dummyEntry28(85),
	dummyEntry29(86),
	dummyEntry30(87),
	
	Duelist/*			*/(88, Race.Human, ClassType.Fighter, ClassLevel.Fourth, Gladiator),
	Dreadnought/*		*/(89, Race.Human, ClassType.Fighter, ClassLevel.Fourth, Warlord),
	PhoenixKnight/*		*/(90, Race.Human, ClassType.Fighter, ClassLevel.Fourth, Paladin),
	HellKnight/*		*/(91, Race.Human, ClassType.Fighter, ClassLevel.Fourth, DarkAvenger),
	Sagittarius/*		*/(92, Race.Human, ClassType.Fighter, ClassLevel.Fourth, Hawkeye),
	Adventurer/*		*/(93, Race.Human, ClassType.Fighter, ClassLevel.Fourth, TreasureHunter),
	Archmage/*			*/(94, Race.Human, ClassType.Mystic, ClassLevel.Fourth, Sorceror),
	Soultaker/*			*/(95, Race.Human, ClassType.Mystic, ClassLevel.Fourth, Necromancer),
	ArcanaLord/*		*/(96, Race.Human, ClassType.Summoner, ClassLevel.Fourth, Warlock),
	Cardinal/*			*/(97, Race.Human, ClassType.Priest, ClassLevel.Fourth, Bishop),
	Hierophant/*		*/(98, Race.Human, ClassType.Priest, ClassLevel.Fourth, Prophet),
	
	EvaTemplar/*		*/(99, Race.Elf, ClassType.Fighter, ClassLevel.Fourth, TempleKnight),
	SwordMuse/*			*/(100, Race.Elf, ClassType.Fighter, ClassLevel.Fourth, Swordsinger),
	WindRider/*			*/(101, Race.Elf, ClassType.Fighter, ClassLevel.Fourth, Plainswalker),
	MoonlightSentinel/*	*/(102, Race.Elf, ClassType.Fighter, ClassLevel.Fourth, SilverRanger),
	MysticMuse/*		*/(103, Race.Elf, ClassType.Mystic, ClassLevel.Fourth, Spellsinger),
	ElementalMaster/*	*/(104, Race.Elf, ClassType.Summoner, ClassLevel.Fourth, ElementalSummoner),
	EvaSaint/*			*/(105, Race.Elf, ClassType.Priest, ClassLevel.Fourth, ElvenElder),
	
	ShillienTemplar/*	*/(106, Race.Darkelf, ClassType.Fighter, ClassLevel.Fourth, ShillienKnight),
	SpectralDancer/*	*/(107, Race.Darkelf, ClassType.Fighter, ClassLevel.Fourth, Bladedancer),
	GhostHunter/*		*/(108, Race.Darkelf, ClassType.Fighter, ClassLevel.Fourth, AbyssWalker),
	GhostSentinel/*		*/(109, Race.Darkelf, ClassType.Fighter, ClassLevel.Fourth, PhantomRanger),
	StormScreamer/*		*/(110, Race.Darkelf, ClassType.Mystic, ClassLevel.Fourth, Spellhowler),
	SpectralMaster/*	*/(111, Race.Darkelf, ClassType.Summoner, ClassLevel.Fourth, PhantomSummoner),
	ShillienSaint/*		*/(112, Race.Darkelf, ClassType.Priest, ClassLevel.Fourth, ShillienElder),
	
	Titan/*				*/(113, Race.Orc, ClassType.Fighter, ClassLevel.Fourth, Destroyer),
	GrandKhavatari/*	*/(114, Race.Orc, ClassType.Fighter, ClassLevel.Fourth, Tyrant),
	Dominator/*			*/(115, Race.Orc, ClassType.Mystic, ClassLevel.Fourth, Overlord),
	Doomcryer/*			*/(116, Race.Orc, ClassType.Mystic, ClassLevel.Fourth, Warcryer),
	
	FortuneSeeker/*		*/(117, Race.Dwarf, ClassType.Fighter, ClassLevel.Fourth, BountyHunter),
	Maestro/*			*/(118, Race.Dwarf, ClassType.Fighter, ClassLevel.Fourth, Warsmith),
	
	dummyEntry31(119),
	dummyEntry32(120),
	dummyEntry33(121),
	dummyEntry34(122),
	
	MaleSoldier/*					*/(123, Race.Kamael, ClassType.Fighter, ClassLevel.First, null),
	FemaleSoldier/*					*/(124, Race.Kamael, ClassType.Fighter, ClassLevel.First, null),
	/*	*/Dragoon/*					*/(125, Race.Kamael, ClassType.Fighter, ClassLevel.Second, MaleSoldier),
	/*	*/Warder/*					*/(126, Race.Kamael, ClassType.Fighter, ClassLevel.Second, FemaleSoldier),
	/*		*/Berserker/*			*/(127, Race.Kamael, ClassType.Fighter, ClassLevel.Third, Dragoon),
	/*		*/MaleSoulBreaker/*		*/(128, Race.Kamael, ClassType.Fighter, ClassLevel.Third, Dragoon),
	/*		*/FemaleSoulBreaker/*	*/(129, Race.Kamael, ClassType.Fighter, ClassLevel.Third, Warder),
	/*		*/Arbalester/*			*/(130, Race.Kamael, ClassType.Fighter, ClassLevel.Third, Warder),
	/*			*/Doombringer/*		*/(131, Race.Kamael, ClassType.Fighter, ClassLevel.Fourth, Berserker),
	/*			*/MaleSoulHound/*	*/(132, Race.Kamael, ClassType.Fighter, ClassLevel.Fourth, MaleSoulBreaker),
	/*			*/FemaleSoulHound/*	*/(133, Race.Kamael, ClassType.Fighter, ClassLevel.Fourth, FemaleSoulBreaker),
	/*			*/Trickster/*		*/(134, Race.Kamael, ClassType.Fighter, ClassLevel.Fourth, Arbalester),
	/*		*/Inspector/*			*/(135, Race.Kamael, ClassType.Fighter, ClassLevel.Third, null),
	/*			*/Judicator/*		*/(136, Race.Kamael, ClassType.Fighter, ClassLevel.Fourth, Inspector);
	
	private final int _id;
	private final Race _race;
	private final ClassType _type;
	private final boolean _isMage;
	private final boolean _isSummoner;
	private final ClassLevel _level;
	private final ClassId _parent;
	
	private ClassId(int id)
	{
		_id = id;
		_race = null;
		_type = null;
		_isMage = false;
		_isSummoner = false;
		_level = null;
		_parent = null;
	}
	
	private ClassId(int id, Race pRace, ClassType pType, ClassLevel pLevel, ClassId parent)
	{
		_id = id;
		_race = pRace;
		_type = pType.getTeachType();
		_isMage = pType.isMage();
		_isSummoner = pType.isSummoner();
		_level = pLevel;
		_parent = parent;
	}
	
	/**
	 * Return the Identifier of the Class.<BR>
	 * <BR>
	 */
	public final int getId()
	{
		return _id;
	}
	
	/**
	 * Return True if the class is a mage class.<BR>
	 * <BR>
	 */
	public final boolean isMage()
	{
		return _isMage;
	}
	
	/**
	 * Return True if the class is a summoner class.<BR>
	 * <BR>
	 */
	public final boolean isSummoner()
	{
		return _isSummoner;
	}
	
	/**
	 * Return the Race object of the class.<BR>
	 * <BR>
	 */
	public final Race getRace()
	{
		return _race;
	}
	
	/**
	 * Return True if this Class is a child of the selected ClassId.<BR>
	 * <BR>
	 * 
	 * @param cid The parent ClassId to check
	 */
	public final boolean childOf(ClassId cid)
	{
		if (_parent == null)
			return false;
		
		if (_parent == cid)
			return true;
		
		return _parent.childOf(cid);
	}
	
	/**
	 * Return True if this Class is equal to the selected ClassId or a child of the selected ClassId.<BR>
	 * <BR>
	 * 
	 * @param cid The parent ClassId to check
	 */
	public final boolean equalsOrChildOf(ClassId cid)
	{
		return this == cid || childOf(cid);
	}
	
	/**
	 * Return the child level of this Class (0=root, 1=child leve 1...).<BR>
	 * <BR>
	 * 
	 * @param cid The parent ClassId to check
	 */
	public final int level()
	{
		return _level.ordinal();
	}
	
	/**
	 * Return its parent ClassId<BR>
	 * <BR>
	 */
	public final ClassId getParent()
	{
		return _parent;
	}
	
	private EnumSet<ClassId> _defaultAvailableSubclasses;
	
	private EnumSet<ClassId> getDefaultAvailableSubclasses()
	{
		return _defaultAvailableSubclasses;
	}
	
	private void setDefaultAvailableSubclasses(EnumSet<ClassId> set)
	{
		_defaultAvailableSubclasses = set;
	}
	
	static
	{
		for (ClassId classId : ClassId.values())
			classId.setDefaultAvailableSubclasses(generateDefaultAvailableSubclasses(classId));
		
		final ClassId[] subclassSet1 = new ClassId[] { DarkAvenger, Paladin, TempleKnight, ShillienKnight };
		for (ClassId classId1 : subclassSet1)
			for (ClassId classId2 : subclassSet1)
				classId1.getDefaultAvailableSubclasses().remove(classId2);
		
		final ClassId[] subclassSet2 = new ClassId[] { TreasureHunter, AbyssWalker, Plainswalker };
		for (ClassId classId1 : subclassSet2)
			for (ClassId classId2 : subclassSet2)
				classId1.getDefaultAvailableSubclasses().remove(classId2);
		
		final ClassId[] subclassSet3 = new ClassId[] { Hawkeye, SilverRanger, PhantomRanger };
		for (ClassId classId1 : subclassSet3)
			for (ClassId classId2 : subclassSet3)
				classId1.getDefaultAvailableSubclasses().remove(classId2);
		
		final ClassId[] subclassSet4 = new ClassId[] { Warlock, ElementalSummoner, PhantomSummoner };
		for (ClassId classId1 : subclassSet4)
			for (ClassId classId2 : subclassSet4)
				classId1.getDefaultAvailableSubclasses().remove(classId2);
		
		final ClassId[] subclassSet5 = new ClassId[] { Sorceror, Spellsinger, Spellhowler };
		for (ClassId classId1 : subclassSet5)
			for (ClassId classId2 : subclassSet5)
				classId1.getDefaultAvailableSubclasses().remove(classId2);
	}
	
	private static EnumSet<ClassId> generateDefaultAvailableSubclasses(ClassId classId)
	{
		if (classId.getLevel() != ClassLevel.Third || classId.getParent() == null)
			return null;
		
		final EnumSet<ClassId> subclasses;
		
		if (classId.getRace() != Race.Kamael)
		{
			subclasses = getSet(null, ClassLevel.Third);
			subclasses.removeAll(getSet(Race.Kamael, null));
			subclasses.remove(Overlord);
			subclasses.remove(Warsmith);
			
			switch (classId.getRace())
			{
				case Elf:
					subclasses.removeAll(getSet(Race.Darkelf, null));
					break;
				case Darkelf:
					subclasses.removeAll(getSet(Race.Elf, null));
					break;
			}
		}
		else
		{
			subclasses = getSet(Race.Kamael, ClassLevel.Third);
			
			//Check sex, male subclasses female and vice versa
			switch (classId.getParent().getParent())
			{
				case MaleSoldier:
					subclasses.remove(MaleSoulBreaker);
					break;
				case FemaleSoldier:
					subclasses.remove(FemaleSoulBreaker);
					break;
				default:
					throw new InternalError();
			}
		}
		
		subclasses.remove(classId);
		
		return subclasses;
	}
	
	public final EnumSet<ClassId> getAvailableSubclasses(L2Player player)
	{
		if (_level != ClassLevel.Third)
			return null;
		
		final EnumSet<ClassId> subclasses = EnumSet.copyOf(getDefaultAvailableSubclasses());
		
		if (_race == Race.Kamael)
		{
			byte subOverLevel75 = 0;
			for (SubClass sc : player.getSubClasses().values())
			{
				if (sc.getLevel() >= 75)
					subOverLevel75++;
			}
			if (subOverLevel75 < 2)
				subclasses.remove(Inspector);
		}
		
		return subclasses;
	}
	
	private static EnumSet<ClassId> getSet(Race race, ClassLevel level)
	{
		EnumSet<ClassId> allOf = EnumSet.noneOf(ClassId.class);
		
		for (ClassId playerClass : ClassId.values())
		{
			if (race == null || playerClass.isOfRace(race))
			{
				if (level == null || playerClass.isOfLevel(level))
				{
					allOf.add(playerClass);
				}
			}
		}
		
		return allOf;
	}
	
	public final boolean isOfRace(Race pRace)
	{
		return _race == pRace;
	}
	
	public final boolean isOfType(ClassType pType)
	{
		return _type == pType;
	}
	
	public final boolean isOfLevel(ClassLevel pLevel)
	{
		return _level == pLevel;
	}
	
	public final ClassLevel getLevel()
	{
		return _level;
	}
}
