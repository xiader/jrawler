package com.jrawler.adaptation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdaptationProperties.class)
public class AdaptationConfig {
}
