package com.interviewlab.transaction.rollback.bad;

import com.interviewlab.transaction.entity.Account;
import com.interviewlab.transaction.entity.AccountRepository;
import com.interviewlab.transaction.rollback.SimulatedCheckedFailureException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * NE YANLIŞ?
 * Bir hesaptan debit yapan ve ardından başarısızlığı bildirmek için <b>checked</b> bir
 * exception fırlatan bir metotta düz {@code @Transactional} kullanımı ({@code rollbackFor}
 * olmadan).
 *
 * <p>NEDEN YANLIŞ?
 * Spring'in varsayılan rollback kuralı (EJB kurallarından miras alınmıştır) şudur: unchecked
 * exception'larda ({@link RuntimeException}, {@link Error}) rollback yap, ama checked
 * exception'larda <b>commit</b> et. "Metot bir exception fırlattı" diyip "demek ki rollback
 * oldu" diye düşünen bir geliştirici, {@code rollbackFor} aksini söylemediği sürece her
 * checked exception için yanılır.
 *
 * <p>PRODUCTION'DA NE OLABİLİR?
 * Hesaptan para çıkar, başarısızlığı çağırana bildirmek için checked exception fırlatılır,
 * çağıran "transaction başarısız oldu" diye loglar ve devam eder - ama debit zaten commit
 * edilmiştir. Bu, gerçek, sessiz ve finansal açıdan önemli bir hata sınıfıdır.
 *
 * <p>NASIL REPRODUCE EDİLİR?
 * {@code RollbackBehaviorTest.shouldCommitDespiteCheckedExceptionByDefault()}'a bakın: bu
 * metodu rollback bekleyerek çağırın, checked exception'ı yakalayın, hesabı yeniden yükleyin
 * ve bakiyenin hâlâ debit edilmiş olduğunu gözlemleyin.
 *
 * <p>NASIL DÜZELTİLİR?
 * {@code @Transactional(rollbackFor = Exception.class)} kullanın (bkz.
 * {@link com.interviewlab.transaction.rollback.good.RollbackForCheckedExceptionService}),
 * veya bu tür başarısızlık sinyalleri için checked exception'ları hiç kullanmayın - bunun
 * yerine unchecked bir {@code BusinessException} hiyerarşisi tercih edin (bkz.
 * docs/exceptions.md).
 */
@Service
public class CheckedExceptionNoRollbackService {

    private final AccountRepository accountRepository;

    public CheckedExceptionNoRollbackService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void debitThenFailWithCheckedException(Long accountId, BigDecimal amount) throws SimulatedCheckedFailureException {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.debit(amount);
        // Dirty checking bu debit'i commit anında flush edecek - ve Spring yine de commit
        // edecek, çünkü checked bir exception varsayılan olarak rollback tetiklemez.
        throw new SimulatedCheckedFailureException("Downstream validation failed");
    }
}
