import java.net.*;
import java.io.*;
 
/*
  Java Transparent Proxy
  Copyright (C) 1999 by Didier Frick (http://www.dfr.ch/)
  This software is provided under the GNU general public license (http://www.gnu.org/copyleft/gpl.html).
*/

public class ProxyThread extends Thread {
     protected class StreamCopyThread extends Thread {
	private Socket inSock;
	private Socket outSock;
	private boolean done=false;
	private StreamCopyThread peer;
	private boolean inFromLocal;	// in from local port
	private OutputStream out;

	private String policy = "<?xml version=\"1.0\"?>\n<!DOCTYPE cross-domain-policy SYSTEM \"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\">\n<cross-domain-policy>\n<allow-access-from domain=\"*\" to-ports=\"5222\" />\n</cross-domain-policy>";
 
	public StreamCopyThread(Socket inSock, Socket outSock, boolean in) {
	    this.inSock=inSock;
	    this.outSock=outSock;
	    this.inFromLocal = in;
	}
 
	public void sendPolicy() {
		try {
			out.write(policy.getBytes());
			System.err.println("Sentcy");
		} catch (IOException ex) {
			System.err.println("Erroring policy file");
		}
	}
 
	public void run() {
	    byte[] buf=new byte[bufSize];
	    int count=-1;
	    try {
		InputStream in=inSock.getInputStream();
		out=outSock.getOutputStream();
		try {
		    while(((count=in.read(buf))>0)&&!isInterrupted()) {
		    	if (inFromLocal && count==23 && new String(buf).startsWith("<policy-file-request/>")) {
				// send policy file back.. don't forward this to other port
				System.err.println("Gotcy request");
				peer.sendPolicy();
				inSock.close();
			}
			else {
				out.write(buf,0,count);
				//System.err.println(count+" bytes "+(inFromLocal?"sent":"received"));
			}
		    }
		} catch(Exception xc) {
		    if(debug) {
			// FIXME
			// It's very difficult to sort out between "normal"
			// exceptions (occuring when one end closes the connection
			// normally), and "exceptional" exceptions (when something
			// really goes wrong)
			// Therefore we only log exceptions occuring here if the debug flag
			// is true, in order to avoid cluttering up the log.
			err.println(header+":"+xc);
			xc.printStackTrace();
		    }
		} finally {
		    // The input and output streams will be closed when the sockets themselves
		    // are closed.
		    out.flush();
		}
	    } catch(Exception xc) {
		err.println(header+":"+xc);
		xc.printStackTrace();
	    }
	    synchronized(lock) {
		done=true;
		try {
		    if((peer==null)||peer.isDone()) {
			// Cleanup if there is only one peer OR
			// if _both_ peers are done
			inSock.close();
			outSock.close();
		    }
		    else 
			// Signal the peer (if any) that we're done on this side of the connection
			peer.interrupt();
		} catch(Exception xc) {
		    err.println(header+":"+xc);
		    xc.printStackTrace();
		} finally {
		    connections.removeElement(this);
		}
	    }
	}
 
	public boolean isDone() {
	    return done;
	}
    
	public void setPeer(StreamCopyThread peer) {
	    this.peer=peer;
	}
     }

    // Holds all the currently active StreamCopyThreads
    private java.util.Vector connections=new java.util.Vector();
    // Used to synchronize the connection-handling threads with this thread
    private Object lock=new Object();
    // The address to forward connections to
    private InetAddress dstAddr;
    // The port to forward connections to
    private int dstPort;
    // Backlog parameter used when creating the ServerSocket
    protected static final int backLog=100;
    // Timeout waiting for a StreamCopyThread to finish
    public static final int threadTimeout=2000; //ms
    // Linger time
    public static final int lingerTime=180; //seconds (?)
    // Size of receive buffer
    public static final int bufSize=2048;
    // Header to prepend to log messages
    private String header;
    // This proxy's server socket
    private ServerSocket srvSock;
    // Debug flag
    private boolean debug=false;
 
    // Log streams for output and error messages
    private PrintStream out;
    private PrintStream err;
 
    private static final String 
	argsMessage="Arguments: ( [source_address] source_port dest_address dest_port ) | config_file";
    private static final String 
	propertyPrefix="proxy";

 
    public ProxyThread(InetAddress srcAddr,int srcPort,
		       InetAddress dstAddr,int dstPort, PrintStream out, PrintStream err) 
	throws IOException {
	this.out=out;
	this.err=err;
	this.srvSock=(srcAddr==null) ? new ServerSocket(srcPort,backLog) :  
	    new ServerSocket(srcPort,backLog,srcAddr);
	this.dstAddr=dstAddr;
	this.dstPort=dstPort;
	this.header=(srcAddr==null ? "" : srcAddr.toString())+":"+srcPort+" <-> "+dstAddr+":"+dstPort;
	start();
    }
 
