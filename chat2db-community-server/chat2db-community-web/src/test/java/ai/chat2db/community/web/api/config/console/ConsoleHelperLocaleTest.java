package ai.chat2db.community.web.api.config.console;

import ai.chat2db.community.tools.console.ConsoleMessage;
import ai.chat2db.community.web.api.model.http.CookieUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsoleHelperLocaleTest {

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private void assertLocaleFor(String acceptLanguage, Locale expected) {
        ConsoleMessage message = new ConsoleMessage();
        Map<String, Object> headers = new HashMap<>();
        headers.put(CookieUtil.ACCEPT_LANGUAGE, acceptLanguage);
        message.setHeaders(headers);

        ConsoleHelper.setHeaders(message);

        assertEquals(expected, LocaleContextHolder.getLocale());
    }

    @Test
    void japaneseAcceptLanguageResolvesToJapanLocale() {
        assertLocaleFor("ja-JP,ja;q=0.9", Locale.JAPAN);
    }

    @Test
    void chineseAcceptLanguageResolvesToChinaLocale() {
        assertLocaleFor("zh-CN,zh;q=0.9", Locale.CHINA);
    }

    @Test
    void otherAcceptLanguageFallsBackToUsLocale() {
        assertLocaleFor("en-US,en;q=0.9", Locale.US);
    }
}
