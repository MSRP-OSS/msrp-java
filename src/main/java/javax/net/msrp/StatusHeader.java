/* Copyright © João Antunes 2008
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
 * @author João André Pereira Antunes 2008
 */
public class StatusHeader
{
    private int namespace;

    private int statusCode;

    private String comment;

    /**
     * Constructs a StatusHeader object
     * 
     * @param namespace String representing the namespace (RFC4975)
     * @param statusCode String representing the status-code (RFC4975)
     * @param comment Represents the comment (RFC4975)
     * @throws InvalidHeaderException invalid response code, namespaces, etc.
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
        if (!ResponseCode.isValid(this.statusCode))
            throw new InvalidHeaderException(ResponseCode.toString(this.statusCode));

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
