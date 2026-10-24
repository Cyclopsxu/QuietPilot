import com.silentauto.controller.RpcConnection;
import java.net.*;
import java.io.IOException;
public final class RpcConnectionRegression {
 public static void main(String[] args)throws Exception {
  int port;
  try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
   port=server.getLocalPort();
   try(Socket client=RpcConnection.connect("127.0.0.1",port,1000);Socket accepted=server.accept()) {
    if(client.getLocalPort()==port)throw new AssertionError("RPC source port must differ");
    client.getOutputStream().write(42);
    if(accepted.getInputStream().read()!=42)throw new AssertionError("real peer must receive request");
   }
  }
  for(int i=0;i<100;i++) {
   try(Socket unexpected=RpcConnection.connect("127.0.0.1",port,1000)) {
    throw new AssertionError("closed backend cannot be reported connected");
   }catch(ConnectException expected){}
  }
  System.out.println("PASS RPC real peer, distinct source port and closed-backend rejection");
 }
}
