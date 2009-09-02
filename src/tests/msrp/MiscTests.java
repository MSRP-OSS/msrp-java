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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.spi.CharsetProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import sun.misc.HexDumpEncoder;

import msrp.*;
import msrp.Transaction.*;
import msrp.events.MessageAbortedEvent;
import msrp.messages.IncomingMessage;
import msrp.messages.Message;
import msrp.messages.OutgoingMessage;

public class MiscTests
    implements MSRPSessionListener, Runnable
{

    static Charset usascii = Charset.forName("US-ASCII");

    static Random test = new Random();

    static InetAddress address = null;

    static SocketAddress socketAddrIn = null;

    private static MiscTests instance = new MiscTests();

    private static void testURLandSockets()
    {

        // Typical URI:
        String sURI =
            new String("msrps://bob.example.com:8493/si438dsaodes;tcp");
        String lURI = new String("msrp://192.168.2.3:8493/si438dsaodes;tcp");
        try
        {
            // URLStreamHandlerFactory = new URLStreamHandlerFactory();
            URI newUri = new URI(sURI);
            URL newUrl =
                new URL(newUri.getScheme(), newUri.getHost(), newUri.getPort(),
                    newUri.getPath());

            System.out.println("Confirming the uri fields, original uri:"
                + sURI + "\n" + "Scheme:" + newUri.getScheme() + "\n"
                + "SchemeSpecificPart:" + newUri.getSchemeSpecificPart() + "\n"
                + "Host:" + newUri.getHost() + "\n" + "Fragment:"
                + newUri.getFragment() + "\n" + "Path:" + newUri.getPath()
                + "\n" + "Port:" + newUri.getPort() + "\nAuthority:"
                + newUri.getAuthority());

            System.out.println("Got the URL:" + newUrl.toString() + "\nHost:"
                + newUrl.getHost() + "\nRef:" + newUrl.getRef() + "\nUserInfo:"
                + newUrl.getUserInfo() + "\nQuery:" + newUrl.getQuery()
                + "\nPath:" + newUrl.getPath() + "\nPort:" + newUrl.getPort()
                + "\nProtocol:" + newUrl.getProtocol());

            URL lUrl = new URL(lURI);
            System.out.println("Local address:"
                + InetAddress.getLocalHost().toString());
            InetAddress local[] =
                InetAddress.getAllByName(InetAddress.getLocalHost()
                    .getHostName());

            int i;
            System.out.println("Start of list of local names:");
            for (i = 0; i < local.length; i++)
                System.out.println(local[i].toString());
            System.out.println("End of list of local names size:"
                + local.length);

            InetAddress remoteAddress = InetAddress.getByName(lUrl.getHost());
            // Detect if it's a local URL:
            for (i = 0; i < local.length; i++)
            {

                if (local[i].equals(remoteAddress))
                    System.out.println("Found out that: "
                        + local[i].getHostAddress() + " is equal to:"
                        + lUrl.getHost());

            }

        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private static void testSocketChannelsAndTransactionID()
    {

        test = new Random();
        // Creating the new SocketChannel and checking if it has a port and an
        // address associated
        try
        {
            SocketChannel outgoing = SocketChannel.open();
            Socket testOutgoing = outgoing.socket();
            System.out.println("Port of the outgoing socket:"
                + testOutgoing.getLocalPort() + " is it bound?:"
                + testOutgoing.isBound() + " address associated:"
                + testOutgoing.getLocalAddress());

            InetSocketAddress socketAddrOut;
            for (socketAddrOut =
                new InetSocketAddress(address,
                    test.nextInt(65535 - 1024) + 1024); !testOutgoing.isBound(); socketAddrOut =
                new InetSocketAddress(address,
                    test.nextInt(65535 - 1024) + 1024))
            {
                try
                {
                    testOutgoing.bind(socketAddrOut);
                }
                catch (IOException e)
                {

                    // do nothing
                }
            }
            testOutgoing = outgoing.socket();
            System.out.println("Port of the outgoing socket:"
                + testOutgoing.getLocalPort() + " is it bound?:"
                + testOutgoing.isBound() + " address associated:"
                + testOutgoing.getLocalAddress());

            MiscTests alltests = new MiscTests();
            Thread test = new Thread(alltests);
            test.start();
            // Try to connect the outgoing to the incoming;
            while (socketAddrIn == null)
                Thread.sleep(1000);

            outgoing.connect(socketAddrIn);

            // is it connected?
            System.out.println("outgoing connected?:" + outgoing.isConnected());

            // generating the transaction

            // it should be able to: "receive chunks of any size from zero
            // octets
            // to the maximum number of octets they can receive for a complete
            // message"
            byte[] outGoingTransaction = new byte[11240];
            byte[] tid = new byte[8];
            generateRandom(tid);

            /*
             * String emptySend = ("MSRP " + new String(tid, usascii) +
             * " SEND\r\n" + "To-Path: msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
             * + "From-Path: msrp://192.168.2.3:1324/123asd;tcp\r\n" +
             * "Message-ID: 12345\r\n" + "Byte-Range: 1-0/0\r\n" + "-------" +
             * new String(tid, usascii) + "$");
             * 
             * 
             * // sending data through the outgoing and receiving it in the
             * incoming byte [] teste = emptySend.getBytes(usascii);
             */

            String responseWithComment =
                ("MSRP " + new String(tid, usascii) + " 200 OK\r\n"
                    + "To-Path: msrp://192.168.2.3:1234/asd23asd;tcp\r\n"
                    + "From-Path: msrp://192.168.2.3:1324/123asd;tcp\r\n"
                    + "-------" + new String(tid, usascii) + "$\r\n");
            byte[] teste = responseWithComment.getBytes(usascii);
            ByteBuffer outByteBuffer = ByteBuffer.wrap(teste);
            outgoing.write(outByteBuffer);

        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private static void testRandomStringGenerator()
    {

        byte[] randomBytes = new byte[8];
        System.out.println("random bytes uninitialized:"
            + (new String(randomBytes, Charset.forName("ascii"))));

        // System.out.println("random bytes:" + (new String(randomBytes,
        // Charset.forName("us-ascii"))) + ":END of bytes ");

        // System.out.println("random bytes:" + (new String(randomBytes,
        // Charset.forName("us-ascii"))) + ":END of bytes ");

        Long startTime = System.currentTimeMillis();
        Long finishTime = System.currentTimeMillis();
        HashSet<URI> uRIs = new HashSet<URI>();
        try
        {
            generateRandom(randomBytes);
            System.out.println("random bytes:"
                + (new String(randomBytes, Charset.forName("us-ascii")))
                + ":END of bytes ");
            URI testURI =
                new URI("msrp", null, "localhost", 1234, "/"
                    + (new String(randomBytes, Charset.forName("us-ascii")))
                    + ";tcp", null, null);
            int i = 0;
            while (!uRIs.contains(testURI))
            {
                i++;
                uRIs.add(testURI);
                System.out.println(testURI);
                generateRandom(randomBytes);
                testURI =
                    new URI("msrp", null, "localhost", 1234,
                        "/"
                            + (new String(randomBytes, Charset
                                .forName("us-ascii"))) + ";tcp", null, null);
            }

            System.out.println("got to the end, value of i:" + i);

        }
        catch (URISyntaxException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    // IMPROVE it could be improved by adding the rest of the unreserved
    // characters according to rfc3986 (-._~)
    // IMPROVE can be improved the speed by not doing so much calls to the
    // Random class
    /**
     * Generates a number of random alpha-numeric characters in US-ASCII
     * 
     * @param byteArray the byte array that will contain the newly generated
     *            bytes. the number of generated bytes is given by the length of
     *            the byteArray
     * 
     **/
    public static void generateRandom(byte[] byteArray)
    {
        int i;
        test.nextBytes(byteArray);
        for (i = 0; i < byteArray.length; i++)
        {
            if (byteArray[i] < 0)
                byteArray[i] *= -1;

            while (!((byteArray[i] >= 65 && byteArray[i] <= 90)
                || (byteArray[i] >= 97 && byteArray[i] <= 122) || (byteArray[i] <= 57 && byteArray[i] >= 48)))
            {

                if (byteArray[i] > 122)
                    byteArray[i] -= test.nextInt(byteArray[i]);
                if (byteArray[i] < 48)
                    byteArray[i] += test.nextInt(5);
                else
                    byteArray[i] += test.nextInt(10);
            }
        }
    }

    private static void testSocketAndURI()
        throws UnknownHostException,
        URISyntaxException
    {
        Socket socket;
        Random random = new Random();
        URI localURI;
        boolean localAddress = false;

        // DEBUG
        System.out.println(address);

        // sanity check, check that the given address is a local one where a
        // socket
        // could be bound
        InetAddress local[] =
            InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());

        // DEBUG
        for (int i = 0; i < local.length; i++)
        {
            // DEBUG
            System.out.println("Local Address:" + local[i]);
        }

        for (InetAddress inetAddress : local)
        {
            if (inetAddress.equals(address))
                localAddress = true;
        }
        if (!localAddress)
            throw new UnknownHostException(
                "the given adress is not a local one");

        // bind a socket to a local random port, if it's in use try again
        // FIXME could prove to be a bug if the bind procedure doesn't work
        socket = new Socket();
        InetSocketAddress socketAddr;
        for (socketAddr =
            new InetSocketAddress(address, random.nextInt(65535 - 1024) + 1024); !socket
            .isBound(); socketAddr =
            new InetSocketAddress(address, random.nextInt(65535 - 1024) + 1024))
        {
            try
            {
                socket.bind(socketAddr);
            }
            catch (IOException e)
            {

                // do nothing
            }
        }

        URI newLocalURI =
            new URI("msrp", null, address.getHostAddress(), socket
                .getLocalPort(), null, null, null);
        localURI = newLocalURI;

        System.out.println("Got the following URI:" + localURI);
    }

    public static void testMessageExchange() throws Exception
    {

        Session sessionSend = new Session(false, false, address);
        URI uri = sessionSend.getURI();
        if (uri == null)
        {
            System.out.println("Error, received a null uri for the session");
            return;
        }
        Session sessionReceive = new Session(false, false, uri, address);
        uri = sessionReceive.getURI();
        System.out.println("Received the following URI:" + uri);
        sessionSend.addMSRPSessionListener(instance);
        sessionReceive.addMSRPSessionListener(instance);

        // This does what the SIP negotiation should do, get the uris for the
        // MSRP Transaction
        try
        {
            // Create a new message and place it on the toSendQueue of the
            // session
            Message newMessage =
                new OutgoingMessage(sessionSend, "plain/text",
                    ("this is just a simple"
                        + "test to see if this message can get through")
                        .getBytes(usascii));
            ArrayList<URI> uris = new ArrayList<URI>();
            uris.add(sessionReceive.getURI());
            // Enable the sessionSend on sessionReceive
            sessionSend.addToPath(uris);

            // TODO sessionSend.addToPath(null);
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw e;
        }

    }

    private static void testUsingLocalAddress()
    {

        try
        {
            InetAddress local = InetAddress.getLocalHost();
            System.out.println(local);
        }
        catch (UnknownHostException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private static void testRegexEndChars()
    {

        byte[] tid = new byte[8];
        generateRandom(tid);
        String tID = new String(tid, usascii);

        String emptySend = ("garbage" + "\r\n-------" + tID);

        int i;
        int j;
        int trim = 12;
        // Generate the pattern for the trim:
        char[] toSave;
        for (j = 0, i = emptySend.lastIndexOf('\r'), toSave =
            new char[emptySend.length() - i - trim]; i < emptySend.length()
            - trim; i++, j++)
        {
            if (i == -1 || i == emptySend.length())
            {
                break;
            }

            toSave[j] = emptySend.charAt(i);
        }
        String patternStringEndT =
            new String(
                "((\r\n)|(\r\n-)|(\r\n--)|(\r\n---)|(\r\n----)|(\r\n-----)|(\r\n------)|"
                    + "(\r\n-------)");
        CharBuffer tidBuffer = CharBuffer.wrap(tID);
        for (i = 0; i < tID.length(); i++)
        {
            patternStringEndT =
                patternStringEndT.concat("|(\r\n-------"
                    + tidBuffer.subSequence(0, i) + ")");
        }
        patternStringEndT = patternStringEndT.concat(")?$");

        Pattern endTransactionTrim =
            Pattern.compile(patternStringEndT, Pattern.DOTALL);
        String toSaveString = new String(toSave);

        // toSaveString = "\n--r";

        Matcher matchEndTransaction = endTransactionTrim.matcher(toSaveString);
        System.out.println("looking at endTransaction:"
            + matchEndTransaction.matches());
        System.out.println("group 1:" + matchEndTransaction.group(1)
            + ":end group1");
        System.out.println("group 2:" + matchEndTransaction.group(2).toString()
            + ":end group2");
        System.out.println("group 3:" + matchEndTransaction.group(3).toString()
            + ":end group3");
    }

    public static void main(String[] args)
    {
        testURLandSockets();
        testUsingLocalAddress();

        try
        {
            address = InetAddress.getByName("192.168.20.20");
            testMessageExchange();
            testSocketChannelsAndTransactionID();
            testSocketAndURI();
        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        testRandomStringGenerator();
    }

    public void receiveMessage(Session session, Message message)
    {
        // TODO print the content of the message
        System.out.println("******Debug: on: " + session.toString()
            + " Received a message! the message is:");

        DataContainer dc = message.getDataContainer();
        if (dc instanceof MemoryDataContainer)
        {
            try
            {
                ByteBuffer byteBuf = dc.get(0, 0);
                byte[] byteArray = new byte[byteBuf.capacity()];
                byteBuf.get(byteArray);
                System.out.println(new String(byteArray, usascii));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else if (dc instanceof FileDataContainer)
            System.out.println("Received the message in the file: "
                + ((FileDataContainer) dc).getFile());

    }

    public void receivedReport(Session session, Transaction report)
    {
        System.out.println("Debug: on: " + session.toString()
            + " Received a response from a message!");

    }

    public void updateSendStatus(Session session, Message message,
        long totalNumberBytesSent)
    {
        System.out.println("Debug: on: " + session.toString()
            + " Received a status update for the message:"
            + message.getMessageID() + " with total nr bytes:"
            + totalNumberBytesSent);

    }

    /**
     * tests the socketChannel infrastructure and more recently the transaction
     * parsing and therefore data receiving
     */

    @Override
    public void run()
    {
        // Generate a new socketChannel and just bind it

        ServerSocketChannel incoming;
        try
        {
            incoming = ServerSocketChannel.open();

            ServerSocket testIncoming = incoming.socket();

            System.out.println("Port of the incoming socket:"
                + testIncoming.getLocalPort() + " is it bound?:"
                + testIncoming.isBound() + " address associated:"
                + testIncoming.getLocalSocketAddress());

            for (socketAddrIn =
                new InetSocketAddress(address,
                    test.nextInt(65535 - 1024) + 1024); !testIncoming.isBound(); socketAddrIn =
                new InetSocketAddress(address,
                    test.nextInt(65535 - 1024) + 1024))
            {
                try
                {
                    testIncoming.bind(socketAddrIn);
                }
                catch (IOException e)
                {

                    // do nothing
                }
            }

            System.out.println("Port of the incoming socket:"
                + testIncoming.getLocalPort() + " is it bound?:"
                + testIncoming.isBound() + " address associated:"
                + testIncoming.getLocalSocketAddress());
            socketAddrIn = (SocketAddress) testIncoming.getLocalSocketAddress();
            SocketChannel incomingInstance = incoming.accept();

            Socket testNewInstance = incomingInstance.socket();

            // check if it's connected and the incoming port and all:
            System.out.println("Port of the new incoming socket:"
                + testNewInstance.getLocalPort() + " is it bound?:"
                + testNewInstance.isBound() + " address associated:"
                + testNewInstance.getLocalSocketAddress()
                + " address to which someone connected:"
                + testNewInstance.getInetAddress());

            /**
             * Start of reading the incoming string this will be an data
             * agnostic function
             */
            byte[] testeIn = new byte[2048];
            ByteBuffer inByteBuffer = ByteBuffer.wrap(testeIn);

            // not sure if correct to use due to the Charset, although i believe
            // that US-ascii is always considered as a base
            // CharBuffer inCharBuffer = inByteBuffer.asCharBuffer();
            try
            {
                while (true)
                {

                    inByteBuffer.clear();
                    int readNrBytes;
                    readNrBytes = incomingInstance.read(inByteBuffer);
                    byte[] readBytes = new byte[readNrBytes];
                    if (readNrBytes != -1 && readNrBytes != 0)
                    {
                        inByteBuffer.flip();
                        int i = 0;
                        while (inByteBuffer.hasRemaining())
                            readBytes[i++] = inByteBuffer.get();

                        String incomingString = new String(readBytes, usascii);

                        // parsing received data if received anything
                        if (incomingString.length() > 0)
                            parser(incomingString);
                    }

                }
            }
            catch (Exception e)
            {
                // TODO Catch everything that .read throws and capture it for
                // processing
                e.printStackTrace();
            }
        }
        catch (IOException e1)
        {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

    }

    static boolean receivingTransaction = false;

    static Transaction incomingTransaction = null;

    static String remainderReceive = new String();

    /**
     * receives the incoming data and identifies a transaction's start and end
     * and creates a new transaction the needed things according to the MSRP
     * norms
     * 
     * @param incomingString raw text data to be handled
     * @throws Exception Generic error exception TODO specialize in the future
     */
    private void parser(String incomingString) throws Exception
    {
        String toParse;
        String tID;
        boolean complete = false;
        if (remainderReceive.length() > 0)
        {
            toParse = remainderReceive.concat(incomingString);
            remainderReceive = new String();
        }
        else
        {
            toParse = incomingString;
        }

        while (!complete && toParse.length() >= 48)
        {
            if (!receivingTransaction)
            {
                // Find the MSRP start of the transaction stamp

                // the TID has to be at least 64bits long = 8chars
                // given as a reasonable limit of 20 for the transaction id
                // although non normative
                // also the method name will have the same 20 limit and has to
                // be a Upper
                // case word like SEND
                Pattern startTransactionRequest =
                    Pattern
                        .compile(
                            "(^MSRP) ([\\p{Alnum}]{8,20}) ([\\p{Upper}]{1,20})\r\n(.*)",
                            Pattern.DOTALL);
                Pattern startTransactionResponse =
                    Pattern
                        .compile(
                            "(^MSRP) ([\\p{Alnum}]{8,20}) ((\\d{3}) ?\\w{1,20}\r\n)(.*)",
                            Pattern.DOTALL);
                Matcher matcherTransactionResponse =
                    startTransactionResponse.matcher(toParse);

                // zero tolerance - If such pattern is not found,
                // make actions to drop the connection as this is an invalid
                // session
                Matcher matcherTransactionRequest =
                    startTransactionRequest.matcher(toParse);
                if (matcherTransactionRequest.matches())
                {
                    receivingTransaction = true;
                    // Retrieve the TID and create a new transaction
                    // Transaction newTransaction = new
                    // Transaction(matcherTransaction
                    // .group(2),matcherTransaction.group(2));
                    tID = matcherTransactionRequest.group(2);
                    TransactionType newTransactionType;
                    try
                    {
                        newTransactionType =
                            TransactionType.valueOf(matcherTransactionRequest
                                .group(3).toUpperCase());
                    }
                    catch (IllegalArgumentException argExcptn)
                    {
                        // Then we have ourselves an unsupported method
                        // create an unsupported transaction and sinalize it
                        newTransactionType =
                            TransactionType
                                .valueOf("Unsupported".toUpperCase());
                    }
                    TransactionManager dummyManager = new TransactionManager();
                    incomingTransaction =
                        new Transaction(tID, newTransactionType, dummyManager,
                            Transaction.IN);
                    if (newTransactionType.equals(TransactionType.UNSUPPORTED))
                        incomingTransaction.signalizeEnd('$');

                    // DEBUG REMOVE
                    System.out.println("identified the following TID:" + tID);

                    // extract the start of transaction line
                    toParse = matcherTransactionRequest.group(4);
                    complete = false;

                }// if (matcherTransactionRequest.matches())
                else if (matcherTransactionResponse.matches())
                {
                    // Encountered a response
                    System.out.println("MSRP:"
                        + matcherTransactionResponse.group(1) + ":MSRP");
                    System.out.println("tID:"
                        + matcherTransactionResponse.group(2) + ":tID");
                    System.out.println("group3:"
                        + matcherTransactionResponse.group(3) + ":group3");
                    System.out.println("group4:"
                        + matcherTransactionResponse.group(4) + ":group4");
                    System.out.println("group5:"
                        + matcherTransactionResponse.group(5) + ":group5");

                    // Has to encounter the end of the transaction as well
                    tID = matcherTransactionResponse.group(2);
                    // tID = foundTransaction.getTID();
                    if (matcherTransactionResponse.group(5) != null)
                    {
                        toParse = matcherTransactionResponse.group(5);
                    }
                    Pattern endTransaction =
                        Pattern.compile("(.*)(\r\n-------" + tID
                            + ")([$+#])(.*)", Pattern.DOTALL);
                    Matcher matchEndTransaction =
                        endTransaction.matcher(toParse);
                    if (matchEndTransaction.matches())
                    {
                        System.out
                            .println("Found the end of the transaction!!");
                        System.out.println("Rest of the body:"
                            + matchEndTransaction.group(1));
                        System.out.println("end of transaction:"
                            + matchEndTransaction.group(2));
                        System.out.println("Continuation flag:"
                            + matchEndTransaction.group(3));
                        System.out.println("Rest of the message:"
                            + matchEndTransaction.group(4));
                        if (matchEndTransaction.group(4) != null)
                            toParse = matchEndTransaction.group(4);
                    }
                }
                else
                {
                    // TODO alter the class of the Exception, get a more
                    // complete exceptions infrastructure?!
                    // TODO receive the exception by the connection and treat it
                    // accordingly
                    throw new Exception("Error, start of the TID not found");
                }

            }// if (!receivingTransaction)
            if (receivingTransaction)
            {
                tID = incomingTransaction.getTID();
                // Identify the end of the transaction (however it might not
                // exist yet or it may not be complete):
                Pattern endTransaction =
                    Pattern.compile("(.*)(\r\n-------" + tID + ")([$+#])(.*)",
                        Pattern.DOTALL);
                Matcher matchEndTransaction = endTransaction.matcher(toParse);
                // DEBUG -start here- REMOVE
                if (matchEndTransaction.matches())
                {
                    System.out.println("Found the end of the transaction!!");
                    System.out.println("Rest of the body:"
                        + matchEndTransaction.group(1));
                    System.out.println("end of transaction:"
                        + matchEndTransaction.group(2));
                    System.out.println("Continuation flag:"
                        + matchEndTransaction.group(3));
                    System.out.println("Rest of the message:"
                        + matchEndTransaction.group(4));
                }
                // DEBUG -end here- REMOVE

                int i = 0;
                // if we have a complete end of transaction:
                if (matchEndTransaction.matches())
                {
                    incomingTransaction.parse(matchEndTransaction.group(1)
                        .getBytes(usascii), 0, matchEndTransaction.group(1)
                        .length(), false);
                    incomingTransaction.signalizeEnd(matchEndTransaction.group(
                        3).charAt(0));
                    receivingTransaction = false;
                    // parse the rest of the received data extracting the
                    // already parsed parts
                    if (matchEndTransaction.group(4) != null)
                    {
                        toParse = matchEndTransaction.group(4);
                        complete = false;
                    }
                    else
                    {
                        // then we have nothing more to analyze
                        complete = true;
                    }
                }
                else
                {
                    // we trim the toParse so that we don't abruptly cut an end
                    // of transaction
                    // we save the characters that we trimmed to be analyzed
                    // next
                    int j;
                    char[] toSave;
                    // we get the possible beginning of the trim characters
                    for (j = 0, i = toParse.lastIndexOf('\r'), toSave =
                        new char[toParse.length() - i]; i < toParse.length(); i++, j++)
                    {
                        if (i == -1 || i == toParse.length())
                        {
                            break;
                        }

                        toSave[j] = toParse.charAt(i);
                    }

                    // buildup of the regex pattern of the possible end of
                    // transaction characters
                    String patternStringEndT =
                        new String(
                            "((\r\n)|(\r\n-)|(\r\n--)|(\r\n---)|(\r\n----)|(\r\n-----)|(\r\n------)|"
                                + "(\r\n-------)");
                    CharBuffer tidBuffer = CharBuffer.wrap(tID);
                    for (i = 0; i < tID.length(); i++)
                    {
                        patternStringEndT =
                            patternStringEndT.concat("|(\r\n-------"
                                + tidBuffer.subSequence(0, i) + ")");
                    }
                    patternStringEndT = patternStringEndT.concat(")?$");

                    Pattern endTransactionTrim =
                        Pattern.compile(patternStringEndT, Pattern.DOTALL);
                    String toSaveString = new String(toSave);

                    // toSaveString = "\n--r";

                    matchEndTransaction =
                        endTransactionTrim.matcher(toSaveString);

                    if (matchEndTransaction.matches())
                    {
                        // if we indeed have end of transaction characters in
                        // the end of the data
                        // we add them to the string that will be analyzed next
                        remainderReceive =
                            remainderReceive.concat(toSaveString);

                        // trimming of the data to parse
                        toParse =
                            toParse.substring(0, toParse.lastIndexOf('\r'));
                    }
                    incomingTransaction.parse(toParse.getBytes(usascii), 0,
                        toParse.length(), false);
                    complete = true;
                }
            }

        }// if (receivingTransaction)

    }

    /**
     * ALWAYS NEEDS TO INITIALIZE AND ASSIGN A DATA CONTAINER TO THE MESSAGE
     */
    @Override
    public boolean acceptHook(Session session, IncomingMessage message)
    {
        /* By default accept all messages */
        MemoryDataContainer mDC =
            new MemoryDataContainer((int) message.getSize());
        message.setDataContainer(mDC);
        return true;
    }

    @Override
    public void abortedMessage(Session session, IncomingMessage message)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void abortedMessageEvent(MessageAbortedEvent abortEvent)
    {
        // TODO Auto-generated method stub

    }

}// end of class

