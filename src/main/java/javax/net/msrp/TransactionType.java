/**
 * 
 */
package javax.net.msrp;

/**
 * @author tuijldert
 *
 */
public enum TransactionType
{
    /** Transaction associated with the SEND method */
    SEND,

    /** Transaction associated with the REPORT method */
    REPORT,

    /** Represents the unsupported methods */
    UNSUPPORTED,

    /** Transaction that is a response to a SEND */
    SENDRESPONSE,
}