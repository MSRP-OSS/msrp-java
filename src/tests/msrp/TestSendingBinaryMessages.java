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

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;

import msrp.utils.TextUtils;
import msrp.testutils.*;

import org.junit.*;

/**
 * Test of sending multiple messages, with binary content, with different sizes
 * and in different ways, using all the data containers available.
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class TestSendingBinaryMessages
{
    InetAddress address;

    Session sendingSession;

    Session receivingSession;

    static Random randomGenerator = new Random();

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

            /* Gets the address from the configuration file */
            testProperties.load(TestReportMechanism.class
                .getResourceAsStream("/test.properties"));
            String addressString = testProperties.getProperty("address");
            /*
             * checks if we want the temp files on a specific directory. if the
             * propriety doesn't exist the default dir used by the JVM is used
             */
            tempFileDir = testProperties.getProperty("tempdirectory");
            address = InetAddress.getByName(addressString);
            
            /* Sets up the sessions used for message transfer: */
            sendingSession = new Session(false, false, address);
            receivingSession =
                new Session(false, false, sendingSession.getURI(), address);

            receivingSession.addMSRPSessionListener(receivingSessionListener);
            sendingSession.addMSRPSessionListener(sendingSessionListener);
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
        receivingSessionListener.getReceiveMessage().getDataContainer()
            .dispose();

        tempFile.delete();
        /* To remove: */
        System.gc();
    }

    /**
     * Tests sending a 300KB Message with a MemoryDataContainer
     */
    @Test
    public void test300KBMessageMemoryToMemory()
    {
        try
        {

            byte[] smallData = new byte[300 * 1024];

            randomGenerator.nextBytes(smallData);
            
            Message threeHKbMessage =
                new Message(sendingSession, "plain/text", smallData);
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
                DataContainer dc = new MemoryDataContainer(300 * 1024);
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
     * Tests sending a 1MB Message with a MemoryDataContainer
     */
    @Test
    public void test1MbMessageMemoryToMemory()
    {
        try
        {

            byte[] smallData = new byte[1024 * 1024];
            randomGenerator.nextBytes(smallData);

            Message threeHKbMessage =
                new Message(sendingSession, "plain/text", smallData);
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
                DataContainer dc = new MemoryDataContainer(1024 * 1024);
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
     * Tests sending a 5MB Message with a MemoryDataContainer
     */
    @Test
    public void test5MbMessageMemoryToMemory()
    {
        try
        {

            byte[] smallData = new byte[5024 * 1024];
            randomGenerator.nextBytes(smallData);

            Message threeHKbMessage =
                new Message(sendingSession, "plain/text", smallData);
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
                DataContainer dc = new MemoryDataContainer(5024 * 1024);
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
     * Tests sending a 300KB Message with a FileDataContainer
     */
    @Test
    public void test300KBMessageFileToMemory()
    {
        try
        {

            byte[] smallData = new byte[300 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();

            Message threeHKbMessage =
                new FileMessage(sendingSession, "plain/text", tempFile);

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
     * Tests sending a 5MB Message with a FileDataContainer
     */
    @Test
    public void test5MBMessageFileToMemory()
    {
        try
        {

            Long startTime = System.currentTimeMillis();
            System.out.println("Starting generating and writing 5MB "
                + "of random data");
            byte[] smallData = new byte[5024 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();
            System.out.println("Stoped generating and writing 5MB "
                + "of random data took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            Message threeHKbMessage =
                new FileMessage(sendingSession, "plain/text", tempFile);

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
     * Tests sending a 300KB Message with a FileDataContainer to a
     * FileDataContainer
     */
    @Test
    public void test300KBMessageFileToFile()
    {
        try
        {

            /*
             * generate random data and fill the new tempFile created before any
             * test starts:
             */
            byte[] smallData = new byte[300 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();
            /* set the random data generated to be collected by the GC */
            smallData = null;

            Message threeHKbMessage =
                new FileMessage(sendingSession, "plain/text", tempFile);

            threeHKbMessage.setSuccessReport(false);

            /* connect the two sessions: */

            ArrayList<URI> toPathSendSession = new ArrayList<URI>();
            toPathSendSession.add(receivingSession.getURI());

            sendingSession.addToPath(toPathSendSession);

            /*
             * message should be transfered or in the process of being
             * completely transfered
             */

            /*
             * generate a new temporary file to receive data and wrap it on a
             * FileDataContainer:
             */
            File receivingTempFile;
            if (tempFileDir != null)
            {
                receivingTempFile =
                    File.createTempFile("recv"
                        + Long.toString(System.currentTimeMillis()), null,
                        new File(tempFileDir));
            }
            else
                receivingTempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, null);

            FileDataContainer newFileDC =
                new FileDataContainer(receivingTempFile);

            /*
             * make the mocklistener accept the message on the newly created
             * file:
             */
            synchronized (receivingSessionListener)
            {

                receivingSessionListener.setDataContainer(newFileDC);
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

            /*
             * compare the two files content byte by byte:
             */
            FileInputStream originalFileStream = new FileInputStream(tempFile);

            /* Get the FileDataContainer's file from the received message: */
            FileDataContainer fileDC =
                (FileDataContainer) receivingSessionListener
                    .getReceiveMessage().getDataContainer();

            FileInputStream receivedFileStream =
                new FileInputStream(fileDC.getFile());

            int originalByte;
            int copiedByte;
            int i, j;
            i = j = 0;
            do
            {
                originalByte = originalFileStream.read();
                copiedByte = receivedFileStream.read();
                i++;
                j++;
                assertEquals("File's content differed:", originalByte,
                    copiedByte);
            }
            while (originalByte != -1 && copiedByte != -1);

            assertTrue("didn't reached end of file content at the same time, "
                + "file size/content differ", originalByte == -1
                && copiedByte == -1);

            /* free the resources: */

            receivedFileStream.close();
            originalFileStream.close();

        }
        catch (Exception e)
        {

            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Tests sending a 20MB Message with a FileDataContainer to a
     * FileDataContainer
     * 
     * FIXME - for later - minimize the memory took by the stack - make the
     * counter work in a different manner (bit by bit counter)
     * 
     * FIXME BUG took 274,5s of which only 10s of overhead of creating the file
     * the fix probably passes by changing the read cycle and the way it gets
     * the data from the file, that currently is byte by byte
     */
    @Ignore("Too big, takes too much time")
    @Test
    public void test20MBMessageFileToFile()
    {
        try
        {

            /*
             * generate random data and fill the new tempFile created before any
             * test starts:
             */
            Long startTime = System.currentTimeMillis();
            System.out.println("Starting generating and writing 20MB "
                + "of random data");
            byte[] smallData;
            FileOutputStream fileStream = new FileOutputStream(tempFile);

            for (int i = 0; i < 20; i++)
            {
                smallData = new byte[1024 * 1024];
                randomGenerator.nextBytes(smallData);
                fileStream.write(smallData);
                fileStream.flush();
            }
            fileStream.close();

            System.out.println("Stoped generating and writing "
                + tempFile.length() + "Bytes " + "of random data took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            /* set the random data generated to be collected by the GC */
            smallData = null;

            System.gc();

            Message threeHKbMessage =
                new FileMessage(sendingSession, "plain/text", tempFile);

            threeHKbMessage.setSuccessReport(false);

            /* connect the two sessions: */

            ArrayList<URI> toPathSendSession = new ArrayList<URI>();
            toPathSendSession.add(receivingSession.getURI());

            sendingSession.addToPath(toPathSendSession);

            /*
             * message should be transfered or in the process of being
             * completely transfered
             */

            /*
             * generate a new temporary file to receive data and wrap it on a
             * FileDataContainer:
             */
            File receivingTempFile;
            if (tempFileDir != null)
            {
                receivingTempFile =
                    File.createTempFile("recv"
                        + Long.toString(System.currentTimeMillis()), null,
                        new File(tempFileDir));
            }
            else
                receivingTempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, null);

            FileDataContainer newFileDC =
                new FileDataContainer(receivingTempFile);

            /*
             * make the mocklistener accept the message on the newly created
             * file:
             */
            synchronized (receivingSessionListener)
            {

                receivingSessionListener.setDataContainer(newFileDC);
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

            /*
             * compare the two files content byte by byte:
             */
            FileInputStream originalFileStream = new FileInputStream(tempFile);

            /* Get the FileDataContainer's file from the received message: */
            FileDataContainer fileDC =
                (FileDataContainer) receivingSessionListener
                    .getReceiveMessage().getDataContainer();

            FileInputStream receivedFileStream =
                new FileInputStream(fileDC.getFile());

            int originalByte;
            int copiedByte;
            int i, j;
            i = j = 0;
            do
            {
                originalByte = originalFileStream.read();
                copiedByte = receivedFileStream.read();
                i++;
                j++;
                assertEquals("File's content differed:", originalByte,
                    copiedByte);
            }
            while (originalByte != -1 && copiedByte != -1);

            assertTrue("didn't reached end of file content at the same time, "
                + "file size/content differ", originalByte == -1
                && copiedByte == -1);

            /* free the resources: */

            receivedFileStream.close();
            originalFileStream.close();

        }
        catch (Exception e)
        {

            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Tests sending a 300KB Message with a MemoryDataContainer
     */
    @Test
    public void test300KBMessageMemoryToMemorySuccessReport()
    {
        try
        {

            byte[] smallData = new byte[300 * 1024];
            randomGenerator.nextBytes(smallData);

            Message threeHKbMessage =
                new Message(sendingSession, "plain/text", smallData);
            threeHKbMessage.setSuccessReport(true);

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
                DataContainer dc = new MemoryDataContainer(300 * 1024);
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
     * Tests sending a 1MB Message with a MemoryDataContainer
     */
    @Test
    public void test1MbMessageMemoryToMemorySuccessReport()
    {
        try
        {

            byte[] smallData = new byte[1024 * 1024];
            randomGenerator.nextBytes(smallData);

            Message threeHKbMessage =
                new Message(sendingSession, "plain/text", smallData);
            threeHKbMessage.setSuccessReport(true);

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
                DataContainer dc = new MemoryDataContainer(1024 * 1024);
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
     * Tests sending a 5MB Message with a MemoryDataContainer
     */
    @Test
    public void test5MbMessageMemoryToMemorySuccessReport()
    {
        try
        {

            byte[] smallData = new byte[5024 * 1024];
            randomGenerator.nextBytes(smallData);

            Message threeHKbMessage =
                new Message(sendingSession, "plain/text", smallData);
            threeHKbMessage.setSuccessReport(true);

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
                DataContainer dc = new MemoryDataContainer(5024 * 1024);
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
     * Tests sending a 300KB Message with a FileDataContainer
     */
    @Test
    public void test300KBMessageFileToMemorySuccessReport()
    {
        try
        {

            byte[] smallData = new byte[300 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();

            Message threeHKbMessage =
                new FileMessage(sendingSession, "plain/text", tempFile);

            threeHKbMessage.setSuccessReport(true);

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
     * Tests sending a 5MB Message with a FileDataContainer
     * FIXME test currently being ignored because of memory issues
     */
    @Ignore("Java Heap space problems running these batch of tests if this one isn't ignored")
    @Test
    public void test5MBMessageFileToMemorySuccessReport()
    {
        try
        {

            Long startTime = System.currentTimeMillis();
            System.out.println("Starting generating and writing 5MB "
                + "of random data");
            byte[] smallData = new byte[5024 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();
            System.out.println("Stoped generating and writing 5MB "
                + "of random data took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            Message threeHKbMessage =
                new FileMessage(sendingSession, "plain/text", tempFile);

            threeHKbMessage.setSuccessReport(true);

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
     * Tests sending a 300KB Message with a FileDataContainer to a
     * FileDataContainer
     */
    @Test
    public void test300KBMessageFileToFileSuccessReport()
    {
        try
        {

            /*
             * generate random data and fill the new tempFile created before any
             * test starts:
             */
            byte[] smallData = new byte[300 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();
            /* set the random data generated to be collected by the GC */
            smallData = null;

            Message threeHKbMessage =
                new FileMessage(sendingSession, "plain/text", tempFile);

            threeHKbMessage.setSuccessReport(true);

            /* connect the two sessions: */

            ArrayList<URI> toPathSendSession = new ArrayList<URI>();
            toPathSendSession.add(receivingSession.getURI());

            sendingSession.addToPath(toPathSendSession);

            /*
             * message should be transfered or in the process of being
             * completely transfered
             */

            /*
             * generate a new temporary file to receive data and wrap it on a
             * FileDataContainer:
             */
            File receivingTempFile;
            if (tempFileDir != null)
            {
                receivingTempFile =
                    File.createTempFile("recv"
                        + Long.toString(System.currentTimeMillis()), null,
                        new File(tempFileDir));
            }
            else
                receivingTempFile =
                    File.createTempFile(Long.toString(System
                        .currentTimeMillis()), null, null);

            FileDataContainer newFileDC =
                new FileDataContainer(receivingTempFile);

            /*
             * make the mocklistener accept the message on the newly created
             * file:
             */
            synchronized (receivingSessionListener)
            {

                receivingSessionListener.setDataContainer(newFileDC);
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

            /*
             * compare the two files content byte by byte:
             */
            FileInputStream originalFileStream = new FileInputStream(tempFile);

            /* Get the FileDataContainer's file from the received message: */
            FileDataContainer fileDC =
                (FileDataContainer) receivingSessionListener
                    .getReceiveMessage().getDataContainer();

            FileInputStream receivedFileStream =
                new FileInputStream(fileDC.getFile());

            int originalByte;
            int copiedByte;
            int i, j;
            i = j = 0;
            do
            {
                originalByte = originalFileStream.read();
                copiedByte = receivedFileStream.read();
                i++;
                j++;
                assertEquals("File's content differed:", originalByte,
                    copiedByte);
            }
            while (originalByte != -1 && copiedByte != -1);

            assertTrue("didn't reached end of file content at the same time, "
                + "file size/content differ", originalByte == -1
                && copiedByte == -1);

            /* free the resources: */

            receivedFileStream.close();
            originalFileStream.close();

        }
        catch (Exception e)
        {

            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /*
     * Currently not tested due to lack of Heap space (they could run with
     * appropriate session and message disposal)
     */
    // /**
    // * Tests sending a 10MB Message with a MemoryDataContainer
    // */
    // @Test
    // public void test10MbMessageMemoryToMemoryToMemory() {
    // try {
    //
    // byte[] smallData = new byte[10024 * 1024];
    // TextUtils.generateRandom(smallData);
    //
    // Message threeHKbMessage = new Message(sendingSession, "plain/text",
    // smallData);
    // threeHKbMessage.setSuccessReport(false);
    //
    // connect the two sessions:
    //
    // ArrayList<URI> toPathSendSession = new ArrayList<URI>();
    // toPathSendSession.add(receivingSession.getURI());
    //
    //
    // sendingSession.addToPath(toPathSendSession);
    //
    //			
    // * message should be transfered or in the process of being
    // * completely transfered
    //			 
    //
    // make the mocklistener accept the message
    // synchronized (receivingSessionListener) {
    // DataContainer dc = new MemoryDataContainer(10024 * 1024);
    // receivingSessionListener.setDataContainer(dc);
    // receivingSessionListener.setAcceptHookResult(new Boolean(true));
    // receivingSessionListener.notify();
    // receivingSessionListener.wait(3000);
    // }
    //
    // if (receivingSessionListener.getAcceptHookMessage() == null
    // || receivingSessionListener.getAcceptHookSession() == null)
    // fail("The Mock didn't worked and the message didn't got "
    // + "accepted");
    //
    // synchronized (receivingSessionListener) {
    //				
    // * allow the message to be received
    //				 
    // receivingSessionListener.wait();
    // }
    //
    // ByteBuffer receivedByteBuffer = receivingSessionListener
    // .getReceiveMessage().getDataContainer().get(0, 0);
    // byte[] receivedData = receivedByteBuffer.array();
    //			
    // * assert that the received data matches the sent data
    //			 
    // assertArrayEquals(smallData, receivedData);
    //
    // } catch (Exception e) {
    // e.printStackTrace();
    // fail(e.getMessage());
    // }
    // }
    //
    // /**
    // * Tests sending a 20MB Message with a MemoryDataContainer
    // */
    // @Test
    // public void test20MbMessageMemoryToMemory() {
    // try {
    //
    // byte[] smallData = new byte[20024 * 1024];
    // TextUtils.generateRandom(smallData);
    //
    // Message threeHKbMessage = new Message(sendingSession, "plain/text",
    // smallData);
    // threeHKbMessage.setSuccessReport(false);
    //
    // connect the two sessions:
    //
    // ArrayList<URI> toPathSendSession = new ArrayList<URI>();
    // toPathSendSession.add(receivingSession.getURI());
    //
    //
    // sendingSession.addToPath(toPathSendSession);
    //
    //			
    // * message should be transfered or in the process of being
    // * completely transfered
    //			 
    //
    // make the mocklistener accept the message
    // synchronized (receivingSessionListener) {
    // DataContainer dc = new MemoryDataContainer(20024 * 1024);
    // receivingSessionListener.setDataContainer(dc);
    // receivingSessionListener.setAcceptHookResult(new Boolean(true));
    // receivingSessionListener.notify();
    // receivingSessionListener.wait(3000);
    // }
    //
    // if (receivingSessionListener.getAcceptHookMessage() == null
    // || receivingSessionListener.getAcceptHookSession() == null)
    // fail("The Mock didn't worked and the message didn't got "
    // + "accepted");
    //
    // synchronized (receivingSessionListener) {
    //				
    // * allow the message to be received
    //				 
    // receivingSessionListener.wait();
    // }
    //
    // ByteBuffer receivedByteBuffer = receivingSessionListener
    // .getReceiveMessage().getDataContainer().get(0, 0);
    // byte[] receivedData = receivedByteBuffer.array();
    //			
    // * assert that the received data matches the sent data
    //			 
    // assertArrayEquals(smallData, receivedData);
    //
    // } catch (Exception e) {
    // e.printStackTrace();
    // fail(e.getMessage());
    // }
    // }
}
