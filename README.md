# GraalVM AOT compilation

For Spring applications, that’s a big issue. The framework does a lot of work at runtime e.g., classpath scanning and reflection.

The usual way to cope with this limitation is to record all interactions with the application running on a JVM via a Java agent provided by Graal VM. At the end of the run, the agent dumps out all recorded interactions into dedicated configuration files:

*  Reflective access
*  Serialized classes
*  Proxied interfaces
*  Resources and resource bundles
*  JNI

This option is compelling and allows one to create native images out of nearly every possible Java application. It comes with a couple of downsides, though, as you need:

1. A complete GraalVM distribution that provides the agent  
2. A test suite that tests every nook and cranny of the application   
3. A process that executes the suite and creates the configuration files with every new release   

None of these items are complicated, but the process is time-consuming and error-prone. It can be automated, but there’s always a risk that a specific release forgets to test a particular use-case and crashes when deployed.

### Spring Native
##### reference
*  https://www.baeldung.com/spring-native-intro   [sample code](https://github.com/eugenp/tutorials/tree/master/spring-native)
*  https://blog.frankel.ch/kick-spring-native-tires/   [sample code](https://github.com/hazelcast-demos/imperative-to-reactive)
*  https://iter01.com/592519.html  [sample code](https://github.com/hazelcast-demos/imperative-to-reactive)
*  https://2much2learn.com/crud-reactive-rest-api-using-spring-boot-r2dbc-graalvm-native/   [sample code](https://github.com/2much2learn/article-sep13-native-reactive-crud-rest-api-using-spring-boot-spring-r2dbc)

#### Build and Run Image
Using Docker Machine.
```bash
$ docker-machine  create lab01   --virtualbox-cpu-count "2"      --virtualbox-memory "9096"    -d "virtualbox"
$ eval $(docker-machine env lab01)
```

That's it! we're ready to build a native image of our Spring Boot project by using the Maven command:  

```bash
$ mvn  clean spring-boot:build-image  -Dmaven.test.skip=true
(ommit... )
[INFO]     [creator]     Adding 1/1 app layer(s)
[INFO]     [creator]     Adding layer 'launcher'
[INFO]     [creator]     Adding layer 'config'
[INFO]     [creator]     Adding layer 'process-types'
[INFO]     [creator]     Adding label 'io.buildpacks.lifecycle.metadata'
[INFO]     [creator]     Adding label 'io.buildpacks.build.metadata'
[INFO]     [creator]     Adding label 'io.buildpacks.project.metadata'
[INFO]     [creator]     Adding label 'org.opencontainers.image.title'
[INFO]     [creator]     Adding label 'org.opencontainers.image.version'
[INFO]     [creator]     Adding label 'org.springframework.boot.version'
[INFO]     [creator]     Setting default process type 'web'
[INFO]     [creator]     Saving docker.io/library/reactive-to-native:0.0.1-SNAPSHOT...
[INFO]     [creator]     *** Images (6611504b9018):
[INFO]     [creator]           docker.io/library/reactive-to-native:0.0.1-SNAPSHOT
[INFO]     [creator]     Adding cache layer 'paketo-buildpacks/graalvm:jdk'
[INFO]     [creator]     Adding cache layer 'paketo-buildpacks/native-image:native-image'
[INFO]
[INFO] Successfully built image 'docker.io/library/reactive-to-native:0.0.1-SNAPSHOT'
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  13:19 min
[INFO] Finished at: 2021-10-09T23:39:47+08:00
[INFO] ------------------------------------------------------------------------

```
The Maven command should create a native image of our Spring Boot App with the name reactive-to-native:0.0.1-SNAPSHOT .

```bash
$ docker images
REPOSITORY                 TAG                 IMAGE ID            CREATED             SIZE
paketobuildpacks/run       tiny-cnb            93a4456476e6        2 days ago          17.4MB
pdc-check                  1.0.2-SNAPSHOT      81db5f81709f        41 years ago        197MB
reactive-to-native         0.0.1-SNAPSHOT      6611504b9018        41 years ago        146MB
paketobuildpacks/builder   tiny                125cab231bb0        41 years ago        471MB
```

Last, we can run the image of our app on Docker using the docker run command:

```bash
$ docker run --rm -p 8080:8080 reactive-to-native:0.0.1-SNAPSHOT
```
##### The first hurdles
Unfortunately, this fails with the following exception:

```bash
Caused by: java.lang.ClassNotFoundException: org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryConfigurations$PooledConnectionFactoryCondition
    at com.oracle.svm.core.hub.ClassForNameSupport.forName(ClassForNameSupport.java:60) ~[na:na]
    at java.lang.Class.forName(DynamicHub.java:1260) ~[na:na]
    at org.springframework.util.ClassUtils.forName(ClassUtils.java:284) ~[na:na]
    at org.springframework.util.ClassUtils.resolveClassName(ClassUtils.java:324) ~[na:na]
    ... 28 common frames omitted
```
It seems that Spring Native missed this one. We need to add it ourselves. There are two ways to do that:  
	1. Either via annotations from the Spring Native dependency   
	2. Or via standard GraalVM config files
	
