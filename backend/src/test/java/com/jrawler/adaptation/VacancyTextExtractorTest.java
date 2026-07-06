package com.jrawler.adaptation;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VacancyTextExtractorTest {

    @Test
    void stripsScriptsStylesAndChrome() {
        String html = """
                <html><head><style>body{color:red}</style></head>
                <body>
                <nav>Home | Jobs | About</nav>
                <script>console.log('tracking')</script>
                <h1>Senior Java Developer</h1>
                <p>We need Spring Boot and Kafka experience.</p>
                <footer>© 2026 Acme</footer>
                </body></html>
                """;

        String text = VacancyTextExtractor.extractText(Jsoup.parse(html));

        assertThat(text).contains("Senior Java Developer");
        assertThat(text).contains("Spring Boot and Kafka");
        assertThat(text).doesNotContain("console.log");
        assertThat(text).doesNotContain("color:red");
        assertThat(text).doesNotContain("Home | Jobs");
        assertThat(text).doesNotContain("© 2026");
    }

    @Test
    void returnsEmptyForBodylessDocument() {
        String text = VacancyTextExtractor.extractText(Jsoup.parse(""));
        assertThat(text).isEmpty();
    }
}
