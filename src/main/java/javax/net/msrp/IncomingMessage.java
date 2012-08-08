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

import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.exceptions.ImplementationException;
import javax.net.msrp.exceptions.InternalErrorException;
import javax.net.msrp.exceptions.InvalidHeaderException;
import javax.net.msrp.wrap.Wrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class is used to generate incoming messages
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class IncomingMessage
    extends Message
{
    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(IncomingMessage.class);

    /**
     * @param session
     * @param messageId
     * @param contentType
     * @param size
     * @param reportMechanism
     */
    public IncomingMessage(Session session, String messageId,
        String contentType, long size)
    {
        this(session, messageId, contentType, size, null);
    }

    /**
     * Constructor called internally when receiving an incoming message used by
     * the IncomingMessage class
     * 
     * @param session
     * @param messageId
     * @param contentType
     * @param reportMechanism the report mechanism to be used for this message
     *            or null to use the default one associated with the session
     * @param size the size of the incoming message (should be bigger than -2
     *            and -1 for unknown (*) total size
     */
    public IncomingMessage(Session session, String messageId,
        String contentType, long size, ReportMechanism reportMechanism)
    {
        super();
        if (size <= UNINTIALIZED) {
            try
            {
                throw new ImplementationException(
                    "Message constructor with invalid size");
            }
            catch (ImplementationException e)
            {
                e.printStackTrace();
            }
        }
        this.session = session;
        this.contentType = contentType;
        this.messageId = messageId;
        this.size = size;
        constructorAssociateReport(reportMechanism);
    }

    public IncomingMessage(Session session, String nickname)
    {
    	super();
    	this.session = session;
    	this.nickname = nickname;
    }
    /**
     * Constructor used internally
     */
    protected IncomingMessage()
    {
    }

    /**
     * Method that uses the associated counter of this message to assert if the
     * message is complete or not
     * 
     * @return true if the message is complete, false otherwise
     */
    @Override
    public boolean isComplete()
    {
        boolean toReturn = getCounter().isComplete();
        logger.trace("Called isComplete, received bytes: "
            + getCounter().getCount() + " message size: " + size
            + " going to return: " + toReturn);
        return toReturn;
    }

    /**
     * @return the number of received bytes so far reported by the associated
     *         Counter class
     */
    public Long getReceivedBytes()
    {
        long valueToReturn = getCounter().getCount();
        logger.trace("getReceivedBytes called, will return: " + valueToReturn);
        return valueToReturn;
    }

    /** Response code of the accept hook call. */
    private int result = ResponseCode.RC413;

	/**
	 * @return the result
	 */
	public int getResult() {
		return result;
	}

	/**
	 * @param result the result to set
	 */
	public void setResult(int result) {
		this.result = result;
	}

	@Override
    public int getDirection()
    {
        return IN;
    }

    public void validate() throws Exception {
    	if (this.getSize() > 0) {
    		if (this.getContentType() == null)
    			throw new InvalidHeaderException("no content type.");
	    	if (Wrap.getInstance().isWrapperType(this.getContentType())) {
	    		wrappedMessage = Wrap.getInstance().getWrapper(this.getContentType());
	    		wrappedMessage.parse(this.getDataContainer().get(0, 0));
	    	}
    	}
    }

    /**
     * Convenience method used to reject an incoming message. Its equivalent to
     * call abort with response 413
     * 
     * @throws InternalErrorException if abort also throws it
     */
    public void reject() throws InternalErrorException
    {
        try
        {
            abort(ResponseCode.RC413, null);
        }
        catch (IllegalUseException e)
        {
            logger.error("Implementation error! abort called internally with"
                + " invalid arguments", e);
        }
    }

    /**
     * If the last transaction hasn't yet a given response given, a response is
     * generated, otherwise a REPORT request with the namespace 000 (equivalent
     * to a response) is generated
     * 
     * @param reason one of <tt>MessageAbortEvent</tt> response codes except
     *            CONTINUATIONFLAG
     * @param reasonExtraInfo corresponds to the comment as defined on RFC 4975
     *            formal syntax. If null, it isn't sent any comment.
     * @throws InternalErrorException if any object is in an invalid state which
     *             prevents this function to abort the message
     * @throws IllegalUseException if the arguments are invalid
     */
    @Override
    public void abort(int reason, String reasonExtraInfo)
        throws InternalErrorException,
        IllegalUseException
    {
        // sanity checks:
        if (lastSendTransaction == null)
            throw new InternalErrorException(
                "abort was called on an incoming message without "
                    + "an assigned Transaction!");
        if (!ResponseCode.isAbortCode(reason))
            throw new IllegalUseException(
                "The reason must be one of the abort response codes");
        // Check to see if we already responded to the transaction being
        // received/last transaction known
        if (!lastSendTransaction.hasResponse())
            lastSendTransaction.getTransactionManager().generateResponse(
                lastSendTransaction, reason, reasonExtraInfo);
        else
        {								// Generate REPORT
            FailureReport report =
                new FailureReport(this, session, lastSendTransaction, "000",
            					reason, reasonExtraInfo);
            							// & send it
            session.getTransactionManager().addPriorityTransaction(report);
        }
        aborted = true;					// mark message as aborted
    }
}
