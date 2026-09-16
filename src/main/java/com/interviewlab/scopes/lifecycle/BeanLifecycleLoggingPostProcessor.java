package com.interviewlab.scopes.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * {@link BeanPostProcessor}, tam olarak {@link BeanLifecycleDemoBean} için container'ın kendi
 * initialization callback'lerinin (ör. {@code @PostConstruct}) ÖNCESİNDE ve SONRASINDA
 * çalışır - Spring'in altyapısının, container'ın kendi lifecycle annotation'larını işlemek
 * için BİLE bir {@code BeanPostProcessor} kullandığını gösterir
 * ({@code CommonAnnotationBeanPostProcessor}, {@code @PostConstruct}'ı tam olarak bu şekilde
 * işler).
 */
@Component
public class BeanLifecycleLoggingPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BeanLifecycleDemoBean) {
            BeanLifecycleDemoBean.recordBeanPostProcessorEvent("2_BEAN_POST_PROCESSOR_BEFORE_INIT");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BeanLifecycleDemoBean) {
            BeanLifecycleDemoBean.recordBeanPostProcessorEvent("3_5_BEAN_POST_PROCESSOR_AFTER_INIT");
        }
        return bean;
    }
}
