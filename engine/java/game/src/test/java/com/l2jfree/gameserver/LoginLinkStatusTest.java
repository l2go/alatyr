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
package com.l2jfree.gameserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.l2jfree.Config;
import com.l2jfree.contract.AdmissionResult;
import com.l2jfree.contract.LoginPort;
import com.l2jfree.contract.ServerStatus;
import com.l2jfree.contract.ServerStatusAttributes;
import com.l2jfree.contract.SessionKey;
import com.l2jfree.contract.WorldStatus;

/** What the world tells the login about itself: the status that the server list shows. */
class LoginLinkStatusTest
{
	private static final LoginPort LOGIN = new LoginPort() {
		@Override
		public AdmissionResult admit(String account, SessionKey key)
		{
			return AdmissionResult.refused();
		}
		
		@Override
		public void leave(String account)
		{
		}
		
		@Override
		public void changeAccessLevel(String account, int level)
		{
		}
	};
	
	private int _serverId;
	private int _port;
	private int _maxPlayers;
	private boolean _gmOnly;
	private boolean _pvp;
	private boolean _clock;
	private boolean _hideName;
	private boolean _unknownBit;
	private boolean _testServer;
	private boolean _brackets;
	private int _ageLimit;
	private String _external;
	private String _internal;
	
	@BeforeEach
	void configure()
	{
		_serverId = Config.SERVER_ID;
		_port = Config.PORT_GAME;
		_maxPlayers = Config.MAXIMUM_ONLINE_USERS;
		_gmOnly = Config.SERVER_GMONLY;
		_pvp = Config.SERVER_PVP;
		_clock = Config.SERVER_LIST_CLOCK;
		_hideName = Config.SERVER_BIT_3;
		_unknownBit = Config.SERVER_BIT_1;
		_testServer = Config.SERVER_LIST_TESTSERVER;
		_brackets = Config.SERVER_LIST_BRACKET;
		_ageLimit = Config.SERVER_AGE_LIM;
		_external = Config.EXTERNAL_HOSTNAME;
		_internal = Config.INTERNAL_HOSTNAME;
		
		Config.SERVER_ID = 7;
		Config.PORT_GAME = 7778;
		Config.MAXIMUM_ONLINE_USERS = 250;
		Config.SERVER_GMONLY = false;
		Config.SERVER_PVP = true;
		Config.SERVER_LIST_CLOCK = false;
		Config.SERVER_BIT_3 = true;
		Config.SERVER_BIT_1 = false;
		Config.SERVER_LIST_TESTSERVER = true;
		Config.SERVER_LIST_BRACKET = false;
		Config.SERVER_AGE_LIM = 15;
		Config.EXTERNAL_HOSTNAME = "203.0.113.7";
		Config.INTERNAL_HOSTNAME = "";
	}
	
	@AfterEach
	void restore()
	{
		Config.SERVER_ID = _serverId;
		Config.PORT_GAME = _port;
		Config.MAXIMUM_ONLINE_USERS = _maxPlayers;
		Config.SERVER_GMONLY = _gmOnly;
		Config.SERVER_PVP = _pvp;
		Config.SERVER_LIST_CLOCK = _clock;
		Config.SERVER_BIT_3 = _hideName;
		Config.SERVER_BIT_1 = _unknownBit;
		Config.SERVER_LIST_TESTSERVER = _testServer;
		Config.SERVER_LIST_BRACKET = _brackets;
		Config.SERVER_AGE_LIM = _ageLimit;
		Config.EXTERNAL_HOSTNAME = _external;
		Config.INTERNAL_HOSTNAME = _internal;
	}
	
	@Test
	void theWorldIsListedWithItsLiveSettings()
	{
		LoginLink link = new LoginLink();
		link.connect(LOGIN);
		
		assertThat(link.status()).isEqualTo(new WorldStatus(7, ServerStatus.STATUS_AUTO, 7778, 0, 250, 15, true, false,
				false, true, true, false));
	}
	
	@Test
	void aWorldConfiguredForGmsOnlyIsListedAsGmOnly()
	{
		Config.SERVER_GMONLY = true;
		LoginLink link = new LoginLink();
		link.connect(LOGIN);
		
		assertThat(link.status().status()).isEqualTo(ServerStatus.STATUS_GM_ONLY);
	}
	
	@Test
	void changedAttributesShowInTheNextStatus()
	{
		LoginLink link = new LoginLink();
		link.connect(LOGIN);
		
		link.setMaxPlayers(10);
		link.changeAttribute(ServerStatusAttributes.SERVER_LIST_CLOCK, 1);
		link.changeAttribute(ServerStatusAttributes.SERVER_AGE_LIMITATION, 18);
		link.setServerStatus(ServerStatus.STATUS_GM_ONLY.ordinal());
		
		WorldStatus status = link.status();
		assertThat(status.maxPlayers()).isEqualTo(10);
		assertThat(status.clock()).isTrue();
		assertThat(status.ageLimit()).isEqualTo(18);
		assertThat(status.status()).isEqualTo(ServerStatus.STATUS_GM_ONLY);
	}
	
	@Test
	void theLoginIsHandedOverOnce()
	{
		LoginLink link = new LoginLink();
		link.connect(LOGIN);
		
		assertThatThrownBy(() -> link.connect(LOGIN)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new LoginLink().connect(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
