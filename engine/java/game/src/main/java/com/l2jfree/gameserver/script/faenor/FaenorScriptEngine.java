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
package com.l2jfree.gameserver.script.faenor;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import com.l2jfree.Config;
import com.l2jfree.gameserver.GameServer;
import com.l2jfree.gameserver.script.Parser;
import com.l2jfree.gameserver.script.ParserNotCreatedException;
import com.l2jfree.gameserver.script.ScriptDocument;
import com.l2jfree.gameserver.script.ScriptEngine;
import com.l2jfree.gameserver.script.ScriptPackage;

/**
 * @author Luis Arias
 *
 */
public class FaenorScriptEngine extends ScriptEngine
{
	private static Logger _log = LoggerFactory.getLogger(GameServer.class);
	public static String PACKAGE_DIRECTORY = "data/faenor/";
	
	private LinkedList<ScriptDocument> _scripts;
	
	public static FaenorScriptEngine getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private FaenorScriptEngine()
	{
		_log.info("FaenorScriptEngine: initialized");
		_scripts = new LinkedList<ScriptDocument>();
		loadPackages();
		parsePackages();
	}
	
	public void reloadPackages()
	{
		_scripts.clear();
		_scripts = new LinkedList<ScriptDocument>();
		parsePackages();
	}
	
	private void loadPackages()
	{
		File packDirectory = new File(Config.DATAPACK_ROOT, PACKAGE_DIRECTORY);
		
		FileFilter fileFilter = new FileFilter() {
			@Override
			public boolean accept(File file)
			{
				return file.getName().endsWith(".zip");
			}
		};
		
		File[] files = packDirectory.listFiles(fileFilter);
		if (files == null)
			return;
		ZipFile zipPack;
		
		for (File element : files)
		{
			try
			{
				zipPack = new ZipFile(element);
			}
			catch (ZipException e)
			{
				_log.error(e.getMessage(), e);
				continue;
			}
			catch (IOException e)
			{
				_log.error(e.getMessage(), e);
				continue;
			}
			
			ScriptPackage module = new ScriptPackage(zipPack);
			
			List<ScriptDocument> scripts = module.getScriptFiles();
			for (ScriptDocument script : scripts)
			{
				_scripts.add(script);
			}
			
			try
			{
				zipPack.close();
			}
			catch (IOException e)
			{
			}
		}
	}
	
	public void parsePackages()
	{
		for (ScriptDocument script : _scripts)
		{
			parseScript(script);
		}
	}
	
	public void parseScript(ScriptDocument script)
	{
		if (_log.isDebugEnabled())
			_log.debug("Parsing Script: " + script.getName());
		
		Node node = script.getDocument().getFirstChild();
		String parserClass = "faenor.Faenor" + node.getNodeName() + "Parser";
		
		Parser parser = null;
		try
		{
			parser = createParser(parserClass);
		}
		catch (ParserNotCreatedException e)
		{
			_log.warn("ERROR: No parser registered for Script: " + parserClass, e);
		}
		
		if (parser == null)
		{
			_log.warn("Unknown Script Type: " + script.getName());
			return;
		}
		
		try
		{
			parser.parseScript(node);
			if (_log.isDebugEnabled())
				_log.debug(script.getName() + "Script Successfully Parsed.");
		}
		catch (Exception e)
		{
			_log.warn("Script Parsing Failed.", e);
		}
	}
	
	@Override
	public String toString()
	{
		if (_scripts.isEmpty())
			return "No Packages Loaded.";
		
		String out = "Script Packages currently loaded:\n";
		
		for (ScriptDocument script : _scripts)
		{
			out += script;
		}
		return out;
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final FaenorScriptEngine _instance = new FaenorScriptEngine();
	}
}
