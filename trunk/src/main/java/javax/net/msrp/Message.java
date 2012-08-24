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

import java.nio.BufferOverflowException;

import javax.net.msrp.events.MessageAbortedEvent;
import javax.net.msrp.exceptions.*;
import javax.net.msrp.utils.TextUtils;


import org.slf4j.*;

/**
 * Class representing a generic MSRP message.
 * 
 * @author Jo�o Andr� Pereira Antunes
 */
public abstract class Message
{
    /** The logger associated with this class */
    private static final Logger logger = LoggerFactory.getLogger(Message.class);

    /** Message is incoming. */
    public static final int IN = 1;

    /** Message is outgoing. */
    public static final int OUT = 2;

    public static final int UNINTIALIZED = -2;

    public static final int UNKNOWN = -1;

    public static final String YES = "yes";
    public static final String NO = "no";
    public static final String PARTIAL = "partial";

    /**
     * Size of this message as indicated on the byte-range header field
     */
    public long size = UNINTIALIZED;

    /**
     * @uml.property name="failureReport"
     */
    private String failureReport = YES;

    /**
     * Abort-state of message
     */
    protected boolean aborted = false;

    /**
     * The data associated with this message (allows for abstraction on the
     * actual container of the data)
     */
    protected DataContainer dataContainer;

    /**
     * Number of bytes sent on the last call made to
     * shouldTriggerSentHook in ReportMechanism status
     * 
     * @see ReportMechanism#shouldTriggerSentHook(Message, Session, long)
     */
    public long lastCallSentData = 0;

    /**
     * Number of bytes sent on the last call made to
     * shouldGenerateReport in ReportMechanism status
     * 
     * @see ReportMechanism#shouldGenerateReport(Message, long, long)
     */
    public long lastCallReportCount = 0;

    /**
     * The report mechanism associated with this message.
     * Basically used to decide upon the granularity of success reports.
     */
    public ReportMechanism reportMechanism;

    /**
     * To be used by ConnectionPrioritizer.
     * 
     * Default value of 0 means no special priority
     * 
     * As an advice use the range -20 to 20 from higher priority to lowest (as
     * in UNIX processes)
     */
    protected short priority = 0;

    /**
     * @uml.property name="_contentType"
     */
    protected String contentType = null;

    /**
     * @uml.property name="messageId"
     */
    protected String messageId;

    /**
     * @uml.property name="_session"
     */
    protected Session session = null;

    /**
     * @uml.property name="successReport"
     */
    private boolean successReport = false;

    protected WrappedMessage wrappedMessage = null;

    protected String nickname = null;

    /**
     * This field keeps a reference to the last SEND transaction associated with
     * this message
     */
    protected Transaction lastSendTransaction = null;

    protected Message(Session session, String contentType, byte[] data)
    {
        this(session, contentType, data, null);
    }

    /** Create new MSRP message and queue in session for sending.
     * @param session the session associated with the message
     * @param contentType the content type associated with this byteArarray
     * @param data the content of the message to be sent
     * @param reportMechanism the report mechanism to be used for this message
     * @return the newly created message
     * @throws RuntimeException if message content is too big to send in memory,
     * 					as defined on Stack (cause {@code BufferOverflowException})
     * @see Stack#setShortMessageBytes(int)
     */
    protected Message(Session session, String contentType, byte[] data,
            ReportMechanism reportMechanism)
    {
        if (data.length > Stack.getShortMessageBytes())
        {
            throw new RuntimeException("Session[" + session +
            			"] data too big, use file source or stream constructors",
            			new BufferOverflowException());
        }
        this.session = session;
        this.contentType = contentType;
		messageId = Stack.generateMessageID();
        dataContainer = new MemoryDataContainer(data);
        size = data.length;
        constructorAssociateReport(reportMechanism);
        this.session.addMessageToSend(this);
    }

