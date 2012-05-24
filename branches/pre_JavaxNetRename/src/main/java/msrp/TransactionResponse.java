package msrp;

import java.nio.ByteBuffer;

import msrp.exceptions.IllegalUseException;
import msrp.utils.TextUtils;

/**
 * This class represents a response to a Transaction, which is considered a
 * transaction as well TODO use the parser to validate the response ?!
 * 
 * @author João André Pereira Antunes
 * 
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
                "Constructing a SENDRESPONSE "
                    + "with an original transaction that isn't a SEND, transaction: "
                    + transaction);

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
        // validate the response code
        if (responseCode != 200 && responseCode != 400 && responseCode != 403
            && responseCode != 408 && responseCode != 413
            && responseCode != 415 && responseCode != 423
            && responseCode != 481 && responseCode != 501
            && responseCode != 506)
            throw new IllegalUseException("Creating a transaction response "
                + "with an invalid response code of: " + responseCode);

        StringBuilder response = new StringBuilder(256);
        response.append("MSRP ").append(transaction.tID).append(" ").append(responseCode);
        if (comment != null)
        	response.append(" ").append(comment);

        response.append("\r\nTo-Path: ").append(
                transaction.fromPath[transaction.fromPath.length - 1]);
        response.append("\r\nFrom-Path: ").append(
                transaction.toPath[transaction.toPath.length - 1]);
        response.append("\r\n-------").append(transaction.tID).append("$\r\n");

        this.fromPath = transaction.toPath;
        this.toPath = transaction.fromPath;
        byte[] contentBytes = response.toString().getBytes(TextUtils.utf8);
        content = ByteBuffer.wrap(contentBytes);
        content.rewind();
    }

    /**
     * Constructor to create the incoming transaction
     * 
     * @param responseCode one of the response codes defined on RFC 4975
     * @param transaction the transaction related with this
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
     * @return an int representing the number of bytes remaining for this
     *         response
     */
    public int getNumberBytesRemaining()
    {
        return content.remaining();
    }

    /**
     * Method that gets bulks of data (int maximum)
     * 
     * @param toFill byte array to be filled
     * @param offset the offset index to start filling the outData
     * 
     * @return the number of bytes filled of the array
     * @throws IndexOutOfBoundsException if the offset is bigger than the array
     *             length
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
        content.get(toFill, 0, lengthToTransfer);
        return lengthToTransfer;
    }

    @Override
    public byte get()
    {
        return content.get();
    }

    @Override
    public boolean hasData()
    {
        return content.hasRemaining();
    }

    @Override
    public String toString()
    {
        return "Transaction response of Tx[" + tID + "] response code["
            + responseCode + "]";
    }

    /**
     * Seen that we use the content field to put the end line we will always
     * return false on this method call
     */
    @Override
    public boolean hasEndLine()
    {
        return false;
    }

    @Override
    protected boolean isIncomingResponse()
    {
        if (direction == IN)
            return true;
        return false;
    }
}