package realmrelay;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Vector;


import realmrelay.net.ListenSocket;
import realmrelay.packets.Packet;
import realmrelay.script.PacketScriptEvent;


public final class ROTMGRelay {
	
	public static final ROTMGRelay instance = new ROTMGRelay();
	
	// #settings
	public String listenHost = "localhost";
	public int listenPort = 2050;
	
	public boolean bUseProxy = false;
	public String proxyHost = "socks4or5.someproxy.net";
	public int proxyPort = 1080;
	
	public String remoteHost = "50.19.47.160";
	public int remotePort = 2050;
	// #settings end
	
	private final ListenSocket listenSocket;
	private final List<User> users = new ArrayList<User>();
	private final List<User> newUsers = new Vector<User>();
	private final Map<Integer, InetSocketAddress> gameIdSocketAddressMap = new HashMap<Integer, InetSocketAddress>();
	private final Map<String, Object> globalVarMap = new HashMap<String, Object>();
	
	private ROTMGRelay() {
		Properties p = new Properties();
		p.setProperty("listenHost", this.listenHost);
		p.setProperty("listenPort", String.valueOf(this.listenPort));
		p.setProperty("bUseProxy", String.valueOf(this.bUseProxy));
		p.setProperty("proxyHost", this.proxyHost);
		p.setProperty("proxyPort", String.valueOf(this.proxyPort));
		p.setProperty("remoteHost", this.remoteHost);
		p.setProperty("remotePort", String.valueOf(this.remotePort));
		File file = new File("settings.properties");
		if (!file.isFile()) {
			try {
				OutputStream out = new FileOutputStream(file);
				p.store(out, null);
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		p = new Properties(p);
		try {
			InputStream in = new FileInputStream(file);
			p.load(in);
			in.close();
			this.listenHost = p.getProperty("listenHost");
			this.listenPort = Integer.parseInt(p.getProperty("listenPort"));
			this.bUseProxy = Boolean.parseBoolean(p.getProperty("bUseProxy"));
			this.proxyHost = p.getProperty("proxyHost");
			this.proxyPort = Integer.parseInt(p.getProperty("proxyPort"));
			this.remoteHost = p.getProperty("remoteHost");
			this.remotePort = Integer.parseInt(p.getProperty("remotePort"));
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.listenSocket = new ListenSocket(this.listenHost, this.listenPort) {

			@Override
			public void socketAccepted(Socket localSocket) {
				User user = new User(localSocket);
				ROTMGRelay.instance.newUsers.add(user);
			}
			
		};
	}
	
	/**
	 * error message
	 * @param message
	 */
	public static void error(String message) {
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		String timestamp = sdf.format(new Date());
		String raw = timestamp + " " + message;
		System.err.println(raw);
	}
	
	/**
	 * echo message
	 * @param message
	 */
	public static void echo(String message) {
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		String timestamp = sdf.format(new Date());
		String raw = timestamp + " " + message;
		System.out.println(raw);
	}
	
	public Object getGlobal(String var) {
		return this.globalVarMap.get(var);
	}
	
	public InetSocketAddress getSocketAddress(int gameId) {
		InetSocketAddress socketAddress = this.gameIdSocketAddressMap.get(gameId);
		if (socketAddress == null) {
			return new InetSocketAddress(this.remoteHost, this.remotePort);
		}
		return socketAddress;
	}
	
	public void setGlobal(String var, Object value) {
		this.globalVarMap.put(var, value);
	}
	
	public void setSocketAddress(int gameId, String host, int port) {
		InetSocketAddress socketAddress = new InetSocketAddress(host, port);
		this.gameIdSocketAddressMap.put(gameId, socketAddress);
	}
	
	public static void main(String[] args) {
		try {
			GETXmlParse.parseXMLData();
		} catch (Exception e) {
			e.printStackTrace();
		}
		Packet.init();
		if (ROTMGRelay.instance.listenSocket.start()) {
			new Thread(new Runnable() {

				@Override
				public void run() {
					while (!ROTMGRelay.instance.listenSocket.isClosed()) {
						while (!ROTMGRelay.instance.newUsers.isEmpty()) {
							User user = ROTMGRelay.instance.newUsers.remove(0);
							ROTMGRelay.instance.users.add(user);
							ROTMGRelay.echo("Connected " + user.localSocket);
							user.scriptManager.trigger("onEnable");
						}
						Iterator<User> i = ROTMGRelay.instance.users.iterator();
						while (i.hasNext()) {
							User user = i.next();
							try {
								user.scriptManager.fireExpiredEvents();
								if (user.remoteSocket != null) {
									try {
										InputStream in = user.remoteSocket.getInputStream();
										if (in.available() > 0) {
											int bytesRead = user.remoteSocket.getInputStream().read(user.remoteBuffer, user.remoteBufferIndex, user.remoteBuffer.length - user.remoteBufferIndex);
											if (bytesRead == -1) {
												throw new SocketException("eof");
											} else if (bytesRead > 0) {
												user.remoteBufferIndex += bytesRead;
												while (user.remoteBufferIndex >= 5) {
													int packetLength = ((ByteBuffer) ByteBuffer.allocate(4).put(user.remoteBuffer[0]).put(user.remoteBuffer[1]).put(user.remoteBuffer[2]).put(user.remoteBuffer[3]).rewind()).getInt();
													ROTMGRelay.echo("Server Packet: " + user.remoteBufferIndex + " / " + packetLength);
													// check to see if packet length is bigger than buffer size
													if (user.remoteBuffer.length < packetLength)
													{      // resize buffer to match packet length
														user.remoteBuffer = Arrays.copyOf(user.remoteBuffer, packetLength);
													}
													if (user.remoteBufferIndex < packetLength) {
														break;
													}
													byte packetId = user.remoteBuffer[4];
													byte[] packetBytes = new byte[packetLength - 5];
													System.arraycopy(user.remoteBuffer, 5, packetBytes, 0, packetLength - 5);
													if (user.remoteBufferIndex > packetLength)
														System.arraycopy(user.remoteBuffer, packetLength, user.remoteBuffer, 0, user.remoteBufferIndex - packetLength);
													user.remoteBufferIndex -= packetLength;
													user.remoteRecvRC4.cipher(packetBytes);
													Packet packet = Packet.create(packetId, packetBytes);
													if (packet.getBytes().length != packetBytes.length) {
														ROTMGRelay.echo("SVR " + packet + " after" + packet.getBytes().length + " before" + packetBytes.length);
														user.kick();
													}
													PacketScriptEvent event = user.scriptManager.serverPacketEvent(packet);
													if (!event.isCancelled()) {
														event.sendToClient(packet);
													}
												}
											}
											user.remoteNoDataTime = System.currentTimeMillis();
										} else if (System.currentTimeMillis() - user.remoteNoDataTime >= 10000) {
											throw new SocketException("no data time-out");
										}
									} catch (Exception e) {
										if (!(e instanceof SocketException)) {
											e.printStackTrace();
										}
										user.disconnect();
									}
								}
								InputStream in = user.localSocket.getInputStream();
								if (in.available() > 0) {
									int bytesRead = in.read(user.localBuffer, user.localBufferIndex, user.localBuffer.length - user.localBufferIndex);
									if (bytesRead == -1) {
										throw new SocketException("eof");
									} else if (bytesRead > 0) {
										user.localBufferIndex += bytesRead;
										while (user.localBufferIndex >= 5) {
											int packetLength = ((ByteBuffer) ByteBuffer.allocate(4).put(user.localBuffer[0]).put(user.localBuffer[1]).put(user.localBuffer[2]).put(user.localBuffer[3]).rewind()).getInt();
											if (user.localBufferIndex < packetLength) {
												break;
											}
											byte packetId = user.localBuffer[4];
											byte[] packetBytes = new byte[packetLength - 5];
											System.arraycopy(user.localBuffer, 5, packetBytes, 0, packetLength - 5);
											if (user.localBufferIndex > packetLength)
												System.arraycopy(user.localBuffer, packetLength, user.localBuffer, 0, user.localBufferIndex - packetLength);
											user.localBufferIndex -= packetLength;
											user.localRecvRC4.cipher(packetBytes);
											Packet packet = Packet.create(packetId, packetBytes);
											if (packet.getBytes().length != packetBytes.length) {
												ROTMGRelay.echo("CLI " + packet + " after" + packet.getBytes().length + " before" + packetBytes.length);
												user.kick();
											}
											PacketScriptEvent event = user.scriptManager.clientPacketEvent(packet);
											if (!event.isCancelled() && event.isConnected()) {
												event.sendToServer(packet);
											}
										}
									}
									user.localNoDataTime = System.currentTimeMillis();
								} else if (System.currentTimeMillis() - user.localNoDataTime >= 10000) {
									throw new SocketException("no data time-out");
								}
							} catch (Exception e) {
								if (!(e instanceof SocketException)) {
									e.printStackTrace();
								}
								user.kick();
								i.remove();
								ROTMGRelay.echo("Disconnected " + user.localSocket);
							}
						}
						try {
							Thread.sleep(20);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					Iterator<User> i = ROTMGRelay.instance.users.iterator();
					while (i.hasNext()) {
						User user = i.next();
						user.kick();
					}
				}
				
			}).start();
			ROTMGRelay.echo("Realm Relay listener started");
		} else {
			ROTMGRelay.echo("Realm Relay listener problem");
		}
	}

}
