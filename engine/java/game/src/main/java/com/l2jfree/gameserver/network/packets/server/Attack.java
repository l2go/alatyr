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

import org.apache.commons.lang3.ArrayUtils;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Creature;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.network.packets.L2ServerPacket;

/**
 * @author Forsaiken
 */
public final class Attack extends L2ServerPacket
{
	private static final String _S__06_ATTACK = "[S] 33 Attack";
	
	public static final int HITFLAG_USESS = 0x10;
	public static final int HITFLAG_CRIT = 0x20;
	public static final int HITFLAG_SHLD = 0x40;
	public static final int HITFLAG_MISS = 0x80;
	
	public final class Hit
	{
		private final int _targetId;
		private final int _damage;
		private final int _flags;
		
		private Hit(L2Object target, int damage, boolean miss, boolean crit, byte shld)
		{
			_targetId = target.getObjectId();
			_damage = damage;
			_flags = getFlags(target, miss, crit, shld);
		}
		
		private final int getFlags(L2Object target, boolean miss, boolean crit, byte shld)
		{
			if (miss)
				return HITFLAG_MISS;
			int flags = 0;
			if (soulshot)
				flags = HITFLAG_USESS | Attack.this._ssGrade;
			if (crit)
				flags |= HITFLAG_CRIT;
			// dirty fix for lags on olympiad
			if (shld > 0 && !(target instanceof L2Player && ((L2Player)target).isInOlympiadMode()))
				flags |= HITFLAG_SHLD;
			return flags;
		}
	}
	
	private final int _attackerObjId;
	private final int _targetObjId;
	private final int _x;
	private final int _y;
	private final int _z;
	private final int _tx;
	private final int _ty;
	private final int _tz;
	
	public final boolean soulshot;
	public final int _ssGrade;
	
	private Hit[] _hits;
	
	/**
	 * @param attacker the attacking L2Creature
	 * @param target the target L2Object
	 * @param useShots true if soulshots used
	 * @param ssGrade the grade of the soulshots
	 */
	public Attack(L2Creature attacker, L2Object target, boolean useShots, int ssGrade)
	{
		_attackerObjId = attacker.getObjectId();
		_targetObjId = target.getObjectId();
		soulshot = useShots;
		_ssGrade = ssGrade;
		_x = attacker.getX();
		_y = attacker.getY();
		_z = attacker.getZ();
		_tx = target.getX();
		_ty = target.getY();
		_tz = target.getZ();
	}
	
	public Hit createHit(L2Object target, int damage, boolean miss, boolean crit, byte shld)
	{
		return new Hit(target, damage, miss, crit, shld);
	}
	
	public void hit(Hit... hits)
	{
		if (_hits == null)
		{
			_hits = hits;
			return;
		}
		
		// this will only happen with pole attacks
		_hits = ArrayUtils.addAll(hits, _hits);
	}
	
	/** @return True if the Server-Client packet Attack contains at least 1 hit. */
	public boolean hasHits()
	{
		return _hits != null;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x33);
		
		writeD(_attackerObjId);
		writeD(_targetObjId);
		writeD(_hits[0]._damage);
		writeC(_hits[0]._flags);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		
		writeH(_hits.length - 1);
		// prevent sending useless packet while there is only one target.
		if (_hits.length > 1)
		{
			for (int i = 1; i < _hits.length; i++)
			{
				writeD(_hits[i]._targetId);
				writeD(_hits[i]._damage);
				writeC(_hits[i]._flags);
			}
		}
		
		if (Config.PACKET_FINAL)
		{
			writeD(_tx);
			writeD(_ty);
			writeD(_tz);
		}
	}
	
	@Override
	public String getType()
	{
		return _S__06_ATTACK;
	}
}
