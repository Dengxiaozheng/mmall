package com.mmall.service;

import com.mmall.common.ServerResponse;
import com.mmall.pojo.User;

/**
 * Created by dxz on 2018/8/6.
 */
public interface IUserService {

    ServerResponse<User>  login(String username, String password);//登录

    ServerResponse<java.lang.String> register(User user);//注册

    ServerResponse<String> checkValid(String str, String type);//验证用户名和邮箱是否存在

    ServerResponse selectQuestion(String username);//找回密码的提示问题

}
