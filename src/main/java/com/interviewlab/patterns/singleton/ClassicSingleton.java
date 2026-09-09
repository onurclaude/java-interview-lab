package com.interviewlab.patterns.singleton;

/**
 * GoF Singleton: kendi yapısı gereği, JVM/classloader başına en fazla bir örneğin var
 * olacağını GARANTİ EDEN bir sınıf - bu garanti bir private constructor ve tek bir static
 * accessor ile sağlanır. Bir Spring {@code @Service} ile karşılaştırın: Spring'in
 * "singleton" scope'u "her {@code ApplicationContext} için bir örnek" anlamına gelir ve bu,
 * sınıfın kendisi tarafından değil CONTAINER tarafından sağlanır - doğrudan
 * {@code new MyService()} çağırsaydınız aynı sınıf birçok kez örneklenebilirdi, ya da aynı
 * JVM'de iki context oluşturduysanız (testlerin sık sık yaptığı gibi) context başına bir kez
 * örneklenebilirdi. Bu projenin başka her yerde kullandığı, container tarafından yönetilen
 * "singleton" versiyonu için docs/bean-scopes.md ve
 * {@link com.interviewlab.scopes.singleton.good.StatelessPriceService}'e bakın.
 *
 * <p>Burada {@code volatile} bir alan ile double-checked locking kullanılmasının nedeni,
 * sonraki her çağrıda bir senkronizasyon maliyeti ödemeden lazy initialization'ı thread-safe
 * hale getirmektir - bu idiom'un doğru olması için {@code instance} üzerinde neden
 * {@code volatile} gerektiğini görmek için docs/design-patterns.md'ye bakın (olmasaydı,
 * instruction reordering nedeniyle başka bir thread yarım kalmış inşa edilmiş bir nesneyi
 * gözlemleyebilirdi).
 */
public final class ClassicSingleton {

    private static volatile ClassicSingleton instance;

    private ClassicSingleton() {
    }

    public static ClassicSingleton getInstance() {
        if (instance == null) {
            synchronized (ClassicSingleton.class) {
                if (instance == null) {
                    instance = new ClassicSingleton();
                }
            }
        }
        return instance;
    }
}
