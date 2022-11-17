package edu.zju.cst.aces.sootex;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class ASMParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ASMParser.class);

    Set<String> getEntries(Set<ClassNode> classNodes, Collection<String> methodSigs) {
        Set<String> entries = new HashSet<>();
        return entries;
    }

    public static Set<ClassNode> loadClasses(File classFile) throws IOException {
        Set<ClassNode> classes = new HashSet<>();
        InputStream is = new FileInputStream(classFile);
        return readClass(classFile.getName(), is, classes);
    }


    public static Set<ClassNode> loadClasses(JarFile jarFile) throws IOException {
        Set<ClassNode> targetClasses = new HashSet<>();
        Stream<JarEntry> str = jarFile.stream();
        str.forEach(z -> readJar(jarFile, z, targetClasses));
        jarFile.close();
        return targetClasses;
    }


    private static Set<ClassNode> readClass(String className, InputStream is, Set<ClassNode> targetClasses) {
        try {
            byte[] bytes = IOUtils.toByteArray(is);
            String cafebabe = String.format("%02X%02X%02X%02X", bytes[0], bytes[1], bytes[2], bytes[3]);
            if (!cafebabe.toLowerCase().equals("cafebabe")) {
                // This class doesn't have a valid magic
                return targetClasses;
            }
            ClassNode cn = getNode(bytes);
            targetClasses.add(cn);
        } catch (Exception e) {
            LOGGER.warn("Fail to read class {}", className, e);
        }
        return targetClasses;
    }


    private static Set<ClassNode> readJar(JarFile jar, JarEntry entry, Set<ClassNode> targetClasses) {
        String name = entry.getName();
        if (name.endsWith(".class")) {
            String className = name.replace(".class", "").replace("/", ".");
            // if relevant options are not specified, classNames will be empty
            try (InputStream jis = jar.getInputStream(entry)) {
                return readClass(className, jis, targetClasses);
            } catch (IOException e) {
                LOGGER.warn("Fail to read class {} in jar {}", entry, jar.getName(), e);
            }
        } else if (name.endsWith("jar") || name.endsWith("war")) {

        }
        return targetClasses;
    }


    private static ClassNode getNode(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        try {
            cr.accept(cn, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // garbage collection friendly
        cr = null;
        return cn;
    }

}
