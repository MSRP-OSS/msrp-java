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

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
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

    /**
     * Associates an interface to the session, used to process incoming messages
     * 
     */
    private MSRPSessionListener msrpSessionListener;

    private MSRPStack stack = MSRPStack.getInstance();

    private ArrayList<URI> toUris = new ArrayList<URI>();

    private TransactionManager txManager;

    private InetAddress localAddress;

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
     * @uml.property name="_relays"
     */
    private boolean isRelay;

    /** URI identifying this session
     * @uml.property name="_URI"
     */
    private URI uri = null;

    /**
     * The Report mechanism associated with this session.
     * The mechanism is basically used to decide on the granularity of reports.
     * Defaults to {@code DefaultReportMechanism}.
     * 
     * @see DefaultReportMechanism
     */
    private ReportMechanism reportMechanism = DefaultReportMechanism.getInstance();

    // TODO alter the constructors to use the this(argument) call

    /**
     * Create a session with the local address.
     * The associated connection will be an active one.
     * 
     * @param isSecure	Is it a secure connection or not (use TLS)?
     * @param isRelay	is this a relaying session?
     * @param address	the address to use as local endpoint.
     * @throws InternalErrorException if any error ocurred. More info about the
     *             error in the accompanying Throwable.
     */
    public Session(boolean isSecure, boolean isRelay, InetAddress address)
        throws InternalErrorException
    {
        try
        {
            this.localAddress = address;
            setRelay(isRelay);
            connection = new Connection(address);

            // Generate new URI and add to list of connection-URIs.
            uri = connection.generateNewURI();
            stack.addConnection(uri, connection);
            logger.debug("MSRP Session created: secure?[" + isSecure + "], relay?["
            			+ isRelay + "] InetAddress: " + address);
        }
        catch (Exception e)
        {
            // let's wrap the exceptions in an InternalError
            throw new InternalErrorException(e);
        }
    }

    /**
     * Creates a session with the local address
     * The associated connection will be a passive one.
     * 
     * @param isSecure	Is it a secure connection or not (use TLS)?
     * @param isRelay	is this a relaying session?
     * @param toUri		the destination URI that will contact this session.
     * @param address	the address to use as local endpoint.
     * @throws InternalErrorException if any error ocurred. More info about the
     *             error in the accompanying Throwable.
     * 
     */
    public Session(boolean isSecure, boolean isRelay, URI toURI, InetAddress address)
        throws InternalErrorException
    {
        this.localAddress = address;
        connection = MSRPStack.getConnectionsInstance(address);
        try
        {
            uri = ((Connections) connection).generateNewUri();
        }
        catch (Exception e)
        {
            // let's wrap every exception in an InternalError one
            logger.error("Got an internal error exception", e);
            throw new InternalErrorException(e);
        }
        ((Connections) connection).addUriToIdentify(uri, this);
        // is the subsequent needed?! TODO
        stack.addConnection(uri, connection);
        setRelay(isRelay);
        toUris.add(toURI);
        logger.trace("MSRP Session created, tls: " + isSecure + " isRelay: " + isRelay
            + " destinationURI: " + toURI + " InetAddress:" + address);

    }

    /**
     * Constructor used to define the failureReport mechanism for all messages (wrapper
     * for the tls isRelay InetAddress constructor)
     * 
     * @throws IOException
     * @throws URISyntaxException
     */
    public Session(boolean isSecure, boolean isRelay, InetAddress address,
        ReportMechanism reportMechanism)
        throws InternalErrorException
    {
        this(isSecure, isRelay, address);
        this.reportMechanism = reportMechanism;
        logger.trace("MSRP Session created with custom failureReport mechanism, tls: "
            + isSecure + " isRelay: " + isRelay + " InetAddress:" + address);
    }

    /**
     * Constructor used to define the failureReport mechanism for all messages (wrapper
     * for the tls, isRelay, destinationURI, InetAddress constructor)
     * 
     * @throws Exception
     */
    public Session(boolean isSecure, boolean isRelay, URI destinationURI,
        InetAddress address, ReportMechanism reportMechanism)
        throws Exception
    {
        this(isSecure, isRelay, destinationURI, address);
        this.reportMechanism = reportMechanism;
        logger.trace("MSRP Session created with custom failureReport mechanism, tls: "
            + isSecure + " isRelay: " + isRelay + " destinationURI: " + destinationURI
            + " InetAddress:" + address);
    }

    /**
     * Adds the given uri and establishes the connection according to the
     * connection model specified in the RFC
     * 
     * @param uri Receives the uri of the new connection Is used to set the new
     *            connection to remote url given by the URI
     * @return the newly or already existing connection
     * @throws URISyntaxException in case there was a problem with the given URI
     * @throws IOException if there was a problem with the socket
     */
    public void addToPath(ArrayList<URI> uris)
        throws URISyntaxException,
        IOException
    {
        // the following for each block only makes sense because of the
        // validation task that is yet to be done described below
        for (URI uri : uris)
        {
            // TODO validate each uri given prior to adding them to the list
            //related with Issue #16
            this.toUris.add(uri);
        }
        connection.addEndPoint(this.toUris.get(this.toUris.size() - 1), localAddress);
        // stack.addActiveSession(this);
        txManager = connection.getTransactionManager();
        txManager.addSession(this);
        txManager.initialize(this);

        logger.trace("Added toPath with URI[0]: " + uris.get(0).toString()
            + " URI array size:" + this.toUris.size());

        /*
         * //TODO: refactor the method it does somethings that don't make sense
         * anymore
         * 
         * URI newUri = new URI(uri); URL newUrl = newUri.toURL();
         * 
         * 
         * Connection newCon =
         * MSRPStack.getInstance().getConnectionByURL(newUrl); if (newCon !=
         * null) { setConnection(newCon); return _connection; } newCon = new
         * Connection(); setConnection(newCon);
         * 
         * 
         * return _connection;
         */
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
    public boolean is_failureReport()
    {
        return failureReport;
    }

    /**
     * Setter of the property <tt>_failureReport</tt>
     * 
     * @param _failureReport The failureReport to set.
     * @uml.property name="_failureReport"
     */
    public void set_failureReport(boolean report)
    {
        this.failureReport = report;
    }

    /**
     * Getter of the property <tt>_successReport</tt>
     * 
     * @return Returns the successReport.
     * @uml.property name="_successReport"
     */
    public boolean is_successReport()
    {
        return successReport;
    }

    /**
     * Setter of the property <tt>_successReport</tt>
     * 
     * @param _successReport The successReport to set.
     * @uml.property name="_successReport"
     */
    public void set_successReport(boolean report)
    {
        successReport = report;
    }

    /**
     * Getter of the property <tt>_message</tt>
     * 
     * @return Returns the _message.
     * @uml.property name="_message"
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
     * Setter of the property <tt>_connectoin</tt>
     * 
     * @param _connectoin The _connection to set.
     * @uml.property name="_connectoin"
     */
    public void setconnection(msrp.Connection connection)
    {
        this.connection = connection;
    }

    /**
     * @param reportMechanism the reportMechanism to set
     */
    public void setReportMechanism(ReportMechanism reportMechanism)
    {
        this.reportMechanism = reportMechanism;
    }

    public void addMSRPSessionListener(MSRPSessionListener object)
    {
        // TODO add the needed listeners from the object
        if (object != null && object instanceof MSRPSessionListener)
        {
            msrpSessionListener = object;
            logger.trace("MSRP Session Listener added to Session: "
                + getURI().toString());
            return;
        }
        logger.error("MSRP Session Listener called but not added to Session: "
            + getURI().toString() + " because it didn't matched the criteria");
    }

    public String toString()
    {
        return getURI().toString();
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
     * Returns and removes the first message of the top of the sendQueue
     * array.
     * 
     * @return the first message to be sent out of the sendQueue array
     */
    public Message getMessageToSend()
    {
        // returns the first message to be sent out of the sendQueue array
        // list
        if (sendQueue.isEmpty())
            return null; // TODO ?! FIXME?! also delete from the active sessions
        Message messageToReturn = sendQueue.get(0);
        sendQueue.remove(messageToReturn);
        return messageToReturn;
    }

    /*
     * TODO: remove these lines
     * 
     * @param tls says if this is a secure connection or not.
     * 
     * @param failureReport sets the failure-failureReport value
     * 
     * @param isRelay says if this sessions uses isRelay or not
     * 
     * @return Session the newly created session with the failureport altered
     * 
     * public Session(boolean tls, boolean failureReport, boolean isRelay) {
     * super(); }
     * 
     * /**
     * 
     * @param tls tells if this session goes through a seucure connection or not
     * 
     * @param successReport sets the value of the success-Report field
     * 
     * @param failureReport sets the failure-failureReport value
     * 
     * @param isRelay does this session uses isRelay
     * 
     * @return a newly created session
     * 
     * public Session(boolean tls, boolean successReport, boolean failureReport,
     * boolean isRelay) { super(); }
     */

    /**
     * Creates an MessageAbortedEvent and fires it
     * 
     * @param message the msrp message that was aborted
     * @param reason the reason
     * @param extraReasonInfo the extra information about the reason if any is
     *            present (it can be transported on the body of a REPORT
     *            request)
     * @param transaction the transaction associated with the abort event
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
     * This function is only used so that we have a central point to eventually
     * do any cleanup before the real callback. (for instance with the Stack
     * itself, at this moment nothing is being done though).
     * 
     * trigger for the registered MSRPSessionListener callback abortedMessage
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
     * @return the reportMechanism associated with this session
     */
    public ReportMechanism getReportMechanism()
    {
        return reportMechanism;
    }

    /**
     * This method releases all of the resources associated with this session.
     * It could eventually, but not necessarily, close connections conforming to
     * the Connection Model on RFC 4975.
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

    /*
     * TODO: remove these lines
     * 
     * @param tls says if this is a secure connection or not.
     * 
     * @param failureReport sets the failure-failureReport value
     * 
     * @param isRelay says if this sessions uses isRelay or not
     * 
     * @return Session the newly created session with the failureport altered
     * 
     * public Session(boolean tls, boolean failureReport, boolean isRelay) {
     * super(); }
     * 
     * /**
     * 
     * @param tls tells if this session goes through a seucure connection or not
     * 
     * @param successReport sets the value of the success-Report field
     * 
     * @param failureReport sets the failure-failureReport value
     * 
     * @param isRelay does this session uses isRelay
     * 
     * @return a newly created session
     * 
     * public Session(boolean tls, boolean successReport, boolean failureReport,
     * boolean isRelay) { super(); }
     */

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

    // public void addUriToIdentify(URI uri)
    // {
    // TODO add the uri to a list in order to verify the authenticity of the
    // sender
    /*
     * if (this.connection instanceof Connections) ((Connections)
     * Connections.getInstance()).addUriToIdentify(uri,this);
     */
    // }
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
     * calling the callback
     */
    /**
     * This function is only used so that we have a central point to eventually
     * do any cleanup before the real callback. (for instance with the Stack
     * itself, at this moment nothing is being done though).
     * 
     * trigger for the registered MSRPSessionListener callback.
     * 
     * @see MSRPSessionListener
     * @param failureReport the transaction associated with the failureReport request
     */
    protected void triggerReceivedReport(Transaction report)
    {
        logger.trace("Called the triggerReceivedReport hook");
        msrpSessionListener.receivedReport(this, report);
    }

    /**
     * This function is only used so that we have a central point to eventually
     * do any cleanup before the real callback. (for instance with the Stack
     * itself, at this moment nothing is being done though).
     * 
     * trigger for the registered MSRPSessionListener callback.
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
     * This function is only used so that we have a central point to eventually
     * do any cleanup before the real callback. (for instance with the Stack
     * itself, at this moment nothing is being done though).
     * 
     * trigger for the registered MSRPSessionListener callback
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
     * This function is only used so that we have a central point to eventually
     * do any cleanup before the real callback. (for instance with the Stack
     * itself, at this moment nothing is being done though).
     * 
     * trigger for the registered MSRPSessionListener callback updateSendStatus
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
     * Adds a message to the sent message list the message is stored because of
     * expected subsequent REPORT requests on this message
     * 
     * @param message the message to add
     */
    protected void addSentOrSendingMessage(Message message)
    {
        messagesSentOrSending.put(message.getMessageID(), message);

    }

    /**
     * Method used to delete a message that stopped being received from the
     * being received queue of the Session NOTE: currently this method is only
     * called for IncomingMessage objects
     * 
     * @param incomingMessage the message to be removed
     */
    protected void delMessageToReceive(IncomingMessage incomingMessage)
    {
        if (messagesReceive.remove(incomingMessage.getMessageID()) == null)
        {
            /*
             * log the fact that the message wasn't found when it should have
             * been!! TODO: log it!
             */
        }

    }

    /*
     * @param connection the connection to add adds the connection to the
     *            session and to the MSRPStack
    private void setConnection(Connection conn)
    {
        if (conn == null)
            logger
                .error("Error, tried to add a null connection to the session!");

        MSRPStack.getInstance().delConnection(connection);
        connection = conn;
        MSRPStack.getInstance().addConnection(connection);
    }
     */

	/**
	 * @return the localAddress
	 */
	public InetAddress getAddress() {
		return localAddress;
	}

	/*
	 * Convenience method (that probably will disappear when the message id
	 * generation is corrected) that searches for the given messageID on the
	 * queue of messages to send
	 * 
	 * @param messageID
	 *            String representing the messageID to search for
	 * @return true if it exists on the queue or false otherwise
    private boolean existsInMessagesToSend(String messageID)
    {
        for (Message message : sendQueue)
        {
            if (message.getMessageID().equals(messageID))
                return true;
        }
        return false;
    }
	 */

	public URI getURI() {
		return uri;
	}

	protected Connection getConnection() {
		return connection;
	}

    /*
     * public Message sendMessage(String contentType, byte[] byteContent) throws
     * Exception { try { Message messageToSend = new
     * Message(this,contentType,byteContent);
     * 
     * sendQueue.add(messageToSend); return messageToSend; } catch
     * (Exception e) { IOInterface.debugln(e.getMessage()); throw e; } }
     * 
     * 
     * public Message sendMessage(String contentType, Stream stream) throws
     * Exception { try { Message messageToSend = new
     * Message(this,contentType,stream);
     * 
     * sendQueue.add(messageToSend); return messageToSend; } catch
     * (Exception e) { IOInterface.debugln(e.getMessage()); throw e; } }
     */

}
