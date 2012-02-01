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

import msrp.messages.*;
import msrp.testutils.*;
import msrp.events.*;

import java.net.*;
import java.util.*;
import java.io.*;

import static org.junit.Assert.*;

import org.junit.*;

/**
 * This class is used to test the abort mechanism of the messages implemented on
 * this MSRP library. The two points of contact of the app with the abort
 * mechanism are the method Message.abortSend(), and the callback on
 * MSRPSessionListener.abortedMessage
 * 
 * @see Message#abortSend()
 * @see MSRPSessionListener#abortedMessage(Session, Message)
 * 
 * @author João André Pereira Antunes
 * 
 */
public class TestAbortMechanism
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
            testProperties.load(TestAbortMechanism.class
                .getResourceAsStream("test.properties"));
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
        tempFile.delete();
        /* To remove: */
        System.gc();
    }

    @Ignore
    @Test
    /*
     * This method tests the functionality of aborting an unsent message,
     * currently this test depends on resolution of Issue #11 FIXME TODO
     */
    public void testAbortionUnsentMessage()
    {
        /* TODO after fixing Issue #11 */

    }

    /**
     * This test is used to test that the abortion of a currently being sent
     * message is working well
     */
    @Test
    public void testAbortionMessage()
    {
        try
        {
            Long startTime = System.currentTimeMillis();
            /* transfer the 5MB bytes message: */
            byte[] bigData = new byte[5 * 1024 * 1024];
            binaryRandom.nextBytes(bigData);
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            fileStream.write(bigData);
            fileStream.flush();
            fileStream.close();
            System.out.println(
            		"Stopped generating and writing 5MB of random data. Took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            Message fiveMegaByteMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            fiveMegaByteMessage.setSuccessReport(false);

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
                receivingSessionListener.wait(6000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't worked and the message didn't got "
                    + "accepted");
            synchronized (sendingSessionListener.updateSendStatusCounter)
            {
                /*
                 * Wait for the first updateSendStatusCounter (that should be
                 * done at the 10% [more or less the buffer size in %])
                 */
                sendingSessionListener.updateSendStatusCounter.wait();
            }
            /* abort the message */
            fiveMegaByteMessage.abort(MessageAbortedEvent.CONTINUATIONFLAG,
                null);

            /*
             * wait for the receiving part to be notified by the library of the
             * abortion of the message
             */
            synchronized (receivingSessionListener.abortMessageCounter)
            {
                receivingSessionListener.abortMessageCounter.wait(6000);

            }

            /* confirm that we have an aborted message */
            assertEquals("We didn't got a call from the library to the "
                + "abortMessage", 1,
                receivingSessionListener.abortMessageCounter.size());

            /*
             * confirm that the aborted message size received before it was
             * aborted is on the 10% + buffer size%
             */
            long receivedMessageSize =
                receivingSessionListener.getAbortedMessage().getSize();
            // how much is Connection.OUTPUTBUFFERLENGTH
            long expectedIdealBValue = 10 * receivedMessageSize / 100;
            int expectedMaximumPValue =
                (int) ((expectedIdealBValue + Connection.OUTPUTBUFFERLENGTH) * 100 / receivedMessageSize);
            // quickfix of issue #28:
            expectedMaximumPValue += 2;
            int obtainedPValue =
                (int) ((int) (((IncomingMessage) receivingSessionListener
                    .getAbortedMessage()).getReceivedBytes() * 100) / receivedMessageSize);
            assertTrue(
                "Aborted message's expected size is wrong, "
                    + "obtained: "
                    + obtainedPValue
                    + " maximum value tolerated: "
                    + expectedMaximumPValue
                    + " in bytes: expected: "
                    + (expectedIdealBValue + Connection.OUTPUTBUFFERLENGTH)
                    + " obtained: "
                    + (((IncomingMessage) receivingSessionListener
                        .getAbortedMessage()).getReceivedBytes()),
                (obtainedPValue >= 10 && obtainedPValue <= expectedMaximumPValue));

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Catched an exception that shouldn't occur:" + e.getMessage());
        }

    }

    /**
     * This test is used to test that the abortion of a message currently being
     * received is working well, with the new abortedMessageEvent mechanism and
     * code 413
     */
    @Test
    public void testAbortSendingMessage()
    {
        try
        {
            Long startTime = System.currentTimeMillis();
            /* transfer the 5MB bytes message: */
            byte[] bigData = new byte[5 * 1024 * 1024];
            binaryRandom.nextBytes(bigData);
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            fileStream.write(bigData);
            fileStream.flush();
            fileStream.close();
            System.out.println(
            		"Stopped generating and writing 5MB of random data. Took: "
    				+ ((System.currentTimeMillis() - startTime) / 1000) + "s");

            Message fiveMegaByteMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            fiveMegaByteMessage.setSuccessReport(false);

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
                receivingSessionListener.wait(6000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't worked and the message didn't got "
                    + "accepted");
            synchronized (sendingSessionListener.updateSendStatusCounter)
            {
                /*
                 * Wait for the first updateSendStatusCounter (that should be
                 * done at the 10% [more or less the buffer size in %])
                 */
                sendingSessionListener.updateSendStatusCounter.wait();
            }
            /* abort the message */
            receivingSessionListener.getAcceptHookMessage().abort(
                MessageAbortedEvent.RESPONSE413, null);
            /*
             * wait for the sending part to be notified by the library of the
             * abortion of the message
             */
            synchronized (sendingSessionListener.abortMessageCounter)
            {
                sendingSessionListener.abortMessageCounter.wait(6000);
            }

            /* confirm that we have an aborted message */
            assertEquals("We didn't got a call from the library to the "
                + "abortMessage", 1, sendingSessionListener.abortMessageCounter
                .size());
            /*
             * wait for the receiving part to be notified by the library of the
             * abortion of the message
             */
            synchronized (receivingSessionListener.abortMessageCounter)
            {
                receivingSessionListener.abortMessageCounter.wait(6000);
            }
            /* confirm that we have an aborted message */
            assertEquals("We didn't got a call from the library to the "
                + "abortMessage", 1, receivingSessionListener.abortMessageCounter
                .size());
            MessageAbortedEvent receivingAbortEvent = receivingSessionListener.messageAbortEvents.get(0);
            // let's wait for the event on the receive end to get the #
            // continuation flag
            assertTrue("Error, reason code different from #, got: "
                + receivingAbortEvent.getReason(),
                receivingAbortEvent.getReason() == MessageAbortedEvent.CONTINUATIONFLAG);


            /*
             * confirm that the aborted message size received before it was
             * aborted is on the 10% + buffer size%
             */
            long sendingMessageSize =
                sendingSessionListener.getAbortedMessage().getSize();
            // how much is Connection.OUTPUTBUFFERLENGTH
            long expectedIdealBValue = 10 * sendingMessageSize / 100;
            int expectedMaximumPValue =
                (int) ((expectedIdealBValue + Connection.OUTPUTBUFFERLENGTH) * 100 / sendingMessageSize);
            // quickfix of issue #28, let's be happy with values under 20
            expectedMaximumPValue = 19;
            int obtainedPValue =
                (int) ((int) (((OutgoingMessage) sendingSessionListener
                    .getAbortedMessage()).getSentBytes() * 100) / sendingMessageSize);
            assertTrue(
                "Aborted message's expected size is wrong, "
                    + "obtained: "
                    + obtainedPValue
                    + " maximum value tolerated: "
                    + expectedMaximumPValue
                    + " in bytes: expected: "
                    + (expectedIdealBValue + Connection.OUTPUTBUFFERLENGTH)
                    + " obtained: "
                    + (((OutgoingMessage) sendingSessionListener
                        .getAbortedMessage()).getSentBytes()),
                (obtainedPValue >= 10 && obtainedPValue <= expectedMaximumPValue));

            MessageAbortedEvent abortEvent =
                sendingSessionListener.messageAbortEvents.get(0);
            assertTrue("Error, reason code different from 413, got: "
                + abortEvent.getReason(),
                abortEvent.getReason() == MessageAbortedEvent.RESPONSE413);


        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Catched an exception that shouldn't occur:" + e.getMessage());
        }

    }
}
