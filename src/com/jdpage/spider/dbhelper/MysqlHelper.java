package com.jdpage.spider.dbhelper;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;
import org.apache.log4j.Logger;

import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Created by Bin on 2014/6/7.
 *
 * @author BIN
 *         Helper for Mysql
 */
public class MysqlHelper {

    private static final Logger logger = Logger.getLogger(MysqlHelper.class);
    private static PreparedStatement preStmtInsertSP,
            preStmtUpdateUserExp,
            preStmtInsertUser,
            preStmtInsertComment,
            preStmtQueryUser;
    private static Connection conn = null;
    // Driver name of Mysql
    private String driver = "com.mysql.jdbc.Driver";
    // url of the connection
    private String url = "jdbc:mysql://127.0.0.1:3306/jd";
    // user name and password
    private String user = "root";
    private String password = "chenbin-";

    public MysqlHelper() {
        initPreStmts();
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // initialize these preparedStatements.
    private void initPreStmts() {
        try {
            preStmtInsertSP = (PreparedStatement) instance().prepareStatement(
                    "insert into s_p (sid, sname, sbrand, sclass, sloc, s_sale_date, scontent)" +
                            "values (?,?,?,?,?,?,?)");
            preStmtUpdateUserExp = (PreparedStatement) instance().prepareStatement(
                    "update s_p set uimpression = ? where sid = ?"
            );
            preStmtInsertUser = (PreparedStatement) instance().prepareStatement(
                    "insert into y_h (uid, uname, sex, level, address) select ?, ?, ?, ?, ? from dual where not exists(select * from y_h where uid = ?)"
            );
            preStmtInsertComment = (PreparedStatement) instance().prepareStatement(
                    "insert into p_l (sid, uid, csale_date, cstar, ctag, cexp, cbuy_date)" +
                            "values (?,?,?,?,?,?,?)"
            );
            preStmtQueryUser = (PreparedStatement) instance().prepareStatement(
                    "select * from y_h where uid = ?"
            );
        } catch (SQLException e) {
            logger.fatal("Error creating prepared statement:");
            logger.error(e.toString());
            logger.info("exit(-1)");
            System.exit(-1);
        }
    }

    public MysqlHelper(String user, String password){
        setUser(user);
        setPassword(password);
        initPreStmts();
    }

    /**
     * Get instance of Mysql Helper.
     *
     * @return Connection.
     */
    private Connection instance() {
        if (null != conn) {
            return conn;
        }

        try {
            Class.forName(driver);
            conn = (Connection) DriverManager.getConnection(url, getUser(), getPassword());
        } catch (Exception e) {
            logger.fatal("Error get instance of MySqlHelper, connection failed.");
            logger.error(e.toString());
            logger.info("exit(-1)");
            System.exit(-1);
        }

        return conn;
    }

    /**
     * Close the connection if Not closed
     * and release some resources.
     */
    public void close() {
        try {
            if (!conn.isClosed()) {
                conn.close();
            }
            if (!preStmtInsertSP.isClosed()) {
                preStmtInsertSP.close();
            }
            if (!preStmtUpdateUserExp.isClosed()) {
                preStmtUpdateUserExp.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
     `sid` int(11) NOT NULL COMMENT '商品id',
     `sname` varchar(50) NOT NULL COMMENT '商品名称',
     `sclass` varchar(100) NOT NULL COMMENT '商品分类',
     `sbrand` varchar(50) DEFAULT NULL COMMENT '品牌',
     `sloc` varchar(50) DEFAULT NULL COMMENT '产地',
     `s_sale_date` date NOT NULL COMMENT '上架时间',
     `uexp` varchar(256) DEFAULT NULL COMMENT '买家Experience',
     `scontext` varchar(1024) DEFAULT NULL COMMENT '商品介绍',
     */

    /**
     * Insert a new kind of good.
     *
     * @param goodClass        The class of good.
     * @param onSaleDate       The on sale date.
     * @param goodId           The good's id
     * @param goodName         The name of good.
     * @param goodBrand        The brand it belongs to.
     * @param goodProcLocation The location it was produced.
     * @param allAttrs         The Total content of Attrs.
     */
    public synchronized void insertSP(String goodClass, String onSaleDate, String goodId,
                                      String goodName, String goodBrand, String goodProcLocation, String allAttrs) {
        try {
            Date date = new Date(DateFormat.getDateTimeInstance().parse(onSaleDate).getTime());

            preStmtInsertSP.setInt(1, Integer.valueOf(goodId));
            preStmtInsertSP.setString(2, goodName);
            preStmtInsertSP.setString(3, goodClass);
            preStmtInsertSP.setString(4, goodBrand);
            preStmtInsertSP.setString(5, goodProcLocation);
            preStmtInsertSP.setDate(6, date);
            preStmtInsertSP.setString(7, allAttrs);

            preStmtInsertSP.execute();
        } catch (ParseException e) {
            System.out.print("page: " + goodId);
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.print("page: " + goodId);
            e.printStackTrace();
        }

    }

    /**
     * Set the good's user experience.
     *
     * @param userImpression The user experience of good.
     * @param goodId         The id of good.
     */
    public synchronized void updateSPUserExperience(String userImpression, String goodId) {
        try {
            preStmtUpdateUserExp.setString(1, userImpression);
            preStmtUpdateUserExp.setInt(2, Integer.valueOf(goodId));

            preStmtUpdateUserExp.executeUpdate();
        } catch (SQLException e) {
            System.out.print("page: " + goodId);
            e.printStackTrace();
        }
    }

    /**
     * Add one record of user.
     *
     * @param uId      User Id.
     * @param uName    User name.
     * @param uSex     User gender.
     * @param uLevel   User level.
     * @param uAddress User address.
     */
    public synchronized void insertUser(String uId, String uName, String uSex, String uLevel, String uAddress) {

        int uid = Integer.valueOf(uId);

        try {
            // make sure this user doesn't in the table.
            preStmtQueryUser.setInt(1,uid);
            ResultSet resultSet = preStmtQueryUser.executeQuery();
            if (1 == resultSet.getFetchSize()){
                return;
            }

            preStmtInsertUser.setInt(1, uid);
            preStmtInsertUser.setString(2, uName);
            preStmtInsertUser.setString(3, uSex);
            preStmtInsertUser.setString(4, uLevel);
            preStmtInsertUser.setString(5, uAddress);

            // the condition of clause "where not exists".
            preStmtInsertUser.setInt(6, uid);

            preStmtInsertUser.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Add one record of comment to database.
     *
     * @param goodId      Good's id.
     * @param userId      User's id.
     * @param commentDate The date when this comment was submitted.
     * @param commentStar Star of this comment. e.g. 1, 2, 3, 4 or 5.
     * @param commentTags Tags of this good.
     * @param userExp     Comment content.
     * @param soldDate    Sold date of this good.
     */
    public synchronized void insertComment(String goodId, String userId, String commentDate,
                                           char commentStar, String commentTags, String userExp, String soldDate) {

        try {
            preStmtInsertComment.setInt(1, Integer.valueOf(goodId));
            preStmtInsertComment.setInt(2, Integer.valueOf(userId));
            preStmtInsertComment.setDate(3, new Date(new SimpleDateFormat("yyyy-MM-dd hh:mm").parse(commentDate).getTime()));
            preStmtInsertComment.setString(4, String.valueOf(commentStar));
            preStmtInsertComment.setString(5, commentTags);
            preStmtInsertComment.setString(6, userExp);
            preStmtInsertComment.setDate(7, new Date(new SimpleDateFormat("yyyy-MM-dd").parse(soldDate).getTime()));

            preStmtInsertComment.execute();
        } catch (SQLException e) {
            System.out.print("page: " + goodId);
            e.printStackTrace();
        } catch (ParseException e) {
            System.out.print("page: " + goodId);
            e.printStackTrace();
        }
    }
}
