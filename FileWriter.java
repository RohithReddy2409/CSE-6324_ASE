import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class FileWriter extends Thread {
	private String threadName="FileWriter";
	private PacketBoundedBufferMonitor bufferMonitor;
	FileChannel channel=null;	
	String filepath="";
	public FileWriter() {}
	public FileWriter(PacketBoundedBufferMonitor bm,String path) {
		this.bufferMonitor=bm;	
		this.filepath=path;
	}
	
	public void run() {
		try {
			String fileName="";
			ByteBuffer buffer=ByteBuffer.allocate(Constants.MAX_DATAGRAM_SIZE);			
			List<Integer> packetIndices=new ArrayList<>();
			
			//System.out.println(">> Begin to write packets to a file"+Constants.CRLF);
			while(true) {
				
				Packet pkt=this.bufferMonitor.withdraw();
				
				if (pkt.getIndex()==-1) {
					System.out.println(">> Finish saving the file:"+fileName);						
					//System.out.println(">> Packet indices: "+packetIndices);
					break;
				}
				
				if (pkt.getIndex()==0) {
					// read the head packet
					String msg = pkt.getContentInString();
					fileName=msg.split(":")[1]; // head packet content: "fileName: file name"
					File file=new File(this.filepath+fileName);
					//System.out.println(file);
			        file.createNewFile();			        	
				    channel =new FileOutputStream(file,false).getChannel();				       
				    //System.out.println(Constants.CRLF+">> Prepare to write the file "+fileName+Constants.CRLF);
				
				}else {
					// write packets tp a file
					//System.out.println(">> Write to a file the packet with index "+pkt.getIndex());					
					buffer = ByteBuffer.wrap(pkt.getContent(),0,pkt.getContentSize());
					channel.write(buffer);				    
				    packetIndices.add(pkt.getIndex());
				}			
				
			}//end of writing a file
			
		}catch(Exception e) {e.printStackTrace();}
		finally {
			try {
				if(channel!=null) {
					channel.close();
				}
			}catch(Exception e) {			
				e.printStackTrace();
			}
		}
   }// end of run()
 
	

}
