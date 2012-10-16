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

    /** Transaction associated with the NICKNAME method */
    NICKNAME,

    /** Represents the unsupported methods */
    UNSUPPORTED,

    /** Transaction that is a response to a SEND or NICKNAME */
    RESPONSE,
}