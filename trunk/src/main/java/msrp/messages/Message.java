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
package msrp.messages;

import java.util.ArrayList;

import msrp.*;
import msrp.Transaction.TransactionType;
import msrp.exceptions.*;
import msrp.utils.TextUtils;

import org.slf4j.*;

/**
 * Class that represents a generic MSRP message.
 * 
 * @author João André Pereira Antunes
 */
public abstract class Message
{
    /**
     * The logger associated with this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Message.class);

    /**
     * Message is incoming.
     */
    public static final int IN = 1;

    /**
     * Message is outgoing.
     */
    public static final int OUT = 2;

    public static final int UNINTIALIZED = -2;

    public static final int UNKNWON = -1;

    public static final String YES = "yes";

    public static final String NO = "no";

    public static final String PARTIAL = "partial";

    /**
     * Variable that contains the size of this message as indicated on the
     * byte-range header field
     */
    public long size = UNINTIALIZED;

    /**
     * @uml.property name="failureReport"
     */
    private String failureReport = YES;

    /**
     * Field used to conserve the abortion state of the message
     */
    protected boolean aborted = false;

    /**
     * The field that contains the data associated with this message (it allows
     * abstraction on the actual container of the data)
     */
    protected DataContainer dataContainer;

    /**
     * a field that contains the number of bytes sent on the last call made to
     * the shouldTriggerSentHook in Report Mechanism status
     * 
     * @see ReportMechanism#shouldTriggerSentHook(Message, Session)
     *      ReportMechanism.shouldTriggerSentHook(..)
     */
    public long lastCallSentData = 0;

    /**
     * a field that contains the number of bytes sent on the last call made to
     * the shouldGenerateReport in Report Mechanism status
     * 
     * @see ReportMechanism#shouldGenerateReport(Message, long)
     *      ReportMechanism.shouldGenerateReport(Message, long)
     */
    public long lastCallReportCount = 0;

    /**
     * this field points to the report mechanism associated with this message
     * the report mechanism basicly is used to decide upon the granularity of
     * the success reports
     */
    public ReportMechanism reportMechanism;

    /**
     * Field to be used by the prioritizer.
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

    /* Constructors: */

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

    /**
     * This field keeps a reference to the last SEND transaction associated with
     * this message
     */
    protected Transaction lastSendTransaction = null;

    public Message(Session session, String contentType, byte[] data,
        ReportMechanism reportMechanism)
        throws Exception
    {
        this(session, contentType, data);
        constructorAssociateReport(reportMechanism);
    }

    /* End of wrappers for report mechanism */

    /**
     * @param session the session associated with the message
     * @param contentType the content type associated withe this byteArarray
     * @param byteArray the content of the message to be sent eventually
     * @param reportMechanism the report mechanism to be used for this message
     * @return the newly created message
     * @throws IllegalUseException if this message content is too big, as
     *             defined on MSRPStack, to be sent with a memory content
     *             message
     * @see MSRPStack#setShortMessageBytes(int)
     * 
     */
    public Message(Session session, String contentType, byte[] data)
        throws IllegalUseException
    {

        if (data.length > MSRPStack.getShortMessageBytes())
        {
            // TODO create new exception for this
            throw new IllegalUseException(
                "Error! message data too big, use file source or stream constructors");
        }
        this.session = session;
        dataContainer = new MemoryDataContainer(data);
        size = data.length;
		messageId = MSRPStack.getInstance().generateMessageID(session);
        this.session.addMessageToSend(this);
        constructorAssociateReport(reportMechanism);
        this.contentType = contentType;

    }

    /**
     * Internal constructor used by the derived classes
     */
    protected Message()
    {
    }

    /**
     * The file transfer direction.
     * 
     * @return returns the direction of the file transfer : IN or OUT.
     */
    public abstract int getDirection();