    protected Message(Session session, String nickname) {
        this.session = session;
        this.nickname = nickname;
        dataContainer = new MemoryDataContainer(0);
        size = 0;
        this.session.addMessageToSend(this);
    }

    /**
     * Internal constructor used by the derived classes
     */
    protected Message()
    {
    }

    /**
     * @return the direction of the transfer: {@link #IN} or {@link #OUT}.
     */
    public abstract int getDirection();

    /**
     * This method must be used by the listener acceptHook() to
     * enable the message to be received.
     * 
     * @param dataContainer the dataContainer to set
     */
    public void setDataContainer(DataContainer dataContainer)
    {
        this.dataContainer = dataContainer;
        if (logger.isTraceEnabled()) {
        	String className = dataContainer == null ?
        				"null" : dataContainer.getClass().getCanonicalName();
            logger.trace("Altered the data container of Message: " + messageId
                    + " to:" + className);
        }
    }

    /**
     * @return the report mechanism
     */
    public ReportMechanism getReportMechanism()
    {
        return reportMechanism;
    }

    /**
     * Convenience method called internally by the constructors of this class
     * to associate the given reportMechanism (or session's default) to the
     * newly created message.
     * <p>
     * MUST be called after a session is associated.
     * 
     * @param reportMechanism
     */
    protected void constructorAssociateReport(ReportMechanism reportMechanism)
    {
        if (reportMechanism == null)
        {
            this.reportMechanism = this.session.getReportMechanism();
        }
        else
        {
            this.reportMechanism = reportMechanism;
        }
    }

    /**
     * Called by OutgoingMessages when their isComplete() is
     * called
     * 
     * @see #isComplete()
     * @return true if message is completely sent
     */
    protected boolean outgoingIsComplete(long sentBytes)
    {
    	if (logger.isTraceEnabled())
            logger.trace(String.format(
            		"Called isComplete(), message(size[%d], sent[%d]) will return[%b]",
            		sentBytes, size, sentBytes == size));
        return sentBytes == size;
    }

    /**
     * Interrupts all of the existing and interruptible SEND request
     * transactions associated with this message that are on the transactions to
     * be sent queue, and gets this message back on top of the messages to send
     * queue of the respective session.
     * 
     * This method is meant to be called internally by the ConnectionPrioritizer.
     * 
     * @throws InternalErrorException if this method, that is called internally,
     *             was called with the message in an invalid state
     */
    protected void pause() throws InternalErrorException
    {
        if (this.isComplete())			/* Sanity checks */
            throw new InternalErrorException("pause()" +
                " was called on a complete message!");
        if (session == null)
            throw new InternalErrorException(
				                "pause() called on message with no session.");
        TransactionManager transactionManager = session.getTransactionManager();
        if (transactionManager == null)
            throw new InternalErrorException(
	                "pause() called on message with no transaction manager.");
        try
        {
        	transactionManager.interruptMessage(this);
        }
        catch (IllegalUseException e)
        {
            throw new InternalErrorException(e);
        }
        session.addMessageOnTop(this);
    }

    /**
     * TODO WORKINPROGRESS to be used by ConnectionPrioritizer. Currently
     * the priority field of the messages is irrelevant as the messages work in
     * a FIFO for each session (NB: not sure if a prioritizer within messages
     * of same session will serve any practical purpose)
     * 
     * @return the priority of this message
     */
    protected short getPriority()
    {
        return priority;
    }

    /**
     * TODO WORKINPROGRESS to be used by the ConnectinPrioritizer.Currently,
     * the priority field of the messages is irrelevant as the messages work in
     * a FIFO for each session (NB: not sure if a prioritizer within messages
     * in same session will serve any practical purpose)
     * 
     * @param priority the priority to set
     */
    protected void setPriority(short priority)
    {
        this.priority = priority;
    }

    /**
     * Has this message object still unused content?
     * 
     * @return true if this message still has some data to retrieve
     */
    public boolean hasData()
    {
        return dataContainer.hasDataToRead();
    }

