package com.jdpage.spider.pagesaver;


import org.apache.log4j.Logger;
import us.codecraft.webmagic.Page;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Bin on 2014/6/10.
 * <p/>
 * Save the page to local driver, page will be store into different folder,
 * depending on if it's a comment page or not.
 */
public class JDPageSaver {

    private static final Logger logger = Logger.getLogger(JDPageSaver.class);
    private static AtomicInteger pageCounter = new AtomicInteger(0);
    private final String subfixComment = "Comment";
    private final String subfixGood = "Good";
    private final String seprator = String.valueOf(File.separatorChar);
    private String basePath = "F:\\JDDevelop\\html\\";

    public JDPageSaver() {
        // Check.
        checkAndMkdir(basePath + subfixComment);
        checkAndMkdir(basePath + subfixGood);
    }

    public JDPageSaver(String basePath) {
        this.basePath = basePath;

        // Check the existence of the following folders.
        checkAndMkdir(basePath + subfixComment);
        checkAndMkdir(basePath + subfixGood);
    }

    /**
     * Save the page to local drive.
     *
     * @param page The page to be saved.
     * @return Indicate whether the page is saved successfully or not.
     */
    public boolean savePage(Page page, boolean isCommentPage) {
        // set the var path.
        String url = page.getUrl().toString();
        String suffix = url.substring(url.lastIndexOf('/'));
        File file;

        // Save the page to corresponding location.
        if (isCommentPage) {
            file = new File(basePath + subfixComment + suffix);
        } else {
            file = new File(basePath + subfixGood + suffix);
        }

        if (file.exists() && file.isFile()) {
            logger.warn("File exists and will be overridden: " + file.getName());
            file.delete();
        }

        try {
            FileWriter fw = new FileWriter(file);
            fw.write(page.getRawText());
            fw.flush();
            fw.close();
        } catch (IOException e) {
            logger.error("Write file error: " + file.getName());
            logger.error(e.toString());
            return false;
        }

        return true;
    }

    /**
     * Check the existence of folders. if not, create.
     *
     * @param path The base path of these folders comment and good.
     */
    public void checkAndMkdir(String path) {
        File file = new File(path);
        if (file.exists() && file.isDirectory()) {
            logger.info("Path " + path + " exists.");
            return;
        }

        // invalid path.
        logger.info("Path " + path + " doesn't exist.");

        String[] parents = file.getAbsolutePath().split(
                seprator.equals("\\") ? "\\\\" : "/"
        );      // pattern is regex, so "\" should be "\\".

        String buffer = "";
        for (String parent : parents) {
            if (parent.isEmpty()) {
                continue;
            }

            buffer += parent + seprator;
            file = new File(buffer);
            try {
                if (!file.exists()) {
                    file.mkdir();
                }
            } catch (Exception e) {
                logger.fatal("Error creating folder: " + buffer);
                logger.error(e.toString());
                logger.info("Exit(-1)");
                System.exit(-1);
            }
        }

        logger.info("Succeeded creating Path: " + path);
    }
}
