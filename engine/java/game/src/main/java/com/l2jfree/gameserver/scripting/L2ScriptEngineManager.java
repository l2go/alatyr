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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.io.LineNumberReader;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.l2jfree.Config;
import com.sun.script.jython.JythonScriptEngineFactory;

/**
 * Caches script engines and provides funcionality for executing and managing scripts.<BR>
 *
 * @author  KenM
 */
public final class L2ScriptEngineManager
{
	private static final Logger _log = LoggerFactory.getLogger(L2ScriptEngineManager.class);
	
	public final static File SCRIPT_FOLDER;
	
	static
	{
		SCRIPT_FOLDER = new File(Config.DATAPACK_ROOT.getAbsolutePath(), "data/scripts");
	}
	
	public static L2ScriptEngineManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private final Map<String, ScriptEngine> _nameEngines = new LinkedHashMap<String, ScriptEngine>();
	private final Map<String, ScriptEngine> _extEngines = new LinkedHashMap<String, ScriptEngine>();
	private final List<ScriptManager<?>> _scriptManagers = new LinkedList<ScriptManager<?>>();
	
	private final CompiledScriptCache _cache;
	
	private final LongAdder _scriptLoadAttempts = new LongAdder();
	private final LongAdder _scriptLoadSuccesses = new LongAdder();
	private final LongAdder _scriptLoadFailures = new LongAdder();
	private volatile ClassLoader _packagedJavaScriptLoader;
	
	// Configs
	// TODO move to config file
	/**
	 * Informs(logs) the scripts being loaded.<BR>
	 * Apply only when executing script from files.<BR>
	 */
	private final boolean VERBOSE_LOADING = false;
	
	/**
	 * If the script engine supports compilation the script is compiled before execution.<BR>
	 */
	private final boolean ATTEMPT_COMPILATION = true;
	
	/**
	* Use Compiled Scripts Cache.<BR>
	* Only works if ATTEMPT_COMPILATION is true.<BR>
	* DISABLED DUE ISSUES (if a superclass file changes subclasses are not recompiled where they should)
	*/
	private final boolean USE_COMPILED_CACHE = false;
	
	/**
	 * Clean an previous error log(if such exists) for the script being loaded before trying to load.<BR>
	 * Apply only when executing script from files.<BR>
	 */
	private final boolean PURGE_ERROR_LOG = true;
	
	private L2ScriptEngineManager()
	{
		if (USE_COMPILED_CACHE)
		{
			_cache = loadCompiledScriptCache();
		}
		else
		{
			_cache = null;
		}
		_log.info("Initializing Script Engine Manager");
		// One factory, chosen here. ServiceLoader would also see the factory inside jython-standalone,
		// and the two disagree on which class serves the python extension.
		registerEngine(new JythonScriptEngineFactory());
		
		preConfigure();
	}
	
	private void registerEngine(ScriptEngineFactory factory)
	{
		_log.info("Script Engine: " + factory.getEngineName() + " " + factory.getEngineVersion() + " - Language: "
				+ factory.getLanguageName() + " " + factory.getLanguageVersion());
		
		ScriptEngine engine = factory.getScriptEngine();
		for (String name : factory.getNames())
		{
			if (_nameEngines.containsKey(name))
				throw new IllegalStateException("Script engine name is already registered: " + name);
			_nameEngines.put(name, engine);
		}
		for (String extension : factory.getExtensions())
		{
			if (_extEngines.containsKey(extension))
				throw new IllegalStateException("Script engine extension is already registered: " + extension);
			_extEngines.put(extension, engine);
		}
	}
	
	private void preConfigure()
	{
		// java class path
		
		// Jython sys.path
		String dataPackDirForwardSlashes = SCRIPT_FOLDER.getPath().replaceAll("\\\\", "/");
		String configScript = "import sys;sys.path.insert(0,'" + dataPackDirForwardSlashes + "');";
		try
		{
			this.eval("jython", configScript);
		}
		catch (ScriptException e)
		{
			_log.error("Failed preconfiguring jython: ", e);
		}
	}
	
