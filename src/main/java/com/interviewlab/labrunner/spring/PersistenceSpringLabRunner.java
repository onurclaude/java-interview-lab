package com.interviewlab.labrunner.spring;

import com.interviewlab.labrunner.LabRunnerPrint;
import com.interviewlab.persistence.bad.MutatingDetachedEntityService;
import com.interviewlab.persistence.entity.Customer;
import com.interviewlab.persistence.good.PersistenceLifecycleService;
import com.interviewlab.persistence.repository.CustomerRepository;

/** IntelliJ'de sağ tık -> Run/Debug. Postman/HTTP GEREKMEZ. `docker compose up -d` GEREKİR. */
public final class PersistenceSpringLabRunner {

    public static void main(String[] args) {
        SpringLabRunnerSupport.run(ctx -> {
            LabRunnerPrint.banner("PERSISTENCE CONTEXT / DIRTY CHECKING — gerçek Spring context");

            CustomerRepository customerRepository = ctx.getBean(CustomerRepository.class);
            MutatingDetachedEntityService bad = ctx.getBean(MutatingDetachedEntityService.class);
            PersistenceLifecycleService good = ctx.getBean(PersistenceLifecycleService.class);

            Long badId = bad.renameCustomerAssumingDirtyChecking("Ada", "ada@example.com", "Ada Lovelace"); // <- BREAKPOINT 1
            Customer badReloaded = customerRepository.findById(badId).orElseThrow();
            LabRunnerPrint.fact("BAD nameInDatabase (hâlâ Ada olmalı)", badReloaded.getName());
            LabRunnerPrint.fact("BAD changeWasPersisted", badReloaded.getName().equals("Ada Lovelace"));

            Customer saved = customerRepository.save(new Customer("Linus", "linus@example.com"));
            good.renameViaDirtyCheckingOnly(saved.getId(), "Linus Torvalds"); // <- BREAKPOINT 2: explicit save() YOK, commit'te flush UPDATE üretir
            Customer goodReloaded = customerRepository.findById(saved.getId()).orElseThrow();
            LabRunnerPrint.fact("GOOD nameInDatabase (Linus Torvalds olmalı)", goodReloaded.getName());
            LabRunnerPrint.fact("GOOD changeWasPersisted", goodReloaded.getName().equals("Linus Torvalds"));

            LabRunnerPrint.section("WHY");
            LabRunnerPrint.line("save() dönünce entity DETACHED olur - dirty checking'in izleyeceği bir persistence");
            LabRunnerPrint.line("context yoktur, BAD'deki changeName() sessizce kaybolur. GOOD'da managed entity +");
            LabRunnerPrint.line("tek @Transactional sınır = explicit save() olmadan dirty checking yeterli.");
        });
    }
}
