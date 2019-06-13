package Server;

public class IndexStream {

	byte[] stream;
	int readIndex = 0;
	int writeIndex = 0;

	public IndexStream() {
		stream = new byte[2048];
	}

	public void write(byte[] bytes) {

		int bytesLength = bytes.length;
		int streamLength = stream.length;

		if (writeIndex + bytesLength <= streamLength) {
			System.arraycopy(bytes, 0, stream, writeIndex, bytesLength);
			writeIndex += bytesLength;
		} else {

			int stayData = getValuableLength() + bytesLength;
			
			byte[] temp;				
			if (stayData <= 2048) {
				
				temp = new byte[2048];
				
			} else {
				
				temp = new byte[stayData];
				
			}

			System.arraycopy(stream, readIndex, temp, 0, getValuableLength());
			writeIndex = getValuableLength();
			readIndex = 0;
			System.arraycopy(bytes, 0, temp, writeIndex, bytesLength);
			writeIndex += bytesLength;
			stream = temp;

		}
	}

	public void read(byte[] bytes) {
		System.arraycopy(stream, readIndex, bytes, 0, getValuableLength());
		readIndex += getValuableLength();
	}

	public int getValuableLength() {
		return writeIndex - readIndex;
	}

	public byte[] toByteArray() {
		byte[] bytes = new byte[getValuableLength()];
		System.arraycopy(stream, readIndex, bytes, 0, getValuableLength());
		return bytes;
	}

	public void reset() {
		readIndex = 0;
		writeIndex = 0;	
	}
}
