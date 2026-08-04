package cn.finalscompass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Final Compass 后端的Spring Boot 启动入口，负责创建应用上下文并启动内嵌Web服务器。 
 * 包含3个主要功能：
 * 1.@Configuration：标记该类为配置类，Spring Boot会扫描该类并加载其中的Bean定义。
 * 2.@EnableAutoConfiguration：启用Spring Boot的自动配置机制，根据应用的类路径和配置文件自动配置Spring应用程序的各种组件。
 * 3.@ComponentScan：启用组件扫描，Spring Boot会扫描指定包及其子包中的组件（如@Controller、@Service、@Repository等），并将它们注册到 Spring 应用上下文中.
*/
@SpringBootApplication
public class FinalsCompassApplication {
    /**
     * JVM进程入口。
     * @param args 启动时传入Spring Boot应用的命令行参数
     */
    public static void main(String[] args) {

        //整个项目入口，java -jar finals-compass-backend.jar
        SpringApplication.run(FinalsCompassApplication.class, args);
    }
}

