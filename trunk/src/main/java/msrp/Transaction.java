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
package msrp;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import msrp.exceptions.*;
import msrp.messages.*;
import msrp.utils.TextUtils;

/**
 * Class that represents a MSRP Transaction (either request or response,
 * incoming or outgoing). It is responsible for parsing all of the data related
 * with the transaction (either incoming or outgoing). When enough data is
 * received to take action upon, it notifies the TransactionManager and the
 * global MSRPStack classes (The communication between these classes is done via
 * the Observer design pattern)
 * 
 * @author Jo�o Andr� Pereira Antunes
 */
public class Transaction
{
    /**
     * the method associated with this transaction we call it transactionType
     * 
     */
    public TransactionType transactionType;

    /**
     * Field that defines the type of transaction it is, regarding the
     * direction, incoming or outgoing
     */
    protected int direction;

    /**
     * this variable is used to denote if this transaction has "content-stuff"
     * or not Used to know if one should add the extra CRLF after the data or
     * not
     */
    protected boolean hasContentStuff = false;

    /**
     * the From-Path parsed to the Transaction containing the associated
     * From-Path URIs from left to right in a growing index order
     */
    protected URI[] fromPath;

    /**
     * the To-Path parsed to the Transaction containing the associated To-Path
     * URIs from left to right in a growing index order
     */
    protected URI[] toPath = null;

    /**
     * the message associated with this transaction
     */
    protected Message message = null;

    /**
     * Array that contains the offset of various pieces of the transaction: the
     * header; the content-stuff to keep track of the CRLF after the end of the
     * data; the end of line; These are indexed by the *INDEX constants
     */
    protected long[] offsetRead = new long[3];

    /**
     * the identifier of this transaction
     */
    protected String tID;

    /**
     * @uml.property name="_transactionManager"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_transactions:msrp.TransactionManager"
     */
    protected msrp.TransactionManager transactionManager = null;

    protected byte[] headerBytes = new byte[MAXHEADERBYTES];

    /**
     * Variable that tells if this Transaction is interrupted (paused or
     * aborted)
     */
    protected boolean interrupted = false;

    /**
     * variable that has the byte associated with the end of transaction char
     * one of: $+#
     * 
     */
    protected byte continuationFlagByte;

    /**
     * Constant used to index the header in the offsetRead
     */
    protected static final int HEADERINDEX = 0;

    /**
     * Constant used to index the end line in the offsetRead
     */
    protected static final int ENDLINEINDEX = 1;

    /**
     * servers the purpose of keeping track of the CRLF after the end of the
     * data Constant used to index the data on the offsetRead
     */
    protected static final int DATAINDEX = 2;

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
     * Value used to represent incoming transactions
     */
    protected static final int IN = 1;

    /**
     * Value used to represent outgoing transactions
     */
    protected static final int OUT = 2;

    /**
     * The constant used to access the byteRange first field that has the number
     * of the first byte of the chunk bound to this transaction
     * 
     * @see #byteRange
     */
    private static final int CHUNKSTARTBYTEINDEX = 0;

    /**
     * The constant used to access the byteRange second field that has the
     * number of the last byte of the chunk bound to this transaction
     * 
     * @see #byteRange
     */
    private static final int CHUNKENDBYTEINDX = 1;

    /**
     * The constant used to access the byteRange second field that has the
     * number of the last byte of the chunk bound to this transaction
     * 
     * @see #byteRange
     */
    private static final int CHUNKENDBYTEINDEX = 1;

    /**
     * Maximum number of bytes allowed for the header data strings (so that we
     * don't have a DoS by memory exaustion)
     * 
     */
    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(Transaction.class);

    private static final int UNKNOWN = -2;

    private static final int UNINTIALIZED = -1;

    private static final int NOTFOUND = -1;

    /**
     * Maximum number of bytes allowed for the header data strings (so that we
     * don't have a DoS by memory exaustion)
     * 
     */
    private static final int MAXHEADERBYTES = 3024;

    private static final int ALLBYTES = 0;

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

    private String contentType;

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
     * @uml.property name="_connection"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_transactions:msrp.Connection"
     */
    private msrp.Connection _connection = null;

    private TransactionResponse response = null;

    /**
     * Field representing the "Failure-report" field which has a default value
     * of "yes" if it's not present
     */
    private String failureReport = "yes";

    /**
     * Field that represents the value of the success report by default and if
     * is omitted is considered false
     */
    private boolean successReport = false;

    private String messageID = null;

    private StringBuffer headerBuffer = new StringBuffer();

    /**
     * if this is a valid transaction or if it has any problem with it assume
     * for starters it's always valid
     */
    private boolean validTransaction = true;

    private StatusHeader statusHeader = null;

    private MSRPStack stack = MSRPStack.getInstance();

    /**
     * The session associated with this transaction
     */
    private Session session;

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
     * variable that controls if this is an interruptible transaction or not
     */
    private boolean interruptible = false;