    /**
     * Fill the given array with DATA bytes, starting from offset
     * and stopping at the array limit or end of data.
     * Returns the number of bytes filled.
     * 
     * @param outData the byte array to fill
     * @param offset the offset index to start filling the outData
     * @return the number of bytes filled
     * @throws ImplementationException when there was something wrong with the
     *             written code
     * @throws InternalErrorException when there was an internal error that lead
     *             this operation to be an unsuccessful one
     */
    public int get(byte[] outData, int offset)
        throws ImplementationException,
        InternalErrorException
    {
        try
        {
            return dataContainer.get(outData, offset);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new ImplementationException(e);
        }
        catch (Exception e)
        {
            throw new InternalErrorException(e);
        }
    }

    /**
     * Handy method to retrieve the associated counter of this message
     * 
     * @return the counter associated with this message
     */
    public Counter getCounter()
    {
        return reportMechanism.getCounter(this);
    }

    /**
     * Getter of the property <tt>contentType</tt>
     * 
     * @return Returns the type.
     * @uml.property name="contentType"
     */
    public String getContentType()
    {
        return contentType;
    }

    /**
     * @return content of message, unwrapped, into string.
     */
    public String getContent() {
    	if (getDirection() == OUT || (getDirection() == IN && isComplete())) {
    		if (wrappedMessage == null)
    			return getRawContent();
    		else
    			return wrappedMessage.getMessageContent();
    	}
    	return null;
    }

    /**
     * @return content of message, not unwrapped, into string.
     */
    public String getRawContent() {
    	if (getDirection() == OUT || (getDirection() == IN && isComplete())) {
    		try {
				return new String(getDataContainer().get(0, getSize()).array(), TextUtils.utf8);
			} catch (Exception e) {
				logger.info("No raw content to retrieve", e);
			}
    	}
		return null;
	}

    /**
     * Method used to set the success report field associated with this message.
     * 
     * @param successReport true to set it to "yes" false to set it to "no"
     */
    public void setSuccessReport(boolean successReport)
    {
        this.successReport = successReport;
    }

    /**
     * Get header content of successReport
     * 
     * @return Returns success report header field of this message. True
     *         represents "yes" and false "no"
     */
    public boolean wantSuccessReport()
    {
        return successReport;
    }

    /**
     * method used to set the failure report string
     * 
     * @param failureReport String representing the failure report field, it
     *            must be one of: yes no partial. case insensitive
     * @throws IllegalUseException if the argument wasn't valid
     */
    public void setFailureReport(String failureReport)
        throws IllegalUseException
    {
        if (failureReport.equalsIgnoreCase(YES) ||
            failureReport.equalsIgnoreCase(NO) ||
            failureReport.equalsIgnoreCase(PARTIAL))
            this.failureReport = failureReport.toLowerCase();
        else
        	throw new IllegalUseException(
        			"Failure report must be one of: 'partial', 'yes' or 'no'.");
    }

	/**
     * Get header content of failureReport.
     * 
     * @return Returns the report.
     * @uml.property name="_failureReport"
     */
    public String getFailureReport()
    {
        return failureReport;
    }

    /**
     * Getter of the property <tt>_messageID</tt>
     * 
     * @return Returns the _messageid.
     * @uml.property name="_messageID"
     */
    public String getMessageID()
    {
        return messageId;
    }

	/**
	 * @return the nickname
	 */
	public String getNickname() {
		return nickname;
	}

	/**
	 * @param nickname the nickname to set
	 */
	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	/**
     * returns the message id of this string
     */
    @Override
    public String toString()
    {
        return messageId;
    }

    /**
     * Note: even if the value is unknown at a first
     *       stage, if this is an incoming message, when the message is
     *       complete this method should report the actual size of the
     *       received message
     * 
     * @return the size (bytes) of the message, -1 if this value is
     *         uninitialized, -2 if the total size is unknown for this message.
     */
    public long getSize()
    {
        return size;
    }

