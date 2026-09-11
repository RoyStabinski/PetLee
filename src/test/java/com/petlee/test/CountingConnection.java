package com.petlee.test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.function.Consumer;

/**
 * The proxy behind {@link CountingDriver}: every {@code prepareStatement}, {@code createStatement}
 * and {@code prepareCall} on the wrapped connection reports its SQL, then behaves exactly as the
 * real one.
 *
 * <p>A dynamic proxy rather than a hand-written delegate — {@link Connection} has some fifty
 * methods, and forty-seven of them would be identical one-liners nobody would ever read.
 */
final class CountingConnection implements InvocationHandler {

    private final Connection delegate;
    private final Consumer<String> onStatement;

    private CountingConnection(Connection delegate, Consumer<String> onStatement) {
        this.delegate = delegate;
        this.onStatement = onStatement;
    }

    static Connection wrap(Connection real, Consumer<String> onStatement) {
        return (Connection) Proxy.newProxyInstance(CountingConnection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, new CountingConnection(real, onStatement));
    }

    @Override
    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
        String name = method.getName();
        boolean createsStatement = "prepareStatement".equals(name)
                || "prepareCall".equals(name)
                || "createStatement".equals(name);

        if (createsStatement && args != null && args.length > 0 && args[0] instanceof String sql) {
            onStatement.accept(sql);
        }

        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException thrownByTheRealConnection) {
            // Unwrap, or every SQLException reaches the provider as an UndeclaredThrowableException
            // and the connection looks broken rather than the statement.
            throw thrownByTheRealConnection.getCause();
        }
    }
}
