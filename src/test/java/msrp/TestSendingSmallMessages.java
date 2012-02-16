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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

import msrp.utils.TextUtils;
import msrp.messages.*;
import msrp.testutils.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * Simple test used for debugging purposes to send a very small (2KB message)
 * 
 * @author João André Pereira Antunes
 * 
 */
public class TestSendingSmallMessages
{
    InetAddress address;

    Session sendingSession;

    static Random randomGenerator = new Random();
    
    Session receivingSession;

    MockMSRPSessionListener receivingSessionListener =
        new MockMSRPSessionListener("receivingSessionListener");

    MockMSRPSessionListener sendingSessionListener =
        new MockMSRPSessionListener("sendinSessionListener");

    File tempFile;

    String tempFileDir;

    @Before
    public void setUpConnection()
    {
        /* fetch the address to which this sessions are going to be bound: */
        Properties testProperties = new Properties();
        try
        {
            /* Set the limit to be of 30 MB of messages allowed in memory */
            MSRPStack.setShortMessageBytes(30024 * 1024);

            testProperties.load(TestReportMechanism.class
                .getResourceAsStream("/test.properties"));
            String addressString = testProperties.getProperty("address");
            /*
             * checks if we want the temp files on a specific directory. if the
             * propriety doesn't exist the default dir used by the JVM is used
             */
            tempFileDir = testProperties.getProperty("tempdirectory");
            address = InetAddress.getByName(addressString);
            sendingSession = new Session(false, false, address);
            receivingSession =
                new Session(false, false, sendingSession.getURI(), address);

            receivingSession.addListener(receivingSessionListener);
            sendingSession.addListener(sendingSessionListener);
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
    }

    /**
     * Tests sending a 3KB Binary Message with a MemoryDataContainer to a
     * MemoryDataContainer
     */
    @Test
    public void test3KBBinaryMessageMemoryToMemory()
    {
        try
        {
            byte[] smallData = new byte[3 * 1024];
            randomGenerator.nextBytes(smallData);

            Message threeHKbMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            threeHKbMessage.setSuccessReport(false);

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
                DataContainer dc = new MemoryDataContainer(3 * 1024);
                receivingSessionListener.setDataContainer(dc);
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait();
            }
            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            synchronized (receivingSessionListener)
            {
                /*
                 * allow message to be received
                 */
                receivingSessionListener.wait(500);
            }
            ByteBuffer receivedByteBuffer =
                receivingSessionListener.getReceiveMessage().getDataContainer()
                    .get(0, 0);
            byte[] receivedData = receivedByteBuffer.array();
            /*
             * assert that the received data matches the sent data
             */
            assertArrayEquals(smallData, receivedData);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Tests sending a 2KB Text Message with a MemoryDataContainer to a
     * MemoryDataContainer
     */
    @Test
    public void test2KBTextMessageMemoryToMemory()
    {
        try
        {
            byte[] smallData = new byte[2 * 1024];
            TextUtils.generateRandom(smallData);

            Message threeHKbMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            threeHKbMessage.setSuccessReport(false);

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
                DataContainer dc = new MemoryDataContainer(2 * 1024);
                receivingSessionListener.setDataContainer(dc);
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait();
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            synchronized (receivingSessionListener)
            {
                /*
                 * allow the message to be received
                 */
                receivingSessionListener.wait(500);
            }

            ByteBuffer receivedByteBuffer =
                receivingSessionListener.getReceiveMessage().getDataContainer()
                    .get(0, 0);
            byte[] receivedData = receivedByteBuffer.array();
            /*
             * assert that the received data matches the sent data
             */
            assertArrayEquals(smallData, receivedData);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
