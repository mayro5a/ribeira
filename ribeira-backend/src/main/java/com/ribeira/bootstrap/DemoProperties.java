package com.ribeira.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Demo data switch; the password comes only from the environment (RIBEIRA_DEMO_PASSWORD). */
@ConfigurationProperties("ribeira.demo")
public record DemoProperties(boolean enabled, String password) {}