    public void run() {
	out.println(header+" : starting");
	try {
	    while(!isInterrupted()) {
		Socket serverSocket=srvSock.accept();
		try {
		    serverSocket.setSoLinger(true,lingerTime);
		    Socket clientSocket=new Socket(dstAddr,dstPort);
		    clientSocket.setSoLinger(true,lingerTime);
		    StreamCopyThread sToC=new StreamCopyThread(serverSocket,clientSocket, true);
		    StreamCopyThread cToS=new StreamCopyThread(clientSocket,serverSocket, false);
		    sToC.setPeer(cToS);
		    cToS.setPeer(sToC);
		    synchronized(lock) {
			connections.addElement(cToS);
			connections.addElement(sToC);
			sToC.start();
			cToS.start();
		    }
		} catch(Exception xc) {
		    err.println(header+":"+xc);
		    if(debug)
			xc.printStackTrace();
		}
	    }
	    srvSock.close();
	} catch(IOException xc) {
	    err.println(header+":"+xc);
	    if(debug)
		xc.printStackTrace();
	} finally {
	    cleanup();
	    out.println(header+" : stopped");
	}
    }
 
     private void cleanup() {
	synchronized(lock) {
	    try {
		while(connections.size()>0) {
		    StreamCopyThread sct=(StreamCopyThread)connections.elementAt(0);
		    sct.interrupt();
		    sct.join(threadTimeout);
		}
	    } catch(InterruptedException xc) {
	    }
	}
    }
 
    private static ProxyThread addProxy(String src,String srcPort, String dst, String dstPort,
					PrintStream out, PrintStream err) throws
					UnknownHostException, IOException
    {
	InetAddress srcAddr=(src==null) ? null : InetAddress.getByName(src);
	return new ProxyThread(srcAddr,Integer.parseInt(srcPort),
			       InetAddress.getByName(dst),Integer.parseInt(dstPort),out,err);
    }
 
    private static java.util.Vector parseConfigFile(String fileName,PrintStream out,PrintStream err) throws 
        FileNotFoundException, IOException, UnknownHostException
    {
	java.util.Vector result=new java.util.Vector();
	FileInputStream in=new FileInputStream(fileName);
	java.util.Properties props= new java.util.Properties();
	props.load(in);
	in.close();
	for(int i=0;;i++) {
	    String srcAddr=props.getProperty(propertyPrefix+"."+i+".sourceAddr");
	    String srcPort=props.getProperty(propertyPrefix+"."+i+".sourcePort");
	    if(srcPort==null)
		break;
	    String dstAddr=props.getProperty(propertyPrefix+"."+i+".destAddr");
	    String dstPort=props.getProperty(propertyPrefix+"."+i+".destPort");
	    if(dstAddr==null) {
		throw new IllegalArgumentException("Missing destination address for proxy "+i);
	    }
	    if(dstPort==null) {
		throw new IllegalArgumentException("Missing destination port for proxy "+i);
	    }
	    result.addElement(addProxy(srcAddr,srcPort,dstAddr,dstPort,out,err));
	}
	return result;
    }
 
    static java.util.Vector parseArguments(String[] argv,PrintStream out,PrintStream err) throws
        FileNotFoundException, IOException, UnknownHostException
    {
	java.util.Vector result=null;
	int argBase=0;
	String src=null;
	if(argv.length>1) {
	    if(argv.length>3) {
		argBase=1;
		src=argv[0];
	    }
	    result=new java.util.Vector();
	    result.addElement(addProxy(src,argv[argBase++],argv[argBase++],argv[argBase++],out,err));
	} else if(argv.length==1) {
	    result=parseConfigFile(argv[0],out,err);
	} else {
	    throw new IllegalArgumentException(argsMessage);
	}
	return result;
    }
 
    public static void main(String[] argv) throws Exception {
	System.out.println("Javasparent Proxy");
	System.out.println("Copyright1999 by Didier Frick (http://www.dfr.ch/)");
	System.out.println("Thisware is provided under the GNU general public license"+
			   " (http://www.gnu.org/copyleft/gpl.html)");
	try {
	    parseArguments(argv,System.out,System.err);
	} catch(IllegalArgumentException xc) {
	    System.err.println(xc.getMessage());
	    System.exit(1);
	}
    }
}