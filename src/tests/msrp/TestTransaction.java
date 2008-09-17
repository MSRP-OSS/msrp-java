/* Copyright © João Antunes 2008
 This file is part of MSRP Java Stack.

    MSRP Java Stack is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MSRP Java Stack is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with MSRP Java Stack.  If not, see <http://www.gnu.org/licenses/>.

 */
package msrp;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Random;

import org.junit.*;
import org.junit.internal.ArrayComparisonFailure;

import static org.junit.Assert.*;

import msrp.*;
import msrp.Transaction.TransactionType;
import msrp.exceptions.ImplementationException;
import msrp.exceptions.InternalErrorException;
import msrp.exceptions.InvalidHeaderException;

/**
 * @author João André Pereira Antunes 2008
 * 
 */
public class TestTransaction
{

    static Charset usascii = Charset.forName("US-ASCII");

    private static String emptyInvalidHeaderCompleteSendTransaction;

    private static String emptyCompleteSendTransaction;

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
            ("To-Path: msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                + "From-Path: msrp://192.168.2.3:1324/123asd;tcp\r\n"
                + "Message-ID: 12345\r\n" + "Byte-Range: 1-0/0\r\n"
                + "Failure-report: yEs\r\n" + "Success-report: no\r\n");

        emptyInvalidHeaderCompleteSendTransaction =
            ("To-Path: msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                + "From-Path: msrp://192.168.2.3:1324/123asd;tcp\r\n"
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
        MiscTests.generateRandom(tid);
        tID = new String(tid, usascii);

        // TODO add support for the content-type in MSRP (?) [at least the
        // parsing should be done to be present on the message type]
        randomData = new byte[30];
        MiscTests.generateRandom(randomData);
        completeSendTransaction =
            ("To-Path: msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                + "From-Path: msrp://192.168.2.3:1324/123asd;tcp\r\n"
                + "Message-ID: 12346\r\n" + "Byte-Range: 1-30/30\r\n"
                + "Content-Type: somethingTODO/notimportantATM\r\n" + "\r\n" + new String(
                randomData, usascii));
        dummyTransactionManager = new TransactionManager();

    }

    @Test
    public void testParsingCompleteBody() throws InvalidHeaderException, ImplementationException
    {

        Transaction newTransaction = new Transaction(tID, TransactionType.REPORT, dummyTransactionManager);
        newTransaction.parse(completeSendTransaction);
        newTransaction.sinalizeEnd('$');

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
    public void testParsingEmptySendHeaders() throws InvalidHeaderException, ImplementationException
    {

        Transaction newTransaction = new Transaction(tID, TransactionType.REPORT, dummyTransactionManager);
        newTransaction.parse(emptyCompleteSendTransaction);
        newTransaction.sinalizeEnd('$');

        URI[] toPathExpected = new URI[1];
        toPathExpected[0] = URI.create("msrp://192.168.2.3:1234/asd23asd;tcp");
        assertArrayEquals("Problem encountered parsing the To-Path",
            toPathExpected, newTransaction.getToPath());

        URI[] fromPathExpected = new URI[1];
        fromPathExpected[0] = URI.create("msrp://192.168.2.3:1324/123asd;tcp");
        assertArrayEquals("Problem encountered parsing the From-Path",
            fromPathExpected, newTransaction.getFromPath());

        String msgIDExpected = "12345";
        assertEquals("Problem encountered parsing the Message-ID",
            msgIDExpected, newTransaction.getMessageID());

        long[] expectedByteRange = new long[2];
        expectedByteRange[0] = 1;
        expectedByteRange[1] = 0;
        System.out.println("Byterange[0]:" + newTransaction.getByteRange()[0]);
        System.out.println("Byterange[1]:" + newTransaction.getByteRange()[1]);
        assertArrayEquals(
            "Problem encountered parsing the Byte-Range X-Y values (as in X-Y"
                + "/Z", expectedByteRange, newTransaction.getByteRange());

        int expectedMessageTotalBytes = 0;

        assertEquals(
            "Problem encountered parsing the Byte-Range Z value (as in X-Y"
                + "/Z", expectedMessageTotalBytes, newTransaction
                .getTotalMessageBytes());

        String failureReportExpected = "yes";
        /*
         * always compare case insensitive as the process only stores and
         * validates the string
         */
        assertEquals("Error parsing the failure report", failureReportExpected,
            newTransaction.getFailureReport().toLowerCase());
        

    }
    /*@Test(expected=msrp.exceptions.InvalidHeaderException.class)
    public void testInvalidHeaderFailureReport() throws InvalidHeaderException 
    {
        
        /*
         * Should throw an exception here:
         
        Transaction newTransaction = new Transaction(tID, TransactionType.SEND);
        try {
        newTransaction.parse(emptyInvalidHeaderCompleteSendTransaction);
        newTransaction.sinalizeEnd('$');
        fail("Exception not thrown!");
        }
        catch (InvalidHeaderException e)
        {
        
        }
    }*/

    @Test
    public void testParsingReportFields()
    {

        String emptyCompleteSendTransaction =
            ("To-Path: msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                + "From-Path: msrp://192.168.2.3:1324/123asd;tcp\r\n"
                + "Message-ID: 12345\r\n" + "Byte-Range: 1-0/0\r\n");
        String completeSendTransactionFailureReport =
            ("To-Path: msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                + "From-Path: msrp://192.168.2.3:1324/123asd;tcp\r\n"
                + "Message-ID: 12346\r\n" + "Byte-Range: 1-30/30\r\n"
                + "Content-Type: somethingTODO/notimportantATM\r\n" + "\r\n" + new String(
                randomData, usascii));
    }

}
