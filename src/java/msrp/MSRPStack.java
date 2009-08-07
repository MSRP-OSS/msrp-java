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
package msrp;

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import msrp.exceptions.ImplementationException;
import msrp.exceptions.InternalErrorException;
import msrp.exceptions.NonValidSessionSuccessReportException;
import msrp.exceptions.ProtocolViolationException;
import msrp.messages.Message;

/**
 * @author D
 * 
 */
public class MSRPStack
    implements Observer
{

    // Protected constructor is sufficient to suppress unauthorized calls to the
    // constructor
    protected MSRPStack()
    {
        localUriConnections = new HashMap<URI, Connection>();
        sessionConnections = new HashMap<URI, Connection>();
        transactions = new HashMap<String, Transaction>();

    }

    private HashMap<URI, Connection> localUriConnections =
        new HashMap<URI, Connection>();

    private HashMap<URI, Connection> sessionConnections =
        new HashMap<URI, Connection>();

    private HashMap<URI, Session> activeSessions = new HashMap<URI, Session>();

    /**
     * Stores all the connections objects mapped to the address they are bound
     * to
     */
    private static HashMap<InetAddress, Connections> addressConnections =
        new HashMap<InetAddress, Connections>();


    private static Random test = new Random();

    /**
     * @param address the ip address to bind to
     * @return a Connections instance bound to the given address if it exists,
     *         or creates one
     */
    protected static Connections getConnectionsInstance(InetAddress address)
    {
        Connections connectionsToReturn = addressConnections.get(address);
        if (connectionsToReturn != null)
        {
            return connectionsToReturn;
        }
        connectionsToReturn = new Connections(address);
        addressConnections.put(address, connectionsToReturn);
        return connectionsToReturn;

    }

    /**
     * TODO (?!) relocate method?! needs to be a method?! FIXME (?!) REFACTORING
     * Method that generates and sends a success report based on the range of
     * the original transaction or of the whole message It interrupts any
     * interruptible ongoing transaction as specified in rfc 4975
     * 
     * @param message Message associated with the report to be generated
     * @param transaction Transaction that triggered the need to send the report
     *            is used to gather the range of bytes on which this report will
     *            report on and the associated session aswell. the value of
     *            transaction can be null if we are invoking this method in
     *            order to generate a report for the whole message
     * 
     * 
     */
    protected static void generateAndSendSuccessReport(Message message,
        Transaction transaction)
    {
        try
        {
            Session session = transaction.getSession();
            Transaction successReport =
                new SuccessReport(message, transaction.getSession(),
                    transaction);
            session.getTransactionManager().addPriorityTransaction(
                successReport);

        }
        catch (NonValidSessionSuccessReportException e)
        {
            e.printStackTrace();
        }
        catch (InternalErrorException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ImplementationException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (ProtocolViolationException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    protected void addActiveSession(Session session)
    {
        activeSessions.put(session.getURI(), session);
    }


    private HashMap<String, Transaction> transactions;

    
    /**
     * The default value for the "short" message bytes
     * 
     * "short" messages are the ones that can be put in memory
     */
    protected static final int DEFAULTSHORTMESSAGEBYTES = 1025*1024;
    /**
     * Maximum number of bytes per un-interruptible transaction
     */
    protected static final int MAXIMUMUNINTERRUPTIBLE = 2048;

    /**
     * Field that has the number of bytes of the short message
     */
    private static int shortMessageBytes = DEFAULTSHORTMESSAGEBYTES;
    
    /**
     * Method used to set the short message bytes of this stack.
     * 
     * A "short" message is a message that can be put in memory.
     * the definition of this short message parameter is used to allow the
     * stack to handle safely messages without storing them in file and 
     * without consuming too much memory.
     * To note: that ATM the number of received messages that need to be stored
     * (which success report = yes) has no way of being controlled FIXME
     *
     * @param newValue the new int value of the short message
     */
    public static void setShortMessageBytes (int newValue)
    {
        shortMessageBytes = newValue;
    }
    
    /**
     * Getter for the value shortMessageBytes
     * see the field or the setter for a definition of a short message
     * @return an int that has the actual shortMessageBytes
     */
    public static int getShortMessageBytes()
    {
        return shortMessageBytes;
    }
    
    
    
    
    /**
     * 
     * @param connection adds the received connection into the connections list
     */
    protected void addConnection(Connection connection)
    {
        if (connection == null)
            return;
        localUriConnections.put(connection.getLocalURI(), connection);

    }

    /**
     * adds a connection associated with the session URI
     * 
     * @param uri the URI to add to the existing connections
     * @param connection the connection associated with this URI
     */
    public void addConnection(URI uri, Connection connection)
    {
        sessionConnections.put(uri, connection);

    }

    /**
     * Returns an activeConnection
     * 
     * @return
     */
    protected Connection getActiveConnection()
    {
        for (Connection conn : sessionConnections.values())
        {
            if (conn.isBound())
                return conn;
        }
        return null;
    }

    /**
     * 
     * @param localUriToSearch the local uri to which we are searching for a
     *            connection
     * @return returns the connection associated with the given local uri
     */
    protected Connection getConnectionByLocalURI(URI localUriToSearch)
    {
        return localUriConnections.get(localUriToSearch);
    }

    /**
     * 
     * @param connection the connection to delete from the connections list
     */
    protected void delConnection(Connection connection)
    {
        /*
         * if (connection == null) return;
         * _urlConnections.remove(connection.getURL());
         * _connections.remove(connection);
         */
    }

    /**
     * in rfc 4975: "Non-SEND request bodies MUST NOT be larger than 10240
     * octets."
     */
    protected static final int MAXNONSENDBODYSIZE = 10240;

    protected void addTransaction(String tid, Transaction transaction)
        throws Exception
    {
        if (!validTransaction(tid))
            // TODO FIXME this shouldn't be possible
            throw new Exception("Error transaction ID no longer valid");
        transactions.put(tid, transaction);
    }

    /**
     * Asserts if this transactionId exists
     * 
     * @param tid Transaction ID to be validated
     * @return true if it's new therefore valid or false if it's not new
     *         therefore invalid
     */
    protected boolean validTransaction(String tid)
    {
        if (transactions.containsKey(tid))
            return false;
        return true;
    }

    /**
     * SingletonHolder is loaded on the first execution of
     * Singleton.getInstance() or the first access to SingletonHolder.instance ,
     * not before.
     */
    private static class SingletonHolder
    {
        private final static MSRPStack INSTANCE = new MSRPStack();
    }

    public static MSRPStack getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    @Override
    public void update(Observable arg0, Object arg1)
    {
        // TODO Auto-generated method stub

    }

    protected boolean isActive(URI uri)
    {
        return activeSessions.containsKey(uri);
    }

    protected Session getSession(URI uri)
    {
        return activeSessions.get(uri);
    }

}
