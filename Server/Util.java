package Server;

import java.nio.ByteBuffer;

public class Util {

	public static int byteArrayToInt(byte[] bytes) {
		int value = 0;
		for (int i = 0; i < 4; i++) {
			int shift = (3 - i) * 8;
			value += (bytes[i] & 0xFF) << shift;
		}
		return value;
	}

	public static short byteToShort(byte[] b) {
		short s = 0;
		short s0 = (short) (b[0] & 0xff);// 最低位
		short s1 = (short) (b[1] & 0xff);
		s0 <<= 8;
		s = (short) (s0 | s1);
		return s;
	}

	public static byte[] shortToByte(short x) {
		byte high = (byte) (0x00FF & (x >> 8));// 定义第一个byte
		byte low = (byte) (0x00FF & x);// 定义第二个byte
		byte[] bytes = new byte[2];
		bytes[0] = high;
		bytes[1] = low;
		return bytes;
	}

	static ByteBuffer buffer = ByteBuffer.allocate(8);

	public static long bytesToLong(byte[] bytes) {
		long number = 0;
		for (int i = 0; i < bytes.length; i++) {
			long temp = bytes[i]<<(7-i)*8;
			number+=temp;
		}
		return number;
//		buffer.put(bytes, 0, bytes.length);
//		buffer.flip();// need flip
//		return buffer.getLong();
	}

	public static byte[] longToBytes(long x) {
		buffer.putLong(0, x);
		return buffer.array();
	}

	public static byte[] cutBytes(byte[] bytes, int index) {
		byte[] temp = new byte[bytes.length - index];
		for (int i = 0; i < temp.length; i++) {
			temp[i] = bytes[i + index];
		}

		return temp;
	}

//	/**
//	 * 合并数组
//	 * @return
//	 */
//	public static byte[] composeBytes(byte[] before , byte[] after) {
//		byte[] result = new byte[before.length+after.length];
//	}

}
