package com.byterax.phoenix.read.quark;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Modern API 102 stand-in for classic {@code XposedBridge}/{@code XposedHelpers}.
 *
 * <p>Original QuarkHook is a classic-only module ({@code assets/xposed_init} →
 * {@code IXposedHookLoadPackage} + {@code XposedBridge.hookAllMethods}). This
 * project is {@code minApiVersion=102}; LSPosed does not wire the legacy
 * bridge into a modern module, so calling compileOnly {@code api:82} stubs
 * installs nothing.
 */
final class QuarkHooks {
    private static final String TAG = "SquemaQuark";

    private static volatile XposedModule module;

    private QuarkHooks() {}

    static void bind(XposedModule xposedModule) {
        module = xposedModule;
    }

    static void log(String message) {
        Log.i(TAG, message);
        XposedModule current = module;
        if (current != null) {
            try {
                current.log(Log.INFO, TAG, message);
            } catch (Throwable ignored) {
            }
        }
    }

    static Class<?> findClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("class not found: " + name, e);
        }
    }

    static void hookAllMethods(Class<?> type, String methodName, Hook hook) {
        if (type == null || methodName == null || hook == null) {
            return;
        }
        int count = 0;
        for (Method method : type.getDeclaredMethods()) {
            if (methodName.equals(method.getName())) {
                hookMember(method, hook);
                count++;
            }
        }
        if (count == 0) {
            log("no method " + type.getName() + "#" + methodName);
        }
    }

    static void hookAllConstructors(Class<?> type, Hook hook) {
        if (type == null || hook == null) {
            return;
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            hookMember(constructor, hook);
        }
    }

    static void findAndHookMethod(String className, ClassLoader loader, String methodName,
                                  Object... parameterTypesAndCallback) {
        if (parameterTypesAndCallback == null || parameterTypesAndCallback.length == 0) {
            throw new IllegalArgumentException("missing callback");
        }
        Object last = parameterTypesAndCallback[parameterTypesAndCallback.length - 1];
        if (!(last instanceof Hook)) {
            throw new IllegalArgumentException("last argument must be Hook");
        }
        Class<?>[] types = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < types.length; i++) {
            Object item = parameterTypesAndCallback[i];
            if (item instanceof Class) {
                types[i] = (Class<?>) item;
            } else {
                throw new IllegalArgumentException("parameter type[" + i + "] is not Class");
            }
        }
        Class<?> type = findClass(className, loader);
        try {
            Method method = type.getDeclaredMethod(methodName, types);
            hookMember(method, (Hook) last);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(type.getName() + "#" + methodName
                    + Arrays.toString(types), e);
        }
    }

    static Object callMethod(Object instance, String name, Object... args) {
        if (instance == null) {
            return null;
        }
        Class<?> type = instance.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                if (!compatible(method.getParameterTypes(), args)) {
                    continue;
                }
                method.setAccessible(true);
                try {
                    return method.invoke(instance, args);
                } catch (Throwable t) {
                    throw new RuntimeException("callMethod " + name, t);
                }
            }
            type = type.getSuperclass();
        }
        throw new RuntimeException("no method " + instance.getClass().getName() + "#" + name);
    }

    static Object getObjectField(Object instance, String name) {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                throw new RuntimeException("getObjectField " + name, t);
            }
        }
        throw new RuntimeException("no field " + instance.getClass().getName() + "#" + name);
    }

    private static void hookMember(Executable member, Hook hook) {
        XposedModule current = module;
        if (current == null) {
            log("hook skipped, module unbound: " + member);
            return;
        }
        try {
            current.hook(member)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Param param = new Param();
                        param.thisObject = chain.getThisObject();
                        param.method = member;
                        List<?> raw = chain.getArgs();
                        param.args = raw == null ? new Object[0] : raw.toArray();
                        hook.beforeHookedMethod(param);
                        if (param.returnEarly) {
                            return param.result;
                        }
                        Object result = chain.proceed(param.args);
                        param.result = result;
                        hook.afterHookedMethod(param);
                        return param.returnEarly ? param.result : result;
                    });
        } catch (Throwable t) {
            log("hook failed " + member + " : " + t);
        }
    }

    private static boolean compatible(Class<?>[] types, Object[] args) {
        int argCount = args == null ? 0 : args.length;
        if (types.length != argCount) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (types[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> expect = box(types[i]);
            if (!expect.isAssignableFrom(arg.getClass()) && !isNumberFit(expect, arg)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumberFit(Class<?> expect, Object arg) {
        return Number.class.isAssignableFrom(expect) && arg instanceof Number;
    }

    private static Class<?> box(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    static abstract class Hook {
        protected void beforeHookedMethod(Param param) {
        }

        protected void afterHookedMethod(Param param) {
        }
    }

    static final class Param {
        Object thisObject;
        Object[] args;
        Member method;
        private Object result;
        private boolean returnEarly;

        void setResult(Object value) {
            result = value;
            returnEarly = true;
        }

        Object getResult() {
            return result;
        }
    }
}
