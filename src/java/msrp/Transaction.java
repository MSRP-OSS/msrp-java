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

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import msrp.exceptions.*;

/**
 * TODO (?) the most adequate name here would be Request TODO(?) and the
 * transaction response solely as RequestResponse or Response TODO(?) from
 * transactionType change to requestType
 * 
 * @author D
 */
public class Transaction
{
    public enum TransactionType
    {

        /**
         * SEND, transaction associated with the method SEND
         */
        SEND,

        /**
         * REPORT, transaction associated with the method REPORT
         */
        REPORT,

        /**
         * UNSUPPORTED, represents the unsupported methods
         */
        UNSUPPORTED,

        /**
         * constructor, implicitly public
         */
        TransactionType()
        {
        }
    }

    /**
     * if this is a complete transaction Note: A transaction could be created
     * and being in the filling process and is only considered complete when
     * signaled by the connection class
     */
    private boolean completeTransaction = false;

    /**
     * On the process of construction of the transaction by parsing of strings
     * this variable is used to denote if we have completed the parsing of the
     * headers
     */
    private boolean headerComplete = false;

    /**
     * this variable is used to denote if this transaction has "content-stuff"
     * or not Used to know if one should add the extra CRLF after the data or
     * not
     */
    protected boolean hasContentStuff = false;

    /**
     * the method associated with this transaction we call it transactionType
     * 
     */
    protected TransactionType transactionType;

    /**
     * @return the transactionType
     */
    protected TransactionType getTransactionType()
    {
        return transactionType;
    }

    /**
     * the From-Path parsed to the Transaction containing the associated
     * From-Path URIs from left to right in a growing index order
     */
    private URI[] fromPath;

    /**
     * the To-Path parsed to the Transaction containing the associated To-Path
     * URIs from left to right in a growing index order
     */
    private URI[] toPath = null;

    /**
     * the message associated with this transaction
     */
    protected Message message = null;

    private String contentType;

    /**
     * Array that contains the offset of various pieces of the transaction: the
     * header; the content-stuff; the end of line; These are indexed by the
     * *INDEX constants
     */
    protected int[] offsetRead = new int[3];

    /**
     * Constant used to index the header in the offsetRead
     */
    protected static final int HEADERINDEX = 0;

    /**
     * Constant used to index the end line in the offsetRead
     */
    protected static final int ENDLINEINDEX = 1;

    /**
     * Constant used to index the data on the offsetRead
     */
    protected static final int DATAINDEX = 2;

    /**
     * two vector array that stores the information about the start and end,
     * respectively index 0 and 1, associated with the Byte-Range parsed to the
     * transaction the value -2 is reserved as unknown
     */
    private long[] byteRange = new long[2];

    /**
     * value associated with the Byte-Range parsed to the transaction refering
     * to the number of bytes of the body.
     * 
     * The values -2 and -1 are reserved as unknown and uninitialized
     * respectively
     */
    private long totalMessageBytes = -1;

    /**
     * the identifier of this transaction
     */
    protected String tID;

    /**
     * @uml.property name="_connection"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_transactions:msrp.Connection"
     */
    private msrp.Connection _connection = null;

    class TransactionResponse
    {
        private ByteBuffer content;

        private int responseCode = 0;

        protected TransactionResponse(Transaction transaction, int responseCode)
        {
            this.responseCode = responseCode;
            response = this;
            String contentString =
                "MSRP " + tID + " " + responseCode + "\r\n" + "To-Path: "
                    + fromPath[fromPath.length - 1] + "\r\n" + "From-Path: "
                    + toPath[toPath.length - 1] + "\r\n" + "-------" + tID
                    + "$\r\n";
            byte[] contentBytes = contentString.getBytes(usascii);
            content = ByteBuffer.wrap(contentBytes);
            content.rewind();
        }

        public byte get()
        {
            return content.get();
        }

        public boolean hasData()
        {
            return content.hasRemaining();
        }

    }

    private TransactionResponse response = null;

    /**
     * Field representing the "Failure-report" field which has a default value
     * of "yes" if it's not present
     */
    private String failureReport = "yes";

    /**
     * @return the failureReport
     */
    public String getFailureReport()
    {
        return failureReport;
    }

    /**
     * @return true if the success report value of the header of this
     *         transaction is yes or false otherwise or if it's ommited
     */
    public boolean getSuccessReport()
    {
        return successReport;
    }

    /**
     * Field that represents the value of the success report by default and if
     * is omitted is considered false
     */
    private boolean successReport = false;

    /**
     * Explicit super constructor
     */
    protected Transaction()
    {
    }

    /**
     * Constructor called when there is an incoming transaction
     * 
     * @param tid
     * @param method
     */
    public Transaction(String tid, TransactionType method,
        TransactionManager manager)
    {
        transactionManager = manager;
        offsetRead[HEADERINDEX] =
            offsetRead[ENDLINEINDEX] = offsetRead[DATAINDEX] = 0;
        byteRange[0] = byteRange[1] = totalMessageBytes = UNINTIALIZED;
        transactionType = method;
        setTID(tid);
        initializeDataStructures();
    }

    /**
     * Constructor used to send new simple short transactions used for one
     * transaction messages
     * 
     * 
     * @param send
     * @param messageBeingSent
     * @param manager
     */
    public Transaction(TransactionType send, Message messageBeingSent,
        TransactionManager manager)
    {
        transactionType = send;
        this.transactionManager = manager;
        tID = manager.generateNewTID();
        message = messageBeingSent;
        if (transactionType.equals(TransactionType.SEND))
        {
            if (messageBeingSent.isComplete())
                throw new IllegalArgumentException("The constructor of "
                    + "this transaction was called with a completely"
                    + "sent message");
            Session session = messageBeingSent.getSession();
            ArrayList<URI> uris = session.getToPath();
            URI toPathUri = uris.get(0);
            URI fromPathUri = session.getURI();
            String messageID = messageBeingSent.getMessageID();

            String headerString =
                new String("MSRP " + tID + " SEND\r\n" + "To-Path: "
                    + toPathUri.toASCIIString() + "\r\n" + "From-Path: "
                    + fromPathUri.toASCIIString() + "\r\n" + "Message-ID: "
                    + messageID + "\r\n");
            if (messageBeingSent.getSuccessReport())
                headerString = headerString.concat("Success-Report: yes\r\n");

            if (!messageBeingSent.getFailureReport().equalsIgnoreCase("yes"))
                /* note: if omitted the failure report is assumed to be yes */
                headerString =
                    headerString.concat("Failure-Report: "
                        + messageBeingSent.getFailureReport() + "\r\n");

            /*
             * assert if either we are in the case of an interruptible
             * transaction or not and fill the Byte-Range fields and other
             * internal fields accordingly
             */
            if (headerBytes.length + (message.getSize() - message.bytesSent())
                + (7 + tID.length() + 1) > MSRPStack.MAXIMUMUNINTERRUPTIBLE)
            {
                interruptible = true;
                headerString = headerString.concat("Byte-Range: 1-*" + "/"
                    + messageBeingSent.getStringTotalSize() + "\r\n"
                    + "Content-Type: " + messageBeingSent.getContentType()
                    + "\r\n\r\n");
            }
            else
            {
                headerString =
                    headerString.concat("Byte-Range: 1-"
                        + messageBeingSent.getStringTotalSize() + "/"
                        + messageBeingSent.getStringTotalSize() + "\r\n"
                        + "Content-Type: " + messageBeingSent.getContentType()
                        + "\r\n\r\n");

            }
            headerBytes = headerString.getBytes(usascii);

            /* by default have the continuation flag to be the end of message */
            continuationFlagByte = ENDMESSAGE;
            initializeDataStructures();
        }

    }

