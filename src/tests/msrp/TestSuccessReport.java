/* Copyright © João Antunes 2008
 This file is part of MSRP Java Stack.

    MSRP Java Stack is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MSRP Java Stack is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with MSRP Java Stack.  If not, see <http://www.gnu.org/licenses/>.

 */
package msrp;

import java.net.*;
import java.util.*;

import msrp.utils.*;

import static org.junit.Assert.*;

import org.junit.*;

/**
 * Test the success reports
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class TestSuccessReport {
	InetAddress address;

	Session sendingSession;

	Session receivingSession;

	MockMSRPSessionListener receivingSessionListener = new MockMSRPSessionListener(
			"receivingSessionListener");

	MockMSRPSessionListener sendingSessionListener = new MockMSRPSessionListener(
			"sendinSessionListener");

	@Before
	public void setUpConnection() {
		/* fetch the address to which this sessions are going to be bound: */
		Properties testProperties = new Properties();
		try {
			testProperties.load(TestSuccessReport.class
					.getResourceAsStream("/test.properties"));
			String addressString = testProperties.getProperty("address");
			address = InetAddress.getByName(addressString);
			sendingSession = new Session(false, false, address);
			receivingSession = new Session(false, false, sendingSession
					.getURI(), address);

			receivingSession.addMSRPSessionListener(receivingSessionListener);
			sendingSession.addMSRPSessionListener(sendingSessionListener);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Test
	public void testDefaultReportMechanismSmall() {

		try {
			/* transfer the 499 bytes message: */
			byte[] smallData = new byte[499];
			TextUtils.generateRandom(smallData);

			Message lessFiveHundredMessage = new Message(sendingSession,
					"plain/text", smallData);
			lessFiveHundredMessage.setSuccessReport(true);

			/* connect the two sessions: */

			ArrayList<URI> toPathSendSession = new ArrayList<URI>();
			toPathSendSession.add(receivingSession.getURI());


			sendingSession.addToPath(toPathSendSession);

			/*
			 * message should be transfered or in the process of being
			 * completely transfered
			 */

			/* make the mocklistener accept the message */
			synchronized (receivingSessionListener) {
				DataContainer dc = new MemoryDataContainer(499);
				receivingSessionListener.setDataContainer(dc);
				receivingSessionListener.setAcceptHookResult(new Boolean(true));
				receivingSessionListener.notify();
				receivingSessionListener.wait(3000);
			}

			if (receivingSessionListener.getAcceptHookMessage() == null
					|| receivingSessionListener.getAcceptHookSession() == null)
				fail("The Mock didn't worked and the message didn't got "
						+ "accepted");
			synchronized (sendingSessionListener) {
				sendingSessionListener.wait(4000);
			}
			assertTrue("Error the success report was called: "
					+ sendingSessionListener.successReportCounter
					+ " times and not 1",
					sendingSessionListener.successReportCounter == 1);

			synchronized (receivingSessionListener) {
				receivingSessionListener.wait(4000);
			}
			/*
			 * assert that the received report message id belongs to the sent
			 * and therefore received message
			 */
			assertEquals(receivingSessionListener.getReceiveMessage()
					.getMessageID(), sendingSessionListener
					.getReceivedReportTransaction().getMessageID());

		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}

	}

//	@Test
//	/**
//	 * @deprecated At the moment this test makes no sense because default
//	 * behavior is to send the success report only when the Message is complete
//	 */
//	public void testDefaultReportMechanismBig() {
//		try {
//			byte[] bigData = new byte[1024 * 1024];
//			TextUtils.generateRandom(bigData);
//			Message oneMegaMessage = new Message(sendingSession, "plain/text",
//					bigData);
//			/* connect the two sessions: */
//
//			ArrayList<URI> toPathSendSession = new ArrayList<URI>();
//			toPathSendSession.add(receivingSession.getURI());
//
//			receivingSession.addUriToIdentify(sendingSession.getURI());
//
//			sendingSession.addToPath(toPathSendSession);
//
//			/*
//			 * message should be transfered or in the process of being
//			 * completely transfered
//			 */
//			/* make the mocklistener accept the message */
//			synchronized (receivingSessionListener) {
//				receivingSessionListener.setAcceptHookResult(new Boolean(true));
//				receivingSessionListener.notify();
//				receivingSessionListener.wait(3000);
//			}
//
//			if (receivingSessionListener.getAcceptHookMessage() == null
//					|| receivingSessionListener.getAcceptHookSession() == null)
//				fail("The Mock didn't worked and the message didn't got "
//						+ "accepted");
//				synchronized (sendingSessionListener) {
//					sendingSessionListener.wait(4000);
//				}
//			assertTrue("Error the success report was called: "
//					+ sendingSessionListener.successReportCounter
//					+ " times and not 1",
//					sendingSessionListener.successReportCounter == 1);
//
//			synchronized (receivingSessionListener) {
//				receivingSessionListener.wait(4000);
//			}
//			/*
//			 * assert that the received report message id belongs to the sent
//			 * and therefore received message
//			 */
//			assertEquals(receivingSessionListener.getReceiveMessage()
//					.getMessageID(), sendingSessionListener
//					.getReceivedReportTransaction().getMessageID());
//		} catch (Exception e) {
//			e.printStackTrace();
//			fail(e.getMessage());
//
//		}
//
//	}
}
