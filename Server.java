import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;




public class Server {
	
	public Server() {	
	}

	public static void main(String[] args) {	
		
		try {
			
			ServerSocket serverSocket=null;			
			try {
				serverSocket = new ServerSocket(Constants.SERVER_TCP_PORT);				
			} catch (IOException ioEx) {
				System.out.println("\n>> Unable to set up port!");
				System.exit(1);
			}
			
			System.out.println("\r\n>> Ready to accept requests");
			// handle multiple client connections
			do {
				try {
					// Wait for clients...
					Socket client = serverSocket.accept();
					System.out.println("\n>> New request is accepted."+Constants.CRLF);
					
					Scanner inputSocket = new Scanner(client.getInputStream());							
					PrintWriter outputSocket = new PrintWriter(client.getOutputStream(), true);
					String filename="";
					// get action type from the received data
					String line=inputSocket.nextLine();	
					String actionType = "";	
					int clientUDPPort=0;
					String deletefilename="";
				    while(!line.equals("STOP")) {
				    	if (line.isEmpty()) {line=inputSocket.nextLine();continue;}
				    	if(line.startsWith("SEND REQUEST")){
				    		System.out.println(">> Request: "+line+Constants.CRLF);
				    		actionType="SEND REQUEST";				    		
				    		clientUDPPort=Integer.parseInt(line.split("#")[2].strip());
							break;
						}
				    	else if(line.startsWith("DELETE REQUEST"))
				    	{
				    		System.out.println("Delete Request is detected");
				    		actionType="DELETE REQUEST";
				    		deletefilename=line.split("#")[1].strip();
				    		break;
				    	}
				    	else if(line.startsWith("RECEIVE REQUEST"))
				    	{
				    		System.out.println("RECEIVE REQUEST is detected");
				    		actionType="RECEIVE REQUEST";
				    		filename=line.split("#")[1].strip();
				    		clientUDPPort=Integer.parseInt(line.split("#")[2].strip());
				    		break;
				    	}
				    	else if(line.startsWith("GETFILELIST REQUEST"))
				    	{
				    		System.out.println("Getting files list");
				    		actionType="GETFILELIST REQUEST";
				    		break;
				    	}
				    	else if(line.startsWith("MODIFYFILE REQUEST"))
				    	{
				    		System.out.println("MODIFY FILE REQUEST");
				    		System.out.println(">> Request: "+line+Constants.CRLF);
				    		actionType="MODIFYFILE REQUEST";
				    		clientUDPPort=Integer.parseInt(line.split("#")[2].strip());
							break;
				    		
				    	}
				    	line=inputSocket.nextLine();
				    }
				    
				    if (actionType.equals("SEND REQUEST")) {
				    	receiveHandle(client,outputSocket,clientUDPPort);
				    }
				    else if(actionType.equals("DELETE REQUEST"))
				    {
				    	deletehandle(deletefilename);
				    }
				    else if(actionType.equals("RECEIVE REQUEST"))
				    {
				    	sendhandle(client,outputSocket,clientUDPPort,filename);
				    }
				    else if(actionType.equals("GETFILELIST REQUEST"))
				    {
				    	getfilelistfromServer(client,outputSocket);
				    }
				    else if(actionType.equals("MODIFYFILE REQUEST"))
				    {
				    	receiveModifyHandle(client,outputSocket,clientUDPPort);
				    }
				    	
				    
				}catch(IOException io) {
					System.out.println(">> Fail to listen to requests!");
					System.exit(1);
				}
				
			} while (true);// end of while loop
			
	
		}catch(Exception e) {
			e.printStackTrace();
		}

	}// end of main
	
	
public static void receiveHandle(Socket socket,PrintWriter outputSocket,int senderPort) {
	try {
		// create the response with the port number which will receive data from clients through UDP
		String response="SEND REQUEST OK: receive data with the port:"+Constants.SERVER_UDP_PORT;
		System.out.println(">> Response: "+response+Constants.CRLF);
		
		// send the response
		outputSocket.println(response+Constants.CRLF+"STOP");
		outputSocket.close();

		
		PacketBoundedBufferMonitor bm=new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);
		InetAddress senderIp=socket.getInetAddress();// get the IP of the sender		
		InetAddress receiverIp=InetAddress.getByName("localhost");
		
		receiveFile(bm,receiverIp,Constants.SERVER_UDP_PORT,senderIp, senderPort);// receive the file		

	}catch(Exception e) {e.printStackTrace();}
	
}
	