    /**
     * Value that represents the $ char in usascii used to flag the end of a
     * message in a transaction
     */
    protected static final byte ENDMESSAGE = 36;

    /**
     * Value that represents the + char in usascii used to flag the interruption
     * of a message in a transaction
     */
    protected static final byte INTERRUPT = 43;

    /**
     * Value that represents the # char in usascii used to flag the abort of a
     * message in a transaction
     */
    protected static final byte ABORTMESSAGE = 35;

    /**
     * Getter of the property <tt>_connection</tt>
     * 
     * @return Returns the _connection.
     * @uml.property name="_connection"
     */
    public msrp.Connection get_connection()
    {
        return _connection;
    }

    private static final int ALLBYTES = 0;

    private int offsetBody = 0;

    /**
     * Method called by the constructors to initialize data structures as
     * needed.
     * 
     * Currently uses this transaction's TransactionType to assert if there is
     * need to reserve space for this transaction body
     */
    private void initializeDataStructures()
    {
        if (!transactionType.equals(TransactionType.SEND))
        {
            bodyBytes = new byte[MSRPStack.MAXNONSENDBODYSIZE];
            bodyByteBuffer = ByteBuffer.wrap(bodyBytes);
        }

    }

    /**
     * 
     * Retrieves the data associated with the body of this transaction
     * 
     * @param size the number of bytes or zero for the whole data
     * @return an array of bytes with the transaction's body or null if it
     *         doesn't exist
     * @throws InternalErrorException if there was some kind of exception this
     *             exception is thrown with the triggering exception within
     */

    protected byte[] getBody(int size) throws InternalErrorException
    {
        if (!transactionType.equals(TransactionType.SEND))
        {
            if (size == ALLBYTES)
            {
                bodyByteBuffer.flip();
                byte[] returnData = new byte[realChunkSize];
                bodyByteBuffer.get(returnData);
                return returnData;
            }
            byte[] dst = new byte[size];
            int i = 0;
            for (; i < dst.length; i++)
            {
                if (bodyByteBuffer.hasRemaining())
                    dst[i] = bodyByteBuffer.get();
            }
            offsetBody += i;
            return dst;

        }
        else if (transactionType.equals(TransactionType.SEND)
            && !isIncomingResponse())
        {
            DataContainer dc = message.getDataContainer();
            ByteBuffer auxByteBuffer;
            try
            {
                if (byteRange[0] == UNINTIALIZED || byteRange[0] == UNKNOWN)
                    throw new InternalErrorException("the limits of this "
                        + "this transaction are unknown/unintialized "
                        + "can't satisfy request");
                long startingOffset = byteRange[0] - 1;
                if (size == ALLBYTES)
                    auxByteBuffer =
                        dc.get(startingOffset, byteRange[1] - (startingOffset));
                else
                    auxByteBuffer = dc.get(startingOffset, size);
            }
            catch (Exception e)
            {

                // TODO log it
                throw new InternalErrorException(e);
            }

            return auxByteBuffer.array();

        }

        return null;

    }

    /**
     * @uml.property name="_transactionManager"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_transactions:msrp.TransactionManager"
     */
    protected msrp.TransactionManager transactionManager = null;

    private String messageID = null;

    /**
     * Getter of the property <tt>_transactionManager</tt>
     * 
     * @return Returns the manager.
     * @uml.property name="_transactionManager"
     */
    protected msrp.TransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    /**
     * Setter of the property <tt>_transactionManager</tt>
     * 
     * @param _transactionManager The manager to set.
     * @uml.property name="_transactionManager"
     */
    protected void setTransactionManager(msrp.TransactionManager manager)
    {
        this.transactionManager = manager;
    }

    /**
     * Setter of the property <tt>_connection</tt>
     * 
     * @param _connection The _connection to set.
     * @uml.property name="_connection"
     */
    public void set_connection(msrp.Connection _connection)
    {
        this._connection = _connection;
    }

    private void setTID(String tid)
    {
        tID = tid;
    }

    public String getTID()
    {

        return tID;
    }

    private StringBuffer headerBuffer = new StringBuffer();

    protected byte[] headerBytes = new byte[MAXHEADERBYTES];

    /**
     * if this is a valid transaction or if it has any problem with it assume
     * for starters it's always valid
     */
    private boolean validTransaction = true;

    private static final int UNKNOWN = -2;

    private static final int UNINTIALIZED = -1;

    private static final int NOTFOUND = -1;

    /**
     * Maximum number of bytes allowed for the header data strings (so that we
     * don't have a DoS by memory exaustion)
     * 
     */
    private static final int MAXHEADERBYTES = 2024;

    /**
     * Adds the given data to the buffer checking if it already doesn't exceed
     * the maximum limit of bytes without an \r\n in that case an Exception is
     * thrown
     * 
     * @param charToAdd the string to add to the buffer used for storage of
     *            complete lines for analyzing posteriorly
     * @throws InvalidHeaderException if MAXBYTES would be passed with the
     *             addition of stringToAdd
     */
    private void addHeaderBuffer(char charToAdd) throws InvalidHeaderException
    {

        if (1 + headerBuffer.length() > MAXHEADERBYTES)
            throw new InvalidHeaderException("Trying to parse a line of "
                + (1 + headerBuffer.length()) + " bytes" + "when the limit is "
                + MAXHEADERBYTES);
        else
        {
            headerBuffer.append(charToAdd);
        }

    }

