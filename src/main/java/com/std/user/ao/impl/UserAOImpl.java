/**
 * @Title UserAOImpl.java 
 * @Package com.ibis.pz.user.impl 
 * @Description 
 * @author miyb  
 * @date 2015-3-8 上午10:52:06 
 * @version V1.0   
 */
package com.std.user.ao.impl;

import java.util.Date;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.std.user.ao.IUserAO;
import com.std.user.bo.IAccountBO;
import com.std.user.bo.IBankCardBO;
import com.std.user.bo.IIdentifyBO;
import com.std.user.bo.ISYSRoleBO;
import com.std.user.bo.ISmsOutBO;
import com.std.user.bo.IUserBO;
import com.std.user.bo.base.Paginable;
import com.std.user.common.DateUtil;
import com.std.user.common.MD5Util;
import com.std.user.common.PhoneUtil;
import com.std.user.common.PropertiesUtil;
import com.std.user.domain.User;
import com.std.user.enums.ECurrency;
import com.std.user.enums.EIDKind;
import com.std.user.enums.EUserKind;
import com.std.user.enums.EUserPwd;
import com.std.user.enums.EUserStatus;
import com.std.user.exception.BizException;
import com.std.user.util.RandomUtil;

/** 
 * @author: miyb 
 * @since: 2015-3-8 上午10:52:06 
 * @history:
 */
@Service
public class UserAOImpl implements IUserAO {
    @Autowired
    protected IUserBO userBO;

    @Autowired
    protected ISYSRoleBO sysRoleBO;

    @Autowired
    protected IAccountBO accountBO;

    @Autowired
    IIdentifyBO dentifyBO;

    @Autowired
    protected IBankCardBO bankCardBO;

    @Autowired
    ISmsOutBO smsOutBO;

    @Override
    public void doCheckMobile(String mobile) {
        userBO.isMobileExist(mobile);
    }