public static void receiveFile(PacketBoundedBufferMonitor bm, InetAddress receiverIp,int receiverPort,InetAddress senderIp,int senderPort) {
	
	PacketReceiver packetReceiver=new PacketReceiver(bm,receiverIp,receiverPort,senderIp,senderPort);
	packetReceiver.start();
	
	FileWriter fileWriter=new FileWriter(bm,Constants.SERVER_FILE_ROOT);
	fileWriter.start();	
	try {
		packetReceiver.join();
		fileWriter.join();
	} 
		catch (InterruptedException e) {e.printStackTrace();}
	
}

public static void deletehandle(String filename)
{
	try
	{
		File file= new File(Constants.SERVER_FILE_ROOT+filename);
		if(file.exists())
		{
			file.delete();
			System.out.println("File Deleted :"+filename);
		}
	}
	catch(Exception ex)
	{
		ex.printStackTrace();
	}
}
public static void sendhandle(Socket socket,PrintWriter outputSocket,int clientUDPPort,String filename)
{
	String response="SEND REQUEST OK: receive data with the port:"+Constants.SERVER_UDP_PORT;
	
	System.out.println(">> Response: "+Constants.CRLF);
	File folder = new File(Constants.SERVER_FILE_ROOT);
	String[] files = folder.list();
	// send the response
	outputSocket.println("Response:"+files.length+""+Constants.CRLF+"STOP");
	outputSocket.close();
	int i=0;

	 try {
				PacketBoundedBufferMonitor bufferMonitor=new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);			
				InetAddress senderIp=InetAddress.getByName("localhost");
				InetAddress receiverIp=InetAddress.getByName("localhost");
				File file=new File(Constants.SERVER_FILE_ROOT+filename);	
				if (!file.exists()) return;		
				FileReader fileReader=new FileReader(bufferMonitor,filename,Constants.SERVER_FILE_ROOT);
				fileReader.start();
				PacketSender packetSender=new PacketSender(bufferMonitor,senderIp,Constants.SERVER_UDP_PORT,receiverIp,clientUDPPort);
				packetSender.start();	
				try {
					packetSender.join();
					fileReader.join();				
				} 
				catch (InterruptedException e) {}	
				i++;
			//}
			}catch(Exception e) {e.printStackTrace();}
}

public static void getfilelistfromServer(Socket socket,PrintWriter outputSocket)
{
	File folder = new File(Constants.SERVER_FILE_ROOT);
	String[] files = folder.list();
	String response="";
	for(int i=0;i<files.length;i++)
	{
		response+=files[i]+":";
	}
	//System.out.println(response);
	
	// send the response
	outputSocket.println(response);
	outputSocket.close();
}
public static void receiveModifyHandle(Socket socket,PrintWriter outputSocket,int senderPort) {
	try {
		// create the response with the port number which will receive data from clients through UDP
		String response="MODIFY REQUEST OK: receive data with the port:"+Constants.SERVER_UDP_PORT;
		System.out.println(">> Response: "+response+Constants.CRLF);
		
		// send the response
		outputSocket.println(response+Constants.CRLF+"STOP");
		outputSocket.close();

		
		PacketBoundedBufferMonitor bm=new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);
		InetAddress senderIp=socket.getInetAddress();// get the IP of the sender		
		InetAddress receiverIp=InetAddress.getByName("localhost");
		
		receiveFile(bm,receiverIp,Constants.SERVER_UDP_PORT,senderIp, senderPort);// receive the file		

	}catch(Exception e) {e.printStackTrace();}
	
}
	
}

	