    /**
     * @return true if the headerBuffer has all of the data of the header
     */
    private boolean hasCompleteHeaderBuffer()
    {
        Matcher endOfHeaderMatcher;
        if (isIncomingResponse())
        {
            /*
             * in the case we are dealing with an incoming response the header
             * ends with the from-path last uri and \r\n
             */
            // TODO support multiple From-Path URIs
            Pattern endOfHeaderWithoutContentStuff =
                Pattern.compile(
                    "(^To-Path:) (.{7,120})(\r\n)(From-Path:) (.{7,})(\r\n)",
                    Pattern.DOTALL);
            endOfHeaderMatcher =
                endOfHeaderWithoutContentStuff.matcher(headerBuffer);

            if (endOfHeaderMatcher.matches())
            {
                return true;
            }
            return false;
        }
        /* In the case that his is a transaction with 'content-stuff' */
        Pattern endOfHeaderWithContentStuff =
            Pattern.compile("(.*)(\r\n){2}(.*)", Pattern.DOTALL);
        endOfHeaderMatcher = endOfHeaderWithContentStuff.matcher(headerBuffer);
        if (endOfHeaderMatcher.matches())
            return true;
        return false;
    }

    private StatusHeader statusHeader = null;

    public StatusHeader getStatusHeader()
    {
        return statusHeader;
    }

    /**
     * Retrieves the first line it encounters on the headerBuffer that is going
     * to be processed by the calling method the line is removed from the buffer
     * 
     * @return a string with the line including the \r\n chars or null if none
     *         found
     */
    private String getLineHeaderBuffer()
    {
        try
        {

            String stringToReturn =
                headerBuffer.substring(0, headerBuffer.indexOf("\r\n"));
            headerBuffer.delete(0, headerBuffer.indexOf("\r\n")
                + "\r\n".length());
            return stringToReturn.concat("\r\n");
        }
        catch (StringIndexOutOfBoundsException e)
        {
            return null;
        }

    }

    /**
     * Method that takes into account the validTransaction field of the
     * transaction and other checks in order to assert if this is a valid
     * transaction or not.
     * 
     * TODO complete this method so that it catches any incoherences with the
     * protocol syntax. All of the syntax problems should be found at this point
     * 
     * TODO Semantic validation: validate that the mandatory headers, regarding,
     * the method are present.
     * 
     * TODO check to see if there is any garbage on the transaction (bytes
     * remaining in the headerBuffer that aren't assigned to any valid field ?!)
     * 
     * 
     * @return
     */
    private void validate()
    {
        // TODO Auto-generated method stub
        return;
    }

    private MSRPStack instanceStack = MSRPStack.getInstance();

    /**
     * The session associated with this transaction
     */
    private Session session;

    /**
     * When the method is called, the transaction should always have a session
     * associated with it
     * 
     * @return the associated session
     * @throws ImplementationException if this transaction has no session
     *             associated with it
     */
    protected Session getSession() throws ImplementationException
    {
        if (session == null)
            throw new ImplementationException("Transaction had no session"
                + " associated!");
        return session;
    }

    protected boolean isValid()
    {
        return validTransaction;

    }

    /**
     * This method's main purpose is to assign a session and a message to the
     * transaction as soon as the headers are complete
     * 
     * method called whenever the transaction's headers are complete it's used
     * to validate the headers and to generate the needed responses ASAP. (It
     * also permits one to start receiving the body already knowing which
     * session it belongs, besides also generating the responses alot sooner)
     * 
     * TODO FIXME revise comments
     */

    private void proccessHeader()
    {
        /*
         * if this is a test (the connection of the TransactionManager is null)
         * then skip this step! (THIS IS ONLY HERE FOR DEBUG PURPOSES) FIXME
         * Ideally we would have a mock connection!! TODO (?!)
         */
        if (transactionManager.getConnection() == null)
        {
            IOInterface.debugln("DEBUG MODE this line should only"
                + "appear if the transaction is a dummy one!");
            return;
        }

        /*
         * If the transaction is an incoming response, atm do nothing. TODO(?!)
         */
        if (isIncomingResponse())
            return;

        if (getTransactionType().equals(TransactionType.UNSUPPORTED))
        {
            /* TODO if this isn't a valid method send back a 501 response */
            /*
             * "important" question: does this 501 response precedes the 506 or
             * not?! should the to-path also be checked before?!
             */
            return;
        }
        validate();

        /* make sure that a message is valid (originates 400 responses) */
        if (!isValid())
        {
            transactionManager.r400(this);
        }

        if (transactionManager.associatedSession((getToPath())[0]) == null)
        {
            // It doesn't have this session associated
            // go see if there is one in the list of the yet to be validated in
            // connections
            Connections connectionsInstance =
                MSRPStack.getConnectionsInstance(transactionManager
                    .getConnection().getIpAddress());
            Session newSession =
                connectionsInstance.sessionToIdentify((getToPath())[0]);
            if (newSession == null)
            {
                /*
                 * if there are no sessions associated with this transaction
                 * manager and also no sessions available to identify associated
                 * with the ToPath URI we have one of two cases: - either this
                 * transaction belongs to another active session (give a 506
                 * response) - or this session doesn't exist at all (give a 481
                 * response)
                 */
                if (instanceStack.isActive((getToPath())[0]))
                {
                    transactionManager.r506(this);
                }
                else
                {
                    transactionManager.r481(this);

                }
            }
            else
            {
                /*
                 * if it was found a session in the toIdentify list
                 */
                if (instanceStack.isActive((getToPath())[0]))
                {
                    /*
                     * but also with another, then give the r506 response and
                     * log this rare event! (that shouldn't have happened)
                     */
                    transactionManager.r506(this);

                    // TODO log it:
                    IOInterface.debugln("Error! received a request"
                        + " that is yet to identify and is associated with"
                        + " another session!");
                    return;
                }
                /*
                 * associate this session with this transaction manager and
                 * remove it from the list of sessions yet to be identified
                 */
                connectionsInstance.identifiedSession(newSession);
                this.session = newSession;
                transactionManager.addSession(newSession);
                associateMessage();

            }
        }
        else
        {
            /*
             * this is one of the sessions for which this transaction manager is
             * responsible
             */
            Session sessionToAssociate =
                transactionManager.associatedSession((getToPath())[0]);
            this.session = sessionToAssociate;
            associateMessage();

        }

    }

