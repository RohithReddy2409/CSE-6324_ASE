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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;


public class Client {
	
	public Client() {	
	}

	public static void main(String[] args) {
        File folder = new File(Constants.CLIENT_FILE_ROOT);
		String[] files = folder.list();
		try {
			//PacketSender packetSender=new PacketSender(bufferMonitor,senderIp,Constants.CLIENT_UDP_PORT,serverIp,serverPort);
			for(int i=0;i<files.length;i++)
			{
				sendfiletoserver(files[i].toString());
			}
			watchclientDir();
		}catch(Exception e) {e.printStackTrace();}
		
	}// end of main
	

	/**
	 * get the port number, through which the server will receive data 
	 * @param tcpSocket
	 * @param action
	 * @param fileName
	 * @param udpPort, the port number that the client will send data
	 * @return
	 */
	public static int getPortFromServer(Socket tcpSocket,String action,String fileName,int udpPort) {
		int serverPort=0;
		try {
			Scanner inputSocket =  new Scanner(tcpSocket.getInputStream());
			PrintWriter outputSocket = new PrintWriter(tcpSocket.getOutputStream(), true);
			
			// send the HTTP packet	
			String request=action+" # "+fileName+" # "+udpPort;
		    outputSocket.println(request+Constants.CRLF+"STOP");
			System.out.println(Constants.CRLF+">> Request:"+request);
		    
			// receive the response	
		    String line=inputSocket.nextLine();
		    
		  // get the port number of the server that will receive data for the file		    
		    while(!line.equals("STOP")) {
		    	if (line.isEmpty()) {line=inputSocket.nextLine();continue;}
		    	if(line.startsWith(action)){
					// get the new port that is assigned by the server to receive data
		    		System.out.println(">> Response:"+line+Constants.CRLF);
					String [] items=line.split(":");					
					serverPort=Integer.parseInt(items[items.length-1]);
					break;
				}
		    	line=inputSocket.nextLine();
		    }
			 inputSocket.close();
		     outputSocket.close();
		}catch(Exception e) {e.printStackTrace();}
		return serverPort;
	}
	public static void sendfiletoserver(String fileName) throws IOException
	{
		InetAddress serverIp=InetAddress.getByName("localhost");			
		Socket tcpSocket = new Socket(serverIp, Constants.SERVER_TCP_PORT);
	
		String action="SEND REQUEST";
		int serverPort=getPortFromServer(tcpSocket,action,fileName,Constants.CLIENT_UDP_PORT);
		if (serverPort==0) {return;}			
	
	
		// start sending the file
		PacketBoundedBufferMonitor bufferMonitor=new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);			
		InetAddress senderIp=InetAddress.getByName("localhost");
		File file=new File(Constants.CLIENT_FILE_ROOT+fileName);	
		if (!file.exists()) return;		
		FileReader fileReader=new FileReader(bufferMonitor,fileName,Constants.CLIENT_FILE_ROOT);
		fileReader.start();
		PacketSender packetSender=new PacketSender(bufferMonitor,senderIp,Constants.CLIENT_UDP_PORT,serverIp,serverPort);
		packetSender.start();	
		try {
			packetSender.join();
			fileReader.join();				
		} 
		catch (InterruptedException e) {}
	}
	
	public static void watchclientDir() throws IOException, InterruptedException 
	{
		WatchService watcher=FileSystems.getDefault().newWatchService();
		Path path=Paths.get(Constants.CLIENT_FILE_ROOT);
		path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_DELETE,StandardWatchEventKinds.ENTRY_MODIFY);
		WatchKey key;
		while((key=watcher.take())!=null)
		{
			System.out.println("Watching client Directory for changes");
			for(WatchEvent<?> event:key.pollEvents())
			{
				System.out.println("Event Type "+event.kind()+"File is affected "+event.context());
				if(event.kind().toString().equals("ENTRY_CREATE"))
				{
					System.out.println("New file created file at client file name is "+event.context());
					sendfiletoserver(event.context().toString());
				}
				else if (event.kind().toString().equals("ENTRY_DELETE"))
				{
					System.out.println("File is deleted at client, File Name : "+event.context());
					Deletefilefromserver(event.context().toString());
				}
				else if(event.kind().toString().equals("ENTRY_MODIFY"))
				{
					System.out.println("File has been modified at client"+event.context());
					getdeltasyncblocks(event.context().toString());
					sendmodifiedfile(event.context().toString());
				}
			}
			key.reset();
		}
			
	}
	public static void Deletefilefromserver(String fileName) throws IOException
	{
		InetAddress serverIp=InetAddress.getByName("localhost");			
		Socket tcpSocket = new Socket(serverIp, Constants.SERVER_TCP_PORT);
		String action="DELETE REQUEST";
		PrintWriter outputSocket = new PrintWriter(tcpSocket.getOutputStream(), true);
		
		// send the HTTP packet	
		String request=action+" # "+fileName;
	    outputSocket.println(request+Constants.CRLF+"STOP");
	    tcpSocket.close();
		return;
	}


	public static List<String>  getdeltasyncblocks(String filename) throws IOException
	{
        List<String> lines = Files.readAllLines(Path.of(Constants.CLIENT_FILE_ROOT+filename));
        List<String> lines2 = Files.readAllLines(Path.of(Constants.SERVER_FILE_ROOT+filename));
        System.out.println("Following changes happened in file");
        for(int i=0;i<lines.size();i++)
        {
            if(lines.get(i).equals(lines2.get(i)))
            {
                //System.out.println("There is no change\n");
            }
            else
            {
            	System.out.println(lines.get(i));
                lines2.set(i, lines2.get(i).replace(lines2.get(i), lines.get(i)));
            }
        }
        return lines2;
	}
	
	public static void sendmodifiedfile(String fileName) throws IOException
	{
		InetAddress serverIp=InetAddress.getByName("localhost");			
		Socket tcpSocket = new Socket(serverIp, Constants.SERVER_TCP_PORT);
	
		String action="MODIFYFILE REQUEST";
		Scanner inputSocket =  new Scanner(tcpSocket.getInputStream());
		PrintWriter outputSocket = new PrintWriter(tcpSocket.getOutputStream(), true);
		int udpPort=Constants.CLIENT_UDP_PORT;
		// send the HTTP packet	
		String request=action+" # "+fileName+" # "+udpPort;
	    outputSocket.println(request+Constants.CRLF+"STOP");
		System.out.println(Constants.CRLF+">> Request:"+request);
	    
		int serverPort=	Constants.SERVER_UDP_PORT;
		// start sending the file
		PacketBoundedBufferMonitor bufferMonitor=new PacketBoundedBufferMonitor(Constants.MONITOR_BUFFER_SIZE);			
		InetAddress senderIp=InetAddress.getByName("localhost");
		File file=new File(Constants.CLIENT_FILE_ROOT+fileName);	
		if (!file.exists()) return;		
		FileReader fileReader=new FileReader(bufferMonitor,fileName,Constants.CLIENT_FILE_ROOT);
		fileReader.start();
		PacketSender packetSender=new PacketSender(bufferMonitor,senderIp,Constants.CLIENT_UDP_PORT,serverIp,serverPort);
		packetSender.start();	
		try {
			packetSender.join();
			fileReader.join();				
		} 
		catch (InterruptedException e) {}
	}
	
	
}