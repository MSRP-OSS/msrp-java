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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import msrp.*;
import msrp.Transaction.TransactionType;
import msrp.exceptions.*;

import org.slf4j.*;

/**
 * Class that represents a message with it's data on memory It must:
 * 
 * - Always have a ReportMechanism associated!
 * 
 * @author D
 */
public abstract class Message
{
    /**
     * The logger associated with this class
     */
    private static final Logger logger = LoggerFactory.getLogger(Message.class);

    /* Constructors: */

    /* Wrappers for reportMechanism : */
    // TODO generate the javadoc using the other constructors javadoc
    public Message(Session session, String contentType, Stream stream,
        ReportMechanism reportMechanism)
    {
        this(session, contentType, stream);
        constructorAssociateReport(reportMechanism);
    }

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
     * @param contentType the content type of this message
     * @param stream the stream associated with this message's content
     * @returns the newly generated message
     */
    public Message(Session session, String contentType, Stream stream)
    {
    }

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

        if (data.length > MSRPStack.getInstance().getShortMessageBytes())
        {
            // TODO create new exception for this
            throw new IllegalUseException(
                "Error! message data too big, use file source or stream constructors");
        }
        this.session = session;
        dataContainer = new MemoryDataContainer(data);
        size = data.length;
        messageId = session.generateMessageID();
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
     * The field that contains the data associated with this message (it allows
     * abstraction on the actual container of the data)
     */
    public DataContainer dataContainer;

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
     * this field points to the report mechanism associated with this message
     * the report mechanism basicly is used to decide upon the granularity of
     * the success reports
     */
    public ReportMechanism reportMechanism;

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
     * This method is intended to abort a message's send process. If the message
     * is not being sent it won't be anymore. If the message is being sent, the
     * current SEND transaction will end with the # continuation-flag char and
     * further data belonging to the message will not be sent. On the other end,
     * if the message is being sent, receiving the # continuation-flag will
     * trigger a call to the abortedMessage method on MSRPSessionListener binded
     * to the session.
     * 
     * @throws InternalErrorException If we have a failure on the sanity checks
     * 
     * @see MSRPSessionListener#abortedMessage(Session, Message)
     */
    public void abortSend() throws InternalErrorException
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

        /* internally signal this message as aborted */
        aborted = true;

        /* remove this message from the list of messages to send of the session */
        session.delMessageToSend(this);

        /*
         * find the first transaction belonging to a SEND of this message and
         * abort it, remove eventually other transactions that serve the purpose
         * of sending this message
         */
        ArrayList<Transaction> transactionsToSend =
            transactionManager.getTransactionsToSend();
        boolean firstTransactionFound = false;
        for (Transaction transaction : transactionsToSend)
        {
            if (transaction.transactionType.equals(TransactionType.SEND)
                && transaction.getMessage().equals(this))
            {
                if (!firstTransactionFound)
                {
                    transaction.abort();
                    firstTransactionFound = true;
                }
                else
                {
                    transactionsToSend.remove(transaction);
                }
            }
        }

    }

    /**
     * Method that is called by the OutgoingMessages when their isComplete() is
     * called
     * 
     * @see #isComplete()
     * @return true if the message is completely sent
     */
    protected boolean outgoingIsComplete()
    {
        boolean toReturn;
        long sentBytes = bytesSent();
        if (sentBytes == size)
            toReturn = true;
        else
            toReturn = false;

        logger.trace("Called isComplete, sent bytes: " + sentBytes
            + " message size: " + size + " going to return: " + toReturn);
        return toReturn;

    }

    /**
     * Field used to conserve the abortion state of the message
     */
    private boolean aborted = false;

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
                transaction.interrupt();
        }

        session.addMessageOnTop(this);

    }

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

    public static final int UNINTIALIZED = -2;

    public static final int UNKNWON = -1;

    /**
     * Variable that contains the size of this message as indicated on the
     * byte-range header field
     */
    public long size = UNINTIALIZED;

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
     * @uml.property name="_contentType"
     */
    protected String contentType = "null";

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

    protected static final String YES = "yes";

    protected static final String NO = "no";

    protected static final String PARTIAL = "partial";

    /**
     * @uml.property name="failureReport"
     */
    private String failureReport = YES;

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
     * @uml.property name="_fileNameContent"
     */
    private String nameContent = "null";

    /**
     * Getter of the property <tt>_fileNameContent</tt>
     * 
     * @return Returns the nameContent.
     * @uml.property name="_fileNameContent"
     */
    public String get_fileNameContent()
    {
        return nameContent;
    }

    /**
     * Setter of the property <tt>_fileNameContent</tt>
     * 
     * @param _fileNameContent The nameContent to set.
     * @uml.property name="_fileNameContent"
     */
    public void set_fileNameContent(String nameContent)
    {
        this.nameContent = nameContent;
    }

    /**
     * @uml.property name="messageId"
     */
    protected String messageId;

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
     * @uml.property name="_session"
     */
    protected Session session = null;

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
     * @uml.property name="_status"
     */
    private String _status = "null";

    /**
     * Getter of the property <tt>_status</tt>
     * 
     * @return Returns the _status.
     * @uml.property name="_status"
     */
    public String get_status()
    {
        return _status;
    }

    /**
     * Setter of the property <tt>_status</tt>
     * 
     * @param _status The _status to set.
     * @uml.property name="_status"
     */
    public void set_status(String _status)
    {
        this._status = _status;
    }

    /**
     * @uml.property name="_streamContent"
     */
    private Stream content1 = null;

    /**
     * Getter of the property <tt>_streamContent</tt>
     * 
     * @return Returns the content1.
     * @uml.property name="_streamContent"
     */
    public Stream get_streamContent()
    {
        return content1;
    }

    /**
     * Setter of the property <tt>_streamContent</tt>
     * 
     * @param _streamContent The content1 to set.
     * @uml.property name="_streamContent"
     */
    public void set_streamContent(Stream content)
    {
        content1 = content;
    }

    /**
     * @uml.property name="successReport"
     */
    private boolean successReport = false;

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
     * @uml.property name="_typeContent"
     */
    private String content2 = "null";

    /**
     * Getter of the property <tt>_typeContent</tt>
     * 
     * @return Returns the content2.
     * @uml.property name="_typeContent"
     */
    public String get_typeContent()
    {
        return content2;
    }

    /**
     * Setter of the property <tt>_typeContent</tt>
     * 
     * @param _typeContent The content2 to set.
     * @uml.property name="_typeContent"
     */
    public void set_typeContent(String content)
    {
        content2 = content;
    }

    /**
     * @return the number of sent bytes that belong to this message
     */
    public long bytesSent()
    {
        return dataContainer.currentReadOffset();
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
     * Method that states if this message is completely sent or received,
     * depending on the type of message
     * 
     * @return true if the message is completely sent or received, false
     *         otherwise
     */
    public abstract boolean isComplete();

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
     */
    public void gotAborted()
    {
        aborted = true;
        dataContainer.dispose();
        session.triggerAbortedMessage(session, (IncomingMessage) this);

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
}
