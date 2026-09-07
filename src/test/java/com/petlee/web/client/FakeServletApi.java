package com.petlee.web.client;

import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Dynamic-proxy stand-ins for the two Servlet types {@link ApiClient}'s static helpers read.
 *
 * <p>The same technique as T-18's {@code TestRequest}, and for the same reason:
 * {@code HttpServletRequest} declares some seventy methods and a hand-written stub would be
 * seventy lines of noise around the four that matter here. Anything not stubbed answers
 * {@code null} or {@code 0}, which is what makes an unexpected call visible instead of plausible.
 */
final class FakeServletApi {

    private FakeServletApi() {
    }

    /**
     * A request that reports exactly what {@link ApiClient#baseUri} asks it for.
     */
    static HttpServletRequest request(String scheme, String host, int port, String contextPath) {
        Map<String, Object> answers = Map.of(
                "getScheme", scheme,
                "getServerName", host,
                "getServerPort", port,
                "getContextPath", contextPath);

        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (target, method, args) -> answers.containsKey(method.getName())
                        ? answers.get(method.getName())
                        : defaultValue(method.getReturnType()));
    }

    /**
     * A context whose {@code SessionCookieConfig} reports {@code cookieName}.
     *
     * @param cookieName the configured name, or {@code null} for a deployment that never set one —
     *                   which is what a real container reports for the default
     */
    static ServletContext context(String cookieName) {
        SessionCookieConfig config = (SessionCookieConfig) Proxy.newProxyInstance(
                SessionCookieConfig.class.getClassLoader(),
                new Class<?>[]{SessionCookieConfig.class},
                (target, method, args) -> "getName".equals(method.getName())
                        ? cookieName
                        : defaultValue(method.getReturnType()));

        return (ServletContext) Proxy.newProxyInstance(
                ServletContext.class.getClassLoader(),
                new Class<?>[]{ServletContext.class},
                (target, method, args) -> "getSessionCookieConfig".equals(method.getName())
                        ? config
                        : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