    /**
     * This method must be used by the acceptHook on the listener in order to
     * allow the message to be received
     * 
     * @param dataContainer the dataContainer to set
     */
    public void setDataContainer(DataContainer dataContainer)
    {
        this.dataContainer = dataContainer;
        logger.trace("Altered the data container of Message: " + messageId
            + " to:" + dataContainer.getClass().getCanonicalName());
    }

    /**
     * @return the reportMechanism
     */
    public ReportMechanism getReportMechanism()
    {
        return reportMechanism;
    }

    /**
     * MUST be called after a session is associated convenience method called
     * internally by the constructors of this class to associate the given
     * reportMechanism or the session's default to the newly created message
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
     * Method that is called by the OutgoingMessages when their isComplete() is
     * called
     * 
     * @see #isComplete()
     * @return true if the message is completely sent
     */
    protected boolean outgoingIsComplete(long sentBytes)
    {
        boolean toReturn;
        if (sentBytes == size)
            toReturn = true;
        else
            toReturn = false;

        logger.trace("Called isComplete, sent bytes: " + sentBytes
            + " message size: " + size + " going to return: " + toReturn);
        return toReturn;

    }

    /**
     * This method interrupts all of the existing and interruptible SEND request
     * transactions associated with this message that are on the transactions to
     * be sent queue, and gets this message back on top of the messages to send
     * queue on the respective session.
     * 
     * This method is meant to be called internally by the prioritizer.
     * 
     * @throws InternalErrorException if this method, that is called internally,
     *             was called with the message in an invalid state
     */
    public void pause() throws InternalErrorException
    {
        /*
         * Sanity checks:
         */
        if (this.isComplete())
            throw new InternalErrorException("The pause method"
                + " was called on a complete message!");
        if (session == null)
            throw new InternalErrorException(
                "The session on this message is null"
                    + " and the pause method was called upon it");
        TransactionManager transactionManager = session.getTransactionManager();
        if (transactionManager == null)
            throw new InternalErrorException("The transaction manager "
                + "associated with this message is null"
                + " and the pause method was called upon it");
        /*
         * End of sanity checks.
         */

        ArrayList<Transaction> transactionsToSend =
            transactionManager.getTransactionsToSend();
        for (Transaction transaction : transactionsToSend)
        {
            if (transaction.transactionType.equals(TransactionType.SEND)
                && transaction.getMessage().equals(this)
                && transaction.isInterruptible())
                try
                {
                    transaction.interrupt();
                }
                catch (IllegalUseException e)
                {
                    throw new InternalErrorException(e);
                }
        }

        session.addMessageOnTop(this);

    }

    /**
     * TODO WORKINPROGRESS Method to be used by the MessagePrioritizer by now
     * the priority field of the messages is irrelevant as the messages work in
     * a FIFO for each session (TODO not sure if a prioritizer within messages
     * in same session will serve any practical purpose)
     * 
     * @return the priority of this message
     */
    public short getPriority()
    {
        return priority;
    }

