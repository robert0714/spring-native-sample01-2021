package org.hazelcast.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.TypeHint;

import com.hazelcast.core.HazelcastInstance;

@SpringBootApplication
@EnableR2dbcRepositories
@NativeHint(options = "--enable-url-protocols=http", 
types = @TypeHint(access = AccessBits.ALL, typeNames = {
		"springfox.documentation.spi.schema.ModelBuilderPlugin",
		"springfox.documentation.spi.schema.ModelPropertyBuilderPlugin",
		"springfox.documentation.spi.schema.SyntheticModelProviderPlugin",
		"springfox.documentation.spi.schema.TypeNameProviderPlugin",
		"springfox.documentation.spi.schema.ViewProviderPlugin",
		"springfox.documentation.spi.service.DocumentationPlugin",
		"springfox.documentation.spi.service.ApiListingBuilderPlugin",
		"springfox.documentation.spi.service.ApiListingScannerPlugin",
		"springfox.documentation.spi.service.DefaultsProviderPlugin",
		"springfox.documentation.spi.service.ExpandedParameterBuilderPlugin",
		"springfox.documentation.spi.service.ModelNamesRegistryFactoryPlugin",
		"springfox.documentation.spi.service.OperationBuilderPlugin",
		"springfox.documentation.spi.service.OperationModelsProviderPlugin",
		"springfox.documentation.spi.service.ParameterBuilderPlugin",
		"springfox.documentation.spi.service.ResponseBuilderPlugin", 
		"springfox.documentation.service.PathDecorator",
		"springfox.documentation.spi.DocumentationType" }) 
)
public class ReactiveToNativeApplication {
    @Bean
    public CachingService service(HazelcastInstance instance, PersonRepository repository) {
        return new CachingService(instance.getMap("persons"), repository);
    }
	public static void main(String[] args) {
		SpringApplication.run(ReactiveToNativeApplication.class, args);
	}

}
