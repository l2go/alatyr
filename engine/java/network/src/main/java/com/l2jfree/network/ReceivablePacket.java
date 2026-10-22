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
package com.l2jfree.network;

import java.nio.BufferUnderflowException;

import com.l2jfree.network.FloodManager.ErrorMode;

/**
 * @author KenM
 */
public abstract class ReceivablePacket<T extends Connection<T, RP, SP>, RP extends ReceivablePacket<T, RP, SP>, SP extends SendablePacket<T, RP, SP>>
		extends AbstractPacket implements Runnable
{
	protected ReceivablePacket()
	{
	}
	
	private T _client;
	private boolean _direct;
	private boolean _ranDirect;
	private boolean _failed;
	
	final void setClient(T client)
	{
		_client = client;
	}
	
	/**
	 * Binds a bridge-built packet to its client. Null, a second bind, and a second {@link #run()} are rejected.
	 */
	public final void bindDirect(T client)
	{
		if (client == null)
			throw new IllegalArgumentException("client");
		if (_client != null)
			throw new IllegalStateException("already bound");
		
		_client = client;
		_direct = true;
	}
	
	/** True after {@link #run()} failed. The failure is still reported. */
	public final boolean failed()
	{
		return _failed;
	}
	
	public final T getClient()
	{
		return _client;
	}
	
	/** Should be overridden. */
	protected int getMinimumLength()
	{
		return 0;
	}
	
	protected final int getAvaliableBytes()
	{
		return getByteBuffer().remaining();
	}
	
	protected abstract boolean read() throws BufferUnderflowException, RuntimeException;
	
	@SuppressWarnings("unchecked")
	@Override
	public final void run()
	{
		if (_direct)
		{
			if (_ranDirect)
				throw new IllegalStateException("already run");
			_ranDirect = true;
		}
		
		try
		{
			runImpl();
		}
		catch (InvalidPacketException e)
		{
			_failed = true;
			getClient().getNetworkServer().report(ErrorMode.FAILED_RUNNING, getClient(), (RP)this, e);
		}
		catch (RuntimeException e)
		{
			_failed = true;
			getClient().getNetworkServer().report(ErrorMode.FAILED_RUNNING, getClient(), (RP)this, e);
		}
	}
	
	protected abstract void runImpl() throws InvalidPacketException, RuntimeException;
	
	protected final void sendPacket(SP sp)
	{
		getClient().sendPacket(sp);
	}
	
	protected final void skip(int bytes)
	{
		if (getByteBuffer().remaining() < bytes)
			throw new BufferUnderflowException();
		
		getByteBuffer().position(getByteBuffer().position() + bytes);
	}
	
	protected final void skipAll()
	{
		getByteBuffer().position(getByteBuffer().limit());
	}
	
	protected final void readB(byte[] dst)
	{
		getByteBuffer().get(dst);
	}
	
	protected final void readB(byte[] dst, int offset, int len)
	{
		getByteBuffer().get(dst, offset, len);
	}
	
	protected final int readC()
	{
		return getByteBuffer().get() & 0xFF;
	}
	
	protected final int readH()
	{
		return getByteBuffer().getShort() & 0xFFFF;
	}
	
	protected final int readD()
	{
		return getByteBuffer().getInt();
	}
	
	protected final long readQ()
	{
		return getByteBuffer().getLong();
	}
	
	protected final double readF()
	{
		return getByteBuffer().getDouble();
	}
	
	protected final String readS()
	{
		StringBuilder sb = new StringBuilder();
		
		for (char c; (c = getByteBuffer().getChar()) != 0;)
			sb.append(c);
		
		return sb.toString();
	}
}
