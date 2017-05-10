/*******************************************************************************
 * (C) Copyright 2016 Jérôme Comte and Dorian Cransac
 *  
 *  This file is part of djigger
 *  
 *  djigger is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  djigger is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with djigger.  If not, see <http://www.gnu.org/licenses/>.
 *
 *******************************************************************************/
package io.denkbar.smb.core;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;



public class Client implements MessageRouterStateListener {

	private String agentHost;

	private int agentPort;

	private MessageRouter router;

	private boolean isAlive;

	public Client() {
		
	}

	public Client(String agentHost, int agentPort) throws UnknownHostException, IOException {
		super();
		this.agentHost = agentHost;
		this.agentPort = agentPort;
		connect(agentHost, agentPort);
		start();
	}

	public void connect(String agentHost, int agentPort) throws IOException, UnknownHostException {
		setMessageRouter(new Socket(agentHost, agentPort));
	}
	
	public void start() {
		router.start();
	}
	
	public ConnectionFuture prepareForIncommingConnection() throws IOException {
		return new ConnectionFuture();
	}
	
	private void setMessageRouter(Socket socket) throws IOException {
		router = new MessageRouter(this, socket);
		this.isAlive = true;
	}
	
	public class ConnectionFuture {
		
		ServerSocket serverSocket; 
		
		public ConnectionFuture() throws IOException {
			super();
			serverSocket = new ServerSocket(0);
			
			final ConnectionFuture me = this;
			new Thread(new Runnable() {
				public void run() {
					Socket socket;
					try {
						socket = serverSocket.accept();
						synchronized (me) {
							setMessageRouter(socket);
							me.notifyAll();
						}
					} catch (IOException e) {
						
					} finally {
						try {
							serverSocket.close();
						} catch (IOException e) {}
					}
				}
			}).start();
			
		}

		public int getLocalPort() {
			return serverSocket.getLocalPort();
		}

		public void waitForConnection(long timeout) throws TimeoutException, InterruptedException {
			synchronized (this) {
				if (!isAlive) {
					wait(timeout);
				}
			}
			if(!isAlive) {
				throw new TimeoutException("Timeout occured while waiting for connection.");
			}
		}
	}
	
	

	public void sendMessage(String command) throws IOException {
		sendMessage(command, null);
	}

	public void sendMessage(String command, Object content) throws IOException {
		router.send(new Message(command, content));
	}

	public Object call(String command, Object content) throws Exception {
		return call(command, content, 60000);
	}
	
	public Object call(String command, Object content, long timeout) throws Exception {
		return router.call(new Message(command, content),timeout);
	}

	public String getAgentHost() {
		return agentHost;
	}

	public int getAgentPort() {
		return agentPort;
	}

	public MessageRouter getMessageRouter() {
		return router;
	}

	public void messageRouterDisconnected(MessageRouter router) {
		isAlive = false;
	}

	public boolean isAlive() {
		return isAlive;
	}
	
	public void close() {
		router.disconnect();
		isAlive = false;
	}

	public void registerPermanentListener(String type, MessageListener listener) {
		router.registerPermanentListener(type, listener);
	}

	public void registerPermanentListenerForAllMessages(MessageListener listener) {
		router.registerPermanentListenerForAllMessages(listener);
	}

	public void registerSynchronListener(String type, SynchronMessageListener listener) {
		router.registerSynchronListener(type, listener);
	}

	public void unregisterPermanentListener(String type, MessageListener listener) {
		router.unregisterPermanentListener(type, listener);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getProxy(final Class<T> interfaceClass, final long calltimeout) {
		return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[]{interfaceClass}, getInvocationHandler(interfaceClass, calltimeout));
	}

	public <T> InvocationHandler getInvocationHandler(final Class<T> interfaceClass, final long calltimeout) {
		return new InvocationHandler() {
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				Object[] params = new Object[]{method.getName(), args};
				return call(interfaceClass.getName(), params, calltimeout);
			}	
		};
	}
	
	public <T> void registerSynchronListener(final Class<T> interfaceClass, final T listener) {
		registerSynchronListener(interfaceClass.getName(), new SynchronMessageListener() {
			public Serializable onSynchronMessage(Message msg) throws Exception {
				Object[] content = (Object[]) msg.getContent();
				Object[] args = (Object[]) content[1];
				int argsCount = args!=null?args.length:0;
				if(argsCount==0) {
					args = new Object[0];
				}
				Class<?>[] types = new Class<?>[argsCount];
				for(int i=0;i<argsCount;i++) {
					types[i]=args[i].getClass();
				}
				
				String methodName = (String)content[0];
				
				Method method = getMethodMatchingSignature(interfaceClass, methodName, types);
				if(method!=null) {
					Object result = method.invoke(listener, args);
					return (Serializable) result;					
				} else {
					throw new RuntimeException("No method matching found");
				}
			}

			private <T> Method getMethodMatchingSignature(final Class<T> interfaceClass, String methodName,
					Class<?>[] types) {
				for(Method m:interfaceClass.getMethods()) {
					if(m.getName().equals(methodName)&&m.getParameters().length==types.length) {
						boolean match = true;
						for(int i=0;i<m.getParameterTypes().length;i++) {
							Class<?> methodType = m.getParameterTypes()[i];
							if(!methodType.isAssignableFrom(types[i])) {
								match = false;
							}
						}
						if(match) {
							return m;
						}
					}
				}
				return null;
			}
		});
	}
}
