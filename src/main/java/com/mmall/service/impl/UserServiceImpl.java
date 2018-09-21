package com.mmall.service.impl;

import com.mmall.common.Const;
import com.mmall.common.ServerResponse;
import com.mmall.common.TokenCache;
import com.mmall.dao.UserMapper;
import com.mmall.pojo.User;
import com.mmall.service.IUserService;
import com.mmall.util.MD5Util;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Created by dxz on 2018/8/6.
 */


//向上注入service，iUserService与controller中的对象名称相同，即可将service注入controlle
@Service("iUserService")
public class UserServiceImpl implements IUserService{

    @Autowired
    private UserMapper userMapper;

    /*用户登录登录
    **
     */
    public ServerResponse<User> login(String username, String password) {
        int resultCount = userMapper.checkUsername(username);//检查用户名是否存在
        if(resultCount == 0){
            return ServerResponse.createByErrorMessage("用户名不存在");
        }
        String md5Password = MD5Util.MD5EncodeUtf8(password);//密码登录MD5,先将密码加密后再与数据库中的比较
        User user = userMapper.selectLogin(username, md5Password);//用户名存在，检查密码是否正确
        if(user == null){
            return ServerResponse.createByErrorMessage("密码错误");
        }
        user.setPassword(StringUtils.EMPTY);
        return ServerResponse.createBySuccess("登录成功", user);//登录成功
    }



    /*
    用户注册，,校验用户名是否存在，邮箱是否存在,定义为普通用户，密码加密，向数据库中插入，注册成功
    */
    public ServerResponse<String> register(User user)
    {

        ServerResponse validResponse = this.checkValid(user.getUsername(), Const.USERNAME);//检查用户名是否存在
        if(!validResponse.isSuccess()){
            return validResponse;
        }
        validResponse = this.checkValid(user.getEmail(), Const.EMAIL);
        if(!validResponse.isSuccess()){
            return validResponse;
        }
        user.setRole(Const.Role.ROLE_CUSTOMER); //定义用户角色为普通用户
        user.setPassword(MD5Util.MD5EncodeUtf8(user.getPassword()));//md5加密
        int resultCount = userMapper.insert(user);
        if(resultCount == 0){
            return ServerResponse.createByErrorMessage("注册失败");
        }
        return ServerResponse.createBySuccessMessage("注册成功");
    }


    /*
    注册过程中，实时校验用户名和邮箱是否存在   返回成功表示都不存在
     */
    public ServerResponse<String> checkValid(String str, String type)
    {
        if(StringUtils.isNotBlank(type)){
            //开始校验
            if(Const.USERNAME.equals(type)){
                int resultCount = userMapper.checkUsername(str);//检查用户名是否存在
                if(resultCount > 0){
                    return ServerResponse.createByErrorMessage("用户名已存在");
                }
            }
            if(Const.EMAIL.equals(type)){
                int resultCount = userMapper.checkEmail(str);
                if(resultCount > 0){
                    return ServerResponse.createByErrorMessage("email已存在");
                }
            }
        }else{
            return ServerResponse.createByErrorMessage("参数错误");
        }
        return ServerResponse.createBySuccessMessage("校验成功");
    }


    /*
    获取找回密码提示问题
     */
    public ServerResponse selectQuestion(String username){
        ServerResponse validResponse = this.checkValid(username, Const.USERNAME);
        if(validResponse.isSuccess()){
            //用户不存在
            return ServerResponse.createByErrorMessage("用户不存在");
        }
        //用户存在，查找提示问题以找回密码
        String question = userMapper.selectQuestionByUsername(username);
        if(StringUtils.isNotBlank(question)){
            return ServerResponse.createBySuccess(question);
        }
        return ServerResponse.createByErrorMessage("该用户未设置找回密码问题");
    }


    /*
    提交问题答案，如果答案正确，返回一个token，用于修改密码
     */
    public ServerResponse<String> checkAnswer(String username, String question, String answer){
        int resultCount = userMapper.checkAnswer(username, question, answer);//检查答案是否正确
        if(resultCount > 0){  //说明答案正确
            String forgetToken = UUID.randomUUID().toString();
            TokenCache.setKey(TokenCache.TOKEN_PREFIX + username, forgetToken);
            return ServerResponse.createBySuccess(forgetToken);
        }
        return ServerResponse.createByErrorMessage("问题的答案错误");
    }

    /*
    重置密码
     */
    public ServerResponse<String> forgetResetPassword(String username, String passwordNew, String forgetToken){
        //校验token
        if (StringUtils.isBlank(forgetToken)){
            return ServerResponse.createByErrorMessage("参数错误，token需要传递");
        }
        ServerResponse validResponse = this.checkValid(username, Const.USERNAME);
        if(validResponse.isSuccess()){
            //用户不存在
            return ServerResponse.createByErrorMessage("用户不存在");
        }
        String token = TokenCache.getKey(TokenCache.TOKEN_PREFIX + username);
        if(StringUtils.isBlank(token)){
            return ServerResponse.createByErrorMessage("token已失效");
        }
        if(StringUtils.equals(forgetToken, token)){
            String md5Password = MD5Util.MD5EncodeUtf8(passwordNew);
            int rowCount = userMapper.updatePasswordByUsername(username, md5Password);
            if(rowCount > 0 ){
                return  ServerResponse.createBySuccessMessage("修改密码成功");
            }
        }else{
            return ServerResponse.createByErrorMessage("token已失效");
        }
        return  ServerResponse.createByErrorMessage("修改密码失败");

    }


    /*
    登录状态，忘记密码
    先获取旧密码，解开MD5，和传入的密码对比，如果正确则修改密码，如果错误则提示旧密码输入错误
    防止横向越权，一定是这个用户的旧密码,所以要用ID号查找
     */
    public ServerResponse<String> resetPassword(String passwordOld, String passwordNew, User user){

        int resultCount = userMapper.checkPassword(MD5Util.MD5EncodeUtf8(passwordOld), user.getId());

        if(resultCount == 0){
            return ServerResponse.createByErrorMessage("旧密码错误");
        }
        user.setPassword(MD5Util.MD5EncodeUtf8(passwordNew));
        int updateCount = userMapper.updateByPrimaryKeySelective(user);
        if(updateCount > 0){
            return ServerResponse.createBySuccessMessage("修改密码成功");
        }
        return ServerResponse.createByErrorMessage("更新失败");
    }

    /*
    更新个人信息
     */
    public ServerResponse<User> updateInformation(User user){
        //username不能被更新，检验email，新的是否存在，如果存在，不能是当前用户的？？
        int resultCount = userMapper.checkEmailByUserId(user.getEmail(), user.getId());

        if(resultCount > 0){
            return  ServerResponse.createByErrorMessage("email已存在，请更换email再尝试更新");
        }

        User updateUser = new User();

        //yuan原登录用户的id
        updateUser.setId(user.getId());
        //修改的数据
        updateUser.setEmail(user.getEmail());
        updateUser.setPhone(user.getPhone());
        updateUser.setQuestion(user.getQuestion());
        updateUser.setAnswer(user.getAnswer());

        int updateCount = userMapper.updateByPrimaryKeySelective(updateUser);
        if(updateCount > 0){
            return ServerResponse.createBySuccess("更新个人信息成功", updateUser);
        }
        return ServerResponse.createByErrorMessage("更新个人信息失败");
    }
}
