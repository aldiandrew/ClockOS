package com.aldiandrew.clockos.shizuku;

interface IUserService {
    String exec(String command);
    String setStatusBarIcon(
        String slot,
        String packageName,
        int iconId,
        int iconLevel,
        String contentDescription
    );
    String removeStatusBarIcon(String slot);
}
