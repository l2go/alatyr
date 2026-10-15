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
package com.l2jfree.gameserver.model.skills.learn;

/**
 * This class ...
 * 
 * @version $Revision: 0.0.0.1 $ $Date: 2008/03/19 15:10:30 $
 */
public final class L2TransformSkillLearn
{
	// these two build the primary key
	private final int _race_id;
	private final int _skill_id;
	private final int _item_id;
	private final int _level;
	
	// not needed, just for easier debug
	private final String _name;
	
	private final int _sp;
	private final int _min_level;
	
	public L2TransformSkillLearn(int race_id, int skill_id, int item_id, int level, String name, int sp, int min_level)
	{
		_race_id = race_id;
		_skill_id = skill_id;
		_item_id = item_id;
		_level = level;
		_name = name.intern();
		_sp = sp;
		_min_level = min_level;
	}
	
	/**
	 * @return Returns the skill_id.
	 */
	public int getId()
	{
		return _skill_id;
	}
	
	/**
	 * @return Returns the level.
	 */
	public int getLevel()
	{
		return _level;
	}
	
	/**
	 * @return Returns the minLevel.
	 */
	public int getMinLevel()
	{
		return _min_level;
	}
	
	/**
	 * @return Returns the name.
	 */
	public String getName()
	{
		return _name;
	}
	
	/**
	 * @return Returns the spCost.
	 */
	public int getSpCost()
	{
		return _sp;
	}
	
	public int getRace()
	{
		return _race_id;
	}
	
	public int getItemId()
	{
		return _item_id;
	}
	
	public static boolean isTransformSkill(int id)
	{
		switch (id)
		{
			case 541: // Transform Grail Apostle
			case 542: // Transform Grail Apostle
			case 543: // Transform Grail Apostle
			case 544: // Transform Unicorn
			case 545: // Transform Unicorn
			case 546: // Transform Unicorn
			case 547: // Transform Lilim Knight
			case 548: // Transform Lilim Knight
			case 549: // Transform Lilim Knight
			case 550: // Transform Golem Guardian
			case 551: // Transform Golem Guardian
			case 552: // Transform Golem Guardian
			case 553: // Transform Inferno Drake
			case 554: // Transform Inferno Drake
			case 555: // Transform Inferno Drake
			case 556: // Transform Dragon Bomber
			case 557: // Transform Dragon Bomber
			case 558: // Transform Dragon Bomber
			case 617: // Transform Onyx Beast
			case 618: // Transform Death Blader
			case 663: // Transfomr Zaken
			case 664: // Transform Anakim
			case 665: // Transform Benom
			case 666: // Transform Gordon
			case 667: // Transform Ranku
			case 668: // Transform Kiyachi
			case 669: // Transform Demon Prince
			case 670: // Transform Heretic
			case 671: // Transform Vale Master
			case 672: // Transform Saber Tooth Tiger
			case 673: // Transform Ol Mahum
			case 674: // Transform Doll Blader
				return true;
		}
		return false;
	}
}
