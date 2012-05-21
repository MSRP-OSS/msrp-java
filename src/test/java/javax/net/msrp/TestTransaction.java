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

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Random;

import javax.net.msrp.Transaction;
import javax.net.msrp.TransactionManager;
import javax.net.msrp.TransactionType;
import javax.net.msrp.exceptions.ConnectionParserException;
import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.exceptions.ImplementationException;
import javax.net.msrp.exceptions.InternalErrorException;
import javax.net.msrp.exceptions.InvalidHeaderException;
import javax.net.msrp.utils.TextUtils;

import org.junit.*;

import static org.junit.Assert.*;


/**
 * Tests for the transaction class. At the moment it tests mainly the parser
 * method of the class and if it has the expected behavior
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class TestTransaction
{

    static Charset usascii = Charset.forName("US-ASCII");

    private static String emptyInvalidHeaderCompleteSendTransaction;

    private static String emptyCompleteSendTransaction;

    private static String completeSendTransaction1;
    private static String completeSendTransaction2;
    private static String completeSendTransaction;

    static private ArrayList<String> emptyCSTslicedRandomly =
        new ArrayList<String>();

    static private String tID;

    static MiscTests instanceMT;

    static Random random;

    static byte[] randomData;

    static TransactionManager dummyTransactionManager;

    @BeforeClass
    public static void setUpValues()
    {
        emptyCompleteSendTransaction =
            ("To-Path: javax.net.msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                + "From-Path: javax.net.msrp://192.168.2.3:1324/123asd;tcp\r\n"
                + "Message-ID: 12345\r\n" + "Byte-Range: 1-0/0\r\n"
                + "Failure-report: yEs\r\n" + "Success-report: no\r\n");

        emptyInvalidHeaderCompleteSendTransaction =
            ("To-Path: javax.net.msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                + "From-Path: javax.net.msrp://192.168.2.3:1324/123asd;tcp javax.net.msrp://from.a.logn.way.away/haha;tcp\r\n"
                + "Message-ID: 12345\r\n" + "Byte-Range: 1-0/0\r\n"
                + "Failure-report: ygs\r\n" + "Success-report: no\r\n");
        instanceMT = new MiscTests();
        random = new Random();
        // Randomly split the transactions strings
        int i, j;
        for (i = 0, j = 0; (i + 1) < emptyCompleteSendTransaction.length(); i +=
            j)
        {
            j = random.nextInt(emptyCompleteSendTransaction.length() - i);
            emptyCSTslicedRandomly.add(emptyCompleteSendTransaction.substring(
                i, j + i));

        }
        byte[] tid = new byte[8];
        TextUtils.generateRandom(tid);
        tID = new String(tid, TextUtils.utf8);

        // TODO add support for the content-type in MSRP (?) [at least the
        // parsing should be done to be present on the message type]
        randomData = new byte[30];
        TextUtils.generateRandom(randomData);
        completeSendTransaction1 =
            ("To-Path: javax.net.msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                + "From-Path: javax.net.msrp://192.168.2.3:1324/123asd;tcp\r\n"
                + "Message-ID: 12346\r\n" + "Byte-Range: 1-30/30\r\n"
                + "Content-Type: somethingTODO/notimportantATM\r\n" + "\r\n");
        completeSendTransaction2 = new String(randomData, TextUtils.utf8);
        completeSendTransaction = completeSendTransaction1 + completeSendTransaction2;
        dummyTransactionManager = new TransactionManager();

    }

    @Test
    public void testParsingCompleteBody()
        throws InvalidHeaderException,
        ImplementationException, IllegalUseException, ConnectionParserException
    {

        Transaction newTransaction =
            new Transaction(tID, TransactionType.REPORT,
                dummyTransactionManager, Transaction.IN);
        newTransaction.parse(completeSendTransaction1.getBytes(TextUtils.utf8), 0,
            completeSendTransaction1.length(), false);
        newTransaction.parse(completeSendTransaction2.getBytes(TextUtils.utf8), 0,
                completeSendTransaction2.length(), true);
        newTransaction.signalizeEnd('$');

        try
        {
            assertArrayEquals("Error there was a problem parsing the data",
                randomData, newTransaction.getBody(0));
        }
        catch (InternalErrorException e)
        {
            fail("Error ocurred while retrieving the body of the transaction");
        }

    }

    @Test
    public void testParsingUnPreparsedCompleteBody()
        throws InvalidHeaderException,
        ImplementationException, IllegalUseException, ConnectionParserException
    {

        Transaction newTransaction =
            new Transaction(tID, TransactionType.REPORT,
                dummyTransactionManager, Transaction.IN);
        newTransaction.parse(completeSendTransaction.getBytes(TextUtils.utf8), 0,
            completeSendTransaction.length(), false);
        newTransaction.signalizeEnd('$');

        try
        {
            byte[] tst = newTransaction.getBody(0);
            assertEquals("Error there was a problem parsing the data",
                0, newTransaction.getBody(0).length);
        }
        catch (InternalErrorException e)
        {
            fail("Error ocurred while retrieving the body of the transaction");
        }

    }

    @Test
    public void testParseBotchedHeader()
        throws InvalidHeaderException,
        ImplementationException, IllegalUseException, ConnectionParserException
    {
        Transaction tx =
                new Transaction(tID, TransactionType.SEND,
                    dummyTransactionManager, Transaction.IN);
    	tx.parse(emptyInvalidHeaderCompleteSendTransaction.getBytes(TextUtils.utf8), 0,
    			emptyInvalidHeaderCompleteSendTransaction.length(), false);
		tx.signalizeEnd('$');
		assertEquals(2, tx.getFromPath().length);
		assertEquals("yes", tx.getFailureReport());
    }

    @Test
    public void testParsingEmptySendHeaders()
        throws InvalidHeaderException,
        ImplementationException, IllegalUseException, ConnectionParserException
    {
        Transaction tx =
            new Transaction(tID, TransactionType.REPORT,
                dummyTransactionManager, Transaction.IN);
        tx.parse(emptyCompleteSendTransaction.getBytes(TextUtils.utf8), 0,
            emptyCompleteSendTransaction.length(), false);
        tx.signalizeEnd('$');

        URI[] toPathExpected = new URI[1];
        toPathExpected[0] = URI.create("javax.net.msrp://192.168.2.3:1234/asd23asd;tcp");
        assertArrayEquals("Problem encountered parsing the To-Path",
            toPathExpected, tx.getToPath());

        URI[] fromPathExpected = new URI[1];
        fromPathExpected[0] = URI.create("javax.net.msrp://192.168.2.3:1324/123asd;tcp");
        assertArrayEquals("Problem encountered parsing the From-Path",
            fromPathExpected, tx.getFromPath());

        String msgIDExpected = "12345";
        assertEquals("Problem encountered parsing the Message-ID",
            msgIDExpected, tx.getMessageID());

        long[] expectedByteRange = new long[2];
        expectedByteRange[0] = 1;
        expectedByteRange[1] = 0;
        assertArrayEquals(
            "Problem encountered parsing the Byte-Range X-Y values (as in X-Y"
                + "/Z", expectedByteRange, tx.getByteRange());

        int expectedMessageTotalBytes = 0;

        assertEquals(
            "Problem encountered parsing the Byte-Range Z value (as in X-Y"
                + "/Z", expectedMessageTotalBytes, tx
                .getTotalMessageBytes());

        String failureReportExpected = "yes";
        /*
         * always compare case insensitive as the process only stores and
         * validates the string
         */
        assertEquals("Error parsing the failure report", failureReportExpected,
            tx.getFailureReport().toLowerCase());
    }
}
