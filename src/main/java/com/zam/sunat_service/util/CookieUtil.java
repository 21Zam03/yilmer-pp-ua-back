package com.zam.sunat_service.util;

import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${parameters.cookie_is_secure}")
    private boolean COOKIE_IS_SECURE;

    @Value("${parameters.cookie_max_age}")
    private int COOKIE_MAX_AGE;

    @Value("${parameters.cookie_name}")
    private String COOKIE_NAME;

    @Value("${parameters.cookie_http_only}")
    private boolean COOKIE_HTTP_ONLY;

    @Value("${parameters.cookie_path}")
    private String COOKIE_PATH;

    public Cookie getCookie(String token) {
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setHttpOnly(COOKIE_HTTP_ONLY);
        cookie.setSecure(COOKIE_IS_SECURE);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(COOKIE_MAX_AGE);
        return cookie;
    }

    public Cookie getExpiredCookie() {
        Cookie cookie = new Cookie(COOKIE_NAME, null);
        cookie.setHttpOnly(COOKIE_HTTP_ONLY);
        cookie.setSecure(COOKIE_IS_SECURE);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(0);
        return cookie;
    }

    public String getCookieName() {
        return COOKIE_NAME;
    }

}
