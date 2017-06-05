package io.denkbar.smb.core;

public class RemoteException extends Exception {

	private static final long serialVersionUID = -6727333279970377942L;
		
	protected String classname;
	
	public RemoteException(Throwable e) {
		super(e.getClass().getName() + ":" + e.getMessage(),e.getCause()!=null?new RemoteException(e.getCause()):null);
		this.setStackTrace(e.getStackTrace());
		this.classname = e.getClass().getName();
	}
	
	public String toString() {
        return "Remote exception " + getMessage(); 
    }

}
