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
package com.l2jfree.loginserver.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class PasswordHasherTest
{
	@Test
	void aNewPasswordIsSaltedAndVerifies()
	{
		String first = PasswordHasher.hash("correct horse", 1_000);
		String second = PasswordHasher.hash("correct horse", 1_000);
		
		assertThat(first).startsWith("pbkdf2-sha256$1000$").isNotEqualTo(second);
		assertThat(PasswordHasher.verify("correct horse", first)).isTrue();
		assertThat(PasswordHasher.verify("correct horse", second)).isTrue();
		assertThat(PasswordHasher.verify("wrong", first)).isFalse();
	}
	
	@Test
	void theFormOfTheOldLineStillVerifies() throws Exception
	{
		byte[] digest = MessageDigest.getInstance("SHA").digest("secret".getBytes(StandardCharsets.UTF_8));
		String legacy = Base64.getEncoder().encodeToString(digest);
		
		assertThat(PasswordHasher.verify("secret", legacy)).isTrue();
		assertThat(PasswordHasher.verify("Secret", legacy)).isFalse();
		assertThat(PasswordHasher.needsRehash(legacy)).isTrue();
	}
	
	/** The old comparison stopped at the length of the stored value, so an empty one accepted every password. */
	@Test
	void aStoredValueThatIsEmptyOrShortOrBrokenAcceptsNothing()
	{
		assertThat(PasswordHasher.verify("anything", "")).isFalse();
		assertThat(PasswordHasher.verify("anything", null)).isFalse();
		assertThat(PasswordHasher.verify("anything", "QQ==")).isFalse();
		assertThat(PasswordHasher.verify("anything", "not base64 at all!")).isFalse();
		assertThat(PasswordHasher.verify("anything", "pbkdf2-sha256$abc$AA==$AA==")).isFalse();
		assertThat(PasswordHasher.verify("anything", "pbkdf2-sha256$1000$AA==")).isFalse();
		assertThat(PasswordHasher.verify("anything", "pbkdf2-sha256$99999999999$AA==$AA==")).isFalse();
	}
	
	@Test
	void onlyTheOldFormAndWeakerIterationsNeedARehash()
	{
		assertThat(PasswordHasher.needsRehash(PasswordHasher.hash("x"))).isFalse();
		assertThat(PasswordHasher.needsRehash(PasswordHasher.hash("x", 1_000))).isTrue();
		assertThat(PasswordHasher.needsRehash(null)).isTrue();
	}
}
