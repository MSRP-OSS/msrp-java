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
import java.nio.*;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Observable;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import msrp.Connections;
import msrp.Session;
import msrp.Transaction.TransactionType;
import msrp.exceptions.ConnectionParserException;
import msrp.exceptions.ConnectionReadException;
import msrp.exceptions.ConnectionWriteException;
import msrp.exceptions.IllegalUseException;
import msrp.exceptions.ImplementationException;
import msrp.exceptions.InvalidHeaderException;
import msrp.messages.Message;
import msrp.utils.NetworkUtils;

/**
 * This class represents an MSRP connection.
 * 
 * It has one pair of threads associated for writing and reading.
 * 
 * It is also responsible for some parsing, including: 
 * Identifying MSRP transaction requests and responses; 
 * Pre-parsing - identifying what is the content of the transaction from what 
 * isn't;
 * Whenever a transactions is found, parse its data using the Transaction's 
 * parse method;
 * 
 * @author João André Pereira Antunes
 */
 class Connection
    extends Observable
    implements Runnable
{

    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(Connection.class);

    public static final int OUTPUTBUFFERLENGTH = 2048;

    public Connection(SocketChannel newSocketChannel)
        throws URISyntaxException
    {

        socketChannel = newSocketChannel;
        random = new Random();
        Socket socket = socketChannel.socket();
        URI newLocalURI =
            new URI("msrp", null, socket.getInetAddress().getHostAddress(),
                socket.getLocalPort(), null, null, null);
        localURI = newLocalURI;
        transactionManager = new TransactionManager(this);
        // this.addObserver(transactionManager);

    }

    /**
     * Connection constructor for simple MSRP sessions
     * 
     * @param address hostname/IP used to bound the new MSRP socket
     * @throws URISyntaxException there was a problem generating the connection
     *             dependent part of the URI
     * @throws IOException if there was a problem with the creation of the
     *             socket
     * 
     */
    public Connection(InetAddress address)
        throws URISyntaxException,
        IOException
    {

        transactionManager = new TransactionManager(this);
        random = new Random();
        // activate the connection:

        boolean localAddress = false;

        // sanity check, check that the given address is a local one where a
        // socket
        // could be bound
        InetAddress local[] =
            InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());

        /*
         * for (InetAddress inetAddress : local) { if
         * (inetAddress.equals(address)) localAddress = true; } if
         * (!localAddress) throw new UnknownHostException(
         * "the given address is a loopback device not a local/inet one");
         */

        if (NetworkUtils.isLinkLocalIPv4Address(address))
        {
            logger.info("Connection: given address is a local one: " + address);
        }

        // bind a socket to a local random port, if it's in use try again
        // FIXME could prove to be a bug if the bind procedure doesn't work

        socketChannel = SelectorProvider.provider().openSocketChannel();
        Socket socket = socketChannel.socket();
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

        // fill the localURI variable that contains the uri parts that are
        // associated with this connection (scheme[protocol], host and port)
        URI newLocalURI =
            new URI("msrp", null, address.getHostAddress(), socket
                .getLocalPort(), null, null, null);
        localURI = newLocalURI;
        // this.addObserver(transactionManager);
    }

    private TransactionManager transactionManager;

    /**
     * private field used mainly to generate new session uris this method should
     * contain all the uris of the sessions associated with this connection TODO
     * 
     * FIXME ?! check to see if this sessions is needed even though the
     * associatedSessions on transactionManager exists and should contain the
     * same information
     */
    private HashSet<URI> sessions = new HashSet<URI>();

    private SocketChannel socketChannel = null;

    private Random random;

    private URL _remoteURL = null;

    private URI localURI = null;

    /**
     * @uml.property name="_connections"
     * @uml.associationEnd inverse="connection1:msrp.Connections"
     */
    /* private Connections connectionsInstance = Connections.getInstance(); */

    protected TransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    /**
     * @param localURI sets the local URI associated with the connection
     */
    protected void setLocalURI(URI localURI)
    {
        localURI = localURI;
    }

    protected boolean isEstablished()
    {
        if (socketChannel == null)
            return false;
        return socketChannel.isConnected();
    }

    /**
     * 
     * @return returns the associated local uri (relevant parts of the uri for
     *         the connection)
     */
    protected URI getLocalURI()
    {
        return localURI;
    }

    protected URL getRemoteURL()
    {
        return _remoteURL;
    }

    protected HashSet<URI> getSessionURIs()
    {
        return sessions;
    }

    private void setRemoteURL(URL url)
    {
        _remoteURL = url;
    }

    /**
     * Generates the new URI and validates it against the existing URIs of the
     * sessions that this connection handles
     * 
     * @throws URISyntaxException If there is a problem with the generation of
     *             the new URI
     */
    protected synchronized URI generateNewURI() throws URISyntaxException
    {

        byte[] randomBytes = new byte[8]; // Variable used for generating the
        // random US-ASCII alpha numeric and
        // digit bytes
        // DEBUG

        generateRandom(randomBytes);
        // DEBUG
        logger.trace("random bytes generated:"
            + (new String(randomBytes, Charset.forName("us-ascii")))
            + ":END of bytes ");

        // Get the current local URI.
        URI newURI =
            new URI(localURI.getScheme(), localURI.getUserInfo(), localURI
                .getHost(), localURI.getPort(), "/"
                + (new String(randomBytes, Charset.forName("us-ascii")))
                + ";tcp", localURI.getQuery(), localURI.getFragment());

        int i = 0;

        while (sessions.contains(newURI))
        {
            i++;
            generateRandom(randomBytes);
            newURI =
                new URI(localURI.getScheme(), localURI.getUserInfo(), localURI
                    .getHost(), localURI.getPort(), "/"
                    + (new String(randomBytes, Charset.forName("us-ascii")))
                    + ";tcp", localURI.getQuery(), localURI.getFragment());
        }

        sessions.add(newURI);

        logger.trace("generated the new URI, value of i:" + i);
        return newURI;

    }

    // IMPROVE it could be improved by adding the rest of the unreserved
    // characters according to rfc3986 (-._~)
    // IMPROVE can be improved the speed by not doing so much calls to the
    // Random class
    /**
     * Generates a number of random alpha-numeric and digit codes in US-ASCII
     * 
     * @param byteArray the byte array that will contain the newly generated
     *            bytes. the number of generated bytes is given by the length of
     *            the byteArray
     * 
     */

    private void generateRandom(byte[] byteArray)
    {
        int i;
        random.nextBytes(byteArray);
        for (i = 0; i < byteArray.length; i++)
        {
            if (byteArray[i] < 0)
                byteArray[i] *= -1;

            while (!((byteArray[i] >= 65 && byteArray[i] <= 90)
                || (byteArray[i] >= 97 && byteArray[i] <= 122) || (byteArray[i] <= 57 && byteArray[i] >= 48)))
            {

                if (byteArray[i] > 122)
                    byteArray[i] -= random.nextInt(byteArray[i]);
                if (byteArray[i] < 48)
                    byteArray[i] += random.nextInt(5);
                else
                    byteArray[i] += random.nextInt(10);
            }
        }
    }

    /**
     * Returns if the socket associated with the connection is bound
     * 
     * @return
     */
    protected boolean isBound()
    {
        if (socketChannel == null)
            return false;
        return socketChannel.socket().isBound();
    }

    /**
     * @uml.property name="_session"
     * @uml.associationEnd inverse="_connectoin:msrp.Session"
     */
    private msrp.Session _session = null;

    /**
     * Getter of the property <tt>_session</tt>
     * 
     * @return Returns the _session.
     * @uml.property name="_session"
     */
    public msrp.Session get_session()
    {
        return _session;
    }

    /**
     * Getter of the property <tt>_transactions</tt>
     * 
     * @return Returns the _transactions.
     * @uml.property name="_transactions"
     */

    /**
     */
    public void close()
    {
    }

    /**
     */
    public void messageInterrupt(Message message)
    {
    }

    /**
     */
    public void newTransaction(Session session, Message message,
        TransactionManager transactionManager, String transactionCode)
    {
    }

    /**
     * @desc - Read (reads from the stream of the socket)
     * @desc - Validation of what is being read
     * @desc - Misc. Interrupts due to read errors (mem, buffers etc)
     * 
     */
    public void read()
    {
    }

    /**
     */
    public void sessionClose(Session session)
    {
    }

    /**
     * Setter of the property <tt>_session</tt>
     * 
     * @param _session The _session to set.
     * @uml.property name="_session"
     */
    public void set_session(msrp.Session _session)
    {
        this._session = _session;
    }

    /**
     * Defines which block of data should be sent over the connection, according
     * to the priority Currently it's implemented an FIFO by the transaction,
     * however it could be later used
     * 
     * @return
     * @throws Exception
     */
    /*
     * private ByteBuffer getPrioritizedData() {
     * 
     * }
     */
    public Connection()
    {

    }

    private final Charset usascii = Charset.forName("US-ASCII");

    private Thread writeThread = null;

    private Thread readThread = null;

    private void writeCycle() throws ConnectionWriteException
    {
        /*
         * TODO FIXME should remove this line here when we get a better model
         * for the threads
         */
        Thread.currentThread().setName(
            "Connection: " + localURI + " writeCycle thread");
        byte[] outData = new byte[OUTPUTBUFFERLENGTH];
        ByteBuffer outByteBuffer = ByteBuffer.wrap(outData);

        int wroteNrBytes = 0;
        while (true)
        {
            try
            {

                if (transactionManager.hasDataToSend())
                {
                    int toWriteNrBytes;

                    outByteBuffer.clear();
                    // toWriteNrBytes = transactionManager.dataToSend(outData);
                    // FIXME remove comment and change method name after the
                    // tests go well
                    toWriteNrBytes = transactionManager.dataToSend2(outData);

                    outByteBuffer.limit(toWriteNrBytes);
                    wroteNrBytes = 0;
                    while (wroteNrBytes != toWriteNrBytes)
                        wroteNrBytes += socketChannel.write(outByteBuffer);
                    // commented, too much output was being generated:
                    /*
                     * logger.trace("Wrote: " + wroteNrBytes +
                     * " bytes of data to: " +
                     * socketChannel.socket().getRemoteSocketAddress()
                     * .toString());
                     */

                }
                else
                    // TODO FIXME do this in another way, maybe with notify!
                    synchronized (writeThread)
                    {
                        writeThread.wait(2000);
                    }
                // Thread.currentThread().wait(2000);
                // Thread.currentThread().sleep(2000);
                // this.wait();
            }
            catch (Exception e)
            {
                throw new ConnectionWriteException(e);
            }
        }
    }

    /**
     * This is the instance of PreParser, it is used to pre parse the received
     * data by the read cycle
     * 
     * @see #readCycle()
     * @see PreParser#preParse(byte[])
     */
    private PreParser preParser = new PreParser();

    private void readCycle() throws ConnectionReadException
    {

        byte[] inData = new byte[OUTPUTBUFFERLENGTH];
        ByteBuffer inByteBuffer = ByteBuffer.wrap(inData);
        int readNrBytes = 0;
        while (readNrBytes != -1)
        {
            inByteBuffer.clear();
            try
            {
                readNrBytes = socketChannel.read(inByteBuffer);

                if (readNrBytes != -1 && readNrBytes != 0)
                {
                    inByteBuffer.flip();

                    // commented, too much output was being generated:
                    /*
                     * logger.trace("Read: " + readNrBytes +
                     * " bytes of data from: " +
                     * socketChannel.socket().getRemoteSocketAddress()
                     * .toString());
                     */
                    preParser.preParse(inData, inByteBuffer.limit());
                }
            }
            catch (Exception e)
            {
                throw new ConnectionReadException(e);
            }
        }
    }

    /**
     * Constantly receives and sends new transactions
     */
    public void run()
    {
        // Sanity checks
        if (!this.isBound() && !this.isEstablished())
        {
            // if the socket is not bound to a local address or is
            // not connected, it shouldn't be running
            logger.error("Error!, Connection shouldn't be running yet");
            return;
        }
        if (!this.isEstablished() || !this.isBound())
        {
            logger.error("Error! got a unestablished either or "
                + "unbound connection");
            return;
        }

        if (writeThread == null && readThread == null)
        {
            writeThread = Thread.currentThread();
            readThread = new Thread(ioOperationGroup, this);
            readThread.setName("Connection: " + localURI + " readThread");
            readThread.start();

        }
        try
        {
            if (writeThread == Thread.currentThread())
            {
                writeCycle();
                writeThread = null;
            }
            if (readThread == Thread.currentThread())
            {
                readCycle();
                readThread = null;
            }
        }
        catch (Exception e)
        {
            // TODO logging or other stuff
            e.printStackTrace();
        }

        return;
        /*
         * to REMOVE
         * 
         * 
         * 
         * byte[] outData = new byte[2048];
         * 
         * ByteBuffer outByteBuffer = ByteBuffer.wrap(outData); // not sure if
         * correct to use due to the Charset, although i believe // that
         * US-ascii is always considered as a base // CharBuffer inCharBuffer =
         * inByteBuffer.asCharBuffer(); try { while (true) {
         * 
         * // READ part of the cycle inByteBuffer.clear(); int readNrBytes;
         * readNrBytes = incomingInstance.read(inByteBuffer); byte[] readBytes =
         * new byte[readNrBytes]; if (readNrBytes != -1 && readNrBytes != 0) {
         * inByteBuffer.flip(); int i = 0; while (inByteBuffer.hasRemaining())
         * readBytes[i++] = inByteBuffer.get();
         * 
         * String incomingString = new String(readBytes, usascii);
         * 
         * // parsing received data if received anything if
         * (incomingString.length() > 0) parser(incomingString); }
         * 
         * // TODO WRITE part of the cycle outByteBuffer.clear(); if
         * (transactionManager.hasDataToSend()) { int toWriteNrBytes =
         * transactionManager.dataToSend(outData);
         * outByteBuffer.limit(toWriteNrBytes); int wroteNrBytes = 0; while
         * (wroteNrBytes != toWriteNrBytes) wroteNrBytes +=
         * incomingInstance.write(outByteBuffer);
         * 
         * }
         * 
         * } } catch (Exception e) { // TODO Catch everything that .read throws
         * and capture it for // processing e.printStackTrace(); }
         * 
         * // TODO Check to see if this session given by the To-Path // is in
         * use in any other connection, if so return a 506 // TODO if the
         * session associated with this transaction doesn't exist // return a
         * 481
         */
    }

    /**
     * Class used to pre-parse the incoming data. The main purpose of this class
     * is to correctly set the receivingBinaryData variable so that accurately
     * it states if this connection is receiving data in binary format or not
     * 
     * @author João André Pereira Antunes
     * 
     */
    class PreParser
    {
        /**
         * Variable that tells if this instance of the connection is currently
         * receiving binary data or not, if it's false it's because it should be
         * receiving US-ASCII data.
         * 
         * This variable is changed by the preParser method and read by the
         * parser method.
         * 
         * @see PreParser#preParser(byte[])
         * @see #parser(String)
         */
        private boolean receivingBinaryData = false;

        /**
         * Variable used by the method preParser in order to ascertain the state
         * of the machine
         */
        private short parserState = 0;

        /**
         * This variable is used to save the possible start of the end-line.
         * It's maximum size can is of: 2 bytes for the initial CRLF after the
         * data; 7 bytes for the '-' char; 32 bytes for the transactid; 1 byte
         * for the continuation flag; 2 bytes for the ending CRLF; + Total: 44
         * bytes.
         * 
         */
        private ByteBuffer smallBuffer = ByteBuffer.allocate(44);

        /**
         * Method that implements a small state machine in order to identify if
         * the incomingData is in binary or usascii. This method is responsible
         * for changing the value of the variable receivingBinaryData to the
         * correct value and then calling the parser.
         * 
         * @param incomingData the incoming byte array that contains the data
         *            received
         * @param length the number of bytes of the given incomingData array to
         *            be considered for preprocessing.
         * @throws ConnectionParserException if there ocurred an exception while
         *             calling the parser method of this class
         * @see #receivingBinaryData
         */
        private void preParse(byte[] incomingData, int length)
            throws ConnectionParserException
        {
            ByteBuffer bufferIncData = ByteBuffer.wrap(incomingData, 0, length);
            /*
             * this variable keeps the index of the last time the data was sent
             * to be processed
             */
            int indexLastTimeChanged = 0;

            if (smallBuffer.position() != 0)
            {
                /* in case we have data to append, append it */
                int positionSmallBuffer = smallBuffer.position();
                byte[] incAppendedData =
                    new byte[(positionSmallBuffer + incomingData.length)];
                smallBuffer.flip();
                smallBuffer.get(incAppendedData, 0, positionSmallBuffer);
                smallBuffer.clear();
                bufferIncData.get(incAppendedData, positionSmallBuffer, length);
                /*
                 * now we substitute the old data for the new one with the
                 * appended bytes
                 */
                bufferIncData = ByteBuffer.wrap(incAppendedData);

                /*
                 * now we must set forward the position of the buffer so that it
                 * doesn't read again the stored bytes
                 */
                bufferIncData.position(positionSmallBuffer);

            }

            while (bufferIncData.hasRemaining())
            {

                /*
                 * we have two distinct points of start for the algorithm,
                 * either we are in the binary state or in the text state
                 */
                if (!receivingBinaryData)
                {
                    switch (parserState)
                    {
                    case 0:
                        if (bufferIncData.get() == '\r')
                        {
                            parserState = 1;
                        }
                        break;
                    case 1:
                        if (bufferIncData.get() == '\n')
                        {
                            parserState = 2;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 2:
                        if (bufferIncData.get() == '\r')
                        {
                            parserState = 3;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 3:
                        if (bufferIncData.get() == '\n')
                        {
                            parserState = 0;
                            /*
                             * if the state (binary or text) changed since the
                             * beginning of the preprocessing of the data, then
                             * we should call the parser with the data we have
                             * so far
                             */
                            parser(
                                bufferIncData.array(),
                                indexLastTimeChanged,
                                bufferIncData.position() - indexLastTimeChanged,
                                receivingBinaryData);
                            indexLastTimeChanged = bufferIncData.position();
                            if (incomingTransaction == null)
                            {
                                // TODO FIXME ?! maybe throw an exception, if we
                                // are here it means that the protocol was
                                // violated
                                logger
                                    .error("The incomingTransaction is null on the preParser in a state where it should not be (receiving binary data)!");
                            }

                            receivingBinaryData = true;
                        }
                        else
                        {
                            rewindOnePosition(bufferIncData);
                            parserState = 0;
                        }
                    }// switch (parserState)
                }// if (!receivingBinaryData)
                else
                {
                    /*
                     * if we are receiving binary data we should/must be in a
                     * transaction
                     */

                    switch (parserState)
                    {
                    case 0:
                        if (bufferIncData.get() == '\r')
                        {
                            parserState = 1;
                        }
                        break;
                    case 1:
                        if (bufferIncData.get() == '\n')
                        {
                            parserState = 2;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 2:
                        if (bufferIncData.get() == '-')
                        {
                            parserState = 3;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 3:
                        if (bufferIncData.get() == '-')
                        {
                            parserState = 4;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 4:
                        if (bufferIncData.get() == '-')
                        {
                            parserState = 5;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 5:
                        if (bufferIncData.get() == '-')
                        {
                            parserState = 6;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 6:
                        if (bufferIncData.get() == '-')
                        {
                            parserState = 7;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 7:

                        if (bufferIncData.get() == '-')
                        {
                            parserState = 8;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    case 8:
                        if (bufferIncData.get() == '-')
                        {
                            parserState = 9;
                        }
                        else
                        {
                            parserState = 0;
                            rewindOnePosition(bufferIncData);
                        }
                        break;
                    default:
                        if (parserState >= 9)
                        {
                            /*
                             * at this point if we don't have any
                             * incomingTransaction associated with this
                             * connection then something wrong happened, log it!
                             */
                            if (incomingTransaction == null)
                            {
                                // log the fact that the incoming
                                // transaction was null
                                // TODO FIXME ?! maybe throw an exception, if we
                                // are here it means that the protocol was
                                // violated
                                logger
                                    .error("The incomingTransaction is null on the preParser in a state where it should not be (receiving binary data)!");
                            }
                            else if (incomingTransaction.getTID().length() == (parserState - 9))
                            {
                                /*
                                 * it means that we reached the end of the
                                 * transact-id then we must lookout for a valid
                                 * continuation flag
                                 */
                                char incChar = (char) bufferIncData.get();
                                if (incChar == '+' || incChar == '$'
                                    || incChar == '#')
                                {
                                    parserState++;
                                }
                                else
                                {
                                    rewindOnePosition(bufferIncData);
                                    parserState = 0;
                                }
                            }
                            else if ((parserState - 9) > incomingTransaction
                                .getTID().length())
                            {
                                if ((parserState - 9) == incomingTransaction
                                    .getTID().length() + 1)
                                {
                                    /* we should expect the CR here */
                                    if (bufferIncData.get() == '\r')
                                    {
                                        parserState++;
                                    }
                                    else
                                    {
                                        parserState = 0;
                                        rewindOnePosition(bufferIncData);
                                    }
                                }
                                else if ((parserState - 9) == incomingTransaction
                                    .getTID().length() + 2)
                                {
                                    /* we should expect the LF here */
                                    if (bufferIncData.get() == '\n')
                                    {
                                        parserState++;
                                        /*
                                         * so from now on we are on text mode
                                         * again so we process all of the binary
                                         * data we have so far excluding "\r\n"
                                         * and "end-line" that later must be
                                         * parsed as text
                                         */
                                        bufferIncData.position(bufferIncData
                                            .position()
                                            - parserState);
                                        parser(bufferIncData.array(),
                                            indexLastTimeChanged, bufferIncData
                                                .position()
                                                - indexLastTimeChanged,
                                            receivingBinaryData);
                                        indexLastTimeChanged =
                                            bufferIncData.position();
                                        receivingBinaryData = false;
                                        parserState = 0;
                                    }
                                    else
                                    {
                                        rewindOnePosition(bufferIncData);
                                        parserState = 0;
                                    }
                                }
                            }

                            else if ((incomingTransaction.getTID().length() > (parserState - 9))
                                && bufferIncData.get() == incomingTransaction
                                    .getTID().charAt(parserState - 9))
                            {
                                parserState++;
                            }
                            else
                            {

                                rewindOnePosition(bufferIncData);
                                parserState = 0;
                            }
                        }// end of default:
                        break;
                    }

                }// else from if(!receivingBinaryData)
            }// while (bufferIncData.hasRemaining())

            /*
             * we pre processed everything, unless we are in binary mode and in
             * a state different than zero we should process all the remaining
             * data
             */
            if (receivingBinaryData && parserState != 0)
            {
                /* here we append the remaining data and process the rest */
                int offset =
                    (bufferIncData.position() - parserState)
                        - indexLastTimeChanged;
                // try added to catch an overflow bug! Issue #
                try
                {
                    smallBuffer.put(bufferIncData.array(), offset,
                        bufferIncData.position() - offset);
                }
                catch (BufferOverflowException e)
                {
                    logger.error("PreParser smallBuffer Overflow, trying to "
                        + "put: " + (bufferIncData.position() - offset)
                        + " bytes, offset: " + offset + " bufferIncData "
                        + "position:" + bufferIncData.position()
                        + " smallbuffer content:"
                        + new String(smallBuffer.array(), usascii)
                        + "|end of content excluding the | char");
                    throw e;

                }
                parser(bufferIncData.array(), indexLastTimeChanged, offset,
                    receivingBinaryData);
            }
            else
            {
                parser(bufferIncData.array(), indexLastTimeChanged,
                    bufferIncData.position() - indexLastTimeChanged,
                    receivingBinaryData);
            }
        }

        /**
         * Rewinds one position in the given buffer, if possible, i.e. if it's
         * at the beginning it will not rewind
         * 
         * @param buffer the buffer to rewind
         */
        private void rewindOnePosition(ByteBuffer buffer)
        {
            int position = buffer.position();
            if (position == 0)
                return;
            buffer.position(position - 1);
        }
    }// class PreParser

    private boolean receivingTransaction = false;

    private Transaction incomingTransaction = null;

    private String remainderReceive = new String();

    /**
     * receives the incoming data and identifies a transaction's start and end
     * and creates a new transaction the needed things according to the MSRP
     * norms
     * 
     * @param incomingBytes raw byte data to be handled
     * @param offset the starting position in the given byte array we should
     *            consider for processing
     * @param length the number of bytes to process starting from the offset
     *            position
     * @param receivingBinaryData true if it is receiving binary data false
     *            otherwise
     * @throws ConnectionParserException Generic error exception TODO specialize
     *             in the future
     */
    private void parser(byte[] incomingBytes, int offset, int length,
        boolean receivingBinaryData) throws ConnectionParserException
    {
        /*
         * TODO/Cleanup With the introduction of the preparser this method could
         * probably be cleaned up because there are actions that make no sense
         * anymore
         */

        if (receivingBinaryData)
        {
            try
            {
                incomingTransaction.parse(incomingBytes, offset, length,
                    receivingBinaryData);
            }
            catch (Exception e)
            {
                // TODO log it
                e.printStackTrace();
            }
        }
        else
        {
            /*
             * here it means that we are receiving text and will have the same
             * behavior than before we made the distinction between binary and
             * text data
             */
            String incomingString =
                new String(incomingBytes, offset, length, usascii);
            String toParse;
            String tID;
            boolean complete = false;
            /*
             * this checks if we have any data reaming to parse from the
             * previous calls to this function, if so it appends it to the start
             * of the incoming data
             */
            if (remainderReceive.length() > 0)
            {
                toParse = remainderReceive.concat(incomingString);
                remainderReceive = new String();
            }
            else
            {
                toParse = incomingString;
            }

            /*
             * Variable used in the case that a call to this method is done with
             * more than one transaction in the incomingString
             */
            ArrayList<String> restTransactions = new ArrayList<String>();
            while (!complete
                && (toParse.length() >= 10 || restTransactions.size() != 0))
            {
                /*
                 * Transaction trim mechanism: (used to deal with the receipt of
                 * more than one transaction on the incomingString) we join
                 * every transaction back to the toParse string for it to be
                 * dealt with
                 */
                if (restTransactions.size() > 0)
                {
                    toParse = toParse.concat(restTransactions.get(0));
                    restTransactions.remove(0);

                }
                if (restTransactions.size() > 1)
                    throw new RuntimeException(
                        "Error! the restTransactions was never meant "
                            + "to have more than one element!");

                /* end Transaction trim mechanism. */

                if (!receivingTransaction)
                {
                    // Find the MSRP start of the transaction stamp

                    // the TID has to be at least 64bits long = 8chars
                    // given as a reasonable limit of 20 for the transaction id
                    // although non normative
                    // also the method name will have the same 20 limit and has
                    // to
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
                                "(^MSRP) ([\\p{Alnum}]{8,20}) ((\\d{3}) ?(\\w{1,20})?\r\n)(.*)",
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
                        logger
                            .debug("recognised the transaction request with ID: "
                                + tID);
                        TransactionType newTransactionType;
                        try
                        {
                            newTransactionType =
                                TransactionType
                                    .valueOf(matcherTransactionRequest.group(3)
                                        .toUpperCase());
                            logger.debug("tID: " + tID + " of type:"
                                + matcherTransactionRequest.group(3));
                        }
                        catch (IllegalArgumentException argExcptn)
                        {
                            // Then we have ourselves an unsupported method
                            // create an unsupported transaction and signalize
                            // it
                            logger.warn("Unsupported type of transaction: "
                                + matcherTransactionRequest.group(3) + " tID:"
                                + tID);
                            newTransactionType =
                                TransactionType.valueOf("Unsupported"
                                    .toUpperCase());
                        }

                        try
                        {
                            incomingTransaction =
                                new Transaction(tID, newTransactionType,
                                    transactionManager, Transaction.IN);
                        }
                        catch (IllegalUseException e)
                        {
                            logger.error("Implementation error creating "
                                + "an incoming transaction", e);
                        }

                        if (newTransactionType
                            .equals(TransactionType.UNSUPPORTED))
                        {
                            incomingTransaction.signalizeEnd('$');
                            logger
                                .warn("Found an unsupported type of transaction, tID: "
                                    + tID
                                    + " signalized the end of it and called the update");
                            setChanged();
                            notifyObservers(newTransactionType);
                        }

                        // extract the start of transaction line
                        toParse = matcherTransactionRequest.group(4);
                        complete = false;

                    }// if (matcherTransactionRequest.matches())
                    else if (matcherTransactionResponse.matches())
                    {
                        receivingTransaction = true;
                        /*
                         * TODO properly log logit // Encountered a response
                         * logger.info("response encountered MSRP:" +
                         * matcherTransactionResponse.group(1) + ":MSRP");
                         * l("tID:" + matcherTransactionResponse.group(2) +
                         * ":tID"); IOInterface.debugln("group3:" +
                         * matcherTransactionResponse.group(3) + ":group3");
                         * IOInterface.debugln("group4:" +
                         * matcherTransactionResponse.group(4) + ":group4");
                         * IOInterface.debugln("group5:" +
                         * matcherTransactionResponse.group(5) + ":group5");
                         * IOInterface.debugln("group6:" +
                         * matcherTransactionResponse.group(6) + ":group6");
                         */

                        tID = matcherTransactionResponse.group(2);
                        incomingTransaction =
                            transactionManager.getTransaction(tID);
                        if (incomingTransaction == null)
                        {
                            logger
                                .error("ERROR! received response for an unfound transaction");
                        }

                        // Has to encounter the end of the transaction as well
                        // (not
                        // sure this is true)

                        logger.debug("Found a response to transaction: " + tID);
                        try
                        {
                            Transaction trResponse =
                                new TransactionResponse(incomingTransaction,
                                    Integer.parseInt(matcherTransactionResponse
                                        .group(4)), Transaction.IN);
                            incomingTransaction = trResponse;
                        }
                        catch (NumberFormatException e)
                        {
                            throw new ConnectionParserException(
                                "Creating transaction response", e);
                        }
                        catch (IllegalUseException e)
                        {
                            throw new ConnectionParserException(
                                "Creating transaction response", e);
                        }

                        // transactionManager.gotResponse(foundTransaction,
                        // matcherTransactionResponse.group(4));
                        if (matcherTransactionResponse.group(6) != null)
                        {
                            toParse = matcherTransactionResponse.group(6);
                        }

                    }
                    else
                    {
                        // TODO alter the class of the Exception, get a more
                        // complete exceptions infrastructure?!
                        // TODO receive the exception by the connection and
                        // treat it
                        // accordingly
                        logger
                            .error("start of the transaction not found on thread: "
                                + Thread.currentThread().getName());
                        throw new ConnectionParserException(
                            "Error, start of the transaction not found on thread: "
                                + Thread.currentThread().getName());
                    }

                }// if (!receivingTransaction)
                if (receivingTransaction)
                {
                    /*
                     * variable used to store the start of data position in
                     * order to optmize the data to save mechanism below
                     */
                    int startOfDataMark = 0;
                    /*
                     * Transaction trim mechanism: Search for the end of the
                     * transaction and trim the toParse string to contain only
                     * one transaction and add the rest to the restTransactions
                     */
                    Pattern endTransaction;
                    tID = incomingTransaction.getTID();
                    endTransaction =
                        Pattern.compile("(.*)(-------" + tID
                            + ")([$+#])(\r\n)(.*)?", Pattern.DOTALL);
                    Matcher matchEndTransaction =
                        endTransaction.matcher(toParse);
                    if (matchEndTransaction.matches())
                    {
                        logger
                            .trace("found the end of the transaction: " + tID);
                        /*
                         * add all of the transaction, including the endline in
                         * the to parse and leave any eventual remaining
                         * transactions
                         */
                        toParse =
                            matchEndTransaction.group(1)
                                + matchEndTransaction.group(2)
                                + matchEndTransaction.group(3)
                                + matchEndTransaction.group(4);
                        /*
                         * if we have remaining data, we add it to the
                         * restTransactions
                         */
                        if (matchEndTransaction.group(5) != null
                            && !matchEndTransaction.group(5).equalsIgnoreCase(
                                ""))
                            restTransactions.add(matchEndTransaction.group(5));

                    }
                    /*
                     * End of transaction trim mechanism
                     */

                    // Identify the end of the transaction (however it might not
                    // exist yet or it may not be complete):
                    /*
                     * identify if this transaction has content-stuff or not by
                     * the 'Content-Type 2CRLF' on the formal syntax
                     */
                    // TODO still the Content-type pattern isn't entirely
                    // correct, still have to account for the fact that the
                    // subtype * (might not exist!) eventually pass this to the
                    // RegexMSRPFactory
                    String tokenRegex = RegexMSRPFactory.token.pattern();
                    Pattern contentStuff =
                        Pattern.compile("(.*)(Content-Type:) (" + tokenRegex
                            + "/" + tokenRegex + ")(\r\n\r\n)(.*)",
                            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                    Matcher matchContentStuff = contentStuff.matcher(toParse);
                    if (matchContentStuff.matches())
                    {
                        logger.trace("transaction " + tID
                            + " was found to have contentstuff");
                        incomingTransaction.hasContentStuff = true;
                        startOfDataMark = matchContentStuff.end(4);
                    }
                    // note if this is a response the hasContentStuff is set to
                    // false on the gotResponse method, so no need to set it
                    // here
                    // although it should be here for legibility reasons
                    if (incomingTransaction.hasContentStuff)
                        endTransaction =
                            Pattern.compile("(.*)(\r\n)(-------" + tID
                                + ")([$+#])(\r\n)(.*)?", Pattern.DOTALL);
                    else
                        endTransaction =
                            Pattern.compile("(.*)(-------" + tID
                                + ")([$+#])(\r\n)(.*)?", Pattern.DOTALL);
                    matchEndTransaction = endTransaction.matcher(toParse);
                    // DEBUG -start here- REMOVE
                    if (matchEndTransaction.matches())
                    {
                        /*
                         * log properly log all of the parts of the regex match:
                         */
                        String endTransactionLogDetails =
                            "Found the end of transaction tID:" + tID
                                + " details: ";
                        endTransactionLogDetails =
                            endTransactionLogDetails
                                .concat("pre-end-line body: "
                                    + matchEndTransaction.group(1)
                                    + " end of line withouth C.F.:");
                        int aux = 2;
                        if (incomingTransaction.hasContentStuff)
                            aux = 3;
                        endTransactionLogDetails =
                            endTransactionLogDetails.concat(matchEndTransaction
                                .group(aux++)
                                + " C.F.: "
                                + matchEndTransaction.group(aux++)
                                + " rest of the message: "
                                + matchEndTransaction.group(aux++));
                        logger.trace(endTransactionLogDetails);
                    }
                    // DEBUG -end here- REMOVE

                    int i = 0;
                    // if we have a complete end of transaction:
                    if (matchEndTransaction.matches())
                    {
                        String restData;
                        try
                        {
                            incomingTransaction.parse(matchEndTransaction
                                .group(1).getBytes(usascii), 0,
                                matchEndTransaction.group(1).length(),
                                receivingBinaryData);
                        }
                        catch (Exception e)
                        {
                            // TODO log it
                            e.printStackTrace();
                        }
                        if (incomingTransaction.hasContentStuff)
                        {
                            incomingTransaction
                                .signalizeEnd(matchEndTransaction.group(4)
                                    .charAt(0));
                            restData = matchEndTransaction.group(6);
                        }
                        else
                        {
                            incomingTransaction
                                .signalizeEnd(matchEndTransaction.group(3)
                                    .charAt(0));
                            restData = matchEndTransaction.group(5);
                        }
                        setChanged();
                        notifyObservers(incomingTransaction);
                        receivingTransaction = false;
                        // parse the rest of the received data extracting the
                        // already parsed parts
                        if (restData != null && !restData.equalsIgnoreCase(""))
                        {
                            toParse = restData;
                            complete = false;
                        }
                        else
                        {
                            if (restData != null)
                                toParse = restData;
                            if (restTransactions.size() == 0)
                                // then we have nothing more to analyze
                                complete = true;
                        }
                    }
                    else
                    {
                        // we trim the toParse so that we don't abruptly cut an
                        // end
                        // of transaction
                        // we save the characters that we trimmed to be analyzed
                        // next
                        int j;
                        char[] toSave;
                        // we get the possible beginning of the trim characters
                        i = toParse.lastIndexOf('\r');

                        /*
                         * Performance optimizer: if we have a marker that has
                         * the position of the data start and the last position
                         * of '\r' identified is in the headers then don't save
                         * anything
                         */
                        if (startOfDataMark != 0 && startOfDataMark != -1)
                            if (i < startOfDataMark)
                                i = -1;

                        for (j = 0, toSave = new char[toParse.length() - i]; i < toParse
                            .length(); i++, j++)
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
                            // if we indeed have end of transaction characters
                            // in
                            // the end of the data
                            // we add them to the string that will be analyzed
                            // next
                            remainderReceive =
                                remainderReceive.concat(toSaveString);

                            logger
                                .trace("trimming end of transaction characters, before was parsing: "
                                    + toParse);
                            // trimming of the data to parse
                            toParse =
                                toParse.substring(0, toParse.lastIndexOf('\r'));
                            logger
                                .trace("trimming end of transaction characters, now is parsing: "
                                    + toParse);

                        }
                        try
                        {
                            incomingTransaction.parse(
                                toParse.getBytes(usascii), 0, toParse.length(),
                                receivingBinaryData);
                        }
                        catch (Exception e)
                        {
                            // log it
                            logger
                                .error(
                                    "Got an exception while parsing data to a transaction",
                                    e);
                            e.printStackTrace();
                        }
                        if (restTransactions.size() == 0)
                            complete = true;

                    }
                }// if (receivingTransaction)

            }
        }
    }

    private ThreadGroup ioOperationGroup;

    public void addEndPoint(URI uri, InetAddress address) throws IOException
    {
        // Adds the given endpoint to the socket address and starts the
        // listening/writing cycle
        SocketAddress remoteAddress =
            new InetSocketAddress(uri.getHost(), uri.getPort());
        /*
         * TODO FIXME probably the new TransactionManager isn't needed, however
         * i'll still create it but copy the values needed for automatic testing
         * SubIssue #1
         */
        boolean testingOld = false;
        String presetTidOld = new String();
        if (transactionManager != null)
        {
            testingOld = transactionManager.testing;
            presetTidOld = transactionManager.presetTID;

        }
        transactionManager = new TransactionManager(this);
        transactionManager.testing = testingOld;
        transactionManager.presetTID = presetTidOld;
        socketChannel.connect(remoteAddress);
        Connections connectionsInstance =
            MSRPStack.getConnectionsInstance(address);

        ioOperationGroup =
            new ThreadGroup(connectionsInstance.getConnectionsGroup(),
                "IO OP connection " + uri.toString() + " group");
        connectionsInstance.startConnectionThread(this, ioOperationGroup);

    }

    /**
     * 
     * @return an InetAddress with only the IP of where this connection is bound
     */
    public InetAddress getIpAddress()
    {
        return socketChannel.socket().getInetAddress();
    }

    /**
     * Method used to notify the write cycle thread
     */
    public void notifyWriteThread()
    {
        if (writeThread != null)
        {
            synchronized (writeThread)
            {
                writeThread.notify();
            }
        }
    }

}
