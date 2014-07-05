package com.jdpage.spider.taskreloader;

import com.sun.istack.internal.NotNull;
import org.apache.log4j.Logger;
import us.codecraft.webmagic.selector.Html;

import java.io.*;
import java.util.*;

/**
 * Created by Bin on 2014/6/14.
 * @author BIN
 * The interface of task reloader.
 * The goal is to reload the undone tasks.
 */
public class JDTaskReloader {

    private File goodDir, commentDir;
//    private String goodPath;
//    private String commentPath;

    // container of urls to be reload.
    private ArrayList<String> urlsToReload = new ArrayList<String>();

    // Map of good file and it's comment files. e.g. <123, 3>
    private Map<String, List<String>> maxNum = new HashMap<String, List<String>>();
    private List<String> goodFiles = new ArrayList<String>();
    private List<String> commentFiles = new ArrayList<String>();

    // the logger of this class.
    private static Logger logger = Logger.getLogger(JDTaskReloader.class);

    /**
     * constructor
     * @param goodPath path of the goods
     * @param commentPath the path of comments
     */
    public JDTaskReloader(String goodPath, String commentPath) {

        goodDir = new File(goodPath);
        if (goodPath.equals(commentPath)) {
            commentDir = goodDir;
        }else {
            commentDir = new File(commentPath);
        }
    }

    /**
     * whether necessary to reload the task.
     * @return boolean.
     */
    public boolean isReloadNeeded() {
        FilenameFilter commentFilenameFilter = null;
        FilenameFilter goodFilenameFilter = null;


        // the directory of good files is the same with the comment files.
        // then it's necessary to assign diff filter to each.
        if (commentDir.getAbsolutePath().equals(goodDir.getAbsolutePath())) {
            commentFilenameFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.matches("\\d+\\.html");
                }
            };
            goodFilenameFilter = new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.matches("\\d+-0-\\d+-0\\.html");
                }
            };
        }

        // list the files in the directory.
        File[] comm = commentDir.listFiles(commentFilenameFilter);
        File[] good = goodDir.listFiles(goodFilenameFilter);

        // add each file name to maxNum.
        for (File file : good){
            String name = file.getName();
            goodFiles.add(name);
            maxNum.put(name.substring(0, name.lastIndexOf(".")), new ArrayList<String>());
        }

        for (File file : comm){
            // the name_ may like this: 123234-0-1-0.html
            String name_ = file.getName();
            commentFiles.add(name_);

            // the name will be 123234
            String name = name_.substring(0, name_.indexOf("-"));

            // and the numStr will be 1
            String numStr = name_.substring(name.length() + 3, name_.lastIndexOf("-"));

            ArrayList<String> numList = (ArrayList<String>)maxNum.get(name);
            // fill the numList with absent files.
            if (null == numList){
                numList = new ArrayList<String>();
            }
            numList.add(numStr);
        }
        // return false if the number of good is lower than 100.
        return 100 < goodFiles.size();
    }

    /**
     * Get the max number of comment page.
     * @param fileName The good page file.
     * @return The number of max comment page.<br />
     * -1 indicates the file name is null or empty, 0 shows us this file is not a page of good.
     */
    private Integer getHtmlMaxPageNum(@NotNull String fileName){
        if (!fileName.matches("\\d+\\.html")){
            return -1;
        }

        String name = fileName.substring(0,fileName.lastIndexOf("."));

        // check the existence of comment file.
        // return -2, indicating the absence of any comment file of this good.
        File commentFile = new File(commentDir.getAbsolutePath().concat(File.separator).concat(name).concat("-0-1-0.html"));
        if (!(commentFile.isFile() && commentFile.exists())){
            return -2;
        }

        // File content
        String content = "";
        try {
            FileInputStream inputStream = new FileInputStream(commentFile);
            byte[] b = new byte[inputStream.available()];
            inputStream.read(b);
            content = new String(b);

            inputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return -3;
        } catch (IOException e) {
            e.printStackTrace();
            return -3;
        }
        Html html = new Html(content);

        // get the count of left pages of comment.
        List<String> numStringList = html.xpath("//div[@class='clearfix']/div[@class='pagin fr']/a/text()").all();
        String numString = numStringList.size() > 2 ? numStringList.get(numStringList.size() - 2).replace(".", "") : "";

        return numString.isEmpty() ? 1 : Integer.valueOf(numString);
    }

    /**
     *
     * @param fileName file name of good
     * @return Absent comment files of one good.
     */
    private ArrayList<String> getAbsentCommentFiles(@NotNull String fileName){

        Integer maxCommentNum = getHtmlMaxPageNum(fileName);
        if (maxCommentNum == -1 || maxCommentNum == 0 || maxCommentNum == -3){
            return null;
        }
        String contentFileName = fileName.substring(0,fileName.lastIndexOf(".")).concat("-0-?-0.html");
        if (maxCommentNum == -2){
            // add the url to urlsToReload.
            urlsToReload.add("http://club.jd.com/review/" + contentFileName.replace("?","1"));
            return null;
        }

        ArrayList<String> lists = (ArrayList<String>) maxNum.get(fileName.substring(0, fileName.lastIndexOf(".")));
        if (null == lists || 1 > lists.size()){
            return null;
        }
        lists.sort(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });

        ArrayList<String> result = new ArrayList<String>();
        for (int cursor = 1; cursor <= maxCommentNum; cursor++){
            String key = String.valueOf(cursor);
            if (!lists.contains(key)){
                result.add("http://club.jd.com/review/" + contentFileName.replace("?",key));
            }
        }
        return result;
    }

    /**
     * get the urls to be reload.
     * @return ArrayList
     */
    public ArrayList<String> getUrlsToReload(){
        logger.info("Start reloading task, it may takes a while...");
        if (!isReloadNeeded()) {
            return null;
        }

        for (String good : goodFiles) {
            // get absent comment file.
            ArrayList<String> items = getAbsentCommentFiles(good);
            if (null == items || 0 == items.size()){
                continue;
            }
            for (String item : items){
                // forgot
                if(!urlsToReload.contains(item)){
                    urlsToReload.add(item);
                }
            }
        }
        logger.info("Finished reloading task...");
        return urlsToReload;
    }
}