    /**
     * Associates this session with the given messageID. If this is a send
     * request: If this message doesn't exist on the context of the session then
     * it gets created. It is assumed that this.session is different from null
     * If this is a report request if a message can't be found the transaction
     * is rendered invalid and it gets logged, the message is set to null
     * 
     * @param messageID the message-ID of the Message to associate
     */
    private void associateMessage()
    {
        message = session.getSentMessage(messageID);
        if (message == null)
        {
            if (this.transactionType.equals(TransactionType.SEND))
            {
                message =
                    new IncomingMessage(session, messageID, this.contentType,
                        totalMessageBytes);
                IncomingMessage incomingMessage = (IncomingMessage) message;
                message.setSuccessReport(successReport);
                try
                {
                    message.setFailureReport(failureReport);
                }
                catch (IllegalUseException e1)
                {
                    // TODO log it
                    // TODO invalidate this transaction and
                    // trigger the appropriate response
                    e1.printStackTrace();
                }

                boolean result = session.triggerAcceptHook(incomingMessage);
                if (result && incomingMessage.result != 200)
                {
                    incomingMessage.result = 200;
                    /*
                     * if the user didn't assigned a data container to the
                     * message then we discard the message and log the
                     * occurrence
                     */
                    if (incomingMessage.dataContainer == null)
                    {
                        result = false;
                        /*
                         * TODO log it Log the fact that the user got his
                         * message discarded because he didn't filled out the
                         * dataContainer field
                         */

                    }

                }
                if (!result)
                {
                    /* The message is to be discarded! */
                    this.validTransaction = false;
                    this.completeTransaction = true;
                    this.generateResponse(incomingMessage.result);
                    try
                    {
                        transactionManager.addPriorityTransaction(this);
                    }
                    catch (InternalErrorException e)
                    {
                        // TODO log it
                        e.printStackTrace();
                    }
                }

            }

            if (this.transactionType.equals(TransactionType.REPORT))
            {
                validTransaction = false;
                /*
                 * the RFC tells us to silently ignore the request if no message
                 * can be associated with it so we'll just log it
                 */
                // TODO log the warning
                IOInterface.debugln("Warning! incoming report request"
                    + " for an unknown message to the stack. " + "Message-ID: "
                    + getMessageID());
            }
        }

    }

    /**
     * Used to parse the raw data between the connection and the transaction it
     * identifies the header and fills the body if existing Also it should find
     * eventual errors on the data received and generate an 400 response throw
     * an (exception or other methods ?!) This method is also responsible for
     * accounting for the data received calling the appropriate functions in the
     * ReportMechanism
     * 
     * @param incData the data to parse to the transaction
     * @param offset the starting point to be parsed on the given toParse array
     * @param length the number of bytes to be considered starting at the offset
     *            position
     * @param receivingBinaryData tells the parse method if the data in the
     *            incData is binary or usascii text
     * @throws InvalidHeaderException if an error was found with the parsing of
     *             the header
     * @throws ImplementationException this is here for debug purposes mainly
     * @see ReportMechanism#countReceivedBodyBlock(Message, Transaction, long,
     *      int)
     */
    public void parse(byte[] incData, int offset, int length,
        boolean receivingBinaryData)
        throws InvalidHeaderException,
        ImplementationException
    {
        /*
         * TODO/CLEANUP: remove the extra code when it's not receiving binary
         * data because there are probably some lines that are unreachable
         * because now the data part of the message is always treated as binary
         */
        if (!receivingBinaryData)
        {
            String toParse = new String(incData, offset, length, usascii);
            // Trims and assembles the received data via the toParse string
            // so that we get a buffer with the whole headers before we try to
            // analyze it

            // if the transaction is marked as complete or invalid, calls to
            // this
            // method will do nothing
            if (!validTransaction)
                return;
            if (completeTransaction)
                /*
                 * it's rather odd that we have a complete transaction and we
                 * are still parsing data to it, so throw an exception
                 */
                throw new ImplementationException(
                    "Error: trying to parse data to"
                        + "a complete transaction!");
            int i = 0;
            while (i < toParse.length())
            {
                if (!headerComplete)
                {
                    try
                    {
                        for (; i < toParse.length()
                            && !hasCompleteHeaderBuffer(); i++)
                        {
                            addHeaderBuffer(toParse.charAt(i));
                        }
                        if (hasCompleteHeaderBuffer())
                        {
                            recognizeHeader();
                            proccessHeader();
                            // TODO (?!) implement the recognition of an
                            // existence
                            // of a body (by looking at the headers to see if
                            // content-stuff exists)
                            // and do the next two calls only in such case (?!)
                            headerComplete = true;
                        }
                    }
                    catch (Exception e)
                    {
                        validTransaction = false;
                        e.printStackTrace();
                        return;
                    }

                }// if (!headercomplete)
                if (headerComplete)
                {
                    /*
                     * if we don't have a valid transaction at this point,
                     * return
                     */
                    if (!isValid())
                        return;
                    try
                    {
                        /*
                         * the local variable used to convert the string to
                         * byte[] FIXME (?!) should we be receiving byte [] at
                         * this point?
                         */
                        byte[] byteData;
                        if (!isIncomingResponse() && message != null
                            && transactionType.equals(TransactionType.SEND))
                        {
                            long startingIndex = realChunkSize;
                            while (toParse.length() - i != 0)
                            {

                                if (message.getReportMechanism()
                                    .getTriggerGranularity() >= toParse
                                    .length()
                                    - i)
                                {
                                    /*
                                     * put all of the remaining data on the data
                                     * container and update the realchunksize,
                                     * account the reported bytes (that
                                     * automatically calls the trigger TODO)
                                     */
                                    byteData =
                                        toParse.substring(i).getBytes(usascii);
                                    message.getDataContainer().put(
                                        startingIndex, byteData);
                                    realChunkSize += byteData.length;
                                    message.getReportMechanism()
                                        .countReceivedBodyBlock(message, this,
                                            startingIndex, byteData.length);
                                    i += byteData.length;
                                }
                                else
                                {
                                    byteData =
                                        toParse.substring(
                                            i,
                                            i
                                                + (message.getReportMechanism()
                                                    .getTriggerGranularity()))
                                            .getBytes(usascii);
                                    message.getDataContainer().put(
                                        startingIndex, byteData);
                                    realChunkSize += byteData.length;
                                    message.getReportMechanism()
                                        .countReceivedBodyBlock(message, this,
                                            startingIndex, byteData.length);
                                    i += byteData.length;
                                    startingIndex += byteData.length;

                                }
                            }
                        }
                        else
                        {
                            byteData = toParse.substring(i).getBytes(usascii);
                            if (byteData.length > 0)
                            {
                                bodyByteBuffer.put(byteData);
                                realChunkSize += byteData.length;
                                i += byteData.length;
                            }
                            else if (bodyByteBuffer == null)
                            {
                                bodyByteBuffer = ByteBuffer.wrap(byteData);
                            }

                        }
                    }
                    catch (Exception e)
                    {
                        // TODO log it
                        e.printStackTrace();
                        // FIXME ?! give a 400 response?!
                        this.generateResponse(400);
                        try
                        {
                            transactionManager.addPriorityTransaction(this);
                        }
                        catch (InternalErrorException e1)
                        {
                            // TODO log it
                            e1.printStackTrace();
                        }
                    }
                }

                if (completeTransaction)
                    break;
            }

        } // if (!receivingBinaryData)
        else
        {
            ByteBuffer incByteBuffer = ByteBuffer.wrap(incData, offset, length);

            if (headerComplete)
            {
                /*
                 * if we don't have a valid transaction at this point, return
                 */
                if (!isValid())
                    return;
                try
                {
                    /*
                     * the local variable used to convert the string to byte[]
                     * FIXME (?!) should we be receiving byte [] at this point?
                     */
                    byte[] byteData;
                    if (!isIncomingResponse() && message != null
                        && transactionType.equals(TransactionType.SEND))
                    {
                        /*
                         * if this isn't an incoming response, has a message
                         * associated with it and it's actually a SEND method
                         */
                        long startingIndex = realChunkSize;
                        while (incByteBuffer.hasRemaining())
                        {

                            if (message.getReportMechanism()
                                .getTriggerGranularity() >= incByteBuffer
                                .limit()
                                - incByteBuffer.position())
                            {
                                /*
                                 * put all of the remaining data on the data
                                 * container and update the realchunksize,
                                 * account the reported bytes (that
                                 * automatically calls the trigger TODO)
                                 */
                                byteData = new byte[incByteBuffer.remaining()];
                                incByteBuffer.get(byteData);
                                message.getDataContainer().put(startingIndex,
                                    byteData);
                                realChunkSize += byteData.length;
                                message.getReportMechanism()
                                    .countReceivedBodyBlock(message, this,
                                        startingIndex, byteData.length);
                                startingIndex += byteData.length;
                            }
                            else
                            {
                                byteData =
                                    new byte[message.getReportMechanism()
                                        .getTriggerGranularity()];
                                incByteBuffer.get(byteData, 0, message
                                    .getReportMechanism()
                                    .getTriggerGranularity());
                                message.getDataContainer().put(startingIndex,
                                    byteData);
                                realChunkSize += byteData.length;
                                message.getReportMechanism()
                                    .countReceivedBodyBlock(message, this,
                                        startingIndex, byteData.length);
                                startingIndex += byteData.length;

                            }
                        }
                    }// if (!isIncomingResponse() && message != null
                    // && transactionType.equals(TransactionType.SEND))
                    else
                    {
                        byteData = new byte[incByteBuffer.remaining()];
                        incByteBuffer.get(byteData);
                        if (byteData.length > 0)
                        {
                            bodyByteBuffer.put(byteData);
                            realChunkSize += byteData.length;
                        }
                        else if (bodyByteBuffer == null)
                        {
                            bodyByteBuffer = ByteBuffer.wrap(byteData);
                        }

                    }
                }
                catch (Exception e)
                {
                    // TODO log it
                    e.printStackTrace();
                    // FIXME ?! give a 400 response?!
                    this.generateResponse(400);
                    try
                    {
                        transactionManager.addPriorityTransaction(this);
                    }
                    catch (InternalErrorException e1)
                    {
                        // TODO log it
                        e1.printStackTrace();
                    }
                }
            }

        }
    }

