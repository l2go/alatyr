/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 */
package com.sun.script.jython;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.junit.jupiter.api.Test;
import org.python.core.PySystemState;

class JythonScriptEngineInteropTest
{
	@Test
	void factoryReportsTheSelectedJythonRuntimeVersion()
	{
		JythonScriptEngineFactory factory = new JythonScriptEngineFactory();

		assertThat(factory.getEngineVersion()).isEqualTo(PySystemState.version.toString());
		assertThat(factory.getLanguageVersion()).isEqualTo(PySystemState.version.toString());
	}

	@Test
	void compiledScriptReadsBindingsAndCallsBoundJavaObjects() throws Exception
	{
		ScriptEngine engine = new JythonScriptEngineFactory().getScriptEngine();
		AtomicReference<String> holder = new AtomicReference<>();
		engine.put("holder", holder);
		engine.put("suffix", " through the L2JFree JSR-223 bridge");

		CompiledScript script = ((Compilable) engine).compile("holder.set(suffix)");
		script.eval();

		assertThat(holder.get()).isEqualTo(" through the L2JFree JSR-223 bridge");
	}

	@Test
	void scriptAssignmentsRemainVisibleThroughEngineBindings() throws ScriptException
	{
		ScriptEngine engine = new JythonScriptEngineFactory().getScriptEngine();

		engine.eval("bridge_value = 'visible'");

		assertThat(engine.get("bridge_value")).isEqualTo("visible");
	}

	/**
	 * Datapack scripts keep their quest in a global ({@code QUEST = karul_bugbear(...)}) and read its attributes at
	 * module level. The global goes through the script context as a Java object, so reading it back must return the
	 * Python instance, not a new wrapper of its Java proxy without the instance attributes.
	 */
	@Test
	void aGlobalPythonSubclassOfAJavaClassKeepsItsAttributes() throws ScriptException
	{
		ScriptEngine engine = new JythonScriptEngineFactory().getScriptEngine();
		engine.eval("""
				from java.lang import Object
				class Probe(Object):
				    def __init__(self):
				        Object.__init__(self)
				        self.npc = 20600
				QUEST = Probe()
				result = QUEST.npc
				""");
		assertThat(engine.get("result")).isEqualTo(20600);
	}
}
