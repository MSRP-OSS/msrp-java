/*
 * Copyright © João Antunes 2009 This file is part of MSRP Java Stack.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;

import javax.net.msrp.RegEx;

import static org.junit.Assert.*;
import org.junit.*;

/**
 * Class that tests the RegEx class thoroughly
 * 
 * @see RegEx
 * @author João André Pereira Antunes
 */
public class TestRegEx
{
    @Test
    public void testTokenRegex()
    {
        Matcher testMatcher;
        // Test with the empty string
        testMatcher = RegEx.token.matcher("");
        assertFalse(
            "Error, the empty string shouldn't be considered a token",
            testMatcher.matches());

        // with a single character from one of the groups
        testMatcher = RegEx.token.matcher("~");
        assertTrue("Error, the ~ char should be considered a token",
            testMatcher.matches());

        // with one character from each group
        testMatcher = RegEx.token.matcher("!#*-1A^");
        assertTrue(
                "Error, token regex badly defined: !#*-1A^ should be considered a valid token",
                testMatcher.matches());

        // a string with a blank space shouldn't be a token
        testMatcher = RegEx.token.matcher("test ");
        assertFalse(
                "Error, a string with a space shouldn't be a considered a token",
                testMatcher.matches());

        // Just something extra to remember this issue by:
        testMatcher =
            RegEx.token.matcher("prs.genericfile/prs.rawbyte");
        assertTrue(
                "oh nooo 'prs.genericfile/prs.rawbyte' should definitely be considered a token",
                testMatcher.matches());
    }

    @Test
    public void testUri()
    {
    	try {
			assertTrue("hmm, not an msrp URI?",
					RegEx.isMsrpUri(new URI("msrp://alicepc.example.com:7777/iau39soe2843z;tcp")));
			assertTrue("hmm, not an msrp URI?",
					RegEx.isMsrpUri(new URI("msrp://alicepc.example.com:7777;tcp")));
			assertFalse("hmm, *is* an msrp URI?",
					RegEx.isMsrpUri(new URI("msrp:/iau39soe2843z;tcp")));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
    }
}
