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

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Npc;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.gameobjects.L2Summon;
import com.l2jfree.gameserver.gameobjects.L2Trap;
import com.l2jfree.gameserver.gameobjects.instance.L2MonsterInstance;
import com.l2jfree.gameserver.gameobjects.itemcontainer.Inventory;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.model.skills.effects.AbnormalEffect;
import com.l2jfree.gameserver.network.L2Client;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

public abstract class AbstractNpcInfo extends L2ServerPacket
{
	private static final String _S__22_NPCINFO = "[S] 0c NpcInfo";
	
	protected int _x, _y, _z, _heading;
	protected int _idTemplate;
	protected boolean _isSummoned;
	protected int _mAtkSpd, _pAtkSpd;
	
	/**
	 * Run speed, swimming run speed and flying run speed
	 */
	protected int _runSpd;
	
	/**
	 * Walking speed, swimming walking speed and flying walking speed
	 */
	protected int _walkSpd;
	
	protected int _rhand, _lhand, _chest;
	protected double _collisionHeight, _collisionRadius;
	protected String _name = "";
	protected String _title = "";
	
	public AbstractNpcInfo(L2Creature cha)
	{
		_isSummoned = cha.isShowSummonAnimation();
		_x = cha.getX();
		_y = cha.getY();
		_z = cha.getZ();
		_heading = cha.getHeading();
		_mAtkSpd = cha.getMAtkSpd();
		_pAtkSpd = cha.getPAtkSpd();
		_runSpd = cha.getTemplate().getBaseRunSpd();
		_walkSpd = cha.getTemplate().getBaseWalkSpd();
	}
	
	/* (non-Javadoc)
	 * @see com.l2jfree.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__22_NPCINFO;
	}
	
	/**
	 * Packet for Npcs
	 */
	public static class NpcInfo extends AbstractNpcInfo
	{
		private final L2Npc _npc;
		
		public NpcInfo(L2Npc cha)
		{
			super(cha);
			_npc = cha;
			_idTemplate = cha.getTemplate().getIdTemplate(); // On every subclass
			_rhand = cha.getRightHandItem(); // On every subclass
			_lhand = cha.getLeftHandItem(); // On every subclass
			_collisionHeight = cha.getCollisionHeight(); // On every subclass
			_collisionRadius = cha.getCollisionRadius(); // On every subclass
			
			if (cha.getTemplate().isServerSideName())
				_name = cha.getTemplate().getName(); // On every subclass
				
			if (cha.isChampion())
			{
				_title = Config.CHAMPION_TITLE; // On every subclass
			}
			else if (cha.getTemplate().isServerSideTitle())
			{
				_title = cha.getTemplate().getTitle(); // On every subclass
			}
			else
			{
				_title = cha.getTitle(); // On every subclass
			}
			
			if (Config.SHOW_NPC_LVL && _npc instanceof L2MonsterInstance)
			{
				String t = "Lvl " + cha.getLevel();
				if (cha.getAggroRange() > 0)
				{
					if (!cha.isChampion() || (cha.isChampion() && !Config.CHAMPION_PASSIVE))
						t += "*";
				}
				
				if (_title != null && !_title.isEmpty())
					t += " " + _title;
				
				_title = t;
			}
		}
		
		@Override
		protected void writeImpl(L2Client client, L2Player activeChar)
		{
			writeC(0x0c);
			writeD(_npc.getObjectId());
			writeD(_idTemplate + 1000000); // npctype id
			writeD(_npc.isAutoAttackable(activeChar) ? 1 : 0);
			writeD(_x);
			writeD(_y);
			writeD(_z);
			writeD(_heading);
			writeD(0x00);
			writeD(_mAtkSpd);
			writeD(_pAtkSpd);
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // swim run speed
			writeD(_walkSpd); // swim walk speed
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly walk speed
			writeF(_npc.getStat().getMovementSpeedMultiplier());
			writeF(_npc.getStat().getAttackSpeedMultiplier());
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_rhand); // right hand weapon
			writeD(_chest);
			writeD(_lhand); // left hand weapon
			writeC(1); // name above char 1=true ... ??
			writeC(_npc.isRunning() ? 1 : 0);
			writeC(_npc.isInCombat() ? 1 : 0);
			writeC(_npc.isAlikeDead() ? 1 : 0);
			writeC(_isSummoned ? 2 : 0); // 0=teleported 1=default 2=summoned
			writeS(_name);
			writeS(_title);
			writeD(0x00); // Title color 0=client default
			writeD(0x00);
			writeD(0x00); // pvp flag
			
