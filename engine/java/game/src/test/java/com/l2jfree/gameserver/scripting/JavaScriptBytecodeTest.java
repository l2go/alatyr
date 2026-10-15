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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.atomic.LongAdder;

import javax.script.ScriptException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.l2jfree.Config;

class JavaScriptBytecodeTest
{
	@Test
	@DisplayName("a Java script without packaged bytecode is not compiled at startup")
	void missingBytecodeNamesTheScript() throws Exception
	{
		File previousRoot = Config.DATAPACK_ROOT;
		File root = Files.createTempDirectory("l2jfree-scripts").toFile();
		Config.DATAPACK_ROOT = root;
		try
		{
			File script = new File(root, "data/scripts/ai/group_template/Missing.java");
			assertThat(script.getParentFile().mkdirs()).isTrue();
			Files.writeString(script.toPath(), "public class Missing {}\n");
			
			L2ScriptEngineManager scripts = mock(L2ScriptEngineManager.class, CALLS_REAL_METHODS);
			assertThatThrownBy(() -> scripts.executeScript(script))
					.isInstanceOf(ScriptException.class)
					.hasMessageContaining(script.getPath())
					.hasMessageContaining("no packaged bytecode");
		}
		finally
		{
			Config.DATAPACK_ROOT = previousRoot;
		}
	}

	@Test
	@DisplayName("a listed Java script without bytecode counts as one failed attempt")
	void aListedJavaScriptWithoutBytecodeCountsAsOneFailure() throws Exception
	{
		File previousRoot = Config.DATAPACK_ROOT;
		if (previousRoot == null)
			Config.DATAPACK_ROOT = Files.createTempDirectory("l2jfree-script-list").toFile();
		File script = new File(L2ScriptEngineManager.SCRIPT_FOLDER, "MissingBytecode.java");
		File errorLog = new File(script.getParentFile(), script.getName() + ".error.log");
		try
		{
			Files.createDirectories(script.getParentFile().toPath());
			Files.writeString(script.toPath(), "public class MissingBytecode {}\n");
			File list = Files.createTempFile("l2jfree-scripts", ".cfg").toFile();
			list.deleteOnExit();
			Files.writeString(list.toPath(), "MissingBytecode.java\n");
			
			L2ScriptEngineManager scripts = mock(L2ScriptEngineManager.class, CALLS_REAL_METHODS);
			counter(scripts, "_scriptLoadAttempts");
			counter(scripts, "_scriptLoadSuccesses");
			counter(scripts, "_scriptLoadFailures");
			
			scripts.executeScriptList(list);
			
			assertThat(counter(scripts, "_scriptLoadAttempts").sum()).isEqualTo(1);
			assertThat(counter(scripts, "_scriptLoadFailures").sum()).isEqualTo(1);
			assertThat(counter(scripts, "_scriptLoadSuccesses").sum()).isZero();
		}
		finally
		{
			Files.deleteIfExists(script.toPath());
			Files.deleteIfExists(errorLog.toPath());
			Config.DATAPACK_ROOT = previousRoot;
		}
	}

	/** Mockito skips field initializers, so the load counters start as empty adders. */
	private static LongAdder counter(L2ScriptEngineManager scripts, String name) throws Exception
	{
		Field field = L2ScriptEngineManager.class.getDeclaredField(name);
		field.setAccessible(true);
		LongAdder adder = (LongAdder)field.get(scripts);
		if (adder == null)
		{
			adder = new LongAdder();
			field.set(scripts, adder);
		}
		return adder;
	}
}