    @Override
    @Transactional
    public String doRegister(String mobile, String loginPwd,
            String loginPwdStrength, String userReferee, String smsCaptcha) {
        // 验证手机号
        userBO.isMobileExist(mobile);
        // 验证推荐人是否是平台的已注册用户
        userBO.checkUserReferee(userReferee);
        // 短信验证码是否正确
        smsOutBO.checkCaptcha(mobile, smsCaptcha, "805041");
        // 插入用户信息
        String userId = userBO.doRegister(mobile, loginPwd, loginPwdStrength,
            userReferee);
        // 分配账号
        accountBO.distributeAccountTwo(userId, mobile, "CNY", userReferee);
        // 发送短信
        smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                + "用户，恭喜您成功注册。请妥善保管您的账户相关信息。", "805041");
        return userId;
    }

    @Override
    @Transactional
    public String doAddUser(String loginName, String mobile, String idKind,
            String idNo, String realName, String userReferee, String updater,
            String remark, String kind, String pdf) {
        String userId = null;
        // 插入用户信息
        String loginPsd = EUserPwd.InitPwd.getCode();
        if (EUserKind.F1.getCode().equals(kind)
                || EUserKind.F2.getCode().equals(kind)) {
            // 验证手机号
            userBO.isMobileExist(mobile, kind);
            // 插入用户信息
            loginPsd = RandomUtil.generate6();
            String tradePsd = EUserPwd.InitPwd.getCode();
            userId = userBO.doAddUser(mobile, mobile, loginPsd, userReferee,
                realName, idKind, idNo, tradePsd, kind, "0", remark, updater,
                pdf, null);
            // 三方认证
            dentifyBO.doIdentify(userId, realName, idKind, idNo);
            // 分配账号
            accountBO.distributeAccount(userId, realName,
                ECurrency.XNB.getCode());
            // 发送短信
            smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                    + "用户，您已成功注册。您的登录密码为" + loginPsd
                    + "，请及时登录个金所网站修改密码。如有疑问，请联系客服：400-0008-139。", "805042");
        } else if (EUserKind.Operator.getCode().equals(kind)) {
            // 验证登录名
            userBO.isLoginNameExist(loginName, kind);
            // 插入用户信息
            userId = userBO.doAddUser(loginName, mobile, loginPsd, userReferee,
                realName, idKind, idNo, loginPsd, kind, "0", remark, updater,
                pdf, null);
        } else if (EUserKind.Integral.getCode().equals(kind)
                || EUserKind.Goods.getCode().equals(kind)) {
            // 验证登录名
            userBO.isLoginNameExist(loginName, kind);
            int level = 1;
            if (StringUtils.isNotBlank(userReferee)) {
                String preUserId = userReferee;
                while (true) {
                    User data = userBO.getUser(preUserId);
                    if (data != null) {
                        preUserId = data.getUserReferee();
                        level++;
                        // 超过3级，按3级处理
                        if (level > 3) {
                            level = 3;
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }

            if (EUserKind.Integral.getCode().equals(kind)) {
                // 插入用户信息
                userId = userBO.doAddUser(loginName, mobile, loginPsd,
                    userReferee, realName, idKind, idNo, loginPsd, kind, level
                            + "", remark, updater, pdf,
                    PropertiesUtil.Config.NOTOP_JFROLECODE);
            } else {
                // 插入用户信息
                userId = userBO.doAddUser(loginName, mobile, loginPsd,
                    userReferee, realName, idKind, idNo, loginPsd, kind, level
                            + "", remark, updater, pdf, null);
            }
            dentifyBO.doIdentify(userId, realName, idKind, idNo);
            // 分配账号
            accountBO.distributeAccount(userId, realName,
                ECurrency.CNY.getCode());
            // 分配账号
            accountBO.distributeAccount(userId, realName,
                ECurrency.XNB.getCode());
        }
        return userId;
    }

    @Override
    @Transactional
    public String doAddUser(String mobile, String realName, String userReferee,
            String updater, String remark, String kind) {
        // 验证手机号
        userBO.isMobileExist(mobile);
        // 插入用户信息
        String loginPsd = RandomUtil.generate6();
        String tradePsd = RandomUtil.generate6();
        String userId = userBO.doAddUser(null, mobile, loginPsd, null,
            realName, null, null, tradePsd, kind, "0", remark, updater, null,
            null);
        // 分配账号
        accountBO.distributeAccount(userId, realName, "CNY");
        // 发送短信
        smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                + "用户，您已成功注册。您的登录密码为" + loginPsd + ";交易密码为" + tradePsd
                + "，请及时登录个金所网站修改密码。如有疑问，请联系客服：400-0008-139。", "805042");
        return userId;
    }

    @Override
    public String doLogin(String loginName, String loginPwd, String kind) {
        User condition = new User();
        if (EUserKind.F1.getCode().equals(kind)) {
            condition.setMobile(loginName);
        } else {
            condition.setLoginName(loginName);
        }
        List<User> userList1 = userBO.queryUserList(condition);
        if (CollectionUtils.isEmpty(userList1)) {
            throw new BizException("xn702002", "登录名不存在");
        }

        condition.setLoginPwd(MD5Util.md5(loginPwd));
        List<User> userList2 = userBO.queryUserList(condition);
        if (CollectionUtils.isEmpty(userList2)) {
            throw new BizException("xn702002", "登录密码错误");
        }
        User user = userList2.get(0);

        if (EUserKind.F1.getCode().equals(kind) && !kind.equals(user.getKind())) {
            throw new BizException("xn702002", "登录用户类型不正确");
        }
        if (!EUserStatus.NORMAL.getCode().equals(user.getStatus())) {
            throw new BizException("xn702002", "当前用户已被锁定，请联系工作人员");
        }
        return user.getUserId();
    }

    @Override
    public void doIdentify(String userId, String idKind, String idNo,
            String realName) {
        // 三方认证
        dentifyBO.doIdentify(userId, realName, idKind, idNo);
        // 更新用户表
        userBO
            .refreshIdentity(userId, realName, EIDKind.IDCard.getCode(), idNo);
        // 回写Account表realName;
        accountBO.refreshRealName(userId, realName);
    }

    @Override
    public void doEditRealName(String userId, String realName) {
        User user = userBO.getUser(userId);
        if (user == null) {
            throw new BizException("xn702002", "用户不存在");
        }
        userBO.refreshRealName(userId, realName);
    }

    @Override
    @Transactional
    public void doSetTradePwd(String userId, String tradePwd,
            String tradePwdStrength, String smsCaptcha) {
        // 判断是否和登录密码重复
        User user = this.doGetUser(userId);
        // 短信验证码是否正确
        smsOutBO.checkCaptcha(user.getMobile(), smsCaptcha, "805045");
        userBO.refreshTradePwd(userId, tradePwd, tradePwdStrength);
        // 发送短信
        String mobile = user.getMobile();
        smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                + "用户，您的交易密码设置成功。请妥善保管您的账户相关信息。", "805045");
    }

    @Override
    @Transactional
    public void doIdentifySetTradePwd(String userId, String idKind,
            String idNo, String realName, String tradePwd,
            String tradePwdStrength, String smsCaptcha) {
        // 三方认证
        dentifyBO.doIdentify(userId, realName, idKind, idNo);
        // 更新用户表
        userBO
            .refreshIdentity(userId, realName, EIDKind.IDCard.getCode(), idNo);
        // 回写Account表realName;
        accountBO.refreshRealName(userId, realName);

        // 开始交易密码设置
        // 判断是否和登录密码重复
        User user = this.doGetUser(userId);
        // 短信验证码是否正确
        smsOutBO.checkCaptcha(user.getMobile(), smsCaptcha, "805046");
        userBO.refreshTradePwd(userId, tradePwd, tradePwdStrength);
        // 发送短信
        String mobile = user.getMobile();
        smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                + "用户，您已通过实名认证，且交易密码设置成功。请妥善保管您的账户相关信息。", "805046");
    }

    @Override
    @Transactional
    public void doChangeMoblie(String userId, String newMobile,
            String smsCaptcha, String tradePwd) {
        User user = userBO.getUser(userId);
        if (user == null) {
            throw new BizException("li01004", "用户名不存在");
        }
        String oldMobile = user.getMobile();
        if (newMobile.equals(oldMobile)) {
            throw new BizException("li01009", "新手机与原手机一致");
        }
        userBO.isMobileExist(newMobile);
        // 验证交易密码
        userBO.checkTradePwd(userId, tradePwd);
        // 短信验证码是否正确（往新手机号发送）
        smsOutBO.checkCaptcha(newMobile, smsCaptcha, "805047");
        userBO.refreshMobile(userId, newMobile);
        // 发送短信
        smsOutBO.sendSmsOut(
            oldMobile,
            "尊敬的"
                    + PhoneUtil.hideMobile(oldMobile)
                    + "用户，您于"
                    + DateUtil.dateToStr(new Date(),
                        DateUtil.DATA_TIME_PATTERN_1)
                    + "提交的更改绑定手机号码服务审核通过，您的新绑定手机号码为" + newMobile
                    + "，请妥善保管您的账户相关信息。", "805047");
    }

    @Override
    @Transactional
    public void doFindLoginPwd(String mobile, String smsCaptcha,
            String newLoginPwd, String loginPwdStrength) {
        User user = userBO.getUserByMobile(mobile);
        if (user == null) {// 这里其实还有一种处理方法：就是直接注册
            throw new BizException("li01004", "用户不存在,请先注册");
        }
        // 短信验证码是否正确
        smsOutBO.checkCaptcha(mobile, smsCaptcha, "805048");

        userBO.refreshLoginPwd(user.getUserId(), MD5Util.md5(newLoginPwd),
            loginPwdStrength);
        // 发送短信
        smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                + "用户，您的登录密码找回成功。请妥善保管您的账户相关信息。", "805048");
    }

    @Override
    @Transactional
    public void doResetLoginPwd(String userId, String oldLoginPwd,
            String newLoginPwd, String loginPwdStrength) {
        if (oldLoginPwd.equals(newLoginPwd)) {
            throw new BizException("li01006", "新登录密码不能与原有密码重复");
        }
        // 验证当前登录密码是否正确
        userBO.checkLoginPwd(userId, oldLoginPwd);
        // 重置
        userBO.refreshLoginPwd(userId, MD5Util.md5(newLoginPwd),
            loginPwdStrength);

        // 发送短信
        User user = userBO.getUser(userId);
        String mobile = user.getMobile();
        if (!mobile.equals("admin")) {
            smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                    + "用户，您的登录密码修改成功。请妥善保管您的账户相关信息。", "805049");
        }
    }

    @Override
    @Transactional
    public void doFindTradePwd(String userId, String newTradePwd,
            String tradePwdStrength, String smsCaptcha, String idKind,
            String idNo) {
        User user = userBO.getUser(userId);
        if (user == null) {
            throw new BizException("li01004", "用户名不存在");
        }
        if (user.getIdKind() == null || user.getIdNo() == null) {
            throw new BizException("li01004", "请先实名认证");
        }
        // 证件是否正确
        if (!(user.getIdKind().equalsIgnoreCase(idKind) && user.getIdNo()
            .equalsIgnoreCase(idNo))) {
            throw new BizException("li01009", "证件验证不通过");
        }

        // 短信验证码是否正确
        String mobile = user.getMobile();
        smsOutBO.checkCaptcha(mobile, smsCaptcha, "805050");
        userBO.refreshTradePwd(userId, newTradePwd, tradePwdStrength);
        if (!mobile.equals("admin")) {
            // 发送短信
            smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                    + "用户，您的交易密码找回成功。请妥善保管您的账户相关信息。", "805050");
        }
    }

    @Override
    public void doFindTradePwd(String userId, String newTradePwd,
            String tradePwdStrength, String smsCaptcha) {
        User user = userBO.getUser(userId);
        if (user == null) {
            throw new BizException("li01004", "用户名不存在");
        }
        // 短信验证码是否正确
        String mobile = user.getMobile();
        smsOutBO.checkCaptcha(mobile, smsCaptcha, "805057");
        userBO.refreshTradePwd(userId, newTradePwd, tradePwdStrength);
        // 发送短信
        if (!mobile.equals("admin")) {
            smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                    + "用户，您的交易密码找回成功。请妥善保管您的账户相关信息。", "805057");
        }
    }

    @Override
    @Transactional
    public void doResetTradePwd(String userId, String oldTradePwd,
            String newTradePwd, String tradePwdStrength) {
        if (oldTradePwd.equals(newTradePwd)) {
            throw new BizException("li01008", "新交易密码与原有交易密码重复");
        }
        User user = null;
        User conditon = new User();
        conditon.setUserId(userId);
        conditon.setTradePwd(MD5Util.md5(oldTradePwd));
        List<User> list = userBO.queryUserList(conditon);
        if (CollectionUtils.isNotEmpty(list)) {
            user = list.get(0);
        } else {
            throw new BizException("li01008", "旧交易密码不正确");
        }
        userBO.refreshTradePwd(userId, newTradePwd, tradePwdStrength);
        // 发送短信
        String mobile = user.getMobile();
        if (!mobile.equals("admin")) {
            smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                    + "用户，您的交易密码修改成功。请妥善保管您的账户相关信息。", "805051");
        }
    }

    @Override
    public void doStatusUser(String userId, String toStatus, String updater,
            String remark) {
        User user = userBO.getUser(userId);
        if (user == null) {
            throw new BizException("li01004", "用户名不存在");
        }
        String mobile = user.getMobile();
        if (EUserStatus.Ren_Locked.getCode().equalsIgnoreCase(toStatus)) {
            userBO.refreshStatus(userId, EUserStatus.Ren_Locked, updater,
                remark);
            // 发送短信
            smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                    + "用户，您已经被注销用户", "805052");
        } else {
            userBO.refreshStatus(userId, EUserStatus.NORMAL, updater, remark);
            // 发送短信
            smsOutBO.sendSmsOut(mobile, "尊敬的" + PhoneUtil.hideMobile(mobile)
                    + "用户，您已经被激活用户", "805052");
        }

    }

    @Override
    public void doRoleUser(String userId, String roleCode, String updater,
            String remark) {
        User user = userBO.getUser(userId);
        if (user == null) {
            throw new BizException("li01004", "用户名不存在");
        }
        userBO.refreshRole(userId, roleCode, updater, remark);

    }

    @Override
    public Paginable<User> queryUserPage(int start, int limit, User condition) {
        return userBO.getPaginable(start, limit, condition);
    }

    @Override
    public List<User> queryUserList(User condition) {
        return userBO.queryUserList(condition);
    }

    @Override
    public User doGetUser(String userId) {
        User user = userBO.getUser(userId);
        if (user == null) {
            throw new BizException("li01004", userId + "用户不存在");
        }
        return user;
    }

    @Override
    public void checkTradePwd(String userId, String tradePwd) {
        userBO.checkTradePwd(userId, tradePwd);
    }

    @Override
    public void sendAppSms(String userId, String content) {
        User user = userBO.getUser(userId);
        if (user == null) {
            throw new BizException("xn702001", "用户不存在");
        }
        smsOutBO.sendSmsOut(user.getMobile(), content, "805903");
    }
}
