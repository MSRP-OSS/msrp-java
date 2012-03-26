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

import msrp.exceptions.*;
import msrp.messages.*;
import msrp.events.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An MSRP Session.
 * 
 * This interface, combined with the {@code MSRPSessionListener} is the primary
 * interface for sending and receiving MSRP traffic. 
 * The class contains a list of MSRP Messages with which it's currently
 * associated.
 * 
 * @author João Antunes
 */
public class Session
{
    /**
     * The logger associated with this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Session.class);

    private MSRPStack stack = MSRPStack.getInstance();

    /**
     * Associates an interface to the session, used to process incoming messages
     */
    private MSRPSessionListener msrpSessionListener;

    private ArrayList<URI> toUris = new ArrayList<URI>();

    private TransactionManager txManager;

    private InetAddress localAddress;

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
     * @desc the connection associated with this session
     * @uml.property name="_connection"
     * @uml.associationEnd inverse="_session:msrp.Connection"
     */
    private Connection connection = null;

    /**
     * @uml.property name="_failureReport"
     */
    private boolean failureReport = true;

    /**
     * @uml.property name="_successReport"
     */
    private boolean successReport;

    /**
     * The queue of messages to send.
     * 
     * @uml.property name="sendQueue"
     */
    private ArrayList<Message> sendQueue = new ArrayList<Message>();

    /**
     * stores sent/being sent messages on request of the Success-Report field.
     * @uml.property name="_messagesSent"
     */
    private HashMap<String, Message> messagesSentOrSending =
        new HashMap<String, Message>();

    /**
     * contains the messages being received
     */
    private HashMap<String, Message> messagesReceive =
        new HashMap<String, Message>();

    /**
     * The Report mechanism associated with this session.
     * The mechanism is basically used to decide on the granularity of reports.
     * Defaults to {@code DefaultReportMechanism}.
     * 
     * @see DefaultReportMechanism
     */
    private ReportMechanism reportMechanism = DefaultReportMechanism.getInstance();

    /* 
     * TODO: Use the static methods to create a public interface
     * {@code MSRPSession} to expose the API, and make this the implementation
     * class (msrp-27).
     */
    /** Create a session with the local address.
     * The associated connection will be an active one.
     * 
     * @param isSecure	Is it a secure connection or not (use TLS)?
     * @param isRelay	is this a relaying session?
     * @param address	the address to use as local endpoint.
     * @throws InternalErrorException if any error ocurred. More info about the
     *             error in the accompanying Throwable.
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

    /** Creates a session with the local address
     * The associated connection will be a passive one.
     * 
     * @param isSecure	Is it a secure connection or not (use TLS)?
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
            connection = MSRPStack.getConnectionsInstance(address);
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

    /** Add your own {@code ReportMechanism} class.
     * This'll enable you to define your own granularity.
     * @param reportMechanism the ReportMechanism to use
     */
    public void setReportMechanism(ReportMechanism reportMechanism)
    {
        this.reportMechanism = reportMechanism;
    }

    /**
     * @return the current {@code ReportMechanism} in use in this session.
     */
    public ReportMechanism getReportMechanism()
    {
        return reportMechanism;
    }

    /** Add a listener to this session to catch any incoming traffic.
     * 
     * @param listener	the session listener
     */
    public void addListener(MSRPSessionListener listener)
    {
        if (listener != null && listener instanceof MSRPSessionListener)
        {
            msrpSessionListener = listener;
            logger.trace("MSRP Session Listener added to Session: " + this);
        } else {
	        logger.error("Listener could not be added to Session: "
	            + this + " because it didn't match the criteria");
        }
    }

    /**
     * Adds the given uri's and establish the connection according RFC.
     * 
     * @param uris the to-path to use.
     * 
     * @throws IOException if there was a connection problem.
     */
    public void addToPath(ArrayList<URI> uris) throws IOException
    {
        for (URI uri : uris)
        {
            // TODO validate each uri given prior to adding them to the list
            // related with Issue #16
            toUris.add(uri);
        }
        connection.addEndPoint(toUris.get(toUris.size() - 1), localAddress);

        txManager = connection.getTransactionManager();
        txManager.addSession(this);
        txManager.initialize(this);

        stack.addActiveSession(this);

        logger.trace("Added "+ toUris.size() +" toPaths with URI[0]="
        				+ uris.get(0).toString());
    }

