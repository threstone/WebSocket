package Server;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server_ws {
	private ServerSocket serverSocket;
	// 192.168.218.1

	/**
	 * 接收客户端连接请求
	 */
	public Server_ws(int port) {

		GetMessageThread thread = new GetMessageThread();
		thread.start();

		try {
			serverSocket = new ServerSocket(port);
			while (true) {
				MySocket socket = new MySocket();
				socket.socket = serverSocket.accept();
				thread.session.add(socket);
				System.out.println("有新连接：" + socket.socket);

			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

}

class GetMessageThread extends Thread {

	public List<MySocket> session = new CopyOnWriteArrayList<MySocket>();

	@Override
	public void run() {

		List<Integer> removeIndex = new ArrayList<Integer>();

		try {
			
			while (true) {

				for (int i = 0; i < session.size(); i++) {
					MySocket sess = session.get(i);
					if (sess.conType != sess.DISCONNECT) {
						sess.doSocketData();
					} else {
						removeIndex.add(i);
					}
				}
				
				//刪除不需要的
				for (int i = removeIndex.size() - 1; i >= 0; i--) {
					session.remove((int)removeIndex.get(i));
					removeIndex.remove(i);
					System.out.println("清楚一个");
				}
				sleep(1);

			}
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
