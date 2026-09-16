package com.interviewlab.labrunner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** IntelliJ'de sağ tık -> Run/Debug. */
public final class CollectionsLabRunner {

    public static void main(String[] args) {
        LabRunnerPrint.banner("COLLECTIONS — ordering, duplicates, null handling farkları");

        Set<String> hashSet = new HashSet<>(List.of("banana", "apple", "cherry")); // BREAKPOINT 1: sırayı incele - garanti YOK
        Set<String> linkedHashSet = new LinkedHashSet<>(List.of("banana", "apple", "cherry")); // insertion order KORUNUR
        Set<String> treeSet = new TreeSet<>(List.of("banana", "apple", "cherry")); // BREAKPOINT 2: sıralı (natural order)

        LabRunnerPrint.fact("HashSet order (garanti yok)", hashSet);
        LabRunnerPrint.fact("LinkedHashSet order (insertion)", linkedHashSet);
        LabRunnerPrint.fact("TreeSet order (sorted)", treeSet);

        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1);
        stack.push(2);
        stack.push(3); // BREAKPOINT 3: ArrayDeque'i hem stack (push/pop, LIFO) hem queue (offer/poll, FIFO) olarak kullan
        LabRunnerPrint.fact("ArrayDeque as stack, pop()", stack.pop());

        List<String> arrayList = new ArrayList<>(List.of("a", "b", "c"));
        LabRunnerPrint.fact("ArrayList.get(1) - O(1)", arrayList.get(1));

        LabRunnerPrint.section("WHY");
        LabRunnerPrint.line("HashSet: en hızlı (O(1) amorti), AMA sıra garantisi YOK. LinkedHashSet: HashSet +");
        LabRunnerPrint.line("ekstra bir linked-list overhead'i ile insertion order korunur. TreeSet: bir");
        LabRunnerPrint.line("kırmızı-siyah ağaç - O(log n), her zaman SIRALI. ArrayDeque, hem Stack hem Queue");
        LabRunnerPrint.line("yerine geçebilir (Stack sınıfı LEGACY ve synchronized - ArrayDeque tercih edilir).");

        LabRunnerPrint.section("BREAKPOINT");
        LabRunnerPrint.line("CollectionsLabRunner.java:14-16 - üç set'i de Variables panelinde genişletip iç");
        LabRunnerPrint.line("veri yapılarını (table/map/tree) karşılaştır.");

        LabRunnerPrint.section("TRY");
        LabRunnerPrint.line("hashSet.add(null) dene (çalışır, HashSet null kabul eder) - sonra TreeSet için aynısını");
        LabRunnerPrint.line("dene (NullPointerException fırlatır - sıralama için null karşılaştırılamaz).");
    }
}