    /**
     * @return the string representing the total number of bytes of this message
     *         or '*' if it's unknown
     */
    public String getStringTotalSize()
    {
        if (size == UNKNOWN)
            return "*";
        return Long.toString(size);
    }

    /**
     * Getter of the property <tt>_session</tt>
     * 
     * @return Returns the _session.
     * @uml.property name="_session"
     */
    public Session getSession()
    {
        return session;
    }

    /**
     * Setter of the property <tt>_session</tt>
     * 
     * @param _session The _session to set.
     * @uml.property name="_session"
     */
    protected void setSession(Session _session)
    {
        this.session = _session;
    }

    /**
     * Retrieves the associated data container of the message
     * 
     * @return DataContainer associated with this message
     */
    public DataContainer getDataContainer()
    {
        return dataContainer;
    }

    /**
     * @param lastSendTransaction the lastSendTransaction to set
     */
    protected void setLastSendTransaction(Transaction lastSendTransaction)
    {
        this.lastSendTransaction = lastSendTransaction;
    }

    /**
     * @return the lastSendTransaction
     */
    public Transaction getLastSendTransaction()
    {
        return lastSendTransaction;
    }

    /**
     * Method that states if this message is completely sent or received,
     * depending on the type of message
     * 
     * @return true if the message is completely sent or received, false
     *         otherwise
     */
    public abstract boolean isComplete();

    /** Message is complete, see if correct.
     * Also the cue for any (un-)wrapping.
     * @throws Exception
     */
    public abstract void validate() throws Exception;

    /**
     * Aborts the Outgoing or incoming message note, both arguments are
     * irrelevant if this is an Outgoing message (as it's aborted with the #
     * continuation flag and is no way to transmit the reason)
     * 
     * @param reason the Reason for the abort, only important if this is an
     *            Incoming message
     * @param reasonExtraInfo the extra info about the abort, or null if it
     *            doesn't exist, this will be sent on the REPORT if we are
     *            aborting an Incoming message
     * @throws InternalErrorException if by any Internal error, the message
     *             couldn't be aborted
     * @throws IllegalUseException if any of the arguments is invalid
     */
    public abstract void abort(int reason, String reasonExtraInfo)
        throws InternalErrorException, IllegalUseException;

    /**
     * Called by Transaction when it wants to notify a message that it got aborted.
     * <p>
     * It is this method's responsibility to notify the listeners associated
     * with it's session and change it's internal state accordingly
     * 
     * @param transaction the transaction associated with the abort
     */
    /*
     * TODO reflect about the possibility to eliminate all the data associated
     * with this message or not. Could be done with a variable associated with
     * the stack, in some cases it may be useful to keep the data. ATM it
     * disposes the DataContainer associated with it
     */
    public void gotAborted(Transaction transaction)
    {
        aborted = true;
        if (dataContainer != null)
        	dataContainer.dispose();
        session.fireMessageAbortedEvent(this, MessageAbortedEvent.CONTINUATIONFLAG,
        		null, transaction);
    }

    /**
     * Was message aborted?
     * 
     * @return true if the message was previously aborted (received the #
     *         continuation flag, called the gotAborted() method)
     * @see #gotAborted(Transaction)
     */
    public boolean wasAborted()
    {
        return aborted;
    }

    /**
     * This creates and fires a MessageAbortEvent
     * 
     * @param reason the reason for the Abort
     * @param extraReasonInfo the String that was carried in the
     *            REPORT request triggering this event (null if empty).
     * @see MessageAbortedEvent
     */
    public void fireMessageAbortedEvent(int reason, String extraReasonInfo, Transaction transaction)
    {
        session.fireMessageAbortedEvent(this, reason, extraReasonInfo, transaction);
    }

    /**
     * Let the message know it has served its' purpose.
     * It will no longer be used and can be garbage collected. Free any resources.
     */
    public void discard()
    {
        if (dataContainer != null)
        	dataContainer.dispose();
    }
}