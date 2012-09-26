/**
 * 
 */
package javax.net.msrp;

import static org.junit.Assert.*;

import java.net.InetAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test composition of status messages.
 * @author tuijldert
 */
public class TestStatusMessage {
	private static final String STATUS_CONTENT =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<isComposing>" +
			"	<state>active</state>" +
			"	<contenttype>text/plain</contenttype>" +
			"	<lastactive>2003-01-27T10:43:00Z</lastactive>" +
			"	<refresh>20</refresh>" +
			"</isComposing>";

	private Session session;
	private DataContainer dc;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		session = new Session(false, false, InetAddress.getLocalHost());
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		session.tearDown();
	}

	@Test
	public final void testOutgoingStatusMessage() {
		OutgoingStatusMessage msg = new OutgoingStatusMessage(session, 50);
		assertTrue(msg.getState() == ImState.idle);
		assertNull(msg.getComposeContentType());
		assertTrue(msg.getRefresh() == 50);
		msg.discard();
	}

	@Test
	public final void testIncomingStatusMessage() {
		dc = new MemoryDataContainer(STATUS_CONTENT.getBytes());
		IncomingStatusMessage msg = new IncomingStatusMessage(session, "123",
				StatusMessage.IMCOMPOSE_TYPE, dc.size());
		msg.setDataContainer(dc);
		try {
			msg.validate();
			assertTrue(msg.getState() == ImState.active);
			assertEquals(msg.getComposeContentType(), "text/plain");
		} catch (Exception e) {
			fail("Exception: " + e.getLocalizedMessage());
		} finally {
			msg.discard();
		}
	}
}
