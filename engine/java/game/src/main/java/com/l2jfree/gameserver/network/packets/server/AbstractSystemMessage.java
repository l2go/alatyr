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

import java.util.Arrays;

import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.items.L2ItemInstance;
import com.l2jfree.gameserver.model.items.templates.L2Item;
import com.l2jfree.gameserver.model.skills.L2Skill;
import com.l2jfree.gameserver.model.skills.effects.L2Effect;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.SystemMessageId;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;
import com.l2jfree.lang.L2Math;

@SuppressWarnings("unchecked")
public abstract class AbstractSystemMessage<T extends AbstractSystemMessage> extends L2ServerPacket
{
	protected static abstract class Element
	{
		public final void write(AbstractSystemMessage sm)
		{
			sm.writeD(getType());
			
			write2(sm);
		}
		
		protected abstract int getType();
		
		protected abstract void write2(AbstractSystemMessage sm);
	}
	
	private static final class TextElement extends Element
	{
		private final String _text;
		
		private TextElement(String text)
		{
			_text = text;
		}
		
		@Override
		protected int getType()
		{
			return TYPE_TEXT;
		}
		
		@Override
		protected void write2(AbstractSystemMessage sm)
		{
			sm.writeS(_text);
		}
	}
	
	private static class NumberElement extends Element
	{
		private final int _number;
		
		private NumberElement(int number)
		{
			_number = number;
		}
		
		@Override
		protected int getType()
		{
			return TYPE_NUMBER;
		}
		
		@Override
		protected final void write2(AbstractSystemMessage sm)
		{
			sm.writeD(_number);
		}
	}
	
	private static final class FortElement extends NumberElement
	{
		private FortElement(int fortId)
		{
			super(fortId);
		}
		
		@Override
		protected int getType()
		{
			return TYPE_FORTRESS;
		}
	}
	
	private static final class NpcElement extends NumberElement
	{
		private NpcElement(int npcId)
		{
			super(npcId);
		}
		
		@Override
		protected int getType()
		{
			return TYPE_NPC_NAME;
		}
	}
	
	private static final class ItemElement extends NumberElement
	{
		private ItemElement(int itemId)
		{
			super(itemId);
		}
		
		@Override
		protected int getType()
		{
			return TYPE_ITEM_NAME;
		}
	}
	
	private static final class LongNumberElement extends Element
	{
		private final long _number;
		
		private LongNumberElement(long number)
		{
			_number = number;
		}
		
		@Override
		protected int getType()
		{
			return TYPE_ITEM_NUMBER;
		}
		
		@Override
		protected void write2(AbstractSystemMessage sm)
		{
			sm.writeCompQ(_number);
		}
	}
	
	private static final class SkillElement extends Element
	{
		private final int _skillId;
		private final int _skillLvl;
		
		private SkillElement(int skillId, int skillLvl)
		{
			_skillId = skillId;
			_skillLvl = skillLvl;
		}
		
		@Override
		protected int getType()
		{
			return TYPE_SKILL_NAME;
		}
		
		@Override
		protected void write2(AbstractSystemMessage sm)
		{
			sm.writeD(_skillId);
			sm.writeD(_skillLvl);
		}
	}
	
	private static final class LocElement extends Element
	{
		private final int _x;
		private final int _y;
		private final int _z;
		
		private LocElement(int x, int y, int z)
		{
			_x = x;
			_y = y;
			_z = z;
		}
		
		@Override
		protected int getType()
		{
			return TYPE_ZONE_NAME;
		}
		
		@Override
		protected void write2(AbstractSystemMessage sm)
		{
			sm.writeD(_x);
			sm.writeD(_y);
			sm.writeD(_z);
		}
	}
	
	// 0 - String
	// 1 - Integer Number
	// 2 - NPC ID (10xxxxx)
	// 3 - Item ID
	// 4 - Skill ID
	// 5 - Fortress ID
	// 6 - Long Number
	// 7 - Zone Name (x, y, z)
	private static final int TYPE_ZONE_NAME = 7;
	private static final int TYPE_ITEM_NUMBER = 6;
	private static final int TYPE_FORTRESS = 5; // maybe not only for fortress, rename if needed
	private static final int TYPE_SKILL_NAME = 4;
	private static final int TYPE_ITEM_NAME = 3;
	private static final int TYPE_NPC_NAME = 2;
	private static final int TYPE_NUMBER = 1;
	private static final int TYPE_TEXT = 0;
	
	protected final int _messageId;
	protected Element[] _elements;
	
	public AbstractSystemMessage(SystemMessageId messageId)
	{
		_messageId = messageId.getId();
		
		_elements = new Element[messageId.size()];
	}
	
	public AbstractSystemMessage(int messageId)
	{
		_messageId = messageId;
		
		SystemMessageId smId = SystemMessageId.getSystemMessageId(messageId, false);
		
		_elements = new Element[smId == null ? 3 : smId.size()];
	}
	
