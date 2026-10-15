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
package com.l2jfree.gameserver.geoeditorcon;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Dezmond
 */
public class GeoEditorListener extends Thread
{
	private static final Logger _log = LoggerFactory.getLogger(GeoEditorListener.class);
	
	private static final int PORT = 9011;
	
	private static final class SingletonHolder
	{
		private static final GeoEditorListener INSTANCE = new GeoEditorListener();
	}
	
	public static GeoEditorListener getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private ServerSocket _serverSocket;
	private GeoEditorThread _geoEditor;
	
	private GeoEditorListener()
	{
		try
		{
			_serverSocket = new ServerSocket(PORT);
		}
		catch (IOException e)
		{
			_log.error("Error creating geoeditor listener! ", e);
			System.exit(1);
		}
		start();
		_log.info("GeoEditorListener Initialized.");
	}
	
	public GeoEditorThread getThread()
	{
		return _geoEditor;
	}
	
	public String getStatus()
	{
		if (_geoEditor != null && _geoEditor.isWorking())
		{
			return "Geoeditor connected.";
		}
		return "Geoeditor not connected.";
	}
	
	@Override
	public void run()
	{
		Socket connection = null;
		try
		{
			while (true)
			{
				connection = _serverSocket.accept();
				if (_geoEditor != null && _geoEditor.isWorking())
				{
					_log.warn("Geoeditor already connected!");
					connection.close();
					continue;
				}
				_log.info("Received geoeditor connection from: " + connection.getInetAddress().getHostAddress());
				_geoEditor = new GeoEditorThread(connection);
				_geoEditor.start();
			}
		}
		catch (Exception e)
		{
			_log.info("GeoEditorListener: ", e);
			try
			{
				if (connection != null)
					connection.close();
			}
			catch (Exception e2)
			{
			}
		}
		finally
		{
			try
			{
				_serverSocket.close();
			}
			catch (IOException io)
			{
				_log.info("", io);
			}
			_log.warn("GeoEditorListener Closed!");
		}
	}
}
