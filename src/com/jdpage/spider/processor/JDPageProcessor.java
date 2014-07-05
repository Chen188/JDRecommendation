package com.jdpage.spider.processor;

import com.jdpage.spider.dbhelper.MysqlHelper;
import com.jdpage.spider.pagesaver.JDPageSaver;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Html;
import us.codecraft.webmagic.selector.Selectable;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Bin on 2014/6/9.
 *
 * <p>Process JD's page.</p>
 * <p>The method dealGoods is created to extract the good's info of the good page,
 * the method dealComment is created to extract the comments of each good,
 * both of them contain the method savePage, which means to store the page to local drive.</p>
 */
public class JDPageProcessor implements PageProcessor {
    private static MysqlHelper mysqlHelper = new MysqlHelper();
    private JDPageSaver jdPageSaver = new JDPageSaver();
    private static Logger logger = Logger.getLogger(JDPageProcessor.class);
    private Site site = Site.me().setRetryTimes(3).setSleepTime(100);

    private Hashtable<String, Integer> urlRetryTime = new Hashtable<String, Integer>();
    public static void main(String[] args) {
        PropertyConfigurator.configure("D:\\JavaProjects\\webtest1\\src\\log4j.properties");

//        ArrayList<String> urlsToReload;
//        urlsToReload = new JDTaskReloader("F:\\JDDevelop\\html\\good\\", "F:\\JDDevelop\\html\\comment\\").getUrlsToReload();
//        Spider spider = Spider.create(new JDPageProcessor()).startUrls(urlsToReload).thread(8);

        Spider spider = Spider.create(new JDPageProcessor()).addUrl("http://channel.jd.com/food.html").thread(8);
        spider.start();

        // can be replaced by event notifier.
        while (true) {
            try {
                // Wait 2s.
                Thread.sleep(2000);
                if (0 == spider.getThreadAlive()) {
                    logger.info("There is no thread alive of spider, waiting for 15s before exit.");
                    Thread.sleep(15 * 1000);
                    break;
                }else {
                    // spider is still running.
                    Thread.sleep(58 * 1000);
                }
            }catch (InterruptedException e) {
                logger.error("Sleep was interrupted.");
            }
        }
        logger.info("Close mysqlHelper and exit.");
        mysqlHelper.close();
    }

    private void dealGoods(Page page, String sid) {
        // Extract the class, good information, the result will be processed by pipeline.
        String class_ = page.getHtml().xpath("//div[@class=\"breadcrumb\"]//a/text()").get();
        if (null == class_) {
            // 保证重试次数不大于3次
            Integer urlTime = urlRetryTime.get(sid);
            if (null != urlTime && urlTime <3 && !page.isNeedCycleRetry()) {
                page.setNeedCycleRetry(true);
            }

            if (urlRetryTime.containsKey(sid)) {
                urlRetryTime.replace(sid, urlRetryTime.get(sid) + 1);
            }else{
                // 0，1，2 共三次机会
                urlRetryTime.put(sid, 0);
            }

            logger.error("page's class is null and retry time is" + urlRetryTime.get(sid) + ", url: " + page.getUrl().toString());
            return;
        }
        if (!class_.startsWith("食品饮料")) {
            // not the class of good we want, just skip.
            return;
        }

        // deal with good's url
        page.addTargetRequests(page.getHtml().links().regex("http://item\\.jd\\.com/\\d+\\.html").all());

        // This is not a page of good, just extract the urls of good.
        if (!page.getUrl().toString().matches("http://item\\.jd\\.com/\\d+\\.html")) {
            return;
        }

        //Store this page to local drive
        jdPageSaver.savePage(page, false);

        List<String> goodInfo = page.getHtml().xpath("//ul[@class=\"detail-list\"]//*/text()").all();

        String goodName = "", goodBrand = "", goodLoc = "", stringDate = "", allAttrs = "";
        boolean isBrand = false;
        boolean extractDone = false;

        for (String buffer : goodInfo) {
            buffer = buffer.trim();

            // Done with extract info, just join them with each other.
            if (extractDone) {
                allAttrs += buffer + "|";
                continue;
            }

            String result = buffer.substring(buffer.indexOf("：") + 1);

            if (isBrand) {
                isBrand = false;
                goodBrand = buffer;
            }

            if (goodName.isEmpty() && buffer.startsWith("商品名称")) {
                goodName = result;
            } else if (sid.isEmpty() && buffer.startsWith("商品编号")) {
                sid = result;
            } else if (goodBrand.isEmpty() && buffer.startsWith("品牌")) {
                if (buffer.length() - 1 == buffer.lastIndexOf("：")) {
                    isBrand = true;
                    allAttrs += buffer;
                    continue;
                }
            } else if (stringDate.isEmpty() && buffer.startsWith("上架时间")) {
                stringDate = result;
            } else if (goodLoc.isEmpty() && buffer.contains("产地")) {
                goodLoc = result;
            }

            allAttrs += buffer + "|";

            if (!goodLoc.isEmpty() && !stringDate.isEmpty() &&
                    !goodBrand.isEmpty() && !goodName.isEmpty()) {
                extractDone = true;
            }
        } //end of for

        // if the date is empty, let it be "0000-00-00 00:00:00",
        // make sure it's not empty.
        if (stringDate.isEmpty()) {
            stringDate = "0000-00-00 00:00:00";
        }

        // can not get the good's id, skip.
        if (sid.isEmpty()){
            return;
        }

        // add the comment page to request queue.
        page.addTargetRequest("http://club.jd.com/review/" + sid + "-0-1-0.html");

        // Add to database.
        mysqlHelper.insertSP(class_, stringDate, sid, goodName, goodBrand, goodLoc, allAttrs);
    }

