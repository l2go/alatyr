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
package com.l2jfree;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.l2jfree.config.ConfigProperty;
import com.l2jfree.config.L2Properties;
import com.l2jfree.io.RedirectingOutputStream.BufferedRedirectingOutputStream;
import com.l2jfree.lang.L2Math;
import com.l2jfree.util.HandlerRegistry;

/**
 * @author evill33t
 */
public abstract class L2AutoInitialization
{
	public static final int LOGIN_PROTOCOL_L2J = 258;
	
	/** Current network protocol version */
	// protocol 1 does not support connection filtering
	public static final int LOGIN_PROTOCOL_CURRENT = 2;
	
	public static final String TELNET_FILE = "./config/telnet.properties";
	
	public static Level EXTENDED_LOG_LEVEL = Level.OFF;
	
	protected static final org.slf4j.Logger _log;
	public static final PrintStream out = System.out;
	public static final PrintStream err = System.err;
	
	static
	{
		Locale.setDefault(Locale.ENGLISH);
		Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e)
			{
				System.err.print("Exception in thread \"" + t.getName() + "\" ");
				
				e.printStackTrace(System.err);
				
				// restart automatically
				if (e instanceof Error && !(e instanceof StackOverflowError))
					Runtime.getRuntime().halt(2);
			}
		});
		
		if (System.getProperty("user.name").equals("root") && System.getProperty("user.home").equals("/root"))
		{
			System.out.print("L2Jfree servers should not run under root-account ... exited.");
			System.exit(-1);
		}
		
		final Map<String, List<String>> conflicts = ClassPathConflicts.find(System.getProperty("java.class.path"));
		
		if (!conflicts.isEmpty())
		{
			System.out.println("Server should not run with classpath conflicts! "
					+ "(rename/remove possible conflicting classpath entries)");
			
			for (List<String> names : conflicts.values())
				for (String name : names)
					System.out.println("\t'" + name + "'");
			
			System.exit(-1);
		}
		
		System.setProperty("line.separator", "\r\n");
		System.setProperty("file.encoding", "UTF-8");
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
		
		_log = LoggerFactory.getLogger(L2AutoInitialization.class);
		_log.info("logging initialized");
		System.setOut(new PrintStream(new BufferedRedirectingOutputStream() {
			@Override
			protected void handleLine(String line)
			{
				LoggerFactory.getLogger("system.stdout").info(line);
			}
		}));
		
		System.setErr(new PrintStream(new BufferedRedirectingOutputStream() {
			@Override
			protected void handleLine(String line)
			{
				final StackTraceElement caller = getCaller();
				
				String source = caller == null ? "" : caller.getClassName() + "." + caller.getMethodName() + "(): ";
				LoggerFactory.getLogger("system.stderr").warn(source + line);
			}
		}));
	}
	
	private static StackTraceElement getCaller()
	{
		StackTraceElement[] stack = new Throwable().getStackTrace();
		
		for (int i = stack.length - 1; i >= 0; i--)
		{
			if (stack[i].getClassName().startsWith("java.io.") || stack[i].getMethodName().equals("printStackTrace"))
				return stack[L2Math.limit(0, i + 1, stack.length - 1)];
			
			if (stack[i].getMethodName().equals("dispatchUncaughtException"))
				break;
		}
		
		return null;
	}
	
	protected L2AutoInitialization()
	{
	}
	
	private static final HandlerRegistry<String, ConfigLoader> _loaders = new HandlerRegistry<String, ConfigLoader>(
			true) {
		@Override
		public String standardizeKey(String key)
		{
			return key.trim().toLowerCase();
		}
	};
	
	protected static void registerConfig(ConfigLoader loader)
	{
		_loaders.register(loader.getName(), loader);
	}
	
	public static void loadConfigs() throws Exception
	{
		for (ConfigLoader loader : _loaders.getHandlers().values())
			loader.load();
	}
	
	public static String loadConfig(String name) throws Exception
	{
		final ConfigLoader loader = _loaders.get(name);
		
		if (loader == null)
			throw new Exception();
		
		try
		{
			loader.load();
			return "'" + loader.getName() + "' reloaded!";
		}
		catch (Exception e)
		{
			return e.getMessage();
		}
	}
	
	public static String getLoaderNames()
	{
		return StringUtils.join(_loaders.getHandlers().keySet().iterator(), "|");
	}
	
	protected static abstract class ConfigLoader
	{
		protected abstract String getName();
		
		protected abstract void load() throws Exception;
		
		@Override
		public final int hashCode()
		{
			return getClass().hashCode();
		}
		
		@Override
		public final boolean equals(Object obj)
		{
			return getClass().equals(obj.getClass());
		}
	}
	
	protected static abstract class ConfigFileLoader extends ConfigLoader
	{
		protected abstract String getFileName();
		
		@Override
		protected final void load() throws Exception
		{
			_log.info("loading '" + getFileName() + "'");
			
			try
			{
				loadReader(openReader());
			}
			catch (Exception e)
			{
				_log.error("Failed to load '" + getFileName() + "'!", e);
				
				throw new Exception("Failed to load '" + getFileName() + "'!");
			}
		}
		
		/** Opens the file. A subclass that resolves the file against the operator directory overrides it. */
		protected BufferedReader openReader() throws IOException
		{
			return new BufferedReader(new FileReader(getFileName()));
		}
		
		protected abstract void loadReader(BufferedReader reader) throws Exception;
	}
	
	protected static abstract class ConfigPropertiesLoader extends ConfigFileLoader
	{
		@Override
		protected final String getFileName()
		{
			return "./config/" + getName().trim() + ".properties";
		}
		
		/** Reads the file through the defaults and operator directories and hands the merged keys on. */
		@Override
		protected final BufferedReader openReader() throws IOException
		{
			final StringWriter merged = new StringWriter();
			new L2Properties(getFileName()).store(merged, null);
			return new BufferedReader(new StringReader(merged.toString()));
		}
		
		protected Class<?>[] getAnnotatedClasses()
		{
			return new Class<?>[] { getClass().getEnclosingClass() };
		}
		
		@Override
		protected final void loadReader(BufferedReader reader) throws Exception
		{
			final L2Properties properties = new L2Properties(reader);
			
			for (Class<?> cl : getAnnotatedClasses())
			{
				for (Field field : cl.getFields())
				{
					final ConfigProperty configProperty = field.getAnnotation(ConfigProperty.class);
					
					if (configProperty == null || !configProperty.loader().equals(getName()))
						continue;
					
					if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()))
					{
						_log.warn("Invalid modifiers for " + field);
						continue;
					}
					
					field.set(null, properties.getProperty(field.getType(), configProperty));
				}
			}
			
			loadImpl(properties);
		}
		
		protected abstract void loadImpl(L2Properties properties);
	}
	
	public static boolean isIDEMode()
	{
		return Files.exists(Paths.get("pom.xml"));
	}
}
