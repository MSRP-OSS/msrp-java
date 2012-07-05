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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

import javax.net.msrp.events.*;
import javax.net.msrp.exceptions.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An MSRP Session.
 * 
 * This interface, combined with {@link SessionListener} is the primary
 * interface for sending and receiving MSRP traffic.
 * <p> 
 * The class manages the list of MSRP Messages with which it's currently
 * associated.
 * 
 * @author João Antunes
 */
public class Session
{
    /** The logger associated with this class */
    private static final Logger logger = LoggerFactory.getLogger(Session.class);

    private Stack stack = Stack.getInstance();

    /**
     * Associates an listener with the session, processing incoming messages
     */
    private SessionListener myListener;

    private ArrayList<URI> toUris = new ArrayList<URI>();

    private TransactionManager txManager;

    private InetAddress localAddress;

    @SuppressWarnings("unused")		// TODO: implement TLS
	private boolean isSecure;

    /**
     * @uml.property name="_relays"
     */
    private boolean isRelay;

    /** URI identifying this session
     * @uml.property name="_URI"
     */
    private URI uri = null;

    /**
     * @desc the {@link Connection} associated with this session
     * @uml.property name="_connection"
     * @uml.associationEnd inverse="_session:javax.net.msrp.Connection"
     */
    private Connection connection = null;

//    /**
//     * @uml.property name="_failureReport"
//     */
//    private boolean failureReport = true;
//
//    /**
//     * @uml.property name="_successReport"
//     */
//    private boolean successReport;

    /**
     * The queue of messages to send.
     * 
     * @uml.property name="sendQueue"
     */
    private ArrayList<Message> sendQueue = new ArrayList<Message>();

    /**
     * stores sent/being sent messages (by message-ID) on request of the Success-Report field.
     * @uml.property name="_messagesSent"
     */
    private HashMap<String, Message> messagesSentOrSending =
        new HashMap<String, Message>();

    /**
     * contains the messages (by message-ID) being received
     */
    private HashMap<String, Message> messagesReceive =
        new HashMap<String, Message>();

    /**
     * The Report mechanism associated with this session.
     * <br>
     * The mechanism is basically used to decide on the granularity of reports.
     * Defaults to {@code DefaultReportMechanism}.
     * 
     * @see DefaultReportMechanism
     */
    private ReportMechanism reportMechanism = DefaultReportMechanism.getInstance();

    /** Create an active session with the local address.
     * <br>
     * The associated connection will be an active one
     * (will connect automatically to target).
     * <br>
     * Connection will be established once a call to {@link #addToPath(ArrayList)}
     * defines the target-list. 
     * 
     * @param isSecure	Is it a secure connection or not (use TLS - not implemented yet)?
     * @param isRelay	is this a relaying session?
     * @param address	the address to use as local endpoint.
     * @throws InternalErrorException if any error ocurred. More info about the
     *             error in the accompanying Throwable.
     * @see #addToPath(ArrayList)
     */
    public static Session create(boolean isSecure, boolean isRelay, InetAddress address)
            throws InternalErrorException {
    	return new Session(isSecure, isRelay, address);
    }

    Session(boolean isSecure, boolean isRelay, InetAddress address)
        throws InternalErrorException
    {
        this.localAddress = address;
        this.isSecure = isSecure;
        this.isRelay = isRelay;
        try
        {
            connection = new Connection(address);

            // Generate new URI and add to list of connection-URIs.
            uri = connection.generateNewURI();
            stack.addConnection(uri, connection);
            logger.debug("MSRP Session created: secure?[" + isSecure + "], relay?["
            			+ isRelay + "] InetAddress: " + address);
        }
        catch (Exception e)				// wrap exceptions to InternalError
        {
            throw new InternalErrorException(e);
        }
    }

    /** Create a passive session with the local address
     * <br>
     * The associated connection will be a passive one
     * (will listen for a connection-request from the target).
     * <br>
     * Messages will be queued until the destination contacts this session.
     * 
     * @param isSecure	Is it a secure connection or not (use TLS - not implemented yet)?
     * @param isRelay	is this a relaying session?
     * @param toUri		the destination URI that will contact this session.
     * @param address	the address to use as local endpoint.
     * @throws InternalErrorException if any error ocurred. More info about the
     *             error in the accompanying Throwable.
     */
    public static Session create(boolean isSecure, boolean isRelay, URI toURI, InetAddress address)
            throws InternalErrorException
    {
    	return new Session(isSecure, isRelay, toURI, address);
    }

