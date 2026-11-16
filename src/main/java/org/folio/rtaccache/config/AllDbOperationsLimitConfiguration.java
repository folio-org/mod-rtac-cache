package org.folio.rtaccache.config;

import org.folio.rtaccache.repository.RtacHoldingBulkRepository;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.interceptor.ConcurrencyThrottleInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Option 3: one shared concurrency limit around every method of both repositories, using the same
 * interceptor that {@code @ConcurrencyLimit} uses.
 */
@Configuration
public class AllDbOperationsLimitConfiguration {

  @Bean
  static BeanPostProcessor allDbOperationsLimiter(Environment environment) {
    var interceptor = new ConcurrencyThrottleInterceptor();
    interceptor.setConcurrencyLimit(environment.getRequiredProperty("rtac.cache.db-max-concurrency", Integer.class));
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof RtacHoldingRepository || bean instanceof RtacHoldingBulkRepository) {
          var factory = new ProxyFactory(bean);
          factory.setProxyTargetClass(bean instanceof RtacHoldingBulkRepository);
          factory.addAdvice(interceptor);
          return factory.getProxy();
        }
        return bean;
      }
    };
  }
}
