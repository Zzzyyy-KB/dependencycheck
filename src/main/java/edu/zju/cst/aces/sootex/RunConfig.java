package edu.zju.cst.aces.sootex;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class RunConfig {

    private String name;
    private String classPath;

    private CGType cgType;

    private Map<String, String> entranceSetting;

    private File tmpFolder;

    private static final Logger LOGGER = LoggerFactory.getLogger(RunConfig.class);

    public RunConfig(String confPath) throws IOException {
        String confFile = FileUtils.readFileToString(new File(confPath));
        JSONObject object = JSON.parseObject(confFile);
        tmpFolder = Files.createTempDir();
        tmpFolder.deleteOnExit();
        name = object.getString("name");
        classPath = buildClassPath(object.getString("ClassPath"));
        cgType = CGType.valueOf(object.getString("CGType"));
        entranceSetting = object.getObject("EntranceSetting", Map.class);
    }

    /**
     *
     * @param str path to wars, jars and class, separated by comma, note that it can be directory
     * @return classpath in standard format that can be feed to soot
     */
    private String buildClassPath(String str) throws IOException {
        List<String> artifacts = new ArrayList<>();
        String[] paths = str.split(";");
        for (String path : paths) {
            if (new File(path).isDirectory()) {
                FileUtils.listFiles(new File(path),
                        new String[]{"jar", "war", "class"}, true).
                        stream().forEach(file -> artifacts.add(file.getAbsolutePath()));
            } else if (path.endsWith(".jar") || path.endsWith(".war") || path.endsWith(".class")) {
                artifacts.add(path);
            }
        }
        List<String> extractedJars = new ArrayList<>();
        for (String path : artifacts) {
            if (path.endsWith("jar") || path.endsWith("war")) {
                try (ZipFile zipFile = new ZipFile(path)) {
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        if (!entry.getName().endsWith(".jar"))
                            continue;
                        File entryDestination = new File(tmpFolder,  entry.getName());
                        entryDestination.getParentFile().mkdirs();
                        transferTo(zipFile.getInputStream(entry), new FileOutputStream(entryDestination));
                        extractedJars.add(entryDestination.getAbsolutePath());
                    }
                }
            }
        }
        artifacts.addAll(extractedJars);
        return String.join(File.pathSeparator, artifacts);
    }

    /**
     * copy from JDK 9+
     * @param is
     * @param out
     * @return
     * @throws IOException
     */
    private static long transferTo(InputStream is,OutputStream out) throws IOException {
        final int DEFAULT_BUFFER_SIZE = 8192;
        Objects.requireNonNull(out, "out");
        long transferred = 0;
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int read;
        while ((read = is.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
            out.write(buffer, 0, read);
            transferred += read;
        }
        return transferred;
    }

    public Set<String> buildEntrance(String classPath) {
        Set<String> entrances = new HashSet<>();

        String[] packageInclusion = entranceSetting.containsKey("PackageInclusion")?
                entranceSetting.get("PackageInclusion").split(";"):new String[]{};
        String[] packageExclusion = entranceSetting.containsKey("PackageExclusion")?
                entranceSetting.get("PackageExclusion").split(";"):new String[]{};
        String[] methodInclusion = entranceSetting.containsKey("MethodInclusion")?
                entranceSetting.get("MethodInclusion").split(";"):new String[]{};
        String[] methodExclusion = entranceSetting.containsKey("MethodExclusion")?
                entranceSetting.get("MethodExclusion").split(";"):new String[]{};

        Set<ClassNode> candidateClasses = new HashSet<>();
        for (String classFile : classPath.split(File.pathSeparator)) {
            try {
                if (classFile.endsWith(".class")) {
                    candidateClasses.addAll(ASMParser.loadClasses(new File(classFile)));
                } else {
                    candidateClasses.addAll(ASMParser.loadClasses(new JarFile(classFile)));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Set<ClassNode> targetClasses = new HashSet<>();
        for (ClassNode clazz : candidateClasses) {
            if (packageInclusion.length == 0) {
                targetClasses.add(clazz);
            } else {
                for (String packInc : packageInclusion) {
                    if (clazz.name.contains(packInc.replace(".", "/"))) {
                        targetClasses.add(clazz);
                    }
                }
            }

            for (String packExc : packageExclusion) {
                if (clazz.name.contains(packExc.replace(".", "/"))) {
                    targetClasses.remove(clazz);
                }
            }
        }

        LOGGER.info("{} classes need to parse", targetClasses.size());

        for (ClassNode clazz : targetClasses) {
            for (MethodNode method : clazz.methods) {
                // skip non-public methods and abstract method
                if (method.access != Opcodes.ACC_PUBLIC)
                    continue;
                if (methodInclusion.length == 0) {
                    entrances.add(getMethodSignature(clazz.name, method.name, method.desc));
                } else {
                    for (String methodInc : methodInclusion) {
                        if (method.name.contains(methodInc)) {
                            entrances.add(getMethodSignature(clazz.name, method.name, method.desc));
                        }
                    }
                }

                for (String methodExc : methodExclusion) {
                    if (method.name.contains(methodExc)) {
                        entrances.remove(getMethodSignature(clazz.name, method.name, method.desc));
                    }
                }
            }
        }

        return entrances;
    }

    private static String getMethodSignature(String className, String methodName, String methodDescriptor) {
        String returnType = Type.getReturnType(methodDescriptor).getClassName();
        List<String> argsType = Arrays.stream(Type.getArgumentTypes(methodDescriptor))
                .map(Type::getClassName)
                .collect(Collectors.toList());
        return String.format("<%s: %s %s(%s)>", className.replace("/", "."), returnType, methodName, String.join(",", argsType));
    }

    /**
     * get all package names from .java files
     * @param path path to the source code of project
     * @return
     */
    public Set<String> getAllPackagesFromSource(String path) {
        // equivalent to unix command: grep -Er '^package\s+\S+;' [folder]
        File srcFolder = new File(path);
        return getAllPackagesFromSource(srcFolder);
    }

    /**
     * get all package names from .java files
     * @param srcFolder full path of a directory or a text file contains the full paths of all source files
     * @return
     */
    public static Set<String> getAllPackagesFromSource(File srcFolder) {
        Pattern packageNamePattern = Pattern.compile("^package\\s+\\S+;");
        Set<String> packageNames = new HashSet<>();
        Collection<File> allFiles = new ArrayList<>();
        // source folder given, only .java source file will be analyzed
        if (srcFolder.isDirectory()) {
            allFiles = FileUtils.listFiles(srcFolder, new String[]{"java"}, true);
        } else {
            // a file contains all the full paths of source files
            try {
                List<String> lines = FileUtils.readLines(srcFolder);
                for (String line : lines) {
                    File path = new File(line);
                    if (path.isDirectory()) {
                        allFiles.addAll(FileUtils.listFiles(path, new String[]{"java"}, true));
                    } else if (line.endsWith(".java")) {
                        allFiles.add(path);
                    } else {
                        System.err.println(line + " is not a .java or a directory");
                    }
                }
            } catch (IOException e) {
                System.err.println("Can not read file " + srcFolder.toString());
            }
        }

        for (File file : allFiles) {
            try {
                List<String> lines = FileUtils.readLines(file);
                for (String line : lines) {
                    Matcher matcher = packageNamePattern.matcher(line.trim());
                    if (matcher.matches()) {
                        packageNames.add(matcher.group().replaceFirst("package", "").
                                replaceFirst(";", "").replaceFirst("\\s+", ""));
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Can not read file " + file.toString());
            }
        }

        return packageNames;
    }

    public static Set<String> compactPackageNames(Collection<String> original) {
        Set<String> compacted = new HashSet<>();
        List<String> sorted = new ArrayList<String>(original);
        Collections.sort(sorted);

        while (!sorted.isEmpty()) {
            boolean hasPrefix = false;
            String cur = sorted.get(sorted.size()-1);
            for (int i = sorted.size() - 2; i >= 0; i--) {
                if (cur.startsWith(sorted.get(i))) {
                    hasPrefix = true;
                    break;
                }
            }
            if (!hasPrefix) {
                compacted.add(cur);
            }
            sorted.remove(cur);
        }
        return compacted;
    }


    public String getName() {
        return name;
    }

    public String getClassPath() {
        return classPath;
    }

    public CGType getCgType() {
        return cgType;
    }

    public Map<String, String> getEntranceSetting() {
        return entranceSetting;
    }

}