    /**
     * Constructor called when there is an incoming transaction
     * 
     * @param tid
     * @param method
     */
    protected Transaction(String tid, TransactionType method,
        TransactionManager manager, int direction)
        throws IllegalUseException
    {
        logger.info("Transaction created (incoming?) Tx-" + method + "[" + tid
        			+ "], handled by " + manager);
        // sanity check:
        if (direction != IN && direction != OUT)
            throw new IllegalUseException("direction has an invalid argument"
                + ": " + direction);

        this.direction = direction;
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
    /**
     * @param send
     * @param messageBeingSent
     * @param manager
     */
    /**
     * @param send
     * @param messageBeingSent
     * @param manager
     */
    /**
     * @param send
     * @param messageBeingSent
     * @param manager
     */
    public Transaction(TransactionType send, OutgoingMessage messageBeingSent,
        TransactionManager manager)
    {
        transactionType = send;
        this.transactionManager = manager;
        tID = manager.generateNewTID();
        message = messageBeingSent;
        if (transactionType == TransactionType.SEND)
        {
            if (messageBeingSent.size > 0 && messageBeingSent.isComplete())
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
             * first value of the Byte-Range header field is the
             * currentReadOffset + 1, or the current number of already sent
             * bytes + 1 because the first field is the number of the first byte
             * being sent:
             */
            long numberFirstByteChunk = messageBeingSent.getSentBytes() + 1;
            // now all of the transactions are interruptible, solving Issue #25
            // if ((message.getSize() - ((OutgoingMessage)message).getSize()) >
            // MSRPStack.MAXIMUMUNINTERRUPTIBLE)
            // {
            interruptible = true;
            headerString =
                headerString.concat("Byte-Range: " + numberFirstByteChunk
                    + "-*" + "/" + messageBeingSent.getStringTotalSize()
                    + "\r\n");
            String ct = messageBeingSent.getContentType();
            if (ct != null && ct.length() > 0)
            	headerString = headerString.concat("Content-Type: " + ct);
            headerString = headerString.concat("\r\n\r\n");

            headerBytes = headerString.getBytes(TextUtils.utf8);

            /* by default have the continuation flag to be the end of message */
            continuationFlagByte = ENDMESSAGE;
            initializeDataStructures();
        }
        logger.info("Transaction created Tx-" + send + "[" + tID
        			+ "], associated message: " + messageBeingSent);
    }

    /**
     * Explicit super constructor
     */
    protected Transaction()
    {
        logger.info("transaction created by the empty constructor");
    }

    /**
     * Asserts if the transaction is Incoming or Outgoing {@link #IN} or
     * {@link #OUT}
     * 
     * @return IN if it's incoming, OUT if outgoing
     */
    public int getDirection()
    {
        return direction;

    }

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
     * Getter of the property <tt>_connection</tt>
     * 
     * @return Returns the _connection.
     * @uml.property name="_connection"
     */
    public msrp.Connection get_connection()
    {
        return _connection;
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

    public String getTID()
    {

        return tID;
    }

    public StatusHeader getStatusHeader()
    {
        return statusHeader;
    }

    @Override
    public String toString()
    {
    	StringBuilder toReturn = new StringBuilder(40);
    	toReturn.append("Tx-").append(transactionType).append("[").append(tID).append("]");
        if (hasResponse())
            toReturn.append(", response code[").append(response.responseCode).append("]");
        return toReturn.toString();

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
            /* Trims and assembles received data via the toParse string
             * so that we get a buffer with all headers before trying to
             * analyze it
             */
            String toParse = new String(incData, offset, length, TextUtils.utf8);
            /*
             * if the transaction is marked as complete or invalid, calls to
             * this method will do nothing
             */
            if (!validTransaction)
                return;
            if (completeTransaction)
                /*
                 * it's rather odd that we have a complete transaction and we
                 * are still parsing data to it, so throw an exception
                 */
                throw new ImplementationException(
                    "Error: trying to parse data to a complete transaction!");

            int i = 0;
            while (i < toParse.length())
            {
                if (!headerComplete)
                {
                    try
                    {
                        int j;
                        while (i < toParse.length() && !isHeaderBufferComplete())
                        {
                        	j = toParse.indexOf("\r\n", i);
                        	if (j == -1) {
                                addHeaderBuffer(toParse.substring(i));
                                i = toParse.length();
                        	} else {
                        		addHeaderBuffer(toParse.substring(i, j + 2));
                        		i = j + 2;
                        	}
                        }
                        if (isHeaderBufferComplete())
                        {
                            recognizeHeader();
                            proccessHeader();
                            headerComplete = true;
                            logger.trace("Parsed header of Tx[" + tID + "]");
                        }
                    }
                    catch (Exception e)
                    {
                        validTransaction = false;
                        logger.warn("Exception parsing Tx-"
                                    + transactionType + "["
                                    + tID + "] returning without parsing");
                        return;
                    }
                }						// if (!headercomplete)
                if (headerComplete)
                {
                    if (!isValid())
                    { 		// no valid transaction at this point? -> return
                        logger.warn("parsing invalid Tx[" + tID
                        		+ "] returning without parsing");
                        return;
                    }
                    try
                    {
                        /*
                         * the local variable used to convert the string to
                         * byte[] FIXME (?!) should we be receiving byte [] at
                         * this point?
                         */
                        byte[] byteData;
                        if (!isIncomingResponse() && message != null
                            && transactionType == TransactionType.SEND)
                        {
                            /*
                             * TODO the byteRange header fields should be
                             * validated to make sure they have no negative
                             * values etc
                             */
                            long startIndex =
                                (byteRange[CHUNKSTARTBYTEINDEX] - 1)
                                    + realChunkSize;
                            logger.trace("parsing the non binary body of "
                                + "Tx-SEND[" + tID
                                + "] start at " + startIndex
                                + ", size " + (toParse.length() - i));

                            while (toParse.length() - i != 0)
                            {
                                if (message.getReportMechanism().getTriggerGranularity()
                                					>= toParse.length() - i)
                                {
                                    /*
                                     * Put all of the remaining data on the data
                                     * container and update the realchunksize.
                                     * Account the reported bytes (that
                                     * automatically calls the trigger TODO)
                                     */
                                    byteData =
                                        toParse.substring(i).getBytes(TextUtils.utf8);
                                    message.getDataContainer()
                                    			.put(startIndex, byteData);
                                    realChunkSize += byteData.length;
                                    message.getReportMechanism()
                                        .countReceivedBodyBlock(message, this,
                                            startIndex, byteData.length);
                                    i += byteData.length;
                                }
                                else
                                {
                                    byteData =
                                        toParse.substring(
                                            i, i + (message.getReportMechanism()
                                                .getTriggerGranularity()))
                                                	.getBytes(TextUtils.utf8);
                                    message.getDataContainer()
                                    			.put(startIndex, byteData);
                                    realChunkSize += byteData.length;
                                    message.getReportMechanism()
                                        .countReceivedBodyBlock(message, this,
                                            startIndex, byteData.length);
                                    i += byteData.length;
                                    startIndex += byteData.length;
                                }
                            }
                        }
                        else
                        {
                            byteData = toParse.substring(i).getBytes(TextUtils.utf8);
                            logger.trace("parsing the body of  non-send or non"
                                + " message from Tx-<?>["
                                + tID + "], nr of bytes=" + byteData.length);
                            if (byteData.length > 0)
                            {
                            	if (bodyByteBuffer == null)
                                    bodyByteBuffer = ByteBuffer.wrap(byteData);
                            	else
                            	{
		                            bodyByteBuffer.put(byteData);
		                            realChunkSize += byteData.length;
		                            i += byteData.length;
                            	}
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        logger.error("caught an exception while parsing Tx["
                        		+ tID + "], generating 400 response", e);
                        try
                        {
                            transactionManager.generateResponse(this, 400,
                            		"Parsing exception: " + e.getMessage());
                        }
                        catch (IllegalUseException e2)
                        {				// This should never happen!
                            throw new ImplementationException(e2);
                        }
                    }
                }
                if (completeTransaction)
                {
                    logger.trace("Tx[" + tID + "] is complete");
                    break;
                }
            }

        } // if (!receivingBinaryData)
        else
        {
            ByteBuffer incByteBuffer = ByteBuffer.wrap(incData, offset, length);

            if (headerComplete)
            {
                if (!isValid())			// no valid transaction? -> return
                {
                    logger.warn("parsing invalid Tx[" + tID
                    		+ "] returning without parsing");
                    return;
                }
                try
                {
                    /*
                     * the local variable used to convert the string to byte[]
                     * FIXME (?!) should we be receiving byte [] at this point?
                     */
                    byte[] byteData;
                    if (!isIncomingResponse() && message != null
                        && transactionType == TransactionType.SEND)
                    {
                        /*
                         * if this isn't an incoming response, has a message
                         * associated with it and it's actually a SEND method
                         * TODO validate the byteRange values for non negatives
                         * etc
                         */
                        long startIndex =
                            (byteRange[CHUNKSTARTBYTEINDEX] - 1)
                                + realChunkSize;
                        logger.trace("parsing the non binary body of "
                                + "Tx-SEND[" + tID
                                + "] start at " + startIndex
                                + ", size " + incByteBuffer.remaining());
                        while (incByteBuffer.hasRemaining())
                        {
                            if (message.getReportMechanism().getTriggerGranularity()
                            		>= incByteBuffer.limit() - incByteBuffer.position())
                            {
                                /*
                                 * put all of the remaining data on the data
                                 * container and update the realchunksize,
                                 * account the reported bytes (that
                                 * automatically calls the trigger TODO)
                                 */
                                byteData = new byte[incByteBuffer.remaining()];
                                incByteBuffer.get(byteData);
                                message.getDataContainer()
                                				.put(startIndex, byteData);
                                realChunkSize += byteData.length;
                                message.getReportMechanism()
                                    .countReceivedBodyBlock(message, this,
                                    			startIndex, byteData.length);
                                startIndex += byteData.length;
                            }
                            else
                            {
                                byteData =
                                    new byte[message.getReportMechanism()
                                             	.getTriggerGranularity()];
                                incByteBuffer.get(byteData, 0, message
                                    .getReportMechanism().getTriggerGranularity());
                                message.getDataContainer()
                                				.put(startIndex, byteData);
                                realChunkSize += byteData.length;
                                message.getReportMechanism()
                                    .countReceivedBodyBlock(message, this,
                                    			startIndex, byteData.length);
                                startIndex += byteData.length;
                            }
                        }
                    }
                    else
                    {
                        logger.trace("parsing the body of  non-send or non"
                                + " message from Tx-<?>[" + tID
                                + "], nr of bytes=" + incByteBuffer.remaining());
                        byteData = new byte[incByteBuffer.remaining()];
                        incByteBuffer.get(byteData);
                        if (byteData.length > 0)
                        {
                        	if (bodyByteBuffer == null)
                                bodyByteBuffer = ByteBuffer.wrap(byteData);
                        	else
                        	{
	                            bodyByteBuffer.put(byteData);
	                            realChunkSize += byteData.length;
                        	}
                        }
                    }
                }
                catch (Exception e)
                {
                    logger.error("caught an exception while parsing Tx["
                    		+ tID + "], generating 400 response", e);
                    try
                    {
                        transactionManager.generateResponse(this, 400,
                    			"Parsing exception: " + e.getMessage());
                    }
                    catch (IllegalUseException e2)
                    {
                        throw new ImplementationException(e2);
                    }
                }
            }
        }
    }

    /**
     * TODO do what is needed if it receives an # char (that being: mark the
     * message as aborted and "send" it to the transactionmanager thread to deal
     * with so that this thread can focus on reading from the socket)
     * 
     * Responsible for marking the needed elements so that further processing
     * could be correctly done Note: this method is called by the reader thread,
     * as such, it should do minimum work and dispatch things to the transaction
     * manager thread with the update
     * 
     * @param endTransactionChar the character associated with the end of
     *            transaction ($, + or #)
     * @throws ConnectionParserException if this method was called in an
     *             outgoing transaction
     */
    public void signalizeEnd(char continuationFlag)
        throws ConnectionParserException
    {
        if (direction == OUT)
            throw new ConnectionParserException("Wrong use of signalizeEnd");
        this.continuationFlagByte = (byte) continuationFlag;

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
            if (byteRange[1] != 0 && byteRange[1] != UNINTIALIZED
                && transactionType == TransactionType.SEND)
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
            if (transactionType == TransactionType.SEND
                && !isIncomingResponse() && message != null
                && continuationFlag == ENDMESSAGE)
            {
                (message.getReportMechanism()).getCounter(message)
                    .receivedEndOfMessage();
            }

            if (transactionType == TransactionType.SEND
                && !isIncomingResponse()
                && continuationFlagByte == ABORTMESSAGE)
            {
                /*
                 * if we received a send request with a continuation flag of
                 * aborted we should notify the message via the method
                 * message.gotAborted so that it can change itself and notify
                 * the appropriate listener that is associated with this message
                 */
                if (message == null)
                    /* TODO log it and maybe try to recover from the error (?!) */
                    throw new RuntimeException(
                        "Error! implementation error, we should always have "
                            + "a message associated at this point");
                message.gotAborted(this);
            }
        }
        String aux = headerBuffer.toString();
        headerBytes = aux.getBytes(TextUtils.utf8);
        completeTransaction = true;
    }

    /**
     * @return the toPath
     */
    public URI[] getToPath()
    {
        return toPath;
    }

    /**
     * @return the fromPath
     */
    public URI[] getFromPath()
    {
        return fromPath;
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
     * 
     * @return true if this transaction has no more data and still has to
     *         transmit all of the end-line content. false otherwise NOTE: This
     *         method returns false for transaction responses because they have
     *         their own end-line inside their content
     */
    public boolean hasEndLine()
    {
        if (hasData())
            return false;
        if (offsetRead[ENDLINEINDEX] > (7 + tID.length() + 2))
            return false;
        return true;

    }

    /**
     * TODO: put it to take into account the dynamic creation of the end of
     * transaction
     * 
     * @deprecated ?? FIXME if only used by the dataToSend of TM then yes put it
     *             private then
     * @return true/false if this transaction still has data (or headers) to be
     *         sent (excluding the end-line) or not
     */
    public boolean hasData()
    {
        if (interrupted)
            return false;
        if (offsetRead[HEADERINDEX] >= headerBytes.length && !message.hasData())
            return false;
        return true;
    }

    /**
     * Method that fills the given array with DATA (header and content excluding
     * end of line) bytes starting from offset and stopping at the array limit
     * or end of data and returns the number of bytes filled
     * 
     * @param outData the byte array to fill
     * @param offset the offset index to start filling the outData
     * @return the number of bytes filled
     * @throws ImplementationException if this function was called when there
     *             was no more data or if it was interrupted
     * @throws IndexOutOfBoundsException if the offset is bigger than the length
     *             of the byte buffer to fill
     * @throws InternalErrorException if something went wrong while trying to
     *             get this data
     */
    public int getData(byte[] outData, int offset)
        throws ImplementationException,
        IndexOutOfBoundsException,
        InternalErrorException
    {
        // sanity checks:
        if (interrupted && offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
        {
            // old line: FIXME to remove these lines if no problems are
            // encountered running the tests return getEndLineByte();
            throw new ImplementationException("The Transaction.get() "
                + "when it should have been the "
                + "Transaction.getEndLineByte");

        }
        if (interrupted)
        {
            throw new ImplementationException("The Transaction.get() "
                + "when it should have been the "
                + "Transaction.getEndLineByte");

        }
        // end of sanity checks
        int bytesCopied = 0;
        boolean stopCopying = false;
        int spaceRemainingOnBuffer = outData.length - offset;
        while ((bytesCopied < spaceRemainingOnBuffer) && !stopCopying)
        {
            if (offset > (outData.length - 1))
                throw new IndexOutOfBoundsException();

            if (offsetRead[HEADERINDEX] < headerBytes.length)
            { // if we are processing the header
                int bytesToCopy = 0;
                if ((outData.length - offset) < (headerBytes.length - offsetRead[HEADERINDEX]))
                    // if the remaining bytes on the outdata is smaller than the
                    // remaining bytes on the header then fill the outdata with
                    // that length
                    bytesToCopy = (outData.length - offset);
                else
                    bytesToCopy =
                        (int) (headerBytes.length - offsetRead[HEADERINDEX]);
                System.arraycopy(headerBytes, (int) offsetRead[HEADERINDEX],
                    outData, offset, bytesToCopy);
                offsetRead[HEADERINDEX] += bytesToCopy;
                bytesCopied += bytesToCopy;
                offset += bytesCopied;
                continue;
            }
            if (!interrupted && message.hasData())
            {
                hasContentStuff = true;

                bytesCopied += message.get(outData, offset);
                offset += bytesCopied;
                continue;

            }
            if (!interrupted && !message.hasData()
                && (offsetRead[HEADERINDEX] >= headerBytes.length))
                stopCopying = true;
        }
        return bytesCopied;
    }

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
    public boolean isInterruptible()
    {
        /*
         * if (dataBytes.length + headerBytes.length > 2048 && !hasResponse() &&
         * (transactionType != TransactionType.REPORT)) return true;
         * return false;
         */
        return interruptible;
    }

    /**
     * identifies if this transaction has an outgoing response
     * 
     * @return true if it has an _outgoing_ response
     */
    public boolean hasResponse()
    {
        if (response == null)
            return false;
        else
            return true;
    }

    /**
     * Asserts if this transaction is a request or a response
     * 
     * @return true if it is a request or false if it is
     */
    protected boolean isRequest()
    {
        if (transactionType == TransactionType.REPORT
            || transactionType == TransactionType.SEND)
            return true;
        return false;
    }

    /**
     * Interrupts this transaction by setting the internal flag and appropriate
     * continuation flag (+)
     * 
     * @throws IllegalUseException if this method was unapropriately called
     *             (meaning the transaction can't be interrupted either because
     *             it's not an OutgoingMessage or is not interruptible)
     * 
     */
    public void interrupt() throws IllegalUseException
    {
        if (!isInterruptible() || message.getDirection() != Message.OUT)
        {
            throw new IllegalUseException("interrupt method of transaction: "
                + tID + " was called on a non interruptible transaction");
        }
        if (((OutgoingMessage) this.message).getSentBytes() != message
            .getSize())
        {
            // FIXME (?!) TODO check to see if there can be the case where the
            // message when gets interrupted has no remaining bytes left to be
            // sent due to possible concurrency here
            continuationFlagByte = INTERRUPT;
            logger.trace("Called the interrupt of transaction " + tID);
            interrupted = true;
        }
    }

    /**
     * Method used to abort the transaction. This method switches the
     * continuation flag and marks this transaction as interrupted
     */
    public void abort()
    {
        logger.info("Aborting transaction: " + this);
        continuationFlagByte = ABORTMESSAGE;
        interrupted = true;
        // let's wake up the write thread
        transactionManager.getConnection().notifyWriteThread();
    }

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

    protected String getContentType()
    {
        return contentType;
    }

    /**
     * 
     * @return the actual number of body bytes that this transaction currently
     *         holds
     */
    protected int getNrBodyBytes()
    {
        return realChunkSize;
    }

    /**
     * Function responsible for giving out the bytes associated with this
     * transaction DATA only, the end-line should be retrieved using the
     * appropriate method {@link #getEndLineByte()} TODO: put it to write the
     * end of transaction dynamically without having to be on the body TODO:
     * tidy up a lil'bit the code
     * 
     * @deprecated use {@link #get(byte[], int)} instead
     * 
     * @return the next byte associated with this transaction
     * @throws ImplementationException if this method was called when the
     *             getEndLineData should have been called or when no more bytes
     *             remain
     */
    protected byte get() throws ImplementationException
    {
        if (hasResponse())
        {
            return response.get();
        }
        if (offsetRead[HEADERINDEX] < headerBytes.length)
            return headerBytes[(int) offsetRead[HEADERINDEX]++];
        else
        {
            if (interrupted
                && offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
            {
                // old line: FIXME to remove these lines if no problems are
                // encountered running the tests return getEndLineByte();
                throw new ImplementationException("The Transaction.get() "
                    + "when it should have been the "
                    + "Transaction.getEndLineByte");

            }
            if (!interrupted && message.hasData())
            {
                hasContentStuff = true;
                return message.get();
            }
            if (!interrupted
                && offsetRead[ENDLINEINDEX] <= (7 + tID.length() + 2))
            {
                // old line: FIXME to remove these lines if no problems are
                // encountered running the tests return getEndLineByte();
                throw new ImplementationException("The Transaction.get() "
                    + "when it should have been the "
                    + "Transaction.getEndLineByte");
            }

            throw new ImplementationException(new InternalErrorException(
                "Error the .get() of the transaction was called without "
                    + "available bytes to get"));
        }

    }

    /**
     * Method used by the TransactionManager to assert if this is or not an
     * incoming response
     */
    protected boolean isIncomingResponse()
    {
        return false;
    }

    /**
     * Method used to dispose the body content
     */
    protected void disposeBody()
    {
        // TODO
    }

    /**
     * This method is used to rewind positions on the read offsets of this
     * transaction. It's main purpose it's to allow the transaction manager to
     * rewind the data prior from interrupting the transaction when an end-line
     * is found on the content of the transaction.
     * 
     * @param numberPositionsToRewind the number of positions to rewind on this
     *            transaction.
     * @throws IllegalUseException if this method was called to do for instance
     *             a rewind on a response
     */
    protected void rewind(int numberPositionsToRewind)
        throws IllegalUseException
    {
        /* sanity checks: */

        /* make sure we aren't trying to rewind a response */
        if (hasResponse())
            throw new IllegalUseException("Trying to rewind a response");

        /* make sure we aren't trying to rewind on the header: */
        if (offsetRead[HEADERINDEX] < headerBytes.length)
            throw new IllegalUseException("Trying to rewind the header");

        /*
         * if the message doesn't has any data, there's really no sense in
         * rewinding:
         */
        if (!hasContentStuff)
            throw new IllegalUseException("Trying to rewind an empty "
                + "transaction");
        /*
         * rewinds the given nr of positions the data container
         */
        DataContainer dataContainer = message.getDataContainer();
        dataContainer.rewindRead(numberPositionsToRewind);
    }

    /**
     * @return the transactionType
     */
    protected TransactionType getTransactionType()
    {
        return transactionType;
    }

    /**
     * @param response the response to set
     */
    protected void setResponse(TransactionResponse response)
    {
        this.response = response;
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
        if (transactionType != TransactionType.SEND)
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
            return dst;

        }
        else if (transactionType == TransactionType.SEND)

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
     * Getter of the property <tt>_transactionManager</tt>
     * 
     * @return Returns the manager.
     * @uml.property name="_transactionManager"
     */
    public msrp.TransactionManager getTransactionManager()
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
     * Method called by the constructors to initialize data structures as
     * needed.
     * 
     * Currently uses this transaction's TransactionType to assert if there is
     * need to reserve space for this transaction body
     */
    private void initializeDataStructures()
    {
        if (transactionType != TransactionType.SEND)
        {
            bodyBytes = new byte[MSRPStack.MAXNONSENDBODYSIZE];
            bodyByteBuffer = ByteBuffer.wrap(bodyBytes);
        }
    }

    private void setTID(String tid)
    {
        tID = tid;
    }

    /**
     * Adds the given data to the buffer checking if it already doesn't exceed
     * the maximum limit of bytes without an \r\n in that case an Exception is
     * thrown
     * 
     * @param toAdd the string to add to the buffer used for storage of
     *            complete lines for analyzing posteriorly
     * @throws InvalidHeaderException if MAXHEADERBYTES would be passed with the
     *             addition of toAdd
     */
    private void addHeaderBuffer(String toAdd) throws InvalidHeaderException
    {
    	int len = toAdd.length() + headerBuffer.length();

    	if ( len > MAXHEADERBYTES)
            throw new InvalidHeaderException("Trying to parse a line of "
                + len + " bytes when the limit is " + MAXHEADERBYTES);
        else
            headerBuffer.append(toAdd);
    }

    private static Pattern endOfHeaderWithoutContent =
            Pattern.compile(
                "^To-Path: .{10,}\r\nFrom-Path: .{10,}\r\n", Pattern.DOTALL);

    private static Pattern endOfHeaderWithContent =
            Pattern.compile(".*(\r\n){2}.*", Pattern.DOTALL);

    /** Has headerbuffer all of the header data?
     * @return true if headerBuffer has all of header-data
     */
    private boolean isHeaderBufferComplete()
    {
    	/*
	     * in case of incoming response the header
	     * ends with the from-paths last uri and CRLF
    	 */
        Matcher isHeaderComplete;
        if (isIncomingResponse())
            isHeaderComplete = endOfHeaderWithoutContent.matcher(headerBuffer);
        else
	        /* In case of a transaction with 'content-stuff' */
	        isHeaderComplete = endOfHeaderWithContent.matcher(headerBuffer);

        if (isHeaderComplete.matches())
            return true;
        return false;
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

    /**
     * This method's main purpose is to assign a session and a message to the
     * transaction as soon as the headers are complete
     * 
     * method called whenever the transaction's headers are complete it's used
     * to validate the headers and to generate the needed responses ASAP. (It
     * also permits one to start receiving the body already knowing which
     * session it belongs, besides also generating the responses alot sooner)
     */
    private void proccessHeader()
    {
        /*
         * if this is a test (the connection of the TransactionManager is null)
         * then skip this step! (THIS IS ONLY HERE FOR DEBUG PURPOSES)
         * FIXME: Ideally we would have a mock connection!!
         */
        if (transactionManager.getConnection() == null)
        {
            logger.debug("DEBUG MODE this line should only"
                + " appear if the transaction is a dummy one!");
            return;
        }

        /*
         * If the transaction is an incoming response, atm do nothing. TODO(?!)
         */
        if (isIncomingResponse())
            return;

        if (getTransactionType() == TransactionType.UNSUPPORTED)
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
            try
            {
                transactionManager.generateResponse(this, 400,
                    "Transaction found invalid");
            }
            catch (IllegalUseException e)
            {
                e.printStackTrace();
            }
        }
        if (transactionManager.associatedSession((getToPath())[0]) == null)
        {
            // It doesn't have this session associated
            // go see if there is one in the list of the yet to be validated in
            // connections
            Connections connectionsInstance =
                MSRPStack.getConnectionsInstance(transactionManager
                    .getConnection().getLocalAddress());
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
            	int rspCode;
                if (stack.isActive((getToPath())[0]))
                	rspCode = 506;
            	else
            		rspCode = 481;
                try
                {
                    transactionManager.generateResponse(this, rspCode, null);
                }
                catch (IllegalUseException e)
                {
                    logger.error("Generating " + rspCode + " response", e);
                }
            }
            else
            {
                /*
                 * if it was found a session in the toIdentify list
                 */
                if (stack.isActive((getToPath())[0]))
                {
                    /*
                     * but also with another, then give the r506 response and
                     * log this rare event! (that shouldn't have happened)
                     */
                    try
                    {
                        transactionManager.generateResponse(this, 506, null);
                    }
                    catch (IllegalUseException e)
                    {
                        logger.error("Generating 506 response", e);

                    }
                    logger.error("Error! received a request"
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
     * is rendered invalid and it gets logged, the message is set to null It
     * also updates the reference to the last transaction in the associated
     * message
     * 
     * @param messageID the message-ID of the Message to associate
     */
    private void associateMessage()
    {
        message = session.getSentOrSendingMessage(messageID);
        /* check if this is a transaction for an already existing message */
        if (session.getReceivingMessage(messageID) != null)
        {
            message = session.getReceivingMessage(messageID);
            if (message.wasAborted())
            /*
             * if the message was previously aborted it shouldn't be on the
             * queue, log the event, delete it from the list of the messages to
             * be received by the bound session and continue the process TODO
             * FIXME: eventually need to check with the stack if the messageID
             * is known and not only with the session and act according to the
             * RFC
             */
            {
                /* TODO: log it */
                session.delMessageToReceive((IncomingMessage) message);
                message = null;
            }
        }
        if (message == null)
        {
            if (this.transactionType == TransactionType.SEND)
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
                    if (incomingMessage.getDataContainer() == null)
                    {
                        result = false;
                        /*
                         * TODO log it Log the fact that the user got his
                         * message discarded because he didn't filled out the
                         * dataContainer field
                         */
                    }
                    else
                    {
                        /*
                         * otherwise we put this message on the receiving
                         * message "list" of the Session
                         */
                        session.putReceivingMessage(incomingMessage);
                    }
                }
                if (!result)
                {
                    /* The message is to be discarded! */
                    this.validTransaction = false;
                    this.completeTransaction = true;
                    try
                    {
                        transactionManager.generateResponse(this,
                            incomingMessage.result, "Message rejected by user");
                    }
                    catch (IllegalUseException e)
                    {
                        // the user set an invalid result, let's log it and
                        // resend it with the 413 default
                        logger
                            .warn("Tried to use an invalid response code as a response, gone with the default 413");
                        try
                        {
                            transactionManager.generateResponse(this, 413,
                                "Message rejected by user");
                        }
                        catch (IllegalUseException e1)
                        {
                            logger.error(
                                "Exception caught generating 413 response for transaction: "
                                    + this, e1);
                        }
                    }
                }
            }
            if (this.transactionType == TransactionType.REPORT)
            {
                validTransaction = false;
                /*
                 * the RFC tells us to silently ignore the request if no message
                 * can be associated with it so we'll just log it
                 */
                logger.warn("Warning! incoming report request"
                    + " for an unknown message to the stack. " + "Message-ID: "
                    + getMessageID());
            }
        }
        // lets update the reference in the Message to this transaction if this
        // is a SEND transaction and an associated message has been found
        if (message != null
            && this.transactionType == TransactionType.SEND)
        {
            message.setLastSendTransaction(this);
        }
    }

    private static Pattern asciiPattern = Pattern.compile("\\p{ASCII}+");

    private static Pattern headers = Pattern.compile(
                    "(^To-Path:) (.{10,})(\r\n)(From-Path:) (.{10,})(\r\n)(\\p{ASCII}*)",
                    Pattern.CASE_INSENSITIVE);

    private static Pattern messageIDPattern = Pattern.compile(
                    "(.*)(Message-ID:) ((\\p{Alnum}|\\.|\\-|\\+|\\%|\\=){3,31})(\r\n)(.*)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern byteRangePattern = Pattern.compile(
                    "(.*)(Byte-Range:) (\\p{Digit}+)-(\\p{Digit}+|\\*)/(\\p{Digit}+|\\*)(\r\n)(.*)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern contentTypePattern = Pattern.compile(
                    "(.*)(Content-Type:) ([^/]{1,30}/[^;]{1,30})(;.*)?\r\n(.*)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern fReportPattern = Pattern.compile(
	                "(.*)(Failure-Report:) ([^\r\n]*)(\r\n)(.*)",
	                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern sReportPattern = Pattern.compile(
		    		"(.*)(Success-Report:) ([^\r\n]*)(\r\n)(.*)",
		            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern statusPattern = Pattern.compile(
	                "(.*)(Status:) (\\p{Digit}{3}) (\\p{Digit}{3})([^\r\n]*)\r\n(.*)",
	                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /**
     * will recognize the headers stored on headerBuffer initializing all of the
     * variables related to the header and checking for violations of the
     * protocol
     * 
     * @throws InvalidHeaderException if it's found that the header is invalid
     *             for some reason
     */
    private void recognizeHeader() throws InvalidHeaderException
    {
    	Matcher matcher;

    	// If the characters aren't all ascii send an invalid header
        matcher = asciiPattern.matcher(headerBuffer);
        if (!matcher.matches())
            throw new InvalidHeaderException(
                "Error, non-ascii characters contained in the header");

        // headers = To-Path CRLF From-Path CRLF 1*( header CRLF )
        matcher = headers.matcher(headerBuffer);
        if (!matcher.matches())
        {
            throw new InvalidHeaderException(
                "Transaction doesn't have valid to/from-path headers."
                    + " Transaction: " + transactionType
                    + " headerBuffer: " + headerBuffer);
        }
        try
        {
            String[] toPaths = matcher.group(2).split(" +");
            URI[] toPath = new URI[toPaths.length];
            int i =0;
            for (String path : toPaths) {
            	toPath[i] = URI.create(path);
            	i++;
            }
            setToPath(toPath);
        }
        catch (Exception e)
        {
            throw new InvalidHeaderException("Problem parsing to-path(s)", e);
        }
        try
        {
            String[] fromPaths = matcher.group(5).split(" +");
            URI[] fromPath = new URI[fromPaths.length];
            int i = 0;
            for (String path : fromPaths) {
            	fromPath[i] = URI.create(path);
            	i++;
            }
            setFromPath(fromPath);
        }
        catch (Exception e)
        {
            throw new InvalidHeaderException("Problem parsing from-path(s)", e);
        }
        // If we are receiving a response the processing ends here
        if (isIncomingResponse())
            return;
        switch (transactionType)		// Method specific headers
        {
        case REPORT:
        case SEND:
            /* Message-ID processing: */
            matcher = messageIDPattern.matcher(headerBuffer);
            if (matcher.matches())
            {
                // TODO get the corresponding message from the messageID or
                // create a new one
                messageID = matcher.group(3);
            }
            else
                throw new InvalidHeaderException("MessageID not found");

            /* Byte-Range processing: */
            matcher = byteRangePattern.matcher(headerBuffer);
            if (matcher.matches())
            {
                byteRange[0] = Integer.parseInt(matcher.group(3));
                if (matcher.group(4).equals("*"))
                    byteRange[1] = UNKNOWN;
                else
                    byteRange[1] = Integer.parseInt(matcher.group(4));
                if (matcher.group(5).equals("*"))
                    totalMessageBytes = UNKNOWN;
                else
                    totalMessageBytes = Integer.parseInt(matcher.group(5));
            }
            matcher = contentTypePattern.matcher(headerBuffer);
            if (matcher.matches())
                this.contentType = matcher.group(3);

            /* Report processing: */
            matcher = fReportPattern.matcher(headerBuffer);
            if (matcher.matches())
            {
            	String value = matcher.group(3).trim().toLowerCase();
            	if (value.matches("yes|no|partial"))
            		failureReport = value;
            	else
	                logger.warn("Failure-Report invalid value found: " + value);
            }
            matcher = sReportPattern.matcher(headerBuffer);
            if (matcher.matches())
            {
            	String value = matcher.group(3).trim().toLowerCase();
            	if (value.equals("yes"))
            			successReport = true;
            	else if (value.equals("no"))
            			successReport = false;
            	else
	                logger.warn("Success-Report invalid value found: " + value);
            }
            /* Report request specific headers: */
            if (transactionType == TransactionType.REPORT)
            {
                /* 'Status:' processing */
                matcher = statusPattern.matcher(headerBuffer);
                if (matcher.matches())
                {
                    String namespace = matcher.group(3);
                    String statusCode = matcher.group(4);
                    String comment = matcher.group(5);
                    statusHeader =
                        new StatusHeader(namespace, statusCode, comment);
                }
            }
            break;
        case UNSUPPORTED:
            // TODO
            break;
        }
    }

    /**
     * @param toPath the toPath to set
     */
    private void setToPath(URI[] toPath)
    {
        this.toPath = toPath;
    }

    /**
     * @param fromPath the fromPath to set
     */
    private void setFromPath(URI[] fromPath)
    {
        this.fromPath = fromPath;
    }

    /**
     * Gets a byte for the end of transaction line
     * 
     * @return a byte of the end of transaction line
     * @throws InternalErrorException if this was called with all of the end of
     *             line bytes already returned
     */
    byte getEndLineByte() throws InternalErrorException
    {
        if (hasContentStuff && offsetRead[DATAINDEX] < 2)
        {
            /*
             * Add the extra CRLF separating the data and the end-line
             */
            if (offsetRead[DATAINDEX]++ == 0)
                return 13;
            else
                return 10;
        }
        if (offsetRead[ENDLINEINDEX] >= 0 && offsetRead[ENDLINEINDEX] <= 6)
        {
            offsetRead[ENDLINEINDEX]++;
            return (byte) '-';
        }
        if (offsetRead[ENDLINEINDEX] > 6
            && (offsetRead[ENDLINEINDEX] < tID.length() + 7))
        {
            byte[] byteTID = tID.getBytes(TextUtils.utf8);
            return byteTID[(int) (offsetRead[ENDLINEINDEX]++ - 7)];
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
}