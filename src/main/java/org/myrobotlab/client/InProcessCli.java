package org.myrobotlab.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.myrobotlab.codec.CodecUtils;
import org.myrobotlab.framework.HelloRequest;
import org.myrobotlab.framework.Message;
import org.myrobotlab.framework.interfaces.ServiceInterface;
import org.myrobotlab.lang.NameGenerator;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.service.Runtime;
import org.myrobotlab.service.interfaces.Gateway;
import org.python.jline.internal.Log;
import org.slf4j.Logger;

/**
 * 
 * @author GroG This class is a "gateway" similar to other mrl
 *         Gateways(interface) In that it exchanges messages with remote
 *         systems. In this case its stdin/stdout
 *
 */
public class InProcessCli implements Runnable {
  // FIXME - make UUID - this is a connection where Runtime is the gateway !!!

  public final static Logger log = LoggerFactory.getLogger(InProcessCli.class);

  String id;
  String uuid;
  Thread myThread = null;
  String name;
  InputStream in;
  OutputStream out;
  boolean running = false;
  String prefix = "/";

  private String remoteId;

  /**
   * The inProcessCli behave like a remote id - although it is in the same
   * process as the mrl instances. Its a general good model to follow, because
   * stdin/stdout is a pipe into and out of the instance, so just like
   * websockets, mqtt or xmpp it should behave the same
   * 
   * @param id
   * @param senderName
   * @param in
   * @param out
   */
  public InProcessCli(String id, String senderName, InputStream in, OutputStream out) {
    this.id = id + "-cli" ; // this becomes a local/remote id with prepended cli-
    this.remoteId = id; // remote id is the mrl instance
    this.name = senderName;
    this.in = in;
    this.out = out;
  }

  public void start() {
    if (myThread == null) {
      myThread = new Thread(this, "client-stdin-worker");
      myThread.start();
    } else {
      Log.info("stdin already running");
    }
  }

  @Override
  public void run() {

    try {
      running = true;
      String uuid = java.util.UUID.randomUUID().toString();
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("gateway", "runtime");
      attributes.put("uuid", uuid);
      attributes.put("id", id);
      attributes.put("header-User-Agent", "stdin-client");
      attributes.put("cwd", "/");
      attributes.put("uri", "/api/cli");
      attributes.put("user", "root");
      attributes.put("host", "local");
      attributes.put("c-type", "cli");
      Runtime runtime = Runtime.getInstance();
      runtime.addConnection(uuid, attributes);
      runtime.getHelloResponse(uuid, new HelloRequest(id, uuid));

      int c = '\n';
      String readLine = "";
      // FIXME - check .available() every 300ms so we don't block forever !
      while (running
          && (c = in.read()) != 0x04 /* ctrl-d 0x04 ctrl-c 0x03 '\n' */) {

        readLine += (char) c;
        if (c == '\n') {
          try {
            process(readLine);

            // if remote send --msg--> sendBlocking ????

            // stdin -> msg ! -> invoke or sendBlockingRemote
            // cli.process(null, "cli", "/api/cli", uuid, out, readLine);
          } catch (Exception e) {
            log.error("cli process threw", e);
          }
          readLine = "";
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    running = false;
    myThread = null;
  }

  public void process(String data) {
    try {

      data = data.trim();

      if ("".equals(data)) {
        writePrompt(out, uuid); // <-- should be id no ?
        return;
      }

      // parse line for /{serviceName}/{method}/jsonEncoded? parms ?? ...
      // parse line for /{serviceName@id}/{method}/jsonEncoded? parms ?? ...

      // "create" cli specific msgs
      Message cliMsg = cliToMsg(data);

      // FIXME - probably not the way to implement exit
      // should be sending id to remote and exiting their ???
      if ("exit".equals(cliMsg.method)) {
        String ret = "exiting " + remoteId + "...";
        setRemote(Runtime.getInstance().getId());
        System.out.println(ret);
        return;
      }

      Object ret = null;

      // THIS IS NOT CORRECT !! - USE YOUR OWN isLocal !!!
      if (Runtime.getInstance().isLocal(cliMsg)) {
        // invoke locally
        ServiceInterface si = Runtime.getService(cliMsg.getName());
        ret = si.invoke(cliMsg);

        // FIXME the ret could be set by sendBlockingRemote too "if it worked"
        // instead we handle it asynchronously
        writeToJson(ret);

        writePrompt(out, data);

      } else {
        // send remotely
        // get gateway
        // send blocking remote
        // return result
        Gateway gateway = Runtime.getGatway(cliMsg.getId());
        // FIXME send"Blocking" is not currently working - send "R"eturn is ..
        // so we aren't going to write it out - instead it will come in and
        // stdInClient will
        // intercept it - as it "correctly" marks all messages from it - so
        // return msg
        // goes to stdInClient - it "pretends" to be another instance/id
        ret = gateway.sendBlockingRemote(cliMsg, 3000);
      }

    } catch (Exception e) {
      log.error("cli threw", e);
    }
  }
  
  /**
   * This is the Cli encoder - it takes a line of text and generates the
   * appropriate msg from it to either invoke (locally) or sendBlockingRemote
   * (remotely)
   * 
   * @param data
   * @return
   */
  public Message cliToMsg(String data) {
    return CodecUtils.cliToMsg("runtime@" + id, "runtime@" + remoteId, data);
  }


  public void write(String o) throws IOException {
    if (o == null) {
      out.write("null".getBytes());
    } else {
      out.write(o.getBytes());
    }
  }

  public void writeToJson(Object o) throws IOException {
    out.write(CodecUtils.toPrettyJson(o).getBytes());
  }

  public void writePrompt(OutputStream out, String id) throws IOException {
    out.write("\n".getBytes());
    out.write(getPrompt(id).getBytes());
    out.write(" ".getBytes());
  }

  /**
   * FIXME - fix name and path !!!
   * 
   * @param uuid
   * @return
   */
  public String getPrompt(String uuid) {
    return String.format("[%s@%s %s]%s", name, remoteId, "/fixpath", "#");
  }

  // FIXME - interrupt does not work on a infinite blocked read
  public void stop() {
    if (myThread != null) {
      myThread.interrupt();
    }
  }

  public static void main(String[] args) {
    try {

      // Logger.getRootLogger().setLevel(Level.INFO);

      Runtime.getInstance();

      InProcessCli client = new InProcessCli(NameGenerator.getName(), "test", System.in, System.out);
      client.start();

      // if interactive vs non-interactive which will pretty much be curl ;P BUT
      // BLOCKING !!! (ie useful)

      // client.startInteractiveMode();

      // System.out.println("password {}", password);

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void setRemote(String remoteId) {
    this.remoteId = remoteId;
  }

  public String getId() {
    return id;
  }

  public boolean isLocal(Message msg) {
    String msgId = msg.getId();
    if (msgId == null) {
      log.error("msg cannot be tested for local cli - needs to be not null {}", msg);
    }
    return msgId.endsWith(id);
  }

}
