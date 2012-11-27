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
import java.nio.*;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Observable;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.msrp.exceptions.ConnectionLostException;
import javax.net.msrp.exceptions.ParseException;
import javax.net.msrp.exceptions.ConnectionReadException;
import javax.net.msrp.exceptions.ConnectionWriteException;
import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.utils.NetworkUtils;
import javax.net.msrp.utils.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class represents an MSRP connection.
 * 
 * It has one pair of threads associated for writing and reading.
 * 
 * It is also responsible for some parsing, including: Identifying MSRP
 * transaction requests and responses; Pre-parsing - identifying what is the
 * content of the transaction from what isn't; Whenever a transactions is found,
 * parse its data using the Transaction's parse method;
 * 
 * @author João André Pereira Antunes
 */
class Connection extends Observable implements Runnable
{
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
                socket.getPort(), null, null, null);
        localURI = newLocalURI;
        transactionManager = new TransactionManager(this);
        // this.addObserver(transactionManager);

    }

    /**
     * Create a new connection object.
     * This connection will create a socket and bind itself to a free port.
     * 
     * @param address hostname/IP used to bound the new MSRP socket
     * @throws URISyntaxException there was a problem generating the connection
     *             dependent part of the URI
     * @throws IOException if there was a problem with the creation of the
     *             socket
     */
    public Connection(InetAddress address) throws URISyntaxException, IOException
    {
        transactionManager = new TransactionManager(this);
        random = new Random();
        // activate the connection:

        if (NetworkUtils.isLinkLocalIPv4Address(address))
        {
            logger.info("Connection: given address is a local one: " + address);
        }

        // bind a socket to a local TEMP port.
        socketChannel = SelectorProvider.provider().openSocketChannel();
        Socket socket = socketChannel.socket();
        InetSocketAddress socketAddr = new InetSocketAddress(address, 0);
        socket.bind(socketAddr);

        // fill the localURI variable that contains the uri parts that are
        // associated with this connection (scheme[protocol], host and port)
        URI newLocalURI =
            new URI("msrp", null, address.getHostAddress(), socket.getLocalPort(),
            		null, null, null);
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

    protected Random random;

    protected URI localURI = null;

    /**
     * @uml.property name="_connections"
     * @uml.associationEnd inverse="connection1:javax.net.msrp.Connections"
     */
    protected TransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    /**
     * @param localURI sets the local URI associated with the connection
     */
    protected void setLocalURI(URI localURI)
    {
        this.localURI = localURI;
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

    protected HashSet<URI> getSessionURIs()
    {
        return sessions;
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
        URI newURI = newUri();

        /* The generated URI must be unique, if not, generate another.
         * This is what the following while does
         */
        int i = 0;
        while (sessions.contains(newURI))
        {
            i++;
            newURI = newUri();
        }
        sessions.add(newURI);
        logger.trace("generated new URI, value of i=" + i);
        return newURI;
    }

    /** Generate a new local URI with a unique session-path.
     * @return the generated URI
     * @throws URISyntaxException @see java.net.URI
     */
    protected URI newUri() throws URISyntaxException {
        byte[] randomBytes = new byte[8];

        generateRandom(randomBytes);

        if (logger.isTraceEnabled())
	        logger.trace("Random bytes generated: "
	            + (new String(randomBytes, TextUtils.utf8))
	            + ":END");

        // Generate new using current local URI.
        return
            new URI(localURI.getScheme(), localURI.getUserInfo(),
            		localURI.getHost(), localURI.getPort(),
            		 "/" + (new String(randomBytes, TextUtils.utf8))
            		 + ";tcp", localURI.getQuery(), localURI.getFragment());
    }

    // IMPROVE add the rest of the unreserved characters according rfc3986 (-._~)
    // IMPROVE speed by not doing so much calls to the Random class
    /**
     * Generates a number of random alpha-numeric codes in US-ASCII
     * 
     * @param byteArray	array that receives the newly generated bytes.
     *            	The number of generated bytes is given by the length of
     *            	the array.
     */
    protected void generateRandom(byte[] byteArray)
    {
        random.nextBytes(byteArray);
        for (int i = 0; i < byteArray.length; i++)
        {
            if (byteArray[i] < 0)
                byteArray[i] *= -1;

            while (!((byteArray[i] >= 65 && byteArray[i] <= 90)
                || (byteArray[i] >= 97 && byteArray[i] <= 122)
                || (byteArray[i] <= 57 && byteArray[i] >= 48)))
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
     * @uml.associationEnd inverse="_connectoin:javax.net.msrp.Session"
     */
    private javax.net.msrp.Session _session = null;

    /**
     * Getter of the property <tt>_session</tt>
     * 
     * @return Returns the _session.
     * @uml.property name="_session"
     */
    public javax.net.msrp.Session get_session()
    {
        return _session;
    }

    /**
     * Getter of the property <tt>_transactions</tt>
     * 
     * @return Returns the _transactions.
     * @uml.property name="_transactions"
     */

    /** close this connection/these threads
     * 
     */
    public void close()
    {
    	if (closing)
    		return;						// already closed
    	closing = true;
    	// writeThread closes itself after timeout...
//    	if (writeThread != null) {
//        	synchronized (writeThread) {
//            	writeThread.notifyAll();
//        	}
//    	}
    	try {
    		if (socketChannel != null)
    			socketChannel.close();
		} catch (IOException e) {
			/* empty */;
		}
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
    public void set_session(javax.net.msrp.Session _session)
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
    public Connection()
    {
    }

    protected boolean closing = false;	// connection closing?

    private Thread writeThread = null;
    private Thread readThread = null;

    private void writeCycle() throws ConnectionWriteException
    {
        /*
         * TODO FIXME should remove this line here when we get a better model
         * for the threads
         */
        Thread.currentThread().setName("Connection: " + localURI + " writeCycle thread");

        byte[] outData = new byte[OUTPUTBUFFERLENGTH];
        ByteBuffer outByteBuffer = ByteBuffer.wrap(outData);

        int wroteNrBytes = 0;
        while (!closing)
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
                    toWriteNrBytes = transactionManager.getDataToSend(outData);

                    outByteBuffer.limit(toWriteNrBytes);
                    wroteNrBytes = 0;
                    while (wroteNrBytes != toWriteNrBytes)
                        wroteNrBytes += socketChannel.write(outByteBuffer);
                }
                else
                {
                    // TODO FIXME do this in another way, maybe with notify!
                    synchronized (writeThread)
                    {
                        writeThread.wait(200);
                    }
                }
            }
            catch (Exception e)
            {
            	if (!closing)
            		throw new ConnectionWriteException(e);
            }
        }
    }

    /**
     * Used to pre-parse the received data by the read cycle
     * 
     * @see #readCycle()
     * @see PreParser#preParse(byte[])
     */
    PreParser preParser = new PreParser(this);

    private void readCycle() throws ConnectionReadException
    {
        byte[] inData = new byte[OUTPUTBUFFERLENGTH];
        ByteBuffer inByteBuffer = ByteBuffer.wrap(inData);
        int readNrBytes = 0;
        while (readNrBytes != -1 && !closing)
        {
            inByteBuffer.clear();
            try
            {
                readNrBytes = socketChannel.read(inByteBuffer);

                if (readNrBytes != -1 && readNrBytes != 0)
                {
                    inByteBuffer.flip();
                    preParser.preParse(inData, inByteBuffer.limit());
                }
            }
            catch (Exception e)
            {
            	if (!closing)
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
        } catch (ConnectionLostException cle) {
        	notifyConnectionLoss(cle);
        } catch (Exception e) {
        	logger.error(e.getLocalizedMessage());
        }
        return;
    }

	/**
	 * Notify sessions related to this connection that this connection is lost. 
	 * @param t	the reason it was lost.
	 */
	void notifyConnectionLoss(Throwable t) {
		Collection<Session> attachedSessions = new ArrayList<Session>();
		for (Session s : Stack.getInstance().getActiveSessions()) {
			if (this.equals(s.getConnection()))
				attachedSessions.add(s);
		}
		/* 
		 * No concurrent modifications: have active sessions remove
		 * themselves from the stack
		 */
		for (Session s : attachedSessions) {
			s.triggerConnectionLost(t.getCause());
		}
	}

    private boolean receivingTransaction = false;

    private Transaction incomingTransaction = null;

    String getCurrentIncomingTid() {
    	if (incomingTransaction != null)
    		return incomingTransaction.getTID();
    	return null;
    }

    /* Find the MSRP start of the transaction stamp
     * the TID has to be at least 64bits long = 8chars
     * given as a reasonable limit of 20 for the transaction id
     * although non normative. Also the method name will have the same 20 limit
     *  and has to be a Upper case word like SEND
     */
    private static Pattern req_start = Pattern.compile(
                    "(^MSRP) ([\\p{Alnum}]{8,20}) ([\\p{Upper}]{1,20})\r\n(.*)",
                    Pattern.DOTALL);
    private static Pattern resp_start = Pattern.compile(
                    "(^MSRP) ([\\p{Alnum}]{8,20}) ((\\d{3})([^\r\n]*)\r\n)(.*)",
                    Pattern.DOTALL);

    /**
     * Parse the incoming data, identifying transaction start or end,
     * creating a new transaction according RFC.
     * 
     * @param incomingBytes raw byte data to be handled
     * @param offset the starting position in the given byte array we should
     *            consider for processing
     * @param length the number of bytes to process starting from the offset
     *            position
     * @param inContentStuff true if it is receiving data regarding the body
     *            of a transaction, false otherwise
     * @throws ParseException Generic parsing exception
     */
    void parser(byte[] incomingBytes, int offset, int length,
        boolean inContentStuff) throws ParseException
    {
        if (inContentStuff)
        {
            try
            {
                incomingTransaction.parse(incomingBytes, offset, length,
                    inContentStuff);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {								// We are receiving headers.
            String incomingString =
                new String(incomingBytes, offset, length, TextUtils.utf8);
            String toParse = incomingString;
            String tID;
            /*
             * For calls containing multiple transactions in incomingString
             */
            ArrayList<String> txRest = new ArrayList<String>();

            do
            {
                /*
                 * Deal with reception of multiple transactions.
                 */
                if (txRest.size() > 0)
                {
                    toParse = txRest.remove(0);
                }
                if (txRest.size() > 0)
                    throw new RuntimeException(
                    		"restTransactions were never meant "
                            + "to have more than one element!");

                if (!receivingTransaction)
                {
                    Matcher matchRequest = req_start.matcher(toParse);
                    Matcher matchResponse = resp_start.matcher(toParse);

                    if (matchRequest.matches())
                    {					// Retrieve TID and create new transaction
                        receivingTransaction = true;
                        tID = matchRequest.group(2);
                        toParse = matchRequest.group(4);
                        String type = matchRequest.group(3).toUpperCase();
                        TransactionType tType;
                        try
                        {
                            tType = TransactionType.valueOf(type);
                            logger.debug(String.format(
                            		"Parsing incoming request Tx-%s[%s]", tType, tID));
                        }
                        catch (IllegalArgumentException iae)
                        {
                            tType = TransactionType.UNSUPPORTED;
                            logger.warn("Unsupported transaction type: Tx-"
                        			+ type + "[" + tID + "]");
                        }
                        try
                        {
                            incomingTransaction = new Transaction(tID, tType,
                            				transactionManager, Direction.IN);
                        }
                        catch (IllegalUseException e)
                        {
                            logger.error("Cannot create an incoming transaction", e);
                        }
                        if (tType == TransactionType.UNSUPPORTED)
                        {
                            incomingTransaction.signalizeEnd('$');
                            logger.warn(
                            		"Found an unsupported transaction type for["
                                    + tID
                                    + "] signalised end and called update");
                            setChanged();
                            notifyObservers(tType);
                            // XXX:? receivingTransaction = false;
                        }
                    }
                    else if (matchResponse.matches())
                    {
                        receivingTransaction = true;
                        tID = matchResponse.group(2);
                        int status = Integer.parseInt(matchResponse.group(4));
                        String comment = matchResponse.group(5);
                        if (matchResponse.group(6) != null)
                            toParse = matchResponse.group(6);

                        incomingTransaction =
                            transactionManager.getTransaction(tID);
                        if (incomingTransaction == null)
                        {
                            logger.error("Received response for unknown transaction");
                            // TODO: cannot continue without a known transaction, proper abort here
                        }
                        logger.debug("Found response to " + incomingTransaction);
                        try
                        {
                            Transaction trResponse =
                                new TransactionResponse(incomingTransaction,
                                			status, comment, Direction.IN);
                            incomingTransaction = trResponse;
                        }
                        catch (IllegalUseException e)
                        {
                            throw new ParseException(
                                "Cannot create transaction response", e);
                        }
                    }
                    else
                    {
                        logger.error("Start of transaction not found while parsing:\n"
                                + incomingString);
                        throw new ParseException(
                            "Error, start of the transaction not found on thread: "
                            + Thread.currentThread().getName());
                    }
                }
                if (receivingTransaction)
                {
                    /*
                     * Split multiple transactions.
                     */
                    tID = incomingTransaction.getTID();
                    Pattern endTransaction = Pattern.compile(
                        		"(.*)(-------" + tID + ")([$+#])(\r\n)(.*)?",
                        		Pattern.DOTALL);
                    Matcher matcher = endTransaction.matcher(toParse);
                    if (matcher.matches())
                    {
                        logger.trace("found end of " + incomingTransaction);
                        toParse = matcher.group(1) + matcher.group(2)
                                + matcher.group(3) + matcher.group(4);
                        /*
                         * add any remaining data to restTransactions
                         */
                        if ((matcher.group(5) != null) &&
                    		(matcher.group(5).length() > 0)) {
                            txRest.add(matcher.group(5));
                        }
                    }
                    /*
                     * identify if transaction has content-stuff or not:
                     * 'Content-Type 2CRLF' from formal syntax.
                     */
                    Pattern contentStuff = Pattern.compile(
                    		"(.*)(Content-Type:) (" + RegEx.token
                            + "/" + RegEx.token + ")(\r\n\r\n)(.*)",
                            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                    matcher = contentStuff.matcher(toParse);
                    if (matcher.matches())
                    {
                        logger.trace(incomingTransaction +
                        			" was found to have content-stuff");
                        incomingTransaction.hasContentStuff = true;
                    }
                    if (incomingTransaction.hasContentStuff) {
                    	// strip 1 CRLF from string to parse...
                        endTransaction = Pattern.compile(
                    		"(.*)(\r\n)(-------" + tID + ")([$+#])(\r\n)(.*)?",
                            Pattern.DOTALL);
                    }
                    matcher = endTransaction.matcher(toParse);

                    if (matcher.matches())
                    {					// we have a complete end of transaction
                        try
                        {
                            incomingTransaction.parse(
                        		matcher.group(1).getBytes(TextUtils.utf8), 0,
                                matcher.group(1).length(),
                                inContentStuff);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        if (incomingTransaction.hasContentStuff)
                            incomingTransaction
                                .signalizeEnd(matcher.group(4).charAt(0));
                        else
                            incomingTransaction
                                .signalizeEnd(matcher.group(3).charAt(0));

                        setChanged();
                        notifyObservers(incomingTransaction);
                        receivingTransaction = false;
                    }
                    else
                    {
                        try
                        {
                            incomingTransaction.parse(
                                toParse.getBytes(TextUtils.utf8), 0, toParse.length(),
                                inContentStuff);
                        }
                        catch (Exception e)
                        {
                            logger.error(
	                            "Exception parsing data to a transaction:", e);
                        }
                    }
                }
            }
            while (txRest.size() > 0);
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
        // -- start of the code that enables a transaction test.
//        boolean testingOld = false;
//        String presetTidOld = new String();
//        if (transactionManager != null)
//        {
//            testingOld = transactionManager.testing;
//            presetTidOld = transactionManager.presetTID;
//
//        }
//        transactionManager = new TransactionManager(this);
//        transactionManager.testing = testingOld;
//        transactionManager.presetTID = presetTidOld;
        // -- end of the code that enables a transaction test.

        socketChannel.connect(remoteAddress);
        Connections connectionsInstance =
            Stack.getConnectionsInstance(address);

        ioOperationGroup =
            new ThreadGroup(connectionsInstance.getConnectionsGroup(),
                "IO OP connection " + uri.toString() + " group");
        connectionsInstance.startConnectionThread(this, ioOperationGroup);
    }

    /**
     * @return the InetAddress of the locally bound IP
     */
    public InetAddress getLocalAddress()
    {
        return socketChannel.socket().getLocalAddress();
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
