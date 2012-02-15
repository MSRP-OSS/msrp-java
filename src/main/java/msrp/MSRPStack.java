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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import msrp.exceptions.*;
import msrp.messages.*;
import msrp.utils.TextUtils;

/**
 * Global MSRP singleton class.
 * 
 * This class should contain all methods that must be global or that its outcome
 * must somewhat depend on knowing about all of the existing MSRP objects like:
 * sessions, connections, transactions, messages, others(?).
 * 
 * @author João André Pereira Antunes
 * 
 */
public class MSRPStack implements Observer {

	/**
	 * The logger associated with this class
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(MSRPStack.class);

	// Protected constructor is sufficient to suppress unauthorized calls to the
	// constructor
	protected MSRPStack() {
		localUriConnections = new HashMap<URI, Connection>();
		sessionConnections = new HashMap<URI, Connection>();
		transactions = new HashMap<String, Transaction>();

	}

	private HashMap<URI, Connection> localUriConnections = new HashMap<URI, Connection>();

	private HashMap<URI, Connection> sessionConnections = new HashMap<URI, Connection>();

	private HashMap<URI, Session> activeSessions = new HashMap<URI, Session>();

	/**
	 * Stores all the connections objects mapped to the address they are bound
	 * to
	 */
	private static HashMap<InetAddress, Connections> addressConnections = new HashMap<InetAddress, Connections>();

	/**
	 * @param address
	 *            the ip address to bind to
	 * @return a Connections instance bound to the given address if it exists,
	 *         or creates one
	 */
	protected static Connections getConnectionsInstance(InetAddress address) {
		Connections connectionsToReturn = addressConnections.get(address);
		if (connectionsToReturn != null) {
			return connectionsToReturn;
		}
		connectionsToReturn = new Connections(address);
		addressConnections.put(address, connectionsToReturn);
		return connectionsToReturn;

	}

	/**
	 * TODO (?!) relocate method?! needs to be a method?! FIXME (?!) REFACTORING
	 * Method that generates and sends a success report based on the range of
	 * the original transaction or of the whole message It interrupts any
	 * interruptible ongoing transaction as specified in rfc 4975
	 * 
	 * @param message
	 *            Message associated with the report to be generated
	 * @param transaction
	 *            Transaction that triggered the need to send the report is used
	 *            to gather the range of bytes on which this report will report
	 *            on and the associated session aswell. the value of transaction
	 *            can be null if we are invoking this method in order to
	 *            generate a report for the whole message
	 * 
	 * 
	 */
	protected static void generateAndSendSuccessReport(Message message,
			Transaction transaction, String comment) {
		try {
			Session session = transaction.getSession();
			Transaction successReport = new SuccessReport(message, transaction
					.getSession(), transaction, comment);
			session.getTransactionManager().addPriorityTransaction(
					successReport);

		} catch (InternalErrorException e) {
			logger.error("InternalError "
					+ "while trying to send a success report", e);
		} catch (ImplementationException e) {
			logger.error("ImplementationException "
					+ "while trying to send a success report", e);
		} catch (IllegalUseException e) {
			logger.error("IllegalUse of success report", e);
		}

	}

	protected void addActiveSession(Session session) {
		activeSessions.put(session.getURI(), session);
	}

	protected void removeActiveSession(Session session) {
		activeSessions.remove(session.getURI());
	}

	private HashMap<String, Transaction> transactions;

	/**
	 * The default value for the "short" message bytes
	 * 
	 * "short" messages are the ones that can be put in memory
	 */
	protected static final int DEFAULTSHORTMESSAGEBYTES = 1025 * 1024;

	/**
	 * Maximum number of bytes per un-interruptible transaction
	 */
	protected static final int MAXIMUMUNINTERRUPTIBLE = 2048;

	/**
	 * Field that has the number of bytes of the short message
	 */
	private static int shortMessageBytes = DEFAULTSHORTMESSAGEBYTES;

	/**
	 * Method used to set the short message bytes of this stack.
	 * 
	 * A "short" message is a message that can be put in memory. the definition
	 * of this short message parameter is used to allow the stack to handle
	 * safely messages without storing them in file and without consuming too
	 * much memory. To note: that ATM the number of received messages that need
	 * to be stored (which success report = yes) has no way of being controlled
	 * FIXME
	 * 
	 * @param newValue
	 *            the new int value of the short message
	 */
	public static void setShortMessageBytes(int newValue) {
		shortMessageBytes = newValue;
	}

	/**
	 * Getter for the value shortMessageBytes see the field or the setter for a
	 * definition of a short message
	 * 
	 * @return an int that has the actual shortMessageBytes
	 */
	public static int getShortMessageBytes() {
		return shortMessageBytes;
	}

