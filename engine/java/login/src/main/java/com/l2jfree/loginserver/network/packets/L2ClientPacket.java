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
package com.l2jfree.loginserver.network.packets;

import java.nio.BufferUnderflowException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.loginserver.network.L2Client;
import com.l2jfree.network.ReceivablePacket;

/**
 * @author KenM
 */
public abstract class L2ClientPacket extends
		ReceivablePacket<L2Client, L2ClientPacket, L2ServerPacket>
{
	protected static final Logger _log = LoggerFactory.getLogger(L2ClientPacket.class);
	
	protected L2ClientPacket()
	{
	}
	
	@Override
	protected final boolean read() throws BufferUnderflowException, RuntimeException
	{
		readImpl();
		return true;
	}
	
	protected abstract void readImpl() throws BufferUnderflowException, RuntimeException;
}
