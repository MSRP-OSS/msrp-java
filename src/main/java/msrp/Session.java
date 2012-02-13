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
 * This class represents a MSRP Session.
 * 
 * This class contains a list of MSRP Messages with whom it's currently
 * associated with. It is also designed to be a mandatory point of contact with
 * the MSRPSessionListener class. All of the callbacks done through this class's
 * trigger methods
 * 
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

    // TODO alter the constructors to use the this(argument) call

    /*
     * TODO: remove these lines
     * 
     * @param tls says if this is a secure connection or not.
     * 
     * @param failureReport sets the failure-report value
     * 
     * @param relays says if this sessions uses relays or not
     * 
     * @return Session the newly created session with the failureport altered
     * 
     * public Session(boolean tls, boolean failureReport, boolean relays) {
     * super(); }
     * 
     * /**
     * 
     * @param tls tells if this session goes through a seucure connection or not
     * 
     * @param successReport sets the value of the success-Report field
     * 
     * @param failureReport sets the failure-report value
     * 
     * @param relays does this session uses relays
     * 
     * @return a newly created session
     * 
     * public Session(boolean tls, boolean successReport, boolean failureReport,
     * boolean relays) { super(); }
     */

    private MSRPStack stackInstance = MSRPStack.getInstance();

    private ArrayList<URI> uris = new ArrayList<URI>();

    private TransactionManager transactionManager;

    private InetAddress address;

    /**
     * @desc the connection associated with this session
     * @uml.property name="_connection"
     * @uml.associationEnd inverse="_session:msrp.Connection"
     */
    private msrp.Connection connection = null;

    /**
     * @uml.property name="_failureReport"
     */
    private boolean report = true;

    /**
     * @uml.property name="_successReport"
     */
    private boolean report1;

    /**
     * This field contains the queue of messages to be sent
     * 
     * @uml.property name="messagesToSend"
     */
    private ArrayList<Message> messagesToSend = new ArrayList<Message>();

    /**
     * @uml.property name="_messagesSent" stores the sent/being sended messages
     *               according to the Success-Report field
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
    private boolean relays;

    /**
     * @uml.property name="_URI" the URI that identifies this session
     */
    private URI uri = null;

    /**
     * this field points to the report mechanism associated with this session
     * the report mechanism basically is used to decide upon the granularity of
     * the success reports
     * 
     * it will use the DefaultReportMechanism
     * 
     * @see DefaultReportMechanism
     */
    private ReportMechanism reportMechanism =
        DefaultReportMechanism.getInstance();

    /**
     * Constructor used to define the report mechanism for all messages (wrapper
     * for the tls relays InetAddress constructor)
     * 
     * @throws IOException
     * @throws URISyntaxException
     */
    public Session(boolean tls, boolean relays, InetAddress address,
        ReportMechanism reportMechanism)
        throws InternalErrorException
    {
        this(tls, relays, address);
        this.reportMechanism = reportMechanism;
        logger.trace("MSRP Session created with custom report mechanism, tls: "
            + tls + " relays: " + relays + " InetAddress:" + address);
    }

    /**
     * Constructor used to define the report mechanism for all messages (wrapper
     * for the tls, relays, destinationURI, InetAddress constructor)
     * 
     * @throws Exception
     */
    public Session(boolean tls, boolean relays, URI destinationURI,
        InetAddress address, ReportMechanism reportMechanism)
        throws Exception
    {
        this(tls, relays, destinationURI, address);
        this.reportMechanism = reportMechanism;
        logger.trace("MSRP Session created with custom report mechanism, tls: "
            + tls + " relays: " + relays + " destinationURI: " + destinationURI
            + " InetAddress:" + address);
    }

    /**
     * Creates a new session with the address given as the _local_ endpoint the
     * associated connection will be an active one
     * 
     * @param tls an boolean telling if this is a secure connection or not
     * @param relays is it a session that uses relays?
     * @param address the configured address to use as the local endpoint
     * @throws InternalErrorException if any error ocurred. More info about the
     *             error can be getted by printing the stacktrace
     */
    public Session(boolean tls, boolean relays, InetAddress address)
        throws InternalErrorException
    {
        try
        {
            this.address = address;
            connection = new Connection(address);
            set_relays(relays);
            // Generates the new URI and adds it to the list of URIs of the
            // connection
            if (connection != null)
            {
                uri = connection.generateNewURI();

                stackInstance.addConnection(uri, connection);
            }
        }
        catch (Exception e)
        {
            // let's wrap every exception in an InternalError one
            throw new InternalErrorException(e);
        }
        logger.trace("MSRP Session created, tls: " + tls + " relays: " + relays
            + " InetAddress:" + address);

    }

    /**
     * Creates a new session with the address given as the _local_ endpoint the
     * associated connection will be a passive one at the moment.
     * 
     * @param tls an boolean telling if this is a secure connection or not
     * @param relays is it a session that uses relays?
     * @param address the configured address to use as the local endpoint
     * @throws InternalErrorException if any kind of internal error ocurred,
     *             usually there was an error with the socket or the generation
     *             of the URI, more information can be obtained by printing the
     *             stacktrace
     * 
     */
    public Session(boolean tls, boolean relays, URI destinationURI,
        InetAddress address)
        throws InternalErrorException
    {
        this.address = address;
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
        stackInstance.addConnection(uri, connection);
        set_relays(relays);
        uris.add(destinationURI);
        logger.trace("MSRP Session created, tls: " + tls + " relays: " + relays
            + " destinationURI: " + destinationURI + " InetAddress:" + address);

    }

    /*
     * TODO: remove these lines
     * 
     * @param tls says if this is a secure connection or not.
     * 
     * @param failureReport sets the failure-report value
     * 
     * @param relays says if this sessions uses relays or not
     * 
     * @return Session the newly created session with the failureport altered
     * 
     * public Session(boolean tls, boolean failureReport, boolean relays) {
     * super(); }
     * 
     * /**
     * 
     * @param tls tells if this session goes through a seucure connection or not
     * 
     * @param successReport sets the value of the success-Report field
     * 
     * @param failureReport sets the failure-report value
     * 
     * @param relays does this session uses relays
     * 
     * @return a newly created session
     * 
     * public Session(boolean tls, boolean successReport, boolean failureReport,
     * boolean relays) { super(); }
     */

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
            this.uris.add(uri);
        }
        connection.addEndPoint(this.uris.get(this.uris.size() - 1), address);
        // stackInstance.addActiveSession(this);
        transactionManager = connection.getTransactionManager();
        transactionManager.addSession(this);
        transactionManager.initialize(this);

        logger.trace("Added toPath with URI[0]: " + uris.get(0).toString()
            + " URI array size:" + this.uris.size());

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
        return uris;
    }

    /**
     * Getter of the property <tt>_failureReport</tt>
     * 
     * @return Returns the report.
     * @uml.property name="_failureReport"
     */
    public boolean is_failureReport()
    {
        return report;
    }

    /**
     * Setter of the property <tt>_failureReport</tt>
     * 
     * @param _failureReport The report to set.
     * @uml.property name="_failureReport"
     */
    public void set_failureReport(boolean report)
    {
        this.report = report;
    }

    /**
     * Getter of the property <tt>_successReport</tt>
     * 
     * @return Returns the report1.
     * @uml.property name="_successReport"
     */
    public boolean is_successReport()
    {
        return report1;
    }

    /**
     * Setter of the property <tt>_successReport</tt>
     * 
     * @param _successReport The report1 to set.
     * @uml.property name="_successReport"
     */
    public void set_successReport(boolean report)
    {
        report1 = report;
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
    public boolean is_relays()
    {
        return relays;
    }

    /**
     * Setter of the property <tt>_relays</tt>
     * 
     * @param _relays The _relays to set.
     * @uml.property name="_relays"
     */
    public void set_relays(boolean relays)
    {
        this.relays = relays;
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
        messagesToSend.add(0, message);
    }

    /**
     * Adds the given message to the end of the message to send queue.
     * Kick off when queue is empty.
     * 
     * @param message the message to be added to the end of the message queue
     */
    public void addMessageToSend(Message message)
    {
        messagesToSend.add(message);
        triggerSending();
    }

	/**
	 * Have transactionManager send awaiting messages from session.
	 */
	private void triggerSending() {
		if (transactionManager != null) {
        	synchronized(getTransactionManager().getTransactionsToSend()) {
        		if (!transactionManager.hasDataToSend()) {
        			transactionManager.setMessageBeingSent(getMessageToSend());
        			transactionManager.generateTransactionsToSend();
        		}
        	}
        }
	}

    /**
     * @return true if this session has messages to send false otherwise
     */
    public boolean hasMessagesToSend()
    {
        return !messagesToSend.isEmpty();
    }

    /**
     * Returns and removes the first message of the top of the messagesToSend
     * array.
     * 
     * @return the first message to be sent out of the messagesToSend array
     */
    public Message getMessageToSend()
    {
        // returns the first message to be sent out of the messagesToSend array
        // list
        if (messagesToSend.isEmpty())
            return null; // TODO ?! FIXME?! also delete from the active sessions
        Message messageToReturn = messagesToSend.get(0);
        messagesToSend.remove(messageToReturn);
        return messageToReturn;
    }

    /*
     * TODO: remove these lines
     * 
     * @param tls says if this is a secure connection or not.
     * 
     * @param failureReport sets the failure-report value
     * 
     * @param relays says if this sessions uses relays or not
     * 
     * @return Session the newly created session with the failureport altered
     * 
     * public Session(boolean tls, boolean failureReport, boolean relays) {
     * super(); }
     * 
     * /**
     * 
     * @param tls tells if this session goes through a seucure connection or not
     * 
     * @param successReport sets the value of the success-Report field
     * 
     * @param failureReport sets the failure-report value
     * 
     * @param relays does this session uses relays
     * 
     * @return a newly created session
     * 
     * public Session(boolean tls, boolean successReport, boolean failureReport,
     * boolean relays) { super(); }
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
		uris = null;

		if (messagesToSend != null) {
			Iterator<Message> it = messagesToSend.iterator();
			while (it.hasNext()) {
				DataContainer buffer = it.next().getDataContainer();
				if (buffer != null)
					buffer.dispose();
			}
		messagesToSend = null;
		}

		if (transactionManager != null) {
			transactionManager.removeSession(this);
			transactionManager = null;
		}
		// FIXME: (msrp-31) allow connection reuse by sessions.
		if (connection != null) {
			connection.close();
			connection = null;
		}
		if (stackInstance != null) {
			stackInstance.removeActiveSession(this);
			stackInstance = null;
		}
    }

    /**
     * at this point this is used by the generation of the success report to
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
        messagesToSend.remove(message);

    }

    /*
     * TODO: remove these lines
     * 
     * @param tls says if this is a secure connection or not.
     * 
     * @param failureReport sets the failure-report value
     * 
     * @param relays says if this sessions uses relays or not
     * 
     * @return Session the newly created session with the failureport altered
     * 
     * public Session(boolean tls, boolean failureReport, boolean relays) {
     * super(); }
     * 
     * /**
     * 
     * @param tls tells if this session goes through a seucure connection or not
     * 
     * @param successReport sets the value of the success-Report field
     * 
     * @param failureReport sets the failure-report value
     * 
     * @param relays does this session uses relays
     * 
     * @return a newly created session
     * 
     * public Session(boolean tls, boolean successReport, boolean failureReport,
     * boolean relays) { super(); }
     */

    /**
     * FIXME This method shouldn't usually be used by the user, but it is
     * required due to the package structure.. related with Issue #27
     * 
     * @return the transactionManager
     */
    public TransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    /**
     * Method that should only be called by the transaction manager addSession
     * method
     * 
     * @param transactionManager the transactionManager to set
     */
    protected void setTransactionManager(TransactionManager transactionManager)
    {
        this.transactionManager = transactionManager;
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
     * @param report the transaction associated with the report request
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
	 * @return the address
	 */
	public InetAddress getAddress() {
		return address;
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
        for (Message message : messagesToSend)
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
     * messagesToSend.add(messageToSend); return messageToSend; } catch
     * (Exception e) { IOInterface.debugln(e.getMessage()); throw e; } }
     * 
     * 
     * public Message sendMessage(String contentType, Stream stream) throws
     * Exception { try { Message messageToSend = new
     * Message(this,contentType,stream);
     * 
     * messagesToSend.add(messageToSend); return messageToSend; } catch
     * (Exception e) { IOInterface.debugln(e.getMessage()); throw e; } }
     */

}
