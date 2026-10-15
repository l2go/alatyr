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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassPathConflictsTest
{
	@TempDir
	Path directory;
	
	@Test
	void oneJarReachedThroughTwoEntriesIsNoConflict() throws Exception
	{
		Files.createFile(directory.resolve("l2jfree-login-3.0.0.jar"));
		String classPath = directory + File.pathSeparator + directory.resolve(".") + File.pathSeparator
				+ directory.resolve("l2jfree-login-3.0.0.jar");
		
		assertThat(ClassPathConflicts.find(classPath)).isEmpty();
	}
	
	@Test
	void classifiersOfOneVersionAreNoConflict() throws Exception
	{
		Files.createFile(directory.resolve("netty-transport-native-epoll-4.2.18.Final-linux-x86_64.jar"));
		Files.createFile(directory.resolve("netty-transport-native-epoll-4.2.18.Final-linux-aarch_64.jar"));
		Files.createFile(directory.resolve("netty-transport-native-epoll-4.2.18.Final.jar"));
		Files.createFile(directory.resolve("netty-transport-native-epoll-4.2.17.Final-linux-x86_64.jar"));
		
		assertThat(ClassPathConflicts.find(directory.toString()))
				.containsOnlyKeys("netty-transport-native-epoll").containsValue(java.util.List.of(
						"netty-transport-native-epoll-4.2.17.Final-linux-x86_64.jar",
						"netty-transport-native-epoll-4.2.18.Final-linux-aarch_64.jar",
						"netty-transport-native-epoll-4.2.18.Final-linux-x86_64.jar",
						"netty-transport-native-epoll-4.2.18.Final.jar"));
		
		Files.delete(directory.resolve("netty-transport-native-epoll-4.2.17.Final-linux-x86_64.jar"));
		assertThat(ClassPathConflicts.find(directory.toString())).isEmpty();
	}
	
	@Test
	void twoVersionsOfOneLibraryAreAConflict() throws Exception
	{
		Path libs = Files.createDirectory(directory.resolve("libs"));
		Files.createFile(libs.resolve("javolution-5.4.1.jar"));
		Files.createFile(directory.resolve("javolution-5.5.0.jar"));
		
		assertThat(ClassPathConflicts.find(libs + File.pathSeparator + directory)).containsOnlyKeys("javolution")
				.containsValue(java.util.List.of("javolution-5.4.1.jar", "javolution-5.5.0.jar"));
	}
}
