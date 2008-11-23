/*
 * Copyright © João Antunes 2008 This file is part of MSRP Java Stack.
 * 
 * MSRP Java Stack is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 * 
 * MSRP Java Stack is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with MSRP Java Stack. If not, see <http://www.gnu.org/licenses/>.
 */
package msrp;

import java.net.*;
import java.util.*;
import java.io.*;

import msrp.testutils.CustomExampleReportMechanism;
import msrp.testutils.MockMSRPSessionListener;
import msrp.utils.*;

import static org.junit.Assert.*;

import org.junit.*;

/**
 * Test the success reports
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class TestReportMechanism
{
    Random binaryRandom = new Random();

    File tempFile;

    String tempFileDir;

    InetAddress address;

    Session sendingSession;

    Session receivingSession;

    MockMSRPSessionListener receivingSessionListener;

    MockMSRPSessionListener sendingSessionListener;

    @Before
    public void setUpConnection()
    {

        sendingSessionListener =
            new MockMSRPSessionListener("sendingSessionListener");
        receivingSessionListener =
            new MockMSRPSessionListener("receivingSessionListener");
        /* fetch the address to which this sessions are going to be bound: */
        Properties testProperties = new Properties();
        try
        {
            testProperties.load(TestReportMechanism.class
                .getResourceAsStream("/test.properties"));
            String addressString = testProperties.getProperty("address");
            address = InetAddress.getByName(addressString);
            /*
             * checks if we want the temp files on a specific directory. if the
             * propriety doesn't exist the default dir used by the JVM is used
             */
            tempFileDir = testProperties.getProperty("tempdirectory");

            if (tempFileDir != null)
            {
                System.out.println("Using temporary file directory: "
                    + tempFileDir);
                tempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, new File(tempFileDir));
            }
            else
                tempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, null);

            /* sets up the MSRP sessions */
            sendingSession = new Session(false, false, address);
            receivingSession =
                new Session(false, false, sendingSession.getURI(), address);

            receivingSession.addMSRPSessionListener(receivingSessionListener);
            sendingSession.addMSRPSessionListener(sendingSessionListener);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    @After
    public void tearDownConnection()
    {
        // TODO needs: tear down of the sessions
        // TODO needs: (?!) timer to mantain connection active even though
        // sessions
        // are over (?!)
        receivingSessionListener.getReceiveMessage().getDataContainer()
            .dispose();

        tempFile.delete();
        /* To remove: */
        System.gc();
    }

    /**
     * This method tests sending a small (499 KBytes) message and at the end
     * makes sure the success report was called one time
     */
    @Test
    public void testDefaultReportMechanismSmall()
    {

        try
        {
            /* transfer the 499 bytes message: */
            byte[] smallData = new byte[499 * 1024];
            TextUtils.generateRandom(smallData);

            Message lessFiveHundredMessage =
                new Message(sendingSession, "plain/text", smallData);
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
            synchronized (receivingSessionListener)
            {
                DataContainer dc = new MemoryDataContainer(499 * 1024);
                receivingSessionListener.setDataContainer(dc);
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait(3000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't worked and the message didn't got "
                    + "accepted");
            synchronized (receivingSessionListener)
            {
                /*
                 * allow the message to be received
                 */
                receivingSessionListener.wait();
            }
            synchronized (sendingSessionListener)
            {
                sendingSessionListener.wait(3000);
            }
            assertTrue("Error the success report was called: "
                + sendingSessionListener.successReportCounter
                + " times and not 1",
                sendingSessionListener.successReportCounter.size() == 1);

            /*
             * assert that the received report message id belongs to the sent
             * and therefore received message
             */
            assertEquals(receivingSessionListener.getReceiveMessage()
                .getMessageID(), sendingSessionListener
                .getReceivedReportTransaction().getMessageID());

            /*
             * Assert that the updateSendStatus was called the due amount of
             * times: . one for the barrier of the 49% to 50% (if the
             * message+headers < buffers of the connection size this won't
             * appear) [not the case actually] . another when the message is
             * completely sent
             */
            assertEquals(
                "The updateSendStatus wasn't called the expected number "
                    + "of times", 2,
                sendingSessionListener.updateSendStatusCounter.size());
            /*
             * make sure it was called with the 50% and 100%
             */
            assertEquals("The updateSendStatus was called with a "
                + "strange/unexpected number of bytes sent as argument", 50,
                (sendingSessionListener.updateSendStatusCounter.get(0)
                    .longValue() * 100)
                    / sendingSessionListener.getUpdateSendStatusMessage()
                        .getSize());
            assertEquals("The updateSendStatus was called with a "
                + "strange/unexpected number of bytes sent as argument", 100,
                (sendingSessionListener.updateSendStatusCounter.get(1)
                    .longValue() * 100)
                    / sendingSessionListener.getUpdateSendStatusMessage()
                        .getSize());

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    /**
     * This method tests sending a big (5 Mbytes) binary message and at the end
     * makes sure the success report was called one time
     */
    @Test
    public void testDefaultReportMechanismBig()
    {

        try
        {
            Long startTime = System.currentTimeMillis();
            /* transfer the 1MB bytes message: */
            byte[] bigData = new byte[5 * 1024 * 1024];
            binaryRandom.nextBytes(bigData);
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            fileStream.write(bigData);
            fileStream.flush();
            fileStream.close();
            System.out.println("Stoped generating and writing 5MB "
                + "of random data took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            Message fiveMegaByteMessage =
                new Message(sendingSession, "plain/text", tempFile);
            fiveMegaByteMessage.setSuccessReport(true);

            /* connect the two sessions: */

            ArrayList<URI> toPathSendSession = new ArrayList<URI>();
            toPathSendSession.add(receivingSession.getURI());

            sendingSession.addToPath(toPathSendSession);

            /*
             * message should be transfered or in the process of being
             * completely transfered
             */

            /* make the mocklistener accept the message */
            synchronized (receivingSessionListener)
            {
                DataContainer dc = new MemoryDataContainer(5 * 1024 * 1024);
                receivingSessionListener.setDataContainer(dc);
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait(3000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't worked and the message didn't got "
                    + "accepted");
            synchronized (receivingSessionListener)
            {
                /*
                 * allow the message to be received
                 */
                receivingSessionListener.wait();
            }
            synchronized (sendingSessionListener)
            {
                sendingSessionListener.wait(3000);
            }
            assertTrue("Error the success report was called: "
                + sendingSessionListener.successReportCounter
                + " times and not 1",
                sendingSessionListener.successReportCounter.size() == 1);

            /*
             * assert that the received report message id belongs to the sent
             * and therefore received message
             */
            assertEquals(receivingSessionListener.getReceiveMessage()
                .getMessageID(), sendingSessionListener
                .getReceivedReportTransaction().getMessageID());

            /*
             * Assert that the updateSendStatus was called the due amount of
             * times: . at each 10% of progress obtained . another when the
             * message is completely sent
             */
            assertEquals(
                "The updateSendStatus wasn't called the expected number "
                    + "of times", 10,
                sendingSessionListener.updateSendStatusCounter.size());
            /*
             * make sure it was called with the right percentages
             */
            for (int i = 0; i < 10; i++)
                assertEquals("The updateSendStatus was called with a "
                    + "strange/unexpected number of bytes sent as argument",
                    (i + 1) * 10,
                    (sendingSessionListener.updateSendStatusCounter.get(i)
                        .longValue() * 100)
                        / sendingSessionListener.getUpdateSendStatusMessage()
                            .getSize());

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    /**
     * This method tests sending a small (499 KBytes) message and at the end
     * makes sure the success report was called one time
     */
    @Test
    public void testCustomReportMechanismSmall()
    {

        try
        {
            /* change the report mechanism of the sessions: */
            receivingSession.setReportMechanism(CustomExampleReportMechanism
                .getInstance());
            sendingSession.setReportMechanism(CustomExampleReportMechanism
                .getInstance());
            /* transfer the 499 bytes message: */
            byte[] smallData = new byte[499 * 1024];
            TextUtils.generateRandom(smallData);

            Message lessFiveHundredMessage =
                new Message(sendingSession, "plain/text", smallData);
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
            synchronized (receivingSessionListener)
            {
                DataContainer dc = new MemoryDataContainer(499 * 1024);
                receivingSessionListener.setDataContainer(dc);
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait(3000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't worked and the message didn't got "
                    + "accepted");
            synchronized (receivingSessionListener)
            {
                /*
                 * allow the message to be received
                 */
                receivingSessionListener.wait();
            }
            synchronized (sendingSessionListener)
            {
                sendingSessionListener.wait(3000);
            }
            assertTrue("Error the success report was called: "
                + sendingSessionListener.successReportCounter
                + " times and not 2",
                sendingSessionListener.successReportCounter.size() == 2);

            /*
             * make sure it was called with the 50% and 100%
             */

            assertEquals(
                "The receivedReport was called with a "
                    + "strange/unexpected number of bytes sent as argument",
                50,
                (sendingSessionListener.successReportCounter.get(0).longValue() * 100)
                    / lessFiveHundredMessage.getSize());
            assertEquals(
                "The receivedReport was called with a "
                    + "strange/unexpected number of bytes sent as argument",
                100,
                (sendingSessionListener.successReportCounter.get(1).longValue() * 100)
                    / lessFiveHundredMessage.getSize());

            /*
             * assert that the received report message id belongs to the sent
             * and therefore received message
             */
            assertEquals(receivingSessionListener.getReceiveMessage()
                .getMessageID(), sendingSessionListener
                .getReceivedReportTransaction().getMessageID());

            /*
             * Assert that the updateSendStatus was called the due amount of
             * times: . Just one for the completion
             */
            assertEquals(
                "The updateSendStatus wasn't called the expected number "
                    + "of times", 1,
                sendingSessionListener.updateSendStatusCounter.size());
            /* make sure that it was called when the message was at 100% */
            assertEquals("The updateSendStatus was called with a "
                + "strange/unexpected number of bytes sent as argument", 100,
                (sendingSessionListener.updateSendStatusCounter.get(0)
                    .longValue() * 100)
                    / sendingSessionListener.getUpdateSendStatusMessage()
                        .getSize());

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    /**
     * This method tests sending a big (5 Mbytes) binary message and at the end
     * makes sure the success report was called one time
     */
    @Test
    public void testCustomReportMechanismBig()
    {

        try
        {
            /* change the report mechanism of the sessions: */
            receivingSession.setReportMechanism(CustomExampleReportMechanism
                .getInstance());
            sendingSession.setReportMechanism(CustomExampleReportMechanism
                .getInstance());
            Long startTime = System.currentTimeMillis();
            /* transfer the 5MB bytes message: */
            byte[] bigData = new byte[5 * 1024 * 1024];
            binaryRandom.nextBytes(bigData);
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            fileStream.write(bigData);
            fileStream.flush();
            fileStream.close();
            System.out.println("Stoped generating and writing 5MB "
                + "of random data took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            Message fiveMegaByteMessage =
                new Message(sendingSession, "plain/text", tempFile);
            fiveMegaByteMessage.setSuccessReport(true);

            /* connect the two sessions: */

            ArrayList<URI> toPathSendSession = new ArrayList<URI>();
            toPathSendSession.add(receivingSession.getURI());

            sendingSession.addToPath(toPathSendSession);

            /*
             * message should be transfered or in the process of being
             * completely transfered
             */

            /* make the mocklistener accept the message */
            synchronized (receivingSessionListener)
            {
                DataContainer dc = new MemoryDataContainer(5 * 1024 * 1024);
                receivingSessionListener.setDataContainer(dc);
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait(3000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't worked and the message didn't got "
                    + "accepted");
            synchronized (receivingSessionListener)
            {
                /*
                 * allow the message to be received
                 */
                receivingSessionListener.wait();
            }
            synchronized (sendingSessionListener)
            {
                sendingSessionListener.wait(3000);
            }
            /*
             * 11 Times might be one time too many times, because the sender
             * receives two reports of the message being completely received.
             * However this kind of behavior isn't incompatible with the one
             * present on the standards. For more info see Issue #10
             */
            assertTrue("Error the success report was called: "
                + sendingSessionListener.successReportCounter.size()
                + " times and not 10",
                sendingSessionListener.successReportCounter.size() == 11);

            /*
             * assert that the received report message id belongs to the sent
             * and therefore received message
             */
            assertEquals(receivingSessionListener.getReceiveMessage()
                .getMessageID(), sendingSessionListener
                .getReceivedReportTransaction().getMessageID());

            /*
             * Assert that the updateSendStatus was called the due amount of
             * times: . when the message is completely sent
             */
            assertEquals(
                "The updateSendStatus wasn't called the expected number "
                    + "of times", 1,
                sendingSessionListener.updateSendStatusCounter.size());
            /*
             * make sure it was called with the right percentages
             */
            assertEquals(
                "The updateSendStatus was called with a "
                    + "strange/unexpected number of bytes sent as argument",
                100,
                (sendingSessionListener.updateSendStatusCounter.get(0).longValue() * 100)
                    / sendingSessionListener.getUpdateSendStatusMessage().getSize());

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    // @Test
    // /**
    // * @deprecated At the moment this test makes no sense because default
    // * behavior is to send the success report only when the Message is
    // complete
    // */
    // public void testDefaultReportMechanismBig() {
    // try {
    // byte[] bigData = new byte[1024 * 1024];
    // TextUtils.generateRandom(bigData);
    // Message oneMegaMessage = new Message(sendingSession, "plain/text",
    // bigData);
    // /* connect the two sessions: */
    //
    // ArrayList<URI> toPathSendSession = new ArrayList<URI>();
    // toPathSendSession.add(receivingSession.getURI());
    //
    // receivingSession.addUriToIdentify(sendingSession.getURI());
    //
    // sendingSession.addToPath(toPathSendSession);
    //
    // /*
    // * message should be transfered or in the process of being
    // * completely transfered
    // */
    // /* make the mocklistener accept the message */
    // synchronized (receivingSessionListener) {
    // receivingSessionListener.setAcceptHookResult(new Boolean(true));
    // receivingSessionListener.notify();
    // receivingSessionListener.wait(3000);
    // }
    //
    // if (receivingSessionListener.getAcceptHookMessage() == null
    // || receivingSessionListener.getAcceptHookSession() == null)
    // fail("The Mock didn't worked and the message didn't got "
    // + "accepted");
    // synchronized (sendingSessionListener) {
    // sendingSessionListener.wait(4000);
    // }
    // assertTrue("Error the success report was called: "
    // + sendingSessionListener.successReportCounter
    // + " times and not 1",
    // sendingSessionListener.successReportCounter == 1);
    //
    // synchronized (receivingSessionListener) {
    // receivingSessionListener.wait(4000);
    // }
    // /*
    // * assert that the received report message id belongs to the sent
    // * and therefore received message
    // */
    // assertEquals(receivingSessionListener.getReceiveMessage()
    // .getMessageID(), sendingSessionListener
    // .getReceivedReportTransaction().getMessageID());
    // } catch (Exception e) {
    // e.printStackTrace();
    // fail(e.getMessage());
    //
    // }
    //
    // }
}
