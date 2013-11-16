package realmrelay;

import java.net.Socket;

import realmrelay.crypto.RC4;
import realmrelay.script.ScriptManager;


public class User {
	
	private static final String rc4key0 = "311F80691451C71B09A13A2A6E";
	private static final String rc4key1 = "72C5583CAFB6818995CBD74B80";
	private static final int bufferLength = 65536;
	
	public final byte[] localBuffer = new byte[bufferLength];
	public int localBufferIndex = 0;
	public final RC4 localRecvRC4 = new RC4(rc4key0);
	public final RC4 localSendRC4 = new RC4(rc4key1);
	public byte[] remoteBuffer = new byte[bufferLength];
	public int remoteBufferIndex = 0;
	public final RC4 remoteRecvRC4 = new RC4(rc4key1);
	public final RC4 remoteSendRC4 = new RC4(rc4key0);
	public final Socket localSocket;
	public Socket remoteSocket = null;
	public final ScriptManager scriptManager = new ScriptManager(this);
	public long localNoDataTime = System.currentTimeMillis();
	public long remoteNoDataTime = System.currentTimeMillis();
	
	public User(Socket localSocket) {
		if (localSocket == null) {
			throw new NullPointerException();
		}
		this.localSocket = localSocket;
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

}
