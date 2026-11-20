package com.ribeira.shared.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

  /**
   * Decimals travel as strings ("3.500") so clients never parse them as floating point
   * (docs/synchronization.md section 2).
   */
  @Bean
  Jackson2ObjectMapperBuilderCustomizer decimalsAsStrings() {
    return builder -> builder.serializerByType(BigDecimal.class, new PlainDecimalSerializer());
  }

  static final class PlainDecimalSerializer extends JsonSerializer<BigDecimal> {
    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider)
        throws IOException {
      generator.writeString(value.toPlainString());
    }
  }
}
