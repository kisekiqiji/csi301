package com.xjtlu.campusspace.ui.account;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

/** The same rules are reachable from registration and the profile page. */
public final class AccountRules {
    private AccountRules() { }

    public static final String TEXT = "1. 文明留言\n"
            + "纸条应与所选空间有关。请尊重他人，不发布辱骂、骚扰、广告或无关内容。\n\n"
            + "2. 如实描述设备问题\n"
            + "报修纸条请写清设备、位置、发现时间和具体问题。这里的纸条是同学间的提醒，不会自动提交学校维修工单；需要维修时请联系学校相应服务。\n\n"
            + "3. 保护个人隐私\n"
            + "不要公开自己或他人的证件号码、联系方式、账号密码等信息。失物照片请遮挡敏感内容，发布他人照片前应取得同意。\n\n"
            + "4. 以官方信息和现场情况为准\n"
            + "课表与预约状态仅供参考；实际预约需进入学校官方系统。纸条不能作为已预约或已报修的凭证。\n\n"
            + "5. 本机账号范围\n"
            + "这里的账号仅保存在当前设备，不是西浦统一身份认证，不验证学籍，也不会同步到其他设备。请设置专用密码，不要填写学校账号密码。清除应用数据或卸载应用可能删除本机账号与纸条。";

    public static void show(Context context) {
        new AlertDialog.Builder(context).setTitle("使用规则")
                .setMessage(TEXT).setPositiveButton("我已阅读", null).show();
    }
}