    Session(boolean isSecure, boolean isRelay, URI toURI, InetAddress address)
        throws InternalErrorException
    {
        this.localAddress = address;
        this.isSecure = isSecure;
        this.isRelay = isRelay;
        try
        {
            connection = Stack.getConnectionsInstance(address);
            uri = ((Connections) connection).generateNewUri();
            stack.addConnection(uri, connection);
        }
        catch (Exception e)				// wrap exceptions to InternalError
        {
            logger.error("Error creating Connections: ", e);
            throw new InternalErrorException(e);
        }

        ((Connections) connection).addUriToIdentify(uri, this);
        toUris.add(toURI);

        logger.debug("MSRP Session created: secure?[" + isSecure + "], relay?[" + isRelay
            + "], toURI=[" + toURI + "], InetAddress:" + address);
    }

    public String toString()
    {
        return this.getURI().toString();
    }

    /** Add your own {@link ReportMechanism} class.
     * This'll enable you to define your own granularity.
     * @param reportMechanism the {@code ReportMechanism} to use.
     * @see DefaultReportMechanism
     */
    public void setReportMechanism(ReportMechanism reportMechanism)
    {
        this.reportMechanism = reportMechanism;
    }

    /**
     * @return the currently used {@code ReportMechanism} in this session.
     * @see DefaultReportMechanism
     */
    public ReportMechanism getReportMechanism()
    {
        return reportMechanism;
    }

    /** Add a listener to this session to catch any incoming traffic.
     * 
     * @param listener	the session listener to add
     * @throws IllegalArgumentException if no listener specified or of the wrong class.
     * @see SessionListener
     */
    public void addListener(SessionListener listener)
    {
        if (listener != null && listener instanceof SessionListener)
        {
            myListener = listener;
            logger.trace("Session Listener added to Session: " + this);
        } else {
        	String reason = "Listener could not be added to Session[" + this +
        					"] because it didn't match the criteria";
	        logger.error(reason);
	        throw new IllegalArgumentException(reason);
        }
    }

    /**
     * Adds the given destination URI's and establish the connection according RFC.
     * <br>
     * This call should follow the creation of a {@link Session}.
     * 
     * @param uris the to-path to use.
     * 
     * @throws IOException if there was a connection problem.
     * @throws IllegalArgumentException if the given URI's are not MSRP URIs
     * @see #create(boolean, boolean, InetAddress)
     */
    public void addToPath(ArrayList<URI> uris) throws IOException
    {
        for (URI uri : uris)
        {
        	if (RegEx.isMsrpUri(uri))
        		toUris.add(uri);
        	else
        		throw new IllegalArgumentException("Invalid To-URI: " + uri);
        }
        connection.addEndPoint(getNextURI(), localAddress);

        txManager = connection.getTransactionManager();
        txManager.addSession(this);
        txManager.initialize(this);

        stack.addActiveSession(this);

        logger.trace("Added "+ toUris.size() +" toPaths with URI[0]="
        				+ uris.get(0).toString());
    }

	/** send the given content over this session.
	 * 
	 * @param contentType	the type of content (refer to the MIME RFC's).
	 * @param content		the content itself
	 * @return				the message-object that will be send, can be used
	 * 						to abort large content.
	 * @see Message
	 */
	public Message sendMessage(String contentType, byte[] content)
	{
		return new OutgoingMessage(this, contentType, content);
	}

	/** send the given file over this session.
	 * 
	 * @param contentType	the type of file.
	 * @param content		the file itself
	 * @return				the message-object that will be send, can be used
	 * 						to abort large content.
	 * @throws SecurityException not allowed to read and/or write
	 * @throws FileNotFoundException file not found
	 */
	public Message sendMessage(String contentType, File content)
			throws FileNotFoundException, SecurityException
	{
		return new OutgoingFileMessage(this, contentType, content);
	}

    /**
     * Release all of the resources associated with this session.
     * It could eventually, but not necessarily, close connections conforming to
     * RFC 4975.
     * After teardown, this session can no longer be used.
     */
    public void tearDown()
    {
		// clear local resources
		toUris = null;

		if (sendQueue != null) {
			for (Message msg : sendQueue) {
				msg.discard();
			}
			sendQueue = null;
		}

		if (txManager != null) {
			txManager.removeSession(this);
			txManager = null;
		}
		// FIXME: (javax.net.msrp-31) allow connection reuse by sessions.
		if (connection != null) {
			connection.close();
			connection = null;
		}
		if (stack != null) {
			stack.removeActiveSession(this);
			stack = null;
		}
    }

