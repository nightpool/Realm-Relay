package realmrelay;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
								user.doTick();
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
