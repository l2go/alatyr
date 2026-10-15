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

import java.nio.ByteBuffer;

/**
 * @author KenM
 */
public abstract class SendablePacket<T extends Connection<T, RP, SP>, RP extends ReceivablePacket<T, RP, SP>, SP extends SendablePacket<T, RP, SP>>
		extends AbstractPacket
{
	/**
	 * The buffer a packet writes into belongs to the thread that writes the packet, not to the packet. A server packet
	 * instance is often sent to many connections (a static packet, a broadcast), and with several I/O threads two of
	 * them can write the same instance at the same time.
	 */
	private static final ThreadLocal<ByteBuffer> WRITING = new ThreadLocal<ByteBuffer>();
	
	protected SendablePacket()
	{
	}
	
	/**
	 * Makes the buffer the target of the packets this thread writes.
	 * 
	 * @return the previous target, to be given to {@link #restoreBuffer(ByteBuffer)}
	 */
	static ByteBuffer attachBuffer(ByteBuffer buf)
	{
		final ByteBuffer previous = WRITING.get();
		WRITING.set(buf);
		return previous;
	}
	
	static void restoreBuffer(ByteBuffer previous)
	{
		if (previous == null)
			WRITING.remove();
		else
			WRITING.set(previous);
	}
	
	@Override
	protected final ByteBuffer getByteBuffer()
	{
		return WRITING.get();
	}
	
	protected final void writeC(boolean value)
	{
		getByteBuffer().put((byte)(value ? 1 : 0));
	}
	
	protected final void writeC(int value)
	{
		getByteBuffer().put((byte)value);
	}
	
	protected final void writeH(boolean value)
	{
		getByteBuffer().putShort((short)(value ? 1 : 0));
	}
	
	protected final void writeH(int value)
	{
		getByteBuffer().putShort((short)value);
	}
	
	protected final void writeD(boolean value)
	{
		getByteBuffer().putInt(value ? 1 : 0);
	}
	
	protected final void writeD(int value)
	{
		getByteBuffer().putInt(value);
	}
	
	protected final void writeD(long value)
	{
		getByteBuffer().putInt(value < Integer.MAX_VALUE ? (int)value : Integer.MAX_VALUE);
	}
	
	protected final void writeQ(boolean value)
	{
		getByteBuffer().putLong(value ? 1 : 0);
	}
	
	protected final void writeQ(long value)
	{
		getByteBuffer().putLong(value);
	}
	
	protected final void writeF(double value)
	{
		getByteBuffer().putDouble(value);
	}
	
	protected final void writeB(byte[] data)
	{
		getByteBuffer().put(data);
	}
	
	/**
	 * Same as {@link SendablePacket#writeS(CharSequence)}, except that <code>'\000'</code> won't be written automatically.<br>
	 * So this way there is no need to concat multiple Strings into a single one.
	 * 
	 * @param charSequence
	 */
	@SuppressWarnings("unchecked")
	protected final SP append(CharSequence charSequence)
	{
		putChars(charSequence);
		
		return (SP)this;
	}
	
	protected final void writeS(CharSequence... charSequences)
	{
		if (charSequences != null)
			for (CharSequence charSequence : charSequences)
				putChars(charSequence);
		
		getByteBuffer().putChar('\000');
	}
	
	protected final void writeS(CharSequence charSequence)
	{
		putChars(charSequence);
		
		getByteBuffer().putChar('\000');
	}
	
	private void putChars(CharSequence charSequence)
	{
		if (charSequence == null)
			return;
		
		final int length = charSequence.length();
		for (int i = 0; i < length; i++)
			getByteBuffer().putChar(charSequence.charAt(i));
	}
	
	protected abstract void write(T client) throws RuntimeException;
}
