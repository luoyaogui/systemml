/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2013
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.runtime.functionobjects;

import com.ibm.bi.dml.runtime.DMLRuntimeException;

// Singleton class

public class Plus extends ValueFunction 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2013\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";

	private static Plus singleObj = null;
	
	private Plus() {
		// nothing to do here
	}
	
	public static Plus getPlusFnObject() {
		if ( singleObj == null )
			singleObj = new Plus();
		return singleObj;
	}
	
	public Object clone() throws CloneNotSupportedException {
		// cloning is not supported for singleton classes
		throw new CloneNotSupportedException();
	}
	
	@Override
	public double execute(double in1, double in2) {
		return in1 + in2;
	}

	@Override
	public double execute(double in1, int in2) {
		return in1 + in2;
	}

	@Override
	public double execute(int in1, double in2) {
		return in1 + in2;
	}

	@Override
	public double execute(int in1, int in2) {
		return in1 + in2;
	}

	public String execute ( String in1, String in2 ) throws DMLRuntimeException {
		return in1 + in2;
	}
	
}