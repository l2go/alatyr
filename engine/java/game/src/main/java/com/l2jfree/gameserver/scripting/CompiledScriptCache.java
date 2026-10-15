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
package com.l2jfree.gameserver.scripting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache of Compiled Scripts
 * 
 * @author  KenM
 */
public class CompiledScriptCache implements Serializable
{
	/**
	 * Version 1
	 */
	private static final long serialVersionUID = 1L;
	
	private static final Logger _log = LoggerFactory.getLogger(CompiledScriptCache.class);
	
	private final Map<String, CompiledScriptHolder> _compiledScriptCache = new LinkedHashMap<String, CompiledScriptHolder>();
	private transient boolean _modified = false;
	
	public CompiledScript loadCompiledScript(ScriptEngine engine, File file) throws IOException, ScriptException
	{
		int len = L2ScriptEngineManager.SCRIPT_FOLDER.getPath().length() + 1;
		String relativeName = file.getPath().substring(len);
		
		CompiledScriptHolder csh = _compiledScriptCache.get(relativeName);
		if (csh != null && csh.matches(file))
		{
			if (_log.isDebugEnabled())
				_log.info("Reusing cached compiled script: " + file);
			return csh.getCompiledScript();
		}
		
		if (_log.isDebugEnabled())
			_log.info("Compiling script: " + file);
		Compilable eng = (Compilable)engine;
		CompiledScript cs;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			cs = eng.compile(reader);
		}
		if (cs instanceof Serializable)
		{
			synchronized (_compiledScriptCache)
			{
				_compiledScriptCache.put(relativeName, new CompiledScriptHolder(cs, file));
				_modified = true;
			}
		}
		return cs;
	}
	
	public boolean isModified()
	{
		return _modified;
	}
	
	public void purge()
	{
		synchronized (_compiledScriptCache)
		{
			for (Iterator<String> it = _compiledScriptCache.keySet().iterator(); it.hasNext();)
			{
				String path = it.next();
				File file = new File(L2ScriptEngineManager.SCRIPT_FOLDER, path);
				if (!file.isFile())
				{
					it.remove();
					_modified = true;
				}
			}
		}
	}
	
	public void save() throws IOException
	{
		synchronized (_compiledScriptCache)
		{
			try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(
					L2ScriptEngineManager.SCRIPT_FOLDER, "CompiledScripts.cache"))))
			{
				oos.writeObject(this);
			}
			_modified = false;
		}
	}
}
