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
package com.l2jfree.gameserver.network.packets.server;

import java.util.ArrayList;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Playable;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author NB4L1
 */
public abstract class EffectInfoPacket extends L2ServerPacket
{
	private final EffectInfoPacketList _list;
	
	protected EffectInfoPacket(EffectInfoPacketList list)
	{
		_list = list;
	}
	
	protected final L2Playable getPlayable()
	{
		return _list._playable;
	}
	
	protected final int size()
	{
		return _list.size();
	}
	
	protected final void writeEffectInfos()
	{
		for (int i = 0, size = _list.size(); i < size; i++)
		{
			final L2Effect e = _list.get(i);
			
			final L2Skill skill = e.getSkill();
			
			writeD(skill.getDisplayId());
			writeH(skill.getLevel());
			writeD(e.getPacketTime());
		}
	}
	
	public static final class EffectInfoPacketList extends ArrayList<L2Effect>
	{
		private static final long serialVersionUID = 84397230897018086L;
		
		private final L2Playable _playable;
		
		public EffectInfoPacketList(L2Playable playable)
		{
			super(Config.ALT_BUFFS_MAX_AMOUNT + 5);
			
			_playable = playable;
			_playable.getEffects().addPacket(EffectInfoPacketList.this);
		}
		
		public final void addEffect(L2Effect e)
		{
			add(e);
		}
	}
}