	private ScriptEngine getEngineByName(String name)
	{
		return _nameEngines.get(name);
	}
	
	private ScriptEngine getEngineByExtension(String ext)
	{
		return _extEngines.get(ext);
	}
	
	public void executeScriptList(File list) throws IOException
	{
		long attemptsBefore = _scriptLoadAttempts.sum();
		long successesBefore = _scriptLoadSuccesses.sum();
		long failuresBefore = _scriptLoadFailures.sum();
		if (!list.isFile())
			return;
		
		if (list.isFile())
		{
			try (LineNumberReader lnr = new LineNumberReader(Files.newBufferedReader(list.toPath(), StandardCharsets.UTF_8)))
			{
				String line;
				File file;
			
			while ((line = lnr.readLine()) != null)
			{
				String[] parts = line.trim().split("#");
				
				if (parts.length > 0 && !parts[0].startsWith("#") && parts[0].length() > 0)
				{
					line = parts[0];
					
					if (line.endsWith("/**"))
					{
						line = line.substring(0, line.length() - 3);
					}
					else if (line.endsWith("/*"))
					{
						line = line.substring(0, line.length() - 2);
					}
					
					file = new File(SCRIPT_FOLDER, line);
					
					if (file.isDirectory() && parts[0].endsWith("/**"))
					{
						this.executeAllScriptsInDirectory(file, true, 32);
					}
					else if (file.isDirectory() && parts[0].endsWith("/*"))
					{
						this.executeAllScriptsInDirectory(file);
					}
					else if (file.isFile())
					{
						long attemptsAtCall = _scriptLoadAttempts.sum();
						try
						{
							this.executeScript(file);
						}
						catch (ScriptException e)
						{
							// executeScript records an attempt before a failure inside the load.
							// A rejection before that, such as a Java file with no packaged
							// bytecode, must still count as one attempt and one failure.
							if (_scriptLoadAttempts.sum() == attemptsAtCall)
							{
								_scriptLoadAttempts.increment();
								_scriptLoadFailures.increment();
							}
							reportScriptFileError(file, e);
						}
					}
					else
					{
						_log.warn("Failed loading: (" + file.getCanonicalPath() + ") @ " + list.getName() + ":"
								+ lnr.getLineNumber() + " - Reason: doesnt exists or is not a file.");
					}
				}
			}
			}
		}
		_log.info("Script loading summary: " + (_scriptLoadSuccesses.sum() - successesBefore) + " loaded, "
				+ (_scriptLoadFailures.sum() - failuresBefore) + " failed from "
				+ (_scriptLoadAttempts.sum() - attemptsBefore) + " attempted.");
	}
	
	public void executeAllScriptsInDirectory(File dir)
	{
		this.executeAllScriptsInDirectory(dir, false, 0);
	}
	
	public void executeAllScriptsInDirectory(File dir, boolean recurseDown, int maxDepth)
	{
		this.executeAllScriptsInDirectory(dir, recurseDown, maxDepth, 0);
	}
	
