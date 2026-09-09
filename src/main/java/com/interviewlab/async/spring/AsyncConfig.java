package com.interviewlab.async.spring;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * NEDEN {@code @EnableAsync}'in varsayılanı yerine özel bir executor? Yapılandırılmadığında,
 * Spring'in varsayılan asenkron executor'ı, her bir çağrı için yepyeni bir thread oluşturan
 * ve bunları asla yeniden kullanmayan ya da sınırlamayan bir
 * {@link org.springframework.core.task.SimpleAsyncTaskExecutor}'dır - bu da
 * {@link com.interviewlab.concurrency.thread.bad.UncontrolledThreadCreationService}'teki
 * "kontrolsüz thread oluşturma" anti-pattern'inin, sadece bir annotation'ın arkasına gizlenmiş
 * halidir. Buradaki isimli bean ({@code @Async("labAsyncExecutor")} ile eşleştirilir), havuzu
 * açık ve sınırlı hale getirir.
 *
 * <p>{@link AsyncConfigurer#getAsyncUncaughtExceptionHandler()} önemlidir çünkü {@code void}
 * dönen bir {@code @Async} metodunun, çağıranın inceleyebileceği bir {@code Future}'ı yoktur -
 * bir istisna fırlatırsa, çağıranın kodunda yakalanabilecek hiçbir yer yoktur. Özel bir
 * handler olmadan, Spring'in varsayılanı bunu sadece loglar. Bu proje bunun yerine bunu
 * kaydeder, böylece bir test istisnanın gerçekten oluştuğunu kanıtlayabilir - bkz. docs/async.md.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final CopyOnWriteArrayList<Throwable> UNCAUGHT_ASYNC_EXCEPTIONS = new CopyOnWriteArrayList<>();

    @Bean(name = "labAsyncExecutor")
    public Executor labAsyncExecutor() {
        return new ThreadPoolExecutor(2, 4, 30, java.util.concurrent.TimeUnit.SECONDS, new ArrayBlockingQueue<>(50));
    }

    @Override
    public Executor getAsyncExecutor() {
        return labAsyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> UNCAUGHT_ASYNC_EXCEPTIONS.add(throwable);
    }

    public static java.util.List<Throwable> uncaughtAsyncExceptions() {
        return java.util.List.copyOf(UNCAUGHT_ASYNC_EXCEPTIONS);
    }

    public static void clearUncaughtAsyncExceptions() {
        UNCAUGHT_ASYNC_EXCEPTIONS.clear();
    }
}