In the above section, I chose to set Spring Native in a dedicated Maven profile. For that reason, let’s use regular configuration files.

META-INF/native-image/org.hazelcast.cache/imperative-to-reactive/reflect-config.json

```json
[
{
  "name":"org.springframework.boot.autoconfigure.r2dbc.ConnectionFactoryConfigurations$PooledConnectionFactoryCondition",
  "methods":[{"name":"<init>","parameterTypes":[] }]
}
]
```
Building and running again yields the following:

```bash
Caused by: java.lang.NoSuchFieldException: VERSION
	at java.lang.Class.getField(DynamicHub.java:1078) ~[na:na]
	at com.hazelcast.instance.BuildInfoProvider.readStaticStringField(BuildInfoProvider.java:139) ~[na:na]
	... 79 common frames omitted
```
This time, a Hazelcast-related static field is missing. We need to configure the missing field, re-build and re-run again. It still fails. 

Because I configure Hazelcast with XML, the whole XML initialization process is needed. At some point, we also need to keep a resource bundle in the native image:

META-INF/native-image/org.hazelcast.cache/imperative-to-reactive/resource-config.json

```json
{
"bundles":[
  {"name":"com.sun.org.apache.xml.internal.serializer.XMLEntities"}
]
}
```

Unfortunately, the build continues to fail. It’s still an XML-related exception **though we configured the class correctly!**

```bash
Caused by: java.lang.RuntimeException: internal error
    at com.sun.org.apache.xerces.internal.impl.dv.xs.XSSimpleTypeDecl.applyFacets1(XSSimpleTypeDecl.java:754) ~[na:na]
    at com.sun.org.apache.xerces.internal.impl.dv.xs.BaseSchemaDVFactory.createBuiltInTypes(BaseSchemaDVFactory.java:207) ~[na:na]
    at com.sun.org.apache.xerces.internal.impl.dv.xs.SchemaDVFactoryImpl.createBuiltInTypes(SchemaDVFactoryImpl.java:47) ~[org.hazelcast.cache.ImperativeToReactiveApplicationKt:na]
    at com.sun.org.apache.xerces.internal.impl.dv.xs.SchemaDVFactoryImpl.<clinit>(SchemaDVFactoryImpl.java:42) ~[org.hazelcast.cache.ImperativeToReactiveApplicationKt:na]
    at com.oracle.svm.core.classinitialization.ClassInitializationInfo.invokeClassInitializer(ClassInitializationInfo.java:375) ~[na:na]
    at com.oracle.svm.core.classinitialization.ClassInitializationInfo.initialize(ClassInitializationInfo.java:295) ~[na:na]
    ... 82 common frames omitted
```
##### Switching to YAML
XML is a huge beast, and I’m not expert enough to understand the exact reason behind the above exception. Engineering is also about finding the right workaround. In this case, I decided to switch from XML configuration to YAML configuration. It’s simple anyway:


hazelcast.yaml

```yaml
hazelcast:
  instance-name: hazelcastInstance
```
We shouldn’t forget to add the above resource into the resource configuration file:

META-INF/native-image/org.hazelcast.cache/imperative-to-reactive/resource-config.json

```json
{
"resources":{
  "includes":[
    {"pattern":"hazelcast.yaml"}
  ]}
}
```
Because of missing charsets at runtime, we also need to initialize the YAML reader at build time:

native-image.properties

```properties
Args = --initialize-at-build-time=com.hazelcast.org.snakeyaml.engine.v2.api.YamlUnicodeReader
```
We need to continue adding a couple of reflectively-accesses classes, all related to Hazelcast.


##### Missing proxies
At this point, we hit a brand new exception at runtime!

