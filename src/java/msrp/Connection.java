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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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

import msrp.Connections;
import msrp.Message;
import msrp.Session;
import msrp.Transaction;
import msrp.TransactionManager;
import msrp.Transaction.TransactionType;
import msrp.exceptions.ConnectionReadException;
import msrp.exceptions.ConnectionWriteException;
import msrp.utils.NetworkUtils;

/**
 * @author João André Pereira Antunes
 */
class Connection extends Observable implements Runnable {

	public Connection(SocketChannel newSocketChannel) throws URISyntaxException {

		socketChannel = newSocketChannel;
		random = new Random();
		Socket socket = socketChannel.socket();
		URI newLocalURI = new URI("msrp", null, socket.getInetAddress()
				.getHostAddress(), socket.getLocalPort(), null, null, null);
		localURI = newLocalURI;
		transactionManager = new TransactionManager(this);
		this.addObserver(transactionManager);
	}

	/**
	 * Connection constructor for simple MSRP sessions
	 * 
	 * @param address
	 *            hostname/IP used to bound the new MSRP socket
	 * @throws URISyntaxException
	 *             there was a problem generating the connection dependent part
	 *             of the URI
	 * @throws IOException
	 *             if there was a problem with the creation of the socket
	 * 
	 */
	public Connection(InetAddress address) throws URISyntaxException,
			IOException {

		transactionManager = new TransactionManager(this);
		random = new Random();
		// activate the connection:

		boolean localAddress = false;

		// sanity check, check that the given address is a local one where a
		// socket
		// could be bound
		InetAddress local[] = InetAddress.getAllByName(InetAddress
				.getLocalHost().getHostName());

		/*
		 * for (InetAddress inetAddress : local) { if
		 * (inetAddress.equals(address)) localAddress = true; } if
		 * (!localAddress) throw new UnknownHostException(
		 * "the given address is a loopback device not a local/inet one");
		 */

		if (NetworkUtils.isLinkLocalIPv4Address(address)) {
			IOInterface
					.debugln("Connection: Warning! given address is a local one: "
							+ address);
		}

		// bind a socket to a local random port, if it's in use try again
		// FIXME could prove to be a bug if the bind procedure doesn't work

		socketChannel = SelectorProvider.provider().openSocketChannel();
		Socket socket = socketChannel.socket();
		InetSocketAddress socketAddr;
		for (socketAddr = new InetSocketAddress(address, random
				.nextInt(65535 - 1024) + 1024); !socket.isBound(); socketAddr = new InetSocketAddress(
				address, random.nextInt(65535 - 1024) + 1024)) {
			try {
				socket.bind(socketAddr);
			} catch (IOException e) {

				// do nothing
			}
		}

		// fill the localURI variable that contains the uri parts that are
		// associated with this connection (scheme[protocol], host and port)
		URI newLocalURI = new URI("msrp", null, address.getHostAddress(),
				socket.getLocalPort(), null, null, null);
		localURI = newLocalURI;
		this.addObserver(transactionManager);
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

	protected TransactionManager getTransactionManager() {
		return transactionManager;
	}

	/**
	 * @param localURI
	 *            sets the local URI associated with the connection
	 */
	protected void setLocalURI(URI localURI) {
		localURI = localURI;
	}

	protected boolean isEstablished() {
		if (socketChannel == null)
			return false;
		return socketChannel.isConnected();
	}

	/**
	 * 
	 * @return returns the associated local uri (relevant parts of the uri for
	 *         the connection)
	 */
	protected URI getLocalURI() {
		return localURI;
	}

	protected URL getRemoteURL() {
		return _remoteURL;
	}

	protected HashSet<URI> getSessionURIs() {
		return sessions;
	}

	private void setRemoteURL(URL url) {
		_remoteURL = url;
	}

	/**
	 * Generates the new URI and validates it against the existing URIs of the
	 * sessions that this connection handles
	 * 
	 * @throws URISyntaxException
	 *             If there is a problem with the generation of the new URI
	 */
	protected synchronized URI generateNewURI() throws URISyntaxException {

		byte[] randomBytes = new byte[8]; // Variable used for generating the
		// random US-ASCII alpha numeric and
		// digit bytes
		// DEBUG
		IOInterface.debugln("random bytes uninitialized:"
				+ (new String(randomBytes, Charset.forName("ascii"))));

		generateRandom(randomBytes);
		// DEBUG
		IOInterface.debugln("random bytes:"
				+ (new String(randomBytes, Charset.forName("us-ascii")))
				+ ":END of bytes ");

		// Get the current local URI.
		URI newURI = new URI(localURI.getScheme(), localURI.getUserInfo(),
				localURI.getHost(), localURI.getPort(),
				"/" + (new String(randomBytes, Charset.forName("us-ascii")))
						+ ";tcp", localURI.getQuery(), localURI.getFragment());

		int i = 0;

		while (sessions.contains(newURI)) {
			i++;
			generateRandom(randomBytes);
			newURI = new URI(localURI.getScheme(), localURI.getUserInfo(),
					localURI.getHost(), localURI.getPort(), "/"
							+ (new String(randomBytes, Charset
									.forName("us-ascii"))) + ";tcp", localURI
							.getQuery(), localURI.getFragment());
		}

		sessions.add(newURI);

		// DEBUG
		IOInterface.debugln("generated the new URI, value of i:" + i);
		return newURI;

	}

	// IMPROVE it could be improved by adding the rest of the unreserved
	// characters according to rfc3986 (-._~)
	// IMPROVE can be improved the speed by not doing so much calls to the
	// Random class
	/**
	 * Generates a number of random alpha-numeric and digit codes in US-ASCII
	 * 
	 * @param byteArray
	 *            the byte array that will contain the newly generated bytes.
	 *            the number of generated bytes is given by the length of the
	 *            byteArray
	 * 
	 */

	private void generateRandom(byte[] byteArray) {
		int i;
		random.nextBytes(byteArray);
		for (i = 0; i < byteArray.length; i++) {
			if (byteArray[i] < 0)
				byteArray[i] *= -1;

			while (!((byteArray[i] >= 65 && byteArray[i] <= 90)
					|| (byteArray[i] >= 97 && byteArray[i] <= 122) || (byteArray[i] <= 57 && byteArray[i] >= 48))) {

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
	protected boolean isBound() {
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
	public msrp.Session get_session() {
		return _session;
	}

	/**
	 * @uml.property name="_transactions"
	 * @uml.associationEnd multiplicity="(1 1)"
	 *                     inverse="_connection:msrp.Transaction"
	 */
	private msrp.Transaction _transactions = null;

	/**
	 * Getter of the property <tt>_transactions</tt>
	 * 
	 * @return Returns the _transactions.
	 * @uml.property name="_transactions"
	 */
	public msrp.Transaction get_transactions() {
		return _transactions;
	}

	/**
     */
	public void close() {
	}

	/**
     */
	public void messageInterrupt(Message message) {
	}

	/**
     */
	public void newTransaction(Session session, Message message,
			TransactionManager transactionManager, String transactionCode) {
	}

	/**
	 * @desc - Read (reads from the stream of the socket)
	 * @desc - Validation of what is being read
	 * @desc - Misc. Interrupts due to read errors (mem, buffers etc)
	 * 
	 */
	public void read() {
	}

	/**
     */
	public void sessionClose(Session session) {
	}

	/**
	 * @desc Write (writes to the stream of the socket)
	 */
	private void write() {
	}

	/**
	 * Setter of the property <tt>_transactions</tt>
	 * 
	 * @param _transactions
	 *            The _transactions to set.
	 * @uml.property name="_transactions"
	 */
	public void set_transactions(msrp.Transaction _transactions) {
		this._transactions = _transactions;
	}

	/**
	 * Setter of the property <tt>_session</tt>
	 * 
	 * @param _session
	 *            The _session to set.
	 * @uml.property name="_session"
	 */
	public void set_session(msrp.Session _session) {
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
	public Connection() {

	}

	private final Charset usascii = Charset.forName("US-ASCII");

	private Thread writeThread = null;

	private Thread readThread = null;

	private void writeCycle() throws ConnectionWriteException {
		/*
		 * TODO FIXME should remove this line here when we get a better model
		 * for the threads
		 */
		Thread.currentThread().setName(
				"Connection: " + localURI + " writeCycle thread");
		byte[] outData = new byte[2048];
		ByteBuffer outByteBuffer = ByteBuffer.wrap(outData);

		int wroteNrBytes = 0;
		while (true) {
			try {
				if (transactionManager.hasDataToSend()) {
					int toWriteNrBytes;

					outByteBuffer.clear();
					toWriteNrBytes = transactionManager.dataToSend(outData);

					outByteBuffer.limit(toWriteNrBytes);
					wroteNrBytes = 0;
					while (wroteNrBytes != toWriteNrBytes)
						wroteNrBytes += socketChannel.write(outByteBuffer);

				} else
					// TODO FIXME do this in another way, maybe with notify!
					Thread.currentThread().sleep(2000);
				// this.wait();
			} catch (Exception e) {
				throw new ConnectionWriteException(e);
			}
		}
	}

	private void readCycle() throws ConnectionReadException {

		byte[] testeIn = new byte[2048];
		ByteBuffer inByteBuffer = ByteBuffer.wrap(testeIn);
		int readNrBytes = 0;
		while (readNrBytes != -1) {
			inByteBuffer.clear();
			try {
				readNrBytes = socketChannel.read(inByteBuffer);

				byte[] readBytes = new byte[readNrBytes];

				if (readNrBytes != -1 && readNrBytes != 0) {
					inByteBuffer.flip();
					int i = 0;
					while (inByteBuffer.hasRemaining())
						readBytes[i++] = inByteBuffer.get();

					//FIXME Issue #1
					String incomingString = new String(readBytes, usascii);

					// parsing received data if received anything
					if (incomingString.length() > 0)
						parser(incomingString);

				}
			} catch (Exception e) {
				throw new ConnectionReadException(e);
			}
		}
	}

	/**
	 * Constantly receives and sends new transactions
	 */
	public void run() {
		// Sanity checks
		if (!this.isBound() && !this.isEstablished()) {
			// if the socket is not bound to a local address or is
			// not connected, it shouldn't be running
			IOInterface.debugln("Error!, Connection shouldn't be running yet");
			return;
		}
		if (!this.isEstablished() || !this.isBound()) {
			// DEBUG it shouldn't get here!
			IOInterface.debugln("Error! got a unestablished either or "
					+ "unbound connection");
			return;
		}

		if (writeThread == null && readThread == null) {
			writeThread = Thread.currentThread();
			readThread = new Thread(ioOperationGroup, this);
			readThread.setName("Connection: " + localURI + " readThread");
			readThread.start();

		}
		try {
			if (writeThread == Thread.currentThread()) {
				writeCycle();
				writeThread = null;
			}
			if (readThread == Thread.currentThread()) {
				readCycle();
				readThread = null;
			}
		} catch (Exception e) {
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

	private boolean receivingTransaction = false;

	private Transaction incomingTransaction = null;

	private String remainderReceive = new String();

	/**
	 * receives the incoming data and identifies a transaction's start and end
	 * and creates a new transaction the needed things according to the MSRP
	 * norms
	 * 
	 * @param incomingString
	 *            raw text data to be handled
	 * @throws Exception
	 *             Generic error exception TODO specialize in the future
	 */
	private void parser(String incomingString) throws Exception {
		String toParse;
		String tID;
		boolean complete = false;
		if (remainderReceive.length() > 0) {
			toParse = remainderReceive.concat(incomingString);
			remainderReceive = new String();
		} else {
			toParse = incomingString;
		}

		ArrayList<String> restTransactions = new ArrayList<String>();
		while (!complete
				&& (toParse.length() >= 48 || restTransactions.size() != 0)) {
			/*
			 * Transaction trim mechanism: (used to deal with the receipt of
			 * more than one transaction on the incomingString) we join every
			 * transaction back to the toParse string for it to be dealt with
			 */
			if (restTransactions.size() > 0) {
				toParse = toParse.concat(restTransactions.get(0));
				restTransactions.remove(0);

			}
			if (restTransactions.size() > 1)
				throw new RuntimeException(
						"Error! the restTransactions was never ment "
								+ "to have more than one element!");

			/* end Transaction trim mechanism. */

			if (!receivingTransaction) {
				// Find the MSRP start of the transaction stamp

				// the TID has to be at least 64bits long = 8chars
				// given as a reasonable limit of 20 for the transaction id
				// although non normative
				// also the method name will have the same 20 limit and has to
				// be a Upper
				// case word like SEND
				Pattern startTransactionRequest = Pattern
						.compile(
								"(^MSRP) ([\\p{Alnum}]{8,20}) ([\\p{Upper}]{1,20})\r\n(.*)",
								Pattern.DOTALL);
				Pattern startTransactionResponse = Pattern
						.compile(
								"(^MSRP) ([\\p{Alnum}]{8,20}) ((\\d{3}) ?(\\w{1,20})?\r\n)(.*)",
								Pattern.DOTALL);
				Matcher matcherTransactionResponse = startTransactionResponse
						.matcher(toParse);

				// zero tolerance - If such pattern is not found,
				// make actions to drop the connection as this is an invalid
				// session
				Matcher matcherTransactionRequest = startTransactionRequest
						.matcher(toParse);
				if (matcherTransactionRequest.matches()) {
					receivingTransaction = true;
					// Retrieve the TID and create a new transaction
					// Transaction newTransaction = new
					// Transaction(matcherTransaction
					// .group(2),matcherTransaction.group(2));
					tID = matcherTransactionRequest.group(2);
					TransactionType newTransactionType;
					try {
						newTransactionType = TransactionType
								.valueOf(matcherTransactionRequest.group(3)
										.toUpperCase());
					} catch (IllegalArgumentException argExcptn) {
						// Then we have ourselves an unsupported method
						// create an unsupported transaction and sinalize it
						newTransactionType = TransactionType
								.valueOf("Unsupported".toUpperCase());
					}

					incomingTransaction = new Transaction(tID,
							newTransactionType, transactionManager);

					if (newTransactionType.equals(TransactionType.UNSUPPORTED)) {
						incomingTransaction.sinalizeEnd('$');
						setChanged();
						notifyObservers(newTransactionType);
					}

					// DEBUG REMOVE
					IOInterface.debugln("identified the following TID:" + tID);

					// extract the start of transaction line
					toParse = matcherTransactionRequest.group(4);
					complete = false;

				}// if (matcherTransactionRequest.matches())
				else if (matcherTransactionResponse.matches()) {
					receivingTransaction = true;
					// Encountered a response
					IOInterface.debugln("MSRP:"
							+ matcherTransactionResponse.group(1) + ":MSRP");
					IOInterface.debugln("tID:"
							+ matcherTransactionResponse.group(2) + ":tID");
					IOInterface.debugln("group3:"
							+ matcherTransactionResponse.group(3) + ":group3");
					IOInterface.debugln("group4:"
							+ matcherTransactionResponse.group(4) + ":group4");
					IOInterface.debugln("group5:"
							+ matcherTransactionResponse.group(5) + ":group5");
					IOInterface.debugln("group6:"
							+ matcherTransactionResponse.group(6) + ":group6");

					tID = matcherTransactionResponse.group(2);
					incomingTransaction = transactionManager
							.getTransaction(tID);
					if (incomingTransaction == null) {
						// TODO maybe log the error!
						IOInterface
								.debugln("ERROR! received response for a not found transaction");
					}

					// Has to encounter the end of the transaction as well (not
					// sure this is true)

					incomingTransaction.gotResponse(matcherTransactionResponse
							.group(4));

					// transactionManager.gotResponse(foundTransaction,
					// matcherTransactionResponse.group(4));
					if (matcherTransactionResponse.group(6) != null) {
						toParse = matcherTransactionResponse.group(6);
					}

				} else {
					// TODO alter the class of the Exception, get a more
					// complete exceptions infrastructure?!
					// TODO receive the exception by the connection and treat it
					// accordingly
					throw new Exception(
							"Error, start of the transaction not found on thread: "
									+ Thread.currentThread().getName());
				}

			}// if (!receivingTransaction)
			if (receivingTransaction) {
				/*
				 * variable used to store the start of data position in order to
				 * optmize the data to save mechanism below
				 */
				int startOfDataMark = 0;
				/*
				 * Transaction trim mechanism: Search for the end of the
				 * transaction and trim the toParse string to contain only one
				 * transaction and add the rest to the restTransactions
				 */
				Pattern endTransaction;
				tID = incomingTransaction.getTID();
				endTransaction = Pattern.compile("(.*)(-------" + tID
						+ ")([$+#])(\r\n)(.*)?", Pattern.DOTALL);
				Matcher matchEndTransaction = endTransaction.matcher(toParse);
				if (matchEndTransaction.matches()) {
					/*
					 * add all of the transaction, including the endline in the
					 * to parse and leave any eventual remaining transactions
					 */
					toParse = matchEndTransaction.group(1)
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
				 * identify if this transaction has content-stuff or not by the
				 * 'Content-Type 2CRLF' on the formal syntax
				 */
				Pattern contentStuff = Pattern.compile(
						"(.*)(Content-Type:) (\\p{Alnum}{1,30}"
								+ "/\\p{Alnum}{1,30})(\r\n\r\n)(.*)",
						Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
				Matcher matchContentStuff = contentStuff.matcher(toParse);
				if (matchContentStuff.matches()) {
					incomingTransaction.hasContentStuff = true;
					startOfDataMark = matchContentStuff.end(4);
				}
				// note if this is a response the hasContentStuff is set to
				// false on the gotResponse method, so no need to set it here
				// although it should be here for legibility reasons
				if (incomingTransaction.hasContentStuff)
					endTransaction = Pattern.compile("(.*)(\r\n)(-------" + tID
							+ ")([$+#])(\r\n)(.*)?", Pattern.DOTALL);
				else
					endTransaction = Pattern.compile("(.*)(-------" + tID
							+ ")([$+#])(\r\n)(.*)?", Pattern.DOTALL);
				matchEndTransaction = endTransaction.matcher(toParse);
				// DEBUG -start here- REMOVE
				if (matchEndTransaction.matches()) {
					IOInterface.debugln("Found the end of the transaction!!");
					IOInterface.debugln("Rest of the body:"
							+ matchEndTransaction.group(1));
					IOInterface.debugln("end of transaction:"
							+ matchEndTransaction.group(2));
					IOInterface.debugln("Continuation flag:"
							+ matchEndTransaction.group(3));
					IOInterface.debugln("Rest of the message:"
							+ matchEndTransaction.group(5));
				}
				// DEBUG -end here- REMOVE

				int i = 0;
				// if we have a complete end of transaction:
				if (matchEndTransaction.matches()) {
					String restData;
					incomingTransaction.parse(matchEndTransaction.group(1));
					if (incomingTransaction.hasContentStuff) {
						incomingTransaction.sinalizeEnd(matchEndTransaction
								.group(4).charAt(0));
						restData = matchEndTransaction.group(6);
					} else {
						incomingTransaction.sinalizeEnd(matchEndTransaction
								.group(3).charAt(0));
						restData = matchEndTransaction.group(5);
					}
					setChanged();
					notifyObservers(incomingTransaction);
					receivingTransaction = false;
					// parse the rest of the received data extracting the
					// already parsed parts
					if (restData != null && !restData.equalsIgnoreCase("")) {
						toParse = restData;
						complete = false;
					} else {
						if (restData != null)
							toParse = restData;
						if (restTransactions.size() == 0)
							// then we have nothing more to analyze
							complete = true;
					}
				} else {
					// we trim the toParse so that we don't abruptly cut an end
					// of transaction
					// we save the characters that we trimmed to be analyzed
					// next
					int j;
					char[] toSave;
					// we get the possible beginning of the trim characters
					i = toParse.lastIndexOf('\r');

					/*
					 * Performance optimizer: if we have a marker that has the
					 * position of the data start and the last position of '\r'
					 * identified is in the headers then don't save anything
					 */
					if (startOfDataMark != 0 && startOfDataMark != -1)
						if (i < startOfDataMark)
							i = -1;

					for (j = 0, toSave = new char[toParse.length() - i]; i < toParse
							.length(); i++, j++) {
						if (i == -1 || i == toParse.length()) {
							break;
						}

						toSave[j] = toParse.charAt(i);
					}

					// buildup of the regex pattern of the possible end of
					// transaction characters
					String patternStringEndT = new String(
							"((\r\n)|(\r\n-)|(\r\n--)|(\r\n---)|(\r\n----)|(\r\n-----)|(\r\n------)|"
									+ "(\r\n-------)");
					CharBuffer tidBuffer = CharBuffer.wrap(tID);
					for (i = 0; i < tID.length(); i++) {
						patternStringEndT = patternStringEndT
								.concat("|(\r\n-------"
										+ tidBuffer.subSequence(0, i) + ")");
					}
					patternStringEndT = patternStringEndT.concat(")?$");

					Pattern endTransactionTrim = Pattern.compile(
							patternStringEndT, Pattern.DOTALL);
					String toSaveString = new String(toSave);

					// toSaveString = "\n--r";

					matchEndTransaction = endTransactionTrim
							.matcher(toSaveString);

					if (matchEndTransaction.matches()) {
						// if we indeed have end of transaction characters in
						// the end of the data
						// we add them to the string that will be analyzed next
						remainderReceive = remainderReceive
								.concat(toSaveString);

						// trimming of the data to parse
						toParse = toParse.substring(0, toParse
								.lastIndexOf('\r'));
					}
					incomingTransaction.parse(toParse);
					if (restTransactions.size() == 0)
						complete = true;

				}
			}// if (receivingTransaction)

		}

	}

	private ThreadGroup ioOperationGroup;

	public void addEndPoint(URI uri, InetAddress address) throws IOException {
		// Adds the given endpoint to the socket address and starts the
		// listening/writing cycle
		SocketAddress remoteAddress = new InetSocketAddress(uri.getHost(), uri
				.getPort());
		transactionManager = new TransactionManager(this);
		socketChannel.connect(remoteAddress);
		Connections connectionsInstance = MSRPStack
				.getConnectionsInstance(address);

		ioOperationGroup = new ThreadGroup(connectionsInstance
				.getConnectionsGroup(), "IO OP connection " + uri.toString()
				+ " group");
		connectionsInstance.startConnectionThread(this, ioOperationGroup);

	}

	/**
	 * 
	 * @return an InetAddress with only the IP of where this connection is bound
	 */
	public InetAddress getIpAddress() {
		return socketChannel.socket().getInetAddress();
	}

	/**
	 * Method used to notify the write cycle thread
	 */
	public void notifyWriteThread() {
		writeThread.notify();
	}

}
