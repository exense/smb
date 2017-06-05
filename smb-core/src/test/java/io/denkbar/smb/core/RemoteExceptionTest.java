package io.denkbar.smb.core;

import org.junit.Test;

public class RemoteExceptionTest {

	@Test
	public void test() {
		try {
			try {
				try {
					throw new Exception("Root");
				} catch (Exception e) {
					throw new Exception("Error 1",e);
				}
			} catch (Exception e) {
				throw new Exception("Error 2",e);
			}
		} catch (Exception e) {
			RemoteException r = new RemoteException(e);
			r.printStackTrace();
		}
	}
}
