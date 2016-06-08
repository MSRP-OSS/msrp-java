/*
 * Copyright � Jo�o Antunes 2008 This file is part of MSRP Java Stack.
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

import java.net.InetAddress;
import java.net.URI;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.UUID;

import javax.net.msrp.exceptions.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Global MSRP singleton class.
 * 
 * This class should contain all methods that must be global or that its outcome
 * must somewhat depend on knowing about all of the existing MSRP objects like:
 * sessions, connections, transactions, messages, others(?).
 * 
 * @author Jo�o Andr� Pereira Antunes
 * 
 */
public class Stack implements Observer {

	/** The logger associated with this class */
	private static final Logger logger = LoggerFactory.getLogger(Stack.class);

	/**
	 * Field containing the maximum size of a "short message"
	 * (= size that can still be handled in memory; 1M default).
	 */
	private static int shortMessageBytes = 1024 * 1024;

	/**
	 * Stores all {@link Connections} objects mapped to the address they are bound to.
	 */
	private static Hashtable<InetAddress, Connections> addressConnections =
						new Hashtable<InetAddress, Connections>();

	private Hashtable<URI, Connection> localUriConnections;

	private Hashtable<URI, Connection> sessionConnections;

	/**
	 * The {@link Session}s that are active in this stack.
	 */
	private Hashtable<URI, Session> activeSessions;

	protected Stack() {
		localUriConnections = new Hashtable<URI, Connection>();
		sessionConnections = new Hashtable<URI, Connection>();
		activeSessions = new Hashtable<URI, Session>();
	}

	private static class SingletonHolder {
		private final static Stack INSTANCE = new Stack();
	}

	/**
	 * @return an instance of the MSRP Stack class.
	 */
	public static Stack getInstance() {
		return SingletonHolder.INSTANCE;
	}

	/**
	 * RFC 4975: "Non-SEND request bodies MUST NOT be larger than 10240 octets."
	 */
	public static final int MAX_NONSEND_BODYSIZE = 10240;

	/**
	 * RFC 4975: Maximum un-interruptible chunk-size in octets.
	 */
	public static final int MAX_UNINTERRUPTIBLE_CHUNK = 2048;

	/**
	 * Set the maximum short message size for this stack.
	 * <P>
	 * A "short message' is a message that can be put in memory.
	 * The definition of this short message parameter is used to allow the stack
	 * to safely handle messages without file storage and consuming too much memory.
	 * 
	 * @param bytes  the new maximum size (in bytes) of short messages.
	 */
	// FIXME: Note: that ATM the number of received messages that need
	// to be stored (with success report = yes) has no way of being controlled
	public static void setShortMessageBytes(int bytes) {
		shortMessageBytes = bytes;
	}

	/**
	 * Get the short message size of this stack.
	 * @see #setShortMessageBytes(int)
	 * 
	 * @return current maximum size (in bytes) of short messages. 
	 */
	public static int getShortMessageBytes() {
		return shortMessageBytes;
	}

	/**
	 * Generate a new unique message-ID
	 * 
	 * @return the generated message-ID
	 */
	public static String generateMessageID() {
		UUID id = UUID.randomUUID();
		return 	Long.toHexString(id.getMostSignificantBits()) +
				Long.toHexString(id.getLeastSignificantBits());
	}

	/**
	 * @param address
	 *            the ip address to bind to
	 *
	 * @return a {@link Connections} instance bound to the given address.
	 */
	synchronized protected static Connections getConnectionsInstance(InetAddress address)
	{
		Connections toReturn = addressConnections.get(address);
		if (toReturn != null)
			return toReturn;

		toReturn = new Connections(address);
		addressConnections.put(address, toReturn);
		return toReturn;
	}

	@Override
	public void update(Observable arg0, Object arg1) {
		/* empty */;
	}

	// TODO (?!) relocate method?! needs to be a method?! REFACTORING?!
	/**
	 * Method that generates and sends a success report based on the range of
	 * the original transaction or of the whole message. It interrupts any
	 * interruptible ongoing transaction as specified in RFC 4975
	 * 
	 * @param message
	 *            {@link Message} upon which the report is generated.
	 * @param transaction
	 *            {@link Transaction} that triggered the need to send the report is used
	 *            to gather the range of bytes on which this report will report
	 *            on and the associated session as well. the value of transaction
	 *            can be null if we are invoking this method in order to
	 *            generate a report for the whole message
	 * @param comment
	 * 			Text to be put in the comment field of the Status header.
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
			logger.error("InternalError trying to send success report", e);
		} catch (ImplementationException e) {
			logger.error("ImplementationException trying to send success report", e);
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

	/** Is there an active {@link Session} for this URI?
	 * @param uri the (from-)URI that this session should handle. 
	 * @return  there is an active session handling this URI (true)
	 */
	protected boolean isActive(URI uri) {
		return activeSessions.containsKey(uri);
	}

	/** Get the active {@link Session} handling this URI
	 * @param uri the (from-)URI that this session should handle. 
	 * @return  the {@link Session} handling this URI (or null).
	 */
	protected Session getSession(URI uri) {
		return activeSessions.get(uri);
	}

	/** Get a collection of all active sessions
	 * @return a collection of all active sessions
	 */
	protected Collection<Session> getActiveSessions() {
		return activeSessions.values();
	}

	/**
	 * @param connection
	 *            adds the received {@link Connection} into the connections list
	 */
	protected void addConnection(Connection connection) {
		if (connection == null)
			return;
		localUriConnections.put(connection.getLocalURI(), connection);
	}

	/**
	 * @param uri the URI used to search for a connection.
	 *
	 * @return The {@link Connection} associated with the given local URI
	 */
	protected Connection getConnectionByLocalURI(URI uri) {
		return localUriConnections.get(uri);
	}

	/**
	 * adds a connection-association with the session URI
	 * 
	 * @param uri
	 *            the URI to add to the existing connections
	 * @param connection
	 *            the {@link Connection} associated with this URI
	 */
	protected void addConnection(URI uri, Connection connection) {
		sessionConnections.put(uri, connection);
	}

	/**
	 * Returns an activeConnection
	 * 
	 * @return an active (bound) {@link Connection}
	 */
	protected Connection getActiveConnection() {
		for (Connection conn : sessionConnections.values()) {
			if (conn.isBound())
				return conn;
		}
		return null;
	}

	/**
	 * Removes the connection-association with the session URI.
     *
     * @param connection the connection to remove.
	 */
	public void removeConnection(Connection connection)
	{
	    for (URI uri : sessionConnections.keySet())
	    {
            Connection thisConnection = sessionConnections.get(uri);
	        if (thisConnection == connection)
	        {
                sessionConnections.remove(uri);
                return;
	        }
	    }
	}
}
