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

import static org.junit.Assert.*;

import javax.net.msrp.Counter;

import org.junit.*;

/**
 * This class is used to test the Counter implementation. The counter serves the
 * main purpose of accounting for the chunks of a Message The counter class
 * needs to be memory and execution time efficient. It's methods are called
 * several times and it's previous implementations where the main reason of the
 * lack of performance The counter class currently accomplishes it through an
 * order arraylist of "clusters". The algorithm is a bit complex to read, this
 * class ensures that it works as expected
 * 
 * These methods and tests are better understood by recurring to the planning
 * folder to better understand the algorithm of the counter and the tests. There
 * is a very useful image on the Project Definition
 * 
 * 
 * @author João André Pereira Antunes
 * 
 */
public class TestCounter
{
    Counter testCounter;

    /**
     * This method sets up a counter with three separate, with some interval
     * between them, clusters
     */
    @Before
    public void setUpCounter()
    {
        testCounter = new Counter(null);
        testCounter.register(1, 20);
        testCounter.register(50, 50);
        testCounter.register(150, 50);
    }

    /**
     * This tests the effectiveness of the Counter register algorithm see image
     * in the planning/project definition .mmap
     * 
     * @see Counter#register(long, long)
     */
    @Test
    public void testCounterEffectiveness()
    {
        assertEquals("Erroneous set-up of the counter, it should had "
            + "120 bytes (20+50+50)", 120, testCounter.getCount());
        assertEquals("The number of continuous bytes should be zero", 0,
            testCounter.getNrConsecutiveBytes());
        // first case
        assertTrue("The register didn't returned true as expected", testCounter
            .register(0, 10));
        assertEquals("It should have had 21 bytes", 21, testCounter
            .getNrConsecutiveBytes());

        assertEquals("Erroneous set-up of the counter, it should had "
            + "121 bytes (20+50+50+1)", 121, testCounter.getCount());

        // test the clusters
        assertEquals("Number of clusters should be three", 3,
            testCounter.counter.size());

        // second case
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(30, 25));
        assertEquals("It should have had 20+50+50+1+20=141 bytes", 141,
            testCounter.getCount());
        assertEquals("It should have had 21 bytes", 21, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be three", 3,
            testCounter.counter.size());
        long expectedCluster[] = new long[2];
        expectedCluster[0] = 30;
        expectedCluster[1] = 70;
        assertArrayEquals("Error, second cluster differs from the expected",
            expectedCluster, testCounter.counter.get(1));

