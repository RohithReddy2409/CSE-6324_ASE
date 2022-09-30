import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
public class Client2 {
	
	public static void main(String[] args) {
		try {
			String[]items=getfilelistfromserver();
			if(items!=null)
			{	
				receivefilefromserver(items);
				DeleteFilesSyncServer(items);
				
			}
		}catch(Exception e) {e.printStackTrace();}

	}

public static int getPortFromServer(Socket tcpSocket,String action,int udpPort,String filename) {
	int serverPort=0;
	try {
		Scanner inputSocket =  new Scanner(tcpSocket.getInputStream());
		PrintWriter outputSocket = new PrintWriter(tcpSocket.getOutputStream(), true);
		
		// send the HTTP packet	
		String request=action+"#"+filename+"#"+udpPort;
	    outputSocket.println(request+Constants.CRLF+"STOP");
		System.out.println(Constants.CRLF+">> Request:"+request);
		
	    outputSocket.close();
	}catch(Exception e) {e.printStackTrace();}
	return serverPort;
}
public static void receiveFile(PacketBoundedBufferMonitor bm, InetAddress receiverIp,int receiverPort,InetAddress senderIp,int senderPort) {
	
	PacketReceiver packetReceiver=new PacketReceiver(bm,receiverIp,receiverPort,senderIp,senderPort);
	//System.out.println(packetReceiver);
	packetReceiver.start();
	//System.out.println("Bye");

	FileWriter fileWriter=new FileWriter(bm,Constants.CLIENT2_FILE_ROOT);
	fileWriter.start();	
	try {
		packetReceiver.join();
		fileWriter.join();
	} 
		catch (InterruptedException e) {e.printStackTrace();}
	
}
public static void receivefilefromserver(String[] items) throws IOException
{
	try {
	for(int i=0;i<items.length;i++)
	{
	InetAddress serverIp=InetAddress.getByName("localhost");			
	Socket tcpSocket = new Socket(serverIp, Constants.SERVER_TCP_PORT);
	String action="RECEIVE REQUEST";
	int serverPort=getPortFromServer(tcpSocket,action,Constants.CLIENT2_UDP_PORT,items[i]);
	//System.out.println("hiii");
	PacketBoundedBufferMonitor bm=new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);
	InetAddress receiverIp=tcpSocket.getInetAddress();// get the IP of the sender		
	InetAddress senderIp=InetAddress.getByName("localhost");
	
	receiveFile(bm,receiverIp,Constants.CLIENT2_UDP_PORT,senderIp, Constants.SERVER_UDP_PORT);// receive the file	
	}

}catch(Exception e) {e.printStackTrace();}
	
}
public static String[] getfilelistfromserver()
{
	try
	{
//		System.out.println("Hi");
		InetAddress serverIp=InetAddress.getByName("localhost");			
		Socket tcpSocket = new Socket(serverIp, Constants.SERVER_TCP_PORT);
		String action="GETFILELIST REQUEST";
		PrintWriter outputSocket = new PrintWriter(tcpSocket.getOutputStream(), true);
		Scanner inputSocket =  new Scanner(tcpSocket.getInputStream());		
		// send the HTTP packet	
		String request=action+"#";
	    outputSocket.println(request+Constants.CRLF+"STOP");
	    String line=inputSocket.nextLine();
    	if (!line.isEmpty())
    	{
    		String [] items=line.split(":");	
			inputSocket.close();
			return items;	
    	}
			 inputSocket.close();
		     outputSocket.close();

	}catch(Exception ex) {ex.printStackTrace();}
    return null;
}
public static void DeleteFilesSyncServer(String[] items)
{
	
	File folder = new File(Constants.CLIENT2_FILE_ROOT);
	String[] files = folder.list();
	boolean found=false;
	for(int i=0;i<files.length;i++)
	{ 
		found=false;
		for(int j=0;j<items.length;j++)
		{
			if(files[i].equals(items[j]))
			{
				found=true;
				break;
			}
		}
		if(found==false)
		{
			System.out.println("Deleting extra files to sync with Server");
			File fi=new File(Constants.CLIENT2_FILE_ROOT+files[i]);
			if(fi.exists())
			{
				System.out.println("Deleting file:"+files[i]);
				fi.delete();
			}
		}
	}
}
	
}

