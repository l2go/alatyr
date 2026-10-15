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
package com.l2jfree.gameserver.gameobjects.shot;

import com.l2jfree.gameserver.model.items.L2ItemInstance;

/**
 * @author NB4L1
 */
public class ShotState
{
	private static final byte NONE = 0;
	private static final byte CHARGED = 1;
	private static final byte BLESSED_CHARGED = 2;
	
	private byte _soulshotState = NONE;
	private byte _spiritshotState = NONE;
	private byte _fishshotState = NONE;
	
	/**
	 * 
	 */
	
	public boolean isSoulshotCharged()
	{
		return Math.abs(_soulshotState) == CHARGED;
	}
	
	public boolean isSpiritshotCharged()
	{
		return Math.abs(_spiritshotState) == CHARGED;
	}
	
	public boolean isBlessedSpiritshotCharged()
	{
		return Math.abs(_spiritshotState) == BLESSED_CHARGED;
	}
	
	public boolean isAnySpiritshotCharged()
	{
		return isSpiritshotCharged() || isBlessedSpiritshotCharged();
	}
	
	public boolean isFishshotCharged()
	{
		return Math.abs(_fishshotState) == CHARGED;
	}
	
	/**
	 * 
	 */
	
	public void chargeSoulshot(CreatureShots shots, L2ItemInstance consume)
	{
		if (_soulshotState > 0)
			return;
		
		if (shots.canChargeSoulshot(consume))
			_soulshotState = CHARGED;
		else
			_soulshotState = NONE;
	}
	
	public void chargeSpiritshot(CreatureShots shots, L2ItemInstance consume)
	{
		if (_spiritshotState > 0)
			return;
		
		if (shots.canChargeSpiritshot(consume))
			_spiritshotState = CHARGED;
		else
			_spiritshotState = NONE;
	}
	
	public void chargeBlessedSpiritshot(CreatureShots shots, L2ItemInstance consume)
	{
		if (_spiritshotState > 0)
			return;
		
		if (shots.canChargeBlessedSpiritshot(consume))
			_spiritshotState = BLESSED_CHARGED;
		else
			_spiritshotState = NONE;
	}
	
	public void chargeFishshot(CreatureShots shots, L2ItemInstance consume)
	{
		if (_fishshotState > 0)
			return;
		
		if (shots.canChargeFishshot(consume))
			_fishshotState = CHARGED;
		else
			_fishshotState = NONE;
	}
	
	/**
	 * 
	 */
	
	public void useSoulshotCharge()
	{
		_soulshotState = (byte)(Math.abs(_soulshotState) * -1);
	}
	
	public void useSpiritshotCharge()
	{
		_spiritshotState = (byte)(Math.abs(_spiritshotState) * -1);
	}
	
	public void useBlessedSpiritshotCharge()
	{
		_spiritshotState = (byte)(Math.abs(_spiritshotState) * -1);
	}
	
	public void useFishshotCharge()
	{
		_fishshotState = (byte)(Math.abs(_fishshotState) * -1);
	}
	
	/**
	 * 
	 */
	
	public void clearShotCharges()
	{
		_soulshotState = NONE;
		_spiritshotState = NONE;
		_fishshotState = NONE;
	}
	
	/**
	 * 
	 */
	
	private static final NullShotState _instance = new NullShotState();
	
	public static ShotState getEmptyInstance()
	{
		return _instance;
	}
	
	private static class NullShotState extends ShotState
	{
		@Override
		public boolean isSoulshotCharged()
		{
			return false;
		}
		
		@Override
		public boolean isSpiritshotCharged()
		{
			return false;
		}
		
		@Override
		public boolean isBlessedSpiritshotCharged()
		{
			return false;
		}
		
		@Override
		public boolean isAnySpiritshotCharged()
		{
			return false;
		}
		
		@Override
		public boolean isFishshotCharged()
		{
			return false;
		}
		
		/**
		 * 
		 */
		
		@Override
		public void chargeSoulshot(CreatureShots shots, L2ItemInstance consume)
		{
		}
		
		@Override
		public void chargeSpiritshot(CreatureShots shots, L2ItemInstance consume)
		{
		}
		
		@Override
		public void chargeBlessedSpiritshot(CreatureShots shots, L2ItemInstance consume)
		{
		}
		
		@Override
		public void chargeFishshot(CreatureShots shots, L2ItemInstance consume)
		{
		}
		
		/**
		 * 
		 */
		
		@Override
		public void useSoulshotCharge()
		{
		}
		
		@Override
		public void useSpiritshotCharge()
		{
		}
		
		@Override
		public void useBlessedSpiritshotCharge()
		{
		}
		
		@Override
		public void useFishshotCharge()
		{
		}
		
		/**
		 * 
		 */
		
		@Override
		public void clearShotCharges()
		{
		}
	}
}