        // third case
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(5, 5));
        assertEquals("It should have had 20+50+50+1+20=141 bytes", 141,
            testCounter.getCount());
        assertEquals("It should have had 21 bytes", 21, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be three", 3,
            testCounter.counter.size());
        expectedCluster[0] = 0;
        expectedCluster[1] = 21;
        assertArrayEquals("Error, first cluster differs from the expected",
            expectedCluster, testCounter.counter.get(0));

        // fourth case
        assertTrue("The register didn't returned true as expected", testCounter
            .register(10, 15));
        assertEquals("It should have had 20+50+50+1+20+4=145 bytes", 145,
            testCounter.getCount());
        assertEquals("It should have had 25 bytes", 25, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be three", 3,
            testCounter.counter.size());
        expectedCluster[0] = 0;
        expectedCluster[1] = 25;
        assertArrayEquals("Error, first cluster differs from the expected",
            expectedCluster, testCounter.counter.get(0));

        // fifth case
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(27, 1));
        assertEquals("It should have had 20+50+50+1+20+4+1=146 bytes", 146,
            testCounter.getCount());
        assertEquals("It should have had 25 bytes", 25, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be four", 4,
            testCounter.counter.size());
        expectedCluster[0] = 27;
        expectedCluster[1] = 1;
        assertArrayEquals("Error, second cluster differs from the expected",
            expectedCluster, testCounter.counter.get(1));

        // sixth case
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(120, 100));
        assertEquals("It should have had 20+50+50+1+20+4+1+50=196 bytes", 196,
            testCounter.getCount());
        assertEquals("It should have had 25 bytes", 25, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be four", 4,
            testCounter.counter.size());
        expectedCluster[0] = 120;
        expectedCluster[1] = 100;
        assertArrayEquals("Error, fourth cluster differs from the expected",
            expectedCluster, testCounter.counter.get(3));

        // seventh case
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(90, 40));
        assertEquals("It should have had 20+50+50+1+20+4+1+50+20=216 bytes",
            216, testCounter.getCount());
        assertEquals("It should have had 25 bytes", 25, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be three", 3,
            testCounter.counter.size());
        expectedCluster[0] = 30;
        expectedCluster[1] = 190;
        assertArrayEquals("Error, third cluster differs from the expected",
            expectedCluster, testCounter.counter.get(2));

        // Eighth case
        // create first the fourth cluster
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(230, 10));
        assertEquals("It should have had 20+50+50+1+20+4+1+50+20+10=226 bytes",
            226, testCounter.getCount());
        assertEquals("It should have had 25 bytes", 25, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be four", 4,
            testCounter.counter.size());
        expectedCluster[0] = 230;
        expectedCluster[1] = 10;
        assertArrayEquals("Error, fourth cluster differs from the expected",
            expectedCluster, testCounter.counter.get(3));

        // add the overlapping and frontier cluster that will merge the third
        // and fourth clusters
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(210, 20));
        assertEquals(
            "It should have had 20+50+50+1+20+4+1+50+20+10+10=236 bytes", 236,
            testCounter.getCount());
        assertEquals("It should have had 25 bytes", 25, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be three", 3,
            testCounter.counter.size());
        expectedCluster[0] = 30;
        expectedCluster[1] = 210;
        assertArrayEquals("Error, third cluster differs from the expected",
            expectedCluster, testCounter.counter.get(2));

        // nineth case
        assertTrue("The register didn't returned true as expected", testCounter
            .register(19, 10));
        assertEquals(
            "It should have had 20+50+50+1+20+4+1+50+20+10+10+3=239 bytes",
            239, testCounter.getCount());
        assertEquals("It should have had 29 bytes", 29, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be four", 2,
            testCounter.counter.size());
        expectedCluster[0] = 0;
        expectedCluster[1] = 29;
        assertArrayEquals("Error, first cluster differs from the expected",
            expectedCluster, testCounter.counter.get(0));

        // tenth case
        assertTrue("The register didn't returned true as expected", testCounter
            .register(0, 250));
        assertEquals("It should have had 250 bytes", 250, testCounter
            .getCount());
        assertEquals("It should have had 250 bytes", 250, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be one", 1, testCounter.counter
            .size());
        expectedCluster[0] = 0;
        expectedCluster[1] = 250;
        assertArrayEquals("Error, first cluster differs from the expected",
            expectedCluster, testCounter.counter.get(0));
    }

    /**
     * specific test that fills the space between the clusters and checks at the
     * end that they merged into one see image in the planning/project
     * definition .mmap
     */
    @Test
    public void testCounterAglomeration()
    {
        long expectedCluster[] = new long[2];
        // 1 - add the glue clusters starting at the end
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(100, 50));
        assertEquals("It should have had 120+50=170 bytes", 170, testCounter
            .getCount());
        assertEquals("It should have had 0 bytes", 0, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be two", 2, testCounter.counter
            .size());
        expectedCluster[0] = 50;
        expectedCluster[1] = 150;
        assertArrayEquals("Error, second cluster differs from the expected",
            expectedCluster, testCounter.counter.get(1));

        // 2 - add the glue clusters starting at the end
        assertTrue("The register didn't returned false as expected",
            !testCounter.register(21, 29));
        assertEquals("It should have had 120+50+29=199 bytes", 199, testCounter
            .getCount());
        assertEquals("It should have had 0 bytes", 0, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be one", 1, testCounter.counter
            .size());
        expectedCluster[0] = 1;
        expectedCluster[1] = 199;
        assertArrayEquals("Error, first cluster differs from the expected",
            expectedCluster, testCounter.counter.get(0));

        // 3 - merge them all
        assertTrue("The register didn't returned true as expected", testCounter
            .register(0, 1));
        assertEquals("It should have had 200 bytes", 200, testCounter
            .getCount());
        assertEquals("It should have had 200 bytes", 200, testCounter
            .getNrConsecutiveBytes());
        // test the clusters
        assertEquals("Number of clusters should be one", 1, testCounter.counter
            .size());
        expectedCluster[0] = 0;
        expectedCluster[1] = 200;
        assertArrayEquals("Error, first cluster differs from the expected",
            expectedCluster, testCounter.counter.get(0));
    }

    /**
     * Test the typical receival of a message TODO this test is embedded in a
     * way in the other tests and is non essential as the other two before
     * already assert the good behavior of the algorithm
     * @TODO
     */
    @Test
    @Ignore("TODO")
    public void testContinuousNonOverlapping()
    {

    }

}
