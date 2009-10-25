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

import java.io.*;
import java.net.*;
import java.util.*;

import msrp.messages.*;
import msrp.testutils.*;

import org.junit.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

/**
 * This class tests the behaviour of the library when sending an existing file
 * to an endpoint and assuring that all of the correct events were called. The
 * file sent is resources/tests/fileToSend
 * 
 * @author João André Pereira Antunes
 * 
 */
public class TestSendingExistingFile
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

            testProperties.load(TestSendingExistingFile.class
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

    @Test
    public void testSendingExistingFileToFile()
    {
        /* set up the sending session to send the existing file: */
        File fileToSend = new File("resources/tests/fileToSend");
        assertTrue("Error, file: fileToSend doesn't exist, expected in: "
            + fileToSend.getAbsolutePath(), fileToSend.exists());
        String sizeFileString = "file with: " + fileToSend.length() + " bytes";

        Message messageToBeSent = null;
        try
        {
            messageToBeSent =
                new OutgoingFileMessage(sendingSession,
                    "prs.genericfile/prs.rawbyte", fileToSend);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
        messageToBeSent.setSuccessReport(true);
        Long startTime = System.currentTimeMillis();

        /* add the listener */
        sendingSession.addMSRPSessionListener(sendingSessionListener);

        /*
         * set up the receiving session handler to receive the data to the
         * receivedFile
         */

        File receivedFile = new File("receivedFile");

        FileDataContainer receivedFileDC = null;
        try
        {
            receivedFileDC = new FileDataContainer(receivedFile);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
        receivingSessionListener.setDataContainer(receivedFileDC);

        receivingSession.addMSRPSessionListener(receivingSessionListener);

        /* start the transfer by adding the toPath to the sendingSession */
        ArrayList<URI> toPathSendSession = new ArrayList<URI>();
        toPathSendSession.add(receivingSession.getURI());
        try
        {
            sendingSession.addToPath(toPathSendSession);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }

        /*
         * message should be transfered or in the process of being completely
         * transfered
         */

        try
        {
            /* make the mocklistener accept the message */
            synchronized (receivingSessionListener)
            {
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait(10000);
            }

            // confirm that the message got accepted on the
            // receivingSessionListener
            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock (?!) didn't worked and the message didn't got "
                    + "accepted");

            synchronized (receivingSessionListener)
            {
                /*
                 * allow the message to be received
                 */
                receivingSessionListener.wait();
            }
            System.out.println(sizeFileString
                + " took: "
                + (System.currentTimeMillis() - startTime)
                + " ms");

            // confirm that the updateSendStatus got called on the
            // sendingSessionListener at least once
            synchronized (sendingSessionListener.updateSendStatusCounter)
            {
                sendingSessionListener.updateSendStatusCounter.wait(5000);
            }
            assertTrue("The updateSendStatus wasn't called even once",
                sendingSessionListener.updateSendStatusCounter.size() >= 1);

            // confirm that the receivedMessage got called on the
            // receivingSessionListener
            assertTrue("The receiveMessage wasn't called",
                receivingSessionListener.getReceiveMessage() != null);

            // confirm that a success report for the whole message was received
            // on the sendingSessionListener
            assertTrue(
                "The success report for the whole message wasn't received",
                sendingSessionListener.successReportCounter.size() >= 1);
            Long lastReport =
                sendingSessionListener.successReportCounter
                    .get(sendingSessionListener.successReportCounter.size() - 1);
            assertEquals(
                "Error, the last success report has a wrong size reported",
                fileToSend.length(), lastReport.longValue());

            // confirm that the received and sent files have the same content
            FileInputStream sentFileStream = new FileInputStream(fileToSend);
            FileInputStream receivedFileStream =
                new FileInputStream(receivedFile);
            // create two 500KB buffer to assert that file content is equal
            byte[] bufferSent = new byte[5 * 1024];
            byte[] bufferReceived = new byte[5 * 1024];
            while (sentFileStream.read(bufferSent) != -1)
            {
                receivedFileStream.read(bufferReceived);
                assertArrayEquals("Error, file contents differ!",
                    bufferSent, bufferSent);
            }

            // clean up:
            // TODO needs: tear down of the sessions
            sentFileStream.close();
            receivedFileStream.close();

        }
        catch (InterruptedException e)
        {
            fail(e.getMessage());
        }
        catch (FileNotFoundException e)
        {
            fail(e.getMessage());
        }
        catch (IOException e)
        {
            fail(e.getMessage());
        }

    }

}
