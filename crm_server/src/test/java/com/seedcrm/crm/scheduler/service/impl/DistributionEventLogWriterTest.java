package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.seedcrm.crm.scheduler.entity.IntegrationCallbackEventLog;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class DistributionEventLogWriterTest {

    @Test
    void shouldKeepSuccessLogInCurrentBusinessTransactionAndFailureLogIndependent() throws Exception {
        Method successWriter = DistributionEventLogWriter.class.getMethod("write", IntegrationCallbackEventLog.class);
        Method failureWriter = DistributionEventLogWriter.class.getMethod("writeRequiresNew", IntegrationCallbackEventLog.class);

        Transactional successTransaction = AnnotatedElementUtils.findMergedAnnotation(successWriter, Transactional.class);
        Transactional failureTransaction = AnnotatedElementUtils.findMergedAnnotation(failureWriter, Transactional.class);

        assertThat(successTransaction).isNotNull();
        assertThat(successTransaction.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(failureTransaction).isNotNull();
        assertThat(failureTransaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
