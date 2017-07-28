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


import java.io.File;
import java.io.FileNotFoundException;
import java.security.InvalidParameterException;

import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.exceptions.InternalErrorException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic outgoing MSRP message
 * 
 * @author Jo�o Andr� Pereira Antunes
 */
public class OutgoingMessage
    extends Message
{
    /** The logger associated with this class */
    private static final Logger logger =
        LoggerFactory.getLogger(OutgoingMessage.class);

    private long chunkSize = 0;
    private long nextOffset = 1;

    private int chunksSent;
    private int chunkOffset;

    /**
     * Create a blank message that can be used to send over a session.
     */
    public OutgoingMessage()
    {
    	;
    }

    public OutgoingMessage(String contentType, byte[] data)
    {
        if (contentType == null || data == null)
            throw new InvalidParameterException("Type must be specified with content");
		this.contentType = contentType;
		dataContainer = new MemoryDataContainer(data);
		size = data.length;
    }

    public OutgoingMessage(String contentType, File file)
            throws FileNotFoundException, SecurityException
    {
    	if (contentType == null || file == null)
    		throw new InvalidParameterException("Type must be specified with content");
        this.contentType = contentType;
        dataContainer = new FileDataContainer(file);
        size = dataContainer.size();
    }

    protected OutgoingMessage(String nickname)
    {
        this.nickname = nickname;
    }

    /**
     * This method is intended to abort a message's send process. If the message
     * is not being sent it won't be anymore. If the message is being sent, the
     * current SEND transaction will end with the # continuation-flag char and
     * further data belonging to the message will not be sent. On the other end,
     * if the message is being sent, receiving the # continuation-flag will
     * trigger a call to the abortedMessageEvent method on SessionListener
     * binded to the session.
     * 
     * @param reason Irrelevant for an OutgoingMessage
     * @param extraInfo Irrelevant for an OutgoingMessage
     * 
     * @throws InternalErrorException If we have a failure on the sanity checks
     * 
     * @see SessionListener#abortedMessageEvent(javax.net.msrp.event.MessageAbortedEvent)
     */
    @Override
	public void abort(int reason, String extraInfo)
    		throws InternalErrorException
    {
        logger.debug("Going to abort an OutgoingMessage, reason: " + reason +
        			" comment: " + extraInfo);
        if (session == null)			/* Sanity checks */
            throw new InternalErrorException(
				                "pause() called on message with no session.");
        TransactionManager transactionManager = session.getTransactionManager();
        if (transactionManager == null)
            throw new InternalErrorException(
	                "pause() called on message with no transaction manager.");

        aborted = true;			/* signal this message internally as aborted */

        /* remove from the list of messages to send in session */
        session.delMessageToSend(this);

        transactionManager.abortMessage(this);
    }

    public int get(byte[] outData, int offset)
        throws IndexOutOfBoundsException, Exception
    {
        if (chunkSize > 0)
        {
            long chunkSent = dataContainer.currentReadOffset() % chunkSize;
            int limit = (int) (chunkSize - chunkSent);
            return dataContainer.get(outData, offset, limit);
        }
        else
            return dataContainer.get(outData, offset);
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.Message#getMessageID()
     */
    @Override
    public String getMessageID()
    {
    	if (messageId == null || messageId.length() < 1)
    		messageId = Stack.generateMessageID();
        return messageId;
    }

    /**
     * Returns the sent bytes determined by the offset of the data container
     * 
     * @return the number of sent bytes
     */
    public long getSentBytes()
    {
        return dataContainer == null ? 0 : dataContainer.currentReadOffset();
    }

    protected void setSession(Session session)
    {
        super.setSession(session);
        long csize = session.getChunkSize();
        if (csize >= 0)
            this.chunkSize = csize;
    }

    /**
     * @return the number of chunks this message will be send in
     */
    protected int getChunks()
    {
        int chunks = 1;
        if (chunkSize > 0 && size > chunkSize)
        {
            chunks = (int) (size / chunkSize);
            if (size % chunkSize != 0)
                chunks++;
        }
        return chunks;
    }

    /**
     * @return the offset for the next chunk (byte-range) to send.
     */
    protected long nextRange()
    {
        if (chunkSize == 0)
            return nextOffset;
        else
        {
            if (nextOffset + chunkSize > size)
                return -1;
            long offset = nextOffset;
            nextOffset += chunkSize;
            return offset;
        }
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.Message#isComplete()
     */
    @Override
    public boolean isComplete()
    {
    	long sentBytes = getSentBytes();
    	if (logger.isTraceEnabled())
            logger.trace(String.format(
            		"isOutgoingComplete(%s, sent[%d])? %b",
            		this.toString(), sentBytes, sentBytes == size));
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
        /*
         * FIXME: How to resume? as this is just re-scheduling....
        session.addMessageOnTop(this);
         */
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.Message#getDirection()
     */
    @Override
    public Direction getDirection()
    {
        return Direction.OUT;
    }

    @Override
	public Message validate() throws Exception
    {
    	return this;
    }

    @Override
    public String toString()
    {
    	return String.format("OutMsg(%s)",
    			messageId == null || messageId.length() < 1 ? "new" : messageId);
    }
}
