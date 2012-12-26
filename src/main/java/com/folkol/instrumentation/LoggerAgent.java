package com.folkol.instrumentation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

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
    private String pattern;
    public LoggerAgent(String agentArgument) {
        pattern = agentArgument != null ? agentArgument : "com/folkol";
    }

    public static void premain(String agentArgument, Instrumentation instrumentation) {
        instrumentation.addTransformer(new LoggerAgent(agentArgument));
    }

    String def = "private static java.util.logging.Logger _log;";
    String ifLog = "if (_log.isLoggable(java.util.logging.Level.INFO))";

    public byte[] transform(ClassLoader loader, String klassNamn, Class<?> klasse, java.security.ProtectionDomain domain, byte[] classFileData) {
        if (klassNamn.startsWith(pattern)) {
            return pyntaKlassen(klassNamn, klasse, classFileData);
        }
        return null;
    }

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
                        pyntaMetod(methods[i]);
                    }
                }
                b = cl.toBytecode();
            }
        } catch (Exception e) {
        } finally {
            if (cl != null) {
                cl.detach();
            }
        }
        return b;
    }

    private void pyntaMetod(CtBehavior method) throws NotFoundException, CannotCompileException {
        String signature = getSignature(method);
        String returnValue = returnValue(method);
        method.insertBefore(ifLog + "_log.info(\"HACKATHON entering " + method.getDeclaringClass().getSimpleName() + " " + signature + ");");
        method.insertAfter(ifLog + "_log.info(\"HACKATHON exiting "  + method.getDeclaringClass().getSimpleName() + " " + signature + returnValue + ");");
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
        return locals.variableName(i + 1);
    }
}