// ActionScript file
package com.eggfly.test{
	import flash.events.Event;
	import flash.events.IOErrorEvent;
	import flash.events.ProgressEvent;
	import flash.events.SecurityErrorEvent;
	import flash.net.Socket;
	import flash.utils.ByteArray;

	public class Test1{
		private var app:Hello;
		private var socket:Socket; 
		public function Test1(app:Hello){
			this.app = app;
		}
		public function foo1(): void {
			//				Security.allowDomain("*");
			//				Security.allowInsecureDomain("*");
			// app.button1.label = "sss";
			socket = new Socket();
			socket.addEventListener(Event.CONNECT, onConnected);
			socket.addEventListener(Event.CLOSE, onClosed);
			socket.addEventListener( ProgressEvent.SOCKET_DATA, onSocketDataReceived );
			socket.addEventListener( IOErrorEvent.IO_ERROR, onIOError );
			socket.addEventListener( SecurityErrorEvent.SECURITY_ERROR, onSecurityError );
			
			socket.connect("www.baidu.com", 80);
			socket.writeUTFBytes("GET / HTTP/1.1\r\n");
			socket.writeUTFBytes("Host: www.baidu.com\r\n\r\n");
			socket.flush();
		}
		public function foo2() : void {
			socket = new Socket();
			socket.addEventListener(Event.CONNECT, onConnected);
			socket.addEventListener(Event.CLOSE, onClosed);
			socket.addEventListener( ProgressEvent.SOCKET_DATA, onSocketDataReceived );
			socket.addEventListener( IOErrorEvent.IO_ERROR, onIOError );
			socket.addEventListener( SecurityErrorEvent.SECURITY_ERROR, onSecurityError );
			socket.connect("ubuntu.host8.tk", 5222);
			socket.writeUTFBytes("<stream:stream to='xiaomi.com' xmlns:stream='xm' xmlns='xm' model='OptiPlex 990' version='102' connpt='wifi' host='58.68.235.232'>");
			socket.flush();
		}
		public function foo4() : void {
			if (socket == null) {
				app.logText.appendText("new socket and try to connect..");
				socket = new Socket();
				socket.addEventListener(Event.CONNECT, onConnected);
				socket.addEventListener(Event.CLOSE, onClosed);
				socket.addEventListener( ProgressEvent.SOCKET_DATA, onSocketDataReceived );
				socket.addEventListener( IOErrorEvent.IO_ERROR, onIOError );
				socket.addEventListener( SecurityErrorEvent.SECURITY_ERROR, onSecurityError );
				socket.connect("58.68.235.232", 5222);
			}
			var data:String = app.text1.text;
			socket.writeUTFBytes(data);
			app.text2.appendText("sent: "+data + "\n");
			socket.flush();
			// app.text1.text = "";
		}
		private function onConnected(event:Event): void
		{
			app.logText.appendText("connected\n");
		}
		private function onClosed(event:Event): void
		{
			app.logText.appendText("closed\n");
			this.socket = null;
		}
		private function onSocketDataReceived(event:ProgressEvent): void
		{
			var bytedata:ByteArray = new ByteArray();
			// The default value of 0 causes all available data to be read.
			socket.readBytes( bytedata );			
			app.text2.appendText("recieved: " + bytedata.toString()+"\n");
		}
		
		/**
		 * This fires the standard dispatchError method
		 *
		 * @param	event
		 */
		protected function onIOError( event:IOErrorEvent ):void
		{
			app.text2.appendText("onIOError");
			app.text2.appendText(event.toString());
		}
		
		/**
		 * @private
		 *
		 * @param	event
		 */
		protected function onSecurityError( event:SecurityErrorEvent ):void
		{
			app.text2.appendText("onSecurityError");
			app.text2.appendText(event.toString());
		}
	}
}