    /** Return destination-path of this session
     * @return the list of To:-URI's
     */
    public ArrayList<URI> getToPath()
    {
        return toUris;
    }

//    /**
//     * Getter of the property <tt>_failureReport</tt>
//     * 
//     * @return Returns the failureReport.
//     * @uml.property name="_failureReport"
//     */
//    public boolean isFailureReport()
//    {
//        return failureReport;
//    }
//
//    /**
//     * Setter of the property <tt>_failureReport</tt>
//     * 
//     * @param _failureReport The failureReport to set.
//     * @uml.property name="_failureReport"
//     */
//    public void setFailureReport(boolean report)
//    {
//        this.failureReport = report;
//    }
//
//    /**
//     * Getter of the property <tt>_successReport</tt>
//     * 
//     * @return Returns the successReport.
//     * @uml.property name="_successReport"
//     */
//    public boolean isSuccessReport()
//    {
//        return successReport;
//    }
//
//    /**
//     * Setter of the property <tt>_successReport</tt>
//     * 
//     * @param _successReport The successReport to set.
//     * @uml.property name="_successReport"
//     */
//    public void setSuccessReport(boolean report)
//    {
//        successReport = report;
//    }

    /**
     * Get messages being received.
     * 
     * @return just those.
     */
    public HashMap<String, Message> getMessagesReceive()
    {
        return messagesReceive;
    }

    /**
     * Getter of the property <tt>_relays</tt>
     * 
     * @return Returns the _relays.
     * @uml.property name="_relays"
     */
    public boolean isRelay()
    {
        return isRelay;
    }

    /**
     * Setter of the property <tt>_relays</tt>
     * 
     * @param _relays The _relays to set.
     * @uml.property name="_relays"
     */
    public void setRelay(boolean isRelay)
    {
        this.isRelay = isRelay;
    }

    /**
     * Setter of the property {@code connection}
     * 
     * @param _connection The _connection to set.
     * @uml.property name="_connection"
     */
    public void setConnection(Connection connection)
    {
        this.connection = connection;
    }

    /**
     * Adds the given message to the top of the message to send queue
     * <p> 
     * Used when a message sending is paused so that when this
     * session activity gets resumed it will continue sending this message
     * 
     * @param message the message to be added on top of the message queue
     */
    protected void addMessageOnTop(Message message)
    {
        sendQueue.add(0, message);
    }

    /**
     * Adds the given message to the end of the message to send queue.
     * Kick off when queue is empty.
     * 
     * @param message the message to be added to the end of the message queue
     */
    public void addMessageToSend(Message message)
    {
        sendQueue.add(message);
        triggerSending();
    }

	/**
	 * Have txManager send awaiting messages from session.
	 */
	private void triggerSending() {
		if (txManager != null)
			txManager.generateTransactionsToSend(getMessageToSend());
	}

    /**
     * @return true if this session has messages to send false otherwise
     */
    public boolean hasMessagesToSend()
    {
        return !sendQueue.isEmpty();
    }

    /**
     * Returns and removes first message from the top of sendQueue
     * 
     * @return first message to be sent from sendQueue
     */
    public Message getMessageToSend()
    {
    	if (sendQueue == null || sendQueue.isEmpty())
    		return null;
    	return sendQueue.remove(0);
    }

    /**
     * Is session still valid (active)?
     * <p>
     * at this point this is used by the generation of the success failureReport to
     * assert if it should be sent or not quoting the RFC:
     * <p>
     * "Endpoints SHOULD NOT send REPORT requests if they have reason to believe
     * the request will not be delivered. For example, they SHOULD NOT send a
     * REPORT request for a session that is no longer valid."
     * 
     * @return true or false depending if this is a "valid" (active?!) session
     *         or not
     */
    public boolean isActive()
    {
        // TODO Auto-generated method stub
        return true;
    }

    /**
     * Delete message from the send-queue.
     * To be used only by {@link Message#abort(int, String)}
     * 
     * @param message
     * @see Message#abort(int, String)
     */
    protected void delMessageToSend(Message message)
    {
        sendQueue.remove(message);
    }

    /**
     * @return the txManager for this session.
     */
    protected TransactionManager getTransactionManager()
    {
        return txManager;
    }

    /**
     * Method that should only be called by {@link TransactionManager#addSession(Session)
     * 
     * @param txManager the txManager to set
     */
    protected void setTransactionManager(TransactionManager transactionManager)
    {
        this.txManager = transactionManager;
    }

    /**
     * 
     * retrieves a message from the sentMessages The sentMessages array may have
     * messages that are currently being sent.
     * <br>
     * They are only stored for REPORT purposes.
     * 
     * @param messageID of the message to retrieve
     * @return the message associated with the messageID
     */
    protected Message getSentOrSendingMessage(String messageID)
    {
        return messagesSentOrSending.get(messageID);
    }

    /**
     * method used by an incoming transaction to retrieve the message object
     * associated with it, if it's already being received
     * 
     * @param messageID of the message to
     * @return the message being received associated with messageID, null if not found.
     */
    protected Message getReceivingMessage(String messageID)
    {
        return messagesReceive.get(messageID);
    }

