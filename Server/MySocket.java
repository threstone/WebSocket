package Server;


import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Scanner;

public class MySocket {

	private final byte HANDSHAKING = 0;
	private final byte CONNECTING = 1;
	public final byte DISCONNECT = 2;
	public Socket socket = null;
	public byte conType = 0; // 连接状态 : 0为未完成握手 1为握手成功后的长链接 2为断开连接
	byte[] notOverData = null;// 在有后续数据时起作用

	IndexStream stream = new IndexStream();

	public void doSocketData() {

		try {
			
			int length = socket.getInputStream().available();

			if (length == 0) {
				return;
			}

			byte[] value = new byte[length];
			socket.getInputStream().read(value);
			stream.write(value);

			if (conType == CONNECTING) { // 握手成功以后接收到的信息的处理方式

				while (stream.getValuableLength() > 2) {

					byte[] data = splitWebSocketFrame();
					if (data == null) {
						break;
					}

					// 解包数据并做相应的事情
					serializeDataToDo(data);
					System.out.println("占:    "+stream.writeIndex);
				}

			} else if (conType == HANDSHAKING) {// 处理握手请求

				byte[] hands = checkHandRequest();

				if (hands != null) {

					String key = handshake(hands);
					if (key != null) {

						StringBuilder stringBuilder = new StringBuilder();
						stringBuilder.append("HTTP/1.1 101 Switching Protocols" + "\r\n");
						stringBuilder.append("Upgrade: websocket" + "\r\n");
						stringBuilder.append("Connection: Upgrade" + "\r\n");
						stringBuilder.append("Sec-WebSocket-Accept: " + getWebSocketAccept(key) + "\r\n" + "\r\n"); // 处理websocket-key

						sendMsg(stringBuilder.toString().getBytes());
						conType = CONNECTING; // 开始长连接

					} else {

						conType = DISCONNECT;
						stream = null;
						socket.close();
						socket = null;

					}
				}

			}

		} catch (IOException e1) {

			System.err.println("连接断开:" + socket);
			conType = DISCONNECT;
			socket = null;
			stream = null;

		}
	}

	/**
	 * 防止握手请求粘包
	 * 
	 * @return
	 */
	private byte[] checkHandRequest() {
		byte[] bytes = stream.toByteArray();
		String str = new String(bytes);
		if (str.indexOf("\r\n\r\n") != -1) {
			stream.reset();
			return bytes;
		}
		return null;
	}

	/**
	 * 防止帧粘包并截取包
	 */
	private byte[] splitWebSocketFrame() {

		byte[] bytes = stream.toByteArray();
		long datalength = bytes[1] & 0x7f;
		if (datalength <= 125) {

			datalength += 2;
			if ((bytes[1] | 0x80) == bytes[1]) {
				datalength += 4;
			}

		} else if (datalength == 126) {

			datalength = 4 + Util.byteToShort(new byte[] { bytes[2], bytes[3] });
			if ((bytes[1] | 0x80) == bytes[1]) {
				datalength += 4;
			}

		} else if (datalength == 127) {

			datalength = 10 + Util.bytesToLong(
					new byte[] { bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7], bytes[8], bytes[9] });
			if ((bytes[1] | 0x80) == bytes[1]) {
				datalength += 4;
			}

		}

		if (datalength > bytes.length) {
			return null;// 不够长return继续接收数据直到够
		}

		// 截取掉处理过的数据
		stream.readIndex += datalength;
		
