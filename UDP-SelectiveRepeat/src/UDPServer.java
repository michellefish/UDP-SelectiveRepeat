   /*****************************************
	*Michelle Fish
	*
	*COMP 4320 - Project 3
	*April 24, 2013
	*Port Assignemnt: 10034 to 10037
	******************************************/
   
   import java.io.*;
   import java.net.*;
   import java.util.*;

   public class UDPServer {
      static DatagramSocket serverSocket;
      static InetAddress clientIPAddress;
      static int clientPort;
      final static int SERVER_PORT = 10034;
      final static int TIMEOUT = 40;
      final static int PACKET_SIZE = 512;
      final static int HEADER_SIZE = 118;
      final static int WINDOW_SIZE = 8;
      final static int ACK = 1;
      final static int NAK = 0;
   	
      //static ArrayList<DatagramPacket> packetBuffer;
      static DatagramPacket[] packetBuffer;
      static Timer timeoutTimer;
      static int[] window;
      static int startWindow;
      static int numberOfTimeouts;
   
      public static void main(String args[]) throws Exception {
         serverSocket = new DatagramSocket(SERVER_PORT);
         while(true) {//server is always listening for connections
         	//receive packet and get IP address and port
            byte[] receiveData = new byte[PACKET_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            clientIPAddress = receivePacket.getAddress();
            clientPort = receivePacket.getPort();
            
         	//parse out filename from get request
            String getRequest = new String(receivePacket.getData());
            if(!getRequest.startsWith("GET"))
               continue;
            System.out.println("\n-----FROM CLIENT-----\n" + getRequest.trim());
            String filename = getRequest.split(" ")[1];
         	
         	//send packets via Selective Repeat Protocol
            selectiveRepeat(filename); 
         }
      }
      
      private static void selectiveRepeat(String filename) throws Exception {
         byte[][] segmentedFile = segmentation(filename);
         byte[] packetData = new byte[PACKET_SIZE];
         numberOfTimeouts = 0;
         timeoutTimer = new Timer(true);  
         window = new int[segmentedFile.length];
         Arrays.fill(window, NAK);
         startWindow = 0;
      
      	//send first window of packets
         for(int i = 0; i < WINDOW_SIZE; i++){
            if(i < segmentedFile.length){
               sendPacket(i, segmentedFile[i]);
            }
         }
      	
         while(true){ 
         	//receive ack
            byte[] ackData = new byte[PACKET_SIZE];
            DatagramPacket getAck = new DatagramPacket(ackData, ackData.length);
            serverSocket.receive(getAck);
            ackPackets(getAck);
            int windowMoved = adjustWindow();
            
         	//send packets that are now in window
            for(int i = windowMoved; i > 0; i--){
               sendPacket(startWindow+WINDOW_SIZE-i, segmentedFile[startWindow+WINDOW_SIZE-i]);
            }
         
         	//check if all packets are acked and we are done
            if (allPacketsAcked()){
            	//send null character packet to indicate EOF
               createPacket(packetData, "\0".getBytes(), window.length);
               DatagramPacket sendPacket = new DatagramPacket(packetData, packetData.length, clientIPAddress, clientPort);
               serverSocket.send(sendPacket);
               String s = new String(sendPacket.getData());
               System.out.println("\n-----TO CLIENT-----\n" + s.trim());
               break;
            }
         }  	
         System.out.println("\nNUMBER OF TIMEOUTS: " + numberOfTimeouts + "\n");
      }
      
      private static boolean allPacketsAcked(){
         boolean allAcked = true;
         for(int i = 0; i < window.length; i++){
            if(window[i] == NAK)
               allAcked = false;
         }
         return allAcked;
      }
      
      private static int adjustWindow() throws Exception{
         int windowMoved = 0;
         while(true){
            if(window[startWindow] == ACK){
               if(startWindow+WINDOW_SIZE < window.length){
                  startWindow++;
                  windowMoved++;
               }
               else
                  break;  
            }
            else
               break;
         }
         return windowMoved;
      }
      
      private static void ackPackets(DatagramPacket pkt){	
         int seq = getSeq(pkt);
         String packetString = new String(pkt.getData());
         int index = packetString.indexOf("Window: ")+("Window: ".length());
      
         for(int i = seq; i < seq+WINDOW_SIZE; i++){
            int ack = Integer.parseInt(packetString.substring(index, index+1).trim());
            if(ack == ACK){
               window[i] = ACK;
            }
            index++;
         }
         System.out.println("\n-----FROM CLIENT-----\n" + packetString.trim());
      }
      
      private static void sendPacket(int seq, byte[] message)throws Exception{
         byte[] packetData = new byte[PACKET_SIZE];
         createPacket(packetData, message, seq);
         DatagramPacket pkt = new DatagramPacket(packetData, packetData.length, clientIPAddress, clientPort);
         serverSocket.send(pkt);
         String s = new String(pkt.getData());
         System.out.println("\n-----TO CLIENT-----\n" + s.trim());
         
      	//set timer
         timeoutTimer.schedule(new PacketTimeout(seq, message), TIMEOUT);
      }
      
      private static byte[][] segmentation(String filename) throws Exception{
         FileInputStream filestream = new FileInputStream(new File(filename));
         int size = (int)Math.ceil((double)(filestream.available())/(PACKET_SIZE-HEADER_SIZE));
         byte[][] segmentedFile = new byte[size][PACKET_SIZE-HEADER_SIZE];
         for(int i = 0; i < size; i++){
            for(int j = 0; j < (PACKET_SIZE-HEADER_SIZE); j++){
               if(filestream.available() != 0)
                  segmentedFile[i][j] = (byte)filestream.read();
               else
                  segmentedFile[i][j] = 0;
            }
         }
         filestream.close();
         return segmentedFile;
      }
   
      private static void createPacket(byte[] packetData, byte[] message, int seq) {
         Arrays.fill(packetData, (byte)0);
         String header = new String("HTTP/1.0 200 Document Follows\r\n"+
            								"Content-Type: text/plain\r\n"+
            								"Content-Length: " + message.length + "\r\n"+
            								"Checksum: " + errorDetectionChecksum(message) + "\r\n"+
            								"Seq: " + seq + "\r\n"+
            								"\r\n");
         byte[] headerData = header.getBytes();
         for (int i = 0; i < headerData.length; i++) {
            packetData[i] = headerData[i];
         }
         for (int i = 0; i < message.length; i++) {
            packetData[headerData.length+i] = message[i];
         }
      }
      
      private static int errorDetectionChecksum(byte[] message){
         int checksum = 0;
         for(int i = 0; i < message.length; i++){
            checksum += message[i];
         }
         return checksum;
      }
      
      private static int getSeq(DatagramPacket pkt){
         String packetString = new String(pkt.getData());
         int index = packetString.indexOf("Seq: ")+("Seq: ".length());
         int seq = Integer.parseInt(packetString.substring(index, index+3).trim());
         return seq;
      }
      
      private static class PacketTimeout extends TimerTask{
         private int seq;
         private byte[] message;
      	
         public PacketTimeout(int seq, byte[] message){
            this.seq = seq;
            this.message = message;
         }
      	
         public void run(){
            //if packet has not been ACKed
            if(window[seq] == 0){
               System.out.println("***PACKET TIMEOUT (seq: "+seq+")***");
               numberOfTimeouts++;
               try{
                  sendPacket(seq, message);
               }
                  catch (Exception e){}
            }
         }
      }
   }