	/**
	 * 
	 * @param connection
	 *            adds the received connection into the connections list
	 */
	protected void addConnection(Connection connection) {
		if (connection == null)
			return;
		localUriConnections.put(connection.getLocalURI(), connection);

	}

	/**
	 * adds a connection associated with the session URI
	 * 
	 * @param uri
	 *            the URI to add to the existing connections
	 * @param connection
	 *            the connection associated with this URI
	 */
	public void addConnection(URI uri, Connection connection) {
		sessionConnections.put(uri, connection);

	}

	/**
	 * Returns an activeConnection
	 * 
	 * @return
	 */
	protected Connection getActiveConnection() {
		for (Connection conn : sessionConnections.values()) {
			if (conn.isBound())
				return conn;
		}
		return null;
	}

	/**
	 * 
	 * @param localUriToSearch
	 *            the local uri to which we are searching for a connection
	 * @return returns the connection associated with the given local uri
	 */
	protected Connection getConnectionByLocalURI(URI localUriToSearch) {
		return localUriConnections.get(localUriToSearch);
	}

	/**
	 * 
	 * @param connection
	 *            the connection to delete from the connections list
	 */
	protected void delConnection(Connection connection) {
		/*
		 * if (connection == null) return;
		 * _urlConnections.remove(connection.getURL());
		 * _connections.remove(connection);
		 */
	}

	/**
	 * in rfc 4975: "Non-SEND request bodies MUST NOT be larger than 10240
	 * octets."
	 */
	protected static final int MAXNONSENDBODYSIZE = 10240;

	protected void addTransaction(String tid, Transaction transaction)
			throws Exception {
		if (!validTransaction(tid))
			// TODO FIXME this shouldn't be possible
			throw new Exception("Error transaction ID no longer valid");
		transactions.put(tid, transaction);
	}

	/**
	 * Asserts if this transactionId exists
	 * 
	 * @param tid
	 *            Transaction ID to be validated
	 * @return true if it's new therefore valid or false if it's not new
	 *         therefore invalid
	 */
	protected boolean validTransaction(String tid) {
		if (transactions.containsKey(tid))
			return false;
		return true;
	}

	static short counter = 0;

	/**
	 * Simple 16 bit counter
	 * 
	 * @return a short from a short counter
	 */
	private short getCounterNumber() {
		if (counter == 32767)
			counter = 0;
		return counter++;
	}

	/**
	 * Generates a new unique message-ID relative to the stack as advised in the
	 * RFC (taking into consideration the errata).
	 * 
	 * @param sessionBeingUsed
	 *            the Session that should be used to transmit the message. This
	 *            Session is only used to get the MAC address of the first
	 *            non-local interface used in it to implement the advice on
	 *            message-id generation given on RFC. If for some reason the MAC
	 *            address can't be accessed, a random number is generated
	 *            instead
	 * 
	 * @return quoting the RFC: "For example, an endpoint could concatenate an
	 *         instance identifier such as a MAC address, its idea of the number
	 *         of seconds since the epoch, a process ID, and a monotonically
	 *         increasing 16-bit integer, all base-64 encoded. Alternately, an
	 *         endpoint without an on-board clock could simply use a 64-bit |
	 *         random number and base-64 encode it." If the param
	 */
	public String generateMessageID(Session sessionBeingUsed) {
		InetAddress address = sessionBeingUsed.getAddress();
		boolean gotMacAddress = false;
		byte[] macAddress = null;
		try {
			macAddress = NetworkInterface.getByInetAddress(address)
				.getHardwareAddress();
			gotMacAddress = true;
		} catch (Exception e) { /* empty */ }

		if (!gotMacAddress || macAddress == null) {
			// if we couldn't get the data to use the advice, we'll use an epoch
			// representation plus a random number
			return Long.toString(System.currentTimeMillis())
					+ Short.toString(getCounterNumber());
		} else {
			// instead of a PID we'll use an hashcode for the class
			// ID = 19 digits max. epoch + 12 digits mac addr = 31 max.
			return Long.toString(System.currentTimeMillis())
					+ TextUtils.toHexString(macAddress);
		}
	}

	/**
	 * SingletonHolder is loaded on the first execution of
	 * Singleton.getInstance() or the first access to SingletonHolder.instance ,
	 * not before.
	 */
	private static class SingletonHolder {
		private final static MSRPStack INSTANCE = new MSRPStack();
	}

	public static MSRPStack getInstance() {
		return SingletonHolder.INSTANCE;
	}

	public void update(Observable arg0, Object arg1) {
		// TODO Auto-generated method stub

	}

	protected boolean isActive(URI uri) {
		return activeSessions.containsKey(uri);
	}

	protected Session getSession(URI uri) {
		return activeSessions.get(uri);
	}

}
