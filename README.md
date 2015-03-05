# 京东商品推荐系统-数据爬虫部分
===
本项目用来抓取京东商城的食品区域的商品信息、评价信息和用户数据，数据库采用Mysql。

爬虫的核心模块采用[WebMagic](https://github.com/code4craft/webmagic)，主要实现了`JDPageProcessor`类，继承自`PageProcessor`。
采用XPath和CSS Selector两种模式抽取网页信息。如抽取商品页面用户链接信息：
```java
String aHref = html.xpath("div[@class='item']/div[@class='user']/div[@class='u-icon']/a/@href").toString();
```
采用的是Xpath抽取方式，过程：提取`html`中`class`为`item`的`div`中的`class`为`user`的`div`中的`class`为`u-icon`中的超链接。`
