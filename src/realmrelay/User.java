package realmrelay;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import realmrelay.net.UserPacketHandler;
import realmrelay.packets.Packet;
import realmrelay.script.ScriptManager;


public class User {
	
	public static final String rc4key0 = "311F80691451C71B09A13A2A6E";
	public static final String rc4key1 = "72C5583CAFB6818995CBD74B80";
	public static final int bufferLength = 65536;
	
	public final Socket localSocket;
	public Socket remoteSocket = null;
		
	public UserPacketHandler remote;
	public UserPacketHandler local;
	public final ScriptManager scriptManager = new ScriptManager(this);
	
	public User(Socket localSocket) {
		if (localSocket == null) {
			throw new NullPointerException();
		}
		this.localSocket = localSocket;
		this.local = new UserPacketHandler(UserPacketHandler.HandlerType.LOCAL, rc4key0, rc4key1, this);
		this.remote = new UserPacketHandler(UserPacketHandler.HandlerType.REMOTE, rc4key1, rc4key0, this);
	}
	
	public void disconnect() {
		if (this.remoteSocket != null) {
			try {
				this.remoteSocket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			this.remoteSocket = null;
			this.scriptManager.trigger("onDisconnect");
		}
	}
	
	public void kick() {
		this.disconnect();
		try {
			this.localSocket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void doTick() throws Exception {
		scriptManager.fireExpiredEvents();
		if (remoteSocket != null) {
			try{
				remote.handle(remoteSocket.getInputStream());
			} catch (Exception e) {
				if (!(e instanceof SocketException)) {
					e.printStackTrace();
				}
				disconnect();
			}
		}
		local.handle(localSocket.getInputStream());
		
	}
	
	public void sendToClient(Packet packet) throws IOException {
		byte[] packetBytes = packet.getBytes();
		this.local.sendRC4.cipher(packetBytes);
		byte packetId = packet.id();
		int packetLength = packetBytes.length + 5;
		DataOutputStream out = new DataOutputStream(this.localSocket.getOutputStream());
		out.writeInt(packetLength);
		out.writeByte(packetId);
		out.write(packetBytes);
	}
	
	public void sendToServer(Packet packet) throws IOException {
		byte[] packetBytes = packet.getBytes();
		this.remote.sendRC4.cipher(packetBytes);
		byte packetId = packet.id();
		int packetLength = packetBytes.length + 5;
		DataOutputStream out = new DataOutputStream(this.remoteSocket.getOutputStream());
		out.writeInt(packetLength);
		out.writeByte(packetId);
		out.write(packetBytes);
	}

}
