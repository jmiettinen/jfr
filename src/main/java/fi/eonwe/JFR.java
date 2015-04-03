package fi.eonwe;

import javax.management.DynamicMBean;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ReflectionException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 */
public class JFR {

    private final static Logger logger = Logger.getLogger(JFR.class.getCanonicalName());

    private static final String DIAGNOSTIC_MBEAN_NAME = "com.sun.management:type=DiagnosticCommand";

    private static final Object FAILURE = new Object();
    private static final DynamicMBean DIAGNOSTIC_BEAN;
    static {
        final String className = "sun.management.ManagementFactoryHelper";
        Class<?> klass = getClassForName(className);
        final String methodName = "getDiagnosticCommandMBean";
        DynamicMBean tmp = null;
        if (klass != null) {
            try {
                Method m = klass.getMethod(methodName);
                m.setAccessible(true);
                tmp = (DynamicMBean) m.invoke(null);
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | ClassCastException e) {
                logger.warning("Cannot call " + className + "#" + methodName);
            }
        }
        DIAGNOSTIC_BEAN = tmp;
    }
    private static Map<String, MBeanOperationInfo> commands;

    public synchronized static boolean ensureLoaded() {
        if (commands != null) return true;
        if (DIAGNOSTIC_BEAN == null) return false;
        MBeanInfo beanInfo = DIAGNOSTIC_BEAN.getMBeanInfo();
        MBeanOperationInfo unlockOp = null;
        MBeanOperationInfo jfrCheckOp = null;
        MBeanOperationInfo jfrStartOp = null;
        MBeanOperationInfo jfrStopOp = null;
        MBeanOperationInfo jfrDumpOp = null;

        for (MBeanOperationInfo oi : beanInfo.getOperations()) {
            switch (oi.getName()) {
                case "vmUnlockCommercialFeatures": unlockOp = oi; break;
                case "jfrCheck": jfrCheckOp = oi; break;
                case "jfrDump": jfrDumpOp = oi; break;
                case "jfrStart": jfrStartOp = oi; break;
                case "jfrStop": jfrStopOp = oi; break;
                default: break;
            }
        }
        boolean failure = false;
        if (unlockOp == null) {
            failure = true;
            logger.warning("Unable to find unlocking command");
        }
        if (jfrCheckOp == null) {
            failure = true;
            logger.warning("Unable to find jfr check command");
        }
        if (jfrDumpOp == null) {
            failure = true;
            logger.warning("Unable to find jfr dump command");
        }
        if (jfrStartOp == null) {
            failure = true;
            logger.warning("Unable to find jfr start command");
        }
        if (jfrStopOp == null) {
            failure = true;
            logger.warning("Unable to find jfr stop command");
        }
        if (!failure) {
            HashMap<String, MBeanOperationInfo> tmpCommands = new HashMap<>();
            tmpCommands.put("unlock", unlockOp);
            tmpCommands.put("check", jfrCheckOp);
            tmpCommands.put("start", jfrStartOp);
            tmpCommands.put("stop", jfrStopOp);
            tmpCommands.put("dump", jfrDumpOp);
            commands = tmpCommands;
            return invokeUnlock() != FAILURE;
        }
        return false;
    }

    private synchronized static Object invokeCommand(String name, Object[] parameters, String[] signature) {
        try {
            if (DIAGNOSTIC_BEAN != null) {
                return DIAGNOSTIC_BEAN.invoke(name, parameters, signature);
            }
            return FAILURE;
        } catch (MBeanException | ReflectionException e) {
            logger.warning(String.format("Unable to invoke %s (%s / %s)", name, Arrays.toString(parameters), Arrays.toString(signature)));
            logger.throwing(JFR.class.getCanonicalName(), "invokeCommand", e);
            return FAILURE;
        }
    }

    private synchronized static Object invokeSimpleCommand(String name) {
        return invokeCommand(name, new Object[0], new String[0]);
    }

    private synchronized static Object invokeUnlock() {
        MBeanOperationInfo cmd = commands.get("unlock");
        String[] expected = {};
//        if (!checkSignature(cmd, expected)) return FAILURE;
        return invokeSimpleCommand(cmd.getName());
    }

    private synchronized static Object record(int durationIsSeconds, String outputFile) {
        String name = String.format("JFR Recording %s", new Date().toString());
        String commandLineArgument = String.format("delay=0s compress=true duration=%ds filename=\"%s\"", durationIsSeconds, outputFile);
        MBeanOperationInfo op = commands.get("start");
        return invokeCommand(op.getName(),
                new Object[]{ new String[] { commandLineArgument } },
                getSignature(op));
    }

    public synchronized static boolean recordFor(int durationInSeconds, String outputFile) {
        if (durationInSeconds <= 0) throw new IllegalArgumentException("Time must be stricly positive (was " + durationInSeconds + ")");
        String outputFileNameWithExtension = outputFile.endsWith("jfr") ? outputFile : outputFile + ".jfr";
        return ensureLoaded() && record(durationInSeconds, outputFileNameWithExtension) != FAILURE;
    }

    public synchronized static boolean recordFor(int durationInSeconds) {
        String fileName = String.format("%s%srecord_%s.jfr",
                System.getProperty("user.dir"),
                "/",
                String.valueOf(System.currentTimeMillis()));
        return recordFor(durationInSeconds, fileName);
    }

    private static String[] toCanonicalNames(Class ... classes) {
        String[] canonicalNames = new String[classes.length];
        for (int i = 0; i < classes.length; i++) {
            canonicalNames[i] = classes[i].getCanonicalName();
        }
        return canonicalNames;
    }

    private static String[] getSignature(MBeanOperationInfo op) {
        MBeanParameterInfo[] pi = op.getSignature();
        String[] types = new String[pi.length];
        for (int i = 0; i < pi.length; i++) {
            types[i] = pi[i].getType();
        }
        return types;
    }

    public synchronized static String checkRecordings() {
        if (ensureLoaded()) {
            MBeanOperationInfo op = commands.get("check");
            Object result = invokeCommand(op.getName(),
                    new Object[]{new String[]{"verbose=false"}},
                    getSignature(op));
            if (result != FAILURE && result instanceof String) return (String) result;
        }
        return "Unable to check recordings";
    }

    private static Class<?> getClassForName(String name) {
        try {
            return JFR.class.getClassLoader().loadClass(name);
        } catch (ClassNotFoundException e) {
            logger.warning("Cannot load class " + name);
            return null;
        }
    }
}
