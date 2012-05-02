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

import java.io.*;
import java.net.*;
import java.util.*;

import org.junit.*;

import msrp.utils.*;
import msrp.messages.Message;
import msrp.messages.OutgoingMessage;
import msrp.testutils.*;

/**
 * This class is used to test that the library correctly sends the data as
 * specified by the protocol. More specifically it checks that the library
 * splits the message in one or more transactions if the end-of-line is
 * contained by the data to transmit.
 * 
 * @author João André Pereira Antunes
 * 
 */
public class TestCorrectlyBreaksSentData
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

            receivingSession.addListener(receivingSessionListener);
            sendingSession.addListener(sendingSessionListener);
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
        // sessions are over (?!)
    	Message in = receivingSessionListener.getReceiveMessage();
    	if (in != null)
    		in.discard();

        tempFile.delete();
        /* To remove: */
        System.gc();
    }

    /**
     * This method asserts that if we try to send a message whose data contains
     * valid end-line characters, the message is broken into at least two
     * transactions
     */
    @Test
    public void testBreakingOfTransactions()
    {
        try
        {
            /* first let's generate the random data to transfer */
            byte[] smallData = new byte[499];
            binaryRandom.nextBytes(smallData);

            /* let's fabricate the new TID */
            byte[] tid = new byte[8];
            String tidString;
            TextUtils.generateRandom(tid);
            tidString = new String(tid, TextUtils.usascii);

            /* debug of the test: */
            System.out.println(this.getClass().getName() + ": Generated the Tx-id: " + tidString);
            /* assign it to the transaction manager of the sending session */

            sendingSession.getConnection().getTransactionManager().testing = true;
            sendingSession.getConnection().getTransactionManager().presetTID = tidString;

            /*
             * now let's generate the end-line and put it in the middle of the
             * data of the message
             */
            byte[] phonyEndLine = ("-------" + tidString + "$").getBytes();
            int i, j;

            for (i = 0, j = 300; i < phonyEndLine.length; i++, j++)
                smallData[j] = phonyEndLine[i];

            /*
             * all set, let's assign the message to the sending session and
             * connect them
             */
            Message newMessage =
                new OutgoingMessage(sendingSession, "plain/text", smallData);

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
                DataContainer dc = new MemoryDataContainer(499);
                receivingSessionListener.setDataContainer(dc);
                receivingSessionListener.setAcceptHookResult(new Boolean(true));
                receivingSessionListener.notify();
                receivingSessionListener.wait();
                receivingSessionListener.wait(500);
            }

            if (receivingSessionListener.getAcceptHookMessage() == null
                || receivingSessionListener.getAcceptHookSession() == null)
                fail("The Mock didn't work, message not accepted");

            /*
             * after the message is received, let's check the list of
             * existingTransactions on the sending session to make sure that the
             * message was transfered in two SEND transactions with different
             * tIDs
             */
            Collection<Transaction> existingTransactions =
                sendingSession.getTransactionManager()
                    .getExistingTransactions();

            boolean foundFirstSendTransaction = false;
            boolean foundSecondSendTransaction = false;
            for (Transaction transaction : existingTransactions)
            {
                if (transaction.getTID().equals(tidString)
                    && transaction.transactionType == TransactionType.SEND)
                {
                    if (foundFirstSendTransaction)
                        fail("Something odd happened, there was "
                            + "more than one transaction found "
                            + "with the first's transaction tid");

                    foundFirstSendTransaction = true;
                }
                if (!transaction.getTID().equals(tidString)
                    && transaction.transactionType == TransactionType.SEND)
                {
                    if (foundSecondSendTransaction)
                        fail("Something odd happened, there was "
                            + "more than one transaction found "
                            + "after the first transaction. "
                            + "there should only be two SEND transactions");

                    foundSecondSendTransaction = true;
                }
            }// for (Transaction transaction : existingTransactions)

            assertEquals("first transaction not found!", true,
                foundFirstSendTransaction);
            assertEquals("second transaction not found!", true,
                foundSecondSendTransaction);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("It occurred an unexpected exception: " + e.getMessage());
        }
    }
}
