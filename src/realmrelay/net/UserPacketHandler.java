package realmrelay.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import realmrelay.ROTMGRelay;
import realmrelay.User;
import realmrelay.crypto.RC4;
import realmrelay.packets.Packet;
import realmrelay.script.PacketScriptEvent;

public class UserPacketHandler {
	public static enum HandlerType{
		REMOTE,
		LOCAL
	}
	
	public HandlerType type;
	public byte[] buffer = new byte[User.bufferLength];
	public int bufferIndex = 0;
	public RC4 recvRC4;
	public RC4 sendRC4;
	public long noDataTime = System.currentTimeMillis();
	public User user;
	
	public UserPacketHandler(HandlerType type, String recvKey, String sendKey, User user) {
		this.type = type;
		this.recvRC4 = new RC4(recvKey);
		this.sendRC4 = new RC4(sendKey);
		this.user = user;
	}


	public void handle(InputStream in) throws Exception{
		if (in.available() == 0){
			if (System.currentTimeMillis() - noDataTime >= 10000) {
				throw new SocketException("no data time-out");
			}
			return;
		}
		int bytesRead = in.read(buffer, bufferIndex, buffer.length - bufferIndex);
		if (bytesRead == -1) {
			throw new SocketException("eof");
		} else if (bytesRead > 0) {
			bufferIndex += bytesRead;
			while (bufferIndex >= 5) {
				int packetLength = ((ByteBuffer) ByteBuffer.allocate(4).put(buffer[0]).put(buffer[1]).put(buffer[2]).put(buffer[3]).rewind()).getInt();
				ROTMGRelay.echo(type+" Packet: " + bufferIndex + " / " + packetLength);
				// check to see if packet length is bigger than buffer size
				if (buffer.length < packetLength)
				{      // resize buffer to match packet length
					buffer = Arrays.copyOf(buffer, packetLength);
				}
				if (bufferIndex < packetLength) {
					break;
				}
				byte packetId = buffer[4];
				byte[] packetBytes = new byte[packetLength - 5];
				System.arraycopy(buffer, 5, packetBytes, 0, packetLength - 5);
				if (bufferIndex > packetLength)
					System.arraycopy(buffer, packetLength, buffer, 0, bufferIndex - packetLength);
				bufferIndex -= packetLength;
				recvRC4.cipher(packetBytes);
				Packet packet = Packet.create(packetId, packetBytes);
				if (packet.getBytes().length != packetBytes.length) {
					ROTMGRelay.echo(type+" " + packet + " after" + packet.getBytes().length + " before" + packetBytes.length);
					user.kick();
				}
				sendPacket(packet);
			}
		}
		noDataTime = System.currentTimeMillis();
	}


	private void sendPacket(Packet packet) throws IOException {
		PacketScriptEvent event = (type == HandlerType.LOCAL) ? 
				user.scriptManager.clientPacketEvent(packet) : user.scriptManager.serverPacketEvent(packet);
        if (!event.isCancelled() && event.isConnected()) {
        	if(type == HandlerType.LOCAL){
        		user.sendToServer(packet); }else{ user.sendToClient(packet); }
        }	
	}
}
