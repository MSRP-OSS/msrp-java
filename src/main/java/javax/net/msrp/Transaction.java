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

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.msrp.exceptions.*;
import javax.net.msrp.utils.TextUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class that represents a MSRP Transaction (either request or response,
 * incoming or outgoing). It is responsible for parsing all of the data related
 * with the transaction (either incoming or outgoing). When enough data is
 * received to take action upon, it notifies the TransactionManager and the
 * global Stack classes.
 * 
 * @author Jo�o Andr� Pereira Antunes
 */
public class Transaction
{
    /**
     * the method associated with this transaction
     */
    protected TransactionType transactionType;

    /**
     * Field that defines the type of transaction it is, regarding the
     * direction, incoming or outgoing
     */
    protected Direction direction;

    /**
     * this variable is used to denote if this transaction has "content-stuff"
     * or not. Used to know if one should add the extra CRLF after the data or
     * not
     */
    protected boolean hasContentStuff = false;

    /**
     * the From-Path parsed to the Transaction containing the associated
     * From-Path URIs from left to right in growing index order.
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
     * Array containing the index of various pieces of the transaction that have
     * been read already: header, content-stuff end (=CRLF) and end-line.
     */
    protected long[] readIndex = new long[3];

    /**
     * Constants used to index the transaction pieces
     */
    protected static final int HEADER = 0;
    protected static final int ENDLINE = 1;
    protected static final int DATA = 2;

    /**
     * the identifier of this transaction
     */
    protected String tID;

    /*
     * @uml.property name="_transactionManager"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_transactions:javax.net.msrp.TransactionManager"
     */
    protected TransactionManager transactionManager = null;

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
    protected byte continuation_flag;

    /**
     * Byte value of the $', '+' and '#' char (in utf8) continuation_flag
     */
    protected static final byte FLAG_END = 36;
    protected static final byte FLAG_IRQ = 43;
    protected static final byte FLAG_ABORT = 35;

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
    private static final int CHUNKENDBYTEINDEX = 1;

    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(Transaction.class);

    private static final int UNKNOWN = -2;

    private static final int UNINTIALIZED = -1;

    private static final int NOTFOUND = -1;

    /**
     * Maximum number of bytes allowed for the header data strings
     * (to prevent a DoS by memory exhaustion)
     */
    private static final int MAXHEADERBYTES = 3024;

    private static final int ALLBYTES = 0;

    /**
     * if this is a complete transaction Note: A transaction could be created
     * and being in the filling process and is only considered complete when
     * signalled by the Connection class
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
     * value associated with the Byte-Range parsed to the transaction referring
     * to the number of bytes of the body.
     * 
     * The values -2 and -1 are reserved as unknown and uninitialized
     * respectively
     */
    private long totalMessageBytes = -1;

    /*
     * @uml.property name="_connection"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="_transactions:javax.net.msrp.Connection"
     */
    private Connection _connection = null;

    private TransactionResponse response = null;

    /**
     * Field representing the "Failure-report" field which has a default value
     * of "yes" if it's not present
     */
    private String failureReport = "yes";

    /**
     * The value of the success report.
     * Default and if omitted: false
     */
    private boolean successReport = false;

    private String messageID = null;

    private String nickname = null;

    private StringBuffer headerBuffer = new StringBuffer();

    /**
     * if this is a valid transaction or if it has any problem with it assume
     * for starters it's always valid
     */
    private boolean validTransaction = true;

    private StatusHeader statusHeader = null;

    private Stack stack = Stack.getInstance();

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
     * the real chunk size of this message and not the one reported in the
     * Byte-Range header field
     */
    private int realChunkSize = 0;

    /**
     * Is this an interruptible transaction or not
     */
    private boolean interruptible = false;

    /**
     * How many data bytes were copied already in this transaction?
     */
    private int dataCopied = 0;

    /**
     * Generic constructor for (possibly incoming) transactions
     * 
     * @param tid   id
     * @param method which method
     * @param manager to use
     * @param direction of message
     * @throws IllegalUseException tsktsk...
     */
    protected Transaction(String tid, TransactionType method,
        TransactionManager manager, Direction direction)
        throws IllegalUseException
    {
        this.direction = direction;
        transactionManager = manager;
        readIndex[HEADER] = readIndex[ENDLINE] = readIndex[DATA] = 0;
        byteRange[0] = byteRange[1] = totalMessageBytes = UNINTIALIZED;
        transactionType = method;
        setTID(tid);
        initializeDataStructures();

        logger.info(toString() + " transaction created, handled by " + manager);
    }

