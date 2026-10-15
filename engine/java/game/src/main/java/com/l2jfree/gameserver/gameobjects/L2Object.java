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

import com.l2jfree.gameserver.gameobjects.knownlist.ObjectKnownList;
import com.l2jfree.gameserver.gameobjects.poly.ObjectPoly;
import com.l2jfree.gameserver.gameobjects.position.ObjectPosition;
import com.l2jfree.gameserver.idfactory.IdFactory;
import com.l2jfree.gameserver.instancemanager.InstanceManager;
import com.l2jfree.gameserver.model.Location;
import com.l2jfree.gameserver.model.entity.Instance;
import com.l2jfree.gameserver.model.restriction.global.GlobalRestrictions;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.model.world.L2WorldRegion;
import com.l2jfree.gameserver.network.packets.server.ActionFailed;
import com.l2jfree.lang.L2Entity;
import com.l2jfree.lang.L2Integer;

/**
 * Mother class of all objects in the world wich ones is it possible to interact (PC, NPC, Item...)<BR>
 * <BR>
 * An object have a visibility, a position and an appearance. An object know several other objects via ObjectKnownList
 * <br>
 * L2Object :<BR>
 * <BR>
 * <li>L2Creature</li>
 * <li>L2ItemInstance</li>
 * <li>L2Potion</li>
 */
public abstract class L2Object implements L2Entity<Integer>
{
	public static final L2Object[] EMPTY_ARRAY = new L2Object[0];
	
	/**
	 * Name of this object
	 */
	private String _name;
	/**
	 * unique identifier
	 */
	private Integer _objectId;
	/**
	 * Appearance and type of object
	 */
	private final ObjectPoly _poly;
	/**
	 * Position of object
	 */
	private final ObjectPosition _position;
	
	// Objects can only see objects in same instancezone, instance 0 is normal world -1 the all seeing world
	private int _instanceId = 0;
	
	/**
	 * Constructor
	 * 
	 * @param objectId
	 */
	protected L2Object(int objectId)
	{
		_objectId = L2Integer.valueOf(objectId);
		_name = "";
		_poly = initPoly();
		_position = initPosition();
	}
	
