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
/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package javax.net.msrp.utils;

import java.net.*;

/**
 * Utility methods and fields to use when working with network addresses.
 *
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Vincent Lucas
 */
public class NetworkUtils
{
    /**
     * A string containing the "any" local address.
     */
    public static final String IN_ADDR_ANY = "0.0.0.0";

    /**
     * Determines whether the address is the result of windows auto configuration.
     * (i.e. One that is in the 169.254.0.0 network)
     * @param add the address to inspect
     * @return true if the address is autoconfigured by windows, false otherwise.
     */
    public static boolean isWindowsAutoConfiguredIPv4Address(InetAddress add)
    {
        return (add.getAddress()[0] & 0xFF) == 169
            && (add.getAddress()[1] & 0xFF) == 254;
    }

    /**
     * Determines whether the address is an IPv4 link local address. IPv4 link
     * local addresses are those in the following networks:
     *
     * 10.0.0.0    to 10.255.255.255
     * 172.16.0.0  to 172.31.255.255
     * 192.168.0.0 to 192.168.255.255
     *
     * @param add the address to inspect
     * @return true if add is a link local ipv4 address and false if not.
     */
    public static boolean isLinkLocalIPv4Address(InetAddress add)
    {
        if (add instanceof Inet4Address)
        {
            byte address[] = add.getAddress();
            if ( (address[0] & 0xFF) == 10)
                return true;
            if ( (address[0] & 0xFF) == 172
                && (address[1] & 0xFF) >= 16 && address[1] <= 31)
                return true;
            if ( (address[0] & 0xFF) == 192
                && (address[1] & 0xFF) == 168)
                return true;
            return false;
        }
        return false;
    }

    /**
     * Verifies whether <tt>address</tt> could be an IPv6 address string.
     *
     * @param address the String that we'd like to determine as an IPv6 address.
     *
     * @return true if the address containaed by <tt>address</tt> is an ipv6
     * address and falase otherwise.
     */
    public static boolean isIPv6Address(String address)
    {
        return (address != null && address.indexOf(':') != -1);
    }

    /**
     * Returns array of hosts from the SRV record of the specified domain.
     * The records are ordered against the SRV record priority
     * @param domain the name of the domain we'd like to resolve (_proto._tcp
     * included).
     * @return an array of InetSocketAddress containing records returned by the DNS
     * server - address and port .
     * @throws ParseException if <tt>domain</tt> is not a valid domain name.
     
    public static InetSocketAddress[] getSRVRecords(String domain)
        throws ParseException
    {
        Record[] records = null;
        try
        {
            Lookup lookup = new Lookup(domain, Type.SRV);
            records = lookup.run();
        }
        catch (TextParseException tpe)
        {
            logger.error("Failed to parse domain="+domain, tpe);
            throw new ParseException(tpe.getMessage(), 0);
        }
        if (records == null)
        {
            return null;
        }

        String[][] pvhn = new String[records.length][4];
        for (int i = 0; i < records.length; i++)
        {
            SRVRecord srvRecord = (SRVRecord) records[i];
            pvhn[i][0] = "" + srvRecord.getPriority();
            pvhn[i][1] = "" + srvRecord.getWeight();
            pvhn[i][2] = "" + srvRecord.getPort();
            pvhn[i][3] = srvRecord.getTarget().toString();
            if (pvhn[i][3].endsWith("."))
            {
                pvhn[i][3] = pvhn[i][3].substring(0, pvhn[i][3].length() - 1);
            }
        }

        /* sort the SRV RRs by RR value (lower is preferred) 
        Arrays.sort(pvhn, new Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                return (Integer.parseInt( ( (String[]) o1)[0])
                        - Integer.parseInt( ( (String[]) o2)[0]));
            }
        });

        /* put sorted host names in an array, get rid of any trailing '.' 
        InetSocketAddress[] sortedHostNames = new InetSocketAddress[pvhn.length];
        for (int i = 0; i < pvhn.length; i++)
        {
            sortedHostNames[i] = 
                new InetSocketAddress(pvhn[i][3], Integer.valueOf(pvhn[i][2]));
        }

        if (logger.isTraceEnabled())
        {
            logger.trace("DNS SRV query for domain " + domain + " returned:");
            for (int i = 0; i < sortedHostNames.length; i++)
            {
                logger.trace(sortedHostNames[i]);
            }
        }
        return sortedHostNames;

    }*/

    /** strip a given uri to only the parts: "scheme://authority" and return that.
     * @param uri to strip
     * @return stripped URI
     */
    public static URI getCompleteAuthority(URI uri) {
    	try {
			return new URI(String.format("%s://%s", uri.getScheme(), uri.getAuthority()));
		} catch (URISyntaxException e) {
			return null;
		}
    }
}