	/** send the given content over this session.
	 * 
	 * @param contentType	the type of content.
	 * @param content		the content itself
	 * @return				the message-object that will be send, can be used
	 * 						to abort large content.
	 * @throws IllegalUseException
	 */
	public Message sendMessage(String contentType, byte[] content)
			throws IllegalUseException
	{
		return new OutgoingMessage(this, contentType, content);
	}

	/** send the given file over this session.
	 * 
	 * @param contentType	the type of file.
	 * @param content		the file itself
	 * @return				the message-object that will be send, can be used
	 * 						to abort large content.
	 * @throws SecurityException 
	 * @throws FileNotFoundException 
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
			Iterator<Message> it = sendQueue.iterator();
			while (it.hasNext()) {
				DataContainer buffer = it.next().getDataContainer();
				if (buffer != null)
					buffer.dispose();
			}
			sendQueue = null;
		}

		if (txManager != null) {
			txManager.removeSession(this);
			txManager = null;
		}
		// FIXME: (msrp-31) allow connection reuse by sessions.
		if (connection != null) {
			connection.close();
			connection = null;
		}
		if (stack != null) {
			stack.removeActiveSession(this);
			stack = null;
		}
    }

    public ArrayList<URI> getToPath()
    {
        return toUris;
    }

    /**
     * Getter of the property <tt>_failureReport</tt>
     * 
     * @return Returns the failureReport.
     * @uml.property name="_failureReport"
     */
    public boolean isFailureReport()
    {
        return failureReport;
    }

    /**
     * Setter of the property <tt>_failureReport</tt>
     * 
     * @param _failureReport The failureReport to set.
     * @uml.property name="_failureReport"
     */
    public void setFailureReport(boolean report)
    {
        this.failureReport = report;
    }

    /**
     * Getter of the property <tt>_successReport</tt>
     * 
     * @return Returns the successReport.
     * @uml.property name="_successReport"
     */
    public boolean isSuccessReport()
    {
        return successReport;
    }

    /**
     * Setter of the property <tt>_successReport</tt>
     * 
     * @param _successReport The successReport to set.
     * @uml.property name="_successReport"
     */
    public void setSuccessReport(boolean report)
    {
        successReport = report;
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
     * Setter of the property <tt>_connection</tt>
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
     * 
     * this method is used when a message sending is paused so that when this
     * session activity get's resumed it will continue sending this message
     * 
     * @param message the message to be added to the top of the message queue
     */
    public void addMessageOnTop(Message message)
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
		if (txManager != null) {
        	synchronized(getTransactionManager().getTransactionsToSend()) {
        		if (!txManager.hasDataToSend()) {
        			txManager.setMessageBeingSent(getMessageToSend());
        			txManager.generateTransactionsToSend();
        		}
        	}
        }
	}

    /**
     * @return true if this session has messages to send false otherwise
     */
    public boolean hasMessagesToSend()
    {
        return !sendQueue.isEmpty();
    }

    /**
     * Returns and removes first message of the top of sendQueue
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
     * at this point this is used by the generation of the success failureReport to
     * assert if it should be sent or not quoting the RFC:
     * 
     * "Endpoints SHOULD NOT send REPORT requests if they have reason to believe
     * the request will not be delivered. For example, they SHOULD NOT send a
     * REPORT request for a session that is no longer valid."
     * 
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
     * Method that receives a message to be deleted from the queue of messages
     * to being sent This method was created meant only to be called by the
     * Message.abortSend()
     * 
     * @see Message#abortSend()
     * @param message
     */
    public void delMessageToSend(Message message)
    {
        sendQueue.remove(message);
    }

