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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.net.msrp.events.*;
import javax.net.msrp.exceptions.*;
import javax.net.msrp.wrap.Wrap;

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
 * @author Jo�o Antunes
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

    /**
     *  RFC 3994 support: indication of message composition.
     */
    private ImState isComposing = ImState.idle;
	/** what am I composing?			*/
    private String composeContentType;
	/** timestamp last active compose	*/
    private long lastActive;
	/** After this time, active transitions to idle */
    private long activeEnd;
    /** the refresh period currently in effect	*/
    private int refresh;

    /** URI identifying this session
     * @uml.property name="_URI"
     */
    private URI uri = null;
    private String id;

    /**
     * @desc the {@link Connection} associated with this session
     * @uml.property name="_connection"
     * @uml.associationEnd inverse="_session:javax.net.msrp.Connection"
     */
    private Connection connection = null;

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
     * Connection will be established once a call to {@link #setToPath(ArrayList)}
     * defines the target-list. 
     * 
     * @param isSecure	Is it a secure connection or not (use TLS - not implemented yet)?
     * @param isRelay	is this a relaying session?
     * @param address	the address to use as local endpoint.
     * @throws InternalErrorException if any error ocurred. More info about the
     *             error in the accompanying Throwable.
     * @see #setToPath(ArrayList)
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
            logger.debug(String.format(
            		"%s MSRP session %s created: secure?[%b]], relay?[%b] InetAddress: %s",
            		toString(), getId(), isSecure, isRelay, address));
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
            uri = ((Connections) connection).generateAndStartNewUri();
            stack.addConnection(uri, connection);
        }
        catch (Exception e)				// wrap exceptions to InternalError
        {
            logger.error("Error creating Connections: ", e);
            throw new InternalErrorException(e);
        }

        ((Connections) connection).addUriToIdentify(uri, this);
        toUris.add(toURI);

        logger.debug(String.format(
        		"%s MSRP session %s created: secure?[%b]], relay?[%b], toURI=[%s], InetAddress: %s",
        		toString(), getId(), isSecure, isRelay, toURI, address));
    }

    @Override
	public String toString()
    {
        return "[session:" + getId() + "]";
    }

    /**
     * @return the 'session-id' part of the sessions' msrp-uri.
     */
    public String getId()
    {
    	if (id == null)
    	{
        	String path = uri.getPath();
    		id = path.substring(1, path.indexOf(';'));
    	}
    	return id;
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
            logger.trace(this + " - Listener added");
        } else {
        	String reason = this + " - Listener could not be added. " +
        					"It didn't match the criteria";
	        logger.error(reason);
	        throw new IllegalArgumentException(reason);
        }
    }

    /**
     * @deprecated
     * Replaced by {@link #setToPath(ArrayList)}
     */
    public void addToPath(ArrayList<URI> uris) throws IOException
    {
    	setToPath(uris);
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
     * @throws RuntimeException when called twice.
     * @see #create(boolean, boolean, InetAddress)
     */
    public void setToPath(ArrayList<URI> uris) throws IOException
    {
    	if (!toUris.isEmpty())			// sanity check
    		throw new RuntimeException("Cannot set To-path twice");
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

        logger.trace(this + " added "+ toUris.size() +" toPaths with URI[0]="
        				+ uris.get(0).toString());
    }

    /** send a bodiless message (keep-alive).
	 * @see Message
     */
    public void sendAliveMessage()
    {
    	sendMessage(new OutgoingAliveMessage());
    }

    /** send the given content over this session.
	 * 
	 * @param contentType	the type of content (refer to the MIME RFC's).
	 * @param content		the content itself
	 * @return				the message-object that will be send, can be used
	 * 						to abort large content.
	 * @see Message
	 */
	public OutgoingMessage sendMessage(String contentType, byte[] content)
	{
		return sendMessage(new OutgoingMessage(contentType, content));
	}

	/** Request the given nickname to be used with this session.
	 * The nickname request will be send to the chatroom at the other end of 
	 * this session.
	 * 
	 * A result will be reported in
	 * {@link SessionListener#receivedNickNameResult(Session, TransactionResponse)
	 * @param nickname the name to use
	 * @return the actual msrp request that is sent out.
	 */
	public OutgoingMessage requestNickname(String nickname)
	{
		return sendMessage(new OutgoingMessage(nickname));
	}

	/** Wrap the given content in another type and send over this session.
	 * @param wrapType		the (mime-)type to wrap it in.
	 * @param from			from-field
	 * @param to			to-field
	 * @param contentType	the (mime-)type to wrap.
	 * @param content		actual content
	 * @return 				the message-object that will be send, can be used
	 * 						to abort large content.
	 */
	public OutgoingMessage sendWrappedMessage(String wrapType, String from, String to,
									String contentType, byte[] content)
	{
		Wrap wrap = Wrap.getInstance();
		if (wrap.isWrapperType(wrapType)) {
			WrappedMessage wm = wrap.getWrapper(wrapType);
			return sendMessage(new OutgoingMessage(wrapType, wm.wrap(from, to, contentType, content)));
		}
		return null;
	}

	/** send the given file over this session.
	 * 
	 * @param contentType	the type of file.
	 * @param fileHandle	the file itself
	 * @return				the message-object that will be send, can be used
	 * 						to abort large content.
	 * @throws SecurityException not allowed to read and/or write
	 * @throws FileNotFoundException file not found
	 */
	public OutgoingMessage sendMessage(String contentType, File fileHandle)
			throws FileNotFoundException, SecurityException
	{
		return sendMessage(new OutgoingMessage(contentType, fileHandle));
	}

	public OutgoingMessage sendMessage(OutgoingMessage message)
	{
		message.setSession(this);
		if (message.hasData())
			endComposing();
		if (message.contentType != null)
	        addMessageToSend(message);
		else
	        addMessageOnTop(message);
		return message;
	}

	/**
	 * Reply ok to a NICKNAME request.
	 * @param request the originating request (transaction)
	 * @throws IllegalUseException arguments or state invalid
	 */
	public void sendNickResult(Transaction request) throws IllegalUseException
	{
		sendNickResult(request, ResponseCode.RC200, null);
	}

	/**
	 * Reply to a NICKNAME request.
	 * @param request the originating request (transaction)
	 * @param responseCode the result to send
	 * @param comment clarifying status comment to add
	 * @throws IllegalUseException arguments or state invalid
	 */
	public void sendNickResult(Transaction request, int responseCode,
								String comment) throws IllegalUseException
	{
		if (request == null)
			throw new InvalidParameterException("Null transaction specified");
		if (request instanceof TransactionResponse)
			request.transactionManager.addPriorityTransaction(request);
		else
			request.transactionManager.generateResponse(request, responseCode, comment);
	}

	/**
	 * Is the user of this session actively composing a message?
	 * @return active or idle
	 */
	public ImState getImState()
	{
		long now = System.currentTimeMillis();

		if ((isComposing == ImState.active) && (activeEnd < now))
			endComposing();
		return isComposing;
	}

	private void endComposing() {
		isComposing = ImState.idle;
		activeEnd = 0;
	}

	/**
	 * What media is the user actively composing?
	 * @return the Content-Type being composed 
	 */
	public String getComposeContentType()
	{
		return composeContentType;
	}

	/**
	 * Last time activity has been signalled.
	 * @return timestamp
	 */
	public long getLastActive()
	{
		return lastActive;
	}

	/**
	 *  Same as {@link Session#setActive(String, int)} but with default refresh period.
	 */
	public void setActive(String contentType)
	{
		setActive(contentType, 120);
	}

	/**
	 * Indicate on session that chatter is composing a message.
	 * An active message indication will be sent when appropriate.
	 * @param contentType the type of message being composed.
	 * @param refresh interval before transitioning to idle.
	 */
	public void setActive(String contentType, int refresh)
	{
		long now = System.currentTimeMillis();
		if (contentType == null || contentType.length() == 0)
			throw new IllegalArgumentException("Content-Type must be a valid string");

		isComposing = ImState.active;
		composeContentType = contentType;
		lastActive = now;

		if ((activeEnd < now) || (this.refresh > 0 && (activeEnd - (this.refresh / 2) < now))) {
			if (refresh < 60)			/* SHOULD not be allowed	*/
				refresh = 60;
			this.refresh = refresh;
			activeEnd = lastActive + (refresh * 1000);
			sendMessage(new OutgoingStatusMessage(this, isComposing, composeContentType, refresh));
		}
	}

	/**
	 * Indicate on session that chatter is idle.
	 * An idle message indication will be sent when appropriate.
	 */
	public void setIdle()
	{
		if (isComposing == ImState.active)
		{
			endComposing();
			sendMessage(new OutgoingStatusMessage(this, isComposing, composeContentType, 0));
		}
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

		if (sendQueue != null)
		{
			for (Message msg : sendQueue) {
				msg.discard();
			}
			sendQueue = null;
		}

		if (txManager != null)
		{
			txManager.removeSession(this);
			txManager = null;
		}
		// FIXME: (javax.net.msrp-31) allow connection reuse by sessions.
		if (connection != null)
		{
			connection.close();
			connection = null;
		}
		if (stack != null)
		{
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
    protected void setConnection(Connection connection)
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
    private void addMessageOnTop(Message message)
    {
        if (sendQueue != null)
        {
        	sendQueue.add(0, message);
        	triggerSending();
        }
    }

    /**
     * Adds the given message to the end of the message to send queue.
     * Kick off when queue is empty.
     * 
     * @param message the message to be added to the end of the message queue
     */
    private void addMessageToSend(Message message)
    {
        if (sendQueue != null)
        {
            sendQueue.add(message);
            triggerSending();
        }
    }

	/**
	 * Have txManager send awaiting messages from session.
	 */
	private void triggerSending()
	{
		if (txManager != null)
		{
			while (hasMessagesToSend())
				txManager.generateTransactionsToSend(getMessageToSend());
		}
	}

    /**
     * @return true if this session has messages to send false otherwise
     */
    public boolean hasMessagesToSend()
    {
        return (sendQueue != null) && (!sendQueue.isEmpty());
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
        // TODO implement some check.
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
        if (sendQueue != null)
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
    /* FIXME: in the future just put the queue of messages being received on
     * the Stack as the Message object isn't necessarily bound to the Session
     */
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
        traceCall("triggerReceivedReport");
        myListener.receivedReport(this, report);
    }

    protected void triggerReceivedNickResult(TransactionResponse response)
    {
        traceCall("triggerReceivedNickResult");
        myListener.receivedNickNameResult(this, response);
    }

    /**
     * trigger for the registered
     * {@link SessionListener#receivedMessage(Session, Message)} callback.
     * 
     * @param message the received message
     * @see SessionListener
     */
    protected void triggerReceiveMessage(IncomingMessage message)
    {
        traceCall("triggerReceiveMessage");
        myListener.receivedMessage(this, message);
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
        traceCall("triggerAcceptHook");
        return myListener.acceptHook(this, message);
    }

    protected void triggerReceivedNickname(Transaction request)
    {
        traceCall("triggerReceivedNickname");
        myListener.receivedNickname(this, request);
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
        traceCall("triggerUpdateSendStatus");
        myListener.updateSendStatus(session, outgoingMessage,
            outgoingMessage.getSentBytes());
    }

	/**
	 * 
	 */
	private void traceCall(String call) {
		if (logger.isTraceEnabled())
			logger.trace(String.format("%s %s() called", toString(), call));
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
        traceCall("fireMessageAbortedEvent");
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
    	traceCall("triggerConnectionLost");
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
        	logger.warn(this + " receiving message to delete [" + message + "] not found");
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
