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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class WorldFilesTest
{
	@Test
	@DisplayName("a missing file is an absent part, and a directory is a read error")
	void missingFileIsAbsentAndADirectoryIsNot() throws Exception
	{
		Logger log = mock(Logger.class);
		File missing = new File(Files.createTempDirectory("world-files").toFile(), "extractable_items.csv");
		
		assertThat(WorldFiles.skip(log, "extractable items", missing)).isTrue();
		verify(log).info("World has no extractable items");
		verifyNoMoreInteractions(log);
		
		Logger wrongKind = mock(Logger.class);
		File directory = Files.createTempDirectory("not-a-file").toFile();
		assertThat(WorldFiles.skip(wrongKind, "extractable items", directory)).isTrue();
		verify(wrongKind).warn(contains(directory.getPath() + " is not a file"));
		verify(wrongKind, never()).info(contains("World has no"));
	}
	
	@Test
	@DisplayName("a file that exists but cannot be opened keeps the path and the reason")
	void unreadableFileIsNotReportedAsMissing() throws Exception
	{
		File file = File.createTempFile("extractable", ".csv");
		assertThat(file.setReadable(false, false)).isTrue();
		try
		{
			Logger log = mock(Logger.class);
			assertThat(WorldFiles.skip(log, "extractable items", file)).isFalse();
			verifyNoInteractions(log);
			
			FileNotFoundException failure = null;
			try
			{
				new FileInputStream(file).close();
			}
			catch (FileNotFoundException e)
			{
				failure = e;
			}
			assertThat(failure).isNotNull();
			WorldFiles.unreadable(log, "extractable items", file, failure);
			verify(log).warn(contains("Cannot read extractable items: " + file.getPath()), org.mockito.ArgumentMatchers.eq(failure));
			verify(log, never()).info(contains("World has no"));
		}
		finally
		{
			file.setReadable(true, false);
			assertThat(file.delete()).isTrue();
		}
	}
	
	@Test
	@DisplayName("a missing or empty directory is absence, and a failed listing is a read error")
	void listingSeparatesAbsenceFromAReadError() throws Exception
	{
		File root = Files.createTempDirectory("world-list").toFile();
		File missing = new File(root, "zones");
		WorldFiles.Listed absent = WorldFiles.list(missing, (FilenameFilter)null);
		assertThat(absent.kind()).isEqualTo(WorldFiles.Kind.MISSING);
		assertThat(absent.files()).isEmpty();
		
		File empty = new File(root, "empty");
		assertThat(empty.mkdir()).isTrue();
		WorldFiles.Listed none = WorldFiles.list(empty, (FilenameFilter)null);
		assertThat(none.kind()).isEqualTo(WorldFiles.Kind.EMPTY);
		
		Logger absentLog = mock(Logger.class);
		assertThat(WorldFiles.leaveOut(absentLog, "zones", absent)).isTrue();
		verify(absentLog).info("World has no zones");
		verify(absentLog, never()).warn(contains("Cannot read"));
		
		File notDirectory = File.createTempFile("zones", ".xml", root);
		WorldFiles.Listed wrong = WorldFiles.list(notDirectory, (FilenameFilter)null);
		assertThat(wrong.kind()).isEqualTo(WorldFiles.Kind.UNREADABLE);
		assertThat(wrong.detail()).contains("is not a directory");
		Logger wrongLog = mock(Logger.class);
		assertThat(WorldFiles.leaveOut(wrongLog, "zones", wrong)).isTrue();
		verify(wrongLog).warn(contains("Cannot read zones: " + notDirectory.getPath()));
		verify(wrongLog, never()).info(contains("World has no"));
		
		File blocked = new File(root, "blocked");
		assertThat(blocked.mkdir()).isTrue();
		assertThat(blocked.setExecutable(false, false)).isTrue();
		try
		{
			WorldFiles.Listed unreadable = WorldFiles.list(blocked, (FilenameFilter)null);
			assertThat(unreadable.kind()).isEqualTo(WorldFiles.Kind.UNREADABLE);
			assertThat(unreadable.files()).isEmpty();
			assertThat(unreadable.detail()).contains(blocked.getPath()).contains("could not be listed");
		}
		finally
		{
			blocked.setExecutable(true, false);
		}
	}
}
