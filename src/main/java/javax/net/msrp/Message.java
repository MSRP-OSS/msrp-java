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

import javax.net.msrp.events.MessageAbortedEvent;
import javax.net.msrp.exceptions.*;
import javax.net.msrp.utils.TextUtils;


import org.slf4j.*;

/**
 * Class representing a generic MSRP message.
 * 
 * @author João André Pereira Antunes
 */
public abstract class Message
{
    /** The logger associated with this class */
    private static final Logger logger = LoggerFactory.getLogger(Message.class);

    public static final int UNINTIALIZED = -2;

    public static final int UNKNOWN = -1;

    public static final String YES = "yes";
    public static final String NO = "no";
    public static final String PARTIAL = "partial";

	/** content-type of an isComposing message	*/
	public static final String IMCOMPOSE_TYPE = "application/im-iscomposing+xml";

    /**
     * Size of this message as indicated on the byte-range header field
     */
    public long size = UNINTIALIZED;

    /**
     * @uml.property name="successReport"
     */
    private boolean successReport = false;

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
     * Basically used to determine the granularity of success reports.
     */
    private ReportMechanism reportMechanism =
    		DefaultReportMechanism.getInstance();

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
     * @uml.property name="contentType"
     */
    protected String contentType = null;

    /**
     * @uml.property name="messageId"
     */
    protected String messageId;

    /**
     * @uml.property name="session"
     */
    protected Session session = null;

    protected WrappedMessage wrappedMessage = null;

    protected String nickname = null;

    /**
     * This field keeps a reference to the last SEND transaction associated with
     * this message
     */
    protected Transaction lastSendTransaction = null;

    /**
     * Constructor used by the derived classes
     */
    protected Message()
    {
    	;
    }

    /**
     * Construct by copying from existing message.
     * @param toCopy
     */
    protected Message(Message toCopy)
    {
    	this.successReport = toCopy.successReport;
    	this.failureReport = toCopy.failureReport;
    	this.aborted = toCopy.aborted;
    	this.dataContainer = toCopy.dataContainer;
    	this.lastCallSentData = toCopy.lastCallSentData;
    	this.lastCallReportCount = toCopy.lastCallReportCount;
    	this.reportMechanism = toCopy.reportMechanism;
    	this.priority = toCopy.priority;
    	this.contentType = toCopy.contentType;
    	this.messageId = toCopy.messageId;
    	this.session = toCopy.session;
    	this.wrappedMessage = toCopy.wrappedMessage;
    	this.nickname = toCopy.nickname;
    	this.lastSendTransaction = toCopy.lastSendTransaction;
    }

    /**
     * @return the direction this message is travelling:
     * 		{@link Direction#IN} or {@link Direction#OUT}.
     */
    public abstract Direction getDirection();

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
     * Convenience method to associate the given reportMechanism (or a default)
     * to this message.
     * 
     * @param reportMechanism
     */
    public void setReportMechanism(ReportMechanism reportMechanism)
    {
        if (reportMechanism != null)
            this.reportMechanism = reportMechanism;
        else if (session != null)
            this.reportMechanism = session.getReportMechanism();
    }

    /**
     * TODO WORKINPROGRESS to be used by ConnectionPrioritizer. Currently
     * the priority field of the messages is irrelevant as the messages work in
     * a FIFO for each session (NB: not sure if a prioritiser within messages
     * of same session will serve any practical purpose)
     * 
     * @return the priority of this message
     */
    protected short getPriority()
    {
        return priority;
    }

    /**
     * TODO WORKINPROGRESS to be used by ConnectionPrioritizer.
     *  
     * @param priority the priority to set
     */
    protected void setPriority(short priority)
    {
        this.priority = priority;
    }

