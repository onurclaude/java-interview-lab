package com.interviewlab.javacore;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reflection'ın kendisi - framework'lerin (Spring dahil) annotation taramak, private
 * constructor'ları çağırmak, alan değerlerini okumak/yazmak için nasıl "sihir" yaptığının
 * temeli. Reflection metaspace İLE AYNI ŞEY DEĞİLDİR - metaspace class metadata'sının
 * BELLEKTE SAKLANDIĞI yerdir, reflection ise o metadata'yı RUNTIME'DA SORGULAMAK için
 * kullanılan bir API'dir (bkz. docs/NOTES_CORRECTIONS.md).
 */
class ReflectionTest {

    private static final class Sample {
        private final String name;

        private Sample(String name) {
            this.name = name;
        }

        private String greet() {
            return "hello, " + name;
        }
    }

    @Test
    void shouldListDeclaredFieldsViaReflection() {
        Field[] fields = Sample.class.getDeclaredFields();
        List<String> fieldNames = Arrays.stream(fields).map(Field::getName).toList();

        assertThat(fieldNames).containsExactly("name");
    }

    @Test
    void shouldListDeclaredMethodsViaReflection() {
        Method[] methods = Sample.class.getDeclaredMethods();
        List<String> methodNames = Arrays.stream(methods).map(Method::getName).toList();

        assertThat(methodNames).contains("greet");
    }

    @Test
    void shouldInvokePrivateMethodViaReflection() throws Exception {
        Sample sample = new Sample("Ada");
        Method greet = Sample.class.getDeclaredMethod("greet");
        greet.setAccessible(true); // private erişim denetimini bypass eder - framework'lerin yaptığı tam olarak bu

        String result = (String) greet.invoke(sample);

        assertThat(result).isEqualTo("hello, Ada");
    }

    @Test
    void shouldReadAndOverwritePrivateFieldViaReflection() throws Exception {
        Sample sample = new Sample("Ada");
        Field nameField = Sample.class.getDeclaredField("name");
        nameField.setAccessible(true);

        assertThat(nameField.get(sample)).isEqualTo("Ada");

        nameField.set(sample, "Grace");
        assertThat(sample.greet()).isEqualTo("hello, Grace");
    }
}
