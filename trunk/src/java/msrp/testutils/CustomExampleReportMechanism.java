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

import msrp.*;
import msrp.messages.Message;

/**
 * Class used to resolve Issues #2 and #3 This class is a custom Report
 * Mechanism used for test purposes to validate the functionality of using
 * different report mechanisms with the protocol.
 * 
 * This class is used to test both the Success report generation and the sent
 * bytes call on the MSRP Listener
 * 
 * List of JUNIT tests that use this class:
 * 
 * @author João André Pereira Antunes
 * 
 */
public class CustomExampleReportMechanism
    extends ReportMechanism
{

    /**
     * Constructor for the singleton class default report mechanism defined to
     * protect unauthorized instances of this class
     */
    public CustomExampleReportMechanism()
    {
    }

    /**
     * SingletonHolder is loaded on the first execution of
     * Singleton.getInstance() or the first access to SingletonHolder.instance ,
     * not before.
     */
    private static class SingletonHolder
    {
        private final static CustomExampleReportMechanism INSTANCE =
            new CustomExampleReportMechanism();

    }

    public static CustomExampleReportMechanism getInstance()
    {
        return SingletonHolder.INSTANCE;
    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.ReportMechanism#getTriggerGranularity()
     */
    /**
     * We have chosen a custom different value of the 1024 of the default just
     * to make sure that it can work with a different value, the 2014 value
     * could be some other
     */
    @Override
    public int getTriggerGranularity()
    {
        return 2014;
    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.ReportMechanism#shouldGenerateReport(msrp.Message, long, long)
     */
    /**
     * for testing purposes, we put the same behavior on this method than the one on the DefaultReportMechanism shouldTriggerSentHook, basicly:
     * 
     * if the message size is unknown always trigger
     * 
     * The default sent hook granularity is for each 10% of the message if the
     * message is bigger than 500K
     * 
     * else only trigger once when it passes the 49% to 50% barrier
     * 
     * also if the message is complete trigger it
     * @see DefaultReportMechanism#shouldTriggerSentHook(Message, Session, long)
     */
    @Override
    public boolean shouldGenerateReport(Message message, long lastCallCount,
        long callCount)
    {
    	if (message.isComplete())
    		return true;
        if (message.getSize() == Message.UNKNWON)
            return true;
        else if (message.getSize() > Message.UNINTIALIZED)
        {
            int lastPercentage =
                (int) lastCallCount * 100 / (int)message.getSize();
            int currentPercentage =
                (int) message.getDataContainer().currentReadOffset()
                    * 100 / (int)message.getSize();
            if (message.getSize() <= 500 * 1024)
            {
                if (lastPercentage < 50 && currentPercentage >= 50)
                    return true;
            }
            else
            {
                if (lastPercentage / 10 == (currentPercentage / 10) - 1)
                    return true;
                return false;

            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see msrp.ReportMechanism#shouldTriggerSentHook(msrp.Message,
     * msrp.Session, long)
     */
    /**
     * This method behaves as the method on the DefaultReportMechanism.shouldGenerateReport method, basicly:
     * it only returns true if the message is complete
     * 
     */
    @Override
    public boolean shouldTriggerSentHook(Message outgoingMessage,
        Session session, long nrBytesLastCall)
    {
        if (outgoingMessage.isComplete())
            return true;
        return false;
    }

}
