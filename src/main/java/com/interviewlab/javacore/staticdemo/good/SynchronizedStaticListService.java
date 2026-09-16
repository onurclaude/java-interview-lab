package com.interviewlab.javacore.staticdemo.good;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link com.interviewlab.javacore.staticdemo.bad.SharedMutableStaticListService}'in doğru
 * karşılığı: {@code static} paylaşımın kendisi hâlâ orada (bu, class-level state için bilinçli
 * bir tasarım kararı olabilir), ama alttaki yapı gerçekten thread-safe - {@link CopyOnWriteArrayList}
 * her yazmada iç dizinin bir kopyasını oluşturur, bu yüzden eşzamanlı okuma/yazmalar birbirini
 * asla bozmaz (read-heavy/write-light senaryolar için ideal - bkz. docs/collections-internals.md).
 */
public final class SynchronizedStaticListService {

    private static final List<String> users = new CopyOnWriteArrayList<>();

    public void addUser(String user) {
        users.add(user);
    }

    public static int userCount() {
        return users.size();
    }

    public static List<String> snapshot() {
        return Collections.unmodifiableList(users);
    }

    public static void clear() {
        users.clear();
    }
}