		return bytes;
	}

	/**
	 * 解包数据并做相应的事情
	 * 
	 * @param value
	 */
	private void serializeDataToDo(byte[] value) {
		// 查看fin确定是否为单个消息中的最后一条
		boolean isLastData = true;
		byte dataType;
		byte maskIndex = 0;
		int dataStart = 0;// 真实数据的起始下标
		long length = 0;

		if ((value[0] | 0x80) != value[0]) {// 是否是本帧数据中的最后一条
			isLastData = false;
		}

		dataType = (byte) (value[0] & 0x0f);// 获得数据类型 与00001111进行&运算

		if ((value[1] | 0x80) == value[1]) {// 是否有掩码 如果有，标记掩码起始位置
			maskIndex = 1;
		}

		// 获得数据的长度并标记真实数据的起始下标
		byte datalength = (byte) (value[1] & 0x7f);// 01111111
		if (datalength <= 125) {
			length = datalength;
			dataStart = 2;
		} else if (datalength == 126) {
			length = Util.byteToShort(new byte[] { value[2], value[3] });
			dataStart = 4;
		} else if (datalength == 127) {
			length = Util.bytesToLong(
					new byte[] { value[2], value[3], value[4], value[5], value[6], value[7], value[8], value[9], });
			dataStart = 10;
		}

		// 进行数据解码
		if (maskIndex == 1) {
			maskIndex = (byte) dataStart;
			dataStart += 4;// 因为有4个字节保存了掩码 所以真实数据的起始下标向后4位

			for (int i = 0; i < length; i++) {
				int index = dataStart + i;

				value[index] = (byte) (value[index] ^ value[maskIndex + (i % 4)]);
			}
		}

		// 处理是否有后续数据的逻辑
		if (isLastData == false) {// 有后续数据
			
			if (this.notOverData == null) {// 第一条数据
				this.notOverData = new byte[(int) length];
				for (int i = 0; i < length; i++, dataStart++) {
					this.notOverData[i] = value[dataStart];
				}
			} else {// 已有数据，那么将新数据加到已有数据之后
				byte[] temp = new byte[this.notOverData.length + (int) length];
				int i = 0;
				for (; i < this.notOverData.length; i++) {
					temp[i] = this.notOverData[i];
				}
				for (int a = dataStart, z = 0; z < length; a++, z++) {
					temp[i] = value[a];
				}
				this.notOverData = temp;
			}
			
			

//			if (dataType != 0) {
//				System.err.println("数据出错 : 非最后一条信息却不标识继续");
//			} else {
//				if (this.notOverData == null) {// 第一条数据
//					this.notOverData = new byte[(int) length];
//					for (int i = 0; i < length; i++, dataStart++) {
//						this.notOverData[i] = value[dataStart];
//					}
//				} else {// 已有数据，那么将新数据加到已有数据之后
//					byte[] temp = new byte[this.notOverData.length + (int) length];
//					int i = 0;
//					for (; i < this.notOverData.length; i++) {
//						temp[i] = this.notOverData[i];
//					}
//					for (int a = dataStart, z = 0; z < length; a++, z++) {
//						temp[i] = value[a];
//					}
//					this.notOverData = temp;
//				}
//			}

		} else {// 无后续数据 即 isLastData == true

			if (this.notOverData == null) {// 这条数据就是一帧

				doData(value, dataType, dataStart, length);

			} else {// 这条数据是整条数据的最后一条，将此数据加入到原有数据之后就可以进行最终处理了
				byte[] temp = new byte[this.notOverData.length + (int) length];
				int i = 0;
				for (; i < this.notOverData.length; i++) {
					temp[i] = this.notOverData[i];
				}
				for (int a = dataStart, z = 0; z < length; a++, z++) {
					temp[i] = value[a];
				}
				doData(temp, dataType, 0, temp.length);
				this.notOverData = null;
			}

		}

	}

	/**
	 * 通过数据与数据类型打包数据成一帧 未做分条处理
	 * 
	 * @param value  数据
	 * @param opcode 数据类型
	 */
	private void packDataToSend(byte[] value, byte opcode) {

		if (value == null) {// 说明这个数据是pong 或者关闭回应中的一个 只有两个字节
			sendMsg(new byte[] { (byte) (0x80 | opcode), (byte) 0 });
			return;
		}

		// 获得发送数据的长度
		long length = value.length;
		// 真实数据的起始位置
		byte dataIndex = 2;
		if (length <= 65535 && length >= 126) {
			length += 2;
			dataIndex = 4;
		} else if (length > 65535) {
			dataIndex = 10;
			length += 8;
		}
		length += 2;

		byte[] sendBytes = new byte[(int) length + value.length];

		// 标记第一个字节 是否是本帧最后的数据 数据的类型 未完成
		sendBytes[0] = (byte) (0x80 | opcode);

		// 写入长度
		if (dataIndex == 2) {
			sendBytes[1] |= value.length;
		} else if (dataIndex == 4) {
			sendBytes[1] |= (byte) 126;
			setLength(value, Util.longToBytes(value.length));
		} else if (dataIndex == 10) {
			sendBytes[1] |= (byte) 127;
			setLength(value, Util.longToBytes(value.length));
		}

		for (int i = 0; i < value.length; i++) {
			sendBytes[dataIndex++] = value[i];
		}

		sendMsg(sendBytes);// 打包好数据发送客户端
	}

	/**
	 * 将长度写入数据
	 * 
	 * @param value
	 * @param lengthArray
	 */
	private void setLength(byte[] value, byte[] lengthArray) {
		for (int i = 0; i < lengthArray.length; i++) {
			value[i + 2] = lengthArray[i];
		}

	}

	/**
	 * 根据获得的数据 按照数据类型 做相应的事情
	 * 
	 * @param value      数据
	 * @param typeIndex  数据类型
	 * @param startIndex 数据的起始下标
	 * @param length     数据的长度
	 */
	private void doData(byte[] value, byte typeIndex, int startIndex, long length) {
		switch (typeIndex) {
		case 1:// 文本信息
			String message = new String(value, startIndex, (int) length);
			System.out.println(message);
			if (message.equals("1")) {
				String vString = "收到了1";
				packDataToSend(vString.getBytes(), (byte) 1);
			}

			break;
		case 2:// 二进制信息
			for (int i = startIndex, z = 0; z < length; i++, z++) {
				System.out.print(value[i]);
			}
			System.out.println();
			break;
		case 8: // 接收到客户端的断开连接请求
			try {
				// 发送给客户端让他也关闭
				packDataToSend(null, (byte) 8);
				this.socket.close();
				stream = null;
				conType = DISCONNECT;
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		case 9:// 客户端的ping请求
			packDataToSend(null, (byte) 10);
			break;
		default:
			System.out.println("标识有误");
			break;
		}
	}

	/**
	 * 发送数据帧给客户端
	 * 
	 * @param value 数据
	 */
	private void sendMsg(byte[] value) {
		try {
			socket.getOutputStream().write(value);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 处理websocket-key 得到websocket-accept
	 * 
	 * @param key :websocket-key
	 * @return :websocket-accept
	 */
	public String getWebSocketAccept(String key) {
		key += "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
			md.update(key.getBytes("utf-8"), 0, key.length());
			byte[] sha1Hash = md.digest();
			return Base64.getEncoder().encodeToString(sha1Hash);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	/**
	 * 解析客户端发送的握手请求
	 * 
	 * @param value 客户端的握手数据
	 * @return 返回websocket-key 进行解码
	 */
	public String handshake(byte[] value) {

		String str = new String(value);

		if (!"GET / HTTP/1.1".equals(str.substring(0, 14))) {
			return null;
		}

		int indexUpgrade = str.indexOf("Upgrade:");
		String strUpgrade = str.substring(indexUpgrade + 9, indexUpgrade + 18);

		if (!strUpgrade.equals("websocket")) {
			return null;
		}

		int indexConnection = str.indexOf("Connection:");
		String strConnection = str.substring(indexConnection + 12, indexConnection + 19);

		if (!strConnection.equals("Upgrade")) {
			return null;
		}

		int indexVersion = str.indexOf("Sec-WebSocket-Version:");
		String strVersion = str.substring(indexVersion + 23, indexVersion + 25);

		if (!strVersion.equals("13")) {
			return null;
		}

		int indexKey = str.indexOf("Sec-WebSocket-Key:");
		int indexKeyLast = str.indexOf("==");
		String key = str.substring(indexKey + 19, indexKeyLast + 2);

		return key;

	}

}
