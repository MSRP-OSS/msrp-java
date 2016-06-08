/**
 * 
 */
package javax.net.msrp.wrap;

import java.util.Hashtable;

import javax.net.msrp.WrappedMessage;
import javax.net.msrp.wrap.cpim.Message;


/** This singleton registers wrapper implementations.
 * @author tuijldert
 *
 */
public class Wrap {

	private Hashtable<String, String> wrappers = new Hashtable<String, String>();

	/**
	 * 
	 */
	protected Wrap() {
		registerWrapper(Message.WRAP_TYPE, Message.class.getName());
	}

	private static class SingletonHolder {
		private final static Wrap INSTANCE = new Wrap();
	}

	public static Wrap getInstance() {
		return SingletonHolder.INSTANCE;
	}

	/** Register a wrapper implementation.
	 * @param contenttype		for which conetent-type
	 * @param wrapImplementator	the implementing wrapper-class
	 */
	public void registerWrapper(String contenttype, String wrapImplementator) {
		wrappers.put(contenttype, wrapImplementator);
	}

	/** Is the content-type a wrapper type?
	 * @param contenttype	the type
	 * @return				true = wrapper type.
	 */
	public boolean isWrapperType(String contenttype) {
		return wrappers.containsKey(contenttype);
	}

	/** Get wrapper implementation object for this type.
	 * @param contenttype	the type
	 * @return				object implementing wrap/unwrap operations for this type.
	 */
	public WrappedMessage getWrapper(String contenttype) {
		try {
			Class<?> cls = Class.forName(wrappers.get(contenttype));
			return (WrappedMessage) cls.newInstance();
		} catch (Exception e) {
			return null;
		}
	}
}
