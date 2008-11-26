/* Copyright © João Antunes 2008
 This file is part of MSRP Java Stack.

    MSRP Java Stack is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    MSRP Java Stack is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with MSRP Java Stack.  If not, see <http://www.gnu.org/licenses/>.

 */
package msrp;

import msrp.Connection;
import msrp.Connections;
import msrp.Transaction;
import msrp.MSRPStack;
import msrp.Transaction.TransactionType;
import msrp.exceptions.ImplementationException;
import msrp.exceptions.InternalErrorException;
import msrp.utils.TextUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

/**
 * @author D
 */
class TransactionManager
    implements Observer
{

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
     * generates and queues the response of the given transaction based on the
     * Report header fields.
     * 
     * @param transaction
     */
    private void proccessResponse(Transaction transaction, int responseCode)
    {
    
        String failureReport = transaction.getFailureReport();
    
        // TODO generate the responses based on the success report field
    
        // generate the responses based on the failure report field
        if (failureReport == null || failureReport.equalsIgnoreCase("yes")
            || failureReport.equalsIgnoreCase("partial"))
        {
            if (failureReport != null
                && failureReport.equalsIgnoreCase("partial")
                && responseCode == 200)
                return;
            else
            {
                // Generate transaction response
                transaction.generateResponse(responseCode);
            }
    
            try
            {
                addPriorityTransaction(transaction);
            }
            catch (InternalErrorException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
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
     */
    private String uniqueTransaction()
    {
        return "";
    }

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
    protected ArrayList<Transaction> getTransactionsToSend()
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
    protected void r200(Transaction transaction)
    {
        IOInterface.debugln("called the r200 with transaction:"
            + transaction.getTID() + " message-id:"
            + transaction.getMessageID());

        if (!transaction.isIncomingResponse())
        {
            // since it's not a response, it is a request

            if (transaction.getTransactionType().equals(TransactionType.SEND))
            {
                // if this is a SEND request
                proccessResponse(transaction, 200);

                long[] byteRange = transaction.getByteRange();
                long totalBytes = transaction.getTotalMessageBytes();
                Message associatedMessage = transaction.getMessage();
                if (associatedMessage != null && associatedMessage.isComplete())
                {
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
                        long callCount =
                            associatedMessage.getCounter().getCount();

                        associatedMessage.getReportMechanism()
                            .triggerSuccessReport(associatedMessage,
                                transaction,
                                associatedMessage.lastCallReportCount,
                                callCount);
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
                String statusCodeString =
                    Integer.toString(transaction.getStatusHeader()
                        .getStatusCode());

                transaction.gotResponse(statusCodeString);
                // REMOVE FIXME TODO the implementation exception should
                // be handled like this?!
                try
                {
                    transaction.getSession().triggerReceivedReport(transaction);
                }
                catch (ImplementationException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }

        }

    }

    /**
     * TODO generates and dispatches a 400 response based on the failure report
     * field
     * 
     * @param transaction
     */
    protected boolean r400(Transaction transaction)
    {
        return false;
    }

    /**
     */
    protected boolean r403()
    {
        return false;
    }

    /**
     * @param transaction
     */
    protected boolean r408(Transaction transaction)
    {
        return false;
    }

    /**
     */
    protected boolean r413()
    {
        return false;
    }

    /**
     */
    protected boolean r415()
    {
        return false;
    }

    /**
     */
    protected boolean r423()
    {
        return false;
    }

    /**
     */
    protected boolean r481(Transaction transaction)
    {
        return false;
    }

    /**
     */
    protected boolean r501()
    {
        return false;
    }

    /**
     * @param transaction
     */
    protected boolean r506(Transaction transaction)
    {
        return false;
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
        connection.addObserver(this);
    }

    protected String generateNewTID()
    {
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
        IOInterface
            .debugln("Called the UPDATE of the Transaction Manager. received a transaction");

        if (connectionObservable.getClass().getName() != "msrp.Connection")
        {
            IOInterface
                .debugln("Error! TransactionManager was notified without the wrong type of object associated");
            return;
        }
        // Sanity check, check if this is the right type of Observable
        if (transactionObject.getClass().getName() != "msrp.Transaction")
        {
            IOInterface
                .debugln("Error! TransactionManager was notified with the wrong observable type");
            return;
        }

        Connection connection = (Connection) connectionObservable;
        Transaction transaction = (Transaction) transactionObject;

        /*
         * If the transaction is an incoming response, atm do nothing. TODO(?!)
         */
        if (transaction.isIncomingResponse())
            return;

        if (transaction.getTransactionType()
            .equals(TransactionType.UNSUPPORTED))
        {
            /*
             * "important" question: does this 501 response precedes the 506 or
             * not?! should the to-path also be checked before?!
             */
            r501();
            return;
        }

        /*
         * if it's a valid transaction call the r200 method otherwise ignore
         * this call
         */
        if (transaction.isValid())
            r200(transaction);
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
     * queue
     */
    protected void generateTransactionsToSend()
    {
        if (messageBeingSent == null)
            return;

        Transaction newTransaction =
            new Transaction(TransactionType.SEND, messageBeingSent, this);

        // Add the transaction to the known list of existing transactions
        // this is used to generate unique TIDs in the connection and to
        // be used when a response to a transaction is received
        existingTransactions.put(newTransaction.getTID(), newTransaction);
        transactionsToSend.add(newTransaction);
        // possibly split the message into several transactions

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
     * Method used by the connection object to retrieve the byte array of data
     * to be sent by the connection
     * 
     * Also it's at this level that the sending of bytes is accounted for
     * purposes of triggering the sendUpdateStatus and the prioritizer
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
            while (i < outData.length)
            {

                if (!t.hasData())
                {
                    /*
                     * Removing the given transaction from the queue of
                     * transactions to send
                     */
                    transactionsToSend.remove(t);
                    break;
                }
                else
                {
                    bytesToAccount++;
                    outData[i++] = t.get();
                }

            } // End of while, the transaction was removed from the transactions
            // to send list

            if (!t.isIncomingResponse()
                && t.transactionType.equals(TransactionType.SEND)
                && !t.hasResponse())
            {
                /*
                 * reporting the sent update status seen that this is an
                 * outgoing send request
                 */
                Message transactionMessage = t.getMessage();
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
     * @param transaction the REPORT or response transaction
     * @throws InternalErrorException if transaction does not contain a response
     */
    protected void addPriorityTransaction(Transaction transaction)
        throws InternalErrorException
    {
        // sanity check, shouldn't be needed:
        if (!transaction.hasResponse()
            && transaction.getTransactionType() != TransactionType.REPORT)
            throw new InternalErrorException("the addTransactionResponse"
                + "was called with a transaction that isn't a response/REPORT");
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
                        transactionsToSend.add(1, transaction);
                    else
                        transactionsToSend.add(i, transaction);
                    trans.interrupt();
                    generateTransactionsToSend();

                    return;
                }
            }

        }
        catch (IndexOutOfBoundsException e)
        {
            // There are no transaction to send, just add the one given
            transactionsToSend.add(transaction);
        }
    }

}