	/**
	 * Default action played by this object
	 * 
	 * @param player
	 */
	public void onAction(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * @param player
	 */
	public void onActionShift(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Determine default action on forced attack
	 * 
	 * @param player
	 */
	public void onForcedAttack(L2Player player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	/**
	 * Do Nothing.<BR>
	 * <BR>
	 * Determine default actions on spawn <B><U> Overridden in </U> :</B><BR>
	 * <BR>
	 * <li> L2GuardInstance : Set the home location of its L2GuardInstance </li>
	 * <li> L2Attackable : Reset the Spoiled falg </li>
	 * <BR>
	 * <BR>
	 */
	public void onSpawn()
	{
	}
	
	public void firstSpawn()
	{
	}
	
	/**
	 * get the x coordinate for this object
	 * 
	 * @return x
	 */
	public final int getX()
	{
		//if (Config.ASSERT)
		//	assert getPosition().getWorldRegion() != null || _isVisible;
		return getPosition().getX();
	}
	
	/**
	 * get the y coordinate for this object
	 * 
	 * @return y
	 */
	public final int getY()
	{
		//if (Config.ASSERT)
		//	assert getPosition().getWorldRegion() != null || _isVisible;
		return getPosition().getY();
	}
	
	/**
	 * get the z coordinate for this object
	 * 
	 * @return z
	 */
	public final int getZ()
	{
		//if (Config.ASSERT)
		//	assert getPosition().getWorldRegion() != null || _isVisible;
		return getPosition().getZ();
	}
	
	public final int getHeading()
	{
		return getPosition().getHeading();
	}
	
	public final void setHeading(int value)
	{
		getPosition().setHeading(value);
	}
	
	/**
	 * Remove a L2Object from the world.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Remove the L2Object from the world</li>
	 * <BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T REMOVE the object from _allObjects of L2World </B></FONT><BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method DOESN'T SEND Server->Client packets to players</B></FONT><BR>
	 * <BR>
	 * <B><U> Assert </U> :</B><BR>
	 * <BR>
	 * <li> _worldRegion != null <I>(L2Object is visible at the beginning)</I></li>
	 * <BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Delete NPC/PC or Unsummon</li>
	 * <BR>
	 * <BR>
	 */
	public void decayMe()
	{
		//if (Config.ASSERT)
		//	assert getPosition().getWorldRegion() != null;
		
		L2WorldRegion reg = getWorldRegion();
		
		synchronized (this)
		{
			getPosition().clearWorldRegion();
		}
		// this can synchronize on others instances, so it's out of
		// synchronized, to avoid deadlocks
		// Remove the L2Object from the world
		L2World.getInstance().removeVisibleObject(this, reg);
		L2World.getInstance().removeObject(this);
	}
	
	/**
	 * Refresh the object id (ask to IdFactory to release the old id and get a new one)
	 * 
	 * @see com.l2jfree.gameserver.idfactory.IdFactory
	 */
	public void refreshID()
	{
		L2World.getInstance().removeObject(this);
		IdFactory.getInstance().releaseId(getObjectId());
		_objectId = L2Integer.valueOf(IdFactory.getInstance().getNextId());
	}
	
	/**
	 * Init the position of a L2Object spawn and add it in the world as a visible object.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Set the x,y,z position of the L2Object spawn and update its _worldregion </li>
	 * <li>Add the L2Object spawn in the _allobjects of L2World </li>
	 * <li>Add the L2Object spawn to _visibleObjects of its L2WorldRegion</li>
	 * <li>Add the L2Object spawn in the world as a <B>visible</B> object</li>
	 * <BR>
	 * <BR>
	 * <B><U> Assert </U> :</B><BR>
	 * <BR>
	 * <li> _worldRegion == null <I>(L2Object is invisible at the beginning)</I></li>
	 * <BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Create Door</li>
	 * <li> Spawn : Monster, Minion, CTs, Summon...</li>
	 * <BR>
	 */
	private void spawnMe(boolean firstspawn)
	{
		//if (Config.ASSERT)
		//	assert getPosition().getWorldRegion() == null && getPosition().getWorldPosition().getX() != 0
		//		&& getPosition().getWorldPosition().getY() != 0 && getPosition().getWorldPosition().getZ() != 0;
		
		synchronized (this)
		{
			getPosition().updateWorldRegion();
		}
		
		L2World.getInstance().addVisibleObject(this);
		
		if (firstspawn)
			firstSpawn();
		
		onSpawn();
	}
	
	public final void spawnMe()
	{
		spawnMe(false);
	}
	
	/**
	 * Init the position of a L2Object spawn and add it in the world as a visible object.<BR>
	 * <BR>
	 * <B><U> Actions</U> :</B><BR>
	 * <BR>
	 * <li>Set the x,y,z position of the L2Object spawn and update its _worldregion </li>
	 * <li>Add the L2Object spawn in the _allobjects of L2World </li>
	 * <li>Add the L2Object spawn to _visibleObjects of its L2WorldRegion</li>
	 * <li>Add the L2Object spawn in the world as a <B>visible</B> object</li>
	 * <BR>
	 * <BR>
	 * <B><U> Assert </U> :</B><BR>
	 * <BR>
	 * <li> _worldRegion == null <I>(L2Object is invisible at the beginning)</I></li>
	 * <BR>
	 * <BR>
	 * <B><U> Example of use </U> :</B><BR>
	 * <BR>
	 * <li> Create Door</li>
	 * <li> Spawn : Monster, Minion, CTs, Summon...</li>
	 * <BR>
	 * 
	 * @param x
	 * @param y
	 * @param z
	 */
	public final void spawnMe(int x, int y, int z, boolean firstspawn)
	{
		synchronized (this)
		{
			getPosition().setWorldPosition(x, y, z);
		}
		
		spawnMe(firstspawn);
	}
	
	public final void spawnMe(int x, int y, int z)
	{
		spawnMe(x, y, z, false);
	}
	
	/**
	 * If the object is visible, decay it. It not, spawn it.
	 */
	public void toggleVisible()
	{
		if (isVisible())
			decayMe();
		else
			spawnMe();
	}
	
	/**
	 * Tell if this object is attackable or not. By default, L2Object are not attackable.
	 * 
	 * @return
	 */
	public boolean isAttackable()
	{
		return false;
	}
	
	/**
	 * Return True if the L2Creature is autoAttackable
	 * 
	 * @param attacker
	 * @return true if L2Creature is autoAttackable, false otherwise
	 */
	public boolean isAutoAttackable(L2Creature attacker)
	{
		return false;
	}
	
	/**
	 * Return the visibilty state of the L2Object. <BR>
	 * <BR>
	 * <B><U> Concept</U> :</B><BR>
	 * <BR>
	 * A L2Object is visble if <B>_worldregion</B>!=null <BR>
	 * <BR>
	 */
	public final boolean isVisible()
	{
		//return getPosition().getWorldRegion() != null && _isVisible;
		return getPosition().getWorldRegion() != null;
	}
	
	public boolean isMoving()
	{
		return false;
	}
	
	/**
	 * Return the known list of object from this instance
	 * 
	 * @return an objectKnownList
	 */
	public ObjectKnownList getKnownList()
	{
		return ObjectKnownList.getInstance();
	}
	
	/**
	 * return the name
	 * 
	 * @return the name
	 */
	public final String getName()
	{
		return _name;
	}
	
	/**
	 * @param value the name to set
	 */
	public void setName(String name)
	{
		_name = (name == null ? "" : name.intern());
	}
	
	/**
	 * @return the object id
	 */
	public final Integer getObjectId()
	{
		return _objectId;
	}
	
	protected final ObjectPoly initPoly()
	{
		return new ObjectPoly();
	}
	
	/**
	 * @return the appearance
	 */
	public final ObjectPoly getPoly()
	{
		return _poly;
	}
	
	protected final ObjectPosition initPosition()
	{
		return new ObjectPosition(this);
	}
	
	/**
	 * @return the position
	 */
	public final ObjectPosition getPosition()
	{
		return _position;
	}
	
	/**
	 * @return reference to region this object is in
	 */
	public final L2WorldRegion getWorldRegion()
	{
		return getPosition().getWorldRegion();
	}
	
	/**
	 * @return The id of the instance zone the object is in - id 0 is global since everything like dropped items, mobs,
	 *         players can be in a instanciated area, it must be in l2object
	 */
	public int getInstanceId()
	{
		return _instanceId;
	}
	
	/**
	 * @param instanceId The id of the instance zone the object is in - id 0 is global
	 */
	public void setInstanceId(int instanceId)
	{
		if (_instanceId == instanceId)
			return;
		
		if (this instanceof L2Player)
		{
			if (_instanceId != instanceId)
			{
				Instance inst;
				if (_instanceId > 0)
				{
					inst = InstanceManager.getInstance().getInstance(_instanceId);
					if (inst != null)
						inst.removePlayer(getObjectId());
				}
				if (instanceId > 0)
				{
					inst = InstanceManager.getInstance().getInstance(instanceId);
					if (inst != null)
						inst.addPlayer(getObjectId());
				}
				GlobalRestrictions.instanceChanged(getActingPlayer(), _instanceId, instanceId);
			}
		}
		else if (this instanceof L2Npc)
		{
			if (_instanceId != instanceId)
			{
				Instance inst;
				if (_instanceId > 0)
				{
					inst = InstanceManager.getInstance().getInstance(_instanceId);
					if (inst != null)
						inst.removeNpc((L2Npc)this);
				}
				if (instanceId > 0)
				{
					inst = InstanceManager.getInstance().getInstance(instanceId);
					if (inst != null)
						inst.addNpc((L2Npc)this);
				}
			}
		}
		
		_instanceId = instanceId;
		
		// If we change it for visible objects, me must clear & revalidate knownlists
		if (isVisible())
		{
			if (this instanceof L2Player)
			{
				// We don't want some ugly looking disappear/appear effects, so don't update
				// the knownlist here, but players usually enter instancezones through teleporting
				// and the teleport will do the revalidation for us.
				
				final L2Summon pet = getActingSummon();
				if (pet != null)
				{
					pet.decayMe();
					pet.spawnMe();
				}
			}
			else
			{
				decayMe();
				spawnMe();
			}
		}
	}
	
	/**
	 * Basic implementation of toString to print the object id
	 */
	@Override
	public String toString()
	{
		StringBuilder tb = new StringBuilder();
		tb.append("(");
		tb.append(getClass().getSimpleName());
		tb.append(") ");
		tb.append(getObjectId());
		tb.append(" - ");
		tb.append(getName());
		
		return tb.toString();
	}
	
	/**
	 * Sends the Server->Client info packet for the object.<br>
	 * <br>
	 */
	public void sendInfo(L2Player activeChar)
	{
	}
	
	public L2Creature getActingCharacter()
	{
		return null;
	}
	
	public final static L2Creature getActingCharacter(L2Object obj)
	{
		return (obj == null ? null : obj.getActingCharacter());
	}
	
	public L2Player getActingPlayer()
	{
		return null;
	}
	
	public final static L2Player getActingPlayer(L2Object obj)
	{
		return (obj == null ? null : obj.getActingPlayer());
	}
	
	public L2Summon getActingSummon()
	{
		return null;
	}
	
	public final static L2Summon getActingSummon(L2Object obj)
	{
		return (obj == null ? null : obj.getActingSummon());
	}
	
	public boolean isInFunEvent()
	{
		L2Player player = getActingPlayer();
		return (player != null && player.isInFunEvent());
	}
	
	public Location getLoc()
	{
		return new Location(getX(), getY(), getZ(), 0);
	}
	
	@Override
	public Integer getPrimaryKey()
	{
		return getObjectId();
	}
	
	// TODO: for subclasses
	public void reset()
	{
	}
	
	public int getMyTargetSelectedColor(L2Player player)
	{
		return 0;
	}
	
	public boolean isInInstance()
	{
		return getInstanceId() > 0;
	}
	
	public boolean isInMultiverse()
	{
		return isInMultiverse(getInstanceId());
	}
	
	public boolean isSameInstance(L2Object object)
	{
		if (object == null)
			return false;
		return isSameInstance(object.getInstanceId());
	}
	
	public boolean isSameInstance(int instanceId)
	{
		return (getInstanceId() == instanceId || isInMultiverse() || isInMultiverse(instanceId));
	}
	
	public static final boolean isSameInstance(L2Object object1, L2Object object2)
	{
		if (object1 == null || object2 == null)
			return false;
		return object1.isSameInstance(object2);
	}
	
	public static final boolean isInMultiverse(int instanceId)
	{
		return instanceId == -1;
	}
}