    /**
     * Put a message on the list of messages being received by this session.
     * 
     * @param message the {@link IncomingMessage} to be put on the receive queue
     * @see #messagesReceive
     */
    // FIXME: in the future just put the queue of messages
    // being received on the Stack as the Message object isn't necessarily bound
    // to the Session
    protected void putReceivingMessage(IncomingMessage message)
    {
        messagesReceive.put(message.getMessageID(), message);
    }

    /*
     * Triggers to the Listener, not really sure if they are needed now, but
     * later can be used to trigger some extra validations before actually
     * calling the callback or cleanup after.
     */
    /**
     * trigger for the registered
     * {@link SessionListener#receivedReport(Session, Transaction)} callback.
     * 
     * @param report the transaction associated with the Report
     * @see SessionListener
     */
    protected void triggerReceivedReport(Transaction report)
    {
        logger.trace("Called the triggerReceivedReport hook");
        myListener.receivedReport(this, report);
    }

    /**
     * trigger for the registered
     * {@link SessionListener#receiveMessage(Session, Message)} callback.
     * 
     * @param message the received message
     * @see SessionListener
     */
    protected void triggerReceiveMessage(Message message)
    {
        logger.trace("Called the triggerReceiveMessage hook");
        myListener.receiveMessage(this, message);
        if (hasMessagesToSend())
        	triggerSending();
    }

    /**
     * trigger for the registered
     * {@link SessionListener#acceptHook(Session, IncomingMessage)} callback.
     * 
     * @param message the message to accept or not
     * @return true or false if we are accepting the message or not
     * @see SessionListener
     */
    protected boolean triggerAcceptHook(IncomingMessage message)
    {
        logger.trace("Called the triggerAcceptHook hook");
        return myListener.acceptHook(this, message);
    }

    /**
     * trigger for the registered
     * {@link SessionListener#updateSendStatus(Session, Message, long)} callback.
     * 
     * @see SessionListener
     */
    protected void triggerUpdateSendStatus(Session session,
        OutgoingMessage outgoingMessage)
    {
        logger.trace("Called the triggerUpdateSendStatus hook");
        myListener.updateSendStatus(session, outgoingMessage,
            outgoingMessage.getSentBytes());
    }

    /**
     * trigger for the registered
     * {@link SessionListener#abortedMessageEvent(MessageAbortedEvent)} callback.
     * 
     * @param message the MSRP message that was aborted
     * @param reason the reason
     * @param extraReasonInfo the extra information about the reason if any is
     *            present (it can be transported on the body of a REPORT request)
     * @param transaction the transaction associated with the abort event
     * 
     * @see MessageAbortedEvent
     */
    protected void fireMessageAbortedEvent(Message message, int reason,
        String extraReasonInfo, Transaction transaction)
    {
        logger.trace("Called the fireMessageAbortedEvent");
        MessageAbortedEvent abortedEvent =
            new MessageAbortedEvent(message, this, reason, extraReasonInfo,
                transaction);
        SessionListener listener;
        synchronized (myListener)
        {
            listener = myListener;
        }
        listener.abortedMessageEvent(abortedEvent);
    }

    /**
     * trigger for the registered
     * {@link SessionListener#connectionLost(Session, Throwable)} callback.
     * @param cause Cause of the connection loss.
     */
    protected void triggerConnectionLost(Throwable cause) {
    	logger.trace("triggerConnectionLost() called");
    	myListener.connectionLost(this, cause);
    }
    /*
     * End of triggers to the Listener
     */

    /**
     * Adds a message to the sent message list. Stored because of
     * expected subsequent REPORT requests on this message
     * 
     * @param message the message to add
     */
    protected void addSentOrSendingMessage(Message message)
    {
        messagesSentOrSending.put(message.getMessageID(), message);
    }

    /**
     * Delete a message that stopped being received from the
     * being-received-queue of the Session.
     * 
     * NOTE: currently only called for {@code IncomingMessage} objects
     * 
     * @param message the message to be removed
     */
    protected void delMessageToReceive(IncomingMessage message)
    {
        if (messagesReceive.remove(message.getMessageID()) == null)
        {
        	logger.warn("Message to receive not found nor deleted, id="
        				+ message.getMessageID());
        }
    }

	/**
	 * @return the local address
	 */
	public InetAddress getAddress() {
		return localAddress;
	}

	/** Return the local URI (From:) of this session.
	 * @return the local URI
	 */
	public URI getURI() {
		return uri;
	}

    /** Retrieve next hop from destination list
     * @return the target URI (To:)
     */
    public URI getNextURI() {
    	return toUris.get(0);
    }

	protected Connection getConnection() {
		return connection;
	}
}