			writeD(_npc.getAbnormalEffect()); // C2
			writeD(0x00); //clan id
			writeD(0x00); //crest id
			writeD(0000); // C2
			writeD(0000); // C2
			writeC(_npc.isFlying() ? 2 : 0); // C2
			writeC(0x00); // title color 0=client
			
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_npc.getWeaponEnchantLevel()); // C4
			writeD(_npc.isFlying() ? 1 : 0); // C6
			writeD(0x00);
			writeD(0x00);// CT1.5 Pet form and skills
			if (Config.PACKET_FINAL)
			{
				writeC(0x01);
				writeC(0x01);
				writeD(_npc.getSpecialEffect());
			}
		}
		
		@Override
		public boolean canBeSentTo(L2Client client, L2Player activeChar)
		{
			if (!activeChar.canSee(_npc))
				return false;
			
			return true;
		}
	}
	
	public static class TrapInfo extends AbstractNpcInfo
	{
		private final L2Trap _trap;
		
		public TrapInfo(L2Trap cha)
		{
			super(cha);
			_trap = cha;
			_idTemplate = cha.getTemplate().getIdTemplate();
			_rhand = 0;
			_lhand = 0;
			_collisionHeight = _trap.getTemplate().getCollisionHeight();
			_collisionRadius = _trap.getTemplate().getCollisionRadius();
			_title = cha.getOwner().getName();
			_runSpd = _trap.getStat().getRunSpeed();
			_walkSpd = _trap.getStat().getWalkSpeed();
		}
		
		@Override
		protected void writeImpl(L2Client client, L2Player activeChar)
		{
			writeC(0x0c);
			writeD(_trap.getObjectId());
			writeD(_idTemplate + 1000000); // npctype id
			writeD(_trap.isAutoAttackable(activeChar) ? 1 : 0);
			writeD(_x);
			writeD(_y);
			writeD(_z);
			writeD(_heading);
			writeD(0x00);
			writeD(_mAtkSpd);
			writeD(_pAtkSpd);
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // swim run speed
			writeD(_walkSpd); // swim walk speed
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly walk speed
			writeF(1.1/*_trap.getStat().getMovementSpeedMultiplier()*/);
			writeF(_trap.getStat().getAttackSpeedMultiplier());
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_rhand); // right hand weapon
			writeD(_chest);
			writeD(_lhand); // left hand weapon
			writeC(1); // name above char 1=true ... ??
			writeC(1);
			writeC(_trap.isInCombat() ? 1 : 0);
			writeC(_trap.isAlikeDead() ? 1 : 0);
			writeC(_isSummoned ? 2 : 0); //  0=teleported  1=default   2=summoned
			writeS(_name);
			writeS(_title);
			writeD(0x00); // title color 0 = client default
			
			writeD(0x00);
			writeD(0x00); // pvp flag
			
			writeD(_trap.getAbnormalEffect()); // C2
			writeD(0x00); //clan id
			writeD(0x00); //crest id
			writeD(0000); // C2
			writeD(0000); // C2
			writeD(0000); // C2
			writeC(0000); // C2
			
			writeC(0x00); // Title color 0=client default
			
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(0x00); // C4
			writeD(0x00); // C6
			writeD(0x00);
			writeD(0);//CT1.5 Pet form and skills
			if (Config.PACKET_FINAL)
			{
				writeC(0x01);
				writeC(0x01);
				writeD(0x00);
			}
		}
		
		@Override
		public boolean canBeSentTo(L2Client client, L2Player activeChar)
		{
			if (!activeChar.canSee(_trap))
				return false;
			
			return true;
		}
	}
	
	/**
	 * Packet for summons
	 */
	public static class SummonInfo extends AbstractNpcInfo
	{
		private final L2Summon _summon;
		private int _form = 0;
		private int _val = 0;
		
		public SummonInfo(L2Summon cha, int val)
		{
			super(cha);
			_summon = cha;
			_val = val;
			
			int npcId = cha.getTemplate().getNpcId();
			
			if (npcId == 16041 || npcId == 16042)
			{
				if (cha.getLevel() > 84)
					_form = 3;
				else if (cha.getLevel() > 79)
					_form = 2;
				else if (cha.getLevel() > 74)
					_form = 1;
			}
			else if (npcId == 16025 || npcId == 16037)
			{
				if (cha.getLevel() > 69)
					_form = 3;
				else if (cha.getLevel() > 64)
					_form = 2;
				else if (cha.getLevel() > 59)
					_form = 1;
			}
			
			// fields not set on AbstractNpcInfo
			_rhand = cha.getWeapon();
			_lhand = 0;
			_chest = cha.getArmor();
			_collisionHeight = _summon.getTemplate().getCollisionHeight();
			_collisionRadius = _summon.getTemplate().getCollisionRadius();
			_name = cha.getName();
			_title = cha.getOwner() != null ? (cha.getOwner().isOnline() == 0 ? "" : cha.getOwner().getName()) : ""; // when owner online, summon will show in title owner name
			_idTemplate = cha.getTemplate().getIdTemplate();
			
			_collisionHeight = cha.getTemplate().getCollisionHeight();
			_collisionRadius = cha.getTemplate().getCollisionRadius();
			
			// few fields needing fix from AbstractNpcInfo
			_runSpd = cha.getPetSpeed();
			_walkSpd = cha.isMountable() ? 45 : 30;
		}
		
		@Override
		protected void writeImpl(L2Client client, L2Player activeChar)
		{
			writeC(0x0c);
			writeD(_summon.getObjectId());
			writeD(_idTemplate + 1000000); // npctype id
			writeD(_summon.isAutoAttackable(activeChar) ? 1 : 0);
			writeD(_x);
			writeD(_y);
			writeD(_z);
			writeD(_heading);
			writeD(0x00);
			writeD(_mAtkSpd);
			writeD(_pAtkSpd);
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // swim run speed
			writeD(_walkSpd); // swim walk speed
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly walk speed
			writeF(_summon.getStat().getMovementSpeedMultiplier());
			writeF(_summon.getStat().getAttackSpeedMultiplier());
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(_rhand); // right hand weapon
			writeD(_chest);
			writeD(_lhand); // left hand weapon
			writeC(1); // name above char 1=true ... ??
			writeC(1);
			writeC(_summon.isInCombat() ? 1 : 0);
			writeC(_summon.isAlikeDead() ? 1 : 0);
			writeC(_val); //  0=teleported  1=default   2=summoned
			writeS(_name);
			writeS(_title);
			writeD(0x01);// Title color 0=client default
			
			writeD(_summon.getPvpFlag());
			writeD(_summon.getKarma());
			
			if (_summon.getOwner().getAppearance().isInvisible())
			{
				writeD((_summon.getAbnormalEffect() | AbnormalEffect.STEALTH.getMask()));
			}
			else
			{
				writeD(_summon.getAbnormalEffect());
			}
			writeD(_summon.getOwner().getClanId());
			writeD(0x00); //crest id
			writeD(0000); // C2
			writeD(0000); // C2
			writeC(0000); // C2
			
			writeC(_summon.getTeam());
			writeF(_collisionRadius);
			writeF(_collisionHeight);
			writeD(0x00); // C4
			writeD(0x00); // C6
			writeD(0x00);
			writeD(_form);//CT1.5 Pet form and skills
			if (Config.PACKET_FINAL)
			{
				writeC(0x01);
				writeC(0x01);
				writeD(0x00);
			}
		}
		
		@Override
		public boolean canBeSentTo(L2Client client, L2Player activeChar)
		{
			// Owner gets PetInfo
			if (_summon.getOwner() == activeChar)
				return false;
			
			if (!activeChar.canSee(_summon))
				return false;
			
			return true;
		}
	}
	
	/**
	 * Packet for morphed PCs
	 */
	public static class PlayerMorphInfo extends AbstractNpcInfo
	{
		private final L2Player _pc;
		private final L2NpcTemplate _template;
		
		public PlayerMorphInfo(L2Player cha, L2NpcTemplate template)
		{
			super(cha);
			_pc = cha;
			_template = template;
		}
		
		@Override
		protected void writeImpl(L2Client client, L2Player activeChar)
		{
			writeC(0x0c);
			writeD(_pc.getObjectId());
			writeD(_pc.getPoly().getPolyId() + 1000000); // npctype id
			writeD(_pc.isAutoAttackable(activeChar) ? 1 : 0);
			writeD(_x);
			writeD(_y);
			writeD(_z);
			writeD(_heading);
			writeD(0x00);
			writeD(_mAtkSpd);
			writeD(_pAtkSpd);
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // swim run speed
			writeD(_walkSpd); // swim walk speed
			writeD(_runSpd);
			writeD(_walkSpd);
			writeD(_runSpd); // fly run speed
			writeD(_walkSpd); // fly walk speed
			writeF(_pc.getStat().getMovementSpeedMultiplier());
			writeF(_pc.getStat().getAttackSpeedMultiplier());
			writeF(_template.getCollisionRadius());
			writeF(_template.getCollisionHeight());
			writeD(_pc.getInventory().getPaperdollItemDisplayId(Inventory.PAPERDOLL_RHAND)); // right hand weapon
			writeD(0); // Chest
			writeD(_pc.getInventory().getPaperdollItemDisplayId(Inventory.PAPERDOLL_LHAND)); // left hand weapon
			writeC(1); // name above char 1=true ... ??
			writeC(_pc.isRunning() ? 1 : 0);
			writeC(_pc.isInCombat() ? 1 : 0);
			writeC(_pc.isAlikeDead() ? 1 : 0);
			writeC(0);
			writeS(_pc.getAppearance().getVisibleName());
			writeS(_pc.getAppearance().getVisibleTitle());
			
			writeD(0x00); // Title Color
			writeD(_pc.getKarma());
			writeD(_pc.getPvpFlag());
			
			if (_pc.getAppearance().isInvisible())
			{
				writeD((_pc.getAbnormalEffect() | AbnormalEffect.STEALTH.getMask()));
			}
			else
			{
				writeD(_pc.getAbnormalEffect()); // C2
			}
			
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			writeC(0x00);
			writeC(0x00);
			writeF(_template.getCollisionRadius());
			writeF(_template.getCollisionHeight());
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			writeD(0x00);
			if (Config.PACKET_FINAL)
			{
				writeC(0x01);
				writeC(0x01);
				writeD(0x00);
			}
		}
		
		@Override
		public boolean canBeSentTo(L2Client client, L2Player activeChar)
		{
			// Won't work
			if (_pc == activeChar)
				return false;
			
			if (!activeChar.canSee(_pc))
				return false;
			
			return true;
		}
	}
}
