/**
 * 
 */
package javax.net.msrp;

/** Encapsulated knowledge on MSRP response codes.
 *
 * @author tuijldert
 */
public class ResponseCode {
	/*
	 * Would much rather have a non-contiguous Enum but apparently not possible
	 * in Java 5. Ah well...
	 */
	public static final int RC200 = 200;
	public static final int RC400 = 400;
	public static final int RC403 = 403;
	public static final int RC404 = 404;
	public static final int RC408 = 408;
	public static final int RC413 = 413;
	public static final int RC415 = 415;
	public static final int RC423 = 423;
	public static final int RC424 = 424;
	public static final int RC425 = 425;
	public static final int RC428 = 428;
	public static final int RC481 = 481;
	public static final int RC501 = 501;
	public static final int RC506 = 506;

	static class Code {
		int code;
		String description;

		Code(int code, String description) {
			this.code = code;
			this.description = description;
		}
	}

	static final Code rcSet[] = {
		new Code(RC200, "200 Ok, successful transaction"),
		new Code(RC400, "400 Request unintelligible"),
		new Code(RC403, "403 Not allowed"),
		new Code(RC404, "404 Failure to resolve recipient's URI"),
		new Code(RC408, "408 Downstream transaction timeout"),
		new Code(RC413, "413 Stop sending immediately"),
		new Code(RC415, "415 Media type not supported"),
		new Code(RC423, "423 Parameter out of bounds"),
		new Code(RC424, "424 Malformed nickname"),
		new Code(RC425, "425 Nickname reserved or already in use"),
		new Code(RC428, "428 Private messages not supported"),
		new Code(RC481, "481 Session not found"),
		new Code(RC501, "501 Unknown request"),
		new Code(RC506, "506 Wrong session"),
	};

	static final int abortCode[] = {
		RC400, RC403, RC413, RC415, RC481
	};

	/** Is given response code a valid MSRP code?
	 * @param code the response code to check
	 * @return true is valid
	 */
	public static boolean isValid(int code) {
		for (Code rc : rcSet) {
			if (code == rc.code )
				return true;
		}
		return false;
	}

	public static boolean isValid(String code) {
		try {
			return isValid(Integer.parseInt(code));
		} catch (NumberFormatException nfe) {
			return false;
		}
	}

	/** Does given response code denote an error?
	 * @param code the response code to check
	 * @return true is (known) error
	 */
	public static boolean isError(int code) {
		return isValid(code) && code > 299;
	}

	public static String toString(int code) {
		for (Code rc : rcSet) {
			if (code == rc.code )
				return rc.description;
		}
		return "Unknown (non-MSRP) response code";
	}

	/** Is response code an indication to abort sending?
	 * @param code the response code
	 * @return true if the code indicates sender should abort sending.
	 */
	public static boolean isAbortCode(int code) {
		for (int ac : abortCode) {
			if (ac == code)
				return true;
		}
		return false;
	}
}
