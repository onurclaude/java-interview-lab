package com.interviewlab.locking.deadlock;

import com.interviewlab.locking.entity.Product;
import com.interviewlab.locking.entity.LockingProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link InconsistentLockOrderTransferService}'in doğru karşılığı: çağıranın hangisini
 * "from", hangisini "to" olarak adlandırdığından bağımsız olarak her zaman önce daha küçük
 * id'yi kilitler - zıt yönlerdeki iki transfer artık bir döngü oluşturmak yerine basitçe
 * aynı ilk kilit için sıraya girer.
 */
@Service
public class DeterministicOrderTransferService {

    private final LockingProductRepository productRepository;

    public DeterministicOrderTransferService(LockingProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void transferStock(Long fromId, Long toId, int amount) {
        Long firstId = fromId < toId ? fromId : toId;
        Long secondId = fromId < toId ? toId : fromId;

        Product first = productRepository.findByIdForUpdate(firstId).orElseThrow();
        Product second = productRepository.findByIdForUpdate(secondId).orElseThrow();

        Product from = firstId.equals(fromId) ? first : second;
        Product to = firstId.equals(fromId) ? second : first;
        from.decreaseStock(amount);
        to.setStock(to.getStock() + amount);
    }
}