	public int length()
	{
		return _elements.length;
	}
	
	private boolean checkNPE(Object obj)
	{
		if (obj == null)
			_log.warn("", new NullPointerException());
		
		return obj == null;
	}
	
	private void addElement(Element element)
	{
		for (int i = 0; i < _elements.length; i++)
		{
			if (_elements[i] == null)
			{
				_elements[i] = element;
				return;
			}
		}
		
		_log.warn("AbstractSystemMessage: Too much parameter for ID: " + _messageId,
				new ArrayIndexOutOfBoundsException());
		
		_elements = Arrays.copyOf(_elements, _elements.length + 1);
		_elements[_elements.length - 1] = element;
	}
	
	@Override
	public void prepareToSend(L2Client client, L2Player activeChar)
	{
		int count = 0;
		
		for (Element element : _elements)
		{
			if (element == null)
				break;
			
			count++;
		}
		
		if (count == _elements.length)
			return;
		
		_elements = Arrays.copyOf(_elements, count);
		
		_log.warn("AbstractSystemMessage: Empty parameter for ID: " + _messageId, new ArrayIndexOutOfBoundsException());
	}
	
	public T addString(String text)
	{
		if (checkNPE(text))
			return (T)this;
		
		addElement(new TextElement(text));
		
		return (T)this;
	}
	
	public T add(String text)
	{
		return addString(text);
	}
	
	public T addFortId(int number)
	{
		addElement(new FortElement(number));
		
		return (T)this;
	}
	
	public T addItemNumber(long number)
	{
		addElement(new LongNumberElement(number));
		
		return (T)this;
	}
	
	public T addExpNumber(long number)
	{
		addElement(new LongNumberElement(number));
		
		return (T)this;
	}
	
	public T addNumber(double number)
	{
		addElement(new NumberElement(L2Math.limit(Integer.MIN_VALUE, Math.round(number), Integer.MAX_VALUE)));
		
		return (T)this;
	}
	
	public T addNumber(int number)
	{
		addElement(new NumberElement(number));
		
		return (T)this;
	}
	
	public T addCharName(L2Creature cha)
	{
		if (checkNPE(cha))
			return (T)this;
		
		if (cha instanceof L2Npc)
			return addNpcName((L2Npc)cha);
		
		if (cha instanceof L2Player)
			return addPcName((L2Player)cha);
		
		if (cha instanceof L2Summon)
			return addNpcName((L2Summon)cha);
		
		return addString(cha.getName());
	}
	
	public T addPcName(L2Player pc)
	{
		return addString(pc.getAppearance().getVisibleName());
	}
	
	public T addNpcName(L2Npc npc)
	{
		return addNpcName(npc.getTemplate());
	}
	
	public T addNpcName(L2Summon npc)
	{
		return addNpcName(npc.getTemplate());
	}
	
	public T addNpcName(L2NpcTemplate tpl)
	{
		if (tpl.isCustom() || tpl.isServerSideName())
			return addString(tpl.getName());
		
		return addNpcName(tpl.getNpcId());
	}
	
	public T addNpcName(int id)
	{
		addElement(new NpcElement(1000000 + id));
		
		return (T)this;
	}
	
	public T addItemName(L2ItemInstance item)
	{
		if (checkNPE(item))
			return (T)this;
		
		return addItemName(item.getItem());
	}
	
	public T addItemName(L2Item item)
	{
		if (checkNPE(item))
			return (T)this;
		
		if (item.getItemDisplayId() == item.getItemId())
			return addItemName(item.getItemId());
		else
			// Custom item - send custom name
			return addString(item.getName());
	}
	
	public T addItemName(int id)
	{
		addElement(new ItemElement(id));
		
		return (T)this;
	}
	
	public T addZoneName(int x, int y, int z)
	{
		addElement(new LocElement(x, y, z));
		
		return (T)this;
	}
	
	public T addSkillName(L2Effect effect)
	{
		if (checkNPE(effect))
			return (T)this;
		
		return addSkillName(effect.getSkill());
	}
	
	public T addSkillName(L2Skill skill)
	{
		if (checkNPE(skill))
			return (T)this;
		
		if (skill.getId() != skill.getDisplayId()) //custom skill -  need nameId or smth like this.
			return addString(skill.getName());
		
		return addSkillName(skill.getId(), skill.getLevel());
	}
	
	public T addSkillName(int id)
	{
		return addSkillName(id, 1);
	}
	
	public T addSkillName(int id, int lvl)
	{
		addElement(new SkillElement(id, lvl));
		
		return (T)this;
	}
	
	protected final void writeMessageIdAndElements()
	{
		writeD(_messageId);
		writeD(_elements.length);
		for (Element element : _elements)
			element.write(this);
	}
}
