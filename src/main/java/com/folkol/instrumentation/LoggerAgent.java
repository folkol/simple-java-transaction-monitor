package com.folkol.instrumentation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.LocalVariableAttribute;

public class LoggerAgent implements ClassFileTransformer {
    public static void premain(String agentArgument, Instrumentation instrumentation) {
        if (agentArgument != null) {
            String[] args = agentArgument.split(",");
            Set<String> argSet = new HashSet<String>(Arrays.asList(args));
            if (argSet.contains("time")) {
                System.out.println("Start at " + new Date());
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    public void run() {
                        System.out.println("Stop at " + new Date());
                    }
                });
            }
        }
        instrumentation.addTransformer(new LoggerAgent());
    }

    String def = "private static java.util.logging.Logger _log;";
    String ifLog = "if (_log.isLoggable(java.util.logging.Level.INFO))";
    String notignore = "com/polopoly";

    public byte[] transform(ClassLoader loader, String klassNamn, Class<?> klasse, java.security.ProtectionDomain domain, byte[] classFileData) {
        if (klassNamn.startsWith(notignore)) {
            return pyntaKlassen(klassNamn, klasse, classFileData);
        }
        return null;
    }

    // Note: The logger variable has been named _log. In a production version an
    // unused variable name should be found and used.
    private byte[] pyntaKlassen(String name, Class<?> clazz, byte[] b) {
        ClassPool pool = ClassPool.getDefault();
        CtClass cl = null;
        try {
            cl = pool.makeClass(new java.io.ByteArrayInputStream(b));
            if (cl.isInterface() == false) {
                CtField field = CtField.make(def, cl);
                String getLogger = "java.util.logging.Logger.getLogger(" + name.replace('/', '.') + ".class.getName());";
                cl.addField(field, getLogger);
                CtBehavior[] methods = cl.getDeclaredBehaviors();
                for (int i = 0; i < methods.length; i++) {
                    if (methods[i].isEmpty() == false) {
                        doMethod(methods[i]);
                    }
                }
                b = cl.toBytecode();
            }
        } catch (Exception e) {
            System.err.println("Could not instrument  " + name + ",  exception : " + e.getMessage());
        } finally {
            if (cl != null) {
                cl.detach();
            }
        }
        return b;
    }

    private void doMethod(CtBehavior method) throws NotFoundException, CannotCompileException {
        String signature = getSignature(method);
        String returnValue = returnValue(method);
        method.insertBefore(ifLog + "_log.info(\">> " + signature + ");");
        method.insertAfter(ifLog + "_log.info(\"<< " + signature + returnValue + ");");
    }

    static String returnValue(CtBehavior method) throws NotFoundException {
        String returnValue = "";
        if (methodReturnsValue(method)) {
            returnValue = "\" returns: \" + $_ ";
        }
        return returnValue;
    }

    private static boolean methodReturnsValue(CtBehavior method) throws NotFoundException {
        CtClass returnType = ((CtMethod) method).getReturnType();
        String returnTypeName = returnType.getName();
        boolean isVoidMethod = (method instanceof CtMethod) && "void".equals(returnTypeName);
        boolean isConstructor = method instanceof CtConstructor;
        boolean methodReturnsValue = (isVoidMethod || isConstructor) == false;
        return methodReturnsValue;
    }

    static String getSignature(CtBehavior method) throws NotFoundException {
        CtClass parameterTypes[] = method.getParameterTypes();
        CodeAttribute codeAttribute = method.getMethodInfo().getCodeAttribute();
        LocalVariableAttribute locals = (LocalVariableAttribute) codeAttribute.getAttribute("LocalVariableTable");
        String methodName = method.getName();
        StringBuffer sb = new StringBuffer(methodName + "(\" ");
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                sb.append(" + \", \" ");
            }
            CtClass parameterType = parameterTypes[i];
            CtClass arrayOf = parameterType.getComponentType();
            sb.append(" + \"");
            sb.append(parameterNameFor(method, locals, i));
            sb.append("\" + \"=");
            // use Arrays.asList() to render array of objects.
            if (arrayOf != null && !arrayOf.isPrimitive()) {
                sb.append("\"+ java.util.Arrays.asList($" + (i + 1) + ")");
            } else {
                sb.append("\"+ $" + (i + 1));
            }
        }
        sb.append("+\")\"");
        String signature = sb.toString();
        return signature;
    }

    static String parameterNameFor(CtBehavior method, LocalVariableAttribute locals, int i) {
        if (locals == null) {
            return Integer.toString(i + 1);
        }
        if (Modifier.isStatic(method.getModifiers())) {
            return locals.variableName(i);
        }
        // skip #0 which is reference to "this"
        return locals.variableName(i + 1);
    }
}