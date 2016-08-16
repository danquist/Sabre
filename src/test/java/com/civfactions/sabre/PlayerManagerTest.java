package com.civfactions.sabre;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.UUID;

import org.apache.commons.lang.NullArgumentException;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.civfactions.sabre.chat.GlobalChat;
import com.civfactions.sabre.groups.SabreFaction;
import com.civfactions.sabre.test.MockDataAccess;
import com.civfactions.sabre.util.TextUtil;

public class PlayerManagerTest {
	
	private static SabrePlugin plugin;
	private static MockDataAccess db;
	
	private SabrePlayer p1;
	private SabrePlayer p2;
	
	private PlayerManager pm;

	@BeforeClass
	public static void setUpClass() throws Exception {
		plugin = mock(SabrePlugin.class);
		when(plugin.getGlobalChat()).thenReturn(mock(GlobalChat.class));
		when(plugin.logger()).thenReturn(mock(SabreLogger.class));
		when(plugin.txt()).thenReturn(new TextUtil());
	}

	@Before
	public void setUp() throws Exception {
		
		db = spy(new MockDataAccess());
		pm = spy(new PlayerManager(plugin, db));
		when(plugin.getPlayerManager()).thenReturn(pm);
		
		Player mock1 = mock(Player.class);
		when(mock1.getName()).thenReturn("Player1");
		when(mock1.getUniqueId()).thenReturn(UUID.randomUUID());
		
		Player mock2 = mock(Player.class);
		when(mock2.getName()).thenReturn("Player2");
		when(mock2.getUniqueId()).thenReturn(UUID.randomUUID());
		
		p1 = spy(new SabrePlayer(plugin, mock1.getUniqueId(), "Player1"));
		p1.setPlayer(mock1);
		when(p1.getPlayer()).thenReturn(mock1);
		
		p2 = spy(new SabrePlayer(plugin, mock2.getUniqueId(), "Player2"));
		p2.setPlayer(mock2);
	}

	@Test
	public void testPlayerManager() {
		Throwable e = null;
		try { new PlayerManager(null, db); } catch (Throwable ex) { e = ex; }
		assertTrue(e instanceof NullArgumentException);
		
		try { new PlayerManager(plugin, null); } catch (Throwable ex) { e = ex; }
		assertTrue(e instanceof NullArgumentException);
	}

	@Test
	public void testLoad() {
		db.players.add(p1);
		pm.load();
		verify(db).playerGetAll();
		assertTrue(pm.getPlayers().contains(p1));
		
		db.players.clear();
		db.players.add(p2);
		pm.load();
		assertFalse(pm.getPlayers().contains(p1));
		assertTrue(pm.getPlayers().contains(p2));
	}

	@Test
	public void testRemovePlayer() {
		db.players.add(p1);
		pm.load();
		
		assertTrue(pm.getPlayers().contains(p1));
		pm.removePlayer(p1);
		verify(db).playerDelete(p1);
		assertFalse(pm.getPlayers().contains(p1));
	}

	@Test
	public void testGetPlayerById() {
		db.players.add(p1);
		db.players.add(p2);
		pm.load();
		
		assertEquals(pm.getPlayerById(p1.getID()), p1);
		assertEquals(pm.getPlayerById(p2.getID()), p2);
		assertNotEquals(pm.getPlayerById(p1.getID()), p2);
	}

	@Test
	public void testGetPlayerByName() {
		db.players.add(p1);
		db.players.add(p2);
		pm.load();
		
		assertEquals(pm.getPlayerByName(p1.getName()), p1);
		assertEquals(pm.getPlayerByName(p2.getName()), p2);
		assertNotEquals(pm.getPlayerByName(p1.getName()), p2);
	}

	@Test
	public void testOnPlayerConnectDisconnect() {
		db.players.add(p1);
		pm.load();
		
		assertFalse(pm.getOnlinePlayers().contains(p1));
		pm.onPlayerConnect(p1);
		assertTrue(pm.getOnlinePlayers().contains(p1));
		pm.onPlayerDisconnect(p1);
		assertFalse(pm.getOnlinePlayers().contains(p1));
	}

	@Test
	public void testSetLastLogin() {
		Date lastLogin = new Date();
		pm.setLastLogin(p1, lastLogin);
		assertEquals(p1.getLastLogin(), lastLogin);
		verify(db).playerUpdateLastLogin(p1);
	}

	@Test
	public void testSetAutoJoin() {
		assertFalse(p1.getAutoJoin());
		pm.setAutoJoin(p1, true);
		assertTrue(p1.getAutoJoin());
		verify(db).playerUpdateAutoJoin(p1);
	}

	@Test
	public void testSetFaction() {
		SabreFaction faction = mock(SabreFaction.class);
		pm.setFaction(p1, faction);
		assertEquals(p1.getFaction(), faction);
		verify(db).playerUpdateFaction(p1);
	}

	@Test
	public void testSetDisplayName() {
		String name = "test";
		pm.setDisplayName(p1, name);
		assertEquals(p1.getName(), name);
		verify(db).playerUpdateName(p1);
	}

	@Test
	public void testSetBanStatus() {
		String banReason = "1337 hax";
		assertFalse(p1.getBanned());
		pm.setBanStatus(p1, true, banReason);
		assertTrue(p1.getBanned());
		assertEquals(p1.getBanMessage(), banReason);
		verify(db).playerUpdateBan(p1);
	}

	@Test
	public void testSetFreedOffline() {
		assertFalse(p1.getFreedOffline());
		pm.setFreedOffline(p1, true);
		assertTrue(p1.getFreedOffline());
		verify(db).playerUpdateFreedOffline(p1);
	}

	@Test
	public void testSetBedLocation() {
		Location bedLocation = mock(Location.class);
		pm.setBedLocation(p1, bedLocation);
		assertEquals(p1.getBedLocation(), bedLocation);
		verify(db).playerUpdateBed(p1);
	}

	@Test
	public void testAddOfflineMessage() {
		String msg1 = "offline message 1";
		String msg2 = "offline message 2";
		pm.addOfflineMessage(p1, msg1);
		pm.addOfflineMessage(p1, msg2);
		assertEquals(p1.getOfflineMessages().size(), 2);
		assertTrue(p1.getOfflineMessages().contains(msg1));
		assertTrue(p1.getOfflineMessages().contains(msg2));
		verify(db).playerAddOfflineMessage(p1, msg1);
		verify(db).playerAddOfflineMessage(p1, msg2);
		
		when(p1.getPlayer().isOnline()).thenReturn(true);
		pm.printOfflineMessages(p1);
		verify(p1.getPlayer()).sendMessage(msg1);
		verify(p1.getPlayer()).sendMessage(msg2);
		
		pm.clearOfflineMessages(p1);
		assertEquals(p1.getOfflineMessages().size(), 0);
		verify(db).playerClearOfflineMessages(p1);
	}

	@Test
	public void testAddPlayTime() {
		
		long playTime = p1.getPlaytime();
		pm.addPlayTime(p1, 1000);
		assertEquals(p1.getPlaytime(), playTime + 1000);
		verify(db).playerUpdatePlayTime(p1);
	}

}
