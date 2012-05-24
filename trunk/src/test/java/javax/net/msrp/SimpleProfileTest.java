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


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;

import javax.net.msrp.FileDataContainer;
import javax.net.msrp.messages.OutgoingFileMessage;

import org.junit.*;

/**
 * 
 * Simple tests built for speed and to be used by the eclipse profiler (TPTP)
 * 
 * @author João André Pereira Antunes
 * 
 */
public class SimpleProfileTest extends TestFrame
{
    /**
     * Tests sending a 300KB Message with a MemoryDataContainer to a
     * MemoryDataContainer
     */
    @Test
    public void testProfile300KbTxtMsgMem2Mem()
    {
        byte[] data = new byte[300 * 1024];
        fillText(data);

        byte[] receivedData = memory2Memory(data, false);

        assertArrayEquals(data, receivedData);
    }

    @Test
    /**
     * Similar to the TestSendingExistingFile test, but doesn't has the JUnit
     * asserts at the end.
     * The main purpose is to provide the profiler with a test that spends the
     * runtime just sending the file. So that the asserts don't influence the 
     * execution-time statistics 
     */
    public void testProfileSendExistingFile2File()
    {
        /* set up the sending session to send the existing file: */
        File fileToSend = new File("resources/tests/fileToSend");
        assertTrue("Error, file: fileToSend doesn't exist, expected in: "
            + fileToSend.getAbsolutePath(), fileToSend.exists());

        try
        {
            outMessage = new OutgoingFileMessage(sendingSession,
                					"prs.genericfile/prs.rawbyte", fileToSend);
	        outMessage.setSuccessReport(true);
	
	        /*
	         * set up the receiving session handler to receive the data to the
	         * receivedFile
	         */
	        receivingTempFile = new File("receivedFile");
	
	        FileDataContainer fdc = new FileDataContainer(receivingTempFile);
	        receivingSessionListener.setDataContainer(fdc);
	
	        send(fdc);
        }
        catch (Exception e)
        {
            fail(e.getMessage());
        }
    }
}