    /**
     * TODO WORKINPROGRESS Method to be used by the MessagePrioritizer by now
     * the priority field of the messages is irrelevant as the messages work in
     * a FIFO for each session (TODO not shure if a prioritizer within messages
     * in same session will serve any practical purpose)
     * 
     * @param priority the priority to set
     */
    public void setPriority(short priority)
    {
        this.priority = priority;
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
        if (failureReport.equalsIgnoreCase("yes")
            || failureReport.equalsIgnoreCase("no")
            || failureReport.equalsIgnoreCase("partial"))
        {
            this.failureReport = failureReport;
            return;
        }
        throw new IllegalUseException("The failure report must be one of: "
            + "partial yes no");
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
     * Method used to check if this message object still has unused content
     * 
     * @return true if this message still has some data to retrieve
     */
    public boolean hasData()
    {
        return dataContainer.hasDataToRead();
    }

    /**
     * 
     * Method that fills the given array with DATA bytes starting from offset
     * and stopping at the array limit or end of data and returns the number of
     * bytes filled
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
            // TODO log it
            e.printStackTrace();
            throw new ImplementationException(e);
        }
        catch (Exception e)
        {
            // TODO log it
            e.printStackTrace();
            throw new InternalErrorException(e);
        }
    }

    /**
     * Function used to retrieve the message content byte by byte
     * 
     * @deprecated due to performance issues please use
     *             {@link #get(byte[], int)}
     * @return a byte of this message's content
     */
    public byte get()
    {
        try
        {
            return dataContainer.get();
        }
        catch (Exception e)
        {
            // TODO log it
            e.printStackTrace();
        }
        return -1;

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
     * @uml.property name="_contentManager"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_message:msrp.ContentManager"
     */

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

    public String getContent() {
    	if (this.getDirection() == OUT || (this.getDirection() == IN && this.isComplete())) {
    		if (wrappedMessage == null) {
    			return getRawContent();
    		} else {
    			return wrappedMessage.getMessageContent();
    		}
    	}
    	return null;
    }

    public String getRawContent() {
    	if (this.getDirection() == OUT || (this.getDirection() == IN && this.isComplete())) {
    		try {
				return new String(this.getDataContainer().get(0, this.getSize()).array(), TextUtils.utf8);
			} catch (Exception e) {
				logger.info("No raw content to retrieve", e);
			}
    	}
		return null;
	}

	/**
     * Getter of the property <tt>_failureReport</tt>
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
     * returns the message id of this string
     */
    @Override
    public String toString()
    {
        return messageId;
    }

    /**
     * 
     * @return the size, in bytes, of the message or -1 if this value is
     *         uninitialized or -2 if the total message bytes is unknown for
     *         this message. Note: even if the value is unknown at a first
     *         stage, if this is an incoming message, when the message is
     *         complete this method should report the actual size of the
     *         received message
     */
    public long getSize()
    {
        return size;
    }

    /**
     * @return the string representing the total number of bytes of this message
     *         or * if it's unknown
     */
    public String getStringTotalSize()
    {
        if (size == UNKNWON)
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
    public void setSession(Session _session)
    {
        this.session = _session;
    }

    /**
     * Getter of the property <tt>successReport</tt> that represents the
     * Success-Report field.
     * 
     * @return Returns the success report header field of this message. True
     *         represents "yes" and false "no"
     */
    public boolean getSuccessReport()
    {
        return successReport;
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
    public void setLastSendTransaction(Transaction lastSendTransaction)
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
        throws InternalErrorException,
        IllegalUseException;

    /**
     * Method called by the Transaction when it wants to notify a message that
     * it got aborted.
     * 
     * It is this method's responsibility to notify the listeners associated
     * with it's session and change it's internal state accordingly
     * 
     * TODO reflect about the possibility to eliminate all the data associated
     * with this message or not. Could be done with a variable associated with
     * the stack, in some cases it may be useful to keep the data. ATM it
     * disposes the DataContainer associated with it
     * 
     * @param transaction the transaction that is associated with the abort
     */
    public void gotAborted(Transaction transaction)
    {
        aborted = true;
        dataContainer.dispose();
        session.triggerAbortedMessage(session, (IncomingMessage) this, transaction);

    }

    /**
     * Method used to detect if the message was aborted or not
     * 
     * @return true if the message was previously aborted (received the #
     *         continuation flag, called the gotAborted() method) or false
     *         otherwise
     * @see #gotAborted()
     */
    public boolean wasAborted()
    {
        return aborted;
    }

    /**
     * This creates and fires a MessageAbortEvent
     * 
     * @param reason the reason for the Abort
     * @param extraReasonInfo eventually the String that was carried in the
     *            REPORT request that triggered this event, or null if none
     *            exists or is being considered
     * @see MessageAbortEvent
     */
    public void fireMessageAbortedEvent(int reason, String extraReasonInfo, Transaction transaction)
    {
        session.fireMessageAbortedEvent(this, reason, extraReasonInfo, transaction);

    }
}
