package de.mobile.siteops.zookeeper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.log4j.Logger;

public class ZookeeperUtils {

    private static Logger logger = Logger.getLogger(ZookeeperUtils.class.getName());

    public static Object deserialize(byte[] bytes) {
        ObjectInputStream oin = null;
        try {
            oin = new ObjectInputStream(new ByteArrayInputStream(bytes));
            return oin.readObject();
        } catch (EOFException eof) {
            return null;
        } catch (IOException e) {
            logger.error("Error when trying to deserialize: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            logger.error("Deserialized class not found: " + e.getMessage());
        }
        return null;
    }

    public static byte[] serialize(Object obj) {
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
                logger.error("Error when trying to serialize: " + e.getMessage());
            }
            bytes = out.toByteArray();
        }
        return bytes;
    }
    
}
