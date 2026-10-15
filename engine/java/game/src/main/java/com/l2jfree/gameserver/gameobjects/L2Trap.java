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

import com.l2jfree.gameserver.gameobjects.templates.L2CreatureTemplate;
import com.l2jfree.gameserver.gameobjects.templates.L2NpcTemplate;
import com.l2jfree.gameserver.gameobjects.view.ICreatureView;
import com.l2jfree.gameserver.gameobjects.view.TrapView;
import com.l2jfree.gameserver.network.packets.server.AbstractNpcInfo;
import com.l2jfree.gameserver.taskmanager.DecayTaskManager;

/**
 *
 * @author nBd
 */
public class L2Trap extends L2Creature
{
	private final L2Player _owner;
	
	/**
	 * @param objectId
	 * @param template
	 */
	public L2Trap(int objectId, L2CreatureTemplate template, L2Player owner)
	{
		super(objectId, template);
		getKnownList();
		getStat();
		getStatus();
		setIsInvul(false);
		_owner = owner;
		getPosition().setXYZInvisible(owner.getX(), owner.getY(), owner.getZ());
	}
	
	@Override
	protected ICreatureView initView()
	{
		return new TrapView(this);
	}
	
	@Override
	public TrapView getView()
	{
		return (TrapView)_view;
	}
	
	/**
	 *
	 * @see com.l2jfree.gameserver.gameobjects.L2Creature#onSpawn()
	 */
	@Override
	public void onSpawn()
	{
		super.onSpawn();
	}
	
	/**
	 *
	 * @see com.l2jfree.gameserver.gameobjects.L2Object#onAction(com.l2jfree.gameserver.gameobjects.L2Player)
	 */
	@Override
	public void onAction(L2Player player)
	{
		player.setTarget(this);
	}
	
	@Override
	public int getMyTargetSelectedColor(L2Player player)
	{
		return player.getLevel() - getLevel();
	}
	
	/**
	 *
	 *
	 */
	public void stopDecay()
	{
		DecayTaskManager.getInstance().cancelDecayTask(this);
	}
	
	/**
	 *
	 * @see com.l2jfree.gameserver.gameobjects.L2Creature#onDecay()
	 */
	@Override
	public void onDecay()
	{
		deleteMe(_owner);
	}
	
	/**
	 *
	 * @return
	 */
	public final int getNpcId()
	{
		return getTemplate().getNpcId();
	}
	
	/**
	 *
	 * @see com.l2jfree.gameserver.gameobjects.L2Object#isAutoAttackable(com.l2jfree.gameserver.gameobjects.L2Creature)
	 */
	@Override
	public boolean isAutoAttackable(L2Creature attacker)
	{
		return _owner.isAutoAttackable(attacker);
	}
	
	/**
	 *
	 * @param owner
	 */
	public void deleteMe(L2Player owner)
	{
		decayMe();
		getKnownList().removeAllKnownObjects();
		owner.setTrap(null);
	}
	
	/**
	 *
	 * @param owner
	 */
	public synchronized void unSummon(L2Player owner)
	{
		if (isVisible() && !isDead())
		{
			if (getWorldRegion() != null)
				getWorldRegion().removeFromZones(this);
			owner.setTrap(null);
			decayMe();
			getKnownList().removeAllKnownObjects();
		}
	}
	
	/**
	 *
	 * @see com.l2jfree.gameserver.gameobjects.L2Creature#getLevel()
	 */
	@Override
	public int getLevel()
	{
		return getTemplate().getLevel();
	}
	
	/**
	 *
	 * @return
	 */
	public final L2Player getOwner()
	{
		return _owner;
	}
	
	@Override
	public L2Player getActingPlayer()
	{
		return _owner;
	}
	
	/**
	 *
	 * @see com.l2jfree.gameserver.gameobjects.L2Creature#getTemplate()
	 */
	@Override
	public L2NpcTemplate getTemplate()
	{
		return (L2NpcTemplate)super.getTemplate();
	}
	
	/**
	 *
	 * @return
	 */
	public boolean isDetected()
	{
		return false;
	}
	
	/**
	 *
	 *
	 */
	public void setDetected()
	{
		// Do nothing
	}
	
	@Override
	public void sendInfo(L2Player activeChar)
	{
		activeChar.sendPacket(new AbstractNpcInfo.TrapInfo(this));
	}
	
	@Override
	public void broadcastFullInfoImpl()
	{
		broadcastPacket(new AbstractNpcInfo.TrapInfo(this));
	}
}
