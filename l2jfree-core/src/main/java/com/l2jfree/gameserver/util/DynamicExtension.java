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
package com.l2jfree.gameserver.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.config.L2Properties;

/**
 * extension loader for l2j
 * 
 * @author galun
 * @version $Id: DynamicExtension.java,v 1.3 2006/05/14 17:19:39 galun Exp $
 */
public class DynamicExtension
{
	private static Logger _log = LoggerFactory.getLogger(DynamicExtension.class);
	private JarClassLoader classLoader;
	private static final String CONFIG = "config/extensions.properties";
	private L2Properties _prop;
	private ConcurrentHashMap<String, Object> _loadedExtensions;
	private final ConcurrentHashMap<String, ExtensionFunction> _getters;
	private final ConcurrentHashMap<String, ExtensionFunction> _setters;
	
	/**
	 * create an instance of DynamicExtension this will be done by GameServer
	 * according to the altsettings.properties
	 * 
	 */
	private DynamicExtension()
	{
		_getters = new ConcurrentHashMap<String, ExtensionFunction>();
		_setters = new ConcurrentHashMap<String, ExtensionFunction>();
		initExtensions();
	}
	
	/**
	 * get the singleton of DynamicInstance
	 * 
	 * @return the singleton instance
	 */
	public static DynamicExtension getInstance()
	{
		return SingletonHolder._instance;
	}
	
	/**
	 * get an extension object by class name
	 * 
	 * @param className he class name as defined in the extension properties
	 * @return the object or null if not found
	 */
	public Object getExtension(String className)
	{
		return _loadedExtensions.get(className);
	}
	
	/**
	 * initialize all configured extensions
	 * 
	 */
	public void initExtensions()
	{
		_prop = new L2Properties();
		_loadedExtensions = new ConcurrentHashMap<String, Object>();
		File file = new File(CONFIG);
		if (!WorldFiles.skip(_log, "extensions", file))
		{
			try (FileInputStream in = new FileInputStream(file))
			{
				_prop.load(in);
			}
			catch (IOException | IllegalArgumentException ex)
			{
				WorldFiles.unreadable(_log, "extensions", file, ex);
			}
		}
		classLoader = new JarClassLoader();
		for (Object o : _prop.keySet())
		{
			String k = (String)o;
			if (k.endsWith("Class"))
			{
				initExtension(_prop.getProperty(k));
			}
		}
	}
	
	/**
	 * init a named extension
	 * 
	 * @param name the class name and optionally a jar file name delimited with
	 *            a '@' if the jar file is not in the class path
	 */
	public void initExtension(String name)
	{
		String className = name;
		String[] p = name.split("@");
		if (p.length > 1)
		{
			classLoader.addJarFile(p[1]);
			className = p[0];
		}
		if (_loadedExtensions.containsKey(className))
			return;
		try
		{
			Class<?> extension = Class.forName(className, true, classLoader);
			Object obj = extension.newInstance();
			extension.getMethod("init", new Class[0]).invoke(obj);
			_log.info("Extension " + className + " loaded.");
			_loadedExtensions.put(className, obj);
		}
		catch (Exception ex)
		{
			_log.warn(name, ex);
		}
	}
	
	/**
	 * create a new class loader which resets the cache (jar files and loaded
	 * classes) on next class loading request it will read the jar again
	 */
	protected void clearCache()
	{
		classLoader = new JarClassLoader();
	}
	
	/**
	 * call unloadExtension() for all known extensions
	 * 
	 */
	public void unloadExtensions()
	{
		for (String e : _loadedExtensions.keySet())
			unloadExtension(e);
	}
	
	/**
	 * unload a named extension
	 * 
	 * @param name the class name and optionally a jar file name delimited with
	 *            a '@'
	 */
	public void unloadExtension(String name)
	{
		String className = name;
		String[] p = name.split("@");
		if (p.length > 1)
		{
			classLoader.addJarFile(p[1]);
			className = p[0];
		}
		try
		{
			Object obj = _loadedExtensions.get(className);
			Class<?> extension = obj.getClass();
			_loadedExtensions.remove(className);
			extension.getMethod("unload", new Class[0]).invoke(obj);
			_log.info("Extension " + className + " unloaded.");
		}
		catch (Exception ex)
		{
			_log.warn("could not unload " + className, ex);
		}
	}
	
	/**
	 * unloads all extensions, resets the cache and initializes all configured
	 * extensions
	 * 
	 */
	public void reload()
	{
		unloadExtensions();
		clearCache();
		initExtensions();
	}
	
	/**
	 * unloads a named extension, resets the cache and initializes the extension
	 * 
	 * @param name the class name and optionally a jar file name delimited with
	 *            a '@' if the jar file is not in the class path
	 */
	public void reload(String name)
	{
		unloadExtension(name);
		clearCache();
		initExtension(name);
	}
	
	/**
	 * register a getter function given a (hopefully) unique name
	 * 
	 * @param name the name of the function
	 * @param function the ExtensionFunction implementation
	 */
	public void addGetter(String name, ExtensionFunction function)
	{
		_getters.put(name, function);
	}
	
	/**
	 * deregister a getter function
	 * 
	 * @param name the name used for registering
	 */
	public void removeGetter(String name)
	{
		_getters.remove(name);
	}
	
	/**
	 * call a getter function registered with DynamicExtension
	 * 
	 * @param name the function name
	 * @param arg a function argument
	 * @return an object from the extension
	 */
	public Object get(String name, String arg)
	{
		ExtensionFunction func = _getters.get(name);
		if (func != null)
			return func.get(arg);
		return "<none>";
	}
	
	/**
	 * register a setter function given a (hopefully) unique name
	 * 
	 * @param name the name of the function
	 * @param function the ExtensionFunction implementation
	 */
	public void addSetter(String name, ExtensionFunction function)
	{
		_setters.put(name, function);
	}
	
	/**
	 * deregister a setter function
	 * 
	 * @param name the name used for registering
	 */
	public void removeSetter(String name)
	{
		_setters.remove(name);
	}
	
	/**
	 * call a setter function registered with DynamicExtension
	 * 
	 * @param name the function name
	 * @param arg a function argument
	 * @param obj an object to set
	 */
	public void set(String name, String arg, Object obj)
	{
		ExtensionFunction func = _setters.get(name);
		if (func != null)
			func.set(arg, obj);
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final DynamicExtension _instance = new DynamicExtension();
	}
}
