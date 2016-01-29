package org.helianto.seed;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.helianto.core.config.HeliantoServiceConfig;
import org.helianto.install.service.DefaultInstallStrategy;
import org.helianto.install.service.EntityInstallStrategy;
import org.helianto.sendgrid.config.SendGridConfig;
import org.hibernate.ejb.HibernatePersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.mchange.v2.c3p0.ComboPooledDataSource;



/**
 * Basic Java configuration. 
 * 
 * @author mauriciofernandesdecastro
 */
@Configuration
@EnableWebMvc
@Import({HeliantoServiceConfig.class, SendGridConfig.class, OAuth2ClientConfig.class})
@ComponentScan(
	basePackages = {
		"org.helianto.*.controller"
})
@EnableJpaRepositories(
    basePackages={"org.helianto.*.repository"})
public abstract class AbstractRootContextConfig extends AbstractContextConfig {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractRootContextConfig.class);
	
	@Inject
	protected Environment env;
	
	/**
	 * Override to set packages to scan.
	 */
	protected String[] getPacakgesToScan() {
		return new String[] {"org.helianto.*.domain"};
	}
	
	/**
	 * Entity manager factory.
	 */
	@Bean 
	public EntityManagerFactory entityManagerFactory() {
		HibernateJpaVendorAdapter vendor = new HibernateJpaVendorAdapter();
		vendor.setGenerateDdl(env.getProperty("helianto.sql.generateDdl", Boolean.class, Boolean.TRUE));
		vendor.setDatabasePlatform(env.getProperty("helianto.jdbc.dialect", "org.hibernate.dialect.HSQLDialect"));
		DataSource dataSource = dataSource();
		logger.info("Creating entity manager from {}", dataSource);
		LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
		bean.setDataSource(dataSource);
		bean.setPackagesToScan(getPacakgesToScan());
		bean.setJpaVendorAdapter(vendor);
		bean.setPersistenceProvider(new HibernatePersistence());
		bean.afterPropertiesSet();
        return bean.getObject();
	}
	
	/**
	 * Data source.
	 */
	@Bean
	public DataSource dataSource() {
		return internalDataSource();
	}
	
	/**
	 * Data source extension point.
	 */
	protected DataSource internalDataSource() {
		try {
			ComboPooledDataSource ds = new ComboPooledDataSource();
			ds.setDriverClass(env.getProperty("helianto.jdbc.driverClassName", "org.hsqldb.jdbcDriver"));
			ds.setJdbcUrl(env.getProperty("helianto.jdbc.url", "jdbc:hsqldb:file:target/testdb/db2;shutdown=true"));
			ds.setUser(env.getProperty("helianto.jdbc.username", "sa"));
			ds.setPassword(env.getProperty("helianto.jdbc.password", ""));
			ds.setAcquireIncrement(5);
			ds.setIdleConnectionTestPeriod(60);
			ds.setMaxPoolSize(100);
			ds.setMaxStatements(50);
			ds.setMinPoolSize(10);
			return ds;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Bean
	public EntityInstallStrategy entityInstallStrategy() {
		return new DefaultInstallStrategy();
	}
	
	/**
	 * Required to allow matrix type params.
	 * @return
	 */
	@Bean
	public RequestMappingHandlerMapping requestMappingHandlerMapping() {
	    final RequestMappingHandlerMapping requestMappingHandlerMapping = new RequestMappingHandlerMapping();
	    requestMappingHandlerMapping.setRemoveSemicolonContent(false); 
	    return requestMappingHandlerMapping;
	}

}
