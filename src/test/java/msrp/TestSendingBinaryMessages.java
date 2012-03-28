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

import msrp.messages.*;
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

    String tempFileDir;
    File tempFile;
    File receivingTempFile;

    Message outMessage = null;

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
            testProperties.load(TestSendingBinaryMessages.class
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

            receivingTempFile = null;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @After
    public void tearDownConnection()
    {
        // TODO needs: (?!) timer to mantain connection active even though
        // sessions are over (?!)
		receivingSessionListener.getReceiveMessage().discard();
    	if (outMessage != null)
    		outMessage.discard();
    	sendingSession.tearDown();
    	receivingSession.tearDown();
    	tempFile.delete();
        if (receivingTempFile != null)
        	receivingTempFile.delete();

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

            byte[] smallData = new byte[300 * 1024];

            randomGenerator.nextBytes(smallData);

            outMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            outMessage.setSuccessReport(false);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();
            byte[] smallData = new byte[1024 * 1024];
            randomGenerator.nextBytes(smallData);

            outMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            outMessage.setSuccessReport(false);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

            byte[] smallData = new byte[5024 * 1024];
            randomGenerator.nextBytes(smallData);

            outMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            outMessage.setSuccessReport(false);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

            byte[] smallData = new byte[300 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();

            outMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            outMessage.setSuccessReport(false);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();
            Long startTime = System.currentTimeMillis();
            System.out.println("Start generating and writing 5MB random data");
            byte[] smallData = new byte[5024 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();
            System.out.println("Done generating and writing 5MB random data, took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            outMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            outMessage.setSuccessReport(false);
            startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();
            /*
             * generate random data and fill the new tempFile created before any
             * test starts:
             */
            byte[] smallData = new byte[300 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            // let's make it interesting and add a CRLF to end of the data:
            Byte cr = Byte.decode("0x0D");
            Byte lf = Byte.decode("0x0A");
            smallData[300 * 1024 - 2] = cr.byteValue();
            smallData[300 * 1024 - 1] = lf.byteValue();
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();
            /* set the random data generated to be collected by the GC */
            smallData = null;

            outMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            outMessage.setSuccessReport(false);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            do
            {
                originalByte = originalFileStream.read();
                copiedByte = receivedFileStream.read();
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
     */
    @Test
    public void test20MBMessageFileToFile()
    {
        try
        {
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

            /*
             * generate random data and fill the new tempFile created before any
             * test starts:
             */
            Long startTime = System.currentTimeMillis();
            System.out.println("Start generating and writing 20MB random data");
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

            System.out.println("Done generating and writing "
                + tempFile.length() + "Bytes " + "random data, took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            /* set the random data generated to be collected by the GC */
            smallData = null;

            System.gc();

            outMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            outMessage.setSuccessReport(false);
            startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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

            byte[] original = new byte[1024];
            byte[] copied = new byte[1024];
            int oBytes;
            int cBytes;
            do
            {
                oBytes = originalFileStream.read(original);
                cBytes = receivedFileStream.read(copied);
                assertEquals("File's length differed:", oBytes, cBytes);
                assertArrayEquals("File's content differed:", original, copied);
            }
            while (oBytes != -1 && cBytes != -1);

            assertTrue("didn't reach end of file content at the same time, "
                + "file size/content differ", oBytes == -1 && cBytes == -1);

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

            byte[] smallData = new byte[300 * 1024];
            randomGenerator.nextBytes(smallData);

            outMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            outMessage.setSuccessReport(true);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

            byte[] smallData = new byte[1024 * 1024];
            randomGenerator.nextBytes(smallData);

            outMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            outMessage.setSuccessReport(true);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

            byte[] smallData = new byte[5024 * 1024];
            randomGenerator.nextBytes(smallData);

            outMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);
            outMessage.setSuccessReport(true);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+"ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();
            byte[] smallData = new byte[300 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();

            outMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            outMessage.setSuccessReport(true);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
    public void test5MBMessageFileToMemorySuccessReport()
    {
        try
        {
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

            Long startTime = System.currentTimeMillis();
            System.out.println("Start generating and writing 5MB random data");
            byte[] smallData = new byte[5024 * 1024];
            FileOutputStream fileStream = new FileOutputStream(tempFile);
            randomGenerator.nextBytes(smallData);
            fileStream.write(smallData);
            fileStream.flush();
            fileStream.close();
            System.out.println("Done generating and writing 5MB random data, took: "
                + ((System.currentTimeMillis() - startTime) / 1000) + "s");

            outMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            outMessage.setSuccessReport(true);
            startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            String methodName =
                new Exception().getStackTrace()[0].getMethodName();

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

            outMessage =
                new OutgoingFileMessage(sendingSession, "plain/text", tempFile);
            outMessage.setSuccessReport(true);
            Long startTime = System.currentTimeMillis();

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
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(methodName + " took: "
                + (System.currentTimeMillis() - startTime)+ "ms");

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
            do
            {
                originalByte = originalFileStream.read();
                copiedByte = receivedFileStream.read();
                assertEquals("Files content differ:", originalByte,
                    copiedByte);
            }
            while (originalByte != -1 && copiedByte != -1);

            assertTrue("didn't reach end of file content at the same time, "
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
}