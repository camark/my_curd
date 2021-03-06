package com.hxkj.sys.service;

import com.alibaba.fastjson.JSON;
import com.hxkj.common.util.DateTimeUtils;
import com.hxkj.common.util.ExecutorServiceUtils;
import com.hxkj.common.util.FreemarkerUtils;
import com.hxkj.common.util.Identities;
import com.hxkj.common.util.ws.SendMsgUtils;
import com.hxkj.sys.model.SysNotification;
import com.hxkj.sys.model.SysNotificationDetail;
import com.hxkj.sys.model.SysNotificationType;
import com.jfinal.aop.Before;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.jfinal.plugin.activerecord.tx.Tx;
import org.apache.log4j.Logger;

import java.util.*;

public class SysNotificationService {

    private final static Logger LOG = Logger.getLogger(SysNotificationService.class);


    /**
     * 发送系统通知
     *
     * @param notificationCode 系统通知类型编码
     * @param templateParams   系统通知模板参数 (模板基于freemarker)
     * @return
     */
    @Before(Tx.class)
    public boolean sendNotification(String notificationCode, Map<String, Object> templateParams) {
        SysNotificationType sysNotificationType = SysNotificationType.dao.findByCode(notificationCode);
        if (sysNotificationType == null) {
            LOG.debug("---- 未找到消息通知类型");
            return false;
        }

        // 查询通知接收人
        Set<Long> receivers = getNotificationReceivers(sysNotificationType.getId());
        if (receivers.size() == 0) {
            LOG.debug("---- 未找到消息接收人");
            return false;
        }

        String msgTitle = sysNotificationType.getTxt();
        String msgContent = FreemarkerUtils.renderAsText(sysNotificationType.getTemplate().replaceAll("FTL", "\\$"), templateParams);

        // 保存通知数据
        boolean flag = saveNotificationData(sysNotificationType, msgTitle, msgContent, receivers);
        if (!flag) {
            LOG.debug("---- 系统通知 数据保存数据库失败");
        }

        LOG.info("系统通知 执行成功");

        // 服务器消息推送 （如此写 信息不能带上 从表id）
        // 此处可以根据 sysNotificationType 的分类 使用服务器消息推送方式
        // 例如 SYSTEM 代表 后台系统通知，可采用 WebSocket 实现 或 更多方式实现
        Map<String, Object> msg = new HashMap<>();
        msg.put("category", sysNotificationType.getCategory());  // 为了扩展更方便
        msg.put("code", sysNotificationType.getCode()); // 为了扩展更方便
        msg.put("logo", sysNotificationType.getLogo());
        msg.put("title", msgTitle);
        msg.put("content", msgContent);
        SendMsgUtils.sendToUsers(receivers, JSON.toJSONString(msg));

        return flag;
    }


    /**
     * 获得 系统通知  关联的所有用户id
     *
     * @param notificationTypeId 系统通知类型id
     * @return
     */
    public Set<Long> getNotificationReceivers(String notificationTypeId) {
        Set<Long> userIdSet = new HashSet<>();

        // 通知类型 角色表 确定用户
        List<Record> userIds = Db.find(" SELECT d.user_id " +
                "FROM " +
                "( SELECT a.role_id FROM sys_notification_type_role a, auth_role b " +
                " WHERE a.role_id = b.id  and a.notification_type_id = ? ) aa " +
                "LEFT JOIN auth_user_role d ON aa.role_id = d.role_id", notificationTypeId);
        for (Record record : userIds) {
            userIdSet.add(record.get("user_id"));
        }
        // 通知类型 用户表 确定用户
        userIds = Db.find("select user_id from sys_notification_type_user where notification_type_id = ? ", notificationTypeId);
        for (Record record : userIds) {
            userIdSet.add(record.get("user_id"));
        }

        return userIdSet;
    }


    /**
     * 保存 系统通知 数据
     *
     * @param sysNotificationType 从数据库中查询到系统通知类型对象
     * @param msgTitle
     * @param msgConent           系统通知模板内容
     * @param receivers           系统通知接收人集合，不可为null，且size 大于0
     * @return
     */
    private boolean saveNotificationData(SysNotificationType sysNotificationType, String msgTitle, String msgConent, Set<Long> receivers) {
        SysNotification sysNotification = new SysNotification();
        sysNotification.setTypeCode(sysNotificationType.getCode());
        sysNotification.setTitle(msgTitle);
        sysNotification.setContent(msgConent);
        sysNotification.setCreateTime(new Date());
        sysNotification.setExpiryTime(DateTimeUtils.getDate(sysNotification.getCreateTime(), sysNotificationType.getUntilExpiryDay()));
        sysNotification.setDeadTime(DateTimeUtils.getDate(sysNotification.getCreateTime(), sysNotificationType.getUntilDeadDay()));
        sysNotification.setId(Identities.id());
        boolean flag = sysNotification.save();

        if (!flag) {
            return false;
        }
        for (Long receiverId : receivers) {
            // 异步 创建 从表数据
            // 子线程 如发生异常 不能触发事务
            ExecutorServiceUtils.pool.submit(new Runnable() {
                @Override
                public void run() {
                    SysNotificationDetail detail = new SysNotificationDetail();
                    detail.setId(Identities.id());
                    detail.setMainId(sysNotification.getId());
                    detail.setReceiver(receiverId);
                    detail.save();
                }
            });
        }

        return true;

    }
}
