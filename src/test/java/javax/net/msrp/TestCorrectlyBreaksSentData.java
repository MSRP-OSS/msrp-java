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

import static org.junit.Assert.*;

import java.util.*;

import javax.net.msrp.Transaction;
import javax.net.msrp.TransactionType;
import javax.net.msrp.utils.*;

import org.junit.*;


/**
 * This class is used to test that the library correctly sends the data as
 * specified by the protocol. More specifically it checks that the library
 * splits the message in one or more transactions if the end-of-line is
 * contained by the data to transmit.
 * 
 * @author João André Pereira Antunes
 * 
 */
public class TestCorrectlyBreaksSentData extends TestFrame
{
    /**
     * This method asserts that if we try to send a message whose data contains
     * valid end-line characters, the message is broken into at least two
     * transactions
     */
    @Test
    public void testBreakingOfSmallTransaction()
    {
    	breakTransactionTest(499);
    }

    @Test
    public void testBreakingOfLargeTransaction()
    {
    	breakTransactionTest(2499);
    }

    void breakTransactionTest(int dataSize) 
    {
    	try
        {
            /* first let's generate the random data to transfer */
            byte[] data = new byte[dataSize];
            fillBinary(data);

            /* let's fabricate the new TID */
            byte[] tid = new byte[8];
            String tidString;
            TextUtils.generateRandom(tid);
            tidString = new String(tid, TextUtils.utf8);

            /* debug of the test: */
            System.out.println(this.getClass().getName() + ": Generated the Tx-id: " + tidString);

            /* assign it to the transaction manager of the sending session */
            sendingSession.getConnection().getTransactionManager().testing = true;
            sendingSession.getConnection().getTransactionManager().presetTID = tidString;

            /*
             * now let's generate the end-line and put it in the middle of the
             * message data
             */
            byte[] phonyEndLine = ("-------" + tidString + "$").getBytes();
            int i, j;

            for (i = 0, j = 300; i < phonyEndLine.length; i++, j++)
                data[j] = phonyEndLine[i];

            /*
             * all set, let's assign the message to the sending session and
             * connect them
             */
            sendingSession.sendMessage(
            		new OutgoingMessage("plain/text", data));

            triggerSendReceive(data);
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
                    && transaction.getTransactionType() == TransactionType.SEND)
                {
                    if (foundFirstSendTransaction)
                        fail("Something odd happened, there was "
                            + "more than one transaction found "
                            + "with the first's transaction tid");

                    foundFirstSendTransaction = true;
                }
                if (!transaction.getTID().equals(tidString)
                    && transaction.getTransactionType() == TransactionType.SEND)
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
            fail("Unexpected exception: " + e.getMessage());
        }
    }
}
