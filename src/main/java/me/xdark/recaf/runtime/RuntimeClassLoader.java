package me.xdark.recaf.runtime;

import me.xdark.recaf.runtime.util.VMUtil;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

/**
 * An implementation of {@link ClassLoader} that allows classes to be garbage collected
 * after all references are removed
 *
 * @author xDark
 */
public final class RuntimeClassLoader extends ClassLoader implements Closeable, AutoCloseable {

    private static final ClassDefiner DEFINER;
    private static final int BUFFER_LOAD_SIZE = 1024;
    private final Map<String, Class<?>> cachedClasses = new HashMap<>();
    private final ClassLoader scl;

    /**
     * Creates a new instance of class loader
     *
     * @param scl system class loader
     */
    public RuntimeClassLoader(ClassLoader scl) {
        super(scl);
        this.scl = scl;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) {
            return loaded;
        }
        if ((loaded = cachedClasses.get(name)) != null) {
            return loaded;
        }
        URL resource = scl.getResource(name.replace('.', '/') + ".class");
        if (resource == null) {
            return super.loadClass(name, resolve);
        }
        try {
            URLConnection connection = resource.openConnection();
            // TODO: We need to somehow replace ProtectionDomain of he class, in order to keep it's certs, etc.
            /*Certificate[] certificates;
            if (connection instanceof JarURLConnection) {
                certificates = ((JarURLConnection) connection).getCertificates();
            }*/
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_LOAD_SIZE];
            try (InputStream in = connection.getInputStream()) {
                for (int r = in.read(buffer); r != -1; r = in.read(buffer)) {
                    bytes.write(buffer, 0, r);
                }
            }
            byte[] code = bytes.toByteArray();
            return defineClass(null, name, ClassHost.class, code, null);
        } catch (IOException ex) {
            throw new ClassNotFoundException(name, ex);
        }
    }

    /**
     * Defines a class from bytes
     *
     * @param protectionDomain not used yet, leave it as {@code null}
     * @param name             name of the class
     * @param host             host, probably {@link ClassHost}
     * @param bytes            bytecode of the class
     * @param cpPatches        patches for constant pool
     * @return defined class
     */
    public Class<?> defineClass(ProtectionDomain protectionDomain, String name, Class<?> host,
                                byte[] bytes, Object[] cpPatches) {
        Class<?> klass = DEFINER.defineClass(host, bytes, cpPatches);
        if (name != null) {
            cachedClasses.put(name, klass);
        }
        // TODO: is this needed?
        cachedClasses.put(klass.getName(), klass);
        return klass;
    }

    @Override
    public void close() {
        cachedClasses.clear();
        System.runFinalization();
        System.gc();
    }

    static {
        Unsafe unsafe = VMUtil.unsafe();
        int version = VMUtil.vmVersion();
        DEFINER = version < 9 ? new Java8ClassDefiner(unsafe) : new Java9ClassDefiner(unsafe);
    }
}
