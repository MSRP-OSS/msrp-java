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


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import javax.net.msrp.Connection;
import javax.net.msrp.exceptions.*;
import javax.net.msrp.utils.NetworkUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the class responsible for accepting incoming TCP connection requests
 * and generating the Connection object.
 * 
 * @author João André Pereira Antunes
 */
public class Connections
    extends Connection
    implements Runnable
{
    /**
     * The logger associated with this class
     */
    private static final Logger logger =
        LoggerFactory.getLogger(Connections.class);

    public Connections(InetAddress address)
    {
        try
        {
            random = new Random();
            if (NetworkUtils.isLinkLocalIPv4Address(address))
            {
                logger.info("Connections: given address is a local one: "
                    + address);
            }
            serverSocketChannel =
                SelectorProvider.provider().openServerSocketChannel();

            // bind the socket to a local temp port.
            ServerSocket socket = serverSocketChannel.socket();
            InetSocketAddress socketAddr = new InetSocketAddress(address, 0);

            socket.bind(socketAddr);

            // fill the localURI variable that contains the uri parts that are
            // associated with this connection (scheme[protocol], host and port)
            localURI =
                new URI("msrp", null, address.getHostAddress(), socket
                    .getLocalPort(), null, null, null);
            Thread server = new Thread(this);
            server.setName("Connections: " + localURI + " server");
            server.start();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private boolean hasStarted = false;

    private Thread associatedThread = null;

    private ServerSocketChannel serverSocketChannel = null;

    private HashMap<URI, Session> urisSessionsToIdentify =
        new HashMap<URI, Session>();

    // Protected constructor is sufficient to suppress unauthorized calls to the
    // constructor
    protected Connection Connection()
    {
        try
        {
            random = new Random();

            boolean localAddress = false;

            InetAddress newAddress = InetAddress.getLocalHost();

            // sanity check, check that the given address is a local one where a
            // socket
            // could be bound
            InetAddress local[] =
                InetAddress.getAllByName(InetAddress.getLocalHost()
                    .getHostName());

            for (InetAddress inetAddress : local)
            {
                if (inetAddress.equals(newAddress))
                    localAddress = true;
            }
            if (!localAddress)
                throw new UnknownHostException(
                    "the given adress is not a local one");

            serverSocketChannel =
                SelectorProvider.provider().openServerSocketChannel();

            // bind the socket to a local temp. port.
            ServerSocket socket = serverSocketChannel.socket();
            InetSocketAddress socketAddr = new InetSocketAddress(newAddress, 0);

            socket.bind(socketAddr);

            // fill the localURI variable that contains the uri parts that are
            // associated with this connection (scheme[protocol], host and port)
            localURI =
                new URI("msrp", null, newAddress.getHostAddress(), socket
                    .getLocalPort(), null, null, null);
        }
        catch (Exception e)
        {
            logger.error("Error! Connection did not get an associated socket");
        }
        return this;
    }

    /**
     * @uml.property name="_connections"
     * @uml.associationEnd multiplicity="(1 1)"
     *                     inverse="connections:javax.net.msrp.TransactionManager"
     */
    private TransactionManager transactionManager = null;

    private HashSet<URI> existingURISessions = new HashSet<URI>();

    public TransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    protected boolean hasStarted()
    {
        return hasStarted;
    }

    public Connection connection(String relevantURI)
    {
        return null;
    }

    public void setTransactionManager(TransactionManager transactionManager)
    {
        this.transactionManager = transactionManager;
    }

    /**
     * SingletonHolder is loaded on the first execution of
     * Singleton.getInstance() or the first access to SingletonHolder.instance ,
     * not before. private static class SingletonHolder { private final static
     * Connections INSTANCE = new Connections();
     * 
     * }
     * 
     * public static Connections getInstance() { return
     * SingletonHolder.INSTANCE; }
     */
    public Thread getAssociatedThread()
    {
        return associatedThread;
    }

    @Override
    public void run()
    {
        hasStarted = true;
        associatedThread = Thread.currentThread();
        try
        {
            // Use the current serverSocketChannel to accept new connections
            while (true)
            {
                Connection connection =
                    new Connection(serverSocketChannel.accept());
                stack.addConnection(connection);
                Thread newConnThread = new Thread(connection);
                newConnThread.setName("connection: " + connection.getLocalURI()
                    + " by connections newConnThread");
                newConnThread.start();

            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
    }

    protected URI generateNewUri()
        throws ImplementationException, URISyntaxException
    {
        if (localURI == null)
            throw new ImplementationException(
            		"Absurd error, Connections doesn't have the needed socket info");

        URI newURI = newUri();

        int i = 0;
        while (existingURISessions.contains(newURI))
        {
            i++;
            newURI = newUri();
        }
        existingURISessions.add(newURI);

        logger.trace("generated new URI, value of i=" + i);

        if (hasStarted() && getAssociatedThread().isAlive())
        {
            ;
        }
        else
        {
            associatedThread = new Thread(this);
            associatedThread.setName("Connections: " + localURI
                + " associatedThread");
            associatedThread.start();
            hasStarted = true;
        }
        return newURI;
    }

    protected void addUriToIdentify(URI uri, Session session)
    {
        urisSessionsToIdentify.put(uri, session);
    }

    /**
     * Returns a session from the list of sessions negotiated but yet to
     * identify
     * 
     * @param uri of the session to be identified
     * @return the associated session to the given uri, taken from the list of
     *         sessions negotiated but yet to identify
     */
    public Session sessionToIdentify(URI uri)
    {
        return urisSessionsToIdentify.get(uri);
    }

    private Stack stack = Stack.getInstance();

    protected void identifiedSession(Session session)
    {
    	urisSessionsToIdentify.remove(session.getURI());
        existingURISessions.add(session.getURI());
        session.setConnection(stack.getConnectionByLocalURI(
        		NetworkUtils.getCompleteAuthority(session.getNextURI())));
        stack.addActiveSession(session);
        // TODO disable the alarm
    }

    private ThreadGroup connectionsGroup =
        new ThreadGroup("MSRP Stack connections");

    protected void startConnectionThread(Runnable connection,
        ThreadGroup ioOperationGroup)
    {
        Thread newConnectionThread = new Thread(ioOperationGroup, connection);
        newConnectionThread.setName("Connections: " + localURI
            + " newConnectionThread");
        newConnectionThread.start();
    }

    /**
     * @return the connectionsGroup
     */
    protected ThreadGroup getConnectionsGroup()
    {
        return connectionsGroup;
    }
}
