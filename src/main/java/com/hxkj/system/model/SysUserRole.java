package com.hxkj.system.model;

import com.hxkj.system.model.base.BaseSysUserRole;

import java.util.List;

/**
 * sys_user_role 用户角色中间表  model
 * @author  zhangchuang
 */
public class SysUserRole extends BaseSysUserRole<SysUserRole> {
    public static final SysUserRole dao = new SysUserRole().dao();

    /**
     * 根据用户id 查询 用户角色
     * @param userId
     * @return
     */
    public SysUserRole findRolesByUserId(String userId) {
        String roleSql = "SELECT"
                + " GROUP_CONCAT(sur.role_id) AS roleIds,"
                + " GROUP_CONCAT(sr.role_name) AS roleNames"
                + " FROM sys_user_role sur"
                + " LEFT JOIN sys_role sr ON sur.role_id = sr.id"
                + " WHERE user_id = ? "
                + " GROUP BY sur.user_id";
        return findFirst(roleSql, userId);
    }

    /**
     * 根据 userid 查询
     * @param userId
     * @return
     */
    public List<SysUserRole> findUserRolesByUserId(String userId) {
        String sql = "select * from sys_user_role where user_id = ? ";
        return find(sql, userId);
    }
}
