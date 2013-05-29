/**
 * 
 */
package javax.net.msrp;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Test sending a status message and receiving it. 
 * @author tuijldert
 */
public class TestSendingStatusMessages extends TestFrame {

	@Test
	public final void testActiveStatus() {
		sendingSession.setActive("text/plain");
        try {
			triggerSendReceive((byte[]) null);
			Message in = receivingSessionListener.getReceiveMessage();
			assertTrue(in instanceof StatusMessage);
			StatusMessage sm = (StatusMessage) in;
			assertTrue(sm.getState() == ImState.active);
			assertTrue(sm.getRefresh() == 120);
		} catch (Exception e) {
			fail(e.getLocalizedMessage());
		}
	}

	@Test
	public final void testActiveConferenceStatus() {
		String from = "sip:from@myplace.org";
		String to	= "sip:to@herplace.org";
		sendingSession.setActive("text/plain", 666, from, to);
        try {
			triggerSendReceive((byte[]) null);
			Message in = receivingSessionListener.getReceiveMessage();
			assertTrue(in instanceof IncomingStatusMessage);
			IncomingStatusMessage sm = (IncomingStatusMessage) in;
			assertTrue(sm.getFrom().equals(from));
			assertTrue(sm.getState() == ImState.active);
			assertTrue(sm.getRefresh() == 666);
		} catch (Exception e) {
			fail(e.getLocalizedMessage());
		}
	}
}
