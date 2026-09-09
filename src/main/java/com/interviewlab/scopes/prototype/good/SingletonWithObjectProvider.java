package com.interviewlab.scopes.prototype.good;

import com.interviewlab.scopes.prototype.PrototypeWorker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * {@link ObjectProvider} bir kez inject edilir (kendisi zaten kararlı, singleton'a uygun bir
 * handle'dır), ama {@link ObjectProvider#getObject()} çağrısı HER çağrıldığında container'dan
 * bir bean ister - bu da prototype scope'lu bir bean için her çağrıda gerçekten yeni bir
 * instance anlamına gelir. Bu,
 * {@link com.interviewlab.scopes.prototype.bad.SingletonWithDirectPrototypeInjection} için
 * açık (explicit) düzeltmedir.
 */
@Service
public class SingletonWithObjectProvider {

    private final ObjectProvider<PrototypeWorker> workerProvider;

    public SingletonWithObjectProvider(ObjectProvider<PrototypeWorker> workerProvider) {
        this.workerProvider = workerProvider;
    }

    public String getWorkerId() {
        return workerProvider.getObject().getId(); // her çağrıda taze bir PrototypeWorker
    }
}