    /**
     * will recognize the header stored on headerBuffer initializing all of the
     * variables related to the header and checking for some violations of the
     * protocol
     * 
     * @throws InvalidHeaderException if it's found that the header is invalid
     *             for some reason
     * 
     */
    private void recognizeHeader() throws InvalidHeaderException
    {
        // (msrps|MSRPS|msrp|MSRP){1}://((\d){3}\.(\d){3}
        // the header should start with the To-Path containing one URI, going to
        // extract it:

        // If the characters aren't all ascii send an invalid header
        /*
         * To note, all of the "" in the formal syntax of the ABNF is compared
         * case insensitive, hence all the patterns here should have it (See rfc
         * 4975 and rfc 4234)
         */
        Pattern asciiPattern = Pattern.compile("\\p{ASCII}+");
        Matcher asciiMatcher = asciiPattern.matcher(headerBuffer);
        if (!asciiMatcher.matches())
            throw new InvalidHeaderException(
                "Error, non-ascii characters contained in the header");

        // TODO support multiple From-Path URIs
        Pattern generalTransactionFields =
            Pattern
                .compile(
                    "(^To-Path:) (.{7,120})(\r\n)(From-Path:) (.{7,})(\r\n)(\\p{ASCII}*)",
                    Pattern.CASE_INSENSITIVE);
        Matcher toAndFromMatcher =
            generalTransactionFields.matcher(headerBuffer);
        if (!toAndFromMatcher.matches())
        {
            throw new InvalidHeaderException(
                "Error, transaction doesn't have a valid to-path and from-path headers");
        }
        IOInterface.debugln("Group:" + toAndFromMatcher.group(1) + ":Groupend");
        IOInterface.debugln("Group:" + toAndFromMatcher.group(2) + ":Groupend");
        IOInterface.debugln("Group:" + toAndFromMatcher.group(3) + ":Groupend");
        IOInterface.debugln("Group:" + toAndFromMatcher.group(4) + ":Groupend");
        IOInterface.debugln("Group:" + toAndFromMatcher.group(5) + ":Groupend");
        IOInterface.debugln("Group:" + toAndFromMatcher.group(6) + ":Groupend");
        IOInterface.debugln("Group:" + toAndFromMatcher.group(7) + ":Groupend");
        String yetToBeParsed = toAndFromMatcher.group(7);
        URI[] newToPath = new URI[1];
        try
        {
            newToPath[0] = URI.create(toAndFromMatcher.group(2));
            setToPath(newToPath);

        }
        catch (Exception e)
        {
            throw new InvalidHeaderException(
                "Problems parsing the to-path URI", e);
        }

        try
        {
            // TODO support multiple From-Path URIs
            URI[] fromPath = new URI[1];
            IOInterface.debugln("FromURI:" + toAndFromMatcher.group(5)
                + ":FromURI");
            fromPath[0] = URI.create(toAndFromMatcher.group(5));
            setFromPath(fromPath);
        }
        catch (Exception e)
        {
            throw new InvalidHeaderException(
                "Problems parsing the from-path URI(s)", e);
        }
        // If we are receiving a response the processing ends here:
        if (isIncomingResponse())
            return;
        // Method specific headers:
        switch (transactionType)
        {
        case REPORT:
        case SEND:
            /* Message-ID processing: */
            Pattern messageIDPattern =
                Pattern
                    .compile(
                        "(.*)(Message-ID:) ((\\p{Alnum}|\\.|\\-|\\+|\\%|\\=){3,31})(\r\n)(.*)",
                        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher messageIDMatcher = messageIDPattern.matcher(yetToBeParsed);
            if (messageIDMatcher.matches())
            {
                // TODO get the corresponding message from the messageID or
                // create a new one
                messageID = messageIDMatcher.group(3);
                yetToBeParsed =
                    (new String()).concat(messageIDMatcher.group(1));
                yetToBeParsed = yetToBeParsed.concat(messageIDMatcher.group(6));
            }
            else
            {
                throw new InvalidHeaderException("Error, messageID not found");
            }

            /* Byte-Range processing: */
            Pattern byteRangePattern =
                Pattern
                    .compile(
                        "(.*)(Byte-Range:) (\\p{Digit}+)-(\\p{Digit}+|\\*)/(\\p{Digit}+|\\*)(\r\n)(.*)",
                        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher byteRangeMatcher = byteRangePattern.matcher(yetToBeParsed);
            if (byteRangeMatcher.matches())
            {
                try
                {
                    byteRange[0] = Integer.parseInt(byteRangeMatcher.group(3));
                    if (byteRangeMatcher.group(4).contentEquals("*"))
                    {
                        byteRange[1] = UNKNOWN;
                    }
                    else
                    {
                        byteRange[1] =
                            Integer.parseInt(byteRangeMatcher.group(4));
                    }
                    if (byteRangeMatcher.group(5).contentEquals("*"))
                    {
                        totalMessageBytes = UNKNOWN;
                    }
                    else
                    {
                        totalMessageBytes =
                            Integer.parseInt(byteRangeMatcher.group(5));
                    }
                }
                catch (NumberFormatException e)
                {
                    throw new InvalidHeaderException(
                        "Error, erroneously parsed part of the Byte-Range field as an INT",
                        e);
                }

            }

            /* Content-Type processing: */
            // TODO change this pattern in order to match the one in the Formal
            // Syntax that is a more complex one
            // due to the possible existence of sub-types
            // to note token on MSRP RFC equals any of the following characters:
            // !#$%&'*+-.[0-9][A-Z][^-~]
            // Pattern contentTypePattern = Pattern.compile(
            // "(.*)(Content-Type:) (([!#$%&'*+\\-.][0-9][A-Z][^-~]){1,30}/([!#$%&'*+-.][0-9][A-Z][^-~]){1,30})(\r\n)(.*)"
            // , Pattern.DOTALL);
            Pattern contentTypePattern =
                Pattern
                    .compile(
                        "(.*)(Content-Type:) (\\p{Alnum}{1,30}/\\p{Alnum}{1,30})(\r\n)(.*)",
                        Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher contentTypeMatcher =
                contentTypePattern.matcher(yetToBeParsed);
            if (contentTypeMatcher.matches())
            {
                IOInterface.debugln("Group:" + contentTypeMatcher.group(1)
                    + ":Groupend");
                IOInterface.debugln("Group:" + contentTypeMatcher.group(2)
                    + ":Groupend");
                IOInterface.debugln("Group:" + contentTypeMatcher.group(3)
                    + ":Groupend");
                IOInterface.debugln("Group:" + contentTypeMatcher.group(4)
                    + ":Groupend");
                IOInterface.debugln("Group:" + contentTypeMatcher.group(5)
                    + ":Groupend");
                this.contentType = contentTypeMatcher.group(3);
            }

            /* Failure-Report processing: */

            Pattern failureReportErrorPattern =
                Pattern.compile("(.*)(Failure-Report:)(.*)", Pattern.DOTALL
                    | Pattern.CASE_INSENSITIVE);
            Pattern failureReportPattern =
                Pattern.compile(
                    "(.*)(Failure-Report: )(yes|no|partial){1}(\r\n)(.*)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher failureReportErrorMatcher =
                failureReportErrorPattern.matcher(yetToBeParsed);
            Matcher failureReportMatcher =
                failureReportPattern.matcher(yetToBeParsed);
            if (failureReportMatcher.matches())
            {
                // We found ourselves a valid header field
                failureReport = failureReportMatcher.group(3);
                yetToBeParsed =
                    failureReportMatcher.group(1).concat(
                        failureReportMatcher.group(5));
            }
            else if (failureReportErrorMatcher.matches())
            {
                /* we might have found an invalid syntax failure report field */
                throw new InvalidHeaderException("Processing "
                    + "Failure-Report failed");
            }

            /* Success-Report processing: */
            Pattern successReportPattern =
                Pattern.compile("(.*)(Success-Report: )(yes|no){1}(\r\n)(.*)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            Matcher successReportMatcher =
                successReportPattern.matcher(yetToBeParsed);

            Pattern successReportErrorPattern =
                Pattern.compile("(.*)(Success-Report:)(.*)", Pattern.DOTALL
                    | Pattern.CASE_INSENSITIVE);
            Matcher successReportErrorMatcher =
                successReportErrorPattern.matcher(yetToBeParsed);
            if (successReportMatcher.matches())
            {
                // We found ourselves a valid header field
                try
                {
                    successReport =
                        parserSuccessReport(successReportMatcher.group(3));
                }
                catch (ProtocolViolationException e)
                {
                    // TODO Auto-generated catch block logit!
                    IOInterface.debugln(e.getMessage());

                }
                yetToBeParsed =
                    successReportMatcher.group(1).concat(
                        successReportMatcher.group(5));
            }
            else if (successReportErrorMatcher.matches())
            {
                /* we might have found an invalid syntax success report field */
                throw new InvalidHeaderException("Processing "
                    + "Success-Report failed");
            }

            /* Report request specific headers: */
            if (transactionType.equals(TransactionType.REPORT))
            {

                /* Status: processing: */

                // TODO FIXME alter the pattern so that it recognizes also the
                // comments as specified in the formal syntax of rfc4975
                Pattern statusHeaderPattern =
                    Pattern
                        .compile(
                            "(.*)(Status:) (\\p{Digit}{3}) (\\p{Digit}{3})(\r\n)(.*)",
                            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
                Pattern statusHeaderErrorPattern =
                    Pattern.compile("(.*)(Status:)(.*)", Pattern.DOTALL
                        | Pattern.CASE_INSENSITIVE);
                Matcher statusHeaderMatcher =
                    statusHeaderPattern.matcher(yetToBeParsed);
                Matcher statusHeaderErrorMatcher =
                    statusHeaderErrorPattern.matcher(yetToBeParsed);

                if (statusHeaderMatcher.matches())
                {
                    String namespace = statusHeaderMatcher.group(3);
                    String statusCode = statusHeaderMatcher.group(4);
                    String comment = null;
                    statusHeader =
                        new StatusHeader(namespace, statusCode, comment);
                    yetToBeParsed =
                        statusHeaderMatcher.group(1).concat(
                            statusHeaderMatcher.group(6));

                }
                else if (statusHeaderErrorMatcher.matches())
                {
                    /* we might have found an invalid syntax status header field */
                    throw new InvalidHeaderException("Processing "
                        + "Status failed");

                }

            }

            break;
        case UNSUPPORTED:
            // TODO
            break;

        }

    }

    private boolean parserSuccessReport(String successReportValue)
        throws ProtocolViolationException
    {
        if (successReportValue.equalsIgnoreCase("yes"))
            return true;
        if (successReportValue.equalsIgnoreCase("no"))
            return false;
        throw new ProtocolViolationException(
            "invalid value of success report header field, received: "
                + successReportValue + " on transaction: " + this.tID);
    }

    protected String getContentType()
    {
        return contentType;
    }

    /**
     * The byte array that contains the body bytes of the transaction in the
     * case that the body doesn't belong to a message
     */
    private byte[] bodyBytes;

    /**
     * The convenience Byte Buffer used to manipulate the body bytes
     */
    private ByteBuffer bodyByteBuffer;

    /**
     * the number of bytes to surpass in order to be considered a not so short
     * message that can be stored on memory. - Chosen value 10MB MAYBE TODO
     * implement a more dynamic method considering the total number of existent
     * messages
     */

    /**
     * the real chunk size of this message and not the one reported in the
     * Byte-Range header field
     */
    private int realChunkSize = 0;

    /**
     * 
     * @return the actual number of body bytes that this transaction currently
     *         holds
     */
    protected int getNrBodyBytes()
    {
        return realChunkSize;

    }

    /*
     * REMOVED! too much overhead used to parse the body of the transaction, one
     * char at a time. Will fill the body of the TODO message according to the
     * byte-range total number of bytes field in the header of this transaction
     * 
     * @param charAt
     */
    /*
     * private void parseBody(byte byteToAdd) { / We'll have three main
     * categories of data storage on the transactions:
     * 
     * However all are handled by the DataContainer class
     */
    /* if we don't have a valid transaction at this point return */
    /*
     * if (!isValid()) return;
     * 
     * try { if (!isIncomingResponse() && message != null &&
     * transactionType.equals(TransactionType.SEND)) {
     * message.dataContainer.put((byteRange[0] - 1) + realChunkSize, byteToAdd);
     * realChunkSize += 1; // call the associated report mechanism add byte
     * message.getReportMechanism().countReceivedBodyByte(message, this); } else
     * { bodyByteBuffer.put(byteToAdd); realChunkSize += 1; } } catch (Exception
     * e) { // TODO log it e.printStackTrace(); // FIXME ?! give a 400
     * response?! this.generateResponse(400); try {
     * transactionManager.addPriorityTransaction(this); } catch
     * (InternalErrorException e1) { // TODO log it e1.printStackTrace(); } }
     * 
     * // TODO Throw an exception if the transaction is not valid/complete
     * 
     * }
     */

    private char continuationFlag;

    /**
     *TODO do what is needed if it receives an # char (that being: mark the
     * message as aborted and "send" it to the transactionmanager thread to deal
     * with so that this thread can focus on reading from the socket)
     * 
     * 
     * Responsible for marking the needed elements so that further processing
     * could be correctly done Note: this method is called by the reader thread,
     * as such, it should do minimum work and dispatch things to the transaction
     * manager thread with the update
     * 
     * @param endTransactionChar the character associated with the end of
     *            transaction ($, + or #)
     */
    public void signalizeEnd(char continuationFlag)
    {
        this.continuationFlag = continuationFlag;

        if (!headerComplete)
        {
            try
            {
                recognizeHeader();
                proccessHeader();
                validTransaction = true;
                headerComplete = true;
            }
            catch (InvalidHeaderException e)
            {
                validTransaction = false;
                e.printStackTrace();
            }

        }
        if (headerComplete)
        {
            // body from the end of transaction line
            if (byteRange[1] != 0 && byteRange[1] != UNINTIALIZED && transactionType.equals(TransactionType.SEND))
                /*
                 * update of the chunk size with the actual data bytes that were
                 * parsed
                 */

                byteRange[1] = realChunkSize;
            /*
             * signal the counter that one received the end of message
             * continuation flag
             * 
             * call the report mechanism function so that it can call the should
             * generate report
             */
            if (transactionType.equals(TransactionType.SEND)
                && !isIncomingResponse() && message != null
                && continuationFlag == ENDMESSAGE)
            {
                (message.getReportMechanism()).getCounter(message)
                    .receivedEndOfMessage();
            }

        }
        String aux = headerBuffer.toString();
        headerBytes = aux.getBytes(usascii);
        completeTransaction = true;

    }

    /**
     * @param toPath the toPath to set
     */
    private void setToPath(URI[] toPath)
    {
        this.toPath = toPath;
    }

    /**
     * @return the toPath
     */
    public URI[] getToPath()
    {
        return toPath;
    }

    /**
     * @param fromPath the fromPath to set
     */
    private void setFromPath(URI[] fromPath)
    {
        this.fromPath = fromPath;
    }

    /**
     * @return the fromPath
     */
    public URI[] getFromPath()
    {
        return fromPath;
    }

    /**
     * @param message the message to set
     */
    private void setMessage(Message message)
    {
        this.message = message;
    }

    /**
     * @return the message associated with this transaction
     */
    public Message getMessage()
    {
        return message;
    }

    /**
     * @param byteRange the byteRange to set
     */
    public void setByteRange(long[] byteRange)
    {
        this.byteRange = byteRange;
    }

    /**
     * @return the byteRange
     */
    public long[] getByteRange()
    {
        return byteRange;
    }

    /**
     * @param totalMessageBytes the totalMessageBytes to set
     */
    public void setTotalMessageBytes(int totalMessageBytes)
    {
        this.totalMessageBytes = totalMessageBytes;
    }

    /**
     * The last Byte-Range field that should represent the total number of bytes
     * of the Message reported on this transaction.
     * 
     * 
     * @return the totalMessageBytes or -1 if this value is uninitialized or -2
     *         if the total message bytes is unknown
     */
    public long getTotalMessageBytes()
    {
        return totalMessageBytes;
    }

    /**
     * @param messageID the messageID to set
     */
    public void setMessageID(String messageID)
    {
        this.messageID = messageID;
    }

    /**
     * @return the messageID
     */
    public String getMessageID()
    {
        return messageID;
    }

    /**
     * TODO: put it to take into account the dynamic creation of the end of
     * transaction
     * 
     * @return true/false if this transaction still has data to be sent or not
     */
    public boolean hasData()
    {
        if (hasResponse())
            return response.hasData();
        if (offsetRead[HEADERINDEX] >= headerBytes.length && !message.hasData()
            && offsetRead[ENDLINEINDEX] > (7 + tID.length() + 2))
            return false;
        if (interrupted && offsetRead[ENDLINEINDEX] > (7 + tID.length() + 2))
            return false;
        return true;
    }

    static Charset usascii = Charset.forName("US-ASCII");

    private boolean interrupted = false;

    /**
     * Gets a byte for the end of transaction line
     * 
     * @return a byte of the end of transaction line
     * @throws InternalErrorException if this was called with all of the end of
     *             line bytes already returned
     */
    byte getEndLineByte() throws InternalErrorException
    {
        if (offsetRead[ENDLINEINDEX] >= 0 && offsetRead[ENDLINEINDEX] <= 6)
        {
            offsetRead[ENDLINEINDEX]++;
            return (byte) '-';
        }
        if (offsetRead[ENDLINEINDEX] > 6
            && (offsetRead[ENDLINEINDEX] < tID.length() + 7))
        {
            byte[] byteTID = tID.getBytes(usascii);
            return byteTID[offsetRead[ENDLINEINDEX]++ - 7];
        }

        // TODO If we are in the last character, get the
        // interruption end of line flag and use it here
        if (offsetRead[ENDLINEINDEX] > (7 + tID.length())
            && offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
        {
            if (offsetRead[ENDLINEINDEX]++ == 7 + tID.length() + 2)
                return 10;
            return 13;

        }
        if (offsetRead[ENDLINEINDEX] > (7 + tID.length()) + 2)
        {
            throw new InternalErrorException(
                "Error the .get() of the transaction was called without available bytes to get");
        }
        offsetRead[ENDLINEINDEX]++;
        return continuationFlagByte;
    }

    /**
     * variable that has the byte associated with the end of transaction char
     * one of: $+#
     * 
     */
    protected byte continuationFlagByte;

    /**
     * Function responsible for giving out the bytes associated with this
     * transaction TODO: put it to write the end of transaction dynamically
     * without having to be on the body
     * 
     * @return the next byte associated with this transaction
     * @throws Exception
     */
    protected byte get() throws InternalErrorException
    {
        if (hasResponse())
        {
            return response.get();
        }
        if (offsetRead[HEADERINDEX] < headerBytes.length)
            return headerBytes[offsetRead[HEADERINDEX]++];
        else
        {
            if (interrupted
                && offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
            {
                return getEndLineByte();
            }
            if (!interrupted && message.hasData())
            {
                hasContentStuff = true;
                return message.get();
            }
            if (!interrupted && hasContentStuff && offsetRead[DATAINDEX] < 2)
            {
                if (offsetRead[DATAINDEX]++ == 0)
                    return 13;
                else
                    return 10;
                /*
                 * Add the extra CRLF separating the data and the end-line
                 */

            }
            if (!interrupted
                && offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
            {
                return getEndLineByte();
            }

            throw new InternalErrorException(
                "Error the .get() of the transaction was called without available bytes to get");
        }

    }

    private int receivedResponseCode = NONEXISTANT;

    private static final int NONEXISTANT = -1;

    /**
     * Function called when a response to this transaction has been received
     * 
     * @param group
     */
    public void gotResponse(String responseCodeString)
    {

        receivedResponseCode = Integer.parseInt(responseCodeString);
        // TODO FIXME reinitialize the rest of the fields?! complete valid etc?!
        // or even call a .reinitialize flags when we identify the transaction
        /*
         * Actually at this point, upon receiving only the response line, this
         * transaction has no response code
         */
        hasContentStuff = false;
        switch (receivedResponseCode)
        // TODO
        {
        case 200:
            break;
        case 400:
            break;
        case 403:
            break;
        case 408:
            break;
        case 413:
            break;
        case 415:
            break;
        case 423:
            break;
        case 481:
            break;
        case 501:
            break;
        case 506:
            break;
        default:
            // Got an unknown response code TODO log it!
            IOInterface.debugln("Got a response code of: " + responseCodeString
                + " that is of the transaction: " + tID);
            return;

        }

        /*
         * this.responseCode = responseCode; this.response = true;
         * transactionManager.addTransactionResponse(this);
         */

    }

    /**
     * Method used by the TransactionManager to assert if this is or not an
     * incoming response
     */
    protected boolean isIncomingResponse()
    {
        if (receivedResponseCode == NONEXISTANT)
            return false;
        return true;

    }

    /**
     * variable that controls if this is an interruptible transaction or not
     */
    private boolean interruptible = false;

    /**
     * Method that asserts if a transaction is interruptible or not.
     * 
     * according to the RFC:
     * "Any chunk that is larger than 2048 octets MUST be interruptible"
     * 
     * Also REPORT requests and responses to transactions shouldn't be
     * interruptible
     * 
     * @return true if the transaction is interruptible false otherwise.
     */
    protected boolean isInterruptible()
    {
        /*
         * if (dataBytes.length + headerBytes.length > 2048 && !hasResponse() &&
         * !(transactionType.equals(TransactionType.REPORT))) return true;
         * return false;
         */
        return interruptible;
    }

    /**
     * identifies if this transaction has an outgoing response
     * 
     * @return true if it has an _outgoing_ response
     */
    protected boolean hasResponse()
    {
        if (response == null)
            return false;
        else
            return true;
    }

    /**
     * Generates the TransactionResponse with the given response code
     * 
     * @param responseCode
     */
    public void generateResponse(int responseCode)
    {
        response = new TransactionResponse(this, responseCode);

    }

    /**
     * Interrupts this transaction by setting the internal flag and appropriate
     * continuation flag (+)
     * 
     * @throws InternalErrorException if this method was unapropriately called
     * 
     */
    public void interrupt() throws InternalErrorException
    {
        if (!isInterruptible())
        {
            throw new InternalErrorException(
                "interrupt method of transaction: " + tID
                    + " was called on a non interruptible transaction");
        }
        if (this.message.bytesSent() != message.getSize())
        {
            // FIXME (?!) TODO check to see if there can be the case where the
            // message when gets interrupted has no remaining bytes left to be
            // sent due to possible concurrency here
            continuationFlag = INTERRUPT;
            IOInterface.debugln("Called the interrupt of transaction " + tID);
            interrupted = true;
        }
    }

    /**
     * Method used to dispose the body content
     */
    protected void disposeBody()
    {

    }

}
