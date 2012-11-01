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
package javax.net.msrp;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.net.msrp.FileDataContainer;

import org.junit.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

/**
 * This class tests the behaviour of the library when sending an existing file
 * to an end point and assuring that all of the correct events were called. The
 * file sent is resources/tests/fileToSend
 * 
 * @author João André Pereira Antunes
 * 
 */
public class TestSendingExistingFile extends TestFrame
{
    @Test
    public void testSendingExistingFileToFile()
    {
        /* set up the sending session to send the existing file: */
        File fileToSend = new File("resources/tests/fileToSend");
        assertTrue("Error, file: fileToSend doesn't exist, expected in: "
            + fileToSend.getAbsolutePath(), fileToSend.exists());
        String sizeFileString = "file with: " + fileToSend.length() + " bytes";

        OutgoingMessage messageToBeSent = null;
        try
        {
            messageToBeSent = new OutgoingMessage("prs.genericfile/prs.rawbyte",
            						fileToSend);
	        messageToBeSent.setSuccessReport(true);
            sendingSession.sendMessage(messageToBeSent);
	        Long startTime = System.currentTimeMillis();

	        sendingSession.addListener(sendingSessionListener);

	        /*
	         * set up the receiving session handler to receive the data to the
	         * receivedFile
	         */
	        File receivedFile = new File("receivedFile");

	        FileDataContainer receivedFDC = null;
            receivedFDC = new FileDataContainer(receivedFile);
	        receivingSessionListener.setDataContainer(receivedFDC);

	        receivingSession.addListener(receivingSessionListener);

	        /* start transfer by adding the toPath to the sendingSession */
	        ArrayList<URI> toPathSendSession = new ArrayList<URI>();
	        toPathSendSession.add(receivingSession.getURI());
            sendingSession.setToPath(toPathSendSession);

            /* make the MockListener accept the message */
            synchronized (receivingSessionListener)
            {
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait();
                receivingSessionListener.wait(1000);
            }
            if (receivingSessionListener.getAcceptHookMessage() == null ||
                receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            System.out.println(sizeFileString + " took: "
                + (System.currentTimeMillis() - startTime) + " ms");

            // confirm that the updateSendStatus got called on the
            // sendingSessionListener at least once
            synchronized (sendingSessionListener.updateSendStatusCounter)
            {
                sendingSessionListener.updateSendStatusCounter.wait(500);
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
            sentFileStream.close();
            receivedFileStream.close();
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }
}
