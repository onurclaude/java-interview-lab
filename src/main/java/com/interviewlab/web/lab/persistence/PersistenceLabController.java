package com.interviewlab.web.lab.persistence;

import com.interviewlab.common.sql.SqlStatementRecorder;
import com.interviewlab.persistence.bad.MutatingDetachedEntityService;
import com.interviewlab.persistence.entity.Customer;
import com.interviewlab.persistence.good.PersistenceLifecycleService;
import com.interviewlab.persistence.repository.CustomerRepository;
import com.interviewlab.web.lab.LabLog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/INTERACTIVE_LABS.md — LAB 01. Persistence context / dirty checking'i sadece kod
 * okuyarak değil, gerçek Postgres'e karşı bir REST çağrısıyla tetikleyip DBeaver'dan
 * doğrulayarak öğrenmek için.
 */
@RestController
@RequestMapping("/api/labs/persistence")
public class PersistenceLabController {

    private final CustomerRepository customerRepository;
    private final MutatingDetachedEntityService mutatingDetachedEntityService;
    private final PersistenceLifecycleService persistenceLifecycleService;
    private final AtomicReference<Long> currentCustomerId = new AtomicReference<>();

    public PersistenceLabController(CustomerRepository customerRepository,
                                     MutatingDetachedEntityService mutatingDetachedEntityService,
                                     PersistenceLifecycleService persistenceLifecycleService) {
        this.customerRepository = customerRepository;
        this.mutatingDetachedEntityService = mutatingDetachedEntityService;
        this.persistenceLifecycleService = persistenceLifecycleService;
    }

    @PostMapping("/reset")
    @Transactional
    public Map<String, Object> reset() {
        customerRepository.deleteAll();
        SqlStatementRecorder.clear();
        currentCustomerId.set(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PERSISTENCE_CONTEXT");
        body.put("action", "RESET");
        body.put("customersRemaining", customerRepository.count());
        body.put("nextStep", "POST /api/labs/persistence/bad");
        return body;
    }

    @PostMapping("/bad")
    public Map<String, Object> bad() {
        LabLog.banner("PERSISTENCE CONTEXT", "BAD");
        LabLog.line("save() sonrası entity DETACHED olacak, sonraki mutasyon hiçbir zaman flush edilmeyecek.");

        Long id = mutatingDetachedEntityService.renameCustomerAssumingDirtyChecking("Ada", "ada@example.com", "Ada Lovelace");
        currentCustomerId.set(id);
        Customer reloaded = customerRepository.findById(id).orElseThrow();

        LabLog.line("DB'den yeniden okundu: name={}", reloaded.getName());
        LabLog.lesson("save() sonrası entity DETACHED'dır - dirty checking'in izleyeceği bir persistence context "
                + "yoktur, bu yüzden changeName() çağrısı sessizce kaybolur.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PERSISTENCE_CONTEXT");
        body.put("mode", "BAD");
        body.put("customerId", id);
        body.put("originalName", "Ada");
        body.put("attemptedNewName", "Ada Lovelace");
        body.put("nameInDatabase", reloaded.getName());
        body.put("changeWasPersisted", reloaded.getName().equals("Ada Lovelace"));
        body.put("problem", "save() dönünce entity DETACHED olur; sonraki changeName() çağrısı hiçbir SQL üretmez, "
                + "isim değişikliği sessizce kaybolur.");
        body.put("nextStep", "SELECT * FROM lab_customer; ile DBeaver'da doğrula, sonra POST /api/labs/persistence/good");
        return body;
    }

    @PostMapping("/good")
    public Map<String, Object> good() {
        LabLog.banner("PERSISTENCE CONTEXT", "GOOD");

        Customer saved = customerRepository.save(new Customer("Linus", "linus@example.com"));
        SqlStatementRecorder.clear();
        currentCustomerId.set(saved.getId());
        LabLog.line("Customer id={} kaydedildi, SQL kaydı temizlendi - şimdi tek bir @Transactional içinde rename ediyoruz.", saved.getId());

        persistenceLifecycleService.renameViaDirtyCheckingOnly(saved.getId(), "Linus Torvalds");
        Customer reloaded = customerRepository.findById(saved.getId()).orElseThrow();

        List<String> updates = SqlStatementRecorder.statementsForCurrentThread().stream()
                .filter(sql -> sql.toLowerCase().contains("update lab_customer"))
                .toList();
        LabLog.line("Kaydedilen UPDATE ifadeleri: {}", updates);
        LabLog.lesson("Aynı @Transactional metodu içinde kalan managed bir entity için, explicit save() hiç "
                + "gerekmez - commit anındaki flush, alan değişikliğini snapshot ile karşılaştırıp UPDATE üretir.");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PERSISTENCE_CONTEXT");
        body.put("mode", "GOOD");
        body.put("customerId", saved.getId());
        body.put("nameInDatabase", reloaded.getName());
        body.put("changeWasPersisted", reloaded.getName().equals("Linus Torvalds"));
        body.put("updateStatementsIssued", updates);
        body.put("lesson", "Managed entity + tek @Transactional sınır = explicit save() olmadan dirty checking yeterli.");
        return body;
    }

    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lab", "PERSISTENCE_CONTEXT");
        body.put("customers", customerRepository.findAll());
        body.put("currentCustomerId", currentCustomerId.get());
        // allStatements() kullanılıyor, statementsForCurrentThread() değil: bu endpoint ayrı
        // bir HTTP isteğinde çağrılıyor, muhtemelen bad()/good()'u çalıştıran thread'den farklı
        // bir Tomcat worker thread'inde - thread'e göre gruplama burada işe yaramaz.
        body.put("lastRecordedSql", SqlStatementRecorder.allStatements());
        body.put("dbeaverQuery", "SELECT * FROM lab_customer;");
        return body;
    }
}
