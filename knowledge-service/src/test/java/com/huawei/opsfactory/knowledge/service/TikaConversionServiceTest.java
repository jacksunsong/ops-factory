package com.huawei.opsfactory.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TikaConversionServiceTest {

    private final TikaConversionService service = new TikaConversionService();

    @Test
    void shouldConvertHtmlInputIntoMarkdownInsteadOfReturningRawHtml() {
        Path htmlFile = Path.of("src/test/resources/inputFiles/SLA_Violation_Analysis_Report_CN.html").toAbsolutePath().normalize();

        TikaConversionService.ConversionResult result = service.convert(htmlFile);

        assertThat(result.contentType()).startsWith("text/html");
        assertThat(result.markdown())
            .contains("# SLA违约归因分析报告")
            .contains("## 执行摘要")
            .doesNotContain("<html")
            .doesNotContain("<style")
            .doesNotContain("<body");
    }
}