    /**
     * Constructor used to send chunks
     * 
     * @param messageToSend send in Tx
     * @param manager use this manager
     */
    public Transaction(OutgoingMessage messageToSend, TransactionManager manager)
    {
        transactionManager = manager;
        tID = manager.generateNewTID();
        message = messageToSend;

        if (message.size > 0 && messageToSend.isComplete())
            throw new IllegalArgumentException(
        		"Transaction constructor called with an already sent message");
    	if (message.getNickname() == null)
            makeSendHeader();
    	else
            makeNickHeader();

        /* by default have the continuation flag to be a chunk */
        continuation_flag = FLAG_IRQ;
        initializeDataStructures();
        logger.info(toString() + " transaction created for message " + message);
    }

    private void makeNickHeader()
    {
        transactionType = TransactionType.NICKNAME;

        this.session = message.getSession();
        ArrayList<URI> uris = session.getToPath();
        URI toPathUri = uris.get(0);
        URI fromPathUri = session.getURI();

        StringBuilder header = new StringBuilder(256);
        header	.append("MSRP ").append(tID).append(" NICKNAME\r\nTo-Path: ")
        		.append(toPathUri.toASCIIString()).append("\r\nFrom-Path: ")
        		.append(fromPathUri.toASCIIString()).append("\r\nUse-Nickname: \"")
        		.append(message.getNickname()).append("\"\r\n");

        headerBytes = header.toString().getBytes(TextUtils.utf8);
    }

    private void makeSendHeader()
    {
		transactionType = TransactionType.SEND;

		this.session = message.getSession();
        ArrayList<URI> uris = session.getToPath();
        URI toPathUri = uris.get(0);
        URI fromPathUri = session.getURI();
        String messageID = message.getMessageID();

        StringBuilder header = new StringBuilder(256);
        header	.append("MSRP ").append(tID).append(" SEND\r\nTo-Path: ")
        		.append(toPathUri.toASCIIString()).append("\r\nFrom-Path: ")
        		.append(fromPathUri.toASCIIString()).append("\r\nMessage-ID: ")
        		.append(messageID).append("\r\n");

        if (message.wantSuccessReport())
            header.append("Success-Report: yes\r\n");

        if (!message.getFailureReport().equalsIgnoreCase("yes"))
            /* note: if omitted, failure report is assumed to be yes */
        	header	.append("Failure-Report: ")
        			.append(message.getFailureReport()).append("\r\n");

        String ct = message.getContentType();
        if (ct == null)
        {
            /* Empty SEND, add empty byte range */
            header.append("Byte-Range: 1-0/0\r\n");
        }
        else
        {
	        /*
	         * first value of the Byte-Range header field is the
	         * currentReadOffset + 1, or the current number of already sent
	         * bytes + 1 because the first field is the number of the first byte
	         * being sent:
	         */
	        long firstByteChunk = ((OutgoingMessage) message).nextRange();
	        /*
	         * Currently all transactions are interruptible, solving Issue #25
	         * if ((message.getSize() - ((OutgoingMessage)message).getSize()) >
	         * 								Stack.MAX_UNINTERRUPTIBLE_CHUNK) {
	         */
	        interruptible = true;
	        header	.append("Byte-Range: ").append(firstByteChunk).append("-*/")
	        		.append(message.getSizeString()).append("\r\n");
	        if (ct.length() == 0)
	        	ct = "text/plain";
	        header.append("Content-Type: ").append(ct).append("\r\n\r\n");
        }
        headerBytes = header.toString().getBytes(TextUtils.utf8);
    }

    /**
     * Explicit super constructor
     */
    protected Transaction()
    {
        logger.info(this + " transaction created by empty constructor");
    }

    /**
     * Asserts if the transaction is Incoming or Outgoing {@link #IN} or
     * {@link #OUT}
     * 
     * @return IN if it's incoming, OUT if outgoing
     */
    public Direction getDirection()
    {
        return direction;
    }

    /**
     * @return the failureReport ['yes', 'no', 'partial']
     */
    public String getFailureReport()
    {
        return failureReport;
    }

    /**
     * @return true if the success report value of the header for this
     *         transaction is 'yes', 'false' otherwise (default).
     */
    public boolean wantSuccessReport()
    {
        return successReport;
    }

