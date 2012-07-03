/* Copyright � Jo�o Antunes 2008
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
package javax.net.msrp;


import javax.net.msrp.exceptions.InvalidHeaderException;

/**
 * This class implements the Status header as defined in RFC 4975
 * 
 * @author Jo�o Andr� Pereira Antunes 2008
 * 
 */
public class StatusHeader
{
    private int namespace;

    private int statusCode;

    private String comment;

    /**
     * Generates a new StatusHeader class
     * 
     * @param namespace String representing the namespace defined in RFC4975
     * @param statusCode String representing the status-code as defined in
     *            RFC4975
     * @param comment String representing the comment as defined in
     *            RFC4975
     * @throws InvalidHeaderException if it was found any error with the parsing
     *             of this status header field
     */
    protected StatusHeader(String namespace, String statusCode, String comment)
        throws InvalidHeaderException
    {
        /*
         * sanity checks, exceptions should never be thrown here due to the fact
         * that the strings are already filtered by the regexp pattern.
         */
        this.statusCode = Integer.parseInt(statusCode);
        this.namespace = Integer.parseInt(namespace);
        this.comment = comment;

        /* Validate the status code */
        if (this.statusCode != 200 && this.statusCode != 400
            && this.statusCode != 403 && this.statusCode != 408
            && this.statusCode != 413 && this.statusCode != 415
            && this.statusCode != 423 && this.statusCode != 481
            && this.statusCode != 501 && this.statusCode != 506)
            throw new InvalidHeaderException("Error in Status header field, "
                + "the given status code is not supported");

        /* Validate the namespace */
        if (this.namespace != 000)
            throw new InvalidHeaderException("Error in Status header field, "
                + "the given namespace is not supported");
    }

    public int getStatusCode()
    {
        return statusCode;
    }

    public int getNamespace()
    {
        return namespace;
    }

    public String getComment()
    {
        return comment;
    }
}