	private void executeAllScriptsInDirectory(File dir, boolean recurseDown, int maxDepth, int currentDepth)
	{
		if (dir.isDirectory())
		{
			File[] files = dir.listFiles();
			if (files == null)
			{
				_log.warn("Unable to list script directory: " + dir.getAbsolutePath());
				return;
			}
			Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(File::getName));
			for (File file : files)
			{
				if (file.isDirectory() && recurseDown && maxDepth > currentDepth)
				{
					if (VERBOSE_LOADING)
					{
						_log.info("Entering folder: " + file.getName());
					}
					this.executeAllScriptsInDirectory(file, recurseDown, maxDepth, currentDepth + 1);
				}
				else if (file.isFile())
				{
					try
					{
						String name = file.getName();
						int lastIndex = name.lastIndexOf('.');
						String extension;
						if (lastIndex != -1)
						{
							extension = name.substring(lastIndex + 1);
							ScriptEngine engine = getEngineByExtension(extension);
							if ("java".equalsIgnoreCase(extension) || engine != null)
							{
								this.executeScript(file);
							}
						}
					}
					catch (IOException e)
					{
						// should never happen
						_log.error(e.getMessage(), e);
					}
					catch (ScriptException e)
					{
						reportScriptFileError(file, e);
						//_log.error(e.getMessage(),e);
					}
				}
			}
		}
		else
		{
			throw new IllegalArgumentException("The argument directory either doesnt exists or is not an directory.");
		}
	}
	
	public CompiledScriptCache getCompiledScriptCache()
	{
		return _cache;
	}
	
	public CompiledScriptCache loadCompiledScriptCache()
	{
		if (USE_COMPILED_CACHE)
		{
			File file = new File(SCRIPT_FOLDER, "CompiledScripts.cache");
			if (file.isFile())
			{
				ObjectInputStream ois = null;
				try
				{
					ois = new ObjectInputStream(new FileInputStream(file));
					CompiledScriptCache cache = (CompiledScriptCache)ois.readObject();
					return cache;
				}
				catch (InvalidClassException e)
				{
					_log.error("Failed loading Compiled Scripts Cache, invalid class (Possibly outdated).", e);
				}
				catch (IOException e)
				{
					_log.error("Failed loading Compiled Scripts Cache from file.", e);
				}
				catch (ClassNotFoundException e)
				{
					_log.error("Failed loading Compiled Scripts Cache, class not found.", e);
				}
				finally
				{
					IOUtils.closeQuietly(ois);
				}
				return new CompiledScriptCache();
			}
			
			return new CompiledScriptCache();
		}
		
		return null;
	}
	
	public void executeScript(File file) throws ScriptException, IOException
	{
		String name = file.getName();
		int lastIndex = name.lastIndexOf('.');
		String extension;
		if (lastIndex != -1)
		{
			extension = name.substring(lastIndex + 1);
		}
		else
		{
			throw new ScriptException("Script file (" + name
					+ ") doesnt has an extension that identifies the ScriptEngine to be used.");
		}
		
		if ("java".equalsIgnoreCase(extension))
		{
			if (!hasPackagedJavaScript(file))
				throw new ScriptException("Java script (" + file.getPath()
						+ ") has no packaged bytecode. Java scripts are compiled in CI and are not compiled at startup.");
			this.executePackagedJavaScript(file);
			return;
		}
		
		ScriptEngine engine = getEngineByExtension(extension);
		if (engine == null)
			throw new ScriptException("No engine registered for extension (" + extension + ")");
		
		this.executeScript(engine, file);
	}
	
	public void executeScript(String engineName, File file) throws IOException, ScriptException
	{
		ScriptEngine engine = getEngineByName(engineName);
		if (engine == null)
			throw new ScriptException("No engine registered with name (" + engineName + ")");
		
		this.executeScript(engine, file);
	}
	
	public void executeScript(ScriptEngine engine, File file) throws IOException, ScriptException
	{
		if ("java".equalsIgnoreCase(getExtension(file)) && hasPackagedJavaScript(file))
		{
			this.executePackagedJavaScript(file);
			return;
		}
		_scriptLoadAttempts.increment();
		boolean loaded = false;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)))
		{
			if (VERBOSE_LOADING)
		{
			_log.info("Loading Script: " + file.getAbsolutePath());
		}
		
		if (PURGE_ERROR_LOG)
		{
			String name = file.getAbsolutePath() + ".error.log";
			File errorLog = new File(name);
			if (errorLog.isFile())
			{
				errorLog.delete();
			}
		}
		
		if (engine instanceof Compilable && ATTEMPT_COMPILATION)
		{
			ScriptContext context = new SimpleScriptContext();
			context.setAttribute("mainClass", getClassForFile(file).replace('/', '.').replace('\\', '.'),
					ScriptContext.ENGINE_SCOPE);
			context.setAttribute(ScriptEngine.FILENAME, file.getName(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("classpath", getScriptClassPath(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("sourcepath", SCRIPT_FOLDER.getAbsolutePath(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("parentLoader", ClassLoader.getSystemClassLoader(), ScriptContext.ENGINE_SCOPE);
			
			ScriptContext ctx = engine.getContext();
			try
			{
				engine.setContext(context);
				ManagedScript.loading(file, () -> {
					CompiledScript cs = USE_COMPILED_CACHE ? _cache.loadCompiledScript(engine, file)
							: ((Compilable)engine).compile(reader);
					return cs.eval(context);
				});
			}
			finally
			{
				engine.setContext(ctx);
				context.removeAttribute(ScriptEngine.FILENAME, ScriptContext.ENGINE_SCOPE);
				context.removeAttribute("mainClass", ScriptContext.ENGINE_SCOPE);
				context.removeAttribute("parentLoader", ScriptContext.ENGINE_SCOPE);
			}
		}
		else
		{
			ScriptContext context = new SimpleScriptContext();
			context.setAttribute("mainClass", getClassForFile(file).replace('/', '.').replace('\\', '.'),
					ScriptContext.ENGINE_SCOPE);
			context.setAttribute(ScriptEngine.FILENAME, file.getName(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("classpath", getScriptClassPath(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("sourcepath", SCRIPT_FOLDER.getAbsolutePath(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("parentLoader", ClassLoader.getSystemClassLoader(), ScriptContext.ENGINE_SCOPE);
			try
			{
				ManagedScript.loading(file, () -> engine.eval(reader, context));
			}
			finally
			{
				engine.getContext().removeAttribute(ScriptEngine.FILENAME, ScriptContext.ENGINE_SCOPE);
				engine.getContext().removeAttribute("mainClass", ScriptContext.ENGINE_SCOPE);
				engine.getContext().removeAttribute("parentLoader", ScriptContext.ENGINE_SCOPE);
			}
			}
			loaded = true;
		}
		finally
		{
			if (loaded)
			{
				_scriptLoadSuccesses.increment();
			}
			else
			{
				_scriptLoadFailures.increment();
			}
		}
	}

	private static String getExtension(File file)
	{
		int dot = file.getName().lastIndexOf('.');
		return dot < 0 ? "" : file.getName().substring(dot + 1);
	}

	private static boolean hasPackagedJavaScript(File file)
	{
		String className = getClassForFile(file);
		if (className == null)
		{
			return false;
		}
		File classFile = new File(SCRIPT_FOLDER, className.replace('.', File.separatorChar) + ".class");
		return classFile.isFile();
	}

	private void executePackagedJavaScript(File file) throws IOException, ScriptException
	{
		_scriptLoadAttempts.increment();
		boolean loaded = false;
		try
		{
			ManagedScript.loading(file, () -> runPackagedJavaScript(file));
			loaded = true;
		}
		finally
		{
			if (loaded)
			{
				_scriptLoadSuccesses.increment();
			}
			else
			{
				_scriptLoadFailures.increment();
			}
		}
	}

	private Void runPackagedJavaScript(File file) throws IOException, ScriptException
	{
		try
		{
			String className = getClassForFile(file).replace('/', '.').replace('\\', '.');
			Class<?> scriptClass = Class.forName(className, false, getPackagedJavaScriptLoader());
			ScriptContext context = new SimpleScriptContext();
			context.setAttribute("mainClass", className, ScriptContext.ENGINE_SCOPE);
			context.setAttribute(ScriptEngine.FILENAME, file.getName(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("classpath", getScriptClassPath(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("sourcepath", SCRIPT_FOLDER.getAbsolutePath(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("parentLoader", ClassLoader.getSystemClassLoader(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute("context", context, ScriptContext.ENGINE_SCOPE);
			context.setAttribute("arguments", new String[0], ScriptContext.ENGINE_SCOPE);
			Method setContext = findPublicStaticMethod(scriptClass, "setScriptContext", ScriptContext.class);
			if (setContext != null)
			{
				setContext.invoke(null, context);
			}
			Method main = findPublicStaticMethod(scriptClass, "main", String[].class);
			if (main == null)
			{
				throw new ScriptException("no main method in " + className);
			}
			main.invoke(null, (Object)context.getAttribute("arguments"));
			return null;
		}
		catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException e)
		{
			throw new ScriptException(e);
		}
	}

	private static Method findPublicStaticMethod(Class<?> type, String name, Class<?>... parameterTypes)
	{
		try
		{
			Method method = type.getMethod(name, parameterTypes);
			return Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) ? method : null;
		}
		catch (NoSuchMethodException e)
		{
			return null;
		}
	}

	private ClassLoader getPackagedJavaScriptLoader() throws IOException
	{
		ClassLoader loader = _packagedJavaScriptLoader;
		if (loader == null)
		{
			synchronized (this)
			{
				loader = _packagedJavaScriptLoader;
				if (loader == null)
				{
					URL scriptRoot = SCRIPT_FOLDER.toURI().toURL();
					loader = new URLClassLoader(new URL[] { scriptRoot }, ClassLoader.getSystemClassLoader());
					_packagedJavaScriptLoader = loader;
				}
			}
		}
		return loader;
	}

	public static String getClassForFile(File script)
	{
		String path = script.getAbsolutePath();
		String scpPath = SCRIPT_FOLDER.getAbsolutePath();
		if (path.startsWith(scpPath))
		{
			int idx = path.lastIndexOf('.');
			return path.substring(scpPath.length() + 1, idx);
		}
		return null;
	}

	private static String getScriptClassPath()
	{
		String runtimeClassPath = System.getProperty("java.class.path", "");
		String scriptFolder = SCRIPT_FOLDER.getAbsolutePath();
		if (runtimeClassPath.isEmpty())
		{
			return scriptFolder;
		}
		return runtimeClassPath + File.pathSeparator + scriptFolder;
	}
	
	public ScriptContext getScriptContext(ScriptEngine engine)
	{
		return engine.getContext();
	}
	
	public ScriptContext getScriptContext(String engineName)
	{
		ScriptEngine engine = getEngineByName(engineName);
		if (engine == null)
			throw new IllegalStateException("No engine registered with name (" + engineName + ")");
		
		return this.getScriptContext(engine);
	}
	
	public Object eval(ScriptEngine engine, String script, ScriptContext context) throws ScriptException
	{
		if (engine instanceof Compilable && ATTEMPT_COMPILATION)
		{
			Compilable eng = (Compilable)engine;
			CompiledScript cs = eng.compile(script);
			return context != null ? cs.eval(context) : cs.eval();
		}
		
		return context != null ? engine.eval(script, context) : engine.eval(script);
	}
	
	public Object eval(String engineName, String script) throws ScriptException
	{
		return this.eval(engineName, script, null);
	}
	
	public Object eval(String engineName, String script, ScriptContext context) throws ScriptException
	{
		ScriptEngine engine = getEngineByName(engineName);
		if (engine == null)
			throw new ScriptException("No engine registered with name (" + engineName + ")");
		
		return this.eval(engine, script, context);
	}
	
	public Object eval(ScriptEngine engine, String script) throws ScriptException
	{
		return this.eval(engine, script, null);
	}
	
	public void reportScriptFileError(File script, ScriptException e)
	{
		_log.warn("Failed executing script: " + script.getPath() + " - " + e.getMessage());
		
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		pw.println("Error on: " + script.getAbsolutePath());
		pw.println("Line: " + e.getLineNumber() + " - Column: " + e.getColumnNumber());
		pw.println();
		e.printStackTrace(pw);
		pw.close();
		
		final String report = sw.toString();

		try
		{
			String fileName = script.getName() + ".error.log";
			Files.writeString(new File(script.getParent(), fileName).toPath(), report, StandardCharsets.UTF_8);

			_log.warn("See " + fileName + " for details.");
		}
		catch (IOException ioe)
		{
			_log.warn("Additionally failed when trying to write an error report on script directory.", ioe);
			_log.info(report);
		}
	}
	
	public void registerScriptManager(ScriptManager<?> manager)
	{
		_scriptManagers.add(manager);
	}
	
	public void removeScriptManager(ScriptManager<?> manager)
	{
		_scriptManagers.remove(manager);
	}
	
	public List<ScriptManager<?>> getScriptManagers()
	{
		return _scriptManagers;
		
	}
	
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final L2ScriptEngineManager _instance = new L2ScriptEngineManager();
	}
}
