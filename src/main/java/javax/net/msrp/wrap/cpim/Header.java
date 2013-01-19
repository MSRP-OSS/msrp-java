/*
 * Copyright © 2010 France Telecom S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.net.msrp.wrap.cpim;

/**
 * CPIM header
 * 
 * @author jexa7410
 */
public class Header {
	public static final String CPIM_TYPE = "message/cpim";

	/**
	 * Header name
	 */
	private String name;

	/**
	 * Header value
	 */
	private String value;

	/**
	 * Constructor
	 * 
	 * @param name Header name
	 * @param value Header value
	 */
	public Header(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public String getName() { return name; }

	public String getValue() { return value; }

	/**
	 * Parse CPIM header
	 * 
	 * @param data Input data
	 * @return Header
	 * @throws Exception
	 */
	public static Header parseHeader(String data) {
		int index = data.indexOf(":");
		String key = data.substring(0, index);
		String value = data.substring(index+1);
		return new Header(key.trim(), value.trim());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return this.name.hashCode();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		// TODO Auto-generated method stub
		return (obj != null && obj.getClass().equals(this.getClass()) &&
				((Header) obj).getName().equalsIgnoreCase(this.name));
	}	

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return name + ": " + value;
	}
}