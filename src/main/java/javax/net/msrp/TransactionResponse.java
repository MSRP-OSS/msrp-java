package javax.net.msrp;

import java.nio.ByteBuffer;

import javax.net.msrp.exceptions.IllegalUseException;
import javax.net.msrp.utils.TextUtils;

/**
 * A response to a Transaction, which is considered a transaction as well
 * TODO use the parser to validate the response ?!
 * 
 * @author João André Pereira Antunes
 */
public class TransactionResponse
    extends Transaction
{
    protected ByteBuffer content;

    protected int responseCode;

    protected String comment;

    /**
     * Creates the outgoing transaction response
     * 
     * @param transaction the original transaction that gave birth to this
     *            response
     * @param responseCode the code, must be supported by the RFCs 4975 or 4976
     * @param comment the comment as defined in RFC 4975 formal syntax,
     *            as the comment is optional, it can also be null if no comment
     *            is desired
     * @param direction the direction of the transaction
     * @throws IllegalUseException if at least one of the arguments is
     *             incompatible
     */
    protected TransactionResponse(Transaction transaction, int responseCode,
        String comment, int direction)
        throws IllegalUseException
    {
        // original transaction must be a SEND transaction
        if (transaction.transactionType != TransactionType.SEND)
            throw new IllegalUseException(
                "Constructing a SENDRESPONSE with an original transaction " +
        		"that isn't a SEND: " + transaction);

        this.transactionType = TransactionType.SENDRESPONSE;
        this.direction = direction;
        this.responseCode = responseCode;
        this.comment = comment;

        // copy the values from the original transaction to this one
        this.message = transaction.message;
        this.tID = transaction.tID;
        this.transactionManager = transaction.transactionManager;
        transaction.setResponse(this);

        if (direction == IN)
    		CreateIncomingResponse(transaction, responseCode, comment, direction);
    	else
    		CreateOutgoingResponse(transaction, responseCode, comment, direction);
    }

    private void CreateOutgoingResponse(Transaction transaction, int responseCode,
            String comment, int direction)
            throws IllegalUseException
    {
        if (!ResponseCode.isValid(responseCode))
            throw new IllegalUseException("Creating a transaction response " +
                "with an invalid response code of: " + responseCode);

        StringBuilder response = new StringBuilder(256);
        response.append("MSRP ").append(transaction.tID).append(" ").append(responseCode);
        if (comment != null)
        	response.append(" ").append(comment);

        response.append("\r\nTo-Path: ").append(
                transaction.fromPath[transaction.fromPath.length - 1])
        		.append("\r\nFrom-Path: ").append(
                transaction.toPath[transaction.toPath.length - 1])
                .append("\r\n-------").append(transaction.tID).append("$\r\n");

        this.fromPath = transaction.toPath;
        this.toPath = transaction.fromPath;
        byte[] contentBytes = response.toString().getBytes(TextUtils.utf8);
        content = ByteBuffer.wrap(contentBytes);
        content.rewind();
    }

    /**
     * Constructor to create the incoming transaction
     * 
     * @param transaction the transaction related with this
     * @param responseCode one of the response codes defined on RFC 4975
     * @param comment status commment field
     * @param direction IN or OUT
     * @throws IllegalUseException when constructing for outgoing non-original SEND.
     */
    private void CreateIncomingResponse(Transaction transaction,
        int responseCode, String comment, int direction)
        throws IllegalUseException
    {
    	;
    }

    /**
     * 
     * @return the number of bytes remaining for this response
     */
    public int getNumberBytesRemaining()
    {
        return content.remaining();
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.Transaction#getData(byte[], int)
     */
    @Override
    public int getData(byte[] toFill, int offset)
        throws IndexOutOfBoundsException
    {
        int remainingBytes = content.remaining();
        int lengthToTransfer = 0;
        if ((toFill.length - offset) > remainingBytes)
            lengthToTransfer = remainingBytes;
        else
            lengthToTransfer = (toFill.length - offset);
        content.get(toFill, offset, lengthToTransfer);
        return lengthToTransfer;
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.Transaction#hasData()
     */
    @Override
    public boolean hasData()
    {
        return content.hasRemaining();
    }

    @Override
    public String toString()
    {
        return "Response to Tx[" + tID + "], code[" + responseCode + "]";
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.Transaction#hasEndLine()
     * 
     * Seen that we use the content field to put the end line we will always
     * return false on this method call
     */
    @Override
    public boolean hasEndLine()
    {
        return false;
    }

    /* (non-Javadoc)
     * @see javax.net.msrp.Transaction#isIncomingResponse()
     */
    @Override
    protected boolean isIncomingResponse()
    {
        if (direction == IN)
            return true;
        return false;
    }
}