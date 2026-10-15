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
package com.l2jfree.gameserver.network.packets.client;

import com.l2jfree.Config;
import com.l2jfree.gameserver.gameobjects.L2Object;
import com.l2jfree.gameserver.gameobjects.L2Player;
import com.l2jfree.gameserver.model.world.L2World;
import com.l2jfree.gameserver.network.packets.L2ClientPacket;

/**
 * This class represents a packet that is sent when an object is "selected"/targeted and
 * <LI>Target is auto-attackable (sword icon when hovering)</LI>
 * <LI>Target is attackable and CTRL key is hold</LI><BR>
 * and a player clicks on that object or uses the attack action.<BR><BR>
 * The coordinates sent are used to check Geodata, but to avoid any exploits
 * the check is done using server-side character's coordinates.
 * 
 * @version $Revision: 1.7.2.1.2.2 $ $Date: 2005/03/27 15:29:30 $
 */
public class AttackRequest extends L2ClientPacket
{
	private static final String _C__ATTACKREQUEST = "[C] 01 AttackRequest c[ddddc]";
	
	private int _objectId;
	
	/*
	    private int _originX;
	    private int _originY;
	    private int _originZ;
	    private boolean _shift;
	*/
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
		/*_originX	= */readD();
		/*_originY	= */readD();
		/*_originZ	= */readD();
		/*_shift	= (readC() == 1);*/readC();
	}
	
	@Override
	protected void runImpl()
	{
		L2Player activeChar = getActiveChar();
		if (activeChar == null)
			return;
		
		// prevent target jumping
		if (activeChar.getLastTargetId() == _objectId
				&& activeChar.getLastTargetTime() > (System.currentTimeMillis() - Config.RETARGET_BLOCKING_PERIOD))
		{
			sendAF();
			return;
		}
		
		// removes spawn protection
		activeChar.onActionRequest();
		
		final L2Object target;
		if (activeChar.getTargetId() == _objectId)
			target = activeChar.getTarget();
		else
			target = L2World.getInstance().findObject(_objectId);
		
		if (target == null)
		{
			sendAF();
			return;
		}
		else if (target instanceof L2Player)
		{
			L2Player tgt = (L2Player)target;
			if (tgt.getAppearance().isInvisible() && !activeChar.isGM())
			{
				sendAF();
				return;
			}
		}
		
		if (!activeChar.isSameInstance(target))
		{
			sendAF();
			return;
		}
		
		if (activeChar.getTarget() != target)
			target.onAction(activeChar);
		else if ((target.getObjectId() != activeChar.getObjectId()) && activeChar.getPrivateStoreType() == 0
				&& activeChar.getActiveRequester() == null)
			target.onForcedAttack(activeChar);
		
		// actually AF is sent after moving to target, just before auto attack starts
		sendAF();
	}
	
	@Override
	public String getType()
	{
		return _C__ATTACKREQUEST;
	}
}