    private void dealComment(Page page, String sid, boolean isFirstCommentPage) {
        // save page
        jdPageSaver.savePage(page, true);

        Selectable selectable = page.getHtml().xpath("//div[@data-widget='tab-content']");

        List<String> elms = selectable.all();
        for (String elm : elms) {

            Html html = new Html(elm);
            String aHref = html.xpath("div[@class='item']/div[@class='user']/div[@class='u-icon']/a/@href").toString();

            // Check weather the user is ana or not.
            if (null == aHref) {
                continue;
            }

            String uid = aHref.substring(aHref.lastIndexOf("/") + 1, aHref.lastIndexOf("."));
            String uName = html.xpath("div[@class='item']/div[@class='user']/div[@class='u-name']/a/text()").toString().trim();
            List<String> uLevelALoc = html.xpath("div[@class='item']/div[@class='user']/span[@class='u-level']/*/text()").all();
            String uLevel = uLevelALoc.get(0);
            String uLoc = "";

            // If contains the address of the user
            if (2 == uLevelALoc.size()) {
                uLoc = uLevelALoc.get(1);
            }
            // The count of stars the user gives;
            char cStar = html.xpath("div[@class='item']/div[@class='i-item']/div[@class='o-topic']/span[1]/@class").toString().split(" ")[1].charAt(2);

            // Date of this comment submitted.
            String cCommentDate = html.xpath("div[@class='item']/div[@class='i-item']/div[@class='o-topic']/span[2]/a/text()").toString();

            List<String> cCommentList = html.xpath("div[@class='item']/div[@class='i-item']/div[@class='comment-content']//*/text()").all();
            String tags = "", uExp = "", uBuyDate = "";
            State state = null;

            // -----------------------
            // Extract the comments.
            // -----------------------
            for (String item : cCommentList) {
                item = item.trim();
                if (item.isEmpty()) {
                    continue;
                }
                if (item.startsWith("标　　签：")) {
                    state = State.TAG;
                    continue;
                }
                if (item.startsWith("心　　得：")) {
                    state = State.EXP;
                    continue;
                }
                if (item.startsWith("购买日期：")) {
                    state = State.DATE;
                    continue;
                }
                if (State.TAG.equals(state)) {
                    tags += item + "|";
                    continue;
                }
                if (State.EXP.equals(state)) {
                    uExp += item + "|";
                    continue;
                }
                if (State.DATE.equals(state) && uBuyDate.length() < 8) {
                    uBuyDate += item;
                }
            }

            //add to database
            mysqlHelper.insertComment(sid, uid, cCommentDate, cStar, tags, uExp, uBuyDate);

            // add user
            mysqlHelper.insertUser(uid, uName, "Secret", uLevel, uLoc);
        }

        // Extract user experience from the first page of comments.
        if (isFirstCommentPage) {
            // update database, set the Good's User experience
            List<String> userImpressionList = page.getHtml().xpath("//dd[@class=\"p-bfc\"]/q/*/text()").all();
            String userImpressions = "";
            boolean isNumOfTag = false;
            for (String elem : userImpressionList) {
                elem = elem.trim();
                if (elem.isEmpty()) {
                    continue;
                }
                if (elem.startsWith("(")) {
                    isNumOfTag = true;
                }
                if (isNumOfTag) {
                    userImpressions += elem + "|";
                    isNumOfTag = false;
                    continue;
                }
                userImpressions += elem;
            }
            mysqlHelper.updateSPUserExperience(userImpressions, sid);

            // ------------------------
            // deal with comments url.
            // ------------------------
            // get the count of left pages of comment.
            List<String> numStringList = page.getHtml().xpath("//div[@class='clearfix']/div[@class='pagin fr']/a/text()").all();
            String numString = numStringList.size() > 2 ? numStringList.get(numStringList.size() - 2).replace(".", "") : "";

            // there is no more than one page.
            if (numString.isEmpty()) {
                return;
            }
            int totalPage = Integer.valueOf(numString);

            List<String> urls = new LinkedList<String>();
            String tmpUrl = "http://club.jd.com/review/";
            for (int cursor = 2; cursor <= totalPage; cursor++) {
                String url = tmpUrl + sid + "-0-" + cursor + "-0.html";
                urls.add(url);
            }

            if (urls.size() > 0) {
                page.addTargetRequests(urls);
            }
        }
    }

    @Override
    public void process(Page page) {
        String url = page.getUrl().toString();
        String tailUrl = url.substring(url.lastIndexOf('/') + 1, url.lastIndexOf("."));
        String[] tailUrlSpilt = tailUrl.split("-");
        String sid = tailUrlSpilt[0];               // The good's id.
        if (1 == tailUrlSpilt.length) {
            dealGoods(page, sid);
        } else {
            dealComment(page, sid, 1 == Integer.valueOf(tailUrlSpilt[2]));
        }
    }

    @Override
    public Site getSite() {
        return site;
    }

    // The current state of iterator.
    private enum State {
        TAG,    // dealing with tag.
        EXP,    // dealing with exp.
        DATE    // dealing with date.
    }
}
