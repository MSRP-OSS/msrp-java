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

import msrp.Transaction.TransactionType;
import msrp.events.MessageAbortedEvent;
import msrp.exceptions.*;
import msrp.messages.*;
import msrp.utils.*;
import msrp.*;

import java.net.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for managing the transactions associated with a
 * connection (that can have many sessions).
 * 
 * It generates the automatic responses, and also triggers some reporting
 * mechanisms and some of the callbacks on the MSRPSessionListener
 * 
 * @author João André Pereira Antunes
 */
public class TransactionManager
    implements Observer
{

    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(TransactionManager.class);

    /**
     * @uml.property name="_message"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_transactionManager:msrp.Message"
     */
    private Message messageBeingSent = null;

    /**
     * @uml.property name="_connections"
     */
    private Connection connection = null;

    /**
     * @uml.property name="_transactionCounter"
     */
    private byte counter = 0;

    /**
     * @uml.property name="_transactions"
     * @uml.associationEnd multiplicity="(0 -1)"
     *                     inverse="_transactionManager:msrp.Transaction"
     */
    private ArrayList<Transaction> transactionsToSend =
        new ArrayList<Transaction>();

    private HashMap<String, Transaction> existingTransactions =
        new HashMap<String, Transaction>();

    private MSRPStack instanceStack = MSRPStack.getInstance();

    private HashMap<URI, Session> associatedSessions =
        new HashMap<URI, Session>();

    /**
     * Variable used so that some method can behave in a different way for
     * automatic testing purposes.
     * 
     * Methods that may behave differently:
     * 
     * @see #generateNewTID()
     */
    protected boolean testing = false;

    /**
     * Variable used for testing purposes in conjunction with the testing
     * boolean flag
     * 
     * @see #testing
     * @see #generateNewTID()
     */
    protected String presetTID;

    /**
     * Generates and queues the TransactionResponse with the given response code
     * 
     * @param responseCode one of the response codes listed in RFC 4975
     * @param optionalComment the comment as defined in RFC 4975 formal syntax,
     *            as the comment is optional, it can also be null if no comment
     *            is desired
     * @throws InternalErrorException if queuing the response got us an error
     * @throws IllegalUseException if the arguments are invalid
     */
    private void generateAndQueueResponse(Transaction originalTransaction,
        int responseCode, String optionalComment)
        throws InternalErrorException,
        IllegalUseException
    {

        TransactionResponse trResponse =
            new TransactionResponse(originalTransaction, responseCode,
                optionalComment, Transaction.OUT);
        originalTransaction.setResponse(trResponse);
        addPriorityTransaction(trResponse);

    }

    /**
     * generates and queues the response of the given transaction, taking into
     * account the Report header fields.
     * 
     * 
     * @param transaction the transaction that we are responding to
     * @param responseCode the response code to respond with
     * @param responseComment the optional string 'comment' as specified in rfc
     *            4975 syntax
     * 
     * @throws InternalErrorException if queuing the response got us an error
     * @throws IllegalUseException if the arguments or their state is invalid
     */
    public void generateResponse(Transaction transaction, int responseCode,
        String responseComment) throws IllegalUseException
    {

        logger.trace("Response being sent for Transaction tId: "
            + transaction.tID + " response code: " + responseCode);
        String failureReport = transaction.getFailureReport();

        if (responseCode != 200 && responseCode != 400 && responseCode != 403
            && responseCode != 408 && responseCode != 413
            && responseCode != 415 && responseCode != 423
            && responseCode != 481 && responseCode != 501
            && responseCode != 506)
            throw new IllegalUseException("Invalid response code");
        // TODO FIXME validate the comment based on the utf8text regex pattern
        // that will be defined in RegexMSRPFactory
        // TODO generate the responses based on the success report field

        // generate the responses based on the failure report field
        if (failureReport == null || failureReport.equalsIgnoreCase("yes")
            || failureReport.equalsIgnoreCase("partial"))
        {
            if (failureReport != null
                && failureReport.equalsIgnoreCase("partial")
                && responseCode == 200)
            {
                return;
            }
            else
            {
                // Generate transaction response

                try
                {
                    generateAndQueueResponse(transaction, responseCode,
                        responseComment);
                }
                catch (InternalErrorException e)
                {
                    logger.error("Generating a " + responseCode
                        + " response for transaction: "
                        + transaction.toString(), e);
                }
                catch (IllegalUseException e)
                {
                    ImplementationException exception =
                        new ImplementationException(
                            "In the processResponse of TransactionManager", e);
                    logger.error(
                        "Implementation error generating response for "
                            + "transaction: " + transaction, exception);
                }
            }

        }

    }

    /**
     * Convenience method that gets the session associated with the given
     * transaction
     * 
     * @param transaction transaction from which to get an associated session
     * @return the session associated with this transaction
     */
    private Session getAssociatedSession(Transaction transaction)
    {
        return MSRPStack.getInstance().getSession((transaction.getToPath())[0]);

    }

    /**
     * private String uniqueTransaction() { return ""; }
     */

    /**
     * Getter of the property <tt>_message</tt>
     * 
     * @return Returns the _message.
     * @uml.property name="_message"
     */

    protected Message getMessageBeingSent()
    {
        return messageBeingSent;
    }

    /**
     * Setter of the property <tt>_message</tt>
     * 
     * @param _message The _message to set.
     * @uml.property name="_message"
     */
    protected void setMessageBeingSent(Message message)
    {
        this.messageBeingSent = message;
    }

    /**
     * Getter of the property <tt>_connections</tt>
     * 
     * @return Returns the _connections.
     * @uml.property name="_connections"
     */
    protected Connection getConnection()
    {
        return connection;
    }

    /**
     * Getter of the property <tt>_transactionCounter</tt>
     * 
     * @return Returns the counter.
     * @uml.property name="_transactionCounter"
     */
    protected byte get_transactionCounter()
    {
        return counter;
    }

    /**
     * Setter of the property <tt>_transactionCounter</tt>
     * 
     * @param _transactionCounter The counter to set.
     * @uml.property name="_transactionCounter"
     */
    protected void set_transactionCounter(byte counter)
    {
        this.counter = counter;
    }

    /**
     * Getter of the property <tt>_transactions</tt>
     * 
     * @return Returns the _transactions.
     * @uml.property name="_transactions"
     */
    public ArrayList<Transaction> getTransactionsToSend()
    {
        return transactionsToSend;
    }

    /**
     * this is when a received transaction will give a 200 response code this
     * function is called independently of the (Failure/Success)-Report field
     * 
     * basicly this function is called whenever any request is error free and
     * awaiting to be processed (although the misleading name of the function
     * this function may not generate any kind of response)
     * 
     * @param transaction
     */
    protected void r200ProcessRequest(Transaction transaction)
    {
        logger.trace("called the r200 with transaction:" + transaction.getTID()
            + " message-id:" + transaction.getMessageID()
            + " Connection (localURI) associated: " + connection.getLocalURI());

        if (transaction.getTransactionType().equals(TransactionType.SEND))
        {
            try
            {
                generateResponse(transaction, 200, null);
            }
            catch (IllegalUseException e1)
            {
                logger.error("Generating a success report for transaction: "
                    + transaction);
            }

            long[] byteRange = transaction.getByteRange();
            long totalBytes = transaction.getTotalMessageBytes();
            Message associatedMessage = transaction.getMessage();
            if (associatedMessage != null && associatedMessage.isComplete())
            {

                logger.trace("transaction: tId" + transaction.getTID()
                    + " has an associated message message-id:"
                    + transaction.getMessageID() + " that is complete");
                /*
                 * if we have a complete message
                 */
                // if we have a complete message with content
                // get the associated session
                Session session = getAssociatedSession(transaction);

                // TODO sanity check: check to see if message already
                // exists
                // (?!
                // no use atm also think twice about
                // maintaining the receivedMessages on Session)

                try
                {
                    long callCount = associatedMessage.getCounter().getCount();

                    associatedMessage.getReportMechanism()
                        .triggerSuccessReport(associatedMessage, transaction,
                            associatedMessage.lastCallReportCount, callCount);
                    associatedMessage.lastCallReportCount = callCount;
                    Message message = transaction.getMessage();
                    /**
                     *TODO Validate the content of the message?! with the
                     * validator?!
                     */
                    session.triggerReceiveMessage(message);
                }
                catch (Exception e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // TODO parse the content-type of the message
            }
        }
        if (transaction.getTransactionType().equals(TransactionType.REPORT))
        {
            StatusHeader transactionStatusHeader =
                transaction.getStatusHeader();
            String statusCodeString =
                Integer.toString(transactionStatusHeader.getStatusCode());
            logger.trace("transaction:" + transaction.getTID()
                + " is a report! Status code: " + statusCodeString);

            /*
             * at the moment just trigger the report, doesn't save it or send
             * it:
             */
            // if (transactionStatusHeader.getNamespace() == 0)
            // REMOVE FIXME TODO the implementation exception should
            // be handled like this?!
            try
            {
                transaction.getSession().triggerReceivedReport(transaction);
            }
            catch (ImplementationException e)
            {
                logger.error("Calling triggerReceivedReport", e);
            }

        }

    }

    /**
     * Dummy constructor used for test purposes
     */
    protected TransactionManager()
    {

    }

    /**
     * Constructor used by the stack the TransactionManager always has a
     * connection associated with it
     * 
     * @param the connection associated with the new TransactionManager
     */
    protected TransactionManager(Connection connection)
    {
        this.connection = connection;
        connection.deleteObservers();
        connection.addObserver(this);
    }

    /**
     * 
     * @return the new Transaction ID generated randomly and making sure that it
     *         doesn't exist on the existingTransactions that is the list of
     *         existing transactions that this transaction manager manages. It
     *         may return also a preset transaction ID for debug and test
     *         purposes.
     */
    protected String generateNewTID()
    {
        /* next two lines used for automatic testing purposes */
        if (testing && presetTID != null)
        { /*
           * we can only generate once a presetTID otherwise the transaction
           * manager will be unable to generate new transactions
           */
            String tidToReturn = new String(presetTID);
            presetTID = null;
            return tidToReturn;
        }
        byte[] tid = new byte[8];
        String newTID;
        TextUtils.generateRandom(tid);
        for (newTID = new String(tid, TextUtils.usascii); existingTransactions
            .containsKey(newTID); TextUtils.generateRandom(tid))
            newTID = new String(tid, TextUtils.usascii);
        return newTID;
    }

    @Override
    public void update(Observable connectionObservable, Object transactionObject)
    {

        // TODO Check to see if the associated transaction belongs to this
        // session
        // Sanity check, check if this is the right type of object

        if (connectionObservable.getClass().getName() != "msrp.Connection")
        {
            logger
                .error("Error! TransactionManager was notified with the wrong type of object associated");
            return;
        }
        // Sanity check, check if this is the right type of Observable
        if (transactionObject.getClass().getName() != "msrp.Transaction"
            && transactionObject.getClass().getName() != "msrp.TransactionResponse")
        {
            logger
                .error("Error! TransactionManager was notified with the wrong observable type");
            return;
        }

        Connection connection = (Connection) connectionObservable;
        Transaction transaction = (Transaction) transactionObject;

        logger.trace("Called the UPDATE of the Transaction Manager. received"
            + " a transaction, associated connection (localURI): "
            + connection.getLocalURI() + " Transaction: " + transaction);
        /*
         * If the transaction is an incoming response, atm do nothing. TODO(?!)
         */
        if (transaction.getTransactionType().equals(
            TransactionType.SENDRESPONSE))
        {
            TransactionResponse transactionResponse =
                (TransactionResponse) transaction;
            logger
                .trace("Transaction tID: "
                    + transaction.getTID()
                    + " is an incoming response and has been processed by the transactionManager for connection localURI: "
                    + this.connection.getLocalURI());
            processResponse(transactionResponse);
            return;
        }
        if (transaction.getTransactionType()
            .equals(TransactionType.UNSUPPORTED))
        {
            /*
             * "important" question: does this 501 response precedes the 506 or
             * not?! should the to-path also be checked before?!
             */
            logger
                .trace("Transaction tID: "
                    + transaction.getTID()
                    + " is not supported and has been processed by the transactionManager for connection localURI: "
                    + this.connection.getLocalURI());
            // TODO r501();
            return;
        }

        /*
         * if it's a valid transaction call and a response hasn't been generated
         * yet, generate the r200 method otherwise ignore this call
         */
        if (transaction.isValid() && transaction.isRequest())
            r200ProcessRequest(transaction);
        logger
            .trace("Transaction tID: "
                + transaction.getTID()
                + " has been processed by the transactionManager for connection localURI: "
                + this.connection.getLocalURI());
    }

    /**
     * This method generates the appropriate actions inside the stack
     * 
     * @param transactionResponse the transaction that contains the response
     *            being processed
     */
    private void processResponse(TransactionResponse transactionResponse)
    {
        // let's see if this response is worthy of a abort event
        if (transactionResponse.responseCode == MessageAbortedEvent.RESPONSE400
            || transactionResponse.responseCode == MessageAbortedEvent.RESPONSE403
            || transactionResponse.responseCode == MessageAbortedEvent.RESPONSE413
            || transactionResponse.responseCode == MessageAbortedEvent.RESPONSE415
            || transactionResponse.responseCode == MessageAbortedEvent.RESPONSE481)
        {
            try
            {
                transactionResponse.getMessage().abort(
                    MessageAbortedEvent.CONTINUATIONFLAG, null);
            }
            catch (InternalErrorException e)
            {
                logger.error("Exception caught aborting the message: "
                    + transactionResponse.getMessage(), e);
            }
            catch (IllegalUseException e)
            {
                logger.error("Exception caught aborting the message: "
                    + transactionResponse.getMessage(), e);
            }
            // TODO support the comment Issue #29
            transactionResponse.getMessage().fireMessageAbortedEvent(
                transactionResponse.responseCode, null, transactionResponse);
        }
    }

    /**
     * Method used by an incoming Transaction to retrieve the session associated
     * with it
     * 
     * @param uriSession
     * @return the session associated with uriSession or null if there is no
     *         such session by that uri associated with this object
     */
    protected Session associatedSession(URI uriSession)
    {
        return associatedSessions.get(uriSession);

    }

    /**
     * Associates the given session to this transaction manager and hencefore
     * with the unique connection associated with this transaction manager Also
     * bind the session to the transaction manager
     * 
     * @param session the Session to be added to the list of associated sessions
     *            of this transaction manager
     */
    protected void addSession(Session session)
    {
        session.setTransactionManager(this);
        associatedSessions.put(session.getURI(), session);

    }

    /**
     * @return a Collection of the sessions associated with this transaction
     *         manager
     */
    protected Collection<Session> getAssociatedSessions()
    {
        return associatedSessions.values();
    }

    /**
     * initializes the given session by sending an existent message of the
     * message queue or sending a new empty send without body
     * 
     * @param session the session to initialize
     */
    protected void initialize(Session session)
    {
        if (session.hasMessagesToSend())
        {

            if (messageBeingSent == null)
                messageBeingSent = session.getMessageToSend();
            // FIXME TODO the generateTransactionsToSend might not initialize
            // the session if the message being sent is not from this session.
            // In that case, the connection should pause the messages being sent
            // and send an empty message or a piece of the message this session
            // has related with Issue #31
            generateTransactionsToSend();
            // connection.notifyWriteThread();

        }
        else
        {
            // TODO send an empty body send message

        }
    }

    /**
     * method used to generate the transactions to be sent based on the message
     * also updates the reference in the Message of the lastSendTransaction of
     * the newly created transaction
     */
    protected void generateTransactionsToSend()
    {
        if (messageBeingSent == null
            || messageBeingSent.getDirection() != Message.OUT)
            return;

        Transaction newTransaction =
            new Transaction(TransactionType.SEND,
                (OutgoingMessage) messageBeingSent, this);

        // Add the transaction to the known list of existing transactions
        // this is used to generate unique TIDs in the connection and to
        // be used when a response to a transaction is received
        existingTransactions.put(newTransaction.getTID(), newTransaction);
        addTransactionToSend(newTransaction, UNIMPORTANT);
        // possibly split the message into several transactions

        // change the reference to the lastSendTransaction of the message
        messageBeingSent.setLastSendTransaction(newTransaction);

        // alert the writing thread that it has a job to do probably solves
        // Issue #32 TODO VERIFY it does solve it with some new performance
        // tests
        connection.notifyWriteThread();

    }

    private static final int UNIMPORTANT = -1;

    /**
     * Adds the given transaction to the queue of transactions to send and wakes
     * up the write thread of the associated connection
     * 
     * @param Transaction transactionToSend
     * @param positionIndex the position in which to add the transaction, if -1
     *            (UNIMPORTANT) just run an .add
     */
    private void addTransactionToSend(Transaction transaction, int positionIndex)
    {
        if (positionIndex != UNIMPORTANT)
            transactionsToSend.add(positionIndex, transaction);
        else
            transactionsToSend.add(transaction);
        connection.notifyWriteThread();

    }

    /**
     * Checks the transaction queue for existing transactions to be sent
     * 
     * @return true if this transaction manager has data on queue to send and
     *         false otherwise
     */
    protected boolean hasDataToSend()
    {
        if (!transactionsToSend.isEmpty())
            return true;
        return false;
    }

    /**
     * Class used to validate the outgoing data to what concerns the transaction
     * id validation.
     * 
     * This class gives the needed methods to check if there is an end-line on
     * the body of the transaction currently being sent or not.
     * 
     * As written in RFC 4975: " If the request contains a body, the sender MUST
     * ensure that the end- line (seven hyphens, the transaction identifier, and
     * a continuation flag) is not present in the body. [...] Some
     * implementations may choose to scan for the closing sequence as they send
     * the body, and if it is encountered, simply interrupt the chunk at that
     * point and start a new transaction with a different transaction identifier
     * to carry the rest of the body."
     * 
     * The approach of interrupting the ongoing transaction and create a new one
     * was the one chosen and implemented by this library
     * 
     * @author João André Pereira Antunes
     * 
     */
    class OutgoingDataValidator
    {

        /**
         * This variable is true if the end-line was found and false otherwise.
         * Calls to the dataHasEndLine reset this variable to false.
         * 
         * @see #dataHasEndLine()
         */
        private boolean foundEndLine = false;

        /**
         * It contains the number of bytes we should rewind the read offsets of
         * the transaction
         */
        private int toRewind = 0;

        /**
         * Variable used to store the transaction ID that this class is using to
         * look for the end-line
         */
        private String transactionID = null;

        /**
         * Variable used by the method parse to assert in which state the parser
         * is so that we can save the state of this state machine between calls
         */
        private short parserState = INITIALSTATE;

        /* constants to make the code a little bit more readable: */
        private static final short INITIALSTATE = 0;

        private static final short FIRSTHYPHEN = 1;

        private static final short SECONDHYPHEN = 2;

        private static final short THIRDHYPHEN = 3;

        private static final short FOURTHHYPHEN = 4;

        private static final short FIFTHHYPHEN = 5;

        private static final short SIXTHHYPHEN = 6;

        private static final short SEVENTHHYPHEN = 7;

        /**
         * Method used to assert if the data we have so far has or not the end
         * of line
         * 
         * @return true if the data parsed so far has the end of line or false
         *         otherwise
         * 
         *         NOTE: This method resets the hasEndLine variable so by doing
         *         two consecutive calls to this method the result of the second
         *         can never be true.
         * @see #foundEndLine
         */
        private boolean dataHasEndLine()
        {
            boolean auxBoolean = foundEndLine;
            foundEndLine = false;
            return auxBoolean;
        }

        /**
         * Method used to initialize this class
         * 
         * @param transactionId the transaction id to be used in the search of
         *            the end-line
         */
        private void init(String transactionId)
        {
            this.transactionID = transactionId;
        }

        /**
         * Method used to reset the outgoingValidator. After this method future
         * calls to the parse won't parse anything before a call to the init
         * method is done
         * 
         */
        private void reset()
        {
            this.transactionID = null;
        }

        /**
         * This method is used to parse the data to be searched for the end line
         * characters
         * 
         * @param outputData the data to be parsed and searched for the end line
         *            string
         * @param length how many bytes of the outputData vector should be
         *            searched
         * @throws ImplementationException if it was detected that this method
         *             was used in an incorrect way
         */
        private void parse(byte[] outputData, int length)
            throws ImplementationException
        {
            if (transactionID == null)
                return;

            if (outputData.length < length)
                throw new ImplementationException("method "
                    + "called with argument length too big");
            /*
             * if we found already the end of line and haven't reset the value
             * with a call to hasEndLine and we call the parse that generates an
             * ImplementationException
             */
            if (foundEndLine)
                throw new ImplementationException(
                    "Error, bad use of the class "
                        + "outgoingDataValidator on TransactionManager, after "
                        + "after calling parse a call should always be made "
                        + "to the dataHasEndLine");
            for (int i = 0; i < length; i++)
            {
                switch (parserState)
                {
                case INITIALSTATE:
                    if (outputData[i] == '-')
                        parserState = FIRSTHYPHEN;
                    break;

                case FIRSTHYPHEN:
                    if (outputData[i] == '-')
                        parserState = SECONDHYPHEN;
                    else
                        parserState = INITIALSTATE;
                    break;

                case SECONDHYPHEN:
                    if (outputData[i] == '-')
                        parserState = THIRDHYPHEN;
                    else
                        parserState = INITIALSTATE;
                    break;

                case THIRDHYPHEN:
                    if (outputData[i] == '-')
                        parserState = FOURTHHYPHEN;
                    else
                        parserState = INITIALSTATE;
                    break;

                case FOURTHHYPHEN:
                    if (outputData[i] == '-')
                        parserState = FIFTHHYPHEN;
                    else
                        parserState = INITIALSTATE;
                    break;

                case FIFTHHYPHEN:
                    if (outputData[i] == '-')
                        parserState = SIXTHHYPHEN;
                    else
                        parserState = INITIALSTATE;
                    break;

                case SIXTHHYPHEN:
                    if (outputData[i] == '-')
                        parserState = SEVENTHHYPHEN;
                    else
                        parserState = INITIALSTATE;
                    break;

                default:
                    if (parserState >= SEVENTHHYPHEN)
                    {
                        if ((parserState - SEVENTHHYPHEN) < transactionID
                            .length()
                            && outputData[i] == transactionID
                                .charAt(parserState - SEVENTHHYPHEN))
                            parserState++;
                        else if ((parserState - SEVENTHHYPHEN >= transactionID
                            .length() && (outputData[i] == '$'
                            || outputData[i] == '#' || outputData[i] == '+')))
                        {
                            foundEndLine = true;
                            toRewind = length - i + parserState;
                            /*
                             * if we had a end-line splitted by buffers we
                             * rewind to the beginning of the data in this
                             * buffer and then interrupt the transaction
                             */
                            if (toRewind > length)
                                toRewind = length;

                            parserState = INITIALSTATE;

                            /* we exit the for */
                            break;

                        }
                        else
                            parserState = INITIALSTATE;
                    }
                    break;

                }
            }

        }

        /**
         * @return this method returns the number of positions we should rewind
         *         on the buffer and on the transaction's read offset before we
         *         interrupt the current transaction
         */
        private int numberPositionsToRewind()
        {
            return toRewind;
        }

    }

    private OutgoingDataValidator outgoingDataValidator =
        new OutgoingDataValidator();

    /**
     * Method used by the connection object to retrieve the byte array of data
     * to be sent by the connection
     * 
     * Validates and interrupts the transaction as neccessary with the validator
     * 
     * It is also at this level that the sending of bytes is accounted for
     * purposes of triggering the sendUpdateStatus and the prioritizer
     * 
     * @param outData the byte array to fill with data to be sent
     * @return the number of bytes filled on outData
     * @throws Exception if something went wrong retrieving the data FIXME (?!
     *             or maybe not ?!) maybe be more specific on the exception
     */
    protected int dataToSend2(byte[] outData) throws Exception
    {
        int byteCounter = 0;
        /*
         * Variable that accounts the number of bytes per transaction sent
         */
        int bytesToAccount = 0;
        while (hasDataToSend() && byteCounter < outData.length)
        {
            outgoingDataValidator.reset();
            Transaction t = transactionsToSend.get(0);
            outgoingDataValidator.init(t.getTID());

            boolean stopTransmission = false;
            while (byteCounter < outData.length && !stopTransmission)
            {
                if (t.hasData())
                {// if we are still transmitting data
                    int result = t.getData(outData, byteCounter);
                    byteCounter += result;
                    bytesToAccount += result;

                }
                else
                {
                    // Let's check to see if we should transmit the end of line
                    if (t.hasEndLine())
                    {
                        /*
                         * the first time we get here we should check if the
                         * end-line was found, and if it was we should rewind,
                         * interrupt the transaction and set the bytesToAccount
                         * back the appropriate positions and also the index i
                         * we can also do the reset of the outgoingDataValidator
                         * because we have for certain that the end-line won't
                         * appear again on the content before the transaction
                         * finishes
                         */
                        outgoingDataValidator.parse(outData, byteCounter);
                        outgoingDataValidator.reset();
                        if (outgoingDataValidator.dataHasEndLine())
                        {
                            int numberPositionsToRewind =
                                outgoingDataValidator.numberPositionsToRewind();
                            t.rewind(numberPositionsToRewind);
                            t.interrupt();
                            generateTransactionsToSend();
                            byteCounter -= numberPositionsToRewind;
                            bytesToAccount -= numberPositionsToRewind;
                            continue;
                        }
                        bytesToAccount++;
                        // TODO ISSUE #PF probably could optimize better with a
                        // bulk end line method, but seen that this is less than
                        // 30 bytes..
                        outData[byteCounter++] = t.getEndLineByte();

                    }
                    else
                    {
                        /*
                         * Removing the given transaction from the queue of
                         * transactions to send
                         */
                        transactionsToSend.remove(t);
                        /*
                         * we should also reset the outgoingDataValidator, so
                         * that future calls to the parser won't misjudge the
                         * correct end-line as an end-line on the body content.
                         */
                        outgoingDataValidator.reset();
                        // let's get the next transaction if any
                        stopTransmission = true; // jump out of second while

                    }
                }
            }// end of transaction while
            stopTransmission = false;
            /*
             * the buffer is full and or the transaction has been removed from
             * the list of transactions to send and if that was the case the
             * outgoingValidator won't make a false positive because it has been
             * reseted
             */

            outgoingDataValidator.parse(outData, byteCounter);
            if (outgoingDataValidator.dataHasEndLine())
            {
                int numberPositionsToRewind =
                    outgoingDataValidator.numberPositionsToRewind();
                t.rewind(numberPositionsToRewind);
                t.interrupt();
                generateTransactionsToSend();
                byteCounter -= numberPositionsToRewind;
                bytesToAccount -= numberPositionsToRewind;
                outgoingDataValidator.reset();
            }

            // account for the bytes sent from this transaction if they should
            // be accounted for
            if (!t.isIncomingResponse()
                && t.transactionType.equals(TransactionType.SEND)
                && !t.hasResponse())
            {
                /*
                 * reporting the sent update status seen that this is an
                 * outgoing send request
                 */
                OutgoingMessage transactionMessage =
                    (OutgoingMessage) t.getMessage();
                if (transactionMessage != null)
                {
                    transactionMessage.reportMechanism.countSentBodyBytes(
                        transactionMessage, bytesToAccount);
                    bytesToAccount = 0;

                }
            }
        }// end of main while, the one that goes across transactions

        return byteCounter;

    }

    /**
     * Method used by the connection object to retrieve the byte array of data
     * to be sent by the connection
     * 
     * Also it's at this level that the sending of bytes is accounted for
     * purposes of triggering the sendUpdateStatus and the prioritizer
     * 
     * @deprecated use #dataToSend2 remove this method when the other is proven
     *             that is working right
     * 
     * @param outData the byte to fill with data to be sent
     * @return the number of bytes filled on outData
     * @throws Exception
     */
    protected int dataToSend(byte[] outData) throws Exception
    {
        int i = 0;
        /*
         * Variable that accounts the number of bytes per transaction to store
         */
        int bytesToAccount = 0;
        for (i = 0; hasDataToSend() && i < outData.length;)
        {
            Transaction t = transactionsToSend.get(0);
            outgoingDataValidator.init(t.getTID());

            while (i < outData.length)
            {

                if (!t.hasData())
                {
                    if (t.hasEndLine())
                    {
                        /*
                         * the first time we get here we should check if the
                         * end-line was found, and if it was we should rewind,
                         * interrupt the transaction and set the bytesToAccount
                         * back the appropriate positions and also the index i
                         * we can also do the reset of the outgoingDataValidator
                         * because we have for certain that the end-line won't
                         * appear again on the content before the transaction
                         * finishes
                         */
                        outgoingDataValidator.parse(outData, i);
                        outgoingDataValidator.reset();
                        if (outgoingDataValidator.dataHasEndLine())
                        {
                            int numberPositionsToRewind =
                                outgoingDataValidator.numberPositionsToRewind();
                            t.rewind(numberPositionsToRewind);
                            t.interrupt();
                            generateTransactionsToSend();
                            i -= numberPositionsToRewind;
                            bytesToAccount -= numberPositionsToRewind;
                            continue;
                        }
                        bytesToAccount++;
                        outData[i++] = t.getEndLineByte();

                    }
                    else
                    {
                        /*
                         * Removing the given transaction from the queue of
                         * transactions to send
                         */
                        transactionsToSend.remove(t);
                        /*
                         * we should also reset the outgoingDataValidator, so
                         * that future calls to the parser won't misjudge the
                         * correct end-line as an end-line on the body content.
                         */
                        outgoingDataValidator.reset();
                        break;
                    }
                }
                else
                {
                    bytesToAccount++;
                    outData[i++] = t.get();
                }

            } /*
               * End of while, the buffer is full and or the transaction has
               * been removed from the list of transactions to send and if that
               * was the case the outgoingValidator won't make a false positive
               * because it has been reseted
               */

            outgoingDataValidator.parse(outData, i);
            if (outgoingDataValidator.dataHasEndLine())
            {
                int numberPositionsToRewind =
                    outgoingDataValidator.numberPositionsToRewind();
                t.rewind(numberPositionsToRewind);
                t.interrupt();
                generateTransactionsToSend();
                i -= numberPositionsToRewind;
                bytesToAccount -= numberPositionsToRewind;
                outgoingDataValidator.reset();
            }

            if (!t.isIncomingResponse()
                && t.transactionType.equals(TransactionType.SEND)
                && !t.hasResponse())
            {
                /*
                 * reporting the sent update status seen that this is an
                 * outgoing send request
                 */
                OutgoingMessage transactionMessage =
                    (OutgoingMessage) t.getMessage();
                if (transactionMessage != null)
                {
                    transactionMessage.reportMechanism.countSentBodyBytes(
                        transactionMessage, bytesToAccount);
                    bytesToAccount = 0;

                }
            }
        }
        return i;
    }

    /**
     * Method used only for automatic test purposes
     * 
     * @return the collection of the values of the existingTransactions variable
     * @see #existingTransactions
     */
    protected Collection<Transaction> getExistingTransactions()
    {
        return existingTransactions.values();
    }

    /**
     * @param tid the String with the transaction id of the desired transaction
     * @return the transaction with transaction id given, if it exists on the
     *         existingTransactions Hashmap, or null otherwise
     * @see #existingTransactions
     */
    protected Transaction getTransaction(String tid)
    {

        return existingTransactions.get(tid);
    }

    /**
     * Checks if the first transaction of the list of transactions to send is
     * interruptible and if it is interrupts it and puts this new one next to be
     * sent. Calls the generateTransactionsToSend() in order to generate the
     * rest of the transactions of the message send request that was interrupted
     * 
     * It's responsible for appropriate queuing of REPORT and responses
     * 
     * FIXME This probably at this point shouldn't be public, solving of Issue
     * #27
     * 
     * @param transaction the REPORT or response transaction
     * @throws IllegalUseException if the transaction argument is invalid
     */
    public void addPriorityTransaction(Transaction transaction)
        throws IllegalUseException
    {
        // sanity check, shouldn't be needed:
        if (transaction.getTransactionType() != TransactionType.SENDRESPONSE
            && transaction.getTransactionType() != TransactionType.REPORT)
            throw new IllegalUseException("the addPriorityTransaction was"
                + " called with a transaction that isn't a response/REPORT");
        if (transaction.getDirection() != Transaction.OUT)
            throw new IllegalUseException(" the addPriorityTransaction was"
                + "called with an invalid direction transaction, "
                + "direction: " + transaction.getDirection());

        try
        {
            /*
             * Make sure that this response doesn't put itself ahead of other
             * priority transactions:
             */
            for (int i = 0; i <= transactionsToSend.size(); i++)
            {
                Transaction trans = transactionsToSend.get(i);
                if (trans.isInterruptible())
                {
                    if (i == 0)
                        addTransactionToSend(transaction, 1);
                    else
                        addTransactionToSend(transaction, i);
                    trans.interrupt();
                    generateTransactionsToSend();

                    return;
                }
            }

        }
        catch (IndexOutOfBoundsException e)
        {
            // There are no transaction to send, just add the one given
            addTransactionToSend(transaction, UNIMPORTANT);
        }
    }

}