    /**
     * Getter of the property <tt>_connection</tt>
     * 
     * @return Returns the _connection.
     * uml.property name="_connection"
     */
    public Connection get_connection()
    {
        return _connection;
    }

    /**
     * Setter of the property <tt>_connection</tt>
     * 
     * @param _connection The _connection to set.
     * uml.property name="_connection"
     */
    public void set_connection(javax.net.msrp.Connection _connection)
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
        if (hasResponse()) {
            toReturn.append(" returned[").append(response.responseCode).append("]");
            String c = response.comment;
            if (c != null && c.length() > 0)
            	toReturn.append(" - ").append(c);
        }
        return toReturn.toString();
    }

    /**
     * Parse the data, identify the header and fill the body.
     * Also it should find errors on received data and generate a 400 response
     * (throw an exception or other methods?!) Also responsible for
     * accounting for the data received calling the appropriate functions in the
     * ReportMechanism
     * 
     * @param incData the data to parse to the transaction
     * @param offset the starting point to be parsed on the given data array
     * @param length the number of bytes to be considered starting at the offset
     *            position
     * @param inContentStuff tells the parse method if the data in the
     *            incData is body data (content-stuff).
     * @throws InvalidHeaderException if an error was found with the parsing of
     *             the header
     * @throws ImplementationException this is here for debug purposes mainly
     * @see ReportMechanism#countReceivedBodyBlock(Message, Transaction, long,
     *      int)
     */
    public void parse(byte[] incData, int offset, int length,
        boolean inContentStuff)
        throws InvalidHeaderException, ImplementationException
    {
        if (!inContentStuff)
        {
            String toParse = new String(incData, offset, length, TextUtils.utf8);
            /*
             * if the transaction is marked as complete or invalid, calls to
             * this method will do nothing
             */
            if (!validTransaction)
                return;
            if (completeTransaction)
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
                            logger.trace(this + " parsed header");
                        }
                    }
                    catch (Exception e)
                    {
                        validTransaction = false;
                        logger.warn(this +
                        		" parse exception, returning without parsing", e);
                        return;
                    }
                }						// if (!headercomplete)
                if (headerComplete)
                {
                    if (!isValid())
                        logger.warn(this + " parsed invalid.");
                    int moreData = toParse.length() - i;
                    if (moreData > 0)
                    	logger.warn(this +
                    			" parsed header but have more data, is preparser ok?");
                    break;
                }
            } // while

        } // if (!inContentStuff)
        else
        {
            ByteBuffer incBuffer = ByteBuffer.wrap(incData, offset, length);

            if (!headerComplete)
            {
            	logger.warn(this + " parsing content-stuff without headers? - quit.");
            	return;
            }
            if (!isValid())			// no valid transaction? -> return
            {
                logger.warn(this + " parsing invalid - quit.");
                return;
            }
            try
            {
                byte[] data;
                if (!isIncomingResponse() && message != null &&
                    transactionType == TransactionType.SEND)
                {
                    /* put remaining data on the container, update realChunkSize.
                     * Account the reported bytes (automatically calls trigger)
                     * 
                     * TODO validate byteRange values for non negatives etc
                     */
                    long start =
                    		(byteRange[CHUNKSTARTBYTEINDEX] - 1) + realChunkSize;
                    int blockSize =
                    		message.getReportMechanism().getTriggerGranularity();
                    data = new byte[blockSize];
                    int size2Copy = blockSize;

                    logger.trace(this + " parsing body, starting " + start +
                            ", size " + incBuffer.remaining());
                    
                    while (incBuffer.hasRemaining())
                    {
                        if (blockSize > incBuffer.remaining()) {
                        	size2Copy = incBuffer.remaining();
                            data = new byte[size2Copy];
                        }
                        incBuffer.get(data);
                        message.getDataContainer().put(start, data);
                        realChunkSize += size2Copy;
                        message.getReportMechanism().countReceivedBodyBlock(
                        		message, this, start, size2Copy);
                        start += size2Copy;
                    }
                }
                else
                {
                    logger.trace(this +
                    		" parsing body of non-send message. Nr of bytes=" +
                    		incBuffer.remaining());
                    data = new byte[incBuffer.remaining()];
                    incBuffer.get(data);
                    if (data.length > 0)
                    {
                    	if (bodyByteBuffer == null)
                            bodyByteBuffer = ByteBuffer.wrap(data);
                    	else
                    	{
                            bodyByteBuffer.put(data);
                            realChunkSize += data.length;
                    	}
                    }
                }
            }
            catch (Exception e)
            {
                logger.error(this +
                		" exception while parsing, generating 400 response", e);
                try
                {
                    transactionManager.generateResponse(this, ResponseCode.RC400,
                			"Parsing exception: " + e.getMessage());
                }
                catch (IllegalUseException e2)
                {
                    throw new ImplementationException(e2);
                }
            }
        }
    }

    /**
     * Responsible for marking the needed elements so that further processing
     * can be correctly done.
     * <p>
     * Note: this is called by the reader thread, as such, it should do
     * minimum work and leave the rest to the transaction manager thread
     * 
     * @param flag the continuation flag ($, + or #)
     * @throws InternalError if called in an outgoing transaction
     */
    public void signalizeEnd(char flag)
    {
        if (direction == Direction.OUT)
            throw new InternalError("Wrong use of signalizeEnd()");

        this.continuation_flag = (byte) flag;
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
                logger.error("Unrecognized header - ", e);
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
            if (transactionType == TransactionType.SEND &&
            		!isIncomingResponse() && message != null &&
            		flag == FLAG_END)
            {
                (message.getReportMechanism()).getCounter(message)
                    .receivedEndOfMessage();
            }
            if (transactionType == TransactionType.SEND &&
            		!isIncomingResponse() && continuation_flag == FLAG_ABORT)
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
                        "Error! implementation error, we should always have " +
                        "a message associated at this point");
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
     * @return the nickname
     */
    public String getNickname()
    {
    	return nickname;
    }

    /**
     * This is the end chunk of the complete message.
     */
    public void setEndChunk()
    {
        this.continuation_flag = FLAG_END;
    }

    /**
     * 
     * @return true = all data has been read, except the end-line.
     *  Note Returns false for responses because they have
     *       their own end-line inside their content.
     */
    public boolean hasEndLine()
    {
        if (hasData())
            return false;
        if (readIndex[ENDLINE] > (7 + tID.length() + 2))
            return false;
        return true;

    }

    /**
     * @return true = transaction still has data (or headers) to be read.
     */
    // TODO: take dynamic creation of an end of transaction into account
    public boolean hasData()
    {
        if (interrupted)
            return false;
        if (readIndex[HEADER] >= headerBytes.length && !message.hasData())
            return false;
        if (session.getChunkSize() > 0 && dataCopied >= session.getChunkSize())
            return false;
        return true;
    }

    /**
     * @return has some data from this transaction already been sent? 
     */
    public boolean hasSentData()
    {
    	return readIndex[HEADER] > 0;
    }

    /**
     * Fills the given array with DATA (header and content excluding
     * end-line) bytes starting from offset and stopping at the array limit
     * or end of data. Returns the number of bytes filled
     * 
     * @param outData the byte array to fill
     * @param offset where to start filling the byte array
     * @return the number of bytes gotten
     * @throws ImplementationException if this function was called when there
     *             was no more data or if it was interrupted
     * @throws IndexOutOfBoundsException if the offset is bigger than the length
     *             of the byte buffer to fill
     * @throws Exception if something went wrong while trying to get this data
     */
    public int getData(byte[] outData, int offset)
        throws ImplementationException,
		        IndexOutOfBoundsException,
		        Exception
    {
        if (interrupted || readIndex[ENDLINE] > 0)
        {
            throw new ImplementationException("Called Transaction.get() " +
            		"when it should've been Transaction.getEndLineByte()");
        }

        int bytesCopied = 0;
        boolean stopCopying = false;
        int spaceRemaining = outData.length - offset;
        while ((bytesCopied < spaceRemaining) && !stopCopying)
        {
            if (offset > (outData.length - 1))
                throw new IndexOutOfBoundsException();

            if (readIndex[HEADER] < headerBytes.length)
            {							// we are processing the header
                int bytesToCopy = 0;
                if ((outData.length - offset) < (headerBytes.length - readIndex[HEADER]))
                    /*
                     * Remaining bytes on outData smaller than remaining on
                     * header. Fill outData with that length.
                     */
                    bytesToCopy = (outData.length - offset);
                else
                    bytesToCopy = (int) (headerBytes.length - readIndex[HEADER]);

                System.arraycopy(headerBytes, (int) readIndex[HEADER],
                				outData, offset, bytesToCopy);
                readIndex[HEADER] += bytesToCopy;
                bytesCopied += bytesToCopy;
                offset += bytesToCopy;
                continue;
            }
            if (!interrupted && message.hasData())
            {
                hasContentStuff = true;

                int chunk = message.get(outData, offset);
                dataCopied += chunk;
                bytesCopied += chunk;
                offset += chunk;
            }
            if (! hasData())
                stopCopying = true;		// header/chunks done, no data to send.
        }
        return bytesCopied;
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
        if (hasContentStuff && readIndex[DATA] < 2)
        {								/* CRLF separating data and end-line */
            if (readIndex[DATA]++ == 0)
                return 13;
            return 10;
        }
        if (readIndex[ENDLINE] <= 6)
        {
            readIndex[ENDLINE]++;
            return (byte) '-';
        }
        int endlen = tID.length() + 7;
        if (readIndex[ENDLINE] > 6 && (readIndex[ENDLINE] < endlen))
        {
            return (byte) tID.charAt((int) (readIndex[ENDLINE]++ - 7));
        }
        if (readIndex[ENDLINE] > (endlen) && readIndex[ENDLINE] <= (endlen + 2))
        {
            if (readIndex[ENDLINE]++ == endlen + 2)
                return 10;
            return 13;
        }
        if (readIndex[ENDLINE] > endlen + 2)
        {
            throw new InternalErrorException(
                "Error: getEndLineByte() called without available bytes to get");
        }
        readIndex[ENDLINE]++;
        return continuation_flag;
    }

    protected int getEndLine(byte[] data, int offset) throws InternalErrorException
    {
    	for (int i = 0; i < data.length - offset; i++)
    	{
    		if (hasEndLine())
    			data[offset + i] = getEndLineByte();
    		else
    			return i;
    	}
    	return 0;
    }
    /**
     * Asserts if a transaction is interruptible or not.
     * 
     * According the RFC:
     * "Any chunk that is larger than 2048 octets MUST be interruptible".
     * 
     * REPORT requests and responses to transactions shouldn't be interruptible
     * 
     * @return true if the transaction is interruptible false otherwise.
     */
    public boolean isInterruptible()
    {
        return interruptible;
    }

    /**
     * identifies if this transaction has an outgoing response
     * 
     * @return true if it has an _outgoing_ response
     */
    public boolean hasResponse()
    {
        return (response != null);
    }

    /**
     * Asserts if this transaction is a request or a response
     * 
     * @return true if it is a request or false if it is
     */
    protected boolean isRequest()
    {
        return (transactionType == TransactionType.REPORT ||
        		transactionType == TransactionType.SEND ||
        		transactionType == TransactionType.NICKNAME);
    }

    /**
     * Interrupt transaction by setting the flag and appropriate
     * continuation flag (+)
     * 
     * @throws IllegalUseException if this method was inappropriately called
     *             (meaning the transaction can't be interrupted either because
     *             it's not an OutgoingMessage or is not interruptible)
     */
    public void interrupt() throws IllegalUseException
    {
        if (!isInterruptible() || message.getDirection() != Direction.OUT)
            throw new IllegalUseException("Transaction.interrupt(" +
            					tID + ") was called but is non interruptible");

        if (((OutgoingMessage) message).getSentBytes() != message.getSize())
        {
            /*
             * FIXME:(?!) check if there is a case where the
             * message being interrupted has no remaining bytes left to
             * sent due to possible concurrency.
             */
            continuation_flag = FLAG_IRQ;
            logger.info(this + " interrupted transaction");
            interrupted = true;
        }
    }

    /**
     * Method used to abort the transaction. This method switches the
     * continuation flag and marks this transaction as interrupted
     */
    public void abort()
    {
        logger.info(this + " aborting transaction.");
        continuation_flag = FLAG_ABORT;
        interrupted = true;
        // let's wake up the write thread
        transactionManager.getConnection().notifyWriteThread();
    }

    /**
     * @return was this transaction aborted?
     */
    public boolean isAborted()
    {
    	return interrupted && continuation_flag == FLAG_ABORT;
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
            throw new ImplementationException("No associated session!");
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
     * @return Actual number of body bytes this transaction currently holds
     */
    protected int getNrBodyBytes()
    {
        return realChunkSize;
    }

    /**
     * @return  Used by TransactionManager to assert if this is an
     *          incoming response
     */
    protected boolean isIncomingResponse()
    {
        return false;
    }

    /**
     * Rewind positions on the read offsets of this transaction.
     * <p>
     * It's main purpose it's to allow the transaction manager to
     * rewind the data prior to interrupting the transaction when an end-line
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
        /* make sure we aren't trying to rewind a response */
        if (hasResponse())
            throw new IllegalUseException("Trying to rewind a response");

        /* make sure we aren't trying to rewind on the header: */
        if (readIndex[HEADER] < headerBytes.length)
            throw new IllegalUseException("Trying to rewind the header");

        /*
         * No sense in rewinding if it doesn't have any data
         */
        if (!hasContentStuff)
            throw new IllegalUseException("Trying to rewind empty transaction");
        /*
         * rewinds the given nr of positions in the data container
         */
        DataContainer dataContainer = message.getDataContainer();
        dataContainer.rewindRead(numberPositionsToRewind);
    }

    /**
     * @return the transactionType
     */
    public TransactionType getTransactionType()
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
     * Retrieves the data associated with the body of this transaction
     * 
     * @param size the number of bytes or zero for the whole data
     * @return an array of bytes with the transaction's body or null if it
     *         doesn't exist
     * @throws InternalErrorException if there was some kind of exception this
     *             is thrown with the wrapped exception
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
        else
        {
            DataContainer dc = message.getDataContainer();
            ByteBuffer auxByteBuffer;
            try
            {
                if (byteRange[0] == UNINTIALIZED || byteRange[0] == UNKNOWN
                        || byteRange[1] == UNINTIALIZED)
                    throw new InternalErrorException("the limits of this "
                        + "transaction are unknown/unintialized, "
                        + "can't satisfy request.");
                long start = byteRange[0] - 1;
                if (size == ALLBYTES)
                    if (byteRange[1] == UNKNOWN)
                        if (session.getChunkSize() == 0)
                            auxByteBuffer = dc.get(start, dc.size() - (start));
                        else
                            auxByteBuffer = dc.get(start, session.getChunkSize() - (start));
                    else
                        auxByteBuffer = dc.get(start, byteRange[1] - (start));
                else
                    auxByteBuffer = dc.get(start, size);
            }
            catch (Exception e)
            {
                throw new InternalErrorException(e);
            }
            return auxByteBuffer.array();
        }
    }

    /**
     * Getter of the property <tt>_transactionManager</tt>
     * 
     * @return Returns the manager.
     * uml.property name="_transactionManager"
     */
    public TransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    /**
     * Setter of the property <tt>_transactionManager</tt>
     * 
     * @param manager The manager to set.
     * uml.property name="_transactionManager"
     */
    protected void setTransactionManager(TransactionManager manager)
    {
        transactionManager = manager;
    }

    /**
     * Constructor-method to initialize data structures as needed.
     * 
     * Currently uses this transaction's TransactionType to assert if there is
     * need to reserve space for this transaction body
     */
    private void initializeDataStructures()
    {
        if (transactionType != TransactionType.SEND &&
    		transactionType != TransactionType.NICKNAME)
        {
            bodyBytes = new byte[Stack.MAX_NONSEND_BODYSIZE];
            bodyByteBuffer = ByteBuffer.wrap(bodyBytes);
        }
    }

    private void setTID(String tid)
    {
        tID = tid;
    }

    /**
     * Adds the given data to the buffer checking if it already doesn't exceed
     * the maximum limit of bytes. In that case an Exception is
     * thrown
     * 
     * @param toAdd the string to add to the buffer used for storage of
     *            complete lines for analyzing posteriorly
     * @throws InvalidHeaderException if too many bytes would be passed with the
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

    /** Has headerBuffer all of the header data?
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

        return isHeaderComplete.matches();
    }

    /**
     * Method that checks whether this is a valid transaction or not.
     */
    /* TODO
     * complete this method so that it catches any incoherences with the
     * protocol syntax. All of the syntax problems should be found at this point
     * 
     * Semantic validation: validate that the mandatory headers, regarding,
     * the method are present.
     * 
     * check to see if there is any garbage on the transaction (bytes
     * remaining in the headerBuffer that aren't assigned to any valid field ?!)
     */
    @SuppressWarnings("static-method")
    private void validate()
    {
        return;
    }

    /**
     * Assign a session and message to the transaction when headers are complete
     * <p>
     * called whenever the transaction's headers are complete and used
     * to validate the headers, to generate the needed responses ASAP. (It
     * also permits one to start receiving the body already knowing which
     * session it belongs, besides also generating the responses a lot sooner)
     */
    private void proccessHeader()
    {
        /*
         * if this is a test (connection of the TransactionManager is null)
         * then skip this step! (ONLY HERE FOR DEBUG PURPOSES)
         */
        if (transactionManager.getConnection() == null)
        {
            logger.debug("DEBUG MODE: should only" +
            			" appear if transaction is a dummy one!");
            return;
        }
        /*
         * If the transaction is an incoming response, atm do nothing. TODO(?!)
         */
        if (isIncomingResponse())
            return;

        if (getTransactionType() == TransactionType.UNSUPPORTED)
        {
            try
            {
                transactionManager.generateResponse(this, ResponseCode.RC501, null);
            }
            catch (IllegalUseException e)
            {
                logger.error(this + " generating response: " +
            			ResponseCode.toString(ResponseCode.RC501), e);

            }
            return;
        }
        validate();

        /* make sure the transaction is valid (originates 400 responses) */
        if (!isValid())
        {
            try
            {
                transactionManager.generateResponse(this, ResponseCode.RC400,
                							"Transaction found invalid");
            }
            catch (IllegalUseException e)
            {
                logger.error("Cannot generate response - ", e);
            }
        }
        Session relatedSession =
        		transactionManager.associatedSession((getToPath())[0]);
        if (relatedSession == null)
        {
            // No session associated, go see if there is one in the list of
        	// yet to be validated Connections
            Connections connectionsInstance =
                Stack.getConnectionsInstance(
                		transactionManager.getConnection().getLocalAddress());
            relatedSession =
                connectionsInstance.sessionToIdentify((getToPath())[0]);
            if (relatedSession == null)
            {
                /*
                 * if there are no sessions associated with this transaction
                 * manager and also no sessions available to associate
                 * with the ToPath URI we have one of two cases:
                 * - either this transaction belongs to another active session
                 *   (give a 506 response)
                 * - or this session doesn't exist at all (give a 481 response)
                 */
            	int rspCode;
                if (stack.isActive((getToPath())[0]))
                	rspCode = ResponseCode.RC506;
            	else
            		rspCode = ResponseCode.RC481;
                try
                {
                    transactionManager.generateResponse(this, rspCode, null);
                }
                catch (IllegalUseException e)
                {
                    logger.error(this + " generating response: " +
                    			ResponseCode.toString(rspCode), e);
                }
            }
            else
            {							/* session found */
                if (stack.isActive((getToPath())[0]))
                {
                    /*
                     * but also with another, then give the r506 response and
                     * log this rare event! (that shouldn't have happened)
                     */
                    try
                    {
                        transactionManager.generateResponse(this, ResponseCode.RC506, null);
                    }
                    catch (IllegalUseException e)
                    {
                        logger.error(this + " generating response: " +
                    			ResponseCode.toString(ResponseCode.RC506), e);

                    }
                    logger.error(this + " error! received a request that is yet to " +
            			"be identified but associated with other session!");
                    return;
                }
                /*
                 * associate session with this transaction manager and
                 * remove from the list of sessions yet to be identified
                 */
                connectionsInstance.identifiedSession(relatedSession);
                this.session = relatedSession;
                transactionManager.addSession(relatedSession);
                associateMessage();
            }
        }
        else
        {
            /*
             * one of the sessions for which this transaction manager is responsible
             */
            this.session = relatedSession;
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
             * be received by the bound session and continue the process
             * FIXME: eventually need to check with the stack if the messageID
             * is known and not only with the session and act according RFC
             */
            {
                session.delMessageToReceive((IncomingMessage) message);
                message = null;
            }
        }
        if (message == null)
        {
        	IncomingMessage in;
        	switch (transactionType) {
        	case NICKNAME:
        		session.triggerReceivedNickname(this);
        		break;
        	case SEND:
            	in = IncomingMessageFactory.createMessage(
            			session, messageID, this.contentType, totalMessageBytes);
                message = in;
                message.setSuccessReport(successReport);
                try
                {
                    message.setFailureReport(failureReport);
                }
                catch (IllegalUseException e1)
                {		// TODO invalidate Tx & trigger appropriate response.
                    logger.error("cannot set failure report - ", e1);
                }

                boolean result = in instanceof IncomingAliveMessage ||
                				session.triggerAcceptHook(in);
                if (result && in.getResult() != ResponseCode.RC200)
                {
                    in.setResult(ResponseCode.RC200);
                    if (in instanceof IncomingAliveMessage || in.getDataContainer() != null)
                    {	// put on receiving message "list" of the Session
                        session.putReceivingMessage(in);
                    }
                    else
                    {	// if user didn't assign DataContainer to message;
                    	//		discard & log.
                    	logger.error(this + 
                			" no datacontainer given to store incoming data, " +
            				"discarding incoming message " + in);
                        result = false;
                    }
                }
                if (!result)
                {						/* The message is to be discarded! */
                    this.validTransaction = false;
                    this.completeTransaction = true;
                    try
                    {
                        transactionManager.generateResponse(this, in.getResult(),
                        					"Message rejected by user");
                    }
                    catch (IllegalUseException e)
                    { // user set an invalid result; log it & re-send with 413 default
                        logger.warn(this + " attempt to use invalid response code, forcing to default.");
                        try
                        {
                            transactionManager.generateResponse(this,
                            		ResponseCode.RC413, "Message rejected by user");
                        }
                        catch (IllegalUseException e1)
                        {
                            logger.error(this + " error generating 413 response", e1);
                        }
                    }
                }
                break;
        	case REPORT:
                validTransaction = false;
                /*
                 * RFC tells us to silently ignore request if no message
                 * can be associated with it. We'll just log it
                 */
                logger.warn(this + " incoming report request for unknown message [" +
                			getMessageID() + "]");
                break;
			default:
				break;
        	}
        }
        // lets update the reference in the Message to this transaction if this
        // is a SEND transaction and an associated message has been found
        if (message != null && transactionType == TransactionType.SEND)
        {
            message.setLastSendTransaction(this);
        }
    }

    private static Pattern asciiPattern = Pattern.compile("\\p{ASCII}+");

    private static Pattern headers = Pattern.compile(
                    "(^To-Path:) (.{10,})(\r\n)(From-Path:) (.{10,})(\r\n)(\\p{ASCII}*)",
                    Pattern.CASE_INSENSITIVE);

    private static Pattern messageIDPattern = Pattern.compile(
                    "(.*)(Message-ID:) (\\p{Alnum}(\\p{Alnum}|\\.|\\-|\\+|\\%|\\=){3,31})(\r\n)(.*)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern byteRangePattern = Pattern.compile(
                    "(.*)(Byte-Range:) (\\p{Digit}+)-(\\p{Digit}+|\\*)/(\\p{Digit}+|\\*)(\r\n)(.*)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern contentTypePattern = Pattern.compile(
                    "(.*)(Content-Type:) ([^/]{1,30}/[^;\r\n]{1,30})(;.*)?\r\n(.*)",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern fReportPattern = Pattern.compile(
	                "(.*)(Failure-Report:) ([^\r\n]*)(\r\n)(.*)",
	                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern sReportPattern = Pattern.compile(
		    		"(.*)(Success-Report:) ([^\r\n]*)(\r\n)(.*)",
		            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern nicknamePattern = Pattern.compile(
		            "(.*)(Use-Nickname:) +\"([^\"]+)\"[^\r\n]*\r\n(.*)",
		            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static Pattern statusPattern = Pattern.compile(
	                "(.*)(Status:) (\\p{Digit}{3}) (\\p{Digit}{3})([^\r\n]*)\r\n(.*)",
	                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /**
     * will recognise the headers stored on headerBuffer, initialise all of the
     * variables related to the header and check for violations of the
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
            /* Report request specific headers: */
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
            /* $FALL-THROUGH$: to shared headers */
        case SEND:
            /* Message-ID processing: */
            matcher = messageIDPattern.matcher(headerBuffer);
            if (matcher.matches())
            {
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
	                logger.warn(this + " failure-Report invalid value found: " +
	                			value);
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
	                logger.warn(this + " success-Report invalid value found: " +
	                			value);
            }
            break;
        case NICKNAME:
            matcher = nicknamePattern.matcher(headerBuffer);
            if (matcher.matches())
                nickname = matcher.group(3);
            else
                throw new InvalidHeaderException("Nickname not found");
            matcher = fReportPattern.matcher(headerBuffer);
            if (matcher.matches())
            	logger.warn(this + " failure report included in NICKNAME request, ignoring...");
            matcher = sReportPattern.matcher(headerBuffer);
            if (matcher.matches())
            	logger.warn(this + " success report included in NICKNAME request, ignoring...");
        	break;
        case UNSUPPORTED:
        	/* nothing to do (yet) */
            break;
		default:
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
}