    /**
     * FIXME This method shouldn't usually be used by the user, but it is
     * required due to the package structure.. related with Issue #27
     * 
     * @return the txManager
     */
    public TransactionManager getTransactionManager()
    {
        return txManager;
    }

    /**
     * Method that should only be called by the transaction manager addSession
     * method
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
     * messages that are currently being sent they are only stored for REPORT
     * purposes.
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
     * @return the message being received associated with messageID or null if
     *         there is none
     * 
     */
    protected Message getReceivingMessage(String messageID)
    {
        return messagesReceive.get(messageID);
    }

    /**
     * Function used to put a message on the list of messages being received by
     * this session. TODO FIXME: in the future just put the queue of messages
     * being received on the Stack as the Message object isn't necessarily bound
     * to the Session
     * 
     * @see #messagesReceive;
     * 
     * @param message the message to be put on the received messages queue
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
     * trigger for the registered {@code MSRPSessionListener} callback.
     * 
     * @see MSRPSessionListener
     * @param report the transaction associated with the Report
     */
    protected void triggerReceivedReport(Transaction report)
    {
        logger.trace("Called the triggerReceivedReport hook");
        msrpSessionListener.receivedReport(this, report);
    }

    /**
     * trigger for the registered {@code MSRPSessionListener} callback.
     * 
     * @see MSRPSessionListener
     * @param message the received message
     */
    protected void triggerReceiveMessage(Message message)
    {
        logger.trace("Called the triggerReceiveMessage hook");
        msrpSessionListener.receiveMessage(this, message);
        if (hasMessagesToSend())
        	triggerSending();
    }

    /**
     * trigger for the registered {@code MSRPSessionListener} callback.
     * 
     * @see MSRPSessionListener
     * @param message the message to accept or not
     * @return true or false if we are accepting the message or not
     */
    protected boolean triggerAcceptHook(IncomingMessage message)
    {
        logger.trace("Called the triggerAcceptHook hook");
        return msrpSessionListener.acceptHook(this, message);
    }

    /**
     * trigger for the registered {@code MSRPSessionListener} callback.
     * 
     * @see MSRPSessionListener
     */
    protected void triggerUpdateSendStatus(Session session,
        OutgoingMessage outgoingMessage)
    {
        logger.trace("Called the triggerUpdateSendStatus hook");
        msrpSessionListener.updateSendStatus(session, outgoingMessage,
            outgoingMessage.getSentBytes());
    }

    /**
     * trigger for the registered {@code abortedMessageEvent} callback.
     * 
     * @param message the msrp message that was aborted
     * @param reason the reason
     * @param extraReasonInfo the extra information about the reason if any is
     *            present (it can be transported on the body of a REPORT
     *            request)
     * @param transaction the transaction associated with the abort event
     * 
     * @see MessageAbortedEvent
     */
    public void fireMessageAbortedEvent(Message message, int reason,
        String extraReasonInfo, Transaction transaction)
    {
        logger.trace("Called the fireMessageAbortedEvent");
        MessageAbortedEvent abortedEvent =
            new MessageAbortedEvent(message, this, reason, extraReasonInfo,
                transaction);
        MSRPSessionListener sessionListener;
        synchronized (msrpSessionListener)
        {
            sessionListener = msrpSessionListener;
        }
        sessionListener.abortedMessageEvent(abortedEvent);
    }

    /**
     * trigger for the registered {@code MSRPSessionListener} callback.
     * 
     * @deprecated use MessageAbortedEvents instead, this one only works for
     *             incoming messages that get a ABORT in the CONTINUATION FLAG
     * 
     * 
     */
    public void triggerAbortedMessage(Session session, IncomingMessage message,
        Transaction transaction)
    {
        logger.trace("Called the triggerAbortedMessage hook");
        msrpSessionListener.abortedMessage(session, message);
        fireMessageAbortedEvent(message, MessageAbortedEvent.CONTINUATIONFLAG,
            null, transaction);
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

	public URI getURI() {
		return uri;
	}

	protected Connection getConnection() {
		return connection;
	}
}