    /**
     * Has this message still unused content?
     * 
     * @return true if this message still has some data to retrieve
     */
    public boolean hasData()
    {
        return (dataContainer != null && dataContainer.hasDataToRead());
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
    		throws ImplementationException, InternalErrorException
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
     * Convenience method to retrieve associated counter of this message
     * 
     * @return the counter associated with this message
     */
    public Counter getCounter()
    {
        return reportMechanism.getCounter(this);
    }

    /**
     * Setter of property {@code contentType}
     */
    public void setContentType(String contentType)
    {
    	this.contentType = contentType;
    }

    /**
     * Getter of property {@code contentType}
     * 
     * @return the content type.
     * @uml.property name="contentType"
     */
    public String getContentType()
    {
        return contentType;
    }

    /**
     * @return true if message contains a wrapped message.
     */
    public boolean isWrapped()
    {
    	return wrappedMessage != null;
    }

    /**
     * @return the wrapped message within this message.
     */
    public WrappedMessage getWrappedMessage()
    {
    	return wrappedMessage;
    }

    /**
     * @return content of message, unwrapped, into string.
     */
    public String getContent() {
    	if (getDirection() == Direction.OUT || (getDirection() == Direction.IN && isComplete())) {
    		if (isWrapped())
    			return new String(wrappedMessage.getMessageContent(), TextUtils.utf8);
    		else
    			return getRawContent();
    	}
    	return null;
    }

    /**
     * @return content of message, not unwrapped, into string.
     */
    public String getRawContent() {
    	if (getDirection() == Direction.OUT || (getDirection() == Direction.IN && isComplete())) {
    		try {
				return new String(getDataContainer().get(0, getSize()).array(), TextUtils.utf8);
			} catch (Exception e) {
				logger.info("No raw content to retrieve", e);
			}
    	}
		return null;
	}

    /**
     * Set the success report field associated with this message.
     * 
     * @param successReport True, set to "yes". False, set to "no".
     */
    public void setSuccessReport(boolean successReport)
    {
        this.successReport = successReport;
    }

    /**
     * @return report header setting of this message. True == "yes", false == "no".
     */
    public boolean wantSuccessReport()
    {
        return successReport;
    }

    /**
     * Set the failure report field associated with this message.
     * 
     * @param failureReport Field setting: "yes", "no" or "partial".
     * @throws IllegalUseException if the argument wasnone of these strings.
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
     * @return failure report setting of this message ('partial', 'yes', 'no').
     * @uml.property name="_failureReport"
     */
    public String getFailureReport()
    {
        return failureReport;
    }

    /**
     * @return the id of this message.
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
     * returns the id of this message
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
     * @return the size (bytes) of this message: -1 == uninitialized, -2 == unknown.
     */
    public long getSize()
    {
        return size;
    }

    /**
     * @return number of bytes in this message as a string, '*' if unknown.
     */
    public String getSizeString()
    {
        return size == UNKNOWN ? "*" : Long.toString(size);
    }

    /**
     * @return session this message is currently associated with.
     * @uml.property name="_session"
     */
    public Session getSession()
    {
        return session;
    }

    /**
     * Associate given session with this message
     * 
     * @param session The session to associate with this message.
     * @uml.property name="_session"
     */
    protected void setSession(Session session)
    {
        this.session = session;
    }

    /**
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
     * @return the last transaction sent
     */
    public Transaction getLastSendTransaction()
    {
        return lastSendTransaction;
    }

    /**
     * @return true if message is completely sent or received, false otherwise.
     */
    public abstract boolean isComplete();

    /** Message is ready to send or completely received, see if correct.
     * Also the cue for any (un-)wrapping.
     * @return	the validated message
     * @throws Exception
     */
    public abstract Message validate() throws Exception;

    /**
     * Aborts the Outgoing or incoming message note, both arguments are
     * irrelevant if this is an Outgoing message (as it's aborted with the #
     * continuation flag and is no way to transmit the reason)
     * 
     * @param reason the Reason for the abort, only important if this is an
     *            Incoming message
     * @param extraInfo extra info about the abort, or null if it
     *            doesn't exist, this will be sent on the REPORT if we are
     *            aborting an Incoming message
     * @throws InternalErrorException if by any Internal error, the message
     *             couldn't be aborted
     * @throws IllegalUseException if any of the arguments is invalid
     */
    public abstract void abort(int reason, String extraInfo)
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
     * TODO reflect on possibly eliminating all data associated with this
     * message or not. Could be done with a variable associated with the stack,
     * in some cases it may be useful to keep the data.
     * ATM it disposes the DataContainer associated with it
     */
    public void gotAborted(Transaction transaction)
    {
        aborted = true;
        discard();
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
     * @param extraInfo the String that was carried in the
     *            REPORT request triggering this event (null if empty).
     * @see MessageAbortedEvent
     */
    public void fireMessageAbortedEvent(int reason, String extraInfo, Transaction transaction)
    {
        session.fireMessageAbortedEvent(this, reason, extraInfo, transaction);
    }

    /**
     * Let the message know it has served its' purpose.
     * It will no longer be used and can be garbage collected. Free any resources.
     */
    public void discard()
    {
        if (dataContainer != null)
        {
            dataContainer.dispose();
            dataContainer = null;
        }
    }
}
