package org.folio.rtaccache.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/** Turns on Spring's resilience annotations, such as {@code @ConcurrencyLimit}. */
@Configuration
@EnableResilientMethods
public class ResilienceConfiguration {
}
