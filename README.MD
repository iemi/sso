# 概述
#### 关于SSO
网上关于SSO的介绍非常多，举个例就是，当我们使用浏览器登录了淘宝https://www.taobao.com以后，我们再访问天猫https://www.tmall.com时就不需要再次登录。也就是淘宝和天猫之间只需要登录其中一个业务系统，另一个就自动登录
#### 运用场景
一般情况下，一个公司内肯定不止一个业务系统，公司内相互可信任的系统，我们希望可以实现和淘宝天猫相同的单点登录效果。多个业务系统使用同一个认证服务也能简化开发
#### 相关知识
对于此文你所要了解的相关知识：
* oauth2.0简介：[http://www.ruanyifeng.com/blog/2019/04/oauth_design.html](http://www.ruanyifeng.com/blog/2019/04/oauth_design.html)
* oauth2.0的四种方式：[http://www.ruanyifeng.com/blog/2019/04/oauth-grant-types.html](http://www.ruanyifeng.com/blog/2019/04/oauth-grant-types.html)
* JWT：[https://zhuanlan.zhihu.com/p/27370773](https://zhuanlan.zhihu.com/p/27370773)
* Spring Security：[https://www.jianshu.com/p/e22fdeedc9a3](https://www.jianshu.com/p/e22fdeedc9a3)
#### Spring Security Oauth+JWT单点登录流程
![实现原理](https://upload-images.jianshu.io/upload_images/19628259-f8b36f1cb1c57349.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
图中一个认证服务器，两个客户端应用，A和B。用户请求访问A的服务器资源时会被引导到认证服务器进行授权，经过登录认证和同意授权后返回授权码给A，A的服务端收到授权码后再次向认证服务器请求token，最终返回JWT类型的token，A解析接收到的JWT进行解析获取到用户相关信息保存进Spring Security的SecurityContext中实现登录；此时用户再次请求访问B的服务器资源时，B会引导用户到认证服务器进行请求授权，此时不在需要再次登录认证，只要授权确认，确认后返回授权码到B，B再次向认证服务器发起获取token请求，认证服务器返回JWT，B对JWT进行解析，保存用户进Spring Security的SecurityContext中实现登录
#### 实现流程
1. 搭建SSO第三方认证授权服务中心
2. 搭建客户端应用1
3. 搭建客户端应用2
4. 测试
#### 前言
本文主要阐述使用Spring Security Oauth+JWT实现单点登录和做到客户端的权限控制，不阐述其相关源码和实现原理
#### 相关版本
Spring boot：2.2.0.RELEASE
***
#实现步骤
一共创建三个服务，一个认证服务，两个客户端服务，采取单独创建三个工程项目的方式，不是三个服务模块化整合在一起。三个服务都引入相同的依赖
```
<dependency>
		<groupId>org.springframework.cloud</groupId>
		<artifactId>spring-cloud-starter-oauth2</artifactId>
		<version>2.1.4.RELEASE</version>
</dependency>
```
#### 搭建认证中心服务
1. 配置文件
```
server.servlet.context-path= /server
```
只配置了服务的根路径，端口用默认的8080
2. 处理用户信息
```
@Component
public class MyUserDetailService implements UserDetailsService {

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        /**
         * 这里实际情况应该是根据参数s查询数据库用户数据
         */
        return new User(username, bCryptPasswordEncoder.encode("123"), AuthorityUtils.commaSeparatedStringToAuthorityList("ROLE_LOW,ROLE_MID"));
    }
}
```
了解Spring Security的同学应该对这个很了解这个类了（可以看我的上文[https://www.jianshu.com/p/e22fdeedc9a3](https://www.jianshu.com/p/e22fdeedc9a3)了解相关），这里让Spring Security只有输入密码123可以经过认证，并且这个用户拥有ROLE_LOW和ROLE_MID的权限
3. 认证中心服务配置
```
@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter{

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private MyUserDetailService myUserDetailService;

//    @Autowired
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {

        clients.inMemory()
                .withClient("client1")
                .secret(passwordEncoder.encode("123"))
                .redirectUris("http://localhost:8081/client1/login")
                .scopes("all")
                .authorizedGrantTypes("authorization_code", "refresh_token", "password")
                .autoApprove(false)
                .and()
                .withClient("client2")
                .secret(passwordEncoder.encode("123"))
                .redirectUris("http://localhost:8082/client2/login")
                .scopes("all")
                .authorizedGrantTypes("authorization_code", "refresh_token", "password")
                .autoApprove(true);

    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {

        endpoints.authenticationManager(authenticationManager)
                .tokenStore(jwtTokenStore())
                .accessTokenConverter(jwtAccessTokenConverter());
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security.tokenKeyAccess("isAuthenticated()");
    }

    @Bean
    public TokenStore jwtTokenStore(){
        return  new JwtTokenStore(jwtAccessTokenConverter());
    }

    @Bean
    public JwtAccessTokenConverter jwtAccessTokenConverter(){
        JwtAccessTokenConverter jwtAccessTokenConverter = new JwtAccessTokenConverter();
        jwtAccessTokenConverter.setSigningKey("testKey");
        return jwtAccessTokenConverter;
    }
}
```
认证中心服务配置类主要做三件事
* @EnableAuthorizationServer注解把这个服务声明为认证服务
* 配置两个客户端相关信息，包含client_id，client_secret，redirect_uri，scope，支持的认证方式（密码模式，授权码模式等），以及配置允许自动授权。这里需要注意redirect_uri必须配置否则获取授权码时会报至少需要注册一个回调地址的错误，还有配置client_secret时必须用BCryptPasswordEncoder进行编码一下，否则会出现client_secret错误的问题，因为Spring Security Oauth默认情况下会对发起请求中的client_secret参数进行编码
* 配置生成的token类型为JWT
* 配置token的安全约束
4. Spring Security配置
```
@Configuration
@EnableWebSecurity
public class SecurityBrowserConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private MyUserDetailService myUserDetailService;

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http.requestMatchers().antMatchers("/oauth/**", "/login/**", "/logout/**")
                .and()
                .authorizeRequests()
                .antMatchers("/oauth/**").authenticated()
                .and()
                .formLogin().permitAll();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(myUserDetailService);
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}
```
有学习过Spring Security的同学对这个配置应该也比较熟悉了，主要就是
* 注册了一个BCryptPasswordEncoder实例
* 配置了Spring Security安全规则
* 给AuthenticationManagerBuilder配置上自定义的MyUserDetailService
* 注册一个AuthenticationManager的实例。如果想实现认证服务器支持密码模式获取token的话必须在这注册这个实例，并且在认证服务配置类AuthorizationServerConfig中给AuthorizationServerEndpointsConfigurer配置上此实例，如下的endpoints.authenticationManager(authenticationManager)
```
public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        endpoints.authenticationManager(authenticationManager)
                .tokenStore(jwtTokenStore())
                .accessTokenConverter(jwtAccessTokenConverter());
    }
```
#### 搭建客户端服务1
1. 配置文件
```
server.port=8081
server.servlet.context-path=/client1

security.oauth2.client.client-id = client1
security.oauth2.client.client-secret= 123
security.oauth2.client.user-authorization-uri = http://localhost:8080/server/oauth/authorize
security.oauth2.client.access-token-uri = http://localhost:8080/server/oauth/token

security.oauth2.resource.jwt.key-uri = http://localhost:8080/server/oauth/token_key
```
端口设为8081，根路径设为/client1，请求token的所必要的参数client-id、client-secret，user-authorization-uri是请求授权码的地址，access-token-uri是请求token的地址，jwt.key-uri是获取JWT的signKey的地址
2. 客户端配置文件
```
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableOAuth2Sso
public class SsoClient1Config extends WebSecurityConfigurerAdapter{
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
                .antMatcher("/**").authorizeRequests()
                .anyRequest().authenticated();
    }
}
```
主要三个注解
* @EnableOAuth2Sso让该服务成为一个客户端应用，当用户访问此服务下的资源时（接口）会引导用户到认证服务器进行登录认证，根据配置文件中的相关配置
* @EnableWebSecurity让Spring Security安全配置生效
* @EnableGlobalMethodSecurity(prePostEnabled = true)让接口方法上的权限控制生效
3. 测试接口
```
@RestController
public class Client1Controller {

    @GetMapping("/high")
    @PreAuthorize("hasAuthority('ROLE_HIGH')")
    public String normal( ) {
        return "high permission";
    }

    @GetMapping("/mid")
    @PreAuthorize("hasAuthority('ROLE_MID')")
    public String medium() {
        return "mid permission";
    }

    @GetMapping("/low")
    @PreAuthorize("hasAuthority('ROLE_LOW')")
    public String admin() {
        return "low permission";
    }

}

```
三个接口分别要求不同的用户权限，ROLE_HIGH、ROLE_MID、ROLE_LOW
#### 搭建客户端服务2
两个客户端其实基本一样，我就直接上代码了
1. 配置文件
```
server.port=8082
server.servlet.context-path=/client2

security.oauth2.client.client-id = client2
security.oauth2.client.client-secret= 123
security.oauth2.client.user-authorization-uri = http://localhost:8080/server/oauth/authorize
security.oauth2.client.access-token-uri = http://localhost:8080/server/oauth/token

security.oauth2.resource.jwt.key-uri = http://localhost:8080/server/oauth/token_key
```
2. 配置类
```
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableOAuth2Sso
public class SsoClient2Config extends WebSecurityConfigurerAdapter{
    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
                .antMatcher("/**").authorizeRequests()
                .anyRequest().authenticated();
    }
}
```
3. 测试接口
```
@RestController
public class Client2Controller {

    @GetMapping("/high")
    @PreAuthorize("hasAuthority('ROLE_HIGH')")
    public String normal( ) {
        return "high permission";
    }

    @GetMapping("/mid")
    @PreAuthorize("hasAuthority('ROLE_MID')")
    public String medium() {
        return "mid permission";
    }

    @GetMapping("/low")
    @PreAuthorize("hasAuthority('ROLE_LOW')")
    public String admin() {
        return "low permission";
    }

}
```
#### 测试
1. **单点登录测试**
三个服务全部启动，直接访问客户端1的接口localhost:8081/client1/low
![访问low接口](https://upload-images.jianshu.io/upload_images/19628259-5a1362db1c050ce2.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
随后被引导到登录页面
![登录页](https://upload-images.jianshu.io/upload_images/19628259-57256a48b4c8d5d0.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
可以发现原本访问的接口在localhost:8081/client1下，跳转到了localhost:8081/server下面。输入用户名user，密码123登录
![请求授权](https://upload-images.jianshu.io/upload_images/19628259-7fcd3a5218cd634e.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
跳转到是否同意授权的页面，点击Authorize
![成功访问客户端1接口](https://upload-images.jianshu.io/upload_images/19628259-85fe699dc69849d6.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
这时候意味着用户已经登录客户端1，尝试访问客户端2的接口localhost:8082/client2/low
![成功访问客户端2接口](https://upload-images.jianshu.io/upload_images/19628259-67b32aa25909019c.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
这时候没有让我们再次登录就可以直接访问了（如果你在认证服务的配置中把客户端2的自动授权设置为false，这里会再次向你询问是否同意授权，但是不用登录），这已经实现单点登录了
2. **权限控制测试**
上面我们登录的这个用户只有ROLE_LOW和ROLE_MID的权限，登录状态下再次尝试访localhost:8081/client1/mid
![访问mid成功](https://upload-images.jianshu.io/upload_images/19628259-0424f544c350bbd0.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
可以看出访问需要ROLE_LOW和ROLE_MID权限的接口都没问题，再访问[http://localhost:8081/client1/high](http://localhost:8081/client1/high)
![访问high失败](https://upload-images.jianshu.io/upload_images/19628259-f4f4e50db2a88788.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
访问需要ROLE_HIGH权限的接口返回403，说明我们的权限控制也实现了
#### 工程结构
最后上一下我的工程结构
1. 认证中心服务
![认证中心](https://upload-images.jianshu.io/upload_images/19628259-b1ea46eee80c75f6.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
2. 客户端1
![客户端1](https://upload-images.jianshu.io/upload_images/19628259-b519d355daa5ebfd.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
3. 客户端2
![客户端2](https://upload-images.jianshu.io/upload_images/19628259-6f38576d5cfbdfcb.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
***
# 总结
* 搭建SSO第三方认证授权服务中心
* 搭建客户端应用1
* 搭建客户端应用2
* 测试
文章详解地址：https://www.jianshu.com/p/f83e8dc3dd9d
