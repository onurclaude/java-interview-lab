package com.interviewlab.persistence.good;

import com.interviewlab.persistence.entity.Customer;
import com.interviewlab.persistence.repository.CustomerRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link com.interviewlab.persistence.bad.SaveAndFlushInLoopService}'in doğru karşılığı:
 * her şeyi persist et, bir kez flush et (örtük olarak, commit anında).
 */
@Service
public class BatchPersistenceService {

    private final CustomerRepository customerRepository;

    public BatchPersistenceService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public void saveAllInOneFlush(List<Customer> customers) {
        customerRepository.saveAll(customers);
        // flush() çağrısı yok: commit anında otomatik olarak gerçekleşen tek flush,
        // bu listedeki her entity'yi halleder.
    }
}
