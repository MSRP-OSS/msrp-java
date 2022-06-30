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

import java.net.*;
import java.security.InvalidParameterException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.net.msrp.events.MessageAbortedEvent;
import javax.net.msrp.exceptions.*;
import javax.net.msrp.utils.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for managing the transactions associated with a
 * connection (that can have many sessions).
 * 
 * It generates the automatic responses, and also triggers some reporting
 * mechanisms and some of the callbacks on the SessionListener
 * 
 * @author Jo�o Andr� Pereira Antunes
 */
public class TransactionManager
    implements Observer
{
    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(TransactionManager.class);

    /*
     * @uml.property name="_connections"
     */
    private Connection connection = null;

    /*
     * @uml.property name="_transactionCounter"
     */
    private byte counter = 0;

    /*
     * @uml.property name="_transactions"
     * @uml.associationEnd multiplicity="(0 -1)"
     *                     inverse="_transactionManager:javax.net.msrp.Transaction"
     */
    private Vector<Transaction> transactionsToSend =
        new Vector<Transaction>();

    private HashMap<String, Transaction> existingTransactions =
        new HashMap<String, Transaction>();

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
        throws InternalErrorException, IllegalUseException
    {
        TransactionResponse trResponse =
            new TransactionResponse(originalTransaction, responseCode,
                optionalComment, Direction.OUT);
        originalTransaction.setResponse(trResponse);
        addPriorityTransaction(trResponse);
    }

    /**
     * generates and queues the response of the given transaction, taking into
     * account the Report header fields.
     * 
     * @param transaction the transaction that we are responding to
     * @param responseCode the response code to respond with
     * @param comment the optional string 'comment' as specified in rfc 4975
     * 
     * @throws IllegalUseException if the arguments or their state is invalid
     */
    public void generateResponse(Transaction transaction, int responseCode,
        String comment) throws IllegalUseException
    {
    	if (transaction == null)
    		throw new InvalidParameterException("null tranaction specified");
        if (!ResponseCode.isValid(responseCode))
            throw new InvalidParameterException("Invalid response code");
        if (transaction.getTransactionType() == TransactionType.REPORT)
        {
            /* 7.1.2. MUST NOT send msrp responses to REPORT requests */
            logger.warn("Cannot respond to report transaction " + transaction);
            return;
        }
        // TODO validate comment based on utf8text
        logger.trace(this + " response being sent for " +
        		transaction + " response code " + responseCode);
        String reportFlag = transaction.getFailureReport();

        // TODO generate responses based on success report field

        // generate response based on failure report field
        if (reportFlag == null || reportFlag.equalsIgnoreCase("yes") ||
    		reportFlag.equalsIgnoreCase("partial"))
        {
            if (reportFlag != null && reportFlag.equalsIgnoreCase("partial") &&
                !ResponseCode.isError(responseCode))
            {
                return;
            }
            else
            {
                try
                {
                    generateAndQueueResponse(transaction, responseCode,
                        comment);
                }
                catch (InternalErrorException e)
                {
                    logger.error(this + " generating a " + responseCode +
                        " response for " + transaction, e);
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
        return Stack.getInstance().getSession((transaction.getToPath())[0]);
    }

    /**
     * Getter of the property <tt>_connections</tt>
     * 
     * @return Returns the _connections.
     * uml.property name="_connections"
     */
    protected Connection getConnection()
    {
        return connection;
    }

    /**
     * Getter of the property <tt>_transactionCounter</tt>
     * 
     * @return Returns the counter.
     * uml.property name="_transactionCounter"
     */
    protected byte get_transactionCounter()
    {
        return counter;
    }

    /**
     * Setter of the property <tt>_transactionCounter</tt>
     * 
     * @param counter The counter to set.
     * uml.property name="_transactionCounter"
     */
    protected void set_transactionCounter(byte counter)
    {
        this.counter = counter;
    }

    /**
     * this is when a received transaction will give a 200 response code this
     * function is called independently of the (Failure/Success)-Report field
     * 
     * Basically this function is called whenever any request is error free and
     * awaiting to be processed (although the misleading name of the function
     * this function may not generate any kind of response)
     * 
     * @param transaction the Tx
     */
    protected void r200ProcessRequest(Transaction transaction)
    {
        logger.trace(this + " called r200 with " + transaction +
            ", message-id[" + transaction.getMessageID() +
            "], associated connection (localURI): " + connection.getLocalURI());

        if (transaction.getTransactionType() == TransactionType.SEND)
        {
            try
            {
                generateResponse(transaction, ResponseCode.RC200, null);
            }
            catch (IllegalUseException e1)
            {
                logger.error(this + " generating success report for " + transaction);
            }
            IncomingMessage message = (IncomingMessage) transaction.getMessage();
            if (message != null && message.isComplete())
            {
                logger.trace(String.format(
                		"%s %s has an associated message-id[%s] that is complete.",
                		this, transaction, transaction.getMessageID()));
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
                	IncomingMessage validated =
                			(IncomingMessage) message.validate();

                	long callCount = validated.getCounter().getCount();
                    validated.getReportMechanism()
                        .triggerSuccessReport(validated, transaction,
                            validated.lastCallReportCount, callCount);
                    validated.lastCallReportCount = callCount;

                    session.triggerReceiveMessage(validated);
                }
                catch (Exception e)
                {
					try {
						FailureReport fr = new FailureReport(message, session, transaction,
								"000", ResponseCode.RC400, e.getMessage());
	                	addPriorityTransaction(fr);
					} catch (Exception e1) { /*empty */ }
                }
            }
        }
        if (transaction.getTransactionType() == TransactionType.REPORT)
        {
            StatusHeader transactionStatusHeader =
                transaction.getStatusHeader();
            String statusCodeString =
                Integer.toString(transactionStatusHeader.getStatusCode());
            logger.trace(String.format(
            			"%s %s is a report! Status code %s.",
            			this, transaction, statusCodeString));

            /*
             * at the moment just trigger the report, doesn't save it or send
             * it:
             */
            // if (transactionStatusHeader.getNamespace() == 0)
            // REMOVE FIXME the implementation exception should
            // be handled like this?!
            try
            {
                transaction.getSession().triggerReceivedReport(transaction);
            }
            catch (ImplementationException e)
            {
                logger.error(this + " error calling triggerReceivedReport", e);
            }
        }
    }

    @Override
    public String toString()
    {
    	return "[TxManager@" + Integer.toHexString(hashCode()) + "]";
    }

    /**
     * Dummy constructor used for test purposes
     */
    protected TransactionManager()
    {
    	;
    }

    /**
     * Constructor used by the stack the TransactionManager always has a
     * connection associated with it
     * 
     * @param connection associated with the new TransactionManager
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
        {
        	/*
        	 * we can only generate a presetTID once, otherwise the transaction
        	 * manager will be unable to generate new transactions
        	 */
            String tidToReturn = new String(presetTID);
            presetTID = null;
            return tidToReturn;
        }
        byte[] tid = new byte[8];
        String newTID;
        do {
        	TextUtils.generateRandom(tid);
            newTID = new String(tid, TextUtils.utf8);
        } while (existingTransactions.containsKey(newTID));
        return newTID;
    }

    @Override
	public void update(Observable connectionObservable, Object transactionObject)
    {
        // TODO Check to see if the associated transaction belongs to this
        // session
        // Sanity check, check if this is the right type of object

        if (!(connectionObservable instanceof Connection))
        {
            logger.error(this + " notified with wrong type of object associated");
            return;
        }
        // Sanity check, check if this is the right type of Observable
        if (!(transactionObject instanceof Transaction))
        {
            logger.error(this + " notified with the wrong observable type");
            return;
        }

        Connection connection = (Connection) connectionObservable;
        Transaction transaction = (Transaction) transactionObject;

        logger.trace(this + "- UPDATE called. Received " +
	            transaction + ", associated connection (localURI): " +
	            connection.getLocalURI());
        /*
         * If the transaction is an incoming response, atm do nothing. TODO(?!)
         */
        if (transaction.getTransactionType() == TransactionType.RESPONSE)
        {
            TransactionResponse transactionResponse =
                (TransactionResponse) transaction;
            logger.trace(String.format(
                    "%s %s is an incoming response and has been processed" +
            		" by the transactionManager for connection (localURI): ",
                    transaction, connection.getLocalURI()));
            processResponse(transactionResponse);
            return;
        }
        if (transaction.getTransactionType() == TransactionType.UNSUPPORTED)
        {
            /*
             * "important" question: does this 501 response precede the 506 or
             * not?! should the to-path also be checked before?!
             */
            logger.trace(String.format(
            		"%s %s is not supported. Processed for connection (localURI): %s",
		            this, transaction, connection.getLocalURI()));
            // TODO r501();
            return;
        }

        /*
         * if it's a valid transaction call and a response hasn't been generated
         * yet, generate the r200 method otherwise ignore this call
         */
        if (transaction.isValid() && transaction.isRequest())
            r200ProcessRequest(transaction);
        logger.trace(String.format(
        		"%s %s has been processed for connection (localURI): %s",
	            this, transaction, this.connection.getLocalURI()));
    }

    /**
     * This method generates the appropriate actions inside the stack
     * 
     * @param response the transaction that contains the response
     *            being processed
     */
    private void processResponse(TransactionResponse response)
    {
    	if (response.response2Type == TransactionType.NICKNAME)
    	{
			response.getMessage().getSession().triggerReceivedNickResult(response);
    	}
        // let's see if this response is worthy of a abort event
    	else if (ResponseCode.isAbortCode(response.responseCode))
        {
            try
            {
                response.getMessage().abort(
                    MessageAbortedEvent.CONTINUATIONFLAG, null);
            }
            catch (InternalErrorException e)
            {
                logger.error(this + " exception caught aborting message "
                    + response.getMessage(), e);
            }
            catch (IllegalUseException e)
            {
                logger.error(this + " exception caught aborting message "
                    + response.getMessage(), e);
            }
            response.getMessage().fireMessageAbortedEvent(
                response.responseCode, null, response);
        }
    }

    /**
     * Method used by an incoming Transaction to retrieve the session associated
     * with it
     * 
     * @param uriSession from
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
        associatedSessions.put(session.getURI(), session);
        session.setTransactionManager(this);
    }

    protected void removeSession(Session session) {
    	associatedSessions.remove(session.getURI());
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
     * Initialises the given session by sending an existent message of the
     * message queue or sending a new empty send without body
     * 
     * @param session the session to initialise
     */
    protected void initialize(Session session)
    {
        if (session.hasMessagesToSend())
            generateTransactionsToSend(session.getMessageToSend());
		else
			try {
				session.sendAliveMessage();
			} catch (Exception e) {
				logger.warn(this + " " + e.getMessage());
			}
    }

    /**
     * Generate transactions to be sent based on the message offered.
     * Also updates the lastSendTransaction-reference in that Message.
     * 
     * @param toSend	the message to queue.
     */
    protected void generateTransactionsToSend(Message toSend)
    {
        if (toSend == null || toSend.getDirection() != Direction.OUT)
            throw new IllegalArgumentException(
                    "No or invalid message to send specified");

        OutgoingMessage validated = null;
        Transaction newTransaction = null;
        try
    	{
	        validated = (OutgoingMessage) toSend.validate();
    	}
    	catch (Exception e)
    	{
    		logger.error("Error validating message to send, ignoring. Reason: ", e);
    		return;
    	}
    	int chunks = validated.getChunks();
        do
        {
            newTransaction = new Transaction((OutgoingMessage) validated, this);
            synchronized(this)
            {
                if (chunks == 1)
                    newTransaction.setEndChunk();

                /* Add transaction to known list of existing transactions,
    	         * used to generate unique TIDs in the connection and to
    	         * be used when a response to a transaction is received.
    	         */
    	        existingTransactions.put(newTransaction.getTID(), newTransaction);
    
    	        // change the reference to the lastSendTransaction of the message
    	        toSend.setLastSendTransaction(newTransaction);
    
    	        addTransactionToSend(newTransaction, UNIMPORTANT);
            }
    	}
    	while (--chunks > 0);
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
     * Remove this transaction from the send queue.
     * In case this is an interrupted transaction, generate and queue the rest.
     * @param tx the transaction to remove.
     */
    private void removeTransactionToSend(Transaction tx) {
		if (transactionsToSend.remove(tx))
		{
			if (tx.interrupted && !tx.isAborted())
			{
				generateTransactionsToSend(tx.getMessage());
			}
		}
    }

    /**
     * Checks the transaction queue for existing transactions to be sent
     * 
     * @return true if this transaction manager has data on queue to send and
     *         false otherwise
     */
    protected boolean hasDataToSend()
    {
         return !transactionsToSend.isEmpty();
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
     * @author Jo�o Andr� Pereira Antunes
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
        private short state = 0;

        /**
         * Assert if the data we have so far contains the end-line
         * 
         * @return true if the data parsed so far has the end-line, false otherwise
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
                        + "calling parse a call should always be made "
                        + "to the dataHasEndLine");
            for (int i = 0; i < length; i++)
            {
                switch (state)
                {
                case 0:
                    if (outputData[i] == '-')
                        state++;
                    break;

                case 1: case 2: case 3: case 4: case 5: case 6:
                    if (outputData[i] == '-')
                        state++;
                    else
                        state = 0;
                    break;

                default:
                    if (state >= 7)
                    {
                        if ((state - 7) < transactionID.length() &&
                    		outputData[i] == transactionID.charAt(state - 7))
                            state++;
                        else if ((state - 7 >= transactionID.length() &&
                        		 (outputData[i] == '$' || outputData[i] == '#' ||
                        		  outputData[i] == '+')))
                        {
                            foundEndLine = true;
                            toRewind = length - i + state;
                            /*
                             * if we had an end-line split by buffers we
                             * rewind to the beginning of the data in this
                             * buffer and then interrupt the transaction
                             */
                            if (toRewind > length)
                                toRewind = length;
                            state = 0;
                            break;					/* exit the for */
                        }
                        else
                            state = 0;
                    }
                    break;
                }
            }
        }

        /**
         * Returns the number of positions we should rewind
         * on the buffer and on the transaction's read offset before we
         * interrupt the current transaction.
         */
        private int amount2Rewind()
        {
            return toRewind;
        }
    }

    private OutgoingDataValidator outgoingDataValidator =
        new OutgoingDataValidator();

    /**
     * Method used by the connection object to retrieve a byte array of data
     * to be sent by the connection.
     * 
     * 3 mechanisms are active here:
     *  - 1 piggyback multiple transactions to send into the byte array.
     *  - 2 split large data over multiple byte-array blocks
     *  - 3 interrupt transactions that contain endline-data in the content
     *  	(and split into multiple transactions, using the validator).
     * 
     * It is also at this level that the sending of bytes is accounted for
     * purposes of triggering the sendUpdateStatus and the prioritiser
     * 
     * @param outData the byte array to fill with data to send
     * @return the number of bytes filled on outData
     * @throws Exception if something went wrong retrieving the data.
     */
    protected int getDataToSend(byte[] outData) throws Exception
    {
        int byteCounter = 0;
        int bytesToAccount = 0;		/* Number of bytes per transaction sent */

        synchronized (this) {
	        while (byteCounter < outData.length && hasDataToSend())
	        {
	            Transaction t = transactionsToSend.get(0);
	            outgoingDataValidator.init(t.getTID());

	            boolean nextTransaction = false;
	            while (byteCounter < outData.length && !nextTransaction)
	            {
	                if (t.hasData())
	                {	// when we are still transmitting data
	                    int result = t.getData(outData, byteCounter);
	                    byteCounter += result;
	                    bytesToAccount += result;
	                }
	                else
	                {
	                    // Let's check to see if we should transmit end of line
	                    if (t.hasEndLine())
	                    {
	                        /*
	                         * First time we get here we should check if the
	                         * end-line was found, if it was we should rewind,
	                         * interrupt transaction and set bytesToAccount
	                         * back to appropriate positions including index i.
	                         * We can also reset the outgoingDataValidator
	                         * because we know the end-line won't
	                         * appear again on content before the transaction
	                         * finishes
	                         */
	                        outgoingDataValidator.parse(outData, byteCounter);
	                        outgoingDataValidator.reset();
	                        if (outgoingDataValidator.dataHasEndLine())
	                        {
	                            int rewindAmount =
	                            		outgoingDataValidator.amount2Rewind();
	                            t.rewind(rewindAmount);
	                            t.interrupt();
	                            byteCounter -= rewindAmount;
	                            bytesToAccount -= rewindAmount;
	                            continue;
	                        }
	                        int nrBytes = t.getEndLine(outData, byteCounter);
	                        byteCounter += nrBytes;
	                        bytesToAccount += nrBytes;
	                    }
	                    else
	                    {
	                        /*
	                         * Removing the given transaction from the queue of
	                         * transactions to send
	                         */
	                    	removeTransactionToSend(t);
	                        /*
	                         * we should also reset the outgoingDataValidator, so
	                         * that future calls to the parser won't misjudge the
	                         * correct end-line as an end-line on the body content.
	                         */
	                        outgoingDataValidator.reset();

	                        nextTransaction = true; // get next transaction, if any
	                    }
	                }
	            }// end of transaction while
//	            nextTransaction = false;

	            /*
	             * the buffer is full or the transaction has been removed from
	             * the list of transactions to send and if that was the case the
	             * outgoingValidator won't make a false positive because it has been
	             * reset
	             */
	            outgoingDataValidator.parse(outData, byteCounter);
	            if (outgoingDataValidator.dataHasEndLine())
	            {
	                int rewindAmount =
	                    outgoingDataValidator.amount2Rewind();
	                t.rewind(rewindAmount);
	                t.interrupt();
	                byteCounter -= rewindAmount;
	                bytesToAccount -= rewindAmount;

                    int nrBytes = t.getEndLine(outData, byteCounter);
                    byteCounter += nrBytes;
                    bytesToAccount += nrBytes;

	                removeTransactionToSend(t);
	                outgoingDataValidator.reset();
	            }
	            /* 
				 * account for the bytes sent from this transaction if they should
	             * be accounted for
	             */
	            if (!t.isIncomingResponse()
	                && t.getTransactionType() == TransactionType.SEND
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
	                    transactionMessage.getReportMechanism().countSentBodyBytes(
	                        transactionMessage, bytesToAccount);
	                    bytesToAccount = 0;
	                }
	            }
	        }	// end of main while, the one that goes across transactions
        }
        return byteCounter;
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
     * Inserts transaction to send at the first interruptible spot in the queue.
     * Or appends it when a transaction is being processed and interrupts that
     * transaction.
     * 
     * It's responsible for appropriate queueing of REPORT and responses
     * 
     * @param transaction the REPORT or response transaction
     * @throws IllegalUseException if the transaction argument is invalid
     */
    public void addPriorityTransaction(Transaction transaction)
        throws IllegalUseException
    {
        // sanity check, shouldn't be needed:
        if (transaction.getTransactionType() != TransactionType.RESPONSE &&
            transaction.getTransactionType() != TransactionType.REPORT)
            throw new IllegalUseException("the addPriorityTransaction was" +
            		" called with a transaction that isn't a response/REPORT");
        if (transaction.getDirection() != Direction.OUT)
            throw new IllegalUseException(" the addPriorityTransaction was" +
	                " called with an invalid direction transaction, " +
	                "direction: " + transaction.getDirection());
        /*
         * Make sure that this response doesn't put itself ahead of other
         * priority transactions:
         */
        synchronized(this)
        {
	        for (int i = 0; i < transactionsToSend.size(); i++)
	        {
	            Transaction t = transactionsToSend.get(i);
	            if (t.isInterruptible())
	            {
	                if (i == 0 && t.hasSentData()) {
	                    addTransactionToSend(transaction, 1);
		                t.interrupt();
	                } else
	                    addTransactionToSend(transaction, i);
	                return;
	            }
	        }
	        // No interruptible transactions to send, just add the one given.
	        addTransactionToSend(transaction, UNIMPORTANT);
        }
    }

    public void interruptMessage(Message message) throws IllegalUseException
    {
    	synchronized (this)
    	{
	        for (Transaction t : transactionsToSend)
	            if (t.getTransactionType() == TransactionType.SEND &&
	                t.getMessage().equals(message) && t.isInterruptible())
                    t.interrupt();
    	}
    }

    /**
     * Remove any transactions of this message that were in the send queue.
     * @param message the message to abort.
     */
    public void abortMessage(Message message)
    {
    	synchronized(this)
    	{
    		boolean first = true;
    		for (Transaction t : transactionsToSend)
	            if (t.getTransactionType() == TransactionType.SEND &&
	                t.getMessage().equals(message))
	            {
	            	logger.debug(String.format("%s %s aborted.", this, t));
	            	if (first)
	            	{
	            		t.abort();
	            		first = false;
	            	}
	            	else
	            		removeTransactionToSend(t);
	            }
    	}
    }
}
