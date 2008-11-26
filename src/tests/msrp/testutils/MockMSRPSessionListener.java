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
package msrp.testutils;

import java.util.ArrayList;

import msrp.DataContainer;
import msrp.IncomingMessage;
import msrp.MSRPSessionListener;
import msrp.MemoryDataContainer;
import msrp.Message;
import msrp.ReportMechanism;
import msrp.Session;
import msrp.Transaction;

/**
 * Class used to test the callbacks of the MSRPStack
 * 
 * @author João André Pereira Antunes 2008
 * 
 */
public class MockMSRPSessionListener
    implements MSRPSessionListener
{

    /* The field results of the calls: */
    private Session acceptHookSession;

    private Message acceptHookMessage;

    private Session receiveMessageSession;

    private Message receiveMessage;

    private Session receivedReportSession;

    private Transaction receivedReportTransaction;

    private Session updateSendStatusSession;

    private Message updateSendStatusMessage;

    private Session abortedMessageSession;
    
    private IncomingMessage abortedMessage;
    
    private Boolean acceptHookResult;

    /**
     * The name of the object
     */
    private String name;

    /**
     * The external to be set, or not data container object that is going to be
     * used on the acceptHook if it exists upon the calling of the trigger
     */
    private DataContainer externalDataContainer;

    /**
     * Counter for the send status update, the number of elements reflect the
     * number of calls to the updateSendStatus method and its values the number
     * of bytes that the function was called with
     * 
     * @see MSRPSessionListener#updateSendStatus(Session, Message, long)
     */
    public ArrayList<Long> updateSendStatusCounter = new ArrayList<Long>();

    /**
     * A counter for the number of success reports received
     */
    public ArrayList<Long> successReportCounter = new ArrayList<Long>();

    /**
     * A counter for the number of message aborts received. the size of this
     * array counts how many message abortions and its values the bytes that
     * were received before the message was aborted
     */
    public ArrayList<Long> abortMessageCounter = new ArrayList<Long>();

    /**
     * Constructor of the mock session listener
     * 
     * @param name the String that names this constructor, used for debug
     *            purposes
     */
    public MockMSRPSessionListener(String name)
    {
        this.name = name;
    }

    /**
     * Convenience method used for debug purposes, that prints the method and
     * the optionalStrings along with this mock name on System.out
     * 
     * @param method the method on which this function was called
     * @param optionalStrings
     */
    private void report(String method, String[] optionalStrings)
    {
        System.out.print(method + " Mock GOT CALLED!!!!!!! mock: " + this.name);
        if (optionalStrings == null)
        {
            System.out.println();
            return;
        }
        for (String string : optionalStrings)
        {
            System.out.print(" " + string);
        }
        System.out.println();

    }

    @Override
    public boolean acceptHook(Session session, IncomingMessage message)
    {
        while (acceptHookResult == null)
        {
            synchronized (this)
            {
                try
                {
                    report("acceptHook", null);
                    wait();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException();
                }
            }
        }
        boolean toReturn = acceptHookResult.booleanValue();

        String[] array =
        { "is going to return: " + toReturn };
        report("acceptHook", array);

        acceptHookMessage = message;
        acceptHookSession = session;
        if (externalDataContainer == null)
        {
            MemoryDataContainer dataContainer =
                new MemoryDataContainer((int) message.getSize());
            message.setDataContainer(dataContainer);
        }
        else
        {
            message.setDataContainer(externalDataContainer);
        }
        acceptHookResult = null;
        synchronized (this)
        {

            notifyAll();
            return toReturn;

        }
    }

    @Override
    public void receiveMessage(Session session, Message message)
    {
        report("receiveMessage", null);
        receiveMessage = message;
        receiveMessageSession = session;
        synchronized (this)
        {
            notifyAll();
        }
    }

    @Override
    public void receivedReport(Session session, Transaction tReport)
    {

        String[] arrayStrings =
            { "received confirmation that: " + tReport.getByteRange()[1]
                + " bytes were sent; " + (tReport.getByteRange()[1] * 100)
                / tReport.getTotalMessageBytes() + "% sent" };
        report("receivedReport", arrayStrings);
        receivedReportSession = session;
        receivedReportTransaction = tReport;
        synchronized (successReportCounter)
        {
            successReportCounter.add(tReport.getByteRange()[1]);
            successReportCounter.notifyAll();
        }

    }

    @Override
    public void updateSendStatus(Session session, Message message,
        long numberBytesSent)
    {
        String[] arrayStrings =
            { "sent: " + numberBytesSent + " bytes; " + (numberBytesSent * 100)
                / message.getSize() + "% sent" };
        report("updateSendStatus", arrayStrings);
        updateSendStatusSession = session;
        updateSendStatusMessage = message;
        synchronized (updateSendStatusCounter)
        {
            updateSendStatusCounter.add(numberBytesSent);
            updateSendStatusCounter.notifyAll();

        }
    }

    @Override
    public void abortedMessage(Session session, IncomingMessage message)
    {
        String[] arrayStrings =
        { "received: " + message.getReceivedBytes() + " bytes; " + (message.getReceivedBytes() * 100)
            / message.getSize() + "% " };
        report("abortedMessage", arrayStrings);
        abortedMessage= message;
        abortedMessageSession = session;
        synchronized (abortMessageCounter)
        {
            abortMessageCounter.add(message.getReceivedBytes());
            abortMessageCounter.notifyAll();
            
        }
        

    }

    /**
     * @return the acceptHookSession
     */
    public Session getAcceptHookSession()
    {
        return acceptHookSession;
    }

    /**
     * @return the acceptHookMessage
     */
    public Message getAcceptHookMessage()
    {
        return acceptHookMessage;
    }

    /**
     * @return the receiveMessageSession
     */
    public Session getReceiveMessageSession()
    {
        return receiveMessageSession;
    }

    /**
     * @return the receiveMessage
     */
    public Message getReceiveMessage()
    {
        return receiveMessage;
    }

    /**
     * @return the receivedReportSession
     */
    public Session getReceivedReportSession()
    {
        return receivedReportSession;
    }

    /**
     * @return the receivedReportTransaction
     */
    public Transaction getReceivedReportTransaction()
    {
        return receivedReportTransaction;
    }

    /**
     * @return the updateSendStatusSession
     */
    public Session getUpdateSendStatusSession()
    {
        return updateSendStatusSession;
    }

    /**
     * @return the updateSendStatusMessage
     */
    public Message getUpdateSendStatusMessage()
    {
        return updateSendStatusMessage;
    }

    /**
     * @return the abortedMessageSession
     */
    public Session getAbortedMessageSession()
    {
        return abortedMessageSession;
    }

    /**
     * @return the abortedMessage
     */
    public IncomingMessage getAbortedMessage()
    {
        return abortedMessage;
    }

    /**
     * @param acceptHookResult the acceptHookResult to set
     */
    public void setAcceptHookResult(Boolean acceptHookResult)
    {
        this.acceptHookResult = acceptHookResult;
    }

    public void setDataContainer(DataContainer dc)
    {
        externalDataContainer = dc;
    }

}
