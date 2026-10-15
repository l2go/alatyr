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
package com.l2jfree.gameserver.status;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.L2AutoInitialization;
import com.l2jfree.config.L2Properties;
import com.l2jfree.tools.random.SecureRnd;

public class Status extends Thread
{
	private static final Logger _log = LoggerFactory.getLogger(Status.class);
	
	private final ServerSocket statusServerSocket;
	
	private final long _uptime;
	private final int _statusPort;
	private String _statusPw;
	
	@Override
	public void run()
	{
		setPriority(Thread.MAX_PRIORITY);
		while (true)
		{
			try
			{
				Socket connection = statusServerSocket.accept();
				
				new GameStatusThread(connection, _uptime, _statusPw);
				if (isInterrupted())
				{
					try
					{
						statusServerSocket.close();
					}
					catch (IOException io)
					{
						_log.warn(io.getMessage(), io);
					}
					break;
				}
			}
			catch (IOException e)
			{
				if (isInterrupted())
				{
					try
					{
						statusServerSocket.close();
					}
					catch (IOException io)
					{
						_log.warn(io.getMessage(), io);
					}
					break;
				}
			}
		}
	}
	
	public Status() throws IOException
	{
		super("Status");
		L2Properties telnetSettings = new L2Properties(L2AutoInitialization.TELNET_FILE);
		
		_statusPort = Integer.parseInt(telnetSettings.getProperty("StatusPort", "12345"));
		_statusPw = telnetSettings.getProperty("StatusPW");
		if (_statusPw == null)
		{
			_log.warn("Server's Telnet Function Has No Password Defined!");
			_log.warn("A Password Has Been Automaticly Created!");
			_statusPw = generateRandomPassword(10);
			_log.warn("Password Has Been Set To: " + _statusPw);
		}
		_log.info("Telnet StatusServer started successfully, listening on Port: " + _statusPort);
		statusServerSocket = new ServerSocket(_statusPort);
		_uptime = System.currentTimeMillis();
	}
	
	private String generateRandomPassword(int length)
	{
		StringBuilder password = new StringBuilder();
		String lowerChar = "qwertyuiopasdfghjklzxcvbnm";
		String upperChar = "QWERTYUIOPASDFGHJKLZXCVBNM";
		String digits = "1234567890";
		for (int i = 0; i < length; i++)
		{
			int charSet = SecureRnd.nextInt(3);
			switch (charSet)
			{
				case 0:
					password.append(lowerChar.charAt(SecureRnd.nextInt(lowerChar.length() - 1)));
					break;
				case 1:
					password.append(upperChar.charAt(SecureRnd.nextInt(upperChar.length() - 1)));
					break;
				case 2:
					password.append(digits.charAt(SecureRnd.nextInt(digits.length() - 1)));
					break;
			}
		}
		return password.toString();
	}
}
