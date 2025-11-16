package com.cache.util;

import com.cache.core.exception.SerializationException;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Utility class for efficient serialization using Kryo.
 * Thread-safe with ThreadLocal Kryo instances.
 */
public class SerializationUtil {
    private static final Logger logger = LoggerFactory.getLogger(SerializationUtil.class);
    
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(() -> {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        kryo.setReferences(true);
        return kryo;
    });

    /**
     * Serialize an object to byte array using Kryo.
     */
    public static byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }
        
        try {
            Kryo kryo = kryoThreadLocal.get();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 Output output = new Output(baos, 4096)) {
                kryo.writeClassAndObject(output, obj);
                output.flush();
                return baos.toByteArray();
            }
        } catch (Exception e) {
            logger.error("Failed to serialize object of type: {}", 
                obj != null ? obj.getClass().getName() : "null", e);
            throw new SerializationException("Serialization failed", e);
        }
    }

    /**
     * Deserialize a byte array to an object using Kryo.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] data, Class<T> expectedType) {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            Kryo kryo = kryoThreadLocal.get();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
                 Input input = new Input(bais, 4096)) {
                Object obj = kryo.readClassAndObject(input);
                if (expectedType != null && !expectedType.isInstance(obj)) {
                    throw new ClassCastException("Expected " + expectedType + " but got " + obj.getClass());
                }
                return (T) obj;
            }
        } catch (Exception e) {
            logger.error("Failed to deserialize object to type: {}", 
                expectedType != null ? expectedType.getName() : "unknown", e);
            throw new SerializationException("Deserialization failed", e);
        }
    }

    /**
     * Deserialize without type checking.
     */
    public static Object deserialize(byte[] data) {
        return deserialize(data, null);
    }
}

