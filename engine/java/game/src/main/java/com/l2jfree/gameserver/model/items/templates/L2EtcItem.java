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
package com.l2jfree.gameserver.model.items.templates;

import java.util.ArrayList;

import com.l2jfree.gameserver.datatables.SkillTable;
import com.l2jfree.gameserver.datatables.SkillTable.SkillInfo;
import com.l2jfree.gameserver.templates.StatsSet;
import com.l2jfree.util.L2Collections;

/**
 * This class is dedicated to the management of EtcItem.
 * 
 * @version $Revision: 1.2.2.1.2.3 $ $Date: 2005/03/27 15:30:10 $
 */
public final class L2EtcItem extends L2Item
{
	private final SkillInfo[] _skillInfos;
	private final String _handler;
	
	/**
	 * Constructor for EtcItem.
	 * 
	 * @see L2Item constructor
	 * @param type : L2EtcItemType designating the type of object Etc
	 * @param set : StatsSet designating the set of couples (key,value) for description of the Etc
	 */
	public L2EtcItem(L2EtcItemType type, StatsSet set)
	{
		super(type, set);
		
		_handler = set.getString("handler").replaceAll("none", "").intern();
		
		final ArrayList<SkillInfo> list = L2Collections.newArrayList();
		try
		{
			for (String skillInfo : set.getString("skills_item").split(";"))
			{
				if (skillInfo.isEmpty())
					continue;
				
				final String[] skill = skillInfo.split("-");
				if (skill.length == 2)
				{
					final int skillId = Integer.parseInt(skill[0]);
					final int skillLvl = Integer.parseInt(skill[1]);
					if (skillId > 0 && skillLvl > 0)
					{
						final SkillInfo info = SkillTable.getInstance().getSkillInfo(skillId, skillLvl);
						
						if (info != null)
							list.add(info);
						else
							throw new IllegalStateException("Invalid 'skills_item' parameters at database for ID "
									+ getItemId());
					}
					else if (skillId != 0 || skillLvl != 0)
						throw new IllegalStateException("Invalid 'skills_item' parameters at database for ID "
								+ getItemId());
				}
				else
					throw new IllegalStateException("Invalid 'skills_item' parameters at database for ID "
							+ getItemId());
			}
			
			_skillInfos = list.toArray(new SkillInfo[list.size()]);
		}
		finally
		{
			L2Collections.recycle(list);
		}
	}
	
	/**
	 * Returns the type of Etc Item
	 * 
	 * @return L2EtcItemType
	 */
	@Override
	public L2EtcItemType getItemType()
	{
		return (L2EtcItemType)super._type;
	}
	
	/**
	 * Returns if the item is consumable
	 * 
	 * @return boolean
	 */
	@Override
	public final boolean isConsumable()
	{
		return ((getItemType() == L2EtcItemType.SHOT) || (getItemType() == L2EtcItemType.POTION)); // ||(type==L2EtcItemType.SCROLL));
	}
	
	/**
	 * Returns skills linked to that EtcItem
	 * 
	 * @return
	 */
	public SkillInfo[] getSkillInfos()
	{
		return _skillInfos;
	}
	
	public String getHandlerName()
	{
		return _handler;
	}
}
