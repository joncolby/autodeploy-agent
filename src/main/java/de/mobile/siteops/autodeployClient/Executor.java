package de.mobile.siteops.autodeployClient;

/**
 * Watches the specified znode and saves the data that corresponds
 * to the znode in the filesystem. It also starts the specified program
 * with the specified arguments when the znode exists and kills
 * the program if the znode goes away.
 */
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.zookeeper.*;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class Executor implements Watcher, Runnable, DataMonitor.DataMonitorListener {

    static Logger logger = Logger.getLogger(Executor.class.getName());

    String znode;

    DataMonitor dm;

    ZooKeeper zk;

    String filename;

    String hostPort;

    String params[];
    
    String script;

    Process child;

    public Executor(String hostPort, String znode, String filename, String script, String params[]) throws KeeperException, IOException {
        this.filename = filename;
        this.hostPort = hostPort;
        this.params = params;
        this.znode = znode;
        zk = new ZooKeeper(hostPort, 3000, this);
        dm = new DataMonitor(zk, znode, null, this);
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        PropertyConfigurator.configureAndWatch("../conf/log4j.properties", 60000);

        if (args.length < 4) {
            System.err.println("USAGE: Executor hostPort znode filename program [args ...]");
            System.exit(2);
        }
        String hostPort = args[0];
        String znode = args[1];
        String filename = args[2];
        String script = args[3];
        String params[] = new String[args.length - 4];
        System.arraycopy(args, 4, params, 0, params.length);

        // String hostznode = znode.substring(0,znode.lastIndexOf('/'));

        logger.info("logging to mongodb!!!");

        try {
            new Executor(hostPort, znode, filename, script, params).run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /***************************************************************************
     * We do not process any events ourselves, we just need to forward them on.
     */
    public void process(WatchedEvent event) {
        dm.process(event);
    }

    public void run() {
        try {
            synchronized (this) {
                while (!dm.dead) {
                    wait();

                    // notifyAll was received from the closing method of DataMonitorListener Interface

                    // try to reconnect if session expired. sessions expired when the connect between server/client down
                    // long enough

                    /*
                     * try { if (zk != null) zk.close();
                     * 
                     * new Executor(this.hostPort, this.znode, this.filename, this.exec).run(); } catch (Exception e) {
                     * e.printStackTrace(); }
                     */

                    // very easy way to handle reinitialization after and expiration - let tanuki restart the app
                    System.err.println("Exiting application with code 99 to allow a restart by the Tanuki Wrapper");
                    System.exit(99);

                }
            }
        } catch (InterruptedException e) {}
    }

    public void closing(int rc) {
        synchronized (this) {
            notifyAll();
        }
    }

    static class StreamWriter extends Thread {
        OutputStream os;

        InputStream is;

        StreamWriter(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
            start();
        }

        public void run() {
            byte b[] = new byte[80];
            int rc;
            try {
                while ((rc = is.read(b)) > 0) {
                    os.write(b, 0, rc);
                }
            } catch (IOException e) {}

        }
    }

    public void exists(byte[] data) {
        if (data == null) {
            if (child != null) {
                System.out.println("znode " + znode + " has been deleted or has no data. Killing child process.");
                child.destroy();
                try {
                    child.waitFor();
                } catch (InterruptedException e) {}
            }
            child = null;
        } else {
            /*
             * if the child process is still running, destroy (kill) the process so the new event can be consumed
             */
            if (child != null) {
                System.out.println("Stopping child");
                child.destroy();
                try {
                    child.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                FileOutputStream fos = new FileOutputStream(filename);
                fos.write(deserialize(data).toString().getBytes());
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                System.out.println("Starting child");
                List<String> args = new ArrayList<String>();
                args.add(script);
                args.add(filename);
                args.addAll(Arrays.asList(params));
                child = Runtime.getRuntime().exec((String[]) args.toArray());

                // new StreamWriter(child.getInputStream(), System.out);
                // new StreamWriter(child.getErrorStream(), System.err);

                // ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                // ByteArrayOutputStream stderr = new ByteArrayOutputStream();

                // StreamGobbler outputGobbler = new StreamGobbler(child.getInputStream(), "OUTPUT", stdout);
                // StreamGobbler errorGobbler = new StreamGobbler(child.getErrorStream(), "ERROR", stderr);

                StreamGobbler outputGobbler = new StreamGobbler(child.getInputStream(), "OUTPUT");
                StreamGobbler errorGobbler = new StreamGobbler(child.getErrorStream(), "ERROR");

                // start threads to reading output of the external program
                errorGobbler.start();
                outputGobbler.start();

                try {

                    // wait for the process to complete and get exit value
                    int exitValue = child.waitFor();

                    System.out.println("process exited with " + exitValue);

                    switch (exitValue) {
                        case 0:
                            try {
                                zk.delete(znode, -1);
                            } catch (KeeperException ke) {
                                System.out.println("problem occurred deleting znode: " + ke.toString());
                            } finally {
                                // always close stdin/stdout
                                // stdout.close();
                                // stderr.close();
                            }

                            break;
                        case 1:
                            System.out.println("Exit code " + exitValue + " is not handled");
                            break;
                        case 2:
                            System.out.println("Exit code " + exitValue + " is not handled");
                            break;
                        default:
                            System.out.println("Exit code " + exitValue + " (fall-through) is not handled");
                            return;
                    }

                } catch (InterruptedException e) {}

            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }

    private Object deserialize(byte[] bytes) {
        ObjectInputStream oin = null;
        try {
            oin = new ObjectInputStream(new ByteArrayInputStream(bytes));
            return oin.readObject();
        } catch (EOFException eof) {
            return null;
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    private byte[] serialize(Object obj) {
        byte[] bytes = new byte[0];
        if (null != obj) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ObjectOutputStream oout = null;
            try {
                oout = new ObjectOutputStream(out);
                oout.writeObject(obj);
            } catch (EOFException eof) {
                return new byte[0];
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
            bytes = out.toByteArray();
        }
        return bytes;
    }

    // START

    class StreamGobbler extends Thread {
        InputStream is;

        String type;

        OutputStream os;

        StreamGobbler(InputStream is, String type) {
            this(is, type, null);
        }

        StreamGobbler(InputStream is, String type, OutputStream redirect) {
            this.is = is;
            this.type = type;
            this.os = redirect;
        }

        public void run() {
            try {
                PrintWriter pw = null;
                if (os != null) pw = new PrintWriter(os);

                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    if (pw != null) pw.println(line);

                    System.out.println(type + ">" + line);

                    if (type == "ERROR") logger.error(line);
                    else
                        logger.info(line);
                }
                if (pw != null) pw.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }

    // END

}
