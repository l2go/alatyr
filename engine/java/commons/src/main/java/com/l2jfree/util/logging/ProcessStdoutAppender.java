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
package com.l2jfree.util.logging;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import ch.qos.logback.core.OutputStreamAppender;

/**
 * Writes to the standard output of the process, whatever {@link System#out} is set to.
 * <p>
 * The servers redirect {@code System.out} into the logger (see {@code L2AutoInitialization}). Logback's
 * {@code ConsoleAppender} looks up {@code System.out} on every write, so it fed each line back into the logger and took
 * the {@code PrintStream} lock while holding its own lock, which deadlocked against threads printing to
 * {@code System.out}. Stopping this appender flushes the stream but never closes the descriptor.
 */
public class ProcessStdoutAppender<E> extends OutputStreamAppender<E>
{
	private final OutputStream _target;
	
	public ProcessStdoutAppender()
	{
		this(new FileOutputStream(FileDescriptor.out));
	}
	
	ProcessStdoutAppender(OutputStream target)
	{
		_target = target;
	}
	
	@Override
	public void start()
	{
		setOutputStream(new FilterOutputStream(_target) {
			@Override
			public void write(byte[] b, int off, int len) throws IOException
			{
				out.write(b, off, len);
			}
			
			@Override
			public void close() throws IOException
			{
				flush();
			}
		});
		super.start();
	}
}