```bash
Caused by: com.oracle.svm.core.jdk.UnsupportedFeatureError: Proxy class defined by interfaces [interface org.hazelcast.cache.PersonRepository, interface org.springframework.data.repository.Repository, interface org.springframework.transaction.interceptor.TransactionalProxy, interface org.springframework.aop.framework.Advised, interface org.springframework.core.DecoratingProxy] not found. Generating proxy classes at runtime is not supported. Proxy classes need to be defined at image build time by specifying the list of interfaces that they implement. To define proxy classes use -H:DynamicProxyConfigurationFiles=<comma-separated-config-files> and -H:DynamicProxyConfigurationResources=<comma-separated-config-resources> options.
    at com.oracle.svm.core.util.VMError.unsupportedFeature(VMError.java:87) ~[na:na]
    at com.oracle.svm.reflect.proxy.DynamicProxySupport.getProxyClass(DynamicProxySupport.java:113) ~[na:na]
    at java.lang.reflect.Proxy.getProxyConstructor(Proxy.java:66) ~[na:na]
    at java.lang.reflect.Proxy.newProxyInstance(Proxy.java:1006) ~[na:na]
    at org.springframework.aop.framework.JdkDynamicAopProxy.getProxy(JdkDynamicAopProxy.java:126) ~[na:na]
    at org.springframework.aop.framework.ProxyFactory.getProxy(ProxyFactory.java:110) ~[na:na]
    at org.springframework.data.repository.core.support.RepositoryFactorySupport.getRepository(RepositoryFactorySupport.java:309) ~[na:na]
    at org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport.lambda$afterPropertiesSet$5(RepositoryFactoryBeanSupport.java:323) ~[org.hazelcast.cache.ImperativeToReactiveApplicationKt:2.4.5]
    at org.springframework.data.util.Lazy.getNullable(Lazy.java:230) ~[na:na]
    at org.springframework.data.util.Lazy.get(Lazy.java:114) ~[na:na]
    at org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport.afterPropertiesSet(RepositoryFactoryBeanSupport.java:329) ~[org.hazelcast.cache.ImperativeToReactiveApplicationKt:2.4.5]
    at org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactoryBean.afterPropertiesSet(R2dbcRepositoryFactoryBean.java:167) ~[org.hazelcast.cache.ImperativeToReactiveApplicationKt:1.2.5]
    at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.invokeInitMethods(AbstractAutowireCapableBeanFactory.java:1845) ~[na:na]
    at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.initializeBean(AbstractAutowireCapableBeanFactory.java:1782) ~[na:na]
    ... 46 common frames omitted
```
This one is about proxies and is pretty straightforward. In this context, Spring Data proxies the ***PersonRepository*** interface through a couple of other components. Those are all listed in the stack trace. [GraalVM can handle proxies](https://www.graalvm.org/reference-manual/native-image/DynamicProxy/) but requires you to configure them.

META-INF/native-image/org.hazelcast.cache/imperative-to-reactive/proxy-config.json

```json
[
  ["org.hazelcast.cache.PersonRepository",
   "org.springframework.data.repository.Repository",
   "org.springframework.transaction.interceptor.TransactionalProxy",
   "org.springframework.aop.framework.Advised",
   "org.springframework.core.DecoratingProxy"]
]
```
####  And now for serialization
With the above configuration, the image should start successfully, which makes me feel all warm inside:

```bash
2021-03-18 20:22:28.305  INFO 1 --- [           main] o.s.nativex.NativeListener               : This application is bootstrapped with code generated with Spring AOT

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v2.4.3)

...blah blah blah...

2021-03-18 20:22:30.654  INFO 1 --- [           main] o.s.b.web.embedded.netty.NettyWebServer  : Netty started on port 8080
2021-03-18 20:22:30.655  INFO 1 --- [           main] o.s.boot.SpringApplication               : Started application in 2.355 seconds (JVM running for 2.358)
```
If we access the endpoint at this point, the app throws a runtime exception:

```bash
java.lang.IllegalStateException: Required identifier property not found for class org.hazelcast.cache.Person!
	at org.springframework.data.mapping.PersistentEntity.getRequiredIdProperty(PersistentEntity.java:105) ~[na:na]
```
AOT left out serialized classes, and we need to manage them. As for proxies, GraalVM knows what to do, but it requires an explicit configuration. Let’s configure the Person class as well as the classes of its properties:

META-INF/native-image/org.hazelcast.cache/imperative-to-reactive/serialization-config.json

```json
[
{"name":"org.hazelcast.cache.Person"},
{"name":"java.time.LocalDate"},
{"name":"java.lang.String"},
{"name":"java.time.Ser"}
]
```
#### Success!

Now, we can (finally!) curl the running image:

```bash
curl http://localhost:8080/person/1
curl http://localhost:8080/person/1
```
The output returns the expected result:

```bash
2021-10-10 00:28:38.200  INFO 1 --- [       Thread-7] org.hazelcast.cache.CachingService       : Person with id 1 set in cache
2021-10-10 00:28:42.274  INFO 1 --- [      Thread-11] org.hazelcast.cache.CachingService       : Person with id 1 found in cache
```
We need to configure the Sort class to work with the root '/' endpoint, which retrieves all entities at once.



####  Export the image
```bash
$ docker image save   reactive-to-native:0.0.1-SNAPSHOT  -o rtn.tar
```
#### Native Profile  in  linux
##### Build and Run
That's it! We're ready to build our native image by providing the native profile in the Maven package command:

```bash
$ mvn -Pnative -DskipTests package
```

The Maven command will create the baeldung-spring-native executor file in the target folder. So, we can run our app by simply accessing the executor file:

```bash
$ target/baeldung-spring-native
Hello World!, This is Baledung Spring Native Application
```

# Known issues
## Compatability 
* Using org.springframework.boot:***spring-boot-starter-cache*** , you can notice native image could be generated, but the appliction will be failed while starting up due to spring bean failure to  generating  (because using  	***@Cacheable*** <org.springframework.cache.annotation.Cacheable>).

* Using io.springfox:***springfox-boot-starter*** , you can notice native image could be generated, but the appliction will be failed while starting up due to spring bean failure to  generating  (because using  OrderAwarePluginRegistry  ).

* Using org.apache.poi:***poi-ooxml*** , you can notice native image could be generated, but the appliction will be failed while starting up due to spring bean failure to  generating 
