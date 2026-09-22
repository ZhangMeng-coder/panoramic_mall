package com.panoramic.trade.order.application;

import com.panoramic.trade.order.infrastructure.OrderDomainConfiguration;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seata 落点哨兵：{@code @GlobalTransactional} 必须挂在**组件扫描得到的类**上，不能挂在
 * {@code @Bean} 方法产出的 bean 上。
 *
 * <p>⚠ 守的是什么：Seata 的 {@code GlobalTransactionScanner} 只按
 * {@code BeanDefinition.getBeanClassName()} 挑要增强的 bean，**取不到类名就直接跳过**（不报错、不告警、
 * 事务根本不开）；而 {@code @Bean} 方法产出的 bean 该方法**恒为空**（类名不会回退成工厂方法的返回类型）。
 * 于是「把注解挪到编排器方法上」这种看着无害的重构会让全局事务**静默失效**——2026-09-22 复评时
 * T12 的首次落点正是这个形状（挂在装配类产出的 {@code OrderCreateCoordinator#create} 上）。
 * 这类洞编译不报、启动不报、只有真出事时才暴露，故用静态断言钉死。</p>
 *
 * <p>⚠ **不启 Spring**：只借 Spring 的类路径扫描工具（{@code ClassPathScanningCandidateComponentProvider}）
 * 拿「谁会被扫成组件」，且类一律用 {@code Class.forName(name, false, loader)} **不初始化**地加载
 * ——不起容器就不会去连 Nacos，也不会让 Seata 的 scanner 去够 TC（{@code 127.0.0.1:8091}）。</p>
 *
 * <p>⚠ 已知漏网形状（本哨兵**不替代**人工核对）：同一个类既是扫描到的组件、又另有一个同类型的
 * {@code @Bean} 产出——那种组合下两个断言都会通过，而真正生效的 bean 未必是扫描出来的那个。
 * 本仓没有这种写法（装配类产出的都是不标 {@code @Component} 的类），出现时要靠人发现。</p>
 */
class GlobalTransactionalPlacementTest {

    /** 扫描基点：只扫本域自己的包（生产启动类扫的是 {@code com.panoramic}，此处按域收窄） */
    private static final String BASE_PACKAGE = "com.panoramic.trade";

    /** 全局事务的真落点（用例入口，组件扫描得到的 {@code @Service}） */
    private static final String ENTRY_POINT_METHOD = "create";

    @Test
    @DisplayName("下单用例入口带 @GlobalTransactional，且它的载体确实是「会被扫成组件」的类")
    void entryPointCarriesGlobalTransactional() {
        Set<String> scanned = scannedClassNames(Component.class);

        // 扫描本身必须有效：否则下面两条断言会因为集合为空而变成「永真」，哨兵静默失效
        assertThat(scanned).as("组件扫描一个类都没扫到，本哨兵已失效，先修扫描").isNotEmpty();

        // Seata 看的是 BeanDefinition#getBeanClassName()：载体不在扫描结果里，注解就永远看不到
        assertThat(scanned)
                .as("用例入口必须是组件扫描得到的类（否则 Seata 取不到它的类名）")
                .contains(OrderApplicationService.class.getName());
        assertThat(annotatedMethodNames(OrderApplicationService.class))
                .as("下单用例入口必须带 @GlobalTransactional —— 全局事务的真落点在 %s#%s",
                        OrderApplicationService.class.getSimpleName(), ENTRY_POINT_METHOD)
                .contains(ENTRY_POINT_METHOD);
    }

    @Test
    @DisplayName("@Bean 方法产出的 bean：其返回类型（含父类型 / 接口）不得带 @GlobalTransactional")
    void noGlobalTransactionalOnBeanProducedTypes() {
        Set<String> configClasses = scannedClassNames(Configuration.class);
        // 扫描本身必须有效：一个装配类都没扫到时下面的循环「零次即通过」，哨兵会静默失效
        assertThat(configClasses).as("没扫到任何 @Configuration，本哨兵已失效，先修扫描")
                .contains(OrderDomainConfiguration.class.getName());

        Set<String> offenders = new TreeSet<>();
        for (String configName : configClasses) {
            Class<?> configClass = load(configName);
            for (Method factory : configClass.getDeclaredMethods()) {
                if (!factory.isAnnotationPresent(Bean.class)) {
                    continue;
                }
                // beanClassName 恒空 ⇒ 该 bean 上（及其类型链上）的 @GlobalTransactional 一律是空挂
                for (Class<?> candidate : selfAndSupertypes(factory.getReturnType())) {
                    if (!annotatedMethodNames(candidate).isEmpty()) {
                        offenders.add(configClass.getSimpleName() + "#" + factory.getName()
                                + "() → " + candidate.getName());
                    }
                }
            }
        }
        assertThat(offenders)
                .as("这些 bean 由 @Bean 方法产出（BeanDefinition#getBeanClassName() 恒为空）→ "
                        + "Seata 的 GlobalTransactionScanner 会跳过它们、注解形同不存在。"
                        + "把注解移到组件扫描得到的类上（下单路径 = OrderApplicationService#create）")
                .isEmpty();
    }

    // ── 内部：扫描与反射 ────────────────────────────────────────────────────────

    /**
     * 会被扫成组件的类名（只看注解，不看 {@code @Conditional*}——条件装配的取舍与本哨兵无关）
     */
    private static Set<String> scannedClassNames(Class<? extends Annotation> marker) {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        // 元注解生效：@Service / @Configuration 都是 @Component 的元注解，故这一个过滤器够用
        provider.addIncludeFilter(new AnnotationTypeFilter(marker));
        Set<String> names = new LinkedHashSet<>();
        for (BeanDefinition definition : provider.findCandidateComponents(BASE_PACKAGE)) {
            names.add(definition.getBeanClassName());
        }
        return names;
    }

    /** 不初始化地加载（{@code initialize=false}：不跑静态块、不触碰容器与 TC） */
    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, GlobalTransactionalPlacementTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("扫描到的类加载不了：" + className, e);
        }
    }

    /**
     * 该类**自己声明**的、带 {@code @GlobalTransactional} 的方法名
     *
     * <p>刻意用 {@code getDeclaredMethods} 而不是 Seata 自己用的 {@code getMethods}：
     * 非 public 方法上的注解 Seata 同样看不到，那正是要抓的「空挂」，不该被过滤掉。</p>
     */
    private static List<String> annotatedMethodNames(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GlobalTransactional.class)) {
                names.add(method.getName());
            }
        }
        return names;
    }

    /** 自身 + 全部父类与接口（Seata 的注解判定也会沿接口链查一遍） */
    private static Set<Class<?>> selfAndSupertypes(Class<?> clazz) {
        Set<Class<?>> all = new LinkedHashSet<>();
        collect(clazz, all);
        return all;
    }

    private static void collect(Class<?> clazz, Set<Class<?>> sink) {
        if (clazz == null || !sink.add(clazz)) {
            return;
        }
        collect(clazz.getSuperclass(), sink);
        for (Class<?> itf : clazz.getInterfaces()) {
            collect(itf, sink);
        }
    }
}
